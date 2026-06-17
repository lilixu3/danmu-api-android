package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.data.service.RootRuntimeController
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootRuntimeStartDecisionTest {

    @Test
    fun `Root start already running must preserve existing uptime anchor`() {
        assertFalse(
            shouldForceNewRootStartedAt(RootRuntimeController.StartOutcome.AlreadyRunning)
        )
    }

    @Test
    fun `Root start newly launched must create a new uptime anchor`() {
        assertTrue(
            shouldForceNewRootStartedAt(RootRuntimeController.StartOutcome.StartedNewProcess)
        )
    }
}
