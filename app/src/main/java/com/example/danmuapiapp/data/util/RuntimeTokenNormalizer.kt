package com.example.danmuapiapp.data.util

/**
 * Normalizes user/runtime TOKEN inputs before they are persisted or used as URL path prefixes.
 *
 * Text backups and sync payloads may contain placeholder values such as "null" or "undefined".
 * They are not valid runtime tokens and must not become "/null" or "/undefined" paths.
 */
object RuntimeTokenNormalizer {
    fun normalizeInput(raw: String?): String {
        val value = raw.orEmpty().trim()
        return if (isNullLike(value)) "" else value
    }

    private fun isNullLike(raw: String?): Boolean {
        val value = raw.orEmpty().trim()
        return value.equals("null", ignoreCase = true) ||
            value.equals("undefined", ignoreCase = true)
    }
}
