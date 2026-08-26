package com.example.danmuapiapp.data.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeServiceLifecyclePolicyTest {

    @Test
    fun `duplicate start while runtime is preparing must keep foreground service`() {
        assertFalse(
            shouldStopServiceAfterRejectedStart(
                serviceStopRequested = false,
                running = true,
                stopping = false,
                threadAlive = false
            )
        )
    }

    @Test
    fun `late start after terminal stop may release idle service`() {
        assertTrue(
            shouldStopServiceAfterRejectedStart(
                serviceStopRequested = true,
                running = false,
                stopping = false,
                threadAlive = false
            )
        )
    }

    @Test
    fun `destroy during desired runtime is reported as unexpected`() {
        assertTrue(
            shouldReportUnexpectedNodeServiceDestroy(
                serviceStopRequested = false,
                stopping = false,
                desiredRunning = true,
                running = true,
                threadAlive = true,
                startupStarted = false
            )
        )
    }

    @Test
    fun `controlled destroy does not overwrite stopped status`() {
        assertFalse(
            shouldReportUnexpectedNodeServiceDestroy(
                serviceStopRequested = true,
                stopping = false,
                desiredRunning = false,
                running = true,
                threadAlive = true,
                startupStarted = false
            )
        )
    }
}
