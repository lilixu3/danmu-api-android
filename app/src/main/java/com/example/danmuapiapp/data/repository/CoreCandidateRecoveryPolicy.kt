package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreCandidateState
import com.example.danmuapiapp.domain.model.RunMode

internal object CoreCandidateRecoveryPolicy {
    fun matchesAttempt(
        candidate: CoreCandidateState?,
        variant: ApiVariant,
        runMode: RunMode,
        installedAtMs: Long
    ): Boolean {
        return candidate?.variant == variant &&
            candidate.runMode == runMode &&
            candidate.installedAtMs == installedAtMs
    }

    fun selectRecoveryPath(
        targetPath: String,
        replacementBackupPath: String?,
        previousTargetPath: String?,
        previousRecoveryPath: String?,
        previousRecoveryAvailable: Boolean
    ): String? {
        return if (
            previousRecoveryAvailable && previousTargetPath == targetPath &&
            !previousRecoveryPath.isNullOrBlank()
        ) {
            previousRecoveryPath
        } else {
            replacementBackupPath
        }
    }
}
