package com.example.danmuapiapp.data.service

object CoreVersionParser {
    private val sourceVersionRegexList = listOf(
        Regex("""(?m)\bVERSION\b\s*[:=]\s*['\"]([^'\"]+)['\"]"""),
        Regex("""(?m)\bversion\b\s*[:=]\s*['\"]([^'\"]+)['\"]""", RegexOption.IGNORE_CASE)
    )
    private val packageVersionRegex = Regex("(?m)\"version\"\\s*:\\s*\"([^\"]+)\"")

    fun extractSourceVersion(text: String): String? {
        for (regex in sourceVersionRegexList) {
            val version = regex.find(text)?.groupValues?.getOrNull(1)?.trim()
            if (!version.isNullOrBlank()) return version
        }
        return null
    }

    fun extractPackageVersion(text: String): String? {
        return packageVersionRegex.find(text)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun extractVersion(text: String): String? {
        return extractSourceVersion(text) ?: extractPackageVersion(text)
    }
}
