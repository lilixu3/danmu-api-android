package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.data.network.newOutboundCall
import com.example.danmuapiapp.data.service.GithubQuotaExhaustedException
import com.example.danmuapiapp.data.service.GithubQuotaRequestContext

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import android.util.Log
import androidx.core.net.toUri
import com.example.danmuapiapp.BuildConfig
import com.example.danmuapiapp.data.remote.github.GithubRemoteService
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
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import com.example.danmuapiapp.data.service.CoreVersionParser
import java.io.IOException

@Singleton
class CoreRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val githubProxyService: GithubProxyService,
    private val githubRemoteService: GithubRemoteService,
    private val settingsRepository: SettingsRepository,
    private val runtimeDependencyPackManager: RuntimeDependencyPackManager,
    private val githubPullRequestService: GithubPullRequestService,
    private val pullRequestMergeService: PullRequestMergeService
) : CoreRepository {

    companion object {
        private const val TAG = "CoreRepo"
        private const val USER_AGENT = "DanmuApiApp"
        private const val WORK_DIR_PREFS = "danmu_work_dir"
        private const val WORK_DIR_KEY_CUSTOM_BASE_PATH = "custom_path"
        private const val RUNTIME_PREFS = "runtime"
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

    private val workDirPrefs = context.getSharedPreferences(WORK_DIR_PREFS, Context.MODE_PRIVATE)
    private val runtimePrefs = context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val coreDisplayCache = CoreDisplayInfoCache(context)
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
    override val isCoreInfoLoading: StateFlow<Boolean> get() = coreInfoRefreshController.isLoading

    private val _downloadProgress = MutableStateFlow(CoreDownloadProgress())
    override val downloadProgress: StateFlow<CoreDownloadProgress> = _downloadProgress.asStateFlow()
    private val _pendingDependencyRepair = MutableStateFlow<CoreDependencyRepairRequest?>(null)
    override val pendingDependencyRepair: StateFlow<CoreDependencyRepairRequest?> =
        _pendingDependencyRepair.asStateFlow()
    private val mutationMutex = Mutex()
    private val _operationState = MutableStateFlow(CoreOperationState())
    override val operationState: StateFlow<CoreOperationState> = _operationState.asStateFlow()
    private val operationController = CoreOperationController(
        readState = { _operationState.value },
        writeState = { _operationState.value = it }
    )
    private val candidateController = CoreCandidateController(
        context = context,
        locationOf = ::getCoreLocation,
        mutationMutex = mutationMutex,
        syncRoot = ::syncCoreDirToRoot,
        refreshCoreInfo = ::refreshCoreInfo
    )
    override val candidateState: StateFlow<CoreCandidateState?> get() = candidateController.candidateState
    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val branchRemoteSourceCache = ConcurrentHashMap<String, CoreRemoteSource>()
    private val defaultBranchCache = ConcurrentHashMap<String, String>()
    private val remoteDataSource = CoreRemoteDataSource(githubRemoteService)
    private val remoteBranchSourceController = CoreRemoteBranchSourceController(
        remoteDataSource = remoteDataSource,
        readDefaultBranch = { repo -> defaultBranchCache[repo.lowercase()] },
        writeDefaultBranch = { repo, branch -> defaultBranchCache[repo.lowercase()] = branch },
        readBranchSource = { cacheKey -> branchRemoteSourceCache[cacheKey] },
        writeBranchSource = { cacheKey, source -> branchRemoteSourceCache[cacheKey] = source }
    )
    private val updateCheckController = CoreUpdateCheckController(
        requireQuotaAvailable = { githubProxyService.apiQuota.requireAvailable() },
        isQuotaBlocked = { githubProxyService.apiQuota.blocked() != null },
        remoteDataSource = remoteDataSource,
        loadPullRequestHead = { repo, number ->
            githubPullRequestService.get(repo, number, forceRefresh = true).headSha
        },
        loadPullRequest = { repo, number ->
            githubPullRequestService.get(repo, number, forceRefresh = true)
        },
        coverageForUpdate = { repo, pullRequest, installedHead, installedBase, targetSha ->
            githubPullRequestService.coverageForUpdate(
                repo,
                pullRequest,
                installedHead,
                installedBase,
                targetSha
            )
        },
        resolveRepo = ::resolveRepo,
        resolveBranch = ::resolveBranch,
        currentRunMode = ::currentRunMode,
        readLocalMetadata = ::readLocalCoreSourceMetadata,
        resolveRemoteSource = { variant -> resolveRemoteSource(variant) },
        readCoreInfoList = { _coreInfoList.value },
        updateCoreInfoList = { transform -> _coreInfoList.update(transform) },
        logWarning = ::logRecoverableWarning
    )
    private val installController = CoreInstallController(
        verifyStagedDependencies = ::verifyCoreRuntimeDependencies,
        verifyInstalledDependencies = ::verifyCoreRuntimeDependencies,
        syncRoot = ::syncCoreDirToRoot,
        hasValidRootCore = ::rootHasValidCore,
        loadPreviousCandidate = { candidateController.load() },
        saveCandidate = { candidate -> candidateController.save(candidate) }
    )
    private val pullRequestController = CorePullRequestController(
        pullRequestService = githubPullRequestService,
        pullRequestMergeService = pullRequestMergeService,
        runtimeDependencyPackManager = runtimeDependencyPackManager,
        installController = installController,
        resolveRepo = ::resolveRepo,
        resolveBranch = ::resolveBranch,
        readReadyCoreInfo = { variant ->
            _coreInfoList.value.firstOrNull { it.variant == variant && it.isReady }
        },
        currentRunMode = ::currentRunMode,
        locationOf = ::getCoreLocation,
        hasValidCore = ::hasValidCore,
        readLocalMetadata = ::readLocalCoreSourceMetadata,
        beginOperation = { variant, actionLabel -> pendingMutationController.beginOperation(variant, actionLabel) },
        finishOperation = { operationId -> pendingMutationController.finishOperation(operationId) },
        markAwaitingRepair = { operationId -> pendingMutationController.markOperationAwaitingRepair(operationId) },
        setPendingMutation = { pending, publishDependencyRepair ->
            pendingMutationController.setPendingMutation(pending, publishDependencyRepair)
        },
        clearPendingMutation = { operationId, deleteStaging ->
            pendingMutationController.clearPendingMutation(operationId, deleteStaging)
        },
        createStagingDir = ::createCoreTempDir,
        writeOperationMarker = ::writeOperationMarker,
        collectMissingCoreDependencies = ::collectMissingCoreRuntimeDependencies,
        verifyCoreDependencies = ::verifyCoreRuntimeDependencies,
        publishProgress = { variant, stageText, progress, downloadedBytes, totalBytes ->
            updateDownloadProgress(
                variant = variant,
                actionLabel = "合并 PR",
                stageText = stageText,
                progress = progress,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes
            )
        },
        clearProgress = { _downloadProgress.value = CoreDownloadProgress() }
    )
    private val installedStateController = CoreInstalledStateController(
        settingsRepository = settingsRepository,
        resolveRepo = ::resolveRepo,
        resolveBranch = ::resolveBranch,
        removeLatestVersion = { updateCheckController.forgetLatestVersion(it) },
        updateCoreInfo = { _coreInfoList.value = it },
        readCoreInfo = { _coreInfoList.value },
        refreshCoreInfoAndAwait = ::refreshCoreInfoAndAwait
    )
    private val revisionVersionCache = CoreRevisionVersionCache()
    private val pendingMutationController = CorePendingMutationController(
        context = context,
        operationController = operationController,
        readOperationState = { _operationState.value },
        readPendingDependencyRepair = { _pendingDependencyRepair.value },
        writePendingDependencyRepair = { _pendingDependencyRepair.value = it },
        runtimeDependencyPackManager = runtimeDependencyPackManager,
        installController = installController,
        installedStateController = installedStateController,
        mutationMutex = mutationMutex,
        verifyCoreDependencies = ::verifyCoreRuntimeDependencies,
        collectMissingCoreDependencies = ::collectMissingCoreRuntimeDependencies,
        currentRunMode = ::currentRunMode,
        locationOf = ::getCoreLocation,
        createStagingDir = ::createCoreTempDir,
        writeOperationMarker = ::writeOperationMarker,
        refreshCoreInfoAndAwait = ::refreshCoreInfoAndAwait,
        publishProgress = { progress -> _downloadProgress.value = progress },
        clearProgress = { _downloadProgress.value = CoreDownloadProgress() },
        logWarning = ::logRecoverableWarning
    )
    private val installPipelineController = CoreInstallPipelineController(
        httpClient = httpClient,
        githubProxyService = githubProxyService,
        pullRequestMergeService = pullRequestMergeService,
        installController = installController,
        installedStateController = installedStateController,
        mutationMutex = mutationMutex,
        userAgent = USER_AGENT,
        runOwnedOperation = { variant, actionLabel, block ->
            pendingMutationController.runOwnedOperation(variant, actionLabel, block)
        },
        resolveRemoteSource = { variant, branchOverride -> resolveRemoteSource(variant, branchOverride) },
        readLocalMetadata = ::readLocalCoreSourceMetadata,
        currentRunMode = ::currentRunMode,
        locationOf = ::getCoreLocation,
        resolveRepo = ::resolveRepo,
        resolveBranch = ::resolveBranch,
        createStagingDir = ::createCoreTempDir,
        writeOperationMarker = ::writeOperationMarker,
        buildDownloadUrlCandidates = ::buildDownloadUrlCandidates,
        extractDanmuFolder = ::extractDanmuFolder,
        collectMissingCoreDependencies = ::collectMissingCoreRuntimeDependencies,
        readPendingMutationOperationId = { pendingMutationController.pendingOperationId() },
        setPendingMutation = { pending -> pendingMutationController.setPendingMutation(pending) },
        publishProgress = { progress -> _downloadProgress.value = progress },
        clearProgress = { _downloadProgress.value = CoreDownloadProgress() }
    )
    private val revisionController = CoreRevisionController(
        remoteDataSource = remoteDataSource,
        revisionVersionCache = revisionVersionCache,
        installPipeline = installPipelineController,
        runOwnedOperation = { variant, actionLabel, block ->
            pendingMutationController.runOwnedOperation(variant, actionLabel, block)
        },
        resolveRepo = ::resolveRepo,
        resolveBranch = ::resolveBranch,
        resolveBranchCandidates = ::resolveBranchCandidates,
        refreshCoreInfo = ::refreshCoreInfo,
        awaitCoreInfoRefresh = { coreInfoRefreshController.awaitCurrentRefresh() },
        runUpdateCheckAfterRollback = { variant ->
            GithubQuotaRequestContext.automatically {
                if (githubProxyService.apiQuota.blocked() == null) checkAndMarkUpdate(variant)
            }
        }
    )
    private val coreInfoRefreshController = CoreInfoRefreshController(
        scope = repoScope,
        displayCache = coreDisplayCache,
        readCoreInfoList = { _coreInfoList.value },
        writeCoreInfoList = { transform -> _coreInfoList.update(transform) },
        latestVersionLabel = { variant, repo, branch ->
            updateCheckController.latestVersionLabel(variant, repo, branch)
        },
        resolveRepo = ::resolveRepo,
        resolveBranch = ::resolveBranch,
        currentRunMode = ::currentRunMode,
        locationOf = ::getCoreLocation,
        hasValidCore = ::hasValidCore,
        readLocalVersion = ::readLocalCoreVersion,
        readLocalMetadata = ::readLocalCoreSourceMetadata,
        coreProjectDir = { RuntimePaths.normalProjectDir(context) },
        mutationMutex = mutationMutex,
        logWarning = ::logRecoverableWarning
    )

    init {
        workDirPrefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        runtimePrefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        repoScope.launch {
            combine(
                settingsRepository.customCoreSource,
                settingsRepository.coreBranchSelections
            ) { customSource, selections -> customSource.repo to selections }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    refreshCoreInfo()
                }
        }
        coreInfoRefreshController.startWatcher(currentRunMode())
        refreshCoreInfo()
        repoScope.launch {
            cleanupStaleCoreArtifacts()
            pendingMutationController.restoreRecordedDependencyRepair()
        }
    }

    override suspend fun confirmCandidateCore(
        variant: ApiVariant,
        runMode: RunMode,
        expectedInstalledAtMs: Long
    ): Result<Boolean> = candidateController.confirmCandidateCore(
        variant = variant,
        runMode = runMode,
        expectedInstalledAtMs = expectedInstalledAtMs
    )

    override suspend fun restoreCandidateCore(
        variant: ApiVariant,
        runMode: RunMode,
        expectedInstalledAtMs: Long
    ): Result<Boolean> = candidateController.restoreCandidateCore(
        variant = variant,
        runMode = runMode,
        expectedInstalledAtMs = expectedInstalledAtMs
    )

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
        return coreInfoRefreshController.loadState(variant, mode).info.isReady
    }

    override fun refreshCoreInfo() {
        coreInfoRefreshController.refresh()
    }

    override suspend fun refreshCoreInfoAndAwait() {
        coreInfoRefreshController.refreshAndAwait()
    }

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

    private fun buildDownloadUrlCandidates(zipUrl: String): List<String> =
        remoteDataSource.buildDownloadUrlCandidates(zipUrl)

    private fun fetchLatestRelease(repo: String): GithubRelease? =
        remoteDataSource.fetchLatestRelease(repo)

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
        return settingsRepository.coreBranchSelections.value
            .resolve(variant)
            .ifBlank { null }
    }

    private fun resolveBranchCandidates(variant: ApiVariant): List<String> {
        return resolveBranch(variant)?.let(::listOf) ?: defaultBranchCandidates()
    }

    private fun resolveRemoteSource(
        variant: ApiVariant,
        branchOverride: String? = null
    ): CoreRemoteSource? {
        val repo = resolveRepo(variant)
        if (repo.isBlank()) return null

        val requestedBranch = normalizeGithubBranch(branchOverride)
            .takeIf { it.isNotBlank() }
            ?: resolveBranch(variant)
        val branch = requestedBranch ?: remoteBranchSourceController.resolveDefaultBranch(repo)
        if (!branch.isNullOrBlank()) {
            return remoteBranchSourceController.fetchSource(repo, branch)
                ?: throw IOException("未找到分支 $branch，请检查仓库与分支名")
        }

        // Older GitHub mirrors may not expose repository metadata or branches.
        return fetchLatestRelease(repo)?.let { CoreRemoteSource(release = it) }
    }

    override suspend fun fetchBranchSnapshot(variant: ApiVariant): Result<CoreBranchSnapshot> =
        withContext(Dispatchers.IO) {
            runCatchingCancellable {
                val repo = resolveRepo(variant)
                if (repo.isBlank()) throw IOException("请先配置核心仓库")
                val branch = resolveBranch(variant)
                    ?: remoteBranchSourceController.resolveDefaultBranch(repo)
                    ?: throw IOException("无法读取默认分支")
                val commit = remoteBranchSourceController.fetchSnapshot(repo, branch)
                    ?: throw IOException("无法读取分支最新提交")
                CoreBranchSnapshot(repo = repo, branch = branch, commit = commit)
            }
        }

    override suspend fun fetchBranches(variant: ApiVariant): Result<CoreBranchCatalog> =
        withContext(Dispatchers.IO) {
            runCatching {
                val repo = resolveRepo(variant)
                if (repo.isBlank()) throw IOException("请先配置核心仓库")
                val catalog = buildCoreBranchCatalog(
                    variant = variant,
                    repo = repo,
                    defaultBranch = remoteDataSource.fetchDefaultBranch(repo),
                    branches = remoteDataSource.fetchBranchList(repo)
                )
                if (catalog.branches.isEmpty()) {
                    throw IOException("未读取到仓库分支，请检查仓库权限或 GitHub 线路")
                }
                catalog
            }
        }

    override suspend fun installCore(variant: ApiVariant): Result<Unit> =
        installPipelineController.installOrUpdateCore(variant, actionLabel = "安装")

    override suspend fun prepareCoreUpdate(variant: ApiVariant): Result<CoreUpdatePlan> =
        updateCheckController.prepareUpdatePlan(variant)

    override suspend fun updateCore(request: CoreUpdateRequest): Result<Unit> =
        installPipelineController.installOrUpdateCore(
            request.plan.variant,
            actionLabel = "更新",
            updateRequest = request
        )

    override suspend fun switchCoreBranch(variant: ApiVariant, branch: String): Result<Unit> {
        val normalizedBranch = normalizeGithubBranch(branch)
        if (normalizedBranch.isBlank()) return Result.failure(IOException("分支名不能为空"))
        return installPipelineController.installOrUpdateCore(
            variant = variant,
            actionLabel = "切换分支",
            branchOverride = normalizedBranch
        )
    }

    override suspend fun deleteCore(variant: ApiVariant): Result<Unit> =
        withContext(Dispatchers.IO) {
            pendingMutationController.runOwnedOperation(variant, "删除") { operationId ->
                val mode = currentRunMode()
                val location = getCoreLocation(variant, mode)
                mutationMutex.withLock {
                    candidateController.load()
                        ?.takeIf { it.variantKey == variant.key && it.runModeKey == mode.key }
                        ?.let { candidateController.clear(it, deleteBackup = true) }
                    if (mode != RunMode.Normal) {
                        deleteRootCoreDir(location.rootDirPath)
                    }
                    if (location.normalDir.exists() && !location.normalDir.deleteRecursively()) {
                        throw IOException("删除核心目录失败：${location.normalDir.absolutePath}")
                    }
                }
                RuntimeDependencyHealthChecker.clearPendingIssue(context, variant)
                refreshCoreInfoAndAwait()
            }
        }

    override suspend fun checkAndMarkUpdate(variant: ApiVariant) {
        updateCheckController.checkAndMarkUpdate(variant)
    }

    override suspend fun checkAllUpdates() {
        updateCheckController.checkAllUpdates()
    }

    override suspend fun fetchUpdateComparison(
        variant: ApiVariant
    ): Result<CoreUpdateComparison> = updateCheckController.fetchUpdateComparison(variant)

    override suspend fun rollbackCore(variant: ApiVariant, release: GithubRelease): Result<Unit> =
        revisionController.rollbackCore(variant, release)

    override suspend fun rollbackCore(
        variant: ApiVariant,
        revision: CoreRevision
    ): Result<Unit> = revisionController.rollbackCore(variant, revision)

    override suspend fun fetchReleaseHistory(variant: ApiVariant): List<GithubRelease> =
        revisionController.fetchReleaseHistory(variant)

    override suspend fun fetchRevisionHistory(
        variant: ApiVariant,
        page: Int,
        pageSize: Int,
        query: String
    ): Result<CoreRevisionPage> = revisionController.fetchRevisionHistory(
        variant = variant,
        page = page,
        pageSize = pageSize,
        query = query
    )

    override suspend fun fetchRevisionVersion(
        variant: ApiVariant,
        revision: CoreRevision
    ): Result<String?> = revisionController.fetchRevisionVersion(variant, revision)

    override suspend fun fetchRevisionDetails(
        variant: ApiVariant,
        revision: CoreRevision
    ): Result<CoreRevisionDetails> = revisionController.fetchRevisionDetails(variant, revision)

    override suspend fun fetchPullRequests(
        variant: ApiVariant,
        page: Int,
        pageSize: Int,
        filter: CorePullRequestFilter,
        query: String,
        forceRefresh: Boolean
    ): Result<CorePullRequestPage> = pullRequestController.fetchPullRequests(
        variant = variant,
        page = page,
        pageSize = pageSize,
        filter = filter,
        query = query,
        forceRefresh = forceRefresh
    )

    override suspend fun enrichPullRequestListItem(
        variant: ApiVariant,
        pullRequest: CorePullRequest,
        allowFirstContributionLookup: Boolean,
        allowInclusionLookup: Boolean
    ): Result<CorePullRequest> = pullRequestController.enrichPullRequestListItem(
        variant = variant,
        pullRequest = pullRequest,
        allowFirstContributionLookup = allowFirstContributionLookup,
        allowInclusionLookup = allowInclusionLookup
    )

    override suspend fun fetchPullRequestDetails(
        variant: ApiVariant,
        pullRequestNumber: Int
    ): Result<CorePullRequest> = pullRequestController.fetchPullRequestDetails(
        variant = variant,
        pullRequestNumber = pullRequestNumber
    )

    override suspend fun fetchPullRequestFiles(
        variant: ApiVariant,
        pullRequestNumber: Int,
        page: Int,
        pageSize: Int
    ): Result<CorePullRequestFilePage> = pullRequestController.fetchPullRequestFiles(
        variant = variant,
        pullRequestNumber = pullRequestNumber,
        page = page,
        pageSize = pageSize
    )

    override suspend fun preparePullRequestStack(
        variant: ApiVariant,
        pullRequestNumbers: List<Int>
    ): Result<CoreDependencyRepairRequest> =
        pullRequestController.preparePullRequestStack(variant, pullRequestNumbers)

    override suspend fun repairPendingDependenciesOnline(operationId: Long): Result<Unit> =
        pendingMutationController.repairPendingDependenciesOnline(operationId)

    override suspend fun repairPendingDependenciesFromArchive(
        operationId: Long,
        archiveUri: String
    ): Result<Unit> = pendingMutationController.repairPendingDependenciesFromArchive(operationId, archiveUri)

    override suspend fun applyPendingCoreMutation(operationId: Long): Result<Unit> =
        pendingMutationController.applyPendingMutation(operationId)

    override suspend fun discardPendingCoreMutation(operationId: Long): Result<Unit> =
        pendingMutationController.discardPendingMutation(operationId)

    override suspend fun applyWorkDirectoryChange(
        targetPath: String?,
        migrateSelectedCore: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        val operationId = runCatching { pendingMutationController.beginOperation(null, "切换工作目录") }
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
            pendingMutationController.finishOperation(operationId)
            Result.success(applyResult.message)
        } catch (cancelled: CancellationException) {
            pendingMutationController.finishOperation(operationId)
            throw cancelled
        } catch (error: Exception) {
            pendingMutationController.finishOperation(operationId)
            Result.failure(error)
        }
    }

    override suspend fun prepareInstalledCoreDependencyRepair(
        variant: ApiVariant,
        origin: CoreDependencyRepairOrigin,
        resumeAction: RuntimeDependencyResumeAction,
        suspectedMissingPackage: String?
    ): CoreDependencyRepairRequest? = pendingMutationController.prepareInstalledCoreDependencyRepair(
        variant = variant,
        origin = origin,
        resumeAction = resumeAction,
        suspectedMissingPackage = suspectedMissingPackage
    )

    private fun readLocalCoreSourceMetadata(
        variant: ApiVariant,
        mode: RunMode
    ): CoreSourceMetadata? {
        val location = getCoreLocation(variant, mode)
        return CoreSourceMetadataStore.read(
            mode = mode,
            normalFile = CoreSourceMetadataStore.fileIn(location.normalDir),
            rootPath = "${location.rootDirPath}/${CoreSourceMetadataStore.FILE_NAME}"
        ) { path ->
            val result = RootShell.exec("cat ${shellQuote(path)} 2>/dev/null", timeoutMs = 4500L)
            result.stdout.takeIf { result.ok && it.isNotBlank() }
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
        return CoreStagingPolicy.prepareStagingDirectory(targetDir, suffix)
    }

    private fun writeOperationMarker(directory: File, operationId: Long) {
        CoreStagingPolicy.writeOperationMarker(directory, operationId)
    }

    private fun cleanupStaleCoreArtifacts() {
        val activeBackupPath = candidateController.load()?.backupDirPath
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { File(it).canonicalPath }.getOrNull() }
        cleanupStaleCoreDirectories(
            projectDir = RuntimePaths.normalProjectDir(context),
            activeBackupPath = activeBackupPath
        )
    }
    private fun deleteRootCoreDir(rootDirPath: String) {
        CoreRootProbe.deleteDirectory(rootDirPath)
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
        return CoreRootProbe.hasValidCore(rootDirPath)
    }

    private fun rootReadCoreVersion(rootDirPath: String): String? {
        return CoreRootProbe.readVersion(rootDirPath)
    }

    private fun extractDanmuFolder(zipStream: InputStream, outDir: File): CoreArchiveExtraction {
        return CoreArchiveLayout.extractDanmuFolder(zipStream, outDir)
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

}

internal object CoreRootProbe {
    fun hasValidCore(rootDirPath: String): Boolean {
        val script = """
            DIR=${shellQuote(rootDirPath)}
            [ -f "${'$'}DIR/worker.js" ] && exit 0
            [ -f "${'$'}DIR/danmu_api/worker.js" ] && exit 0
            [ -f "${'$'}DIR/danmu-api/worker.js" ] && exit 0
            exit 1
        """.trimIndent()
        return RootShell.exec(script, timeoutMs = 3000L).ok
    }

    fun readVersion(rootDirPath: String): String? {
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
        val candidateArgs = (globalsCandidates + packageCandidates)
            .joinToString(separator = " ") { shellQuote(it) }
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
        return CoreVersionParser.extractVersion(text)?.removePrefix("v")?.takeIf { it.isNotBlank() }
    }

    fun deleteDirectory(rootDirPath: String) {
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
}
