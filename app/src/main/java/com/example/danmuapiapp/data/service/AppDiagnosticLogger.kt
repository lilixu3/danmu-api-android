package com.example.danmuapiapp.data.service

import android.content.Context
import android.util.Log
import com.example.danmuapiapp.domain.model.AppLogSource
import com.example.danmuapiapp.domain.model.LogEntry
import com.example.danmuapiapp.domain.model.LogLevel
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

object AppDiagnosticLogger {

    private data class FileSpec(
        val fileName: String,
        val maxBytes: Long,
        val backupCount: Int
    )

    private const val LOG_DIR_NAME = "app-logs"
    private const val APP_THROWABLE_MAX_CHARS = 12_000
    private const val ROOT_TAIL_MAX_CHARS = 600
    private const val ROOT_TAIL_MAX_LINES = 12

    private val appLogSpec = FileSpec(
        fileName = "app.log",
        maxBytes = 512L * 1024L,
        backupCount = 2
    )
    private val rootBootstrapSpec = FileSpec(
        fileName = "root-bootstrap.log",
        maxBytes = 128L * 1024L,
        backupCount = 1
    )
    private val localTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val uncaughtHandlerInstalled = AtomicBoolean(false)
    private val writeLock = Any()

    fun installGlobalExceptionHandler(context: Context) {
        if (!uncaughtHandlerInstalled.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                e(
                    context = appContext,
                    tag = "UncaughtException",
                    message = "线程 ${thread.name} 发生未捕获异常",
                    throwable = throwable
                )
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                thread.threadGroup?.uncaughtException(thread, throwable)
            }
        }
    }

    fun i(context: Context, tag: String, message: String) {
        log(context, LogLevel.Info, tag, message, null)
    }

    fun w(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        log(context, LogLevel.Warn, tag, message, throwable)
    }

    fun e(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        log(context, LogLevel.Error, tag, message, throwable)
    }

    fun readAppEntries(context: Context, maxEntries: Int): List<LogEntry> {
        return readStructuredEntries(context, appLogSpec, maxEntries)
    }

    fun readRootBootstrapEntries(context: Context, maxEntries: Int): List<LogEntry> {
        val files = rollingFilesInReadOrder(context, rootBootstrapSpec)
        if (files.isEmpty()) return emptyList()
        val out = ArrayList<LogEntry>()
        files.forEach { file ->
            val fileTimestamp = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
            runCatching { file.readLines(Charsets.UTF_8) }
                .getOrDefault(emptyList())
                .forEachIndexed { index, raw ->
                    val trimmed = raw.trim()
                    if (trimmed.isBlank()) return@forEachIndexed
                    out += parseRootBootstrapLine(trimmed, fileTimestamp + index)
                }
        }
        return out.takeLast(maxEntries.coerceAtLeast(1))
    }

    fun prepareRootBootstrapLog(context: Context): File {
        synchronized(writeLock) {
            val baseFile = logFile(context, rootBootstrapSpec)
            val parent = baseFile.parentFile
            runCatching { parent?.mkdirs() }
            resetRollingFile(baseFile, rootBootstrapSpec)
            runCatching {
                baseFile.writeText("", Charsets.UTF_8)
            }
            val now = formatLocalTimestamp(System.currentTimeMillis())
            appendRootBootstrapLine(
                context,
                "$now [INFO] 准备捕获 Root 启动日志"
            )
            return baseFile
        }
    }

    fun appendRootBootstrapLine(context: Context, line: String) {
        val normalized = line.trimEnd()
        if (normalized.isBlank()) return
        appendRawLine(context, rootBootstrapSpec, normalized + "\n")
    }

    fun readRootBootstrapTail(
        context: Context,
        maxLines: Int = ROOT_TAIL_MAX_LINES,
        maxChars: Int = ROOT_TAIL_MAX_CHARS
    ): String {
        val entries = readRootBootstrapEntries(context, maxLines.coerceAtLeast(1))
        if (entries.isEmpty()) return ""
        return entries.joinToString("\n") { entry ->
            buildString {
                append(entry.message)
                if (entry.tag.isNotBlank()) {
                    append(" [")
                    append(entry.tag)
                    append("]")
                }
            }
        }.takeLast(maxChars.coerceAtLeast(80))
    }

    fun clearAppLogs(context: Context) {
        clearRollingFiles(context, appLogSpec)
    }

    fun clearRootBootstrapLogs(context: Context) {
        clearRollingFiles(context, rootBootstrapSpec)
    }

    fun clearAll(context: Context) {
        clearAppLogs(context)
        clearRootBootstrapLogs(context)
    }

    private fun log(
        context: Context,
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable?
    ) {
        val appContext = context.applicationContext
        writeToLogcat(level, tag, message, throwable)
        runCatching {
            val now = System.currentTimeMillis()
            val payload = JSONObject().apply {
                put("ts", now)
                put("level", level.name)
                put("source", AppLogSource.App.name)
                put("tag", tag)
                put("message", message)
                if (throwable != null) {
                    put(
                        "throwable",
                        Log.getStackTraceString(throwable).take(APP_THROWABLE_MAX_CHARS)
                    )
                }
            }
            appendRawLine(appContext, appLogSpec, payload.toString() + "\n")
        }
    }

    private fun writeToLogcat(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        when (level) {
            LogLevel.Info -> {
                if (throwable != null) Log.i(tag, message, throwable) else Log.i(tag, message)
            }

            LogLevel.Warn -> {
                if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
            }

            LogLevel.Error -> {
                if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
            }
        }
    }

    private fun appendRawLine(context: Context, spec: FileSpec, line: String) {
        synchronized(writeLock) {
            val file = logFile(context, spec)
            val parent = file.parentFile
            runCatching { parent?.mkdirs() }
            val bytes = line.toByteArray(Charsets.UTF_8)
            val currentSize = file.length().coerceAtLeast(0L)
            if (currentSize > 0L && currentSize + bytes.size > spec.maxBytes) {
                rotateRollingFile(file, spec)
            }
            runCatching {
                FileOutputStream(file, true).use { stream ->
                    stream.write(bytes)
                    stream.flush()
                }
            }
        }
    }

    private fun clearRollingFiles(context: Context, spec: FileSpec) {
        synchronized(writeLock) {
            rollingFilesInReadOrder(context, spec).forEach { file ->
                runCatching { file.delete() }
            }
        }
    }

    private fun readStructuredEntries(context: Context, spec: FileSpec, maxEntries: Int): List<LogEntry> {
        val files = rollingFilesInReadOrder(context, spec)
        if (files.isEmpty()) return emptyList()
        val out = ArrayList<LogEntry>()
        files.forEach { file ->
            runCatching { file.readLines(Charsets.UTF_8) }
                .getOrDefault(emptyList())
                .forEach { raw ->
                    val trimmed = raw.trim()
                    if (trimmed.isBlank()) return@forEach
                    val entry = runCatching { JSONObject(trimmed) }.getOrNull()
                        ?.let(::mapStructuredEntry)
                        ?: return@forEach
                    out += entry
                }
        }
        return out.takeLast(maxEntries.coerceAtLeast(1))
    }

    private fun mapStructuredEntry(obj: JSONObject): LogEntry {
        val timestamp = obj.optLong("ts").takeIf { it > 0L } ?: System.currentTimeMillis()
        val level = parseLevel(obj.optString("level"))
        val source = parseSource(obj.optString("source"))
        val tag = obj.optString("tag").trim()
        val message = buildString {
            append(obj.optString("message").trim())
            val throwableText = obj.optString("throwable").trim()
            if (throwableText.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append(throwableText)
            }
        }.ifBlank { obj.toString() }

        return LogEntry(
            timestamp = timestamp,
            level = level,
            message = message,
            source = source,
            tag = tag
        )
    }

    private fun parseRootBootstrapLine(line: String, fallbackTimestamp: Long): LogEntry {
        val structuredRegex =
            Regex("""^\[?(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})]?\s+\[([A-Z]+)]\s*(.*)$""")
        val match = structuredRegex.find(line)
        if (match != null) {
            val timestamp = parseLocalTimestamp(match.groupValues[1]).takeIf { it > 0L }
                ?: fallbackTimestamp
            val level = parseLevel(match.groupValues[2])
            return LogEntry(
                timestamp = timestamp,
                level = level,
                message = match.groupValues[3].ifBlank { line },
                source = AppLogSource.RootBootstrap,
                tag = "RootBootstrap"
            )
        }

        val lowered = line.lowercase()
        val level = when {
            "error" in lowered || "failed" in lowered || "exception" in lowered -> LogLevel.Error
            "warn" in lowered -> LogLevel.Warn
            else -> LogLevel.Info
        }
        return LogEntry(
            timestamp = fallbackTimestamp,
            level = level,
            message = line,
            source = AppLogSource.RootBootstrap,
            tag = "RootBootstrap"
        )
    }

    private fun parseLocalTimestamp(raw: String): Long {
        return runCatching {
            LocalDateTime.parse(raw, localTimeFormatter)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(0L)
    }

    private fun parseLevel(raw: String): LogLevel {
        return when (raw.trim().uppercase()) {
            "ERROR" -> LogLevel.Error
            "WARN", "WARNING" -> LogLevel.Warn
            else -> LogLevel.Info
        }
    }

    private fun parseSource(raw: String): AppLogSource {
        return runCatching { AppLogSource.valueOf(raw.trim()) }.getOrDefault(AppLogSource.App)
    }

    private fun resetRollingFile(file: File, spec: FileSpec) {
        val rotated = rotateRollingFile(file, spec)
        if (!rotated) {
            runCatching {
                if (file.exists()) {
                    file.writeText("", Charsets.UTF_8)
                }
            }
        }
    }

    private fun rotateRollingFile(file: File, spec: FileSpec): Boolean {
        if (!file.exists()) return false
        if (spec.backupCount <= 0) {
            runCatching { file.delete() }
            return true
        }
        val oldest = File("${file.absolutePath}.${spec.backupCount}")
        runCatching {
            if (oldest.exists()) oldest.delete()
        }
        for (index in spec.backupCount - 1 downTo 1) {
            val src = File("${file.absolutePath}.$index")
            val dst = File("${file.absolutePath}.${index + 1}")
            if (src.exists()) {
                runCatching { src.renameTo(dst) }
            }
        }
        val firstBackup = File("${file.absolutePath}.1")
        return runCatching { file.renameTo(firstBackup) }.getOrDefault(false)
    }

    private fun rollingFilesInReadOrder(context: Context, spec: FileSpec): List<File> {
        val base = logFile(context, spec)
        return buildList {
            for (index in spec.backupCount downTo 1) {
                val backup = File("${base.absolutePath}.$index")
                if (backup.exists() && backup.isFile) add(backup)
            }
            if (base.exists() && base.isFile) add(base)
        }
    }

    private fun logFile(context: Context, spec: FileSpec): File {
        val dir = File(context.noBackupFilesDir, LOG_DIR_NAME)
        return File(dir, spec.fileName)
    }

    private fun formatLocalTimestamp(timestamp: Long): String {
        return runCatching {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
                .format(localTimeFormatter)
        }.getOrElse { localTimeFormatter.format(LocalDateTime.now()) }
    }
}
