package com.example.danmuapiapp.data.service

import org.junit.Assert.assertTrue
import org.junit.Test

class NodeServiceWakeLockPolicyTest {

    @Test
    fun `renewal leaves time before expiry`() {
        assertTrue(NodeService.RUNTIME_WAKE_LOCK_RENEW_INTERVAL_MS > 0L)
        assertTrue(NodeService.RUNTIME_WAKE_LOCK_RENEW_INTERVAL_MS < NodeService.RUNTIME_WAKE_LOCK_TIMEOUT_MS)
    }

    @Test
    fun `runtime wake lock timeout is finite and long enough for TV serving`() {
        assertTrue(NodeService.RUNTIME_WAKE_LOCK_TIMEOUT_MS > 0L)
        assertTrue(NodeService.RUNTIME_WAKE_LOCK_TIMEOUT_MS >= 60L * 60L * 1000L)
    }
}
