package com.example.danmuapiapp.data.util

object SensitiveDataRedactor {
    private const val REDACTION = "****"

    private val keyedSecrets = listOf(
        Regex("(?i)((?:api[_-]?key|token|admin_token|password|secret|cookie)\\s*[:=]\\s*)[^&\\s,;]+"),
        Regex("(?i)(Authorization\\s*:\\s*(?:Bearer|token)\\s+)[^\\s]+")
    )
    private val githubTokens = Regex("(?i)\\b(?:gh[pousr]_[A-Za-z0-9_]{16,}|github_pat_[A-Za-z0-9_]{16,})\\b")
    private val urlPathSecret = Regex("(https?://(?:\\[[^]]+]|[^/\\s]+)/(?:[A-Za-z0-9_-]{6,})(?=/?(?:[?\\s]|$)))")
    private val bracketedIpv6 = Regex("\\[[0-9a-fA-F:]+(?:%[A-Za-z0-9._-]+)?]")
    private val ipv4 = Regex("(?<![A-Za-z0-9])(?:\\d{1,3}\\.){3}\\d{1,3}(?![A-Za-z0-9])")

    fun redact(text: String, redactNetworkAddresses: Boolean = false): String {
        var output = text
        keyedSecrets.forEach { pattern ->
            output = pattern.replace(output) { match -> match.groupValues[1] + REDACTION }
        }
        output = githubTokens.replace(output, REDACTION)
        output = urlPathSecret.replace(output) { match ->
            val value = match.value
            value.substringBeforeLast('/') + "/$REDACTION"
        }
        if (redactNetworkAddresses) {
            output = bracketedIpv6.replace(output, "[<IP>]")
            output = ipv4.replace(output, "<IP>")
        }
        return output
    }
}

