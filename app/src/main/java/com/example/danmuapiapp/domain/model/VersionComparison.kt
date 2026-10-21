package com.example.danmuapiapp.domain.model

/**
 * Compares two semantic version strings without depending on Android or UI code.
 *
 * Numeric core components are compared in order. A release without a pre-release
 * suffix sorts after the same version with one, and numeric pre-release identifiers
 * sort before non-numeric identifiers, matching SemVer precedence rules.
 */
fun compareVersions(left: String, right: String): Int {
    if (left == right) return 0

    val leftParts = parseVersionParts(left)
    val rightParts = parseVersionParts(right)

    val coreLength = maxOf(leftParts.core.size, rightParts.core.size)
    for (index in 0 until coreLength) {
        val leftValue = leftParts.core.getOrNull(index) ?: 0
        val rightValue = rightParts.core.getOrNull(index) ?: 0
        if (leftValue != rightValue) return leftValue - rightValue
    }

    val leftPreRelease = leftParts.preRelease
    val rightPreRelease = rightParts.preRelease
    if (leftPreRelease.isEmpty() && rightPreRelease.isEmpty()) return 0
    if (leftPreRelease.isEmpty()) return 1
    if (rightPreRelease.isEmpty()) return -1

    val preReleaseLength = maxOf(leftPreRelease.size, rightPreRelease.size)
    for (index in 0 until preReleaseLength) {
        val leftValue = leftPreRelease.getOrNull(index)
        val rightValue = rightPreRelease.getOrNull(index)
        if (leftValue == rightValue) continue
        if (leftValue == null) return -1
        if (rightValue == null) return 1

        val leftNumber = leftValue.toIntOrNull()
        val rightNumber = rightValue.toIntOrNull()
        if (leftNumber != null && rightNumber != null) {
            if (leftNumber != rightNumber) return leftNumber - rightNumber
            continue
        }
        if (leftNumber != null) return -1
        if (rightNumber != null) return 1

        val lexical = leftValue.compareTo(rightValue)
        if (lexical != 0) return lexical
    }

    return 0
}

private data class VersionParts(
    val core: List<Int>,
    val preRelease: List<String>
)

private fun parseVersionParts(version: String): VersionParts {
    val cleaned = version.trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore('+')
    if (cleaned.isBlank()) {
        return VersionParts(core = listOf(0), preRelease = emptyList())
    }

    val main = cleaned.substringBefore('-')
    val preRelease = cleaned.substringAfter('-', "")
        .takeIf { it.isNotBlank() }

    val core = main.split('.')
        .filter { it.isNotBlank() }
        .map { token ->
            token.toIntOrNull()
                ?: token.takeWhile { character -> character.isDigit() }.toIntOrNull()
                ?: 0
        }
        .ifEmpty { listOf(0) }

    val preReleaseParts = preRelease?.split('.')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()

    return VersionParts(core = core, preRelease = preReleaseParts)
}
