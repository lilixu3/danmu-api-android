package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreCandidateState
import com.example.danmuapiapp.domain.model.RunMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreCandidateRecoveryPolicyTest {
    @Test
    fun consecutiveReplacementKeepsEarlierKnownGoodRecovery() {
        val selected = CoreCandidateRecoveryPolicy.selectRecoveryPath(
            targetPath = "/work/danmu_api_stable",
            replacementBackupPath = "/work/danmu_api_stable.backup-new",
            previousTargetPath = "/work/danmu_api_stable",
            previousRecoveryPath = "/work/danmu_api_stable.backup-known-good",
            previousRecoveryAvailable = true
        )
        assertEquals("/work/danmu_api_stable.backup-known-good", selected)
    }

    @Test
    fun differentCoreUsesItsOwnReplacementBackup() {
        val selected = CoreCandidateRecoveryPolicy.selectRecoveryPath(
            targetPath = "/work/danmu_api_dev",
            replacementBackupPath = "/work/danmu_api_dev.backup-new",
            previousTargetPath = "/work/danmu_api_stable",
            previousRecoveryPath = "/work/danmu_api_stable.backup-known-good",
            previousRecoveryAvailable = true
        )
        assertEquals("/work/danmu_api_dev.backup-new", selected)
    }

    @Test
    fun recoveryAttemptMustMatchTheExactCandidateInstallation() {
        val candidate = CoreCandidateState(
            variant = ApiVariant.Stable,
            runMode = RunMode.Normal,
            actionLabel = "更新",
            installedAtMs = 200L,
            hasRecoveryPoint = true
        )

        assertTrue(
            CoreCandidateRecoveryPolicy.matchesAttempt(
                candidate,
                ApiVariant.Stable,
                RunMode.Normal,
                installedAtMs = 200L
            )
        )
        assertFalse(
            CoreCandidateRecoveryPolicy.matchesAttempt(
                candidate,
                ApiVariant.Stable,
                RunMode.Normal,
                installedAtMs = 100L
            )
        )
    }
}
