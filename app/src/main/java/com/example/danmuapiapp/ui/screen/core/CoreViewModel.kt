package com.example.danmuapiapp.ui.screen.core

import com.example.danmuapiapp.ui.common.CoreUpdateChoiceController
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.data.repository.CoreDisplayInfoCache
import com.example.danmuapiapp.data.service.GithubAccountService
import com.example.danmuapiapp.data.service.GithubProxySpeedTester
import com.example.danmuapiapp.data.service.GithubProxyService
import com.example.danmuapiapp.domain.model.*
import com.example.danmuapiapp.domain.repository.CoreRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.domain.repository.SettingsRepository
import com.example.danmuapiapp.ui.common.RuntimeRestartEvidence
import com.example.danmuapiapp.ui.common.ProxyPickerController
import com.example.danmuapiapp.ui.common.awaitCoreRestart
import com.example.danmuapiapp.ui.common.continueAfterDependencyRepair
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class CoreViewModel @Inject constructor(
    private val coreRepo: CoreRepository,
    private val runtimeRepo: RuntimeRepository,
    private val settingsRepo: SettingsRepository,
    private val githubProxyService: GithubProxyService,
    private val githubProxySpeedTester: GithubProxySpeedTester,
    private val githubAccountService: GithubAccountService,
    private val coreDisplayCache: CoreDisplayInfoCache
) : ViewModel() {

    val coreInfoList = coreRepo.coreInfoList
    val isCoreInfoLoading = coreRepo.isCoreInfoLoading
    val downloadProgress = coreRepo.downloadProgress
    val pendingDependencyRepair = coreRepo.pendingDependencyRepair
    val runtimeState = runtimeRepo.runtimeState
    val coreDisplayNames = settingsRepo.coreDisplayNames
    val coreBranchSelections = settingsRepo.coreBranchSelections
    val customCoreSource = settingsRepo.customCoreSource
    val customRepo = settingsRepo.customRepo
    val customRepoBranch = settingsRepo.customRepoBranch
    val githubAccountStatus = githubAccountService.status
    val githubApiQuota = githubProxyService.apiQuota
    val proxyOptions = githubProxyService.proxyOptions()
    val selectedGithubProxyId = githubProxyService.selectedProxyId

    private val initialCoreDisplayInfo = coreDisplayCache.read()

    fun cachedCoreDisplayInfo(variant: ApiVariant): CoreInfo? = initialCoreDisplayInfo
        ?.takeIf { it.scope == coreDisplayCache.scopeFor() }
        ?.installedInfo(variant)

    var workspaceRefreshError by mutableStateOf<String?>(null)
        private set
    var isRefreshingWorkspace by mutableStateOf(false)
        private set
    var isManualWorkspaceRefresh by mutableStateOf(false)
        private set
    private var workspaceRefreshJob: Job? = null
    private var workspaceRefreshKey: String? = null
    private var workspaceGeneration = 0L
    private var lastWorkspaceQuotaRefresh = 0L
    private val workspaceHeadCache = mutableStateMapOf<String, Pair<Long, CoreBranchSnapshot>>().apply {
        coreDisplayCache.readRemoteSnapshots().forEach { put(it.key, it.checkedAt to it.snapshot()) }
    }

    fun workspaceEntryFor(variant: ApiVariant): Pair<Long, CoreBranchSnapshot>? {
        val repo = resolveCoreVariantRepo(variant, customRepo.value)
        val branch = resolveCoreVariantBranch(variant, customRepo.value, customRepoBranch.value,
            coreBranchSelections.value).orEmpty()
        return workspaceHeadCache["${variant.key}:$repo:$branch"]
    }

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
    var branchDialogVariant by mutableStateOf<ApiVariant?>(null)
        private set
    var branchCatalog by mutableStateOf<CoreBranchCatalog?>(null)
        private set
    var isLoadingBranches by mutableStateOf(false)
        private set
    var branchLoadError by mutableStateOf<String?>(null)
        private set
    var isCheckingUpdate by mutableStateOf(false)
        private set
    var showUpdateDialog by mutableStateOf(false)
        private set
    var updateDialogVariant by mutableStateOf<ApiVariant?>(null)
        private set
    var updateDialogInfo by mutableStateOf<CoreInfo?>(null)
        private set
    var showUpdateDetails by mutableStateOf(false)
        private set
    var updateDetailsVariant by mutableStateOf<ApiVariant?>(null)
        private set
    var updateComparison by mutableStateOf<CoreUpdateComparison?>(null)
        private set
    var isLoadingUpdateComparison by mutableStateOf(false)
        private set
    var updateComparisonError by mutableStateOf<String?>(null)
        private set
    var showGearMenu by mutableStateOf<ApiVariant?>(null)
        private set
    var showGithubTokenDialog by mutableStateOf(false)
        private set

    val showRevisionHistory: Boolean
        get() = revisionHistoryController.isVisible
    val rollbackVariant: ApiVariant?
        get() = revisionHistoryController.variant
    val revisionHistory: List<CoreRevision>
        get() = revisionHistoryController.revisions
    val isLoadingHistory: Boolean
        get() = revisionHistoryController.isLoading
    val revisionHistoryError: String?
        get() = revisionHistoryController.historyError
    val revisionVersionLoadingShas: Set<String>
        get() = revisionHistoryController.loadingVersionShas
    val revisionSearchQuery: String
        get() = revisionHistoryController.searchQuery
    val appliedRevisionSearchQuery: String
        get() = revisionHistoryController.appliedSearchQuery
    val revisionPage: Int
        get() = revisionHistoryController.page
    val revisionHasNextPage: Boolean
        get() = revisionHistoryController.hasNextPage
    val selectedRevisionDetails: CoreRevisionDetails?
        get() = revisionHistoryController.selectedDetails
    val selectedRevision: CoreRevision?
        get() = revisionHistoryController.selectedRevision
    val isLoadingRevisionDetails: Boolean
        get() = revisionHistoryController.isLoadingDetails
    val revisionDetailsError: String?
        get() = revisionHistoryController.detailsError
    val pendingRollbackRevision: CoreRevision?
        get() = revisionHistoryController.pendingRollback

    val showProxyPickerDialog: Boolean
        get() = proxyPickerController.uiState.isVisible
    val proxySelectedId: String
        get() = proxyPickerController.uiState.selectedId
    val proxyTestingIds: Set<String>
        get() = proxyPickerController.uiState.testingIds
    val proxyLatencyMap: Map<String, Long>
        get() = proxyPickerController.uiState.latencyMap

    private val proxyPickerController = ProxyPickerController(
        githubProxyService = githubProxyService,
        githubProxySpeedTester = githubProxySpeedTester,
        scope = viewModelScope,
        proxyOptionsProvider = { proxyOptions }
    )
    private val revisionHistoryController = CoreRevisionHistoryController(
        coreRepo = coreRepo,
        scope = viewModelScope,
        customSourceFingerprint = {
            settingsRepo.customCoreSource.value.let { "${it.repo}:${it.branch}" }
        },
        onApiUsage = ::refreshGithubAccountAfterApiUsage,
        onHistoryLoadFailed = { variant ->
            promptProxyReselectIfNeeded(PendingProxyAction.LoadRollbackHistory(variant))
        }
    )

    private var pendingProxyAction: PendingProxyAction? = null
    private var updateComparisonJob: Job? = null
    private var updateComparisonGeneration = 0L
    private var githubAccountJob: Job? = null
    private var githubAccountGeneration = 0L
    private var branchLoadJob: Job? = null
    private var branchLoadGeneration = 0L

    init {
        observeRuntimeDrivenCoreRefresh()
        observePendingDependencyRepair()
        observeCoreInstallationChanges()
        if (githubAccountService.shouldRefresh() && !githubAccountStatus.value.isLoading) {
            refreshGithubAccount()
        }
        // 启动时只补齐缺失信息；已有远端信息的时效检查仍在进入核心页时进行。
        if (workspaceEntryFor(runtimeState.value.variant) == null) {
            refreshWorkspace(runtimeState.value.variant)
        }
    }

    private fun observeCoreInstallationChanges() {
        viewModelScope.launch {
            var previous: List<CoreInfo>? = null
            var previousScope: String? = null
            combine(coreInfoList, isCoreInfoLoading, runtimeState.map { it.runMode }.distinctUntilChanged()) { info, loading, mode ->
                Triple(info, loading, coreDisplayCache.scopeFor(mode))
            }.collect { (info, loading, scope) ->
                if (loading) return@collect
                if (previousScope != scope) previous = null
                val changed = changedCoreInstallations(previous, info)
                previous = info
                previousScope = scope
                changed.forEach { variant ->
                    workspaceHeadCache.keys.filter { it.startsWith("${variant.key}:") }.forEach(workspaceHeadCache::remove)
                    coreDisplayCache.removeRemoteSnapshots(variant)
                    refreshWorkspace(variant, force = true, afterCoreMutation = true)
                }
            }
        }
    }

    private fun observePendingDependencyRepair() {
        viewModelScope.launch {
            pendingDependencyRepair.collect { request ->
                if (request == null || request.missingDependencies.isEmpty()) {
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
                .drop(1)
                .collect {
                    coreRepo.refreshCoreInfo()
                }
        }
    }

    private sealed interface PendingProxyAction {
        data class Install(val variant: ApiVariant) : PendingProxyAction
        data class CheckUpdate(val variant: ApiVariant) : PendingProxyAction
        data class LoadUpdateDetails(val variant: ApiVariant) : PendingProxyAction
        data class DoUpdate(val variant: ApiVariant) : PendingProxyAction
        data class Reinstall(val variant: ApiVariant) : PendingProxyAction
        data class SwitchBranch(val variant: ApiVariant, val branch: String) : PendingProxyAction
        data class LoadRollbackHistory(val variant: ApiVariant) : PendingProxyAction
        data class Rollback(val variant: ApiVariant, val revision: CoreRevision) : PendingProxyAction
        data object RepairDependenciesOnline : PendingProxyAction
    }

    private enum class PostApplyRestartResult {
        None,
        Restarting,
        StopTimeout
    }

    fun refreshWorkspace(variant: ApiVariant, force: Boolean = false, afterCoreMutation: Boolean = false) {
        if (isOperating && !afterCoreMutation) return
        if (githubApiQuota.blocked() != null) {
            if (force && !afterCoreMutation) githubApiQuota.allowManualOperation()
            return
        }
        val repo = resolveCoreVariantRepo(variant, customRepo.value)
        val branch = resolveCoreVariantBranch(
            variant, customRepo.value, customRepoBranch.value, coreBranchSelections.value
        ).orEmpty()
        val key = "${variant.key}:$repo:$branch"
        if (!force && workspaceRefreshKey == key && workspaceRefreshJob?.isActive == true) return
        val updateSnapshot = coreWorkspaceUpdateSnapshot(
            coreInfoList.value.firstOrNull { it.variant == variant }, repo, branch
        )
        if (updateSnapshot != null && updateSnapshot.first > (workspaceHeadCache[key]?.first ?: 0L)) {
            workspaceHeadCache[key] = updateSnapshot
            coreDisplayCache.saveRemoteSnapshot(variant, branch, updateSnapshot.second, updateSnapshot.first)
        }
        val cached = workspaceHeadCache[key]
        val generation = ++workspaceGeneration
        workspaceRefreshJob?.cancel()
        workspaceRefreshError = null
        workspaceRefreshKey = null
        if (!force && (repo.isBlank() || !shouldRefreshCoreWorkspace(
                hasRemoteInfo = cached?.second?.commit?.sha?.isNotBlank() == true,
                lastCheckedAtMillis = cached?.first ?: 0L,
                nowMillis = System.currentTimeMillis()
            ))) {
            isRefreshingWorkspace = false
            isManualWorkspaceRefresh = false
            workspaceRefreshJob = null
            return
        }
        workspaceRefreshKey = key
        isRefreshingWorkspace = true
        isManualWorkspaceRefresh = force && !afterCoreMutation
        workspaceRefreshJob = viewModelScope.launch(
            com.example.danmuapiapp.data.service.GithubQuotaRequestContext.element(!force || afterCoreMutation)
        ) {
            try {
                // 本地状态由仓库独立读取；远端请求不用排在 Root 文件扫描后面。
                if (force && !afterCoreMutation) coreRepo.refreshCoreInfo()
                if (repo.isNotBlank()) {
                    coreRepo.fetchBranchSnapshot(variant).fold(
                        onSuccess = { snapshot ->
                            if (generation == workspaceGeneration) {
                                val checkedAt = System.currentTimeMillis()
                                workspaceHeadCache[key] = checkedAt to snapshot
                                coreDisplayCache.saveRemoteSnapshot(variant, branch, snapshot, checkedAt)
                            }
                        },
                        onFailure = { error ->
                            if (error is CancellationException) throw error
                            if (generation == workspaceGeneration) {
                                workspaceRefreshError = if (error is com.example.danmuapiapp.data.service.GithubQuotaExhaustedException) null
                                    else error.message ?: "分支信息读取失败"
                            }
                        }
                    )
                }
                if (generation == workspaceGeneration && !showGithubTokenDialog &&
                    !githubAccountStatus.value.isLoading && (force ||
                        System.currentTimeMillis() - lastWorkspaceQuotaRefresh >= 60_000L)) {
                    lastWorkspaceQuotaRefresh = System.currentTimeMillis()
                    refreshGithubAccount()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == workspaceGeneration) {
                    workspaceRefreshError = error.message ?: "核心信息刷新失败"
                }
            } finally {
                if (generation == workspaceGeneration) {
                    isRefreshingWorkspace = false
                    isManualWorkspaceRefresh = false
                    workspaceRefreshJob = null
                    workspaceRefreshKey = null
                }
            }
        }
    }

    fun updateVariant(variant: ApiVariant) {
        if (isOperating) return
        if (runtimeState.value.variant == variant) return
        viewModelScope.launch {
            isOperating = true
            try {
                val info = coreInfoList.value.firstOrNull { it.variant == variant }
                val ready = withContext(Dispatchers.IO) {
                    coreRepo.isCoreReady(variant)
                } && info?.sourceMismatch != true
                if (!ready) {
                    operationMessage = if (info?.sourceMismatch == true) {
                        "${variantLabel(variant)}来源与设置不一致，请先重新下载核心"
                    } else {
                        "${variantLabel(variant)}尚未安装，请先安装或配置仓库"
                    }
                    return@launch
                }

                val previous = runtimeState.value
                if (previous.variant == variant) return@launch
                val expectsSameVariantRecovery = coreRepo.candidateState.value?.let { candidate ->
                    candidate.variant == variant && candidate.hasRecoveryPoint
                } == true
                val restartSnapshot = RuntimeRestartEvidence.snapshot(previous)
                runtimeRepo.updateVariant(variant)
                if (previous.status != ServiceStatus.Running) {
                    operationMessage = "已切换到${variantLabel(variant)}"
                    return@launch
                }
                runtimeRepo.restartService()
                val restartResult = runtimeState.awaitCoreRestart(
                    targetVariant = variant,
                    beforeRestart = restartSnapshot,
                    timeoutMs = 45_000L
                )
                if (restartResult.status == ServiceStatus.Running) {
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
                if (RuntimeRestartEvidence.isConfirmedRunning(
                        state = runtimeState.value,
                        targetVariant = variant,
                        beforeRestart = restartSnapshot,
                        sawProgress = restartResult.sawProgress
                    )
                ) {
                    operationMessage = "已切换并启动${variantLabel(variant)}"
                    return@launch
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
            try {
                coreRepo.deleteCore(variant).fold(
                    onSuccess = { operationMessage = "$label 已删除" },
                    onFailure = { operationMessage = "删除失败: ${it.message}" }
                )
            } finally {
                isOperating = false
            }
        }
    }

    fun checkUpdate(variant: ApiVariant) {
        requireProxyAndRun(PendingProxyAction.CheckUpdate(variant))
    }

    private fun doCheckUpdate(variant: ApiVariant) {
        viewModelScope.launch {
            isCheckingUpdate = true
            try {
                settingsRepo.setIgnoredUpdateVersion(variant, null)
                coreRepo.checkAndMarkUpdate(variant)
                if (coreInfoList.value.find { it.variant == variant }?.updateCheckError != null &&
                    !githubApiQuota.allowManualOperation()) return@launch
                openUpdatePrompt(variant)
            } finally {
                isCheckingUpdate = false
            }
        }
    }

    fun openUpdatePrompt(variant: ApiVariant) {
        updateDialogVariant = variant
        updateDialogInfo = coreInfoList.value.find { it.variant == variant }
        showUpdateDialog = true
    }

    fun openUpdateDetails(variant: ApiVariant) {
        requireProxyAndRun(PendingProxyAction.LoadUpdateDetails(variant))
    }

    private fun openUpdateDetailsNow(variant: ApiVariant) {
        showUpdateDialog = false
        updateDetailsVariant = variant
        updateComparison = null
        updateComparisonError = null
        showUpdateDetails = true
        loadUpdateComparison(variant)
    }

    fun retryUpdateComparison() {
        updateDetailsVariant?.let(::loadUpdateComparison)
    }

    private fun loadUpdateComparison(variant: ApiVariant) {
        updateComparisonGeneration += 1
        val generation = updateComparisonGeneration
        updateComparisonJob?.cancel()
        updateComparison = null
        updateComparisonError = null
        isLoadingUpdateComparison = true
        updateComparisonJob = viewModelScope.launch {
            coreRepo.fetchUpdateComparison(variant).fold(
                onSuccess = { comparison ->
                    if (generation == updateComparisonGeneration && showUpdateDetails) {
                        updateComparison = comparison
                    }
                },
                onFailure = { error ->
                    if (generation == updateComparisonGeneration && showUpdateDetails) {
                        updateComparisonError = error.message ?: "无法获取核心变更详情"
                        promptProxyReselectIfNeeded(PendingProxyAction.LoadUpdateDetails(variant))
                    }
                }
            )
            if (generation == updateComparisonGeneration && showUpdateDetails) {
                isLoadingUpdateComparison = false
                updateComparisonJob = null
                refreshGithubAccountAfterApiUsage()
            }
        }
    }

    fun dismissUpdateDetails() {
        updateComparisonGeneration += 1
        updateComparisonJob?.cancel()
        updateComparisonJob = null
        showUpdateDetails = false
        updateDetailsVariant = null
        updateComparison = null
        updateComparisonError = null
        isLoadingUpdateComparison = false
    }

    fun doUpdate(variant: ApiVariant) {
        requireProxyAndRun(PendingProxyAction.DoUpdate(variant))
    }

    val coreUpdateChoice = CoreUpdateChoiceController()

    private fun doUpdateCore(variant: ApiVariant) {
        if (isOperating || isCheckingUpdate) return
        val label = variantLabel(variant)
        showUpdateDialog = false
        dismissUpdateDetails()
        viewModelScope.launch {
            isCheckingUpdate = true
            try {
                val request = coreUpdateChoice.prepare(coreRepo, variant).getOrElse {
                    if (it !is com.example.danmuapiapp.data.service.GithubQuotaExhaustedException) {
                        operationMessage = "无法准备更新：${it.message}"
                    }
                    return@launch
                } ?: return@launch
                performCoreMutation(
                    variant = variant,
                    actionMessage = "正在更新 $label...",
                    successMessage = "$label 更新成功",
                    stopTimeoutMessage = "$label 更新前停止服务超时，请稍后重试",
                    failurePrefix = "更新失败",
                    pendingAction = PendingProxyAction.DoUpdate(variant),
                    applyBlock = { coreRepo.updateCore(request) }
                )
            } finally {
                isCheckingUpdate = false
            }
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
        revisionHistoryController.open(variant)
    }

    fun onRevisionVisible(revision: CoreRevision) = revisionHistoryController.onRevisionVisible(revision)

    fun previousRevisionPage() = revisionHistoryController.previousPage()

    fun nextRevisionPage() = revisionHistoryController.nextPage()

    fun dismissRollbackDialog() = revisionHistoryController.dismiss()

    fun updateRevisionSearchQuery(query: String) =
        revisionHistoryController.updateSearchQuery(query)

    fun submitRevisionSearch() = revisionHistoryController.submitSearch()

    fun openRevisionDetails(revision: CoreRevision) = revisionHistoryController.openDetails(revision)

    fun closeRevisionDetails() = revisionHistoryController.closeDetails()

    fun requestRollback(revision: CoreRevision) = revisionHistoryController.requestRollback(revision)

    fun cancelRollbackRequest() = revisionHistoryController.cancelRollbackRequest()

    fun confirmRollback() {
        val variant = revisionHistoryController.variant ?: return
        val revision = revisionHistoryController.pendingRollback ?: return
        revisionHistoryController.cancelRollbackRequest()
        requireProxyAndRun(PendingProxyAction.Rollback(variant, revision))
    }

    private fun doRollbackTo(variant: ApiVariant, revision: CoreRevision) {
        revisionHistoryController.hideForRollback()
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
        lastWorkspaceQuotaRefresh = System.currentTimeMillis()
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

    fun openVariantSettingsDialog(variant: ApiVariant) {
        showGearMenu = null
        showVariantSettingsDialog = variant
    }

    fun dismissVariantSettingsDialog() {
        showVariantSettingsDialog = null
    }

    fun openBranchDialog(variant: ApiVariant) {
        if (isOperating) return
        if (coreInfoList.value.firstOrNull { it.variant == variant }?.isInstalled != true) {
            operationMessage = "请先安装${variantLabel(variant)}"
            return
        }
        if (variant == ApiVariant.Custom && settingsRepo.customCoreSource.value.repo.isBlank()) {
            operationMessage = "请先配置自定义核心仓库"
            return
        }
        branchDialogVariant = variant
        branchCatalog = null
        loadBranches(variant)
    }

    fun retryLoadBranches() {
        branchDialogVariant?.let(::loadBranches)
    }

    fun dismissBranchDialog() {
        branchLoadGeneration += 1
        branchLoadJob?.cancel()
        branchLoadJob = null
        branchDialogVariant = null
        branchCatalog = null
        branchLoadError = null
        isLoadingBranches = false
    }

    private fun loadBranches(variant: ApiVariant) {
        branchLoadGeneration += 1
        val generation = branchLoadGeneration
        branchLoadJob?.cancel()
        isLoadingBranches = true
        branchLoadError = null
        branchLoadJob = viewModelScope.launch {
            coreRepo.fetchBranches(variant).fold(
                onSuccess = { catalog ->
                    if (generation != branchLoadGeneration) return@fold
                    branchCatalog = catalog
                },
                onFailure = { error ->
                    if (generation != branchLoadGeneration) return@fold
                    branchLoadError = error.message ?: "无法读取仓库分支"
                }
            )
            if (generation == branchLoadGeneration) {
                isLoadingBranches = false
                branchLoadJob = null
            }
        }
    }

    fun switchBranch(branch: String) {
        val variant = branchDialogVariant ?: return
        val normalized = normalizeGithubBranch(branch)
        if (normalized.isBlank()) return
        val current = settingsRepo.coreBranchSelections.value.resolve(variant)
        dismissBranchDialog()
        if (normalized.equals(current, ignoreCase = true)) {
            operationMessage = "${variantLabel(variant)}当前已使用 $normalized 分支"
            return
        }
        requireProxyAndRun(PendingProxyAction.SwitchBranch(variant, normalized))
    }

    private fun doSwitchBranch(variant: ApiVariant, branch: String) {
        val label = variantLabel(variant)
        viewModelScope.launch {
            performCoreMutation(
                variant = variant,
                actionMessage = "正在将 $label 切换到 $branch 分支...",
                successMessage = "$label 已切换到 $branch 分支",
                stopTimeoutMessage = "$label 切换分支前停止服务超时，请稍后重试",
                failurePrefix = "切换分支失败",
                pendingAction = PendingProxyAction.SwitchBranch(variant, branch),
                applyBlock = { coreRepo.switchCoreBranch(variant, branch) }
            )
        }
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
            val branchText = resolved.branch.ifBlank { "默认分支" }
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
            try {
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
            } finally {
                isOperating = false
            }
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
        if (!githubApiQuota.allowManualOperation()) return
        isOperating = true
        try {
            operationMessage = actionMessage
            val applyPlan = decideCoreApplyPlan(runtimeState.value, variant)

            if (applyPlan.shouldStopServiceBeforeApply) {
                operationMessage = "正在停止服务以安全应用 ${variantLabel(variant)} 变更..."
                runtimeRepo.stopService()
                val stopped = waitForRuntimeStoppedBeforeCoreMutation()
                if (!stopped) {
                    operationMessage = stopTimeoutMessage
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
                    if (error is com.example.danmuapiapp.data.service.GithubQuotaExhaustedException) {
                        operationMessage = null
                    } else if (error is CoreDependencyRepairRequiredException) {
                        operationMessage = "${variantLabel(variant)}${error.request.actionLabel}已暂停，等待修复依赖"
                        showDependencyRequiredPrompt = true
                    } else {
                        operationMessage = "$failurePrefix: ${error.message}"
                        promptProxyReselectIfNeeded(pendingAction)
                    }
                }
            )
        } finally {
            isOperating = false
        }
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
        proxyPickerController.dismiss()
        pendingProxyAction = null
    }

    fun selectProxy(proxyId: String) {
        proxyPickerController.select(proxyId)
    }

    fun retestProxySpeed() {
        proxyPickerController.retest()
    }

    fun confirmProxySelection() {
        proxyPickerController.confirm {
            val action = pendingProxyAction
            pendingProxyAction = null
            if (action != null) {
                executePendingProxyAction(action)
            } else {
                val selectedId = proxyPickerController.uiState.selectedId
                operationMessage = "已切换 GitHub 线路：${proxyOptions.firstOrNull { it.id == selectedId }?.name ?: "未知"}"
            }
        }
    }

    private fun requireProxyAndRun(action: PendingProxyAction) {
        if (action != PendingProxyAction.RepairDependenciesOnline && !githubApiQuota.allowManualOperation()) return
        if (githubProxyService.hasUserSelectedProxy()) {
            executePendingProxyAction(action)
            return
        }
        pendingProxyAction = action
        openProxyPickerDialog(withTip = "首次使用请先选择 GitHub 线路")
    }

    private fun executePendingProxyAction(action: PendingProxyAction) {
        if (action != PendingProxyAction.RepairDependenciesOnline && !githubApiQuota.allowManualOperation()) return
        when (action) {
            is PendingProxyAction.Install -> doInstallCore(action.variant)
            is PendingProxyAction.CheckUpdate -> doCheckUpdate(action.variant)
            is PendingProxyAction.LoadUpdateDetails -> openUpdateDetailsNow(action.variant)
            is PendingProxyAction.DoUpdate -> doUpdateCore(action.variant)
            is PendingProxyAction.Reinstall -> doReinstallCore(action.variant)
            is PendingProxyAction.SwitchBranch -> doSwitchBranch(action.variant, action.branch)
            is PendingProxyAction.LoadRollbackHistory -> loadRollbackHistory(action.variant)
            is PendingProxyAction.Rollback -> doRollbackTo(action.variant, action.revision)
            PendingProxyAction.RepairDependenciesOnline -> doRepairPendingDependenciesOnline()
        }
    }

    private fun promptProxyReselectIfNeeded(retryAction: PendingProxyAction) {
        if (githubApiQuota.blocked() != null) return
        if (!githubProxyService.isUsingProxy()) return
        pendingProxyAction = retryAction
        openProxyPickerDialog(withTip = "当前加速线路不可用，请重新测速并选择")
    }

    private fun openProxyPickerDialog(withTip: String?) {
        proxyPickerController.open()
        withTip?.let { operationMessage = it }
    }

    private fun variantLabel(variant: ApiVariant): String {
        return coreDisplayNames.value.resolve(variant)
    }
}
