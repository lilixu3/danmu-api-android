package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.RunMode

/**
 * Small, testable policy layer for Root uptime anchoring.
 *
 * Root runtime liveness is intentionally conservative: a temporary port outage or a stale
 * repository state must not erase the user's visible uptime anchor until the runtime is
 * definitely gone.
 */
internal fun shouldClearRootStartedAtBeforeStart(rootProbablyRunning: Boolean): Boolean {
    return !rootProbablyRunning
}

internal const val ROOT_PASSIVE_LIVENESS_HINT_TTL_MS: Long = 7L * 24L * 60L * 60L * 1000L

internal fun shouldClearStartedAtOnError(
    runMode: RunMode,
    portOpen: Boolean,
    rootProbablyRunning: Boolean
): Boolean {
    return when (runMode) {
        RunMode.Normal -> !portOpen
        RunMode.Root -> !portOpen && !rootProbablyRunning
    }
}

internal fun isRootPassiveLivenessLikely(
    portOpen: Boolean,
    pidPresent: Boolean,
    startedAtMs: Long?,
    nowMs: Long
): Boolean {
    if (portOpen) return true
    if (!pidPresent) return false
    val startedAt = startedAtMs ?: return false
    if (startedAt <= 0L || startedAt > nowMs) return false
    return nowMs - startedAt <= ROOT_PASSIVE_LIVENESS_HINT_TTL_MS
}

internal fun selectRuntimeStartedAtAnchor(
    mode: RunMode,
    current: Long,
    forceNew: Boolean,
    normalHealthStartedAt: Long?,
    rootProcessStartedAt: Long?,
    rootPidFileModifiedAt: Long?,
    nowMs: Long
): Long {
    if (!forceNew && current > 0L) return current

    return when (mode) {
        RunMode.Normal -> {
            if (!forceNew) normalHealthStartedAt else null
        }
        RunMode.Root -> {
            rootProcessStartedAt ?: normalHealthStartedAt ?: if (!forceNew) rootPidFileModifiedAt else null
        }
    } ?: nowMs
}
