package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.RunMode

/**
 * Keeps Root-only side effects behind explicit Root mode boundaries.
 *
 * Switching to / staying in Normal mode must not touch Magisk/APatch/KernelSU module
 * files or keep a persistent `su` session alive. Those operations are reserved for
 * Root mode or explicit Root module actions.
 */
internal fun shouldSyncRootAutoStartModeFlag(
    targetMode: RunMode,
    rootAutoStartEnabled: Boolean
): Boolean {
    return rootAutoStartEnabled && targetMode.requiresRoot
}

internal fun shouldCloseRootShellSessionForMode(mode: RunMode): Boolean {
    return !mode.requiresRoot
}
