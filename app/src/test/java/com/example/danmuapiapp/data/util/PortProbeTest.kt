package com.example.danmuapiapp.data.util

import java.net.ServerSocket
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortProbeTest {

    private fun <T> withListeningSocket(block: (Int) -> T): T {
        ServerSocket(0).use { server ->
            return block(server.localPort)
        }
    }

    @Test
    fun `isOpen reports true for a listening port and false otherwise`() {
        withListeningSocket { port ->
            assertTrue(PortProbe.isOpen(port = port))
        }
        // 已释放的端口（几乎不会立刻被复用）应当探测失败。
        val closedPort = withListeningSocket { it }
        assertFalse(PortProbe.isOpen(port = closedPort, timeoutMs = 120))
    }

    @Test
    fun `isOpen rejects invalid ports without touching the network`() {
        assertFalse(PortProbe.isOpen(port = 0))
        assertFalse(PortProbe.isOpen(port = -1))
        assertFalse(PortProbe.isOpen(port = 70_000))
    }

    @Test
    fun `probe succeeds on the first attempt for a listening port`() = runBlocking {
        val server = ServerSocket(0)
        try {
            val result = PortProbe.probe(port = server.localPort, attempts = 3, retryDelayMs = 10L)
            assertTrue(result.open)
            assertEquals(1, result.attempts)
            assertEquals(null, result.error)
        } finally {
            server.close()
        }
    }

    @Test
    fun `probe reports every attempt and the last error when nothing listens`() = runBlocking {
        val closedPort = withListeningSocket { it }
        val result = PortProbe.probe(
            port = closedPort,
            attempts = 2,
            timeoutMs = 120,
            retryDelayMs = 10L
        )
        assertFalse(result.open)
        assertEquals(2, result.attempts)
        assertNotNull(result.error)
    }
}
