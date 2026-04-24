package com.example.danmuapiapp.ui.screen.core

import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.model.ServiceStatus

internal data class CoreApplyPlan(
    val shouldStopServiceBeforeApply: Boolean,
    val shouldRestartServiceAfterApply: Boolean
)

internal fun decideCoreApplyPlan(
    state: RuntimeState,
    targetVariant: ApiVariant
): CoreApplyPlan {
    val sameVariant = state.variant == targetVariant
    val runtimeActive = state.status == ServiceStatus.Running || state.status == ServiceStatus.Starting
    return CoreApplyPlan(
        shouldStopServiceBeforeApply = false,
        shouldRestartServiceAfterApply = sameVariant && runtimeActive
    )
}
