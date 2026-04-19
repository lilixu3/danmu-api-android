package com.example.danmuapiapp.ui.screen.core

import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.model.ServiceStatus

internal data class CoreApplyPlan(
    val shouldStopServiceBeforeApply: Boolean,
    val shouldStartServiceAfterApply: Boolean
)

internal fun decideCoreApplyPlan(
    state: RuntimeState,
    targetVariant: ApiVariant
): CoreApplyPlan {
    val sameVariant = state.variant == targetVariant
    val runtimeActive = state.status == ServiceStatus.Running || state.status == ServiceStatus.Starting
    val shouldStopAndResume = sameVariant && runtimeActive
    return CoreApplyPlan(
        shouldStopServiceBeforeApply = shouldStopAndResume,
        shouldStartServiceAfterApply = shouldStopAndResume
    )
}
