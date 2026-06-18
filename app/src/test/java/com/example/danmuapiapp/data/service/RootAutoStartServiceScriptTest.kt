package com.example.danmuapiapp.data.service

import org.junit.Assert.assertTrue
import org.junit.Test

class RootAutoStartServiceScriptTest {

    @Test
    fun `service script passes started-at file to Root entrypoint`() {
        val script = RootAutoStartScriptBuilders.buildServiceSh(
            moduleId = "danmuapi_boot_autostart",
            moduleDir = "/data/adb/modules/danmuapi_boot_autostart",
            flagDir = "/data/adb/danmuapi_boot",
            flagFile = "/data/adb/danmuapi_boot/enabled",
            modeFile = "/data/adb/danmuapi_boot/mode",
            mainClass = RootNodeEntry::class.java.name
        )

        assertTrue(script.contains("STARTED_AT_FILE=\"${'$'}RUNTIME/root_node_started_at_ms\""))
        assertTrue(script.contains("--started-at-file \"${'$'}STARTED_AT_FILE\""))
    }
}
