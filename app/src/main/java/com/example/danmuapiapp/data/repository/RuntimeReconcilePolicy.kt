package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.ServiceStatus

internal fun shouldRunPeriodicRuntimeReconcile(appForeground: Boolean): Boolean {
    return appForeground
}

internal fun shouldRunPeriodicNormalStateReconcile(
    runMode: RunMode,
    status: ServiceStatus
): Boolean {
    if (runMode != RunMode.Normal) return false
    return status == ServiceStatus.Starting ||
        status == ServiceStatus.Running ||
        status == ServiceStatus.Stopping
}

internal const val NORMAL_RUNNING_UNREACHABLE_THRESHOLD = 2

internal enum class NormalRunningReconcileAction {
    Healthy,
    RespectUserDismissal,
    RestoreForeground,
    WaitForNextProbe,
    MarkStopped,
    MarkError
}

internal fun decideNormalRunningReconcileAction(
    consecutiveUnreachableCount: Int,
    serviceRunning: Boolean,
    processRunning: Boolean,
    portOpen: Boolean,
    notificationActive: Boolean,
    canDisplayNotification: Boolean,
    notificationManuallyHidden: Boolean = false
): NormalRunningReconcileAction {
    if (portOpen) {
        if (notificationManuallyHidden) {
            return NormalRunningReconcileAction.RespectUserDismissal
        }
        return if (!serviceRunning || (canDisplayNotification && !notificationActive)) {
            NormalRunningReconcileAction.RestoreForeground
        } else {
            NormalRunningReconcileAction.Healthy
        }
    }
    if (consecutiveUnreachableCount < NORMAL_RUNNING_UNREACHABLE_THRESHOLD) {
        return NormalRunningReconcileAction.WaitForNextProbe
    }
    return if (!serviceRunning && !processRunning) {
        NormalRunningReconcileAction.MarkStopped
    } else {
        NormalRunningReconcileAction.MarkError
    }
}
