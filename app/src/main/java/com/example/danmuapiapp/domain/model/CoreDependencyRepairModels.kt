package com.example.danmuapiapp.domain.model

import java.io.IOException

enum class CoreDependencyRepairOrigin {
    CoreMutation,
    WorkDirectory,
    RuntimeStart
}

data class CoreDependencyRepairRequest(
    val operationId: Long,
    val variant: ApiVariant,
    val actionLabel: String,
    val missingDependencies: List<String>,
    val candidateVersion: String? = null,
    val onlineRepairSupported: Boolean = variant != ApiVariant.Custom,
    val origin: CoreDependencyRepairOrigin = CoreDependencyRepairOrigin.CoreMutation
)

enum class CoreOperationPhase {
    Idle,
    Running,
    AwaitingDependencyRepair
}

data class CoreOperationState(
    val operationId: Long = 0L,
    val variant: ApiVariant? = null,
    val actionLabel: String = "",
    val phase: CoreOperationPhase = CoreOperationPhase.Idle
) {
    val isActive: Boolean
        get() = phase != CoreOperationPhase.Idle
}

class CoreDependencyRepairRequiredException(
    val request: CoreDependencyRepairRequest
) : IOException(
    "${request.actionLabel}待修复依赖：${request.missingDependencies.joinToString(", ")}"
)
