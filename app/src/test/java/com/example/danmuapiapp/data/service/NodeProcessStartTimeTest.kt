package com.example.danmuapiapp.data.service

import org.junit.Assert.*
import org.junit.Test

class NodeProcessStartTimeTest {
    private fun stat(ticks: String): String =
        "123 (node (worker) process) " + (listOf("S") + List(18) { "0" } + ticks + "0").joinToString("  ")

    @Test
    fun `stat parser returns start instant and not process age`() {
        val startedAt = parseNodeProcessStartedElapsedMs(stat("359900"), 100L)!!
        assertEquals(3_599_000L, startedAt)
        assertFalse(hasNodeProcessExceededMinUptime(3_600_000L, startedAt, 10_000L))
    }

    @Test
    fun `ten second grace period includes full interval on long running device`() {
        val startedAt = 30L * 24 * 60 * 60 * 1000
        assertFalse(hasNodeProcessExceededMinUptime(startedAt + 9999, startedAt, 10_000))
        assertTrue(hasNodeProcessExceededMinUptime(startedAt + 10_000, startedAt, 10_000))
    }

    @Test
    fun `invalid timestamps or clock frequencies do not produce old process age`() {
        assertNull(parseNodeProcessStartedElapsedMs("invalid", 100))
        assertNull(parseNodeProcessStartedElapsedMs(stat("bad"), 100))
        assertNull(parseNodeProcessStartedElapsedMs(stat("-1"), 100))
        assertNull(parseNodeProcessStartedElapsedMs(stat("100"), 0))
        assertNull(parseNodeProcessStartedElapsedMs(stat("100"), -1))
        assertFalse(hasNodeProcessExceededMinUptime(100, 200, 10))
    }

    @Test
    fun `fractional ticks and large values are converted without multiplying total ticks`() {
        assertEquals(1250L, parseNodeProcessStartedElapsedMs(stat("125"), 100))
        assertEquals(9_000_000_000_000_000L, parseNodeProcessStartedElapsedMs(stat("900000000000000"), 100))
    }
}
