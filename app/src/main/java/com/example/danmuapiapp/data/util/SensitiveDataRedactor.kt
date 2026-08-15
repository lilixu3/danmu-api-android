package com.example.danmuapiapp.data.util

object SensitiveDataRedactor {
    private const val REDACTION = "****"

    private val keyedSecret = Regex(
        """(?i)((?<![A-Za-z0-9_])["']?(?:api[_-]?key|admin[_-]?token|token|password|secret|cookie)["']?\s*[:=]\s*)("[^"]*"|'[^']*'|[^&\s,;]+)"""
    )
    private val authorization = Regex("(?i)(Authorization\\s*:\\s*(?:Bearer|token)\\s+)[^\\s]+")
    private val githubTokens = Regex("(?i)\\b(?:gh[pousr]_[A-Za-z0-9_]{16,}|github_pat_[A-Za-z0-9_]{16,})\\b")
    private val runtimeUrlPathSecret = Regex(
        """((?:https?://)?(?:\[[^]]+]|[A-Za-z0-9.-]+):\d{1,5}/)([^/?#\s]+)"""
    )
    private val longUrlPathSecret = Regex(
        """(https?://(?:\[[^]]+]|[^/\s]+)/)([A-Za-z0-9_-]{6,})(?=/?(?:[?#\s]|$))"""
    )
    private val bracketedIpv6 = Regex("\\[[0-9a-fA-F:]+(?:%[A-Za-z0-9._-]+)?]")
    private val ipv4 = Regex("(?<![A-Za-z0-9])(?:\\d{1,3}\\.){3}\\d{1,3}(?![A-Za-z0-9])")

    fun redact(text: String, redactNetworkAddresses: Boolean = false): String {
        var output = text
        output = keyedSecret.replace(output) { match ->
            val rawValue = match.groupValues[2]
            val redactedValue = when {
                rawValue.length >= 2 && rawValue.first() == '"' && rawValue.last() == '"' -> "\"$REDACTION\""
                rawValue.length >= 2 && rawValue.first() == '\'' && rawValue.last() == '\'' -> "'$REDACTION'"
                else -> REDACTION
            }
            match.groupValues[1] + redactedValue
        }
        output = authorization.replace(output) { match -> match.groupValues[1] + REDACTION }
        output = githubTokens.replace(output, REDACTION)
        output = runtimeUrlPathSecret.replace(output) { match -> match.groupValues[1] + REDACTION }
        output = longUrlPathSecret.replace(output) { match -> match.groupValues[1] + REDACTION }
        if (redactNetworkAddresses) {
            output = bracketedIpv6.replace(output, "[<IP>]")
            output = ipv4.replace(output, "<IP>")
        }
        return output
    }
}
