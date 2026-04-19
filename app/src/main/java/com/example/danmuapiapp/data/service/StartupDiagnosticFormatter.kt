package com.example.danmuapiapp.data.service

object StartupDiagnosticFormatter {

    fun mergeBootDetail(
        primary: String,
        tail: String,
        recentLogLabel: String
    ): String {
        val normalizedPrimary = primary.trim().ifBlank { "未知错误" }
        val normalizedTail = tail.trim()
        if (normalizedTail.isBlank()) return normalizedPrimary

        val header = "最近$recentLogLabel："
        if (normalizedPrimary.contains(header) && normalizedPrimary.contains(normalizedTail)) {
            return normalizedPrimary
        }

        return "$normalizedPrimary\n$header\n$normalizedTail"
    }
}
