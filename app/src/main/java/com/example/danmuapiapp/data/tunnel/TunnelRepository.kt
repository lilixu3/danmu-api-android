package com.example.danmuapiapp.data.tunnel

import com.example.danmuapiapp.data.network.newOutboundCall

import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.domain.repository.EnvConfigRepository
import com.example.danmuapiapp.data.service.NodeKeepAlivePrefs
import com.example.danmuapiapp.data.service.GithubProxyService
import com.example.danmuapiapp.data.remote.github.GithubRemoteService
import com.example.danmuapiapp.data.repository.runCatchingCancellable
import com.example.danmuapiapp.data.repository.useCancellableResponse
import com.example.danmuapiapp.data.service.NodeService
import com.example.danmuapiapp.domain.model.ServiceStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import com.example.danmuapiapp.data.service.RootShell
import com.example.danmuapiapp.data.service.RootAutoStartModule
import com.example.danmuapiapp.data.util.ShellUtils.shellQuote
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

data class TunnelActionResult(
    val ok: Boolean,
    val message: String,
    val pid: Long = 0L
)

data class FrpcRelease(
    val version: String,
    val downloadUrl: String
)

data class TunnelUiState(
    val settings: TunnelSettings = TunnelSettings(),
    val running: Boolean = false,
    val state: String = "stopped",
    val pid: Long = 0L,
    val since: Long = 0L,
    val restarts: Int = 0,
    val lastError: String = "",
    val kernelReady: Boolean = true,
    val kernelVersion: String = FRPC_KERNEL_VERSION,
    val linkState: TunnelLinkState = TunnelLinkState.Unknown,
    val serviceRunning: Boolean = false,
    val rootMode: Boolean = false,
    /** 当前弹幕 API 端口/Token：穿透本机目标与公网地址都跟随它们。 */
    val servicePort: Int = 9321,
    val serviceToken: String = ""
) {
    /** 运行中时的公网入口（不含 token 路径）。 */
    val publicBase: String get() = if (running) derivePublicAddress(settings) else ""
}

/**
 * 内网穿透统一入口：配置读写、普通/Root 双模式启停、状态与日志。
 *
 * 普通模式的 frpc 跑在 :node 前台服务里，这里只发 Intent + 读状态文件；
 * Root 模式直接走 RootShell。
 */
@Singleton
class TunnelRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtimeRepository: RuntimeRepository,
    private val envConfigRepository: EnvConfigRepository,
    private val okHttpClient: OkHttpClient,
    private val githubProxyService: GithubProxyService,
    private val githubRemoteService: GithubRemoteService
) {

    private val kernelUpdateMutex = Mutex()

    private val _state = MutableStateFlow(TunnelUiState())
    val state: StateFlow<TunnelUiState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 状态广播来自 :node 进程（普通模式）或 App 进程（Root 模式），被动刷新即可。 */
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            if (intent?.action != TunnelStore.ACTION_TUNNEL_STATUS) return
            scope.launch { refreshInternal() }
        }
    }

    init {
        runCatching {
            ContextCompat.registerReceiver(
                context,
                statusReceiver,
                IntentFilter(TunnelStore.ACTION_TUNNEL_STATUS),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) { refreshInternal() }

    suspend fun save(settings: TunnelSettings): TunnelActionResult =
        withContext(Dispatchers.IO) {
            // localPort 以 .env 里的真实端口为准（runtimeState.port 可能是旧缓存）
            val realPort = currentServicePort()
            val normalized = buildEffectiveFrpcConfig(settings, realPort)
            if (!TunnelStore.writeText(TunnelStore.configFile(context), normalized)) {
                return@withContext TunnelActionResult(false, "配置写入失败")
            }
            if (!TunnelStore.writeSettings(context, settings)) {
                return@withContext TunnelActionResult(false, "开关写入失败")
            }
            if (settings.autoStart && NodeKeepAlivePrefs.isRootMode(context)) {
                val bootScript = RootAutoStartModule.refreshInstalledServiceScript(context)
                if (!bootScript.ok) return@withContext TunnelActionResult(false, bootScript.message)
            }
            val wasRunning = _state.value.running
            if (!settings.enabled && wasRunning) {
                val stopped = stopBlocking()
                if (!stopped.ok) return@withContext stopped
            } else if (settings.enabled && wasRunning) {
                if (NodeKeepAlivePrefs.isRootMode(context)) {
                    val stopped = RootTunnel.stop(context)
                    if (!stopped.ok) return@withContext stopped
                    val started = RootTunnel.start(context)
                    if (!started.ok) return@withContext started
                } else {
                    sendTunnelAction(NodeService.ACTION_TUNNEL_STOP)
                    sendTunnelAction(NodeService.ACTION_TUNNEL_START)
                }
            }
            refreshInternal()
            TunnelActionResult(true, if (settings.enabled) "已保存并应用" else "已保存（未启用）")
        }

    /**
     * 服务端口变化后跟随：重写 frpc 配置里的 localPort；穿透在跑就按新模式重启。
     * 由「服务配置」保存端口后调用。
     */
    suspend fun syncServicePort(port: Int): TunnelActionResult = withContext(Dispatchers.IO) {
        val settings = TunnelStore.readSettings(context)
        if (!settings.enabled) return@withContext TunnelActionResult(true, "")
        val text = buildEffectiveFrpcConfig(settings, port)
        if (!TunnelStore.writeText(TunnelStore.configFile(context), text)) {
            return@withContext TunnelActionResult(false, "穿透端口配置写入失败")
        }
        if (_state.value.running) {
            if (NodeKeepAlivePrefs.isRootMode(context)) {
                val stopped = RootTunnel.stop(context)
                if (!stopped.ok) return@withContext stopped
                val started = RootTunnel.start(context)
                if (!started.ok) return@withContext started
            } else if (runtimeRepository.runtimeState.value.status == ServiceStatus.Running) {
                sendTunnelAction(NodeService.ACTION_TUNNEL_STOP)
                sendTunnelAction(NodeService.ACTION_TUNNEL_START)
            }
            // 服务正在重启时只改配置：NodeService 起来后会按“随服务启动”自动拉起
        }
        refreshInternal()
        TunnelActionResult(true, "内网穿透已跟随端口 $port")
    }

    suspend fun start(): TunnelActionResult = withContext(Dispatchers.IO) {
        startBlocking().also { refreshInternal() }
    }

    suspend fun stop(): TunnelActionResult = withContext(Dispatchers.IO) {
        stopBlocking().also { refreshInternal() }
    }

    /** 重启穿透：先等停止收敛，再启动。 */
    suspend fun restart(): TunnelActionResult = withContext(Dispatchers.IO) {
        refreshInternal()
        if (_state.value.running) {
            val stopped = stopBlocking()
            if (!stopped.ok) return@withContext stopped
            var waited = 0L
            while (waited < 8000L && _state.value.running) {
                Thread.sleep(300L)
                waited += 300L
                refreshInternal()
            }
        }
        startBlocking().also { refreshInternal() }
    }

    suspend fun readLog(lines: Int = 200): String =
        withContext(Dispatchers.IO) { TunnelStore.readLogTail(context, lines) }

    suspend fun clearLog(): TunnelActionResult = withContext(Dispatchers.IO) {
        // 兼容旧开机脚本创建的 root:root 0644 日志，仍然必须原地截断。
        val cleared = TunnelStore.clearLog(context) ||
            (NodeKeepAlivePrefs.isRootMode(context) && RootShell.exec(
                ": > ${shellQuote(TunnelStore.logFile(context).absolutePath)}", 6000L
            ).ok)
        refreshInternal()
        TunnelActionResult(cleared, if (cleared) "日志已清空" else "日志清空失败，请检查文件权限")
    }

    private fun startBlocking(): TunnelActionResult {
        val settings = TunnelStore.readSettings(context)
        if (!settings.enabled) return TunnelActionResult(false, "请先开启并保存内网穿透")
        val rootMode = NodeKeepAlivePrefs.isRootMode(context)
        if (!TunnelStore.kernelReady(context, rootMode)) {
            return TunnelActionResult(false, "缺少 frpc 内核（libfrpc.so），请更新 App")
        }
        if (rootMode) {
            return RootTunnel.start(context)
        }
        if (runtimeRepository.runtimeState.value.status != ServiceStatus.Running) {
            return TunnelActionResult(false, "请先启动服务，再开启穿透")
        }
        sendTunnelAction(NodeService.ACTION_TUNNEL_START)
        return TunnelActionResult(true, "已请求启动穿透")
    }

    private fun stopBlocking(): TunnelActionResult {
        if (NodeKeepAlivePrefs.isRootMode(context)) {
            return RootTunnel.stop(context)
        }
        if (runtimeRepository.runtimeState.value.status == ServiceStatus.Running) {
            sendTunnelAction(NodeService.ACTION_TUNNEL_STOP)
        } else {
            TunnelStore.writeStatus(context, "stopped", "normal", 0L, 0L, 0, "")
        }
        return TunnelActionResult(true, "已请求停止穿透")
    }

    private fun sendTunnelAction(action: String) {
        runCatching {
            context.startService(
                Intent(context, NodeService::class.java).setAction(action)
            )
        }
    }

    private fun refreshInternal() {
        val settings = TunnelStore.readSettings(context)
        val status = TunnelStore.readStatus(context)
        val log = TunnelStore.readLogTail(context, 60)
        val rootMode = NodeKeepAlivePrefs.isRootMode(context)
        val heartbeat = status.optLong("heartbeat", 0L)
        val rootPid = if (rootMode) RootTunnel.runningPid(context) else 0L
        val running = when {
            rootMode -> rootPid > 0L
            else -> status.optString("state") == "running" &&
                System.currentTimeMillis() - heartbeat < 30_000L
        }
        _state.value = TunnelUiState(
            settings = settings,
            running = running,
            state = if (running) "running" else status.optString("state", "stopped"),
            pid = if (rootMode) rootPid else status.optLong("pid", 0L),
            since = status.optLong("since", 0L),
            restarts = status.optInt("restarts", 0),
            // 只用监管进程写入的启动错误；运行期日志（如本机目标端口拒绝）不算启动失败
            lastError = status.optString("lastError", ""),
            kernelReady = TunnelStore.kernelReady(context, rootMode),
            kernelVersion = if (rootMode && running) {
                TunnelStore.readText(File(TunnelStore.dir(context), "frpc-root.pid.version"), 64)
                    .trim().ifBlank { TunnelStore.kernelVersion(context, true) }
            } else TunnelStore.kernelVersion(context, rootMode),
            // 丢失记录后发现的旧进程不能借用另一实例留下的成功/失败日志。
            linkState = if (rootMode && rootPid != status.optLong("pid", 0L)) {
                TunnelLinkState.Unknown
            } else parseFrpcLogLinkState(log),
            serviceRunning = runtimeRepository.runtimeState.value.status == ServiceStatus.Running,
            rootMode = rootMode,
            servicePort = currentServicePort(),
            serviceToken = runtimeRepository.runtimeState.value.token
        )
    }

    /** 以 .env 的 DANMU_API_PORT 为准，读不到再退回 runtimeState.port。 */
    private fun currentServicePort(): Int {
        val path = runCatching { envConfigRepository.getEnvFilePath() }.getOrNull()
        if (!path.isNullOrBlank()) {
            val file = File(path)
            if (file.isFile) {
                val match = Regex(
                    "^\\s*DANMU_API_PORT\\s*=\\s*(\\d{1,5})",
                    RegexOption.MULTILINE
                ).find(file.readText())
                match?.groupValues?.get(1)?.toIntOrNull()
                    ?.takeIf { it in 1..65535 }
                    ?.let { return it }
            }
        }
        return runtimeRepository.runtimeState.value.port
    }

    // ------------------------------------------------------------ frpc 内核更新

    /** 查 GitHub 上 fatedier/frp 的最新 Release，并挑出本机架构对应的资源。 */
    suspend fun checkFrpcUpdate(): Result<FrpcRelease> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            val payload = githubRemoteService.requestTextResponseCancellable(
                githubRemoteService.apiUrlCandidates("repos/fatedier/frp/releases/latest"),
                mapOf("Accept" to "application/vnd.github+json", "User-Agent" to "DanmuApiApp")
            ) ?: error("无法读取 GitHub 内核版本")
            val json = JSONObject(payload.body)
            val version = json.optString("tag_name").removePrefix("v").trim()
            if (version.isEmpty()) error("没有读取到版本号")
            val suffix = "linux_${frpcPlatform()}.tar.gz"
            var url = ""
            val assets = json.optJSONArray("assets")
            for (index in 0 until (assets?.length() ?: 0)) {
                val asset = assets?.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                if (name.startsWith("frp_") && name.endsWith(suffix)) {
                    url = asset.optString("browser_download_url")
                    break
                }
            }
            if (url.isEmpty()) error("最新版里没有找到 $suffix")
            FrpcRelease(version, url)
        }
    }

    /** 下载并替换内核（仅 Root 模式可执行；普通模式内核随 App 包更新）。 */
    suspend fun applyFrpcUpdate(release: FrpcRelease): TunnelActionResult =
        withContext(Dispatchers.IO) {
            if (!NodeKeepAlivePrefs.isRootMode(context)) {
                return@withContext TunnelActionResult(
                    false,
                    "普通模式的内核随 App 包内置，请在应用更新里升级；Root 模式可直接更新内核"
                )
            }
            kernelUpdateMutex.withLock {
                runCatchingCancellable {
                    val version = release.version.removePrefix("v")
                    require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?").matches(version)) { "无效的内核版本" }
                    val platform = frpcPlatform()
                    // The digest must come from the official TLS-authenticated API.
                    // Reverse proxies may supply archive bytes, never the trust anchor.
                    val trustedClient = okHttpClient.newBuilder().callTimeout(20, TimeUnit.SECONDS).build()
                    val metadataRequest = Request.Builder().url("https://api.github.com/repos/fatedier/frp/releases/tags/v$version")
                        .header("Accept", "application/vnd.github+json").header("User-Agent", "DanmuApiApp")
                    githubProxyService.applyGithubAuth(metadataRequest, metadataRequest.build().url.toString())
                    val official = trustedClient.newOutboundCall(metadataRequest.build()).useCancellableResponse { response ->
                        check(response.isSuccessful && response.request.url.host == "api.github.com") { "无法核验官方发布摘要，已停止更新" }
                        val bytes = response.body.byteStream().readBytesLimited(1024 * 1024)
                        bytes.toString(Charsets.UTF_8)
                    }
                    val asset = verifiedFrpcAsset(official, version, platform)
                    check(release.downloadUrl == asset.url) { "待更新资产与官方发布不一致" }
                    val archive = File.createTempFile("frpc-download-", ".tar.gz", context.cacheDir)
                    val kernel = File.createTempFile("frpc-extract-", ".pending", context.cacheDir)
                    try {
                        var downloaded = false
                        val job = currentCoroutineContext()
                        for (url in githubProxyService.buildUrlCandidates(asset.url).plus(asset.url).distinct()) {
                            job.ensureActive()
                            downloaded = runCatchingCancellable {
                                val request = Request.Builder().url(url).header("User-Agent", "DanmuApiApp").build()
                                okHttpClient.newBuilder().callTimeout(5, TimeUnit.MINUTES).build().newOutboundCall(request).useCancellableResponse { response ->
                                    check(response.isSuccessful) { "下载失败 ${response.code}" }
                                    val declared = response.body.contentLength()
                                    check(declared == -1L || declared == asset.size) { "下载大小与官方发布不匹配" }
                                    FileOutputStream(archive).use { output ->
                                        copyVerifiedFrpcArchive(response.body.byteStream(), output, asset) { job.ensureActive() }
                                    }
                                }
                                true
                            }.getOrDefault(false)
                            if (downloaded) break
                        }
                        check(downloaded) { "内核下载失败或摘要不匹配，未执行下载文件" }
                        FileOutputStream(kernel).use { output ->
                            archive.inputStream().use { extractFrpcArchive(it, output) { job.ensureActive() } }
                        }
                        verifyFrpcElf(kernel, platform)
                        job.ensureActive()
                        val result = synchronized(RootTunnel) {
                            installFrpcKernel(
                                kernel = kernel, version = version,
                                target = TunnelStore.customKernelFile(context),
                                versionFile = TunnelStore.customKernelVersionFile(context),
                                wasRunning = RootTunnel.isRunning(context),
                                verify = { candidate ->
                                    verifyFrpcElf(candidate, platform)
                                    val checked = RootShell.exec("${shellQuote(candidate.absolutePath)} --version", 10_000L)
                                    check(checked.ok && checked.stdout.trim().removePrefix("v") == version) { "下载内核无法执行或版本不匹配" }
                                }, stop = { RootTunnel.stop(context) }, start = { RootTunnel.start(context) }
                            )
                        }
                        refreshInternal()
                        result
                    } finally { archive.delete(); kernel.delete() }
                }.getOrElse { error ->
                    TunnelActionResult(false, "更新失败：${error.message}")
                }
            }
        }

    private fun frpcPlatform(): String = when {
        Build.SUPPORTED_ABIS.any { it == "arm64-v8a" } -> "arm64"
        Build.SUPPORTED_ABIS.any { it == "armeabi-v7a" } -> "arm"
        else -> "amd64"
    }
}

private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        check(output.size() + count <= limit) { "发布信息超过大小限制" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
