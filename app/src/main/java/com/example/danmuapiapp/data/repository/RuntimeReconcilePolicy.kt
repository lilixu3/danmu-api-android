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

/** 通知查询连续缺失该次数后才触发重发，过滤单次查询抖动（MIUI 折叠/排序竞态等假阴性）。 */
internal const val NORMAL_NOTIFICATION_MISS_CONFIRM_THRESHOLD = 2

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
    consecutiveNotificationMisses: Int = 0,
    notificationManuallyHidden: Boolean = false
): NormalRunningReconcileAction {
    if (portOpen) {
        if (!serviceRunning) {
            // 通知仍挂在通知栏说明前台服务健在（服务消亡时系统会自动撤掉其前台通知），
            // 此时 getRunningServices 的单次抖动不可信，绝不能重发通知。
            if (notificationActive) {
                return NormalRunningReconcileAction.Healthy
            }
            return NormalRunningReconcileAction.RestoreForeground
        }
        if (notificationManuallyHidden) {
            return NormalRunningReconcileAction.RespectUserDismissal
        }
        return if (canDisplayNotification && !notificationActive) {
            if (consecutiveNotificationMisses >= NORMAL_NOTIFICATION_MISS_CONFIRM_THRESHOLD) {
                NormalRunningReconcileAction.RestoreForeground
            } else {
                NormalRunningReconcileAction.Healthy
            }
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
