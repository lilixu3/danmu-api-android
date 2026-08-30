package com.example.danmuapiapp.desktop.app

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

const val DEFAULT_API_TOKEN = "87654321"

fun buildApiAddress(
    host: String,
    port: Int,
    token: String?,
    scheme: String = "http",
): String {
    require(host.isNotBlank()) { "API 地址 host 不能为空" }
    require(port in 1..65_535) { "API 地址端口无效：$port" }
    val normalizedToken = token?.trim().takeUnless { it.isNullOrEmpty() } ?: DEFAULT_API_TOKEN
    val encodedToken = URLEncoder.encode(normalizedToken, StandardCharsets.UTF_8).replace("+", "%20")
    val authority = if (host.contains(':') && !host.startsWith('[')) "[$host]" else host
    return "$scheme://$authority:$port/$encodedToken"
}

fun effectiveUptimeSeconds(
    snapshotUptimeSeconds: Long?,
    snapshotReceivedAtMs: Long,
    nowMs: Long,
): Long? {
    if (snapshotUptimeSeconds == null || snapshotUptimeSeconds < 0L) return null
    val elapsedSeconds = ((nowMs - snapshotReceivedAtMs).coerceAtLeast(0L)) / 1_000L
    return snapshotUptimeSeconds + elapsedSeconds
}
