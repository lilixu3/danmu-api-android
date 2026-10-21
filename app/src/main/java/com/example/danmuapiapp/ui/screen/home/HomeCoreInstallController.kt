package com.example.danmuapiapp.ui.screen.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.danmuapiapp.data.service.GithubProxyService
import com.example.danmuapiapp.data.service.GithubQuotaExhaustedException
import com.example.danmuapiapp.domain.model.*
import com.example.danmuapiapp.domain.repository.CoreRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.ui.common.CoreUpdateChoiceController
import com.example.danmuapiapp.ui.common.RuntimeRestartEvidence
import com.example.danmuapiapp.ui.common.awaitCoreRestart
import com.example.danmuapiapp.ui.common.continueAfterDependencyRepair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 首页「核心安装、更新与切换」子控制器：下载安装、更新事务、启动监控、失败回退与依赖修复续跑。
 *
 * 对外由 [HomeViewModel] 转发，行为与拆分前一致。
 */
internal class HomeCoreInstallController(
    private val scope: CoroutineScope,
    private val runtimeRepo: RuntimeRepository,
    private val coreRepo: CoreRepository,
    private val githubProxyService: GithubProxyService,
    private val runtimeState: StateFlow<RuntimeState>,
    private val coreInfoList: StateFlow<List<CoreInfo>>,
    private val coreUpdateChoice: CoreUpdateChoiceController,
    private val updatePromptController: HomeCoreUpdatePromptController,
    private val variantLabel: (ApiVariant) -> String,
    private val postMessage: (String) -> Unit,
    private val setPendingProxyAction: (HomePendingProxyAction) -> Unit,
    private val openProxyPickerDialog: () -> Unit
) {
    var showNoCoreDialog by mutableStateOf(false)
        private set
    var unavailableVariant by mutableStateOf<ApiVariant?>(null)
        private set
    var showVariantPicker by mutableStateOf(false)
        private set
    var isInstallingCore by mutableStateOf(false)
        private set
    var isSwitchingCore by mutableStateOf(false)
        private set
    var isUpdatingCore by mutableStateOf(false)
        private set
    var isRepairingDependencies by mutableStateOf(false)
        private set

    /** 安装/更新/切换/依赖修复任一进行中。 */
    val isCoreBusy: Boolean
        get() = isSwitchingCore || isInstallingCore || isUpdatingCore || isRepairingDependencies

    private var pendingRepairContinuation: PendingRepairContinuation? = null

    private sealed interface PendingRepairContinuation {
        data class InstallAndStart(val variant: ApiVariant) : PendingRepairContinuation
        data class Update(val variant: ApiVariant) : PendingRepairContinuation
    }

    /** 依赖修复控制器回写「修复中」状态。 */
    fun updateRepairingDependencies(repairing: Boolean) {
        isRepairingDependencies = repairing
    }

    fun tryStartService() {
        if (isCoreBusy) return
        val variant = runtimeState.value.variant
        scope.launch {
            val ready = withContext(Dispatchers.IO) {
                coreRepo.isCoreReady(variant)
            }
            if (!ready) {
                val info = coreInfoList.value.find { it.variant == variant }
                if (info?.sourceMismatch == true) {
                    postMessage("${variantLabel(variant)} 当前来源与设置不一致，请先重新下载核心")
                } else {
                    showNoCoreDialog = true
                }
                return@launch
            }
            runtimeRepo.startService()
        }
    }

    fun dismissNoCoreDialog() {
        showNoCoreDialog = false
    }

    fun dismissUnavailableVariantDialog() {
        unavailableVariant = null
    }

    fun installUnavailableVariant() {
        val variant = unavailableVariant ?: return
        unavailableVariant = null
        installAndStart(variant)
    }

    fun consumeUnavailableVariantForSettings() {
        unavailableVariant = null
    }

    fun openCoreDownloadDialog() {
        if (isCoreBusy) return
        showNoCoreDialog = true
    }

    fun installAndStart(variant: ApiVariant) {
        if (isSwitchingCore || isUpdatingCore || isRepairingDependencies) return
        if (!githubProxyService.apiQuota.allowManualOperation()) return
        showNoCoreDialog = false
        if (!githubProxyService.hasUserSelectedProxy()) {
            setPendingProxyAction(HomePendingProxyAction.Install(variant))
            openProxyPickerDialog()
            return
        }
        doInstallAndStart(variant)
    }

    fun doInstallAndStart(variant: ApiVariant) {
        if (!githubProxyService.apiQuota.allowManualOperation()) return
        isInstallingCore = true
        scope.launch {
            runtimeRepo.addLog(LogLevel.Info, "正在下载 ${variantLabel(variant)}...")
            coreRepo.installCore(variant).fold(
                onSuccess = {
                    isInstallingCore = false
                    pendingRepairContinuation = null
                    completeInstallAndStart(variant)
                },
                onFailure = { error ->
                    isInstallingCore = false
                    if (error is GithubQuotaExhaustedException) return@fold
                    runtimeRepo.addLog(LogLevel.Error, "安装失败: ${error.message}")
                    if (error is CoreDependencyRepairRequiredException) {
                        pendingRepairContinuation = PendingRepairContinuation.InstallAndStart(variant)
                        postMessage("${variantLabel(variant)}安装已暂停，等待修复依赖")
                    } else if (githubProxyService.isUsingProxy()) {
                        setPendingProxyAction(HomePendingProxyAction.Install(variant))
                        openProxyPickerDialog()
                    }
                }
            )
        }
    }

    fun updateCurrentVariant(variant: ApiVariant) {
        if (isCoreBusy) return
        if (!githubProxyService.apiQuota.allowManualOperation()) return
        if (!githubProxyService.hasUserSelectedProxy()) {
            setPendingProxyAction(HomePendingProxyAction.Update(variant))
            openProxyPickerDialog()
            return
        }
        doUpdateCurrentVariant(variant)
    }

    fun doUpdateCurrentVariant(variant: ApiVariant) {
        if (!githubProxyService.apiQuota.allowManualOperation()) return
        isUpdatingCore = true
        scope.launch {
            try {
                val request = coreUpdateChoice.prepare(coreRepo, variant).getOrElse {
                    if (it !is GithubQuotaExhaustedException) {
                        postMessage("无法准备更新：${it.message}")
                    }
                    return@launch
                } ?: return@launch
                runtimeRepo.addLog(LogLevel.Info, "正在更新 ${variantLabel(variant)}...")
                coreRepo.updateCore(request).fold(
                    onSuccess = {
                        pendingRepairContinuation = null
                        completeCoreUpdate(variant)
                        isUpdatingCore = false
                    },
                    onFailure = { error ->
                        isUpdatingCore = false
                        if (error is GithubQuotaExhaustedException) return@fold
                        runtimeRepo.addLog(LogLevel.Error, "更新失败: ${error.message}")
                        if (error is CoreDependencyRepairRequiredException) {
                            pendingRepairContinuation = PendingRepairContinuation.Update(variant)
                            postMessage("${variantLabel(variant)}更新已暂停，等待修复依赖")
                        } else if (githubProxyService.isUsingProxy()) {
                            setPendingProxyAction(HomePendingProxyAction.Update(variant))
                            openProxyPickerDialog()
                        }
                    }
                )
            } finally {
                isUpdatingCore = false
            }
        }
    }

    fun maybeRestartAfterCoreUpdate(variant: ApiVariant) {
        val state = runtimeState.value
        if (state.variant != variant) return
        if (state.status != ServiceStatus.Running) return
        runtimeRepo.addLog(LogLevel.Info, "核心已更新，正在重启服务以应用变更...")
        runtimeRepo.restartService()
    }

    fun completeInstallAndStart(variant: ApiVariant) {
        val previousVariant = runtimeState.value.variant
        val wasRunning = runtimeState.value.status == ServiceStatus.Running
        val expectsSameVariantRecovery = coreRepo.candidateState.value?.let { candidate ->
            candidate.variant == variant && candidate.hasRecoveryPoint
        } == true
        runtimeRepo.updateVariant(variant)
        runtimeRepo.addLog(LogLevel.Info, "${variantLabel(variant)} 安装成功，已切换为当前核心")
        if (wasRunning) {
            runtimeRepo.addLog(LogLevel.Info, "正在重启服务以应用新核心...")
            runtimeRepo.restartService()
        } else {
            runtimeRepo.startService()
        }
        monitorInstalledCoreStart(variant, previousVariant, wasRunning, expectsSameVariantRecovery)
    }

    private fun monitorInstalledCoreStart(
        installedVariant: ApiVariant,
        previousVariant: ApiVariant,
        wasRunning: Boolean,
        expectsSameVariantRecovery: Boolean
    ) {
        scope.launch {
            isSwitchingCore = true
            try {
                var sawProgress = false
                val started = withTimeoutOrNull(45_000L) {
                    runtimeState.first { state ->
                        when (state.status) {
                            ServiceStatus.Starting, ServiceStatus.Stopping -> {
                                sawProgress = true
                                false
                            }
                            ServiceStatus.Running -> state.variant == installedVariant &&
                                (sawProgress || !wasRunning)
                            ServiceStatus.Error -> sawProgress
                            ServiceStatus.Stopped -> sawProgress
                        }
                    }
                }?.status == ServiceStatus.Running
                if (started) return@launch

                val recoveredSameVariant = if (expectsSameVariantRecovery) {
                    withTimeoutOrNull(40_000L) {
                        runtimeState.first { state ->
                            state.variant == installedVariant && state.status == ServiceStatus.Running
                        }
                    } != null
                } else false
                if (recoveredSameVariant) {
                    postMessage("新核心启动失败，已恢复该版本的上一个可用核心")
                    return@launch
                }

                if (previousVariant == installedVariant) {
                    postMessage("安装完成，但核心启动失败，请查看运行日志")
                    return@launch
                }
                val previousReady = withContext(Dispatchers.IO) {
                    coreRepo.isCoreReady(previousVariant)
                }
                runtimeRepo.updateVariant(previousVariant)
                if (!previousReady) {
                    postMessage("新核心启动失败，已恢复原核心选择；原核心需要重新安装")
                    return@launch
                }
                runtimeRepo.addLog(
                    LogLevel.Warn,
                    "新安装核心启动失败，已切回 ${variantLabel(previousVariant)}"
                )
                runtimeRepo.restartService()
                val fallbackRunning = withTimeoutOrNull(45_000L) {
                    runtimeState.first { state ->
                        state.variant == previousVariant && state.status == ServiceStatus.Running
                    }
                } != null
                postMessage(
                    if (fallbackRunning) {
                        "新核心启动失败，已恢复并启动 ${variantLabel(previousVariant)}"
                    } else {
                        "新核心启动失败，已恢复 ${variantLabel(previousVariant)} 选择，请检查运行日志"
                    }
                )
            } finally {
                isSwitchingCore = false
            }
        }
    }

    fun completeCoreUpdate(variant: ApiVariant) {
        runtimeRepo.addLog(LogLevel.Info, "${variantLabel(variant)} 更新成功")
        maybeRestartAfterCoreUpdate(variant)
        updatePromptController.clearIgnoredVersion(variant)
    }

    /** 依赖修复完成后的续跑回调（供 [com.example.danmuapiapp.ui.common.CoreDependencyRepairController] 使用）。 */
    suspend fun onDependenciesApplied(request: CoreDependencyRepairRequest): String {
        val continuation = pendingRepairContinuation
        pendingRepairContinuation = null
        when (continuation) {
            is PendingRepairContinuation.InstallAndStart -> {
                if (continuation.variant == request.variant) {
                    completeInstallAndStart(request.variant)
                    return "${variantLabel(request.variant)}安装成功，服务正在启动"
                }
            }
            is PendingRepairContinuation.Update -> {
                if (continuation.variant == request.variant) {
                    completeCoreUpdate(request.variant)
                    return "${variantLabel(request.variant)}更新成功"
                }
            }
            null -> Unit
        }

        coreRepo.refreshCoreInfo()
        val runtimeContinuation = runtimeRepo.continueAfterDependencyRepair(request)
        if (runtimeContinuation != null) {
            return "${variantLabel(request.variant)}依赖已修复，$runtimeContinuation"
        }
        maybeRestartAfterCoreUpdate(request.variant)
        return if (request.origin == CoreDependencyRepairOrigin.WorkDirectory) {
            "当前工作目录依赖已修复"
        } else {
            "${variantLabel(request.variant)}${request.actionLabel}成功"
        }
    }

    /** 用户放弃依赖修复时的提示文案。 */
    fun onDependenciesDiscarded(request: CoreDependencyRepairRequest): String {
        pendingRepairContinuation = null
        return if (request.origin == CoreDependencyRepairOrigin.WorkDirectory) {
            "已取消依赖修复，当前工作目录保持不变"
        } else {
            "已取消${variantLabel(request.variant)}${request.actionLabel}，原核心保持不变"
        }
    }

    fun switchVariant(variant: ApiVariant) {
        if (isCoreBusy) return

        val current = runtimeState.value
        if (variant == current.variant) {
            showVariantPicker = false
            updatePromptController.refreshUpdatePrompt(coreInfoList.value)
            return
        }

        showVariantPicker = false
        scope.launch {
            isSwitchingCore = true
            try {
                val wasRunning = current.status == ServiceStatus.Running

                val ready = withContext(Dispatchers.IO) {
                    coreRepo.isCoreReady(variant)
                } && coreInfoList.value.find { it.variant == variant }?.sourceMismatch != true
                if (!ready) {
                    val latestInfo = coreInfoList.value.find { it.variant == variant }
                    if (latestInfo?.sourceMismatch == true) {
                        runtimeRepo.addLog(LogLevel.Warn, "${variantLabel(variant)} 来源与设置不一致，需先重新下载")
                        postMessage("${variantLabel(variant)} 当前来源与设置不一致，请先重新下载核心")
                        return@launch
                    }
                    runtimeRepo.addLog(LogLevel.Warn, "${variantLabel(variant)} 未安装，已保留当前核心选择")
                    unavailableVariant = variant
                    return@launch
                }

                runtimeRepo.addLog(LogLevel.Info, "切换核心到 ${variantLabel(variant)}")
                val expectsSameVariantRecovery = coreRepo.candidateState.value?.let { candidate ->
                    candidate.variant == variant && candidate.hasRecoveryPoint
                } == true
                runtimeRepo.updateVariant(variant)

                if (wasRunning) {
                    val restartSnapshot = RuntimeRestartEvidence.snapshot(current)
                    runtimeRepo.addLog(LogLevel.Info, "正在重启服务以应用核心切换...")
                    runtimeRepo.restartService()
                    val restarted = runtimeState.awaitCoreRestart(
                        targetVariant = variant,
                        beforeRestart = restartSnapshot,
                        timeoutMs = 45_000
                    ).status
                    if (restarted != ServiceStatus.Running) {
                        val reason = when (restarted) {
                            ServiceStatus.Error -> "切换后服务启动失败，请查看日志"
                            ServiceStatus.Stopped -> "切换后服务未运行，请重试启动"
                            null -> "切换后服务启动超时，请查看日志"
                            else -> "切换后服务状态异常，请查看日志"
                        }
                        runtimeRepo.addLog(LogLevel.Error, reason)
                        val recoveredSameVariant = if (expectsSameVariantRecovery) {
                            runtimeRepo.addLog(LogLevel.Warn, "正在等待候选核心自动恢复结果")
                            withTimeoutOrNull(40_000L) {
                                runtimeState.first { state ->
                                    state.variant == variant && state.status == ServiceStatus.Running
                                }
                            } != null
                        } else {
                            false
                        }
                        if (recoveredSameVariant) {
                            postMessage("新版本启动失败，已恢复 ${variantLabel(variant)} 的上一个可用版本")
                            return@launch
                        }

                        val previousReady = withContext(Dispatchers.IO) {
                            coreRepo.isCoreReady(current.variant)
                        }
                        runtimeRepo.updateVariant(current.variant)
                        if (previousReady) {
                            runtimeRepo.addLog(
                                LogLevel.Warn,
                                "切换失败，已恢复选择 ${variantLabel(current.variant)}，正在重新启动"
                            )
                            runtimeRepo.restartService()
                            val restored = withTimeoutOrNull(45_000L) {
                                runtimeState.first { state ->
                                    state.variant == current.variant && state.status == ServiceStatus.Running
                                }
                            } != null
                            postMessage(
                                if (restored) {
                                    "切换失败，已恢复并启动 ${variantLabel(current.variant)}"
                                } else {
                                    "切换失败，已恢复 ${variantLabel(current.variant)} 选择，请检查运行日志"
                                }
                            )
                        } else {
                            postMessage("切换失败，已恢复原核心选择；原核心当前不可用，请先重新安装")
                        }
                    }
                }
            } catch (t: Throwable) {
                runtimeRepo.addLog(LogLevel.Error, "切换核心失败: ${t.message ?: "未知错误"}")
            } finally {
                isSwitchingCore = false
                updatePromptController.refreshUpdatePrompt(coreInfoList.value)
            }
        }
    }

    fun openVariantPicker() { showVariantPicker = true }
    fun dismissVariantPicker() { showVariantPicker = false }
}
