package com.example.danmuapiapp.ui.screen.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.data.service.GithubProxyService
import com.example.danmuapiapp.domain.model.*
import com.example.danmuapiapp.domain.repository.CoreRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class CoreViewModel @Inject constructor(
    private val coreRepo: CoreRepository,
    private val runtimeRepo: RuntimeRepository,
    private val settingsRepo: SettingsRepository,
    private val githubProxyService: GithubProxyService
) : ViewModel() {

    val coreInfoList = coreRepo.coreInfoList
    val downloadProgress = coreRepo.downloadProgress
    val runtimeState = runtimeRepo.runtimeState
    val coreDisplayNames = settingsRepo.coreDisplayNames
    val customCoreSource = settingsRepo.customCoreSource
    val customRepo = settingsRepo.customRepo
    val customRepoBranch = settingsRepo.customRepoBranch
    val proxyOptions = githubProxyService.proxyOptions()

    var isOperating by mutableStateOf(false)
        private set
    var operationMessage by mutableStateOf<String?>(null)
        private set
    var showVariantSettingsDialog by mutableStateOf<ApiVariant?>(null)
        private set
    var isCheckingUpdate by mutableStateOf(false)
        private set
    var showUpdateDialog by mutableStateOf(false)
        private set
    var updateDialogVariant by mutableStateOf<ApiVariant?>(null)
        private set
    var updateDialogInfo by mutableStateOf<CoreInfo?>(null)
        private set
    var showGearMenu by mutableStateOf<ApiVariant?>(null)
        private set
    var showRollbackDialog by mutableStateOf(false)
        private set
    var rollbackVariant by mutableStateOf<ApiVariant?>(null)
        private set
    var releaseHistory by mutableStateOf<List<GithubRelease>>(emptyList())
        private set
    var isLoadingHistory by mutableStateOf(false)
        private set
    var showProxyPickerDialog by mutableStateOf(false)
        private set
    var proxySelectedId by mutableStateOf(githubProxyService.currentSelectedOption().id)
        private set
    var proxyTestingIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var proxyLatencyMap by mutableStateOf<Map<String, Long>>(emptyMap())
        private set

    private var pendingProxyAction: PendingProxyAction? = null
    private var proxyTestJob: Job? = null

    init {
        coreRepo.refreshCoreInfo()
        observeRuntimeDrivenCoreRefresh()
    }

    private fun observeRuntimeDrivenCoreRefresh() {
        viewModelScope.launch {
            runtimeState
                .map { Triple(it.runMode, it.status, it.variant) }
                .distinctUntilChanged()
                .collect {
                    coreRepo.refreshCoreInfo()
                }
        }
    }

    private sealed interface PendingProxyAction {
        data class Install(val variant: ApiVariant) : PendingProxyAction
        data class CheckUpdate(val variant: ApiVariant) : PendingProxyAction
        data class DoUpdate(val variant: ApiVariant) : PendingProxyAction
        data class Reinstall(val variant: ApiVariant) : PendingProxyAction
        data class LoadRollbackHistory(val variant: ApiVariant) : PendingProxyAction
        data class Rollback(val variant: ApiVariant, val release: GithubRelease) : PendingProxyAction
    }

    private enum class PostApplyRestartResult {
        None,
        Restarting,
        StopTimeout
    }

    fun updateVariant(variant: ApiVariant) {
        runtimeRepo.updateVariant(variant)
        if (runtimeState.value.status == ServiceStatus.Running) {
            runtimeRepo.restartService()
        }
    }

    fun installCore(variant: ApiVariant) {
        requireProxyAndRun(PendingProxyAction.Install(variant))
    }

    private fun doInstallCore(variant: ApiVariant) {
        val label = variantLabel(variant)
        viewModelScope.launch {
            performCoreMutation(
                variant = variant,
                actionMessage = "正在安装 $label...",
                successMessage = "$label 安装成功",
                stopTimeoutMessage = "$label 安装前停止服务超时，请稍后重试",
                failurePrefix = "安装失败",
                pendingAction = PendingProxyAction.Install(variant),
                applyBlock = { coreRepo.installCore(variant) }
            )
        }
    }

    fun deleteCore(variant: ApiVariant) {
        val label = variantLabel(variant)
        viewModelScope.launch {
            isOperating = true
            coreRepo.deleteCore(variant).fold(
                onSuccess = { operationMessage = "$label 已删除" },
                onFailure = { operationMessage = "删除失败: ${it.message}" }
            )
            isOperating = false
        }
    }

    fun checkUpdate(variant: ApiVariant) {
        requireProxyAndRun(PendingProxyAction.CheckUpdate(variant))
    }

    private fun doCheckUpdate(variant: ApiVariant) {
        viewModelScope.launch {
            isCheckingUpdate = true
            settingsRepo.setIgnoredUpdateVersion(variant, null)
            coreRepo.checkAndMarkUpdate(variant)
            val info = coreInfoList.value.find { it.variant == variant }
            updateDialogVariant = variant
            updateDialogInfo = info
            showUpdateDialog = true
            isCheckingUpdate = false
        }
    }

    fun doUpdate(variant: ApiVariant) {
        requireProxyAndRun(PendingProxyAction.DoUpdate(variant))
    }

    private fun doUpdateCore(variant: ApiVariant) {
        val label = variantLabel(variant)
        showUpdateDialog = false
        viewModelScope.launch {
            performCoreMutation(
                variant = variant,
                actionMessage = "正在更新 $label...",
                successMessage = "$label 更新成功",
                stopTimeoutMessage = "$label 更新前停止服务超时，请稍后重试",
                failurePrefix = "更新失败",
                pendingAction = PendingProxyAction.DoUpdate(variant),
                applyBlock = { coreRepo.updateCore(variant) }
            )
        }
    }

    fun dismissUpdateDialog() { showUpdateDialog = false }

    fun openGearMenu(variant: ApiVariant) { showGearMenu = variant }
    fun dismissGearMenu() { showGearMenu = null }

    fun reinstallCore(variant: ApiVariant) {
        requireProxyAndRun(PendingProxyAction.Reinstall(variant))
    }

    private fun doReinstallCore(variant: ApiVariant) {
        val label = variantLabel(variant)
        showGearMenu = null
        viewModelScope.launch {
            performCoreMutation(
                variant = variant,
                actionMessage = "正在重装 $label...",
                successMessage = "$label 重装成功",
                stopTimeoutMessage = "$label 重装前停止服务超时，请稍后重试",
                failurePrefix = "重装失败",
                pendingAction = PendingProxyAction.Reinstall(variant),
                applyBlock = { coreRepo.installCore(variant) }
            )
        }
    }

    fun openRollbackDialog(variant: ApiVariant) {
        requireProxyAndRun(PendingProxyAction.LoadRollbackHistory(variant))
    }

    private fun loadRollbackHistory(variant: ApiVariant) {
        showGearMenu = null
        rollbackVariant = variant
        showRollbackDialog = true
        viewModelScope.launch {
            isLoadingHistory = true
            releaseHistory = coreRepo.fetchReleaseHistory(variant)
            if (releaseHistory.isEmpty()) {
                operationMessage = "未获取到可回退版本，请在设置中检查 GitHub 代理"
                promptProxyReselectIfNeeded(PendingProxyAction.LoadRollbackHistory(variant))
            }
            isLoadingHistory = false
        }
    }

    fun dismissRollbackDialog() {
        showRollbackDialog = false
        rollbackVariant = null
        releaseHistory = emptyList()
    }

    fun rollbackTo(variant: ApiVariant, release: GithubRelease) {
        requireProxyAndRun(PendingProxyAction.Rollback(variant, release))
    }

    private fun doRollbackTo(variant: ApiVariant, release: GithubRelease) {
        showRollbackDialog = false
        viewModelScope.launch {
            performCoreMutation(
                variant = variant,
                actionMessage = "正在回退到 ${release.tagName}...",
                successMessage = "已回退到 ${release.tagName}",
                stopTimeoutMessage = "${variantLabel(variant)} 回退前停止服务超时，请稍后重试",
                failurePrefix = "回退失败",
                pendingAction = PendingProxyAction.Rollback(variant, release),
                applyBlock = { coreRepo.rollbackCore(variant, release) }
            )
        }
    }

    fun openVariantSettingsDialog(variant: ApiVariant) {
        showGearMenu = null
        showVariantSettingsDialog = variant
    }

    fun dismissVariantSettingsDialog() {
        showVariantSettingsDialog = null
    }

    fun saveVariantSettings(
        variant: ApiVariant,
        displayName: String,
        customRepo: String = "",
        customBranch: String = ""
    ) {
        if (variant == ApiVariant.Custom) {
            val resolved = settingsRepo.saveCustomCoreConfig(
                displayName = displayName,
                repoInput = customRepo,
                branchInput = customBranch
            )
            coreRepo.refreshCoreInfo()
            val label = resolved.displayName.ifBlank { variant.label }
            val repoText = resolved.repo.ifBlank { "未配置仓库" }
            val branchText = resolved.branch.ifBlank { "--" }
            operationMessage = "$label 已保存（$repoText · $branchText）"
        } else {
            settingsRepo.setVariantDisplayName(variant, displayName)
            val label = displayName.trim().ifBlank { variant.label }
            operationMessage = "$label 名称已保存"
        }
        showVariantSettingsDialog = null
    }

    fun dismissMessage() {
        operationMessage = null
    }

    private suspend fun performCoreMutation(
        variant: ApiVariant,
        actionMessage: String,
        successMessage: String,
        stopTimeoutMessage: String,
        failurePrefix: String,
        pendingAction: PendingProxyAction,
        applyBlock: suspend () -> Result<Unit>
    ) {
        isOperating = true
        operationMessage = actionMessage
        val applyPlan = decideCoreApplyPlan(runtimeState.value, variant)

        if (applyPlan.shouldStopServiceBeforeApply) {
            operationMessage = "正在停止服务以安全应用 ${variantLabel(variant)} 变更..."
            runtimeRepo.stopService()
            val stopped = waitForRuntimeStoppedBeforeCoreMutation()
            if (!stopped) {
                operationMessage = stopTimeoutMessage
                isOperating = false
                return
            }
        }

        applyBlock().fold(
            onSuccess = {
                val restartPlan = decideCoreApplyPlan(runtimeState.value, variant)
                when (
                    if (restartPlan.shouldRestartServiceAfterApply) {
                        restartRuntimeAfterCoreMutation(variant)
                    } else {
                        PostApplyRestartResult.None
                    }
                ) {
                    PostApplyRestartResult.Restarting -> {
                        operationMessage = "${successMessage}，服务正在重启以应用变更"
                    }
                    PostApplyRestartResult.StopTimeout -> {
                        operationMessage = "${successMessage}，但服务自动重启前停止超时，请稍后手动重启服务"
                    }
                    PostApplyRestartResult.None -> {
                        operationMessage = successMessage
                    }
                }
            },
            onFailure = {
                operationMessage = "$failurePrefix: ${it.message}"
                promptProxyReselectIfNeeded(pendingAction)
            }
        )
        isOperating = false
    }

    private suspend fun restartRuntimeAfterCoreMutation(variant: ApiVariant): PostApplyRestartResult {
        val state = runtimeState.value
        if (state.variant != variant) return PostApplyRestartResult.None

        return when (state.status) {
            ServiceStatus.Running -> {
                runtimeRepo.restartService()
                PostApplyRestartResult.Restarting
            }
            ServiceStatus.Starting -> {
                runtimeRepo.stopService()
                val stopped = waitForRuntimeStoppedBeforeCoreMutation()
                if (stopped) {
                    runtimeRepo.startService()
                    PostApplyRestartResult.Restarting
                } else {
                    PostApplyRestartResult.StopTimeout
                }
            }
            else -> PostApplyRestartResult.None
        }
    }

    private suspend fun waitForRuntimeStoppedBeforeCoreMutation(timeoutMs: Long = 25_000L): Boolean {
        return withTimeoutOrNull(timeoutMs) {
            runtimeState.first { state ->
                state.status == ServiceStatus.Stopped || state.status == ServiceStatus.Error
            }
        } != null
    }

    fun openProxyPickerManually() {
        pendingProxyAction = null
        openProxyPickerDialog(withTip = null)
    }

    fun dismissProxyPickerDialog() {
        showProxyPickerDialog = false
        stopProxySpeedTest()
        pendingProxyAction = null
    }

    fun selectProxy(proxyId: String) {
        proxySelectedId = proxyId
    }

    fun retestProxySpeed() {
        startProxySpeedTest()
    }

    fun confirmProxySelection() {
        githubProxyService.setSelectedProxy(proxySelectedId)
        showProxyPickerDialog = false
        stopProxySpeedTest()
        val action = pendingProxyAction
        pendingProxyAction = null
        if (action != null) {
            executePendingProxyAction(action)
        } else {
            operationMessage = "已切换 GitHub 线路：${proxyOptions.firstOrNull { it.id == proxySelectedId }?.name ?: "未知"}"
        }
    }

    private fun requireProxyAndRun(action: PendingProxyAction) {
        if (githubProxyService.hasUserSelectedProxy()) {
            executePendingProxyAction(action)
            return
        }
        pendingProxyAction = action
        openProxyPickerDialog(withTip = "首次使用请先选择 GitHub 线路")
    }

    private fun executePendingProxyAction(action: PendingProxyAction) {
        when (action) {
            is PendingProxyAction.Install -> doInstallCore(action.variant)
            is PendingProxyAction.CheckUpdate -> doCheckUpdate(action.variant)
            is PendingProxyAction.DoUpdate -> doUpdateCore(action.variant)
            is PendingProxyAction.Reinstall -> doReinstallCore(action.variant)
            is PendingProxyAction.LoadRollbackHistory -> loadRollbackHistory(action.variant)
            is PendingProxyAction.Rollback -> doRollbackTo(action.variant, action.release)
        }
    }

    private fun promptProxyReselectIfNeeded(retryAction: PendingProxyAction) {
        if (!githubProxyService.isUsingProxy()) return
        pendingProxyAction = retryAction
        openProxyPickerDialog(withTip = "当前加速线路不可用，请重新测速并选择")
    }

    private fun openProxyPickerDialog(withTip: String?) {
        showProxyPickerDialog = true
        proxySelectedId = githubProxyService.currentSelectedOption().id
        withTip?.let { operationMessage = it }
        startProxySpeedTest()
    }

    private fun startProxySpeedTest() {
        stopProxySpeedTest()
        proxyLatencyMap = emptyMap()
        proxyTestingIds = proxyOptions.map { it.id }.toSet()
        proxyTestJob = viewModelScope.launch {
            proxyOptions.forEach { option ->
                launch {
                    val latency = githubProxyService.testLatency(option)
                    proxyLatencyMap = proxyLatencyMap + (option.id to latency)
                    proxyTestingIds = proxyTestingIds - option.id
                }
            }
        }
    }

    private fun stopProxySpeedTest() {
        proxyTestJob?.cancel()
        proxyTestJob = null
        proxyTestingIds = emptySet()
    }

    private fun variantLabel(variant: ApiVariant): String {
        return coreDisplayNames.value.resolve(variant)
    }
}
