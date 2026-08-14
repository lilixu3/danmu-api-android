package com.example.danmuapiapp.domain.model

data class CoreCandidateState(
    val variant: ApiVariant,
    val runMode: RunMode,
    val actionLabel: String,
    val installedAtMs: Long,
    val hasRecoveryPoint: Boolean
)

