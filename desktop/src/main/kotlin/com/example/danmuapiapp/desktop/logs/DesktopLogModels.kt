package com.example.danmuapiapp.desktop.logs

import java.io.File
import java.time.Instant

/** Viewport state for a polling terminal: preserve history position unless already at latest. */
data class LogViewportState(
    val isAtLatest: Boolean,
    val pendingNewCount: Int,
)

fun updateLogViewport(
    previousTotal: Int,
    currentTotal: Int,
    wasAtLatest: Boolean,
): LogViewportState {
    require(previousTotal >= 0) { "previousTotal 不能为负数" }
    require(currentTotal >= 0) { "currentTotal 不能为负数" }
    return if (wasAtLatest) {
        LogViewportState(isAtLatest = true, pendingNewCount = 0)
    } else {
        LogViewportState(
            isAtLatest = false,
            pendingNewCount = (currentTotal - previousTotal).coerceAtLeast(0),
        )
    }
}

fun displayLogTimestamp(timestamp: String?): String {
    val value = timestamp?.trim().orEmpty()
    if (value.length >= 19 && value[4] == '-' && value[7] == '-' && value[10] in setOf('T', ' ')) {
        return value.substring(11, 19)
    }
    return "--:--:--"
}

/** A single immutable line shown by the desktop log viewer. */
data class DesktopLogEntry(
    val sourceId: String,
    val sourceLabel: String,
    val timestamp: String?,
    val level: String?,
    val tag: String?,
    val message: String,
    val rawLine: String,
    val sequence: Long,
)

data class DesktopLogFilter(
    val query: String = "",
    val levels: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val sourceIds: Set<String> = emptySet(),
)

data class DesktopLogSource(
    val id: String,
    val label: String,
    val file: File,
    val description: String,
)

sealed interface DesktopLogSourceStatus {
    data object Missing : DesktopLogSourceStatus
    data object Empty : DesktopLogSourceStatus
    data class Readable(
        val lineCount: Int,
        val truncated: Boolean,
    ) : DesktopLogSourceStatus
    data class Failed(val reason: String, val cause: Throwable? = null) : DesktopLogSourceStatus
}

data class DesktopLogSourceResult(
    val source: DesktopLogSource,
    val status: DesktopLogSourceStatus,
    val entries: List<DesktopLogEntry>,
)

data class DesktopLogReadResult(
    val entries: List<DesktopLogEntry>,
    val sources: List<DesktopLogSourceResult>,
    val readAt: Instant,
    val diagnostics: List<String> = emptyList(),
)

fun filterLogEntries(
    entries: List<DesktopLogEntry>,
    filter: DesktopLogFilter,
): List<DesktopLogEntry> {
    val query = filter.query.trim().lowercase()
    return entries.filter { entry ->
        val searchable = buildString {
            append(entry.rawLine)
            append('\n')
            append(entry.message)
            append('\n')
            append(entry.sourceLabel)
            append('\n')
            append(entry.level.orEmpty())
            append('\n')
            append(entry.tag.orEmpty())
        }.lowercase()
        val queryMatches = query.isEmpty() || searchable.contains(query)
        val levelMatches = filter.levels.isEmpty() || entry.level?.let { it in filter.levels } == true
        val tagMatches = filter.tags.isEmpty() || entry.tag?.let { it in filter.tags } == true
        val sourceMatches = filter.sourceIds.isEmpty() || entry.sourceId in filter.sourceIds
        queryMatches && levelMatches && tagMatches && sourceMatches
    }
}

fun formatLogEntriesForExport(entries: List<DesktopLogEntry>): String =
    entries.joinToString(System.lineSeparator()) { it.rawLine }

fun DesktopLogFilter.isEmpty(): Boolean =
    query.isBlank() && levels.isEmpty() && tags.isEmpty() && sourceIds.isEmpty()
