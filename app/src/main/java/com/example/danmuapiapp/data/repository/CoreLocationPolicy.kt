package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.RunMode

internal enum class CorePresenceSource {
    NormalDir,
    RootDir
}

internal fun corePresenceSourceFor(mode: RunMode): CorePresenceSource {
    return when (mode) {
        RunMode.Normal -> CorePresenceSource.NormalDir
        RunMode.Root -> CorePresenceSource.RootDir
    }
}
