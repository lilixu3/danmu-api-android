package com.example.danmuapiapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class KeepAliveHeartbeatModeTest {

    @Test
    fun `storage keys round trip`() {
        KeepAliveHeartbeatMode.entries.forEach { mode ->
            assertEquals(mode, KeepAliveHeartbeatMode.fromKey(mode.key))
        }
    }

    @Test
    fun `missing or unknown key keeps accessibility compatibility`() {
        assertEquals(
            KeepAliveHeartbeatMode.Accessibility,
            KeepAliveHeartbeatMode.fromKey(null)
        )
        assertEquals(
            KeepAliveHeartbeatMode.Accessibility,
            KeepAliveHeartbeatMode.fromKey("unknown")
        )
        assertEquals(
            KeepAliveHeartbeatMode.System,
            KeepAliveHeartbeatMode.fromKey(" SYSTEM ")
        )
    }
}
