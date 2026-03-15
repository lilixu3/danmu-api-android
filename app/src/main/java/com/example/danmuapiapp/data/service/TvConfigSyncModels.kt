package com.example.danmuapiapp.data.service

import kotlinx.serialization.Serializable

@Serializable
data class TvConfigSyncPayload(
    val version: Int = 1,
    val sourceDeviceName: String = "",
    val sentAtEpochMs: Long = 0L,
    val envContent: String = "",
    val runtime: TvConfigSyncRuntime = TvConfigSyncRuntime(),
    val settings: TvConfigSyncSettings = TvConfigSyncSettings()
)

@Serializable
data class TvConfigSyncRuntime(
    val port: Int = 9321,
    val token: String = "",
    val variantKey: String = "stable"
)

@Serializable
data class TvConfigSyncSettings(
    val githubProxy: String = "original",
    val githubToken: String = "",
    val customRepo: String = "",
    val customRepoDisplayName: String = ""
)

@Serializable
data class TvConfigSyncResponse(
    val ok: Boolean = false,
    val message: String = ""
)

data class TvConfigSyncTarget(
    val applyUrl: String,
    val deviceName: String = ""
)
