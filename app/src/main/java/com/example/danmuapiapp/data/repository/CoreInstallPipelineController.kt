package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.data.network.newOutboundCall
import com.example.danmuapiapp.data.service.GithubProxyService
import com.example.danmuapiapp.data.service.GithubQuotaExhaustedException
import com.example.danmuapiapp.data.service.PullRequestMergeService
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreDependencyRepairRequest
import com.example.danmuapiapp.domain.model.CoreDependencyRepairRequiredException
import com.example.danmuapiapp.domain.model.CoreDownloadProgress
import com.example.danmuapiapp.domain.model.CoreUpdateRequest
import com.example.danmuapiapp.domain.model.GithubRelease
import com.example.danmuapiapp.domain.model.RunMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * 核心下载与安装流水线：更新计划校验、PR 组合重放、压缩包下载与解包、暂存核心发布。
 * Repository 只提供来源解析、目录位置、进度发布和操作生命周期回调。
 */
internal class CoreInstallPipelineController(
    private val httpClient: OkHttpClient,
    private val githubProxyService: GithubProxyService,
    private val pullRequestMergeService: PullRequestMergeService,
    private val installController: CoreInstallController,
    private val installedStateController: CoreInstalledStateController,
    private val mutationMutex: Mutex,
    private val userAgent: String,
    private val runOwnedOperation: suspend (ApiVariant, String, suspend (Long) -> Unit) -> Result<Unit>,
    private val resolveRemoteSource: (ApiVariant, String?) -> CoreRemoteSource?,
    private val readLocalMetadata: (ApiVariant, RunMode) -> CoreSourceMetadata?,
    private val currentRunMode: () -> RunMode,
    private val locationOf: (ApiVariant, RunMode) -> CoreLocation,
    private val resolveRepo: (ApiVariant) -> String,
    private val resolveBranch: (ApiVariant) -> String?,
    private val createStagingDir: (File, String) -> File,
    private val writeOperationMarker: (File, Long) -> Unit,
    private val buildDownloadUrlCandidates: (String) -> List<String>,
    private val extractDanmuFolder: (InputStream, File) -> CoreArchiveExtraction,
    private val collectMissingCoreDependencies: (File) -> List<String>,
    private val readPendingMutationOperationId: () -> Long?,
    private val setPendingMutation: (PendingCoreMutation) -> Unit,
    private val publishProgress: (CoreDownloadProgress) -> Unit,
    private val clearProgress: () -> Unit
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun installOrUpdateCore(
        variant: ApiVariant,
        actionLabel: String,
        branchOverride: String? = null,
        updateRequest: CoreUpdateRequest? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runOwnedOperation(variant, actionLabel) { operationId ->
            val plan = updateRequest?.plan
            if (plan != null) {
                val installed = readLocalMetadata(variant, currentRunMode())
                val fingerprint = installed?.let { json.encodeToString(it) }.orEmpty()
                if (plan.runMode != currentRunMode() || plan.repo != resolveRepo(variant) ||
                    (!resolveBranch(variant).isNullOrBlank() && !CoreRemoteParsing.branchesEquivalent(plan.branch, resolveBranch(variant).orEmpty())) ||
                    plan.installedSourceFingerprint != fingerprint) {
                    throw IOException("核心或来源已变化，请重新检查更新并选择 PR 处理方式")
                }
            }
            val remoteSource = if (plan != null) CoreRemoteSource(
                release = plan.release,
                metadata = CoreSourceMetadata(repo = plan.repo, branch = plan.branch,
                    commitSha = plan.targetCommitSha, versionLabel = plan.versionLabel)
            ) else resolveRemoteSource(variant, branchOverride)
                ?: throw IOException("无法获取版本信息")
            val release = remoteSource.release
            val sourceMetadata = remoteSource.metadata ?: buildReleaseInstallMetadata(
                variant = variant,
                release = release,
                versionHint = release.version.ifBlank { release.tagName }.ifBlank { null }
            )
            val mode = currentRunMode()
            val installed = readLocalMetadata(variant, mode)
            if (updateRequest?.keepPullRequests == true && plan != null && plan.pullRequestsToReapply.isNotEmpty()) {
                val metadata = sourceMetadata ?: throw IOException("无法读取远程基线，PR 组合未修改")
                if (installed == null || !installed.repo.equals(metadata.repo, ignoreCase = true) ||
                    !CoreRemoteParsing.branchesEquivalent(installed.branch, metadata.branch)) {
                    throw IOException("PR 组合来源与目标分支不一致，请先切换分支或重装核心")
                }
                val location = locationOf(variant, mode)
                val staging = createStagingDir(location.normalDir, "staging")
                try {
                    writeOperationMarker(staging, operationId)
                    val merged = pullRequestMergeService.buildInto(
                        repository = metadata.repo,
                        baseBranch = metadata.branch,
                        pullRequestNumbers = plan.pullRequestsToReapply,
                        destination = staging,
                        preferredBaseCommitSha = metadata.commitSha,
                        expectedHeadShas = plan.confirmedPullRequestHeads,
                        requiredPreviousHeads = plan.installedPullRequestHeads,
                        verifyUpdatePreservation = true
                    ) { stage, progress ->
                        reportProgress(
                            variant = variant,
                            actionLabel = actionLabel,
                            stageText = stage,
                            progress = progress,
                            downloadedBytes = 0L,
                            totalBytes = -1L
                        )
                    }
                    val mergedMetadata = CoreSourceMetadata(
                        repo = merged.repository, branch = merged.baseBranch,
                        commitSha = merged.baseCommitSha, versionLabel = merged.version.orEmpty(),
                        pullRequestNumbers = merged.pullRequests.map { it.number },
                        pullRequestHeadShas = merged.pullRequests.map { it.headSha },
                        localMergeSha = merged.localMergeSha
                    )
                    finalizeStagedCore(operationId, variant, actionLabel, staging, location.normalDir,
                        mode, location.rootDirPath, merged.version, mergedMetadata)
                    installedStateController.persistResolvedSourceIfNeeded(variant, mergedMetadata)
                    installedStateController.refreshInstalledState(variant)
                } finally {
                    // 缺依赖时保留候选目录，让现有修复流程继续；合并失败不触碰已安装核心。
                    if (readPendingMutationOperationId() != operationId) staging.deleteRecursively()
                }
                return@runOwnedOperation
            }
            val versionHint = sourceMetadata?.versionLabel?.ifBlank { null }
                ?: release.version.ifBlank { release.tagName }.ifBlank { null }
            downloadAndExtract(
                operationId = operationId,
                variant = variant,
                zipUrl = release.zipballUrl,
                versionHint = versionHint,
                actionLabel = actionLabel,
                sourceMetadata = sourceMetadata
            )
            installedStateController.persistResolvedSourceIfNeeded(variant, sourceMetadata)
            installedStateController.refreshInstalledState(variant)
        }
    }

    private fun buildReleaseInstallMetadata(
        variant: ApiVariant,
        release: GithubRelease,
        versionHint: String?
    ): CoreSourceMetadata? = CoreInstallMetadataPolicy.buildReleaseMetadata(
        repo = resolveRepo(variant),
        branch = resolveBranch(variant),
        release = release,
        versionHint = versionHint
    )

    suspend fun downloadAndExtract(
        operationId: Long,
        variant: ApiVariant,
        zipUrl: String,
        versionHint: String?,
        actionLabel: String,
        sourceMetadata: CoreSourceMetadata? = null
    ) {
        val mode = currentRunMode()
        val location = locationOf(variant, mode)
        val targetDir = location.normalDir
        val stagingDir = createStagingDir(targetDir, "staging")
        var effectiveSourceMetadata = sourceMetadata
        writeOperationMarker(stagingDir, operationId)

        reportProgress(
            variant = variant,
            actionLabel = actionLabel,
            stageText = "准备下载核心包",
            progress = null,
            downloadedBytes = 0L,
            totalBytes = -1L
        )

        kotlinx.coroutines.coroutineScope {
            val activeDownload = java.util.concurrent.atomic.AtomicReference<okhttp3.Call?>()
            val cancellationWatcher = launch(kotlinx.coroutines.Dispatchers.IO, start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                try { kotlinx.coroutines.awaitCancellation() } finally { activeDownload.get()?.cancel() }
            }
            try {
                val candidateUrls = buildDownloadUrlCandidates(zipUrl)
                var lastFailureMessage: String? = null
                var selectedResponse: okhttp3.Response? = null
                for (url in candidateUrls) {
                    try {
                        currentCoroutineContext().ensureActive()
                        val reqBuilder = Request.Builder()
                            .url(url)
                            .header("User-Agent", userAgent)
                        githubProxyService.applyGithubAuth(reqBuilder, url)
                        val call = httpClient.newOutboundCall(reqBuilder.build())
                        activeDownload.set(call)
                        val resp = call.executeCancellable()
                        if (resp.isSuccessful) {
                            selectedResponse = resp
                            break
                        } else {
                            lastFailureMessage = CoreDownloadPolicy.failureMessage(resp.code)
                            resp.close()
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (exhausted: GithubQuotaExhaustedException) {
                        throw exhausted
                    } catch (e: Exception) {
                        lastFailureMessage = e.message ?: "下载失败：网络异常"
                    }
                }
                val response = selectedResponse
                    ?: throw IOException(lastFailureMessage ?: "下载失败，请检查仓库、分支和 GitHub 线路")

                var lastBytes = 0L
                var totalBytes = -1L
                var lastEmitAt = 0L

                try {
                    response.use { resp ->
                        val body = resp.body
                        totalBytes = body.contentLength().takeIf { it > 0 } ?: -1L
                        val rawStream = body.byteStream()
                        reportProgress(
                            variant = variant,
                            actionLabel = actionLabel,
                            stageText = "正在下载核心包",
                            progress = if (totalBytes > 0) 0f else null,
                            downloadedBytes = 0L,
                            totalBytes = totalBytes
                        )

                        val operationJob = currentCoroutineContext()[Job]
                        val streamWithProgress = ProgressInputStream(rawStream) { bytes ->
                            operationJob?.ensureActive()
                            lastBytes = bytes
                            val now = System.currentTimeMillis()
                            val shouldEmit = CoreDownloadPolicy.shouldEmitProgress(
                                now = now,
                                lastEmitAt = lastEmitAt,
                                downloadedBytes = bytes,
                                totalBytes = totalBytes
                            )
                            if (shouldEmit) {
                                lastEmitAt = now
                                reportProgress(
                                    variant = variant,
                                    actionLabel = actionLabel,
                                    stageText = "正在下载核心包",
                                    progress = CoreDownloadPolicy.progress(bytes, totalBytes),
                                    downloadedBytes = bytes,
                                    totalBytes = totalBytes
                                )
                            }
                        }

                        val extracted = extractDanmuFolder(streamWithProgress, stagingDir)
                        if (!extracted.extractedAny) {
                            throw IOException("核心压缩包中未找到 danmu_api 或 danmu-api 目录")
                        }
                        if (effectiveSourceMetadata?.commitSha.isNullOrBlank() &&
                            !extracted.commitSha.isNullOrBlank()
                        ) {
                            effectiveSourceMetadata = effectiveSourceMetadata?.copy(
                                commitSha = extracted.commitSha
                            )
                        }
                    }
                } catch (e: Exception) {
                    runCatching { stagingDir.deleteRecursively() }
                    throw e
                }

                reportProgress(
                    variant = variant,
                    actionLabel = actionLabel,
                    stageText = "正在整理核心文件",
                    progress = 1f,
                    downloadedBytes = CoreDownloadPolicy.completedBytes(totalBytes, lastBytes),
                    totalBytes = totalBytes
                )

                finalizeStagedCore(
                    operationId = operationId,
                    variant = variant,
                    actionLabel = actionLabel,
                    stagingDir = stagingDir,
                    targetDir = targetDir,
                    mode = mode,
                    rootDirPath = location.rootDirPath,
                    versionHint = versionHint,
                    sourceMetadata = effectiveSourceMetadata
                )
            } catch (e: Exception) {
                if (e !is CoreDependencyRepairRequiredException) {
                    runCatching { stagingDir.deleteRecursively() }
                }
                throw e
            } finally {
                cancellationWatcher.cancel()
                clearProgress()
            }
        }
    }

    private suspend fun finalizeStagedCore(
        operationId: Long,
        variant: ApiVariant,
        actionLabel: String,
        stagingDir: File,
        targetDir: File,
        mode: RunMode,
        rootDirPath: String,
        versionHint: String?,
        sourceMetadata: CoreSourceMetadata?
    ) {
        val pending = installController.prepareStagedCore(
            operationId = operationId,
            variant = variant,
            actionLabel = actionLabel,
            stagingDir = stagingDir,
            targetDir = targetDir,
            mode = mode,
            rootDirPath = rootDirPath,
            versionHint = versionHint,
            sourceMetadata = sourceMetadata,
            collectMissingDependencies = collectMissingCoreDependencies
        )
        if (pending.repair.missingDependencies.isNotEmpty()) {
            setPendingMutation(pending)
            throw CoreDependencyRepairRequiredException(pending.repair)
        }
        mutationMutex.withLock {
            installController.applyStagedCore(pending)
        }
    }

    private fun reportProgress(
        variant: ApiVariant,
        actionLabel: String,
        stageText: String,
        progress: Float?,
        downloadedBytes: Long,
        totalBytes: Long
    ) {
        publishProgress(
            CoreDownloadProgress(
                inProgress = true,
                variant = variant,
                actionLabel = actionLabel,
                stageText = stageText,
                progress = progress,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes
            )
        )
    }
}
