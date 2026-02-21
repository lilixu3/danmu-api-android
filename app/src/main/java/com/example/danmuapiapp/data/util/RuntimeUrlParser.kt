package com.example.danmuapiapp.data.util

import java.net.URI
import java.util.Locale

/**
 * 从运行时 URL 中提取协议+主机+端口，忽略 token/path。
 */
object RuntimeUrlParser {

    fun extractBase(url: String): String {
        val raw = url.trim()
        if (raw.isBlank()) return ""

        val fromUri = runCatching {
            val uri = URI(raw)
            val host = uri.host?.trim().orEmpty()
            if (host.isBlank()) return@runCatching null

            val scheme = uri.scheme
                ?.trim()
                ?.ifBlank { "http" }
                ?.lowercase(Locale.ROOT)
                ?: "http"
            val normalizedHost = if (host.contains(':') && !host.startsWith("[")) {
                "[$host]"
            } else {
                host
            }
            val portPart = if (uri.port > 0) ":${uri.port}" else ""
            "$scheme://$normalizedHost$portPart"
        }.getOrNull()

        if (!fromUri.isNullOrBlank()) return fromUri

        val matched = Regex("^https?://[^/]+", RegexOption.IGNORE_CASE).find(raw)?.value
        return matched?.trimEnd('/').orEmpty()
    }
}
