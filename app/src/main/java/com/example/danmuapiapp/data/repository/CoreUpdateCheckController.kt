package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.data.service.GithubQuotaExhaustedException
import com.example.danmuapiapp.data.service.GithubQuotaRequestContext
import com.example.danmuapiapp.data.service.PullRequestUpdateCoverage
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreInfo
import com.example.danmuapiapp.domain.model.CorePullRequest
import com.example.danmuapiapp.domain.model.CorePullRequestInclusion
import com.example.danmuapiapp.domain.model.CoreRemoteCommit
import com.example.danmuapiapp.domain.model.CoreSourceStatus
import com.example.danmuapiapp.domain.model.CoreUpdateComparison
import com.example.danmuapiapp.domain.model.CoreUpdatePlan
import com.example.danmuapiapp.domain.model.CoreUpdateRelation
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.compareVersions
import com.example.danmuapiapp.domain.model.formatCoreSourceText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

internal data class LatestRemoteVersionCacheEntry(
    val versionLabel: String,
    val repo: String,
    val branch: String
)

/**
 * 核心更新检查：远端版本/提交关系判定、PR 头检查、比较缓存与最新版本标签缓存。
 * Repository 只提供来源解析与核心状态发布回调。
 */
internal class CoreUpdateCheckController(
    private val requireQuotaAvailable: () -> Unit,
    private val isQuotaBlocked: () -> Boolean,
    private val remoteDataSource: CoreRemoteDataSource,
    private val loadPullRequestHead: suspend (String, Int) -> String,
    private val loadPullRequest: suspend (String, Int) -> CorePullRequest,
    private val coverageForUpdate: suspend (String, CorePullRequest, String, String, String) -> PullRequestUpdateCoverage,
    private val resolveRepo: (ApiVariant) -> String,
    private val resolveBranch: (ApiVariant) -> String?,
    private val currentRunMode: () -> RunMode,
    private val readLocalMetadata: (ApiVariant, RunMode) -> CoreSourceMetadata?,
    private val resolveRemoteSource: (ApiVariant) -> CoreRemoteSource?,
    private val readCoreInfoList: () -> List<CoreInfo>,
    private val updateCoreInfoList: ((List<CoreInfo>) -> List<CoreInfo>) -> Unit,
    private val logWarning: (String, Throwable) -> Unit
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val updateComparisonCache = ConcurrentHashMap<String, CoreUpdateComparison>()
    private val latestRemoteVersionCache = ConcurrentHashMap<ApiVariant, LatestRemoteVersionCacheEntry>()

    fun forgetLatestVersion(variant: ApiVariant) {
        latestRemoteVersionCache.remove(variant)
    }

    /** 返回上次检查记录的最新版本标签，仓库或分支已变化时视为失效。 */
    fun latestVersionLabel(variant: ApiVariant, repo: String, branch: String?): String? =
        latestRemoteVersionCache[variant]
            ?.takeIf { it.repo == repo && it.branch == (branch ?: "") }
            ?.versionLabel

    suspend fun checkAndMarkUpdate(variant: ApiVariant) {
        withContext(Dispatchers.IO) {
            val checkedAt = System.currentTimeMillis()
            try {
                val info = readCoreInfoList().find { it.variant == variant } ?: return@withContext
                if (!info.isInstalled) return@withContext
                requireQuotaAvailable()
                val remoteSource = resolveRemoteSource(variant)
                    ?: throw IOException("无法读取远程核心信息")
                val remoteVersion = remoteSource.metadata?.versionLabel
                    ?.removePrefix("v")
                    ?.trim()
                    .orEmpty()
                val localVersion = info.version?.removePrefix("v")?.trim().orEmpty()
                val localMetadata = readLocalMetadata(variant, currentRunMode())
                val remoteSha = remoteSource.metadata?.commitSha?.trim().orEmpty()
                val localSha = localMetadata?.commitSha?.trim().orEmpty()
                val sourceStatus = if (
                    variant == ApiVariant.Custom || !resolveBranch(variant).isNullOrBlank()
                ) {
                    CoreRemoteParsing.resolveCoreSourceStatus(
                        localMetadata = localMetadata,
                        desiredRepo = resolveRepo(variant),
                        desiredBranch = resolveBranch(variant)
                    )
                } else {
                    CoreSourceStatus.NotApplicable
                }
                val sourceMismatch = sourceStatus == CoreSourceStatus.Mismatched
                val shaRelation = CorePullRequestUpdatePolicy.branchRelation(localSha, remoteSha)
                var relationshipCheckError: IOException? = null
                val branchRelation = if (!sourceMismatch && shaRelation == CoreUpdateRelation.Changed) {
                    val cacheKey = "${resolveRepo(variant).lowercase()}:${localSha.lowercase()}:${remoteSha.lowercase()}"
                    val commit = remoteSource.remoteCommit ?: CoreRemoteCommit(remoteSha, "远程提交")
                    val comparison = updateComparisonCache[cacheKey] ?: remoteDataSource.fetchUpdateComparison(
                        repo = resolveRepo(variant),
                        branch = resolveBranch(variant).orEmpty(),
                        localCommitSha = localSha,
                        remoteCommit = commit
                    )
                    comparison?.let { if (updateComparisonCache.size >= 24) updateComparisonCache.clear(); updateComparisonCache[cacheKey] = it }
                    // A missing relationship is not proof that the remote is ahead.
                    if (comparison == null) relationshipCheckError = IOException("无法确认提交更新方向，请稍后重试")
                    comparison?.relation ?: CoreUpdateRelation.Unknown
                } else shaRelation
                val baseRelation = when {
                    sourceMismatch -> CoreUpdateRelation.Unknown
                    branchRelation != null -> branchRelation
                    remoteVersion.isNotBlank() && localVersion.isNotBlank() -> when {
                        compareVersions(remoteVersion, localVersion) > 0 -> CoreUpdateRelation.Changed
                        compareVersions(remoteVersion, localVersion) < 0 -> CoreUpdateRelation.LocalAhead
                        else -> CoreUpdateRelation.Identical
                    }
                    else -> CoreUpdateRelation.Unknown
                }
                var pullRequestCheckError: Exception? = null
                val changedPullRequests = if (!sourceMismatch && localMetadata != null) {
                    val remoteHeads = buildMap {
                        localMetadata.pullRequestNumbers.forEach { number ->
                            try {
                                val head = loadPullRequestHead(resolveRepo(variant), number)
                                    .takeIf { it.isNotBlank() } ?: throw IOException("无法读取 PR #$number 的最新提交")
                                put(number, head)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                pullRequestCheckError = error
                                logWarning("检查 PR #$number 更新失败", error)
                            }
                        }
                    }
                    CorePullRequestUpdatePolicy.changedHeads(localMetadata, remoteHeads)
                } else emptyMap()
                val relation = CorePullRequestUpdatePolicy.relation(baseRelation, changedPullRequests)
                // 已确认主分支有新提交时，不因单个 PR API 失败而隐藏更新入口。
                if (!relation.hasRemoteUpdate) {
                    relationshipCheckError?.let { throw it }
                    pullRequestCheckError?.let { throw it }
                }
                val hasVersionUpdate = !sourceMismatch && relation.hasRemoteUpdate
                val availableVersionLabel = CoreRemoteParsing.buildLatestVersionLabel(remoteVersion, remoteSha)
                if (availableVersionLabel.isNotBlank()) {
                    latestRemoteVersionCache[variant] = LatestRemoteVersionCacheEntry(
                        versionLabel = availableVersionLabel,
                        repo = resolveRepo(variant),
                        branch = resolveBranch(variant).orEmpty()
                    )
                } else {
                    latestRemoteVersionCache.remove(variant)
                }
                val desiredSource = formatCoreSourceText(
                    resolveRepo(variant),
                    resolveBranch(variant)
                ).ifBlank { null }
                val remoteCommit = remoteSource.remoteCommit ?: remoteSha.takeIf { it.isNotBlank() }?.let { sha ->
                    CoreRemoteCommit(
                        sha = sha,
                        title = remoteSource.release.body.lineSequence().firstOrNull()?.trim().orEmpty()
                            .ifBlank { "提交 ${sha.take(7)}" },
                        message = remoteSource.release.body,
                        committedAt = remoteSource.release.publishedAt,
                        htmlUrl = "https://github.com/${resolveRepo(variant)}/commit/$sha"
                    )
                }
                updateCoreInfoList { current -> current.map {
                    if (it.variant == variant) {
                        it.copy(
                            availableVersion = availableVersionLabel.ifBlank { null }.takeIf { hasVersionUpdate },
                            hasVersionUpdate = hasVersionUpdate,
                            updatedPullRequestHeads = changedPullRequests,
                            sourceMismatch = sourceMismatch,
                            sourceStatus = sourceStatus,
                            desiredSource = desiredSource.takeIf { sourceMismatch },
                            remoteVersion = remoteVersion.ifBlank { null },
                            remoteBranch = remoteSource.metadata?.branch?.ifBlank { null }
                                ?: resolveBranch(variant),
                            remoteCommit = remoteCommit,
                            updateRelation = relation,
                            updateCheckError = null,
                            updateCheckedAtEpochMillis = checkedAt
                        )
                    }
                    else it
                } }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (e is GithubQuotaExhaustedException && GithubQuotaRequestContext.isAutomatic()) return@withContext
                if (e !is GithubQuotaExhaustedException) logWarning("checkAndMarkUpdate(${variant.key}) 失败", e)
                updateCoreInfoList { current -> current.map { info ->
                    if (info.variant == variant) {
                        info.copy(
                            updateCheckError = e.message?.takeIf { it.isNotBlank() }
                                ?: "无法连接 GitHub",
                            updateCheckedAtEpochMillis = checkedAt
                        )
                    } else {
                        info
                    }
                } }
            }
        }
    }

    suspend fun checkAllUpdates() {
        withContext(Dispatchers.IO) {
            ApiVariant.entries.forEach { variant ->
                if (isQuotaBlocked()) return@withContext
                try {
                    checkAndMarkUpdate(variant)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    logWarning("checkAllUpdates: ${variant.key} 更新检查失败", e)
                }
            }
        }
    }

    suspend fun fetchUpdateComparison(
        variant: ApiVariant
    ): Result<CoreUpdateComparison> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            var info = readCoreInfoList().firstOrNull { it.variant == variant }
                ?: throw IOException("未找到核心状态")
            if (!info.isInstalled) throw IOException("核心尚未安装")
            if (info.sourceMismatch) throw IOException("当前核心来源与设置不一致")

            if (info.remoteCommit == null) {
                requireQuotaAvailable()
                checkAndMarkUpdate(variant)
                info = readCoreInfoList().firstOrNull { it.variant == variant }
                    ?: throw IOException("未找到核心状态")
                info.updateCheckError?.let { throw IOException(it) }
            }

            val repo = resolveRepo(variant)
            if (repo.isBlank()) throw IOException("请先配置核心仓库")
            val localMetadata = readLocalMetadata(variant, currentRunMode())
            val localSha = localMetadata?.commitSha?.trim().orEmpty()
            if (localSha.isBlank()) {
                throw IOException("当前安装缺少提交标记，重新安装后才能查看版本差异")
            }
            val remoteCommit = info.remoteCommit
                ?: throw IOException("尚未读取到远程最新提交")
            val remoteSha = remoteCommit.sha.trim()
            if (remoteSha.isBlank()) throw IOException("远程最新提交信息不完整")
            val branch = info.remoteBranch?.trim().orEmpty()
                .ifBlank { resolveBranch(variant).orEmpty() }
                .ifBlank { localMetadata?.branch.orEmpty() }

            val baseComparison = if (CoreRemoteParsing.commitShasEquivalent(localSha, remoteSha)) {
                CoreUpdateComparisonParser.identical(
                    repo = repo,
                    branch = branch,
                    commit = remoteCommit
                )
            } else {
                val cacheKey = "${repo.lowercase()}:${localSha.lowercase()}:${remoteSha.lowercase()}"
                updateComparisonCache[cacheKey] ?: remoteDataSource.fetchUpdateComparison(
                    repo = repo,
                    branch = branch,
                    localCommitSha = localSha,
                    remoteCommit = remoteCommit
                )?.also { parsed ->
                    if (updateComparisonCache.size >= 24) updateComparisonCache.clear()
                    updateComparisonCache[cacheKey] = parsed
                } ?: throw IOException("无法获取已安装版本与远程版本的差异")
            }

            val comparison = CorePullRequestUpdatePolicy.comparison(baseComparison, info.updatedPullRequestHeads)
            val hasUpdate = comparison.relation.hasRemoteUpdate ||
                (comparison.relation == CoreUpdateRelation.Unknown &&
                    !CoreRemoteParsing.commitShasEquivalent(localSha, remoteSha))
            val availableVersionLabel = CoreRemoteParsing.buildLatestVersionLabel(
                info.remoteVersion.orEmpty(),
                remoteSha
            )
            updateCoreInfoList { current -> current.map { currentInfo ->
                if (currentInfo.variant == variant) {
                    currentInfo.copy(
                        availableVersion = availableVersionLabel.ifBlank { null }
                            .takeIf { hasUpdate },
                        hasVersionUpdate = hasUpdate,
                        updateRelation = comparison.relation,
                        updateCheckError = null
                    )
                } else {
                    currentInfo
                }
            } }
            comparison
        }
    }

    /** 生成更新计划：确认远程基线，并逐个确认已安装 PR 组合是否已被目标提交包含。 */
    suspend fun prepareUpdatePlan(variant: ApiVariant): Result<CoreUpdatePlan> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            val mode = currentRunMode()
            val installed = readLocalMetadata(variant, mode)
            val remote = resolveRemoteSource(variant) ?: throw IOException("无法读取远程核心信息")
            val repo = resolveRepo(variant)
            val branch = remote.metadata?.branch.orEmpty()
            val targetSha = remote.metadata?.commitSha.orEmpty()
            val numbers = installed?.pullRequestNumbers.orEmpty()
            if (numbers.isNotEmpty() && (targetSha.isBlank() || installed?.repo != repo ||
                    !CoreRemoteParsing.branchesEquivalent(installed.branch, branch))) {
                throw IOException("无法确认 PR 组合的远程基线，请检查来源与网络后重试")
            }
            val missing = mutableListOf<Int>()
            val unknown = mutableListOf<Int>()
            val confirmedHeads = mutableMapOf<Int, String>()
            val notes = mutableMapOf<Int, String>()
            val installedHeads = numbers.mapIndexed { index, number ->
                number to installed?.pullRequestHeadShas?.getOrNull(index).orEmpty()
            }.toMap()
            numbers.forEach { number ->
                val inclusion = try {
                    val pr = loadPullRequest(repo, number)
                    confirmedHeads[number] = pr.headSha
                    val coverage = coverageForUpdate(
                        repo,
                        pr,
                        installedHeads[number].orEmpty(),
                        installed?.commitSha.orEmpty(),
                        targetSha
                    )
                    if (coverage.reason.isNotBlank()) notes[number] = coverage.reason
                    coverage.inclusion
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (exhausted: GithubQuotaExhaustedException) {
                    throw exhausted
                } catch (error: Exception) {
                    logWarning("确认 PR #$number 是否已包含失败", error)
                    notes[number] = "查询失败，尚未确认是否包含；可以取消后重试"
                    CorePullRequestInclusion.Unknown
                }
                when (inclusion) {
                    CorePullRequestInclusion.Included -> Unit
                    CorePullRequestInclusion.NotIncluded -> missing += number
                    else -> unknown += number
                }
            }
            CoreUpdatePlan(
                variant = variant, runMode = mode, repo = repo, branch = branch,
                targetCommitSha = targetSha,
                versionLabel = remote.metadata?.versionLabel.orEmpty().ifBlank { remote.release.version },
                release = remote.release.copy(
                    zipballUrl = if (targetSha.isNotBlank()) {
                        remoteDataSource.buildBranchZipUrl(repo, targetSha)
                    } else {
                        remote.release.zipballUrl
                    }
                ),
                installedSourceFingerprint = installed?.let { json.encodeToString(it) }.orEmpty(),
                pullRequestNumbers = numbers,
                notIncludedPullRequestNumbers = missing,
                unknownPullRequestNumbers = unknown,
                confirmedPullRequestHeads = confirmedHeads,
                installedPullRequestHeads = installedHeads,
                pullRequestNotes = notes
            )
        }
    }
}
