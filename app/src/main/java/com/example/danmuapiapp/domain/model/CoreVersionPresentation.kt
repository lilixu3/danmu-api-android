package com.example.danmuapiapp.domain.model

private val versionLikeRegex = Regex("""^[vV]?\d[0-9A-Za-z._@-]*$""")

fun formatCoreVersionValue(value: String?): String {
    val trimmed = value?.trim().orEmpty()
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
