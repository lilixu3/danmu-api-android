package com.example.danmuapiapp.data.service

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class NodeRuntimeHostRelayTest {
    private class Host {
        val messages = mutableListOf<String>()
        var stops = 0
    }

    @Test
    fun `old queued notification and stop callbacks cannot affect replacement service`() {
        val queue = mutableListOf<() -> Unit>()
        val relay = NodeRuntimeHostRelay<Host> { queue += it }
        val old = Host()
        val replacement = Host()
        relay.attach(old)
        relay.dispatch { it.messages += "old" }
        relay.dispatch { it.stops++ }
        relay.detach(old)
        relay.attach(replacement)
        relay.dispatch { it.messages += "new" }
        queue.forEach { it() }
        assertTrue(old.messages.isEmpty())
        assertEquals(0, old.stops)
        assertEquals(listOf("new"), replacement.messages)
        assertEquals(0, replacement.stops)
    }

    @Test
    fun `late destroy of previous host must not detach current host`() {
        val relay = NodeRuntimeHostRelay<Host> { it() }
        val old = Host()
        val replacement = Host()
        relay.attach(old)
        relay.attach(replacement)
        relay.detach(old)
        relay.dispatch { it.messages += "still attached" }
        assertEquals(listOf("still attached"), replacement.messages)
    }

    @Test
    fun `detached host receives no completion but a new host receives subsequent runtime events`() {
        val queue = mutableListOf<() -> Unit>()
        val relay = NodeRuntimeHostRelay<Host> { queue += it }
        val old = Host()
        relay.attach(old)
        relay.detach(old)
        relay.dispatch { it.stops++ }
        val replacement = Host()
        relay.attach(replacement)
        relay.dispatch { it.stops++ }
        queue.forEach { it() }
        assertEquals(0, old.stops)
        assertEquals(1, replacement.stops)
    }

    @Test
    fun `service replacement does not grant a second active native invocation`() {
        val native = NodeRuntimeInvocationGate()
        val relay = NodeRuntimeHostRelay<Host> { it() }
        val old = Host()
        relay.attach(old)
        assertTrue(native.tryClaim())
        relay.detach(old)
        relay.attach(Host())
        assertFalse(native.tryClaim())
        native.release()
        assertTrue(native.tryClaim())
    }

    @Test
    fun `concurrent start requests grant exactly one native invocation`() {
        val native = NodeRuntimeInvocationGate()
        val ready = CountDownLatch(1)
        val done = CountDownLatch(8)
        val claimed = AtomicInteger()
        repeat(8) {
            Thread {
                try {
                    ready.await(5, TimeUnit.SECONDS)
                    if (native.tryClaim()) claimed.incrementAndGet()
                } finally {
                    done.countDown()
                }
            }.start()
        }
        ready.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(1, claimed.get())
    }

    @Test
    fun `native invocation stays claimed until runtime thread actually finishes`() {
        val native = NodeRuntimeInvocationGate()
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val finished = CountDownLatch(1)
        assertTrue(native.tryClaim())
        val thread = Thread {
            try {
                native.runClaimed {
                    started.countDown()
                    finish.await(5, TimeUnit.SECONDS)
                }
            } finally {
                finished.countDown()
            }
        }
        thread.start()
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            // Java 启动协程/通知宿主可以先结束，真实 JNI 调用权不能先结束。
            assertTrue(native.isActive)
            assertFalse(native.tryClaim())
        } finally {
            finish.countDown()
            thread.join(5_000L)
        }
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertFalse(native.isActive)
        assertTrue(native.tryClaim())
    }

    @Test
    fun `exception in runtime thread still returns its native invocation claim`() {
        val native = NodeRuntimeInvocationGate()
        assertTrue(native.tryClaim())
        val failure = runCatching {
            native.runClaimed<Unit> { throw IllegalStateException("native failure") }
        }
        assertEquals("native failure", failure.exceptionOrNull()?.message)
        assertFalse(native.isActive)
        assertTrue(native.tryClaim())
    }

    @Test
    fun `native thread cannot run without first owning the invocation claim`() {
        val native = NodeRuntimeInvocationGate()
        var ran = false
        assertTrue(runCatching { native.runClaimed { ran = true } }.isFailure)
        assertFalse(ran)
        assertFalse(native.isActive)
    }

}
