package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.data.service.NodeProjectManager
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreInfo
import com.example.danmuapiapp.domain.model.CoreSourceStatus
import com.example.danmuapiapp.domain.model.CoreUpdateRelation
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.compareVersions
import com.example.danmuapiapp.domain.model.formatCoreSourceText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * 核心状态刷新管线：安装状态扫描、更新标记合并、目录监听与去抖刷新、首次加载状态。
 * Repository 只提供状态流的读写、来源解析与本地状态读取回调。
 */
internal class CoreInfoRefreshController(
    private val scope: CoroutineScope,
    private val displayCache: CoreDisplayInfoCache,
    private val readCoreInfoList: () -> List<CoreInfo>,
    private val writeCoreInfoList: ((List<CoreInfo>) -> List<CoreInfo>) -> Unit,
    private val latestVersionLabel: (ApiVariant, String, String?) -> String?,
    private val resolveRepo: (ApiVariant) -> String,
    private val resolveBranch: (ApiVariant) -> String?,
    private val currentRunMode: () -> RunMode,
    private val locationOf: (ApiVariant, RunMode) -> CoreLocation,
    private val hasValidCore: (ApiVariant, RunMode) -> Boolean,
    private val readLocalVersion: (ApiVariant, RunMode) -> String?,
    private val readLocalMetadata: (ApiVariant, RunMode) -> CoreSourceMetadata?,
    private val coreProjectDir: () -> File,
    private val mutationMutex: Mutex,
    private val logWarning: (String, Throwable) -> Unit
) {
    private companion object {
        const val CORE_REFRESH_DEBOUNCE_MS = 800L
    }

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val refreshTicket = AtomicLong(0L)
    private var refreshJob: Job? = null
    private var debounceJob: Job? = null
    private var pendingRefreshReason: String? = null
    private var hasLoadedOnce = false
    private var watcher: CoreDirWatcher? = null

    fun refresh() {
        // 先让旧读取失效，再取消它，避免旧任务的 finally 提前结束首次加载状态。
        val ticket = refreshTicket.incrementAndGet()
        refreshJob?.cancel()
        if (!hasLoadedOnce) {
            _isLoading.value = true
        }
        refreshJob = scope.launch {
            try {
                val mode = currentRunMode()
                val displayScope = displayCache.scopeFor(mode)
                startWatcher(mode)
                // A scan must not read the old directory while a replacement is
                // underway, or publish a partial snapshot after installation.
                mutationMutex.withLock {
                    val refreshed = ApiVariant.entries.map { loadState(it, mode) }
                    currentCoroutineContext().ensureActive()
                    if (refreshTicket.get() != ticket) return@withLock
                    writeCoreInfoList { current ->
                        refreshed.map { state ->
                            mergeVersionUpdateState(
                                previousInfo = current.find { it.variant == state.info.variant },
                                refreshedInfo = state.info,
                                refreshedMetadata = state.localMetadata
                            )
                        }
                    }
                    displayCache.save(displayScope, refreshed.map { it.info })
                }
            } finally {
                if (refreshTicket.get() == ticket) {
                    hasLoadedOnce = true
                    _isLoading.value = false
                }
            }
        }
    }

    suspend fun refreshAndAwait() {
        refresh()
        while (true) {
            val activeRefresh = refreshJob ?: return
            activeRefresh.join()
            if (refreshJob === activeRefresh) return
        }
    }

    /** 等待当前已排队的刷新结束，不触发新的扫描。 */
    suspend fun awaitCurrentRefresh() {
        refreshJob?.join()
    }

    fun loadState(variant: ApiVariant, mode: RunMode): LoadedCoreState {
        val location = locationOf(variant, mode)
        if (mode == RunMode.Normal) {
            NodeProjectManager.normalizeCoreLayout(location.normalDir)
        }
        val installed = hasValidCore(variant, mode)
        val version = if (installed) readLocalVersion(variant, mode) else null
        val localMetadata = if (installed) readLocalMetadata(variant, mode) else null
        val desiredRepo = resolveRepo(variant)
        val desiredBranch = resolveBranch(variant)
        val shouldTrackSource = installed && desiredRepo.isNotBlank() &&
            (variant == ApiVariant.Custom || !desiredBranch.isNullOrBlank())
        val desiredSourceText = if (shouldTrackSource) {
            formatCoreSourceText(desiredRepo, desiredBranch)
        } else ""
        val sourceStatus = if (shouldTrackSource) {
            CoreRemoteParsing.resolveCoreSourceStatus(
                localMetadata = localMetadata,
                desiredRepo = desiredRepo,
                desiredBranch = desiredBranch
            )
        } else {
            CoreSourceStatus.NotApplicable
        }
        val sourceMismatch = sourceStatus == CoreSourceStatus.Mismatched

        return LoadedCoreState(
            info = CoreInfo(
                variant = variant,
                version = version,
                isInstalled = installed,
                sourceMismatch = sourceMismatch,
                sourceStatus = sourceStatus,
                desiredSource = desiredSourceText.ifBlank { null }.takeIf { sourceMismatch },
                pullRequestNumbers = localMetadata?.pullRequestNumbers.orEmpty(),
                sourceCommitSha = localMetadata?.commitSha.orEmpty(),
                sourceInstalledAtEpochMillis = localMetadata?.installedAtEpochMillis ?: 0L,
                sourceCommitPublishedAt = localMetadata?.commitPublishedAt.orEmpty()
            ),
            localMetadata = localMetadata
        )
    }

    private fun mergeVersionUpdateState(
        previousInfo: CoreInfo?,
        refreshedInfo: CoreInfo,
        refreshedMetadata: CoreSourceMetadata?
    ): CoreInfo {
        if (refreshedInfo.isInstalled.not() || refreshedInfo.sourceMismatch) {
            return refreshedInfo
        }

        val latestKnownVersionLabel = previousInfo?.availableVersion
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: latestVersionLabel(
                refreshedInfo.variant,
                resolveRepo(refreshedInfo.variant),
                resolveBranch(refreshedInfo.variant)
            )
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            .orEmpty()

        val previousAvailable = CoreRemoteParsing.parseAvailableVersionLabel(latestKnownVersionLabel)
        val localVersion = refreshedInfo.version?.removePrefix("v")?.trim().orEmpty()
        val localSha = refreshedMetadata?.commitSha?.trim().orEmpty()
        val remoteSha = previousInfo?.remoteCommit?.sha?.trim().orEmpty()
            .ifBlank { previousAvailable.commitSha }
        val changedPullRequests = CorePullRequestUpdatePolicy.changedHeads(
            refreshedMetadata, previousInfo?.updatedPullRequestHeads.orEmpty()
        )
        val stillHasUpdate = when {
            changedPullRequests.isNotEmpty() -> true
            remoteSha.isNotBlank() && localSha.isNotBlank() && CoreRemoteParsing.commitShasEquivalent(remoteSha, localSha) -> false
            previousInfo?.updateRelation == CoreUpdateRelation.Identical ||
                previousInfo?.updateRelation == CoreUpdateRelation.LocalAhead -> false
            previousInfo?.updateRelation?.hasRemoteUpdate == true -> true
            remoteSha.isNotBlank() && localSha.isNotBlank() -> true
            remoteSha.isNotBlank() -> true
            localVersion.isBlank() || previousAvailable.version.isBlank() ->
                previousInfo?.hasVersionUpdate == true
            else -> compareVersions(previousAvailable.version, localVersion) > 0
        }
        return refreshedInfo.copy(
            availableVersion = latestKnownVersionLabel.ifBlank { null }.takeIf { stillHasUpdate },
            hasVersionUpdate = stillHasUpdate,
            updatedPullRequestHeads = changedPullRequests,
            remoteVersion = previousInfo?.remoteVersion,
            remoteBranch = previousInfo?.remoteBranch,
            remoteCommit = previousInfo?.remoteCommit,
            updateRelation = if (changedPullRequests.isNotEmpty()) {
                CoreUpdateRelation.Changed
            } else if (
                remoteSha.isNotBlank() && localSha.isNotBlank() && CoreRemoteParsing.commitShasEquivalent(remoteSha, localSha)
            ) {
                CoreUpdateRelation.Identical
            } else {
                previousInfo?.updateRelation ?: CoreUpdateRelation.Unknown
            },
            updateCheckError = previousInfo?.updateCheckError,
            updateCheckedAtEpochMillis = previousInfo?.updateCheckedAtEpochMillis
        )
    }

    @Synchronized
    fun startWatcher(mode: RunMode) {
        if (mode != RunMode.Normal) {
            watcher?.stop()
            watcher = null
            return
        }

        val projectDir = coreProjectDir()
        val projectPath = projectDir.absolutePath
        val current = watcher
        if (current != null && current.rootPath == projectPath) return

        current?.stop()
        watcher = null

        if (!projectDir.exists() || !projectDir.isDirectory) return
        val created = CoreDirWatcher(projectDir) { changedPath ->
            scheduleRefresh("检测到核心目录变更：$changedPath")
        }
        created.start()
        watcher = created
    }

    private fun scheduleRefresh(reason: String) {
        pendingRefreshReason = reason
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(CORE_REFRESH_DEBOUNCE_MS)
            if (pendingRefreshReason == null) return@launch
            pendingRefreshReason = null
            try {
                refresh()
            } catch (e: Exception) {
                logWarning("debouncedRefreshCoreInfo 刷新失败", e)
            }
        }
    }

}
