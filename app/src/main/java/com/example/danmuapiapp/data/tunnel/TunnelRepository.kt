package com.example.danmuapiapp.data.tunnel

import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.domain.repository.EnvConfigRepository
import com.example.danmuapiapp.data.service.NodeKeepAlivePrefs
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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
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
    private val okHttpClient: OkHttpClient
) {

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

    suspend fun save(settings: TunnelSettings, configText: String): TunnelActionResult =
        withContext(Dispatchers.IO) {
            // localPort 以 .env 里的真实端口为准（runtimeState.port 可能是旧缓存）
            val realPort = currentServicePort()
            val normalized = if (settings.mode == TunnelMode.Form) {
                buildFrpcToml(settings.form, realPort)
            } else {
                syncPastedLocalPort(configText, realPort)
            }
            if (!TunnelStore.writeText(TunnelStore.configFile(context), normalized)) {
                return@withContext TunnelActionResult(false, "配置写入失败")
            }
            if (!TunnelStore.writeSettings(context, settings)) {
                return@withContext TunnelActionResult(false, "开关写入失败")
            }
            val wasRunning = _state.value.running
            if (!settings.enabled && wasRunning) {
                stopBlocking()
            } else if (settings.enabled && wasRunning) {
                if (NodeKeepAlivePrefs.isRootMode(context)) {
                    RootTunnel.stop(context)
                    RootTunnel.start(context)
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
        val text = if (settings.mode == TunnelMode.Form) {
            buildFrpcToml(settings.form, port)
        } else {
            syncPastedLocalPort(settings.configText, port)
        }
        TunnelStore.writeText(TunnelStore.configFile(context), text)
        if (_state.value.running) {
            if (NodeKeepAlivePrefs.isRootMode(context)) {
                RootTunnel.stop(context)
                RootTunnel.start(context)
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
        if (_state.value.running) {
            stopBlocking()
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

    suspend fun clearLog() = withContext(Dispatchers.IO) {
        TunnelStore.clearLog(context)
        refreshInternal()
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
        val running = when {
            !settings.enabled -> false
            rootMode -> RootTunnel.isRunning(context)
            else -> status.optString("state") == "running" &&
                System.currentTimeMillis() - heartbeat < 30_000L
        }
        _state.value = TunnelUiState(
            settings = settings,
            running = running,
            state = status.optString("state", "stopped"),
            pid = status.optLong("pid", 0L),
            since = status.optLong("since", 0L),
            restarts = status.optInt("restarts", 0),
            // 只用监管进程写入的启动错误；运行期日志（如本机目标端口拒绝）不算启动失败
            lastError = status.optString("lastError", ""),
            kernelReady = TunnelStore.kernelReady(context, rootMode),
            kernelVersion = TunnelStore.kernelVersion(context),
            linkState = parseFrpcLogLinkState(log),
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
        runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/repos/fatedier/frp/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            val json = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("GitHub 返回 ${response.code}")
                JSONObject(response.body?.string().orEmpty())
            }
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
            runCatching {
                val request = Request.Builder().url(release.downloadUrl).build()
                val bytes = okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("下载失败 ${response.code}")
                    response.body?.bytes() ?: error("下载内容为空")
                }
                val kernel = extractFrpcFromTarGz(bytes) ?: error("压缩包里没有找到 frpc")
                val target = TunnelStore.customKernelFile(context)
                target.parentFile?.mkdirs()
                target.writeBytes(kernel)
                target.setExecutable(true, false)
                TunnelStore.writeText(TunnelStore.customKernelVersionFile(context), release.version)
                if (_state.value.running) {
                    RootTunnel.stop(context)
                    RootTunnel.start(context)
                }
                refreshInternal()
                TunnelActionResult(true, "内核已更新到 ${release.version}")
            }.getOrElse { error ->
                TunnelActionResult(false, "更新失败：${error.message}")
            }
        }

    private fun frpcPlatform(): String = when {
        Build.SUPPORTED_ABIS.any { it == "arm64-v8a" } -> "arm64"
        Build.SUPPORTED_ABIS.any { it == "armeabi-v7a" } -> "arm"
        else -> "amd64"
    }
}
