package com.example.danmuapiapp.data.tunnel

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executor

class TunnelCommandQueueTest {
    @Test fun `close drains prior work and final stop but rejects later starts`() {
        val pending = mutableListOf<Runnable>()
        val steps = mutableListOf<String>()
        val queue = TunnelCommandQueue(Executor { pending += it })
        queue.execute { steps += "start" }
        queue.execute { steps += "stop" }
        queue.close { steps += "final-stop" }
        queue.execute { fail("closed service must not start") }
        queue.close { fail("final stop must run once") }
        assertTrue(steps.isEmpty())
        pending.forEach { it.run() }
        assertEquals(listOf("start", "stop", "final-stop"), steps)
    }
    @Test fun `task failure cannot skip queued final stop`() {
        val pending = mutableListOf<Runnable>()
        val errors = mutableListOf<Exception>()
        var stopped = false
        val queue = TunnelCommandQueue(Executor { pending += it }, { errors += it })
        queue.execute { throw IllegalStateException("start failed") }
        queue.close { stopped = true }
        pending.forEach { it.run() }
        assertTrue(stopped)
        assertEquals(1, errors.size)
    }
}
