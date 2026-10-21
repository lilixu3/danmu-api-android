package com.example.danmuapiapp.data.repository

import android.content.Context
import com.example.danmuapiapp.data.service.FavoriteCacheStore
import com.example.danmuapiapp.data.service.NodeProjectManager
import com.example.danmuapiapp.data.service.NodeService
import com.example.danmuapiapp.data.service.NormalModeRuntimeProfiles
import com.example.danmuapiapp.data.service.RootAutoStartModule
import com.example.danmuapiapp.data.service.RootAutoStartPrefs
import com.example.danmuapiapp.data.service.RootRuntimeController
import com.example.danmuapiapp.data.service.RootShell
import com.example.danmuapiapp.data.service.RuntimeDependencyHealthChecker
import com.example.danmuapiapp.data.service.RuntimeModePrefs
import com.example.danmuapiapp.data.service.RuntimePaths
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreDependencyRepairOrigin
import com.example.danmuapiapp.domain.model.ErrorHandler
import com.example.danmuapiapp.domain.model.LogLevel
import com.example.danmuapiapp.domain.model.NormalModeStabilityMode
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.RuntimeDependencyResumeAction
import com.example.danmuapiapp.domain.model.RuntimeListenMode
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.model.RuntimeTransitionKind
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.repository.CoreRepository
import com.example.danmuapiapp.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * 运行时生命周期控制：启动、停止、重启、服务配置应用、陈旧进程回收与运行模式切换。
 *
 * Repository 只提供状态流、运行数据事务、日志与状态机回调；这里决定"何时启停、如何等待"。
 */
internal class RuntimeLifecycleController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<RuntimeState>,
    private val stateStore: RuntimeStateStore,
    private val probe: RuntimeProcessProbe,
    private val status: RuntimeStatusController,
    private val hotReload: RuntimeHotReloadController,
    private val coreRepository: CoreRepository,
    private val settingsRepository: SettingsRepository,
    private val normalStart: NormalRuntimeStartState,
    private val log: (LogLevel, String) -> Unit
) {

    private companion object {
        const val NORMAL_STALE_PROCESS_CONFIRM_TIMEOUT_MS = 1500L
        const val NORMAL_STALE_PROCESS_KILL_TIMEOUT_MS = 4000L
        const val NORMAL_STALE_PROCESS_RETRY_DELAY_MS = 220L
        const val NORMAL_RESTART_STOP_TIMEOUT_MS = 12_000L
        const val NORMAL_STOP_TIMEOUT_MS = 8_000L
        const val NORMAL_RESTART_START_TIMEOUT_MS = 15_000L
        const val STOP_SLOW_NOTICE_DELAY_MS = 3_000L
        const val MODE_SWITCH_STOP_TIMEOUT_MS = 5_000L
    }

    private fun normalRuntimeProfile() = NormalModeRuntimeProfiles.current(context)

    suspend fun startServiceLocked(
        startingStatusMessage: String? = null,
        dependenciesAlreadyChecked: Boolean = false
    ) {
        val snapshot = state.value
        if (snapshot.status == ServiceStatus.Starting) return
        if (snapshot.status == ServiceStatus.Running) {
            if (probe.isRuntimePortActive(snapshot)) return
            // 状态仍记为运行中，但端口已经关闭：这是后台杀进程/异常退出后的
            // 陈旧状态，继续往下走会直接返回，导致“重启服务”实际没有拉起 Node。
            log(LogLevel.Warn, "检测到服务状态与实际端口不一致，准备重新启动")
            status.markStopped()
        }
        if (!dependenciesAlreadyChecked && !ensureRuntimeDependenciesBeforeStart(
                snapshot = snapshot,
                actionLabel = "启动",
                resumeAction = RuntimeDependencyResumeAction.Start
            )
        ) {
            return
        }

        if (snapshot.runMode == RunMode.Normal) {
            val recovered = recoverStaleNormalProcessIfNeeded(snapshot.port)
            if (!recovered) return
        }

        state.update {
            it.copy(
                status = ServiceStatus.Starting,
                errorMessage = null,
                statusMessage = startingStatusMessage ?: "正在准备启动服务…"
            )
        }
        normalStart.pendingExplicitStart = snapshot.runMode == RunMode.Normal
        if (snapshot.runMode == RunMode.Root) {
            if (shouldClearRootStartedAtBeforeStart(
                    rootProbablyRunning = probe.isRootRuntimeOwnedByApp(snapshot.port)
                )
            ) {
                stateStore.clearRuntimeStartedAt()
            }
        } else {
            stateStore.clearRuntimeStartedAt()
        }
        log(LogLevel.Info, "正在启动服务...")
        stateStore.persistSelectedVariant(snapshot.variant, commit = true)

        when (snapshot.runMode) {
            RunMode.Normal -> startNormalService(snapshot)
            RunMode.Root -> startRootService(snapshot)
        }
    }

    private fun startNormalService(snapshot: RuntimeState) {
        if (snapshot.port in 1..1023) {
            val reason = "普通模式无法监听 1-1023 端口，请切换 Root 模式或改用 1024+ 端口"
            status.markError(reason)
            log(LogLevel.Error, reason)
            return
        }
        val portOpenBeforeStart = probe.isPortOpen(snapshot.port)
        val ownershipBeforeStart = if (portOpenBeforeStart) {
            probe.readRuntimeOwnership(snapshot.port, RunMode.Normal)
        } else {
            RuntimeOwnership.Unknown
        }
        when (
            decideNormalStartPreflight(
                portOpen = portOpenBeforeStart,
                ownership = ownershipBeforeStart
            )
        ) {
            NormalStartPreflightDecision.Proceed -> Unit
            NormalStartPreflightDecision.ForeignInstanceOccupiesPort -> {
                val reason = buildNormalForeignPortOccupiedMessage(snapshot.port)
                status.markError(reason)
                log(LogLevel.Error, reason)
                return
            }
        }
        val profile = normalRuntimeProfile()
        val projectDir = RuntimePaths.normalProjectDir(context)
        val selectedCoreDir = File(projectDir, "danmu_api_${snapshot.variant.key}")
        val selectedCoreReady = runCatching {
            NodeProjectManager.hasValidCore(selectedCoreDir)
        }.getOrDefault(false)
        if (!selectedCoreReady) {
            val fallback = ApiVariant.entries.firstOrNull { variant ->
                NodeProjectManager.hasValidCore(File(projectDir, "danmu_api_${variant.key}"))
            }
            val reason = if (fallback != null && fallback != snapshot.variant) {
                "当前核心 ${settingsRepository.coreDisplayNames.value.resolve(snapshot.variant)} 未安装，可先切换到 ${settingsRepository.coreDisplayNames.value.resolve(fallback)} 或下载后再启动"
            } else {
                "当前核心 ${settingsRepository.coreDisplayNames.value.resolve(snapshot.variant)} 未安装，请先下载核心后再启动"
            }
            status.markError(reason)
            log(LogLevel.Error, reason)
            return
        }
        if (profile.conservativeMode) {
            val reasonText = when (profile.strategyMode) {
                NormalModeStabilityMode.Auto -> {
                    val reasons = listOfNotNull(
                        "低内存设备".takeIf { profile.lowRamDevice },
                        "共享存储目录".takeIf { profile.slowStorageWorkDir }
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
            log(
                LogLevel.Info,
                "已启用普通模式稳定优先策略$reasonText：关闭 worker/热更新并延长启动等待时间"
            )
        }
        val envSynced = runCatching {
            // 仅在现有运行目录可用时做快速配置同步，避免主进程重复走重型解压。
            NodeProjectManager.syncRuntimeEnvIfProjectReady(
                context = context,
                targetProjectDir = projectDir,
                preferredVariantKey = snapshot.variant.key
            )
        }
        if (envSynced.isFailure) {
            val reason = envSynced.exceptionOrNull()?.message ?: "未知错误"
            status.markError("运行时配置写入失败：$reason")
            log(LogLevel.Error, "普通模式启动前写入配置失败: $reason")
            return
        }
        val startResult = runCatching {
            NodeService.start(context, userInitiated = true)
        }
        if (startResult.isFailure) {
            val reason = ErrorHandler.buildDetailedMessage(
                startResult.exceptionOrNull() ?: IllegalStateException("未知错误")
            )
            status.markError("普通模式拉起前台服务失败：$reason")
            log(LogLevel.Error, "普通模式拉起前台服务失败: $reason")
            return
        }
        status.armCandidateObservation(snapshot.variant, snapshot.runMode)
        normalStart.startIssuedAtMs = System.currentTimeMillis()

        scope.launch {
            delay(profile.startupPrimaryNoticeMs)
            val current = state.value
            if (current.runMode == RunMode.Normal && current.status == ServiceStatus.Starting) {
                if (probe.isPortOpen(current.port)) {
                    status.markRunning(forceNewStart = normalStart.pendingExplicitStart)
                } else {
                    log(LogLevel.Warn, "普通模式首次启动较慢，继续等待...")
                    status.updateStatusMessage(
                        expectedStatus = ServiceStatus.Starting,
                        message = "设备较慢，正在继续等待服务就绪…可点击取消启动"
                    )
                    delay(profile.startupSecondaryNoticeMs)
                    val retrySnapshot = state.value
                    if (retrySnapshot.runMode == RunMode.Normal &&
                        retrySnapshot.status == ServiceStatus.Starting
                    ) {
                        if (probe.isPortOpen(retrySnapshot.port)) {
                            status.markRunning(forceNewStart = normalStart.pendingExplicitStart)
                        } else {
                            log(LogLevel.Warn, "普通模式启动耗时较长，继续等待服务自行就绪...")
                            status.updateStatusMessage(
                                expectedStatus = ServiceStatus.Starting,
                                message = "启动耗时较长，仍在等待服务就绪…"
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun startRootService(snapshot: RuntimeState) {
        val recovered = RootRuntimeController.recoverRuntime(context, snapshot.port)
        if (recovered != null) {
            status.adoptRecoveredRootRuntime(recovered)
            return
        }
        status.armCandidateObservation(snapshot.variant, snapshot.runMode)
        val result = RootRuntimeController.start(context, snapshot.port, quickMode = false)
        if (result.ok) {
            status.markRunning(
                pid = RootRuntimeController.getPid(context),
                forceNewStart = shouldForceNewRootStartedAt(result.startOutcome)
            )
            log(LogLevel.Info, result.message)
        } else {
            val detail = result.detail.ifBlank { result.message }
            status.markError(detail)
            log(LogLevel.Error, "Root 启动失败: $detail")
            val suspectedMissingPackage = RuntimeDependencyHealthChecker.recordModuleNotFoundIssue(
                context = context,
                projectDir = RuntimePaths.normalProjectDir(context),
                message = detail,
                preferredVariant = snapshot.variant
            )
            if (detail.startsWith("运行时依赖缺失：") || suspectedMissingPackage != null) {
                try {
                    coreRepository.prepareInstalledCoreDependencyRepair(
                        variant = snapshot.variant,
                        origin = CoreDependencyRepairOrigin.RuntimeStart,
                        suspectedMissingPackage = suspectedMissingPackage
                    )
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    log(LogLevel.Error, "创建 Root 依赖修复任务失败：${error.message ?: "未知错误"}")
                }
            }
        }
    }

    suspend fun stopServiceLocked() {
        val snapshot = state.value
        if (snapshot.status == ServiceStatus.Stopped || snapshot.status == ServiceStatus.Stopping) return

        val wasStarting = snapshot.status == ServiceStatus.Starting
        state.update {
            it.copy(
                status = ServiceStatus.Stopping,
                errorMessage = null,
                statusMessage = "正在安全停止服务…"
            )
        }
        log(LogLevel.Info, "正在停止服务...")
        status.cancelUptimeCounter()

        when (snapshot.runMode) {
            RunMode.Normal -> {
                val restarting = normalStart.pendingRestart
                val stopResult = runCatching {
                    NodeService.stop(context)
                }
                if (stopResult.isFailure) {
                    val reason = ErrorHandler.buildDetailedMessage(
                        stopResult.exceptionOrNull() ?: IllegalStateException("未知错误")
                    )
                    status.markError("普通模式下发停止指令失败：$reason")
                    log(LogLevel.Error, "普通模式下发停止指令失败: $reason")
                    return
                }
                val stopPort = snapshot.port
                scope.launch {
                    delay(STOP_SLOW_NOTICE_DELAY_MS)
                    status.updateStatusMessage(
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
                        probe.waitForRestartStopCompletion(
                            mode = RunMode.Normal,
                            oldPort = stopPort,
                            timeoutMs = NORMAL_STOP_TIMEOUT_MS
                        )
                    } else {
                        probe.waitForRuntimeStopped(
                            mode = RunMode.Normal,
                            oldPort = stopPort,
                            timeoutMs = NORMAL_STOP_TIMEOUT_MS
                        )
                    }
                    val current = state.value
                    if (current.runMode == RunMode.Normal && current.status == ServiceStatus.Stopping) {
                        if (stopped && restarting) {
                            status.updateStatusMessage(
                                expectedStatus = ServiceStatus.Stopping,
                                message = "旧实例已停止，正在重新启动服务…"
                            )
                            log(LogLevel.Info, "普通模式停止完成，等待重启流程继续")
                        } else if (stopped) {
                            status.markStopped()
                        } else if (!restarting) {
                            log(LogLevel.Warn, "普通模式停止较慢，已恢复为可重试状态")
                            if (wasStarting) {
                                status.markStarting("启动取消较慢，可再次点击取消启动")
                            } else {
                                status.markRunning(
                                    forceNewStart = false,
                                    statusMessage = "停止较慢，可再次点击停止"
                                )
                            }
                        }
                    }
                }
            }

            RunMode.Root -> {
                val result = RootRuntimeController.stop(context, snapshot.port)
                if (result.ok) {
                    status.markStopped()
                    log(LogLevel.Info, result.message)
                } else {
                    val detail = result.detail.ifBlank { result.message }
                    status.markError(detail)
                    log(LogLevel.Error, "Root 停止失败: $detail")
                }
            }
        }
    }

    suspend fun restartServiceLocked() {
        val snapshot = state.value
        if (snapshot.status == ServiceStatus.Starting) return
        if (snapshot.status == ServiceStatus.Stopping) {
            if (!probe.isRuntimePortActive(snapshot)) {
                status.markStopped()
                startServiceLocked()
            }
            return
        }

        if (snapshot.status == ServiceStatus.Stopped || snapshot.status == ServiceStatus.Error) {
            startServiceLocked()
            return
        }
        if (!probe.isRuntimePortActive(snapshot)) {
            // 运行状态陈旧但服务已经不在，直接重新启动即可。
            log(LogLevel.Warn, "重启时检测到旧实例已不在，直接启动新实例")
            status.markStopped()
            startServiceLocked()
            return
        }
        if (!ensureRuntimeDependenciesBeforeStart(
                snapshot = snapshot,
                actionLabel = "重启",
                resumeAction = RuntimeDependencyResumeAction.Restart
            )
        ) {
            return
        }

        when (snapshot.runMode) {
            RunMode.Normal -> {
                normalStart.pendingRestart = true
                try {
                    val oldPort = snapshot.port
                    stopServiceLocked()
                    val stopped = probe.waitForRestartStopCompletion(
                        mode = RunMode.Normal,
                        oldPort = oldPort,
                        timeoutMs = NORMAL_RESTART_STOP_TIMEOUT_MS
                    )
                    if (!stopped) {
                        log(LogLevel.Warn, "普通模式重启前停止较慢，请稍后重试")
                        status.markRunning(
                            forceNewStart = false,
                            statusMessage = "重启前停止较慢，请稍后重试"
                        )
                        return
                    }

                    startServiceLocked(
                        startingStatusMessage = "正在重启服务…",
                        dependenciesAlreadyChecked = true
                    )
                    val started = probe.waitForPort(
                        oldPort,
                        wantOpen = true,
                        timeoutMs = NORMAL_RESTART_START_TIMEOUT_MS
                    )
                    if (started) {
                        if (state.value.status != ServiceStatus.Running) {
                            status.markRunning(forceNewStart = true)
                        }
                    } else {
                        if (state.value.status == ServiceStatus.Starting) {
                            log(LogLevel.Warn, "普通模式重启耗时较长，继续等待服务自行就绪...")
                            status.updateStatusMessage(
                                expectedStatus = ServiceStatus.Starting,
                                message = "重启耗时较长，仍在等待服务就绪…"
                            )
                        }
                    }
                } finally {
                    normalStart.pendingRestart = false
                }
            }

            RunMode.Root -> {
                state.update {
                    it.copy(
                        status = ServiceStatus.Stopping,
                        errorMessage = null,
                        statusMessage = "正在重启服务…"
                    )
                }
                status.armCandidateObservation(snapshot.variant, snapshot.runMode)
                val result = RootRuntimeController.restart(context, snapshot.port)
                if (result.ok) {
                    status.markRunning(pid = RootRuntimeController.getPid(context), forceNewStart = true)
                    log(LogLevel.Info, "服务已重启")
                } else {
                    val detail = result.detail.ifBlank { result.message }
                    status.markError(detail)
                    log(LogLevel.Error, "重启失败: $detail")
                }
            }
        }
    }

    suspend fun applyServiceConfigLocked(
        port: Int,
        token: String,
        listenMode: RuntimeListenMode,
        restartIfRunning: Boolean
    ) {
        if (port !in 1..65535) return

        val snapshot = state.value
        val portChanged = snapshot.port != port
        val tokenChanged = snapshot.token != token
        val listenModeChanged = snapshot.listenMode != listenMode
        if (!portChanged && !tokenChanged && !listenModeChanged) return

        val wasActive = snapshot.status == ServiceStatus.Running ||
            snapshot.status == ServiceStatus.Starting
        if (!restartIfRunning || !wasActive) {
            stateStore.persistServiceConfigChanges(
                port = port,
                token = token,
                applyPort = portChanged,
                applyToken = tokenChanged,
                listenMode = listenMode,
                applyListenMode = listenModeChanged
            )
            syncRuntimeEnvFromPrefs(snapshot.runMode)
            return
        }

        val oldPort = snapshot.port
        val oldMode = snapshot.runMode
        val suppressStopTimeout = oldMode == RunMode.Normal
        if (suppressStopTimeout) {
            normalStart.pendingRestart = true
        }
        val transitionId = status.beginTransition(
            kind = RuntimeTransitionKind.Restarting,
            message = "正在应用服务配置并重启…"
        )

        try {
            val changedText = buildString {
                if (portChanged) append("端口 ${snapshot.port} -> $port")
                if (tokenChanged) {
                    if (isNotEmpty()) append("，")
                    append("Token 已更新")
                }
                if (listenModeChanged) {
                    if (isNotEmpty()) append("，")
                    append("监听 ${snapshot.listenMode.label} -> ${listenMode.label}")
                }
            }.ifBlank { "配置已更新" }
            log(LogLevel.Info, "正在应用服务配置：$changedText")

            status.updateTransition(transitionId, "正在安全停止旧服务并应用配置…")
            stopServiceLocked()
            val stopped = if (oldMode == RunMode.Normal) {
                probe.waitForRestartStopCompletion(
                    mode = oldMode,
                    oldPort = oldPort,
                    timeoutMs = NORMAL_RESTART_STOP_TIMEOUT_MS
                )
            } else {
                probe.waitForRuntimeStopped(oldMode, oldPort, timeoutMs = NORMAL_RESTART_STOP_TIMEOUT_MS)
            }
            if (!stopped) {
                val detail = "应用服务配置失败：旧实例停止超时"
                status.markError(detail)
                log(LogLevel.Error, detail)
                return
            }

            stateStore.persistServiceConfigChanges(
                port = port,
                token = token,
                applyPort = portChanged,
                applyToken = tokenChanged,
                listenMode = listenMode,
                applyListenMode = listenModeChanged
            )
            syncRuntimeEnvFromPrefs(oldMode)
            log(LogLevel.Info, "配置已写入，正在启动服务...")
            status.updateTransition(transitionId, "配置已写入，正在重新启动服务…")
            startServiceLocked(startingStatusMessage = "正在重启服务以应用新配置…")
        } finally {
            status.endTransition(transitionId)
            if (suppressStopTimeout) {
                normalStart.pendingRestart = false
            }
        }
    }

    suspend fun switchRunMode(mode: RunMode) {
        status.cancelCandidateObservation()
        val current = state.value
        if (current.runMode == mode) return

        if (mode.requiresRoot) {
            val rootCheck = RootShell.exec("id", timeoutMs = 3500L)
            if (!rootCheck.ok) {
                log(LogLevel.Warn, "运行模式切换失败：${rootSwitchDeniedReason(rootCheck)}")
                return
            }
        }

        if (mode == RunMode.Root && current.status != ServiceStatus.Running && current.status != ServiceStatus.Starting) {
            val recovered = RootRuntimeController.recoverRuntime(context, current.port)
            if (recovered != null) {
                status.adoptRecoveredRootRuntime(recovered)
                return
            }
        }

        val transitionId = status.beginTransition(
            kind = RuntimeTransitionKind.SwitchingRunMode,
            message = "正在从${current.runMode.label}模式切换到${mode.label}模式…"
        )
        try {
            val shouldResume = current.status == ServiceStatus.Running ||
                current.status == ServiceStatus.Starting

            if (shouldResume) {
                status.updateTransition(transitionId, "正在安全停止${current.runMode.label}模式服务…")
                stopServiceLocked()
                probe.waitForPort(current.port, wantOpen = false, timeoutMs = MODE_SWITCH_STOP_TIMEOUT_MS)
            }

            status.updateTransition(transitionId, "正在同步运行数据并切换到${mode.label}模式…")
            val favoriteSync = FavoriteCacheStore.synchronizeModes(
                context = context,
                preferredMode = current.runMode,
                otherMode = mode
            )
            if (favoriteSync.isFailure) {
                val detail = favoriteSync.exceptionOrNull()?.message ?: "未知错误"
                log(LogLevel.Error, "运行模式切换失败：收藏数据同步失败：$detail")
                if (shouldResume) {
                    status.updateTransition(transitionId, "模式切换失败，正在恢复原服务…")
                    startServiceLocked(startingStatusMessage = "正在恢复原服务…")
                }
                return
            }
            log(LogLevel.Info, "已同步 ${favoriteSync.getOrThrow().count} 项收藏到 ${mode.label} 模式")

            stateStore.clearRuntimeStartedAt()
            RuntimeModePrefs.put(context, mode)
            val rootAutoStartEnabled = RootAutoStartPrefs.isBootAutoStartEnabled(context)
            if (shouldSyncRootAutoStartModeFlag(mode, rootAutoStartEnabled)) {
                val flagResult = RootAutoStartModule.writeRunModeFlag(mode)
                if (!flagResult.ok) {
                    log(LogLevel.Warn, "开机触发模式同步失败：${flagResult.message}")
                }
            } else if (rootAutoStartEnabled) {
                log(LogLevel.Info, "已切换到普通模式：跳过 Root 开机触发模式同步，避免普通模式触发 Root 授权")
            }
            if (shouldCloseRootShellSessionForMode(mode)) {
                RootShell.closeSession()
            }
            state.update {
                it.copy(
                    runMode = mode,
                    status = ServiceStatus.Stopped,
                    uptimeSeconds = 0,
                    pid = null,
                    errorMessage = null,
                    statusMessage = null
                )
            }
            hotReload.stop()

            log(LogLevel.Info, "运行模式已切换为 ${mode.label}")

            if (mode == RunMode.Root) {
                val recovered = RootRuntimeController.recoverRuntime(context, current.port)
                status.rootRecoveryChecked = true
                if (recovered != null) {
                    status.adoptRecoveredRootRuntime(recovered)
                    return
                }
            }

            if (shouldResume) {
                status.updateTransition(transitionId, "已切换到${mode.label}模式，正在启动服务…")
                startServiceLocked(
                    startingStatusMessage = "已切换到${mode.label}模式，正在启动服务…"
                )
            }
        } finally {
            status.endTransition(transitionId)
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

    private suspend fun ensureRuntimeDependenciesBeforeStart(
        snapshot: RuntimeState,
        actionLabel: String,
        resumeAction: RuntimeDependencyResumeAction
    ): Boolean {
        val dependencyRepair = try {
            coreRepository.prepareInstalledCoreDependencyRepair(
                variant = snapshot.variant,
                origin = CoreDependencyRepairOrigin.RuntimeStart,
                resumeAction = resumeAction
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val reason = error.message ?: "运行时依赖检测失败"
            if (snapshot.status == ServiceStatus.Running) {
                state.update {
                    it.copy(
                        status = ServiceStatus.Running,
                        errorMessage = null,
                        statusMessage = "$actionLabel 前依赖检查失败：$reason"
                    )
                }
            } else {
                status.markError(reason)
            }
            log(LogLevel.Error, "$actionLabel 前依赖检测失败：$reason")
            return false
        }
        if (dependencyRepair == null) return true

        val reason = "当前核心缺少运行时依赖，$actionLabel 已暂停，请先修复依赖"
        state.update {
            it.copy(
                status = if (snapshot.status == ServiceStatus.Running) {
                    ServiceStatus.Running
                } else {
                    ServiceStatus.Stopped
                },
                errorMessage = null,
                statusMessage = reason
            )
        }
        log(LogLevel.Warn, "$reason：${dependencyRepair.missingDependencies.joinToString(", ")}")
        return false
    }

    private suspend fun recoverStaleNormalProcessIfNeeded(port: Int): Boolean {
        val stalePid = confirmStaleNormalProcess(port, NORMAL_STALE_PROCESS_CONFIRM_TIMEOUT_MS)
            ?: return true
        if (NodeService.recoveryCandidatePid(context) != stalePid) return true

        log(LogLevel.Info, "普通模式启动前回收未监听端口的旧服务进程，PID=$stalePid")
        val killed = NodeService.killProcessIfRunning(context, expectedPid = stalePid)
        if (!killed && NodeService.recoveryCandidatePid(context) != stalePid) return true
        if (!killed && NodeService.isProcessRunning(context)) {
            status.markError("普通模式残留进程回收失败，请重试启动")
            log(LogLevel.Error, "普通模式残留进程回收失败：无法结束 :node 进程")
            return false
        }

        val stopped = probe.waitForNormalProcessReclaimed(
            expectedPid = stalePid,
            timeoutMs = NORMAL_STALE_PROCESS_KILL_TIMEOUT_MS
        )
        if (!stopped) {
            status.markError("普通模式残留进程回收超时，请重试启动")
            log(LogLevel.Error, "普通模式残留进程回收超时：:node 进程未及时退出")
            return false
        }

        status.markStopped()
        delay(NORMAL_STALE_PROCESS_RETRY_DELAY_MS)
        return true
    }

    private suspend fun confirmStaleNormalProcess(port: Int, timeoutMs: Long): Int? {
        val expectedPid = NodeService.recoveryCandidatePid(context) ?: return null
        val deadline = System.currentTimeMillis() + timeoutMs.coerceAtLeast(0L)
        while (System.currentTimeMillis() < deadline) {
            if (NodeService.recoveryCandidatePid(context) != expectedPid) return null
            if (port in 1..65535 && probe.isPortOpen(port)) return null
            delay(180)
        }
        return expectedPid.takeIf {
            NodeService.recoveryCandidatePid(context) == expectedPid &&
                (port !in 1..65535 || !probe.isPortOpen(port))
        }
    }

    private fun syncRuntimeEnvFromPrefs(mode: RunMode) {
        if (mode == RunMode.Normal) {
            val normalDir = runCatching {
                NodeProjectManager.ensureProjectExtracted(context, RuntimePaths.normalProjectDir(context))
            }.getOrElse {
                log(LogLevel.Warn, "同步运行时配置失败：${it.message ?: "无法初始化工作目录"}")
                return
            }

            runCatching {
                NodeProjectManager.writeRuntimeEnv(
                    context = context,
                    targetProjectDir = normalDir,
                    preferredVariantKey = state.value.variant.key
                )
            }.onFailure {
                log(LogLevel.Warn, "写入运行时配置失败：${it.message ?: "未知错误"}")
                return
            }
            return
        }

        // Root 模式仅同步 Root 目录，避免改配置时回写普通模式目录。
        val rootSyncResult = RootRuntimeController.syncRuntimeEnvOnly(context)
        if (!rootSyncResult.ok) {
            val detail = rootSyncResult.detail.ifBlank { rootSyncResult.message }
            log(LogLevel.Warn, "同步 Root 运行时配置失败：$detail")
        }
    }

}
