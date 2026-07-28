package com.example.danmuapiapp.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.danmuapiapp.domain.model.CoreDependencyRepairRequest
import com.example.danmuapiapp.domain.repository.CoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CoreDependencyRepairController(
    private val scope: CoroutineScope,
    private val repository: CoreRepository,
    private val autoShowRequiredPrompt: Boolean = true,
    private val shouldHandle: (CoreDependencyRepairRequest) -> Boolean = { true },
    private val setOperating: (Boolean) -> Unit,
    private val postMessage: (String) -> Unit,
    private val onApplied: suspend (CoreDependencyRepairRequest) -> String,
    private val onDiscarded: (CoreDependencyRepairRequest) -> String
) {
    val pendingDependencyRepair: StateFlow<CoreDependencyRepairRequest?> =
        repository.pendingDependencyRepair

    var showRequiredPrompt by mutableStateOf(false)
        private set
    var showRepairDialog by mutableStateOf(false)
        private set
    var isRepairing by mutableStateOf(false)
        private set

    init {
        scope.launch {
            pendingDependencyRepair.collect { request ->
                if (request == null || !shouldHandle(request)) {
                    showRequiredPrompt = false
                    showRepairDialog = false
                } else if (autoShowRequiredPrompt && !showRepairDialog) {
                    showRequiredPrompt = true
                }
            }
        }
    }

    fun dismissRequiredPrompt() {
        showRequiredPrompt = false
    }

    fun openRepairDialog() {
        val request = pendingDependencyRepair.value ?: return
        if (!shouldHandle(request)) return
        showRequiredPrompt = false
        showRepairDialog = true
    }

    fun dismissRepairDialog() {
        showRepairDialog = false
    }

    fun repairOnlineNow() {
        performRepair(
            progressMessage = "正在在线修复运行时依赖...",
            repairBlock = repository::repairPendingDependenciesOnline
        )
    }

    fun repairFromArchive(archiveUri: String) {
        performRepair(
            progressMessage = "正在导入并校验运行时依赖...",
            repairBlock = { operationId ->
                repository.repairPendingDependenciesFromArchive(operationId, archiveUri)
            }
        )
    }

    fun discardPendingMutation() {
        val request = pendingDependencyRepair.value ?: return
        if (!shouldHandle(request) || isRepairing) return
        showRequiredPrompt = false
        showRepairDialog = false
        scope.launch {
            isRepairing = true
            setOperating(true)
            try {
                repository.discardPendingCoreMutation(request.operationId).fold(
                    onSuccess = { postMessage(onDiscarded(request)) },
                    onFailure = { postMessage("取消失败：${it.message ?: "任务状态已变化"}") }
                )
            } finally {
                isRepairing = false
                setOperating(false)
            }
        }
    }

    private fun performRepair(
        progressMessage: String,
        repairBlock: suspend (Long) -> Result<Unit>
    ) {
        val request = pendingDependencyRepair.value ?: return
        if (!shouldHandle(request) || isRepairing) return
        showRequiredPrompt = false
        showRepairDialog = false
        scope.launch {
            isRepairing = true
            setOperating(true)
            postMessage(progressMessage)
            try {
                repairBlock(request.operationId).fold(
                    onSuccess = {
                        postMessage("依赖校验通过，正在继续${request.actionLabel}...")
                        repository.applyPendingCoreMutation(request.operationId).fold(
                            onSuccess = {
                                val message = try {
                                    onApplied(request)
                                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                        "依赖已修复，但后续操作失败：${error.message ?: "未知错误"}"
                                }
                                postMessage(message)
                            },
                            onFailure = { error ->
                                postMessage("${request.actionLabel}失败：${error.message ?: "未知错误"}")
                            }
                        )
                    },
                    onFailure = { error ->
                        postMessage("依赖修复失败：${error.message ?: "未知错误"}")
                        val current = pendingDependencyRepair.value
                        showRepairDialog = current != null && shouldHandle(current)
                    }
                )
            } finally {
                isRepairing = false
                setOperating(false)
            }
        }
    }
}
