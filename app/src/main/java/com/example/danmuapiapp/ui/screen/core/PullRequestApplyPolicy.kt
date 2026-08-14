package com.example.danmuapiapp.ui.screen.core

import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.model.ServiceStatus

internal data class PullRequestApplyPlan(
    val shouldRequestStop: Boolean,
    val shouldAwaitStopped: Boolean,
    val shouldStartTargetAfterApply: Boolean
)

internal fun decidePullRequestApplyPlan(
    state: RuntimeState,
    targetVariant: ApiVariant,
    activateAfterInstall: Boolean
): PullRequestApplyPlan {
    val runtimeActive = state.status == ServiceStatus.Running ||
        state.status == ServiceStatus.Starting
    val replacingActiveCore = state.variant == targetVariant
    val stopForApply = runtimeActive && (replacingActiveCore || activateAfterInstall)
    return PullRequestApplyPlan(
        shouldRequestStop = stopForApply,
        shouldAwaitStopped = stopForApply || state.status == ServiceStatus.Stopping,
        shouldStartTargetAfterApply = stopForApply
    )
}
