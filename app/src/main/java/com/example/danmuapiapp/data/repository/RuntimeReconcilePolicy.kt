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
    return status == ServiceStatus.Starting || status == ServiceStatus.Stopping
}
