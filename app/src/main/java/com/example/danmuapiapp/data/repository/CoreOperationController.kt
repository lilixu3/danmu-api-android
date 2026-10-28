package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreOperationPhase
import com.example.danmuapiapp.domain.model.CoreOperationState
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/** Owns the single core mutation gate and its operation identity. */
internal class CoreOperationController(
    private val readState: () -> CoreOperationState,
    private val writeState: (CoreOperationState) -> Unit,
    private val nextOperationId: AtomicLong = AtomicLong(0L)
) {
    fun begin(variant: ApiVariant?, actionLabel: String): Long {
        val active = readState()
        if (active.isActive) {
            throw IOException(
                "已有${active.variant?.label.orEmpty()}${active.actionLabel}任务正在进行，请先完成或取消"
            )
        }
        val operationId = nextOperationId.incrementAndGet()
        writeState(
            CoreOperationState(
                operationId = operationId,
                variant = variant,
                actionLabel = actionLabel,
                phase = CoreOperationPhase.Running
            )
        )
        return operationId
    }

    fun markAwaitingRepair(operationId: Long) {
        val current = readState()
        if (current.operationId != operationId || !current.isActive) return
        writeState(current.copy(phase = CoreOperationPhase.AwaitingDependencyRepair))
    }

    fun claimPending(
        operationId: Long,
        actionLabel: String,
        pending: PendingCoreMutation?,
        clearPending: () -> Unit
    ): PendingCoreMutation {
        val current = readState()
        if (current.operationId != operationId || pending?.operationId != operationId) {
            throw IOException("依赖修复任务已变化，请使用当前提示重新操作")
        }
        if (!pending.stagingDir.isDirectory) {
            clearPending()
            writeState(CoreOperationState())
            throw IOException("待修复的候选核心已失效，请重新执行${pending.repair.actionLabel}")
        }
        if (current.phase != CoreOperationPhase.AwaitingDependencyRepair) {
            throw IOException("${current.actionLabel.ifBlank { actionLabel }}任务正在处理中，请稍候")
        }
        writeState(current.copy(phase = CoreOperationPhase.Running))
        return pending
    }

    fun finish(operationId: Long) {
        if (readState().operationId == operationId) {
            writeState(CoreOperationState())
        }
    }
}
