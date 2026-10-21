package com.example.danmuapiapp.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.example.danmuapiapp.data.service.AppDiagnosticLogger
import com.example.danmuapiapp.data.service.NodeService
import com.example.danmuapiapp.data.service.NormalModeRuntimeProfiles
import com.example.danmuapiapp.data.service.RootRuntimeNotificationManager
import com.example.danmuapiapp.data.service.RootShell
import com.example.danmuapiapp.data.service.RuntimeDataAccess
import com.example.danmuapiapp.data.service.RuntimeDependencyHealthChecker
import com.example.danmuapiapp.data.service.RuntimePaths
import com.example.danmuapiapp.data.util.RuntimeTokenNormalizer
import com.example.danmuapiapp.domain.model.*
import com.example.danmuapiapp.domain.repository.AdminSessionRepository
import com.example.danmuapiapp.domain.repository.CoreRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 运行时仓库门面。
 *
 * 只保留装配、串行用户操作入口、NodeService 状态广播桥接与公开 API 转发：
 * - 状态存储与初始装载：[RuntimeStateStore]
 * - 端口/进程/归属探测：[RuntimeProcessProbe]
 * - 状态机与候选核心观察：[RuntimeStatusController]
 * - 启停、配置应用与模式切换：[RuntimeLifecycleController]
 * - 初始与周期状态协调：[RuntimeStateReconciler]
 * - 日志读取与解析：[RuntimeLogController]
 * - 工作目录热重载：[RuntimeHotReloadController]
 * - 网络事件与访问地址刷新：[RuntimeNetworkMonitorController]
 */
@Singleton
class RuntimeRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val adminSessionRepository: AdminSessionRepository,
    private val coreRepository: CoreRepository,
    private val runtimeData: RuntimeDataAccess
) : RuntimeRepository {

    private companion object {
        /** 候选核心自动恢复前等待旧实例退出的上限。 */
        const val CANDIDATE_RECOVERY_STOP_TIMEOUT_MS = 8_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val operationMutex = Mutex()
    private val normalStart = NormalRuntimeStartState()
    private val normalRuntimeEventOrder = NormalRuntimeEventOrder()
    private val userOperationInFlight = AtomicBoolean(false)

    private val probe = RuntimeProcessProbe(context)
    private val stateStore = RuntimeStateStore(
        context = context,
        isNormalProcessRunning = { probe.isNormalProcessRunning() },
        healthUptimeSeconds = { port -> probe.readHealthUptimeSeconds(port) }
    )

    private val _runtimeState = stateStore.state
    override val runtimeState: StateFlow<RuntimeState> = _runtimeState.asStateFlow()

    private val logController = RuntimeLogController(
        context = context,
        scope = scope,
        settingsRepository = settingsRepository,
        currentState = { _runtimeState.value },
        isPortOpen = { port -> probe.isPortOpen(port) }
    )
    override val logs: StateFlow<List<LogEntry>> = logController.logs

    private val hotReloadController = RuntimeHotReloadController(
        context = context,
        scope = scope,
        state = _runtimeState,
        operationMutex = operationMutex,
        hotReloadEnabled = { NormalModeRuntimeProfiles.current(context).hotReloadEnabled },
        log = ::addLog,
        restartRuntime = ::restartRuntimeFromHotReload
    )

    private val statusController = RuntimeStatusController(
        context = context,
        scope = scope,
        state = _runtimeState,
        stateStore = stateStore,
        probe = probe,
        normalStart = normalStart,
        coreRepository = coreRepository,
        log = ::addLog,
        stopHotReload = { hotReloadController.stop() },
        onRuntimeRunning = { hotReloadController.onRuntimeStateChanged(_runtimeState.value) },
        recoverCandidateCore = ::recoverCandidateCore
    )

    private val lifecycleController = RuntimeLifecycleController(
        context = context,
        scope = scope,
        state = _runtimeState,
        stateStore = stateStore,
        probe = probe,
        status = statusController,
        hotReload = hotReloadController,
        coreRepository = coreRepository,
        settingsRepository = settingsRepository,
        normalStart = normalStart,
        log = ::addLog
    )

    private val reconciler = RuntimeStateReconciler(
        context = context,
        scope = scope,
        operationMutex = operationMutex,
        state = _runtimeState,
        probe = probe,
        status = statusController,
        settingsRepository = settingsRepository,
        normalStart = normalStart,
        log = ::addLog
    )

    private val networkMonitor = RuntimeNetworkMonitorController(
        context = context,
        scope = scope,
        state = _runtimeState,
        stateStore = stateStore,
        onRootEndpointChanged = { snapshot ->
            RootRuntimeNotificationManager.show(
                context = context,
                text = snapshot.statusMessage.orEmpty(),
                endpoint = snapshot.lanUrl
            )
        }
    )

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            // 仅普通模式处理 NodeService 广播，避免 Root 模式误更新。
            if (_runtimeState.value.runMode != RunMode.Normal) return

            val status = intent.getStringExtra(NodeService.EXTRA_STATUS) ?: return
            if (!normalRuntimeEventOrder.accept(
                    processStartedAt = intent.getLongExtra(NodeService.EXTRA_PROCESS_STARTED_ELAPSED_MS, -1L),
                    generation = intent.getLongExtra(NodeService.EXTRA_RUNTIME_GENERATION, -1L),
                    sequence = intent.getLongExtra(NodeService.EXTRA_EVENT_SEQUENCE, -1L)
                )
            ) {
                AppDiagnosticLogger.i(context, "RuntimeRepo", "忽略旧 Node 实例或乱序的状态广播：$status")
                return
            }
            val message = intent.getStringExtra(NodeService.EXTRA_MESSAGE)
            val explicitStart = intent.getBooleanExtra(NodeService.EXTRA_EXPLICIT_START, false)

            when (status) {
                NodeService.STATUS_STARTING -> {
                    if (_runtimeState.value.status != ServiceStatus.Stopping) {
                        if (normalStart.startIssuedAtMs <= 0L) {
                            normalStart.startIssuedAtMs = System.currentTimeMillis()
                        }
                        normalStart.pendingExplicitStart = explicitStart
                        statusController.markStarting(message)
                    }
                }

                NodeService.STATUS_STOPPING -> {
                    if (normalStart.pendingRestart) {
                        statusController.markStopping("正在重启服务…")
                    } else {
                        statusController.markStopping(message)
                    }
                }

                NodeService.STATUS_RUNNING -> {
                    val explicit = explicitStart || normalStart.pendingExplicitStart
                    statusController.markRunning(forceNewStart = explicit, statusMessage = message)
                    normalStart.pendingExplicitStart = false
                    addLog(LogLevel.Info, "服务已启动")
                }

                NodeService.STATUS_STOPPED -> {
                    when (
                        decideNormalStoppedBroadcastAction(
                            runMode = _runtimeState.value.runMode,
                            status = _runtimeState.value.status,
                            pendingNormalRestart = normalStart.pendingRestart,
                            normalStartIssuedAtMs = normalStart.startIssuedAtMs
                        )
                    ) {
                        NormalStoppedBroadcastAction.IgnoreAsStale -> {
                            addLog(LogLevel.Info, "已忽略普通模式旧停止广播，继续等待当前启动结果")
                            return
                        }

                        NormalStoppedBroadcastAction.RestartStopReported -> {
                            normalStart.pendingExplicitStart = false
                            statusController.updateStatusMessage(
                                expectedStatus = ServiceStatus.Stopping,
                                message = "旧实例已报告停止，正在等待服务销毁…"
                            )
                            addLog(LogLevel.Info, "普通模式旧实例已报告停止，等待旧 Service 完成销毁")
                            return
                        }

                        NormalStoppedBroadcastAction.MarkStopped -> {
                            normalStart.pendingExplicitStart = false
                            statusController.markStopped(message)
                            addLog(LogLevel.Info, "服务已停止")
                        }
                    }
                }

                NodeService.STATUS_ERROR -> {
                    val error = intent.getStringExtra(NodeService.EXTRA_ERROR) ?: "未知错误"
                    normalStart.pendingExplicitStart = false
                    statusController.markError(error, statusMessage = message ?: error)
                    addLog(LogLevel.Error, "服务错误: $error")
                    val suspectedMissingPackage = RuntimeDependencyHealthChecker.recordModuleNotFoundIssue(
                        context = context,
                        projectDir = RuntimePaths.normalProjectDir(context),
                        message = error,
                        preferredVariant = _runtimeState.value.variant
                    )
                    if (error.startsWith("运行时依赖缺失：") || suspectedMissingPackage != null) {
                        scope.launch {
                            runCatching {
                                coreRepository.prepareInstalledCoreDependencyRepair(
                                    variant = _runtimeState.value.variant,
                                    origin = CoreDependencyRepairOrigin.RuntimeStart,
                                    suspectedMissingPackage = suspectedMissingPackage
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter(NodeService.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(statusReceiver, filter)
        }

        val initState = _runtimeState.value
        if (initState.status == ServiceStatus.Starting || initState.status == ServiceStatus.Running) {
            statusController.armCandidateObservation(initState.variant, initState.runMode)
        }
        if (shouldCloseRootShellSessionForMode(initState.runMode)) {
            RootShell.closeSession()
        }
        if (initState.status == ServiceStatus.Running) {
            statusController.restoreRunningStateOnStartup()
        }

        scope.launch {
            operationMutex.withLock { reconciler.reconcileInitialState() }
        }
        reconciler.startPeriodicReconcile()
        networkMonitor.start()
    }

    private fun launchSerializedUserOperation(action: String, block: suspend () -> Unit) {
        if (!userOperationInFlight.compareAndSet(false, true)) return
        hotReloadController.cancelRootFingerprintCheck()
        scope.launch {
            try {
                operationMutex.withLock {
                    runCatching {
                        runtimeData.transition { block() }
                    }.onFailure {
                        handleRuntimeOperationFailure(action, it)
                    }
                }
            } finally {
                userOperationInFlight.set(false)
            }
        }
    }

    private fun handleRuntimeOperationFailure(action: String, throwable: Throwable) {
        val detail = ErrorHandler.buildDetailedMessage(throwable)
        val message = "${action}异常：$detail"
        statusController.markError(message)
        addLog(LogLevel.Error, message)
    }

    /** 热重载触发的重启：与用户操作共用同一个互斥锁，但不占用用户操作闸门。 */
    private suspend fun restartRuntimeFromHotReload() {
        runtimeData.transition { lifecycleController.restartServiceLocked() }
    }

    /**
     * 候选核心启动失败后的自动恢复编排：停止异常实例、还原上一个可用版本并重新启动。
     * 具体状态迁移交给 [RuntimeStatusController]，启停交给 [RuntimeLifecycleController]。
     */
    private suspend fun recoverCandidateCore(
        attempt: RuntimeStatusController.CandidateStartAttempt,
        reason: String
    ) {
        operationMutex.withLock {
            runtimeData.transition recovery@ {
                val candidate = coreRepository.candidateState.value ?: return@recovery
                if (!CoreCandidateRecoveryPolicy.matchesAttempt(
                        candidate = candidate,
                        variant = attempt.variant,
                        runMode = attempt.runMode,
                        installedAtMs = attempt.candidateInstalledAtMs
                    )
                ) {
                    return@recovery
                }
                if (!candidate.hasRecoveryPoint) {
                    coreRepository.confirmCandidateCore(
                        attempt.variant,
                        attempt.runMode,
                        attempt.candidateInstalledAtMs
                    )
                    addLog(LogLevel.Error, "新安装核心启动失败，且没有可恢复的旧版本：$reason")
                    return@recovery
                }

                val transitionId = statusController.beginTransition(
                    kind = RuntimeTransitionKind.RecoveringCore,
                    message = "新核心启动异常，正在恢复上一个可用版本…"
                )
                try {
                    addLog(LogLevel.Warn, "候选核心启动异常，正在恢复上一个可用版本：$reason")
                    val snapshot = _runtimeState.value
                    if (snapshot.status != ServiceStatus.Stopped) {
                        statusController.updateTransition(transitionId, "正在安全停止异常核心…")
                        lifecycleController.stopServiceLocked()
                        val stopped = probe.waitForRuntimeStopped(
                            mode = attempt.runMode,
                            oldPort = snapshot.port,
                            timeoutMs = CANDIDATE_RECOVERY_STOP_TIMEOUT_MS
                        )
                        if (!stopped) {
                            addLog(LogLevel.Error, "候选核心进程未能安全停止，已取消自动恢复以保护运行目录")
                            return@recovery
                        }
                    }

                    statusController.updateTransition(transitionId, "正在恢复上一个可用核心…")
                    coreRepository.restoreCandidateCore(
                        attempt.variant,
                        attempt.runMode,
                        attempt.candidateInstalledAtMs
                    ).fold(
                        onSuccess = { restored ->
                            if (!restored) {
                                addLog(LogLevel.Error, "候选核心恢复点已失效，请手动重新安装核心")
                                return@fold
                            }
                            statusController.markStopped("新核心启动失败，已恢复上一个可用版本")
                            addLog(LogLevel.Warn, "已恢复上一个可用核心，正在重新启动服务")
                            statusController.updateTransition(transitionId, "已恢复上一个版本，正在重新启动服务…")
                            lifecycleController.startServiceLocked(startingStatusMessage = "已恢复上一个版本，正在重新启动…")
                        },
                        onFailure = { error ->
                            statusController.markError("自动恢复失败：${error.message ?: "未知错误"}")
                            addLog(LogLevel.Error, "自动恢复核心失败：${error.message ?: "未知错误"}")
                        }
                    )
                } finally {
                    statusController.endTransition(transitionId)
                }
            }
        }
    }

    override fun startService() {
        launchSerializedUserOperation("启动服务") {
            lifecycleController.startServiceLocked()
        }
    }

    override fun stopService() {
        statusController.cancelCandidateObservation()
        launchSerializedUserOperation("停止服务") {
            lifecycleController.stopServiceLocked()
        }
    }

    override fun restartService() {
        statusController.cancelCandidateObservation()
        launchSerializedUserOperation("重启服务") {
            lifecycleController.restartServiceLocked()
        }
    }

    override suspend fun restartServiceAndAwait() {
        // 端口探测 / 健康检查都是阻塞网络操作，必须离开主线程，
        // 否则会被 BlockGuard 抛 NetworkOnMainThreadException，探测结果全部变成“关闭”。
        withContext(Dispatchers.IO) {
            statusController.cancelCandidateObservation()
            operationMutex.withLock {
                runCatching {
                    runtimeData.transition { lifecycleController.restartServiceLocked() }
                }.onFailure {
                    handleRuntimeOperationFailure("重启服务", it)
                }
            }
        }
    }

    override fun refreshRuntimeState() {
        reconciler.refreshAsync()
    }

    override suspend fun refreshRuntimeStateAndAwait() {
        withContext(Dispatchers.IO) {
            reconciler.refreshNow()
        }
    }

    override fun setAppForeground(foreground: Boolean) {
        reconciler.setAppForeground(foreground)
    }

    override fun refreshLogs() {
        logController.refresh()
    }

    override fun applyServiceConfig(
        port: Int,
        token: String,
        restartIfRunning: Boolean,
        listenMode: RuntimeListenMode?
    ) {
        statusController.cancelCandidateObservation()
        launchSerializedUserOperation("应用服务配置") {
            lifecycleController.applyServiceConfigLocked(
                port = port,
                token = RuntimeTokenNormalizer.normalizeInput(token),
                listenMode = listenMode ?: _runtimeState.value.listenMode,
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
            token = RuntimeTokenNormalizer.normalizeInput(token),
            restartIfRunning = false
        )
    }

    override fun updateVariant(variant: ApiVariant) {
        statusController.cancelCandidateObservation()
        stateStore.persistSelectedVariant(variant, commit = true)
        _runtimeState.update { it.copy(variant = variant) }
    }

    override fun updateRunMode(mode: RunMode) {
        launchSerializedUserOperation("切换运行模式") {
            lifecycleController.switchRunMode(mode)
        }
    }

    override fun beginRuntimeTransition(kind: RuntimeTransitionKind, message: String): Long {
        return statusController.beginTransition(kind, message)
    }

    override fun updateRuntimeTransition(id: Long, message: String) {
        statusController.updateTransition(id, message)
    }

    override fun endRuntimeTransition(id: Long) {
        statusController.endTransition(id)
    }

    override fun clearLogs() {
        logController.clear()
    }

    override fun addLog(level: LogLevel, message: String) {
        logController.addLog(level, message)
    }
}
