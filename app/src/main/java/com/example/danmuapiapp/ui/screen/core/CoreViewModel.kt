package com.example.danmuapiapp.ui.screen.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.data.service.GithubAccountService
import com.example.danmuapiapp.data.service.GithubProxyService
import com.example.danmuapiapp.domain.model.*
import com.example.danmuapiapp.domain.repository.CoreRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.domain.repository.SettingsRepository
import com.example.danmuapiapp.ui.common.continueAfterDependencyRepair
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
    private val githubProxyService: GithubProxyService,
    private val githubAccountService: GithubAccountService
) : ViewModel() {

    val coreInfoList = coreRepo.coreInfoList
    val downloadProgress = coreRepo.downloadProgress
    val pendingDependencyRepair = coreRepo.pendingDependencyRepair
    val runtimeState = runtimeRepo.runtimeState
    val coreDisplayNames = settingsRepo.coreDisplayNames
    val customCoreSource = settingsRepo.customCoreSource
    val customRepo = settingsRepo.customRepo
    val customRepoBranch = settingsRepo.customRepoBranch
    val githubToken = settingsRepo.githubToken
    val githubAccountStatus = githubAccountService.status
    val proxyOptions = githubProxyService.proxyOptions()

    var isOperating by mutableStateOf(false)
        private set
    var operationMessage by mutableStateOf<String?>(null)
        private set
    var showDependencyRequiredPrompt by mutableStateOf(false)
        private set
    var showDependencyRepairDialog by mutableStateOf(false)
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
    var showRevisionHistory by mutableStateOf(false)
        private set
    var rollbackVariant by mutableStateOf<ApiVariant?>(null)
        private set
    var revisionHistory by mutableStateOf<List<CoreRevision>>(emptyList())
        private set
    var isLoadingHistory by mutableStateOf(false)
        private set
    var revisionHistoryError by mutableStateOf<String?>(null)
        private set
    var revisionSearchQuery by mutableStateOf("")
        private set
    var appliedRevisionSearchQuery by mutableStateOf("")
        private set
    var revisionPage by mutableStateOf(1)
        private set
    var revisionHasNextPage by mutableStateOf(false)
        private set
    var selectedRevisionDetails by mutableStateOf<CoreRevisionDetails?>(null)
        private set
    var selectedRevision by mutableStateOf<CoreRevision?>(null)
        private set
    var isLoadingRevisionDetails by mutableStateOf(false)
        private set
    var revisionDetailsError by mutableStateOf<String?>(null)
        private set
    var pendingRollbackRevision by mutableStateOf<CoreRevision?>(null)
        private set
    var showGithubTokenDialog by mutableStateOf(false)
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
    private var githubAccountJob: Job? = null
    private var githubAccountGeneration = 0L
    private var revisionDetailsJob: Job? = null
    private var revisionDetailsGeneration = 0L
    private var revisionHistoryJob: Job? = null
    private var revisionHistoryGeneration = 0L

    init {
        coreRepo.refreshCoreInfo()
        observeRuntimeDrivenCoreRefresh()
        observePendingDependencyRepair()
        refreshGithubAccount()
    }

    private fun observePendingDependencyRepair() {
        viewModelScope.launch {
            pendingDependencyRepair.collect { request ->
                if (request == null) {
                    showDependencyRequiredPrompt = false
                    showDependencyRepairDialog = false
                } else if (!showDependencyRepairDialog) {
                    showDependencyRequiredPrompt = true
                }
            }
        }
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
        data class Rollback(val variant: ApiVariant, val revision: CoreRevision) : PendingProxyAction
        data object RepairDependenciesOnline : PendingProxyAction
    }

    private enum class PostApplyRestartResult {
        None,
        Restarting,
        StopTimeout
    }

    fun updateVariant(variant: ApiVariant) {
        if (isOperating) return
        val info = coreInfoList.value.firstOrNull { it.variant == variant }
        if (info?.isInstalled != true) {
            operationMessage = "${variantLabel(variant)}尚未安装，请先安装或配置仓库"
            return
        }
        val previous = runtimeState.value
        if (previous.variant == variant) return
        viewModelScope.launch {
            isOperating = true
            try {
                val expectsSameVariantRecovery = coreRepo.candidateState.value?.let { candidate ->
                    candidate.variant == variant && candidate.hasRecoveryPoint
                } == true
                runtimeRepo.updateVariant(variant)
                if (previous.status != ServiceStatus.Running) {
                    operationMessage = "已切换到${variantLabel(variant)}"
                    return@launch
                }
                runtimeRepo.restartService()
                var sawRestart = false
                val started = withTimeoutOrNull(45_000L) {
                    runtimeState.first { state ->
                        when (state.status) {
                            ServiceStatus.Starting, ServiceStatus.Stopping -> {
                                sawRestart = true
                                false
                            }
                            ServiceStatus.Running -> sawRestart && state.variant == variant
                            ServiceStatus.Error, ServiceStatus.Stopped -> sawRestart
                        }
                    }
                }?.status == ServiceStatus.Running
                if (started) {
                    operationMessage = "已切换并启动${variantLabel(variant)}"
                    return@launch
                }
                if (expectsSameVariantRecovery) {
                    val recovered = withTimeoutOrNull(40_000L) {
                        runtimeState.first { state ->
                            state.variant == variant && state.status == ServiceStatus.Running
                        }
                    } != null
                    if (recovered) {
                        operationMessage = "新版本启动失败，已恢复该核心的上一个可用版本"
                        return@launch
                    }
                }
                runtimeRepo.updateVariant(previous.variant)
                runtimeRepo.restartService()
                val restored = withTimeoutOrNull(45_000L) {
                    runtimeState.first { state ->
                        state.variant == previous.variant && state.status == ServiceStatus.Running
                    }
                } != null
                operationMessage = if (restored) {
                    "切换失败，已恢复并启动${variantLabel(previous.variant)}"
                } else {
                    "切换失败，已恢复原核心选择，请查看运行日志"
                }
            } finally {
                isOperating = false
                coreRepo.refreshCoreInfo()
            }
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
        cancelRevisionDetailsRequest()
        showGearMenu = null
        rollbackVariant = variant
        showRevisionHistory = true
        revisionHistoryError = null
        revisionSearchQuery = ""
        appliedRevisionSearchQuery = ""
        revisionPage = 1
        revisionHasNextPage = false
        selectedRevision = null
        selectedRevisionDetails = null
        revisionDetailsError = null
        loadRevisionPage(variant = variant, page = 1, query = "")
    }

    private fun loadRevisionPage(variant: ApiVariant, page: Int, query: String) {
        revisionHistoryGeneration += 1
        val generation = revisionHistoryGeneration
        revisionHistoryJob?.cancel()
        revisionHistoryJob = viewModelScope.launch {
            isLoadingHistory = true
            revisionHistoryError = null
            coreRepo.fetchRevisionHistory(
                variant = variant,
                page = page,
                pageSize = REVISION_PAGE_SIZE,
                query = query
            ).fold(
                onSuccess = { result ->
                    if (!isCurrentRevisionPageRequest(generation, variant)) return@fold
                    revisionHistory = result.revisions
                    revisionPage = result.page
                    revisionHasNextPage = result.hasNextPage
                    appliedRevisionSearchQuery = query
                },
                onFailure = { error ->
                    if (!isCurrentRevisionPageRequest(generation, variant)) return@fold
                    revisionHistory = emptyList()
                    revisionHasNextPage = false
                    revisionHistoryError = error.message ?: "无法读取提交记录"
                    promptProxyReselectIfNeeded(PendingProxyAction.LoadRollbackHistory(variant))
                }
            )
            if (!isCurrentRevisionPageRequest(generation, variant)) return@launch
            isLoadingHistory = false
            revisionHistoryJob = null
            refreshGithubAccountAfterApiUsage()
        }
    }

    private fun isCurrentRevisionPageRequest(
        generation: Long,
        variant: ApiVariant
    ): Boolean = generation == revisionHistoryGeneration &&
        showRevisionHistory && rollbackVariant == variant

    fun previousRevisionPage() {
        val variant = rollbackVariant ?: return
        if (isLoadingHistory || revisionPage <= 1) return
        loadRevisionPage(variant, revisionPage - 1, appliedRevisionSearchQuery)
    }

    fun nextRevisionPage() {
        val variant = rollbackVariant ?: return
        if (isLoadingHistory || !revisionHasNextPage) return
        loadRevisionPage(variant, revisionPage + 1, appliedRevisionSearchQuery)
    }

    fun dismissRollbackDialog() {
        revisionHistoryGeneration += 1
        revisionHistoryJob?.cancel()
        revisionHistoryJob = null
        isLoadingHistory = false
        cancelRevisionDetailsRequest()
        showRevisionHistory = false
        rollbackVariant = null
        revisionHistory = emptyList()
        revisionHistoryError = null
        revisionSearchQuery = ""
        appliedRevisionSearchQuery = ""
        revisionPage = 1
        revisionHasNextPage = false
        selectedRevision = null
        selectedRevisionDetails = null
        revisionDetailsError = null
        pendingRollbackRevision = null
    }

    fun updateRevisionSearchQuery(query: String) {
        revisionSearchQuery = query
    }

    fun submitRevisionSearch() {
        val variant = rollbackVariant ?: return
        loadRevisionPage(variant, page = 1, query = revisionSearchQuery.trim())
    }

    fun openRevisionDetails(revision: CoreRevision) {
        val variant = rollbackVariant ?: return
        cancelRevisionDetailsRequest()
        val generation = revisionDetailsGeneration
        val revisionSha = revision.commitSha
        selectedRevision = revision
        selectedRevisionDetails = null
        revisionDetailsError = null
        revisionDetailsJob = viewModelScope.launch {
            isLoadingRevisionDetails = true
            val result = coreRepo.fetchRevisionDetails(variant, revision)
            if (!isCurrentRevisionDetailsRequest(generation, variant, revisionSha)) return@launch
            result.fold(
                onSuccess = { selectedRevisionDetails = it },
                onFailure = { revisionDetailsError = it.message ?: "无法获取变动详情" }
            )
            if (isCurrentRevisionDetailsRequest(generation, variant, revisionSha)) {
                isLoadingRevisionDetails = false
                revisionDetailsJob = null
                refreshGithubAccountAfterApiUsage()
            }
        }
    }

    fun closeRevisionDetails() {
        cancelRevisionDetailsRequest()
        selectedRevision = null
        selectedRevisionDetails = null
        revisionDetailsError = null
        isLoadingRevisionDetails = false
    }

    private fun cancelRevisionDetailsRequest() {
        revisionDetailsGeneration += 1
        revisionDetailsJob?.cancel()
        revisionDetailsJob = null
        isLoadingRevisionDetails = false
    }

    private fun isCurrentRevisionDetailsRequest(
        generation: Long,
        variant: ApiVariant,
        revisionSha: String
    ): Boolean {
        return generation == revisionDetailsGeneration &&
            showRevisionHistory &&
            rollbackVariant == variant &&
            selectedRevision?.commitSha == revisionSha
    }

    fun requestRollback(revision: CoreRevision) {
        pendingRollbackRevision = revision
    }

    fun cancelRollbackRequest() {
        pendingRollbackRevision = null
    }

    fun confirmRollback() {
        val variant = rollbackVariant ?: return
        val revision = pendingRollbackRevision ?: return
        pendingRollbackRevision = null
        requireProxyAndRun(PendingProxyAction.Rollback(variant, revision))
    }

    private fun doRollbackTo(variant: ApiVariant, revision: CoreRevision) {
        cancelRevisionDetailsRequest()
        showRevisionHistory = false
        val versionLabel = revision.version.trim().takeIf { it.isNotBlank() }
            ?.let { "v$it" }
            ?: "提交 ${revision.shortSha}"
        viewModelScope.launch {
            performCoreMutation(
                variant = variant,
                actionMessage = "正在回退到 $versionLabel...",
                successMessage = "已回退到 $versionLabel",
                stopTimeoutMessage = "${variantLabel(variant)} 回退前停止服务超时，请稍后重试",
                failurePrefix = "回退失败",
                pendingAction = PendingProxyAction.Rollback(variant, revision),
                applyBlock = { coreRepo.rollbackCore(variant, revision) }
            )
        }
    }

    fun openGithubTokenDialog() {
        showGithubTokenDialog = true
    }

    fun dismissGithubTokenDialog() {
        showGithubTokenDialog = false
    }

    fun refreshGithubAccount() {
        val generation = beginGithubAccountOperation()
        githubAccountJob = viewModelScope.launch {
            githubAccountService.refresh()
            if (generation == githubAccountGeneration) githubAccountJob = null
        }
    }

    fun validateAndSaveGithubToken(input: String) {
        val token = input.trim()
        val generation = beginGithubAccountOperation()
        githubAccountJob = viewModelScope.launch {
            if (token.isBlank()) {
                settingsRepo.setGithubToken("")
                githubAccountService.refresh("")
                if (generation != githubAccountGeneration) return@launch
                operationMessage = "已使用匿名 GitHub 额度"
                showGithubTokenDialog = false
                githubAccountJob = null
                return@launch
            }
            val result = githubAccountService.refresh(token)
            if (generation != githubAccountGeneration) return@launch
            when (result.tokenValid) {
                true -> {
                    val saveError = runCatching { settingsRepo.setGithubToken(token) }.exceptionOrNull()
                    if (saveError != null) {
                        githubAccountService.refresh()
                        if (generation != githubAccountGeneration) return@launch
                        operationMessage = saveError.message ?: "GitHub Token 安全保存失败"
                        githubAccountJob = null
                        return@launch
                    }
                    githubAccountService.refresh()
                    if (generation != githubAccountGeneration) return@launch
                    operationMessage = "GitHub Token 验证成功${result.login?.let { "：$it" }.orEmpty()}"
                    showGithubTokenDialog = false
                }
                false, null -> {
                    val errorMessage = if (result.tokenValid == false) {
                        result.error ?: "GitHub Token 无效，未保存"
                    } else {
                        result.error ?: "暂时无法验证 Token，未保存"
                    }
                    githubAccountService.refresh()
                    if (generation != githubAccountGeneration) return@launch
                    operationMessage = errorMessage
                }
            }
            if (generation == githubAccountGeneration) githubAccountJob = null
        }
    }

    fun clearGithubToken() {
        val generation = beginGithubAccountOperation()
        settingsRepo.setGithubToken("")
        githubAccountJob = viewModelScope.launch {
            githubAccountService.refresh("")
            if (generation == githubAccountGeneration) githubAccountJob = null
        }
        operationMessage = "已清空 GitHub Token，当前使用匿名额度"
        showGithubTokenDialog = false
    }

    private fun beginGithubAccountOperation(): Long {
        githubAccountGeneration += 1
        githubAccountJob?.cancel()
        githubAccountJob = null
        return githubAccountGeneration
    }

    private fun refreshGithubAccountAfterApiUsage() {
        if (githubAccountJob?.isActive == true) return
        refreshGithubAccount()
    }

    companion object {
        private const val REVISION_PAGE_SIZE = 15
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

    fun dismissDependencyRequiredPrompt() {
        showDependencyRequiredPrompt = false
    }

    fun openDependencyRepairDialog() {
        if (pendingDependencyRepair.value == null) return
        showDependencyRequiredPrompt = false
        showDependencyRepairDialog = true
    }

    fun dismissDependencyRepairDialog() {
        showDependencyRepairDialog = false
    }

    fun repairPendingDependenciesOnline() {
        requireProxyAndRun(PendingProxyAction.RepairDependenciesOnline)
    }

    private fun doRepairPendingDependenciesOnline() {
        performPendingDependencyRepair(
            progressMessage = "正在在线修复运行时依赖...",
            repairBlock = coreRepo::repairPendingDependenciesOnline
        )
    }

    fun repairPendingDependenciesFromArchive(archiveUri: String) {
        performPendingDependencyRepair(
            progressMessage = "正在导入并校验运行时依赖...",
            repairBlock = { operationId ->
                coreRepo.repairPendingDependenciesFromArchive(operationId, archiveUri)
            }
        )
    }

    private fun performPendingDependencyRepair(
        progressMessage: String,
        repairBlock: suspend (Long) -> Result<Unit>
    ) {
        val request = pendingDependencyRepair.value ?: return
        showDependencyRequiredPrompt = false
        showDependencyRepairDialog = false
        viewModelScope.launch {
            isOperating = true
            operationMessage = progressMessage
            repairBlock(request.operationId).fold(
                onSuccess = {
                    val latestRequest = pendingDependencyRepair.value
                        ?.takeIf { it.operationId == request.operationId }
                        ?: request
                    operationMessage = "依赖校验通过，正在继续${latestRequest.actionLabel}..."
                    applyRepairedPendingCore(latestRequest)
                },
                onFailure = { error ->
                    operationMessage = "依赖修复失败：${error.message ?: "未知错误"}"
                    showDependencyRepairDialog = pendingDependencyRepair.value != null
                }
            )
            isOperating = false
        }
    }

    private suspend fun applyRepairedPendingCore(request: CoreDependencyRepairRequest) {
        coreRepo.applyPendingCoreMutation(request.operationId).fold(
            onSuccess = {
                val continuation = runtimeRepo.continueAfterDependencyRepair(request)
                if (continuation != null) {
                    operationMessage =
                        "${variantLabel(request.variant)}依赖已修复，$continuation"
                    return@fold
                }
                val restartPlan = decideCoreApplyPlan(runtimeState.value, request.variant)
                val restartResult = if (restartPlan.shouldRestartServiceAfterApply) {
                    restartRuntimeAfterCoreMutation(request.variant)
                } else {
                    PostApplyRestartResult.None
                }
                val success = "${variantLabel(request.variant)}${request.actionLabel}成功"
                operationMessage = when (restartResult) {
                    PostApplyRestartResult.Restarting -> "$success，服务正在重启以应用变更"
                    PostApplyRestartResult.StopTimeout -> "$success，但服务自动重启前停止超时，请稍后手动重启服务"
                    PostApplyRestartResult.None -> success
                }
            },
            onFailure = { error ->
                operationMessage = "${request.actionLabel}失败：${error.message ?: "未知错误"}"
            }
        )
    }

    fun discardPendingCoreMutation() {
        val request = pendingDependencyRepair.value ?: return
        showDependencyRequiredPrompt = false
        showDependencyRepairDialog = false
        viewModelScope.launch {
            coreRepo.discardPendingCoreMutation(request.operationId).fold(
                onSuccess = {
                    operationMessage = "已取消${variantLabel(request.variant)}${request.actionLabel}，原核心保持不变"
                },
                onFailure = {
                    operationMessage = "取消失败：${it.message ?: "任务状态已变化"}"
                }
            )
        }
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
            onFailure = { error ->
                if (error is CoreDependencyRepairRequiredException) {
                    operationMessage = "${variantLabel(variant)}${error.request.actionLabel}已暂停，等待修复依赖"
                    showDependencyRequiredPrompt = true
                } else {
                    operationMessage = "$failurePrefix: ${error.message}"
                    promptProxyReselectIfNeeded(pendingAction)
                }
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
            is PendingProxyAction.Rollback -> doRollbackTo(action.variant, action.revision)
            PendingProxyAction.RepairDependenciesOnline -> doRepairPendingDependenciesOnline()
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
