package com.example.danmuapiapp.data.util

import java.net.URI
import java.util.Locale

/** Accept a device host or a complete HTTP endpoint without losing its scheme or query. */
fun resolvePushTargetAddress(raw: String): String? {
    val value = raw.trim()
    if (value.isBlank() || value.any { it.isWhitespace() || it.isISOControl() }) return null
    val hasScheme = value.contains("://")
    val uri = runCatching { URI(if (hasScheme) value else "http://$value") }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
    if (scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
    if (uri.port != -1 && uri.port !in 1..65535) return null
    if (uri.rawAuthority.endsWith(':') || uri.rawFragment != null) return null
    val authority = uri.rawAuthority + if (!hasScheme && uri.port == -1) ":9978" else ""
    val base = "$scheme://$authority"
    return if (uri.rawPath.orEmpty().trimEnd('/').isEmpty() && uri.rawQuery == null) {
        "$base/action?do=refresh&type=danmaku&path="
    } else {
        base + uri.rawPath.orEmpty() + (uri.rawQuery?.let { "?$it" } ?: "")
    }
}

/** Only the scheme and hostname are case insensitive; paths and escaped query values are not. */
fun pushTargetAddressKey(raw: String): String {
    val resolved = resolvePushTargetAddress(raw) ?: return raw.trim()
    val uri = URI(resolved)
    val scheme = uri.scheme.lowercase(Locale.ROOT)
    val port = when (uri.port) {
        -1 -> ""
        80 -> if (scheme == "http") "" else ":80"
        443 -> if (scheme == "https") "" else ":443"
        else -> ":${uri.port}"
    }
    return buildString {
        append(scheme).append("://")
        uri.rawUserInfo?.let { append(it).append('@') }
        append(uri.host.lowercase(Locale.ROOT)).append(port)
        append(uri.rawPath.orEmpty())
        uri.rawQuery?.let { append('?').append(it) }
    }
}
