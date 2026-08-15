package com.example.danmuapiapp.ui.common

import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.model.ServiceStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

internal data class RuntimeRestartSnapshot(
    val runMode: RunMode,
    val pid: Int?,
    val uptimeSeconds: Long
)

internal data class RuntimeRestartObservation(
    val terminalStatus: ServiceStatus?,
    val sawProgress: Boolean
)

internal data class RuntimeRestartWaitResult(
    val status: ServiceStatus?,
    val sawProgress: Boolean
)

internal object RuntimeRestartEvidence {
    fun snapshot(state: RuntimeState): RuntimeRestartSnapshot = RuntimeRestartSnapshot(
        runMode = state.runMode,
        pid = state.pid,
        uptimeSeconds = state.uptimeSeconds
    )

    fun observe(
        state: RuntimeState,
        targetVariant: ApiVariant,
        beforeRestart: RuntimeRestartSnapshot,
        sawProgress: Boolean
    ): RuntimeRestartObservation = when (state.status) {
        ServiceStatus.Starting,
        ServiceStatus.Stopping,
        ServiceStatus.Stopped -> RuntimeRestartObservation(
            terminalStatus = null,
            sawProgress = true
        )

        ServiceStatus.Error -> RuntimeRestartObservation(
            terminalStatus = ServiceStatus.Error,
            sawProgress = sawProgress
        )

        ServiceStatus.Running -> RuntimeRestartObservation(
            terminalStatus = ServiceStatus.Running.takeIf {
                isConfirmedRunning(state, targetVariant, beforeRestart, sawProgress)
            },
            sawProgress = sawProgress
        )
    }

    fun isConfirmedRunning(
        state: RuntimeState,
        targetVariant: ApiVariant,
        beforeRestart: RuntimeRestartSnapshot,
        sawProgress: Boolean
    ): Boolean {
        if (state.status != ServiceStatus.Running || state.variant != targetVariant) return false
        if (sawProgress) return true
        if (state.runMode != beforeRestart.runMode) return false
        return when (state.runMode) {
            RunMode.Root -> {
                val pidChanged = state.pid != null &&
                    beforeRestart.pid != null &&
                    state.pid != beforeRestart.pid
                val uptimeReset = state.uptimeSeconds < beforeRestart.uptimeSeconds
                pidChanged || uptimeReset
            }

            RunMode.Normal -> state.uptimeSeconds < beforeRestart.uptimeSeconds
        }
    }
}

internal suspend fun StateFlow<RuntimeState>.awaitCoreRestart(
    targetVariant: ApiVariant,
    beforeRestart: RuntimeRestartSnapshot,
    timeoutMs: Long
): RuntimeRestartWaitResult {
    var sawProgress = false
    var terminalStatus: ServiceStatus? = null
    val completed = withTimeoutOrNull(timeoutMs) {
        first { state ->
            val observation = RuntimeRestartEvidence.observe(
                state = state,
                targetVariant = targetVariant,
                beforeRestart = beforeRestart,
                sawProgress = sawProgress
            )
            sawProgress = observation.sawProgress
            terminalStatus = observation.terminalStatus
            terminalStatus != null
        }
    }
    if (completed != null) {
        return RuntimeRestartWaitResult(terminalStatus, sawProgress)
    }

    val latest = value
    if (RuntimeRestartEvidence.isConfirmedRunning(
            state = latest,
            targetVariant = targetVariant,
            beforeRestart = beforeRestart,
            sawProgress = sawProgress
        )
    ) {
        return RuntimeRestartWaitResult(ServiceStatus.Running, sawProgress)
    }
    return RuntimeRestartWaitResult(
        status = latest.status.takeIf {
            it == ServiceStatus.Stopped || it == ServiceStatus.Error
        },
        sawProgress = sawProgress
    )
}
