package com.example.danmuapiapp.ui.screen.tools

import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class TunnelLogPresentationTest {
    @Test fun `ANSI color codes are removed without losing log content`() {
        val text = "\u001B[1;34m2026-09-27 12:50:21.181 [I] [sub/root.go:149] start frpc service for config file [/app/frpc.toml]\u001B[0m"
        val entry = parseTunnelLog(text).single()
        assertEquals("12:50:21", entry.time)
        assertEquals("正在加载 frpc 配置", entry.summary)
        assertTrue(entry.raw.contains("[sub/root.go:149]"))
        assertTrue(entry.raw.contains("/app/frpc.toml"))
        assertFalse(entry.raw.contains('\u001B'))
        assertFalse(entry.raw.contains("[0m"))
    }

    @Test fun `orphaned color fragments from screenshot are removed`() {
        val text = "[1;34m2026-09-27 12:50:21.275 [I] [client/service.go:332] [133bc3e944a9bfaa] login to server success, get run id [133bc3e944a9bfaa]\n[0m[1;34m2026-09-27 12:50:21.310 [I] [client/control.go:174] [133bc3e944a9bfaa] [my-proxy] start proxy success\n[0m"
        val entries = parseTunnelLog(text)
        assertEquals(2, entries.size)
        assertEquals("服务器登录成功", entries[0].summary)
        assertEquals("隧道已建立 · my-proxy", entries[1].summary)
        assertFalse(entries.any { it.raw.contains("[1;34m") || it.raw.contains("[0m") })
    }

    @Test fun `errors retain diagnostic reasons and are filterable`() {
        val entry = parseTunnelLog("2026-09-27 12:50:22.100 [E] [client/service.go:295] [133bc3e944a9bfaa] login to server failed: dial tcp 127.0.0.1:7000: connection refused").single()
        assertEquals(TunnelLogLevel.Error, entry.level)
        assertTrue(entry.isIssue)
        assertTrue(entry.summary.contains("服务器登录失败"))
        assertTrue(entry.summary.contains("127.0.0.1:7000: connection refused"))
    }

    @Test fun `warnings debug and unknown messages keep their content`() {
        val entries = parseTunnelLog("2026/09/27 12:50:22 [W] [client/service.go:99] unusual warning\n2026-09-27T12:50:23 [D] unusual debug\ncustom supervisor message")
        assertEquals(3, entries.size)
        assertTrue(entries[0].isIssue)
        assertEquals("unusual warning", entries[0].summary)
        assertEquals(TunnelLogLevel.Debug, entries[1].level)
        assertFalse(entries[1].isIssue)
        assertEquals("custom supervisor message", entries[2].raw)
    }

    @Test fun `indented stack traces stay with their parent event`() {
        val entries = parseTunnelLog("2026-09-27 12:50:22 [E] operation failed\n    at example.go:12\n    at example.go:34\n2026-09-27 12:50:23 [I] retrying")
        assertEquals(2, entries.size)
        assertTrue(entries.first().raw.contains("at example.go:12\n    at example.go:34"))
        assertEquals("operation failed", entries.first().summary)
    }

    @Test fun `startup marker has a readable time instead of epoch milliseconds`() {
        val entry = parseTunnelLog("--- frpc start 0 ---", TimeZone.getTimeZone("UTC")).single()
        assertEquals(TunnelLogLevel.Session, entry.level)
        assertEquals("1970-01-01 00:00:00", entry.timestamp)
        assertEquals("frpc 启动", entry.summary)
    }

    @Test fun `empty and color-only logs do not create empty rows`() {
        assertTrue(parseTunnelLog("\n[0m\n\u001B[0m\n\r\n").isEmpty())
        assertTrue(parseTunnelLog("").isEmpty())
    }

    @Test fun `ordinary brackets and proxy names are not mistaken for ANSI codes`() {
        val entry = parseTunnelLog("2026-09-27 12:50:21 [I] [proxy/proxy_manager.go:177] [133bc3e944a9bfaa] proxy added: [movie-api]").single()
        assertEquals("已加载隧道 · movie-api", entry.summary)
        assertTrue(entry.raw.contains("[movie-api]"))
        assertEquals("payload [123] [ok]", cleanTunnelLog("payload [123] [ok]"))
    }

    @Test fun `terminal hyperlinks are stripped but visible text remains`() {
        assertEquals("open example", cleanTunnelLog("open \u001B]8;;https://example.com\u0007example\u001B]8;;\u0007"))
    }

    @Test fun `copied details use the same secret redaction as app logs`() {
        val entry = parseTunnelLog("2026-09-27 12:50:21 [E] authentication failed: token=example-secret").single()
        assertFalse(entry.raw.contains("example-secret"))
        assertFalse(entry.summary.contains("example-secret"))
        assertTrue(entry.raw.contains("token=****"))
    }
}
