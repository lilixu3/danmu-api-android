package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreDependencyRepairOrigin
import com.example.danmuapiapp.domain.model.CoreDependencyRepairRequest
import com.example.danmuapiapp.domain.model.RuntimeDependencyResumeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoreDependencyRepairPolicyTest {
    @Test
    fun `runtime start upgrades an unspecified resume action to start`() {
        assertEquals(
            RuntimeDependencyResumeAction.Start,
            CoreDependencyRepairPolicy.effectiveResumeAction(
                CoreDependencyRepairOrigin.RuntimeStart,
                RuntimeDependencyResumeAction.None
            )
        )
        assertEquals(
            RuntimeDependencyResumeAction.Restart,
            CoreDependencyRepairPolicy.effectiveResumeAction(
                CoreDependencyRepairOrigin.RuntimeStart,
                RuntimeDependencyResumeAction.Restart
            )
        )
        assertEquals(
            RuntimeDependencyResumeAction.None,
            CoreDependencyRepairPolicy.effectiveResumeAction(
                CoreDependencyRepairOrigin.WorkDirectory,
                RuntimeDependencyResumeAction.None
            )
        )
    }

    @Test
    fun `merging prefers restart then start and keeps none`() {
        assertEquals(
            RuntimeDependencyResumeAction.Restart,
            CoreDependencyRepairPolicy.mergeResumeAction(
                RuntimeDependencyResumeAction.Start,
                RuntimeDependencyResumeAction.Restart
            )
        )
        assertEquals(
            RuntimeDependencyResumeAction.Restart,
            CoreDependencyRepairPolicy.mergeResumeAction(
                RuntimeDependencyResumeAction.Restart,
                RuntimeDependencyResumeAction.None
            )
        )
        assertEquals(
            RuntimeDependencyResumeAction.Start,
            CoreDependencyRepairPolicy.mergeResumeAction(
                RuntimeDependencyResumeAction.None,
                RuntimeDependencyResumeAction.Start
            )
        )
        assertEquals(
            RuntimeDependencyResumeAction.None,
            CoreDependencyRepairPolicy.mergeResumeAction(
                RuntimeDependencyResumeAction.None,
                RuntimeDependencyResumeAction.None
            )
        )
    }

    @Test
    fun `action labels match the existing UI wording`() {
        assertEquals("启动", CoreDependencyRepairPolicy.actionLabel(RuntimeDependencyResumeAction.Start))
        assertEquals("重启", CoreDependencyRepairPolicy.actionLabel(RuntimeDependencyResumeAction.Restart))
        assertEquals("修复", CoreDependencyRepairPolicy.actionLabel(RuntimeDependencyResumeAction.None))
    }

    @Test
    fun `missing dependencies merge suspected requirement and stay sorted`() {
        assertEquals(
            listOf("a@1", "b@2", "express@4"),
            CoreDependencyRepairPolicy.collectMissingDependencies(
                detected = listOf("b@2", "a@1", "b@2"),
                suspectedRequirement = "express@4"
            )
        )
        assertEquals(
            listOf("a@1"),
            CoreDependencyRepairPolicy.collectMissingDependencies(listOf("a@1"), null)
        )
    }

    @Test
    fun `install candidate with same missing set is reused only for the same variant`() {
        val pending = pending(type = PendingCoreMutationType.ReplaceCore)
        assertEquals(
            pending.repair,
            CoreDependencyRepairPolicy.reusableRequest(
                existing = pending,
                variant = ApiVariant.Stable,
                missingDependencies = listOf("express@4"),
                effectiveResumeAction = RuntimeDependencyResumeAction.Restart
            )
        )
        assertNull(
            CoreDependencyRepairPolicy.reusableRequest(
                existing = pending,
                variant = ApiVariant.Dev,
                missingDependencies = listOf("express@4"),
                effectiveResumeAction = RuntimeDependencyResumeAction.None
            )
        )
        assertNull(
            CoreDependencyRepairPolicy.reusableRequest(
                existing = pending,
                variant = ApiVariant.Stable,
                missingDependencies = listOf("other@1"),
                effectiveResumeAction = RuntimeDependencyResumeAction.None
            )
        )
    }

    @Test
    fun `installed dependency repair upgrades its resume action when requested`() {
        val pending = pending(resume = RuntimeDependencyResumeAction.None)
        assertEquals(
            pending.repair,
            CoreDependencyRepairPolicy.reusableRequest(
                pending,
                ApiVariant.Stable,
                listOf("express@4"),
                RuntimeDependencyResumeAction.None
            )
        )
        val upgraded = CoreDependencyRepairPolicy.reusableRequest(
            pending,
            ApiVariant.Stable,
            listOf("express@4"),
            RuntimeDependencyResumeAction.Restart
        )!!
        assertEquals(RuntimeDependencyResumeAction.Restart, upgraded.resumeAction)
        assertEquals("重启", upgraded.actionLabel)
    }

}

    private fun pending(
        variant: ApiVariant = ApiVariant.Stable,
        missing: List<String> = listOf("express@4"),
        resume: RuntimeDependencyResumeAction = RuntimeDependencyResumeAction.None,
        type: PendingCoreMutationType = PendingCoreMutationType.RepairInstalledDependencies
    ): PendingCoreMutation = PendingCoreMutation(
        operationId = 1L,
        repair = CoreDependencyRepairRequest(
            operationId = 1L,
            variant = variant,
            actionLabel = CoreDependencyRepairPolicy.actionLabel(resume),
            missingDependencies = missing,
            resumeAction = resume
        ),
        stagingDir = java.io.File("/tmp/staging"),
        targetDir = java.io.File("/tmp/target"),
        mode = com.example.danmuapiapp.domain.model.RunMode.Normal,
        rootDirPath = "/root/core",
        sourceMetadata = null,
        type = type
    )
