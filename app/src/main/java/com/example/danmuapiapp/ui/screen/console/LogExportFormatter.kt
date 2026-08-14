package com.example.danmuapiapp.ui.screen.console

import com.example.danmuapiapp.domain.model.AppLogSource
import com.example.danmuapiapp.domain.model.LogEntry
import com.example.danmuapiapp.domain.model.LogLevel
import com.example.danmuapiapp.data.util.SensitiveDataRedactor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal object LogExportFormatter {
    fun defaultFileName(
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val formatter = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).apply {
            this.timeZone = timeZone
        }
        return "danmu-api-logs-${formatter.format(Date(nowMs))}.txt"
    }

    fun buildExportText(
        logs: List<LogEntry>,
        scopeLabel: String,
        generatedAtMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
        versionName: String,
        runModeLabel: String,
        statusLabel: String,
        port: Int
    ): String {
        val exportTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
            this.timeZone = timeZone
        }
        return buildString {
            appendLine("弹幕 App 日志导出")
            appendLine("导出时间: ${exportTimeFormat.format(Date(generatedAtMs))}")
            appendLine("日志范围: $scopeLabel")
            appendLine("日志条数: ${logs.size}")
            appendLine("服务状态: $statusLabel")
            appendLine("运行模式: $runModeLabel")
            appendLine("端口: $port")
            appendLine("版本: $versionName")
            appendLine()
            appendLine("============================================================")
            appendLine()
            appendLine(toLogLinesText(logs, fullTimestamp = true, timeZone = timeZone))
        }.trimEnd() + "\n"
    }

    fun toClipboardText(logs: List<LogEntry>): String {
        return toLogLinesText(logs, fullTimestamp = false)
    }

    fun toLogLinesText(
        logs: List<LogEntry>,
        fullTimestamp: Boolean,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val pattern = if (fullTimestamp) "yyyy-MM-dd HH:mm:ss" else "HH:mm:ss"
        val timeFormat = SimpleDateFormat(pattern, Locale.getDefault()).apply {
            this.timeZone = timeZone
        }
        return logs.joinToString("\n") { entry ->
            buildString {
                append('[')
                append(timeFormat.format(Date(entry.timestamp)))
                append(']')
                append('[')
                append(levelLabel(entry.level))
                append(']')
                append('[')
                append(entry.source.label)
                append(']')
                val category = entry.category.trim().ifBlank { entry.tag.trim() }
                if (category.isNotBlank()) {
                    append('[')
                    append(category)
                    append(']')
                }
                append(' ')
                append(redactSensitiveText(entry.message))
            }
        }
    }

    fun redactSensitiveText(text: String): String {
        return SensitiveDataRedactor.redact(text)
    }

    private fun levelLabel(level: LogLevel): String {
        return when (level) {
            LogLevel.Info -> "Info"
            LogLevel.Warn -> "Warn"
            LogLevel.Error -> "Error"
        }
    }
}
