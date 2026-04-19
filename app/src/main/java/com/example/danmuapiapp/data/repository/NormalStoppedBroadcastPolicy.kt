package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.ServiceStatus

internal enum class NormalStoppedBroadcastAction {
    CompleteRestartWait,
    IgnoreAsStale,
    MarkStopped
}

internal fun decideNormalStoppedBroadcastAction(
    runMode: RunMode,
    status: ServiceStatus,
    pendingNormalRestart: Boolean,
    normalStartIssuedAtMs: Long
): NormalStoppedBroadcastAction {
    if (runMode != RunMode.Normal) {
        return NormalStoppedBroadcastAction.MarkStopped
    }
    if (pendingNormalRestart && status == ServiceStatus.Stopping) {
        return NormalStoppedBroadcastAction.CompleteRestartWait
    }
    if (status == ServiceStatus.Starting && normalStartIssuedAtMs > 0L) {
        return NormalStoppedBroadcastAction.IgnoreAsStale
    }
    return NormalStoppedBroadcastAction.MarkStopped
}
