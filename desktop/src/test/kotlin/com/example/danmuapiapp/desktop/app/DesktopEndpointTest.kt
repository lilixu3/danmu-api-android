package com.example.danmuapiapp.desktop.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DesktopEndpointTest {
    @Test
    fun buildsLoopbackAndLanApiAddressWithDefaultToken() {
        assertEquals("http://127.0.0.1:9321/87654321", buildApiAddress("127.0.0.1", 9321, null))
        assertEquals("http://10.0.0.144:9321/87654321", buildApiAddress("10.0.0.144", 9321, ""))
    }

    @Test
    fun encodesExplicitTokenAndIpv6Authority() {
        assertEquals("http://[fe80::1]:9321/a%2Fb%20c", buildApiAddress("fe80::1", 9321, "a/b c"))
    }

    @Test
    fun computesContinuousUptimeFromSnapshotReceiveTime() {
        assertEquals(12L, effectiveUptimeSeconds(10L, 1000L, 3_999L))
        assertEquals(13L, effectiveUptimeSeconds(10L, 1000L, 4_000L))
        assertNull(effectiveUptimeSeconds(null, 1000L, 4_000L))
    }
}
