package com.example.danmuapiapp.ui.component

import java.net.URI

internal object MarkdownUriPolicy {
    fun canOpenLink(value: String): Boolean {
        val uri = parseAbsoluteUri(value) ?: return false
        return when (uri.scheme.lowercase()) {
            "http", "https" -> !uri.host.isNullOrBlank()
            "mailto" -> uri.schemeSpecificPart.isNotBlank()
            else -> false
        }
    }

    fun canLoadImage(value: String): Boolean {
        val uri = parseAbsoluteUri(value) ?: return false
        return uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    }

    private fun parseAbsoluteUri(value: String): URI? {
        val normalized = value.trim()
        if (normalized.isEmpty() || normalized.any { it.isISOControl() }) return null
        return (runCatching { URI(normalized) }.getOrNull() ?: run {
            val separator = normalized.indexOf(':')
            if (separator <= 0) return null
            runCatching {
                URI(
                    normalized.substring(0, separator),
                    normalized.substring(separator + 1),
                    null
                )
            }.getOrNull()
        })
            ?.takeIf(URI::isAbsolute)
    }
}
