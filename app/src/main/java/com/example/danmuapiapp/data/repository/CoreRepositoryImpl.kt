package com.example.danmuapiapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.FileObserver
import android.os.Looper
import android.util.Log
import androidx.core.net.toUri
import com.example.danmuapiapp.BuildConfig
import com.example.danmuapiapp.data.remote.github.GithubRemoteService
import com.example.danmuapiapp.data.service.CoreVersionParser
import com.example.danmuapiapp.data.service.GithubPullRequestService
import com.example.danmuapiapp.data.service.GithubProxyService
import com.example.danmuapiapp.data.service.NodeProjectManager
import com.example.danmuapiapp.data.service.PullRequestMergeService
import com.example.danmuapiapp.data.service.RootShell
import com.example.danmuapiapp.data.service.RootRuntimeController
import com.example.danmuapiapp.data.service.RuntimeDependencyHealthChecker
import com.example.danmuapiapp.data.service.RuntimeModePrefs
import com.example.danmuapiapp.data.service.RuntimePaths
import com.example.danmuapiapp.data.util.ShellUtils.shellQuote
import com.example.danmuapiapp.domain.model.*
import com.example.danmuapiapp.domain.repository.CoreRepository
import com.example.danmuapiapp.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.*
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoreRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val githubRemoteService: GithubRemoteService,
    private val githubProxyService: GithubProxyService,
    private val settingsRepository: SettingsRepository,
    private val runtimeDependencyPackManager: RuntimeDependencyPackManager,
    private val githubPullRequestService: GithubPullRequestService,
    private val pullRequestMergeService: PullRequestMergeService
) : CoreRepository {

    companion object {
        private const val TAG = "CoreRepo"
        private const val USER_AGENT = "DanmuApiApp"
        private const val CORE_REFRESH_DEBOUNCE_MS = 800L
        private const val WORK_DIR_PREFS = "danmu_work_dir"
        private const val WORK_DIR_KEY_CUSTOM_BASE_PATH = "custom_path"
        private const val RUNTIME_PREFS = "runtime"
        private const val CORE_SOURCE_METADATA_FILE = ".danmuapiapp-core-source.json"
        private const val OPERATION_MARKER_FILE = ".danmuapiapp-operation"
        private const val CANDIDATE_PREFS = "core_candidate_recovery"
        private const val CANDIDATE_KEY = "pending_candidate"
        private const val MARKED_STAGING_RETENTION_MS = 60L * 60L * 1000L
        private const val LEGACY_STAGING_RETENTION_MS = 24L * 60L * 60L * 1000L
    }

    private fun logRecoverableWarning(message: String, throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        if (BuildConfig.DEBUG) {
            Log.w(TAG, message, throwable)
        } else {
            val summary = throwable.message?.takeIf { it.isNotBlank() }
                ?: throwable::class.java.simpleName
            Log.w(TAG, "$message：$summary")
        }
    }

    @Serializable
    private data class CoreSourceMetadata(
        val repo: String = "",
        val branch: String = "",
        val commitSha: String = "",
        val commitPublishedAt: String = "",
        val versionLabel: String = "",
        val pullRequestNumbers: List<Int> = emptyList(),
        val pullRequestHeadShas: List<String> = emptyList(),
        val localMergeSha: String = ""
    )

    private data class CoreRemoteSource(
        val release: GithubRelease,
        val metadata: CoreSourceMetadata? = null
    )

    private enum class PendingCoreMutationType {
        ReplaceCore,
        RepairInstalledDependencies
    }

    private data class PendingCoreMutation(
        val operationId: Long,
        val repair: CoreDependencyRepairRequest,
        val stagingDir: File,
        val targetDir: File,
        val mode: RunMode,
        val rootDirPath: String,
        val sourceMetadata: CoreSourceMetadata?,
        val type: PendingCoreMutationType = PendingCoreMutationType.ReplaceCore
    )

    @Serializable
    private data class PersistedCoreCandidate(
        val variantKey: String,
        val runModeKey: String,
        val actionLabel: String,
        val installedAtMs: Long,
        val targetDirPath: String,
        val rootDirPath: String,
        val backupDirPath: String = ""
    )

    private data class BranchHeadInfo(
        val sha: String,
        val publishedAt: String,
        val title: String
    )

    private data class LatestRemoteVersionCacheEntry(
        val versionLabel: String,
        val repo: String,
        val branch: String
    )

    private val workDirPrefs = context.getSharedPreferences(WORK_DIR_PREFS, Context.MODE_PRIVATE)
    private val runtimePrefs = context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
    private val candidatePrefs = context.getSharedPreferences(CANDIDATE_PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when {
            prefs === workDirPrefs && key == WORK_DIR_KEY_CUSTOM_BASE_PATH -> refreshCoreInfo()
            prefs === runtimePrefs &&
                (key == RuntimeModePrefs.KEY_RUN_MODE || key == RuntimeModePrefs.KEY_ROOT_MODE_LEGACY) -> {
                refreshCoreInfo()
            }
        }
    }

    private val _coreInfoList = MutableStateFlow(
        ApiVariant.entries.map { variant ->
            CoreInfo(
                variant = variant,
                version = null,
                isInstalled = false
            )
        }
    )
    override val coreInfoList: StateFlow<List<CoreInfo>> = _coreInfoList.asStateFlow()
    private val _isCoreInfoLoading = MutableStateFlow(true)
    override val isCoreInfoLoading: StateFlow<Boolean> = _isCoreInfoLoading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(CoreDownloadProgress())
    override val downloadProgress: StateFlow<CoreDownloadProgress> = _downloadProgress.asStateFlow()
    private val _pendingDependencyRepair = MutableStateFlow<CoreDependencyRepairRequest?>(null)
    override val pendingDependencyRepair: StateFlow<CoreDependencyRepairRequest?> =
        _pendingDependencyRepair.asStateFlow()
    private val coreOperationLock = Any()
    private val mutationMutex = Mutex()
    private val nextOperationId = AtomicLong(0L)
    private val _operationState = MutableStateFlow(CoreOperationState())
    override val operationState: StateFlow<CoreOperationState> = _operationState.asStateFlow()
    private val _candidateState = MutableStateFlow(loadPersistedCandidate()?.toPublicState())
    override val candidateState: StateFlow<CoreCandidateState?> = _candidateState.asStateFlow()
    @Volatile
    private var pendingCoreMutation: PendingCoreMutation? = null
    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var coreWatcher: CoreDirWatcher? = null
    private var coreRefreshJob: Job? = null
    private var refreshAllJob: Job? = null
    private var pendingCoreRefreshReason: String? = null
    private var hasLoadedCoreInfoOnce = false
    private val refreshTicket = AtomicLong(0L)
    private val latestRemoteVersionCache = ConcurrentHashMap<ApiVariant, LatestRemoteVersionCacheEntry>()

    init {
        workDirPrefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        runtimePrefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        repoScope.launch {
            settingsRepository.customCoreSource
                .map { it.repo to it.branch }
                .distinctUntilChanged()
                .collect {
                    refreshCoreInfo()
                }
        }
        ensureCoreDirWatcher(currentRunMode())
        refreshCoreInfo()
        repoScope.launch {
            cleanupStaleCoreArtifacts()
            restoreRecordedDependencyRepair()
        }
    }

    private fun loadPersistedCandidate(): PersistedCoreCandidate? {
        val raw = candidatePrefs.getString(CANDIDATE_KEY, null)?.trim().orEmpty()
        if (raw.isBlank()) return null
        return runCatching { json.decodeFromString<PersistedCoreCandidate>(raw) }
            .getOrNull()
            ?.takeIf(::isCandidatePathValid)
    }

    private fun PersistedCoreCandidate.toPublicState(): CoreCandidateState? {
        val variant = ApiVariant.entries.firstOrNull { it.key == variantKey } ?: return null
        val mode = RunMode.fromKey(runModeKey) ?: return null
        return CoreCandidateState(
            variant = variant,
            runMode = mode,
            actionLabel = actionLabel,
            installedAtMs = installedAtMs,
            hasRecoveryPoint = backupDirPath.isNotBlank() && File(backupDirPath).isDirectory
        )
    }

    private fun isCandidatePathValid(candidate: PersistedCoreCandidate): Boolean {
        val variant = ApiVariant.entries.firstOrNull { it.key == candidate.variantKey } ?: return false
        val mode = RunMode.fromKey(candidate.runModeKey) ?: return false
        val expected = getCoreLocation(variant, mode)
        val target = runCatching { File(candidate.targetDirPath).canonicalFile }.getOrNull() ?: return false
        val expectedTarget = runCatching { expected.normalDir.canonicalFile }.getOrNull() ?: return false
        if (target != expectedTarget || candidate.rootDirPath != expected.rootDirPath) return false
        if (candidate.backupDirPath.isBlank()) return true
        val backup = runCatching { File(candidate.backupDirPath).canonicalFile }.getOrNull() ?: return false
        return backup.parentFile == expectedTarget.parentFile &&
            backup.name.startsWith("${expectedTarget.name}.backup-")
    }

    private fun saveCandidate(candidate: PersistedCoreCandidate) {
        val previous = loadPersistedCandidate()
        if (previous?.backupDirPath?.isNotBlank() == true &&
            previous.backupDirPath != candidate.backupDirPath
        ) {
            runCatching { File(previous.backupDirPath).deleteRecursively() }
        }
        if (!candidatePrefs.edit().putString(CANDIDATE_KEY, json.encodeToString(candidate)).commit()) {
            throw IOException("无法保存核心启动观察记录")
        }
        _candidateState.value = candidate.toPublicState()
    }

    private fun clearCandidate(candidate: PersistedCoreCandidate, deleteBackup: Boolean) {
        if (deleteBackup && candidate.backupDirPath.isNotBlank()) {
            runCatching { File(candidate.backupDirPath).deleteRecursively() }
        }
        candidatePrefs.edit().remove(CANDIDATE_KEY).commit()
        _candidateState.value = null
    }

    override suspend fun confirmCandidateCore(
        variant: ApiVariant,
        runMode: RunMode
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            mutationMutex.withLock {
                val candidate = loadPersistedCandidate() ?: return@withLock false
                if (candidate.variantKey != variant.key || candidate.runModeKey != runMode.key) {
                    return@withLock false
                }
                clearCandidate(candidate, deleteBackup = true)
                true
            }
        }
    }

    override suspend fun restoreCandidateCore(
        variant: ApiVariant,
        runMode: RunMode
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            mutationMutex.withLock {
                val candidate = loadPersistedCandidate() ?: return@withLock false
                if (candidate.variantKey != variant.key || candidate.runModeKey != runMode.key) {
                    return@withLock false
                }
                if (candidate.backupDirPath.isBlank()) {
                    clearCandidate(candidate, deleteBackup = false)
                    return@withLock false
                }
                val targetDir = File(candidate.targetDirPath)
                val backupDir = File(candidate.backupDirPath)
                if (!backupDir.isDirectory) {
                    clearCandidate(candidate, deleteBackup = false)
                    return@withLock false
                }
                if (targetDir.exists() && !targetDir.deleteRecursively()) {
                    throw IOException("无法移除启动失败的候选核心")
                }
                moveDirectory(backupDir, targetDir)
                if (!NodeProjectManager.hasValidCore(targetDir)) {
                    throw IOException("恢复点中的核心文件不完整")
                }
                if (runMode != RunMode.Normal) {
                    syncCoreDirToRoot(targetDir, candidate.rootDirPath)
                }
                clearCandidate(candidate, deleteBackup = false)
                refreshCoreInfo()
                true
            }
        }
    }

    private fun beginCoreOperation(variant: ApiVariant?, actionLabel: String): Long {
        synchronized(coreOperationLock) {
            val active = _operationState.value
            if (active.isActive) {
                throw IOException(
                    "已有${active.variant?.label.orEmpty()}${active.actionLabel}任务正在进行，请先完成或取消"
                )
            }
            val operationId = nextOperationId.incrementAndGet()
            _operationState.value = CoreOperationState(
                operationId = operationId,
                variant = variant,
                actionLabel = actionLabel,
                phase = CoreOperationPhase.Running
            )
            return operationId
        }
    }

    private fun markOperationAwaitingRepair(operationId: Long) {
        synchronized(coreOperationLock) {
            val current = _operationState.value
            if (current.operationId != operationId || !current.isActive) return
            _operationState.value = current.copy(phase = CoreOperationPhase.AwaitingDependencyRepair)
        }
    }

    private fun claimPendingOperation(operationId: Long, actionLabel: String): PendingCoreMutation {
        synchronized(coreOperationLock) {
            val current = _operationState.value
            val pending = pendingCoreMutation
            if (current.operationId != operationId || pending?.operationId != operationId) {
                throw IOException("依赖修复任务已变化，请使用当前提示重新操作")
            }
            if (!pending.stagingDir.isDirectory) {
                pendingCoreMutation = null
                _pendingDependencyRepair.value = null
                _operationState.value = CoreOperationState()
                throw IOException("待修复的候选核心已失效，请重新执行${pending.repair.actionLabel}")
            }
            if (current.phase != CoreOperationPhase.AwaitingDependencyRepair) {
                throw IOException("${current.actionLabel.ifBlank { actionLabel }}任务正在处理中，请稍候")
            }
            _operationState.value = current.copy(phase = CoreOperationPhase.Running)
            return pending
        }
    }

    private fun finishCoreOperation(operationId: Long) {
        synchronized(coreOperationLock) {
            if (_operationState.value.operationId == operationId) {
                _operationState.value = CoreOperationState()
            }
        }
    }

    private suspend fun runOwnedCoreOperation(
        variant: ApiVariant,
        actionLabel: String,
        block: suspend (Long) -> Unit
    ): Result<Unit> {
        val operationId = runCatching { beginCoreOperation(variant, actionLabel) }
            .getOrElse { return Result.failure(it) }
        return try {
            block(operationId)
            finishCoreOperation(operationId)
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            clearPendingCoreMutation(operationId, deleteStaging = true)
            finishCoreOperation(operationId)
            throw cancelled
        } catch (required: CoreDependencyRepairRequiredException) {
            markOperationAwaitingRepair(operationId)
            Result.failure(required)
        } catch (error: Exception) {
            clearPendingCoreMutation(operationId, deleteStaging = true)
            finishCoreOperation(operationId)
            Result.failure(error)
        }
    }

    override fun isCoreInstalled(variant: ApiVariant): Boolean {
        val cached = _coreInfoList.value.find { it.variant == variant }?.isInstalled ?: false
        if (cached) return true

        val isMainThread = Looper.myLooper() == Looper.getMainLooper()
        val mode = currentRunMode()
        if (mode != RunMode.Normal && isMainThread) {
            // 避免主线程触发 su 检测导致界面卡顿，交给后台刷新后再更新状态。
            refreshCoreInfo()
            return false
        }
        return hasValidCore(variant, mode)
    }

    override fun isCoreReady(variant: ApiVariant): Boolean {
        val cached = _coreInfoList.value.find { it.variant == variant }
        if (cached?.isInstalled == true) return cached.isReady

        val isMainThread = Looper.myLooper() == Looper.getMainLooper()
        val mode = currentRunMode()
        if (mode != RunMode.Normal && isMainThread) {
            refreshCoreInfo()
            return cached?.isReady == true
        }
        return loadCoreState(variant, mode).info.isReady
    }

    override fun refreshCoreInfo() {
        refreshAllJob?.cancel()
        if (!hasLoadedCoreInfoOnce) {
            _isCoreInfoLoading.value = true
        }
        val ticket = refreshTicket.incrementAndGet()
        refreshAllJob = repoScope.launch {
            try {
                val mode = currentRunMode()
                ensureCoreDirWatcher(mode)
                val previous = _coreInfoList.value
                val refreshed = ApiVariant.entries.map { loadCoreState(it, mode) }
                val merged = refreshed.map { state ->
                    mergeVersionUpdateState(
                        previousInfo = previous.find { it.variant == state.info.variant },
                        refreshedInfo = state.info,
                        refreshedMetadata = state.localMetadata
                    )
                }
                _coreInfoList.value = merged
            } finally {
                if (refreshTicket.get() == ticket) {
                    hasLoadedCoreInfoOnce = true
                    _isCoreInfoLoading.value = false
                }
            }
        }
    }

    private data class LoadedCoreState(
        val info: CoreInfo,
        val localMetadata: CoreSourceMetadata?
    )

    private fun loadCoreState(variant: ApiVariant, mode: RunMode): LoadedCoreState {
        val location = getCoreLocation(variant, mode)
        if (mode == RunMode.Normal) {
            NodeProjectManager.normalizeCoreLayout(location.normalDir)
        }
        val installed = hasValidCore(variant, mode)
        val version = if (installed) readLocalCoreVersion(variant, mode) else null
        val localMetadata = if (installed) readLocalCoreSourceMetadata(variant, mode) else null
        val desiredSourceText = if (variant == ApiVariant.Custom && installed) buildDesiredCustomSourceText() else ""
        val sourceStatus = if (variant == ApiVariant.Custom && installed) {
            resolveCustomSourceStatus(
                localMetadata = localMetadata,
                desiredRepo = resolveRepo(variant),
                desiredBranch = resolveBranch(variant)
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
                pullRequestNumbers = localMetadata?.pullRequestNumbers.orEmpty()
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
            ?: latestRemoteVersionCache[refreshedInfo.variant]
                ?.takeIf { it.repo == resolveRepo(refreshedInfo.variant) && it.branch == (resolveBranch(refreshedInfo.variant) ?: "") }
                ?.versionLabel
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            ?: return refreshedInfo

        val previousAvailable = parseAvailableVersionLabel(latestKnownVersionLabel)
        val localVersion = refreshedInfo.version?.removePrefix("v")?.trim().orEmpty()
        val localSha = refreshedMetadata?.commitSha?.trim().orEmpty()
        val stillHasUpdate = when {
            previousAvailable.commitSha.isNotBlank() && localSha.isNotBlank() ->
                commitShasEquivalent(previousAvailable.commitSha, localSha).not()
            previousAvailable.commitSha.isNotBlank() -> true
            localVersion.isBlank() || previousAvailable.version.isBlank() ->
                true
            else -> compareVersions(previousAvailable.version, localVersion) > 0
        }
        return if (stillHasUpdate) {
            refreshedInfo.copy(
                hasVersionUpdate = true,
                availableVersion = latestKnownVersionLabel
            )
        } else {
            refreshedInfo
        }
    }

    private data class CoreLocation(
        val mode: RunMode,
        val normalDir: File,
        val rootDirPath: String
    )

    private fun currentRunMode(): RunMode = RuntimePaths.currentRunMode(context)

    private fun getCoreLocation(variant: ApiVariant, mode: RunMode = currentRunMode()): CoreLocation {
        val normalDir = File(RuntimePaths.normalProjectDir(context), "danmu_api_${variant.key}")
        val rootDirPath = "${RuntimePaths.rootProjectDir(context).absolutePath}/danmu_api_${variant.key}"
        return CoreLocation(
            mode = mode,
            normalDir = normalDir,
            rootDirPath = rootDirPath
        )
    }

    private fun hasValidCore(variant: ApiVariant, mode: RunMode): Boolean {
        val location = getCoreLocation(variant, mode)
        return when (corePresenceSourceFor(mode)) {
            CorePresenceSource.NormalDir -> NodeProjectManager.hasValidCore(location.normalDir)
            CorePresenceSource.RootDir -> rootHasValidCore(location.rootDirPath)
        }
    }

    private fun readLocalCoreVersion(variant: ApiVariant, mode: RunMode): String? {
        val location = getCoreLocation(variant, mode)
        return when (corePresenceSourceFor(mode)) {
            CorePresenceSource.NormalDir -> NodeProjectManager.readCoreVersion(location.normalDir)
            CorePresenceSource.RootDir -> rootReadCoreVersion(location.rootDirPath)
        }
    }

    private fun apiUrlCandidates(path: String): List<String> = githubRemoteService.apiUrlCandidates(path)

    private fun rawUrlCandidates(repo: String, filePath: String): List<String> =
        githubRemoteService.rawUrlCandidates(repo, filePath)

    private fun withProxyCandidates(url: String): List<String> = githubRemoteService.withProxyCandidates(url)

    private fun requestText(urls: List<String>, headers: Map<String, String>): String? =
        githubRemoteService.requestText(urls, headers)

    private fun <T> requestMapped(
        urls: List<String>,
        headers: Map<String, String>,
        mapper: (String) -> T?
    ): T? = githubRemoteService.requestMapped(urls, headers, mapper)

    private fun fetchLatestRelease(repo: String): GithubRelease? {
        return githubRemoteService.fetchLatestRelease(repo)?.let { release ->
            GithubRelease(
                tagName = release.tagName,
                name = release.name,
                body = release.body,
                publishedAt = release.publishedAt,
                zipballUrl = release.zipballUrl
            )
        }
    }

    private fun fetchVersionFromGlobals(
        repo: String,
        branches: List<String> = defaultBranchCandidates()
    ): String? = githubRemoteService.fetchVersionFromGlobals(repo, branches)

    private fun defaultBranchCandidates(): List<String> = listOf("main", "master")

    override suspend fun checkUpdate(variant: ApiVariant): GithubRelease? =
        withContext(Dispatchers.IO) {
            try {
                resolveRemoteSource(variant)?.release
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }

    private fun currentCustomCoreSource(): ResolvedCustomCoreSource = settingsRepository.customCoreSource.value

    private fun resolveRepo(variant: ApiVariant): String {
        return if (variant == ApiVariant.Custom) currentCustomCoreSource().repo else variant.repo
    }

    private fun resolveBranch(variant: ApiVariant): String? {
        return if (variant == ApiVariant.Custom) {
            currentCustomCoreSource().branch.ifBlank { DEFAULT_CUSTOM_CORE_BRANCH }
        } else {
            null
        }
    }

    private fun resolveBranchCandidates(variant: ApiVariant): List<String> {
        return if (variant == ApiVariant.Custom) {
            listOf(resolveBranch(variant) ?: DEFAULT_CUSTOM_CORE_BRANCH)
        } else {
            defaultBranchCandidates()
        }
    }

    private fun resolveRemoteSource(variant: ApiVariant): CoreRemoteSource? {
        val repo = resolveRepo(variant)
        if (repo.isBlank()) return null

        val branch = resolveBranch(variant)
        if (!branch.isNullOrBlank()) {
            return fetchBranchRemoteSource(repo, branch)
                ?: throw IOException("未找到分支 $branch，请检查仓库与分支名")
        }

        fetchLatestRelease(repo)?.let { return CoreRemoteSource(release = it) }

        resolveBranchCandidates(variant).forEach { candidate ->
            fetchBranchRemoteSource(repo, candidate)?.let { return it }
        }
        return null
    }

    private fun fetchBranchRemoteSource(repo: String, branch: String): CoreRemoteSource? {
        val resolvedBranch = resolveRemoteBranchName(repo, branch) ?: return null
        val versionLabel = fetchVersionFromGlobals(repo, listOf(resolvedBranch)).orEmpty()
        val head = fetchBranchHead(repo, resolvedBranch)
        if (head == null) {
            if (versionLabel.isBlank()) return null
            val fallback = buildBranchRemoteFallbackPlan(repo, resolvedBranch, versionLabel)
            return CoreRemoteSource(
                release = GithubRelease(
                    tagName = fallback.tagName,
                    name = fallback.name,
                    body = "",
                    publishedAt = "",
                    zipballUrl = fallback.zipballUrl
                ),
                metadata = CoreSourceMetadata(
                    repo = repo,
                    branch = resolvedBranch,
                    commitSha = "",
                    commitPublishedAt = "",
                    versionLabel = fallback.versionLabel
                )
            )
        }

        val shortSha = head.sha.take(7)
        val branchTag = versionLabel.ifBlank { resolvedBranch }
        val branchName = buildString {
            append(resolvedBranch)
            if (shortSha.isNotBlank()) {
                append(" @ ")
                append(shortSha)
            }
        }
        return CoreRemoteSource(
            release = GithubRelease(
                tagName = branchTag,
                name = branchName,
                body = head.title,
                publishedAt = head.publishedAt,
                zipballUrl = buildBranchZipUrl(repo, resolvedBranch)
            ),
            metadata = CoreSourceMetadata(
                repo = repo,
                branch = resolvedBranch,
                commitSha = head.sha,
                commitPublishedAt = head.publishedAt,
                versionLabel = versionLabel
            )
        )
    }

    private fun resolveRemoteBranchName(repo: String, requestedBranch: String): String? {
        val normalized = requestedBranch.trim()
            .removePrefix("refs/heads/")
            .trim()
            .trim('/')
        if (normalized.isBlank()) return null

        if (fetchBranchHead(repo, normalized) != null) return normalized
        if (!fetchVersionFromGlobals(repo, listOf(normalized)).isNullOrBlank()) return normalized

        val branches = fetchBranchList(repo)
        val direct = branches.firstOrNull { it.equals(normalized, ignoreCase = true) }
        if (direct != null) return direct

        val suffixMatches = branches.filter { branchName ->
            branchName.substringAfterLast('/').equals(normalized, ignoreCase = true) ||
                branchName.endsWith("/$normalized", ignoreCase = true)
        }
        return suffixMatches.singleOrNull()
    }

    private fun fetchBranchList(repo: String): List<String> {
        val apiBranches = requestMapped(
            urls = apiUrlCandidates("repos/$repo/branches?per_page=100"),
            headers = mapOf(
                "Accept" to "application/vnd.github+json",
                "User-Agent" to USER_AGENT
            )
        ) { body ->
            val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonArray
                ?: return@requestMapped null
            root.mapNotNull { element ->
                ((element as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull?.trim()
                    ?.takeIf { it.isNotBlank() }
            }.takeIf { it.isNotEmpty() }
        }
        if (!apiBranches.isNullOrEmpty()) return apiBranches

        return fetchBranchListFromHtml(repo)
    }

    private fun fetchBranchListFromHtml(repo: String): List<String> {
        val escapedRepo = Regex.escape(repo)
        val branchRegex = Regex("""href=["']/""" + escapedRepo + """/tree/([^"'?#]+)["']""")
        return requestMapped(
            urls = withProxyCandidates("https://github.com/$repo/branches/all"),
            headers = mapOf("User-Agent" to USER_AGENT)
        ) { body ->
            branchRegex.findAll(body)
                .mapNotNull { match ->
                    match.groupValues.getOrNull(1)
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                }
                .toList()
                .distinct()
                .takeIf { it.isNotEmpty() }
        } ?: emptyList()
    }

    private fun fetchBranchHead(repo: String, branch: String): BranchHeadInfo? {
        val encodedBranch = encodeUrlPart(branch)
        return requestMapped(
            urls = apiUrlCandidates("repos/$repo/commits/$encodedBranch"),
            headers = mapOf(
                "Accept" to "application/vnd.github+json",
                "User-Agent" to USER_AGENT
            )
        ) { body ->
            val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
                ?: return@requestMapped null
            val sha = (root["sha"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (sha.isBlank()) return@requestMapped null
            val commitObj = root["commit"] as? JsonObject
            val message = (commitObj?.get("message") as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            val title = message.lineSequence().firstOrNull()?.trim().orEmpty().ifBlank { "提交 ${sha.take(7)}" }
            val authorObj = commitObj?.get("author") as? JsonObject
            val publishedAt = (authorObj?.get("date") as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            BranchHeadInfo(
                sha = sha,
                publishedAt = publishedAt,
                title = title
            )
        }
    }

    private fun encodeUrlPart(raw: String): String {
        return URLEncoder.encode(raw, Charsets.UTF_8.name()).replace("+", "%20")
    }

    private fun buildBranchZipUrl(repo: String, branch: String): String {
        return "https://api.github.com/repos/$repo/zipball/${encodeUrlPart(branch)}"
    }

    override suspend fun installCore(variant: ApiVariant): Result<Unit> =
        installOrUpdateCore(variant, actionLabel = "安装")

    override suspend fun updateCore(variant: ApiVariant): Result<Unit> =
        installOrUpdateCore(variant, actionLabel = "更新")

    override suspend fun deleteCore(variant: ApiVariant): Result<Unit> =
        withContext(Dispatchers.IO) {
            runOwnedCoreOperation(variant, "删除") { operationId ->
                val mode = currentRunMode()
                val location = getCoreLocation(variant, mode)
                mutationMutex.withLock {
                    loadPersistedCandidate()
                        ?.takeIf { it.variantKey == variant.key && it.runModeKey == mode.key }
                        ?.let { clearCandidate(it, deleteBackup = true) }
                    if (mode != RunMode.Normal) {
                        deleteRootCoreDir(location.rootDirPath)
                    }
                    if (location.normalDir.exists() && !location.normalDir.deleteRecursively()) {
                        throw IOException("删除核心目录失败：${location.normalDir.absolutePath}")
                    }
                }
                RuntimeDependencyHealthChecker.clearPendingIssue(context, variant)
                refreshCoreInfo()
            }
        }

    override suspend fun checkAndMarkUpdate(variant: ApiVariant) {
        withContext(Dispatchers.IO) {
            try {
                val info = _coreInfoList.value.find { it.variant == variant } ?: return@withContext
                if (!info.isInstalled) return@withContext
                val remoteSource = resolveRemoteSource(variant) ?: return@withContext
                val remoteVersion = remoteSource.metadata?.versionLabel
                    ?.removePrefix("v")
                    ?.trim()
                    .orEmpty()
                val localVersion = info.version?.removePrefix("v")?.trim().orEmpty()
                val localMetadata = readLocalCoreSourceMetadata(variant, currentRunMode())
                val remoteSha = remoteSource.metadata?.commitSha?.trim().orEmpty()
                val localSha = localMetadata?.commitSha?.trim().orEmpty()
                val sourceStatus = if (variant == ApiVariant.Custom) {
                    resolveCustomSourceStatus(
                        localMetadata = localMetadata,
                        desiredRepo = resolveRepo(variant),
                        desiredBranch = resolveBranch(variant)
                    )
                } else {
                    CoreSourceStatus.NotApplicable
                }
                val sourceMismatch = sourceStatus == CoreSourceStatus.Mismatched
                val hasVersionUpdate = when {
                    sourceMismatch -> false
                    remoteSha.isNotBlank() && localSha.isNotBlank() -> !commitShasEquivalent(remoteSha, localSha)
                    remoteVersion.isNotBlank() && localVersion.isNotBlank() ->
                        compareVersions(remoteVersion, localVersion) > 0
                    else -> false
                }
                val availableVersionLabel = buildLatestVersionLabel(remoteVersion, remoteSha)
                if (availableVersionLabel.isNotBlank()) {
                    latestRemoteVersionCache[variant] = LatestRemoteVersionCacheEntry(
                        versionLabel = availableVersionLabel,
                        repo = resolveRepo(variant),
                        branch = resolveBranch(variant).orEmpty()
                    )
                } else {
                    latestRemoteVersionCache.remove(variant)
                }
                val desiredSource = buildDesiredCustomSourceText().ifBlank { null }
                _coreInfoList.value = _coreInfoList.value.map {
                    if (it.variant == variant) {
                        it.copy(
                            availableVersion = availableVersionLabel.ifBlank { null }.takeIf { hasVersionUpdate },
                            hasVersionUpdate = hasVersionUpdate,
                            sourceMismatch = sourceMismatch,
                            sourceStatus = sourceStatus,
                            desiredSource = desiredSource.takeIf { sourceMismatch }
                        )
                    }
                    else it
                }
            } catch (e: Exception) {
                logRecoverableWarning("checkAndMarkUpdate(${variant.key}) 失败", e)
            }
        }
    }

    override suspend fun checkAllUpdates() {
        withContext(Dispatchers.IO) {
            ApiVariant.entries.forEach { variant ->
                try { checkAndMarkUpdate(variant) } catch (e: Exception) {
                    logRecoverableWarning("checkAllUpdates: ${variant.key} 更新检查失败", e)
                }
            }
        }
    }

    override suspend fun rollbackCore(variant: ApiVariant, release: GithubRelease): Result<Unit> =
        withContext(Dispatchers.IO) {
            runOwnedCoreOperation(variant, "回退") { operationId ->
                val versionHint = release.tagName.ifBlank {
                    release.name.ifBlank { "" }
                }.ifBlank { null }
                downloadAndExtract(
                    operationId = operationId,
                    variant = variant,
                    zipUrl = release.zipballUrl,
                    versionHint = versionHint,
                    actionLabel = "回退",
                    sourceMetadata = buildRollbackMetadata(variant, release, versionHint)
                )
                refreshCoreInfo()
                refreshAllJob?.join()
                checkAndMarkUpdate(variant)
            }
        }

    override suspend fun rollbackCore(
        variant: ApiVariant,
        revision: CoreRevision
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runOwnedCoreOperation(variant, "回退") { operationId ->
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
            downloadAndExtract(
                operationId = operationId,
                variant = variant,
                zipUrl = revision.archiveUrl,
                versionHint = versionHint,
                actionLabel = "回退",
                sourceMetadata = buildRollbackMetadata(variant, release, versionHint)
            )
            refreshCoreInfo()
            refreshAllJob?.join()
            checkAndMarkUpdate(variant)
        }
    }

    override suspend fun fetchReleaseHistory(variant: ApiVariant): List<GithubRelease> =
        withContext(Dispatchers.IO) {
            val repo = resolveRepo(variant)
            if (repo.isBlank()) return@withContext emptyList()
            try {
                val commitHistory = fetchCommitHistory(repo, resolveBranchCandidates(variant))
                if (commitHistory.isNotEmpty()) return@withContext commitHistory

                fetchReleaseHistoryFromReleases(repo)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
        }

    override suspend fun fetchRevisionHistory(
        variant: ApiVariant,
        page: Int,
        pageSize: Int,
        query: String
    ): Result<CoreRevisionPage> = withContext(Dispatchers.IO) {
        runCatching {
            require(page > 0) { "页码无效" }
            require(pageSize in 1..100) { "每页数量无效" }
            val repo = resolveRepo(variant)
            if (repo.isBlank()) throw IOException("请先配置核心仓库")
            val branches = resolveBranchCandidates(variant)
            val normalizedQuery = query.trim()
            val revisionPage = if (normalizedQuery.isBlank()) {
                fetchRevisionCommits(
                    repo = repo,
                    branchCandidates = branches,
                    includeDefaultBranchFallback = variant != ApiVariant.Custom,
                    page = page,
                    pageSize = pageSize
                )
            } else {
                searchRevisionCommitsByAuthorOrKeyword(
                    repo = repo,
                    branchCandidates = branches,
                    includeDefaultBranchFallback = variant != ApiVariant.Custom,
                    query = normalizedQuery,
                    page = page,
                    pageSize = pageSize,
                    keywordSearch = {
                        if (variant == ApiVariant.Custom) {
                            searchRevisionCommitsOnBranch(
                                repo = repo,
                                branchCandidates = branches,
                                query = normalizedQuery,
                                page = page,
                                pageSize = pageSize
                            )
                        } else {
                            searchRevisionCommits(
                                repo = repo,
                                query = normalizedQuery,
                                page = page,
                                pageSize = pageSize
                            )
                        }
                    }
                )
            }
            val versionLookupLimit = Semaphore(6)
            val commits = revisionPage.revisions.map { revision ->
                async {
                    versionLookupLimit.withPermit {
                        revision.copy(
                            version = fetchRevisionVersion(repo, revision.commitSha).orEmpty()
                        )
                    }
                }
            }.awaitAll()
            CoreRevisionPage(
                revisions = commits,
                page = page,
                hasNextPage = revisionPage.hasNextPage
            )
        }
    }

    override suspend fun fetchRevisionDetails(
        variant: ApiVariant,
        revision: CoreRevision
    ): Result<CoreRevisionDetails> = withContext(Dispatchers.IO) {
        runCatching {
            val repo = resolveRepo(variant)
            if (repo.isBlank()) throw IOException("请先配置核心仓库")
            fetchRevisionDetailsFromGithub(repo, revision)
                ?: throw IOException("无法获取该提交的文件变动")
        }
    }

    override suspend fun fetchPullRequests(
        variant: ApiVariant,
        page: Int,
        pageSize: Int,
        filter: CorePullRequestFilter,
        query: String
    ): Result<CorePullRequestPage> = withContext(Dispatchers.IO) {
        runCatching {
            val repo = resolveRepo(variant)
            if (repo.isBlank()) throw IOException("请先配置自定义核心仓库")
            val branch = resolvePullRequestBaseBranch(variant, repo)
            val locallyMergedPullRequestNumbers = _coreInfoList.value
                .firstOrNull { it.variant == variant && it.isReady }
                ?.pullRequestNumbers
                .orEmpty()
            githubPullRequestService.list(
                repository = repo,
                baseBranch = branch,
                page = page,
                pageSize = pageSize,
                filter = filter,
                locallyMergedPullRequestNumbers = locallyMergedPullRequestNumbers,
                query = query
            )
        }
    }

    override suspend fun fetchPullRequestDetails(
        variant: ApiVariant,
        pullRequestNumber: Int
    ): Result<CorePullRequest> = withContext(Dispatchers.IO) {
        runCatching {
            val repo = resolveRepo(variant)
            if (repo.isBlank()) throw IOException("请先配置自定义核心仓库")
            githubPullRequestService.get(repo, pullRequestNumber)
        }
    }

    override suspend fun fetchPullRequestFiles(
        variant: ApiVariant,
        pullRequestNumber: Int,
        page: Int,
        pageSize: Int
    ): Result<CorePullRequestFilePage> = withContext(Dispatchers.IO) {
        runCatching {
            val repo = resolveRepo(variant)
            if (repo.isBlank()) throw IOException("请先配置自定义核心仓库")
            githubPullRequestService.listFiles(
                repository = repo,
                pullRequestNumber = pullRequestNumber,
                page = page,
                pageSize = pageSize
            )
        }
    }

    override suspend fun preparePullRequestStack(
        variant: ApiVariant,
        pullRequestNumbers: List<Int>
    ): Result<CoreDependencyRepairRequest> = withContext(Dispatchers.IO) {
        val operationId = runCatching { beginCoreOperation(variant, "准备 PR 组合") }
            .getOrElse { return@withContext Result.failure(it) }
        var stagingDir: File? = null
        try {
            val repo = resolveRepo(variant)
            if (repo.isBlank()) throw IOException("请先配置自定义核心仓库")
            val branch = resolvePullRequestBaseBranch(variant, repo)
            val mode = currentRunMode()
            val location = getCoreLocation(variant, mode)
            val staging = createCoreTempDir(location.normalDir, "staging")
            stagingDir = staging
            writeOperationMarker(staging, operationId)

            val merged = pullRequestMergeService.buildInto(
                repository = repo,
                baseBranch = branch,
                pullRequestNumbers = pullRequestNumbers,
                destination = staging
            ) { stage, progress ->
                updateDownloadProgress(
                    variant = variant,
                    actionLabel = "合并 PR",
                    stageText = stage,
                    progress = progress,
                    downloadedBytes = 0L,
                    totalBytes = -1L
                )
            }
            val pending = prepareStagedCore(
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
                )
            )
            setPendingCoreMutation(pending)
            markOperationAwaitingRepair(operationId)
            Result.success(pending.repair)
        } catch (cancelled: CancellationException) {
            clearPendingCoreMutation(operationId, deleteStaging = true)
            stagingDir?.let { runCatching { it.deleteRecursively() } }
            finishCoreOperation(operationId)
            throw cancelled
        } catch (error: Exception) {
            clearPendingCoreMutation(operationId, deleteStaging = true)
            stagingDir?.let { runCatching { it.deleteRecursively() } }
            finishCoreOperation(operationId)
            Result.failure(error)
        } finally {
            _downloadProgress.value = CoreDownloadProgress()
        }
    }

    private fun resolvePullRequestBaseBranch(variant: ApiVariant, repository: String): String {
        return resolveBranch(variant)?.takeIf { it.isNotBlank() }
            ?: githubPullRequestService.resolveDefaultBranch(repository)
    }

    override suspend fun repairPendingDependenciesOnline(operationId: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            val pending = runCatching { claimPendingOperation(operationId, "修复依赖") }
                .getOrElse { return@withContext Result.failure(it) }
            updateDownloadProgress(
                variant = pending.repair.variant,
                actionLabel = "修复依赖",
                stageText = "正在检查签名运行时依赖",
                progress = null,
                downloadedBytes = 0L,
                totalBytes = -1L
            )
            try {
                val result = runtimeDependencyPackManager.installIfAvailable(
                    coreDir = pending.stagingDir,
                    variant = pending.repair.variant,
                    onProgress = { stage, progress, downloadedBytes, totalBytes ->
                        updateDownloadProgress(
                            variant = pending.repair.variant,
                            actionLabel = "修复依赖",
                            stageText = stage,
                            progress = progress,
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes
                        )
                    }
                )
                verifyCoreRuntimeDependencies(
                    coreDir = pending.stagingDir,
                    unavailableReason = result.unavailableReason
                )
                markOperationAwaitingRepair(operationId)
                Result.success(Unit)
            } catch (cancelled: CancellationException) {
                markOperationAwaitingRepair(operationId)
                throw cancelled
            } catch (error: Exception) {
                markOperationAwaitingRepair(operationId)
                Result.failure(error)
            } finally {
                _downloadProgress.value = CoreDownloadProgress()
            }
        }

    override suspend fun repairPendingDependenciesFromArchive(
        operationId: Long,
        archiveUri: String
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            val pending = runCatching { claimPendingOperation(operationId, "导入依赖") }
                .getOrElse { return@withContext Result.failure(it) }
            val temporaryArchive = runCatching {
                File.createTempFile("runtime-import-", ".zip", context.cacheDir)
            }.getOrElse {
                markOperationAwaitingRepair(operationId)
                return@withContext Result.failure(it)
            }
            updateDownloadProgress(
                variant = pending.repair.variant,
                actionLabel = "导入依赖",
                stageText = "正在读取本地依赖包",
                progress = null,
                downloadedBytes = 0L,
                totalBytes = -1L
            )
            try {
                val input = context.contentResolver.openInputStream(archiveUri.toUri())
                    ?: throw IOException("无法读取选择的依赖压缩包")
                input.buffered().use { source ->
                    FileOutputStream(temporaryArchive).use { target ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val read = source.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > RuntimeDependencyPackProtocol.MAX_ARCHIVE_BYTES) {
                                throw IOException("依赖压缩包超过大小上限")
                            }
                            target.write(buffer, 0, read)
                        }
                    }
                }
                updateDownloadProgress(
                    variant = pending.repair.variant,
                    actionLabel = "导入依赖",
                    stageText = "正在校验本地依赖闭包",
                    progress = null,
                    downloadedBytes = temporaryArchive.length(),
                    totalBytes = temporaryArchive.length()
                )
                LocalRuntimeDependencyArchiveImporter.verifyAndInstall(
                    archive = temporaryArchive,
                    coreDir = pending.stagingDir,
                    runtimeNodeModulesDir = File(RuntimePaths.normalProjectDir(context), "node_modules")
                )
                verifyCoreRuntimeDependencies(pending.stagingDir)
                markOperationAwaitingRepair(operationId)
                Result.success(Unit)
            } catch (cancelled: CancellationException) {
                markOperationAwaitingRepair(operationId)
                throw cancelled
            } catch (error: Exception) {
                markOperationAwaitingRepair(operationId)
                Result.failure(error)
            } finally {
                runCatching { temporaryArchive.delete() }
                _downloadProgress.value = CoreDownloadProgress()
            }
        }

    override suspend fun applyPendingCoreMutation(operationId: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            val pending = runCatching { claimPendingOperation(operationId, "应用依赖") }
                .getOrElse { return@withContext Result.failure(it) }
            try {
                mutationMutex.withLock {
                    when (pending.type) {
                        PendingCoreMutationType.ReplaceCore -> {
                            applyStagedCore(pending)
                            persistResolvedCustomSourceIfNeeded(
                                variant = pending.repair.variant,
                                metadata = pending.sourceMetadata
                            )
                        }
                        PendingCoreMutationType.RepairInstalledDependencies -> {
                            applyStagedDependencies(pending)
                        }
                    }
                }
                RuntimeDependencyHealthChecker.clearPendingIssue(context, pending.repair.variant)
                refreshCoreInfo()
                clearPendingCoreMutation(operationId, deleteStaging = true)
                finishCoreOperation(operationId)
                Result.success(Unit)
            } catch (cancelled: CancellationException) {
                markOperationAwaitingRepair(operationId)
                throw cancelled
            } catch (error: Exception) {
                clearPendingCoreMutation(operationId, deleteStaging = true)
                finishCoreOperation(operationId)
                Result.failure(error)
            }
        }

    override suspend fun discardPendingCoreMutation(operationId: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { claimPendingOperation(operationId, "取消任务") }
                .fold(
                    onSuccess = {
                        clearPendingCoreMutation(operationId, deleteStaging = true)
                        finishCoreOperation(operationId)
                        Result.success(Unit)
                    },
                    onFailure = { Result.failure(it) }
                )
        }

    override suspend fun applyWorkDirectoryChange(
        targetPath: String?,
        migrateSelectedCore: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        val operationId = runCatching { beginCoreOperation(null, "切换工作目录") }
            .getOrElse { return@withContext Result.failure(it) }
        try {
            val applyResult = mutationMutex.withLock {
                RuntimePaths.applyCustomBaseDir(
                    context = context,
                    targetPath = targetPath,
                    switchMode = if (migrateSelectedCore) {
                        RuntimePaths.WorkDirSwitchMode.MigrateSelectedCore
                    } else {
                        RuntimePaths.WorkDirSwitchMode.SwitchOnly
                    }
                )
            }
            if (!applyResult.ok) {
                throw IOException(applyResult.message)
            }
            finishCoreOperation(operationId)
            Result.success(applyResult.message)
        } catch (cancelled: CancellationException) {
            finishCoreOperation(operationId)
            throw cancelled
        } catch (error: Exception) {
            finishCoreOperation(operationId)
            Result.failure(error)
        }
    }

    override suspend fun prepareInstalledCoreDependencyRepair(
        variant: ApiVariant,
        origin: CoreDependencyRepairOrigin,
        resumeAction: RuntimeDependencyResumeAction,
        suspectedMissingPackage: String?
    ): CoreDependencyRepairRequest? = withContext(Dispatchers.IO) {
        val effectiveResumeAction = if (
            origin == CoreDependencyRepairOrigin.RuntimeStart &&
            resumeAction == RuntimeDependencyResumeAction.None
        ) {
            RuntimeDependencyResumeAction.Start
        } else {
            resumeAction
        }
        val mode = currentRunMode()
        val location = getCoreLocation(variant, mode)
        val coreDir = location.normalDir
        if (!NodeProjectManager.hasValidCore(coreDir)) return@withContext null
        NodeProjectManager.writeRuntimeDependencyRequirements(coreDir)

        val projectDir = RuntimePaths.normalProjectDir(context)
        val detectedMissing = if (NodeProjectManager.hasProjectEntry(projectDir)) {
            collectMissingCoreRuntimeDependencies(coreDir)
        } else {
            NodeProjectManager.collectMissingRuntimeDepsForCoreAgainstBundledRuntime(coreDir)
        }
        val suspectedRequirement = NodeProjectManager.runtimeDependencyRequirementForCore(
            coreDir = coreDir,
            packageName = suspectedMissingPackage
        )
        val missing = (detectedMissing + listOfNotNull(suspectedRequirement)).distinct().sorted()
        if (missing.isEmpty()) {
            RuntimeDependencyHealthChecker.clearPendingIssue(context, variant)
            return@withContext null
        }

        synchronized(coreOperationLock) {
            pendingCoreMutation?.takeIf {
                it.repair.variant == variant && it.repair.missingDependencies == missing
            }?.let { existing ->
                if (existing.type != PendingCoreMutationType.RepairInstalledDependencies) {
                    return@withContext existing.repair
                }
                val mergedResumeAction = when {
                    existing.repair.resumeAction == RuntimeDependencyResumeAction.Restart ||
                        effectiveResumeAction == RuntimeDependencyResumeAction.Restart -> {
                        RuntimeDependencyResumeAction.Restart
                    }
                    existing.repair.resumeAction == RuntimeDependencyResumeAction.Start ||
                        effectiveResumeAction == RuntimeDependencyResumeAction.Start -> {
                        RuntimeDependencyResumeAction.Start
                    }
                    else -> RuntimeDependencyResumeAction.None
                }
                if (mergedResumeAction == existing.repair.resumeAction) {
                    return@withContext existing.repair
                }
                val updatedRepair = existing.repair.copy(
                    actionLabel = when (mergedResumeAction) {
                        RuntimeDependencyResumeAction.Start -> "启动"
                        RuntimeDependencyResumeAction.Restart -> "重启"
                        RuntimeDependencyResumeAction.None -> "修复"
                    },
                    resumeAction = mergedResumeAction
                )
                pendingCoreMutation = existing.copy(repair = updatedRepair)
                _pendingDependencyRepair.value = updatedRepair
                return@withContext updatedRepair
            }
        }

        val operationId = beginCoreOperation(variant, "修复依赖")

        val stagingDir = createCoreTempDir(coreDir, "dependency-repair")
        writeOperationMarker(stagingDir, operationId)
        try {
            val packageJson = File(coreDir, "package.json")
            if (!packageJson.isFile) {
                throw IOException("当前核心缺少依赖清单，无法执行依赖修复")
            }
            packageJson.copyTo(File(stagingDir, "package.json"), overwrite = true)
            val request = CoreDependencyRepairRequest(
                operationId = operationId,
                variant = variant,
                actionLabel = when (effectiveResumeAction) {
                    RuntimeDependencyResumeAction.Start -> "启动"
                    RuntimeDependencyResumeAction.Restart -> "重启"
                    RuntimeDependencyResumeAction.None -> "修复"
                },
                missingDependencies = missing,
                candidateVersion = NodeProjectManager.readCoreVersion(coreDir),
                onlineRepairSupported = RuntimeDependencyPackProtocol.supportsOnlineRepair(variant),
                origin = origin,
                resumeAction = effectiveResumeAction
            )
            setPendingCoreMutation(
                PendingCoreMutation(
                    operationId = operationId,
                    repair = request,
                    stagingDir = stagingDir,
                    targetDir = coreDir,
                    mode = mode,
                    rootDirPath = location.rootDirPath,
                    sourceMetadata = null,
                    type = PendingCoreMutationType.RepairInstalledDependencies
                )
            )
            markOperationAwaitingRepair(operationId)
            request
        } catch (cancelled: CancellationException) {
            runCatching { stagingDir.deleteRecursively() }
            finishCoreOperation(operationId)
            throw cancelled
        } catch (error: Exception) {
            runCatching { stagingDir.deleteRecursively() }
            finishCoreOperation(operationId)
            throw error
        }
    }

    private fun setPendingCoreMutation(pending: PendingCoreMutation) {
        synchronized(coreOperationLock) {
            val current = _operationState.value
            if (current.operationId != pending.operationId || pendingCoreMutation != null) {
                throw IOException("已有核心任务正在等待处理，不能覆盖")
            }
            pendingCoreMutation = pending
            _pendingDependencyRepair.value = pending.repair
        }
    }

    private fun clearPendingCoreMutation(operationId: Long, deleteStaging: Boolean) {
        val removed = synchronized(coreOperationLock) {
            val old = pendingCoreMutation?.takeIf { it.operationId == operationId }
                ?: return@synchronized null
            pendingCoreMutation = null
            _pendingDependencyRepair.value = null
            old
        }
        if (deleteStaging && removed != null) {
            runCatching { removed.stagingDir.deleteRecursively() }
        }
    }

    private fun fetchCommitHistory(
        repo: String,
        branchCandidates: List<String>
    ): List<GithubRelease> {
        val urls = buildList {
            branchCandidates.forEach { branch ->
                addAll(apiUrlCandidates("repos/$repo/commits?sha=${encodeUrlPart(branch)}&per_page=20"))
            }
            addAll(apiUrlCandidates("repos/$repo/commits?per_page=20"))
        }.distinct()

        val result = requestMapped(
            urls = urls,
            headers = mapOf(
                "Accept" to "application/vnd.github+json",
                "User-Agent" to USER_AGENT
            )
        ) { body ->
            val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonArray
                ?: return@requestMapped null
            root.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                parseCommitAsRelease(repo, obj)
            }.takeIf { it.isNotEmpty() }
        }
        if (!result.isNullOrEmpty()) return result

        return fetchCommitHistoryFromAtom(repo, branchCandidates)
    }

    private fun fetchRevisionCommits(
        repo: String,
        branchCandidates: List<String>,
        includeDefaultBranchFallback: Boolean,
        page: Int,
        pageSize: Int,
        author: String? = null
    ): CoreRevisionPage {
        val authorParameter = author?.let { "&author=${encodeUrlPart(it)}" }.orEmpty()
        val urls = buildList {
            branchCandidates.forEach { branch ->
                addAll(
                    apiUrlCandidates(
                        "repos/$repo/commits?sha=${encodeUrlPart(branch)}" +
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

        val payload = githubRemoteService.requestTextResponse(
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

    private fun searchRevisionCommitsByAuthorOrKeyword(
        repo: String,
        branchCandidates: List<String>,
        includeDefaultBranchFallback: Boolean,
        query: String,
        page: Int,
        pageSize: Int,
        keywordSearch: () -> CoreRevisionPage
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

    private fun searchRevisionCommits(
        repo: String,
        query: String,
        page: Int,
        pageSize: Int
    ): CoreRevisionPage {
        val githubQuery = CoreRevisionSearch.query(repo, query)
        val payload = githubRemoteService.requestTextResponse(
            urls = apiUrlCandidates(
                "search/commits?q=${encodeUrlPart(githubQuery)}&sort=committer-date&order=desc" +
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

    private fun searchRevisionCommitsOnBranch(
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
                pageSize = 100
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

    private fun fetchRevisionVersion(repo: String, commitSha: String): String? {
        CoreRevisionVersionResolver.globalsPaths(commitSha).firstNotNullOfOrNull { path ->
            requestMapped(
                urls = rawUrlCandidates(repo, path),
                headers = mapOf("User-Agent" to USER_AGENT)
            ) { body -> CoreRevisionVersionResolver.parseGlobalsVersion(body) }
        }?.let { return it }

        // raw.githubusercontent.com cannot read private repositories with our
        // API-only credential. Fall back to the official Contents API at the
        // exact commit; the response is requested as raw globals.js source.
        return CoreRevisionVersionResolver.globalsFilePaths().firstNotNullOfOrNull { filePath ->
            requestMapped(
                urls = apiUrlCandidates(
                    "repos/$repo/contents/$filePath?ref=${encodeUrlPart(commitSha)}"
                ),
                headers = githubApiHeaders(
                    accept = "application/vnd.github.raw+json"
                )
            ) { body -> CoreRevisionVersionResolver.parseGlobalsVersion(body) }
        }
    }

    private fun fetchRevisionDetailsFromGithub(
        repo: String,
        revision: CoreRevision
    ): CoreRevisionDetails? {
        val files = mutableListOf<CoreRevisionFileChange>()
        val seenFilePages = mutableSetOf<List<String>>()
        var additions = 0
        var deletions = 0
        var page = 1
        while (true) {
            val pagePayload = requestMapped(
                urls = apiUrlCandidates(
                    "repos/$repo/commits/${encodeUrlPart(revision.commitSha)}?per_page=100&page=$page"
                ),
                headers = githubApiHeaders()
            ) { body ->
                val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
                    ?: return@requestMapped null
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

    private data class RevisionDetailsPage(
        val files: List<CoreRevisionFileChange>,
        val additions: Int?,
        val deletions: Int?
    )

    private fun githubApiHeaders(
        accept: String = "application/vnd.github+json"
    ): Map<String, String> = mapOf(
        "Accept" to accept,
        "X-GitHub-Api-Version" to "2022-11-28",
        "User-Agent" to USER_AGENT
    )

    private fun JsonObject.stringValue(key: String): String {
        return (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    }

    private fun JsonObject.intValue(key: String): Int {
        return (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
    }

    private fun parseCommitAsRelease(repo: String, obj: JsonObject): GithubRelease? {
        val sha = (obj["sha"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (sha.isBlank()) return null

        val shortSha = sha.take(7)
        val commitObj = obj["commit"] as? JsonObject
        val message = (commitObj?.get("message") as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val title = message.lineSequence().firstOrNull()?.trim().orEmpty().ifBlank { "提交 $shortSha" }
        val authorObj = commitObj?.get("author") as? JsonObject
        val publishedAt = (authorObj?.get("date") as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

        return GithubRelease(
            tagName = shortSha,
            name = title,
            body = message,
            publishedAt = publishedAt,
            zipballUrl = "https://api.github.com/repos/$repo/zipball/$sha"
        )
    }

    private fun fetchReleaseHistoryFromReleases(repo: String): List<GithubRelease> {
        return requestMapped(
            urls = apiUrlCandidates("repos/$repo/releases?per_page=10"),
            headers = mapOf(
                "Accept" to "application/vnd.github+json",
                "User-Agent" to USER_AGENT
            )
        ) { body ->
            val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonArray
                ?: return@requestMapped null
            root.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                parseRelease(obj.toString())
            }.takeIf { it.isNotEmpty() }
        } ?: emptyList()
    }

    private fun fetchCommitHistoryFromAtom(
        repo: String,
        branchCandidates: List<String>
    ): List<GithubRelease> {
        for (branch in branchCandidates) {
            val parsed = requestMapped(
                urls = withProxyCandidates("https://github.com/$repo/commits/${encodeUrlPart(branch)}.atom"),
                headers = mapOf(
                    "Accept" to "application/atom+xml,application/xml,text/xml,*/*",
                    "User-Agent" to USER_AGENT
                )
            ) { body ->
                parseCommitAtom(repo, body).takeIf { it.isNotEmpty() }
            }
            if (!parsed.isNullOrEmpty()) return parsed
        }
        return emptyList()
    }

    private fun parseCommitAtom(repo: String, xmlText: String): List<GithubRelease> {
        val entryRegex = Regex("(?s)<entry>(.*?)</entry>")
        return entryRegex.findAll(xmlText).mapNotNull { match ->
            val entry = match.groupValues.getOrNull(1).orEmpty()
            val sha = extractCommitSha(entry) ?: return@mapNotNull null
            val shortSha = sha.take(7)

            val rawTitle = extractXmlTag(entry, "title")
            val title = decodeXmlEntities(rawTitle).lineSequence()
                .firstOrNull()
                ?.trim()
                .orEmpty()
                .ifBlank { "提交 $shortSha" }
            val publishedAt = decodeXmlEntities(extractXmlTag(entry, "updated")).trim()

            GithubRelease(
                tagName = shortSha,
                name = title,
                body = title,
                publishedAt = publishedAt,
                zipballUrl = "https://api.github.com/repos/$repo/zipball/$sha"
            )
        }.toList()
    }

    private fun extractCommitSha(entryXml: String): String? {
        val linkRegex = Regex("""href=["']https://github\.com/[^"']+/commit/([0-9a-fA-F]{7,40})["']""")
        val fromLink = linkRegex.find(entryXml)?.groupValues?.getOrNull(1)?.trim()
        if (!fromLink.isNullOrBlank()) return fromLink

        val idRegex = Regex("""<id>[^<]*/commit/([0-9a-fA-F]{7,40})</id>""")
        val fromId = idRegex.find(entryXml)?.groupValues?.getOrNull(1)?.trim()
        if (!fromId.isNullOrBlank()) return fromId

        return null
    }

    private fun extractXmlTag(xmlText: String, tag: String): String {
        val regex = Regex("(?s)<$tag>(.*?)</$tag>")
        return regex.find(xmlText)?.groupValues?.getOrNull(1).orEmpty()
    }

    private fun decodeXmlEntities(text: String): String {
        return text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
    }

    private fun parseRelease(jsonStr: String): GithubRelease? {
        return try {
            val obj = json.parseToJsonElement(jsonStr).jsonObject
            GithubRelease(
                tagName = obj["tag_name"]?.jsonPrimitive?.content ?: "",
                name = obj["name"]?.jsonPrimitive?.content ?: "",
                body = obj["body"]?.jsonPrimitive?.content ?: "",
                publishedAt = obj["published_at"]?.jsonPrimitive?.content ?: "",
                zipballUrl = obj["zipball_url"]?.jsonPrimitive?.content ?: ""
            )
        } catch (e: Exception) {
            logRecoverableWarning("parseRelease 解析失败", e)
            null
        }
    }

    private fun buildLatestVersionLabel(
        versionLabel: String,
        commitSha: String
    ): String {
        val normalizedVersion = versionLabel.trim()
        val shortSha = commitSha.trim().takeIf { it.isNotBlank() }?.take(7).orEmpty()
        return when {
            normalizedVersion.isNotBlank() && shortSha.isNotBlank() -> "$normalizedVersion@$shortSha"
            normalizedVersion.isNotBlank() -> normalizedVersion
            else -> ""
        }
    }

    private data class AvailableVersionLabel(
        val version: String = "",
        val commitSha: String = ""
    )

    private fun parseAvailableVersionLabel(value: String?): AvailableVersionLabel {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank()) return AvailableVersionLabel()
        if ('@' !in trimmed && trimmed.matches(Regex("^[0-9a-fA-F]{7,40}$"))) {
            return AvailableVersionLabel(commitSha = trimmed)
        }
        val version = trimmed.substringBefore('@').trim()
        val commitSha = trimmed.substringAfter('@', "").trim()
        return AvailableVersionLabel(
            version = version.removePrefix("v").trim(),
            commitSha = commitSha
        )
    }

    private fun commitShasEquivalent(left: String, right: String): Boolean {
        val normalizedLeft = left.trim().lowercase()
        val normalizedRight = right.trim().lowercase()
        if (normalizedLeft.isBlank() || normalizedRight.isBlank()) return false
        return normalizedLeft == normalizedRight ||
            normalizedLeft.startsWith(normalizedRight) ||
            normalizedRight.startsWith(normalizedLeft)
    }

    private fun buildRollbackMetadata(
        variant: ApiVariant,
        release: GithubRelease,
        versionHint: String?
    ): CoreSourceMetadata? {
        val branch = resolveBranch(variant) ?: return null
        val repo = resolveRepo(variant)
        if (repo.isBlank()) return null
        val commitSha = release.commitSha.ifBlank {
            extractArchiveCommitSha(release.zipballUrl).orEmpty()
        }
        return CoreSourceMetadata(
            repo = repo,
            branch = branch,
            commitSha = commitSha,
            commitPublishedAt = release.publishedAt,
            versionLabel = release.version.ifBlank { versionHint.orEmpty() }
        )
    }

    private fun extractArchiveCommitSha(zipUrl: String): String? {
        val match = Regex("""/(?:archive|zipball)/([0-9a-fA-F]{7,40})(?:\.zip)?""").find(zipUrl)
        return match?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun buildDesiredCustomSourceText(): String {
        return currentCustomCoreSource()
            .takeIf { it.isValidRepo }
            ?.sourceText
            .orEmpty()
    }

    private fun resolveCustomSourceStatus(
        localMetadata: CoreSourceMetadata?,
        desiredRepo: String,
        desiredBranch: String?
    ): CoreSourceStatus {
        if (desiredRepo.isBlank()) return CoreSourceStatus.NotApplicable
        if (localMetadata == null) return CoreSourceStatus.UnknownLegacy

        val localRepo = normalizeGithubRepo(localMetadata.repo)
        val localBranch = normalizeGithubBranch(localMetadata.branch)
        val targetBranch = normalizeGithubBranch(desiredBranch).ifBlank { DEFAULT_CUSTOM_CORE_BRANCH }
        return when {
            localRepo.isBlank() || localBranch.isBlank() -> CoreSourceStatus.UnknownLegacy
            !localRepo.equals(desiredRepo, ignoreCase = true) -> CoreSourceStatus.Mismatched
            !branchesEquivalent(localBranch, targetBranch) -> CoreSourceStatus.Mismatched
            else -> CoreSourceStatus.Matched
        }
    }

    private fun branchesEquivalent(localBranch: String, desiredBranch: String): Boolean {
        if (localBranch.equals(desiredBranch, ignoreCase = true)) return true
        if (!desiredBranch.contains('/')) {
            return localBranch.substringAfterLast('/').equals(desiredBranch, ignoreCase = true) ||
                localBranch.endsWith("/$desiredBranch", ignoreCase = true)
        }
        return false
    }

    private fun metadataFile(coreDir: File): File = File(coreDir, CORE_SOURCE_METADATA_FILE)

    private fun writeCoreSourceMetadata(
        coreDir: File,
        metadata: CoreSourceMetadata?
    ) {
        val file = metadataFile(coreDir)
        if (metadata == null || (metadata.repo.isBlank() && metadata.branch.isBlank() && metadata.commitSha.isBlank())) {
            runCatching { file.delete() }
            return
        }
        runCatching {
            file.writeText(json.encodeToString(metadata), Charsets.UTF_8)
        }
    }

    private fun readLocalCoreSourceMetadata(
        variant: ApiVariant,
        mode: RunMode
    ): CoreSourceMetadata? {
        val location = getCoreLocation(variant, mode)
        val candidates = buildList {
            if (mode != RunMode.Normal) {
                add(File(location.rootDirPath, CORE_SOURCE_METADATA_FILE))
            }
            add(metadataFile(location.normalDir))
        }
        return candidates.firstNotNullOfOrNull { file ->
            if (!file.exists() || !file.isFile) return@firstNotNullOfOrNull null
            runCatching {
                json.decodeFromString<CoreSourceMetadata>(file.readText(Charsets.UTF_8))
            }.getOrNull()
        }
    }

    private suspend fun installOrUpdateCore(
        variant: ApiVariant,
        actionLabel: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runOwnedCoreOperation(variant, actionLabel) { operationId ->
            val remoteSource = resolveRemoteSource(variant)
                ?: throw IOException("无法获取版本信息")
            val release = remoteSource.release
            val versionHint = remoteSource.metadata?.versionLabel?.ifBlank { null }
            downloadAndExtract(
                operationId = operationId,
                variant = variant,
                zipUrl = release.zipballUrl,
                versionHint = versionHint,
                actionLabel = actionLabel,
                sourceMetadata = remoteSource.metadata
            )
            persistResolvedCustomSourceIfNeeded(variant, remoteSource.metadata)
            refreshCoreInfo()
        }
    }

    private fun persistResolvedCustomSourceIfNeeded(
        variant: ApiVariant,
        metadata: CoreSourceMetadata?
    ) {
        if (variant != ApiVariant.Custom || metadata == null) return
        val normalizedRepo = normalizeGithubRepo(metadata.repo)
        val normalizedBranch = normalizeGithubBranch(metadata.branch).ifBlank { DEFAULT_CUSTOM_CORE_BRANCH }
        val currentRepo = resolveRepo(variant)
        val currentBranch = resolveBranch(variant).orEmpty()
        if (normalizedRepo.equals(currentRepo, ignoreCase = true) &&
            normalizedBranch.equals(currentBranch, ignoreCase = true)
        ) {
            return
        }
        settingsRepository.saveCustomCoreSource(
            repoInput = normalizedRepo,
            branchInput = normalizedBranch
        )
    }

    private suspend fun downloadAndExtract(
        operationId: Long,
        variant: ApiVariant,
        zipUrl: String,
        versionHint: String?,
        actionLabel: String,
        sourceMetadata: CoreSourceMetadata? = null
    ) {
        val mode = currentRunMode()
        val location = getCoreLocation(variant, mode)
        val targetDir = location.normalDir
        val stagingDir = createCoreTempDir(targetDir, "staging")
        writeOperationMarker(stagingDir, operationId)

        updateDownloadProgress(
            variant = variant,
            actionLabel = actionLabel,
            stageText = "准备下载核心包",
            progress = null,
            downloadedBytes = 0L,
            totalBytes = -1L
        )

        try {
            val candidateUrls = buildDownloadUrlCandidates(zipUrl)
            var lastFailureMessage: String? = null
            var selectedResponse: okhttp3.Response? = null
            for (url in candidateUrls) {
                try {
                    currentCoroutineContext().ensureActive()
                    val reqBuilder = Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                    githubProxyService.applyGithubAuth(reqBuilder, url)
                    val resp = httpClient.newCall(reqBuilder.build()).executeCancellable()
                    if (resp.isSuccessful) {
                        selectedResponse = resp
                        break
                    } else {
                        lastFailureMessage = when (resp.code) {
                            401, 403 -> "下载失败：GitHub 拒绝访问（HTTP ${resp.code}），请检查 Token、仓库权限或代理线路"
                            404 -> "下载失败：仓库、分支或版本不存在（HTTP 404）"
                            else -> "下载失败：GitHub 返回 HTTP ${resp.code}"
                        }
                        resp.close()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
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
                    updateDownloadProgress(
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
                        val shouldEmit = now - lastEmitAt >= 300 || (totalBytes > 0 && bytes >= totalBytes)
                        if (shouldEmit) {
                            lastEmitAt = now
                            val progress = if (totalBytes > 0) {
                                (bytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            } else {
                                null
                            }
                            updateDownloadProgress(
                                variant = variant,
                                actionLabel = actionLabel,
                                stageText = "正在下载核心包",
                                progress = progress,
                                downloadedBytes = bytes,
                                totalBytes = totalBytes
                            )
                        }
                    }

                    val extracted = extractDanmuFolder(streamWithProgress, stagingDir)
                    if (!extracted) throw IOException("核心压缩包中未找到 danmu_api 或 danmu-api 目录")
                }
            } catch (e: Exception) {
                runCatching { stagingDir.deleteRecursively() }
                throw e
            }

            updateDownloadProgress(
                variant = variant,
                actionLabel = actionLabel,
                stageText = "正在整理核心文件",
                progress = 1f,
                downloadedBytes = if (totalBytes > 0) totalBytes else lastBytes,
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
                sourceMetadata = sourceMetadata
            )
        } catch (e: Exception) {
            if (e !is CoreDependencyRepairRequiredException) {
                runCatching { stagingDir.deleteRecursively() }
            }
            throw e
        } finally {
            _downloadProgress.value = CoreDownloadProgress()
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
        val pending = prepareStagedCore(
            operationId = operationId,
            variant = variant,
            actionLabel = actionLabel,
            stagingDir = stagingDir,
            targetDir = targetDir,
            mode = mode,
            rootDirPath = rootDirPath,
            versionHint = versionHint,
            sourceMetadata = sourceMetadata
        )
        if (pending.repair.missingDependencies.isNotEmpty()) {
            setPendingCoreMutation(pending)
            throw CoreDependencyRepairRequiredException(pending.repair)
        }
        mutationMutex.withLock {
            applyStagedCore(pending)
        }
    }

    private fun prepareStagedCore(
        operationId: Long,
        variant: ApiVariant,
        actionLabel: String,
        stagingDir: File,
        targetDir: File,
        mode: RunMode,
        rootDirPath: String,
        versionHint: String?,
        sourceMetadata: CoreSourceMetadata?
    ): PendingCoreMutation {
        NodeProjectManager.normalizeCoreLayout(stagingDir)
        NodeProjectManager.ensureCorePackageJson(stagingDir, versionHint)
        NodeProjectManager.writeRuntimeDependencyRequirements(stagingDir)
        writeCoreSourceMetadata(stagingDir, sourceMetadata)

        if (!NodeProjectManager.hasValidCore(stagingDir)) {
            throw IOException("核心文件不完整，缺少关键入口文件")
        }
        return PendingCoreMutation(
            operationId = operationId,
            repair = CoreDependencyRepairRequest(
                operationId = operationId,
                variant = variant,
                actionLabel = actionLabel,
                missingDependencies = collectMissingCoreRuntimeDependencies(stagingDir),
                candidateVersion = versionHint,
                onlineRepairSupported = RuntimeDependencyPackProtocol.supportsOnlineRepair(variant)
            ),
            stagingDir = stagingDir,
            targetDir = targetDir,
            mode = mode,
            rootDirPath = rootDirPath,
            sourceMetadata = sourceMetadata
        )
    }

    private fun applyStagedCore(pending: PendingCoreMutation) {
        var backupDir: File? = null
        var targetReplaced = false
        val previousCandidate = loadPersistedCandidate()
        try {
            val operationMarker = File(pending.stagingDir, OPERATION_MARKER_FILE)
            if (operationMarker.exists() && !operationMarker.delete()) {
                throw IOException("无法清理核心事务标记")
            }
            verifyCoreRuntimeDependencies(pending.stagingDir)
            if (!NodeProjectManager.hasValidCore(pending.stagingDir)) {
                throw IOException("候选核心文件不完整，缺少关键入口文件")
            }
            backupDir = replaceCoreDirectory(pending.targetDir, pending.stagingDir)
            targetReplaced = true
            if (pending.mode != RunMode.Normal) {
                syncCoreDirToRoot(pending.targetDir, pending.rootDirPath)
                if (!rootHasValidCore(pending.rootDirPath)) {
                    throw IOException("Root 核心同步后仍缺少关键入口文件")
                }
            }
            val recoveryPath = CoreCandidateRecoveryPolicy.selectRecoveryPath(
                targetPath = pending.targetDir.absolutePath,
                replacementBackupPath = backupDir?.absolutePath,
                previousTargetPath = previousCandidate?.targetDirPath,
                previousRecoveryPath = previousCandidate?.backupDirPath,
                previousRecoveryAvailable = previousCandidate?.backupDirPath
                    ?.takeIf { it.isNotBlank() }
                    ?.let { File(it).isDirectory } == true
            )
            val recoveryDir = recoveryPath?.let(::File)
            if (backupDir != null && backupDir != recoveryDir) {
                runCatching { backupDir.deleteRecursively() }
            }
            saveCandidate(
                PersistedCoreCandidate(
                    variantKey = pending.repair.variant.key,
                    runModeKey = pending.mode.key,
                    actionLabel = pending.repair.actionLabel,
                    installedAtMs = System.currentTimeMillis(),
                    targetDirPath = pending.targetDir.absolutePath,
                    rootDirPath = pending.rootDirPath,
                    backupDirPath = recoveryDir?.absolutePath.orEmpty()
                )
            )
        } catch (error: Exception) {
            if (targetReplaced) {
                runCatching { restoreCoreDirectory(pending.targetDir, backupDir) }
                if (pending.mode != RunMode.Normal && pending.targetDir.exists()) {
                    runCatching { syncCoreDirToRoot(pending.targetDir, pending.rootDirPath) }
                }
            }
            throw error
        }
    }

    private fun applyStagedDependencies(pending: PendingCoreMutation) {
        verifyCoreRuntimeDependencies(pending.stagingDir)
        if (!NodeProjectManager.hasValidCore(pending.targetDir)) {
            throw IOException("当前工作目录中的核心已失效，请重新下载核心")
        }
        replaceInstalledCoreDependencies(
            stagingCoreDir = pending.stagingDir,
            installedCoreDir = pending.targetDir,
            afterInstall = {
                if (pending.mode != RunMode.Normal) {
                    syncCoreDirToRoot(pending.targetDir, pending.rootDirPath)
                }
            },
            verifyInstalled = { verifyCoreRuntimeDependencies(pending.targetDir) }
        )
    }

    private fun buildDownloadUrlCandidates(zipUrl: String): List<String> {
        val preferDirectFirst = zipUrl.contains("://api.github.com/")
        return if (preferDirectFirst) {
            listOf(zipUrl).plus(withProxyCandidates(zipUrl)).distinct()
        } else {
            withProxyCandidates(zipUrl)
        }
    }

    private fun updateDownloadProgress(
        variant: ApiVariant,
        actionLabel: String,
        stageText: String,
        progress: Float?,
        downloadedBytes: Long,
        totalBytes: Long
    ) {
        _downloadProgress.value = CoreDownloadProgress(
            inProgress = true,
            variant = variant,
            actionLabel = actionLabel,
            stageText = stageText,
            progress = progress,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes
        )
    }

    private fun createCoreTempDir(targetDir: File, suffix: String): File {
        val parentDir = targetDir.parentFile ?: throw IOException("核心目录路径无效")
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            throw IOException("无法创建核心目录: ${parentDir.absolutePath}")
        }
        val tempDir = File(parentDir, "${targetDir.name}.${suffix}-${System.currentTimeMillis()}")
        runCatching { tempDir.deleteRecursively() }
        if (!tempDir.mkdirs()) {
            throw IOException("无法创建临时目录: ${tempDir.absolutePath}")
        }
        return tempDir
    }

    private fun writeOperationMarker(directory: File, operationId: Long) {
        File(directory, OPERATION_MARKER_FILE).writeText(
            "$operationId\n${System.currentTimeMillis()}\n",
            Charsets.UTF_8
        )
    }

    private fun cleanupStaleCoreArtifacts() {
        val projectDir = RuntimePaths.normalProjectDir(context)
        if (!projectDir.isDirectory) return
        val now = System.currentTimeMillis()
        val activeBackupPath = loadPersistedCandidate()?.backupDirPath
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { File(it).canonicalPath }.getOrNull() }
        val candidates = buildList {
            addAll(projectDir.listFiles().orEmpty().filter { it.isDirectory })
            projectDir.listFiles().orEmpty()
                .filter { it.isDirectory && it.name.startsWith("danmu_api_") }
                .forEach { coreDir -> addAll(coreDir.listFiles().orEmpty().filter { it.isDirectory }) }
        }
        candidates.distinctBy { it.absolutePath }.forEach { directory ->
            if (activeBackupPath != null &&
                runCatching { directory.canonicalPath }.getOrNull() == activeBackupPath
            ) return@forEach
            val isKnownArtifact = directory.name.contains(".staging-") ||
                directory.name.contains(".dependency-repair-") ||
                directory.name.contains(".backup-") ||
                directory.name.startsWith(".node_modules-backup-")
            if (!isKnownArtifact) return@forEach
            val marker = File(directory, OPERATION_MARKER_FILE)
            val createdAt = marker.takeIf { it.isFile }
                ?.readLines()
                ?.getOrNull(1)
                ?.toLongOrNull()
                ?: directory.lastModified()
            val retention = if (marker.isFile) {
                MARKED_STAGING_RETENTION_MS
            } else {
                LEGACY_STAGING_RETENTION_MS
            }
            if (createdAt > 0L && now - createdAt >= retention) {
                runCatching { directory.deleteRecursively() }
            }
        }
    }

    private suspend fun restoreRecordedDependencyRepair() {
        val issue = RuntimeDependencyHealthChecker.readPendingIssue(context) ?: return
        if (_pendingDependencyRepair.value != null) return
        runCatching {
            prepareInstalledCoreDependencyRepair(
                variant = issue.variant,
                origin = CoreDependencyRepairOrigin.RuntimeStart,
                suspectedMissingPackage = issue.suspectedPackage
            )
        }.onFailure { error ->
            logRecoverableWarning("恢复启动依赖修复提示失败", error)
        }
    }

    private fun replaceCoreDirectory(targetDir: File, stagingDir: File): File? {
        if (!stagingDir.exists()) throw IOException("临时核心目录不存在")
        val parentDir = targetDir.parentFile ?: throw IOException("核心目录路径无效")
        var backupDir: File? = null

        if (targetDir.exists()) {
            backupDir = File(parentDir, "${targetDir.name}.backup-${System.currentTimeMillis()}")
            runCatching { backupDir.deleteRecursively() }
            moveDirectory(targetDir, backupDir)
        }

        try {
            moveDirectory(stagingDir, targetDir)
        } catch (e: Exception) {
            restoreCoreDirectory(targetDir, backupDir)
            throw e
        }
        return backupDir
    }

    private fun restoreCoreDirectory(targetDir: File, backupDir: File?) {
        val backup = backupDir ?: return
        if (!backup.exists()) return
        runCatching { if (targetDir.exists()) targetDir.deleteRecursively() }
        moveDirectory(backup, targetDir)
    }

    private fun moveDirectory(sourceDir: File, targetDir: File) {
        if (!sourceDir.exists()) return
        targetDir.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) {
                throw IOException("无法创建目录: ${parent.absolutePath}")
            }
        }
        if (sourceDir.renameTo(targetDir)) return
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        copyDirectoryOrThrow(sourceDir, targetDir)
    }

    private fun deleteRootCoreDir(rootDirPath: String) {
        val script = """
            DIR=${shellQuote(rootDirPath)}
            rm -rf "${'$'}DIR"
            [ ! -e "${'$'}DIR" ]
        """.trimIndent()
        val result = RootShell.exec(script, timeoutMs = 8000L)
        if (!result.ok) {
            val detail = (result.stderr.ifBlank { result.stdout }).trim().ifBlank { "未知错误" }
            throw IOException("删除 Root 核心目录失败: $detail")
        }
    }

    private fun syncCoreDirToRoot(srcDir: File, rootDirPath: String) {
        RootRuntimeController.syncCoreDirectoryFromNormal(srcDir, rootDirPath).let { result ->
            if (!result.ok) {
                val detail = result.detail.ifBlank { result.message }.ifBlank { "未知错误" }
                throw IOException("同步 Root 核心目录失败: $detail")
            }
        }
    }

    private fun rootHasValidCore(rootDirPath: String): Boolean {
        val script = """
            DIR=${shellQuote(rootDirPath)}
            [ -f "${'$'}DIR/worker.js" ] && exit 0
            [ -f "${'$'}DIR/danmu_api/worker.js" ] && exit 0
            [ -f "${'$'}DIR/danmu-api/worker.js" ] && exit 0
            exit 1
        """.trimIndent()
        return RootShell.exec(script, timeoutMs = 3000L).ok
    }

    private fun rootReadCoreVersion(rootDirPath: String): String? {
        val globalsCandidates = listOf(
            "$rootDirPath/configs/globals.js",
            "$rootDirPath/config/globals.js",
            "$rootDirPath/globals.js",
            "$rootDirPath/danmu_api/configs/globals.js",
            "$rootDirPath/danmu_api/config/globals.js",
            "$rootDirPath/danmu_api/globals.js",
            "$rootDirPath/danmu-api/configs/globals.js",
            "$rootDirPath/danmu-api/config/globals.js",
            "$rootDirPath/danmu-api/globals.js"
        )
        val packageCandidates = listOf(
            "$rootDirPath/package.json",
            "$rootDirPath/danmu_api/package.json",
            "$rootDirPath/danmu-api/package.json"
        )
        val allCandidates = globalsCandidates + packageCandidates
        val candidateArgs = allCandidates.joinToString(separator = " ") { shellQuote(it) }
        val script = """
            for FILE in \
            $candidateArgs
            do
              [ -f "${'$'}FILE" ] || continue
              sed -n '1,220p' "${'$'}FILE" 2>/dev/null || cat "${'$'}FILE" 2>/dev/null || true
              printf '\n'
            done
        """.trimIndent()
        val result = RootShell.exec(script, timeoutMs = 4500L)
        if (!result.ok) return null
        val text = result.stdout
        if (text.isBlank()) return null

        val version = CoreVersionParser.extractVersion(text)
        return version?.removePrefix("v")?.takeIf { it.isNotBlank() }
    }

    private fun parseVersionFromSource(text: String): String? {
        return CoreVersionParser.extractSourceVersion(text)
    }

    private data class VersionParts(
        val core: List<Int>,
        val preRelease: List<String>
    )

    /**
     * 比较两个语义化版本号
     * @return 正数表示 v1 > v2，0 表示相等，负数表示 v1 < v2
     */
    private fun compareVersions(v1: String, v2: String): Int {
        if (v1 == v2) return 0

        val p1 = parseVersionParts(v1)
        val p2 = parseVersionParts(v2)

        val coreLen = maxOf(p1.core.size, p2.core.size)
        for (i in 0 until coreLen) {
            val n1 = p1.core.getOrNull(i) ?: 0
            val n2 = p2.core.getOrNull(i) ?: 0
            if (n1 != n2) return n1 - n2
        }

        val pre1 = p1.preRelease
        val pre2 = p2.preRelease
        if (pre1.isEmpty() && pre2.isEmpty()) return 0
        if (pre1.isEmpty()) return 1
        if (pre2.isEmpty()) return -1

        val preLen = maxOf(pre1.size, pre2.size)
        for (i in 0 until preLen) {
            val a = pre1.getOrNull(i)
            val b = pre2.getOrNull(i)
            if (a == b) continue
            if (a == null) return -1
            if (b == null) return 1

            val ai = a.toIntOrNull()
            val bi = b.toIntOrNull()
            if (ai != null && bi != null) {
                if (ai != bi) return ai - bi
                continue
            }
            if (ai != null && bi == null) return -1
            if (ai == null && bi != null) return 1

            val lexical = a.compareTo(b)
            if (lexical != 0) return lexical
        }

        return 0
    }

    private fun parseVersionParts(version: String): VersionParts {
        val cleaned = version.trim()
            .removePrefix("v")
            .substringBefore('+')
        if (cleaned.isBlank()) {
            return VersionParts(core = listOf(0), preRelease = emptyList())
        }

        val main = cleaned.substringBefore('-')
        val pre = cleaned.substringAfter('-', "").takeIf { it.isNotBlank() }

        val core = main.split('.')
            .filter { it.isNotBlank() }
            .map { token ->
                token.toIntOrNull()
                    ?: token.takeWhile { ch -> ch.isDigit() }.toIntOrNull()
                    ?: 0
            }
            .ifEmpty { listOf(0) }

        val preRelease = pre?.split('.')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        return VersionParts(core = core, preRelease = preRelease)
    }

    private fun extractDanmuFolder(zipStream: InputStream, outDir: File): Boolean {
        var extractedAny = false

        ZipInputStream(BufferedInputStream(zipStream)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val name = entry.name ?: ""
                val rel = resolveDanmuRelativePath(name)
                val rootFileName = resolveRootLevelFileName(name)
                if (rel == null && rootFileName == null) {
                    zis.closeEntry()
                    continue
                }

                if (rel != null && rel.isBlank()) {
                    zis.closeEntry()
                    continue
                }

                val outFile = File(outDir, rel ?: rootFileName.orEmpty())
                val canonRoot = outDir.canonicalPath
                val canonOut = outFile.canonicalPath
                if (canonOut != canonRoot && !canonOut.startsWith(canonRoot + File.separator)) {
                    zis.closeEntry()
                    continue
                }

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                    extractedAny = true
                }
                zis.closeEntry()
            }
        }
        return extractedAny
    }

    private fun resolveRootLevelFileName(entryName: String): String? {
        val clean = entryName.replace('\\', '/').trim('/')
        if (clean.isBlank()) return null
        val parts = clean.split('/')
        if (parts.size != 2) return null
        return when (parts[1]) {
            "package.json" -> "package.json"
            else -> null
        }
    }

    private fun resolveDanmuRelativePath(entryName: String): String? {
        val clean = entryName.replace('\\', '/').trim('/')
        if (clean.isBlank()) return null

        val parts = clean.split('/')
        val idx = parts.indexOfFirst {
            it.equals("danmu_api", ignoreCase = true) ||
                it.equals("danmu-api", ignoreCase = true)
        }
        if (idx < 0) return null

        val relParts = parts.drop(idx + 1)
        if (relParts.isEmpty()) return ""
        return relParts.joinToString("/")
    }

    private class ProgressInputStream(
        private val source: InputStream,
        private val onRead: (Long) -> Unit
    ) : FilterInputStream(source) {
        private var totalRead = 0L

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) {
                totalRead += 1
                onRead(totalRead)
            }
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val readSize = super.read(buffer, offset, length)
            if (readSize > 0) {
                totalRead += readSize
                onRead(totalRead)
            }
            return readSize
        }
    }

    private fun collectMissingCoreRuntimeDependencies(coreDir: File): List<String> {
        val normalProjectDir = RuntimePaths.normalProjectDir(context)
        return NodeProjectManager.collectMissingRuntimeDepsForCore(
            coreDir = coreDir,
            runtimeNodeModulesDir = File(normalProjectDir, "node_modules")
        )
    }

    private fun verifyCoreRuntimeDependencies(
        coreDir: File,
        unavailableReason: String? = null
    ) {
        val missing = collectMissingCoreRuntimeDependencies(coreDir)
        if (missing.isNotEmpty()) {
            throw IOException(
                buildString {
                    append("仍缺少运行时依赖：")
                    append(missing.joinToString(", "))
                    unavailableReason?.takeIf { it.isNotBlank() }?.let {
                        append("。")
                        append(it)
                    }
                }
            )
        }
    }

    private fun ensureCoreDirWatcher(mode: RunMode) {
        if (mode != RunMode.Normal) {
            coreWatcher?.stop()
            coreWatcher = null
            return
        }

        val projectDir = RuntimePaths.normalProjectDir(context)
        val projectPath = projectDir.absolutePath
        val current = coreWatcher
        if (current != null && current.rootPath == projectPath) return

        current?.stop()
        coreWatcher = null

        if (!projectDir.exists() || !projectDir.isDirectory) return
        val watcher = CoreDirWatcher(projectDir) { changedPath ->
            scheduleCoreInfoRefresh("检测到核心目录变更：$changedPath")
        }
        watcher.start()
        coreWatcher = watcher
    }

    private fun scheduleCoreInfoRefresh(reason: String) {
        pendingCoreRefreshReason = reason
        coreRefreshJob?.cancel()
        coreRefreshJob = repoScope.launch {
            delay(CORE_REFRESH_DEBOUNCE_MS)
            if (pendingCoreRefreshReason == null) return@launch
            pendingCoreRefreshReason = null
            try {
                refreshCoreInfo()
            } catch (e: Exception) {
                logRecoverableWarning("debouncedRefreshCoreInfo 刷新失败", e)
            }
        }
    }

    private inner class CoreDirWatcher(
        rootDir: File,
        private val onChanged: (String) -> Unit
    ) {
        val rootPath: String = rootDir.absolutePath
        private val rootCanonical = runCatching { rootDir.canonicalFile }.getOrElse { rootDir }
        private val observers = LinkedHashMap<String, FileObserver>()
        private val mask = FileObserver.CLOSE_WRITE or
            FileObserver.MODIFY or
            FileObserver.CREATE or
            FileObserver.MOVED_TO or
            FileObserver.MOVED_FROM or
            FileObserver.DELETE or
            FileObserver.DELETE_SELF or
            FileObserver.MOVE_SELF

        fun start() {
            watchRecursively(rootCanonical)
        }

        fun stop() {
            observers.values.forEach { observer ->
                runCatching { observer.stopWatching() }
            }
            observers.clear()
        }

        private fun createFileObserver(path: String, onEvent: (Int, String?) -> Unit): FileObserver {
            val watchFile = File(path)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                object : FileObserver(watchFile, mask) {
                    override fun onEvent(event: Int, relativePath: String?) {
                        onEvent.invoke(event, relativePath)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                object : FileObserver(path, mask) {
                    override fun onEvent(event: Int, relativePath: String?) {
                        onEvent.invoke(event, relativePath)
                    }
                }
            }
        }

        private fun watchRecursively(dir: File) {
            if (!dir.exists() || !dir.isDirectory || shouldIgnore(dir)) return
            val key = runCatching { dir.canonicalPath }.getOrElse { dir.absolutePath }
            if (observers.containsKey(key)) return

            val observer = createFileObserver(key) { event, path ->
                val target = if (path.isNullOrBlank()) {
                    File(key)
                } else {
                    File(key, path)
                }
                if (shouldIgnore(target)) return@createFileObserver

                if ((event and (FileObserver.CREATE or FileObserver.MOVED_TO)) != 0 && target.isDirectory) {
                    watchRecursively(target)
                }

                val rel = toRelative(target).ifBlank { target.name }
                onChanged(rel)

                if (event and (FileObserver.DELETE_SELF or FileObserver.MOVE_SELF) != 0) {
                    observers.remove(key)?.let { removed ->
                        runCatching { removed.stopWatching() }
                    }
                }
            }

            observer.startWatching()
            observers[key] = observer
            dir.listFiles()?.filter { it.isDirectory }?.forEach { child ->
                watchRecursively(child)
            }
        }

        private fun toRelative(target: File): String {
            return runCatching {
                target.canonicalFile.relativeTo(rootCanonical).path.replace('\\', '/')
            }.getOrElse {
                target.name
            }
        }

        private fun shouldIgnore(target: File): Boolean {
            val rel = toRelative(target)
            if (rel == "." || rel.isBlank()) return false
            return rel == ".app_version" ||
                // 核心目录允许用户手工替换；这里不能在覆盖过程中递归介入，
                // 否则 refreshCoreInfo() -> normalizeCoreLayout() 会对半写入目录做结构整理。
                rel.startsWith("danmu_api_stable/") ||
                rel == "danmu_api_stable" ||
                rel.startsWith("danmu_api_dev/") ||
                rel == "danmu_api_dev" ||
                rel.startsWith("danmu_api_custom/") ||
                rel == "danmu_api_custom" ||
                rel.startsWith("logs/") ||
                rel == "logs" ||
                rel.startsWith(".cache/") ||
                rel == ".cache" ||
                rel.startsWith("node_modules/") ||
                rel == "node_modules"
        }
    }

}

internal fun copyDirectoryOrThrow(
    sourceDir: File,
    targetDir: File,
    copyBlock: (File, File) -> Boolean = { src, dst -> src.copyRecursively(dst, overwrite = true) },
    cleanupBlock: (File) -> Boolean = { dir -> dir.deleteRecursively() }
) {
    val copied = copyBlock(sourceDir, targetDir)
    if (!copied) {
        runCatching { if (targetDir.exists()) targetDir.deleteRecursively() }
        throw IOException("复制目录失败: ${sourceDir.absolutePath} -> ${targetDir.absolutePath}")
    }
    if (!cleanupBlock(sourceDir)) {
        throw IOException("无法清理目录: ${sourceDir.absolutePath}")
    }
}

internal fun replaceInstalledCoreDependencies(
    stagingCoreDir: File,
    installedCoreDir: File,
    afterInstall: () -> Unit = {},
    verifyInstalled: () -> Unit
) {
    val stagedNodeModules = File(stagingCoreDir, "node_modules")
    if (!stagedNodeModules.isDirectory) {
        throw IOException("待安装依赖缺少 node_modules 目录")
    }
    if (!installedCoreDir.isDirectory) {
        throw IOException("当前核心目录不存在")
    }

    val targetNodeModules = File(installedCoreDir, "node_modules")
    val backupNodeModules = File(
        installedCoreDir,
        ".node_modules-backup-${System.currentTimeMillis()}"
    )
    val metadataNames = listOf(
        RuntimeDependencyPackProtocol.INSTALLED_MANIFEST_FILE,
        LocalRuntimeDependencyArchiveImporter.LOCAL_IMPORT_AUDIT_FILE,
        RuntimeDependencyPackProtocol.LEGACY_INSTALLED_LOCK_FILE
    )
    val previousMetadata = metadataNames.associateWith { name ->
        File(installedCoreDir, name).takeIf { it.isFile }?.readBytes()
    }

    fun moveDirectory(source: File, target: File) {
        if (source.renameTo(target)) return
        copyDirectoryOrThrow(source, target)
    }

    var originalMoved = false
    var keepBackup = false
    try {
        if (targetNodeModules.exists()) {
            runCatching { backupNodeModules.deleteRecursively() }
            moveDirectory(targetNodeModules, backupNodeModules)
            originalMoved = true
        }
        moveDirectory(stagedNodeModules, targetNodeModules)

        metadataNames.forEach { name ->
            runCatching { File(installedCoreDir, name).delete() }
            val stagedMetadata = File(stagingCoreDir, name)
            if (stagedMetadata.isFile) {
                stagedMetadata.copyTo(File(installedCoreDir, name), overwrite = true)
            }
        }
        verifyInstalled()
        afterInstall()
        runCatching { backupNodeModules.deleteRecursively() }
    } catch (error: Exception) {
        runCatching { targetNodeModules.deleteRecursively() }
        if (originalMoved && backupNodeModules.exists()) {
            runCatching { moveDirectory(backupNodeModules, targetNodeModules) }
                .onFailure { restoreError ->
                    keepBackup = true
                    error.addSuppressed(restoreError)
                }
        }
        metadataNames.forEach { name ->
            val target = File(installedCoreDir, name)
            runCatching { target.delete() }
            previousMetadata[name]?.let { bytes ->
                runCatching { target.writeBytes(bytes) }
            }
        }
        throw error
    } finally {
        if (!keepBackup) {
            runCatching { backupNodeModules.deleteRecursively() }
        }
    }
}
