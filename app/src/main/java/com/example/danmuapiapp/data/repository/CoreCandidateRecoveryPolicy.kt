package com.example.danmuapiapp.data.repository

internal object CoreCandidateRecoveryPolicy {
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

