package com.example.danmuapiapp.data.service

/**
 * Small fail-closed evaluator for the npm ranges used by the core's direct
 * dependencies. Resolution still happens in the signed pack CI; this helper
 * only verifies that an installed exact package version satisfies the source
 * declaration.
 */
internal object NpmVersionRange {
    private val preReleasePattern = Regex("(?:^|[\\s<>=~^|])v?\\d+\\.\\d+\\.\\d+-[0-9A-Za-z-]")

    private data class SemVer(
        val major: Int,
        val minor: Int,
        val patch: Int
    ) : Comparable<SemVer> {
        override fun compareTo(other: SemVer): Int =
            compareValuesBy(this, other, SemVer::major, SemVer::minor, SemVer::patch)
    }

    private data class ParsedVersion(
        val version: SemVer,
        val componentCount: Int,
        val wildcardIndex: Int? = null
    )

    fun isSatisfied(rawRange: String, rawVersion: String): Boolean {
        val normalizedVersion = rawVersion.trim()
        val range = rawRange.trim()
        if (preReleasePattern.containsMatchIn(normalizedVersion) || preReleasePattern.containsMatchIn(range)) {
            return false
        }
        val actual = parseVersion(normalizedVersion)?.version ?: return false
        if (range.isBlank() || range.startsWith("file:") || range.startsWith("git") ||
            range.startsWith("http:") || range.startsWith("https:") ||
            range.startsWith("workspace:") || range.startsWith("npm:")
        ) {
            return false
        }
        return range.split("||").any { branch ->
            evaluateBranch(branch.trim(), actual)
        }
    }

    private fun evaluateBranch(rawBranch: String, actual: SemVer): Boolean {
        if (rawBranch in setOf("*", "x", "X")) return true
        val hyphen = Regex("^\\s*([^\\s]+)\\s+-\\s+([^\\s]+)\\s*$").matchEntire(rawBranch)
        if (hyphen != null) {
            val lower = parseVersion(hyphen.groupValues[1])?.version ?: return false
            val upper = parseVersion(hyphen.groupValues[2])?.version ?: return false
            return actual >= lower && actual <= upper
        }
        val tokens = rawBranch
            .replace(',', ' ')
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return false
        return tokens.all { token -> evaluateToken(token, actual) }
    }

    private fun evaluateToken(token: String, actual: SemVer): Boolean {
        if (token in setOf("*", "x", "X")) return true
        if (token.startsWith('^')) {
            val base = parseVersion(token.drop(1))?.version ?: return false
            val upper = when {
                base.major > 0 -> SemVer(base.major + 1, 0, 0)
                base.minor > 0 -> SemVer(0, base.minor + 1, 0)
                else -> SemVer(0, 0, base.patch + 1)
            }
            return actual >= base && actual < upper
        }
        if (token.startsWith('~')) {
            val parsed = parseVersion(token.drop(1)) ?: return false
            val base = parsed.version
            val upper = if (parsed.componentCount <= 1) {
                SemVer(base.major + 1, 0, 0)
            } else {
                SemVer(base.major, base.minor + 1, 0)
            }
            return actual >= base && actual < upper
        }

        val comparator = Regex("^(>=|<=|>|<|=)?(.+)$").matchEntire(token) ?: return false
        val operator = comparator.groupValues[1]
        val parsed = parseVersion(comparator.groupValues[2]) ?: return false
        parsed.wildcardIndex?.let { wildcardIndex ->
            return when (wildcardIndex) {
                0 -> true
                1 -> actual.major == parsed.version.major
                2 -> actual.major == parsed.version.major && actual.minor == parsed.version.minor
                else -> false
            }
        }
        if (operator.isEmpty() && parsed.componentCount < 3) {
            return when (parsed.componentCount) {
                1 -> actual.major == parsed.version.major
                2 -> actual.major == parsed.version.major && actual.minor == parsed.version.minor
                else -> false
            }
        }
        return when (operator) {
            ">=" -> actual >= parsed.version
            "<=" -> actual <= parsed.version
            ">" -> actual > parsed.version
            "<" -> actual < parsed.version
            "", "=" -> actual == parsed.version
            else -> false
        }
    }

    private fun parseVersion(raw: String): ParsedVersion? {
        val value = raw.trim().removePrefix("v").substringBefore('+').substringBefore('-')
        if (value.isBlank()) return null
        val parts = value.split('.')
        if (parts.size > 3) return null
        val numbers = IntArray(3)
        var wildcardIndex: Int? = null
        for (index in parts.indices) {
            val part = parts[index]
            if (part == "x" || part == "X" || part == "*") {
                wildcardIndex = index
                break
            }
            numbers[index] = part.toIntOrNull()?.takeIf { it >= 0 } ?: return null
        }
        return ParsedVersion(
            version = SemVer(numbers[0], numbers[1], numbers[2]),
            componentCount = parts.size,
            wildcardIndex = wildcardIndex
        )
    }
}
