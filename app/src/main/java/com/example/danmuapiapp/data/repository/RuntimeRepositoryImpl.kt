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
import androidx.core.content.edit
import com.example.danmuapiapp.data.util.DotEnvCodec
import com.example.danmuapiapp.data.util.TokenDefaults
import com.example.danmuapiapp.data.service.AppDiagnosticLogger
import com.example.danmuapiapp.data.service.NodeService
import com.example.danmuapiapp.data.service.NodeProjectManager
import com.example.danmuapiapp.data.service.NormalModeRuntimeProfiles
import com.example.danmuapiapp.data.service.RootRuntimeController
import com.example.danmuapiapp.data.service.RuntimePaths
import com.example.danmuapiapp.data.service.RootShell
import com.example.danmuapiapp.data.service.RootAutoStartModule
import com.example.danmuapiapp.data.service.RootAutoStartPrefs
import com.example.danmuapiapp.data.service.RuntimeModePrefs
import com.example.danmuapiapp.domain.model.*
import com.example.danmuapiapp.domain.repository.AdminSessionRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.domain.repository.SettingsRepository
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuntimeRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val adminSessionRepository: AdminSessionRepository
) : RuntimeRepository {

    companion object {
        private const val KEY_RUNTIME_STARTED_AT_MS = "runtime_started_at_ms"
        private const val LOG_HTTP_TIMEOUT_MS = 900
        private const val LOG_CLEAR_HTTP_TIMEOUT_MS = 1200
        private const val NETWORK_URL_REFRESH_DEBOUNCE_MS = 300L
        private const val NORMAL_START_TIMEOUT_PRIMARY_MS = 15_000L
        private const val NORMAL_START_TIMEOUT_EXTEND_MS = 20_000L
        private const val NORMAL_STATE_RECONCILE_INTERVAL_MS = 8_000L
        private const val NORMAL_START_STALE_TIMEOUT_MS =
            NORMAL_START_TIMEOUT_PRIMARY_MS + NORMAL_START_TIMEOUT_EXTEND_MS + 8_000L
        private const val NORMAL_STALE_PROCESS_CONFIRM_TIMEOUT_MS = 1500L
        private const val NORMAL_STALE_PROCESS_KILL_TIMEOUT_MS = 4000L
        private const val NORMAL_STALE_PROCESS_RETRY_DELAY_MS = 220L
        private const val NORMAL_RESTART_STOP_TIMEOUT_MS = 12_000L
        private const val NORMAL_STOP_TIMEOUT_MS = 8_000L
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
        private val ISO_BRACKET_LOG_REGEX =
            Regex("""^\[?(\d{4}-\d{2}-\d{2}T[^\]]+)\]?\s+\[([A-Z]+)\]\s*(.*)$""")
        private val LOCAL_BRACKET_LOG_REGEX =
            Regex("""^\[?(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})\]?\s+\[([A-Z]+)\]\s*(.*)$""")
        private val ISO_COLON_LOG_REGEX =
            Regex("""^\[?(\d{4}-\d{2}-\d{2}T[^\]]+)\]?\s+([A-Za-z]+):\s*(.*)$""")
        private val LOCAL_COLON_LOG_REGEX =
            Regex("""^\[?(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})\]?\s+([A-Za-z]+):\s*(.*)$""")
        private val SERVICE_LOG_REGEX =
            Regex("""^\[([^\]]+)]\s+([A-Za-z]+):\s*(.*)$""")
        private val STACK_TRACE_LINE_REGEX =
            Regex("""^(?:at\s+\S+|\.\.\. \d+ more|Caused by:|Suppressed:).*$""")
        private val TIMESTAMP_TAG_REGEX =
            Regex("""^\d{4}-\d{2}-\d{2}(?:[ T]\d{2}:\d{2}:\d{2}.*)?$""")
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
    private var appFileLogsCache: List<LogEntry> = emptyList()
    @Volatile
    private var clearedServiceLogCounts: Map<LogEntry, Int> = emptyMap()

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            // 仅普通模式处理 NodeService 广播，避免 Root 模式误更新。
            if (_runtimeState.value.runMode != RunMode.Normal) return

            val status = intent.getStringExtra(NodeService.EXTRA_STATUS) ?: return
            val message = intent.getStringExtra(NodeService.EXTRA_MESSAGE)
            val explicitStart = intent.getBooleanExtra(NodeService.EXTRA_EXPLICIT_START, false)

            when (status) {
                NodeService.STATUS_STARTING -> {
                    if (_runtimeState.value.status != ServiceStatus.Stopping) {
                        if (normalStartIssuedAtMs <= 0L) {
                            normalStartIssuedAtMs = System.currentTimeMillis()
                        }
                        normalPendingExplicitStart = explicitStart
                        markStarting(message)
                    }
                }

                NodeService.STATUS_STOPPING -> {
                    if (pendingNormalRestart) {
                        markStopping("正在重启服务…")
                    } else {
                        markStopping(message)
                    }
                }

                NodeService.STATUS_RUNNING -> {
                    val explicit = explicitStart || normalPendingExplicitStart
                    markRunning(forceNewStart = explicit, statusMessage = message)
                    normalPendingExplicitStart = false
                    addLog(LogLevel.Info, "服务已启动")
                }

                NodeService.STATUS_STOPPED -> {
                    when (
                        decideNormalStoppedBroadcastAction(
                            runMode = _runtimeState.value.runMode,
                            status = _runtimeState.value.status,
                            pendingNormalRestart = pendingNormalRestart,
                            normalStartIssuedAtMs = normalStartIssuedAtMs
                        )
                    ) {
                        NormalStoppedBroadcastAction.IgnoreAsStale -> {
                            addLog(LogLevel.Info, "已忽略普通模式旧停止广播，继续等待当前启动结果")
                            return
                        }

                        NormalStoppedBroadcastAction.CompleteRestartWait -> {
                            normalStoppedBroadcastSeq.incrementAndGet()
                            normalPendingExplicitStart = false
                            updateStatusMessage(
                                expectedStatus = ServiceStatus.Stopping,
                                message = "旧实例已停止，正在重新启动服务…"
                            )
                            addLog(LogLevel.Info, "普通模式旧实例已停止，重启流程继续")
                            return
                        }

                        NormalStoppedBroadcastAction.MarkStopped -> {
                            normalStoppedBroadcastSeq.incrementAndGet()
                            normalPendingExplicitStart = false
                            markStopped(message)
                            addLog(LogLevel.Info, "服务已停止")
                        }
                    }
                }

                NodeService.STATUS_ERROR -> {
                    val error = intent.getStringExtra(NodeService.EXTRA_ERROR) ?: "未知错误"
                    normalPendingExplicitStart = false
                    markError(error, statusMessage = message ?: error)
                    addLog(LogLevel.Error, "服务错误: $error")
                }
            }
        }
    }

    private var uptimeJob: Job? = null
    private var hotReloadWatcher: WorkDirHotReloadWatcher? = null
    private var hotReloadJob: Job? = null
    private var rootFingerprintCheckJob: Job? = null
    private var pendingHotReloadReason: String? = null
    private var lastHotReloadAtMs: Long = 0L
    private var hotReloadSuppressUntilMs: Long = 0L
    private var rootWorkDirFingerprint: String? = null
    private var lastRootFingerprintCheckAtMs: Long = 0L
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var networkUrlRefreshJob: Job? = null
    @Volatile
    private var pendingNormalRestart = false

    @Volatile
    private var normalStartIssuedAtMs = 0L

    @Volatile
    private var normalPendingExplicitStart = false
    private val normalStoppedBroadcastSeq = AtomicLong(0L)

    private var reconcileConsecutiveDeadCount = 0
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
        startNormalStateReconciler()
        startNetworkMonitor()
    }

    private fun loadInitialState(): RuntimeState {
        val mode = RuntimeModePrefs.get(context)
        val defaultTokenEnvFile = normalRuntimeEnvFileForBootstrap(mode)
        val envValues = readRuntimeEnvValues(defaultTokenEnvFile)
        val portFromEnv = envValues["DANMU_API_PORT"]?.toIntOrNull()?.takeIf { it in 1..65535 }
        val port = if (prefs.contains("port")) {
            prefs.getInt("port", 9321)
        } else {
            portFromEnv ?: 9321
        }
        val token = TokenDefaults.resolveTokenFromPrefs(prefs, context, defaultTokenEnvFile)
        val runtimeVariant = normalizeVariantKey(
            if (prefs.contains("variant")) prefs.getString("variant", null) else null
        )
        val legacyVariant = normalizeVariantKey(
            context.getSharedPreferences("danmu_api_variant", Context.MODE_PRIVATE)
                .getString("variant", null)
        )
        val envVariant = normalizeVariantKey(envValues["DANMU_API_VARIANT"])
        val selectedVariantKey = runtimeVariant ?: legacyVariant ?: envVariant ?: ApiVariant.Stable.key
        val variant = ApiVariant.entries.find { it.key == selectedVariantKey } ?: ApiVariant.Stable

        if (runtimeVariant == null || legacyVariant == null || runtimeVariant != selectedVariantKey || legacyVariant != selectedVariantKey) {
            persistSelectedVariant(variant, commit = false)
        }

        val portOpen = isPortOpen(port)
        // 普通模式的真值源以本地端口是否可达为准。
        // 主进程被系统回收后，ActivityManager 对远程 :node 进程的枚举并不稳定，
        // 继续把“端口可达 && 能枚举到进程”当成运行条件，会导致服务实际还活着但 UI 误判为已停。
        val normalRuntimeAlive = isNormalRuntimeReachable(port)
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

    private fun normalRuntimeEnvFileForBootstrap(mode: RunMode): File? {
        if (mode != RunMode.Normal) return null
        return runCatching {
            val projectDir = RuntimePaths.normalProjectDir(context)
            val entryFile = File(projectDir, "main.js")
            if (!projectDir.exists() || !projectDir.isDirectory || !entryFile.exists()) {
                return@runCatching null
            }
            File(projectDir, "config/.env")
        }.getOrNull()
    }

    private fun readRuntimeEnvValues(envFile: File?): Map<String, String> {
        if (envFile == null || !envFile.exists() || !envFile.isFile) return emptyMap()
        return runCatching {
            DotEnvCodec.parse(envFile.readText(Charsets.UTF_8))
        }.getOrDefault(emptyMap())
    }

    private val userOperationInFlight = AtomicBoolean(false)

    private fun launchSerializedUserOperation(action: String, block: suspend () -> Unit) {
        if (!userOperationInFlight.compareAndSet(false, true)) return
        cancelRootFingerprintCheck()
        scope.launch {
            try {
                operationMutex.withLock {
                    runCatching {
                        block()
                    }.onFailure {
                        handleRuntimeOperationFailure(action, it)
                    }
                }
            } finally {
                userOperationInFlight.set(false)
            }
        }
    }

    override fun startService() {
        launchSerializedUserOperation("启动服务") {
            startServiceLocked()
        }
    }

    override fun stopService() {
        launchSerializedUserOperation("停止服务") {
            stopServiceLocked()
        }
    }

    override fun restartService() {
        launchSerializedUserOperation("重启服务") {
            restartServiceLocked()
        }
    }

    override fun refreshRuntimeState() {
        scope.launch {
            runCatching {
                operationMutex.withLock {
                    reconcileInitialState()
                    if (_runtimeState.value.runMode == RunMode.Normal) {
                        reconcileNormalStateLocked()
                    }
                }
            }.onFailure {
                AppDiagnosticLogger.w(
                    context,
                    "RuntimeRepo",
                    "主动刷新运行状态失败：${it.message ?: "未知错误"}",
                    it
                )
            }
        }
    }

    override fun refreshLogs() {
        scope.launch {
            refreshLogsOnce()
        }
    }

    override fun applyServiceConfig(port: Int, token: String, restartIfRunning: Boolean) {
        launchSerializedUserOperation("应用服务配置") {
            applyServiceConfigLocked(
                port = port,
                token = token.trim(),
                restartIfRunning = restartIfRunning
            )
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
        persistSelectedVariant(variant, commit = true)
        _runtimeState.update { it.copy(variant = variant) }
    }

    private fun persistSelectedVariant(variant: ApiVariant, commit: Boolean) {
        prefs.edit(commit = commit) { putString("variant", variant.key) }
        context.getSharedPreferences("danmu_api_variant", Context.MODE_PRIVATE).edit(commit = commit) {
            putString("variant", variant.key)
        }
    }

    private fun normalizeVariantKey(raw: String?): String? {
        val value = raw?.trim()?.lowercase().orEmpty()
        return when (value) {
            "stable" -> "stable"
            "dev", "develop", "development" -> "dev"
            "custom" -> "custom"
            else -> null
        }
    }

    override fun updateRunMode(mode: RunMode) {
        launchSerializedUserOperation("切换运行模式") {
            val current = _runtimeState.value
            if (current.runMode == mode) return@launchSerializedUserOperation

            if (mode.requiresRoot) {
                val rootCheck = RootShell.exec("id", timeoutMs = 3500L)
                if (!rootCheck.ok) {
                    addLog(LogLevel.Warn, "运行模式切换失败：${rootSwitchDeniedReason(rootCheck)}")
                    return@launchSerializedUserOperation
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
                    errorMessage = null,
                    statusMessage = null
                )
            }
            stopWorkDirHotReload()

            addLog(LogLevel.Info, "运行模式已切换为 ${mode.label}")

            if (shouldResume) {
                startServiceLocked()
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

            val stopBroadcastSeqBefore = normalStoppedBroadcastSeq.get()
            stopServiceLocked()
            val stopped = if (oldMode == RunMode.Normal) {
                waitForRestartStopCompletion(
                    mode = oldMode,
                    oldPort = oldPort,
                    timeoutMs = 12_000L,
                    stopBroadcastSeqBefore = stopBroadcastSeqBefore
                )
            } else {
                waitForRuntimeStopped(oldMode, oldPort, timeoutMs = 12_000L)
            }
            if (!stopped) {
                val detail = "应用服务配置失败：旧实例停止超时"
                markError(detail)
                addLog(LogLevel.Error, detail)
                return
            }

            persistServiceConfigChanges(
                port = port,
                token = token,
                applyPort = portChanged,
                applyToken = tokenChanged
            )
            syncRuntimeEnvFromPrefs(oldMode)
            addLog(LogLevel.Info, "配置已写入，正在启动服务...")
            startServiceLocked(startingStatusMessage = "正在重启服务以应用新配置…")
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
        prefs.edit {
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
        }

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
        if (mode == RunMode.Normal) {
            val normalDir = runCatching {
                NodeProjectManager.ensureProjectExtracted(context, RuntimePaths.normalProjectDir(context))
            }.getOrElse {
                addLog(LogLevel.Warn, "同步运行时配置失败：${it.message ?: "无法初始化工作目录"}")
                return
            }

            runCatching {
                NodeProjectManager.writeRuntimeEnv(
                    context = context,
                    targetProjectDir = normalDir,
                    preferredVariantKey = _runtimeState.value.variant.key
                )
            }.onFailure {
                addLog(LogLevel.Warn, "写入运行时配置失败：${it.message ?: "未知错误"}")
                return
            }
            return
        }

        // Root 模式仅同步 Root 目录，避免改配置时回写普通模式目录。
        val rootSyncResult = RootRuntimeController.syncRuntimeEnvOnly(context)
        if (!rootSyncResult.ok) {
            val detail = rootSyncResult.detail.ifBlank { rootSyncResult.message }
            addLog(LogLevel.Warn, "同步 Root 运行时配置失败：$detail")
        }
    }

    private fun normalRuntimeProfile() = NormalModeRuntimeProfiles.current(context)

    override fun clearLogs() {
        clearedServiceLogCounts = serviceLogsCache.groupingBy { it }.eachCount()
        serviceLogsCache = emptyList()
        appFileLogsCache = emptyList()
        _eventLogs.value = emptyList()
        _logs.value = emptyList()
        AppDiagnosticLogger.clearAll(context)
        scope.launch {
            clearServiceLogsOnce()
            refreshLogsOnce()
        }
    }

    override fun addLog(level: LogLevel, message: String) {
        _eventLogs.update { current ->
            (current + LogEntry(
                level = level,
                message = message,
                source = AppLogSource.App,
                tag = "Runtime"
            )).takeLast(settingsRepository.logMaxCount.value)
        }
        publishMergedLogs()
    }

    private suspend fun startServiceLocked(startingStatusMessage: String? = null) {
        val state = _runtimeState.value
        if (state.status == ServiceStatus.Running || state.status == ServiceStatus.Starting) return

        if (state.runMode == RunMode.Normal) {
            val recovered = recoverStaleNormalProcessIfNeeded(state.port)
            if (!recovered) return
        }

        _runtimeState.update {
            it.copy(
                status = ServiceStatus.Starting,
                errorMessage = null,
                statusMessage = startingStatusMessage ?: "正在准备启动服务…"
            )
        }
        normalPendingExplicitStart = state.runMode == RunMode.Normal
        clearRuntimeStartedAt()
        addLog(LogLevel.Info, "正在启动服务...")
        persistSelectedVariant(state.variant, commit = true)

        when (state.runMode) {
            RunMode.Normal -> {
                if (state.port in 1..1023) {
                    val reason = "普通模式无法监听 1-1023 端口，请切换 Root 模式或改用 1024+ 端口"
                    markError(reason)
                    addLog(LogLevel.Error, reason)
                    return
                }
                val normalProfile = normalRuntimeProfile()
                val projectDir = RuntimePaths.normalProjectDir(context)
                val selectedCoreDir = File(projectDir, "danmu_api_${state.variant.key}")
                val selectedCoreReady = runCatching {
                    NodeProjectManager.hasValidCore(selectedCoreDir)
                }.getOrDefault(false)
                if (!selectedCoreReady) {
                    val fallback = ApiVariant.entries.firstOrNull { variant ->
                        NodeProjectManager.hasValidCore(File(projectDir, "danmu_api_${variant.key}"))
                    }
                    val reason = if (fallback != null && fallback != state.variant) {
                        "当前核心 ${settingsRepository.coreDisplayNames.value.resolve(state.variant)} 未安装，可先切换到 ${settingsRepository.coreDisplayNames.value.resolve(fallback)} 或下载后再启动"
                    } else {
                        "当前核心 ${settingsRepository.coreDisplayNames.value.resolve(state.variant)} 未安装，请先下载核心后再启动"
                    }
                    markError(reason)
                    addLog(LogLevel.Error, reason)
                    return
                }
                if (normalProfile.conservativeMode) {
                    val reasonText = when (normalProfile.strategyMode) {
                        NormalModeStabilityMode.Auto -> {
                            val reasons = listOfNotNull(
                                "低内存设备".takeIf { normalProfile.lowRamDevice },
                                "共享存储目录".takeIf { normalProfile.slowStorageWorkDir }
                            )
                            if (reasons.isEmpty()) {
                                "（自动）"
                            } else {
                                "（自动：${reasons.joinToString("、")}）"
                            }
                        }
                        NormalModeStabilityMode.PreferStability -> "（手动开启）"
                        NormalModeStabilityMode.PreferPerformance -> ""
                    }
                    addLog(
                        LogLevel.Info,
                        "已启用普通模式稳定优先策略$reasonText：关闭 worker/热更新并延长启动等待时间"
                    )
                }
                val envSynced = runCatching {
                    // 仅在现有运行目录可用时做快速配置同步，避免主进程重复走重型解压。
                    NodeProjectManager.syncRuntimeEnvIfProjectReady(
                        context = context,
                        targetProjectDir = projectDir,
                        preferredVariantKey = state.variant.key
                    )
                }
                if (envSynced.isFailure) {
                    val reason = envSynced.exceptionOrNull()?.message ?: "未知错误"
                    markError("运行时配置写入失败：$reason")
                    addLog(LogLevel.Error, "普通模式启动前写入配置失败: $reason")
                    return
                }
                val startResult = runCatching {
                    NodeService.start(context, userInitiated = true)
                }
                if (startResult.isFailure) {
                    val reason = ErrorHandler.buildDetailedMessage(
                        startResult.exceptionOrNull() ?: IllegalStateException("未知错误")
                    )
                    markError("普通模式拉起前台服务失败：$reason")
                    addLog(LogLevel.Error, "普通模式拉起前台服务失败: $reason")
                    return
                }
                normalStartIssuedAtMs = System.currentTimeMillis()

                scope.launch {
                    delay(normalProfile.startupPrimaryNoticeMs)
                    val snapshot = _runtimeState.value
                    if (snapshot.runMode == RunMode.Normal && snapshot.status == ServiceStatus.Starting) {
                        if (isPortOpen(snapshot.port)) {
                            markRunning(forceNewStart = normalPendingExplicitStart)
                        } else {
                            addLog(LogLevel.Warn, "普通模式首次启动较慢，继续等待...")
                            updateStatusMessage(
                                expectedStatus = ServiceStatus.Starting,
                                message = "设备较慢，正在继续等待服务就绪…可点击取消启动"
                            )
                            delay(normalProfile.startupSecondaryNoticeMs)
                            val retrySnapshot = _runtimeState.value
                            if (retrySnapshot.runMode == RunMode.Normal &&
                                retrySnapshot.status == ServiceStatus.Starting
                            ) {
                                if (isPortOpen(retrySnapshot.port)) {
                                    markRunning(forceNewStart = normalPendingExplicitStart)
                                } else {
                                    addLog(LogLevel.Warn, "普通模式启动耗时较长，继续等待服务自行就绪...")
                                    updateStatusMessage(
                                        expectedStatus = ServiceStatus.Starting,
                                        message = "启动耗时较长，仍在等待服务就绪…"
                                    )
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

        val wasStarting = state.status == ServiceStatus.Starting
        _runtimeState.update {
            it.copy(
                status = ServiceStatus.Stopping,
                errorMessage = null,
                statusMessage = "正在安全停止服务…"
            )
        }
        addLog(LogLevel.Info, "正在停止服务...")
        uptimeJob?.cancel()

        when (state.runMode) {
            RunMode.Normal -> {
                val restarting = pendingNormalRestart
                val stopBroadcastSeqBefore = normalStoppedBroadcastSeq.get()
                val stopResult = runCatching {
                    NodeService.stop(context)
                }
                if (stopResult.isFailure) {
                    val reason = ErrorHandler.buildDetailedMessage(
                        stopResult.exceptionOrNull() ?: IllegalStateException("未知错误")
                    )
                    markError("普通模式下发停止指令失败：$reason")
                    addLog(LogLevel.Error, "普通模式下发停止指令失败: $reason")
                    return
                }
                val stopPort = state.port
                scope.launch {
                    delay(3_000L)
                    updateStatusMessage(
                        expectedStatus = ServiceStatus.Stopping,
                        message = if (restarting) {
                            "重启前正在停止旧实例，若长时间无响应会自动处理"
                        } else {
                            "仍在停止中，长时间无响应可再次尝试停止"
                        }
                    )
                }
                scope.launch {
                    val stopped = if (restarting) {
                        waitForRestartStopCompletion(
                            mode = RunMode.Normal,
                            oldPort = stopPort,
                            timeoutMs = NORMAL_STOP_TIMEOUT_MS,
                            stopBroadcastSeqBefore = stopBroadcastSeqBefore
                        )
                    } else {
                        waitForRuntimeStopped(
                            mode = RunMode.Normal,
                            oldPort = stopPort,
                            timeoutMs = NORMAL_STOP_TIMEOUT_MS
                        )
                    }
                    val snapshot = _runtimeState.value
                    if (snapshot.runMode == RunMode.Normal && snapshot.status == ServiceStatus.Stopping) {
                        if (stopped && restarting) {
                            updateStatusMessage(
                                expectedStatus = ServiceStatus.Stopping,
                                message = "旧实例已停止，正在重新启动服务…"
                            )
                            addLog(LogLevel.Info, "普通模式停止完成，等待重启流程继续")
                        } else if (stopped) {
                            markStopped()
                        } else if (!restarting) {
                            addLog(LogLevel.Warn, "普通模式停止较慢，已恢复为可重试状态")
                            if (wasStarting) {
                                markStarting("启动取消较慢，可再次点击取消启动")
                            } else {
                                markRunning(
                                    forceNewStart = false,
                                    statusMessage = "停止较慢，可再次点击停止"
                                )
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
                    val oldPort = state.port
                    val stopBroadcastSeqBefore = normalStoppedBroadcastSeq.get()
                    stopServiceLocked()
                    val stopped = waitForRestartStopCompletion(
                        mode = RunMode.Normal,
                        oldPort = oldPort,
                        timeoutMs = NORMAL_RESTART_STOP_TIMEOUT_MS,
                        stopBroadcastSeqBefore = stopBroadcastSeqBefore
                    )
                    if (!stopped) {
                        addLog(LogLevel.Warn, "普通模式重启前停止较慢，请稍后重试")
                        markRunning(
                            forceNewStart = false,
                            statusMessage = "重启前停止较慢，请稍后重试"
                        )
                        return
                    }

                    startServiceLocked(startingStatusMessage = "正在重启服务…")
                    val started = waitForPort(
                        oldPort,
                        wantOpen = true,
                        timeoutMs = NORMAL_RESTART_START_TIMEOUT_MS
                    )
                    if (started) {
                        if (_runtimeState.value.status != ServiceStatus.Running) {
                            markRunning(forceNewStart = true)
                        }
                    } else {
                        if (_runtimeState.value.status == ServiceStatus.Starting) {
                            addLog(LogLevel.Warn, "普通模式重启耗时较长，继续等待服务自行就绪...")
                            updateStatusMessage(
                                expectedStatus = ServiceStatus.Starting,
                                message = "重启耗时较长，仍在等待服务就绪…"
                            )
                        }
                    }
                } finally {
                    pendingNormalRestart = false
                }
            }

            RunMode.Root -> {
                _runtimeState.update {
                    it.copy(
                        status = ServiceStatus.Stopping,
                        errorMessage = null,
                        statusMessage = "正在重启服务…"
                    )
                }
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

    private suspend fun recoverStaleNormalProcessIfNeeded(port: Int): Boolean {
        val staleProcess = confirmStaleNormalProcess(port, NORMAL_STALE_PROCESS_CONFIRM_TIMEOUT_MS)
        if (!staleProcess) return true

        addLog(LogLevel.Warn, "检测到普通模式残留进程，启动前先回收旧进程")
        val killed = NodeService.killProcessIfRunning(context)
        if (!killed && NodeService.isProcessRunning(context)) {
            markError("普通模式残留进程回收失败，请重试启动")
            addLog(LogLevel.Error, "普通模式残留进程回收失败：无法结束 :node 进程")
            return false
        }

        val stopped = waitForNormalProcessStopped(
            port = port,
            timeoutMs = NORMAL_STALE_PROCESS_KILL_TIMEOUT_MS
        )
        if (!stopped) {
            markError("普通模式残留进程回收超时，请重试启动")
            addLog(LogLevel.Error, "普通模式残留进程回收超时：:node 进程未及时退出")
            return false
        }

        markStopped()
        delay(NORMAL_STALE_PROCESS_RETRY_DELAY_MS)
        return true
    }

    private suspend fun confirmStaleNormalProcess(port: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs.coerceAtLeast(0L)
        while (System.currentTimeMillis() < deadline) {
            if (!NodeService.isProcessRunning(context)) return false
            if (port in 1..65535 && isPortOpen(port)) return false
            delay(180)
        }
        return NodeService.isProcessRunning(context) &&
            (port !in 1..65535 || !isPortOpen(port))
    }

    private fun markStarting(message: String? = null) {
        _runtimeState.update {
            it.copy(
                status = ServiceStatus.Starting,
                errorMessage = null,
                statusMessage = message ?: it.statusMessage ?: "正在初始化运行环境，请稍候"
            )
        }
    }

    private fun markStopping(message: String? = null) {
        _runtimeState.update {
            it.copy(
                status = ServiceStatus.Stopping,
                errorMessage = null,
                statusMessage = message ?: it.statusMessage ?: "正在安全停止服务…"
            )
        }
        uptimeJob?.cancel()
    }

    private fun updateStatusMessage(expectedStatus: ServiceStatus, message: String) {
        _runtimeState.update { state ->
            if (state.status != expectedStatus) {
                state
            } else {
                state.copy(statusMessage = message)
            }
        }
    }

    private fun markRunning(
        pid: Int? = null,
        forceNewStart: Boolean = false,
        statusMessage: String? = null
    ) {
        val mode = _runtimeState.value.runMode
        if (mode == RunMode.Normal) {
            normalStartIssuedAtMs = 0L
            normalPendingExplicitStart = false
        }
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
                errorMessage = null,
                statusMessage = statusMessage ?: "接口已就绪，可直接在局域网访问"
            )
        }
        startUptimeCounter(startedAt)
        // 运行态改为事件驱动刷新时，这里启动热更新监听。
        handleWorkDirHotReload(_runtimeState.value)
    }

    private fun markStopped(statusMessage: String? = null) {
        if (_runtimeState.value.runMode == RunMode.Normal) {
            normalStartIssuedAtMs = 0L
            normalPendingExplicitStart = false
        }
        clearRuntimeStartedAt()
        _runtimeState.update {
            it.copy(
                status = ServiceStatus.Stopped,
                pid = null,
                uptimeSeconds = 0,
                errorMessage = null,
                statusMessage = statusMessage ?: "服务已停止"
            )
        }
        uptimeJob?.cancel()
        stopWorkDirHotReload()
    }

    private fun markError(message: String, statusMessage: String? = message) {
        val state = _runtimeState.value
        val stillRunning = isPortOpen(state.port)
        if (!stillRunning) {
            clearRuntimeStartedAt()
        }
        if (state.runMode == RunMode.Normal && !stillRunning) {
            normalStartIssuedAtMs = 0L
            normalPendingExplicitStart = false
        }

        _runtimeState.update {
            it.copy(
                status = ServiceStatus.Error,
                errorMessage = message,
                pid = if (stillRunning) it.pid else null,
                uptimeSeconds = if (stillRunning) it.uptimeSeconds else 0,
                statusMessage = statusMessage
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
        if (
            state.status == ServiceStatus.Running ||
            state.status == ServiceStatus.Starting ||
            state.status == ServiceStatus.Stopping
        ) {
            return
        }

        when (state.runMode) {
            RunMode.Normal -> {
                if (isNormalRuntimeReachable(state.port)) {
                    markRunning(forceNewStart = false)
                    if (state.status == ServiceStatus.Error || state.status == ServiceStatus.Stopped) {
                        addLog(LogLevel.Info, "检测到普通模式服务仍可访问，已自动纠正为运行中")
                    }
                }
            }

            RunMode.Root -> {
                if (RootRuntimeController.isRunning(context, state.port)) {
                    markRunning(
                        pid = RootRuntimeController.getPid(context),
                        forceNewStart = false
                    )
                    if (state.status == ServiceStatus.Error || state.status == ServiceStatus.Stopped) {
                        addLog(LogLevel.Info, "检测到 Root 模式服务仍在运行，已自动纠正为运行中")
                    }
                }
            }
        }
    }

    private fun startNormalStateReconciler() {
        scope.launch {
            while (isActive) {
                delay(NORMAL_STATE_RECONCILE_INTERVAL_MS)
                runCatching {
                    operationMutex.withLock {
                        reconcileInitialState()
                        reconcileNormalStateLocked()
                    }
                }
            }
        }
    }

    private suspend fun reconcileNormalStateLocked() {
        val state = _runtimeState.value
        if (state.runMode != RunMode.Normal) {
            normalStartIssuedAtMs = 0L
            reconcileConsecutiveDeadCount = 0
            return
        }
        if (state.status != ServiceStatus.Running &&
            state.status != ServiceStatus.Starting &&
            state.status != ServiceStatus.Stopping
        ) {
            reconcileConsecutiveDeadCount = 0
            return
        }

        val serviceRunning = isNormalServiceRunning()
        val processRunning = isNormalProcessRunning()
        val portOpen = isPortOpen(state.port)
        when (state.status) {
            ServiceStatus.Running -> {
                when {
                    portOpen -> {
                        reconcileConsecutiveDeadCount = 0
                    }

                    !portOpen -> {
                        reconcileConsecutiveDeadCount++
                        if (reconcileConsecutiveDeadCount >= 2) {
                            addLog(
                                LogLevel.Warn,
                                if (processRunning) {
                                    "检测到普通模式监听端口不可用，状态已自动重置；下次启动会先回收残留进程"
                                } else {
                                    "检测到普通模式进程已退出，状态已自动重置"
                                }
                            )
                            markStopped("服务未运行，可重新启动")
                            reconcileConsecutiveDeadCount = 0
                        }
                    }

                    else -> {
                        reconcileConsecutiveDeadCount = 0
                    }
                }
            }

            ServiceStatus.Starting -> {
                reconcileConsecutiveDeadCount = 0
                if (portOpen) {
                    markRunning(forceNewStart = normalPendingExplicitStart)
                    return
                }
                val startAt = normalStartIssuedAtMs
                if (startAt <= 0L) {
                    when {
                        serviceRunning || processRunning -> {
                            normalStartIssuedAtMs = System.currentTimeMillis()
                            updateStatusMessage(
                                expectedStatus = ServiceStatus.Starting,
                                message = state.statusMessage ?: "正在初始化运行环境，请稍候"
                            )
                        }

                        else -> {
                            markStopped("服务未运行，可重新启动")
                            addLog(LogLevel.Warn, "检测到普通模式启动状态残留，已恢复为可重试状态")
                        }
                    }
                    return
                }
                val staleTimeoutMs = normalRuntimeProfile().startupStaleTimeoutMs
                if (System.currentTimeMillis() - startAt >= staleTimeoutMs) {
                    val message = when {
                        serviceRunning -> "普通模式启动卡住：服务进程仍在但端口未就绪，请重试启动"
                        processRunning -> "普通模式启动卡住：旧进程未完全退出，请重试启动"
                        else -> "普通模式启动状态失效，请重试启动"
                    }
                    markError(message)
                    addLog(
                        LogLevel.Warn,
                        when {
                            serviceRunning -> "普通模式前台服务仍在运行，但端口长时间未就绪，已恢复为可重试状态"
                            processRunning -> "普通模式旧进程长时间未退出，已恢复为可重试状态"
                            else -> "普通模式长时间未完成启动，已自动恢复为可重试状态"
                        }
                    )
                }
            }

            ServiceStatus.Stopping -> {
                reconcileConsecutiveDeadCount = 0
                if (!processRunning && !portOpen) {
                    markStopped()
                }
            }

            ServiceStatus.Stopped,
            ServiceStatus.Error -> Unit
        }
    }

    private fun handleRuntimeOperationFailure(action: String, throwable: Throwable) {
        val detail = ErrorHandler.buildDetailedMessage(throwable)
        val message = "${action}异常：$detail"
        markError(message)
        addLog(LogLevel.Error, message)
    }

    private fun publishMergedLogs() {
        val merged = (serviceLogsCache + appFileLogsCache + _eventLogs.value)
            .sortedBy { it.timestamp }
            .takeLast(settingsRepository.logMaxCount.value)
        _logs.value = merged
    }

    private fun refreshLogsOnce() {
        if (!settingsRepository.logEnabled.value) return
        runCatching {
            val serviceLogs = filterClearedServiceLogs(collectServiceLogsOnce())
            val appLogs = collectAppLogsOnce()
            val shouldPublish =
                serviceLogs != serviceLogsCache ||
                    appLogs != appFileLogsCache ||
                    _eventLogs.value.isNotEmpty() ||
                    _logs.value.isNotEmpty()
            serviceLogsCache = serviceLogs
            appFileLogsCache = appLogs
            if (shouldPublish) {
                publishMergedLogs()
            }
        }.onFailure {
            if (_eventLogs.value.isNotEmpty() && _logs.value != _eventLogs.value) {
                publishMergedLogs()
            }
        }
    }

    private fun collectAppLogsOnce(): List<LogEntry> {
        val limit = settingsRepository.logMaxCount.value.coerceAtLeast(100)
        return (AppDiagnosticLogger.readAppEntries(context, limit) +
            AppDiagnosticLogger.readRootBootstrapEntries(context, limit))
            .sortedBy { it.timestamp }
            .takeLast(limit)
    }

    private fun collectServiceLogsOnce(): List<LogEntry> {
        val state = _runtimeState.value
        if (state.status != ServiceStatus.Running && !isPortOpen(state.port)) return emptyList()

        val baseUrl = resolveRuntimeTokenApiBaseUrl() ?: return emptyList()
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
        return if (raw.isNotBlank()) parseLogLines(raw) else emptyList()
    }

    private fun resolveRuntimeTokenApiBaseUrl(): String? {
        val state = _runtimeState.value
        val tokenPath = state.token.trim().trim('/').takeIf { it.isNotBlank() } ?: return null
        return "http://127.0.0.1:${state.port}/$tokenPath"
    }

    private fun clearServiceLogsOnce(): Boolean {
        val state = _runtimeState.value
        if (state.status != ServiceStatus.Running && !isPortOpen(state.port)) return false

        val baseUrl = resolveRuntimeTokenApiBaseUrl() ?: return false
        val cleared = runCatching {
            val conn = (URL("$baseUrl/api/logs/clear").openConnection() as HttpURLConnection).apply {
                connectTimeout = LOG_CLEAR_HTTP_TIMEOUT_MS
                readTimeout = LOG_CLEAR_HTTP_TIMEOUT_MS
                requestMethod = "POST"
                doOutput = true
                setFixedLengthStreamingMode(0)
            }
            try {
                conn.outputStream.use { }
                conn.responseCode in 200..299
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
        if (cleared) {
            clearedServiceLogCounts = emptyMap()
            return true
        }
        return false
    }

    private fun filterClearedServiceLogs(entries: List<LogEntry>): List<LogEntry> {
        val baseline = clearedServiceLogCounts
        if (baseline.isEmpty() || entries.isEmpty()) return entries

        val remaining = baseline.toMutableMap()
        var visibleStartIndex = 0
        while (visibleStartIndex < entries.size) {
            val entry = entries[visibleStartIndex]
            val count = remaining[entry] ?: 0
            if (count <= 0) break
            if (count == 1) {
                remaining.remove(entry)
            } else {
                remaining[entry] = count - 1
            }
            visibleStartIndex++
        }
        return entries.drop(visibleStartIndex)
    }

    private fun parseLogLines(raw: String): List<LogEntry> {
        val blocks = splitLogBlocks(raw)
        if (blocks.isEmpty()) return emptyList()

        return blocks.map { block ->
            val entry = parseStructuredLogLine(block)
            entry ?: LogEntry(
                timestamp = System.currentTimeMillis(),
                level = LogLevel.Info,
                message = normalizeCoreMessage(block),
                source = AppLogSource.Core,
                tag = "Core"
            )
        }.takeLast(settingsRepository.logMaxCount.value)
    }

    private fun splitLogBlocks(raw: String): List<String> {
        val blocks = mutableListOf<String>()
        val current = StringBuilder()

        raw.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isBlank()) return@forEach

            if (isStructuredLogHeader(trimmed)) {
                if (current.isNotEmpty()) {
                    blocks += current.toString().trim()
                    current.setLength(0)
                }
                current.append(trimmed)
            } else {
                if (current.isNotEmpty()) current.append('\n')
                current.append(trimmed)
            }
        }

        if (current.isNotEmpty()) {
            blocks += current.toString().trim()
        }

        return blocks
    }

    private fun isStructuredLogHeader(line: String): Boolean {
        if (
            ISO_BRACKET_LOG_REGEX.matches(line) ||
            LOCAL_BRACKET_LOG_REGEX.matches(line) ||
            ISO_COLON_LOG_REGEX.matches(line) ||
            LOCAL_COLON_LOG_REGEX.matches(line)
        ) {
            return true
        }

        val serviceMatch = SERVICE_LOG_REGEX.find(line) ?: return false
        return !looksLikeTimestampTag(serviceMatch.groupValues[1].trim())
    }

    private fun parseStructuredLogLine(block: String): LogEntry? {
        val lines = block.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return null

        val header = parseStructuredLogHeader(lines.first()) ?: return null
        val message = buildString {
            if (header.message.isNotBlank()) {
                append(header.message.trim())
            }
            if (lines.size > 1) {
                if (isNotBlank()) append('\n')
                append(lines.drop(1).joinToString("\n"))
            }
        }.ifBlank { lines.first() }

        return LogEntry(
            timestamp = header.timestamp,
            level = header.level,
            message = normalizeCoreMessage(message),
            source = AppLogSource.Core,
            tag = header.tag
        )
    }

    private fun parseStructuredLogHeader(line: String): ParsedLogHeader? {
        ISO_BRACKET_LOG_REGEX.find(line)?.let { match ->
            return ParsedLogHeader(
                timestamp = parseIsoTimestamp(match.groupValues[1]),
                level = parseLogLevel(match.groupValues[2]),
                message = match.groupValues[3],
                tag = "Core"
            )
        }

        LOCAL_BRACKET_LOG_REGEX.find(line)?.let { match ->
            return ParsedLogHeader(
                timestamp = parseLocalTimestamp(match.groupValues[1]),
                level = parseLogLevel(match.groupValues[2]),
                message = match.groupValues[3],
                tag = "Core"
            )
        }

        ISO_COLON_LOG_REGEX.find(line)?.let { match ->
            return ParsedLogHeader(
                timestamp = parseIsoTimestamp(match.groupValues[1]),
                level = parseLogLevel(match.groupValues[2]),
                message = match.groupValues[3],
                tag = "Core"
            )
        }

        LOCAL_COLON_LOG_REGEX.find(line)?.let { match ->
            return ParsedLogHeader(
                timestamp = parseLocalTimestamp(match.groupValues[1]),
                level = parseLogLevel(match.groupValues[2]),
                message = match.groupValues[3],
                tag = "Core"
            )
        }

        SERVICE_LOG_REGEX.find(line)?.let { match ->
            val tag = match.groupValues[1].trim()
            if (looksLikeTimestampTag(tag)) return null

            return ParsedLogHeader(
                timestamp = System.currentTimeMillis(),
                level = parseLogLevel(match.groupValues[2]),
                message = match.groupValues[3],
                tag = tag
            )
        }

        return null
    }

    private fun parseIsoTimestamp(raw: String): Long {
        return runCatching { Instant.parse(raw).toEpochMilli() }
            .getOrElse { System.currentTimeMillis() }
    }

    private fun parseLocalTimestamp(raw: String): Long {
        return runCatching {
            val localDt = LocalDateTime.parse(
                raw,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            )
            localDt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrElse { System.currentTimeMillis() }
    }

    private fun parseLogLevel(raw: String): LogLevel {
        return when (raw.trim().uppercase()) {
            "ERROR" -> LogLevel.Error
            "WARN", "WARNING" -> LogLevel.Warn
            else -> LogLevel.Info
        }
    }

    private fun looksLikeTimestampTag(raw: String): Boolean {
        return TIMESTAMP_TAG_REGEX.matches(raw)
    }

    private fun normalizeCoreMessage(message: String): String {
        val lines = message.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.isEmpty()) return message.trim()
        if (lines.size == 1) return lines.first()
        if (lines.drop(1).any { STACK_TRACE_LINE_REGEX.matches(it) }) {
            return lines.joinToString("\n")
        }
        return lines.joinToString(" ")
            .replace(Regex("""\s+([,;:)\]}])"""), "$1")
            .replace(Regex("""([\[{(])\s+"""), "$1")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
    }

    private data class ParsedLogHeader(
        val timestamp: Long,
        val level: LogLevel,
        val message: String,
        val tag: String
    )

    private fun handleWorkDirHotReload(state: RuntimeState) {
        if (state.status != ServiceStatus.Running) {
            stopWorkDirHotReload()
            return
        }

        when (state.runMode) {
            RunMode.Normal -> {
                if (!normalRuntimeProfile().hotReloadEnabled) {
                    stopNormalWorkDirWatcher()
                    hotReloadJob?.cancel()
                    hotReloadJob = null
                    pendingHotReloadReason = null
                    rootWorkDirFingerprint = null
                    lastRootFingerprintCheckAtMs = 0L
                    return
                }
                ensureNormalWorkDirWatcher()
                rootWorkDirFingerprint = null
                lastRootFingerprintCheckAtMs = 0L
            }

            RunMode.Root -> {
                // Root 模式以 Root 工作目录为唯一热更新来源，避免普通目录变更干扰。
                stopNormalWorkDirWatcher()
                scheduleRootFingerprintCheck()
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

        if (!normalRuntimeProfile().hotReloadEnabled) {
            return
        }

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
        cancelRootFingerprintCheck()
        hotReloadJob?.cancel()
        hotReloadJob = null
        pendingHotReloadReason = null
        rootWorkDirFingerprint = null
        lastRootFingerprintCheckAtMs = 0L
    }

    private fun cancelRootFingerprintCheck() {
        rootFingerprintCheckJob?.cancel()
        rootFingerprintCheckJob = null
    }

    private fun scheduleRootFingerprintCheck() {
        val runningJob = rootFingerprintCheckJob
        if (runningJob != null && runningJob.isActive) return
        val job = scope.launch {
            try {
                checkRootWorkDirFingerprint()
            } finally {
                if (rootFingerprintCheckJob === coroutineContext[Job]) {
                    rootFingerprintCheckJob = null
                }
            }
        }
        rootFingerprintCheckJob = job
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
        val snapshot = _runtimeState.value
        if (snapshot.status != ServiceStatus.Running) return
        if (snapshot.runMode == RunMode.Normal && !normalRuntimeProfile().hotReloadEnabled) return

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
        private val observers = ConcurrentHashMap<String, FileObserver>()
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
                rel.startsWith("config/") ||
                rel == "config" ||
                rel.startsWith("danmu_api_stable/") ||
                rel == "danmu_api_stable" ||
                rel.startsWith("danmu_api_dev/") ||
                rel == "danmu_api_dev" ||
                rel.startsWith("danmu_api_custom/") ||
                rel == "danmu_api_custom" ||
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
        prefs.edit { putLong(KEY_RUNTIME_STARTED_AT_MS, startedAt) }
    }

    private fun clearRuntimeStartedAt() {
        prefs.edit { remove(KEY_RUNTIME_STARTED_AT_MS) }
    }

    private fun uptimeSecondsFrom(startedAtMs: Long): Long {
        return ((System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)) / 1000L
    }

    private fun isPortOpen(port: Int): Boolean {
        var socket: Socket? = null
        return try {
            socket = Socket()
            socket.soTimeout = 450
            socket.connect(InetSocketAddress("127.0.0.1", port), 450)
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

    private fun isNormalRuntimeStopped(port: Int): Boolean {
        return !isNormalProcessRunning() && !isPortOpen(port)
    }

    private fun isNormalRestartStopCompleted(oldPort: Int, stopBroadcastSeqBefore: Long): Boolean {
        if (normalStoppedBroadcastSeq.get() > stopBroadcastSeqBefore) {
            return true
        }
        return !isNormalServiceRunning() && !isPortOpen(oldPort)
    }

    private suspend fun waitForRestartStopCompletion(
        mode: RunMode,
        oldPort: Int,
        timeoutMs: Long,
        stopBroadcastSeqBefore: Long
    ): Boolean {
        if (mode != RunMode.Normal) {
            return waitForRuntimeStopped(mode, oldPort, timeoutMs)
        }
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isNormalRestartStopCompleted(oldPort, stopBroadcastSeqBefore)) {
                return true
            }
            delay(180)
        }
        return isNormalRestartStopCompleted(oldPort, stopBroadcastSeqBefore)
    }

    private suspend fun waitForRuntimeStopped(mode: RunMode, oldPort: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val stopped = when (mode) {
                RunMode.Normal -> isNormalRuntimeStopped(oldPort)
                RunMode.Root -> !RootRuntimeController.isRunning(context, oldPort)
            }
            if (stopped) return true
            delay(180)
        }
        return when (mode) {
            RunMode.Normal -> isNormalRuntimeStopped(oldPort)
            RunMode.Root -> !RootRuntimeController.isRunning(context, oldPort)
        }
    }

    private fun isNormalProcessStopped(port: Int): Boolean {
        return !isNormalProcessRunning() && !isPortOpen(port)
    }

    private suspend fun waitForNormalProcessStopped(port: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isNormalProcessStopped(port)) return true
            delay(180)
        }
        return isNormalProcessStopped(port)
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

    private fun isNormalProcessRunning(): Boolean {
        return NodeService.isProcessRunning(context)
    }

    private fun isNormalRuntimeReachable(port: Int): Boolean {
        return isPortOpen(port)
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
