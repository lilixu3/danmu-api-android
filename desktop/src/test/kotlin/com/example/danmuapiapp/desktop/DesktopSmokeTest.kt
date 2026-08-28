package com.example.danmuapiapp.desktop

import com.example.danmuapiapp.desktop.node.DesktopRuntimeState
import com.example.danmuapiapp.desktop.node.StartConfig
import com.example.danmuapiapp.desktop.node.WindowsNodeSupervisor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DesktopSmokeTest {

    @Test
    fun buildInfoContainsBaseLines() {
        val lines = buildInfoLines()
        assertTrue(lines.first().contains("弹幕API"))
        assertTrue(lines.any { it.startsWith("OS:") })
        assertTrue(lines.any { it.startsWith("Java:") })
    }

    @Test
    fun invalidNodeExeReportsFailedState() {
        val supervisor = WindowsNodeSupervisor()
        val snapshot = supervisor.start(
            StartConfig(
                nodeExe = File("Z:/definitely/missing/node.exe"),
                scriptDir = File("Z:/definitely/missing"),
                identityFile = File("build/tmp-desktop-smoke-home/instance-id"),
            )
        )
        assertEquals(DesktopRuntimeState.Failed, snapshot.state)
        assertNotNull(snapshot.failureReason)
    }
}
