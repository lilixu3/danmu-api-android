package com.example.danmuapiapp.domain.model

enum class RuntimeListenMode(
    val key: String,
    val label: String,
    val bindHost: String
) {
    Ipv4Only("ipv4", "仅 IPv4", "0.0.0.0"),
    DualStack("dual_stack", "IPv4 + IPv6", "::");

    companion object {
        const val PREFERENCE_KEY = "listen_mode"
        const val ENV_KEY = "DANMU_API_HOST"

        fun fromKey(raw: String?): RuntimeListenMode? {
            val value = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.key == value }
        }

        fun fromBindHost(raw: String?): RuntimeListenMode? {
            val value = raw
                ?.trim()
                ?.removePrefix("[")
                ?.removeSuffix("]")
                ?.lowercase()
                .orEmpty()
            return when (value) {
                "0.0.0.0" -> Ipv4Only
                "::" -> DualStack
                else -> null
            }
        }
    }
}
