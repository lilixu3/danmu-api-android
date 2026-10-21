package com.example.danmuapiapp.ui.screen.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CorePullRequest
import com.example.danmuapiapp.domain.model.CorePullRequestFilter
import com.example.danmuapiapp.domain.model.CorePullRequestFilePage
import com.example.danmuapiapp.domain.model.CorePullRequestInclusion
import com.example.danmuapiapp.domain.model.CorePullRequestPage
import com.example.danmuapiapp.domain.model.canApplyToCurrentCore
import com.example.danmuapiapp.domain.repository.CoreRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.domain.repository.SettingsRepository
import com.example.danmuapiapp.ui.common.cancelTrackedJobs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

/**
 * PR 实验室门面 ViewModel：只做依赖装配、状态转发与跨控制器编排。
 *
 * 组合构建、原子应用与启动恢复流水线在 [PullRequestLabBuildController]；本类保留
 * 列表分页与富化缓存、搜索筛选、选择队列与详情面板状态。
 */
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
    var listContentGeneration by mutableStateOf(0L)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var activateAfterInstall by mutableStateOf(true)
        private set

    private var pageLoadJob: Job? = null
    private var detailsJob: Job? = null
    private var pageGeneration = 0L
    private var detailsGeneration = 0L
    private var knownLocallyMergedPullRequests = locallyMergedPullRequestNumbers()
    private var refreshAfterLocalMergeChange = false
    private val pageCache = linkedMapOf<PullRequestPageCacheKey, CachedPullRequestPage>()
    private val pageEnrichmentJobs = mutableMapOf<Int, Job>()
    private val scheduledPullRequestNumbers = mutableSetOf<Int>()
    private val firstContributionLookupAuthors = linkedSetOf<String>()
    private var inclusionLookupCount = 0
    private val pageEnrichmentSemaphore = Semaphore(PAGE_ENRICHMENT_CONCURRENCY)

    private val buildController = PullRequestLabBuildController(
        scope = viewModelScope,
        coreRepository = coreRepository,
        runtimeRepository = runtimeRepository,
        variant = variant,
        runtimeState = runtimeState,
        selectedPullRequests = { selectedPullRequests },
        clearSelection = { selectedPullRequests = emptyList() },
        activateAfterInstall = { activateAfterInstall },
        isPrivateRepository = { pageData?.isPrivateRepository == true },
        onBuildSettled = ::refreshAfterLocalMergeChangeIfIdle,
        postError = { errorMessage = it }
    )

    init {
        loadPage(1)
    }

    fun loadPage(page: Int, forceRefresh: Boolean = false) {
        if (isBuilding || isActivating) return
        val targetPage = page.coerceAtLeast(1)
        pageLoadJob?.cancel()
        cancelPageEnrichment()
        val generation = ++pageGeneration
        listContentGeneration = generation
        val cacheKey = currentPageCacheKey(targetPage)
        val cached = pageCache[cacheKey]
            ?.takeIf { !forceRefresh && System.currentTimeMillis() - it.cachedAtMs < PAGE_CACHE_TTL_MS }
        if (cached != null) {
            errorMessage = null
            isLoading = false
            pageLoadJob = null
            applyLoadedPage(cached.page)
            return
        }
        pageLoadJob = viewModelScope.launch {
            isLoading = true
            errorMessage = null
            coreRepository.fetchPullRequests(
                variant = variant,
                page = targetPage,
                pageSize = PAGE_SIZE,
                filter = selectedFilter,
                query = appliedSearchQuery,
                forceRefresh = forceRefresh
            ).fold(
                onSuccess = { loaded ->
                    if (generation != pageGeneration) return@fold
                    applyLoadedPage(loaded)
                    cachePage(cacheKey, loaded)
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

    fun refresh() = loadPage(pageData?.page ?: 1, forceRefresh = true)

    fun onPullRequestVisible(pullRequest: CorePullRequest) {
        if (isLoading || isBuilding || isActivating) return
        val currentPage = pageData ?: return
        val current = currentPage.items.firstOrNull { it.number == pullRequest.number } ?: return
        if (current.number in scheduledPullRequestNumbers) return
        val author = current.author.trim().lowercase()
        val needsFirstContributionLookup = current.firstContribution == null &&
            current.state.equals("open", ignoreCase = true) &&
            author.isNotBlank() && author != "unknown" &&
            (current.authorAssociation.isBlank() ||
                current.authorAssociation.equals("NONE", ignoreCase = true))
        val allowFirstContributionLookup = needsFirstContributionLookup &&
            author !in firstContributionLookupAuthors &&
            firstContributionLookupAuthors.size < MAX_FIRST_CONTRIBUTION_LOOKUPS_PER_PAGE
        val installedCommitSha = coreInfoList.value
            .firstOrNull { it.variant == variant && it.isReady }
            ?.sourceCommitSha
            .orEmpty()
        val needsInclusionLookup = current.currentCoreInclusion == CorePullRequestInclusion.Unknown &&
            !current.mergedAt.isNullOrBlank() &&
            installedCommitSha.isNotBlank() &&
            current.number !in locallyMergedPullRequestNumbers()
        val allowInclusionLookup = needsInclusionLookup &&
            inclusionLookupCount < MAX_INCLUSION_LOOKUPS_PER_PAGE
        if (!allowFirstContributionLookup && !allowInclusionLookup) return
        scheduledPullRequestNumbers += current.number
        if (allowFirstContributionLookup) firstContributionLookupAuthors += author
        if (allowInclusionLookup) inclusionLookupCount += 1

        val generation = pageGeneration
        pageEnrichmentJobs[current.number] = viewModelScope.launch {
            val result = pageEnrichmentSemaphore.withPermit {
                coreRepository.enrichPullRequestListItem(
                    variant = variant,
                    pullRequest = current,
                    allowFirstContributionLookup = allowFirstContributionLookup,
                    allowInclusionLookup = allowInclusionLookup
                )
            }
            if (generation != pageGeneration) return@launch
            result.onSuccess(::applyEnrichedPullRequest)
            pageEnrichmentJobs.remove(current.number)
        }
    }

    private fun applyLoadedPage(loaded: CorePullRequestPage) {
        pageData = loaded
        val refreshed = loaded.items.associateBy { it.number }
        selectedPullRequests = selectedPullRequests.map { selected ->
            refreshed[selected.number] ?: selected
        }
    }

    private fun applyEnrichedPullRequest(enriched: CorePullRequest) {
        val currentPage = pageData ?: return
        if (currentPage.items.none { it.number == enriched.number }) return
        val items = currentPage.items.map { item ->
            when {
                item.number == enriched.number -> enriched
                enriched.firstContribution != null &&
                    item.firstContribution == null &&
                    item.author.equals(enriched.author, ignoreCase = true) -> {
                    item.copy(firstContribution = enriched.firstContribution)
                }
                else -> item
            }
        }
        val updatedPage = currentPage.copy(items = items)
        pageData = updatedPage
        selectedPullRequests = selectedPullRequests.map { selected ->
            items.firstOrNull { it.number == selected.number } ?: selected
        }
        cachePage(currentPageCacheKey(updatedPage.page), updatedPage)
    }

    private fun cancelPageEnrichment() {
        pageEnrichmentJobs.cancelTrackedJobs()
        scheduledPullRequestNumbers.clear()
        firstContributionLookupAuthors.clear()
        inclusionLookupCount = 0
    }

    private fun currentPageCacheKey(page: Int): PullRequestPageCacheKey = PullRequestPageCacheKey(
        filter = selectedFilter,
        page = page,
        query = appliedSearchQuery,
        coreFingerprint = currentCoreFingerprint()
    )

    private fun currentCoreFingerprint(): String {
        val core = coreInfoList.value.firstOrNull { it.variant == variant && it.isReady }
        return buildString {
            append(core?.sourceCommitSha.orEmpty())
            append(':')
            append(core?.pullRequestNumbers.orEmpty().sorted().joinToString(","))
        }
    }

    private fun cachePage(key: PullRequestPageCacheKey, page: CorePullRequestPage) {
        pageCache[key] = CachedPullRequestPage(page, System.currentTimeMillis())
        while (pageCache.size > MAX_PAGE_CACHE_ENTRIES) {
            pageCache.remove(pageCache.keys.first())
        }
    }

    private fun invalidatePageCache() {
        pageCache.clear()
    }

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
        if (!pullRequest.canApplyToCurrentCore(alreadyIncluded)) return
        selectedPullRequests = if (selectedPullRequests.any { it.number == pullRequest.number }) {
            selectedPullRequests.filterNot { it.number == pullRequest.number }
        } else {
            selectedPullRequests + pullRequest
        }
        buildController.clearConflict()
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
                detailsResult.onSuccess(::applyEnrichedPullRequest)
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

    val isBuilding: Boolean
        get() = buildController.isBuilding
    val isActivating: Boolean
        get() = buildController.isActivating
    val statusMessage: String?
        get() = buildController.statusMessage
    val conflictFiles: List<String>
        get() = buildController.conflictFiles
    val conflictPullRequestNumber: Int?
        get() = buildController.conflictPullRequestNumber
    val showBuildConfirmation: Boolean
        get() = buildController.showBuildConfirmation

    fun requestBuild() = buildController.requestBuild()

    fun dismissBuildConfirmation() = buildController.dismissBuildConfirmation()

    fun confirmBuild() = buildController.confirmBuild()

    fun cancelBuild() = buildController.cancelBuild()

    fun consumeStatusMessage() = buildController.consumeStatusMessage()

    fun updateActivateAfterInstall(enabled: Boolean) {
        activateAfterInstall = enabled
    }

    fun dismissError() {
        errorMessage = null
    }

    private fun locallyMergedPullRequestNumbers(): Set<Int> = coreInfoList.value
        .firstOrNull { it.variant == variant && it.isReady }
        ?.pullRequestNumbers
        .orEmpty()
        .toSet()

    private fun refreshAfterLocalMergeChangeIfIdle() {
        if (!refreshAfterLocalMergeChange || isBuilding || isActivating) return
        refreshAfterLocalMergeChange = false
        invalidatePageCache()
        loadPage(1, forceRefresh = true)
    }

    companion object {
        const val PAGE_SIZE = 15
        const val PULL_REQUEST_FILE_PAGE_SIZE = 15
        private const val MAX_SEARCH_QUERY_LENGTH = 180
        private const val PAGE_CACHE_TTL_MS = 2L * 60L * 1000L
        private const val MAX_PAGE_CACHE_ENTRIES = 12
        private const val PAGE_ENRICHMENT_CONCURRENCY = 3
        private const val MAX_FIRST_CONTRIBUTION_LOOKUPS_PER_PAGE = 4
        private const val MAX_INCLUSION_LOOKUPS_PER_PAGE = 4
    }

    private data class PullRequestPageCacheKey(
        val filter: CorePullRequestFilter,
        val page: Int,
        val query: String,
        val coreFingerprint: String
    )

    private data class CachedPullRequestPage(
        val page: CorePullRequestPage,
        val cachedAtMs: Long
    )
}
