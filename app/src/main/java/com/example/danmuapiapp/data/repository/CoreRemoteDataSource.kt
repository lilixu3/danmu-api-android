package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.data.remote.github.GithubRemoteService
import com.example.danmuapiapp.domain.model.CoreRemoteCommit
import com.example.danmuapiapp.domain.model.CoreRevision
import com.example.danmuapiapp.domain.model.CoreRevisionDetails
import com.example.danmuapiapp.domain.model.CoreRevisionFileChange
import com.example.danmuapiapp.domain.model.CoreRevisionPage
import com.example.danmuapiapp.domain.model.CoreUpdateComparison
import com.example.danmuapiapp.domain.model.GithubRelease
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException

internal interface CoreRemoteTransport {
    fun apiUrlCandidates(path: String): List<String>

    fun withProxyCandidates(url: String): List<String>

    fun <T> requestMapped(
        urls: List<String>,
        headers: Map<String, String>,
        mapper: (String) -> T?
    ): T?

    fun rawUrlCandidates(repo: String, filePath: String): List<String>

    suspend fun requestTextResponseCancellable(
        urls: List<String>,
        headers: Map<String, String>,
        bodyValidator: (String) -> Boolean
    ): GithubRemoteService.TextResponsePayload?

    suspend fun <T> requestMappedCancellable(
        urls: List<String>,
        headers: Map<String, String>,
        mapper: (String) -> T?
    ): T?
}

private class GithubCoreRemoteTransport(
    private val githubRemoteService: GithubRemoteService
) : CoreRemoteTransport {
    override fun apiUrlCandidates(path: String): List<String> =
        githubRemoteService.apiUrlCandidates(path)

    override fun withProxyCandidates(url: String): List<String> =
        githubRemoteService.withProxyCandidates(url)

    override fun <T> requestMapped(
        urls: List<String>,
        headers: Map<String, String>,
        mapper: (String) -> T?
    ): T? = githubRemoteService.requestMapped(urls, headers, mapper)

    override fun rawUrlCandidates(repo: String, filePath: String): List<String> =
        githubRemoteService.rawUrlCandidates(repo, filePath)

    override suspend fun requestTextResponseCancellable(
        urls: List<String>,
        headers: Map<String, String>,
        bodyValidator: (String) -> Boolean
    ): GithubRemoteService.TextResponsePayload? =
        githubRemoteService.requestTextResponseCancellable(urls, headers, bodyValidator)

    override suspend fun <T> requestMappedCancellable(
        urls: List<String>,
        headers: Map<String, String>,
        mapper: (String) -> T?
    ): T? = githubRemoteService.requestMappedCancellable(urls, headers, mapper)
}

/**
 * Stateless GitHub branch metadata access. Caching and refresh orchestration stay in the repository.
 */
internal class CoreRemoteDataSource internal constructor(
    private val transport: CoreRemoteTransport
) {
    constructor(githubRemoteService: GithubRemoteService) : this(GithubCoreRemoteTransport(githubRemoteService))

    companion object {
        private const val USER_AGENT = "DanmuApiApp"
        private const val GITHUB_ACCEPT = "application/vnd.github+json"
        private const val BRANCH_PAGE_SIZE = 100
        private const val MAX_BRANCH_PAGES = 5
        private const val REVISION_PAGE_SIZE = 100
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun apiUrlCandidates(path: String): List<String> = transport.apiUrlCandidates(path)

    private fun withProxyCandidates(url: String): List<String> = transport.withProxyCandidates(url)

    private fun <T> requestMapped(
        urls: List<String>,
        headers: Map<String, String>,
        mapper: (String) -> T?
    ): T? = transport.requestMapped(urls, headers, mapper)

    private fun apiHeaders(): Map<String, String> = mapOf(
        "Accept" to GITHUB_ACCEPT,
        "User-Agent" to USER_AGENT
    )

    fun fetchDefaultBranch(repo: String): String? {
        val apiDefault = requestMapped(
            urls = apiUrlCandidates("repos/$repo"),
            headers = apiHeaders()
        ) { body -> CoreRemoteBranchParsing.parseDefaultBranch(body) }
        if (!apiDefault.isNullOrBlank()) return apiDefault

        return requestMapped(
            urls = withProxyCandidates("https://github.com/$repo"),
            headers = mapOf("User-Agent" to USER_AGENT)
        ) { body -> CoreRemoteBranchParsing.parseDefaultBranchFromHtml(body) }
    }

    fun fetchBranchList(repo: String): List<String> {
        val fetchedBranches = mutableListOf<String>()
        for (page in 1..MAX_BRANCH_PAGES) {
            val pageBranches = requestMapped(
                urls = apiUrlCandidates("repos/$repo/branches?per_page=$BRANCH_PAGE_SIZE&page=$page"),
                headers = apiHeaders()
            ) { body -> CoreRemoteBranchParsing.parseBranchList(body) } ?: break
            fetchedBranches.addAll(pageBranches)
            if (pageBranches.size < BRANCH_PAGE_SIZE) break
        }
        val apiBranches = fetchedBranches.distinct()
        if (apiBranches.isNotEmpty()) return apiBranches

        return fetchBranchListFromHtml(repo)
    }

    fun fetchBranchListFromHtml(repo: String): List<String> {
        return requestMapped(
            urls = withProxyCandidates("https://github.com/$repo/branches/all"),
            headers = mapOf("User-Agent" to USER_AGENT)
        ) { body -> CoreRemoteBranchParsing.parseBranchListFromHtml(repo, body) } ?: emptyList()
    }

    fun fetchBranchHead(repo: String, branch: String): BranchHeadInfo? {
        val encodedBranch = CoreRemoteUrlPolicy.encodeUrlPart(branch)
        return requestMapped(
            urls = apiUrlCandidates("repos/$repo/commits/$encodedBranch"),
            headers = apiHeaders()
        ) { body -> CoreRemoteBranchParsing.parseBranchHead(body) }
    }

    fun buildDownloadUrlCandidates(zipUrl: String): List<String> =
        CoreRemoteUrlPolicy.downloadCandidates(zipUrl, withProxyCandidates(zipUrl))

    fun buildBranchZipUrl(repo: String, branch: String): String {
        return "https://api.github.com/repos/$repo/zipball/${CoreRemoteUrlPolicy.encodeUrlPart(branch)}"
    }

    suspend fun fetchRevisionCommits(
        repo: String,
        branchCandidates: List<String>,
        includeDefaultBranchFallback: Boolean,
        page: Int,
        pageSize: Int,
        author: String? = null
    ): CoreRevisionPage {
        val authorParameter = author?.let { "&author=${CoreRemoteUrlPolicy.encodeUrlPart(it)}" }.orEmpty()
        val urls = buildList {
            branchCandidates.forEach { branch ->
                addAll(
                    apiUrlCandidates(
                        "repos/$repo/commits?sha=${CoreRemoteUrlPolicy.encodeUrlPart(branch)}" +
                            "&per_page=$pageSize&page=$page$authorParameter"
                    )
                )
            }
            if (includeDefaultBranchFallback) {
                addAll(
                    apiUrlCandidates(
                        "repos/$repo/commits?per_page=$pageSize&page=$page$authorParameter"
                    )
                )
            }
        }.distinct()

        val payload = transport.requestTextResponseCancellable(
            urls = urls,
            headers = githubApiHeaders(),
            bodyValidator = { body ->
                runCatching { json.parseToJsonElement(body) }.getOrNull() is JsonArray
            }
        ) ?: throw IOException("无法读取 GitHub 提交记录，请检查网络、API 额度或仓库分支")
        val root = runCatching { json.parseToJsonElement(payload.body) }.getOrNull() as? JsonArray
            ?: throw IOException("GitHub 提交记录响应格式无效")
        val revisions = root.mapNotNull { element ->
            parseCoreRevision(repo, element as? JsonObject ?: return@mapNotNull null)
        }
        return CoreRevisionPage(
            revisions = revisions,
            page = page,
            hasNextPage = CoreRevisionSearch.hasNextHistoryPage(
                linkHeader = payload.linkHeader,
                receivedCount = revisions.size,
                pageSize = pageSize
            )
        )
    }

    suspend fun searchRevisionCommitsByAuthorOrKeyword(
        repo: String,
        branchCandidates: List<String>,
        includeDefaultBranchFallback: Boolean,
        query: String,
        page: Int,
        pageSize: Int,
        keywordSearch: suspend () -> CoreRevisionPage
    ): CoreRevisionPage {
        val authorQuery = CoreRevisionSearch.authorQuery(query) ?: return keywordSearch()
        val authorPage = fetchRevisionCommits(
            repo = repo,
            branchCandidates = branchCandidates,
            includeDefaultBranchFallback = includeDefaultBranchFallback,
            page = page,
            pageSize = pageSize,
            author = authorQuery.login
        )
        if (authorQuery.explicit || authorPage.revisions.isNotEmpty()) return authorPage

        // A plain one-word query may be either a login or commit text. Only fall
        // back to text search when this repository has no commits by that login.
        if (page > 1) {
            val firstAuthorPage = fetchRevisionCommits(
                repo = repo,
                branchCandidates = branchCandidates,
                includeDefaultBranchFallback = includeDefaultBranchFallback,
                page = 1,
                pageSize = 1,
                author = authorQuery.login
            )
            if (firstAuthorPage.revisions.isNotEmpty()) return authorPage
        }
        return keywordSearch()
    }

    suspend fun searchRevisionCommits(
        repo: String,
        query: String,
        page: Int,
        pageSize: Int
    ): CoreRevisionPage {
        val githubQuery = CoreRevisionSearch.query(repo, query)
        val payload = transport.requestTextResponseCancellable(
            urls = apiUrlCandidates(
                "search/commits?q=${CoreRemoteUrlPolicy.encodeUrlPart(githubQuery)}&sort=committer-date&order=desc" +
                    "&per_page=$pageSize&page=$page"
            ),
            headers = githubApiHeaders(),
            bodyValidator = { body ->
                val candidate = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
                candidate?.get("items") is JsonArray
            }
        ) ?: throw IOException("GitHub 提交搜索失败，请检查网络、搜索额度或关键词")
        val root = runCatching { json.parseToJsonElement(payload.body) }.getOrNull() as? JsonObject
            ?: throw IOException("GitHub 提交搜索响应格式无效")
        val items = root["items"] as? JsonArray ?: JsonArray(emptyList())
        val revisions = items.mapNotNull { element ->
            parseCoreRevision(repo, element as? JsonObject ?: return@mapNotNull null)
        }
        val totalCount = root.intValue("total_count")
        return CoreRevisionPage(
            revisions = revisions,
            page = page,
            hasNextPage = CoreRevisionSearch.hasNextPage(
                linkHeader = payload.linkHeader,
                page = page,
                pageSize = pageSize,
                totalCount = totalCount,
                receivedCount = revisions.size
            )
        )
    }

    suspend fun searchRevisionCommitsOnBranch(
        repo: String,
        branchCandidates: List<String>,
        query: String,
        page: Int,
        pageSize: Int
    ): CoreRevisionPage {
        val firstResultIndex = Math.multiplyExact(page - 1, pageSize)
        val requiredMatchCount = Math.addExact(firstResultIndex, pageSize + 1)
        val matches = mutableListOf<CoreRevision>()
        var sourcePage = 1

        while (matches.size < requiredMatchCount) {
            val source = fetchRevisionCommits(
                repo = repo,
                branchCandidates = branchCandidates,
                includeDefaultBranchFallback = false,
                page = sourcePage,
                pageSize = REVISION_PAGE_SIZE
            )
            matches += source.revisions.filter { revision ->
                CoreRevisionSearch.matches(revision, query)
            }
            if (!source.hasNextPage) break
            sourcePage += 1
        }

        return CoreRevisionPage(
            revisions = matches.drop(firstResultIndex).take(pageSize),
            page = page,
            hasNextPage = matches.size > firstResultIndex + pageSize
        )
    }

    fun fetchCommitHistory(
        repo: String,
        branchCandidates: List<String>
    ): List<GithubRelease> {
        val urls = CoreRemoteUrlPolicy.commitHistoryEndpoints(
            repo = repo,
            branchCandidates = branchCandidates,
            apiUrlCandidates = ::apiUrlCandidates
        )

        val result = transport.requestMapped(
            urls = urls,
            headers = githubApiHeaders()
        ) { body -> CoreRemoteParsing.parseCommitArray(repo, body) }
        if (!result.isNullOrEmpty()) return result

        return fetchCommitHistoryFromAtom(repo, branchCandidates)
    }

    fun fetchLatestRelease(repo: String): GithubRelease? {
        return transport.requestMapped(
            urls = apiUrlCandidates("repos/$repo/releases/latest"),
            headers = githubApiHeaders()
        ) { body -> CoreRemoteParsing.parseRelease(body) }
    }

    fun fetchVersionFromGlobals(
        repo: String,
        branches: List<String> = listOf("main", "master")
    ): String? {
        val paths = branches
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("main", "master") }
            .flatMap { branch ->
                listOf(
                    "refs/heads/$branch/danmu_api/configs/globals.js",
                    "refs/heads/$branch/danmu-api/configs/globals.js"
                )
            }
        return paths.firstNotNullOfOrNull { path ->
            transport.requestMapped(
                urls = transport.rawUrlCandidates(repo, path),
                headers = mapOf("User-Agent" to USER_AGENT)
            ) { body -> CoreRevisionVersionResolver.parseGlobalsVersion(body) }
        }
    }

    fun fetchReleaseHistoryFromReleases(repo: String): List<GithubRelease> {
        return transport.requestMapped(
            urls = apiUrlCandidates("repos/$repo/releases?per_page=10"),
            headers = githubApiHeaders()
        ) { body -> CoreRemoteParsing.parseReleaseArray(body) } ?: emptyList()
    }

    suspend fun resolveGlobalsVersionAtCommit(
        repo: String,
        commitSha: String,
        filePaths: List<String>
    ): CoreRevisionVersionMatch? {
        filePaths.forEach { filePath ->
            val version = transport.requestMappedCancellable(
                urls = transport.rawUrlCandidates(repo, "$commitSha/$filePath"),
                headers = mapOf("User-Agent" to USER_AGENT)
            ) { body -> CoreRevisionVersionResolver.parseGlobalsVersion(body) }
            if (version != null) return CoreRevisionVersionMatch(filePath, version)
        }

        // raw.githubusercontent.com cannot read private repositories with our
        // API-only credential. Fall back to the official Contents API at the
        // exact commit; the response is requested as raw globals.js source.
        filePaths.forEach { filePath ->
            val version = transport.requestMappedCancellable(
                urls = apiUrlCandidates(
                    "repos/$repo/contents/$filePath?ref=${CoreRemoteUrlPolicy.encodeUrlPart(commitSha)}"
                ),
                headers = githubApiHeaders(accept = "application/vnd.github.raw+json")
            ) { body -> CoreRevisionVersionResolver.parseGlobalsVersion(body) }
            if (version != null) return CoreRevisionVersionMatch(filePath, version)
        }
        return null
    }

    fun githubApiHeaders(accept: String = "application/vnd.github+json"): Map<String, String> = mapOf(
        "Accept" to accept,
        "X-GitHub-Api-Version" to "2022-11-28",
        "User-Agent" to USER_AGENT
    )

    suspend fun fetchUpdateComparison(
        repo: String,
        branch: String,
        localCommitSha: String,
        remoteCommit: CoreRemoteCommit
    ): CoreUpdateComparison? {
        return transport.requestMappedCancellable(
            urls = apiUrlCandidates(
                "repos/$repo/compare/${CoreRemoteUrlPolicy.encodeUrlPart(localCommitSha)}..." +
                    CoreRemoteUrlPolicy.encodeUrlPart(remoteCommit.sha)
            ),
            headers = githubApiHeaders()
        ) { body ->
            CoreUpdateComparisonParser.parse(
                raw = body,
                repo = repo,
                branch = branch,
                localCommitSha = localCommitSha,
                fallbackRemoteCommit = remoteCommit
            )
        }
    }

    suspend fun fetchRevisionDetailsFromGithub(
        repo: String,
        revision: CoreRevision
    ): CoreRevisionDetails? {
        val files = mutableListOf<CoreRevisionFileChange>()
        val seenFilePages = mutableSetOf<List<String>>()
        var additions = 0
        var deletions = 0
        var page = 1
        while (true) {
            val pagePayload = transport.requestMappedCancellable(
                urls = apiUrlCandidates(
                    "repos/$repo/commits/${CoreRemoteUrlPolicy.encodeUrlPart(revision.commitSha)}?per_page=100&page=$page"
                ),
                headers = githubApiHeaders()
            ) { body ->
                val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
                    ?: return@requestMappedCancellable null
                val filesArray = root["files"] as? JsonArray ?: JsonArray(emptyList())
                val pageFiles = filesArray.mapNotNull { element ->
                    val file = element as? JsonObject ?: return@mapNotNull null
                    val path = file.stringValue("filename")
                    if (path.isBlank()) return@mapNotNull null
                    val patch = file.stringValue("patch")
                    val status = file.stringValue("status").ifBlank { "modified" }
                    CoreRevisionFileChange(
                        path = path,
                        previousPath = file.stringValue("previous_filename").ifBlank { null },
                        status = status,
                        additions = file.intValue("additions"),
                        deletions = file.intValue("deletions"),
                        changes = file.intValue("changes"),
                        lines = CoreRevisionParser.parsePatch(patch),
                        patchUnavailableReason = if (patch.isBlank()) {
                            if (status == "removed" || status == "added" || status == "modified") {
                                "GitHub 未返回文本补丁，文件可能是二进制或变动过大"
                            } else {
                                "此文件没有可显示的文本补丁"
                            }
                        } else {
                            null
                        }
                    )
                }
                val stats = root["stats"] as? JsonObject
                RevisionDetailsPage(
                    files = pageFiles,
                    additions = stats?.intValue("additions"),
                    deletions = stats?.intValue("deletions")
                )
            } ?: return if (page == 1) null else break

            val pageIdentity = pagePayload.files.map { "${it.status}:${it.path}" }
            if (page > 1 && (pagePayload.files.isEmpty() || !seenFilePages.add(pageIdentity))) break
            if (page == 1) seenFilePages += pageIdentity
            if (page == 1) {
                additions = pagePayload.additions ?: 0
                deletions = pagePayload.deletions ?: 0
            }
            files += pagePayload.files
            if (pagePayload.files.size < 100) break
            page += 1
        }
        if (files.isEmpty()) return CoreRevisionDetails(
            revision = revision,
            files = emptyList(),
            additions = additions,
            deletions = deletions,
            changedFiles = 0
        )
        return CoreRevisionDetails(
            revision = revision,
            files = files,
            additions = additions.takeIf { it > 0 } ?: files.sumOf { it.additions },
            deletions = deletions.takeIf { it > 0 } ?: files.sumOf { it.deletions },
            changedFiles = files.size
        )
    }

    private fun fetchCommitHistoryFromAtom(
        repo: String,
        branchCandidates: List<String>
    ): List<GithubRelease> {
        for (branch in branchCandidates) {
            val parsed = transport.requestMapped(
                urls = withProxyCandidates("https://github.com/$repo/commits/${CoreRemoteUrlPolicy.encodeUrlPart(branch)}.atom"),
                headers = mapOf(
                    "Accept" to "application/atom+xml,application/xml,text/xml,*/*",
                    "User-Agent" to USER_AGENT
                )
            ) { body -> CoreRemoteParsing.parseCommitAtom(repo, body).takeIf { it.isNotEmpty() } }
            if (!parsed.isNullOrEmpty()) return parsed
        }
        return emptyList()
    }

    private fun parseCoreRevision(repo: String, obj: JsonObject): CoreRevision? {
        val sha = obj.stringValue("sha")
        if (sha.isBlank()) return null
        val commit = obj["commit"] as? JsonObject
        val message = commit?.stringValue("message").orEmpty().trim()
        val authorObject = commit?.get("author") as? JsonObject
        val committerObject = commit?.get("committer") as? JsonObject
        val githubAuthor = obj["author"] as? JsonObject
        val title = message.lineSequence().firstOrNull()?.trim().orEmpty()
            .ifBlank { "提交 ${sha.take(7)}" }
        return CoreRevision(
            commitSha = sha,
            title = title,
            message = message.ifBlank { title },
            author = githubAuthor?.stringValue("login")
                .orEmpty()
                .ifBlank { authorObject?.stringValue("name").orEmpty() }
                .ifBlank { "未知作者" },
            committedAt = authorObject?.stringValue("date")
                .orEmpty()
                .ifBlank { committerObject?.stringValue("date").orEmpty() },
            version = "",
            archiveUrl = "https://api.github.com/repos/$repo/zipball/$sha"
        )
    }

    private fun JsonObject.stringValue(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

    private fun JsonObject.intValue(key: String): Int =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
}

internal data class CoreRevisionVersionMatch(
    val filePath: String,
    val version: String
)

private data class RevisionDetailsPage(
    val files: List<CoreRevisionFileChange>,
    val additions: Int?,
    val deletions: Int?
)
