package com.example.danmuapiapp.desktop.logs

import com.example.danmuapiapp.desktop.runtime.DesktopPaths
import com.example.danmuapiapp.desktop.runtime.HealthReadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DesktopLogTest {
    private val source = DesktopLogSource("core", "核心业务", java.io.File("core.log"), "核心")

    private fun entry(
        raw: String,
        sourceId: String = "core",
        sourceLabel: String = "核心业务",
        level: String? = "INFO",
        tag: String? = "HTTP",
        sequence: Long = 0,
    ) = DesktopLogEntry(sourceId, sourceLabel, null, level, tag, raw, raw, sequence)

    @Test
    fun parsesTimestampLevelTagAndKeepsRawLine() {
        val parsed = DesktopLogLineParser.parse(
            source,
            "2026-08-29T12:34:56Z [HTTP] INFO: request completed",
            7,
        )
        assertEquals("2026-08-29T12:34:56Z", parsed.timestamp)
        assertEquals("INFO", parsed.level)
        assertEquals("HTTP", parsed.tag)
        assertEquals("2026-08-29T12:34:56Z [HTTP] INFO: request completed", parsed.rawLine)
        assertEquals("request completed", parsed.message)
        assertEquals(7L, parsed.sequence)
    }

    @Test
    fun filtersSearchLevelTagAndSourceWithAndSemantics() {
        val entries = listOf(
            entry("one", level = "INFO", tag = "HTTP"),
            entry("two error", level = "ERROR", tag = "HTTP"),
            entry("three", sourceId = "tray", sourceLabel = "托盘", level = "INFO", tag = "UI"),
        )
        assertEquals(listOf("two error"), filterLogEntries(entries, DesktopLogFilter(query = "ERROR", levels = setOf("ERROR"))).map { it.rawLine })
        assertEquals(listOf("three"), filterLogEntries(entries, DesktopLogFilter(tags = setOf("UI"), sourceIds = setOf("tray"))).map { it.rawLine })
        assertEquals(entries, filterLogEntries(entries, DesktopLogFilter()))
    }

    @Test
    fun formattingUsesOnlyProvidedCurrentResults() {
        val all = listOf(entry("all"), entry("match"), entry("other"))
        val filtered = filterLogEntries(all, DesktopLogFilter(query = "match"))
        assertEquals("match", formatLogEntriesForExport(filtered))
        assertEquals("", formatLogEntriesForExport(emptyList()))
    }

    @Test
    fun mapsLogLevelsToTerminalColorSemantics() {
        assertEquals("error", com.example.danmuapiapp.desktop.app.logLevelColorName("ERROR"))
        assertEquals("warning", com.example.danmuapiapp.desktop.app.logLevelColorName("WARN"))
        assertEquals("normal", com.example.danmuapiapp.desktop.app.logLevelColorName("INFO"))
        assertEquals("debug", com.example.danmuapiapp.desktop.app.logLevelColorName("DEBUG"))
        assertEquals("unknown", com.example.danmuapiapp.desktop.app.logLevelColorName(null))
    }

    @Test
    fun readerReportsMissingEmptyInvalidUtf8AndTruncation() {
        val root = Files.createTempDirectory("danmu-log-test").toFile()
        val paths = DesktopPaths(root)
        val reader = DesktopLogReader(maxBytesPerSource = 4)
        val result = reader.read(paths, HealthReadState.Idle)
        assertTrue(result.sources.any { it.status == DesktopLogSourceStatus.Missing })

        val tray = java.io.File(paths.logsDir, "tray.log")
        tray.parentFile.mkdirs()
        tray.writeText("", Charsets.UTF_8)
        assertTrue(reader.read(paths).sources.any { it.status == DesktopLogSourceStatus.Empty })

        tray.writeBytes(byteArrayOf(0xC3.toByte(), 0x28))
        val invalid = reader.read(paths).sources.first { it.source.id == "tray" }
        assertTrue(invalid.status is DesktopLogSourceStatus.Failed)

        tray.writeText("one\ntwo\nthree\n", Charsets.UTF_8)
        val truncated = reader.read(paths).sources.first { it.source.id == "tray" }
        assertTrue(truncated.status is DesktopLogSourceStatus.Readable && (truncated.status as DesktopLogSourceStatus.Readable).truncated)
    }
}
