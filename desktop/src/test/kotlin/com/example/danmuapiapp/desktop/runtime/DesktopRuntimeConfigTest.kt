package com.example.danmuapiapp.desktop.runtime

import com.example.danmuapiapp.desktop.node.StartConfig
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DesktopRuntimeConfigTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun envValuesAreUsedWhenNoExplicitSettingsExist() {
        val root = temp.newFolder("runtime")
        val script = File(root, "nodejs-project")
        File(script, "config").mkdirs()
        File(script, "config/.env").writeText(
            "DANMU_API_PORT=19421\nDANMU_API_HOST=127.0.0.1\nDANMU_API_VARIANT=dev\n"
        )
        val settings = DesktopSettings(File(temp.root, "settings.properties"))
        assertEquals(
            DesktopRuntimeConfig(19421, "127.0.0.1", "dev"),
            DesktopRuntimeConfigResolver.resolve(settings, script),
        )
    }

    @Test
    fun explicitSettingsOverrideEnvAndDefaultFallsBackTo9321() {
        val root = temp.newFolder("runtime")
        val script = File(root, "nodejs-project")
        File(script, "config").mkdirs()
        File(script, "config/.env").writeText(
            "DANMU_API_PORT=broken\nDANMU_API_HOST=\nDANMU_API_VARIANT=unknown\n"
        )
        val settings = DesktopSettings(File(temp.root, "settings.properties"))
        assertEquals(StartConfig.DEFAULT_PORT, DesktopRuntimeConfigResolver.resolve(settings, script).port)
        assertEquals(StartConfig.DEFAULT_LISTEN_HOST, DesktopRuntimeConfigResolver.resolve(settings, script).listenHost)
        assertEquals("stable", DesktopRuntimeConfigResolver.resolve(settings, script).variant)

        settings.setPortOverride(20123)
        settings.setListenHostOverride("127.0.0.1")
        settings.setVariantOverride("custom")
        assertEquals(
            DesktopRuntimeConfig(20123, "127.0.0.1", "custom"),
            DesktopRuntimeConfigResolver.resolve(settings, script),
        )
    }

    @Test
    fun invalidExplicitPortIsClearedAndFallsBack() {
        val settings = DesktopSettings(File(temp.root, "settings.properties"))
        settings.setPortOverride(0)
        assertEquals(null, settings.portOverride)
    }
}
