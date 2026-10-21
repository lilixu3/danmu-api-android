package com.example.danmuapiapp.ui.screen.localdanmu

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun formatLocalDanmuTime(raw: String): String {
    if (raw.isBlank()) return "时间未知"
    return runCatching {
        val time = OffsetDateTime.parse(raw)
            .atZoneSameInstant(ZoneId.systemDefault())
        time.format(DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.getDefault()))
    }.getOrElse { raw.take(16) }
}
