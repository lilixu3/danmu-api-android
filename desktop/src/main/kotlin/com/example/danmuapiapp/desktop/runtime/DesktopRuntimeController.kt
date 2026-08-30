package com.example.danmuapiapp.desktop.runtime

import com.example.danmuapiapp.desktop.node.DesktopCoreInstaller
import com.example.danmuapiapp.desktop.node.DesktopRuntimeState
import com.example.danmuapiapp.desktop.node.GithubProxyCatalog
import com.example.danmuapiapp.desktop.node.StartConfig
import com.example.danmuapiapp.desktop.node.WindowsNodeSupervisor
import com.example.danmuapiapp.desktop.node.WindowsProcessTerminator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.URI
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** 概览页展示的服务阶段（与监督器状态机一一对应）。 */
enum class ServicePhase {
    Stopped,
    Preparing,
    Starting,
    Running,
    Stopping,
    /** The service cannot start until the user manually installs a core from the Core page. */
    CoreSetupRequired,
    Failed,
}

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
    val needsCoreSetup: Boolean get() = phase == ServicePhase.CoreSetupRequired

    val isBusy: Boolean get() = phase == ServicePhase.Preparing || phase == ServicePhase.Starting || phase == ServicePhase.Stopping
    val canStart: Boolean get() = phase == ServicePhase.Stopped || phase == ServicePhase.CoreSetupRequired || phase == ServicePhase.Failed
    val canStop: Boolean get() = phase == ServicePhase.Running
}

/**
 * 概览页的运行时控制器：串行化 启动/停止/重启 操作，把监督器状态映射为 UI 状态。
 * 首启只解压内嵌运行时；核心不自动下载，缺失时发布 CoreSetupRequired，要求用户在核心页手动准备。
 * 端口/监听/变体等业务配置由监督器写入 config/.env，与 Android 端同一机制。
 */
class DesktopRuntimeController(
    val settings: DesktopSettings = DesktopSettings(DesktopSettings.defaultSettingsFile()),
    private val supervisorFactory: () -> WindowsNodeSupervisor = { WindowsNodeSupervisor() },
    /** Test-only/manual preparation hook. Production startup never downloads a core. */
    private val coreInstaller: ((File, String) -> Unit)? = null,
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
        submit { doStop("服务已停止", reason = "user-stop") }
    }

    fun restart() {
        submit {
            doStop("正在重启…", reason = "user-restart")
            doStart()
        }
    }

    /** Android 端配置保存语义：端口/监听地址变更时，运行中服务必须完整重启。 */
    fun applyRuntimeConfiguration() {
        submit {
            if (_state.value.phase == ServicePhase.Running) {
                doStop("配置已保存，正在重启服务…", reason = "runtime-config-restart")
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

    /**
     * 关闭窗口时的兜底：确认 node.exe 不会残留（优雅关闭，必要时强杀）。
     * ShutdownHook 可能在 Compose 已开始退出后执行，因此这里不能依赖 worker；所有结果都写入
     * 运行时生命周期日志，避免把真正的停止原因静默吞掉。
     */
    fun shutdown() {
        worker.shutdown()
        val active = supervisor
        if (active != null) {
            val result = runCatching { active.forceStop(reason = "controller.shutdown") }
            result.onFailure { error ->
                appendLifecycleLog(
                    "controller.shutdown 停止 Node 异常：${error.message ?: error::class.java.simpleName}",
                )
            }.onSuccess { snapshot ->
                if (snapshot.state != DesktopRuntimeState.Stopped) {
                    appendLifecycleLog(
                        "controller.shutdown 停止 Node 未完成：${snapshot.failureReason ?: snapshot.state}",
                    )
                }
            }
        }
        supervisor = null
    }

    /** 无感自启模式用：node 子进程是否仍存活。 */
    fun isChildAlive(): Boolean = supervisor?.isChildAlive() ?: false

    /**
     * 将本地 Node 的真实进程状态同步到 UI 状态。健康轮询只负责展示诊断，不能替代这里的
     * 进程监督；Node 消失后必须明确进入 Failed，且保留退出码与 stderr 尾部。
     */
    fun reconcileLiveness() {
        submit { doReconcileLiveness() }
    }

    private fun doReconcileLiveness() {
        if (_state.value.phase != ServicePhase.Running) return
        val active = supervisor ?: return
        val failure = active.livenessFailure() ?: return
        val snapshot = active.snapshot
        supervisor = null
        update {
            copy(
                phase = ServicePhase.Failed,
                message = failure,
                port = null,
                pid = snapshot.pid,
                runtimeIdentity = null,
            )
        }
        appendLifecycleLog("Node 子进程退出，服务状态已同步为 Failed：${failure.take(500)}")
    }

    private fun settingsFile(): File = DesktopSettings.defaultSettingsFile()

    private fun appendLifecycleLog(message: String) {
        val line = "${java.time.LocalDateTime.now()}  $message${System.lineSeparator()}"
        runCatching {
            paths.logsDir.mkdirs()
            File(paths.logsDir, "lifecycle.log").appendText(line, Charsets.UTF_8)
        }.onFailure { error ->
            System.err.println("Desktop 生命周期日志写入失败：${error.message ?: error::class.java.simpleName}；原消息=$message")
        }
    }

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
            // A non-null hook is reserved for tests/manual preparation. The production default is
            // null, so starting a service can never initiate a remote download.
            coreInstaller?.invoke(scriptDir, settings.githubProxyId)
        } catch (t: Throwable) {
            update {
                copy(
                    phase = ServicePhase.Failed,
                    message = "核心校验失败：${t.message}",
                )
            }
            return
        }
        val variantDir = File(scriptDir, "danmu_api_${runtimeConfig.variant}")
        if (!File(variantDir, "worker.js").isFile) {
            update {
                copy(
                    phase = ServicePhase.CoreSetupRequired,
                    message = "核心尚未准备：请先打开“核心”页面，选择 GitHub 线路并手动下载 ${runtimeConfig.variant} 核心",
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
                adminToken = settings.adminTokenOverride,
                identityFile = File(settingsFile().parentFile, "instance-id"),
                lifecycleLog = ::appendLifecycleLog,
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

    private fun doStop(finishedMessage: String, reason: String) {
        appendLifecycleLog("Desktop 请求停止服务：reason=$reason，phase=${_state.value.phase}，pid=${_state.value.pid}")
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
        val snapshot = supervisor!!.stop(reason = reason)
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
        val pid = jsonInt(body, "pid")?.toLong()?.takeIf { it > 0L } ?: return
        externalInstance = true
        externalPort = runtimeConfig.port
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
        val current = _state.value
        val pid = current.pid ?: return update {
            copy(phase = ServicePhase.Failed, message = "后台实例缺少可验证 PID，已取消停止")
        }
        val runtimeDir = File(paths.runtimeDir, "nodejs-project")
        val nodeExe = File(paths.runtimeDir, "node.exe")
        update { copy(phase = ServicePhase.Stopping, message = "正在停止后台服务…") }
        val result = WindowsProcessTerminator.terminateNodeTree(pid, nodeExe, runtimeDir)
        if (result.isFailure) {
            externalInstance = false
            externalPort = null
            val reason = result.exceptionOrNull()?.message ?: "后台服务停止失败"
            update { copy(phase = ServicePhase.Failed, message = reason) }
            appendLifecycleLog("停止后台实例失败：$reason")
            return
        }
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && !isPortFree(port)) {
            Thread.sleep(300)
        }
        if (!isPortFree(port)) {
            externalInstance = false
            externalPort = null
            val reason = "后台 Node 已终止，但端口 $port 仍被占用，拒绝报告为已停止"
            update { copy(phase = ServicePhase.Failed, message = reason) }
            appendLifecycleLog(reason)
            return
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
        appendLifecycleLog("已按用户请求终止后台 Node：pid=$pid，port=$port")
    }

    private fun isPortFree(port: Int): Boolean {
        return try {
            java.net.ServerSocket().use { socket ->
                socket.reuseAddress = false
                socket.bind(java.net.InetSocketAddress("0.0.0.0", port))
            }
            true
        } catch (_: java.io.IOException) {
            false
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
