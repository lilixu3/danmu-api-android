package com.example.danmuapiapp.ui.screen.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreRevision
import com.example.danmuapiapp.domain.model.CoreRevisionDetails
import com.example.danmuapiapp.domain.model.CoreRevisionPage
import com.example.danmuapiapp.domain.repository.CoreRepository
import com.example.danmuapiapp.ui.common.cancelTrackedJobs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 核心回退对话框的状态与加载逻辑：分页与页缓存、按提交 sha 补齐版本号、变更详情、待回退提交暂存。
 *
 * 从 `CoreViewModel` 原样抽出，行为不变。远端仓库调用之外的副作用交回宿主：
 * 历史加载失败时由 [onHistoryLoadFailed] 决定是否重选加速线路，每次远端用量后由 [onApiUsage] 刷新账号额度。
 */
internal class CoreRevisionHistoryController(
    private val coreRepo: CoreRepository,
    private val scope: CoroutineScope,
    private val customSourceFingerprint: () -> String,
    private val onApiUsage: () -> Unit,
    private val onHistoryLoadFailed: (ApiVariant) -> Unit
) {

    var isVisible by mutableStateOf(false)
        private set
    var variant by mutableStateOf<ApiVariant?>(null)
        private set
    var revisions by mutableStateOf<List<CoreRevision>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var historyError by mutableStateOf<String?>(null)
        private set
    var loadingVersionShas by mutableStateOf<Set<String>>(emptySet())
        private set
    var searchQuery by mutableStateOf("")
        private set
    var appliedSearchQuery by mutableStateOf("")
        private set
    var page by mutableStateOf(1)
        private set
    var hasNextPage by mutableStateOf(false)
        private set
    var selectedRevision by mutableStateOf<CoreRevision?>(null)
        private set
    var selectedDetails by mutableStateOf<CoreRevisionDetails?>(null)
        private set
    var isLoadingDetails by mutableStateOf(false)
        private set
    var detailsError by mutableStateOf<String?>(null)
        private set
    var pendingRollback by mutableStateOf<CoreRevision?>(null)
        private set

    private var historyJob: Job? = null
    private var historyGeneration = 0L
    private var detailsJob: Job? = null
    private var detailsGeneration = 0L
    private val versionJobs = mutableMapOf<String, Job>()
    private val versionLookupSemaphore = Semaphore(REVISION_VERSION_LOOKUP_CONCURRENCY)
    private val pageCache = linkedMapOf<RevisionPageCacheKey, CachedRevisionPage>()
    private var activePageCacheKey: RevisionPageCacheKey? = null

    fun open(target: ApiVariant) {
        cancelDetailsRequest()
        variant = target
        isVisible = true
        historyError = null
        searchQuery = ""
        appliedSearchQuery = ""
        page = 1
        hasNextPage = false
        selectedRevision = null
        selectedDetails = null
        detailsError = null
        loadPage(variant = target, page = 1, query = "")
    }

    /** 回退执行前收起面板，保留变体，便于回退失败后继续查看历史。 */
    fun hideForRollback() {
        cancelDetailsRequest()
        isVisible = false
    }

    fun dismiss() {
        historyGeneration += 1
        historyJob?.cancel()
        historyJob = null
        cancelVersionRequests()
        activePageCacheKey = null
        isLoading = false
        loadingVersionShas = emptySet()
        cancelDetailsRequest()
        isVisible = false
        variant = null
        revisions = emptyList()
        historyError = null
        searchQuery = ""
        appliedSearchQuery = ""
        page = 1
        hasNextPage = false
        selectedRevision = null
        selectedDetails = null
        detailsError = null
        pendingRollback = null
    }

    fun previousPage() {
        val target = variant ?: return
        if (isLoading || page <= 1) return
        loadPage(target, page - 1, appliedSearchQuery)
    }

    fun nextPage() {
        val target = variant ?: return
        if (isLoading || !hasNextPage) return
        loadPage(target, page + 1, appliedSearchQuery)
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
    }

    fun submitSearch() {
        val target = variant ?: return
        loadPage(target, page = 1, query = searchQuery.trim())
    }

    /** 列表项可见时补齐版本号；已解析或正在请求的提交直接跳过。 */
    fun onRevisionVisible(revision: CoreRevision) {
        val target = variant ?: return
        val generation = historyGeneration
        val commitSha = revision.commitSha
        if (!isCurrentPageRequest(generation, target) ||
            revision.version.isNotBlank() ||
            commitSha in versionJobs
        ) {
            return
        }
        loadingVersionShas = loadingVersionShas + commitSha
        versionJobs[commitSha] = scope.launch {
            try {
                val version = versionLookupSemaphore.withPermit {
                    coreRepo.fetchRevisionVersion(target, revision).getOrNull()
                }
                if (isCurrentPageRequest(generation, target)) {
                    applyVersion(commitSha, version)
                }
            } finally {
                versionJobs.remove(commitSha)
                if (isCurrentPageRequest(generation, target)) {
                    loadingVersionShas = loadingVersionShas - commitSha
                }
            }
        }
    }

    fun openDetails(revision: CoreRevision) {
        val target = variant ?: return
        onRevisionVisible(revision)
        cancelDetailsRequest()
        val generation = detailsGeneration
        val revisionSha = revision.commitSha
        selectedRevision = revision
        selectedDetails = null
        detailsError = null
        detailsJob = scope.launch {
            isLoadingDetails = true
            val result = coreRepo.fetchRevisionDetails(target, revision)
            if (!isCurrentDetailsRequest(generation, target, revisionSha)) return@launch
            result.fold(
                onSuccess = { selectedDetails = it },
                onFailure = { detailsError = it.message ?: "无法获取变动详情" }
            )
            if (isCurrentDetailsRequest(generation, target, revisionSha)) {
                isLoadingDetails = false
                detailsJob = null
                onApiUsage()
            }
        }
    }

    fun closeDetails() {
        cancelDetailsRequest()
        selectedRevision = null
        selectedDetails = null
        detailsError = null
        isLoadingDetails = false
    }

    fun requestRollback(revision: CoreRevision) {
        onRevisionVisible(revision)
        pendingRollback = revision
    }

    fun cancelRollbackRequest() {
        pendingRollback = null
    }

    private fun loadPage(variant: ApiVariant, page: Int, query: String) {
        historyGeneration += 1
        val generation = historyGeneration
        historyJob?.cancel()
        cancelVersionRequests()
        val cacheKey = pageCacheKey(variant, page, query)
        val cached = pageCache[cacheKey]?.takeIf {
            System.currentTimeMillis() - it.cachedAtMs < REVISION_PAGE_CACHE_TTL_MS
        }
        if (cached != null) {
            historyError = null
            revisions = cached.page.revisions
            this.page = cached.page.page
            hasNextPage = cached.page.hasNextPage
            appliedSearchQuery = query
            isLoading = false
            historyJob = null
            activePageCacheKey = cacheKey
            return
        }
        historyJob = scope.launch {
            isLoading = true
            historyError = null
            val result = coreRepo.fetchRevisionHistory(
                variant = variant,
                page = page,
                pageSize = REVISION_PAGE_SIZE,
                query = query
            )
            result.fold(
                onSuccess = { result ->
                    if (!isCurrentPageRequest(generation, variant)) return@fold
                    revisions = result.revisions
                    this@CoreRevisionHistoryController.page = result.page
                    hasNextPage = result.hasNextPage
                    appliedSearchQuery = query
                    activePageCacheKey = cacheKey
                    cachePage(cacheKey, result)
                },
                onFailure = { error ->
                    if (!isCurrentPageRequest(generation, variant)) return@fold
                    revisions = emptyList()
                    hasNextPage = false
                    activePageCacheKey = null
                    historyError = error.message ?: "无法读取提交记录"
                    onHistoryLoadFailed(variant)
                }
            )
            if (!isCurrentPageRequest(generation, variant)) return@launch
            isLoading = false
            historyJob = null
            onApiUsage()
        }
    }

    private fun cancelVersionRequests() {
        versionJobs.cancelTrackedJobs()
        loadingVersionShas = emptySet()
    }

    private fun applyVersion(commitSha: String, version: String?) {
        val resolvedVersion = version.orEmpty()
        revisions = revisions.map { revision ->
            if (revision.commitSha == commitSha) revision.copy(version = resolvedVersion) else revision
        }
        selectedRevision = selectedRevision?.let { revision ->
            if (revision.commitSha == commitSha) revision.copy(version = resolvedVersion) else revision
        }
        selectedDetails = selectedDetails?.let { details ->
            if (details.revision.commitSha == commitSha) {
                details.copy(revision = details.revision.copy(version = resolvedVersion))
            } else {
                details
            }
        }
        pendingRollback = pendingRollback?.let { revision ->
            if (revision.commitSha == commitSha) revision.copy(version = resolvedVersion) else revision
        }
        loadingVersionShas = loadingVersionShas - commitSha
        activePageCacheKey?.let { key ->
            val cached = pageCache[key] ?: return@let
            pageCache[key] = cached.copy(
                page = cached.page.copy(revisions = revisions)
            )
        }
    }

    private fun pageCacheKey(
        variant: ApiVariant,
        page: Int,
        query: String
    ): RevisionPageCacheKey {
        val sourceFingerprint = if (variant == ApiVariant.Custom) {
            customSourceFingerprint()
        } else {
            variant.key
        }
        return RevisionPageCacheKey(
            variant = variant,
            page = page,
            query = query,
            sourceFingerprint = sourceFingerprint
        )
    }

    private fun cachePage(key: RevisionPageCacheKey, page: CoreRevisionPage) {
        pageCache[key] = CachedRevisionPage(page, System.currentTimeMillis())
        while (pageCache.size > MAX_REVISION_PAGE_CACHE_ENTRIES) {
            pageCache.remove(pageCache.keys.first())
        }
    }

    private fun isCurrentPageRequest(
        generation: Long,
        variant: ApiVariant
    ): Boolean = generation == historyGeneration &&
        isVisible && this.variant == variant

    private fun cancelDetailsRequest() {
        detailsGeneration += 1
        detailsJob?.cancel()
        detailsJob = null
        isLoadingDetails = false
    }

    private fun isCurrentDetailsRequest(
        generation: Long,
        variant: ApiVariant,
        revisionSha: String
    ): Boolean {
        return generation == detailsGeneration &&
            isVisible &&
            this.variant == variant &&
            selectedRevision?.commitSha == revisionSha
    }

    companion object {
        private const val REVISION_PAGE_SIZE = 15
        private const val REVISION_VERSION_LOOKUP_CONCURRENCY = 1
        private const val REVISION_PAGE_CACHE_TTL_MS = 2L * 60L * 1000L
        private const val MAX_REVISION_PAGE_CACHE_ENTRIES = 12
    }

    private data class RevisionPageCacheKey(
        val variant: ApiVariant,
        val page: Int,
        val query: String,
        val sourceFingerprint: String
    )

    private data class CachedRevisionPage(
        val page: CoreRevisionPage,
        val cachedAtMs: Long
    )
}
