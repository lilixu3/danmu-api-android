package com.example.danmuapiapp.ui.common

import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.model.ServiceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeRestartEvidenceTest {
    private val snapshot = RuntimeRestartSnapshot(
        runMode = RunMode.Normal,
        pid = 100,
        uptimeSeconds = 120
    )

    @Test
    fun `stopped is restart progress instead of terminal failure`() {
        val observation = RuntimeRestartEvidence.observe(
            state = state(ServiceStatus.Stopped),
            targetVariant = ApiVariant.Dev,
            beforeRestart = snapshot,
            sawProgress = false
        )

        assertTrue(observation.sawProgress)
        assertNull(observation.terminalStatus)
    }

    @Test
    fun `running target succeeds after restart progress`() {
        val observation = RuntimeRestartEvidence.observe(
            state = state(ServiceStatus.Running),
            targetVariant = ApiVariant.Dev,
            beforeRestart = snapshot,
            sawProgress = true
        )

        assertEquals(ServiceStatus.Running, observation.terminalStatus)
    }

    @Test
    fun `running target without progress needs new process evidence`() {
        val unchanged = state(ServiceStatus.Running, uptimeSeconds = 120)
        val restarted = state(ServiceStatus.Running, uptimeSeconds = 1)

        assertFalse(
            RuntimeRestartEvidence.isConfirmedRunning(
                unchanged,
                ApiVariant.Dev,
                snapshot,
                sawProgress = false
            )
        )
        assertTrue(
            RuntimeRestartEvidence.isConfirmedRunning(
                restarted,
                ApiVariant.Dev,
                snapshot,
                sawProgress = false
            )
        )
    }

    @Test
    fun `root restart can be confirmed by pid change`() {
        val rootSnapshot = RuntimeRestartSnapshot(RunMode.Root, pid = 200, uptimeSeconds = 120)
        val restarted = state(
            status = ServiceStatus.Running,
            runMode = RunMode.Root,
            pid = 201,
            uptimeSeconds = 120
        )

        assertTrue(
            RuntimeRestartEvidence.isConfirmedRunning(
                restarted,
                ApiVariant.Dev,
                rootSnapshot,
                sawProgress = false
            )
        )
    }

    @Test
    fun `running another variant never confirms target restart`() {
        val wrongVariant = state(ServiceStatus.Running).copy(variant = ApiVariant.Stable)

        assertFalse(
            RuntimeRestartEvidence.isConfirmedRunning(
                wrongVariant,
                ApiVariant.Dev,
                snapshot,
                sawProgress = true
            )
        )
    }

    @Test
    fun `error remains terminal failure`() {
        val observation = RuntimeRestartEvidence.observe(
            state = state(ServiceStatus.Error),
            targetVariant = ApiVariant.Dev,
            beforeRestart = snapshot,
            sawProgress = false
        )

        assertEquals(ServiceStatus.Error, observation.terminalStatus)
    }

    private fun state(
        status: ServiceStatus,
        runMode: RunMode = RunMode.Normal,
        pid: Int? = 100,
        uptimeSeconds: Long = 120
    ) = RuntimeState(
        status = status,
        variant = ApiVariant.Dev,
        runMode = runMode,
        pid = pid,
        uptimeSeconds = uptimeSeconds
    )
}
