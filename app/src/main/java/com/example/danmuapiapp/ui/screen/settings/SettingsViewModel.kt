package com.example.danmuapiapp.ui.screen.settings

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.data.service.AppBackupPreview
import com.example.danmuapiapp.data.service.AppBackupSection
import com.example.danmuapiapp.data.service.AppBackupService
import com.example.danmuapiapp.data.service.AppUpdateService
import com.example.danmuapiapp.data.service.FavoriteCacheStore
import com.example.danmuapiapp.data.service.GithubAccountService
import com.example.danmuapiapp.data.service.GithubProxyService
import com.example.danmuapiapp.data.service.GithubProxySpeedTester
import com.example.danmuapiapp.data.service.RuntimePaths
import com.example.danmuapiapp.data.service.TvConfigSyncClient
import com.example.danmuapiapp.data.service.WebDavService
import com.example.danmuapiapp.data.tunnel.TunnelRepository
import com.example.danmuapiapp.domain.model.AccessEntryLayout
import com.example.danmuapiapp.domain.model.AccessEntryTab
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.AppBackgroundMode
import com.example.danmuapiapp.domain.model.AppBackgroundRefreshPolicy
import com.example.danmuapiapp.domain.model.CoreDependencyRepairOrigin
import com.example.danmuapiapp.domain.model.CoreDependencyRepairRequest
import com.example.danmuapiapp.domain.model.GlassMaterialPreference
import com.example.danmuapiapp.domain.model.GlassTuningPreference
import com.example.danmuapiapp.domain.model.GlassTuningPreset
import com.example.danmuapiapp.domain.model.KeepAliveHeartbeatMode
import com.example.danmuapiapp.domain.model.LogLevel
import com.example.danmuapiapp.domain.model.NightModePreference
import com.example.danmuapiapp.domain.model.NormalModeStabilityMode
import com.example.danmuapiapp.domain.model.NormalNotificationBehavior
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.RuntimeListenMode
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.repository.AdminSessionRepository
import com.example.danmuapiapp.domain.repository.CoreRepository
import com.example.danmuapiapp.domain.repository.EnvConfigRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.domain.repository.SettingsRepository
import com.example.danmuapiapp.ui.common.AppUpdateInstallerController
import com.example.danmuapiapp.ui.common.CoreDependencyRepairController
import com.example.danmuapiapp.ui.common.ProxyPickerController
import com.example.danmuapiapp.ui.common.continueAfterDependencyRepair
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import com.example.danmuapiapp.data.service.TvConfigSyncCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import com.example.danmuapiapp.R
import com.example.danmuapiapp.data.service.DanmuQuickSettingsTileService
import kotlinx.coroutines.Job
import com.example.danmuapiapp.data.service.RootAutoStartModule
import com.example.danmuapiapp.data.service.RootAutoStartPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 设置页门面 ViewModel：只做依赖装配、状态转发与跨控制器编排。
 *
 * 具体职责按域拆到同包控制器中：
 * - [SettingsServiceControlController]：服务监听、运行模式、保活心跳、通知行为
 * - [SettingsRootAutoStartController]：Root 开机自启模块
 * - [SettingsQuickSettingsTileController]：控制中心快捷按钮
 * - [SettingsAppearanceController]：主题、玻璃、背景、DPI 等外观偏好
 * - [SettingsWorkDirController]：工作目录切换与核心变体同步
 * - [SettingsBackupController]：.env / 收藏 / 完整备份与 WebDAV
 * - [SettingsTvSyncController]：扫码同步配置到电视端
 * - [SettingsGithubAccountController]：GitHub Token 与额度刷新
 * - [SettingsAppUpdateController]：应用更新检查、下载与安装
 *
 * 对外 API 与拆分前保持一致，界面调用点零改动。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtimeRepo: RuntimeRepository,
    private val coreRepo: CoreRepository,
    private val settingsRepo: SettingsRepository,
    private val adminSessionRepository: AdminSessionRepository,
    private val envConfigRepoLazy: Lazy<EnvConfigRepository>,
    private val githubProxyService: GithubProxyService,
    private val githubProxySpeedTester: GithubProxySpeedTester,
    private val githubAccountService: GithubAccountService,
    private val webDavService: WebDavService,
    private val appBackupService: AppBackupService,
    private val appUpdateService: AppUpdateService,
    private val tvConfigSyncClient: TvConfigSyncClient,
    private val tunnelRepository: TunnelRepository
) : ViewModel() {

    private val envConfigRepo: EnvConfigRepository
        get() = envConfigRepoLazy.get()

    val runtimeState = runtimeRepo.runtimeState
    val aboutCoreInfo = coreRepo.coreInfoList
    val aboutCoreDisplayNames = settingsRepo.coreDisplayNames

    fun refreshAboutInfo() {
        coreRepo.refreshCoreInfo()
        refreshWorkDirInfo()
    }
    val pendingDependencyRepair = coreRepo.pendingDependencyRepair
    val githubProxy = settingsRepo.githubProxy
    val githubAccountStatus = githubAccountService.status
    val customRepo = settingsRepo.customRepo
    val tokenVisible = settingsRepo.tokenVisible
    val keepAlive = settingsRepo.keepAlive
    val keepAliveHeartbeatEnabled = settingsRepo.keepAliveHeartbeatEnabled
    val keepAliveHeartbeatMode = settingsRepo.keepAliveHeartbeatMode
    val keepAliveHeartbeatIntervalMinutes = settingsRepo.keepAliveHeartbeatIntervalMinutes
    val coreUpdateCheckIntervalMinutes = settingsRepo.coreUpdateCheckIntervalMinutes
    val normalModeStabilityMode = settingsRepo.normalModeStabilityMode
    val normalNotificationBehavior = settingsRepo.normalNotificationBehavior
    val serviceNotificationInfoEnabled = settingsRepo.serviceNotificationInfoEnabled
    val rootNotificationEnabled = settingsRepo.rootNotificationEnabled
    val accessEntryDefaultTab = settingsRepo.accessEntryDefaultTab
    val accessEntryLayout = settingsRepo.accessEntryLayout
    val nightMode = settingsRepo.nightMode
    val glassMaterial = settingsRepo.glassMaterial
    val glassBottomBar = settingsRepo.glassBottomBar
    val glassTuning = settingsRepo.glassTuning
    val appBackground = settingsRepo.appBackground
    val appDpiOverride = settingsRepo.appDpiOverride
    val hideFromRecents = settingsRepo.hideFromRecents
    val fileLogEnabled = settingsRepo.fileLogEnabled
    val adminSessionState = adminSessionRepository.sessionState
    val proxyOptions = githubProxyService.proxyOptions()
    val selectedGithubProxyId = githubProxyService.selectedProxyId

    var operationMessage by mutableStateOf<String?>(null)
        private set

    private val proxyPickerController = ProxyPickerController(
        githubProxyService = githubProxyService,
        githubProxySpeedTester = githubProxySpeedTester,
        scope = viewModelScope,
        proxyOptionsProvider = { proxyOptions }
    )
    private val appUpdateInstaller = AppUpdateInstallerController(
        scope = viewModelScope,
        appUpdateService = appUpdateService,
        postMessage = { operationMessage = it }
    )
    private var pendingOnlineDependencyRepair = false
    var isRepairingDependencies by mutableStateOf(false)
        private set
    private val dependencyRepairController = CoreDependencyRepairController(
        scope = viewModelScope,
        repository = coreRepo,
        shouldHandle = { it.origin == CoreDependencyRepairOrigin.WorkDirectory },
        setOperating = { isRepairingDependencies = it },
        postMessage = { operationMessage = it },
        onApplied = ::onWorkDirDependenciesApplied,
        onDiscarded = { "已取消依赖修复，当前工作目录保持不变" }
    )

    private val appearanceController = SettingsAppearanceController(
        context = context,
        settingsRepo = settingsRepo,
        postMessage = { operationMessage = it }
    )
    private val quickSettingsTileController = SettingsQuickSettingsTileController(
        postMessage = { operationMessage = it }
    )
    private val backupController = SettingsBackupController(
        context = context,
        scope = viewModelScope,
        envConfigRepo = envConfigRepo,
        appBackupService = appBackupService,
        webDavService = webDavService,
        settingsRepo = settingsRepo,
        coreRepo = coreRepo,
        runtimeRepo = runtimeRepo,
        postMessage = { operationMessage = it }
    )
    private val tvSyncController = SettingsTvSyncController(
        scope = viewModelScope,
        tvConfigSyncClient = tvConfigSyncClient,
        runtimeRepo = runtimeRepo,
        postMessage = { operationMessage = it }
    )
    private val githubAccountController = SettingsGithubAccountController(
        scope = viewModelScope,
        settingsRepo = settingsRepo,
        githubAccountService = githubAccountService,
        postMessage = { operationMessage = it }
    )
    private val rootAutoStartController = SettingsRootAutoStartController(
        context = context,
        scope = viewModelScope,
        runtimeRepo = runtimeRepo,
        postMessage = { operationMessage = it }
    )
    private val workDirController = SettingsWorkDirController(
        context = context,
        scope = viewModelScope,
        coreRepo = coreRepo,
        runtimeRepo = runtimeRepo,
        envConfigRepo = envConfigRepo,
        postMessage = { operationMessage = it }
    )
    private val serviceControlController = SettingsServiceControlController(
        context = context,
        scope = viewModelScope,
        runtimeRepo = runtimeRepo,
        coreRepo = coreRepo,
        settingsRepo = settingsRepo,
        tunnelRepository = tunnelRepository,
        refreshRuntimeRelatedStates = { refreshRuntimeRelatedStates() },
        refreshWorkDirInfo = { refreshWorkDirInfo() },
        postMessage = { operationMessage = it }
    )
    private val appUpdateController = SettingsAppUpdateController(
        scope = viewModelScope,
        appUpdateService = appUpdateService,
        githubProxyService = githubProxyService,
        appUpdateInstaller = appUpdateInstaller,
        postMessage = { operationMessage = it }
    )

    // ---- 服务与运行模式 ----

    val normalBootAutoStartEnabled: Boolean
        get() = serviceControlController.normalBootAutoStartEnabled
    val isRunModeSwitching: Boolean
        get() = serviceControlController.isRunModeSwitching
    val a11yEnabled: Boolean
        get() = serviceControlController.a11yEnabled

    fun saveServiceConfig(
        port: Int,
        token: String,
        listenMode: RuntimeListenMode
    ) = serviceControlController.saveServiceConfig(port, token, listenMode)

    fun restartService() = serviceControlController.restartService()

    fun updateVariant(variant: ApiVariant) = serviceControlController.updateVariant(variant)

    fun updateRunMode(mode: RunMode) = serviceControlController.updateRunMode(mode)

    fun setAutoStart(enabled: Boolean) = serviceControlController.setAutoStart(enabled)

    fun setNormalBootAutoStart(enabled: Boolean) =
        serviceControlController.setNormalBootAutoStart(enabled)

    fun setKeepAliveEnabled(enabled: Boolean) = serviceControlController.setKeepAliveEnabled(enabled)

    fun prepareAccessibilitySetup() = serviceControlController.prepareAccessibilitySetup()

    fun setKeepAliveHeartbeatEnabled(enabled: Boolean) =
        serviceControlController.setKeepAliveHeartbeatEnabled(enabled)

    fun setKeepAliveHeartbeatMode(mode: KeepAliveHeartbeatMode) =
        serviceControlController.setKeepAliveHeartbeatMode(mode)

    fun setKeepAliveHeartbeatIntervalMinutes(minutes: Int) =
        serviceControlController.setKeepAliveHeartbeatIntervalMinutes(minutes)

    fun setCoreUpdateCheckIntervalMinutes(minutes: Int) =
        serviceControlController.setCoreUpdateCheckIntervalMinutes(minutes)

    fun setNormalModeStabilityMode(mode: NormalModeStabilityMode) =
        serviceControlController.setNormalModeStabilityMode(mode)

    fun setNormalNotificationBehavior(behavior: NormalNotificationBehavior) =
        serviceControlController.setNormalNotificationBehavior(behavior)

    fun setServiceNotificationInfoEnabled(enabled: Boolean) =
        serviceControlController.setServiceNotificationInfoEnabled(enabled)

    fun setRootNotificationEnabled(enabled: Boolean) =
        serviceControlController.setRootNotificationEnabled(enabled)

    fun refreshRuntimeRelatedStates() {
        serviceControlController.refreshBootStates()
        rootAutoStartController.refreshBootState()
    }

    fun hasPostNotificationPermission(): Boolean =
        serviceControlController.hasPostNotificationPermission()

    // ---- Root 开机自启与控制中心磁贴 ----

    val rootBootAutoStartEnabled: Boolean
        get() = rootAutoStartController.rootBootAutoStartEnabled
    val isRootAutoStartOperating: Boolean
        get() = rootAutoStartController.isRootAutoStartOperating

    fun enableRootBootAutoStart() = rootAutoStartController.enableRootBootAutoStart()

    fun disableRootBootAutoStart(uninstallModule: Boolean) =
        rootAutoStartController.disableRootBootAutoStart(uninstallModule)

    fun requestAddQuickSettingsTile(activity: Activity?) =
        quickSettingsTileController.requestAddQuickSettingsTile(activity)

    // ---- 外观与主题 ----

    fun setAccessEntryDefaultTab(tab: AccessEntryTab) =
        appearanceController.setAccessEntryDefaultTab(tab)

    fun setAccessEntryLayout(layout: AccessEntryLayout) =
        appearanceController.setAccessEntryLayout(layout)

    fun setNightMode(mode: NightModePreference) = appearanceController.setNightMode(mode)

    fun setGlassMaterial(material: GlassMaterialPreference) =
        appearanceController.setGlassMaterial(material)

    fun setGlassBottomBar(enabled: Boolean) = appearanceController.setGlassBottomBar(enabled)

    fun setGlassTuning(tuning: GlassTuningPreference) = appearanceController.setGlassTuning(tuning)

    fun setGlassPreset(preset: GlassTuningPreset) = appearanceController.setGlassPreset(preset)

    fun setAppBackgroundMode(mode: AppBackgroundMode) =
        appearanceController.setAppBackgroundMode(mode)

    fun setLocalAppBackground(uri: String) = appearanceController.setLocalAppBackground(uri)

    fun setOnlineAppBackground(url: String, random: Boolean) =
        appearanceController.setOnlineAppBackground(url, random)

    fun setRandomBackgroundRefreshPolicy(policy: AppBackgroundRefreshPolicy) =
        appearanceController.setRandomBackgroundRefreshPolicy(policy)

    fun setCustomRandomBackgroundRefreshInterval(input: String): Boolean =
        appearanceController.setCustomRandomBackgroundRefreshInterval(input)

    fun setAppDpiOverride(activity: Activity?, dpi: Int) =
        appearanceController.setAppDpiOverride(activity, dpi)

    fun currentSystemDensityDpi(): Int = appearanceController.currentSystemDensityDpi()

    fun setHideFromRecents(enabled: Boolean) = appearanceController.setHideFromRecents(enabled)

    fun setFileLogEnabled(enabled: Boolean) = appearanceController.setFileLogEnabled(enabled)

    // ---- 应用更新 ----

    val appUpdateCurrentVersion: String
        get() = appUpdateController.appUpdateCurrentVersion
    val appUpdateLatestVersion: String?
        get() = appUpdateController.appUpdateLatestVersion
    val appUpdateReleaseNotes: String
        get() = appUpdateController.appUpdateReleaseNotes
    val appUpdateReleasePage: String
        get() = appUpdateController.appUpdateReleasePage
    val appUpdateAssetName: String?
        get() = appUpdateController.appUpdateAssetName
    val appUpdateAssetSizeBytes: Long
        get() = appUpdateController.appUpdateAssetSizeBytes
    val appUpdateHasUpdate: Boolean
        get() = appUpdateController.appUpdateHasUpdate
    val appUpdateDownloadUrls: List<String>
        get() = appUpdateController.appUpdateDownloadUrls
    val isCheckingAppUpdate: Boolean
        get() = appUpdateController.isCheckingAppUpdate
    val showAppUpdateAvailableDialog: Boolean
        get() = appUpdateController.showAppUpdateAvailableDialog
    val showAppUpdateMethodDialog: Boolean
        get() = appUpdateController.showAppUpdateMethodDialog
    val isDownloadingAppUpdate: Boolean
        get() = appUpdateController.isDownloadingAppUpdate
    val appUpdateDownloadPercent: Int
        get() = appUpdateController.appUpdateDownloadPercent
    val appUpdateDownloadDetail: String
        get() = appUpdateController.appUpdateDownloadDetail
    val downloadedAppUpdate: AppUpdateService.DownloadedApk?
        get() = appUpdateController.downloadedAppUpdate
    val showInstallAppUpdateDialog: Boolean
        get() = appUpdateController.showInstallAppUpdateDialog

    fun checkAppUpdate() = appUpdateController.checkAppUpdate()

    fun downloadLatestAppUpdate() = appUpdateController.downloadLatestAppUpdate()

    fun dismissAppUpdateAvailableDialog() = appUpdateController.dismissAppUpdateAvailableDialog()

    fun openAppUpdateMethodDialog() = appUpdateController.openAppUpdateMethodDialog()

    fun dismissAppUpdateMethodDialog() = appUpdateInstaller.dismissMethodDialog()

    fun startInAppUpdateDownload() = appUpdateController.startInAppUpdateDownload()

    fun installDownloadedAppUpdate(activity: Activity) =
        appUpdateInstaller.installDownloaded(activity)

    fun openBrowserDownload(activity: Activity) = appUpdateController.openBrowserDownload(activity)

    fun dismissInstallAppUpdateDialog() = appUpdateInstaller.dismissInstallDialog()

    fun openAppUpdateReleasePage(activity: Activity) =
        appUpdateController.openAppUpdateReleasePage(activity)

    fun openDownloadsApp(activity: Activity) = appUpdateInstaller.openDownloadsApp(activity)

    // ---- GitHub 代理、账号与依赖修复 ----

    fun adminModeSummary(): String {
        val state = adminSessionState.value
        return when {
            state.isAdminMode -> "已开启 · ${state.tokenHint}"
            state.hasAdminTokenConfigured -> "未开启 · 点击输入 ADMIN_TOKEN"
            else -> "未配置 ADMIN_TOKEN"
        }
    }

    fun setGithubProxy(proxy: String) = settingsRepo.setGithubProxy(proxy)
    fun setCustomRepo(repo: String) = settingsRepo.setCustomRepo(repo)
    fun setTokenVisible(visible: Boolean) = settingsRepo.setTokenVisible(visible)

    fun currentProxyLabel(): String {
        return githubProxyService.currentSelectedOption().name
    }

    fun saveGithubToken(token: String) = githubAccountController.saveGithubToken(token)

    fun clearGithubToken() = githubAccountController.clearGithubToken()

    fun refreshGithubAccount() = githubAccountController.refreshGithubAccount()

    val showProxyPickerDialog: Boolean
        get() = proxyPickerController.uiState.isVisible
    val proxySelectedId: String
        get() = proxyPickerController.uiState.selectedId
    val proxyTestingIds: Set<String>
        get() = proxyPickerController.uiState.testingIds
    val proxyLatencyMap: Map<String, Long>
        get() = proxyPickerController.uiState.latencyMap

    fun openProxyPicker() {
        proxyPickerController.open()
    }

    fun dismissProxyPickerDialog() {
        proxyPickerController.dismiss()
        pendingOnlineDependencyRepair = false
    }

    fun selectProxy(proxyId: String) {
        proxyPickerController.select(proxyId)
    }

    fun retestProxySpeed() {
        proxyPickerController.retest()
    }

    fun confirmProxySelection() {
        val repairDependencies = pendingOnlineDependencyRepair
        proxyPickerController.confirm {
            pendingOnlineDependencyRepair = false
            if (repairDependencies) {
                dependencyRepairController.repairOnlineNow()
            }
        }
    }

    val showDependencyRequiredPrompt: Boolean
        get() = dependencyRepairController.showRequiredPrompt
    val showDependencyRepairDialog: Boolean
        get() = dependencyRepairController.showRepairDialog

    fun dismissDependencyRequiredPrompt() = dependencyRepairController.dismissRequiredPrompt()

    fun openDependencyRepairDialog() = dependencyRepairController.openRepairDialog()

    fun dismissDependencyRepairDialog() = dependencyRepairController.dismissRepairDialog()

    fun repairPendingDependenciesOnline() {
        if (githubProxyService.hasUserSelectedProxy()) {
            dependencyRepairController.repairOnlineNow()
        } else {
            pendingOnlineDependencyRepair = true
            operationMessage = "在线修复前，请先选择 GitHub 线路"
            proxyPickerController.open()
        }
    }

    fun repairPendingDependenciesFromArchive(archiveUri: String) {
        dependencyRepairController.repairFromArchive(archiveUri)
    }

    fun discardPendingCoreMutation() = dependencyRepairController.discardPendingMutation()

    private suspend fun onWorkDirDependenciesApplied(
        request: CoreDependencyRepairRequest
    ): String {
        coreRepo.refreshCoreInfo()
        val continuation = runtimeRepo.continueAfterDependencyRepair(request)
        if (continuation != null) {
            return "${request.variant.label}在当前工作目录中的依赖已修复，$continuation"
        }
        val running = runtimeState.value.status == ServiceStatus.Running
        if (running) {
            runtimeRepo.addLog(LogLevel.Info, "工作目录依赖已修复，正在重启服务应用新目录")
            runtimeRepo.restartService()
        }
        return if (running) {
            "${request.variant.label}在当前工作目录中的依赖已修复，服务正在重启"
        } else {
            "${request.variant.label}在当前工作目录中的依赖已修复"
        }
    }

    // ---- 工作目录 ----

    val workDirInfo: RuntimePaths.WorkDirInfo
        get() = workDirController.workDirInfo
    val isApplyingWorkDir: Boolean
        get() = workDirController.isApplyingWorkDir

    fun envFilePath(): String = backupController.envFilePath()

    fun refreshWorkDirInfo() = workDirController.refreshWorkDirInfo()

    fun applyWorkDirPath(inputPath: String, migrateSelectedCore: Boolean = false) =
        workDirController.applyWorkDirPath(inputPath, migrateSelectedCore)

    fun restoreDefaultWorkDir() = workDirController.restoreDefaultWorkDir()

    fun applyWorkDirFromTreeUri(uri: Uri?) = workDirController.applyWorkDirFromTreeUri(uri)

    // ---- 备份与恢复 ----

    val isFullBackupOperating: Boolean
        get() = backupController.isFullBackupOperating
    val isWebDavOperating: Boolean
        get() = backupController.isWebDavOperating
    val webDavOperatingText: String
        get() = backupController.webDavOperatingText
    val showWebDavConfigDialog: Boolean
        get() = backupController.showWebDavConfigDialog
    val webDavUrlInput: String
        get() = backupController.webDavUrlInput
    val webDavUserInput: String
        get() = backupController.webDavUserInput
    val webDavPassInput: String
        get() = backupController.webDavPassInput
    val webDavPathInput: String
        get() = backupController.webDavPathInput

    fun buildExportFileName(): String = backupController.buildExportFileName()

    suspend fun exportEnvContent(): Result<String> = backupController.exportEnvContent()

    fun importEnvContent(content: String) = backupController.importEnvContent(content)

    fun buildFavoriteExportFileName(): String = backupController.buildFavoriteExportFileName()

    fun buildFullBackupFileName(): String = backupController.buildFullBackupFileName()

    suspend fun createFullBackup(sections: Set<AppBackupSection>): Result<String> =
        backupController.createFullBackup(sections)

    fun inspectFullBackup(content: String): Result<AppBackupPreview> =
        backupController.inspectFullBackup(content)

    fun restoreFullBackup(content: String, sections: Set<AppBackupSection>) =
        backupController.restoreFullBackup(content, sections)

    suspend fun exportFavoriteContent(): Result<FavoriteCacheStore.Snapshot> =
        backupController.exportFavoriteContent()

    fun importFavoriteContent(content: String) = backupController.importFavoriteContent(content)

    fun openWebDavConfigDialog() = backupController.openWebDavConfigDialog()

    fun dismissWebDavConfigDialog() = backupController.dismissWebDavConfigDialog()

    fun updateWebDavUrl(value: String) = backupController.updateWebDavUrl(value)

    fun updateWebDavUser(value: String) = backupController.updateWebDavUser(value)

    fun updateWebDavPass(value: String) = backupController.updateWebDavPass(value)

    fun updateWebDavPath(value: String) = backupController.updateWebDavPath(value)

    fun saveWebDavConfig() = backupController.saveWebDavConfig()

    fun webDavSummary(): String = backupController.webDavSummary()

    fun backupToWebDav() = backupController.backupToWebDav()

    fun restoreFromWebDav() = backupController.restoreFromWebDav()

    // ---- 电视同步与消息 ----

    val isTvSyncOperating: Boolean
        get() = tvSyncController.isTvSyncOperating
    val tvSyncOperatingText: String
        get() = tvSyncController.tvSyncOperatingText

    fun syncConfigToTv(inviteText: String) = tvSyncController.syncConfigToTv(inviteText)

    fun dismissMessage() {
        operationMessage = null
    }

    fun postMessage(message: String) {
        operationMessage = message
    }
}

/**
 * 设置页「同步到电视」子控制器：解析同步码并推送当前配置。
 */
internal class SettingsTvSyncController(
    private val scope: CoroutineScope,
    private val tvConfigSyncClient: TvConfigSyncClient,
    private val runtimeRepo: RuntimeRepository,
    private val postMessage: (String) -> Unit
) {
    var isTvSyncOperating by mutableStateOf(false)
        private set
    var tvSyncOperatingText by mutableStateOf("")
        private set

    fun syncConfigToTv(inviteText: String) {
        if (isTvSyncOperating) return
        scope.launch {
            val target = TvConfigSyncCodec.parseTarget(inviteText).getOrElse {
                postMessage(it.message ?: "未识别到电视同步码")
                return@launch
            }
            isTvSyncOperating = true
            tvSyncOperatingText = if (target.deviceName.isBlank()) {
                "正在向电视发送当前配置..."
            } else {
                "正在同步到 ${target.deviceName}..."
            }
            tvConfigSyncClient.syncToTarget(target).fold(
                onSuccess = {
                    postMessage(it)
                    runtimeRepo.addLog(LogLevel.Info, "已通过扫码同步配置到电视端")
                },
                onFailure = {
                    postMessage("同步失败：${it.message ?: "请检查局域网与同步码"}")
                }
            )
            isTvSyncOperating = false
            tvSyncOperatingText = ""
        }
    }
}

/**
 * 设置页「控制中心快捷按钮」子控制器：请求系统添加磁贴并回报结果。
 */
internal class SettingsQuickSettingsTileController(
    private val postMessage: (String) -> Unit
) {
    fun requestAddQuickSettingsTile(activity: Activity?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            postMessage("当前系统版本不支持控制中心快捷按钮")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            postMessage("请在控制中心编辑页手动添加“弹幕API服务”")
            return
        }
        if (activity == null) {
            postMessage("当前界面无法发起添加，请稍后重试")
            return
        }

        val manager = activity.getSystemService(StatusBarManager::class.java)
        if (manager == null) {
            postMessage("系统控制中心服务不可用，请手动添加")
            return
        }

        val component = ComponentName(activity, DanmuQuickSettingsTileService::class.java)
        val label = activity.getString(R.string.qs_tile_label)
        val icon = Icon.createWithResource(activity, R.drawable.ic_qs_danmu_service)
        manager.requestAddTileService(
            component,
            label,
            icon,
            activity.mainExecutor
        ) { result ->
            postMessage(
                when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "已添加控制中心按钮"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "控制中心按钮已存在"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> "未添加控制中心按钮"
                    StatusBarManager.TILE_ADD_REQUEST_ERROR_REQUEST_IN_PROGRESS -> "系统正在处理上一次添加请求"
                    StatusBarManager.TILE_ADD_REQUEST_ERROR_APP_NOT_IN_FOREGROUND -> "请保持应用在前台后重试"
                    else -> "添加控制中心按钮失败：$result"
                }
            )
        }
    }
}

/**
 * 设置页「GitHub 账号」子控制器：Token 校验保存、清空与额度刷新。
 *
 * 通过递增 generation 取消过期的异步刷新，行为与拆分前一致。
 */
internal class SettingsGithubAccountController(
    private val scope: CoroutineScope,
    private val settingsRepo: SettingsRepository,
    private val githubAccountService: GithubAccountService,
    private val postMessage: (String) -> Unit
) {
    private var githubAccountJob: Job? = null
    private var githubAccountGeneration = 0L

    fun saveGithubToken(token: String) {
        val normalized = token.trim()
        val generation = beginGithubAccountOperation()
        githubAccountJob = scope.launch {
            if (normalized.isBlank()) {
                settingsRepo.setGithubToken("")
                githubAccountService.refresh("")
                if (generation != githubAccountGeneration) return@launch
                postMessage("已切换为匿名 GitHub 额度")
                githubAccountJob = null
                return@launch
            }
            val result = githubAccountService.refresh(normalized)
            if (generation != githubAccountGeneration) return@launch
            when (result.tokenValid) {
                true -> {
                    val saveError = runCatching { settingsRepo.setGithubToken(normalized) }.exceptionOrNull()
                    if (saveError != null) {
                        githubAccountService.refresh()
                        if (generation != githubAccountGeneration) return@launch
                        postMessage(saveError.message ?: "GitHub Token 安全保存失败")
                        githubAccountJob = null
                        return@launch
                    }
                    githubAccountService.refresh()
                    if (generation != githubAccountGeneration) return@launch
                    postMessage("GitHub Token 验证成功${result.login?.let { "：$it" }.orEmpty()}")
                }
                false, null -> {
                    val errorMessage = if (result.tokenValid == false) {
                        result.error ?: "GitHub Token 无效，未保存"
                    } else {
                        result.error ?: "暂时无法验证 Token，未保存"
                    }
                    githubAccountService.refresh()
                    if (generation != githubAccountGeneration) return@launch
                    postMessage(errorMessage)
                }
            }
            if (generation == githubAccountGeneration) githubAccountJob = null
        }
    }

    fun clearGithubToken() {
        val generation = beginGithubAccountOperation()
        settingsRepo.setGithubToken("")
        githubAccountJob = scope.launch {
            githubAccountService.refresh("")
            if (generation == githubAccountGeneration) githubAccountJob = null
        }
        postMessage("已清空 GitHub Token，当前使用匿名额度")
    }

    fun refreshGithubAccount() {
        val generation = beginGithubAccountOperation()
        githubAccountJob = scope.launch {
            githubAccountService.refresh()
            if (generation == githubAccountGeneration) githubAccountJob = null
        }
    }

    private fun beginGithubAccountOperation(): Long {
        githubAccountGeneration += 1
        githubAccountJob?.cancel()
        githubAccountJob = null
        return githubAccountGeneration
    }
}

/**
 * 设置页「Root 开机自启」子控制器：安装/卸载自启模块与开关状态。
 *
 * 行为与拆分前一致，仅把状态与协程从 ViewModel 中搬出。
 */
internal class SettingsRootAutoStartController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val runtimeRepo: RuntimeRepository,
    private val postMessage: (String) -> Unit
) {
    var rootBootAutoStartEnabled by mutableStateOf(
        RootAutoStartPrefs.isBootAutoStartEnabled(context)
    )
        private set
    var isRootAutoStartOperating by mutableStateOf(false)
        private set

    fun enableRootBootAutoStart() {
        if (isRootAutoStartOperating) return
        if (runtimeRepo.runtimeState.value.runMode != RunMode.Root) {
            postMessage("请先切换到 Root 模式")
            return
        }
        scope.launch {
            isRootAutoStartOperating = true
            val result = withContext(Dispatchers.IO) {
                RootAutoStartModule.installAndEnable(context)
            }
            if (result.ok) {
                RootAutoStartPrefs.setBootAutoStartEnabled(context, true)
                rootBootAutoStartEnabled = true
                postMessage("已安装模块并开启开机自启，建议重启设备验证")
            } else {
                postMessage("开启失败：${result.message}")
            }
            isRootAutoStartOperating = false
        }
    }

    fun disableRootBootAutoStart(uninstallModule: Boolean) {
        if (isRootAutoStartOperating) return
        scope.launch {
            isRootAutoStartOperating = true
            val result = withContext(Dispatchers.IO) {
                if (uninstallModule) RootAutoStartModule.uninstall()
                else RootAutoStartModule.disableOnly()
            }
            if (result.ok) {
                RootAutoStartPrefs.setBootAutoStartEnabled(context, false)
                rootBootAutoStartEnabled = false
                postMessage(
                    if (uninstallModule) {
                        "已卸载模块并关闭开机自启"
                    } else {
                        "已关闭开机自启（模块保留）"
                    }
                )
            } else {
                postMessage(
                    if (uninstallModule) {
                        "卸载失败：${result.message}"
                    } else {
                        "关闭失败：${result.message}"
                    }
                )
            }
            isRootAutoStartOperating = false
        }
    }

    fun refreshBootState() {
        rootBootAutoStartEnabled = RootAutoStartPrefs.isBootAutoStartEnabled(context)
    }
}
