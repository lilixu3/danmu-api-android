package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.CoreDependencyRepairOrigin
import com.example.danmuapiapp.domain.model.CoreDependencyRepairRequest
import com.example.danmuapiapp.domain.model.RuntimeDependencyResumeAction

/** Keeps dependency-repair resume semantics independent from repository state. */
internal object CoreDependencyRepairPolicy {
    fun effectiveResumeAction(
        origin: CoreDependencyRepairOrigin,
        requested: RuntimeDependencyResumeAction
    ): RuntimeDependencyResumeAction {
        return if (origin == CoreDependencyRepairOrigin.RuntimeStart &&
            requested == RuntimeDependencyResumeAction.None
        ) {
            RuntimeDependencyResumeAction.Start
        } else {
            requested
        }
    }

    fun mergeResumeAction(
        existing: RuntimeDependencyResumeAction,
        requested: RuntimeDependencyResumeAction
    ): RuntimeDependencyResumeAction {
        return when {
            existing == RuntimeDependencyResumeAction.Restart ||
                requested == RuntimeDependencyResumeAction.Restart -> RuntimeDependencyResumeAction.Restart
            existing == RuntimeDependencyResumeAction.Start ||
                requested == RuntimeDependencyResumeAction.Start -> RuntimeDependencyResumeAction.Start
            else -> RuntimeDependencyResumeAction.None
        }
    }

    fun actionLabel(action: RuntimeDependencyResumeAction): String = when (action) {
        RuntimeDependencyResumeAction.Start -> "启动"
        RuntimeDependencyResumeAction.Restart -> "重启"
        RuntimeDependencyResumeAction.None -> "修复"
    }

    fun collectMissingDependencies(
        detected: List<String>,
        suspectedRequirement: String?
    ): List<String> = (detected + listOfNotNull(suspectedRequirement)).distinct().sorted()

    fun reusableRequest(
        existing: PendingCoreMutation?,
        variant: com.example.danmuapiapp.domain.model.ApiVariant,
        missingDependencies: List<String>,
        effectiveResumeAction: RuntimeDependencyResumeAction
    ): CoreDependencyRepairRequest? {
        val current = existing?.takeIf {
            it.repair.variant == variant && it.repair.missingDependencies == missingDependencies
        } ?: return null
        if (current.type != PendingCoreMutationType.RepairInstalledDependencies) return current.repair
        val merged = mergeResumeAction(current.repair.resumeAction, effectiveResumeAction)
        if (merged == current.repair.resumeAction) return current.repair
        return current.repair.copy(
            actionLabel = actionLabel(merged),
            resumeAction = merged
        )
    }

}
