package com.example.danmuapiapp.desktop.runtime

import com.example.danmuapiapp.desktop.node.DesktopCoreInstaller
import com.example.danmuapiapp.desktop.node.DesktopRuntimeState
import com.example.danmuapiapp.desktop.node.GithubProxyCatalog
import com.example.danmuapiapp.desktop.node.StartConfig
import com.example.danmuapiapp.desktop.node.WindowsNodeSupervisor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.URI
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** 概览页展示的服务阶段（与监督器状态机一一对应）。 */
enum class ServicePhase { Stopped, Preparing, Starting, Running, Stopping, Failed }

data class ServiceUiState(
    val phase: ServicePhase = ServicePhase.Stopped,
    val message: String = "服务未运行",
    val port: Int? = null,
    val pid: Long? = null,
    val runtimeIdentity: String? = null,
) {
    val isRunning: Boolean get() = phase == ServicePhase.Running
    val isStopped: Boolean get() = phase == ServicePhase.Stopped
    val isFailed: Boolean get() = phase == ServicePhase.Failed


    val isBusy: Boolean get() = phase == ServicePhase.Preparing || phase == ServicePhase.Starting || phase == ServicePhase.Stopping
    val canStart: Boolean get() = phase == ServicePhase.Stopped || phase == ServicePhase.Failed
    val canStop: Boolean get() = phase == ServicePhase.Running
}

/**
 * 概览页的运行时控制器：串行化 启动/停止/重启 操作，把监督器状态映射为 UI 状态。
 * 首启自动完成：内嵌运行时解压到运行目录 + 核心在线下载安装（走用户选择的 GitHub 线路）。
 * 端口/监听/变体等业务配置由监督器写入 config/.env，与 Android 端同一机制。
 */
class DesktopRuntimeController(
    val settings: DesktopSettings = DesktopSettings(DesktopSettings.defaultSettingsFile()),
    private val supervisorFactory: () -> WindowsNodeSupervisor = { WindowsNodeSupervisor() },
    private val coreInstaller: (File, String) -> Unit = { scriptDir, proxyId ->
        val cacheDir = DesktopPaths(settings.runtimeRootOverride?.let(::File)).coreCacheDir
        DesktopCoreInstaller.ensureCoreInstalled(scriptDir, cacheDir, proxyId)
    },
    private val runtimeExtractor: (File) -> Unit = { target ->
        if (!ClasspathRuntimeExtractor.isRuntimeExtracted(target)) {
            ClasspathRuntimeExtractor.extract(target)
        }
    },
) {

    val paths: DesktopPaths = DesktopPaths(settings.runtimeRootOverride?.let(::File))

    private val worker: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "danmu-desktop-runtime").apply { isDaemon = true }
    }

    private val _state = MutableStateFlow(ServiceUiState())
    val state: StateFlow<ServiceUiState> = _state.asStateFlow()

    private var supervisor: WindowsNodeSupervisor? = null

    /** 当前后台实例被认领时记录实际端口，停止时不能回退到固定默认端口。 */
    @Volatile
    private var externalPort: Int? = null

    fun start() {
        submit { doStart() }
    }

    fun stop() {
        submit { doStop("服务已停止") }
    }

    fun restart() {
        submit {
            doStop("正在重启…")
            doStart()
        }
    }

    /** Android 端配置保存语义：端口/监听地址变更时，运行中服务必须完整重启。 */
    fun applyRuntimeConfiguration() {
        submit {
            if (_state.value.phase == ServicePhase.Running) {
                doStop("配置已保存，正在重启服务…")
                doStart()
            } else {
                update { copy(message = "配置已保存，下次启动服务时生效") }
            }
        }
    }

    /** 端口/监听/变体当前显示值，供设置页展示“实际将使用”的配置。 */
    fun configuredRuntime(): DesktopRuntimeConfig {
        return DesktopRuntimeConfigResolver.resolve(
            settings,
            File(paths.runtimeDir, "nodejs-project"),
        )
    }

    /** 关闭窗口时的兜底：确认 node.exe 不会残留（优雅关闭，必要时强杀）。 */
    fun shutdown() {
        worker.shutdown()
        supervisor?.let { runCatching { it.stop() } }
        supervisor = null
    }

    /** 无感自启模式用：node 子进程是否仍存活。 */
    fun isChildAlive(): Boolean = supervisor?.isChildAlive() ?: false

    private fun settingsFile(): File = DesktopSettings.defaultSettingsFile()

    private fun submit(block: () -> Unit) {
        worker.execute {
            try {
                block()
            } catch (t: Throwable) {
                update { copy(phase = ServicePhase.Failed, message = t.message ?: t.toString()) }
            }
        }
    }

    private fun doStart() {
        val current = _state.value
        if (!current.canStart) return
        update { copy(phase = ServicePhase.Preparing, message = "正在准备运行时（解压随包资源、检查核心）…") }
        val runtimeDir = paths.runtimeDir
        runtimeExtractor(runtimeDir)
        val scriptDir = File(runtimeDir, "nodejs-project")
        val nodeExe = File(runtimeDir, "node.exe")
        val runtimeConfig = DesktopRuntimeConfigResolver.resolve(settings, scriptDir)
        if (!nodeExe.isFile) {
            update {
                copy(
                    phase = ServicePhase.Failed,
                    message = "缺少 node.exe：当前构建未携带运行时（打包时需提供 -PdanmuNodeExe）",
                )
            }
            return
        }
        try {
            coreInstaller(scriptDir, settings.githubProxyId)
        } catch (t: Throwable) {
            update {
                copy(
                    phase = ServicePhase.Failed,
                    message = "核心安装失败：${t.message}（可在 设置 → GitHub 线路 中选择加速镜像后重试）",
                )
            }
            return
        }

        update { copy(phase = ServicePhase.Starting, message = "正在启动 Node 服务…") }
        val supervisor = supervisorFactory().also { this.supervisor = it }
        val snapshot = supervisor.start(
            StartConfig(
                nodeExe = nodeExe,
                scriptDir = scriptDir,
                port = runtimeConfig.port,
                listenHost = runtimeConfig.listenHost,
                variant = runtimeConfig.variant,
                identityFile = File(settingsFile().parentFile, "instance-id"),
            )
        )
        when (snapshot.state) {
            DesktopRuntimeState.Running -> update {
                copy(
                    phase = ServicePhase.Running,
                    message = "服务运行中",
                    port = snapshot.port,
                    pid = snapshot.pid,
                    runtimeIdentity = snapshot.runtimeIdentity,
                )
            }
            else -> update {
                copy(
                    phase = ServicePhase.Failed,
                    message = snapshot.failureReason ?: "启动失败（未知原因，详见运行目录 logs）",
                    port = null,
                    pid = null,
                    runtimeIdentity = null,
                )
            }
        }
    }

    private fun doStop(finishedMessage: String) {
        if (supervisor == null) {
            // 本进程没有自己的子进程：Running 状态来自后台实例（开机自启/headless），直接远端停止
            if (externalInstance && _state.value.phase == ServicePhase.Running) {
                stopExternalInstance()
            } else {
                update { copy(phase = ServicePhase.Stopped, message = finishedMessage) }
            }
            return
        }
        if (_state.value.phase != ServicePhase.Running && _state.value.phase != ServicePhase.Failed) return
        update { copy(phase = ServicePhase.Stopping, message = "正在停止 Node 服务…") }
        val snapshot = supervisor!!.stop()
        supervisor = null
        if (snapshot.state == DesktopRuntimeState.Stopped) {
            update {
                copy(
                    phase = ServicePhase.Stopped,
                    message = finishedMessage,
                    port = null,
                    pid = null,
                    runtimeIdentity = null,
                )
            }
        } else {
            update {
                copy(
                    phase = ServicePhase.Failed,
                    message = snapshot.failureReason ?: "停止异常",
                    port = null,
                    pid = null,
                    runtimeIdentity = null,
                )
            }
        }
    }

    private fun update(transform: ServiceUiState.() -> ServiceUiState) {
        _state.value = _state.value.transform()
    }

    // ---- 后台实例探测（headless 自启的服务由 UI 接管显示与控制）----

    @Volatile
    private var externalInstance = false

    private val probeClient = java.net.http.HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofMillis(600))
        .build()

    init {
        // 应用打开时探测：若同安装身份的服务已在后台运行（开机自启），直接显示为运行中
        submit { detectExternalInstance() }
    }

    private fun detectExternalInstance() {
        if (_state.value.phase != ServicePhase.Stopped) return
        val identityFile = File(settingsFile().parentFile, "instance-id")
        val myIdentity = if (identityFile.isFile) identityFile.readText(Charsets.UTF_8).trim() else return
        if (myIdentity.isBlank()) return
        val runtimeConfig = DesktopRuntimeConfigResolver.resolve(
            settings,
            File(paths.runtimeDir, "nodejs-project"),
        )
        val body = probeHealthBody(runtimeConfig.port) ?: return
        val identity = jsonQuoted(body, "runtimeIdentity") ?: return
        // 安全边界：身份 + 配置端口 + envHome + resolvedHome + cwd 全部匹配，
        // 终端直接启动、其他目录实例、局域网设备和同端口外部服务都不会被认领。
        val owned = RuntimeOwnership.isOwned(
            expectedIdentity = myIdentity,
            expectedPort = runtimeConfig.port,
            expectedHome = File(paths.runtimeDir, "nodejs-project"),
            health = RuntimeOwnership.Health(
                runtimeIdentity = identity,
                port = jsonInt(body, "main"),
                envHome = jsonQuoted(body, "envHome")?.let(::jsonUnescape),
                resolvedHome = jsonQuoted(body, "resolvedHome")?.let(::jsonUnescape),
                cwd = jsonQuoted(body, "cwd")?.let(::jsonUnescape),
            ),
        )
        if (!owned) return
        externalInstance = true
        externalPort = runtimeConfig.port
        val pid = jsonInt(body, "pid")?.toLong()
        update {
            copy(
                phase = ServicePhase.Running,
                message = "服务运行中（后台实例，停止/重启可直接操作）",
                port = runtimeConfig.port,
                pid = pid,
                runtimeIdentity = identity,
            )
        }
    }

    private fun stopExternalInstance() {
        val port = externalPort ?: return update {
            copy(phase = ServicePhase.Failed, message = "未找到可验证的后台实例，已取消停止")
        }
        update { copy(phase = ServicePhase.Stopping, message = "正在停止后台服务…") }
        runCatching {
            probeClient.send(
                java.net.http.HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/__shutdown"))
                    .timeout(java.time.Duration.ofSeconds(3))
                    .GET()
                    .build(),
                java.net.http.HttpResponse.BodyHandlers.discarding(),
            )
        }
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (probeHealthBody(port) == null) break
            Thread.sleep(300)
        }
        externalInstance = false
        externalPort = null
        update {
            copy(
                phase = ServicePhase.Stopped,
                message = "服务已停止",
                port = null,
                pid = null,
                runtimeIdentity = null,
            )
        }
    }

    private fun probeHealthBody(port: Int): String? {
        return runCatching {
            val response = probeClient.send(
                java.net.http.HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/__health"))
                    .timeout(java.time.Duration.ofMillis(800))
                    .GET()
                    .build(),
                java.net.http.HttpResponse.BodyHandlers.ofString(),
            )
            if (response.statusCode() != 200) null else response.body()
        }.getOrNull()
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
