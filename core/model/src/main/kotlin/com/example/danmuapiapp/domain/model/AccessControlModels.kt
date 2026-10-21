package com.example.danmuapiapp.domain.model

enum class DeviceAccessMode(val key: String, val label: String) {
    Off("off", "关闭"),
    Blacklist("blacklist", "黑名单");

    companion object {
        fun fromKey(raw: String?): DeviceAccessMode {
            return when (raw?.trim()?.lowercase()) {
                "blacklist", "black", "block", "deny" -> Blacklist
                else -> Off
            }
        }
    }
}

enum class DeviceAccessSource {
    AccessRecord,
    LanScan,
    BlacklistRule
}

data class DeviceAccessConfig(
    val mode: DeviceAccessMode = DeviceAccessMode.Off,
    val blacklist: List<String> = emptyList(),
    val updatedAtMs: Long = 0L
)

data class DeviceAccessDevice(
    val ip: String,
    val firstSeenAtMs: Long = 0L,
    val lastSeenAtMs: Long = 0L,
    val totalRequests: Int = 0,
    val allowedRequests: Int = 0,
    val blockedRequests: Int = 0,
    val lastMethod: String = "GET",
    val lastPath: String = "",
    val lastReason: String = "",
    val lastUserAgent: String = "",
    val inBlacklist: Boolean = false,
    val effectiveBlocked: Boolean = false,
    val source: DeviceAccessSource = DeviceAccessSource.AccessRecord
)

data class DeviceAccessSnapshot(
    val config: DeviceAccessConfig = DeviceAccessConfig(),
    val devices: List<DeviceAccessDevice> = emptyList(),
    val trackedDevices: Int = 0,
    val blacklistCount: Int = 0,
    val totalAllowedRequests: Long = 0L,
    val totalBlockedRequests: Long = 0L,
    val lanScannedCount: Int = 0,
    val lastLanScanAtMs: Long = 0L
)
