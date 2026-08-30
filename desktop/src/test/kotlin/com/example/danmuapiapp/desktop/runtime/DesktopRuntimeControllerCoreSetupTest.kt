package com.example.danmuapiapp.desktop.runtime

import com.example.danmuapiapp.desktop.node.WindowsNodeSupervisor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DesktopRuntimeControllerCoreSetupTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun missingCorePublishesCoreSetupRequiredWithoutStartingSupervisor() {
        val settings = DesktopSettings(File(temp.root, "settings.properties"))
        settings.setRuntimeRoot(File(temp.root, "runtime").absolutePath)
        var supervisorCreated = false
        val controller = DesktopRuntimeController(
            settings = settings,
            supervisorFactory = {
                supervisorCreated = true
                error("核心缺失时不应创建 WindowsNodeSupervisor")
            },
            runtimeExtractor = { runtimeDir ->
                val scriptDir = File(runtimeDir, "nodejs-project")
                scriptDir.mkdirs()
                File(scriptDir, "main.js").writeText("// test runtime")
                File(runtimeDir, "node.exe").writeText("test runtime marker")
            },
        )
        try {
            controller.start()
            val deadline = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < deadline && controller.state.value.phase == ServicePhase.Stopped) {
                Thread.sleep(25)
            }
            while (System.currentTimeMillis() < deadline && controller.state.value.phase == ServicePhase.Preparing) {
                Thread.sleep(25)
            }
            assertEquals(ServicePhase.CoreSetupRequired, controller.state.value.phase)
            assertTrue(controller.state.value.message.contains("核心尚未准备"))
            assertEquals(false, supervisorCreated)
        } finally {
            controller.shutdown()
        }
    }
}
