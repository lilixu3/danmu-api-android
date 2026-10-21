package com.example.danmuapiapp.ui.startup

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.danmuapiapp.data.service.NodeKeepAlivePrefs
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.ui.screen.home.NormalModeKeepAliveGuideNavigator

internal enum class SetupStep {
    Mode,
    Core,
    Dependency,
    Permission
}

internal enum class NotificationAction {
    Request,
    Settings
}

internal data class StartupPermissionState(
    val runMode: RunMode,
    val notificationRequired: Boolean,
    val notificationGranted: Boolean,
    val notificationRequestAttempted: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val localNetwork: LocalNetworkPermissionState,
    val notificationSupported: Boolean,
    val batterySupported: Boolean,
    val localNetworkSupported: Boolean,
    val notificationSkipped: Boolean,
    val batterySkipped: Boolean,
    val localNetworkSkipped: Boolean
) {
    val notificationReady: Boolean
        get() = startupPermissionStepReady(notificationRequired, notificationSupported, notificationGranted, notificationSkipped)

    val batteryRequired: Boolean
        get() = runMode == RunMode.Normal

    val batteryReady: Boolean
        get() = startupPermissionStepReady(batteryRequired, batterySupported, batteryOptimizationIgnored, batterySkipped)

    val localNetworkReady: Boolean
        get() = startupPermissionStepReady(localNetwork.required, localNetworkSupported, localNetwork.granted, localNetworkSkipped)

    val unsupportedItems: List<StartupPermissionItem>
        get() = buildList {
            if (runMode == RunMode.Normal && notificationRequired && !notificationSupported && !notificationGranted) add(StartupPermissionItem.Notification)
            if (batteryRequired && !batterySupported && !batteryOptimizationIgnored) add(StartupPermissionItem.Battery)
            if (localNetwork.required && !localNetworkSupported && !localNetwork.granted) add(StartupPermissionItem.LocalNetwork)
        }
}

internal fun readPermissionState(context: Context, runMode: RunMode): StartupPermissionState {
    val appContext = context.applicationContext
    val localNetworkRequired = Build.VERSION.SDK_INT >= LocalNetworkPermissionPolicy.ANDROID_17_API_LEVEL
    val localNetworkGranted = localNetworkRequired.not() || runCatching { ContextCompat.checkSelfPermission(
        appContext, LocalNetworkPermissionPolicy.PERMISSION
    ) == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
    return StartupPermissionState(
        runMode = runMode,
        notificationRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
        notificationGranted = runCatching { NodeKeepAlivePrefs.hasPostNotificationsPermission(appContext) }.getOrDefault(false),
        notificationRequestAttempted = StartupPermissionGatePrefs.hasRequestedNotificationPermission(appContext),
        batteryOptimizationIgnored = NormalModeKeepAliveGuideNavigator.isIgnoringBatteryOptimizations(appContext),
        localNetwork = LocalNetworkPermissionPolicy.stateFor(
            sdkInt = Build.VERSION.SDK_INT,
            granted = localNetworkGranted,
            requestAttempted = StartupPermissionGatePrefs.hasRequestedLocalNetworkPermission(appContext)
        ),
        notificationSupported = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            StartupPermissionSupport.runtimePermissionSupported(appContext, Manifest.permission.POST_NOTIFICATIONS),
        batterySupported = StartupPermissionSupport.batterySupported(appContext),
        localNetworkSupported = !localNetworkRequired ||
            StartupPermissionSupport.runtimePermissionSupported(appContext, LocalNetworkPermissionPolicy.PERMISSION),
        notificationSkipped = StartupPermissionGatePrefs.hasSkipped(appContext, StartupPermissionItem.Notification),
        batterySkipped = StartupPermissionGatePrefs.hasSkipped(appContext, StartupPermissionItem.Battery),
        localNetworkSkipped = StartupPermissionGatePrefs.hasSkipped(appContext, StartupPermissionItem.LocalNetwork)
    )
}

internal fun resolveNotificationAction(
    activity: Activity?,
    permissionState: StartupPermissionState
): NotificationAction? {
    if (permissionState.notificationReady) return null
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    if (activity == null) return NotificationAction.Settings

    val shouldShowRationale = runCatching { ActivityCompat.shouldShowRequestPermissionRationale(
        activity, Manifest.permission.POST_NOTIFICATIONS
    ) }.getOrDefault(false)
    return if (permissionState.notificationRequestAttempted.not() || shouldShowRationale) {
        NotificationAction.Request
    } else {
        NotificationAction.Settings
    }
}

internal fun resolveLocalNetworkAction(
    activity: Activity?,
    state: LocalNetworkPermissionState
): LocalNetworkPermissionAction? {
    if (state.ready) return null
    val shouldShowRationale = activity?.let {
        runCatching { ActivityCompat.shouldShowRequestPermissionRationale(
            it, LocalNetworkPermissionPolicy.PERMISSION
        ) }.getOrDefault(false)
    } == true
    return LocalNetworkPermissionPolicy.resolveAction(
        state = state,
        hasActivity = activity != null,
        shouldShowRationale = shouldShowRationale
    )
}
