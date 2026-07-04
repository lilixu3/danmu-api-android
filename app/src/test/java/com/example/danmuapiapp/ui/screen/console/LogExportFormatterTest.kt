package com.example.danmuapiapp.ui.screen.console

import com.example.danmuapiapp.domain.model.AppLogSource
import com.example.danmuapiapp.domain.model.LogEntry
import com.example.danmuapiapp.domain.model.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogExportFormatterTest {

    @Test
    fun `export text includes metadata and full timestamped log lines`() {
        val logs = listOf(
            LogEntry(
                timestamp = 1_700_000_000_000L,
                level = LogLevel.Info,
                message = "[TMDB] 共获取到 1 条搜索结果",
                source = AppLogSource.Core,
                category = "tmdb"
            )
        )

        val text = LogExportFormatter.buildExportText(
            logs = logs,
            scopeLabel = "当前筛选结果",
            generatedAtMs = 1_700_000_060_000L,
            timeZone = java.util.TimeZone.getTimeZone("UTC"),
            versionName = "1.0.5.47",
            runModeLabel = "普通",
            statusLabel = "运行中",
            port = 9321
        )

        assertTrue(text.contains("弹幕 App 日志导出"))
        assertTrue(text.contains("日志范围: 当前筛选结果"))
        assertTrue(text.contains("日志条数: 1"))
        assertTrue(text.contains("版本: 1.0.5.47"))
        assertTrue(text.contains("运行模式: 普通"))
        assertTrue(text.contains("端口: 9321"))
        assertTrue(text.contains("[2023-11-14 22:13:20][Info][核心][tmdb] [TMDB] 共获取到 1 条搜索结果"))
    }

    @Test
    fun `export redacts tokens api keys authorization cookies and admin token`() {
        val raw = "api_key=abcdef&token=secret Authorization: Bearer aaa.bbb Cookie: sid=123 ADMIN_TOKEN=my-admin"

        val redacted = LogExportFormatter.redactSensitiveText(raw)

        assertFalse(redacted.contains("abcdef"))
        assertFalse(redacted.contains("secret"))
        assertFalse(redacted.contains("aaa.bbb"))
        assertFalse(redacted.contains("sid=123"))
        assertFalse(redacted.contains("my-admin"))
        assertTrue(redacted.contains("api_key=****"))
        assertTrue(redacted.contains("token=****"))
        assertTrue(redacted.contains("Authorization: Bearer ****"))
        assertTrue(redacted.contains("Cookie: ****"))
        assertTrue(redacted.contains("ADMIN_TOKEN=****"))
    }

    @Test
    fun `default export file name is stable and txt`() {
        assertEquals(
            "danmu-api-logs-20231114-221320.txt",
            LogExportFormatter.defaultFileName(
                nowMs = 1_700_000_000_000L,
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            )
        )
    }
}
