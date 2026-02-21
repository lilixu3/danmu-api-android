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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
    val customRepo = settingsRepo.customRepo
    val proxyOptions = githubProxyService.proxyOptions()

    var isOperating by mutableStateOf(false)
        private set
    var operationMessage by mutableStateOf<String?>(null)
        private set
    var showCustomRepoDialog by mutableStateOf(false)
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
        viewModelScope.launch {
            isOperating = true
            operationMessage = "正在安装 ${variant.label}..."
            coreRepo.installCore(variant).fold(
                onSuccess = { operationMessage = "${variant.label} 安装成功" },
                onFailure = {
                    operationMessage = "安装失败: ${it.message}"
                    promptProxyReselectIfNeeded(PendingProxyAction.Install(variant))
                }
            )
            isOperating = false
        }
    }

    fun deleteCore(variant: ApiVariant) {
        viewModelScope.launch {
            isOperating = true
            coreRepo.deleteCore(variant).fold(
                onSuccess = { operationMessage = "${variant.label} 已删除" },
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
        showUpdateDialog = false
        viewModelScope.launch {
            isOperating = true
            operationMessage = "正在更新 ${variant.label}..."
            coreRepo.updateCore(variant).fold(
                onSuccess = {
                    postCoreAppliedMessageAndRestartIfNeeded(
                        variant = variant,
                        baseMessage = "${variant.label} 更新成功"
                    )
                },
                onFailure = {
                    operationMessage = "更新失败: ${it.message}"
                    promptProxyReselectIfNeeded(PendingProxyAction.DoUpdate(variant))
                }
            )
            isOperating = false
        }
    }

    fun dismissUpdateDialog() { showUpdateDialog = false }

    fun openGearMenu(variant: ApiVariant) { showGearMenu = variant }
    fun dismissGearMenu() { showGearMenu = null }

    fun reinstallCore(variant: ApiVariant) {
        requireProxyAndRun(PendingProxyAction.Reinstall(variant))
    }

    private fun doReinstallCore(variant: ApiVariant) {
        showGearMenu = null
        viewModelScope.launch {
            isOperating = true
            operationMessage = "正在重装 ${variant.label}..."
            coreRepo.deleteCore(variant)
            coreRepo.installCore(variant).fold(
                onSuccess = {
                    postCoreAppliedMessageAndRestartIfNeeded(
                        variant = variant,
                        baseMessage = "${variant.label} 重装成功"
                    )
                },
                onFailure = {
                    operationMessage = "重装失败: ${it.message}"
                    promptProxyReselectIfNeeded(PendingProxyAction.Reinstall(variant))
                }
            )
            isOperating = false
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
            isOperating = true
            operationMessage = "正在回退到 ${release.tagName}..."
            coreRepo.rollbackCore(variant, release).fold(
                onSuccess = {
                    postCoreAppliedMessageAndRestartIfNeeded(
                        variant = variant,
                        baseMessage = "已回退到 ${release.tagName}"
                    )
                },
                onFailure = {
                    operationMessage = "回退失败: ${it.message}"
                    promptProxyReselectIfNeeded(PendingProxyAction.Rollback(variant, release))
                }
            )
            isOperating = false
        }
    }

    fun openCustomRepoDialog() { showCustomRepoDialog = true }
    fun dismissCustomRepoDialog() { showCustomRepoDialog = false }

    fun saveCustomRepo(repo: String) {
        settingsRepo.setCustomRepo(repo)
        showCustomRepoDialog = false
        operationMessage = "自定义仓库已保存: $repo"
    }

    fun dismissMessage() {
        operationMessage = null
    }

    private fun postCoreAppliedMessageAndRestartIfNeeded(variant: ApiVariant, baseMessage: String) {
        val state = runtimeState.value
        if (state.variant == variant && state.status == ServiceStatus.Running) {
            runtimeRepo.restartService()
            operationMessage = "${baseMessage}，服务正在重启以应用变更"
        } else {
            operationMessage = baseMessage
        }
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
}
