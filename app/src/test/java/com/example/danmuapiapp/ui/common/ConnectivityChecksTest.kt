package com.example.danmuapiapp.ui.common

import com.example.danmuapiapp.data.service.AppOutboundConnectivityResult
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Test

class ConnectivityChecksTest {
    private suspend fun finish(checks: ConnectivityChecks) = withTimeout(3000) {
        while (checks.running) delay(1)
    }

    @Test fun `batch limits concurrent requests and deduplicates ids`() = runBlocking {
        val checks = ConnectivityChecks(this)
        val started = Channel<String>(Channel.UNLIMITED)
        val release = CompletableDeferred<Unit>()
        var active = 0
        var maximum = 0
        try {
            checks.start(listOf("one", "two", "three", "four", "one")) { id ->
                active++
                maximum = maxOf(maximum, active)
                started.send(id)
                try { release.await(); AppOutboundConnectivityResult(id, true, 12) }
                finally { active-- }
            }
            withTimeout(3000) { repeat(3) { started.receive() } }
            assertEquals(3, maximum)
            assertTrue(started.tryReceive().isFailure)
            assertEquals(4, checks.pending.size)
            release.complete(Unit)
            finish(checks)
            assertEquals(4, checks.results.size)
            assertTrue(checks.results.values.all { it.connected })
            assertTrue(checks.pending.isEmpty())
        } finally { checks.cancel() }
    }

    @Test fun `cancelled batch cannot overwrite a later result or clear its progress`() = runBlocking {
        val checks = ConnectivityChecks(this)
        val oldStarted = CompletableDeferred<Unit>()
        val oldRelease = CompletableDeferred<Unit>()
        val newStarted = CompletableDeferred<Unit>()
        val newRelease = CompletableDeferred<Unit>()
        try {
            checks.start(listOf("same", "queued")) { id ->
                withContext(NonCancellable) { oldStarted.complete(Unit); oldRelease.await() }
                AppOutboundConnectivityResult(id, false)
            }
            withTimeout(3000) { oldStarted.await() }
            checks.cancel()
            assertTrue(checks.pending.isEmpty())
            assertTrue(checks.results.isEmpty())
            checks.start(listOf("same")) { id ->
                newStarted.complete(Unit)
                newRelease.await()
                AppOutboundConnectivityResult(id, true, 8)
            }
            withTimeout(3000) { newStarted.await() }
            oldRelease.complete(Unit)
            repeat(5) { yield() }
            assertTrue(checks.running)
            assertEquals(listOf("same"), checks.pending.toList())
            assertTrue(checks.results.isEmpty())
            newRelease.complete(Unit)
            finish(checks)
            assertEquals(8L, checks.results.getValue("same").durationMs)
            assertFalse(checks.results.containsKey("queued"))
        } finally { oldRelease.complete(Unit); newRelease.complete(Unit); checks.cancel() }
    }

    @Test fun `one failed request is redacted and does not abort the batch`() = runBlocking {
        val checks = ConnectivityChecks(this)
        checks.start(listOf("ok", "bad")) { id ->
            if (id == "bad") error("private credential in upstream exception")
            AppOutboundConnectivityResult(id, true, 7)
        }
        finish(checks)
        assertTrue(checks.results.getValue("ok").connected)
        assertFalse(checks.results.getValue("bad").connected)
        assertEquals("连接失败或超时", checks.results.getValue("bad").reason)
    }

    @Test fun `repeat start while busy does not create an overlapping batch`() = runBlocking {
        val checks = ConnectivityChecks(this)
        val release = CompletableDeferred<Unit>()
        try {
            checks.start(listOf("one")) { id -> release.await(); AppOutboundConnectivityResult(id, true) }
            checks.start(listOf("two")) { error("must not run") }
            assertEquals(listOf("one"), checks.pending.toList())
            release.complete(Unit)
            finish(checks)
            assertEquals(setOf("one"), checks.results.keys)
        } finally { checks.cancel() }
    }
}
