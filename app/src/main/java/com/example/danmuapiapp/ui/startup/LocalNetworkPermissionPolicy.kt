package com.example.danmuapiapp.ui.startup

import com.example.danmuapiapp.domain.model.RunMode

internal enum class LocalNetworkPermissionAction {
    Request,
    Settings
}

internal data class LocalNetworkPermissionState(
    val required: Boolean,
    val granted: Boolean,
    val requestAttempted: Boolean
) {
    val ready: Boolean
        get() = required.not() || granted
}

internal object LocalNetworkPermissionPolicy {
    const val ANDROID_17_API_LEVEL = 37
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    fun stateFor(
        sdkInt: Int,
        granted: Boolean,
        requestAttempted: Boolean
    ): LocalNetworkPermissionState {
        return LocalNetworkPermissionState(
            required = sdkInt >= ANDROID_17_API_LEVEL,
            granted = granted,
            requestAttempted = requestAttempted
        )
    }

    fun resolveAction(
        state: LocalNetworkPermissionState,
        hasActivity: Boolean,
        shouldShowRationale: Boolean
    ): LocalNetworkPermissionAction? {
        if (state.ready) return null
        if (hasActivity.not()) return LocalNetworkPermissionAction.Settings
        return if (state.requestAttempted.not() || shouldShowRationale) {
            LocalNetworkPermissionAction.Request
        } else {
            LocalNetworkPermissionAction.Settings
        }
    }

    fun shouldShowSetupStep(
        runMode: RunMode,
        notificationReady: Boolean,
        batteryReady: Boolean,
        localNetworkState: LocalNetworkPermissionState
    ): Boolean {
        if (localNetworkState.ready.not()) return true
        return runMode == RunMode.Normal && (notificationReady.not() || batteryReady.not())
    }

    fun shouldShowCompatGuide(
        state: LocalNetworkPermissionState,
        dismissedThisLaunch: Boolean
    ): Boolean {
        return state.ready.not() && dismissedThisLaunch.not()
    }

    fun shouldShowAddressHint(state: LocalNetworkPermissionState): Boolean {
        return state.ready.not()
    }
}
