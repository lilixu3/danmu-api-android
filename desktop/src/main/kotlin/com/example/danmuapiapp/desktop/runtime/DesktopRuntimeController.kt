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
        val cacheDir = DesktopSettings.defaultSettingsFile().parentFile
            ?.let { File(it, "core-cache") }
            ?: File(System.getProperty("java.io.tmpdir"), "danmu-desktop-core-cache")
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
                port = StartConfig.DEFAULT_PORT,
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
        val active = supervisor ?: return update { copy(phase = ServicePhase.Stopped, message = finishedMessage) }
        if (_state.value.phase != ServicePhase.Running && _state.value.phase != ServicePhase.Failed) return
        update { copy(phase = ServicePhase.Stopping, message = "正在停止 Node 服务…") }
        val snapshot = active.stop()
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
}
