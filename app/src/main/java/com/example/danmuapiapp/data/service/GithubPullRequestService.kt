package com.example.danmuapiapp.data.service

import com.example.danmuapiapp.data.remote.github.GithubRemoteService
import com.example.danmuapiapp.domain.model.CorePullRequest
import com.example.danmuapiapp.domain.model.CorePullRequestFilter
import com.example.danmuapiapp.domain.model.CorePullRequestFilePage
import com.example.danmuapiapp.domain.model.CorePullRequestInclusion
import com.example.danmuapiapp.domain.model.CorePullRequestPage
import com.example.danmuapiapp.domain.model.CoreRevisionFileChange
import com.example.danmuapiapp.domain.model.PullRequestFirstContribution
import com.example.danmuapiapp.domain.model.matchesFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GithubPullRequestService @Inject constructor(
    private val githubRemoteService: GithubRemoteService
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val repositoryInfoCache = ConcurrentHashMap<String, GithubRepositoryInfo>()
    private val repositoryContributorCache = ConcurrentHashMap<String, Boolean>()
    private val repositoryContributorFailureCache = ConcurrentHashMap<String, Long>()
    private val currentCoreInclusionCache = ConcurrentHashMap<String, CorePullRequestInclusion>()
    private val currentCoreInclusionFailureCache = ConcurrentHashMap<String, Long>()
    private val pullRequestDetailsCache = ConcurrentHashMap<String, PullRequestDetailsCacheEntry>()
    private val listSourcePageCache = ConcurrentHashMap<PullRequestListSourceCacheKey, CachedPullRequestSourcePage>()
    private val searchSourcePageCache = ConcurrentHashMap<PullRequestSearchSourceCacheKey, CachedPullRequestSearchSourcePage>()
    private val contributorLookupLocks = ConcurrentHashMap<String, Mutex>()
    private val inclusionLookupLocks = ConcurrentHashMap<String, Mutex>()
    private val pullRequestDetailsLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun resolveDefaultBranch(repository: String): String {
        val repo = validateRepository(repository)
        return repositoryInfo(repo).defaultBranch
    }

    private suspend fun repositoryInfo(repository: String): GithubRepositoryInfo {
        repositoryInfoCache[repository]?.let { return it }
        val info = githubRemoteService.requestMappedCancellable(
            urls = githubRemoteService.apiUrlCandidates("repos/$repository"),
            headers = githubHeaders()
        ) { body ->
            val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
                ?: return@requestMappedCancellable null
            val defaultBranch = string(root, "default_branch").takeIf { it.isNotBlank() }
                ?: return@requestMappedCancellable null
            GithubRepositoryInfo(
                defaultBranch = defaultBranch,
                isPrivate = (root["private"] as? JsonPrimitive)?.booleanOrNull ?: false
            )
        } ?: throw IOException("无法读取仓库默认分支")
        repositoryInfoCache[repository] = info
        return info
    }

    suspend fun list(
        repository: String,
        baseBranch: String,
        page: Int,
        pageSize: Int,
        filter: CorePullRequestFilter,
        locallyMergedPullRequestNumbers: Collection<Int>,
        installedCommitSha: String = "",
        query: String = "",
        forceRefresh: Boolean = false
    ): CorePullRequestPage {
        val repo = validateRepository(repository)
        val repositoryInfo = repositoryInfo(repo)
        val branch = baseBranch.trim().takeIf { it.isNotBlank() }
            ?: throw IOException("PR 目标分支不能为空")
        if (forceRefresh) invalidateSourcePageCache(repo, branch)
        val safePage = page.coerceAtLeast(1)
        val safePageSize = pageSize.coerceIn(1, 100)
        val targetStartLong = (safePage - 1L) * safePageSize
        if (targetStartLong > Int.MAX_VALUE) throw IOException("PR 页码过大")
        val targetStart = targetStartLong.toInt()
        val targetRequired = (targetStartLong + safePageSize + 1L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        val locallyMergedNumbers = locallyMergedPullRequestNumbers
            .asSequence()
            .filter { it > 0 }
            .distinct()
            .toList()
        val locallyMergedSet = locallyMergedNumbers.toSet()
        val normalizedQuery = query.trim()

        if (normalizedQuery.isNotBlank()) {
            return search(
                repository = repo,
                repositoryInfo = repositoryInfo,
                baseBranch = branch,
                page = safePage,
                pageSize = safePageSize,
                filter = filter,
                locallyMergedNumbers = locallyMergedNumbers,
                locallyMergedSet = locallyMergedSet,
                installedCommitSha = installedCommitSha,
                query = normalizedQuery
            )
        }

        if (filter == CorePullRequestFilter.All) {
            val sourcePage = requestListPage(
                repository = repo,
                baseBranch = branch,
                state = "all",
                page = safePage,
                pageSize = safePageSize
            )
            return CorePullRequestPage(
                repository = repo,
                baseBranch = branch,
                isPrivateRepository = repositoryInfo.isPrivate,
                items = applyCachedListMetadata(
                    repository = repo,
                    pullRequests = sourcePage.items,
                    installedCommitSha = installedCommitSha,
                    locallyMergedNumbers = locallyMergedSet
                ),
                filter = filter,
                page = safePage,
                hasPreviousPage = safePage > 1,
                hasNextPage = sourcePage.hasNextPage
            )
        }

        val matching = mutableListOf<CorePullRequest>()
        if (filter == CorePullRequestFilter.Merged) {
            locallyMergedNumbers.forEach { number ->
                matching += localPullRequestPlaceholder(repo, branch, number)
            }
        }

        val sourceState = when (filter) {
            CorePullRequestFilter.Open -> "open"
            CorePullRequestFilter.Merged, CorePullRequestFilter.Closed -> "closed"
            CorePullRequestFilter.All -> "all"
        }
        var sourcePageNumber = 1
        var sourceHasNextPage = true
        while (matching.size < targetRequired && sourceHasNextPage) {
            currentCoroutineContext().ensureActive()
            val sourcePage = requestListPage(
                repository = repo,
                baseBranch = branch,
                state = sourceState,
                page = sourcePageNumber,
                pageSize = SOURCE_PAGE_SIZE
            )
            matching += sourcePage.items.filter { pullRequest ->
                pullRequest.number !in locallyMergedSet &&
                    pullRequest.matchesFilter(filter, locallyMerged = false)
            }
            sourceHasNextPage = sourcePage.hasNextPage
            sourcePageNumber += 1
        }

        return CorePullRequestPage(
            repository = repo,
            baseBranch = branch,
            isPrivateRepository = repositoryInfo.isPrivate,
            items = applyCachedListMetadata(
                repository = repo,
                pullRequests = matching.drop(targetStart).take(safePageSize),
                installedCommitSha = installedCommitSha,
                locallyMergedNumbers = locallyMergedSet
            ),
            filter = filter,
            page = safePage,
            hasPreviousPage = safePage > 1,
            hasNextPage = matching.size > targetStart + safePageSize
        )
    }

    private suspend fun search(
        repository: String,
        repositoryInfo: GithubRepositoryInfo,
        baseBranch: String,
        page: Int,
        pageSize: Int,
        filter: CorePullRequestFilter,
        locallyMergedNumbers: List<Int>,
        locallyMergedSet: Set<Int>,
        installedCommitSha: String,
        query: String
    ): CorePullRequestPage {
        val targetStartLong = (page - 1L) * pageSize
        if (targetStartLong > Int.MAX_VALUE) throw IOException("PR 页码过大")
        val targetStart = targetStartLong.toInt()
        val targetRequired = (targetStartLong + pageSize + 1L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        val plan = GithubPullRequestSearchQuery.plan(
            repository = repository,
            baseBranch = baseBranch,
            filter = filter,
            input = query
        )
        val matching = mutableListOf<CorePullRequest>()

        if (filter == CorePullRequestFilter.Merged) {
            locallyMergedNumbers.forEach { number ->
                currentCoroutineContext().ensureActive()
                val pullRequest = getOrPlaceholder(repository, baseBranch, number)
                if (GithubPullRequestSearchQuery.matchesLocal(pullRequest, query)) {
                    matching += pullRequest
                }
            }
        }

        var activeQuery = plan.primaryQuery
        var sourcePageNumber = 1
        var sourceHasNextPage = true
        var fallbackChecked = false
        while (matching.size < targetRequired && sourceHasNextPage) {
            currentCoroutineContext().ensureActive()
            val sourcePage = requestSearchPage(
                repository = repository,
                baseBranch = baseBranch,
                query = activeQuery,
                page = sourcePageNumber,
                pageSize = SOURCE_PAGE_SIZE
            )
            if (!fallbackChecked && sourcePageNumber == 1 && sourcePage.totalCount == 0) {
                fallbackChecked = true
                val fallback = plan.fallbackQuery
                if (fallback != null) {
                    activeQuery = fallback
                    continue
                }
            }
            matching += sourcePage.items.filter { pullRequest ->
                when (filter) {
                    CorePullRequestFilter.Open -> pullRequest.number !in locallyMergedSet
                    CorePullRequestFilter.Merged -> pullRequest.number !in locallyMergedSet
                    CorePullRequestFilter.Closed -> pullRequest.number !in locallyMergedSet
                    CorePullRequestFilter.All -> true
                }
            }
            sourceHasNextPage = sourcePage.hasNextPage
            sourcePageNumber += 1
        }

        return CorePullRequestPage(
            repository = repository,
            baseBranch = baseBranch,
            isPrivateRepository = repositoryInfo.isPrivate,
            items = applyCachedListMetadata(
                repository = repository,
                pullRequests = matching.drop(targetStart).take(pageSize),
                installedCommitSha = installedCommitSha,
                locallyMergedNumbers = locallyMergedSet
            ),
            filter = filter,
            page = page,
            hasPreviousPage = page > 1,
            hasNextPage = matching.size > targetStart + pageSize
        )
    }

    private suspend fun requestSearchPage(
        repository: String,
        baseBranch: String,
        query: String,
        page: Int,
        pageSize: Int
    ): GithubPullRequestSearchSourcePage {
        val cacheKey = PullRequestSearchSourceCacheKey(
            repository = repository.lowercase(),
            baseBranch = baseBranch.lowercase(),
            query = query,
            page = page,
            pageSize = pageSize
        )
        cachedSearchSourcePage(cacheKey)?.let { return it }
        val response = githubRemoteService.requestTextResponseCancellable(
            urls = githubRemoteService.apiUrlCandidates(
                "search/issues?q=${encode(query)}&sort=updated&order=desc" +
                    "&per_page=$pageSize&page=$page"
            ),
            headers = githubHeaders()
        ) ?: throw IOException("GitHub PR 搜索失败，请检查网络、Token 与搜索额度")
        val payload = GithubPullRequestPayloadParser.parseSearch(
            raw = response.body,
            repository = repository,
            baseBranch = baseBranch
        )
            ?: throw IOException("GitHub 返回了无法识别的 PR 搜索数据")
        return GithubPullRequestSearchSourcePage(
            items = payload.items,
            totalCount = payload.totalCount,
            hasNextPage = response.linkHeader?.contains("rel=\"next\"") == true ||
                (response.linkHeader == null && page * pageSize < payload.totalCount)
        ).also { cacheSearchSourcePage(cacheKey, it) }
    }

    private suspend fun requestListPage(
        repository: String,
        baseBranch: String,
        state: String,
        page: Int,
        pageSize: Int
    ): GithubPullRequestSourcePage {
        val cacheKey = PullRequestListSourceCacheKey(
            repository = repository.lowercase(),
            baseBranch = baseBranch.lowercase(),
            state = state.lowercase(),
            page = page,
            pageSize = pageSize
        )
        cachedListSourcePage(cacheKey)?.let { return it }
        val path = buildString {
            append("repos/")
            append(repository)
            append("/pulls?state=")
            append(state)
            append("&base=")
            append(encode(baseBranch))
            append("&sort=updated&direction=desc&per_page=")
            append(pageSize)
            append("&page=")
            append(page)
        }
        val response = githubRemoteService.requestTextResponseCancellable(
            urls = githubRemoteService.apiUrlCandidates(path),
            headers = githubHeaders()
        ) ?: throw IOException("无法读取 PR 列表，请检查网络、Token 与 API 额度")
        val items = GithubPullRequestPayloadParser.parseList(response.body)
            ?: throw IOException("GitHub 返回了无法识别的 PR 数据")
        return GithubPullRequestSourcePage(
            items = items,
            hasNextPage = response.linkHeader?.contains("rel=\"next\"") == true ||
                (response.linkHeader == null && items.size == pageSize)
        ).also { cacheListSourcePage(cacheKey, it) }
    }

    private fun localPullRequestPlaceholder(
        repository: String,
        baseBranch: String,
        number: Int
    ) = CorePullRequest(
        number = number,
        state = "open",
        title = "本地已并入 PR #$number",
        body = "GitHub 当前无法返回这个 PR 的摘要，但它已记录在当前核心的本地合并信息中。",
        author = "unknown",
        htmlUrl = "https://github.com/$repository/pull/$number",
        draft = false,
        updatedAt = "",
        headSha = "",
        headLabel = "",
        baseSha = "",
        baseRef = baseBranch,
        baseRepository = repository
    )

    suspend fun get(repository: String, pullRequestNumber: Int): CorePullRequest {
        val repo = validateRepository(repository)
        if (pullRequestNumber <= 0) throw IOException("PR 编号无效")
        val cacheKey = "${repo.lowercase()}:$pullRequestNumber"
        cachedPullRequestDetails(cacheKey)?.let { return it }
        return mutexFor(pullRequestDetailsLocks, cacheKey).withLock {
            cachedPullRequestDetails(cacheKey)?.let { return@withLock it }
            val body = githubRemoteService.requestTextCancellable(
                urls = githubRemoteService.apiUrlCandidates("repos/$repo/pulls/$pullRequestNumber"),
                headers = githubHeaders()
            ) ?: throw IOException("无法读取 PR #$pullRequestNumber，可能已关闭、删除或无权访问")
            val pullRequest = GithubPullRequestPayloadParser.parseOne(body)
                ?: throw IOException("GitHub 返回了无法识别的 PR #$pullRequestNumber 数据")
            resolveFirstContribution(repo, pullRequest).also { resolved ->
                if (pullRequestDetailsCache.size >= MAX_PULL_REQUEST_DETAILS_CACHE_ENTRIES) {
                    pullRequestDetailsCache.clear()
                }
                pullRequestDetailsCache[cacheKey] = PullRequestDetailsCacheEntry(
                    pullRequest = resolved,
                    cachedAtMs = System.currentTimeMillis()
                )
            }
        }
    }

    suspend fun getWithCurrentCoreInclusion(
        repository: String,
        pullRequestNumber: Int,
        installedCommitSha: String,
        locallyMergedPullRequestNumbers: Collection<Int>
    ): CorePullRequest {
        val repo = validateRepository(repository)
        val pullRequest = get(repo, pullRequestNumber)
        return resolveCurrentCoreInclusion(
            repository = repo,
            pullRequest = pullRequest,
            installedCommitSha = installedCommitSha,
            locallyMerged = pullRequestNumber in locallyMergedPullRequestNumbers
        )
    }

    suspend fun enrichListItem(
        repository: String,
        pullRequest: CorePullRequest,
        installedCommitSha: String,
        locallyMergedNumbers: Set<Int>,
        allowFirstContributionLookup: Boolean,
        allowInclusionLookup: Boolean
    ): CorePullRequest {
        val repo = validateRepository(repository)
        currentCoroutineContext().ensureActive()
        val withContribution = if (allowFirstContributionLookup) {
            resolveFirstContribution(repo, pullRequest)
        } else {
            applyCachedFirstContribution(repo, pullRequest)
        }
        if (!allowInclusionLookup) {
            return withContribution.copy(
                currentCoreInclusion = pullRequest.currentCoreInclusion
            )
        }
        val locallyMerged = pullRequest.number in locallyMergedNumbers
        val detailed = if (!locallyMerged &&
            installedCommitSha.isNotBlank() &&
            !withContribution.mergedAt.isNullOrBlank() &&
            withContribution.mergeCommitSha.isBlank()
        ) {
            try {
                get(repo, withContribution.number)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                withContribution
            }
        } else {
            withContribution
        }
        return resolveCurrentCoreInclusion(
            repository = repo,
            pullRequest = detailed,
            installedCommitSha = installedCommitSha,
            locallyMerged = locallyMerged
        )
    }

    private fun applyCachedListMetadata(
        repository: String,
        pullRequests: List<CorePullRequest>,
        installedCommitSha: String,
        locallyMergedNumbers: Set<Int>
    ): List<CorePullRequest> = pullRequests.map { pullRequest ->
        val withContribution = applyCachedFirstContribution(repository, pullRequest)
        val locallyMerged = pullRequest.number in locallyMergedNumbers
        val installed = installedCommitSha.trim().lowercase()
        val merged = withContribution.mergeCommitSha.trim().lowercase()
        val inclusion = when {
            locallyMerged -> CorePullRequestInclusion.LocalMerge
            withContribution.mergedAt.isNullOrBlank() -> CorePullRequestInclusion.NotIncluded
            installed == merged && COMMIT_SHA_PATTERN.matches(installed) -> {
                CorePullRequestInclusion.Included
            }
            COMMIT_SHA_PATTERN.matches(installed) && COMMIT_SHA_PATTERN.matches(merged) -> {
                currentCoreInclusionCache[inclusionCacheKey(repository, merged, installed)]
                    ?: CorePullRequestInclusion.Unknown
            }
            else -> CorePullRequestInclusion.Unknown
        }
        withContribution.copy(currentCoreInclusion = inclusion)
    }

    private suspend fun resolveCurrentCoreInclusion(
        repository: String,
        pullRequest: CorePullRequest,
        installedCommitSha: String,
        locallyMerged: Boolean
    ): CorePullRequest {
        val inclusion = when {
            locallyMerged -> CorePullRequestInclusion.LocalMerge
            pullRequest.mergedAt.isNullOrBlank() -> CorePullRequestInclusion.NotIncluded
            else -> resolveMergedPullRequestInclusion(
                repository = repository,
                installedCommitSha = installedCommitSha,
                mergeCommitSha = pullRequest.mergeCommitSha
            )
        }
        return pullRequest.copy(currentCoreInclusion = inclusion)
    }

    private suspend fun resolveMergedPullRequestInclusion(
        repository: String,
        installedCommitSha: String,
        mergeCommitSha: String
    ): CorePullRequestInclusion {
        val installed = installedCommitSha.trim().lowercase()
        val merged = mergeCommitSha.trim().lowercase()
        if (!COMMIT_SHA_PATTERN.matches(installed) || !COMMIT_SHA_PATTERN.matches(merged)) {
            return CorePullRequestInclusion.Unknown
        }
        if (installed == merged) return CorePullRequestInclusion.Included

        val cacheKey = inclusionCacheKey(repository, merged, installed)
        currentCoreInclusionCache[cacheKey]?.let { return it }
        if (isRecentFailure(currentCoreInclusionFailureCache[cacheKey])) {
            return CorePullRequestInclusion.Unknown
        }
        return mutexFor(inclusionLookupLocks, cacheKey).withLock {
            currentCoreInclusionCache[cacheKey]?.let { return@withLock it }
            if (isRecentFailure(currentCoreInclusionFailureCache[cacheKey])) {
                return@withLock CorePullRequestInclusion.Unknown
            }
            val compareStatus = githubRemoteService.requestMappedCancellable(
                urls = githubRemoteService.apiUrlCandidates(
                    "repos/$repository/compare/$merged...$installed?per_page=1&page=1"
                ),
                headers = githubHeaders()
            ) { body ->
                val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
                    ?: return@requestMappedCancellable null
                string(root, "status").takeIf { it.isNotBlank() }
            }
            val inclusion = PullRequestCurrentCorePolicy.fromCompareStatus(compareStatus)
            if (inclusion != CorePullRequestInclusion.Unknown) {
                if (currentCoreInclusionCache.size >= MAX_INCLUSION_CACHE_ENTRIES) {
                    currentCoreInclusionCache.clear()
                }
                currentCoreInclusionCache[cacheKey] = inclusion
                currentCoreInclusionFailureCache.remove(cacheKey)
            } else {
                currentCoreInclusionFailureCache[cacheKey] = System.currentTimeMillis()
            }
            inclusion
        }
    }

    private fun inclusionCacheKey(repository: String, merged: String, installed: String): String =
        "${repository.lowercase()}:$merged:$installed"

    suspend fun listFiles(
        repository: String,
        pullRequestNumber: Int,
        page: Int,
        pageSize: Int
    ): CorePullRequestFilePage {
        val repo = validateRepository(repository)
        if (pullRequestNumber <= 0) throw IOException("PR 编号无效")
        val safePage = page.coerceAtLeast(1)
        val safePageSize = pageSize.coerceIn(1, 100)
        val response = githubRemoteService.requestTextResponseCancellable(
            urls = githubRemoteService.apiUrlCandidates(
                "repos/$repo/pulls/$pullRequestNumber/files?per_page=$safePageSize&page=$safePage"
            ),
            headers = githubHeaders()
        ) ?: throw IOException("无法读取 PR #$pullRequestNumber 的文件变更")
        val files = GithubPullRequestPayloadParser.parseFiles(response.body)
            ?: throw IOException("GitHub 返回了无法识别的 PR 文件数据")
        return CorePullRequestFilePage(
            files = files,
            page = safePage,
            hasPreviousPage = safePage > 1,
            hasNextPage = response.linkHeader?.contains("rel=\"next\"") == true ||
                (response.linkHeader == null && files.size == safePageSize)
        )
    }

    private fun githubHeaders(): Map<String, String> = mapOf(
        "Accept" to "application/vnd.github+json",
        "User-Agent" to GithubRemoteService.UserAgent
    )

    private fun validateRepository(repository: String): String {
        val value = repository.trim().removeSuffix(".git").trim('/')
        if (!REPOSITORY_PATTERN.matches(value)) {
            throw IOException("GitHub 仓库地址无效")
        }
        return value
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private suspend fun resolveFirstContribution(
        repository: String,
        pullRequest: CorePullRequest
    ): CorePullRequest {
        if (!PullRequestFirstContributionPolicy.needsRepositoryLookup(pullRequest)) {
            return pullRequest
        }
        val cacheKey = "$repository:${pullRequest.author}".lowercase()
        val hasExistingContribution = repositoryContributorCache[cacheKey] ?: run {
            if (isRecentFailure(repositoryContributorFailureCache[cacheKey])) {
                null
            } else {
                mutexFor(contributorLookupLocks, cacheKey).withLock {
                    repositoryContributorCache[cacheKey] ?: run {
                        if (isRecentFailure(repositoryContributorFailureCache[cacheKey])) {
                            null
                        } else {
                            fetchHasExistingContribution(repository, pullRequest.author).also { result ->
                                if (result == null) {
                                    repositoryContributorFailureCache[cacheKey] = System.currentTimeMillis()
                                } else {
                                    repositoryContributorCache[cacheKey] = result
                                    repositoryContributorFailureCache.remove(cacheKey)
                                }
                            }
                        }
                    }
                }
            }
        }
        return PullRequestFirstContributionPolicy.applyRepositoryLookup(
            pullRequest = pullRequest,
            hasExistingContribution = hasExistingContribution
        )
    }

    private fun applyCachedFirstContribution(
        repository: String,
        pullRequest: CorePullRequest
    ): CorePullRequest {
        if (!PullRequestFirstContributionPolicy.needsRepositoryLookup(pullRequest)) {
            return pullRequest
        }
        return PullRequestFirstContributionPolicy.applyRepositoryLookup(
            pullRequest = pullRequest,
            hasExistingContribution = repositoryContributorCache[
                "$repository:${pullRequest.author}".lowercase()
            ]
        )
    }

    private suspend fun fetchHasExistingContribution(repository: String, author: String): Boolean? {
        return githubRemoteService.requestMappedCancellable(
            urls = githubRemoteService.apiUrlCandidates(
                "repos/$repository/commits?author=${encode(author)}&per_page=1"
            ),
            headers = githubHeaders()
        ) { body ->
            val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonArray
                ?: return@requestMappedCancellable null
            root.isNotEmpty()
        }
    }

    private fun cachedPullRequestDetails(cacheKey: String): CorePullRequest? {
        val cached = pullRequestDetailsCache[cacheKey] ?: return null
        if (System.currentTimeMillis() - cached.cachedAtMs < PULL_REQUEST_DETAILS_CACHE_TTL_MS) {
            return cached.pullRequest
        }
        pullRequestDetailsCache.remove(cacheKey, cached)
        return null
    }

    private fun cachedListSourcePage(cacheKey: PullRequestListSourceCacheKey): GithubPullRequestSourcePage? {
        val cached = listSourcePageCache[cacheKey] ?: return null
        if (System.currentTimeMillis() - cached.cachedAtMs < SOURCE_PAGE_CACHE_TTL_MS) {
            return cached.page
        }
        listSourcePageCache.remove(cacheKey, cached)
        return null
    }

    private fun cachedSearchSourcePage(cacheKey: PullRequestSearchSourceCacheKey): GithubPullRequestSearchSourcePage? {
        val cached = searchSourcePageCache[cacheKey] ?: return null
        if (System.currentTimeMillis() - cached.cachedAtMs < SOURCE_PAGE_CACHE_TTL_MS) {
            return cached.page
        }
        searchSourcePageCache.remove(cacheKey, cached)
        return null
    }

    private fun cacheListSourcePage(
        cacheKey: PullRequestListSourceCacheKey,
        page: GithubPullRequestSourcePage
    ) {
        if (listSourcePageCache.size >= MAX_SOURCE_PAGE_CACHE_ENTRIES) {
            listSourcePageCache.clear()
        }
        listSourcePageCache[cacheKey] = CachedPullRequestSourcePage(page, System.currentTimeMillis())
    }

    private fun cacheSearchSourcePage(
        cacheKey: PullRequestSearchSourceCacheKey,
        page: GithubPullRequestSearchSourcePage
    ) {
        if (searchSourcePageCache.size >= MAX_SOURCE_PAGE_CACHE_ENTRIES) {
            searchSourcePageCache.clear()
        }
        searchSourcePageCache[cacheKey] = CachedPullRequestSearchSourcePage(page, System.currentTimeMillis())
    }

    private fun invalidateSourcePageCache(repository: String, baseBranch: String) {
        val normalizedRepository = repository.lowercase()
        val normalizedBranch = baseBranch.lowercase()
        listSourcePageCache.keys.removeAll { key ->
            key.repository == normalizedRepository && key.baseBranch == normalizedBranch
        }
        searchSourcePageCache.keys.removeAll { key ->
            key.repository == normalizedRepository && key.baseBranch == normalizedBranch
        }
    }

    private fun isRecentFailure(failedAtMs: Long?): Boolean = failedAtMs != null &&
        System.currentTimeMillis() - failedAtMs < LOOKUP_FAILURE_TTL_MS

    private fun mutexFor(cache: ConcurrentHashMap<String, Mutex>, key: String): Mutex {
        cache[key]?.let { return it }
        if (cache.size >= MAX_LOOKUP_LOCKS) cache.clear()
        val candidate = Mutex()
        return cache.putIfAbsent(key, candidate) ?: candidate
    }

    private suspend fun getOrPlaceholder(
        repository: String,
        baseBranch: String,
        number: Int
    ): CorePullRequest = try {
        get(repository, number)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        localPullRequestPlaceholder(repository, baseBranch, number)
    }

    companion object {
        private const val SOURCE_PAGE_SIZE = 100
        private const val MAX_INCLUSION_CACHE_ENTRIES = 512
        private const val MAX_PULL_REQUEST_DETAILS_CACHE_ENTRIES = 256
        private const val MAX_SOURCE_PAGE_CACHE_ENTRIES = 16
        private const val MAX_LOOKUP_LOCKS = 512
        private const val LOOKUP_FAILURE_TTL_MS = 30_000L
        private const val PULL_REQUEST_DETAILS_CACHE_TTL_MS = 30_000L
        private const val SOURCE_PAGE_CACHE_TTL_MS = 2L * 60L * 1000L
        private val REPOSITORY_PATTERN = Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
        private val COMMIT_SHA_PATTERN = Regex("[0-9a-f]{7,40}")
    }
}

private data class PullRequestDetailsCacheEntry(
    val pullRequest: CorePullRequest,
    val cachedAtMs: Long
)

private data class PullRequestListSourceCacheKey(
    val repository: String,
    val baseBranch: String,
    val state: String,
    val page: Int,
    val pageSize: Int
)

private data class PullRequestSearchSourceCacheKey(
    val repository: String,
    val baseBranch: String,
    val query: String,
    val page: Int,
    val pageSize: Int
)

private data class CachedPullRequestSourcePage(
    val page: GithubPullRequestSourcePage,
    val cachedAtMs: Long
)

private data class CachedPullRequestSearchSourcePage(
    val page: GithubPullRequestSearchSourcePage,
    val cachedAtMs: Long
)

internal object PullRequestCurrentCorePolicy {
    fun fromCompareStatus(status: String?): CorePullRequestInclusion = when {
        status.equals("ahead", ignoreCase = true) ||
            status.equals("identical", ignoreCase = true) -> CorePullRequestInclusion.Included
        status.equals("behind", ignoreCase = true) ||
            status.equals("diverged", ignoreCase = true) -> CorePullRequestInclusion.NotIncluded
        else -> CorePullRequestInclusion.Unknown
    }
}

private data class GithubRepositoryInfo(
    val defaultBranch: String,
    val isPrivate: Boolean
)

internal object GithubPullRequestPayloadParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseList(raw: String): List<CorePullRequest>? {
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonArray
            ?: return null
        return root.mapNotNull { parseObject(it as? JsonObject ?: return@mapNotNull null) }
    }

    fun parseOne(raw: String): CorePullRequest? {
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject
            ?: return null
        return parseObject(root)
    }

    fun parseSearch(
        raw: String,
        repository: String,
        baseBranch: String
    ): GithubPullRequestSearchPayload? {
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject
            ?: return null
        val items = root["items"] as? JsonArray ?: return null
        return GithubPullRequestSearchPayload(
            totalCount = (root["total_count"] as? JsonPrimitive)?.intOrNull ?: 0,
            items = items.mapNotNull { element ->
                parseSearchObject(
                    root = element as? JsonObject ?: return@mapNotNull null,
                    repository = repository,
                    baseBranch = baseBranch
                )
            }
        )
    }

    fun parseFiles(raw: String): List<CoreRevisionFileChange>? {
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonArray
            ?: return null
        return root.mapNotNull { element ->
            val file = element as? JsonObject ?: return@mapNotNull null
            val path = string(file, "filename")
            if (path.isBlank()) return@mapNotNull null
            val status = string(file, "status").ifBlank { "modified" }
            val patch = string(file, "patch")
            CoreRevisionFileChange(
                path = path,
                previousPath = string(file, "previous_filename").ifBlank { null },
                status = status,
                additions = int(file, "additions"),
                deletions = int(file, "deletions"),
                changes = int(file, "changes"),
                lines = CorePatchParser.parse(patch),
                patchUnavailableReason = if (patch.isBlank()) {
                    if (status in setOf("added", "removed", "modified", "changed")) {
                        "GitHub 未返回文本补丁，文件可能是二进制或变动过大"
                    } else {
                        "此文件没有可显示的文本补丁"
                    }
                } else {
                    null
                }
            )
        }
    }

    private fun parseObject(root: JsonObject): CorePullRequest? {
        val number = (root["number"] as? JsonPrimitive)?.intOrNull ?: return null
        val head = root["head"] as? JsonObject ?: return null
        val base = root["base"] as? JsonObject ?: return null
        val user = root["user"] as? JsonObject
        val baseRepo = base["repo"] as? JsonObject
        val authorAssociation = string(root, "author_association")
        return CorePullRequest(
            number = number,
            state = string(root, "state").ifBlank { "open" },
            title = string(root, "title").ifBlank { "PR #$number" },
            body = string(root, "body"),
            author = string(user, "login").ifBlank { "unknown" },
            htmlUrl = string(root, "html_url"),
            draft = (root["draft"] as? JsonPrimitive)?.booleanOrNull ?: false,
            updatedAt = string(root, "updated_at"),
            headSha = string(head, "sha"),
            headLabel = string(head, "label").ifBlank { string(head, "ref") },
            baseSha = string(base, "sha"),
            baseRef = string(base, "ref"),
            baseRepository = string(baseRepo, "full_name"),
            authorAssociation = authorAssociation,
            firstContribution = parseFirstContribution(authorAssociation),
            mergedAt = string(root, "merged_at").ifBlank { null },
            mergeCommitSha = string(root, "merge_commit_sha"),
            closedAt = string(root, "closed_at").ifBlank { null },
            mergeable = (root["mergeable"] as? JsonPrimitive)?.booleanOrNull,
            additions = (root["additions"] as? JsonPrimitive)?.intOrNull,
            deletions = (root["deletions"] as? JsonPrimitive)?.intOrNull,
            changedFiles = (root["changed_files"] as? JsonPrimitive)?.intOrNull
        )
    }

    private fun parseSearchObject(
        root: JsonObject,
        repository: String,
        baseBranch: String
    ): CorePullRequest? {
        val number = (root["number"] as? JsonPrimitive)?.intOrNull ?: return null
        val pullRequest = root["pull_request"] as? JsonObject ?: return null
        val user = root["user"] as? JsonObject
        val authorAssociation = string(root, "author_association")
        return CorePullRequest(
            number = number,
            state = string(root, "state").ifBlank { "open" },
            title = string(root, "title").ifBlank { "PR #$number" },
            body = string(root, "body"),
            author = string(user, "login").ifBlank { "unknown" },
            htmlUrl = string(root, "html_url"),
            draft = (root["draft"] as? JsonPrimitive)?.booleanOrNull ?: false,
            updatedAt = string(root, "updated_at"),
            headSha = "",
            headLabel = "",
            baseSha = "",
            baseRef = baseBranch,
            baseRepository = repository,
            authorAssociation = authorAssociation,
            firstContribution = parseFirstContribution(authorAssociation),
            mergedAt = string(pullRequest, "merged_at").ifBlank { null },
            closedAt = string(root, "closed_at").ifBlank { null }
        )
    }

    private fun string(obj: JsonObject?, key: String): String =
        ((obj?.get(key) as? JsonPrimitive)?.contentOrNull ?: "").trim()

    private fun int(obj: JsonObject?, key: String): Int =
        (obj?.get(key) as? JsonPrimitive)?.intOrNull ?: 0

    private fun parseFirstContribution(raw: String): PullRequestFirstContribution? = when {
        raw.equals("FIRST_TIMER", ignoreCase = true) -> PullRequestFirstContribution.Github
        raw.equals("FIRST_TIME_CONTRIBUTOR", ignoreCase = true) -> {
            PullRequestFirstContribution.Repository
        }
        else -> null
    }
}

internal data class GithubPullRequestSearchPayload(
    val totalCount: Int,
    val items: List<CorePullRequest>
)

internal data class GithubPullRequestSearchPlan(
    val primaryQuery: String,
    val fallbackQuery: String?
)

internal object GithubPullRequestSearchQuery {
    private val loginPattern = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?")
    private val explicitAuthorPattern = Regex("(?i)^author:@?([A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?)$")
    private val numberPattern = Regex("#?([0-9]+)")
    private val lockedQualifierPattern = Regex(
        "(?i)(^|\\s)(?:(?:repo|base):(?:\\\"[^\\\"]*\\\"|\\S+)|" +
            "(?:is|state):(?:open|closed|merged|unmerged)|(?:is|type):(?:pr|issue))(?=\\s|$)"
    )

    fun plan(
        repository: String,
        baseBranch: String,
        filter: CorePullRequestFilter,
        input: String
    ): GithubPullRequestSearchPlan {
        val userQuery = stripLockedQualifiers(input)
        val explicitAuthor = explicitAuthorPattern.matchEntire(userQuery)?.groupValues?.get(1)
        val atAuthor = userQuery.takeIf { it.startsWith('@') }
            ?.drop(1)
            ?.takeIf(loginPattern::matches)
        val number = numberPattern.matchEntire(userQuery)?.groupValues?.get(1)
        val plainAuthor = userQuery.takeIf(loginPattern::matches)

        val primaryExpression = when {
            explicitAuthor != null -> "author:$explicitAuthor"
            atAuthor != null -> "author:$atAuthor"
            number != null -> "$number in:number"
            plainAuthor != null -> "author:$plainAuthor"
            else -> userQuery
        }
        val fallbackExpression = plainAuthor?.let { userQuery }
        return GithubPullRequestSearchPlan(
            primaryQuery = scope(repository, baseBranch, filter, primaryExpression),
            fallbackQuery = fallbackExpression?.let {
                scope(repository, baseBranch, filter, it)
            }
        )
    }

    fun matchesLocal(pullRequest: CorePullRequest, input: String): Boolean {
        val userQuery = stripLockedQualifiers(input)
        explicitAuthorPattern.matchEntire(userQuery)?.groupValues?.get(1)?.let { author ->
            return pullRequest.author.equals(author, ignoreCase = true)
        }
        if (userQuery.startsWith('@')) {
            val author = userQuery.drop(1)
            return loginPattern.matches(author) && pullRequest.author.equals(author, ignoreCase = true)
        }
        numberPattern.matchEntire(userQuery)?.groupValues?.get(1)?.toIntOrNull()?.let { number ->
            return pullRequest.number == number
        }
        if (loginPattern.matches(userQuery)) {
            return pullRequest.author.equals(userQuery, ignoreCase = true)
        }
        if (userQuery.contains(':')) return false
        return sequenceOf(
            pullRequest.title,
            pullRequest.body,
            pullRequest.author,
            pullRequest.number.toString()
        ).any { value -> value.contains(userQuery, ignoreCase = true) }
    }

    private fun scope(
        repository: String,
        baseBranch: String,
        filter: CorePullRequestFilter,
        expression: String
    ): String = listOfNotNull(
        expression.takeIf { it.isNotBlank() },
        "repo:$repository",
        "is:pr",
        "base:$baseBranch",
        when (filter) {
            CorePullRequestFilter.Open -> "is:open"
            CorePullRequestFilter.Merged -> "is:merged"
            CorePullRequestFilter.Closed -> "is:closed is:unmerged"
            CorePullRequestFilter.All -> null
        }
    ).joinToString(" ")

    private fun stripLockedQualifiers(input: String): String = lockedQualifierPattern
        .replace(input.trim(), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}

internal object PullRequestFirstContributionPolicy {
    fun authorsNeedingLookup(
        pullRequests: List<CorePullRequest>,
        limit: Int,
        isCached: (String) -> Boolean
    ): List<String> {
        if (limit <= 0) return emptyList()
        return pullRequests.asSequence()
            .filter(::needsRepositoryLookup)
            .map(CorePullRequest::author)
            .distinctBy { it.lowercase() }
            .filterNot(isCached)
            .take(limit)
            .toList()
    }

    fun needsRepositoryLookup(pullRequest: CorePullRequest): Boolean {
        if (pullRequest.firstContribution != null) return false
        if (!pullRequest.state.equals("open", ignoreCase = true)) return false
        if (pullRequest.author.isBlank() || pullRequest.author == "unknown") return false
        return pullRequest.authorAssociation.isBlank() ||
            pullRequest.authorAssociation.equals("NONE", ignoreCase = true)
    }

    fun applyRepositoryLookup(
        pullRequest: CorePullRequest,
        hasExistingContribution: Boolean?
    ): CorePullRequest {
        if (!needsRepositoryLookup(pullRequest) || hasExistingContribution != false) {
            return pullRequest
        }
        return pullRequest.copy(firstContribution = PullRequestFirstContribution.Repository)
    }
}

private data class GithubPullRequestSourcePage(
    val items: List<CorePullRequest>,
    val hasNextPage: Boolean
)

private data class GithubPullRequestSearchSourcePage(
    val items: List<CorePullRequest>,
    val totalCount: Int,
    val hasNextPage: Boolean
)

private fun string(obj: JsonObject?, key: String): String =
    ((obj?.get(key) as? JsonPrimitive)?.contentOrNull ?: "").trim()
