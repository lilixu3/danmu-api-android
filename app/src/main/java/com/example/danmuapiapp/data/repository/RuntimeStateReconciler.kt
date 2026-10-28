package com.example.danmuapiapp.data.repository

import android.content.Context
import com.example.danmuapiapp.data.service.AppDiagnosticLogger
import com.example.danmuapiapp.data.service.NormalModeRuntimeProfiles
import com.example.danmuapiapp.data.service.NormalNotificationBehaviorPrefs
import com.example.danmuapiapp.data.service.NodeService
import com.example.danmuapiapp.data.service.RootRuntimeController
import com.example.danmuapiapp.data.service.RootRuntimeRecovery
import com.example.danmuapiapp.data.service.RootShell
import com.example.danmuapiapp.data.service.RuntimeModePrefs
import com.example.danmuapiapp.domain.model.LogLevel
import com.example.danmuapiapp.domain.model.NormalNotificationBehavior
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 运行状态协调：初始接管、周期纠正、前后台切换与前台服务通知补发。
 *
 * Repository 只提供状态流、操作互斥、日志与生命周期回调；这里只做"发现真实状态并纠正"。
 */
internal class RuntimeStateReconciler(
    private val context: Context,
    private val scope: CoroutineScope,
    private val operationMutex: Mutex,
    private val state: MutableStateFlow<RuntimeState>,
    private val probe: RuntimeProcessProbe,
    private val status: RuntimeStatusController,
    private val settingsRepository: SettingsRepository,
    private val normalStart: NormalRuntimeStartState,
    private val log: (LogLevel, String) -> Unit
) {

    private companion object {
        const val NORMAL_STATE_RECONCILE_INTERVAL_MS = 8_000L
    }

    private val mayRecoverRootModeAfterReinstall = !RuntimeModePrefs.hasExplicitSelection(context)

    @Volatile
    private var appForeground = false
    private var rootReconcileConsecutiveDeadCount = 0
    private var normalReconcileConsecutivePortMisses = 0

    fun startPeriodicReconcile() {
        scope.launch {
            while (isActive) {
                delay(NORMAL_STATE_RECONCILE_INTERVAL_MS)
                if (!shouldRunPeriodicRuntimeReconcile(appForeground)) continue
                runCatching {
                    operationMutex.withLock {
                        reconcileInitialState()
                        reconcileNormalStateLocked()
                        reconcileRootStateLocked()
                    }
                }
            }
        }
    }

    fun refreshAsync() {
        scope.launch {
            try {
                refreshNow()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppDiagnosticLogger.w(
                    context,
                    "RuntimeRepo",
                    "主动刷新运行状态失败：${error.message ?: "未知错误"}",
                    error
                )
            }
        }
    }

    suspend fun refreshNow() {
        operationMutex.withLock {
            if (shouldCloseRootShellSessionForMode(state.value.runMode)) {
                RootShell.closeSession()
            }
            reconcileInitialState()
            if (state.value.runMode == RunMode.Normal) {
                reconcileNormalStateLocked()
            } else if (state.value.runMode == RunMode.Root) {
                reconcileRootStateLocked()
            }
        }
    }

    fun setAppForeground(foreground: Boolean) {
        appForeground = foreground
        if (foreground) {
            refreshAsync()
        } else {
            restoreForegroundNotificationOnBackground()
        }
    }

    /**
     * 离开应用时仅在通知确实不在位时才重发：
     * 无条件重发会让每次切后台都把通知刷到顶部，表现为"通知明明还在却被刷新"。
     *
     * 端口探测放在 IO 线程并带少量重试：主线程做阻塞网络操作会被 BlockGuard
     * 直接抛 NetworkOnMainThreadException（曾经把探测结果全部变成"端口已关闭"），
     * 而刚启动/正忙的核心也可能偶尔吃满探测超时，重试可以避免误判。
     */
    private fun restoreForegroundNotificationOnBackground() {
        val snapshot = state.value
        if (snapshot.runMode != RunMode.Normal ||
            (snapshot.status != ServiceStatus.Running && snapshot.status != ServiceStatus.Starting)
        ) {
            return
        }
        scope.launch {
            restoreForegroundNotificationOffMainThread(snapshot)
        }
    }

    private suspend fun restoreForegroundNotificationOffMainThread(snapshot: RuntimeState) {
        val probeResult = probe.probePort(snapshot)
        if (!probeResult.open) {
            // 只有确认 Node 进程真的不在了才完整启动；进程还在就只补通知，
            // 避免把后台冻结造成的探测失败当成运行时退出。
            if (probe.isNormalProcessRunning()) {
                AppDiagnosticLogger.w(
                    context,
                    "RuntimeRepository",
                    "后台恢复通知时端口探测失败（已重试 ${probeResult.attempts} 次，耗时 ${probeResult.elapsedMs} ms，" +
                        "最后一次 ${probeResult.error ?: "未知错误"}），Node 进程仍在，仅补发通知"
                )
                runCatching { NodeService.ensureForegroundNotification(context) }
                return
            }
            AppDiagnosticLogger.w(
                context,
                "RuntimeRepository",
                "后台恢复通知时确认运行时已退出（重试 ${probeResult.attempts} 次，耗时 ${probeResult.elapsedMs} ms），改为完整启动 Node"
            )
            runCatching { NodeService.requestRecoveryStart(context) }
            return
        }
        val manuallyHidden = NormalNotificationBehaviorPrefs.shouldSuppressNotification(context)
        if (manuallyHidden) {
            // 只有"前台恢复"策略才在离开应用时补回被手动划掉的通知，
            // "尊重关闭"需要保持隐藏，划掉即恢复早在划掉时就已补发。
            if (settingsRepository.normalNotificationBehavior.value ==
                NormalNotificationBehavior.ForegroundRestore
            ) {
                NormalNotificationBehaviorPrefs.clearManuallyHidden(context)
                NodeService.ensureForegroundNotification(context)
            }
            return
        }
        val notificationPresent =
            NodeService.canDisplayForegroundNotification(context) &&
                NodeService.isForegroundNotificationActive(context)
        if (notificationPresent) {
            return
        }
        AppDiagnosticLogger.i(context, "RuntimeRepository", "离开应用时通知不在位，补发前台服务通知")
        NodeService.ensureForegroundNotification(context)
    }

    suspend fun reconcileInitialState() {
        val before = state.value
        if (!status.rootRecoveryChecked && before.transition == null) {
            status.rootRecoveryChecked = true
            val recovered = when {
                before.runMode == RunMode.Root -> RootRuntimeController.recoverRuntime(context, before.port)
                mayRecoverRootModeAfterReinstall && !probe.isNormalProcessRunning() -> {
                    val passive = RootRuntimeController.recoverRuntimePassive(context, before.port)
                    val shouldTryRoot = RootRuntimeRecovery.shouldTryAfterDataLoss(
                        noExplicitMode = true, normalProcessRunning = false,
                        rootHealthMatches = passive != null, portOpen = probe.isPortOpen(before.port)
                    )
                    if (shouldTryRoot) RootRuntimeController.recoverRuntime(context, before.port) ?: passive else null
                }
                else -> null
            }
            if (recovered != null) {
                status.adoptRecoveredRootRuntime(recovered)
                return
            }
        }
        val snapshot = state.value
        if (
            snapshot.status == ServiceStatus.Running ||
            snapshot.status == ServiceStatus.Starting ||
            snapshot.status == ServiceStatus.Stopping
        ) {
            return
        }

        when (snapshot.runMode) {
            RunMode.Normal -> {
                if (probe.isNormalRuntimeReachable(snapshot.port)) {
                    val serviceRunning = probe.isNormalServiceRunning()
                    val notificationSuppressed =
                        NormalNotificationBehaviorPrefs.shouldSuppressNotification(context)
                    val foregroundRequested = if (notificationSuppressed && serviceRunning) {
                        false
                    } else {
                        NodeService.ensureForegroundNotification(
                            context = context,
                            force = !serviceRunning
                        )
                    }
                    status.markRunning(
                        forceNewStart = false,
                        statusMessage = when {
                            notificationSuppressed -> "接口已就绪，通知按设置保持隐藏"
                            foregroundRequested -> "接口可用，正在恢复前台服务通知"
                            else -> "接口已就绪，可直接在局域网访问"
                        }
                    )
                    if (snapshot.status == ServiceStatus.Error || snapshot.status == ServiceStatus.Stopped) {
                        log(LogLevel.Info, "检测到普通模式服务仍可访问，已自动纠正为运行中")
                    }
                }
            }

            RunMode.Root -> {
                if (probe.isRootRuntimeOwnedByApp(snapshot.port)) {
                    status.markRunning(
                        pid = RootRuntimeController.getPid(context),
                        forceNewStart = false
                    )
                    if (snapshot.status == ServiceStatus.Error || snapshot.status == ServiceStatus.Stopped) {
                        log(LogLevel.Info, "检测到 Root 模式服务仍在运行，已自动纠正为运行中")
                    }
                }
            }
        }
    }

    suspend fun reconcileNormalStateLocked() {
        val snapshot = state.value
        if (!shouldRunPeriodicNormalStateReconcile(snapshot.runMode, snapshot.status)) {
            if (snapshot.runMode != RunMode.Normal) {
                normalStart.clearStartMarkers()
                normalReconcileConsecutivePortMisses = 0
            }
            return
        }

        val serviceRunning = probe.isNormalServiceRunning()
        val processRunning = probe.isNormalProcessRunning()
        val portOpen = probe.isPortOpen(snapshot.port)
        when (snapshot.status) {
            ServiceStatus.Starting -> {
                normalReconcileConsecutivePortMisses = 0
                if (portOpen) {
                    status.markRunning(forceNewStart = normalStart.pendingExplicitStart)
                    return
                }
                val startAt = normalStart.startIssuedAtMs
                if (startAt <= 0L) {
                    when {
                        serviceRunning || processRunning || portOpen -> {
                            normalStart.startIssuedAtMs = System.currentTimeMillis()
                            status.updateStatusMessage(
                                expectedStatus = ServiceStatus.Starting,
                                message = snapshot.statusMessage ?: "正在初始化运行环境，请稍候"
                            )
                        }

                        else -> {
                            status.markStopped("服务未运行，可重新启动")
                            log(LogLevel.Warn, "检测到普通模式启动状态残留，已恢复为可重试状态")
                        }
                    }
                    return
                }
                val staleTimeoutMs = NormalModeRuntimeProfiles.current(context).startupStaleTimeoutMs
                if (System.currentTimeMillis() - startAt >= staleTimeoutMs) {
                    val message = when {
                        serviceRunning -> "普通模式启动卡住：服务进程仍在但端口未就绪，请重试启动"
                        processRunning -> "普通模式启动卡住：旧进程未完全退出，请重试启动"
                        else -> "普通模式启动状态失效，请重试启动"
                    }
                    status.markError(message)
                    log(
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
                normalReconcileConsecutivePortMisses = 0
                if (!processRunning && !portOpen) {
                    status.markStopped()
                }
            }

            ServiceStatus.Running -> {
                if (portOpen) {
                    normalReconcileConsecutivePortMisses = 0
                } else {
                    normalReconcileConsecutivePortMisses++
                    if (normalReconcileConsecutivePortMisses >= 2) {
                        normalReconcileConsecutivePortMisses = 0
                        status.markStopped("服务端口已关闭，可重新启动")
                        log(LogLevel.Warn, "检测到普通模式服务端口已关闭，运行状态已自动重置")
                    }
                }
            }

            ServiceStatus.Stopped,
            ServiceStatus.Error -> {
                normalReconcileConsecutivePortMisses = 0
            }
        }
    }

    fun reconcileRootStateLocked() {
        val snapshot = state.value
        if (snapshot.runMode != RunMode.Root) {
            rootReconcileConsecutiveDeadCount = 0
            return
        }
        if (snapshot.status != ServiceStatus.Running &&
            snapshot.status != ServiceStatus.Starting &&
            snapshot.status != ServiceStatus.Stopping
        ) {
            rootReconcileConsecutiveDeadCount = 0
            return
        }

        val ownedByCurrentApp = probe.isRootRuntimeOwnedByApp(snapshot.port)
        val passiveAliveHint = if (ownedByCurrentApp) {
            true
        } else {
            RootRuntimeController.isProbablyRunning(context, snapshot.port)
        }
        when (snapshot.status) {
            ServiceStatus.Running -> {
                if (ownedByCurrentApp || passiveAliveHint) {
                    rootReconcileConsecutiveDeadCount = 0
                } else {
                    rootReconcileConsecutiveDeadCount++
                    if (shouldMarkRootStoppedAfterPassiveMiss(rootReconcileConsecutiveDeadCount, passiveAliveHint)) {
                        status.markStopped("Root 服务未运行，可重新启动")
                        log(LogLevel.Warn, "检测到 Root 模式服务已退出，状态已自动重置")
                        rootReconcileConsecutiveDeadCount = 0
                    }
                }
            }

            ServiceStatus.Starting -> {
                rootReconcileConsecutiveDeadCount = 0
                if (ownedByCurrentApp) {
                    status.markRunning(
                        pid = RootRuntimeController.getPid(context),
                        forceNewStart = false
                    )
                }
            }

            ServiceStatus.Stopping -> {
                rootReconcileConsecutiveDeadCount = 0
                if (!ownedByCurrentApp && !passiveAliveHint) {
                    status.markStopped()
                }
            }

            ServiceStatus.Stopped,
            ServiceStatus.Error -> Unit
        }
    }
}
