package com.example.danmuapiapp.data.service

import org.junit.Assert.assertTrue
import org.junit.Test

class RootRuntimeControllerMarkerTest {

    @Test
    fun `clear runtime marker script removes pid and started-at files`() {
        val script = RootRuntimeController.buildClearRuntimeMarkersShell(
            pidPath = "/data/user/0/pkg/files/root_node.pid",
            startedAtPath = "/data/user/0/pkg/files/root_node_started_at_ms"
        )

        assertTrue(script.contains("/data/user/0/pkg/files/root_node.pid"))
        assertTrue(script.contains("/data/user/0/pkg/files/root_node_started_at_ms"))
        assertTrue(script.contains("rm -f"))
    }
}
