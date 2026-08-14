package com.example.danmuapiapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeListenModeTest {
    @Test
    fun `listen mode parses persisted keys`() {
        assertEquals(RuntimeListenMode.Ipv4Only, RuntimeListenMode.fromKey("ipv4"))
        assertEquals(RuntimeListenMode.DualStack, RuntimeListenMode.fromKey(" DUAL_STACK "))
        assertNull(RuntimeListenMode.fromKey("unknown"))
    }

    @Test
    fun `listen mode migrates supported env bind hosts`() {
        assertEquals(RuntimeListenMode.Ipv4Only, RuntimeListenMode.fromBindHost("0.0.0.0"))
        assertEquals(RuntimeListenMode.DualStack, RuntimeListenMode.fromBindHost("::"))
        assertEquals(RuntimeListenMode.DualStack, RuntimeListenMode.fromBindHost("[::]"))
        assertNull(RuntimeListenMode.fromBindHost("127.0.0.1"))
    }
}
