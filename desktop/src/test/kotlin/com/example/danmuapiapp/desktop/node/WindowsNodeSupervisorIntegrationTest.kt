package com.example.danmuapiapp.desktop.node

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * W-0003 Windows 集成验收：真实 node.exe 启动现有运行时副本，
 * 校验 Running/Stopped 判定、端口释放、身份消失与无残留进程。
 */
class WindowsNodeSupervisorIntegrationTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val supervisors = mutableListOf<WindowsNodeSupervisor>()

    @After
    fun stopTrackedSupervisors() {
        // 断言失败时也要回收 node 子进程，避免留下孤儿 node.exe
        supervisors.forEach { supervisor ->
            runCatching { supervisor.stop() }
        }
        supervisors.clear()
    }

    private val isWindows: Boolean
        get() = System.getProperty("os.name").lowercase().contains("windows")

    private fun resolveNodeExe(): File? {
        val candidates = listOf(
            System.getProperty("danmu.desktop.nodeExe")?.takeIf { it.isNotBlank() },
            System.getenv("DANMU_DESKTOP_NODE_EXE")?.takeIf { it.isNotBlank() },
        ).filterNotNull()
        candidates.forEach { candidate ->
            val file = File(candidate)
            if (file.isFile) return file
        }
        return null
    }

    private fun resolveRuntimeSource(): File? {
        val path = System.getProperty("danmu.desktop.runtimeSource")?.takeIf { it.isNotBlank() }
            ?: return null
        return File(path).takeIf { it.isDirectory }
    }

    private fun copyRuntime(): File {
        val source = resolveRuntimeSource()
        assumeTrue("缺少 nodejs-project 运行时源目录（danmu.desktop.runtimeSource），跳过", source != null)
        val destination = File(temp.root, "nodejs-project")
        if (destination.exists()) destination.deleteRecursively()
        source!!.copyRecursively(destination, overwrite = true)
        return destination
    }

    private fun isPortFree(port: Int): Boolean {
        return try {
            ServerSocket().use { it.bind(InetSocketAddress("127.0.0.1", port)) }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun healthUnreachable(port: Int): Boolean {
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build()
        return try {
            val response = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/__health")).GET().build(),
                HttpResponse.BodyHandlers.discarding(),
            )
            response.statusCode() !in 200..299
        } catch (_: Exception) {
            true
        }
    }

    private fun isPidAlive(pid: Long): Boolean {
        if (!isWindows) return true
        val process = ProcessBuilder("tasklist", "/FI", "PID eq $pid", "/NH", "/FO", "CSV").start()
        val output = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).use { it.readText() }
        process.waitFor()
        return output.contains(pid.toString())
    }

    private fun runCycles(cycles: Int) {
        val nodeExe = resolveNodeExe()
        assumeTrue("未提供 node.exe（-PdanmuNodeExe），跳过", nodeExe != null)
        assumeTrue("仅 Windows 执行", isWindows)
        val scriptDir = copyRuntime()
        val startedPids = mutableListOf<Long>()
        val suiteStart = System.currentTimeMillis()
        repeat(cycles) { index ->
            val supervisor = WindowsNodeSupervisor()
            supervisors += supervisor
            val cycleStart = System.currentTimeMillis()
            val running = supervisor.start(
                StartConfig(nodeExe!!, scriptDir, File(temp.root, "home-$index"))
            )
            assertEquals("第 ${index + 1} 轮启动应为 Running", DesktopRuntimeState.Running, running.state)
            assertNotNull(running.port)
            assertNotNull(running.pid)
            assertNotNull(running.runtimeIdentity)
            val identity = running.runtimeIdentity!!
            assertEquals(identity, supervisor.snapshot.runtimeIdentity)
            startedPids += running.pid!!
            val port = running.port!!
            val stopped = supervisor.stop()
            assertEquals("第 ${index + 1} 轮停止应为 Stopped", DesktopRuntimeState.Stopped, stopped.state)
            assertTrue("端口 $port 应已释放", isPortFree(port))
            assertTrue("健康接口应不可达（旧 identity 消失）", healthUnreachable(port))
            val lastPid = startedPids.last()
            assertFalse("node.exe (pid=$lastPid) 应已退出", isPidAlive(lastPid))
            println("cycle ${index + 1}/$cycles: port=$port elapsed=${System.currentTimeMillis() - cycleStart}ms")
        }
        println("total $cycles cycles in ${System.currentTimeMillis() - suiteStart}ms")
        startedPids.forEachIndexed { index, pid ->
            assertFalse("残留 node.exe 检查 #$index pid=$pid", isPidAlive(pid))
        }
    }

    @Test
    fun startAndStopThreeCyclesWithoutResidue() {
        runCycles(3)
    }

    @Test
    fun twentyConsecutiveCyclesWithoutResidue() {
        assumeTrue(
            "仅 -PdesktopLongSmoke=true 时执行",
            System.getProperty("danmu.desktop.longSmoke") == "true"
        )
        runCycles(20)
    }

    @Test
    fun portOccupiedLeadsToFailed() {
        val nodeExe = resolveNodeExe()
        assumeTrue("未提供 node.exe，跳过", nodeExe != null)
        assumeTrue("仅 Windows 执行", isWindows)
        val scriptDir = copyRuntime()
        val blocker = ServerSocket()
        blocker.bind(InetSocketAddress("127.0.0.1", 0))
        val occupiedPort = blocker.localPort
        try {
            val supervisor = WindowsNodeSupervisor()
            supervisors += supervisor
            val snapshot = supervisor.start(
                StartConfig(nodeExe!!, scriptDir, File(temp.root, "home-conflict"), fixedPort = occupiedPort)
            )
            assertEquals(DesktopRuntimeState.Failed, snapshot.state)
            assertNotNull(snapshot.failureReason)
            supervisor.stop()
        } finally {
            blocker.close()
        }
    }

    @Test
    fun missingEntryLeadsToFailed() {
        val nodeExe = resolveNodeExe()
        assumeTrue("未提供 node.exe，跳过", nodeExe != null)
        assumeTrue("仅 Windows 执行", isWindows)
        val scriptDir = copyRuntime()
        File(scriptDir, "main.js").delete()
        val supervisor = WindowsNodeSupervisor()
        val snapshot = supervisor.start(
            StartConfig(nodeExe!!, scriptDir, File(temp.root, "home-missing-entry"))
        )
        assertEquals(DesktopRuntimeState.Failed, snapshot.state)
        assertTrue(snapshot.failureReason.orEmpty().contains("入口缺失"))
    }

    @Test
    fun missingDependenciesLeadToFailed() {
        val nodeExe = resolveNodeExe()
        assumeTrue("未提供 node.exe，跳过", nodeExe != null)
        assumeTrue("仅 Windows 执行", isWindows)
        val scriptDir = copyRuntime()
        File(scriptDir, "node_modules").renameTo(File(scriptDir, "node_modules-hidden"))
        val supervisor = WindowsNodeSupervisor()
        supervisors += supervisor
        val snapshot = supervisor.start(
            StartConfig(nodeExe!!, scriptDir, File(temp.root, "home-missing-deps"))
        )
        assertEquals(DesktopRuntimeState.Failed, snapshot.state)
        supervisor.stop()
    }

    @Test
    fun missingCoreLeadsToFailedWithoutInstaller() {
        val nodeExe = resolveNodeExe()
        assumeTrue("未提供 node.exe，跳过", nodeExe != null)
        assumeTrue("仅 Windows 执行", isWindows)
        // 运行时副本不含核心（核心不随包内置）；禁用在线安装器后宿主应快速进入 Failed
        val scriptDir = copyRuntime()
        assertFalse("运行时源不应内置核心", File(scriptDir, "danmu_api_stable/worker.js").isFile)
        val supervisor = WindowsNodeSupervisor()
        supervisors += supervisor
        val snapshot = supervisor.start(
            StartConfig(nodeExe!!, scriptDir, File(temp.root, "home-nocore"), ensureCore = {})
        )
        assertEquals(DesktopRuntimeState.Failed, snapshot.state)
        supervisor.stop()
    }

    @Test
    fun coreInstallerDownloadsOnlineAndIsIdempotent() {
        assumeTrue("仅 Windows 执行", isWindows)
        val scriptDir = copyRuntime()
        DesktopCoreInstaller.ensureCoreInstalled(scriptDir)
        assertTrue("在线安装后核心应存在", File(scriptDir, "danmu_api_stable/worker.js").isFile)
        // 幂等：重复调用不再触发下载（无网络断言不可靠，这里验证不抛异常即可）
        DesktopCoreInstaller.ensureCoreInstalled(scriptDir)
    }
}
