package com.example.danmuapiapp.data.repository

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.FileObserver
import com.example.danmuapiapp.data.util.TokenDefaults
import com.example.danmuapiapp.data.service.NodeService
import com.example.danmuapiapp.data.service.NodeProjectManager
import com.example.danmuapiapp.data.service.RootRuntimeController
import com.example.danmuapiapp.data.service.RuntimePaths
import com.example.danmuapiapp.data.service.RootShell
import com.example.danmuapiapp.data.service.RootAutoStartModule
import com.example.danmuapiapp.data.service.RootAutoStartPrefs
import com.example.danmuapiapp.data.service.RuntimeModePrefs
import com.example.danmuapiapp.domain.model.*
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuntimeRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : RuntimeRepository {

    companion object {
        private const val KEY_RUNTIME_STARTED_AT_MS = "runtime_started_at_ms"
        private const val LOG_HTTP_TIMEOUT_MS = 900
        private const val NETWORK_URL_REFRESH_DEBOUNCE_MS = 300L
        private const val NORMAL_START_TIMEOUT_PRIMARY_MS = 15_000L
        private const val NORMAL_START_TIMEOUT_EXTEND_MS = 20_000L
        private const val NORMAL_RESTART_STOP_TIMEOUT_MS = 12_000L
        private const val NORMAL_RESTART_START_TIMEOUT_MS = 15_000L
        private const val HOT_RELOAD_DEBOUNCE_MS = 800L
        private const val HOT_RELOAD_MIN_INTERVAL_MS = 2500L
        private const val ROOT_FINGERPRINT_CHECK_INTERVAL_MS = 6000L
        private val NORMAL_RESTART_CRITICAL_FILES = setOf(
            "main.js",
            "android-server.mjs",
            "worker-proxy.mjs"
        )
        private val NORMAL_RESTART_SKIP_PREFIXES = listOf(
            "config/",
            "danmu_api_stable/",
            "danmu_api_dev/",
            "danmu_api_custom/"
        )
    }

    private val prefs = context.getSharedPreferences("runtime", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val operationMutex = Mutex()
    private val connectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    private val _runtimeState = MutableStateFlow(loadInitialState())
    override val runtimeState: StateFlow<RuntimeState> = _runtimeState.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    override val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()
    private val _eventLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    private var serviceLogsCache: List<LogEntry> = emptyList()

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            // 仅普通模式处理 NodeService 广播，避免 Root 模式误更新。
            if (_runtimeState.value.runMode != RunMode.Normal) return

            when (intent.getStringExtra(NodeService.EXTRA_STATUS)) {
                NodeService.STATUS_RUNNING -> {
                    markRunning(forceNewStart = true)
                    addLog(LogLevel.Info, "服务已启动")
                }

                NodeService.STATUS_STOPPED -> {
                    markStopped()
                    addLog(LogLevel.Info, "服务已停止")
                }

                NodeService.STATUS_ERROR -> {
                    val error = intent.getStringExtra(NodeService.EXTRA_ERROR) ?: "未知错误"
                    markError(error)
                    addLog(LogLevel.Error, "服务错误: $error")
                }
            }
        }
    }

    private var uptimeJob: Job? = null
    private var hotReloadWatcher: WorkDirHotReloadWatcher? = null
    private var hotReloadJob: Job? = null
    private var pendingHotReloadReason: String? = null
    private var lastHotReloadAtMs: Long = 0L
    private var hotReloadSuppressUntilMs: Long = 0L
    private var rootWorkDirFingerprint: String? = null
    private var lastRootFingerprintCheckAtMs: Long = 0L
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var networkUrlRefreshJob: Job? = null
    @Volatile
    private var pendingNormalRestart = false

    init {
        val filter = IntentFilter(NodeService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(statusReceiver, filter)
        }

        val initState = _runtimeState.value
        if (initState.status == ServiceStatus.Running) {
            val startedAt = ensureRuntimeStartedAt(initState.runMode, forceNew = false)
            startUptimeCounter(startedAt)
        }

        scope.launch {
            reconcileInitialState()
        }
        startNetworkMonitor()
    }

    private fun loadInitialState(): RuntimeState {
        val mode = RuntimeModePrefs.get(context)
        val defaultTokenEnvFile = runCatching {
            if (mode == RunMode.Normal) {
                NodeProjectManager.ensureProjectExtracted(context, RuntimePaths.normalProjectDir(context))
            }
            File(RuntimePaths.normalProjectDir(context), "config/.env")
        }.getOrNull()
        val envValues = readRuntimeEnvValues(defaultTokenEnvFile)
        val portFromEnv = envValues["DANMU_API_PORT"]?.toIntOrNull()?.takeIf { it in 1..65535 }
        val port = if (prefs.contains("port")) {
            prefs.getInt("port", 9321)
        } else {
            portFromEnv ?: 9321
        }
        val token = TokenDefaults.resolveTokenFromPrefs(prefs, context, defaultTokenEnvFile)
        val legacyVariant = context
            .getSharedPreferences("danmu_api_variant", Context.MODE_PRIVATE)
            .getString("variant", "")
            .orEmpty()
            .trim()
        val rawVariant = when {
            prefs.contains("variant") -> prefs.getString("variant", "stable").orEmpty().trim()
            envValues["DANMU_API_VARIANT"].isNullOrBlank().not() -> envValues["DANMU_API_VARIANT"].orEmpty().trim()
            legacyVariant.isNotBlank() -> legacyVariant
            else -> "stable"
        }
        val normalizedVariant = when (rawVariant.lowercase()) {
            "dev", "develop", "development" -> "dev"
            "custom" -> "custom"
            else -> "stable"
        }
        val variant = ApiVariant.entries.find { it.key == normalizedVariant } ?: ApiVariant.Stable

        val portOpen = isPortOpen(port)
        val normalRuntimeAlive = portOpen && isNormalServiceRunning()
        val rootPid = if (mode == RunMode.Root) RootRuntimeController.getPid(context) else null
        val running = if (mode == RunMode.Root) {
            portOpen || rootPid != null
        } else {
            normalRuntimeAlive
        }
        var startedAt = readRuntimeStartedAt()

        if (running) {
            if (startedAt <= 0L) {
                startedAt = when (mode) {
                    RunMode.Root -> {
                        RootRuntimeController.getPidFileLastModified(context)
                            ?: System.currentTimeMillis()
                    }

                    RunMode.Normal -> System.currentTimeMillis()
                }
                saveRuntimeStartedAt(startedAt)
            }
        } else if (startedAt > 0L) {
            clearRuntimeStartedAt()
            startedAt = 0L
        }

        val uptimeSeconds = if (running && startedAt > 0L) {
            uptimeSecondsFrom(startedAt)
        } else {
            0L
        }

        return RuntimeState(
            status = if (running) ServiceStatus.Running else ServiceStatus.Stopped,
            port = port,
            token = token,
            variant = variant,
            runMode = mode,
            pid = if (running && mode == RunMode.Root) rootPid else null,
            uptimeSeconds = uptimeSeconds,
            localUrl = buildLocalUrl(port, token),
            lanUrl = buildLanUrl(getLanIp(), port, token)
        )
    }

    private fun readRuntimeEnvValues(envFile: File?): Map<String, String> {
        if (envFile == null || !envFile.exists() || !envFile.isFile) return emptyMap()
        return runCatching {
            val map = linkedMapOf<String, String>()
            envFile.readLines(Charsets.UTF_8).forEach { line ->
                val raw = line.trim()
                if (raw.isEmpty() || raw.startsWith("#")) return@forEach
                val eq = raw.indexOf('=')
                if (eq <= 0) return@forEach
                val key = raw.substring(0, eq).trim()
                if (key.isBlank()) return@forEach
                var value = raw.substring(eq + 1).trim()
                if (value.length >= 2 &&
                    ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'")))
                ) {
                    value = value.substring(1, value.length - 1)
                }
                map[key] = value
            }
            map
        }.getOrDefault(emptyMap())
    }

    override fun startService() {
        scope.launch {
            operationMutex.withLock {
                startServiceLocked()
            }
        }
    }

    override fun stopService() {
        scope.launch {
            operationMutex.withLock {
                stopServiceLocked()
            }
        }
    }

    override fun restartService() {
        scope.launch {
            operationMutex.withLock {
                restartServiceLocked()
            }
        }
    }

    override fun refreshLogs() {
        scope.launch {
            refreshLogsOnce()
        }
    }

    override fun applyServiceConfig(port: Int, token: String, restartIfRunning: Boolean) {
        scope.launch {
            operationMutex.withLock {
                applyServiceConfigLocked(
                    port = port,
                    token = token.trim(),
                    restartIfRunning = restartIfRunning
                )
            }
        }
    }

    override fun updatePort(port: Int) {
        if (port !in 1..65535) return
        applyServiceConfig(
            port = port,
            token = _runtimeState.value.token,
            restartIfRunning = false
        )
    }

    override fun updateToken(token: String) {
        applyServiceConfig(
            port = _runtimeState.value.port,
            token = token.trim(),
            restartIfRunning = false
        )
    }

    override fun updateVariant(variant: ApiVariant) {
        prefs.edit().putString("variant", variant.key).apply()
        context.getSharedPreferences("danmu_api_variant", Context.MODE_PRIVATE)
            .edit()
            .putString("variant", variant.key)
            .apply()
        _runtimeState.update { it.copy(variant = variant) }
    }

    override fun updateRunMode(mode: RunMode) {
        scope.launch {
            operationMutex.withLock {
                val current = _runtimeState.value
                if (current.runMode == mode) return@withLock

                if (mode.requiresRoot) {
                    val rootCheck = RootShell.exec("id", timeoutMs = 3500L)
                    if (!rootCheck.ok) {
                        addLog(LogLevel.Warn, "运行模式切换失败：${rootSwitchDeniedReason(rootCheck)}")
                        return@withLock
                    }
                }

                val shouldResume = current.status == ServiceStatus.Running ||
                    current.status == ServiceStatus.Starting

                if (shouldResume) {
                    stopServiceLocked()
                    waitForPort(current.port, wantOpen = false, timeoutMs = 5000L)
                }

                clearRuntimeStartedAt()
                RuntimeModePrefs.put(context, mode)
                val shouldSyncBootMode = RootAutoStartPrefs.isBootAutoStartEnabled(context)
                if (shouldSyncBootMode) {
                    val flagResult = RootAutoStartModule.writeRunModeFlag(mode)
                    if (!flagResult.ok) {
                        addLog(LogLevel.Warn, "开机触发模式同步失败：${flagResult.message}")
                    }
                }
                _runtimeState.update {
                    it.copy(
                        runMode = mode,
                        status = ServiceStatus.Stopped,
                        uptimeSeconds = 0,
                        pid = null,
                        errorMessage = null
                    )
                }
                stopWorkDirHotReload()

                addLog(LogLevel.Info, "运行模式已切换为 ${mode.label}")

                if (shouldResume) {
                    startServiceLocked()
                }
            }
        }
    }

    private fun rootSwitchDeniedReason(result: RootShell.Result): String {
        if (result.timedOut) {
            return "Root 授权超时"
        }
        val detail = (result.stderr.ifBlank { result.stdout })
            .lineSequence()
            .firstOrNull()
            ?.trim()
            .orEmpty()
        if (detail.contains("not found", ignoreCase = true)) {
            return "未检测到 su"
        }
        if (detail.contains("denied", ignoreCase = true)) {
            return "未授予 Root 权限"
        }
        return detail.ifBlank { "未获得 Root 权限" }
    }

    private suspend fun applyServiceConfigLocked(
        port: Int,
        token: String,
        restartIfRunning: Boolean
    ) {
        if (port !in 1..65535) return

        val snapshot = _runtimeState.value
        val portChanged = snapshot.port != port
        val tokenChanged = snapshot.token != token
        if (!portChanged && !tokenChanged) return

        val wasActive = snapshot.status == ServiceStatus.Running ||
            snapshot.status == ServiceStatus.Starting
        if (!restartIfRunning || !wasActive) {
            persistServiceConfigChanges(
                port = port,
                token = token,
                applyPort = portChanged,
                applyToken = tokenChanged
            )
            syncRuntimeEnvFromPrefs(snapshot.runMode)
            return
        }

        val oldPort = snapshot.port
        val oldMode = snapshot.runMode
        val suppressStopTimeout = oldMode == RunMode.Normal
        if (suppressStopTimeout) {
            pendingNormalRestart = true
        }

        try {
            val changedText = buildString {
                if (portChanged) append("端口 ${snapshot.port} -> $port")
                if (tokenChanged) {
                    if (isNotEmpty()) append("，")
                    append("Token 已更新")
                }
            }.ifBlank { "配置已更新" }
            addLog(LogLevel.Info, "正在应用服务配置：$changedText")

            stopServiceLocked()
            val stopped = waitForRuntimeStopped(oldMode, oldPort, timeoutMs = 12_000L)
            if (!stopped) {
                val detail = "应用服务配置失败：旧实例停止超时"
                markError(detail)
                addLog(LogLevel.Error, detail)
                return
            }

            markStopped()
            persistServiceConfigChanges(
                port = port,
                token = token,
                applyPort = portChanged,
                applyToken = tokenChanged
            )
            syncRuntimeEnvFromPrefs(oldMode)
            addLog(LogLevel.Info, "配置已写入，正在启动服务...")
            startServiceLocked()
        } finally {
            if (suppressStopTimeout) {
                pendingNormalRestart = false
            }
        }
    }

    private fun persistServiceConfigChanges(
        port: Int,
        token: String,
        applyPort: Boolean,
        applyToken: Boolean
    ) {
        prefs.edit().apply {
            if (applyPort) {
                putInt("port", port)
            }
            if (applyToken) {
                if (token.isBlank()) {
                    remove("token")
                } else {
                    putString("token", token)
                }
            }
        }.apply()

        val lanIp = getLanIp()
        _runtimeState.update { state ->
            val nextPort = if (applyPort) port else state.port
            val nextToken = if (applyToken) {
                token.ifBlank { TokenDefaults.resolveCoreDefaultToken(context) }
            } else {
                state.token
            }
            state.copy(
                port = nextPort,
                token = nextToken,
                localUrl = buildLocalUrl(nextPort, nextToken),
                lanUrl = buildLanUrl(lanIp, nextPort, nextToken)
            )
        }
    }

    private fun syncRuntimeEnvFromPrefs(mode: RunMode) {
        val normalDir = runCatching {
            NodeProjectManager.ensureProjectExtracted(context, RuntimePaths.normalProjectDir(context))
        }.getOrElse {
            addLog(LogLevel.Warn, "同步运行时配置失败：${it.message ?: "无法初始化工作目录"}")
            return
        }

        runCatching {
            NodeProjectManager.writeRuntimeEnv(context, normalDir)
        }.onFailure {
            addLog(LogLevel.Warn, "写入运行时配置失败：${it.message ?: "未知错误"}")
            return
        }

        if (mode != RunMode.Root) return
        val syncResult = RootRuntimeController.syncRuntimeEnvOnly(context)
        if (!syncResult.ok) {
            val detail = syncResult.detail.ifBlank { syncResult.message }
            addLog(LogLevel.Warn, "同步 Root 运行时配置失败：$detail")
        }
    }

    override fun clearLogs() {
        serviceLogsCache = emptyList()
        _eventLogs.value = emptyList()
        _logs.value = emptyList()
    }

    override fun addLog(level: LogLevel, message: String) {
        _eventLogs.update { current ->
            (current + LogEntry(level = level, message = message)).takeLast(500)
        }
        publishMergedLogs()
    }

    private suspend fun startServiceLocked() {
        val state = _runtimeState.value
        if (state.status == ServiceStatus.Running || state.status == ServiceStatus.Starting) return

        _runtimeState.update { it.copy(status = ServiceStatus.Starting, errorMessage = null) }
        clearRuntimeStartedAt()
        addLog(LogLevel.Info, "正在启动服务...")

        when (state.runMode) {
            RunMode.Normal -> {
                if (state.port in 1..1023) {
                    val reason = "普通模式无法监听 1-1023 端口，请切换 Root 模式或改用 1024+ 端口"
                    markError(reason)
                    addLog(LogLevel.Error, reason)
                    return
                }
                val prepared = runCatching {
                    // 首次安装后这里可能需要解包运行时，提前准备可避免启动超时误判。
                    NodeProjectManager.ensureProjectExtracted(context)
                }
                if (prepared.isFailure) {
                    val reason = prepared.exceptionOrNull()?.message ?: "未知错误"
                    markError("运行时准备失败：$reason")
                    addLog(LogLevel.Error, "普通模式启动前准备失败: $reason")
                    return
                }
                val projectDir = prepared.getOrNull() ?: return
                val selectedCoreDir = File(projectDir, "danmu_api_${state.variant.key}")
                val selectedCoreReady = runCatching {
                    NodeProjectManager.hasValidCore(selectedCoreDir)
                }.getOrDefault(false)
                if (!selectedCoreReady) {
                    val fallback = ApiVariant.entries.firstOrNull { variant ->
                        NodeProjectManager.hasValidCore(File(projectDir, "danmu_api_${variant.key}"))
                    }
                    val reason = if (fallback != null && fallback != state.variant) {
                        "当前核心 ${state.variant.label} 未安装，可先切换到 ${fallback.label} 或下载后再启动"
                    } else {
                        "当前核心 ${state.variant.label} 未安装，请先下载核心后再启动"
                    }
                    markError(reason)
                    addLog(LogLevel.Error, reason)
                    return
                }
                NodeService.start(context)
                scope.launch {
                    delay(NORMAL_START_TIMEOUT_PRIMARY_MS)
                    val snapshot = _runtimeState.value
                    if (snapshot.runMode == RunMode.Normal && snapshot.status == ServiceStatus.Starting) {
                        if (isPortOpen(snapshot.port)) {
                            markRunning(forceNewStart = true)
                        } else {
                            addLog(LogLevel.Warn, "普通模式首次启动较慢，继续等待...")
                            delay(NORMAL_START_TIMEOUT_EXTEND_MS)
                            val retrySnapshot = _runtimeState.value
                            if (retrySnapshot.runMode == RunMode.Normal &&
                                retrySnapshot.status == ServiceStatus.Starting
                            ) {
                                if (isPortOpen(retrySnapshot.port)) {
                                    markRunning(forceNewStart = true)
                                } else {
                                    markError("普通模式启动超时")
                                }
                            }
                        }
                    }
                }
            }

            RunMode.Root -> {
                val result = RootRuntimeController.start(context, state.port, quickMode = false)
                if (result.ok) {
                    markRunning(pid = RootRuntimeController.getPid(context), forceNewStart = true)
                    addLog(LogLevel.Info, result.message)
                } else {
                    val detail = result.detail.ifBlank { result.message }
                    markError(detail)
                    addLog(LogLevel.Error, "Root 启动失败: $detail")
                }
            }
        }
    }

    private suspend fun stopServiceLocked() {
        val state = _runtimeState.value
        if (state.status == ServiceStatus.Stopped || state.status == ServiceStatus.Stopping) return

        _runtimeState.update { it.copy(status = ServiceStatus.Stopping) }
        addLog(LogLevel.Info, "正在停止服务...")
        uptimeJob?.cancel()

        when (state.runMode) {
            RunMode.Normal -> {
                NodeService.stop(context)
                scope.launch {
                    delay(4_000)
                    val snapshot = _runtimeState.value
                    if (snapshot.runMode == RunMode.Normal && snapshot.status == ServiceStatus.Stopping) {
                        if (isNormalServiceStopped() && !isPortOpen(snapshot.port)) {
                            markStopped()
                        } else {
                            if (!pendingNormalRestart) {
                                markError("普通模式停止超时")
                            }
                        }
                    }
                }
            }

            RunMode.Root -> {
                val result = RootRuntimeController.stop(context, state.port)
                if (result.ok) {
                    markStopped()
                    addLog(LogLevel.Info, result.message)
                } else {
                    val detail = result.detail.ifBlank { result.message }
                    markError(detail)
                    addLog(LogLevel.Error, "Root 停止失败: $detail")
                }
            }
        }
    }

    private suspend fun restartServiceLocked() {
        val state = _runtimeState.value
        if (state.status == ServiceStatus.Starting || state.status == ServiceStatus.Stopping) return

        if (state.status == ServiceStatus.Stopped || state.status == ServiceStatus.Error) {
            startServiceLocked()
            return
        }

        when (state.runMode) {
            RunMode.Normal -> {
                pendingNormalRestart = true
                try {
                    stopServiceLocked()
                    val stopped = waitForNormalServiceStopped(timeoutMs = NORMAL_RESTART_STOP_TIMEOUT_MS)
                    if (!stopped) {
                        markError("普通模式重启失败：停止超时")
                        addLog(LogLevel.Error, "普通模式重启失败：停止超时")
                        return
                    }

                    markStopped()
                    startServiceLocked()
                    val started = waitForPort(
                        state.port,
                        wantOpen = true,
                        timeoutMs = NORMAL_RESTART_START_TIMEOUT_MS
                    )
                    if (started) {
                        if (_runtimeState.value.status != ServiceStatus.Running) {
                            markRunning(forceNewStart = true)
                        }
                    } else {
                        if (_runtimeState.value.status == ServiceStatus.Starting) {
                            markError("普通模式重启失败：启动超时")
                        }
                    }
                } finally {
                    pendingNormalRestart = false
                }
            }

            RunMode.Root -> {
                _runtimeState.update { it.copy(status = ServiceStatus.Stopping) }
                val result = RootRuntimeController.restart(context, state.port)
                if (result.ok) {
                    markRunning(pid = RootRuntimeController.getPid(context), forceNewStart = true)
                    addLog(LogLevel.Info, "服务已重启")
                } else {
                    val detail = result.detail.ifBlank { result.message }
                    markError(detail)
                    addLog(LogLevel.Error, "重启失败: $detail")
                }
            }
        }
    }

    private fun markRunning(pid: Int? = null, forceNewStart: Boolean = false) {
        val mode = _runtimeState.value.runMode
        val startedAt = ensureRuntimeStartedAt(mode, forceNew = forceNewStart)
        val uptime = uptimeSecondsFrom(startedAt)
        val lanIp = getLanIp()

        _runtimeState.update {
            it.copy(
                status = ServiceStatus.Running,
                pid = pid ?: it.pid,
                uptimeSeconds = uptime,
                localUrl = buildLocalUrl(it.port, it.token),
                lanUrl = buildLanUrl(lanIp, it.port, it.token),
                errorMessage = null
            )
        }
        startUptimeCounter(startedAt)
        // 运行态改为事件驱动刷新时，这里启动热更新监听。
        handleWorkDirHotReload(_runtimeState.value)
    }

    private fun markStopped() {
        clearRuntimeStartedAt()
        _runtimeState.update {
            it.copy(status = ServiceStatus.Stopped, pid = null, uptimeSeconds = 0)
        }
        uptimeJob?.cancel()
        stopWorkDirHotReload()
    }

    private fun markError(message: String) {
        val state = _runtimeState.value
        val stillRunning = isPortOpen(state.port)
        if (!stillRunning) {
            clearRuntimeStartedAt()
        }

        _runtimeState.update {
            it.copy(
                status = ServiceStatus.Error,
                errorMessage = message,
                pid = if (stillRunning) it.pid else null,
                uptimeSeconds = if (stillRunning) it.uptimeSeconds else 0
            )
        }
        uptimeJob?.cancel()
        if (!stillRunning) {
            stopWorkDirHotReload()
        }
    }

    private fun startUptimeCounter(startedAtMs: Long) {
        uptimeJob?.cancel()
        uptimeJob = scope.launch {
            while (isActive) {
                _runtimeState.update { state ->
                    if (state.status == ServiceStatus.Running) {
                        state.copy(uptimeSeconds = uptimeSecondsFrom(startedAtMs))
                    } else {
                        state
                    }
                }
                delay(1000)
            }
        }
    }

    private suspend fun reconcileInitialState() {
        val state = _runtimeState.value
        if (state.status == ServiceStatus.Running) return

        when (state.runMode) {
            RunMode.Normal -> {
                if (isPortOpen(state.port) && isNormalServiceRunning()) {
                    markRunning(forceNewStart = false)
                }
            }

            RunMode.Root -> {
                if (RootRuntimeController.isRunning(context, state.port)) {
                    markRunning(
                        pid = RootRuntimeController.getPid(context),
                        forceNewStart = false
                    )
                }
            }
        }
    }

    private fun publishMergedLogs() {
        val merged = (serviceLogsCache + _eventLogs.value)
            .sortedBy { it.timestamp }
            .takeLast(500)
        _logs.value = merged
    }

    private fun refreshLogsOnce() {
        runCatching {
            val serviceLogs = collectServiceLogsOnce()
            if (serviceLogs.isNotEmpty() || serviceLogsCache.isNotEmpty()) {
                serviceLogsCache = serviceLogs
                publishMergedLogs()
            } else if (_eventLogs.value.isNotEmpty() && _logs.value != _eventLogs.value) {
                publishMergedLogs()
            }
        }.onFailure {
            if (_eventLogs.value.isNotEmpty() && _logs.value != _eventLogs.value) {
                publishMergedLogs()
            }
        }
    }

    private fun collectServiceLogsOnce(): List<LogEntry> {
        val state = _runtimeState.value
        if (state.status != ServiceStatus.Running && !isPortOpen(state.port)) return emptyList()

        val baseUrl = state.localUrl.trimEnd('/')
        if (baseUrl.isBlank()) return emptyList()
        val raw = runCatching {
            val conn = (URL("$baseUrl/api/logs").openConnection() as HttpURLConnection).apply {
                connectTimeout = LOG_HTTP_TIMEOUT_MS
                readTimeout = LOG_HTTP_TIMEOUT_MS
                requestMethod = "GET"
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching ""
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
            } finally {
                conn.disconnect()
            }
        }.getOrElse { "" }
        if (raw.isBlank()) return emptyList()
        return parseLogLines(raw)
    }

    private fun parseLogLines(raw: String): List<LogEntry> {
        val lines = raw.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return emptyList()

        return lines.map { line ->
            val entry = parseStructuredLogLine(line)
            entry ?: LogEntry(
                timestamp = System.currentTimeMillis(),
                level = LogLevel.Info,
                message = line
            )
        }.takeLast(400)
    }

    private fun parseStructuredLogLine(line: String): LogEntry? {
        val isoRegex = Regex("""^\[?(\d{4}-\d{2}-\d{2}T[^ \]]+)\]?\s+\[([A-Z]+)\]\s*(.*)$""")
        val localRegex = Regex("""^\[?(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})\]?\s+\[([A-Z]+)\]\s*(.*)$""")
        val serviceRegex = Regex("""^\[([^\]]+)]\s+([A-Za-z]+):\s*(.*)$""")

        val m = isoRegex.find(line) ?: localRegex.find(line) ?: serviceRegex.find(line) ?: return null
        val ts = m.groupValues.getOrNull(1).orEmpty()
        val levelRaw = m.groupValues.getOrNull(2).orEmpty()
        val msg = m.groupValues.getOrNull(3).orEmpty()

        val timestamp = runCatching { Instant.parse(ts).toEpochMilli() }.getOrElse {
            runCatching {
                val localDt = LocalDateTime.parse(
                    ts,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                )
                localDt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrElse { System.currentTimeMillis() }
        }

        val level = when (levelRaw.trim().uppercase()) {
            "ERROR" -> LogLevel.Error
            "WARN", "WARNING" -> LogLevel.Warn
            else -> LogLevel.Info
        }

        return LogEntry(
            timestamp = timestamp,
            level = level,
            message = msg
        )
    }

    private fun handleWorkDirHotReload(state: RuntimeState) {
        if (state.status != ServiceStatus.Running) {
            stopWorkDirHotReload()
            return
        }

        when (state.runMode) {
            RunMode.Normal -> {
                ensureNormalWorkDirWatcher()
                rootWorkDirFingerprint = null
                lastRootFingerprintCheckAtMs = 0L
            }

            RunMode.Root -> {
                // Root 模式以 Root 工作目录为唯一热更新来源，避免普通目录变更干扰。
                stopNormalWorkDirWatcher()
                checkRootWorkDirFingerprint()
            }
        }
    }

    private fun startNetworkMonitor() {
        val cm = connectivityManager ?: return
        if (networkCallback != null) return

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scheduleRuntimeUrlRefresh()
            }

            override fun onLost(network: Network) {
                scheduleRuntimeUrlRefresh()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                scheduleRuntimeUrlRefresh()
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                scheduleRuntimeUrlRefresh()
            }
        }

        val registered = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(callback)
            } else {
                val request = NetworkRequest.Builder().build()
                @Suppress("DEPRECATION")
                cm.registerNetworkCallback(request, callback)
            }
            true
        }.getOrElse { false }

        if (registered) {
            networkCallback = callback
            scheduleRuntimeUrlRefresh()
        }
    }

    private fun scheduleRuntimeUrlRefresh() {
        networkUrlRefreshJob?.cancel()
        networkUrlRefreshJob = scope.launch {
            delay(NETWORK_URL_REFRESH_DEBOUNCE_MS)
            refreshRuntimeUrls()
        }
    }

    private fun refreshRuntimeUrls(force: Boolean = false) {
        val lanIp = getLanIp()
        _runtimeState.update { state ->
            val localUrl = buildLocalUrl(state.port, state.token)
            val lanUrl = buildLanUrl(lanIp, state.port, state.token)
            if (!force && state.localUrl == localUrl && state.lanUrl == lanUrl) {
                state
            } else {
                state.copy(localUrl = localUrl, lanUrl = lanUrl)
            }
        }
    }

    private fun ensureNormalWorkDirWatcher() {
        val projectDir = RuntimePaths.normalProjectDir(context)
        val projectPath = projectDir.absolutePath
        val currentWatcher = hotReloadWatcher
        if (currentWatcher != null && currentWatcher.rootPath == projectPath) {
            return
        }

        currentWatcher?.stop()
        hotReloadWatcher = null

        if (!projectDir.exists() || !projectDir.isDirectory) {
            return
        }

        val watcher = WorkDirHotReloadWatcher(projectDir) { changedPath ->
            val normalized = normalizeHotReloadPath(changedPath)
            if (!shouldRestartOnNormalPathChange(normalized)) {
                return@WorkDirHotReloadWatcher
            }
            scheduleHotReload("检测到工作目录关键变更：$normalized")
        }
        watcher.start()
        hotReloadWatcher = watcher
    }

    private fun normalizeHotReloadPath(path: String): String {
        return path.trim()
            .replace('\\', '/')
            .trimStart('/')
    }

    private fun shouldRestartOnNormalPathChange(path: String): Boolean {
        if (path.isBlank()) return false
        val normalized = path.lowercase()

        if (normalized in NORMAL_RESTART_CRITICAL_FILES) {
            return true
        }
        if (NORMAL_RESTART_SKIP_PREFIXES.any { prefix ->
                normalized == prefix.removeSuffix("/") || normalized.startsWith(prefix)
            }) {
            // 核心目录与 config 变更由 Node 进程内热重载处理，避免整服务重启。
            return false
        }

        // 其余工作目录变更默认不触发服务重启，避免普通模式频繁抖动。
        return false
    }

    private fun stopNormalWorkDirWatcher() {
        hotReloadWatcher?.stop()
        hotReloadWatcher = null
    }

    private fun stopWorkDirHotReload() {
        stopNormalWorkDirWatcher()
        hotReloadJob?.cancel()
        hotReloadJob = null
        pendingHotReloadReason = null
        rootWorkDirFingerprint = null
        lastRootFingerprintCheckAtMs = 0L
    }

    private fun checkRootWorkDirFingerprint() {
        val now = System.currentTimeMillis()
        if (now - lastRootFingerprintCheckAtMs < ROOT_FINGERPRINT_CHECK_INTERVAL_MS) {
            return
        }
        lastRootFingerprintCheckAtMs = now

        val fingerprint = readRootWorkDirFingerprint() ?: return
        val previous = rootWorkDirFingerprint
        rootWorkDirFingerprint = fingerprint

        if (previous != null && previous != fingerprint) {
            scheduleHotReload("检测到 Root 工作目录变更")
        }
    }

    private fun scheduleHotReload(reason: String) {
        val now = System.currentTimeMillis()
        if (now < hotReloadSuppressUntilMs) return
        if (now - lastHotReloadAtMs < HOT_RELOAD_MIN_INTERVAL_MS) return
        if (_runtimeState.value.status != ServiceStatus.Running) return

        pendingHotReloadReason = reason
        hotReloadJob?.cancel()
        hotReloadJob = scope.launch {
            delay(HOT_RELOAD_DEBOUNCE_MS)
            val trigger = pendingHotReloadReason ?: return@launch
            pendingHotReloadReason = null

            operationMutex.withLock {
                val snapshot = _runtimeState.value
                if (snapshot.status != ServiceStatus.Running) return@withLock

                val nowLocked = System.currentTimeMillis()
                if (nowLocked < hotReloadSuppressUntilMs) return@withLock
                if (nowLocked - lastHotReloadAtMs < HOT_RELOAD_MIN_INTERVAL_MS) return@withLock
                lastHotReloadAtMs = nowLocked
                hotReloadSuppressUntilMs = nowLocked + 5000L

                addLog(LogLevel.Info, "$trigger，正在热更新服务")
                if (snapshot.runMode != RunMode.Normal) {
                    // Root 模式直接基于 Root 工作目录重启，不再用普通目录覆盖 Root 目录。
                }
                restartServiceLocked()
            }
        }
    }

    private fun readRootWorkDirFingerprint(): String? {
        val dir = RuntimePaths.rootProjectDir(context).absolutePath
        val script = """
            DIR=${shellQuote(dir)}
            if [ ! -d "${'$'}DIR" ]; then
              echo "missing"
              exit 0
            fi
            find "${'$'}DIR" \
              \( -path "${'$'}DIR/logs" -o -path "${'$'}DIR/logs/*" -o -path "${'$'}DIR/.cache" -o -path "${'$'}DIR/.cache/*" -o -path "${'$'}DIR/node_modules" -o -path "${'$'}DIR/node_modules/*" \) -prune \
              -o -type f -print 2>/dev/null | while IFS= read -r F; do
                S=${'$'}(stat -c '%Y:%s' "${'$'}F" 2>/dev/null || toybox stat -c '%Y:%s' "${'$'}F" 2>/dev/null || echo '0:0')
                echo "${'$'}S:${'$'}F"
              done | sort | cksum | awk '{print "${'$'}1:${'$'}2"}'
        """.trimIndent()
        val result = RootShell.exec(script, timeoutMs = 4500L)
        if (!result.ok) return null
        return result.stdout.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() }
    }

    private inner class WorkDirHotReloadWatcher(
        rootDir: File,
        private val onChanged: (String) -> Unit
    ) {
        val rootPath: String = rootDir.absolutePath
        private val rootCanonical = runCatching { rootDir.canonicalFile }.getOrElse { rootDir }
        private val observers = LinkedHashMap<String, FileObserver>()
        private val mask = FileObserver.CLOSE_WRITE or
            FileObserver.MODIFY or
            FileObserver.ATTRIB or
            FileObserver.CREATE or
            FileObserver.MOVED_TO or
            FileObserver.MOVED_FROM or
            FileObserver.DELETE or
            FileObserver.DELETE_SELF or
            FileObserver.MOVE_SELF

        fun start() {
            watchRecursively(rootCanonical)
        }

        fun stop() {
            observers.values.forEach { observer ->
                runCatching { observer.stopWatching() }
            }
            observers.clear()
        }

        private fun createFileObserver(path: String, onEvent: (Int, String?) -> Unit): FileObserver {
            val watchFile = File(path)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                object : FileObserver(watchFile, mask) {
                    override fun onEvent(event: Int, relativePath: String?) {
                        onEvent.invoke(event, relativePath)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                object : FileObserver(path, mask) {
                    override fun onEvent(event: Int, relativePath: String?) {
                        onEvent.invoke(event, relativePath)
                    }
                }
            }
        }

        private fun watchRecursively(dir: File) {
            if (!dir.exists() || !dir.isDirectory || shouldIgnore(dir)) return
            val key = runCatching { dir.canonicalPath }.getOrElse { dir.absolutePath }
            if (observers.containsKey(key)) return

            val observer = createFileObserver(key) { event, path ->
                val target = if (path.isNullOrBlank()) {
                    File(key)
                } else {
                    File(key, path)
                }
                if (shouldIgnore(target)) return@createFileObserver

                if ((event and (FileObserver.CREATE or FileObserver.MOVED_TO)) != 0 && target.isDirectory) {
                    watchRecursively(target)
                }

                val rel = toRelative(target).ifBlank { target.name }
                onChanged(rel)

                if (event and (FileObserver.DELETE_SELF or FileObserver.MOVE_SELF) != 0) {
                    observers.remove(key)?.let { removed ->
                        runCatching { removed.stopWatching() }
                    }
                }
            }

            observer.startWatching()
            observers[key] = observer
            dir.listFiles()?.filter { it.isDirectory }?.forEach { child ->
                watchRecursively(child)
            }
        }

        private fun toRelative(target: File): String {
            return runCatching {
                target.canonicalFile.relativeTo(rootCanonical).path.replace('\\', '/')
            }.getOrElse {
                target.name
            }
        }

        private fun shouldIgnore(target: File): Boolean {
            val rel = toRelative(target)
            if (rel == "." || rel.isBlank()) return false
            return rel == ".app_version" ||
                rel.startsWith("logs/") ||
                rel == "logs" ||
                rel.startsWith(".cache/") ||
                rel == ".cache" ||
                rel.startsWith("node_modules/") ||
                rel == "node_modules"
        }
    }

    private fun shellQuote(s: String): String {
        return "'" + s.replace("'", "'\"'\"'") + "'"
    }

    private fun ensureRuntimeStartedAt(mode: RunMode, forceNew: Boolean): Long {
        val current = readRuntimeStartedAt()
        if (!forceNew && current > 0L) return current

        val fromRootPidFile = if (!forceNew && mode == RunMode.Root) {
            RootRuntimeController.getPidFileLastModified(context)
        } else {
            null
        }

        val startedAt = fromRootPidFile ?: System.currentTimeMillis()
        saveRuntimeStartedAt(startedAt)
        return startedAt
    }

    private fun readRuntimeStartedAt(): Long {
        return prefs.getLong(KEY_RUNTIME_STARTED_AT_MS, 0L)
    }

    private fun saveRuntimeStartedAt(startedAt: Long) {
        prefs.edit().putLong(KEY_RUNTIME_STARTED_AT_MS, startedAt).apply()
    }

    private fun clearRuntimeStartedAt() {
        prefs.edit().remove(KEY_RUNTIME_STARTED_AT_MS).apply()
    }

    private fun uptimeSecondsFrom(startedAtMs: Long): Long {
        return ((System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)) / 1000L
    }

    private fun isPortOpen(port: Int): Boolean {
        var socket: Socket? = null
        return try {
            socket = Socket()
            socket.soTimeout = 220
            socket.connect(InetSocketAddress("127.0.0.1", port), 220)
            true
        } catch (_: Exception) {
            false
        } finally {
            runCatching { socket?.close() }
        }
    }

    private suspend fun waitForPort(port: Int, wantOpen: Boolean, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val open = isPortOpen(port)
            if (open == wantOpen) return true
            delay(160)
        }
        return false
    }

    private suspend fun waitForRuntimeStopped(mode: RunMode, oldPort: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val stopped = when (mode) {
                RunMode.Normal -> isNormalServiceStopped() && !isPortOpen(oldPort)
                RunMode.Root -> !RootRuntimeController.isRunning(context, oldPort)
            }
            if (stopped) return true
            delay(180)
        }
        return when (mode) {
            RunMode.Normal -> isNormalServiceStopped() && !isPortOpen(oldPort)
            RunMode.Root -> !RootRuntimeController.isRunning(context, oldPort)
        }
    }

    private suspend fun waitForNormalServiceStopped(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isNormalServiceStopped()) return true
            delay(150)
        }
        return isNormalServiceStopped()
    }

    private fun isNormalServiceStopped(): Boolean {
        return !isNormalServiceRunning()
    }

    @Suppress("DEPRECATION")
    private fun isNormalServiceRunning(): Boolean {
        return runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return false
            am.getRunningServices(Int.MAX_VALUE)
                .any { it.service.className == NodeService::class.java.name }
        }.getOrElse { false }
    }

    private fun buildLocalUrl(port: Int, token: String): String {
        val tokenPath = token.trim().takeIf { it.isNotEmpty() }?.let { "/$it" }.orEmpty()
        return "http://127.0.0.1:$port$tokenPath"
    }

    private fun buildLanUrl(ip: String, port: Int, token: String): String {
        val tokenPath = token.trim().takeIf { it.isNotEmpty() }?.let { "/$it" }.orEmpty()
        return "http://$ip:$port$tokenPath"
    }

    private fun isUsableIpv4(ip: String): Boolean {
        return ip != "0.0.0.0" && !ip.startsWith("169.254.")
    }

    private fun getLanIp(): String {
        val activeNetworkIp = runCatching {
            val cm = connectivityManager ?: return@runCatching null
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return@runCatching null
            val activeNetwork = cm.activeNetwork ?: return@runCatching null
            val properties = cm.getLinkProperties(activeNetwork) ?: return@runCatching null
            properties.linkAddresses
                .asSequence()
                .mapNotNull { it.address as? Inet4Address }
                .mapNotNull { it.hostAddress }
                .firstOrNull { isUsableIpv4(it) }
        }.getOrNull()
        if (!activeNetworkIp.isNullOrBlank()) {
            return activeNetworkIp
        }

        try {
            var fallbackIp: String? = null
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { intf ->
                if (!intf.isUp || intf.isLoopback) return@forEach
                val name = (intf.name ?: "").lowercase()
                val prefer = name.startsWith("wlan") ||
                    name.startsWith("eth") ||
                    name.startsWith("en") ||
                    name.startsWith("rmnet")
                intf.inetAddresses?.toList()?.forEach { addr ->
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress ?: return@forEach
                        if (!isUsableIpv4(ip)) return@forEach
                        if (prefer) return ip
                        if (fallbackIp == null) {
                            fallbackIp = ip
                        }
                    }
                }
            }
            if (!fallbackIp.isNullOrBlank()) {
                return fallbackIp
            }
        } catch (_: Exception) {
        }
        return "0.0.0.0"
    }
}
