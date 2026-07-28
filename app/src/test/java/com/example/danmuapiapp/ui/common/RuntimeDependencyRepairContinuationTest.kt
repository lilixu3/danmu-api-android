package com.example.danmuapiapp.ui.common

import com.example.danmuapiapp.domain.model.RuntimeDependencyResumeAction
import com.example.danmuapiapp.domain.model.ServiceStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeDependencyRepairContinuationTest {

    @Test
    fun `重启修复完成后按当前服务状态继续`() {
        assertEquals(
            RuntimeDependencyContinuation.Restart,
            resolveRuntimeDependencyContinuation(
                RuntimeDependencyResumeAction.Restart,
                ServiceStatus.Running
            )
        )
        assertEquals(
            RuntimeDependencyContinuation.Start,
            resolveRuntimeDependencyContinuation(
                RuntimeDependencyResumeAction.Restart,
                ServiceStatus.Stopped
            )
        )
    }

    @Test
    fun `启动修复不会中断已经运行的服务`() {
        assertEquals(
            RuntimeDependencyContinuation.KeepRunning,
            resolveRuntimeDependencyContinuation(
                RuntimeDependencyResumeAction.Start,
                ServiceStatus.Running
            )
        )
        assertEquals(
            RuntimeDependencyContinuation.Start,
            resolveRuntimeDependencyContinuation(
                RuntimeDependencyResumeAction.Start,
                ServiceStatus.Error
            )
        )
    }

    @Test
    fun `普通修复不自动改变服务状态`() {
        ServiceStatus.entries.forEach { status ->
            assertEquals(
                RuntimeDependencyContinuation.None,
                resolveRuntimeDependencyContinuation(RuntimeDependencyResumeAction.None, status)
            )
        }
    }
}
