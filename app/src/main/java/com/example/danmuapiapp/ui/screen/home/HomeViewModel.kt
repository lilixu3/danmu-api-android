package com.example.danmuapiapp.ui.screen.home

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.FileObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.data.service.AppForegroundUpdateChecker
import com.example.danmuapiapp.data.service.AppForegroundAnnouncementChecker
import com.example.danmuapiapp.data.service.AppUpdateService
import com.example.danmuapiapp.data.service.GithubProxyService
import com.example.danmuapiapp.data.service.GithubProxySpeedTester
import com.example.danmuapiapp.data.service.NormalModeRuntimeProfiles
import com.example.danmuapiapp.data.service.RuntimePaths
import com.example.danmuapiapp.data.service.RootShell
import com.example.danmuapiapp.data.util.RuntimeTokenNormalizer
import com.example.danmuapiapp.domain.model.*
import com.example.danmuapiapp.domain.repository.AdminSessionRepository
import com.example.danmuapiapp.domain.repository.CacheRepository
import com.example.danmuapiapp.domain.repository.CoreRepository
import com.example.danmuapiapp.domain.repository.RequestRecordRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.domain.repository.SettingsRepository
import com.example.danmuapiapp.ui.common.AppUpdateInstallerController
import com.example.danmuapiapp.ui.common.CoreDependencyRepairController
import com.example.danmuapiapp.ui.common.ProxyPickerController
import com.example.danmuapiapp.ui.common.RuntimeRestartEvidence
import com.example.danmuapiapp.ui.common.awaitCoreRestart
import com.example.danmuapiapp.ui.common.buildRootSwitchDeniedMessage
import com.example.danmuapiapp.ui.common.continueAfterDependencyRepair
import com.example.danmuapiapp.ui.screen.home.support.resolveAutoCoreUpdatePrompt
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val runtimeRepo: RuntimeRepository,
    private val coreRepo: CoreRepository,
    private val requestRecordRepo: RequestRecordRepository,
    private val settingsRepo: SettingsRepository,
    private val githubProxyService: GithubProxyService,
    private val githubProxySpeedTester: GithubProxySpeedTester,
    private val appForegroundUpdateChecker: AppForegroundUpdateChecker,
    private val appForegroundAnnouncementChecker: AppForegroundAnnouncementChecker,
    private val appUpdateService: AppUpdateService,
    private val cacheRepo: CacheRepository,
    private val adminSessionRepository: AdminSessionRepository
) : ViewModel() {
    companion object {
        private const val CACHE_FILE_REFRESH_DEBOUNCE_MS = 420L
        private const val REQUEST_RECORD_REFRESH_DELAY_MS = 900L
        private const val CACHE_RUNTIME_REFRESH_DELAY_MS = 1200L
        private const val CONSERVATIVE_REQUEST_RECORD_REFRESH_DELAY_MS = 1600L
        private const val CONSERVATIVE_CACHE_RUNTIME_REFRESH_DELAY_MS = 2200L
        private const val CACHE_FILE_OBSERVER_MASK = FileObserver.CLOSE_WRITE or
            FileObserver.MODIFY or
            FileObserver.CREATE or
            FileObserver.MOVED_TO
    }

    val runtimeState = runtimeRepo.runtimeState
    val coreInfoList = coreRepo.coreInfoList
    val isCoreInfoLoading = coreRepo.isCoreInfoLoading
    val pendingDependencyRepair = coreRepo.pendingDependencyRepair
    val coreDisplayNames = settingsRepo.coreDisplayNames
    val customRepo = settingsRepo.customRepo
    val customRepoBranch = settingsRepo.customRepoBranch
    val tokenVisible = settingsRepo.tokenVisible
    val proxyOptions = githubProxyService.proxyOptions()
    val cacheStats = cacheRepo.cacheStats
    val cacheEntries = cacheRepo.cacheEntries
    val cacheClearCapability = cacheRepo.clearCapability
    val isCacheLoading = cacheRepo.isLoading
    val adminSessionState = adminSessionRepository.sessionState
    val unreadAnnouncements = appForegroundAnnouncementChecker.unreadAnnouncements
    val requestTotalCount: StateFlow<Int> = requestRecordRepo.records
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val requestTodayCount: StateFlow<Int> = requestRecordRepo.records
        .map { list ->
            val today = LocalDate.now(ZoneId.systemDefault())
            list.count { record ->
                val day = Instant.ofEpochMilli(record.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                day == today
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    var showNoCoreDialog by mutableStateOf(false)
        private set
    var unavailableVariant by mutableStateOf<ApiVariant?>(null)
        private set
    var showVariantPicker by mutableStateOf(false)
        private set
    var isInstallingCore by mutableStateOf(false)
        private set
    var isSwitchingCore by mutableStateOf(false)
        private set
    var isUpdatingCore by mutableStateOf(false)
        private set
    var isRepairingDependencies by mutableStateOf(false)
        private set
    var isCheckingCoreUpdate by mutableStateOf(false)
        private set
    var showUpdatePromptDialog by mutableStateOf(false)
        private set
    var updatePromptVariant by mutableStateOf<ApiVariant?>(null)
        private set
    var updatePromptCurrentVersion by mutableStateOf<String?>(null)
        private set
    var updatePromptLatestVersion by mutableStateOf<String?>(null)
        private set
    var updatePromptSourceMismatch by mutableStateOf(false)
        private set
    var updatePromptSourceUnknownLegacy by mutableStateOf(false)
        private set
    var updatePromptDesiredSource by mutableStateOf<String?>(null)
        private set
    var showCoreUpdateDetails by mutableStateOf(false)
        private set
    var coreUpdateDetailsVariant by mutableStateOf<ApiVariant?>(null)
        private set
    var coreUpdateComparison by mutableStateOf<CoreUpdateComparison?>(null)
        private set
    var isLoadingCoreUpdateComparison by mutableStateOf(false)
        private set
    var coreUpdateComparisonError by mutableStateOf<String?>(null)
        private set
    var coreUpdateCheckDialogMessage by mutableStateOf<String?>(null)
        private set
    var coreUpdateCheckDialogIsError by mutableStateOf(false)
        private set
    var showAppUpdatePromptDialog by mutableStateOf(false)
        private set
    var showForegroundAnnouncementDialog by mutableStateOf(false)
        private set
    var appUpdatePromptCurrentVersion by mutableStateOf<String?>(null)
        private set
    var appUpdatePromptLatestVersion by mutableStateOf<String?>(null)
        private set
    var appUpdatePromptReleaseNotes by mutableStateOf("")
        private set
    var appUpdatePromptReleasePage by mutableStateOf("")
        private set
    var appUpdatePromptDownloadUrls by mutableStateOf<List<String>>(emptyList())
        private set
    var appUpdateMessage by mutableStateOf<String?>(null)
        private set
    var foregroundAnnouncementPrompt by mutableStateOf<AppAnnouncement?>(null)
        private set
    var showCacheAdminRequiredDialog by mutableStateOf(false)
        private set
    var cacheAdminRequiredMessage by mutableStateOf("")
        private set

    val showProxyPickerDialog: Boolean
        get() = proxyPickerController.uiState.isVisible
    val proxySelectedId: String
        get() = proxyPickerController.uiState.selectedId
    val proxyTestingIds: Set<String>
        get() = proxyPickerController.uiState.testingIds
    val proxyLatencyMap: Map<String, Long>
        get() = proxyPickerController.uiState.latencyMap
    val showAppUpdateMethodDialog: Boolean
        get() = appUpdateInstaller.uiState.showMethodDialog
    val isDownloadingAppUpdate: Boolean
        get() = appUpdateInstaller.uiState.isDownloading
    val appUpdateDownloadPercent: Int
        get() = appUpdateInstaller.uiState.downloadPercent
    val appUpdateDownloadDetail: String
        get() = appUpdateInstaller.uiState.downloadDetail
    val downloadedAppUpdate: AppUpdateService.DownloadedApk?
        get() = appUpdateInstaller.uiState.downloadedApk
    val showInstallAppUpdateDialog: Boolean
        get() = appUpdateInstaller.uiState.showInstallDialog
    val showDependencyRequiredPrompt: Boolean
        get() = dependencyRepairController.showRequiredPrompt
    val showDependencyRepairDialog: Boolean
        get() = dependencyRepairController.showRepairDialog

    private val ignoredUpdateVersionMap = mutableMapOf<ApiVariant, String?>()
    private val suppressedAutoUpdatePromptVersionMap = mutableMapOf<ApiVariant, String?>()
    private val proxyPickerController = ProxyPickerController(
        githubProxyService = githubProxyService,
        githubProxySpeedTester = githubProxySpeedTester,
        scope = viewModelScope,
        proxyOptionsProvider = { proxyOptions }
    )
    private val appUpdateInstaller = AppUpdateInstallerController(
        scope = viewModelScope,
        appUpdateService = appUpdateService,
        postMessage = { appUpdateMessage = it }
    )
    private var pendingRepairContinuation: PendingRepairContinuation? = null
    private val dependencyRepairController = CoreDependencyRepairController(
        scope = viewModelScope,
        repository = coreRepo,
        setOperating = { isRepairingDependencies = it },
        postMessage = { appUpdateMessage = it },
        onApplied = ::onHomeDependenciesApplied,
        onDiscarded = { request ->
            pendingRepairContinuation = null
            if (request.origin == CoreDependencyRepairOrigin.WorkDirectory) {
                "已取消依赖修复，当前工作目录保持不变"
            } else {
                "已取消${variantLabel(request.variant)}${request.actionLabel}，原核心保持不变"
            }
        }
    )
    private var pendingProxyAction: PendingProxyAction? = null
    private var cacheRefreshJob: Job? = null
    private var requestRecordRefreshJob: Job? = null
    private var runtimeCacheRefreshJob: Job? = null
    private var cacheFileRefreshDebounceJob: Job? = null
    private var coreUpdateComparisonJob: Job? = null
    private var coreUpdateComparisonGeneration = 0L
    private var cacheFileObserver: FileObserver? = null
    private var cacheObserverRootPath: String? = null

    private sealed interface PendingProxyAction {
        data class Install(val variant: ApiVariant) : PendingProxyAction
        data class Update(val variant: ApiVariant) : PendingProxyAction
        data class CheckUpdate(val variant: ApiVariant) : PendingProxyAction
        data class LoadUpdateDetails(val variant: ApiVariant) : PendingProxyAction
        data object RepairDependenciesOnline : PendingProxyAction
    }

    private sealed interface PendingRepairContinuation {
        data class InstallAndStart(val variant: ApiVariant) : PendingRepairContinuation
        data class Update(val variant: ApiVariant) : PendingRepairContinuation
    }

    var isClearingCache by mutableStateOf(false)
        private set
    var selectedCacheClearItems by mutableStateOf(CacheClearItem.entries.toSet())
        private set

    init {
        loadIgnoredUpdateVersions()
        observeUpdatePrompt()
        observeForegroundAppUpdate()
        observeForegroundAnnouncement()
        observeRuntimeDrivenCoreRefresh()
        observeRuntimeDrivenRequestRecordRefresh()
        observeRuntimeDrivenCacheRefresh()
        observeCacheClearCapability()
    }

    private fun observeCacheClearCapability() {
        viewModelScope.launch {
            cacheClearCapability.collect { capability ->
                if (!capability.supportsSelective) {
                    selectedCacheClearItems = CacheClearItem.entries.toSet()
                }
            }
        }
    }

    private fun observeRuntimeDrivenCoreRefresh() {
        viewModelScope.launch {
            runtimeState
                .map { it.runMode to it.variant }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    coreRepo.refreshCoreInfo()
                }
        }
    }

    private fun observeRuntimeDrivenRequestRecordRefresh() {
        viewModelScope.launch {
            runtimeState
                .map { it.status }
                .distinctUntilChanged()
                .collect { status ->
                    if (status == ServiceStatus.Running) {
                        scheduleRequestRecordRefresh()
                    } else {
                        requestRecordRefreshJob?.cancel()
                        requestRecordRefreshJob = null
                    }
                }
        }
    }

    private fun observeRuntimeDrivenCacheRefresh() {
        viewModelScope.launch {
            runtimeState
                .map { it.runMode to it.status }
                .distinctUntilChanged()
                .collect { (runMode, status) ->
                    if (status == ServiceStatus.Running &&
                        runMode == RunMode.Normal &&
                        !normalRuntimeProfile().conservativeMode
                    ) {
                        startCacheFileObserver(runMode)
                    } else {
                        stopCacheFileObserver()
                    }
                    if (status == ServiceStatus.Running) {
                        scheduleRuntimeCacheRefresh()
                    } else {
                        runtimeCacheRefreshJob?.cancel()
                        runtimeCacheRefreshJob = null
                    }
                }
        }
    }

    private fun normalRuntimeProfile() = NormalModeRuntimeProfiles.current(appContext)

    private fun requestRecordRefreshDelayMs(): Long {
        val state = runtimeState.value
        return if (state.runMode == RunMode.Normal && normalRuntimeProfile().conservativeMode) {
            CONSERVATIVE_REQUEST_RECORD_REFRESH_DELAY_MS
        } else {
            REQUEST_RECORD_REFRESH_DELAY_MS
        }
    }

    private fun runtimeCacheRefreshDelayMs(): Long {
        val state = runtimeState.value
        return if (state.runMode == RunMode.Normal && normalRuntimeProfile().conservativeMode) {
            CONSERVATIVE_CACHE_RUNTIME_REFRESH_DELAY_MS
        } else {
            CACHE_RUNTIME_REFRESH_DELAY_MS
        }
    }

    private fun scheduleRequestRecordRefresh(delayMs: Long = requestRecordRefreshDelayMs()) {
        requestRecordRefreshJob?.cancel()
        requestRecordRefreshJob = viewModelScope.launch {
            delay(delayMs)
            refreshRequestRecords()
        }
    }

    private fun refreshRequestRecords() {
        viewModelScope.launch(Dispatchers.IO) {
            if (runtimeState.value.status != ServiceStatus.Running) return@launch
            runCatching {
                requestRecordRepo.refreshFromService()
            }
        }
    }

    fun refreshCache() {
        refreshCacheInternal(force = true)
    }

    private fun scheduleRuntimeCacheRefresh(delayMs: Long = runtimeCacheRefreshDelayMs()) {
        runtimeCacheRefreshJob?.cancel()
        runtimeCacheRefreshJob = viewModelScope.launch {
            delay(delayMs)
            refreshCacheInternal(force = true)
        }
    }

    private fun refreshCacheInternal(force: Boolean = false) {
        if (runtimeState.value.status != ServiceStatus.Running) return
        if (force) {
            cacheRefreshJob?.cancel()
        } else if (cacheRefreshJob?.isActive == true) {
            return
        }
        cacheRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching { cacheRepo.refresh() }
        }
    }

    private fun startCacheFileObserver(runMode: RunMode) {
        val cacheDir = File(RuntimePaths.projectDir(appContext, runMode), ".cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        if (!cacheDir.exists() || !cacheDir.isDirectory) {
            stopCacheFileObserver()
            return
        }
        val rootPath = cacheDir.absolutePath
        if (cacheFileObserver != null && cacheObserverRootPath == rootPath) {
            return
        }
        stopCacheFileObserver()
        val observer = createFileObserver(cacheDir) { event, path ->
            if (event and CACHE_FILE_OBSERVER_MASK == 0) return@createFileObserver
            if (!shouldHandleCacheFileChange(path)) return@createFileObserver
            scheduleCacheRefreshFromFileEvent()
        }
        observer.startWatching()
        cacheFileObserver = observer
        cacheObserverRootPath = rootPath
    }

    private fun stopCacheFileObserver() {
        cacheFileObserver?.let { observer ->
            runCatching { observer.stopWatching() }
        }
        cacheFileObserver = null
        cacheObserverRootPath = null
        cacheFileRefreshDebounceJob?.cancel()
        cacheFileRefreshDebounceJob = null
    }

    private fun scheduleCacheRefreshFromFileEvent() {
        cacheFileRefreshDebounceJob?.cancel()
        cacheFileRefreshDebounceJob = viewModelScope.launch {
            delay(CACHE_FILE_REFRESH_DEBOUNCE_MS)
            refreshCacheInternal()
        }
    }

    private fun shouldHandleCacheFileChange(path: String?): Boolean {
        val name = path
            ?.substringAfterLast('/')
            ?.trim()
            ?.lowercase()
            .orEmpty()
        if (name.isBlank()) return false
        return name == "reqrecords" || name == "todayreqnum"
    }

    private fun createFileObserver(
        dir: File,
        onEvent: (Int, String?) -> Unit
    ): FileObserver {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(dir, CACHE_FILE_OBSERVER_MASK) {
                override fun onEvent(event: Int, path: String?) {
                    onEvent.invoke(event, path)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(dir.absolutePath, CACHE_FILE_OBSERVER_MASK) {
                override fun onEvent(event: Int, path: String?) {
                    onEvent.invoke(event, path)
                }
            }
        }
    }

    fun prepareCacheClearSelection() {
        selectedCacheClearItems = CacheClearItem.entries.toSet()
        refreshCache()
    }

    fun toggleCacheClearItem(item: CacheClearItem) {
        if (!cacheClearCapability.value.supportsSelective) return
        selectedCacheClearItems = if (item in selectedCacheClearItems) {
            selectedCacheClearItems - item
        } else {
            selectedCacheClearItems + item
        }
    }

    fun selectAllCacheClearItems() {
        selectedCacheClearItems = CacheClearItem.entries.toSet()
    }

    fun clearCacheClearSelection() {
        if (cacheClearCapability.value.supportsSelective) selectedCacheClearItems = emptySet()
    }

    fun clearSelectedCache() {
        if (selectedCacheClearItems.isEmpty()) {
            appUpdateMessage = "请至少选择一项要清理的缓存"
            return
        }
        val adminState = adminSessionState.value
        if (!adminState.isAdminMode) {
            cacheAdminRequiredMessage = if (adminState.hasAdminTokenConfigured) {
                "清理缓存属于管理员写操作，请先到 设置 > 管理员权限 开启管理员模式。"
            } else {
                "当前核心可能要求 ADMIN_TOKEN 才能清理缓存，请先到 设置 > 管理员权限 配置并开启管理员模式。"
            }
            showCacheAdminRequiredDialog = true
            return
        }
        if (isClearingCache) return
        isClearingCache = true
        viewModelScope.launch(Dispatchers.IO) {
            val requestedItems = selectedCacheClearItems
            cacheRepo.clear(requestedItems).fold(
                onSuccess = { result ->
                    appUpdateMessage = when {
                        !result.isVerified -> "清理请求已完成，但当前核心未返回确认详情，请刷新后核对"
                        result.usedSelectiveProtocol -> "已清理 ${result.clearedItems.size} 项缓存"
                        else -> "缓存已全部清理"
                    }
                },
                onFailure = { appUpdateMessage = "清理失败：${it.message}" }
            )
            isClearingCache = false
        }
    }

    fun dismissCacheAdminRequiredDialog() {
        showCacheAdminRequiredDialog = false
        cacheAdminRequiredMessage = ""
    }

    fun toggleTokenVisible() {
        settingsRepo.setTokenVisible(!tokenVisible.value)
    }

    fun tryStartService() {
        if (isSwitchingCore || isInstallingCore || isUpdatingCore || isRepairingDependencies) return
        val variant = runtimeState.value.variant
        viewModelScope.launch {
            val ready = withContext(Dispatchers.IO) {
                coreRepo.isCoreReady(variant)
            }
            if (!ready) {
                val info = coreInfoList.value.find { it.variant == variant }
                if (info?.sourceMismatch == true) {
                    appUpdateMessage = "${variantLabel(variant)} 当前来源与设置不一致，请先重新下载核心"
                } else {
                    showNoCoreDialog = true
                }
                return@launch
            }
            runtimeRepo.startService()
        }
    }

    fun dismissNoCoreDialog() {
        showNoCoreDialog = false
    }

    fun dismissUnavailableVariantDialog() {
        unavailableVariant = null
    }

    fun installUnavailableVariant() {
        val variant = unavailableVariant ?: return
        unavailableVariant = null
        installAndStart(variant)
    }

    fun consumeUnavailableVariantForSettings() {
        unavailableVariant = null
    }

    fun openCoreDownloadDialog() {
        if (isSwitchingCore || isInstallingCore || isUpdatingCore || isRepairingDependencies) return
        showNoCoreDialog = true
    }

    fun installAndStart(variant: ApiVariant) {
        if (isSwitchingCore || isUpdatingCore || isRepairingDependencies) return
        showNoCoreDialog = false
        if (!githubProxyService.hasUserSelectedProxy()) {
            pendingProxyAction = PendingProxyAction.Install(variant)
            openProxyPickerDialog()
            return
        }
        doInstallAndStart(variant)
    }

    private fun doInstallAndStart(variant: ApiVariant) {
        isInstallingCore = true
        viewModelScope.launch {
            runtimeRepo.addLog(LogLevel.Info, "正在下载 ${variantLabel(variant)}...")
            coreRepo.installCore(variant).fold(
                onSuccess = {
                    isInstallingCore = false
                    pendingRepairContinuation = null
                    completeInstallAndStart(variant)
                },
                onFailure = { error ->
                    runtimeRepo.addLog(LogLevel.Error, "安装失败: ${error.message}")
                    isInstallingCore = false
                    if (error is CoreDependencyRepairRequiredException) {
                        pendingRepairContinuation = PendingRepairContinuation.InstallAndStart(variant)
                        appUpdateMessage = "${variantLabel(variant)}安装已暂停，等待修复依赖"
                    } else if (githubProxyService.isUsingProxy()) {
                        pendingProxyAction = PendingProxyAction.Install(variant)
                        openProxyPickerDialog()
                    }
                }
            )
        }
    }

    fun openUpdatePromptFromCard() {
        val variant = runtimeState.value.variant
        val info = coreInfoList.value.find { it.variant == variant } ?: return
        if (!info.needsAttention) return
        showCoreAttentionPrompt(variant, info)
    }

    fun quickCheckCurrentCoreUpdate() {
        if (isSwitchingCore || isInstallingCore || isUpdatingCore ||
            isCheckingCoreUpdate || isRepairingDependencies
        ) return

        resetCoreUpdateCheckDialogState()
        val variant = runtimeState.value.variant
        val info = coreInfoList.value.find { it.variant == variant }
        if (info?.isInstalled != true) {
            coreUpdateCheckDialogMessage = "${variantLabel(variant)} 未安装，无法检查更新"
            coreUpdateCheckDialogIsError = true
            return
        }

        if (!githubProxyService.hasUserSelectedProxy()) {
            pendingProxyAction = PendingProxyAction.CheckUpdate(variant)
            openProxyPickerDialog()
            return
        }

        doQuickCheckCurrentCoreUpdate(variant)
    }

    private fun doQuickCheckCurrentCoreUpdate(variant: ApiVariant) {
        isCheckingCoreUpdate = true
        coreUpdateCheckDialogMessage = null
        coreUpdateCheckDialogIsError = false
        viewModelScope.launch {
            val checked = runCatching {
                settingsRepo.setIgnoredUpdateVersion(variant, null)
                ignoredUpdateVersionMap[variant] = null
                coreRepo.checkAndMarkUpdate(variant)
            }.onFailure {
                coreUpdateCheckDialogMessage = "检查更新失败：${it.message ?: "请稍后重试"}"
                coreUpdateCheckDialogIsError = true
            }.isSuccess
            if (!checked) {
                isCheckingCoreUpdate = false
                return@launch
            }

            val latestInfo = coreInfoList.value.find { it.variant == variant }
            if (latestInfo?.updateCheckError != null) {
                coreUpdateCheckDialogMessage =
                    "检查更新失败：${latestInfo.updateCheckError}"
                coreUpdateCheckDialogIsError = true
            } else if (latestInfo?.needsAttention == true) {
                showCoreAttentionPrompt(variant, latestInfo)
            } else if (latestInfo?.isInstalled == true) {
                coreUpdateCheckDialogMessage = "已确认 ${variantLabel(variant)} 当前是最新版本"
                coreUpdateCheckDialogIsError = false
            }
            isCheckingCoreUpdate = false
        }
    }

    fun dismissCurrentUpdatePrompt() {
        val variant = updatePromptVariant
        val latest = updatePromptLatestVersion
        if (
            variant != null &&
            updatePromptSourceMismatch.not() &&
            updatePromptSourceUnknownLegacy.not() &&
            !latest.isNullOrBlank()
        ) {
            suppressedAutoUpdatePromptVersionMap[variant] = latest.trim()
        }
        clearCoreAttentionPrompt()
    }

    fun openUpdateDetailsFromPrompt() {
        val variant = updatePromptVariant ?: return
        if (updatePromptSourceMismatch || updatePromptSourceUnknownLegacy) return
        if (!githubProxyService.hasUserSelectedProxy()) {
            pendingProxyAction = PendingProxyAction.LoadUpdateDetails(variant)
            dismissCurrentUpdatePrompt()
            openProxyPickerDialog()
            return
        }
        openCoreUpdateDetailsNow(variant)
    }

    private fun openCoreUpdateDetailsNow(variant: ApiVariant) {
        val latest = coreInfoList.value.firstOrNull { it.variant == variant }
            ?.availableVersion
            ?.trim()
            .orEmpty()
        if (latest.isNotBlank()) {
            suppressedAutoUpdatePromptVersionMap[variant] = latest
        }
        clearCoreAttentionPrompt()
        coreUpdateDetailsVariant = variant
        coreUpdateComparison = null
        coreUpdateComparisonError = null
        showCoreUpdateDetails = true
        loadCoreUpdateComparison(variant)
    }

    fun retryCoreUpdateComparison() {
        coreUpdateDetailsVariant?.let(::loadCoreUpdateComparison)
    }

    private fun loadCoreUpdateComparison(variant: ApiVariant) {
        coreUpdateComparisonGeneration += 1
        val generation = coreUpdateComparisonGeneration
        coreUpdateComparisonJob?.cancel()
        coreUpdateComparison = null
        coreUpdateComparisonError = null
        isLoadingCoreUpdateComparison = true
        coreUpdateComparisonJob = viewModelScope.launch {
            coreRepo.fetchUpdateComparison(variant).fold(
                onSuccess = { comparison ->
                    if (generation == coreUpdateComparisonGeneration && showCoreUpdateDetails) {
                        coreUpdateComparison = comparison
                    }
                },
                onFailure = { error ->
                    if (generation == coreUpdateComparisonGeneration && showCoreUpdateDetails) {
                        coreUpdateComparisonError = error.message ?: "无法获取核心变更详情"
                    }
                }
            )
            if (generation == coreUpdateComparisonGeneration && showCoreUpdateDetails) {
                isLoadingCoreUpdateComparison = false
                coreUpdateComparisonJob = null
            }
        }
    }

    fun dismissCoreUpdateDetails() {
        coreUpdateComparisonGeneration += 1
        coreUpdateComparisonJob?.cancel()
        coreUpdateComparisonJob = null
        showCoreUpdateDetails = false
        coreUpdateDetailsVariant = null
        coreUpdateComparison = null
        coreUpdateComparisonError = null
        isLoadingCoreUpdateComparison = false
    }

    fun updateFromCoreUpdateDetails() {
        val variant = coreUpdateDetailsVariant ?: return
        val latest = coreInfoList.value.firstOrNull { it.variant == variant }
            ?.availableVersion
            ?.trim()
            .orEmpty()
        if (latest.isNotBlank()) {
            suppressedAutoUpdatePromptVersionMap[variant] = latest
        }
        dismissCoreUpdateDetails()
        updateCurrentVariant(variant)
    }

    fun ignoreCurrentUpdatePrompt() {
        val variant = updatePromptVariant
        val latest = updatePromptLatestVersion?.trim().orEmpty()
        if (
            variant != null &&
            updatePromptSourceMismatch.not() &&
            updatePromptSourceUnknownLegacy.not() &&
            latest.isNotBlank()
        ) {
            settingsRepo.setIgnoredUpdateVersion(variant, latest)
            ignoredUpdateVersionMap[variant] = latest
        }
        clearCoreAttentionPrompt()
    }

    fun updateFromPrompt() {
        val variant = updatePromptVariant ?: runtimeState.value.variant
        val latest = updatePromptLatestVersion
        if (
            updatePromptSourceMismatch.not() &&
            updatePromptSourceUnknownLegacy.not() &&
            !latest.isNullOrBlank()
        ) {
            suppressedAutoUpdatePromptVersionMap[variant] = latest.trim()
        }
        clearCoreAttentionPrompt()
        updateCurrentVariant(variant)
    }

    private fun updateCurrentVariant(variant: ApiVariant) {
        if (isSwitchingCore || isInstallingCore || isUpdatingCore || isRepairingDependencies) return
        if (!githubProxyService.hasUserSelectedProxy()) {
            pendingProxyAction = PendingProxyAction.Update(variant)
            openProxyPickerDialog()
            return
        }
        doUpdateCurrentVariant(variant)
    }

    private fun doUpdateCurrentVariant(variant: ApiVariant) {
        isUpdatingCore = true
        viewModelScope.launch {
            runtimeRepo.addLog(LogLevel.Info, "正在更新 ${variantLabel(variant)}...")
            coreRepo.updateCore(variant).fold(
                onSuccess = {
                    pendingRepairContinuation = null
                    completeCoreUpdate(variant)
                    isUpdatingCore = false
                },
                onFailure = { error ->
                    runtimeRepo.addLog(LogLevel.Error, "更新失败: ${error.message}")
                    isUpdatingCore = false
                    if (error is CoreDependencyRepairRequiredException) {
                        pendingRepairContinuation = PendingRepairContinuation.Update(variant)
                        appUpdateMessage = "${variantLabel(variant)}更新已暂停，等待修复依赖"
                    } else if (githubProxyService.isUsingProxy()) {
                        pendingProxyAction = PendingProxyAction.Update(variant)
                        openProxyPickerDialog()
                    }
                }
            )
        }
    }

    private fun maybeRestartAfterCoreUpdate(variant: ApiVariant) {
        val state = runtimeState.value
        if (state.variant != variant) return
        if (state.status != ServiceStatus.Running) return
        runtimeRepo.addLog(LogLevel.Info, "核心已更新，正在重启服务以应用变更...")
        runtimeRepo.restartService()
    }

    private fun completeInstallAndStart(variant: ApiVariant) {
        val previousVariant = runtimeState.value.variant
        val wasRunning = runtimeState.value.status == ServiceStatus.Running
        val expectsSameVariantRecovery = coreRepo.candidateState.value?.let { candidate ->
            candidate.variant == variant && candidate.hasRecoveryPoint
        } == true
        runtimeRepo.updateVariant(variant)
        runtimeRepo.addLog(LogLevel.Info, "${variantLabel(variant)} 安装成功，已切换为当前核心")
        if (wasRunning) {
            runtimeRepo.addLog(LogLevel.Info, "正在重启服务以应用新核心...")
            runtimeRepo.restartService()
        } else {
            runtimeRepo.startService()
        }
        monitorInstalledCoreStart(variant, previousVariant, wasRunning, expectsSameVariantRecovery)
    }

    private fun monitorInstalledCoreStart(
        installedVariant: ApiVariant,
        previousVariant: ApiVariant,
        wasRunning: Boolean,
        expectsSameVariantRecovery: Boolean
    ) {
        viewModelScope.launch {
            isSwitchingCore = true
            try {
                var sawProgress = false
                val started = withTimeoutOrNull(45_000L) {
                    runtimeState.first { state ->
                        when (state.status) {
                            ServiceStatus.Starting, ServiceStatus.Stopping -> {
                                sawProgress = true
                                false
                            }
                            ServiceStatus.Running -> state.variant == installedVariant &&
                                (sawProgress || !wasRunning)
                            ServiceStatus.Error -> sawProgress
                            ServiceStatus.Stopped -> sawProgress
                        }
                    }
                }?.status == ServiceStatus.Running
                if (started) return@launch

                val recoveredSameVariant = if (expectsSameVariantRecovery) {
                    withTimeoutOrNull(40_000L) {
                        runtimeState.first { state ->
                            state.variant == installedVariant && state.status == ServiceStatus.Running
                        }
                    } != null
                } else false
                if (recoveredSameVariant) {
                    appUpdateMessage = "新核心启动失败，已恢复该版本的上一个可用核心"
                    return@launch
                }

                if (previousVariant == installedVariant) {
                    appUpdateMessage = "安装完成，但核心启动失败，请查看运行日志"
                    return@launch
                }
                val previousReady = withContext(Dispatchers.IO) {
                    coreRepo.isCoreReady(previousVariant)
                }
                runtimeRepo.updateVariant(previousVariant)
                if (!previousReady) {
                    appUpdateMessage = "新核心启动失败，已恢复原核心选择；原核心需要重新安装"
                    return@launch
                }
                runtimeRepo.addLog(
                    LogLevel.Warn,
                    "新安装核心启动失败，已切回 ${variantLabel(previousVariant)}"
                )
                runtimeRepo.restartService()
                val fallbackRunning = withTimeoutOrNull(45_000L) {
                    runtimeState.first { state ->
                        state.variant == previousVariant && state.status == ServiceStatus.Running
                    }
                } != null
                appUpdateMessage = if (fallbackRunning) {
                    "新核心启动失败，已恢复并启动 ${variantLabel(previousVariant)}"
                } else {
                    "新核心启动失败，已恢复 ${variantLabel(previousVariant)} 选择，请检查运行日志"
                }
            } finally {
                isSwitchingCore = false
            }
        }
    }

    private fun completeCoreUpdate(variant: ApiVariant) {
        runtimeRepo.addLog(LogLevel.Info, "${variantLabel(variant)} 更新成功")
        maybeRestartAfterCoreUpdate(variant)
        settingsRepo.setIgnoredUpdateVersion(variant, null)
        ignoredUpdateVersionMap[variant] = null
    }

    private suspend fun onHomeDependenciesApplied(request: CoreDependencyRepairRequest): String {
        val continuation = pendingRepairContinuation
        pendingRepairContinuation = null
        when (continuation) {
            is PendingRepairContinuation.InstallAndStart -> {
                if (continuation.variant == request.variant) {
                    completeInstallAndStart(request.variant)
                    return "${variantLabel(request.variant)}安装成功，服务正在启动"
                }
            }
            is PendingRepairContinuation.Update -> {
                if (continuation.variant == request.variant) {
                    completeCoreUpdate(request.variant)
                    return "${variantLabel(request.variant)}更新成功"
                }
            }
            null -> Unit
        }

        coreRepo.refreshCoreInfo()
        val runtimeContinuation = runtimeRepo.continueAfterDependencyRepair(request)
        if (runtimeContinuation != null) {
            return "${variantLabel(request.variant)}依赖已修复，$runtimeContinuation"
        }
        maybeRestartAfterCoreUpdate(request.variant)
        return if (request.origin == CoreDependencyRepairOrigin.WorkDirectory) {
            "当前工作目录依赖已修复"
        } else {
            "${variantLabel(request.variant)}${request.actionLabel}成功"
        }
    }

    fun dismissDependencyRequiredPrompt() = dependencyRepairController.dismissRequiredPrompt()

    fun openDependencyRepairDialog() = dependencyRepairController.openRepairDialog()

    fun dismissDependencyRepairDialog() = dependencyRepairController.dismissRepairDialog()

    fun repairPendingDependenciesOnline() {
        if (githubProxyService.hasUserSelectedProxy()) {
            dependencyRepairController.repairOnlineNow()
        } else {
            pendingProxyAction = PendingProxyAction.RepairDependenciesOnline
            openProxyPickerDialog()
        }
    }

    fun repairPendingDependenciesFromArchive(archiveUri: String) {
        dependencyRepairController.repairFromArchive(archiveUri)
    }

    fun discardPendingCoreMutation() = dependencyRepairController.discardPendingMutation()

    fun openForegroundAppUpdateMethodDialog() {
        if (appUpdatePromptLatestVersion.isNullOrBlank()) return
        showAppUpdatePromptDialog = false
        appUpdateInstaller.openMethodDialog()
        appForegroundUpdateChecker.consumeLatestPrompt(appUpdatePromptLatestVersion)
    }

    fun dismissForegroundAppUpdatePrompt() {
        showAppUpdatePromptDialog = false
        appForegroundUpdateChecker.snoozeReminderForToday()
        appForegroundUpdateChecker.consumeLatestPrompt(appUpdatePromptLatestVersion)
        appUpdateMessage = "已设置今日不提醒，24 小时内不再弹出更新提示"
    }

    fun dismissForegroundAppUpdateMethodDialog() {
        appUpdateInstaller.dismissMethodDialog()
    }

    fun acknowledgeForegroundAnnouncementPrompt() {
        val announcement = foregroundAnnouncementPrompt ?: return
        showForegroundAnnouncementDialog = false
        appForegroundAnnouncementChecker.acknowledgeAnnouncement(announcement.id)
    }

    fun acknowledgeAllUnreadAnnouncements() {
        val announcementIds = unreadAnnouncements.value.map { it.id }
        if (announcementIds.isEmpty()) return
        showForegroundAnnouncementDialog = false
        appForegroundAnnouncementChecker.acknowledgeAnnouncements(announcementIds)
        if (announcementIds.size > 1) {
            appUpdateMessage = "未读公告已全部标记为已读"
        }
    }

    fun closeForegroundAnnouncementPrompt() {
        val announcement = foregroundAnnouncementPrompt ?: return
        showForegroundAnnouncementDialog = false
        appForegroundAnnouncementChecker.consumeLatestPrompt(announcement.id)
    }

    fun snoozeForegroundAnnouncementPrompt() {
        val announcement = foregroundAnnouncementPrompt ?: return
        showForegroundAnnouncementDialog = false
        appForegroundAnnouncementChecker.snoozeForToday(announcement.id)
        appUpdateMessage = "该公告已设置今日不提醒，24 小时内不会再次弹出"
    }

    fun openForegroundAnnouncementPrimaryAction(activity: Activity) {
        val action = foregroundAnnouncementPrompt?.primaryAction ?: return
        appUpdateService.openUrl(activity, action.url)
        acknowledgeForegroundAnnouncementPrompt()
    }

    fun openForegroundAnnouncementSecondaryAction(activity: Activity) {
        val action = foregroundAnnouncementPrompt?.secondaryAction ?: return
        appUpdateService.openUrl(activity, action.url)
        acknowledgeForegroundAnnouncementPrompt()
    }

    fun openAnnouncementDetails(announcement: AppAnnouncement) {
        foregroundAnnouncementPrompt = announcement
        showForegroundAnnouncementDialog = true
    }

    fun startInAppUpdateDownload() {
        appUpdateInstaller.startDownload(
            urls = appUpdatePromptDownloadUrls,
            latestVersion = appUpdatePromptLatestVersion,
            missingMessage = "未找到可用安装包，请使用浏览器下载"
        )
    }

    fun openBrowserDownload(activity: Activity) {
        appUpdateInstaller.openBrowserDownload(
            activity = activity,
            downloadUrls = appUpdatePromptDownloadUrls,
            releasePage = appUpdatePromptReleasePage,
            fallbackReleasePage = "https://github.com/lilixu3/danmu-api-android/releases/latest"
        )
    }

    fun installDownloadedAppUpdate(activity: Activity) {
        appUpdateInstaller.installDownloaded(activity)
    }

    fun dismissInstallAppUpdateDialog() {
        appUpdateInstaller.dismissInstallDialog()
    }

    fun openDownloadsApp(activity: Activity) {
        appUpdateInstaller.openDownloadsApp(activity)
    }

    fun resetCoreUpdateCheckDialogState() {
        if (isCheckingCoreUpdate) return
        coreUpdateCheckDialogMessage = null
        coreUpdateCheckDialogIsError = false
    }

    fun dismissAppUpdateMessage() {
        appUpdateMessage = null
    }

    fun postMessage(message: String) {
        appUpdateMessage = message
    }

    fun stopService() = runtimeRepo.stopService()
    fun restartService() = runtimeRepo.restartService()
    fun refreshRuntimeState() = runtimeRepo.refreshRuntimeState()

    fun applyPortQuick(port: Int) {
        val state = runtimeState.value
        if (isSwitchingCore || isInstallingCore || isUpdatingCore ||
            isCheckingCoreUpdate || isRepairingDependencies
        ) {
            postMessage("当前有运行任务，稍后再修改端口")
            return
        }
        if (state.status == ServiceStatus.Starting || state.status == ServiceStatus.Stopping) {
            postMessage("服务切换中，请稍后再修改端口")
            return
        }
        if (state.runMode == RunMode.Normal && port in 1..1023) {
            postMessage("普通模式无法监听 1-1023 端口，请切换 Root 模式或改用 1024+ 端口")
            return
        }
        if (state.port == port) {
            postMessage("端口未变化")
            return
        }

        runtimeRepo.applyServiceConfig(
            port = port,
            token = state.token,
            restartIfRunning = true
        )
        postMessage(
            if (state.status == ServiceStatus.Running) {
                "正在切换到新端口：$port"
            } else {
                "端口已更新为：$port"
            }
        )
    }

    fun applyTokenQuick(token: String) {
        val state = runtimeState.value
        if (isSwitchingCore || isInstallingCore || isUpdatingCore ||
            isCheckingCoreUpdate || isRepairingDependencies
        ) {
            postMessage("当前有运行任务，稍后再修改 Token")
            return
        }
        if (state.status == ServiceStatus.Starting || state.status == ServiceStatus.Stopping) {
            postMessage("服务切换中，请稍后再修改 Token")
            return
        }

        val normalized = RuntimeTokenNormalizer.normalizeInput(token)
        if (state.token == normalized) {
            postMessage("Token 未变化")
            return
        }

        runtimeRepo.applyServiceConfig(
            port = state.port,
            token = normalized,
            restartIfRunning = false
        )
        postMessage(
            if (state.status == ServiceStatus.Running) {
                "Token 已热更新，新请求将按最新 Token 生效"
            } else {
                "Token 已更新"
            }
        )
    }

    fun toggleService() {
        if (isSwitchingCore || isInstallingCore || isUpdatingCore || isRepairingDependencies) return
        when (runtimeState.value.status) {
            ServiceStatus.Running,
            ServiceStatus.Starting -> stopService()
            ServiceStatus.Stopped, ServiceStatus.Error -> tryStartService()
            else -> { }
        }
    }

    fun toggleRunModeQuick() {
        val current = runtimeState.value.runMode
        val target = when (current) {
            RunMode.Normal -> RunMode.Root
            RunMode.Root -> RunMode.Normal
        }
        switchRunModeQuick(target)
    }

    fun switchRunModeQuick(target: RunMode) {
        if (isSwitchingCore || isInstallingCore || isUpdatingCore ||
            isCheckingCoreUpdate || isRepairingDependencies
        ) return
        if (runtimeState.value.runMode == target) return

        viewModelScope.launch {
            if (target.requiresRoot) {
                val check = withContext(Dispatchers.IO) {
                    RootShell.exec("id", timeoutMs = 4000L)
                }
                if (!check.ok) {
                    appUpdateMessage = buildRootSwitchDeniedMessage(check)
                    return@launch
                }
            }

            runtimeRepo.updateRunMode(target)
            val switched = withTimeoutOrNull(12_000L) {
                runtimeState.first { it.runMode == target }
            } != null
            appUpdateMessage = if (switched) {
                "已切换到${target.label}"
            } else {
                "切换${target.label}失败，请检查日志后重试"
            }
        }
    }

    fun switchVariant(variant: ApiVariant) {
        if (isSwitchingCore || isInstallingCore || isUpdatingCore || isRepairingDependencies) return

        val current = runtimeState.value
        if (variant == current.variant) {
            showVariantPicker = false
            refreshUpdatePrompt(coreInfoList.value)
            return
        }

        showVariantPicker = false
        viewModelScope.launch {
            isSwitchingCore = true
            try {
                val wasRunning = current.status == ServiceStatus.Running

                val ready = withContext(Dispatchers.IO) {
                    coreRepo.isCoreReady(variant)
                } && coreInfoList.value.find { it.variant == variant }?.sourceMismatch != true
                if (!ready) {
                    val latestInfo = coreInfoList.value.find { it.variant == variant }
                    if (latestInfo?.sourceMismatch == true) {
                        runtimeRepo.addLog(LogLevel.Warn, "${variantLabel(variant)} 来源与设置不一致，需先重新下载")
                        appUpdateMessage = "${variantLabel(variant)} 当前来源与设置不一致，请先重新下载核心"
                        return@launch
                    }
                    runtimeRepo.addLog(LogLevel.Warn, "${variantLabel(variant)} 未安装，已保留当前核心选择")
                    unavailableVariant = variant
                    return@launch
                }

                runtimeRepo.addLog(LogLevel.Info, "切换核心到 ${variantLabel(variant)}")
                val expectsSameVariantRecovery = coreRepo.candidateState.value?.let { candidate ->
                    candidate.variant == variant && candidate.hasRecoveryPoint
                } == true
                runtimeRepo.updateVariant(variant)

                if (wasRunning) {
                    val restartSnapshot = RuntimeRestartEvidence.snapshot(current)
                    runtimeRepo.addLog(LogLevel.Info, "正在重启服务以应用核心切换...")
                    runtimeRepo.restartService()
                    val restarted = runtimeState.awaitCoreRestart(
                        targetVariant = variant,
                        beforeRestart = restartSnapshot,
                        timeoutMs = 45_000
                    ).status
                    if (restarted != ServiceStatus.Running) {
                        val reason = when (restarted) {
                            ServiceStatus.Error -> "切换后服务启动失败，请查看日志"
                            ServiceStatus.Stopped -> "切换后服务未运行，请重试启动"
                            null -> "切换后服务启动超时，请查看日志"
                            else -> "切换后服务状态异常，请查看日志"
                        }
                        runtimeRepo.addLog(LogLevel.Error, reason)
                        val recoveredSameVariant = if (expectsSameVariantRecovery) {
                            runtimeRepo.addLog(LogLevel.Warn, "正在等待候选核心自动恢复结果")
                            withTimeoutOrNull(40_000L) {
                                runtimeState.first { state ->
                                    state.variant == variant && state.status == ServiceStatus.Running
                                }
                            } != null
                        } else {
                            false
                        }
                        if (recoveredSameVariant) {
                            appUpdateMessage = "新版本启动失败，已恢复 ${variantLabel(variant)} 的上一个可用版本"
                            return@launch
                        }

                        val previousReady = withContext(Dispatchers.IO) {
                            coreRepo.isCoreReady(current.variant)
                        }
                        runtimeRepo.updateVariant(current.variant)
                        if (previousReady) {
                            runtimeRepo.addLog(
                                LogLevel.Warn,
                                "切换失败，已恢复选择 ${variantLabel(current.variant)}，正在重新启动"
                            )
                            runtimeRepo.restartService()
                            val restored = withTimeoutOrNull(45_000L) {
                                runtimeState.first { state ->
                                    state.variant == current.variant && state.status == ServiceStatus.Running
                                }
                            } != null
                            appUpdateMessage = if (restored) {
                                "切换失败，已恢复并启动 ${variantLabel(current.variant)}"
                            } else {
                                "切换失败，已恢复 ${variantLabel(current.variant)} 选择，请检查运行日志"
                            }
                        } else {
                            appUpdateMessage = "切换失败，已恢复原核心选择；原核心当前不可用，请先重新安装"
                        }
                    }
                }
            } catch (t: Throwable) {
                runtimeRepo.addLog(LogLevel.Error, "切换核心失败: ${t.message ?: "未知错误"}")
            } finally {
                isSwitchingCore = false
                refreshUpdatePrompt(coreInfoList.value)
            }
        }
    }

    fun openVariantPicker() { showVariantPicker = true }
    fun dismissVariantPicker() { showVariantPicker = false }

    fun formatUptime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
        else String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }

    fun dismissProxyPickerDialog() {
        proxyPickerController.dismiss()
        pendingProxyAction = null
    }

    fun selectProxy(proxyId: String) {
        proxyPickerController.select(proxyId)
    }

    fun retestProxySpeed() {
        proxyPickerController.retest()
    }

    fun confirmProxySelection() {
        proxyPickerController.confirm {
            pendingProxyAction?.let { action ->
                pendingProxyAction = null
                when (action) {
                    is PendingProxyAction.Install -> doInstallAndStart(action.variant)
                    is PendingProxyAction.Update -> doUpdateCurrentVariant(action.variant)
                    is PendingProxyAction.CheckUpdate -> doQuickCheckCurrentCoreUpdate(action.variant)
                    is PendingProxyAction.LoadUpdateDetails -> openCoreUpdateDetailsNow(action.variant)
                    PendingProxyAction.RepairDependenciesOnline -> {
                        dependencyRepairController.repairOnlineNow()
                    }
                }
            }
        }
    }

    private fun openProxyPickerDialog() {
        proxyPickerController.open()
    }


    private fun loadIgnoredUpdateVersions() {
        ApiVariant.entries.forEach { variant ->
            ignoredUpdateVersionMap[variant] = settingsRepo.getIgnoredUpdateVersion(variant)
        }
    }

    private fun observeForegroundAppUpdate() {
        viewModelScope.launch {
            appForegroundUpdateChecker.latestUpdate.collect { info ->
                if (info == null || !info.hasUpdate) return@collect

                appUpdatePromptCurrentVersion = info.currentVersion
                appUpdatePromptLatestVersion = info.latestVersion
                appUpdatePromptReleaseNotes = info.releaseNotes
                appUpdatePromptReleasePage = info.releasePage
                appUpdatePromptDownloadUrls = info.downloadUrls

                if (!showAppUpdateMethodDialog && !isDownloadingAppUpdate && !showInstallAppUpdateDialog) {
                    showAppUpdatePromptDialog = true
                }
            }
        }
    }

    private fun observeForegroundAnnouncement() {
        viewModelScope.launch {
            appForegroundAnnouncementChecker.latestAnnouncement.collect { announcement ->
                if (announcement == null) {
                    return@collect
                }

                if (
                    !showUpdatePromptDialog &&
                    !showAppUpdatePromptDialog &&
                    !showAppUpdateMethodDialog &&
                    !showInstallAppUpdateDialog
                ) {
                    foregroundAnnouncementPrompt = announcement
                    showForegroundAnnouncementDialog = true
                }
            }
        }
    }

    private fun observeUpdatePrompt() {
        viewModelScope.launch {
            coreInfoList.collect { list ->
                refreshUpdatePrompt(list)
            }
        }
        viewModelScope.launch {
            runtimeState.map { it.variant }.distinctUntilChanged().collect {
                clearCoreAttentionPrompt()
                refreshUpdatePrompt(coreInfoList.value)
            }
        }
    }

    private fun refreshUpdatePrompt(list: List<CoreInfo>) {
        val currentVariant = runtimeState.value.variant
        val info = list.find { it.variant == currentVariant }
        if (info == null || !info.isInstalled || !info.hasVersionUpdate || info.availableVersion.isNullOrBlank()) {
            if (
                updatePromptVariant == currentVariant &&
                updatePromptSourceMismatch.not() &&
                updatePromptSourceUnknownLegacy.not()
            ) {
                clearCoreAttentionPrompt()
            }
            suppressedAutoUpdatePromptVersionMap.remove(currentVariant)
            return
        }

        val latest = info.availableVersion.trim()
        val ignored = ignoredUpdateVersionMap[currentVariant]?.trim().orEmpty()
        if (ignored.isNotBlank() && ignored != latest) {
            settingsRepo.setIgnoredUpdateVersion(currentVariant, null)
            ignoredUpdateVersionMap[currentVariant] = null
        }

        val suppressed = suppressedAutoUpdatePromptVersionMap[currentVariant]?.trim().orEmpty()
        if (suppressed.isNotBlank() && suppressed != latest) {
            suppressedAutoUpdatePromptVersionMap.remove(currentVariant)
        }

        val samePromptShown = showUpdatePromptDialog &&
            updatePromptVariant == currentVariant &&
            updatePromptLatestVersion == latest

        val prompt = resolveAutoCoreUpdatePrompt(
            info = info,
            ignoredVersion = ignoredUpdateVersionMap[currentVariant],
            suppressedVersion = suppressedAutoUpdatePromptVersionMap[currentVariant],
            samePromptShown = samePromptShown
        ) ?: return

        updatePromptVariant = currentVariant
        updatePromptCurrentVersion = prompt.currentVersion
        updatePromptLatestVersion = prompt.latestVersion
        updatePromptSourceMismatch = false
        updatePromptSourceUnknownLegacy = false
        updatePromptDesiredSource = null
        showUpdatePromptDialog = true
    }

    private fun showCoreAttentionPrompt(
        variant: ApiVariant,
        info: CoreInfo
    ) {
        updatePromptVariant = variant
        updatePromptCurrentVersion = info.version
        updatePromptLatestVersion = info.availableVersion
        updatePromptSourceMismatch = info.sourceMismatch
        updatePromptSourceUnknownLegacy = info.sourceStatus == CoreSourceStatus.UnknownLegacy
        updatePromptDesiredSource = info.desiredSource
        showUpdatePromptDialog = true
    }

    private fun clearCoreAttentionPrompt() {
        showUpdatePromptDialog = false
        updatePromptVariant = null
        updatePromptCurrentVersion = null
        updatePromptLatestVersion = null
        updatePromptSourceMismatch = false
        updatePromptSourceUnknownLegacy = false
        updatePromptDesiredSource = null
    }

    private fun variantLabel(variant: ApiVariant): String {
        return coreDisplayNames.value.resolve(variant)
    }

    override fun onCleared() {
        stopCacheFileObserver()
        cacheRefreshJob?.cancel()
        coreUpdateComparisonJob?.cancel()
        proxyPickerController.dismiss()
        super.onCleared()
    }
}
