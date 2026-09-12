package com.example.danmuapiapp.data.service

import org.junit.Assert.assertEquals
import org.junit.Test

class NodeRuntimePhaseTest {
    private fun phase(
        running: Boolean = true,
        stopping: Boolean = false,
        threadAlive: Boolean = true,
        startupStarted: Boolean = true,
        generation: Long = 2L,
        readyGeneration: Long = -1L
    ) = nodeRuntimePhase(running, stopping, threadAlive, startupStarted, generation, readyGeneration)

    @Test
    fun `reattach with alive JNI thread but no ready event must still show waiting`() {
        assertEquals(NodeRuntimePhase.WaitingForPort, phase())
        assertEquals("正在等待服务端口就绪…", phase().message)
    }

    @Test
    fun `preparing runtime with no JNI thread cannot display running`() {
        assertEquals(NodeRuntimePhase.Preparing, phase(threadAlive = false))
    }

    @Test
    fun `previous generation readiness must not promote new startup`() {
        assertEquals(NodeRuntimePhase.WaitingForPort, phase(readyGeneration = 1L))
    }

    @Test
    fun `completed startup can be replayed without another readiness probe`() {
        assertEquals(NodeRuntimePhase.Ready, phase(readyGeneration = 2L))
    }

    @Test
    fun `adopted runtime with a published ready event does not require a local thread handle`() {
        assertEquals(
            NodeRuntimePhase.Ready,
            phase(threadAlive = false, startupStarted = false, generation = 0L, readyGeneration = 0L)
        )
    }

    @Test
    fun `stop phase overrides all ready and alive markers`() {
        assertEquals(NodeRuntimePhase.Stopping, phase(stopping = true, readyGeneration = 2L))
    }

    @Test
    fun `idle process is not proof of a running HTTP server`() {
        assertEquals(
            NodeRuntimePhase.Idle,
            phase(running = false, threadAlive = false, startupStarted = false, readyGeneration = 2L)
        )
    }

    @Test
    fun `default minus one markers do not count as completed readiness`() {
        assertEquals(NodeRuntimePhase.WaitingForPort, phase(generation = -1L, readyGeneration = -1L))
    }
}
