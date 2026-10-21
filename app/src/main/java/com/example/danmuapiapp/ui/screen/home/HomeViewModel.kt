package com.example.danmuapiapp.ui.screen.home

import com.example.danmuapiapp.ui.common.CoreUpdateChoiceController
import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.data.service.AppForegroundUpdateChecker
import com.example.danmuapiapp.data.service.AppForegroundAnnouncementChecker
import com.example.danmuapiapp.data.service.AppUpdateService
import com.example.danmuapiapp.data.tunnel.TunnelRepository
import com.example.danmuapiapp.data.service.GithubProxyService
import com.example.danmuapiapp.data.service.GithubProxySpeedTester
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
import com.example.danmuapiapp.ui.screen.home.support.formatHomeUptime
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * 首页门面 ViewModel：只做依赖装配、状态转发与跨控制器编排。
 *
 * 具体职责按域拆到同包控制器中：
 * - [HomeCoreInstallController]：核心安装、更新、切换、启动监控与依赖修复续跑
 * - [HomeCoreUpdateCheckController]：手动检查更新与核心变更详情
 * - [HomeCoreUpdatePromptController]：核心更新提示状态机与忽略/延后版本记录
 * - [HomeCacheRefreshController]：运行时状态驱动的缓存/请求记录刷新与缓存目录监听
 * - [HomeCacheClearController]：缓存清理选择、管理员校验与执行
 * - [HomeAppUpdateController]：应用更新提示、下载入口与前台公告
 * - [HomeRuntimeQuickActionsController]：端口/Token 快捷修改、服务启停与运行模式切换
 *
 * 对外 API 与拆分前保持一致，界面调用点零改动。
 */
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
    private val adminSessionRepository: AdminSessionRepository,
    private val tunnelRepository: TunnelRepository
) : ViewModel() {
    val runtimeState = runtimeRepo.runtimeState
    val tunnelState = tunnelRepository.state

    fun refreshTunnel() {
        viewModelScope.launch { tunnelRepository.refresh() }
    }
    val coreInfoList = coreRepo.coreInfoList
    val isCoreInfoLoading = coreRepo.isCoreInfoLoading
    val pendingDependencyRepair = coreRepo.pendingDependencyRepair
    val coreDisplayNames = settingsRepo.coreDisplayNames
    val customRepo = settingsRepo.customRepo
    val customRepoBranch = settingsRepo.customRepoBranch
    val accessEntryDefaultTab = settingsRepo.accessEntryDefaultTab
    val accessEntryLayout = settingsRepo.accessEntryLayout
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

    var appUpdateMessage by mutableStateOf<String?>(null)
        private set

    val coreUpdateChoice = CoreUpdateChoiceController()

    private var pendingProxyAction: HomePendingProxyAction? = null
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
    private val cacheRefreshController = HomeCacheRefreshController(
        appContext = appContext,
        scope = viewModelScope,
        runtimeState = runtimeState,
        cacheRepo = cacheRepo,
        requestRecordRepo = requestRecordRepo
    )
    private val cacheClearController = HomeCacheClearController(
        scope = viewModelScope,
        cacheRepo = cacheRepo,
        adminSessionState = adminSessionState,
        cacheClearCapability = cacheClearCapability,
        postMessage = { appUpdateMessage = it },
        refreshCache = { cacheRefreshController.refreshCache() }
    )
    private val updatePromptController: HomeCoreUpdatePromptController = HomeCoreUpdatePromptController(
        scope = viewModelScope,
        coreInfoList = coreInfoList,
        runtimeState = runtimeState,
        settingsRepo = settingsRepo,
        onUpdateRequested = { variant: ApiVariant -> coreInstallController.updateCurrentVariant(variant) }
    )
    private val appUpdateController = HomeAppUpdateController(
        scope = viewModelScope,
        appUpdateService = appUpdateService,
        githubProxyService = githubProxyService,
        foregroundUpdateChecker = appForegroundUpdateChecker,
        foregroundAnnouncementChecker = appForegroundAnnouncementChecker,
        appUpdateInstaller = appUpdateInstaller,
        postMessage = { appUpdateMessage = it },
        isCoreUpdatePromptVisible = { updatePromptController.showUpdatePromptDialog }
    )
    private val coreInstallController: HomeCoreInstallController = HomeCoreInstallController(
        scope = viewModelScope,
        runtimeRepo = runtimeRepo,
        coreRepo = coreRepo,
        githubProxyService = githubProxyService,
        runtimeState = runtimeState,
        coreInfoList = coreInfoList,
        coreUpdateChoice = coreUpdateChoice,
        updatePromptController = updatePromptController,
        variantLabel = ::variantLabel,
        postMessage = { appUpdateMessage = it },
        setPendingProxyAction = { pendingProxyAction = it },
        openProxyPickerDialog = ::openProxyPickerDialog
    )
    private val coreUpdateCheckController: HomeCoreUpdateCheckController = HomeCoreUpdateCheckController(
        scope = viewModelScope,
        coreRepo = coreRepo,
        githubProxyService = githubProxyService,
        runtimeState = runtimeState,
        coreInfoList = coreInfoList,
        updatePromptController = updatePromptController,
        variantLabel = ::variantLabel,
        isCoreBusy = { coreInstallController.isCoreBusy },
        setPendingProxyAction = { pendingProxyAction = it },
        openProxyPickerDialog = ::openProxyPickerDialog,
        onUpdateRequested = { variant: ApiVariant -> coreInstallController.updateCurrentVariant(variant) }
    )
    private val dependencyRepairController = CoreDependencyRepairController(
        scope = viewModelScope,
        repository = coreRepo,
        setOperating = coreInstallController::updateRepairingDependencies,
        postMessage = { appUpdateMessage = it },
        onApplied = coreInstallController::onDependenciesApplied,
        onDiscarded = coreInstallController::onDependenciesDiscarded
    )
    private val quickActionsController = HomeRuntimeQuickActionsController(
        scope = viewModelScope,
        runtimeRepo = runtimeRepo,
        runtimeState = runtimeState,
        postMessage = { appUpdateMessage = it },
        isCoreBusy = { coreInstallController.isCoreBusy },
        isCheckingCoreUpdate = { coreUpdateCheckController.isCheckingCoreUpdate },
        onStartRequested = { coreInstallController.tryStartService() },
        onStopRequested = { runtimeRepo.stopService() }
    )

    val showNoCoreDialog: Boolean
        get() = coreInstallController.showNoCoreDialog
    val unavailableVariant: ApiVariant?
        get() = coreInstallController.unavailableVariant
    val showVariantPicker: Boolean
        get() = coreInstallController.showVariantPicker
    val isInstallingCore: Boolean
        get() = coreInstallController.isInstallingCore
    val isSwitchingCore: Boolean
        get() = coreInstallController.isSwitchingCore
    val isUpdatingCore: Boolean
        get() = coreInstallController.isUpdatingCore
    val isRepairingDependencies: Boolean
        get() = coreInstallController.isRepairingDependencies
    val isCheckingCoreUpdate: Boolean
        get() = coreUpdateCheckController.isCheckingCoreUpdate
    val showUpdatePromptDialog: Boolean
        get() = updatePromptController.showUpdatePromptDialog
    val updatePromptVariant: ApiVariant?
        get() = updatePromptController.updatePromptVariant
    val updatePromptCurrentVersion: String?
        get() = updatePromptController.updatePromptCurrentVersion
    val updatePromptLatestVersion: String?
        get() = updatePromptController.updatePromptLatestVersion
    val updatePromptSourceMismatch: Boolean
        get() = updatePromptController.updatePromptSourceMismatch
    val updatePromptSourceUnknownLegacy: Boolean
        get() = updatePromptController.updatePromptSourceUnknownLegacy
    val updatePromptDesiredSource: String?
        get() = updatePromptController.updatePromptDesiredSource
    val showCoreUpdateDetails: Boolean
        get() = coreUpdateCheckController.showCoreUpdateDetails
    val coreUpdateDetailsVariant: ApiVariant?
        get() = coreUpdateCheckController.coreUpdateDetailsVariant
    val coreUpdateComparison: CoreUpdateComparison?
        get() = coreUpdateCheckController.coreUpdateComparison
    val isLoadingCoreUpdateComparison: Boolean
        get() = coreUpdateCheckController.isLoadingCoreUpdateComparison
    val coreUpdateComparisonError: String?
        get() = coreUpdateCheckController.coreUpdateComparisonError
    val coreUpdateCheckDialogMessage: String?
        get() = coreUpdateCheckController.coreUpdateCheckDialogMessage
    val coreUpdateCheckDialogIsError: Boolean
        get() = coreUpdateCheckController.coreUpdateCheckDialogIsError
    val showAppUpdatePromptDialog: Boolean
        get() = appUpdateController.showAppUpdatePromptDialog
    val showForegroundAnnouncementDialog: Boolean
        get() = appUpdateController.showForegroundAnnouncementDialog
    val appUpdatePromptCurrentVersion: String?
        get() = appUpdateController.appUpdatePromptCurrentVersion
    val appUpdatePromptLatestVersion: String?
        get() = appUpdateController.appUpdatePromptLatestVersion
    val appUpdatePromptReleaseNotes: String
        get() = appUpdateController.appUpdatePromptReleaseNotes
    val appUpdatePromptReleasePage: String
        get() = appUpdateController.appUpdatePromptReleasePage
    val appUpdatePromptDownloadUrls: List<String>
        get() = appUpdateController.appUpdatePromptDownloadUrls
    val isRefreshingAppUpdatePrompt: Boolean
        get() = appUpdateController.isRefreshingAppUpdatePrompt
    val foregroundAnnouncementPrompt: AppAnnouncement?
        get() = appUpdateController.foregroundAnnouncementPrompt
    val showCacheAdminRequiredDialog: Boolean
        get() = cacheClearController.showCacheAdminRequiredDialog
    val cacheAdminRequiredMessage: String
        get() = cacheClearController.cacheAdminRequiredMessage
    val isClearingCache: Boolean
        get() = cacheClearController.isClearingCache
    val selectedCacheClearItems: Set<CacheClearItem>
        get() = cacheClearController.selectedCacheClearItems

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

    init {
        updatePromptController.loadIgnoredUpdateVersions()
        updatePromptController.observeUpdatePrompt()
        appUpdateController.observeForegroundAppUpdate()
        appUpdateController.observeForegroundAnnouncement()
        observeRuntimeDrivenCoreRefresh()
        cacheRefreshController.observeRuntimeDrivenRefreshes()
        cacheClearController.observeClearCapability()
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

    fun toggleTokenVisible() {
        settingsRepo.setTokenVisible(!tokenVisible.value)
    }

    fun refreshCache() = cacheRefreshController.refreshCache()

    fun prepareCacheClearSelection() = cacheClearController.prepareCacheClearSelection()

    fun toggleCacheClearItem(item: CacheClearItem) = cacheClearController.toggleCacheClearItem(item)

    fun selectAllCacheClearItems() = cacheClearController.selectAllCacheClearItems()

    fun clearCacheClearSelection() = cacheClearController.clearCacheClearSelection()

    fun clearSelectedCache() = cacheClearController.clearSelectedCache()

    fun dismissCacheAdminRequiredDialog() = cacheClearController.dismissCacheAdminRequiredDialog()

    fun tryStartService() = coreInstallController.tryStartService()

    fun dismissNoCoreDialog() = coreInstallController.dismissNoCoreDialog()

    fun dismissUnavailableVariantDialog() = coreInstallController.dismissUnavailableVariantDialog()

    fun installUnavailableVariant() = coreInstallController.installUnavailableVariant()

    fun consumeUnavailableVariantForSettings() = coreInstallController.consumeUnavailableVariantForSettings()

    fun openCoreDownloadDialog() = coreInstallController.openCoreDownloadDialog()

    fun installAndStart(variant: ApiVariant) = coreInstallController.installAndStart(variant)

    fun switchVariant(variant: ApiVariant) = coreInstallController.switchVariant(variant)

    fun openVariantPicker() = coreInstallController.openVariantPicker()

    fun dismissVariantPicker() = coreInstallController.dismissVariantPicker()

    fun openUpdatePromptFromCard() = updatePromptController.openUpdatePromptFromCard()

    fun quickCheckCurrentCoreUpdate() = coreUpdateCheckController.quickCheckCurrentCoreUpdate()

    fun dismissCurrentUpdatePrompt() = updatePromptController.dismissCurrentUpdatePrompt()

    fun openUpdateDetailsFromPrompt() = coreUpdateCheckController.openUpdateDetailsFromPrompt()

    fun retryCoreUpdateComparison() = coreUpdateCheckController.retryCoreUpdateComparison()

    fun dismissCoreUpdateDetails() = coreUpdateCheckController.dismissCoreUpdateDetails()

    fun updateFromCoreUpdateDetails() = coreUpdateCheckController.updateFromCoreUpdateDetails()

    fun ignoreCurrentUpdatePrompt() = updatePromptController.ignoreCurrentUpdatePrompt()

    fun updateFromPrompt() = updatePromptController.updateFromPrompt()

    fun dismissDependencyRequiredPrompt() = dependencyRepairController.dismissRequiredPrompt()

    fun openDependencyRepairDialog() = dependencyRepairController.openRepairDialog()

    fun dismissDependencyRepairDialog() = dependencyRepairController.dismissRepairDialog()

    fun repairPendingDependenciesOnline() {
        if (githubProxyService.hasUserSelectedProxy()) {
            dependencyRepairController.repairOnlineNow()
        } else {
            pendingProxyAction = HomePendingProxyAction.RepairDependenciesOnline
            openProxyPickerDialog()
        }
    }

    fun repairPendingDependenciesFromArchive(archiveUri: String) {
        dependencyRepairController.repairFromArchive(archiveUri)
    }

    fun discardPendingCoreMutation() = dependencyRepairController.discardPendingMutation()

    fun openForegroundAppUpdateMethodDialog() = appUpdateController.openForegroundAppUpdateMethodDialog()

    fun refreshForegroundAppUpdatePrompt() = appUpdateController.refreshForegroundAppUpdatePrompt()

    fun dismissForegroundAppUpdatePrompt() = appUpdateController.dismissForegroundAppUpdatePrompt()

    fun dismissForegroundAppUpdateMethodDialog() = appUpdateController.dismissForegroundAppUpdateMethodDialog()

    fun acknowledgeForegroundAnnouncementPrompt() = appUpdateController.acknowledgeForegroundAnnouncementPrompt()

    fun acknowledgeAllUnreadAnnouncements() = appUpdateController.acknowledgeAllUnreadAnnouncements()

    fun closeForegroundAnnouncementPrompt() = appUpdateController.closeForegroundAnnouncementPrompt()

    fun snoozeForegroundAnnouncementPrompt() = appUpdateController.snoozeForegroundAnnouncementPrompt()

    fun openForegroundAnnouncementPrimaryAction(activity: Activity) =
        appUpdateController.openForegroundAnnouncementPrimaryAction(activity)

    fun openForegroundAnnouncementSecondaryAction(activity: Activity) =
        appUpdateController.openForegroundAnnouncementSecondaryAction(activity)

    fun openAnnouncementDetails(announcement: AppAnnouncement) =
        appUpdateController.openAnnouncementDetails(announcement)

    fun startInAppUpdateDownload() = appUpdateController.startInAppUpdateDownload()

    fun openBrowserDownload(activity: Activity) = appUpdateController.openBrowserDownload(activity)

    fun installDownloadedAppUpdate(activity: Activity) = appUpdateController.installDownloadedAppUpdate(activity)

    fun dismissInstallAppUpdateDialog() = appUpdateController.dismissInstallAppUpdateDialog()

    fun openDownloadsApp(activity: Activity) = appUpdateController.openDownloadsApp(activity)

    fun resetCoreUpdateCheckDialogState() = coreUpdateCheckController.resetCoreUpdateCheckDialogState()

    fun dismissAppUpdateMessage() {
        appUpdateMessage = null
    }

    fun postMessage(message: String) {
        appUpdateMessage = message
    }

    fun stopService() = runtimeRepo.stopService()
    fun restartService() = runtimeRepo.restartService()
    fun refreshRuntimeState() = runtimeRepo.refreshRuntimeState()

    fun applyPortQuick(port: Int) = quickActionsController.applyPortQuick(port)

    fun applyTokenQuick(token: String) = quickActionsController.applyTokenQuick(token)

    fun toggleService() = quickActionsController.toggleService()

    fun toggleRunModeQuick() = quickActionsController.toggleRunModeQuick()

    fun switchRunModeQuick(target: RunMode) = quickActionsController.switchRunModeQuick(target)

    fun formatUptime(seconds: Long): String = formatHomeUptime(seconds)

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
                    is HomePendingProxyAction.Install -> coreInstallController.doInstallAndStart(action.variant)
                    is HomePendingProxyAction.Update -> coreInstallController.doUpdateCurrentVariant(action.variant)
                    is HomePendingProxyAction.CheckUpdate ->
                        coreUpdateCheckController.doQuickCheckCurrentCoreUpdate(action.variant)
                    is HomePendingProxyAction.LoadUpdateDetails ->
                        coreUpdateCheckController.openCoreUpdateDetailsNow(action.variant)
                    HomePendingProxyAction.RepairDependenciesOnline -> {
                        dependencyRepairController.repairOnlineNow()
                    }
                }
            }
        }
    }

    private fun openProxyPickerDialog() {
        proxyPickerController.open()
    }

    private fun variantLabel(variant: ApiVariant): String {
        return coreDisplayNames.value.resolve(variant)
    }

    override fun onCleared() {
        cacheRefreshController.stop()
        coreUpdateCheckController.cancelComparison()
        proxyPickerController.dismiss()
        super.onCleared()
    }
}
