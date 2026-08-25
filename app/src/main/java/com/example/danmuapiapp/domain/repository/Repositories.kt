package com.example.danmuapiapp.domain.repository

import com.example.danmuapiapp.domain.model.*
import kotlinx.coroutines.flow.StateFlow

interface RuntimeRepository {
    val runtimeState: StateFlow<RuntimeState>
    val logs: StateFlow<List<LogEntry>>
    fun startService()
    fun stopService()
    fun restartService()
    fun refreshRuntimeState()
    suspend fun refreshRuntimeStateAndAwait()
    fun setAppForeground(foreground: Boolean)
    fun refreshLogs()
    fun applyServiceConfig(
        port: Int,
        token: String,
        restartIfRunning: Boolean = true,
        listenMode: RuntimeListenMode? = null
    )
    fun updatePort(port: Int)
    fun updateToken(token: String)
    fun updateVariant(variant: ApiVariant)
    fun updateRunMode(mode: RunMode)
    fun beginRuntimeTransition(kind: RuntimeTransitionKind, message: String): Long
    fun updateRuntimeTransition(id: Long, message: String)
    fun endRuntimeTransition(id: Long)
    fun clearLogs()
    fun addLog(level: LogLevel, message: String)
}

interface CoreRepository {
    val coreInfoList: StateFlow<List<CoreInfo>>
    val isCoreInfoLoading: StateFlow<Boolean>
    val downloadProgress: StateFlow<CoreDownloadProgress>
    val pendingDependencyRepair: StateFlow<CoreDependencyRepairRequest?>
    val operationState: StateFlow<CoreOperationState>
    val candidateState: StateFlow<CoreCandidateState?>
    fun isCoreInstalled(variant: ApiVariant): Boolean
    fun isCoreReady(variant: ApiVariant): Boolean
    fun refreshCoreInfo()
    suspend fun refreshCoreInfoAndAwait()
    suspend fun checkUpdate(variant: ApiVariant): GithubRelease?
    suspend fun fetchBranches(variant: ApiVariant): Result<CoreBranchCatalog>
    suspend fun checkAndMarkUpdate(variant: ApiVariant)
    suspend fun checkAllUpdates()
    suspend fun fetchUpdateComparison(variant: ApiVariant): Result<CoreUpdateComparison>
    suspend fun installCore(variant: ApiVariant): Result<Unit>
    suspend fun updateCore(variant: ApiVariant): Result<Unit>
    suspend fun switchCoreBranch(variant: ApiVariant, branch: String): Result<Unit>
    suspend fun deleteCore(variant: ApiVariant): Result<Unit>
    suspend fun rollbackCore(variant: ApiVariant, release: GithubRelease): Result<Unit>
    suspend fun fetchReleaseHistory(variant: ApiVariant): List<GithubRelease>
    suspend fun fetchRevisionHistory(
        variant: ApiVariant,
        page: Int,
        pageSize: Int = 15,
        query: String = ""
    ): Result<CoreRevisionPage>
    suspend fun fetchRevisionVersion(
        variant: ApiVariant,
        revision: CoreRevision
    ): Result<String?>
    suspend fun fetchRevisionDetails(
        variant: ApiVariant,
        revision: CoreRevision
    ): Result<CoreRevisionDetails>
    suspend fun fetchPullRequests(
        variant: ApiVariant,
        page: Int,
        pageSize: Int = 15,
        filter: CorePullRequestFilter = CorePullRequestFilter.Open,
        query: String = "",
        forceRefresh: Boolean = false
    ): Result<CorePullRequestPage>
    suspend fun enrichPullRequestListItem(
        variant: ApiVariant,
        pullRequest: CorePullRequest,
        allowFirstContributionLookup: Boolean,
        allowInclusionLookup: Boolean
    ): Result<CorePullRequest>
    suspend fun fetchPullRequestDetails(
        variant: ApiVariant,
        pullRequestNumber: Int
    ): Result<CorePullRequest>
    suspend fun fetchPullRequestFiles(
        variant: ApiVariant,
        pullRequestNumber: Int,
        page: Int,
        pageSize: Int = 15
    ): Result<CorePullRequestFilePage>
    suspend fun preparePullRequestStack(
        variant: ApiVariant,
        pullRequestNumbers: List<Int>
    ): Result<CoreDependencyRepairRequest>
    suspend fun rollbackCore(variant: ApiVariant, revision: CoreRevision): Result<Unit>
    suspend fun repairPendingDependenciesOnline(operationId: Long): Result<Unit>
    suspend fun repairPendingDependenciesFromArchive(operationId: Long, archiveUri: String): Result<Unit>
    suspend fun applyPendingCoreMutation(operationId: Long): Result<Unit>
    suspend fun discardPendingCoreMutation(operationId: Long): Result<Unit>
    suspend fun confirmCandidateCore(
        variant: ApiVariant,
        runMode: RunMode,
        expectedInstalledAtMs: Long
    ): Result<Boolean>
    suspend fun restoreCandidateCore(
        variant: ApiVariant,
        runMode: RunMode,
        expectedInstalledAtMs: Long
    ): Result<Boolean>
    suspend fun applyWorkDirectoryChange(
        targetPath: String?,
        migrateSelectedCore: Boolean
    ): Result<String>
    suspend fun prepareInstalledCoreDependencyRepair(
        variant: ApiVariant,
        origin: CoreDependencyRepairOrigin = CoreDependencyRepairOrigin.WorkDirectory,
        resumeAction: RuntimeDependencyResumeAction = RuntimeDependencyResumeAction.None,
        suspectedMissingPackage: String? = null
    ): CoreDependencyRepairRequest?
}

interface SettingsRepository {
    val githubProxy: StateFlow<String>
    val announcementBaseUrl: StateFlow<String>
    val autoStart: StateFlow<Boolean>
    val keepAlive: StateFlow<Boolean>
    val keepAliveHeartbeatEnabled: StateFlow<Boolean>
    val keepAliveHeartbeatMode: StateFlow<KeepAliveHeartbeatMode>
    val keepAliveHeartbeatIntervalMinutes: StateFlow<Int>
    val coreUpdateCheckIntervalMinutes: StateFlow<Int>
    val normalModeStabilityMode: StateFlow<NormalModeStabilityMode>
    val nightMode: StateFlow<NightModePreference>
    val glassMaterial: StateFlow<GlassMaterialPreference>
    val glassTuning: StateFlow<GlassTuningPreference>
    val appBackground: StateFlow<AppBackgroundPreference>
    val appDpiOverride: StateFlow<Int>
    val hideFromRecents: StateFlow<Boolean>
    val coreDisplayNames: StateFlow<CoreVariantDisplayNames>
    val coreBranchSelections: StateFlow<CoreBranchSelections>
    val customCoreSource: StateFlow<ResolvedCustomCoreSource>
    val customRepo: StateFlow<String>
    val customRepoBranch: StateFlow<String>
    val customRepoDisplayName: StateFlow<String>
    val tokenVisible: StateFlow<Boolean>
    val fileLogEnabled: StateFlow<Boolean>
    val logEnabled: StateFlow<Boolean>
    val logPreviewEnabled: StateFlow<Boolean>
    val logMaxCount: StateFlow<Int>
    fun setGithubProxy(proxy: String)
    fun setGithubToken(token: String)
    fun setAutoStart(enabled: Boolean)
    fun setKeepAlive(enabled: Boolean)
    fun setKeepAliveHeartbeatEnabled(enabled: Boolean)
    fun setKeepAliveHeartbeatMode(mode: KeepAliveHeartbeatMode)
    fun setKeepAliveHeartbeatIntervalMinutes(minutes: Int)
    fun setCoreUpdateCheckIntervalMinutes(minutes: Int)
    fun setNormalModeStabilityMode(mode: NormalModeStabilityMode)
    fun setNightMode(mode: NightModePreference)
    fun setGlassMaterial(material: GlassMaterialPreference)
    fun setGlassTuning(tuning: GlassTuningPreference)
    fun setAppBackground(background: AppBackgroundPreference)
    fun setAppDpiOverride(dpi: Int)
    fun setHideFromRecents(enabled: Boolean)
    fun setVariantDisplayName(variant: ApiVariant, name: String)
    fun setCoreBranch(variant: ApiVariant, branch: String)
    fun saveCustomCoreSource(repoInput: String, branchInput: String): ResolvedCustomCoreSource
    fun saveCustomCoreConfig(
        displayName: String,
        repoInput: String,
        branchInput: String
    ): ResolvedCustomCoreConfig
    fun setCustomRepo(repo: String)
    fun setCustomRepoBranch(branch: String)
    fun setCustomRepoDisplayName(name: String)
    fun setTokenVisible(visible: Boolean)
    fun setFileLogEnabled(enabled: Boolean)
    fun setLogEnabled(enabled: Boolean)
    fun setLogPreviewEnabled(enabled: Boolean)
    fun setLogMaxCount(count: Int)
    fun getIgnoredUpdateVersion(variant: ApiVariant): String?
    fun setIgnoredUpdateVersion(variant: ApiVariant, version: String?)
    fun reloadFromStorage()
}

interface RequestRecordRepository {
    val records: StateFlow<List<RequestRecord>>
    suspend fun refreshFromService()
    fun addRecord(record: RequestRecord)
    fun clearRecords()
}

interface AccessControlRepository {
    suspend fun fetchSnapshot(includeLanNeighbors: Boolean = false): Result<DeviceAccessSnapshot>
    suspend fun scanLanDevices(): Result<DeviceAccessSnapshot>
    suspend fun saveConfig(
        config: DeviceAccessConfig,
        clearDevices: Boolean = false,
        clearBlacklist: Boolean = false
    ): Result<DeviceAccessSnapshot>
}

interface CacheRepository {
    val cacheStats: StateFlow<CacheStats>
    val cacheEntries: StateFlow<List<CacheEntry>>
    val clearCapability: StateFlow<CacheClearCapability>
    val isLoading: StateFlow<Boolean>
    suspend fun refresh()
    suspend fun clear(items: Set<CacheClearItem>): Result<CacheClearResult>
}

interface DanmuDownloadRepository {
    val settings: StateFlow<DanmuDownloadSettings>
    val records: StateFlow<List<DanmuDownloadRecord>>
    val queueTasks: StateFlow<List<DanmuDownloadTask>>
    fun setSaveTreeUri(uri: String, displayName: String)
    fun clearSaveTreeUri()
    fun setDefaultFormat(format: DanmuDownloadFormat)
    fun setFileNameTemplate(template: String)
    fun setConflictPolicy(policy: DownloadConflictPolicy)
    fun setThrottlePreset(preset: DownloadThrottlePreset)
    fun setCustomThrottleConfig(
        baseDelayMs: Long,
        jitterMaxMs: Long,
        batchSize: Int,
        batchRestMs: Long,
        backoffBaseMs: Long,
        backoffMaxMs: Long
    )
    fun enqueueTasks(inputs: List<DanmuDownloadInput>): Int
    fun setQueueTaskStatus(
        taskId: Long,
        status: DownloadQueueStatus,
        detail: String = "",
        incrementAttempt: Boolean = false
    )
    /**
     * Replaces the resolved download chain while preserving task identity,
     * status, and attempt history.
     */
    fun updateQueueTaskInput(
        taskId: Long,
        input: DanmuDownloadInput,
        detail: String = ""
    ): Boolean
    fun setQueueTaskRetryNotBefore(taskId: Long, timestampMs: Long)
    fun resetQueueTasks(taskIds: Set<Long>, detail: String = "等待重试"): Int
    fun markRunningTasksAsPending(detail: String = "等待恢复"): Int
    fun clearQueueTasks()
    fun clearCompletedQueueTasks(): Int
    fun reorderQueueTasks(reorderedTasks: List<DanmuDownloadTask>)
    suspend fun downloadEpisode(
        input: DanmuDownloadInput,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<DanmuDownloadResult>
    suspend fun loadDanmuPreview(
        record: DanmuDownloadRecord,
        previewLimit: Int = 50000
    ): Result<DanmuFilePreview>
    suspend fun syncExistingFiles(): Result<DownloadDirectorySyncResult>
    suspend fun deleteRecords(
        recordIds: Set<Long>,
        deleteLocalFiles: Boolean
    ): Result<DownloadRecordDeleteResult>
}

interface AdminSessionRepository {
    val sessionState: StateFlow<AdminSessionState>
    fun refresh()
    suspend fun login(inputToken: String): Result<Unit>
    suspend fun logout()
    suspend fun setAdminTokenAndLogin(token: String): Result<Unit>
    fun currentAdminTokenOrNull(): String
}
