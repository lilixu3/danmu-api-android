package com.example.danmuapiapp.domain.model

private val versionLikeRegex = Regex("""^[vV]?\d[0-9A-Za-z._@-]*$""")
private val commitOnlyRegex = Regex("""^[0-9a-fA-F]{7,40}$""")

private fun normalizeCoreVersionDisplayValue(value: String?): String {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isBlank()) return ""

    val beforeAt = trimmed.substringBefore('@').trim()
    val afterAt = trimmed.substringAfter('@', "").trim()
    return when {
        beforeAt.isBlank() -> trimmed
        afterAt.isNotBlank() && commitOnlyRegex.matches(afterAt) -> beforeAt
        else -> trimmed
    }
}

private fun shortCoreCommitSuffix(value: String?): String {
    val commit = value
        ?.trim()
        ?.substringAfter('@', "")
        ?.trim()
        .orEmpty()
    return commit.takeIf { commitOnlyRegex.matches(it) }?.take(7).orEmpty()
}

private fun sameCommit(left: String, right: String): Boolean {
    if (left.isBlank() || right.isBlank()) return false
    return left.equals(right, ignoreCase = true) ||
        left.startsWith(right, ignoreCase = true) ||
        right.startsWith(left, ignoreCase = true)
}

private fun appendCommitWhenNeeded(displayValue: String, commit: String): String {
    if (displayValue == "--" || commit.isBlank()) return displayValue
    return "$displayValue@$commit"
}

fun formatCoreVersionValue(value: String?): String {
    val trimmed = normalizeCoreVersionDisplayValue(value)
    if (trimmed.isBlank()) return "--"
    return if (versionLikeRegex.matches(trimmed) && !trimmed.startsWith("v", ignoreCase = true)) {
        "v$trimmed"
    } else {
        trimmed
    }
}

fun formatCoreVersionTransition(currentVersion: String?, latestVersion: String?): String {
    val currentText = formatCoreVersionValue(currentVersion)
    val latestText = formatCoreVersionValue(latestVersion)
    val currentCommit = shortCoreCommitSuffix(currentVersion)
    val latestCommit = shortCoreCommitSuffix(latestVersion)
    val latestWithCommit = appendCommitWhenNeeded(latestText, latestCommit)
    return when {
        latestText == "--" -> currentText
        currentText == "--" -> latestWithCommit
        currentText == latestText && latestCommit.isNotBlank() && !sameCommit(currentCommit, latestCommit) ->
            "$currentText → $latestWithCommit"
        currentText == latestText -> currentText
        else -> "$currentText → $latestText"
    }
}
