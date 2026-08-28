package com.example.danmuapiapp.desktop.runtime

import com.example.danmuapiapp.desktop.node.DesktopCoreInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 通过控制器走完整的 准备→启动→运行→停止 闭环（真实 node.exe + 缓存核心，端口 9321）。 */
class DesktopRuntimeControllerIntegrationTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val isWindows: Boolean
        get() = System.getProperty("os.name").lowercase().contains("windows")

    private fun resolveNodeExe(): File? {
        val candidates = listOf(
            System.getProperty("danmu.desktop.nodeExe")?.takeIf { it.isNotBlank() },
            System.getenv("DANMU_DESKTOP_NODE_EXE")?.takeIf { it.isNotBlank() },
        ).filterNotNull()
        candidates.forEach { candidate -> val f = File(candidate); if (f.isFile) return f }
        return null
    }

    private fun awaitPhase(controller: DesktopRuntimeController, phase: ServicePhase, timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (controller.state.value.phase == phase) return
            Thread.sleep(200)
        }
        org.junit.Assert.fail("等待 $phase 超时，当前=${controller.state.value}")
    }

    @Test
    fun startAndStopThroughController() {
        assumeTrue("仅 Windows 执行", isWindows)
        val nodeExe = resolveNodeExe()
        assumeTrue("未提供 node.exe（-PdanmuNodeExe），跳过", nodeExe != null)
        val settings = DesktopSettings(File(temp.root, "settings.properties"))
        val controller = DesktopRuntimeController(
            settings = settings,
            // 核心走系统级缓存（复用监督器集成测试下载的 zipball），避免测试重复联网
            coreInstaller = { scriptDir, _ -> DesktopCoreInstaller.ensureCoreInstalled(scriptDir) },
        )
        try {
            controller.start()
            awaitPhase(controller, ServicePhase.Running, 90_000)
            val running = controller.state.value
            assertNotNull(running.port)
            assertEquals(9321, running.port)
            assertNotNull(running.pid)
            assertNotNull(running.runtimeIdentity)

            controller.stop()
            awaitPhase(controller, ServicePhase.Stopped, 30_000)
            assertEquals(null, controller.state.value.port)
        } finally {
            controller.shutdown()
        }
    }
}
