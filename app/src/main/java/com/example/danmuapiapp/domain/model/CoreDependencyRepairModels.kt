package com.example.danmuapiapp.domain.model

import java.io.IOException

data class CoreDependencyRepairRequest(
    val variant: ApiVariant,
    val actionLabel: String,
    val missingDependencies: List<String>,
    val candidateVersion: String? = null,
    val onlineRepairSupported: Boolean = variant != ApiVariant.Custom
)

class CoreDependencyRepairRequiredException(
    val request: CoreDependencyRepairRequest
) : IOException(
    "${request.actionLabel}待修复依赖：${request.missingDependencies.joinToString(", ")}"
)
