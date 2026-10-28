package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreRevision
import com.example.danmuapiapp.domain.model.CoreRevisionDetails
import com.example.danmuapiapp.domain.model.CoreRevisionPage
import com.example.danmuapiapp.domain.model.GithubRelease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * 回退与历史：Release/提交历史、Revision 版本解析和回退安装。
 * 下载安装本身交给安装流水线，Repository 只提供来源解析与刷新回调。
 */
internal class CoreRevisionController(
    private val remoteDataSource: CoreRemoteDataSource,
    private val revisionVersionCache: CoreRevisionVersionCache,
    private val installPipeline: CoreInstallPipelineController,
    private val runOwnedOperation: suspend (ApiVariant, String, suspend (Long) -> Unit) -> Result<Unit>,
    private val resolveRepo: (ApiVariant) -> String,
    private val resolveBranch: (ApiVariant) -> String?,
    private val resolveBranchCandidates: (ApiVariant) -> List<String>,
    private val refreshCoreInfo: () -> Unit,
    private val awaitCoreInfoRefresh: suspend () -> Unit,
    private val runUpdateCheckAfterRollback: suspend (ApiVariant) -> Unit
) {
    suspend fun rollbackCore(variant: ApiVariant, release: GithubRelease): Result<Unit> =
        withContext(Dispatchers.IO) {
            runOwnedOperation(variant, "回退") { operationId ->
                val versionHint = release.tagName.ifBlank {
                    release.name.ifBlank { "" }
                }.ifBlank { null }
                performRollback(
                    operationId = operationId,
                    variant = variant,
                    zipUrl = release.zipballUrl,
                    versionHint = versionHint,
                    sourceMetadata = buildRollbackMetadata(variant, release, versionHint)
                )
            }
        }

    suspend fun rollbackCore(variant: ApiVariant, revision: CoreRevision): Result<Unit> =
        withContext(Dispatchers.IO) {
            runOwnedOperation(variant, "回退") { operationId ->
                val versionHint = revision.version.trim().ifBlank { null }
                val release = GithubRelease(
                    tagName = "",
                    name = revision.title,
                    body = revision.message,
                    publishedAt = revision.committedAt,
                    zipballUrl = revision.archiveUrl,
                    commitSha = revision.commitSha,
                    version = revision.version
                )
                performRollback(
                    operationId = operationId,
                    variant = variant,
                    zipUrl = revision.archiveUrl,
                    versionHint = versionHint,
                    sourceMetadata = buildRollbackMetadata(variant, release, versionHint)
                )
            }
        }

    suspend fun fetchReleaseHistory(variant: ApiVariant): List<GithubRelease> =
        withContext(Dispatchers.IO) {
            val repo = resolveRepo(variant)
            if (repo.isBlank()) return@withContext emptyList()
            try {
                val commitHistory = remoteDataSource.fetchCommitHistory(repo, resolveBranchCandidates(variant))
                if (commitHistory.isNotEmpty()) return@withContext commitHistory

                remoteDataSource.fetchReleaseHistoryFromReleases(repo)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
        }

    suspend fun fetchRevisionHistory(
        variant: ApiVariant,
        page: Int,
        pageSize: Int,
        query: String
    ): Result<CoreRevisionPage> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            require(page > 0) { "页码无效" }
            require(pageSize in 1..100) { "每页数量无效" }
            val repo = resolveRepo(variant)
            if (repo.isBlank()) throw IOException("请先配置核心仓库")
            val branches = resolveBranchCandidates(variant)
            val normalizedQuery = query.trim()
            val revisionPage = if (normalizedQuery.isBlank()) {
                remoteDataSource.fetchRevisionCommits(
                    repo = repo,
                    branchCandidates = branches,
                    includeDefaultBranchFallback = variant != ApiVariant.Custom,
                    page = page,
                    pageSize = pageSize
                )
            } else {
                remoteDataSource.searchRevisionCommitsByAuthorOrKeyword(
                    repo = repo,
                    branchCandidates = branches,
                    includeDefaultBranchFallback = variant != ApiVariant.Custom,
                    query = normalizedQuery,
                    page = page,
                    pageSize = pageSize,
                    keywordSearch = {
                        if (variant == ApiVariant.Custom) {
                            remoteDataSource.searchRevisionCommitsOnBranch(
                                repo = repo,
                                branchCandidates = branches,
                                query = normalizedQuery,
                                page = page,
                                pageSize = pageSize
                            )
                        } else {
                            remoteDataSource.searchRevisionCommits(
                                repo = repo,
                                query = normalizedQuery,
                                page = page,
                                pageSize = pageSize
                            )
                        }
                    }
                )
            }
            CoreRevisionPage(
                revisions = revisionPage.revisions,
                page = page,
                hasNextPage = revisionPage.hasNextPage
            )
        }
    }

    suspend fun fetchRevisionVersion(
        variant: ApiVariant,
        revision: CoreRevision
    ): Result<String?> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            val repo = resolveRepo(variant)
            if (repo.isBlank()) throw IOException("请先配置核心仓库")
            resolveRevisionVersion(repo, revision.commitSha)
        }
    }

    suspend fun fetchRevisionDetails(
        variant: ApiVariant,
        revision: CoreRevision
    ): Result<CoreRevisionDetails> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            val repo = resolveRepo(variant)
            if (repo.isBlank()) throw IOException("请先配置核心仓库")
            remoteDataSource.fetchRevisionDetailsFromGithub(repo, revision)
                ?: throw IOException("无法获取该提交的文件变动")
        }
    }

    private suspend fun performRollback(
        operationId: Long,
        variant: ApiVariant,
        zipUrl: String,
        versionHint: String?,
        sourceMetadata: CoreSourceMetadata?
    ) {
        installPipeline.downloadAndExtract(
            operationId = operationId,
            variant = variant,
            zipUrl = zipUrl,
            versionHint = versionHint,
            actionLabel = "回退",
            sourceMetadata = sourceMetadata
        )
        refreshCoreInfo()
        awaitCoreInfoRefresh()
        runUpdateCheckAfterRollback(variant)
    }

    private fun buildRollbackMetadata(
        variant: ApiVariant,
        release: GithubRelease,
        versionHint: String?
    ): CoreSourceMetadata? {
        val repo = resolveRepo(variant)
        return CoreInstallMetadataPolicy.buildRollbackMetadata(
            repo = repo,
            branch = resolveBranch(variant),
            release = release,
            versionHint = versionHint,
            resolveTagCommitSha = { tag -> remoteDataSource.fetchBranchHead(repo, tag)?.sha }
        )
    }

    private suspend fun resolveRevisionVersion(repo: String, commitSha: String): String? {
        revisionVersionCache.cachedVersion(repo, commitSha)?.let { return it }
        val now = System.currentTimeMillis()
        if (revisionVersionCache.isFailureCoolingDown(repo, commitSha, now)) return null

        val match = remoteDataSource.resolveGlobalsVersionAtCommit(
            repo = repo,
            commitSha = commitSha,
            filePaths = revisionVersionCache.lookupPaths(repo)
        )
        if (match != null) {
            revisionVersionCache.recordSuccess(
                repo = repo,
                commitSha = commitSha,
                filePath = match.filePath,
                version = match.version
            )
            return match.version
        }
        revisionVersionCache.recordFailure(repo, commitSha, now)
        return null
    }
}
