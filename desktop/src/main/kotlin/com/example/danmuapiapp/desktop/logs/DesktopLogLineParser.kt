package com.example.danmuapiapp.desktop.logs

import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

object DesktopLogLineParser {
    private val levelRegex = Regex("\\b(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL)\\b", RegexOption.IGNORE_CASE)
    private val timestampRegex = Regex(
        "^(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{1,9})?(?:Z|[+-]\\d{2}:?\\d{2})?)\\s*",
    )
    private val bracketTagRegex = Regex("^\\[([^]]+)]\\s*")
    private val levelPrefixRegex = Regex("^(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL)\\s*[:|-]?\\s*", RegexOption.IGNORE_CASE)

    fun parse(source: DesktopLogSource, rawLine: String, sequence: Long): DesktopLogEntry {
        var remaining = rawLine.trimStart()
        var timestamp: String? = null
        timestampRegex.find(remaining)?.let { match ->
            val candidate = match.groupValues[1]
            if (isTimestamp(candidate)) {
                timestamp = candidate
                remaining = remaining.removeRange(match.range).trimStart()
            }
        }

        var tag: String? = null
        bracketTagRegex.find(remaining)?.let { match ->
            val candidate = match.groupValues[1].trim()
            if (!levelRegex.matches(candidate)) {
                tag = candidate
                remaining = remaining.removeRange(match.range).trimStart()
            }
        }

        val level = levelRegex.find(remaining)?.groupValues?.firstOrNull()?.uppercase()
        levelPrefixRegex.find(remaining)?.let { match ->
            remaining = remaining.removeRange(match.range).trimStart()
        }
        val message = remaining.ifEmpty { rawLine }
        return DesktopLogEntry(
            sourceId = source.id,
            sourceLabel = source.label,
            timestamp = timestamp,
            level = level,
            tag = tag,
            message = message,
            rawLine = rawLine,
            sequence = sequence,
        )
    }

    private fun isTimestamp(value: String): Boolean = try {
        OffsetDateTime.parse(value.replace(',', '.'))
        true
    } catch (_: DateTimeParseException) {
        value.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{1,9})?"))
    }
}
