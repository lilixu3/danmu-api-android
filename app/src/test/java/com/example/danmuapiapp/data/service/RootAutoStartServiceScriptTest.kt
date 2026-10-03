package com.example.danmuapiapp.data.service

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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
        assertTrue(script.contains(".danmuapiapp-required-dependencies"))
        assertTrue(script.contains("selected core dependencies incomplete; open app to repair"))
    }

    @Test
    fun `boot tunnel uses shared kernel selection identity and format neutral config`() {
        val script = RootAutoStartScriptBuilders.buildServiceSh(
            "module", "/module", "/flags", "/enabled", "/mode", "Entry",
            frpDir = "/app/files/frp", nativeLibDir = "/app/lib", packageName = "example.app"
        )
        assertTrue(script.contains(com.example.danmuapiapp.data.tunnel.RootTunnelScripts.selectKernel()))
        assertTrue(script.contains("frpc.conf"))
        assertTrue(script.contains("owns_frpc"))
        assertTrue(script.contains("frpc_ticks"))
        assertTrue(script.contains("--version"))
        assertTrue(script.contains("FRP_PIDFILE.version"))
        val shell = ProcessBuilder("sh", "-n").redirectErrorStream(true).start()
        shell.outputStream.bufferedWriter().use { it.write(script) }
        val output = shell.inputStream.bufferedReader().readText()
        assertEquals(output, 0, shell.waitFor())
    }
    @Test fun bootDetectsNiceNameAndVerifiesRuntimeProject() {
        val script = RootAutoStartScriptBuilders.buildServiceSh(
            "module", "/module", "/flags", "/enabled", "/mode", "Entry", packageName = "example.app"
        )
        assertTrue(script.contains(RootRuntimeRecovery.identityFunctionShell()))
        assertTrue(script.contains("owned_root \"\$OLD\""))
        assertTrue(script.contains("owned_root \"\$NEW\""))
        assertTrue(script.contains("PROJECT=\"\$PROJ\""))
        val shell = ProcessBuilder("sh", "-n").redirectErrorStream(true).start()
        shell.outputStream.bufferedWriter().use { it.write(script) }
        assertEquals(shell.inputStream.bufferedReader().readText(), 0, shell.waitFor())
    }

}
