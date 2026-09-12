package com.example.danmuapiapp.ui.screen.localdanmu

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun formatLocalDanmuSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb -> String.format(Locale.getDefault(), "%.2f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.getDefault(), "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.getDefault(), "%.0f KB", bytes / kb)
        else -> "$bytes B"
    }
}

internal fun formatLocalDanmuTime(raw: String): String {
    if (raw.isBlank()) return "时间未知"
    return runCatching {
        val time = OffsetDateTime.parse(raw)
            .atZoneSameInstant(ZoneId.systemDefault())
        time.format(DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.getDefault()))
    }.getOrElse { raw.take(16) }
}
