package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreDependencyRepairRequest
import com.example.danmuapiapp.domain.model.CoreOperationPhase
import com.example.danmuapiapp.domain.model.CoreOperationState
import com.example.danmuapiapp.domain.model.RunMode
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreOperationControllerTest {
    private var state = CoreOperationState()
    private val controller = CoreOperationController(
        readState = { state },
        writeState = { state = it },
        nextOperationId = AtomicLong(4L)
    )

    private fun pending(staging: File) = PendingCoreMutation(
        operationId = 5L,
        repair = CoreDependencyRepairRequest(
            operationId = 5L,
            variant = ApiVariant.Stable,
            actionLabel = "更新",
            missingDependencies = listOf("express@4")
        ),
        stagingDir = staging,
        targetDir = File(staging.parentFile, "danmu_api_stable"),
        mode = RunMode.Normal,
        rootDirPath = "/root/core",
        sourceMetadata = null
    )

    @Test
    fun `begin assigns the next identity and rejects a second active task`() {
        val id = controller.begin(ApiVariant.Stable, "更新")
        assertEquals(5L, id)
        assertEquals(CoreOperationPhase.Running, state.phase)

        val error = runCatching { controller.begin(ApiVariant.Dev, "安装") }.exceptionOrNull()
        assertTrue(error is IOException)
        assertEquals("已有稳定版更新任务正在进行，请先完成或取消", error?.message)
    }

    @Test
    fun `repair transition only applies to the owning active operation`() {
        controller.begin(ApiVariant.Stable, "更新")
        controller.markAwaitingRepair(99L)
        assertEquals(CoreOperationPhase.Running, state.phase)

        controller.markAwaitingRepair(5L)
        assertEquals(CoreOperationPhase.AwaitingDependencyRepair, state.phase)
    }

    @Test
    fun `claim resumes awaiting mutation and clears pending when staging is missing`() {
        val parent = Files.createTempDirectory("core-operation").toFile()
        val staging = File(parent, "staging").apply { mkdirs() }
        try {
            controller.begin(ApiVariant.Stable, "更新")
            controller.markAwaitingRepair(5L)
            var cleared = 0
            val claimed = controller.claimPending(5L, "修复依赖", pending(staging)) { cleared += 1 }
            assertEquals(5L, claimed.operationId)
            assertEquals(CoreOperationPhase.Running, state.phase)
            assertEquals(0, cleared)

            state = state.copy(phase = CoreOperationPhase.AwaitingDependencyRepair)
            staging.deleteRecursively()
            val error = runCatching {
                controller.claimPending(5L, "修复依赖", pending(staging)) { cleared += 1 }
            }.exceptionOrNull()
            assertEquals("待修复的候选核心已失效，请重新执行更新", error?.message)
            assertEquals(1, cleared)
            assertEquals(CoreOperationState(), state)
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun `claim rejects mismatched ids and busy phases`() {
        val parent = Files.createTempDirectory("core-operation-busy").toFile()
        val staging = File(parent, "staging").apply { mkdirs() }
        try {
            controller.begin(ApiVariant.Stable, "更新")
            val missing = runCatching {
                controller.claimPending(5L, "修复依赖", null) {}
            }.exceptionOrNull()
            assertEquals("依赖修复任务已变化，请使用当前提示重新操作", missing?.message)

            val busy = runCatching {
                controller.claimPending(5L, "修复依赖", pending(staging)) {}
            }.exceptionOrNull()
            assertEquals("更新任务正在处理中，请稍候", busy?.message)
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun `finish clears only the matching operation`() {
        controller.begin(ApiVariant.Stable, "更新")
        controller.finish(4L)
        assertEquals(CoreOperationPhase.Running, state.phase)
        controller.finish(5L)
        assertEquals(CoreOperationState(), state)
        assertNull(state.variant)
    }
}
