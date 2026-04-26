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
    return when {
        latestText == "--" -> currentText
        currentText == "--" -> latestText
        else -> "$currentText → $latestText"
    }
}
