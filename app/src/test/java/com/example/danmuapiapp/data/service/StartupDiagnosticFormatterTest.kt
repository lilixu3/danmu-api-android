package com.example.danmuapiapp.data.service

import org.junit.Assert.assertEquals
import org.junit.Test

class StartupDiagnosticFormatterTest {

    @Test
    fun returns_primary_when_tail_is_blank() {
        val detail = StartupDiagnosticFormatter.mergeBootDetail(
            primary = "Node 进程异常退出，退出码：1",
            tail = "   ",
            recentLogLabel = "普通模式启动日志"
        )

        assertEquals("Node 进程异常退出，退出码：1", detail)
    }

    @Test
    fun appends_recent_boot_log_when_tail_exists() {
        val detail = StartupDiagnosticFormatter.mergeBootDetail(
            primary = "普通模式启动超时：服务进程仍在但端口未就绪",
            tail = "2026-04-14 12:00:00 [ERROR] Error: missing module",
            recentLogLabel = "普通模式启动日志"
        )

        assertEquals(
            "普通模式启动超时：服务进程仍在但端口未就绪\n最近普通模式启动日志：\n2026-04-14 12:00:00 [ERROR] Error: missing module",
            detail
        )
    }

    @Test
    fun avoids_duplicate_recent_log_suffix() {
        val existing = "Node 进程异常退出，退出码：1\n最近普通模式启动日志：\nboom"

        val detail = StartupDiagnosticFormatter.mergeBootDetail(
            primary = existing,
            tail = "boom",
            recentLogLabel = "普通模式启动日志"
        )

        assertEquals(existing, detail)
    }
}
