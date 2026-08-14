package com.example.danmuapiapp.data.repository

import org.junit.Assert.assertEquals
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
}

