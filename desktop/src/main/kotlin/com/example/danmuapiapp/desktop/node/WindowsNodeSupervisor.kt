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
    /** nodejs-project 目录；与 Android 普通模式一致，它同时是 DANMU_API_HOME（服务端默认取脚本目录）。 */
    val scriptDir: File,
    /** 服务端口：与 Android/核心默认一致，写入 config/.env（DANMU_API_PORT），不在进程环境注入。 */
    val port: Int = DEFAULT_PORT,
    /** 监听地址：与 Android 默认 listenMode=Ipv4Only 一致（0.0.0.0，局域网可访问）。 */
    val listenHost: String = DEFAULT_LISTEN_HOST,
    /** 核心变体：与 Android 默认一致。 */
    val variant: String = "stable",
    /**
     * Desktop 对核心 ADMIN_TOKEN 的显式覆盖。
     * null 表示保留现有 .env 值；空字符串表示明确移除该配置。
     */
    val adminToken: String? = null,
    /** 安装身份文件（持久化，等价 Android RuntimeIdentityStore）。 */
    val identityFile: File,
    val startupTimeoutMs: Long = 30_000,
    val shutdownTimeoutMs: Long = 10_000,
    /** 核心准备策略由上层核心管理流程显式注入；启动服务本身不得触发远程下载。 */
    val ensureCore: (File) -> Unit = {},
    /** Desktop 宿主生命周期诊断；默认不写日志，测试夹具可保持纯进程监督。 */
    val lifecycleLog: (String) -> Unit = {},
    ) {
    companion object {
        /** 与 Android 端及核心默认端口一致（config/.env 的 DANMU_API_PORT）。 */
        const val DEFAULT_PORT = 9321

        /** 与 Android 默认监听模式（RuntimeListenMode.Ipv4Only）一致。 */
        const val DEFAULT_LISTEN_HOST = "0.0.0.0"
    }
}

data class RuntimeSnapshot(
    val state: DesktopRuntimeState,
    val port: Int? = null,
    val pid: Long? = null,
    val runtimeIdentity: String? = null,
    val failureReason: String? = null,
)

/**
 * W-0003/W-0203 进程监督器。与 Android NodeService 的基础约定对齐：
 * - 端口/监听/变体写 `$HOME(config)/.env`（服务端从 .env 读取，.env 覆盖进程环境变量）；
 * - TOKEN 不注入：无用户配置时沿用核心 envs.js 默认值（87654321）；
 * - DANMU_API_RUNTIME_IDENTITY 注入持久安装身份；
 * - TMPDIR/HOME/NODE_COMPILE_CACHE 显式注入，避免写到程序安装目录。
 * Running 判定：进程存活 + 端口 + 健康接口身份/端口/PID/工作目录一致；
 * Stopped 判定：进程退出 + 端口释放（旧身份不可达）。
 */
class WindowsNodeSupervisor {

    private val stateRef = AtomicReference(DesktopRuntimeState.Stopped)
    private var process: Process? = null
    private var port: Int = -1
    private var identity: String = ""
    private var config: StartConfig? = null
    private var failureReason: String? = null
    private var lifecycleLog: (String) -> Unit = {}

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
        lifecycleLog = config.lifecycleLog
        lifecycleLog("WindowsNodeSupervisor.start 请求：端口=${config.port}，变体=${config.variant}")
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
            // 核心准备由显式注入的 hook 决定；生产控制器默认不联网下载核心。
            config.ensureCore(config.scriptDir)
            identity = ensureIdentity(config.identityFile)
            prepareRuntimeDirs(config)
            port = config.port
            this.config = config
            preflightPort(config)
        } catch (t: Throwable) {
            return fail(t.message ?: "准备阶段失败")
        }

        stateRef.set(DesktopRuntimeState.Starting)
        val scriptDir = config.scriptDir
        val pb = ProcessBuilder(config.nodeExe.absolutePath, File(scriptDir, "main.js").absolutePath)
        pb.directory(scriptDir)
        // 对齐 Android NodeRuntimeEnv：只注入运行环境变量，业务配置一律走 .env
        pb.environment().apply {
            put("DANMU_API_RUNTIME_IDENTITY", identity)
            put("HOME", scriptDir.parentFile?.absolutePath ?: scriptDir.absolutePath)
            put("TEMP", File(scriptDir, "tmp").absolutePath)
            put("TMP", File(scriptDir, "tmp").absolutePath)
            put("NODE_COMPILE_CACHE", File(scriptDir, "compile-cache").absolutePath)
        }
        val logsDir = File(scriptDir, "logs")
        pb.redirectOutput(File(logsDir, "node-stdout.log"))
        pb.redirectError(File(logsDir, "node-stderr.log"))

        val proc = try {
            pb.start()
        } catch (t: Throwable) {
            return fail("node.exe 启动失败: ${t.message}")
        }
        process = proc
        lifecycleLog("node.exe 已启动：pid=${proc.pid()}")

        val deadline = System.currentTimeMillis() + config.startupTimeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!proc.isAlive) {
                val reason =
                    "node.exe 提前退出，exitCode=${proc.exitValue()}，stderr 尾部:\n" +
                        tail(File(logsDir, "node-stderr.log"))
                lifecycleLog(reason)
                return fail(reason)
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
    fun stop(reason: String = "user"): RuntimeSnapshot {
        lifecycleLog("WindowsNodeSupervisor.stop 请求：reason=$reason，state=${stateRef.get()}，pid=${process?.pid()}")
        val current = stateRef.get()
        if (current == DesktopRuntimeState.Stopped) return snapshot
        stateRef.set(DesktopRuntimeState.Stopping)
        val cfg = config
        val proc = process
        if (proc != null && proc.isAlive) {
            if (current == DesktopRuntimeState.Running) {
                val shutdownResult = runCatching {
                    client.send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/__shutdown"))
                            .timeout(Duration.ofSeconds(3))
                            .GET()
                            .build(),
                        HttpResponse.BodyHandlers.discarding(),
                    )
                }
                shutdownResult.onSuccess { response ->
                    lifecycleLog("已发送 /__shutdown：status=${response.statusCode()}")
                }.onFailure { error ->
                    lifecycleLog("发送 /__shutdown 失败：${error.message ?: error::class.java.simpleName}")
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
        val exitCode = runCatching { proc?.exitValue() }.getOrNull()
        lifecycleLog("node.exe 已退出：pid=${proc?.pid()}，exitCode=$exitCode，stopReason=$reason")
        process = null
        stateRef.set(DesktopRuntimeState.Stopped)
        return snapshot
    }

    /**
     * 应用进程退出时的强制清理路径。
     *
     * 这里故意不请求 /__shutdown：应用退出不是用户的“停止服务”操作，不能再依赖 Node
     * 业务 HTTP 处理器。只终止本监督器创建的子进程，避免退出时把“应用退出”误记成 API
     * 触发的正常停止，也避免 Node 在 Desktop JVM 退出后变成孤儿进程。
     */
    @Synchronized
    fun forceStop(reason: String = "application-exit"): RuntimeSnapshot {
        lifecycleLog("WindowsNodeSupervisor.forceStop 请求：reason=$reason，state=${stateRef.get()}，pid=${process?.pid()}")
        val proc = process
        stateRef.set(DesktopRuntimeState.Stopping)
        if (proc != null && proc.isAlive) {
            lifecycleLog("应用退出清理不发送 /__shutdown，直接终止 node.exe：pid=${proc.pid()}")
            forceKill(proc)
        }
        if (proc != null && proc.isAlive) {
            return fail("应用退出时无法终止 node.exe（pid=${proc.pid()}）")
        }
        if (port > 0 && !isPortFree(port)) {
            return fail("应用退出后端口 $port 仍被占用")
        }
        val exitCode = runCatching { proc?.exitValue() }.getOrNull()
        lifecycleLog("应用退出清理完成：pid=${proc?.pid()}，exitCode=$exitCode")
        process = null
        stateRef.set(DesktopRuntimeState.Stopped)
        return snapshot
    }

    /** 返回真实的子进程退出诊断；进程仍存活时返回 null。 */
    @Synchronized
    fun livenessFailure(): String? {
        val proc = process ?: return "Node 子进程句柄不存在"
        if (proc.isAlive) return null
        val code = runCatching { proc.exitValue() }.getOrNull()
        val cfg = config
        val stderr = cfg?.let { tail(File(it.scriptDir, "logs/node-stderr.log")) }.orEmpty()
        val reason = buildString {
            append("Node 子进程已退出，exitCode=").append(code ?: "未知")
            if (stderr.isNotBlank()) append("，stderr 尾部:\n").append(stderr)
        }
        lifecycleLog(reason)
        failureReason = reason
        stateRef.set(DesktopRuntimeState.Failed)
        return reason
    }

    /**
     * 端口占用预检（对齐 Android NormalStartPreflightPolicy 的对外语义）：
     * - 端口空闲：直接通过；
     * - 同身份实例占用（如开机自启的无窗口进程）：优雅接管——/__shutdown 远端实例并等待端口释放；
     * - 其他实例占用：明确失败。
     */
    private fun preflightPort(config: StartConfig) {
        while (!isPortFree(config.port)) {
            val health = runCatching { healthBody(config.port) }.getOrNull()
            val sameIdentity = health != null && jsonQuoted(health, "runtimeIdentity") == identity
            if (!sameIdentity) {
                throw IOException("端口 ${config.port} 已有其他实例在运行，请先停止外部进程后再启动")
            }
            runCatching {
                client.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:${config.port}/__shutdown"))
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.discarding(),
                )
            }
            val deadline = System.currentTimeMillis() + 10_000
            while (!isPortFree(config.port) && System.currentTimeMillis() < deadline) {
                Thread.sleep(300)
            }
            if (!isPortFree(config.port)) {
                throw IOException("端口 ${config.port} 被本应用实例占用且无法释放，请关闭该实例后重试")
            }
        }
    }

    /** 无感自启模式用：node 子进程是否仍存活。 */
    fun isChildAlive(): Boolean = process?.isAlive ?: false

    /** Running 要求：身份、端口、PID、工作目录与健康接口逐项一致（计划 7.2 节）。 */
    private fun probeHealth(config: StartConfig, proc: Process): Boolean {
        val body = healthBody(config.port) ?: return false
        if (jsonQuoted(body, "runtimeIdentity") != identity) return false
        if (jsonInt(body, "main") != config.port) return false
        if (jsonInt(body, "pid")?.toLong() != proc.pid().toLong()) return false
        val resolvedHome = jsonQuoted(body, "resolvedHome")?.let(::jsonUnescape) ?: return false
        if (File(resolvedHome).canonicalPath != config.scriptDir.canonicalPath) return false
        val cwd = jsonQuoted(body, "cwd")?.let(::jsonUnescape) ?: return false
        if (File(cwd).canonicalPath != config.scriptDir.canonicalPath) return false
        return true
    }

    private fun healthBody(port: Int): String? {
        val response = client.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/__health"))
                .timeout(Duration.ofMillis(800))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        if (response.statusCode() != 200) return null
        return response.body()
    }

    /**
     * 运行时目录准备，语义对齐 Android NodeProjectManager 写 config/.env：
     * 覆盖 DANMU_API_PORT / DANMU_API_HOST / DANMU_API_VARIANT，保留 TOKEN 与其他键，
     * 并创建 .cache / config / logs / tmp / compile-cache 目录。
     */
    private fun prepareRuntimeDirs(config: StartConfig) {
        val home = config.scriptDir
        listOf("config", "logs", ".cache", "tmp", "compile-cache").forEach {
            File(home, it).mkdirs()
        }
        val envFile = File(home, "config/.env")
        val existing = if (envFile.isFile) {
            envFile.readLines(Charsets.UTF_8)
        } else {
            emptyList()
        }
        val overrides = linkedMapOf(
            "DANMU_API_PORT" to config.port.toString(),
            "DANMU_API_HOST" to config.listenHost,
            "DANMU_API_VARIANT" to config.variant,
        )
        val seen = mutableSetOf<String>()
        val lines = existing.mapNotNull { rawLine ->
            val key = rawLine.substringBefore('=').trim().uppercase()
            when {
                key == "ADMIN_TOKEN" && config.adminToken != null -> {
                    seen += key
                    config.adminToken.takeIf { it.isNotBlank() }?.let { "$key=$it" }
                }
                overrides.containsKey(key) -> {
                    seen += key
                    "${key}=${overrides.getValue(key)}"
                }
                rawLine.isNotBlank() -> rawLine
                else -> null
            }
        }.toMutableList()
        overrides.forEach { (key, value) ->
            if (key !in seen) lines += "$key=$value"
        }
        if (config.adminToken != null && config.adminToken.isNotBlank() && "ADMIN_TOKEN" !in seen) {
            lines += "ADMIN_TOKEN=${config.adminToken}"
        }
        envFile.parentFile?.mkdirs()
        envFile.writeText(lines.joinToString(System.lineSeparator()) + System.lineSeparator())
    }

    /** 安装身份持久化（等价 Android RuntimeIdentityStore：一次生成，长期复用）。 */
    private fun ensureIdentity(identityFile: File): String {
        identityFile.parentFile?.mkdirs()
        val existing = if (identityFile.isFile) {
            identityFile.readText(Charsets.UTF_8).trim()
        } else {
            ""
        }
        if (existing.isNotBlank()) return existing
        val created = "desktop-" + UUID.randomUUID()
        identityFile.writeText(created)
        return created
    }

    private fun isPortFree(target: Int): Boolean {
        return try {
            // Bind the IPv4 wildcard address, not only loopback. A service listening on
            // 0.0.0.0 must make the port unavailable to the supervisor as well.
            ServerSocket().use { socket ->
                socket.reuseAddress = false
                socket.bind(InetSocketAddress("0.0.0.0", target))
            }
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
