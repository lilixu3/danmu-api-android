package com.example.danmuapiapp.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeStartedAtRecoveryTest {

    @Test
    fun `普通模式可从 health uptime 反推出真实启动时间`() {
        val nowMs = 1_700_000_000_000L

        val startedAt = calculateStartedAtFromUptime(nowMs, uptimeSec = 7_200L)

        assertEquals(1_699_992_800_000L, startedAt)
    }

    @Test
    fun `无效 uptime 不应覆盖运行时间`() {
        val nowMs = 1_700_000_000_000L

        assertNull(calculateStartedAtFromUptime(nowMs, uptimeSec = null))
        assertNull(calculateStartedAtFromUptime(nowMs, uptimeSec = -1L))
    }

    @Test
    fun `health 响应应解析 uptimeSec 字段`() {
        val json = """{"ok":true,"uptimeSec":3661,"port":9321}"""

        assertEquals(3_661L, parseHealthUptimeSeconds(json))
    }
}
