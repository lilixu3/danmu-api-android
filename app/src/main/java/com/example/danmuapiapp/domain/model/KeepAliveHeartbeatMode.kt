package com.example.danmuapiapp.domain.model

enum class KeepAliveHeartbeatMode(
    val key: String,
    val label: String
) {
    Accessibility("a11y", "无障碍心跳"),
    System("system", "系统定时心跳（实验）");

    companion object {
        fun fromKey(raw: String?): KeepAliveHeartbeatMode {
            return entries.firstOrNull { it.key == raw?.trim()?.lowercase() }
                ?: Accessibility
        }
    }
}
