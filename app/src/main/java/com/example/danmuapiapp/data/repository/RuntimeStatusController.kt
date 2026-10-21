package com.example.danmuapiapp.data.repository

import android.content.Context
import com.example.danmuapiapp.data.service.RootRuntimeController
import com.example.danmuapiapp.data.service.RootRuntimeNotificationManager
import com.example.danmuapiapp.data.service.RuntimeModePrefs
import com.example.danmuapiapp.data.service.VideoShellInjectionConfigPublisher
import com.example.danmuapiapp.data.util.RuntimeTokenNormalizer
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.LogLevel
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.RuntimeListenMode
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.model.RuntimeTransition
import com.example.danmuapiapp.domain.model.RuntimeTransitionKind
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.repository.CoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 运行状态机：状态迁移发布、Root 通知同步、运行时长计数、候选核心观察与接管。
 *
 * Repository 只提供日志、热重载停表回调，以及"候选核心启动失败后如何恢复"的编排回调；
 * 这里不直接决定启停流程。
 */
internal class RuntimeStatusController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<RuntimeState>,
    private val stateStore: RuntimeStateStore,
    private val probe: RuntimeProcessProbe,
    private val normalStart: NormalRuntimeStartState,
    private val coreRepository: CoreRepository,
    private val log: (LogLevel, String) -> Unit,
    private val stopHotReload: () -> Unit,
    private val onRuntimeRunning: () -> Unit,
    private val recoverCandidateCore: suspend (CandidateStartAttempt, String) -> Unit
) {

    private companion object {
        const val UPTIME_REFRESH_INTERVAL_MS = 10_000L
        const val CANDIDATE_HEALTHY_OBSERVATION_MS = 25_000L
        const val MESSAGE_READY = "接口已就绪，可直接在局域网访问"
        const val MESSAGE_STOPPED = "服务已停止"
        const val MESSAGE_STOPPING = "正在安全停止服务…"
        const val MESSAGE_STARTING = "正在初始化运行环境，请稍候"
    }

    /** 候选核心启动观察记录。 */
    internal data class CandidateStartAttempt(
        val variant: ApiVariant,
        val runMode: RunMode,
        val candidateInstalledAtMs: Long,
        val startedAtMs: Long = System.currentTimeMillis()
    )

    private var uptimeJob: Job? = null

    @Volatile
    private var candidateStartAttempt: CandidateStartAttempt? = null
    private var candidateValidationJob: Job? = null
    private val candidateRecoveryInFlight = AtomicBoolean(false)
    private val runtimeTransitionSequence = AtomicLong(0L)

    /** Root 恢复只尝试一次；接管成功后由这里统一置位。 */
    @Volatile
    var rootRecoveryChecked: Boolean = false

    fun beginTransition(kind: RuntimeTransitionKind, message: String): Long {
        val id = runtimeTransitionSequence.incrementAndGet()
        state.update { current ->
            current.copy(
                transition = RuntimeTransition(
                    id = id,
                    kind = kind,
                    message = message
                )
            )
        }
        return id
    }

    fun updateTransition(id: Long, message: String) {
        state.update { current ->
            val active = current.transition
            if (active?.id == id) {
                current.copy(transition = active.copy(message = message))
            } else {
                current
            }
        }
    }

    fun endTransition(id: Long) {
        state.update { current ->
            if (current.transition?.id == id) current.copy(transition = null) else current
        }
    }

    fun markStarting(message: String? = null) {
        state.update {
            it.copy(
                status = ServiceStatus.Starting,
                errorMessage = null,
                statusMessage = message ?: it.statusMessage ?: MESSAGE_STARTING
            )
        }
    }

    fun markStopping(message: String? = null) {
        state.update {
            it.copy(
                status = ServiceStatus.Stopping,
                errorMessage = null,
                statusMessage = message ?: it.statusMessage ?: MESSAGE_STOPPING
            )
        }
        uptimeJob?.cancel()
    }

    fun updateStatusMessage(expectedStatus: ServiceStatus, message: String) {
        val changed = state.value.status == expectedStatus &&
            state.value.statusMessage != message
        state.update { current ->
            if (current.status != expectedStatus) {
                current
            } else {
                current.copy(statusMessage = message)
            }
        }
        if (changed) syncRootNotification(expectedStatus, message)
    }

    /** Root 模式没有前台服务，通知完全跟随状态机，普通模式由 :node 进程的 NodeService 自行维护。 */
    private fun syncRootNotification(status: ServiceStatus, message: String) {
        if (state.value.runMode != RunMode.Root) return
        when (status) {
            ServiceStatus.Running -> RootRuntimeNotificationManager.show(
                context = context,
                text = message,
                endpoint = state.value.lanUrl
            )
            else -> RootRuntimeNotificationManager.cancel(context)
        }
    }

    fun markRunning(
        pid: Int? = null,
        forceNewStart: Boolean = false,
        statusMessage: String? = null
    ) {
        val mode = state.value.runMode
        if (mode == RunMode.Normal) {
            normalStart.clearStartMarkers()
        }
        val startedAt = stateStore.ensureRuntimeStartedAt(
            mode = mode,
            forceNew = forceNewStart,
            port = state.value.port
        )
        val uptime = stateStore.uptimeSecondsFrom(startedAt)
        val networkAddresses = stateStore.resolveAddresses()

        state.update {
            it.copy(
                status = ServiceStatus.Running,
                pid = pid ?: it.pid,
                uptimeSeconds = uptime,
                localUrl = stateStore.buildLocalUrl(it.port, it.token),
                lanUrl = stateStore.buildLanUrl(networkAddresses.ipv4, it.port, it.token),
                lanIpv6Url = stateStore.buildLanIpv6Url(
                    networkAddresses.ipv6,
                    it.port,
                    it.token,
                    it.listenMode
                ),
                errorMessage = null,
                statusMessage = statusMessage ?: MESSAGE_READY
            )
        }
        VideoShellInjectionConfigPublisher.publishAsync(context)
        startUptimeCounter(startedAt)
        // 运行态改为事件驱动刷新时，这里启动热更新监听。
        onRuntimeRunning()
        scheduleCandidateConfirmation()
        if (mode == RunMode.Root) {
            RootRuntimeNotificationManager.show(
                context = context,
                text = state.value.statusMessage.orEmpty(),
                endpoint = state.value.lanUrl
            )
        }
    }

    fun markStopped(statusMessage: String? = null) {
        val failedAttempt = candidateStartAttempt
        if (state.value.runMode == RunMode.Normal) {
            normalStart.clearStartMarkers()
        }
        stateStore.clearRuntimeStartedAt()
        state.update {
            it.copy(
                status = ServiceStatus.Stopped,
                pid = null,
                uptimeSeconds = 0,
                errorMessage = null,
                statusMessage = statusMessage ?: MESSAGE_STOPPED
            )
        }
        uptimeJob?.cancel()
        stopHotReload()
        if (state.value.runMode == RunMode.Root) {
            RootRuntimeNotificationManager.cancel(context)
        }
        if (failedAttempt != null) {
            onCandidateStartFailure("候选核心启动后提前退出")
        }
    }

    fun markError(message: String, statusMessage: String? = message) {
        val failedAttempt = candidateStartAttempt
        val snapshot = state.value
        val portOpen = probe.isPortOpen(snapshot.port)
        val rootProbablyRunning = snapshot.runMode == RunMode.Root &&
            probe.isRootRuntimeOwnedByApp(snapshot.port)
        val stillRunning = if (snapshot.runMode == RunMode.Root) {
            rootProbablyRunning
        } else {
            portOpen
        }
        if (shouldClearStartedAtOnError(snapshot.runMode, portOpen, rootProbablyRunning)) {
            stateStore.clearRuntimeStartedAt()
        }
        if (snapshot.runMode == RunMode.Normal && !stillRunning) {
            normalStart.clearStartMarkers()
        }

        state.update {
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
            stopHotReload()
        }
        if (snapshot.runMode == RunMode.Root) {
            RootRuntimeNotificationManager.cancel(context)
        }
        if (failedAttempt != null) {
            onCandidateStartFailure(message)
        }
    }

    fun armCandidateObservation(variant: ApiVariant, runMode: RunMode) {
        val candidate = coreRepository.candidateState.value ?: return
        if (candidate.variant != variant || candidate.runMode != runMode) return
        candidateValidationJob?.cancel()
        candidateStartAttempt = CandidateStartAttempt(
            variant = variant,
            runMode = runMode,
            candidateInstalledAtMs = candidate.installedAtMs
        )
        log(LogLevel.Info, "${candidate.actionLabel}后的核心已进入启动观察期")
    }

    fun cancelCandidateObservation() {
        candidateValidationJob?.cancel()
        candidateValidationJob = null
        candidateStartAttempt = null
    }

    /** 停止流程开始后立刻停表，避免停止过程中运行时长继续跳动。 */
    fun cancelUptimeCounter() {
        uptimeJob?.cancel()
    }

    /** 构造时若已处于运行态：补运行时长锚点并启动候选核心确认倒计时。 */
    fun restoreRunningStateOnStartup() {
        val startedAt = stateStore.ensureRuntimeStartedAt(
            mode = state.value.runMode,
            forceNew = false,
            port = state.value.port
        )
        startUptimeCounter(startedAt)
        scheduleCandidateConfirmation()
    }

    private fun scheduleCandidateConfirmation() {
        val attempt = candidateStartAttempt ?: return
        candidateValidationJob?.cancel()
        candidateValidationJob = scope.launch {
            delay(CANDIDATE_HEALTHY_OBSERVATION_MS)
            val snapshot = state.value
            val candidate = coreRepository.candidateState.value
            val stillMatches = snapshot.status == ServiceStatus.Running &&
                snapshot.variant == attempt.variant && snapshot.runMode == attempt.runMode &&
                CoreCandidateRecoveryPolicy.matchesAttempt(
                    candidate = candidate,
                    variant = attempt.variant,
                    runMode = attempt.runMode,
                    installedAtMs = attempt.candidateInstalledAtMs
                )
            val reachable = when (attempt.runMode) {
                RunMode.Normal -> probe.isNormalRuntimeReachable(snapshot.port)
                RunMode.Root -> probe.isRootRuntimeOwnedByApp(snapshot.port)
            }
            if (!stillMatches || !reachable || candidateStartAttempt != attempt) return@launch
            coreRepository.confirmCandidateCore(
                attempt.variant,
                attempt.runMode,
                attempt.candidateInstalledAtMs
            ).fold(
                onSuccess = { confirmed ->
                    if (confirmed) {
                        log(LogLevel.Info, "候选核心已稳定运行，已确认为可用版本")
                    }
                },
                onFailure = { error ->
                    log(LogLevel.Warn, "确认候选核心失败：${error.message ?: "未知错误"}")
                }
            )
            if (candidateStartAttempt == attempt) {
                candidateStartAttempt = null
                candidateValidationJob = null
            }
        }
    }

    private fun onCandidateStartFailure(reason: String) {
        val attempt = candidateStartAttempt ?: return
        candidateValidationJob?.cancel()
        candidateValidationJob = null
        candidateStartAttempt = null
        if (!candidateRecoveryInFlight.compareAndSet(false, true)) return

        scope.launch {
            try {
                recoverCandidateCore(attempt, reason)
            } finally {
                candidateRecoveryInFlight.set(false)
            }
        }
    }

    private fun startUptimeCounter(startedAtMs: Long) {
        uptimeJob?.cancel()
        uptimeJob = scope.launch {
            while (isActive) {
                state.update { current ->
                    if (current.status == ServiceStatus.Running) {
                        current.copy(uptimeSeconds = stateStore.uptimeSecondsFrom(startedAtMs))
                    } else {
                        current
                    }
                }
                delay(UPTIME_REFRESH_INTERVAL_MS)
            }
        }
    }

    fun adoptRecoveredRootRuntime(recovered: RootRuntimeController.RecoveredRuntime) {
        val before = state.value
        val env = recovered.env
        val token = if (env.isNotEmpty()) RuntimeTokenNormalizer.normalizeInput(env["TOKEN"].orEmpty()) else before.token
        val variant = stateStore.normalizeVariantKey(env["DANMU_API_VARIANT"])
            ?.let { key -> ApiVariant.entries.firstOrNull { it.key == key } }
            ?: before.variant
        val listenMode = RuntimeListenMode.fromBindHost(env[RuntimeListenMode.ENV_KEY]) ?: before.listenMode
        RuntimeModePrefs.put(context, RunMode.Root)
        stateStore.persistRecoveredRootConfig(
            port = recovered.port,
            token = token,
            listenMode = listenMode
        )
        stateStore.persistSelectedVariant(variant, commit = true)
        state.update {
            it.copy(runMode = RunMode.Root, port = recovered.port, token = token, variant = variant,
                listenMode = listenMode, pid = recovered.pid, errorMessage = null)
        }
        recovered.startedAtMs?.let { stateStore.saveRuntimeStartedAt(it) }
        rootRecoveryChecked = true
        markRunning(
            pid = recovered.pid, forceNewStart = false,
            statusMessage = if (recovered.apiReady) "已接管正在运行的 Root 服务"
                else "已接管 Root 进程，健康接口暂未响应，可停止或重启"
        )
        log(LogLevel.Info, "已恢复 Root 进程控制，PID=${recovered.pid}，端口=${recovered.port}")
    }
}
