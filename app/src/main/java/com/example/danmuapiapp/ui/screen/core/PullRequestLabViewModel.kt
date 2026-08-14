package com.example.danmuapiapp.ui.screen.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreDependencyRepairRequest
import com.example.danmuapiapp.domain.model.CorePullRequest
import com.example.danmuapiapp.domain.model.CorePullRequestFilter
import com.example.danmuapiapp.domain.model.CorePullRequestFilePage
import com.example.danmuapiapp.domain.model.CorePullRequestPage
import com.example.danmuapiapp.domain.model.CorePullRequestStatus
import com.example.danmuapiapp.domain.model.PullRequestMergeConflictException
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.model.effectiveStatus
import com.example.danmuapiapp.domain.repository.CoreRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class PullRequestLabViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val coreRepository: CoreRepository,
    private val runtimeRepository: RuntimeRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {
    val variant: ApiVariant = ApiVariant.entries.firstOrNull {
        it.key == savedStateHandle.get<String>("variant")
    } ?: ApiVariant.Stable

    val coreInfoList = coreRepository.coreInfoList
    val runtimeState = runtimeRepository.runtimeState
    val downloadProgress = coreRepository.downloadProgress
    val pendingDependencyRepair = coreRepository.pendingDependencyRepair
    val coreDisplayNames = settingsRepository.coreDisplayNames

    var pageData by mutableStateOf<CorePullRequestPage?>(null)
        private set
    var selectedFilter by mutableStateOf(CorePullRequestFilter.Open)
        private set
    var isSearchVisible by mutableStateOf(false)
        private set
    var searchQuery by mutableStateOf("")
        private set
    var appliedSearchQuery by mutableStateOf("")
        private set
    var selectedPullRequests by mutableStateOf<List<CorePullRequest>>(emptyList())
        private set
    var openedPullRequest by mutableStateOf<CorePullRequest?>(null)
        private set
    var pullRequestFilePage by mutableStateOf<CorePullRequestFilePage?>(null)
        private set
    var isLoadingPullRequestDetails by mutableStateOf(false)
        private set
    var pullRequestDetailsError by mutableStateOf<String?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isBuilding by mutableStateOf(false)
        private set
    var isActivating by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var statusMessage by mutableStateOf<String?>(null)
        private set
    var conflictFiles by mutableStateOf<List<String>>(emptyList())
        private set
    var conflictPullRequestNumber by mutableStateOf<Int?>(null)
        private set
    var showBuildConfirmation by mutableStateOf(false)
        private set
    var activateAfterInstall by mutableStateOf(true)
        private set
    var showDependencyRequiredPrompt by mutableStateOf(false)
        private set
    var showDependencyRepairDialog by mutableStateOf(false)
        private set

    private var pageLoadJob: Job? = null
    private var detailsJob: Job? = null
    private var buildJob: Job? = null
    private var pageGeneration = 0L
    private var detailsGeneration = 0L
    private var activateAfterDependencyRepair = false
    private var knownLocallyMergedPullRequests = locallyMergedPullRequestNumbers()
    private var refreshAfterLocalMergeChange = false

    init {
        loadPage(1)
        viewModelScope.launch {
            pendingDependencyRepair.collect { request ->
                if (request == null) {
                    showDependencyRequiredPrompt = false
                    showDependencyRepairDialog = false
                } else if (request.variant == variant &&
                    request.missingDependencies.isNotEmpty() &&
                    !showDependencyRepairDialog
                ) {
                    showDependencyRequiredPrompt = true
                }
            }
        }
    }

    fun loadPage(page: Int) {
        if (isBuilding || isActivating) return
        val targetPage = page.coerceAtLeast(1)
        pageLoadJob?.cancel()
        val generation = ++pageGeneration
        pageLoadJob = viewModelScope.launch {
            isLoading = true
            errorMessage = null
            coreRepository.fetchPullRequests(
                variant = variant,
                page = targetPage,
                pageSize = PAGE_SIZE,
                filter = selectedFilter,
                query = appliedSearchQuery
            ).fold(
                onSuccess = { loaded ->
                    if (generation != pageGeneration) return@fold
                    pageData = loaded
                    val refreshed = loaded.items.associateBy { it.number }
                    selectedPullRequests = selectedPullRequests.map { selected ->
                        refreshed[selected.number] ?: selected
                    }
                },
                onFailure = { error ->
                    if (generation == pageGeneration) {
                        errorMessage = error.message ?: "PR 列表加载失败"
                    }
                }
            )
            if (generation == pageGeneration) isLoading = false
        }
    }

    fun refresh() = loadPage(pageData?.page ?: 1)

    fun toggleSearch() {
        if (isBuilding || isActivating) return
        if (isSearchVisible) {
            closeSearch()
        } else {
            isSearchVisible = true
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query.take(MAX_SEARCH_QUERY_LENGTH)
    }

    fun submitSearch() {
        if (isBuilding || isActivating) return
        appliedSearchQuery = searchQuery.trim()
        pageData = null
        errorMessage = null
        loadPage(1)
    }

    fun clearSearch() {
        searchQuery = ""
        if (appliedSearchQuery.isBlank()) return
        appliedSearchQuery = ""
        pageData = null
        errorMessage = null
        loadPage(1)
    }

    private fun closeSearch() {
        isSearchVisible = false
        searchQuery = ""
        if (appliedSearchQuery.isBlank()) return
        appliedSearchQuery = ""
        pageData = null
        errorMessage = null
        loadPage(1)
    }

    fun selectFilter(filter: CorePullRequestFilter) {
        if (filter == selectedFilter || isBuilding || isActivating) return
        selectedFilter = filter
        pageData = null
        errorMessage = null
        loadPage(1)
    }

    fun toggleSelection(pullRequest: CorePullRequest) {
        if (isBuilding || isActivating) return
        val alreadyIncluded = pullRequest.number in locallyMergedPullRequestNumbers()
        if (pullRequest.effectiveStatus(alreadyIncluded) != CorePullRequestStatus.Open) return
        selectedPullRequests = if (selectedPullRequests.any { it.number == pullRequest.number }) {
            selectedPullRequests.filterNot { it.number == pullRequest.number }
        } else {
            selectedPullRequests + pullRequest
        }
        conflictFiles = emptyList()
        conflictPullRequestNumber = null
    }

    fun syncLocallyMergedPullRequests(numbers: Collection<Int>) {
        val normalized = numbers.filter { it > 0 }.toSet()
        if (normalized.isNotEmpty() && selectedPullRequests.isNotEmpty()) {
            selectedPullRequests = selectedPullRequests.filterNot { it.number in normalized }
        }
        if (normalized == knownLocallyMergedPullRequests) return
        knownLocallyMergedPullRequests = normalized
        refreshAfterLocalMergeChange = true
        refreshAfterLocalMergeChangeIfIdle()
    }

    fun moveSelection(index: Int, offset: Int) {
        val target = index + offset
        if (index !in selectedPullRequests.indices || target !in selectedPullRequests.indices) return
        selectedPullRequests = selectedPullRequests.toMutableList().apply {
            add(target, removeAt(index))
        }
    }

    fun openPullRequestDetails(pullRequest: CorePullRequest) {
        detailsJob?.cancel()
        val generation = ++detailsGeneration
        openedPullRequest = pullRequest
        pullRequestFilePage = null
        pullRequestDetailsError = null
        detailsJob = viewModelScope.launch {
            isLoadingPullRequestDetails = true
            val detailsDeferred = async {
                coreRepository.fetchPullRequestDetails(variant, pullRequest.number)
            }
            val filesDeferred = async {
                coreRepository.fetchPullRequestFiles(
                    variant = variant,
                    pullRequestNumber = pullRequest.number,
                    page = 1,
                    pageSize = PULL_REQUEST_FILE_PAGE_SIZE
                )
            }
            val detailsResult = detailsDeferred.await()
            val filesResult = filesDeferred.await()
            if (generation == detailsGeneration) {
                detailsResult.onSuccess { openedPullRequest = it }
                filesResult.fold(
                    onSuccess = { pullRequestFilePage = it },
                    onFailure = {
                        pullRequestDetailsError = it.message ?: "PR 文件变更加载失败"
                    }
                )
                if (detailsResult.isFailure && filesResult.isFailure) {
                    pullRequestDetailsError = detailsResult.exceptionOrNull()?.message
                        ?: filesResult.exceptionOrNull()?.message
                        ?: "PR 详情加载失败"
                }
                isLoadingPullRequestDetails = false
            }
        }
    }

    fun loadPullRequestFilePage(page: Int) {
        val pullRequest = openedPullRequest ?: return
        if (isLoadingPullRequestDetails) return
        val targetPage = page.coerceAtLeast(1)
        detailsJob?.cancel()
        val generation = ++detailsGeneration
        detailsJob = viewModelScope.launch {
            isLoadingPullRequestDetails = true
            pullRequestDetailsError = null
            coreRepository.fetchPullRequestFiles(
                variant = variant,
                pullRequestNumber = pullRequest.number,
                page = targetPage,
                pageSize = PULL_REQUEST_FILE_PAGE_SIZE
            ).fold(
                onSuccess = {
                    if (generation == detailsGeneration) pullRequestFilePage = it
                },
                onFailure = {
                    if (generation == detailsGeneration) {
                        pullRequestDetailsError = it.message ?: "PR 文件变更加载失败"
                    }
                }
            )
            if (generation == detailsGeneration) isLoadingPullRequestDetails = false
        }
    }

    fun closePullRequestDetails() {
        detailsJob?.cancel()
        detailsJob = null
        detailsGeneration += 1
        openedPullRequest = null
        pullRequestFilePage = null
        pullRequestDetailsError = null
        isLoadingPullRequestDetails = false
    }

    fun requestBuild() {
        if (selectedPullRequests.isEmpty() || isBuilding || isActivating) return
        if (pageData?.isPrivateRepository == true) {
            errorMessage = "为保证 Token 只发往 GitHub 官方 API，PR 实验室暂不克隆私有仓库"
            return
        }
        showBuildConfirmation = true
    }

    fun dismissBuildConfirmation() {
        showBuildConfirmation = false
    }

    fun updateActivateAfterInstall(enabled: Boolean) {
        activateAfterInstall = enabled
    }

    fun confirmBuild() {
        if (isBuilding || isActivating || selectedPullRequests.isEmpty()) return
        showBuildConfirmation = false
        errorMessage = null
        statusMessage = null
        conflictFiles = emptyList()
        conflictPullRequestNumber = null
        val shouldActivate = activateAfterInstall
        buildJob = viewModelScope.launch {
            isBuilding = true
            try {
                coreRepository.preparePullRequestStack(
                    variant = variant,
                    pullRequestNumbers = selectedPullRequests.map { it.number }
                ).fold(
                    onSuccess = { request ->
                        if (request.missingDependencies.isNotEmpty()) {
                            activateAfterDependencyRepair = shouldActivate
                            statusMessage = "PR 组合已生成，等待补齐运行时依赖"
                            showDependencyRequiredPrompt = true
                        } else {
                            activateAfterDependencyRepair = false
                            applyPreparedPullRequest(request, shouldActivate)
                        }
                    },
                    onFailure = { error ->
                        when (error) {
                            is PullRequestMergeConflictException -> {
                                conflictPullRequestNumber = error.pullRequestNumber
                                conflictFiles = error.conflictFiles
                                errorMessage = error.message
                            }
                            else -> errorMessage = error.message ?: "PR 组合构建失败"
                        }
                    }
                )
            } finally {
                isBuilding = false
                refreshAfterLocalMergeChangeIfIdle()
            }
        }
    }

    fun cancelBuild() {
        if (isActivating) return
        buildJob?.cancel()
        buildJob = null
        isBuilding = false
        refreshAfterLocalMergeChangeIfIdle()
        statusMessage = "已取消 PR 组合构建，当前核心未变更"
    }

    fun consumeStatusMessage() {
        statusMessage = null
    }

    fun dismissError() {
        errorMessage = null
    }

    fun dismissDependencyRequiredPrompt() {
        showDependencyRequiredPrompt = false
    }

    fun openDependencyRepairDialog() {
        if (pendingDependencyRepair.value?.variant != variant) return
        showDependencyRequiredPrompt = false
        showDependencyRepairDialog = true
    }

    fun dismissDependencyRepairDialog() {
        showDependencyRepairDialog = false
    }

    fun repairPendingDependenciesOnline() {
        repairPendingDependencies(coreRepository::repairPendingDependenciesOnline)
    }

    fun repairPendingDependenciesFromArchive(archiveUri: String) {
        repairPendingDependencies { operationId ->
            coreRepository.repairPendingDependenciesFromArchive(operationId, archiveUri)
        }
    }

    private fun repairPendingDependencies(
        repair: suspend (Long) -> Result<Unit>
    ) {
        val request = pendingDependencyRepair.value?.takeIf { it.variant == variant } ?: return
        showDependencyRequiredPrompt = false
        showDependencyRepairDialog = false
        buildJob = viewModelScope.launch {
            isBuilding = true
            repair(request.operationId).fold(
                onSuccess = {
                    applyPreparedPullRequest(request, activateAfterDependencyRepair)
                },
                onFailure = {
                    errorMessage = it.message ?: "依赖修复失败"
                    showDependencyRepairDialog = pendingDependencyRepair.value != null
                }
            )
            isBuilding = false
            refreshAfterLocalMergeChangeIfIdle()
        }
    }

    fun discardPendingCoreMutation() {
        val request = pendingDependencyRepair.value?.takeIf { it.variant == variant } ?: return
        showDependencyRequiredPrompt = false
        showDependencyRepairDialog = false
        viewModelScope.launch {
            coreRepository.discardPendingCoreMutation(request.operationId).fold(
                onSuccess = { statusMessage = "已取消 PR 组合，原核心保持不变" },
                onFailure = { errorMessage = it.message ?: "取消失败" }
            )
        }
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
        try {
            val applyPlan = decidePullRequestApplyPlan(previous, variant, shouldActivate)
            stoppedForApply = if (applyPlan.shouldAwaitStopped) {
                statusMessage = "PR 组合已准备完成，正在安全停止服务"
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
                    errorMessage = "停止服务超时，未替换当前核心，请稍后重试"
                    return@withContext
                }
                applyPlan.shouldStartTargetAfterApply
            } else {
                false
            }

            statusMessage = "正在原子应用 PR 组合"
            val applyResult = coreRepository.applyPendingCoreMutation(request.operationId)
            val applyError = applyResult.exceptionOrNull()
            if (applyError != null) {
                errorMessage = applyError.message ?: "应用 PR 组合失败"
                restoreInterruptedService(previous, stoppedForApply, wasActive)
                return@withContext
            }
            finishInstallation(
                shouldActivate = shouldActivate,
                previous = previous,
                stoppedForApply = stoppedForApply
            )
        } catch (error: Exception) {
            errorMessage = error.message ?: "应用 PR 组合时发生异常"
            restoreInterruptedService(previous, stoppedForApply, wasActive)
        } finally {
            isActivating = false
            refreshAfterLocalMergeChangeIfIdle()
        }
    }

    private fun locallyMergedPullRequestNumbers(): Set<Int> = coreInfoList.value
        .firstOrNull { it.variant == variant && it.isReady }
        ?.pullRequestNumbers
        .orEmpty()
        .toSet()

    private fun refreshAfterLocalMergeChangeIfIdle() {
        if (!refreshAfterLocalMergeChange || isBuilding || isActivating) return
        refreshAfterLocalMergeChange = false
        loadPage(1)
    }

    private fun restoreInterruptedService(
        previous: RuntimeState,
        stoppedForApply: Boolean,
        wasActive: Boolean
    ) {
        if (stoppedForApply && wasActive) {
            runtimeRepository.updateVariant(previous.variant)
            runtimeRepository.startService()
            statusMessage = "PR 组合未应用，正在恢复原服务"
        }
    }

    private suspend fun finishInstallation(
        shouldActivate: Boolean,
        previous: RuntimeState,
        stoppedForApply: Boolean
    ) {
        coreRepository.refreshCoreInfo()
        selectedPullRequests = emptyList()
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
        viewModelScope.launch {
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

    companion object {
        const val PAGE_SIZE = 15
        const val PULL_REQUEST_FILE_PAGE_SIZE = 15
        private const val MAX_SEARCH_QUERY_LENGTH = 180
    }
}
