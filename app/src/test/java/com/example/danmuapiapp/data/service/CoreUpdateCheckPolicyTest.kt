package com.example.danmuapiapp.data.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreUpdateCheckPolicyTest {
    @Test
    fun defaultsToTenMinutesForUnknownValues() {
        assertEquals(10, CoreUpdateCheckPolicy.normalizeIntervalMinutes(0))
        assertEquals(10, CoreUpdateCheckPolicy.normalizeIntervalMinutes(17))
        assertEquals(30, CoreUpdateCheckPolicy.normalizeIntervalMinutes(30))
    }

    @Test
    fun checksOnlyAfterConfiguredCooldown() {
        val last = 1_000_000L
        assertFalse(
            CoreUpdateCheckPolicy.shouldCheck(last + 9 * 60_000L, last, 10)
        )
        assertTrue(
            CoreUpdateCheckPolicy.shouldCheck(last + 10 * 60_000L, last, 10)
        )
    }

    @Test
    fun firstRunAndClockRollbackAreDueImmediately() {
        assertTrue(CoreUpdateCheckPolicy.shouldCheck(100L, 0L, 10))
        assertTrue(CoreUpdateCheckPolicy.shouldCheck(100L, 200L, 10))
    }
}
