package com.example.danmuapiapp.data.util

import java.net.URLDecoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ParseUtils {

    fun parseTimestamp(raw: String, fallback: Long = System.currentTimeMillis()): Long {
        if (raw.isBlank()) return fallback
        return runCatching {
            Instant.parse(raw).toEpochMilli()
        }.getOrElse {
            runCatching {
                val local = LocalDateTime.parse(
                    raw,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                )
                local.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrDefault(fallback)
        }
    }

    fun decodeUtf8(raw: String): String {
        if (raw.isBlank()) return ""
        return runCatching {
            URLDecoder.decode(raw, Charsets.UTF_8.name())
        }.getOrDefault(raw)
    }
}
