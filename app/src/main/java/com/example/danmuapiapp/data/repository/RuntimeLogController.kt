package com.example.danmuapiapp.data.repository

import android.content.Context
import com.example.danmuapiapp.data.service.AppDiagnosticLogger
import com.example.danmuapiapp.data.util.RuntimeApiUrls
import com.example.danmuapiapp.data.util.RuntimeManagementPaths
import com.example.danmuapiapp.data.util.RuntimeTokenNormalizer
import com.example.danmuapiapp.domain.model.AppLogSource
import com.example.danmuapiapp.domain.model.CoreLogNoiseFilter
import com.example.danmuapiapp.domain.model.LogEntry
import com.example.danmuapiapp.domain.model.LogLevel
import com.example.danmuapiapp.domain.model.LogTagClassifier
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.repository.AdminSessionRepository
import com.example.danmuapiapp.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 运行时日志：核心日志抓取、App 日志聚合、清空与多格式解析。
 *
 * Repository 只提供当前运行状态、端口探活与设置里的日志开关/上限，
 * 日志来源、缓存与解析规则全部收敛在这里。
 */
internal class RuntimeLogController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val adminSessionRepository: AdminSessionRepository,
    private val currentState: () -> RuntimeState,
    private val isPortOpen: (Int) -> Boolean
) {

    private companion object {
        const val LOG_HTTP_TIMEOUT_MS = 900
        const val LOG_CLEAR_HTTP_TIMEOUT_MS = 1200
        const val MIN_LOG_LIMIT = 100

        val ISO_BRACKET_LOG_REGEX =
            Regex("""^\[?(\d{4}-\d{2}-\d{2}T[^\]]+)\]?\s+\[([A-Z]+)\]\s*(.*)$""")
        val LOCAL_BRACKET_LOG_REGEX =
            Regex("""^\[?(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})\]?\s+\[([A-Z]+)\]\s*(.*)$""")
        val ISO_COLON_LOG_REGEX =
            Regex("""^\[?(\d{4}-\d{2}-\d{2}T[^\]]+)\]?\s+([A-Za-z]+):\s*(.*)$""")
        val LOCAL_COLON_LOG_REGEX =
            Regex("""^\[?(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})\]?\s+([A-Za-z]+):\s*(.*)$""")
        val SERVICE_LOG_REGEX =
            Regex("""^\[([^\]]+)]\s+([A-Za-z]+):\s*(.*)$""")
        val STACK_TRACE_LINE_REGEX =
            Regex("""^(?:at\s+\S+|\.\.\. \d+ more|Caused by:|Suppressed:).*$""")
        val TIMESTAMP_TAG_REGEX =
            Regex("""^\d{4}-\d{2}-\d{2}(?:[ T]\d{2}:\d{2}:\d{2}.*)?$""")
    }

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()
    private val _eventLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    private var serviceLogsCache: List<LogEntry> = emptyList()
    private var appFileLogsCache: List<LogEntry> = emptyList()
    @Volatile
    private var clearedServiceLogCounts: Map<LogEntry, Int> = emptyMap()

    fun refresh() {
        scope.launch {
            refreshOnce()
        }
    }

    fun clear() {
        clearedServiceLogCounts = serviceLogsCache.groupingBy { it }.eachCount()
        serviceLogsCache = emptyList()
        appFileLogsCache = emptyList()
        _eventLogs.value = emptyList()
        _logs.value = emptyList()
        AppDiagnosticLogger.clearAll(context)
        scope.launch {
            clearServiceLogsOnce()
            refreshOnce()
        }
    }

    fun addLog(level: LogLevel, message: String) {
        val tagInfo = LogTagClassifier.classifyAppEntry(AppLogSource.App, "Runtime")
        _eventLogs.update { current ->
            (current + LogEntry(
                level = level,
                message = message,
                source = AppLogSource.App,
                tag = "Runtime",
                category = tagInfo.category,
                tags = tagInfo.tags
            )).takeLast(settingsRepository.logMaxCount.value)
        }
        publishMergedLogs()
    }

    private fun publishMergedLogs() {
        val merged = (serviceLogsCache + appFileLogsCache + _eventLogs.value)
            .sortedBy { it.timestamp }
            .takeLast(settingsRepository.logMaxCount.value)
        _logs.value = merged
    }

    private fun refreshOnce() {
        if (!settingsRepository.logEnabled.value) return
        runCatching {
            val serviceLogs = filterClearedServiceLogs(collectServiceLogsOnce())
            val appLogs = collectAppLogsOnce()
            val shouldPublish =
                serviceLogs != serviceLogsCache ||
                    appLogs != appFileLogsCache ||
                    _eventLogs.value.isNotEmpty() ||
                    _logs.value.isNotEmpty()
            serviceLogsCache = serviceLogs
            appFileLogsCache = appLogs
            if (shouldPublish) {
                publishMergedLogs()
            }
        }.onFailure {
            if (_eventLogs.value.isNotEmpty() && _logs.value != _eventLogs.value) {
                publishMergedLogs()
            }
        }
    }

    private fun collectAppLogsOnce(): List<LogEntry> {
        val limit = settingsRepository.logMaxCount.value.coerceAtLeast(MIN_LOG_LIMIT)
        return (AppDiagnosticLogger.readAppEntries(context, limit) +
            AppDiagnosticLogger.readRootBootstrapEntries(context, limit) +
            AppDiagnosticLogger.readNormalBootstrapEntries(context, limit))
            .sortedBy { it.timestamp }
            .takeLast(limit)
    }

    private fun collectServiceLogsOnce(): List<LogEntry> {
        val state = currentState()
        if (state.status != ServiceStatus.Running && !isPortOpen(state.port)) return emptyList()

        val baseUrl = resolveRuntimeTokenApiBaseUrl() ?: return emptyList()
        val raw = runCatching {
            val conn = (URL("$baseUrl/api/logs").openConnection() as HttpURLConnection).apply {
                connectTimeout = LOG_HTTP_TIMEOUT_MS
                readTimeout = LOG_HTTP_TIMEOUT_MS
                requestMethod = "GET"
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching ""
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
            } finally {
                conn.disconnect()
            }
        }.getOrElse { "" }
        return if (raw.isNotBlank()) parseLogLines(raw) else emptyList()
    }

    private fun resolveRuntimeTokenApiBaseUrl(): String? {
        val state = currentState()
        val tokenPath = RuntimeTokenNormalizer.normalizeInput(state.token)
            .trim('/')
            .takeIf { it.isNotBlank() }
            ?: return null
        return RuntimeApiUrls.local(state.port, tokenPath)
    }

    private fun clearServiceLogsOnce(): Boolean {
        val state = currentState()
        if (state.status != ServiceStatus.Running && !isPortOpen(state.port)) return false

        val tokenPath = RuntimeManagementPaths.adminTokenPath(
            adminMode = adminSessionRepository.sessionState.value.isAdminMode,
            adminToken = adminSessionRepository.currentAdminTokenOrNull()
        ) ?: return false
        val url = RuntimeApiUrls.local(state.port, tokenPath, "api/logs/clear")
        val cleared = runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = LOG_CLEAR_HTTP_TIMEOUT_MS
                readTimeout = LOG_CLEAR_HTTP_TIMEOUT_MS
                requestMethod = "POST"
                doOutput = true
                setFixedLengthStreamingMode(0)
            }
            try {
                conn.outputStream.use { }
                conn.responseCode in 200..299
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
        if (cleared) {
            clearedServiceLogCounts = emptyMap()
            return true
        }
        return false
    }

    private fun filterClearedServiceLogs(entries: List<LogEntry>): List<LogEntry> {
        val baseline = clearedServiceLogCounts
        if (baseline.isEmpty() || entries.isEmpty()) return entries

        val remaining = baseline.toMutableMap()
        var visibleStartIndex = 0
        while (visibleStartIndex < entries.size) {
            val entry = entries[visibleStartIndex]
            val count = remaining[entry] ?: 0
            if (count <= 0) break
            if (count == 1) {
                remaining.remove(entry)
            } else {
                remaining[entry] = count - 1
            }
            visibleStartIndex++
        }
        return entries.drop(visibleStartIndex)
    }

    private fun parseLogLines(raw: String): List<LogEntry> {
        val blocks = CoreLogNoiseFilter.removeLogEndpointPollingBlocks(splitLogBlocks(raw))
        if (blocks.isEmpty()) return emptyList()

        return blocks.map { block ->
            val entry = parseStructuredLogLine(block)
            entry ?: run {
                val normalized = normalizeCoreMessage(block)
                val tagInfo = LogTagClassifier.classifyCoreMessage(normalized)
                LogEntry(
                    timestamp = System.currentTimeMillis(),
                    level = LogLevel.Info,
                    message = normalized,
                    source = AppLogSource.Core,
                    tag = tagInfo.category.ifBlank { "Core" },
                    category = tagInfo.category,
                    tags = tagInfo.tags
                )
            }
        }.takeLast(settingsRepository.logMaxCount.value)
    }

    private fun splitLogBlocks(raw: String): List<String> {
        val blocks = mutableListOf<String>()
        val current = StringBuilder()

        raw.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isBlank()) return@forEach

            if (isStructuredLogHeader(trimmed)) {
                if (current.isNotEmpty()) {
                    blocks += current.toString().trim()
                    current.setLength(0)
                }
                current.append(trimmed)
            } else {
                if (current.isNotEmpty()) current.append('\n')
                current.append(trimmed)
            }
        }

        if (current.isNotEmpty()) {
            blocks += current.toString().trim()
        }

        return blocks
    }

    private fun isStructuredLogHeader(line: String): Boolean {
        if (
            ISO_BRACKET_LOG_REGEX.matches(line) ||
            LOCAL_BRACKET_LOG_REGEX.matches(line) ||
            ISO_COLON_LOG_REGEX.matches(line) ||
            LOCAL_COLON_LOG_REGEX.matches(line)
        ) {
            return true
        }

        val serviceMatch = SERVICE_LOG_REGEX.find(line) ?: return false
        return !looksLikeTimestampTag(serviceMatch.groupValues[1].trim())
    }

    private fun parseStructuredLogLine(block: String): LogEntry? {
        val lines = block.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return null

        val header = parseStructuredLogHeader(lines.first()) ?: return null
        val message = buildString {
            if (header.message.isNotBlank()) {
                append(header.message.trim())
            }
            if (lines.size > 1) {
                if (isNotBlank()) append('\n')
                append(lines.drop(1).joinToString("\n"))
            }
        }.ifBlank { lines.first() }

        val normalizedMessage = normalizeCoreMessage(message)
        val tagInfo = LogTagClassifier.classifyCoreMessage(normalizedMessage, header.tag)
        return LogEntry(
            timestamp = header.timestamp,
            level = header.level,
            message = normalizedMessage,
            source = AppLogSource.Core,
            tag = tagInfo.category.ifBlank { header.tag },
            category = tagInfo.category,
            tags = tagInfo.tags
        )
    }

    private fun parseStructuredLogHeader(line: String): ParsedLogHeader? {
        ISO_BRACKET_LOG_REGEX.find(line)?.let { match ->
            return ParsedLogHeader(
                timestamp = parseIsoTimestamp(match.groupValues[1]),
                level = parseLogLevel(match.groupValues[2]),
                message = match.groupValues[3],
                tag = "Core"
            )
        }

        LOCAL_BRACKET_LOG_REGEX.find(line)?.let { match ->
            return ParsedLogHeader(
                timestamp = parseLocalTimestamp(match.groupValues[1]),
                level = parseLogLevel(match.groupValues[2]),
                message = match.groupValues[3],
                tag = "Core"
            )
        }

        ISO_COLON_LOG_REGEX.find(line)?.let { match ->
            return ParsedLogHeader(
                timestamp = parseIsoTimestamp(match.groupValues[1]),
                level = parseLogLevel(match.groupValues[2]),
                message = match.groupValues[3],
                tag = "Core"
            )
        }

        LOCAL_COLON_LOG_REGEX.find(line)?.let { match ->
            return ParsedLogHeader(
                timestamp = parseLocalTimestamp(match.groupValues[1]),
                level = parseLogLevel(match.groupValues[2]),
                message = match.groupValues[3],
                tag = "Core"
            )
        }

        SERVICE_LOG_REGEX.find(line)?.let { match ->
            val tag = match.groupValues[1].trim()
            if (looksLikeTimestampTag(tag)) return null

            return ParsedLogHeader(
                timestamp = System.currentTimeMillis(),
                level = parseLogLevel(match.groupValues[2]),
                message = match.groupValues[3],
                tag = tag
            )
        }

        return null
    }

    private fun parseIsoTimestamp(raw: String): Long {
        return runCatching { Instant.parse(raw).toEpochMilli() }
            .getOrElse { System.currentTimeMillis() }
    }

    private fun parseLocalTimestamp(raw: String): Long {
        return runCatching {
            val localDt = LocalDateTime.parse(
                raw,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            )
            localDt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrElse { System.currentTimeMillis() }
    }

    private fun parseLogLevel(raw: String): LogLevel {
        return when (raw.trim().uppercase()) {
            "ERROR" -> LogLevel.Error
            "WARN", "WARNING" -> LogLevel.Warn
            else -> LogLevel.Info
        }
    }

    private fun looksLikeTimestampTag(raw: String): Boolean {
        return TIMESTAMP_TAG_REGEX.matches(raw)
    }

    private fun normalizeCoreMessage(message: String): String {
        val lines = message.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.isEmpty()) return message.trim()
        if (lines.size == 1) return lines.first()
        if (lines.drop(1).any { STACK_TRACE_LINE_REGEX.matches(it) }) {
            return lines.joinToString("\n")
        }
        return lines.joinToString(" ")
            .replace(Regex("""\s+([,;:)\]}])"""), "$1")
            .replace(Regex("""([\[{(])\s+"""), "$1")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
    }

    private data class ParsedLogHeader(
        val timestamp: Long,
        val level: LogLevel,
        val message: String,
        val tag: String
    )
}
