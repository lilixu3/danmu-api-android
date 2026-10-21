package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.data.service.GithubPullRequestService
import com.example.danmuapiapp.data.service.PullRequestMergeService
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreDependencyRepairRequest
import com.example.danmuapiapp.domain.model.CoreInfo
import com.example.danmuapiapp.domain.model.CorePullRequest
import com.example.danmuapiapp.domain.model.CorePullRequestFilePage
import com.example.danmuapiapp.domain.model.CorePullRequestFilter
import com.example.danmuapiapp.domain.model.CorePullRequestPage
import com.example.danmuapiapp.domain.model.RunMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * PR 实验室数据源与 PR 组合准备：查询 PR 列表/详情/文件，并把多个 PR 合并结果准备成待应用的核心候选。
 * Repository 只提供来源解析、状态读取与操作生命周期回调。
 */
internal class CorePullRequestController(
    private val pullRequestService: GithubPullRequestService,
    private val pullRequestMergeService: PullRequestMergeService,
    private val runtimeDependencyPackManager: RuntimeDependencyPackManager,
    private val installController: CoreInstallController,
    private val resolveRepo: (ApiVariant) -> String,
    private val resolveBranch: (ApiVariant) -> String?,
    private val readReadyCoreInfo: (ApiVariant) -> CoreInfo?,
    private val currentRunMode: () -> RunMode,
    private val locationOf: (ApiVariant, RunMode) -> CoreLocation,
    private val hasValidCore: (ApiVariant, RunMode) -> Boolean,
    private val readLocalMetadata: (ApiVariant, RunMode) -> CoreSourceMetadata?,
    private val beginOperation: (ApiVariant, String) -> Long,
    private val finishOperation: (Long) -> Unit,
    private val markAwaitingRepair: (Long) -> Unit,
    private val setPendingMutation: (PendingCoreMutation, Boolean) -> Unit,
    private val clearPendingMutation: (Long, Boolean) -> Unit,
    private val createStagingDir: (File, String) -> File,
    private val writeOperationMarker: (File, Long) -> Unit,
    private val collectMissingCoreDependencies: (File) -> List<String>,
    private val verifyCoreDependencies: (File, String?) -> Unit,
    private val publishProgress: (ApiVariant, String, Float?, Long, Long) -> Unit,
    private val clearProgress: () -> Unit
) {
    suspend fun fetchPullRequests(
        variant: ApiVariant,
        page: Int,
        pageSize: Int,
        filter: CorePullRequestFilter,
        query: String,
        forceRefresh: Boolean
    ): Result<CorePullRequestPage> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            val repo = requireRepo(variant, "请先配置自定义核心仓库")
            val branch = resolvePullRequestBaseBranch(variant, repo)
            val readyInfo = readReadyCoreInfo(variant)
            pullRequestService.list(
                repository = repo,
                baseBranch = branch,
                page = page,
                pageSize = pageSize,
                filter = filter,
                locallyMergedPullRequestNumbers = readyInfo?.pullRequestNumbers.orEmpty(),
                installedCommitSha = readyInfo?.sourceCommitSha.orEmpty(),
                query = query,
                forceRefresh = forceRefresh
            )
        }
    }

    suspend fun enrichPullRequestListItem(
        variant: ApiVariant,
        pullRequest: CorePullRequest,
        allowFirstContributionLookup: Boolean,
        allowInclusionLookup: Boolean
    ): Result<CorePullRequest> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            val repo = requireRepo(variant, "请先配置自定义核心仓库")
            val readyInfo = readReadyCoreInfo(variant)
            pullRequestService.enrichListItem(
                repository = repo,
                pullRequest = pullRequest,
                installedCommitSha = readyInfo?.sourceCommitSha.orEmpty(),
                locallyMergedNumbers = readyInfo?.pullRequestNumbers.orEmpty().toSet(),
                allowFirstContributionLookup = allowFirstContributionLookup,
                allowInclusionLookup = allowInclusionLookup
            )
        }
    }

    suspend fun fetchPullRequestDetails(
        variant: ApiVariant,
        pullRequestNumber: Int
    ): Result<CorePullRequest> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            val repo = requireRepo(variant, "请先配置自定义核心仓库")
            val readyInfo = readReadyCoreInfo(variant)
            pullRequestService.getWithCurrentCoreInclusion(
                repository = repo,
                pullRequestNumber = pullRequestNumber,
                installedCommitSha = readyInfo?.sourceCommitSha.orEmpty(),
                locallyMergedPullRequestNumbers = readyInfo?.pullRequestNumbers.orEmpty()
            )
        }
    }

    suspend fun fetchPullRequestFiles(
        variant: ApiVariant,
        pullRequestNumber: Int,
        page: Int,
        pageSize: Int
    ): Result<CorePullRequestFilePage> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            val repo = requireRepo(variant, "请先配置自定义核心仓库")
            pullRequestService.listFiles(
                repository = repo,
                pullRequestNumber = pullRequestNumber,
                page = page,
                pageSize = pageSize
            )
        }
    }

    suspend fun preparePullRequestStack(
        variant: ApiVariant,
        pullRequestNumbers: List<Int>
    ): Result<CoreDependencyRepairRequest> {
        var ownedOperationId: Long? = null
        return try {
            withContext(Dispatchers.IO) {
                val operationId = runCatching { beginOperation(variant, "准备 PR 组合") }
                    .getOrElse { return@withContext Result.failure(it) }
                ownedOperationId = operationId
                var stagingDir: File? = null
                try {
                    val repo = requireRepo(variant, "请先配置自定义核心仓库")
                    val branch = resolvePullRequestBaseBranch(variant, repo)
                    val mode = currentRunMode()
                    val location = locationOf(variant, mode)
                    val localMetadata = if (hasValidCore(variant, mode)) {
                        readLocalMetadata(variant, mode)
                    } else {
                        null
                    }
                    val canInheritInstalledStack = localMetadata != null &&
                        localMetadata.repo.equals(repo, ignoreCase = true) &&
                        localMetadata.branch.equals(branch, ignoreCase = true)
                    val installedCommitSha = localMetadata
                        ?.takeIf { canInheritInstalledStack }
                        ?.commitSha
                        .orEmpty()
                    val effectivePullRequestNumbers = buildList {
                        if (canInheritInstalledStack) addAll(localMetadata.pullRequestNumbers)
                        addAll(pullRequestNumbers)
                    }.distinct()
                    val staging = createStagingDir(location.normalDir, "staging")
                    stagingDir = staging
                    writeOperationMarker(staging, operationId)

                    val merged = pullRequestMergeService.buildInto(
                        repository = repo,
                        baseBranch = branch,
                        pullRequestNumbers = effectivePullRequestNumbers,
                        destination = staging,
                        preferredBaseCommitSha = installedCommitSha
                    ) { stage, progress ->
                        publishProgress(variant, stage, progress, 0L, -1L)
                    }
                    val pending = installController.prepareStagedCore(
                        operationId = operationId,
                        variant = variant,
                        actionLabel = "应用 PR 组合",
                        stagingDir = staging,
                        targetDir = location.normalDir,
                        mode = mode,
                        rootDirPath = location.rootDirPath,
                        versionHint = merged.version,
                        sourceMetadata = CoreSourceMetadata(
                            repo = merged.repository,
                            branch = merged.baseBranch,
                            commitSha = merged.baseCommitSha,
                            commitPublishedAt = System.currentTimeMillis().toString(),
                            versionLabel = merged.version.orEmpty(),
                            pullRequestNumbers = merged.pullRequests.map { it.number },
                            pullRequestHeadShas = merged.pullRequests.map { it.headSha },
                            localMergeSha = merged.localMergeSha
                        ),
                        collectMissingDependencies = collectMissingCoreDependencies
                    )
                    if (pending.repair.missingDependencies.isNotEmpty()) {
                        publishProgress(variant, "正在准备运行环境", null, 0L, -1L)
                        val dependencies = runtimeDependencyPackManager.installIfAvailable(
                            coreDir = staging,
                            variant = variant
                        ) { stage, progress, downloadedBytes, totalBytes ->
                            publishProgress(variant, stage, progress, downloadedBytes, totalBytes)
                        }
                        verifyCoreDependencies(staging, dependencies.unavailableReason)
                    }
                    // The PR lab owns this ready candidate; it is not a user dependency
                    // repair task and must not trigger the global startup gate.
                    setPendingMutation(
                        pending.copy(repair = pending.repair.copy(missingDependencies = emptyList())),
                        false
                    )
                    markAwaitingRepair(operationId)
                    Result.success(pending.repair.copy(missingDependencies = emptyList()))
                } catch (cancelled: CancellationException) {
                    clearPendingMutation(operationId, true)
                    stagingDir?.let { runCatching { it.deleteRecursively() } }
                    finishOperation(operationId)
                    throw cancelled
                } catch (error: Exception) {
                    clearPendingMutation(operationId, true)
                    stagingDir?.let { runCatching { it.deleteRecursively() } }
                    finishOperation(operationId)
                    Result.failure(error)
                } finally {
                    clearProgress()
                }
            }
        } catch (cancelled: CancellationException) {
            // IO 已发布候选任务后，切回界面时仍可能被取消；不要留下无法继续的 PR 任务。
            withContext(NonCancellable + Dispatchers.IO) {
                ownedOperationId?.let { operationId ->
                    clearPendingMutation(operationId, true)
                    finishOperation(operationId)
                }
            }
            throw cancelled
        }
    }

    private suspend fun resolvePullRequestBaseBranch(variant: ApiVariant, repository: String): String {
        return resolveBranch(variant)?.takeIf { it.isNotBlank() }
            ?: pullRequestService.resolveDefaultBranch(repository)
    }

    private fun requireRepo(variant: ApiVariant, message: String): String {
        val repo = resolveRepo(variant)
        if (repo.isBlank()) throw IOException(message)
        return repo
    }
}
