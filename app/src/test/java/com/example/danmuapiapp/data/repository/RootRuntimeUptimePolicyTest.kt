package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.RunMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootRuntimeUptimePolicyTest {

    @Test
    fun `Root should not stop on a single passive miss`() {
        assertFalse(
            shouldMarkRootStoppedAfterPassiveMiss(
                consecutiveMissCount = 1,
                passiveAliveHint = false
            )
        )
    }

    @Test
    fun `Root should stop after repeated passive misses`() {
        assertTrue(
            shouldMarkRootStoppedAfterPassiveMiss(
                consecutiveMissCount = ROOT_RECONCILE_STALE_MISS_THRESHOLD,
                passiveAliveHint = false
            )
        )
    }

    @Test
    fun `Root should reject zero passive misses`() {
        assertFalse(
            shouldMarkRootStoppedAfterPassiveMiss(
                consecutiveMissCount = 0,
                passiveAliveHint = false
            )
        )
    }

    @Test
    fun `Root should not stop when passive liveness still indicates alive`() {
        assertFalse(
            shouldMarkRootStoppedAfterPassiveMiss(
                consecutiveMissCount = ROOT_RECONCILE_STALE_MISS_THRESHOLD,
                passiveAliveHint = true
            )
        )
    }


    @Test
    fun `Root start should not clear existing uptime anchor when runtime may already be alive`() {
        assertFalse(shouldClearRootStartedAtBeforeStart(rootProbablyRunning = true))
    }

    @Test
    fun `Root start may clear uptime anchor only when runtime is definitely not alive`() {
        assertTrue(shouldClearRootStartedAtBeforeStart(rootProbablyRunning = false))
    }

    @Test
    fun `Root error should preserve uptime anchor when pid file still indicates runtime alive`() {
        assertFalse(
            shouldClearStartedAtOnError(
                runMode = RunMode.Root,
                portOpen = false,
                rootProbablyRunning = true
            )
        )
    }

    @Test
    fun `Root error should clear uptime anchor only when port and pid both indicate stopped`() {
        assertTrue(
            shouldClearStartedAtOnError(
                runMode = RunMode.Root,
                portOpen = false,
                rootProbablyRunning = false
            )
        )
    }

    @Test
    fun `Root passive liveness should trust an open port even without pid hint`() {
        assertTrue(
            isRootPassiveLivenessLikely(
                portOpen = true,
                pidPresent = false,
                startedAtMs = null,
                nowMs = 10_000L
            )
        )
    }

    @Test
    fun `Root passive liveness should reject stale pid without started-at anchor`() {
        assertFalse(
            isRootPassiveLivenessLikely(
                portOpen = false,
                pidPresent = true,
                startedAtMs = null,
                nowMs = 10_000L
            )
        )
    }

    @Test
    fun `Root passive liveness should reject very old pid hint`() {
        assertFalse(
            isRootPassiveLivenessLikely(
                portOpen = false,
                pidPresent = true,
                startedAtMs = 1_000L,
                nowMs = 1_000L + ROOT_PASSIVE_LIVENESS_HINT_TTL_MS + 1L
            )
        )
    }

    @Test
    fun `Root passive liveness should accept recent pid and started-at hint`() {
        assertTrue(
            isRootPassiveLivenessLikely(
                portOpen = false,
                pidPresent = true,
                startedAtMs = 1_000L,
                nowMs = 1_000L + ROOT_PASSIVE_LIVENESS_HINT_TTL_MS
            )
        )
    }

    @Test
    fun `Root startedAt should prefer root process anchor over pid file mtime and now`() {
        val selected = selectRuntimeStartedAtAnchor(
            mode = RunMode.Root,
            current = 0L,
            forceNew = false,
            normalHealthStartedAt = null,
            rootProcessStartedAt = 1_000L,
            rootPidFileModifiedAt = 2_000L,
            nowMs = 3_000L
        )

        assertEquals(1_000L, selected)
    }

    @Test
    fun `Root startedAt should recover from health uptime before falling back to pid file mtime`() {
        val selected = selectRuntimeStartedAtAnchor(
            mode = RunMode.Root,
            current = 0L,
            forceNew = false,
            normalHealthStartedAt = 1_500L,
            rootProcessStartedAt = null,
            rootPidFileModifiedAt = 2_000L,
            nowMs = 3_000L
        )

        assertEquals(1_500L, selected)
    }

    @Test
    fun `Root force-new start should still use root process anchor when available`() {
        val selected = selectRuntimeStartedAtAnchor(
            mode = RunMode.Root,
            current = 9_000L,
            forceNew = true,
            normalHealthStartedAt = null,
            rootProcessStartedAt = 4_000L,
            rootPidFileModifiedAt = 2_000L,
            nowMs = 5_000L
        )

        assertEquals(4_000L, selected)
    }
}
