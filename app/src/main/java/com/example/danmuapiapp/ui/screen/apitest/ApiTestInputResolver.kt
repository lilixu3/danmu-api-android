package com.example.danmuapiapp.ui.screen.apitest

internal object ApiTestInputResolver {
    private val httpUrlRegex = Regex("""https?://[^\s\"'<>]+""", RegexOption.IGNORE_CASE)
    private val trailingPunctuation = charArrayOf(',', '，', '。', '.', ')', ']', '}', '）', '】')

    fun extractHttpUrl(raw: String): String? {
        val match = httpUrlRegex.find(raw.trim()) ?: return null
        return match.value.trim().trimEnd(*trailingPunctuation).takeIf { it.startsWith("http", ignoreCase = true) }
    }
}
