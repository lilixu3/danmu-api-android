package com.example.danmuapiapp.data.service

import org.junit.Assert.*
import org.junit.Test

class NodeRuntimeCommandFenceTest {
    @Test
    fun `stop invalidates previously queued starts even if another start follows`() {
        val fence = NodeRuntimeCommandFence()
        val oldStart = fence.recordStart()
        fence.recordStop()
        val newStart = fence.recordStart()
        assertFalse(fence.acceptsStart(oldStart))
        assertTrue(fence.acceptsStart(newStart))
    }

    @Test
    fun `duplicate start and service reattachment do not cancel current startup work`() {
        val fence = NodeRuntimeCommandFence()
        val first = fence.recordStart()
        val duplicate = fence.recordStart()
        assertTrue(fence.acceptsStart(first))
        assertTrue(fence.acceptsStart(duplicate))
    }

    @Test
    fun `new start prevents queued old stopSelf callback from stopping new service session`() {
        val fence = NodeRuntimeCommandFence()
        fence.recordStop()
        val stopCallbackVersion = fence.commandVersion
        assertTrue(fence.acceptsStopCallback(stopCallbackVersion))
        fence.recordStart()
        assertFalse(fence.acceptsStopCallback(stopCallbackVersion))
    }

    @Test
    fun `new explicit stop supersedes callbacks from earlier commands`() {
        val fence = NodeRuntimeCommandFence()
        fence.recordStart()
        val oldCallbackVersion = fence.commandVersion
        fence.recordStop()
        assertFalse(fence.acceptsStopCallback(oldCallbackVersion))
        assertTrue(fence.acceptsStopCallback(fence.commandVersion))
    }
}
