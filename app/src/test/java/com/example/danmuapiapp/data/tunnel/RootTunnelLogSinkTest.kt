package com.example.danmuapiapp.data.tunnel

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class RootTunnelLogSinkTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun `native sink caps retained data preserves split UTF8 and flushes last event on EOF`() {
        val log = temporary.newFile("frpc.log")
        log.writeBytes(ByteArray(2 * 1024 * 1024))
        val data = ("start proxy success\n" + "x".repeat(3 * 1024 * 1024) + "\nlogin to server failed\n中文LATEST").toByteArray()
        val input = object : ByteArrayInputStream(data) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, minOf(len, 257))
        }
        val events = mutableListOf<TunnelLinkState>()
        val observer = FrpcLinkObserver(events::add)
        pumpFrpcBoundedLog(input, log, observer::accept)
        assertTrue(log.length() <= 1024 * 1024 + 16384)
        assertTrue(log.readText().endsWith("中文LATEST"))
        assertEquals(listOf(TunnelLinkState.Connected, TunnelLinkState.Error), events)
    }
    @Test fun `root FIFO invokes the kept Android entry and closes inherited lock FD`() {
        val script = RootTunnelScripts.logSink(trackLink = true)
        assertTrue(script.contains("RootFrpcLogEntry --bounded-log"))
        assertTrue(script.contains("CLASSPATH=\"${'$'}FRP_LOG_APK\""))
        assertTrue(script.contains("9>&-"))
        assertTrue(script.contains("--frpc-instance"))
        assertFalse(script.contains("libdanmu_outbound"))
    }
}
