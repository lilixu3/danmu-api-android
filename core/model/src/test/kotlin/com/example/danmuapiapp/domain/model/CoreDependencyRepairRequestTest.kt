package com.example.danmuapiapp.domain.model

import org.junit.Assert.*
import org.junit.Test

class CoreDependencyRepairRequestTest {
    @Test fun `ready PR candidate does not require the startup dependency screen`() {
        val request = CoreDependencyRepairRequest(
            operationId = 42L,
            variant = ApiVariant.Stable,
            actionLabel = "应用 PR 组合",
            missingDependencies = emptyList()
        )
        assertFalse(request.requiresUserRepair)
    }

    @Test fun `missing runtime dependencies still require user repair`() {
        val request = CoreDependencyRepairRequest(
            operationId = 42L,
            variant = ApiVariant.Stable,
            actionLabel = "修复运行依赖",
            missingDependencies = listOf("express"),
            origin = CoreDependencyRepairOrigin.RuntimeStart,
            resumeAction = RuntimeDependencyResumeAction.Start
        )
        assertTrue(request.requiresUserRepair)
    }

    @Test fun `completed repair closes startup gate while preserving the operation to apply and resume`() {
        val request = CoreDependencyRepairRequest(
            operationId = 42L,
            variant = ApiVariant.Stable,
            actionLabel = "重装",
            missingDependencies = listOf("express"),
            candidateVersion = "1.21.5",
            resumeAction = RuntimeDependencyResumeAction.Restart
        )
        val ready = request.copy(missingDependencies = emptyList())
        assertFalse(ready.requiresUserRepair)
        assertEquals(request.operationId, ready.operationId)
        assertEquals(request.candidateVersion, ready.candidateVersion)
        assertEquals(RuntimeDependencyResumeAction.Restart, ready.resumeAction)
    }
}
