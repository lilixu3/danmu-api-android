package com.example.danmuapiapp.data.service

import org.junit.Assert.assertEquals
import org.junit.Test

class NodeHeartbeatRecoveryTest {
    @Test fun `manual stop and root mode must prevent all recovery`() {
        for (portOpen in listOf(false, true)) {
            assertEquals(HeartbeatRecoveryAction.None, heartbeatRecoveryAction(false, false, portOpen, true, false))
            assertEquals(HeartbeatRecoveryAction.None, heartbeatRecoveryAction(true, true, portOpen, true, false))
        }
    }
    @Test fun `an occupied port without our process is not healthy and must not be restarted`() {
        assertEquals(HeartbeatRecoveryAction.PortOccupied, heartbeatRecoveryAction(true, false, true, false, false))
    }
    @Test fun `an orphaned foreground host is reattached without restarting healthy runtime`() {
        assertEquals(HeartbeatRecoveryAction.ReattachHost, heartbeatRecoveryAction(true, false, true, true, false))
        assertEquals(HeartbeatRecoveryAction.None, heartbeatRecoveryAction(true, false, true, true, true))
    }
    @Test fun `closed port requires recovery even if a process still exists`() {
        assertEquals(HeartbeatRecoveryAction.RecoverRuntime, heartbeatRecoveryAction(true, false, false, true, true))
        assertEquals(HeartbeatRecoveryAction.RecoverRuntime, heartbeatRecoveryAction(true, false, false, false, false))
    }
}
