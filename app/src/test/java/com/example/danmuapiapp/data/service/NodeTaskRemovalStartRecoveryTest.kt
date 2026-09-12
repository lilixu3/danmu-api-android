package com.example.danmuapiapp.data.service

import org.junit.Assert.*
import org.junit.Test

class NodeTaskRemovalStartRecoveryTest {
    private class Host {
        val recovery = NodeTaskRemovalStartRecovery()
        val calls = mutableListOf<String>()
        var foregroundAllowed = true

        fun taskRemoved(
            normalMode: Boolean = true,
            desiredRunning: Boolean = true,
            stopRequested: Boolean = false
        ): NodeTaskRemovalStartRecovery.Result = recovery.onTaskRemoved(
            normalMode = normalMode,
            desiredRunning = desiredRunning,
            stopRequested = stopRequested,
            enterForeground = {
                calls += "foreground"
                foregroundAllowed
            },
            requestStart = { explicit, notificationOnly ->
                calls += "requestStart(explicit=$explicit,notificationOnly=$notificationOnly)"
            }
        )
    }

    @Test
    fun `logged create then task removed sequence starts runtime even without onStartCommand`() {
        val recreated = Host()
        assertTrue(recreated.calls.isEmpty())
        assertEquals(NodeTaskRemovalStartRecovery.Result.StartRequested, recreated.taskRemoved())
        assertEquals(
            listOf("foreground", "requestStart(explicit=false,notificationOnly=false)"),
            recreated.calls
        )
    }

    @Test
    fun `ordinary started service is untouched when user swipes task away`() {
        val existing = Host()
        existing.recovery.onStartCommandReceived()
        assertEquals(NodeTaskRemovalStartRecovery.Result.Skipped, existing.taskRemoved())
        assertTrue(existing.calls.isEmpty())
    }

    @Test
    fun `new service has its own delivery history rather than inheriting dead process history`() {
        val previous = Host()
        previous.recovery.onStartCommandReceived()
        assertEquals(NodeTaskRemovalStartRecovery.Result.Skipped, previous.taskRemoved())
        val replacement = Host()
        assertEquals(NodeTaskRemovalStartRecovery.Result.StartRequested, replacement.taskRemoved())
    }

    @Test
    fun `repeated task removal callbacks only issue one recovery`() {
        val host = Host()
        assertEquals(NodeTaskRemovalStartRecovery.Result.StartRequested, host.taskRemoved())
        repeat(4) {
            assertEquals(NodeTaskRemovalStartRecovery.Result.Skipped, host.taskRemoved())
        }
        assertEquals(2, host.calls.size)
    }

    @Test
    fun `late real start command does not cause another task removal recovery`() {
        val host = Host()
        host.taskRemoved()
        host.recovery.onStartCommandReceived()
        assertEquals(NodeTaskRemovalStartRecovery.Result.Skipped, host.taskRemoved())
        assertEquals(2, host.calls.size)
    }

    @Test
    fun `sticky null command received before task removal uses normal path only`() {
        // null intent 也会先经过 NodeService.onStartCommand 的投递记录。
        val host = Host()
        host.recovery.onStartCommandReceived()
        assertEquals(NodeTaskRemovalStartRecovery.Result.Skipped, host.taskRemoved())
        assertTrue(host.calls.isEmpty())
    }

    @Test
    fun `a pending user stop visible through desiredRunning prevents cold recovery`() {
        val host = Host()
        assertEquals(
            NodeTaskRemovalStartRecovery.Result.Skipped,
            host.taskRemoved(desiredRunning = false)
        )
        assertTrue(host.calls.isEmpty())
    }

    @Test
    fun `service stop or destroy request prevents cold recovery even with old desired flag`() {
        val host = Host()
        assertEquals(
            NodeTaskRemovalStartRecovery.Result.Skipped,
            host.taskRemoved(stopRequested = true)
        )
        assertTrue(host.calls.isEmpty())
    }

    @Test
    fun `root mode cannot accidentally start a second normal mode runtime`() {
        val host = Host()
        assertEquals(
            NodeTaskRemovalStartRecovery.Result.Skipped,
            host.taskRemoved(normalMode = false)
        )
        assertTrue(host.calls.isEmpty())
    }

    @Test
    fun `notification auxiliary command received first is not overwritten by recovery`() {
        // RECOVERY 只补“任何 onStartCommand 都未收到”的缺口，不改写通知辅助命令。
        val host = Host()
        host.recovery.onStartCommandReceived()
        assertEquals(NodeTaskRemovalStartRecovery.Result.Skipped, host.taskRemoved())
        assertTrue(host.calls.isEmpty())
    }

    @Test
    fun `failed foreground setup never invokes Node start and does not retry in a loop`() {
        val host = Host().apply { foregroundAllowed = false }
        assertEquals(NodeTaskRemovalStartRecovery.Result.ForegroundUnavailable, host.taskRemoved())
        host.foregroundAllowed = true
        assertEquals(NodeTaskRemovalStartRecovery.Result.Skipped, host.taskRemoved())
        assertEquals(listOf("foreground"), host.calls)
    }

    @Test
    fun `throwing recovery callback is not retried by repeated lifecycle events`() {
        val recovery = NodeTaskRemovalStartRecovery()
        val failure = runCatching {
            recovery.onTaskRemoved(true, true, false, { true }) { _, _ ->
                throw IllegalStateException("start failed")
            }
        }
        assertEquals("start failed", failure.exceptionOrNull()?.message)
        assertEquals(
            NodeTaskRemovalStartRecovery.Result.Skipped,
            recovery.onTaskRemoved(true, true, false, { fail("must not retry"); true }) { _, _ ->
                fail("must not retry")
            }
        )
    }

    @Test
    fun `stop after recovery enqueue still invalidates queued runtime start`() {
        val recovery = NodeTaskRemovalStartRecovery()
        val commands = NodeRuntimeCommandFence()
        var queuedEpoch: Long? = null
        recovery.onTaskRemoved(true, true, false, { true }) { _, _ ->
            queuedEpoch = commands.recordStart()
        }
        commands.recordStop()
        assertNotNull(queuedEpoch)
        assertFalse(commands.acceptsStart(queuedEpoch!!))
    }

    @Test
    fun `later sticky start and recovered start share existing idempotent command epoch`() {
        val recovery = NodeTaskRemovalStartRecovery()
        val commands = NodeRuntimeCommandFence()
        val native = NodeRuntimeInvocationGate()
        var recoveredEpoch: Long? = null
        recovery.onTaskRemoved(true, true, false, { true }) { _, _ ->
            recoveredEpoch = commands.recordStart()
        }
        recovery.onStartCommandReceived()
        val lateStickyEpoch = commands.recordStart()
        assertEquals(recoveredEpoch, lateStickyEpoch)
        assertTrue(commands.acceptsStart(recoveredEpoch!!))
        assertTrue(commands.acceptsStart(lateStickyEpoch))
        assertTrue(native.tryClaim())
        assertFalse(native.tryClaim())
        native.release()
    }
}
