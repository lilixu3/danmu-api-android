package com.example.danmuapiapp.ui.screen.settings

import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.net.Uri
import android.content.res.Resources
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.data.service.AppUpdateService
import com.example.danmuapiapp.data.service.AppBackupPreview
import com.example.danmuapiapp.data.service.AppBackupSection
import com.example.danmuapiapp.data.service.AppBackupService
import com.example.danmuapiapp.data.service.BackupEnvironmentPolicy
import com.example.danmuapiapp.data.service.DanmuQuickSettingsTileService
import com.example.danmuapiapp.data.service.GithubProxyService
import com.example.danmuapiapp.data.service.GithubProxySpeedTester
import com.example.danmuapiapp.data.service.GithubAccountService
import com.example.danmuapiapp.data.service.FavoriteCacheStore
import com.example.danmuapiapp.data.service.NodeKeepAlivePrefs
import com.example.danmuapiapp.data.service.NodeProjectManager
import com.example.danmuapiapp.data.service.NormalModeRuntimeProfiles
import com.example.danmuapiapp.data.service.NormalAutoStartPrefs
import com.example.danmuapiapp.data.service.RootAutoStartModule
import com.example.danmuapiapp.data.service.RootAutoStartPrefs
import com.example.danmuapiapp.data.service.RootShell
import com.example.danmuapiapp.data.service.RuntimePaths
import com.example.danmuapiapp.data.service.SystemHeartbeatScheduler
import com.example.danmuapiapp.data.service.TvConfigSyncClient
import com.example.danmuapiapp.data.service.TvConfigSyncCodec
import com.example.danmuapiapp.data.service.WebDavService
import com.example.danmuapiapp.R
import com.example.danmuapiapp.data.util.AppAppearancePrefs
import com.example.danmuapiapp.data.util.RuntimeTokenNormalizer
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.AppBackgroundMode
import com.example.danmuapiapp.domain.model.AppBackgroundRefreshPolicy
import com.example.danmuapiapp.domain.model.CoreDependencyRepairOrigin
import com.example.danmuapiapp.domain.model.CoreDependencyRepairRequest
import com.example.danmuapiapp.domain.model.GlassMaterialPreference
import com.example.danmuapiapp.domain.model.isValidBackgroundImageUrl
import com.example.danmuapiapp.domain.model.KeepAliveHeartbeatMode
import com.example.danmuapiapp.domain.model.LogLevel
import com.example.danmuapiapp.domain.model.NightModePreference
import com.example.danmuapiapp.domain.model.NormalModeStabilityMode
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.RuntimeListenMode
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.model.WebDavConfig
import com.example.danmuapiapp.domain.repository.CoreRepository
import com.example.danmuapiapp.domain.repository.EnvConfigRepository
import com.example.danmuapiapp.domain.repository.AdminSessionRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.domain.repository.SettingsRepository
import com.example.danmuapiapp.ui.common.AppUpdateInstallerController
import com.example.danmuapiapp.ui.common.CoreDependencyRepairController
import com.example.danmuapiapp.ui.common.ProxyPickerController
import com.example.danmuapiapp.ui.common.buildRootSwitchDeniedMessage
import com.example.danmuapiapp.ui.common.continueAfterDependencyRepair
import com.example.danmuapiapp.ui.common.parseEnvContentMap
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

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
    private val tvConfigSyncClient: TvConfigSyncClient
) : ViewModel() {

    private val envConfigRepo: EnvConfigRepository
        get() = envConfigRepoLazy.get()

    val runtimeState = runtimeRepo.runtimeState
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
    val nightMode = settingsRepo.nightMode
    val glassMaterial = settingsRepo.glassMaterial
    val appBackground = settingsRepo.appBackground
    val appDpiOverride = settingsRepo.appDpiOverride
    val hideFromRecents = settingsRepo.hideFromRecents
    val fileLogEnabled = settingsRepo.fileLogEnabled
    val adminSessionState = adminSessionRepository.sessionState
    val proxyOptions = githubProxyService.proxyOptions()

    var normalBootAutoStartEnabled by mutableStateOf(
        NormalAutoStartPrefs.isBootAutoStartEnabled(context)
    )
        private set
    var rootBootAutoStartEnabled by mutableStateOf(
        RootAutoStartPrefs.isBootAutoStartEnabled(context)
    )
        private set
    var isRootAutoStartOperating by mutableStateOf(false)
        private set
    var isFullBackupOperating by mutableStateOf(false)
        private set
    var isRunModeSwitching by mutableStateOf(false)
        private set
    var a11yEnabled by mutableStateOf(NodeKeepAlivePrefs.isAccessibilityServiceEnabled(context))
        private set

    var appUpdateCurrentVersion by mutableStateOf(appUpdateService.currentVersionName())
        private set
    var appUpdateLatestVersion by mutableStateOf<String?>(null)
        private set
    var appUpdateReleaseNotes by mutableStateOf("点击下方按钮检查更新")
        private set
    var appUpdateReleasePage by mutableStateOf("")
        private set
    var appUpdateAssetName by mutableStateOf<String?>(null)
        private set
    var appUpdateAssetSizeBytes by mutableStateOf(0L)
        private set
    var appUpdateHasUpdate by mutableStateOf(false)
        private set
    var appUpdateDownloadUrls by mutableStateOf<List<String>>(emptyList())
        private set
    var isCheckingAppUpdate by mutableStateOf(false)
        private set
    var showAppUpdateAvailableDialog by mutableStateOf(false)
        private set

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

    val showProxyPickerDialog: Boolean
        get() = proxyPickerController.uiState.isVisible
    val proxySelectedId: String
        get() = proxyPickerController.uiState.selectedId
    val proxyTestingIds: Set<String>
        get() = proxyPickerController.uiState.testingIds
    val proxyLatencyMap: Map<String, Long>
        get() = proxyPickerController.uiState.latencyMap
    var operationMessage by mutableStateOf<String?>(null)
        private set
    var isWebDavOperating by mutableStateOf(false)
        private set
    var webDavOperatingText by mutableStateOf("")
        private set
    var isTvSyncOperating by mutableStateOf(false)
        private set
    var tvSyncOperatingText by mutableStateOf("")
        private set
    var showWebDavConfigDialog by mutableStateOf(false)
        private set
    var webDavUrlInput by mutableStateOf("")
        private set
    var webDavUserInput by mutableStateOf("")
        private set
    var webDavPassInput by mutableStateOf("")
        private set
    var webDavPathInput by mutableStateOf("")
        private set
    var workDirInfo by mutableStateOf(defaultWorkDirInfo())
        private set
    var isApplyingWorkDir by mutableStateOf(false)
        private set
    var isRepairingDependencies by mutableStateOf(false)
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
    private var githubAccountJob: Job? = null
    private var githubAccountGeneration = 0L
    private val dependencyRepairController = CoreDependencyRepairController(
        scope = viewModelScope,
        repository = coreRepo,
        shouldHandle = { it.origin == CoreDependencyRepairOrigin.WorkDirectory },
        setOperating = { isRepairingDependencies = it },
        postMessage = { operationMessage = it },
        onApplied = ::onWorkDirDependenciesApplied,
        onDiscarded = { "已取消依赖修复，当前工作目录保持不变" }
    )

    val showDependencyRequiredPrompt: Boolean
        get() = dependencyRepairController.showRequiredPrompt
    val showDependencyRepairDialog: Boolean
        get() = dependencyRepairController.showRepairDialog

    fun adminModeSummary(): String {
        val state = adminSessionState.value
        return when {
            state.isAdminMode -> "已开启 · ${state.tokenHint}"
            state.hasAdminTokenConfigured -> "未开启 · 点击输入 ADMIN_TOKEN"
            else -> "未配置 ADMIN_TOKEN"
        }
    }

    fun saveServiceConfig(
        port: Int,
        token: String,
        listenMode: RuntimeListenMode
    ) {
        val normalizedToken = RuntimeTokenNormalizer.normalizeInput(token)
        val old = runtimeState.value
        if (old.runMode == RunMode.Normal && port in 1..1023) {
            operationMessage = "普通模式无法监听 1-1023 端口，请切换 Root 模式或改用 1024+ 端口"
            return
        }

        val changed = old.port != port ||
            old.token != normalizedToken ||
            old.listenMode != listenMode
        if (!changed) {
            operationMessage = "配置未变化"
            return
        }

        runtimeRepo.applyServiceConfig(
            port = port,
            token = normalizedToken,
            restartIfRunning = true,
            listenMode = listenMode
        )
        operationMessage = if (old.status == ServiceStatus.Running || old.status == ServiceStatus.Starting) {
            "配置已保存，服务正在应用新的监听设置"
        } else {
            "配置已保存"
        }
    }

    fun restartService() = runtimeRepo.restartService()

    fun updateVariant(variant: ApiVariant) {
        if (!coreRepo.isCoreInstalled(variant)) {
            operationMessage = "${variant.label}尚未安装，请先到核心页安装或配置仓库"
            return
        }
        runtimeRepo.updateVariant(variant)
        if (runtimeState.value.status == ServiceStatus.Running) {
            runtimeRepo.restartService()
        }
    }

    fun updateRunMode(mode: RunMode) {
        if (isRunModeSwitching) return
        if (runtimeState.value.runMode == mode) return

        viewModelScope.launch {
            isRunModeSwitching = true
            try {
                if (mode.requiresRoot) {
                    val check = withContext(Dispatchers.IO) {
                        RootShell.exec("id", timeoutMs = 4000L)
                    }
                    if (!check.ok) {
                        operationMessage = buildRootSwitchDeniedMessage(check)
                        return@launch
                    }
                }

                runtimeRepo.updateRunMode(mode)
                refreshRuntimeRelatedStates()
                refreshWorkDirInfo()
                SystemHeartbeatScheduler.refresh(context)
            } finally {
                isRunModeSwitching = false
            }
        }
    }

    fun setAutoStart(enabled: Boolean) = setNormalBootAutoStart(enabled)

    fun setNormalBootAutoStart(enabled: Boolean) {
        NormalAutoStartPrefs.setBootAutoStartEnabled(context, enabled)
        normalBootAutoStartEnabled = enabled
        operationMessage = if (enabled) {
            "已开启普通模式开机自启"
        } else {
            "已关闭普通模式开机自启"
        }
    }

    fun setKeepAliveEnabled(enabled: Boolean) {
        settingsRepo.setKeepAlive(enabled)
        if (!enabled) {
            NodeKeepAlivePrefs.requestDisableAccessibilityService(context)
        }
        SystemHeartbeatScheduler.refresh(context)
        operationMessage = if (enabled) {
            "已开启无障碍保活，请在系统无障碍中启用服务"
        } else {
            "已关闭无障碍保活"
        }
    }

    fun setKeepAliveHeartbeatEnabled(enabled: Boolean) {
        settingsRepo.setKeepAliveHeartbeatEnabled(enabled)
        SystemHeartbeatScheduler.refresh(context)
        operationMessage = if (enabled) {
            "已开启心跳兜底检查"
        } else {
            "已关闭心跳兜底检查"
        }
    }

    fun setKeepAliveHeartbeatMode(mode: KeepAliveHeartbeatMode) {
        settingsRepo.setKeepAliveHeartbeatMode(mode)
        SystemHeartbeatScheduler.refresh(context)
        operationMessage = when (mode) {
            KeepAliveHeartbeatMode.Accessibility -> "已切换为无障碍心跳"
            KeepAliveHeartbeatMode.System -> "已切换为系统定时心跳（实验）"
        }
    }

    fun setKeepAliveHeartbeatIntervalMinutes(minutes: Int) {
        val normalized = NodeKeepAlivePrefs.normalizeHeartbeatIntervalMinutes(minutes)
        settingsRepo.setKeepAliveHeartbeatIntervalMinutes(normalized)
        SystemHeartbeatScheduler.refresh(context)
        operationMessage = "心跳间隔已更新为 ${normalized} 分钟"
    }

    fun setCoreUpdateCheckIntervalMinutes(minutes: Int) {
        settingsRepo.setCoreUpdateCheckIntervalMinutes(minutes)
        operationMessage = "核心自动检查间隔已更新为 ${settingsRepo.coreUpdateCheckIntervalMinutes.value} 分钟"
    }

    fun setNormalModeStabilityMode(mode: NormalModeStabilityMode) {
        if (normalModeStabilityMode.value == mode) {
            operationMessage = "普通模式稳定策略已是 ${mode.label}"
            return
        }
        settingsRepo.setNormalModeStabilityMode(mode)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    NodeProjectManager.syncRuntimeEnvIfProjectReady(
                        context = context,
                        targetProjectDir = RuntimePaths.normalProjectDir(context),
                        preferredVariantKey = runtimeState.value.variant.key
                    )
                }
            }
            val state = runtimeState.value
            operationMessage = when {
                state.runMode != RunMode.Normal -> {
                    "普通模式稳定策略已改为 ${mode.label}，切回普通模式后生效"
                }
                state.status == ServiceStatus.Running -> {
                    runtimeRepo.addLog(LogLevel.Info, "普通模式稳定策略已切换为 ${mode.label}，正在重启服务应用新策略")
                    runtimeRepo.restartService()
                    "普通模式稳定策略已改为 ${mode.label}，服务正在重启"
                }
                else -> {
                    "普通模式稳定策略已改为 ${mode.label}，下次启动生效"
                }
            }
        }
    }

    fun setNightMode(mode: NightModePreference) {
        settingsRepo.setNightMode(mode)
        operationMessage = when (mode) {
            NightModePreference.FollowSystem -> "主题已改为跟随系统"
            NightModePreference.Light -> "已切换为浅色主题"
            NightModePreference.Dark -> "已切换为暗色主题"
        }
    }

    fun setGlassMaterial(material: GlassMaterialPreference) {
        settingsRepo.setGlassMaterial(material)
        operationMessage = when (material) {
            GlassMaterialPreference.LiquidGlass -> "已启用液态玻璃"
            GlassMaterialPreference.Off -> "已关闭液态玻璃"
        }
    }

    fun setAppBackgroundMode(mode: AppBackgroundMode) {
        val current = appBackground.value
        if (current.mode == mode) return
        settingsRepo.setAppBackground(current.copy(mode = mode))
        operationMessage = when (mode) {
            AppBackgroundMode.Solid -> "已使用纯色背景"
            AppBackgroundMode.LocalImage -> "已使用本地图片背景"
            AppBackgroundMode.OnlineImage -> "已切换到在线图片背景"
            AppBackgroundMode.RandomOnlineImage -> "已启用前台随机图片背景"
        }
    }

    fun setLocalAppBackground(uri: String) {
        settingsRepo.setAppBackground(
            appBackground.value.copy(
                mode = AppBackgroundMode.LocalImage,
                localImageUri = uri
            )
        )
        operationMessage = "已应用本地图片背景"
    }

    fun setOnlineAppBackground(url: String, random: Boolean) {
        val normalized = url.trim()
        if (!isValidBackgroundImageUrl(normalized)) {
            operationMessage = "请输入有效的 HTTP 或 HTTPS 图片链接"
            return
        }
        val current = appBackground.value
        settingsRepo.setAppBackground(
            if (random) {
                current.copy(
                    mode = AppBackgroundMode.RandomOnlineImage,
                    randomImageUrl = normalized
                )
            } else {
                current.copy(
                    mode = AppBackgroundMode.OnlineImage,
                    onlineImageUrl = normalized
                )
            }
        )
        operationMessage = if (random) {
            "已应用前台随机图片接口"
        } else {
            "已应用在线图片背景"
        }
    }

    fun setRandomBackgroundRefreshPolicy(policy: AppBackgroundRefreshPolicy) {
        val current = appBackground.value
        if (policy == AppBackgroundRefreshPolicy.Custom &&
            current.customRandomRefreshSeconds <= 0L
        ) {
            operationMessage = "请先输入并应用自定义刷新秒数"
            return
        }
        if (current.randomRefreshPolicy == policy) return
        settingsRepo.setAppBackground(current.copy(randomRefreshPolicy = policy))
        operationMessage = "随机背景前台刷新条件已设为 ${randomRefreshPolicyLabel(policy)}"
    }

    fun setCustomRandomBackgroundRefreshInterval(input: String): Boolean {
        val seconds = input.trim().toLongOrNull()
        if (seconds == null || seconds <= 0L || seconds > Long.MAX_VALUE / 1_000L) {
            operationMessage = "请输入大于 0 的有效秒数"
            return false
        }
        val current = appBackground.value
        settingsRepo.setAppBackground(
            current.copy(
                randomRefreshPolicy = AppBackgroundRefreshPolicy.Custom,
                customRandomRefreshSeconds = seconds
            )
        )
        operationMessage = "随机背景前台刷新条件已设为 ${seconds} 秒"
        return true
    }

    private fun randomRefreshPolicyLabel(policy: AppBackgroundRefreshPolicy): String {
        return when (policy) {
            AppBackgroundRefreshPolicy.OnForeground -> "每次进入前台"
            AppBackgroundRefreshPolicy.Seconds30 -> "30 秒"
            AppBackgroundRefreshPolicy.Minute1 -> "1 分钟"
            AppBackgroundRefreshPolicy.Minutes3 -> "3 分钟"
            AppBackgroundRefreshPolicy.Minutes5 -> "5 分钟"
            AppBackgroundRefreshPolicy.Minutes10 -> "10 分钟"
            AppBackgroundRefreshPolicy.Custom -> "自定义"
        }
    }

    fun setAppDpiOverride(activity: Activity?, dpi: Int) {
        val normalized = AppAppearancePrefs.normalizeAppDpiOverride(dpi)
        if (normalized == appDpiOverride.value) {
            operationMessage = if (normalized == AppAppearancePrefs.APP_DPI_SYSTEM) {
                "当前已是跟随系统 DPI"
            } else {
                "当前已是 ${normalized} DPI"
            }
            return
        }
        settingsRepo.setAppDpiOverride(normalized)
        operationMessage = if (normalized == AppAppearancePrefs.APP_DPI_SYSTEM) {
            "已恢复跟随系统 DPI，正在刷新界面"
        } else {
            "已应用 ${normalized} DPI，正在刷新界面"
        }
        activity?.recreate()
    }

    fun currentSystemDensityDpi(): Int = Resources.getSystem().displayMetrics.densityDpi

    fun setHideFromRecents(enabled: Boolean) {
        settingsRepo.setHideFromRecents(enabled)
        operationMessage = if (enabled) {
            "已隐藏最近任务卡片"
        } else {
            "已恢复显示最近任务卡片"
        }
    }

    fun requestAddQuickSettingsTile(activity: Activity?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            operationMessage = "当前系统版本不支持控制中心快捷按钮"
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            operationMessage = "请在控制中心编辑页手动添加“弹幕API服务”"
            return
        }
        if (activity == null) {
            operationMessage = "当前界面无法发起添加，请稍后重试"
            return
        }

        val manager = activity.getSystemService(StatusBarManager::class.java)
        if (manager == null) {
            operationMessage = "系统控制中心服务不可用，请手动添加"
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
            operationMessage = when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "已添加控制中心按钮"
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "控制中心按钮已存在"
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> "未添加控制中心按钮"
                StatusBarManager.TILE_ADD_REQUEST_ERROR_REQUEST_IN_PROGRESS -> "系统正在处理上一次添加请求"
                StatusBarManager.TILE_ADD_REQUEST_ERROR_APP_NOT_IN_FOREGROUND -> "请保持应用在前台后重试"
                else -> "添加控制中心按钮失败：$result"
            }
        }
    }

    fun enableRootBootAutoStart() {
        if (isRootAutoStartOperating) return
        if (runtimeState.value.runMode != RunMode.Root) {
            operationMessage = "请先切换到 Root 模式"
            return
        }
        viewModelScope.launch {
            isRootAutoStartOperating = true
            val result = withContext(Dispatchers.IO) {
                RootAutoStartModule.installAndEnable(context)
            }
            if (result.ok) {
                RootAutoStartPrefs.setBootAutoStartEnabled(context, true)
                rootBootAutoStartEnabled = true
                operationMessage = "已安装模块并开启开机自启，建议重启设备验证"
            } else {
                operationMessage = "开启失败：${result.message}"
            }
            isRootAutoStartOperating = false
        }
    }

    fun disableRootBootAutoStart(uninstallModule: Boolean) {
        if (isRootAutoStartOperating) return
        viewModelScope.launch {
            isRootAutoStartOperating = true
            val result = withContext(Dispatchers.IO) {
                if (uninstallModule) RootAutoStartModule.uninstall()
                else RootAutoStartModule.disableOnly()
            }
            if (result.ok) {
                RootAutoStartPrefs.setBootAutoStartEnabled(context, false)
                rootBootAutoStartEnabled = false
                operationMessage = if (uninstallModule) {
                    "已卸载模块并关闭开机自启"
                } else {
                    "已关闭开机自启（模块保留）"
                }
            } else {
                operationMessage = if (uninstallModule) {
                    "卸载失败：${result.message}"
                } else {
                    "关闭失败：${result.message}"
                }
            }
            isRootAutoStartOperating = false
        }
    }

    fun refreshRuntimeRelatedStates() {
        normalBootAutoStartEnabled = NormalAutoStartPrefs.isBootAutoStartEnabled(context)
        rootBootAutoStartEnabled = RootAutoStartPrefs.isBootAutoStartEnabled(context)
        a11yEnabled = NodeKeepAlivePrefs.isAccessibilityServiceEnabled(context)
    }

    fun hasPostNotificationPermission(): Boolean {
        return NodeKeepAlivePrefs.hasPostNotificationsPermission(context)
    }

    fun checkAppUpdate() {
        if (isCheckingAppUpdate) return
        viewModelScope.launch {
            isCheckingAppUpdate = true
            appUpdateCurrentVersion = appUpdateService.currentVersionName()
            val result = appUpdateService.checkLatestRelease()
            result.fold(
                onSuccess = { info ->
                    appUpdateLatestVersion = info.latestVersion
                    appUpdateReleaseNotes = info.releaseNotes
                    appUpdateReleasePage = info.releasePage
                    appUpdateAssetName = info.bestAsset?.name
                    appUpdateAssetSizeBytes = info.bestAsset?.size ?: 0L
                    appUpdateDownloadUrls = info.downloadUrls

                    if (info.hasUpdate) {
                        appUpdateHasUpdate = true
                        showAppUpdateAvailableDialog = true
                        appUpdateInstaller.dismissMethodDialog()
                        operationMessage = "发现新版本 v${info.latestVersion}"
                    } else {
                        appUpdateHasUpdate = false
                        showAppUpdateAvailableDialog = false
                        appUpdateInstaller.reset()
                        operationMessage = "当前已是最新版本（v${info.currentVersion}）"
                    }
                },
                onFailure = {
                    showAppUpdateAvailableDialog = false
                    appUpdateInstaller.dismissMethodDialog()
                    operationMessage = "检查更新失败：${it.message ?: "请稍后重试"}"
                }
            )
            isCheckingAppUpdate = false
        }
    }

    fun downloadLatestAppUpdate() {
        if (isDownloadingAppUpdate || isCheckingAppUpdate) return
        appUpdateInstaller.startDownload(
            urls = appUpdateDownloadUrls,
            latestVersion = appUpdateLatestVersion,
            missingMessage = "请先检查更新"
        )
    }

    fun dismissAppUpdateAvailableDialog() {
        showAppUpdateAvailableDialog = false
    }

    fun openAppUpdateMethodDialog() {
        showAppUpdateAvailableDialog = false
        appUpdateInstaller.openMethodDialog()
    }

    fun dismissAppUpdateMethodDialog() {
        appUpdateInstaller.dismissMethodDialog()
    }

    fun startInAppUpdateDownload() {
        appUpdateInstaller.dismissMethodDialog()
        downloadLatestAppUpdate()
    }

    fun installDownloadedAppUpdate(activity: Activity) {
        appUpdateInstaller.installDownloaded(activity)
    }

    fun openBrowserDownload(activity: Activity) {
        appUpdateInstaller.openBrowserDownload(
            activity = activity,
            downloadUrls = appUpdateDownloadUrls,
            releasePage = appUpdateReleasePage,
            fallbackReleasePage = "https://github.com/lilixu3/danmu-api-android/releases/latest",
            beforeOpen = { showAppUpdateAvailableDialog = false }
        )
    }

    fun dismissInstallAppUpdateDialog() {
        appUpdateInstaller.dismissInstallDialog()
    }

    fun openAppUpdateReleasePage(activity: Activity) {
        val url = appUpdateReleasePage.ifBlank { "https://github.com/lilixu3/danmu-api-android/releases/latest" }
        appUpdateService.openUrl(activity, url)
    }

    fun openDownloadsApp(activity: Activity) {
        appUpdateInstaller.openDownloadsApp(activity)
    }

    fun setFileLogEnabled(enabled: Boolean) {
        settingsRepo.setFileLogEnabled(false)
        operationMessage = "已固定为 API 日志模式，不再写入本地日志文件"
    }

    fun setGithubProxy(proxy: String) = settingsRepo.setGithubProxy(proxy)
    fun setCustomRepo(repo: String) = settingsRepo.setCustomRepo(repo)
    fun setTokenVisible(visible: Boolean) = settingsRepo.setTokenVisible(visible)

    fun currentProxyLabel(): String {
        return githubProxyService.currentSelectedOption().name
    }

    fun saveGithubToken(token: String) {
        val normalized = token.trim()
        val generation = beginGithubAccountOperation()
        githubAccountJob = viewModelScope.launch {
            if (normalized.isBlank()) {
                settingsRepo.setGithubToken("")
                githubAccountService.refresh("")
                if (generation != githubAccountGeneration) return@launch
                operationMessage = "已切换为匿名 GitHub 额度"
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
                        operationMessage = saveError.message ?: "GitHub Token 安全保存失败"
                        githubAccountJob = null
                        return@launch
                    }
                    githubAccountService.refresh()
                    if (generation != githubAccountGeneration) return@launch
                    operationMessage = "GitHub Token 验证成功${result.login?.let { "：$it" }.orEmpty()}"
                }
                false, null -> {
                    val errorMessage = if (result.tokenValid == false) {
                        result.error ?: "GitHub Token 无效，未保存"
                    } else {
                        result.error ?: "暂时无法验证 Token，未保存"
                    }
                    githubAccountService.refresh()
                    if (generation != githubAccountGeneration) return@launch
                    operationMessage = errorMessage
                }
            }
            if (generation == githubAccountGeneration) githubAccountJob = null
        }
    }

    fun clearGithubToken() {
        val generation = beginGithubAccountOperation()
        settingsRepo.setGithubToken("")
        githubAccountJob = viewModelScope.launch {
            githubAccountService.refresh("")
            if (generation == githubAccountGeneration) githubAccountJob = null
        }
        operationMessage = "已清空 GitHub Token，当前使用匿名额度"
    }

    fun refreshGithubAccount() {
        val generation = beginGithubAccountOperation()
        githubAccountJob = viewModelScope.launch {
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

    fun envFilePath(): String = envConfigRepo.getEnvFilePath()

    fun refreshWorkDirInfo() {
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { loadWorkDirInfoSafe() }
            workDirInfo = info
        }
    }

    fun applyWorkDirPath(inputPath: String, migrateSelectedCore: Boolean = false) {
        val path = inputPath.trim().ifBlank { null }
        applyWorkDirInternal(
            targetPath = path,
            switchMode = if (migrateSelectedCore) {
                RuntimePaths.WorkDirSwitchMode.MigrateSelectedCore
            } else {
                RuntimePaths.WorkDirSwitchMode.SwitchOnly
            }
        )
    }

    fun restoreDefaultWorkDir() {
        applyWorkDirInternal(null, RuntimePaths.WorkDirSwitchMode.SwitchOnly)
    }

    fun applyWorkDirFromTreeUri(uri: Uri?) {
        if (uri == null) {
            operationMessage = "未选择目录"
            return
        }
        val resolvedPath = RuntimePaths.resolveTreeUriToPath(uri)
        if (resolvedPath.isNullOrBlank()) {
            operationMessage = "无法解析所选目录，请改用手动输入"
            return
        }
        applyWorkDirPath(resolvedPath)
    }

    fun buildExportFileName(): String {
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        return "danmu_api_$ts.env"
    }

    suspend fun exportEnvContent(): Result<String> {
        return withContext(Dispatchers.IO) {
            envConfigRepo.readCurrentRawContent()
                .map { it.ifBlank { "# DanmuApiApp .env\n" } }
        }
    }

    fun importEnvContent(content: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                envConfigRepo.saveRawContent(content)
            }
            result.fold(
                onSuccess = {
                    applyRuntimeFromEnv(content)
                    operationMessage = "导入成功，已覆盖当前 .env，建议重启服务"
                    runtimeRepo.addLog(LogLevel.Info, "已导入 .env 配置，建议重启服务")
                },
                onFailure = {
                    operationMessage = "导入失败：${it.message ?: "写入 .env 失败"}"
                    runtimeRepo.addLog(LogLevel.Error, "导入 .env 配置失败：${it.message ?: "写入失败"}")
                }
            )
        }
    }

    fun buildFavoriteExportFileName(): String {
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        return "danmu_api_favorites_$ts.json"
    }

    fun buildFullBackupFileName(): String {
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        return "danmu_api_app_backup_$ts.json"
    }

    suspend fun createFullBackup(sections: Set<AppBackupSection>): Result<String> {
        if (isFullBackupOperating) return Result.failure(IllegalStateException("备份任务正在进行"))
        isFullBackupOperating = true
        return try {
            withContext(Dispatchers.IO) {
                envConfigRepo.readCurrentRawContent().fold(
                    onSuccess = { appBackupService.createBackup(sections, it) },
                    onFailure = { Result.failure(it) }
                )
            }
        } finally {
            isFullBackupOperating = false
        }
    }

    fun inspectFullBackup(content: String): Result<AppBackupPreview> = appBackupService.inspect(content)

    fun restoreFullBackup(content: String, sections: Set<AppBackupSection>) {
        if (isFullBackupOperating) return
        viewModelScope.launch {
            isFullBackupOperating = true
            try {
                val wasRunning = runtimeState.value.status == ServiceStatus.Running
                val currentEnv = withContext(Dispatchers.IO) {
                    envConfigRepo.readCurrentRawContent().getOrThrow()
                }
                val result = withContext(Dispatchers.IO) {
                    appBackupService.restore(
                        raw = content,
                        selectedSections = sections,
                        currentEnvContent = currentEnv,
                        environmentWriter = envConfigRepo::saveRawContent
                    )
                }
                result.fold(
                    onSuccess = { restored ->
                        restored.mergedEnvironment?.let(::applyRuntimeFromEnv)
                        settingsRepo.reloadFromStorage()
                        coreRepo.refreshCoreInfo()
                        if (wasRunning) runtimeRepo.restartService()
                        val favoriteText = restored.favoriteCount?.let { "，$it 项收藏" }.orEmpty()
                        operationMessage = "完整备份恢复成功$favoriteText" +
                            if (wasRunning) "，服务正在重启" else "；部分界面设置将在重启 App 后完全生效"
                        runtimeRepo.addLog(LogLevel.Info, "已恢复完整备份：${restored.restoredSections.joinToString()}")
                    },
                    onFailure = { error ->
                        operationMessage = "完整备份恢复失败：${error.message ?: "文件无效"}"
                    }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                operationMessage = "完整备份恢复失败：${error.message ?: "无法读取当前配置"}"
            } finally {
                isFullBackupOperating = false
            }
        }
    }

    suspend fun exportFavoriteContent(): Result<FavoriteCacheStore.Snapshot> {
        return withContext(Dispatchers.IO) { FavoriteCacheStore.readCurrent(context) }
    }

    fun importFavoriteContent(content: String) {
        viewModelScope.launch {
            val wasRunning = runtimeState.value.status == ServiceStatus.Running
            val result = withContext(Dispatchers.IO) {
                FavoriteCacheStore.writeCurrent(context, content)
            }
            result.fold(
                onSuccess = { snapshot ->
                    if (wasRunning) runtimeRepo.restartService()
                    operationMessage = if (wasRunning) {
                        "已导入 ${snapshot.count} 项收藏，服务正在重启"
                    } else {
                        "已导入 ${snapshot.count} 项收藏，下次启动服务时生效"
                    }
                    runtimeRepo.addLog(LogLevel.Info, "已导入 ${snapshot.count} 项收藏数据")
                },
                onFailure = { error ->
                    operationMessage = "收藏导入失败：${error.message ?: "文件格式无效"}"
                    runtimeRepo.addLog(LogLevel.Error, "收藏导入失败：${error.message ?: "文件格式无效"}")
                }
            )
        }
    }

    fun openWebDavConfigDialog() {
        val config = webDavService.loadConfig()
        webDavUrlInput = config.url
        webDavUserInput = config.username
        webDavPassInput = config.password
        webDavPathInput = config.folderPath
        showWebDavConfigDialog = true
    }

    fun dismissWebDavConfigDialog() {
        showWebDavConfigDialog = false
    }

    fun updateWebDavUrl(value: String) {
        webDavUrlInput = value
    }

    fun updateWebDavUser(value: String) {
        webDavUserInput = value
    }

    fun updateWebDavPass(value: String) {
        webDavPassInput = value
    }

    fun updateWebDavPath(value: String) {
        webDavPathInput = value
    }

    fun saveWebDavConfig() {
        val config = WebDavConfig(
            url = webDavUrlInput.trim(),
            username = webDavUserInput.trim(),
            password = webDavPassInput,
            folderPath = webDavPathInput.trim()
        )
        webDavService.saveConfig(config)
        showWebDavConfigDialog = false
        operationMessage = "WebDAV 设置已保存"
    }

    fun webDavSummary(): String {
        val config = webDavService.loadConfig()
        if (!webDavService.isConfigured(config)) return "未配置"
        val host = config.url.trim().ifBlank { "-" }
        val folder = config.folderPath.trim().ifBlank { "DanmuApi" }
        return "$host  /  $folder"
    }

    fun backupToWebDav() {
        if (isWebDavOperating) return
        viewModelScope.launch {
            val config = webDavService.loadConfig()
            if (!webDavService.isConfigured(config)) {
                operationMessage = "请先配置 WebDAV 账户"
                openWebDavConfigDialog()
                return@launch
            }
            isWebDavOperating = true
            webDavOperatingText = "正在上传安全完整备份到 WebDAV..."
            try {
                val fullBundle = withContext(Dispatchers.IO) {
                    envConfigRepo.readCurrentRawContent().fold(
                        onSuccess = { content ->
                            appBackupService.createBackup(AppBackupSection.entries.toSet(), content)
                        },
                        onFailure = { Result.failure(it) }
                    )
                }
                if (fullBundle.isFailure) {
                    operationMessage = "WebDAV 备份失败：${fullBundle.exceptionOrNull()?.message}"
                    return@launch
                }
                val bundleUpload = webDavService.backupAppBundle(fullBundle.getOrThrow())
                if (bundleUpload.isFailure) {
                    operationMessage = "WebDAV 备份失败：${bundleUpload.exceptionOrNull()?.message}"
                    return@launch
                }
                operationMessage = "WebDAV 完整备份成功，凭据未上传"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                operationMessage = "WebDAV 备份失败：${error.message ?: "未知错误"}"
            } finally {
                isWebDavOperating = false
                webDavOperatingText = ""
            }
        }
    }

    fun restoreFromWebDav() {
        if (isWebDavOperating) return
        viewModelScope.launch {
            val config = webDavService.loadConfig()
            if (!webDavService.isConfigured(config)) {
                operationMessage = "请先配置 WebDAV 账户"
                openWebDavConfigDialog()
                return@launch
            }
            isWebDavOperating = true
            webDavOperatingText = "正在从 WebDAV 下载完整备份..."
            try {
                val bundleResult = webDavService.restoreAppBundle()
                if (bundleResult.isFailure) {
                    operationMessage = "WebDAV 恢复失败：${bundleResult.exceptionOrNull()?.message}"
                    return@launch
                }
                val fullBundle = bundleResult.getOrNull()
                if (fullBundle != null) {
                    val preview = withContext(Dispatchers.Default) {
                        appBackupService.inspect(fullBundle)
                    }
                    val sections = preview.getOrElse { error ->
                        operationMessage = "云端完整备份无效：${error.message}"
                        return@launch
                    }.sections
                    restoreFullBackup(fullBundle, sections)
                    return@launch
                }
                val envResult = webDavService.restoreEnv()
                val favoriteResult = if (envResult.isSuccess) {
                    webDavService.restoreFavorites()
                } else {
                    Result.success(null)
                }
                when {
                    envResult.isFailure -> {
                        operationMessage = "WebDAV 恢复失败：${envResult.exceptionOrNull()?.message}"
                    }
                    favoriteResult.isFailure -> {
                        operationMessage = "WebDAV 恢复失败：${favoriteResult.exceptionOrNull()?.message}"
                    }
                    else -> {
                        restoreLegacyWebDavBackup(
                            envContent = envResult.getOrThrow(),
                            favoriteContent = favoriteResult.getOrNull()
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                operationMessage = "WebDAV 恢复失败：${error.message ?: "未知错误"}"
            } finally {
                isWebDavOperating = false
                webDavOperatingText = ""
            }
        }
    }

    private suspend fun restoreLegacyWebDavBackup(
        envContent: String,
        favoriteContent: String?
    ) {
        val validatedFavorite = favoriteContent?.let { FavoriteCacheStore.snapshotOf(it) }
        val previousEnv = withContext(Dispatchers.IO) {
            envConfigRepo.readCurrentRawContent().getOrThrow()
        }
        val safeEnvContent = BackupEnvironmentPolicy.merge(
            current = previousEnv,
            restored = BackupEnvironmentPolicy.exportValues(envContent)
        )
        val previousFavorite = if (validatedFavorite != null) {
            withContext(Dispatchers.IO) { FavoriteCacheStore.readCurrent(context).getOrThrow() }
        } else null

        try {
            withContext(Dispatchers.IO) {
                envConfigRepo.saveRawContent(safeEnvContent).getOrThrow()
            }
            val restoredFavorite = validatedFavorite?.let { snapshot ->
                withContext(Dispatchers.IO) {
                    FavoriteCacheStore.writeCurrent(context, snapshot.content).getOrThrow()
                }
            }
            applyRuntimeFromEnv(safeEnvContent)
            val wasRunning = runtimeState.value.status == ServiceStatus.Running
            if (wasRunning) runtimeRepo.restartService()
            val favoriteSummary = restoredFavorite?.let { "，恢复 ${it.count} 项收藏" }
                ?: "，云端无收藏备份，已保留本地收藏"
            operationMessage = if (wasRunning) {
                "WebDAV 恢复成功$favoriteSummary，服务正在重启"
            } else {
                "WebDAV 恢复成功$favoriteSummary"
            }
            runtimeRepo.addLog(LogLevel.Info, "已从 WebDAV 恢复配置与收藏")
        } catch (error: Throwable) {
            val rollbackErrors = mutableListOf<Throwable>()
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching { envConfigRepo.saveRawContent(previousEnv).getOrThrow() }
                    .exceptionOrNull()?.let(rollbackErrors::add)
                if (previousFavorite != null) {
                    runCatching {
                        FavoriteCacheStore.writeCurrent(context, previousFavorite.content).getOrThrow()
                    }.exceptionOrNull()?.let(rollbackErrors::add)
                }
            }
            rollbackErrors.forEach(error::addSuppressed)
            throw error
        }
    }

    fun syncConfigToTv(inviteText: String) {
        if (isTvSyncOperating) return
        viewModelScope.launch {
            val target = TvConfigSyncCodec.parseTarget(inviteText).getOrElse {
                operationMessage = it.message ?: "未识别到电视同步码"
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
                    operationMessage = it
                    runtimeRepo.addLog(LogLevel.Info, "已通过扫码同步配置到电视端")
                },
                onFailure = {
                    operationMessage = "同步失败：${it.message ?: "请检查局域网与同步码"}"
                }
            )
            isTvSyncOperating = false
            tvSyncOperatingText = ""
        }
    }

    fun dismissMessage() {
        operationMessage = null
    }

    fun postMessage(message: String) {
        operationMessage = message
    }

    private fun applyWorkDirInternal(
        targetPath: String?,
        switchMode: RuntimePaths.WorkDirSwitchMode
    ) {
        if (isApplyingWorkDir) return
        viewModelScope.launch {
            isApplyingWorkDir = true
            try {
                val result = coreRepo.applyWorkDirectoryChange(
                    targetPath = targetPath,
                    migrateSelectedCore = switchMode == RuntimePaths.WorkDirSwitchMode.MigrateSelectedCore
                ).fold(
                    onSuccess = { RuntimePaths.ApplyResult(ok = true, message = it) },
                    onFailure = {
                        RuntimePaths.ApplyResult(
                            ok = false,
                            message = it.message ?: "切换工作目录失败"
                        )
                    }
                )
                if (!result.ok) {
                    operationMessage = result.message
                    return@launch
                }
                val previousVariant = runtimeState.value.variant
                var resolvedVariant: ApiVariant? = null
                val storageHint = if (NormalModeRuntimeProfiles.current(context).slowStorageWorkDir) {
                    "共享存储目录启动会更慢，低端机建议优先使用默认目录"
                } else {
                    null
                }
                withContext(Dispatchers.IO) {
                    runCatching {
                        val projectDir = RuntimePaths.normalProjectDir(context)
                        resolvedVariant = syncRuntimeVariantFromEnv(projectDir)
                        if (NodeProjectManager.hasProjectEntry(projectDir)) {
                            NodeProjectManager.writeRuntimeEnv(context, projectDir)
                        }
                    }
                }
                coreRepo.refreshCoreInfo()
                envConfigRepo.reload()
                refreshWorkDirInfo()
                val selectedVariant = resolvedVariant
                val dependencyCheck = runCatching {
                    if (selectedVariant != null) {
                        coreRepo.prepareInstalledCoreDependencyRepair(selectedVariant)
                    } else {
                        null
                    }
                }
                val dependencyRepair = dependencyCheck.getOrNull()
                val dependencyCheckError = dependencyCheck.exceptionOrNull()
                val variantMessage = when {
                    selectedVariant == null -> "当前目录未检测到可用核心，请先下载核心"
                    selectedVariant != previousVariant -> "已自动切换核心为 ${selectedVariant.label}"
                    else -> null
                }
                if (dependencyCheckError != null) {
                    runtimeRepo.addLog(
                        LogLevel.Error,
                        "工作目录依赖检测失败：${dependencyCheckError.message ?: "未知错误"}"
                    )
                    operationMessage = buildString {
                        append(result.message)
                        append("，依赖检测失败，已跳过自动重启：")
                        append(dependencyCheckError.message ?: "未知错误")
                    }
                } else if (dependencyRepair != null) {
                    runtimeRepo.addLog(
                        LogLevel.Warn,
                        "工作目录已切换，但 ${selectedVariant?.label ?: "当前核心"} 缺少运行时依赖，等待修复"
                    )
                    operationMessage = buildString {
                        append(result.message)
                        if (!variantMessage.isNullOrBlank()) {
                            append("，")
                            append(variantMessage)
                        }
                        append("，检测到缺失依赖，请点击修复依赖")
                        if (!storageHint.isNullOrBlank()) {
                            append("。")
                            append(storageHint)
                        }
                    }
                } else if (runtimeState.value.status == ServiceStatus.Running && selectedVariant != null) {
                    if (selectedVariant != previousVariant) {
                        runtimeRepo.addLog(LogLevel.Info, "已根据新目录自动切换核心到 ${selectedVariant.label}")
                    }
                    runtimeRepo.addLog(LogLevel.Info, "工作目录已变更，正在重启服务应用新目录")
                    runtimeRepo.restartService()
                    operationMessage = buildString {
                        append(result.message)
                        if (!variantMessage.isNullOrBlank()) {
                            append("，")
                            append(variantMessage)
                        }
                        append("，服务正在重启，请稍候")
                        if (!storageHint.isNullOrBlank()) {
                            append("。")
                            append(storageHint)
                        }
                    }
                } else {
                    if (runtimeState.value.status == ServiceStatus.Running && selectedVariant == null) {
                        runtimeRepo.addLog(LogLevel.Warn, "工作目录已切换，但新目录没有可用核心，正在停止旧目录服务")
                        runtimeRepo.stopService()
                    }
                    operationMessage = buildString {
                        append(result.message)
                        if (!variantMessage.isNullOrBlank()) {
                            append("，")
                            append(variantMessage)
                        }
                        if (!storageHint.isNullOrBlank()) {
                            append("。")
                            append(storageHint)
                        }
                    }
                }
            } finally {
                isApplyingWorkDir = false
            }
        }
    }

    private fun syncRuntimeVariantFromEnv(projectDir: java.io.File): ApiVariant? {
        val installedVariants = ApiVariant.entries.filter { variant ->
            NodeProjectManager.hasValidCore(java.io.File(projectDir, "danmu_api_${variant.key}"))
        }
        if (installedVariants.isEmpty()) return null

        val envFile = java.io.File(projectDir, "config/.env")
        val preferredVariant = if (envFile.exists() && envFile.isFile) {
            val text = runCatching { envFile.readText(Charsets.UTF_8) }.getOrDefault("")
            val env = parseEnvContentMap(text)
            val rawVariant = env["DANMU_API_VARIANT"]?.trim()?.lowercase().orEmpty()
            ApiVariant.entries.firstOrNull { it.key == rawVariant }
        } else {
            null
        }

        val currentVariant = runtimeState.value.variant
        val resolvedVariant = when {
            preferredVariant != null && installedVariants.contains(preferredVariant) -> preferredVariant
            installedVariants.contains(currentVariant) -> currentVariant
            installedVariants.contains(ApiVariant.Stable) -> ApiVariant.Stable
            else -> installedVariants.first()
        }

        runtimeRepo.updateVariant(resolvedVariant)
        return resolvedVariant
    }


    private fun applyRuntimeFromEnv(content: String) {
        val env = parseEnvContentMap(content)

        val current = runtimeState.value
        val port = env["DANMU_API_PORT"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: current.port
        val listenMode = RuntimeListenMode.fromBindHost(env[RuntimeListenMode.ENV_KEY])
            ?: current.listenMode
        runtimeRepo.applyServiceConfig(
            port = port,
            token = RuntimeTokenNormalizer.normalizeInput(env["TOKEN"]),
            restartIfRunning = false,
            listenMode = listenMode
        )
        env["DANMU_API_VARIANT"]?.lowercase()?.let { raw ->
            ApiVariant.entries.firstOrNull { it.key == raw }?.let { runtimeRepo.updateVariant(it) }
        }
        settingsRepo.setFileLogEnabled(false)
    }



    private fun loadWorkDirInfoSafe(): RuntimePaths.WorkDirInfo {
        return runCatching { RuntimePaths.buildWorkDirInfo(context) }
            .getOrElse {
                defaultWorkDirInfo()
            }
    }

    private fun defaultWorkDirInfo(): RuntimePaths.WorkDirInfo {
        val runMode = runtimeState.value.runMode
        val defaultBase = RuntimePaths.defaultBaseDir(context)
        val rootBase = RuntimePaths.rootBaseDir(context)
        val normalBase = defaultBase
        return RuntimePaths.WorkDirInfo(
            runMode = runMode,
            currentBaseDir = if (runMode != RunMode.Normal) rootBase else normalBase,
            normalBaseDir = normalBase,
            defaultBaseDir = defaultBase,
            customBaseDir = null,
            rootBaseDir = rootBase,
            isCustomEnabled = false
        )
    }
}
