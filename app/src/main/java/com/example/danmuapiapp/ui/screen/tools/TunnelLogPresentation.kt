package com.example.danmuapiapp.ui.screen.tools

import com.example.danmuapiapp.data.util.SensitiveDataRedactor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal enum class TunnelLogLevel(val label: String) {
    Info("信息"), Warning("警告"), Error("错误"), Debug("调试"), Session("启动")
}

internal data class TunnelLogEntry(
    val timestamp: String,
    val level: TunnelLogLevel,
    val summary: String,
    val raw: String
) {
    val time: String get() = timestamp.substringAfter(' ', timestamp).substringBefore('.')
    val isIssue: Boolean get() = level == TunnelLogLevel.Warning || level == TunnelLogLevel.Error
}

private val terminalOsc = Regex("\u001B\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)")
private val terminalCsi = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")
// Some collected lines have already lost ESC, leaving visible '[0m' / '[1;34m' fragments.
private val orphanColors = Regex("\\[(?:0|1|2|3|4|7|8|9|22|23|24|27|28|29|3[0-9]|4[0-9]|9[0-7]|10[0-7])(?:;[0-9]+)*m")
private val datedLog = Regex("^(\\d{4}[-/]\\d{2}[-/]\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?)\\s+\\[([A-Za-z]+)]\\s*(.*)$")
private val sourceLocation = Regex("^\\[[^]\\n]+\\.go:\\d+]\\s*")
private val runId = Regex("^\\[[a-fA-F0-9]{8,}]\\s*")
private val proxyPrefix = Regex("^\\[([^]\\n]+)]\\s*(.+)$")
private val sessionMarker = Regex("^---\\s*frpc start\\s+(\\d+)\\s*---$")

internal fun cleanTunnelLog(raw: String): String = SensitiveDataRedactor.redact(
    orphanColors.replace(terminalCsi.replace(terminalOsc.replace(raw, ""), ""), "")
        .replace("\r\n", "\n").replace('\r', '\n')
        .filter { it == '\n' || it == '\t' || !it.isISOControl() }
).trim()

/** Presentation only: keep the supervisor's raw log and link-state parsing unchanged. */
internal fun parseTunnelLog(raw: String, timeZone: TimeZone = TimeZone.getDefault()): List<TunnelLogEntry> {
    val entries = mutableListOf<TunnelLogEntry>()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).apply { this.timeZone = timeZone }
    cleanTunnelLog(raw).lineSequence().forEach { line ->
        if (line.isBlank()) return@forEach
        val trimmed = line.trim()
        val session = sessionMarker.matchEntire(trimmed)
        if (session != null) {
            val timestamp = session.groupValues[1].toLongOrNull()?.let { dateFormat.format(Date(it)) }.orEmpty()
            entries += TunnelLogEntry(timestamp, TunnelLogLevel.Session, "frpc 启动", trimmed)
            return@forEach
        }
        val match = datedLog.matchEntire(trimmed)
        if (match != null) {
            val level = when (match.groupValues[2].uppercase(Locale.ROOT)) {
                "W", "WARN", "WARNING" -> TunnelLogLevel.Warning
                "E", "ERROR", "F", "FATAL" -> TunnelLogLevel.Error
                "D", "DEBUG", "T", "TRACE" -> TunnelLogLevel.Debug
                else -> TunnelLogLevel.Info
            }
            val message = runId.replaceFirst(sourceLocation.replaceFirst(match.groupValues[3], ""), "")
            entries += TunnelLogEntry(match.groupValues[1].replace('T', ' '), level, summarizeTunnelMessage(message), trimmed)
        } else if (line.first().isWhitespace() && entries.isNotEmpty()) {
            // Stack traces and wrapped continuation lines belong to the previous record.
            val previous = entries.last()
            entries[entries.lastIndex] = previous.copy(raw = previous.raw + "\n" + line)
        } else {
            entries += TunnelLogEntry("", TunnelLogLevel.Info, trimmed, trimmed)
        }
    }
    return entries
}

private fun summarizeTunnelMessage(message: String): String {
    val proxy = proxyPrefix.matchEntire(message)
    val body = proxy?.groupValues?.get(2) ?: message
    val name = proxy?.groupValues?.get(1)
    val lower = body.lowercase(Locale.ROOT)
    return when {
        lower.startsWith("start frpc service for config file") -> "正在加载 frpc 配置"
        lower == "try to connect to server..." || lower == "try to connect to server" -> "正在连接服务器"
        lower.startsWith("login to server success") -> "服务器登录成功"
        lower == "start proxy success" -> if (name != null) "隧道已建立 · $name" else "隧道已建立"
        lower.startsWith("proxy added:") -> "已加载隧道 · " + body.substringAfter(':').trim().removeSurrounding("[", "]")
        // Error messages retain their reason, port, and other diagnostic information.
        lower.startsWith("login to server failed") -> "服务器登录失败" + body.substring("login to server failed".length)
        lower.startsWith("connect to server error") -> "连接服务器失败" + body.substring("connect to server error".length)
        else -> message
    }
}
