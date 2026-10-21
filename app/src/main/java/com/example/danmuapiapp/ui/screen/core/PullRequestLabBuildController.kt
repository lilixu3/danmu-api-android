package com.example.danmuapiapp.ui.screen.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreDependencyRepairRequest
import com.example.danmuapiapp.domain.model.CorePullRequest
import com.example.danmuapiapp.domain.model.PullRequestMergeConflictException
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.model.RuntimeTransitionKind
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.repository.CoreRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * PR 实验室「组合构建、原子应用与启动恢复」流水线控制器。
 *
 * 负责组合准备、冲突与错误上报、停服后原子应用、安装后启动确认，以及失败时的
 * 服务恢复与核心变体回退。构建期状态由本控制器持有，[PullRequestLabViewModel] 以
 * getter 转发；错误文案与页面加载共用一条错误通道，因此仍由 ViewModel 持有并注入。
 *
 * 行为与拆分前一致：超时阈值、状态文案、取消语义与恢复顺序均未改动。
 */
internal class PullRequestLabBuildController(
    private val scope: CoroutineScope,
    private val coreRepository: CoreRepository,
    private val runtimeRepository: RuntimeRepository,
    private val variant: ApiVariant,
    private val runtimeState: StateFlow<RuntimeState>,
    private val selectedPullRequests: () -> List<CorePullRequest>,
    private val clearSelection: () -> Unit,
    private val activateAfterInstall: () -> Boolean,
    private val isPrivateRepository: () -> Boolean,
    private val onBuildSettled: () -> Unit,
    private val postError: (String?) -> Unit
) {
    var isBuilding by mutableStateOf(false)
        private set
    var isActivating by mutableStateOf(false)
        private set
    var statusMessage by mutableStateOf<String?>(null)
        private set
    var conflictFiles by mutableStateOf<List<String>>(emptyList())
        private set
    var conflictPullRequestNumber by mutableStateOf<Int?>(null)
        private set
    var showBuildConfirmation by mutableStateOf(false)
        private set

    private var buildJob: Job? = null

    /** 选择变化后清掉上一次的冲突提示。 */
    fun clearConflict() {
        conflictFiles = emptyList()
        conflictPullRequestNumber = null
    }

    fun requestBuild() {
        if (selectedPullRequests().isEmpty() || isBuilding || isActivating) return
        if (isPrivateRepository()) {
            postError("为保证 Token 只发往 GitHub 官方 API，PR 实验室暂不克隆私有仓库")
            return
        }
        showBuildConfirmation = true
    }

    fun dismissBuildConfirmation() {
        showBuildConfirmation = false
    }

    fun confirmBuild() {
        if (isBuilding || isActivating || selectedPullRequests().isEmpty()) return
        showBuildConfirmation = false
        postError(null)
        statusMessage = null
        conflictFiles = emptyList()
        conflictPullRequestNumber = null
        val shouldActivate = activateAfterInstall()
        buildJob = scope.launch {
            isBuilding = true
            try {
                coreRepository.preparePullRequestStack(
                    variant = variant,
                    pullRequestNumbers = selectedPullRequests().map { it.number }
                ).fold(
                    onSuccess = { request ->
                        applyPreparedPullRequest(request, shouldActivate)
                    },
                    onFailure = { error ->
                        when (error) {
                            is PullRequestMergeConflictException -> {
                                conflictPullRequestNumber = error.pullRequestNumber
                                conflictFiles = error.conflictFiles
                                postError(error.message)
                            }
                            else -> postError(error.message ?: "PR 组合构建失败")
                        }
                    }
                )
            } finally {
                isBuilding = false
                onBuildSettled()
            }
        }
    }

    fun cancelBuild() {
        if (isActivating) return
        buildJob?.cancel()
        buildJob = null
        statusMessage = "已取消 PR 组合构建，当前核心未变更"
    }

    fun consumeStatusMessage() {
        statusMessage = null
    }

    private suspend fun applyPreparedPullRequest(
        request: CoreDependencyRepairRequest,
        shouldActivate: Boolean
    ) = withContext(NonCancellable) {
        isActivating = true
        val previous = runtimeState.value
        val wasActive = previous.status == ServiceStatus.Running ||
            previous.status == ServiceStatus.Starting
        var stoppedForApply = false
        var runtimeTransitionId: Long? = null
        try {
            val applyPlan = decidePullRequestApplyPlan(previous, variant, shouldActivate)
            if (applyPlan.shouldStartTargetAfterApply) {
                runtimeTransitionId = runtimeRepository.beginRuntimeTransition(
                    kind = RuntimeTransitionKind.ApplyingPullRequests,
                    message = "正在安全应用 PR 组合并重启服务…"
                )
            }
            stoppedForApply = if (applyPlan.shouldAwaitStopped) {
                statusMessage = "PR 组合已准备完成，正在安全停止服务"
                runtimeTransitionId?.let { transitionId ->
                    runtimeRepository.updateRuntimeTransition(
                        transitionId,
                        "PR 组合已准备完成，正在安全停止旧服务…"
                    )
                }
                stoppedForApply = applyPlan.shouldStartTargetAfterApply
                if (applyPlan.shouldRequestStop) runtimeRepository.stopService()
                val stopped = withTimeoutOrNull(25_000L) {
                    runtimeState.first {
                        it.status == ServiceStatus.Stopped || it.status == ServiceStatus.Error
                    }
                } != null
                if (!stopped) {
                    coreRepository.discardPendingCoreMutation(request.operationId)
                    restoreInterruptedService(previous, stoppedForApply, wasActive)
                    postError("停止服务超时，未替换当前核心，请稍后重试")
                    return@withContext
                }
                applyPlan.shouldStartTargetAfterApply
            } else {
                false
            }

            statusMessage = "正在原子应用 PR 组合"
            runtimeTransitionId?.let { transitionId ->
                runtimeRepository.updateRuntimeTransition(
                    transitionId,
                    "旧服务已停止，正在原子应用 PR 组合…"
                )
            }
            val applyResult = coreRepository.applyPendingCoreMutation(request.operationId)
            val applyError = applyResult.exceptionOrNull()
            if (applyError != null) {
                postError(applyError.message ?: "应用 PR 组合失败")
                restoreInterruptedService(previous, stoppedForApply, wasActive)
                return@withContext
            }
            runtimeTransitionId?.let { transitionId ->
                runtimeRepository.updateRuntimeTransition(
                    transitionId,
                    "PR 组合已应用，正在启动服务…"
                )
            }
            finishInstallation(
                shouldActivate = shouldActivate,
                previous = previous,
                stoppedForApply = stoppedForApply
            )
        } catch (error: Exception) {
            postError(error.message ?: "应用 PR 组合时发生异常")
            restoreInterruptedService(previous, stoppedForApply, wasActive)
        } finally {
            runtimeTransitionId?.let(runtimeRepository::endRuntimeTransition)
            isActivating = false
            onBuildSettled()
        }
    }

    private suspend fun restoreInterruptedService(
        previous: RuntimeState,
        stoppedForApply: Boolean,
        wasActive: Boolean
    ) {
        if (stoppedForApply && wasActive) {
            runtimeRepository.updateVariant(previous.variant)
            runtimeRepository.startService()
            statusMessage = "PR 组合未应用，正在恢复原服务"
            withTimeoutOrNull(8_000L) {
                runtimeState.first { state ->
                    state.status == ServiceStatus.Starting ||
                        state.status == ServiceStatus.Running ||
                        state.status == ServiceStatus.Error
                }
            }
        }
    }

    private suspend fun finishInstallation(
        shouldActivate: Boolean,
        previous: RuntimeState,
        stoppedForApply: Boolean
    ) {
        coreRepository.refreshCoreInfo()
        clearSelection()
        val shouldRunAppliedCore = stoppedForApply &&
            (shouldActivate || previous.variant == variant)
        if (shouldActivate) {
            runtimeRepository.updateVariant(variant)
        }
        if (!shouldRunAppliedCore) {
            statusMessage = "PR 组合核心已安装"
            return
        }

        val wasActive = previous.status == ServiceStatus.Running ||
            previous.status == ServiceStatus.Starting
        val expectsSameVariantRecovery = coreRepository.candidateState.value?.let { candidate ->
            candidate.variant == variant && candidate.hasRecoveryPoint
        } == true

        runtimeRepository.startService()

        var sawTransition = false
        val terminal = withTimeoutOrNull(45_000L) {
            runtimeState.first { state ->
                when (state.status) {
                    ServiceStatus.Starting, ServiceStatus.Stopping -> {
                        sawTransition = true
                        false
                    }
                    ServiceStatus.Running -> sawTransition && state.variant == variant
                    ServiceStatus.Error, ServiceStatus.Stopped -> sawTransition
                }
            }
        }
        if (terminal?.status == ServiceStatus.Running) {
            statusMessage = "PR 组合已安装并启动，正在后台确认稳定性"
            if (!expectsSameVariantRecovery && previous.variant != variant) {
                monitorFreshVariantCandidate(previous.variant, wasActive)
            }
            return
        }

        delay(500L)
        val candidateStillPending = coreRepository.candidateState.value?.variant == variant
        if (terminal?.status == ServiceStatus.Stopped && candidateStillPending) {
            statusMessage = "PR 组合已安装并切换，服务已停止，未执行自动回退"
            return
        }
        if (expectsSameVariantRecovery) {
            val recovered = withTimeoutOrNull(40_000L) {
                runtimeState.first { it.variant == variant && it.status == ServiceStatus.Running }
            } != null
            if (recovered) {
                statusMessage = "PR 组合启动失败，已恢复该核心的上一个可用版本"
                return
            }
        }
        restorePreviousVariant(previous.variant, wasActive)
    }

    private fun monitorFreshVariantCandidate(previousVariant: ApiVariant, wasActive: Boolean) {
        scope.launch {
            val outcome = withTimeoutOrNull(35_000L) {
                combine(coreRepository.candidateState, runtimeState) { candidate, runtime ->
                    candidate to runtime
                }.first { (candidate, _) -> candidate?.variant != variant }
            } ?: return@launch
            val runtime = outcome.second
            if (runtime.variant == variant &&
                (runtime.status == ServiceStatus.Error || runtime.status == ServiceStatus.Stopped)
            ) {
                restorePreviousVariant(previousVariant, wasActive)
            } else if (runtime.variant == variant && runtime.status == ServiceStatus.Running) {
                statusMessage = "PR 组合已稳定运行"
            }
        }
    }

    private suspend fun restorePreviousVariant(previousVariant: ApiVariant, shouldRestart: Boolean) {
        if (previousVariant == variant || runtimeState.value.variant != variant) {
            statusMessage = "PR 组合启动失败，请查看运行日志"
            return
        }
        val previousReady = withContext(Dispatchers.IO) {
            coreRepository.isCoreReady(previousVariant)
        }
        runtimeRepository.updateVariant(previousVariant)
        if (!previousReady) {
            statusMessage = "PR 组合启动失败，已恢复原核心选择；原核心需要重新安装"
            return
        }
        if (!shouldRestart) {
            statusMessage = "PR 组合启动失败，已恢复原核心选择"
            return
        }
        runtimeRepository.restartService()
        val restored = withTimeoutOrNull(45_000L) {
            runtimeState.first {
                it.variant == previousVariant && it.status == ServiceStatus.Running
            }
        } != null
        statusMessage = if (restored) {
            "PR 组合启动失败，已恢复并启动原核心"
        } else {
            "PR 组合启动失败，已恢复原核心选择，请查看运行日志"
        }
    }
}
