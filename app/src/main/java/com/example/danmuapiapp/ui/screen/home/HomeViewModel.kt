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
import com.example.danmuapiapp.domain.model.*
import com.example.danmuapiapp.domain.repository.AdminSessionRepository
import com.example.danmuapiapp.domain.repository.CacheRepository
import com.example.danmuapiapp.domain.repository.CoreRepository
import com.example.danmuapiapp.domain.repository.RequestRecordRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.domain.repository.SettingsRepository
import com.example.danmuapiapp.ui.common.AppUpdateInstallerController
import com.example.danmuapiapp.ui.common.ProxyPickerController
import com.example.danmuapiapp.ui.common.buildRootSwitchDeniedMessage
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
    private data class RestartSnapshot(
        val runMode: RunMode,
        val pid: Int?,
        val uptimeSeconds: Long
    )

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
    val tokenVisible = settingsRepo.tokenVisible
    val proxyOptions = githubProxyService.proxyOptions()
    val cacheStats = cacheRepo.cacheStats
    val cacheEntries = cacheRepo.cacheEntries
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
    var showVariantPicker by mutableStateOf(false)
        private set
    var isInstallingCore by mutableStateOf(false)
        private set
    var isSwitchingCore by mutableStateOf(false)
        private set
    var isUpdatingCore by mutableStateOf(false)
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
    private var pendingProxyAction: PendingProxyAction? = null
    private var cacheRefreshJob: Job? = null
    private var requestRecordRefreshJob: Job? = null
    private var runtimeCacheRefreshJob: Job? = null
    private var cacheFileRefreshDebounceJob: Job? = null
    private var cacheFileObserver: FileObserver? = null
    private var cacheObserverRootPath: String? = null

    private sealed interface PendingProxyAction {
        data class Install(val variant: ApiVariant) : PendingProxyAction
        data class Update(val variant: ApiVariant) : PendingProxyAction
        data class CheckUpdate(val variant: ApiVariant) : PendingProxyAction
    }

    var isClearingCache by mutableStateOf(false)
        private set

    init {
        loadIgnoredUpdateVersions()
        observeUpdatePrompt()
        observeForegroundAppUpdate()
        observeForegroundAnnouncement()
        observeRuntimeDrivenCoreRefresh()
        observeRuntimeDrivenRequestRecordRefresh()
        observeRuntimeDrivenCacheRefresh()
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

    fun quickClearCache() {
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
            cacheRepo.clearAll().fold(
                onSuccess = { appUpdateMessage = "缓存已清理" },
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
        if (isSwitchingCore || isInstallingCore || isUpdatingCore) return
        val variant = runtimeState.value.variant
        viewModelScope.launch {
            val installed = withContext(Dispatchers.IO) {
                coreRepo.isCoreInstalled(variant)
            }
            if (!installed) {
                showNoCoreDialog = true
                return@launch
            }
            runtimeRepo.startService()
        }
    }

    fun dismissNoCoreDialog() {
        showNoCoreDialog = false
    }

    fun openCoreDownloadDialog() {
        if (isSwitchingCore || isInstallingCore || isUpdatingCore) return
        showNoCoreDialog = true
    }

    fun installAndStart(variant: ApiVariant) {
        if (isSwitchingCore || isUpdatingCore) return
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
            runtimeRepo.addLog(LogLevel.Info, "正在下载 ${variant.label}...")
            coreRepo.installCore(variant).fold(
                onSuccess = {
                    isInstallingCore = false
                    runtimeRepo.updateVariant(variant)
                    runtimeRepo.addLog(LogLevel.Info, "${variant.label} 安装成功，已切换为当前核心")
                    val status = runtimeState.value.status
                    if (status == ServiceStatus.Running) {
                        runtimeRepo.addLog(LogLevel.Info, "正在重启服务以应用新核心...")
                        runtimeRepo.restartService()
                    } else {
                        runtimeRepo.startService()
                    }
                },
                onFailure = {
                    runtimeRepo.addLog(LogLevel.Error, "安装失败: ${it.message}")
                    isInstallingCore = false
                    if (githubProxyService.isUsingProxy()) {
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
        if (!info.hasUpdate || info.latestVersion.isNullOrBlank()) return
        updatePromptVariant = variant
        updatePromptCurrentVersion = info.version
        updatePromptLatestVersion = info.latestVersion
        showUpdatePromptDialog = true
    }

    fun quickCheckCurrentCoreUpdate() {
        if (isSwitchingCore || isInstallingCore || isUpdatingCore || isCheckingCoreUpdate) return

        resetCoreUpdateCheckDialogState()
        val variant = runtimeState.value.variant
        val info = coreInfoList.value.find { it.variant == variant }
        if (info?.isInstalled != true) {
            coreUpdateCheckDialogMessage = "${variant.label} 未安装，无法检查更新"
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
            if (latestInfo?.hasUpdate == true && !latestInfo.latestVersion.isNullOrBlank()) {
                updatePromptVariant = variant
                updatePromptCurrentVersion = latestInfo.version
                updatePromptLatestVersion = latestInfo.latestVersion
                showUpdatePromptDialog = true
            } else if (latestInfo?.isInstalled == true) {
                coreUpdateCheckDialogMessage = "已确认 ${variant.label} 当前是最新版本"
                coreUpdateCheckDialogIsError = false
            }
            isCheckingCoreUpdate = false
        }
    }

    fun ignoreCurrentUpdatePrompt() {
        val variant = updatePromptVariant
        val latest = updatePromptLatestVersion?.trim().orEmpty()
        if (variant != null && latest.isNotBlank()) {
            settingsRepo.setIgnoredUpdateVersion(variant, latest)
            ignoredUpdateVersionMap[variant] = latest
        }
        showUpdatePromptDialog = false
    }

    fun updateFromPrompt() {
        val variant = updatePromptVariant ?: runtimeState.value.variant
        val latest = updatePromptLatestVersion
        if (!latest.isNullOrBlank()) {
            suppressedAutoUpdatePromptVersionMap[variant] = latest.trim()
        }
        showUpdatePromptDialog = false
        updateCurrentVariant(variant)
    }

    private fun updateCurrentVariant(variant: ApiVariant) {
        if (isSwitchingCore || isInstallingCore || isUpdatingCore) return
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
            runtimeRepo.addLog(LogLevel.Info, "正在更新 ${variant.label}...")
            coreRepo.updateCore(variant).fold(
                onSuccess = {
                    runtimeRepo.addLog(LogLevel.Info, "${variant.label} 更新成功")
                    maybeRestartAfterCoreUpdate(variant)
                    settingsRepo.setIgnoredUpdateVersion(variant, null)
                    ignoredUpdateVersionMap[variant] = null
                    isUpdatingCore = false
                },
                onFailure = {
                    runtimeRepo.addLog(LogLevel.Error, "更新失败: ${it.message}")
                    isUpdatingCore = false
                    if (githubProxyService.isUsingProxy()) {
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
        if (isSwitchingCore || isInstallingCore || isUpdatingCore || isCheckingCoreUpdate) {
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
        if (isSwitchingCore || isInstallingCore || isUpdatingCore || isCheckingCoreUpdate) {
            postMessage("当前有运行任务，稍后再修改 Token")
            return
        }
        if (state.status == ServiceStatus.Starting || state.status == ServiceStatus.Stopping) {
            postMessage("服务切换中，请稍后再修改 Token")
            return
        }

        val normalized = token.trim()
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
        if (isSwitchingCore || isInstallingCore || isUpdatingCore) return
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
        if (isSwitchingCore || isInstallingCore || isUpdatingCore || isCheckingCoreUpdate) return
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
        if (isSwitchingCore || isInstallingCore || isUpdatingCore) return

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

                val installed = withContext(Dispatchers.IO) {
                    coreRepo.isCoreInstalled(variant)
                }
                if (!installed) {
                    runtimeRepo.updateVariant(variant)
                    runtimeRepo.addLog(LogLevel.Warn, "${variant.label} 未安装，已切换选择，下载后可直接使用")
                    if (current.status == ServiceStatus.Running) {
                        runtimeRepo.addLog(LogLevel.Info, "服务仍在运行当前核心，下载后会自动重启应用新核心")
                    }
                    appUpdateMessage = "${variant.label} 尚未安装，请先下载核心"
                    showNoCoreDialog = true
                    return@launch
                }

                runtimeRepo.addLog(LogLevel.Info, "切换核心到 ${variant.label}")
                runtimeRepo.updateVariant(variant)

                if (wasRunning) {
                    val restartSnapshot = current.toRestartSnapshot()
                    runtimeRepo.addLog(LogLevel.Info, "正在重启服务以应用核心切换...")
                    runtimeRepo.restartService()
                    val restarted = waitForRestartAfterCoreSwitch(
                        beforeRestart = restartSnapshot,
                        timeoutMs = 45_000
                    )
                    if (restarted != ServiceStatus.Running) {
                        val reason = when (restarted) {
                            ServiceStatus.Error -> "切换后服务启动失败，请查看日志"
                            ServiceStatus.Stopped -> "切换后服务未运行，请重试启动"
                            null -> "切换后服务启动超时，请查看日志"
                            else -> "切换后服务状态异常，请查看日志"
                        }
                        runtimeRepo.addLog(LogLevel.Error, reason)
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

    private suspend fun waitForRestartAfterCoreSwitch(
        beforeRestart: RestartSnapshot,
        timeoutMs: Long
    ): ServiceStatus? {
        var sawRestartProgress = false
        val result = withTimeoutOrNull(timeoutMs) {
            runtimeState.first { state ->
                when (state.status) {
                    ServiceStatus.Starting,
                    ServiceStatus.Stopping,
                    ServiceStatus.Stopped -> {
                        sawRestartProgress = true
                        false
                    }

                    ServiceStatus.Error -> true

                    ServiceStatus.Running -> {
                        sawRestartProgress || state.isRunningAfterRestart(beforeRestart)
                    }
                }
            }.status
        }
        return result ?: runtimeState.value.status.takeIf {
            it == ServiceStatus.Stopped || it == ServiceStatus.Error
        }
    }

    private fun RuntimeState.toRestartSnapshot(): RestartSnapshot {
        return RestartSnapshot(
            runMode = runMode,
            pid = pid,
            uptimeSeconds = uptimeSeconds
        )
    }

    private fun RuntimeState.isRunningAfterRestart(snapshot: RestartSnapshot): Boolean {
        if (status != ServiceStatus.Running) return false
        return when (runMode) {
            RunMode.Root -> {
                val pidChanged = pid != null && snapshot.pid != null && pid != snapshot.pid
                val uptimeReset = uptimeSeconds < snapshot.uptimeSeconds
                pidChanged || uptimeReset
            }

            RunMode.Normal -> uptimeSeconds < snapshot.uptimeSeconds
        }
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
                showUpdatePromptDialog = false
                refreshUpdatePrompt(coreInfoList.value)
            }
        }
    }

    private fun refreshUpdatePrompt(list: List<CoreInfo>) {
        val currentVariant = runtimeState.value.variant
        val info = list.find { it.variant == currentVariant }
        if (info == null || !info.isInstalled || !info.hasUpdate || info.latestVersion.isNullOrBlank()) {
            if (updatePromptVariant == currentVariant) {
                showUpdatePromptDialog = false
                updatePromptVariant = null
                updatePromptCurrentVersion = null
                updatePromptLatestVersion = null
            }
            suppressedAutoUpdatePromptVersionMap.remove(currentVariant)
            return
        }

        val latest = info.latestVersion.trim()
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
        showUpdatePromptDialog = true
    }


    override fun onCleared() {
        stopCacheFileObserver()
        cacheRefreshJob?.cancel()
        proxyPickerController.dismiss()
        super.onCleared()
    }
}
