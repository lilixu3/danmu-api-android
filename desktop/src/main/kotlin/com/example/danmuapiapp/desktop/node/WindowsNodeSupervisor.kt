package com.example.danmuapiapp.desktop.node

import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Windows 宿主的显式进程状态机（对应总计划 7.2 节）。 */
enum class DesktopRuntimeState { Stopped, Preparing, Starting, Running, Stopping, Failed }

data class StartConfig(
    val nodeExe: File,
    val scriptDir: File,
    val dataHome: File,
    val variant: String = "desktop-p0",
    val startupTimeoutMs: Long = 30_000,
    val shutdownTimeoutMs: Long = 10_000,
    /** 核心缓存目录（在线下载的 zipball 缓存），null 用系统临时目录。 */
    val coreCacheDir: File? = null,
    /** 核心准备策略：默认在线下载（核心不随包内置）；测试可注入 no-op。 */
    val ensureCore: (File) -> Unit = { scriptDir ->
        DesktopCoreInstaller.ensureCoreInstalled(scriptDir, coreCacheDir)
    },
    /** 测试用：强制使用指定端口（例如模拟端口占用）。 */
    val fixedPort: Int? = null,
)

data class RuntimeSnapshot(
    val state: DesktopRuntimeState,
    val port: Int? = null,
    val pid: Long? = null,
    val runtimeIdentity: String? = null,
    val failureReason: String? = null,
)

/**
 * W-0003 最小进程监督器：用固定版本 node.exe 子进程运行现有 Node 运行时，
 * 以“子进程存活 + 端口监听 + 健康接口身份/端口/PID/工作目录一致”多重条件判定 Running，
 * 以“子进程退出 + 端口释放（旧身份不可达）”判定 Stopped。不依赖单一 STOPPED 消息。
 */
class WindowsNodeSupervisor {

    private val stateRef = AtomicReference(DesktopRuntimeState.Stopped)
    private var process: Process? = null
    private var port: Int = -1
    private var identity: String = ""
    private var config: StartConfig? = null
    private var failureReason: String? = null

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(600))
        .build()

    val snapshot: RuntimeSnapshot
        @Synchronized get() = RuntimeSnapshot(
            state = stateRef.get(),
            port = port.takeIf { it > 0 },
            pid = process?.pid()?.toLong(),
            runtimeIdentity = identity.takeIf { stateRef.get() == DesktopRuntimeState.Running },
            failureReason = failureReason,
        )

    @Synchronized
    fun start(config: StartConfig): RuntimeSnapshot {
        val current = stateRef.get()
        check(current == DesktopRuntimeState.Stopped || current == DesktopRuntimeState.Failed) {
            "当前状态 $current 不允许启动，请先 stop()"
        }
        failureReason = null
        stateRef.set(DesktopRuntimeState.Preparing)
        try {
            require(System.getProperty("os.name").lowercase().contains("windows")) {
                "WindowsNodeSupervisor 仅支持 Windows 宿主"
            }
            require(config.nodeExe.isFile) { "node.exe 不存在: ${config.nodeExe.absolutePath}" }
            require(File(config.scriptDir, "main.js").isFile) {
                "入口缺失: ${File(config.scriptDir, "main.js").absolutePath}"
            }
            // 与 Android 端一致：核心不随包内置，缺失时在线下载安装到运行时目录
            config.ensureCore(config.scriptDir)
            prepareDataHome(config.dataHome)
            port = config.fixedPort ?: reserveFreePort()
            identity = "desktop-" + UUID.randomUUID()
            this.config = config
        } catch (t: Throwable) {
            return fail(t.message ?: "准备阶段失败")
        }

        stateRef.set(DesktopRuntimeState.Starting)
        val pb = ProcessBuilder(config.nodeExe.absolutePath, File(config.scriptDir, "main.js").absolutePath)
        pb.directory(config.dataHome)
        // 计划 7.3 节要求显式注入的环境变量；路径一律由参数注入，不在 JS 内拼接盘符。
        pb.environment().apply {
            put("DANMU_API_PORT", port.toString())
            put("DANMU_API_HOST", "127.0.0.1")
            put("DANMU_API_HOME", config.dataHome.absolutePath)
            put("DANMU_API_VARIANT", config.variant)
            put("DANMU_API_RUNTIME_IDENTITY", identity)
            put("HOME", config.dataHome.absolutePath)
            put("TEMP", File(config.dataHome, "tmp").absolutePath)
            put("TMP", File(config.dataHome, "tmp").absolutePath)
            put("NODE_COMPILE_CACHE", File(config.dataHome, "compile-cache").absolutePath)
        }
        val logsDir = File(config.dataHome, "logs")
        pb.redirectOutput(File(logsDir, "node-stdout.log"))
        pb.redirectError(File(logsDir, "node-stderr.log"))

        val proc = try {
            pb.start()
        } catch (t: Throwable) {
            return fail("node.exe 启动失败: ${t.message}")
        }
        process = proc

        val deadline = System.currentTimeMillis() + config.startupTimeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!proc.isAlive) {
                return fail(
                    "node.exe 提前退出，exitCode=${proc.exitValue()}，stderr 尾部:\n" +
                        tail(File(logsDir, "node-stderr.log"))
                )
            }
            val ready = runCatching { probeHealth(config, proc) }.getOrDefault(false)
            if (ready) {
                stateRef.set(DesktopRuntimeState.Running)
                return snapshot
            }
            Thread.sleep(300)
        }
        return fail(
            "健康检查超时（${config.startupTimeoutMs}ms），stderr 尾部:\n" +
                tail(File(logsDir, "node-stderr.log"))
        )
    }

    /**
     * 优雅关闭：loopback /__shutdown → 等待子进程退出 → 超时则 taskkill 强制终止。
     * Stopped 的判定：子进程确认退出且端口已释放（旧 runtime identity 不可达）。
     */
    @Synchronized
    fun stop(): RuntimeSnapshot {
        val current = stateRef.get()
        if (current == DesktopRuntimeState.Stopped) return snapshot
        stateRef.set(DesktopRuntimeState.Stopping)
        val cfg = config
        val proc = process
        if (proc != null && proc.isAlive) {
            if (current == DesktopRuntimeState.Running) {
                runCatching {
                    client.send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/__shutdown"))
                            .timeout(Duration.ofSeconds(3))
                            .GET()
                            .build(),
                        HttpResponse.BodyHandlers.discarding(),
                    )
                }
                val graceful = proc.waitFor(cfg?.shutdownTimeoutMs ?: 10_000, TimeUnit.MILLISECONDS)
                if (!graceful) {
                    forceKill(proc)
                }
            } else {
                forceKill(proc)
            }
        }
        if (proc != null && proc.isAlive) {
            return fail("停止超时：node.exe 仍存活（pid=${proc.pid()}）")
        }
        if (port > 0 && !isPortFree(port)) {
            return fail("停止后端口 $port 仍被占用，旧 runtime identity 未确认消失")
        }
        process = null
        stateRef.set(DesktopRuntimeState.Stopped)
        return snapshot
    }

    /** Running 要求：身份、端口、PID、工作目录与健康接口逐项一致（计划 7.2 节）。 */
    private fun probeHealth(config: StartConfig, proc: Process): Boolean {
        val response = client.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/__health"))
                .timeout(Duration.ofMillis(800))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        if (response.statusCode() != 200) return false
        val body = response.body()
        if (jsonQuoted(body, "runtimeIdentity") != identity) return false
        if (jsonInt(body, "main") != port) return false
        if (jsonInt(body, "pid")?.toLong() != proc.pid().toLong()) return false
        val resolvedHome = jsonQuoted(body, "resolvedHome")?.let(::jsonUnescape) ?: return false
        if (File(resolvedHome).canonicalPath != config.dataHome.canonicalPath) return false
        val cwd = jsonQuoted(body, "cwd")?.let(::jsonUnescape) ?: return false
        if (File(cwd).canonicalPath != config.dataHome.canonicalPath) return false
        return true
    }

    private fun prepareDataHome(dataHome: File) {
        File(dataHome, "config").mkdirs()
        File(dataHome, "logs").mkdirs()
        File(dataHome, "tmp").mkdirs()
        File(dataHome, "compile-cache").mkdirs()
        // 运行时服务会用 $DANMU_API_HOME/config/.env 覆盖进程环境变量；
        // 清除其中可能的 DANMU_API_PORT 行，保证端口由宿主环境变量决定。
        val envFile = File(dataHome, "config/.env")
        if (!envFile.exists()) {
            envFile.writeText("# danmu desktop: 端口与身份由宿主环境变量注入\n")
        } else {
            val sanitized = envFile.readLines().filterNot { it.contains("DANMU_API_PORT") }
            envFile.writeText(sanitized.joinToString(System.lineSeparator()) + System.lineSeparator())
        }
    }

    private fun reserveFreePort(): Int {
        ServerSocket().use { socket ->
            socket.bind(InetSocketAddress("127.0.0.1", 0))
            return socket.localPort
        }
    }

    private fun isPortFree(target: Int): Boolean {
        return try {
            ServerSocket().use { it.bind(InetSocketAddress("127.0.0.1", target)) }
            true
        } catch (_: IOException) {
            false
        }
    }

    private fun forceKill(proc: Process) {
        runCatching {
            ProcessBuilder("taskkill", "/PID", proc.pid().toString(), "/T", "/F")
                .start()
                .waitFor(10, TimeUnit.SECONDS)
        }
        proc.waitFor(5, TimeUnit.SECONDS)
    }

    private fun fail(reason: String): RuntimeSnapshot {
        failureReason = reason
        process?.let { proc ->
            if (proc.isAlive) forceKill(proc)
        }
        stateRef.set(DesktopRuntimeState.Failed)
        return snapshot
    }

    private fun tail(file: File, maxLines: Int = 40): String {
        if (!file.isFile) return "(无 stderr 日志)"
        return try {
            file.readLines().takeLast(maxLines).joinToString("\n")
        } catch (_: IOException) {
            "(读取 stderr 日志失败)"
        }
    }

    private fun jsonQuoted(body: String, key: String): String? {
        return Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
            .find(body)?.groupValues?.get(1)
    }

    private fun jsonInt(body: String, key: String): Int? {
        return Regex("\"$key\"\\s*:\\s*(-?\\d+)")
            .find(body)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun jsonUnescape(raw: String): String {
        return raw
            .replace("\\\\", "\u0000")
            .replace("\\\"", "\"")
            .replace("\\/", "/")
            .replace("\u0000", "\\")
    }
}
