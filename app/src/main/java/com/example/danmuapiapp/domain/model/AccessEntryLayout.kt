package com.example.danmuapiapp.domain.model

enum class AccessEntryLayout(val storageValue: String, val label: String) {
    Expanded("expanded", "平铺"),
    Tabs("tabs", "标签");

    companion object {
        fun fromStorageValue(value: String?) = entries.firstOrNull { it.storageValue == value } ?: Tabs
    }
}

/** 偏好始终保留；地址暂不可用时只调整当前展示，不覆盖用户设置。 */
enum class AccessEntryTab(val storageValue: String, val label: String) {
    Tunnel("tunnel", "穿透"),
    Ipv4("ipv4", "IPv4"),
    Ipv6("ipv6", "IPv6"),
    Local("local", "本机");

    companion object {
        fun fromStorageValue(value: String?) = entries.firstOrNull { it.storageValue == value } ?: Ipv4
        fun resolve(requested: AccessEntryTab, available: Set<AccessEntryTab>): AccessEntryTab =
            requested.takeIf { it in available } ?: Ipv4.takeIf { it in available } ?: Local
    }
}
