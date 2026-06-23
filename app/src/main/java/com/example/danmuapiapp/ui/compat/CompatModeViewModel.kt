package com.example.danmuapiapp.ui.compat

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.data.service.AppUpdateService
import com.example.danmuapiapp.data.service.NodeKeepAlivePrefs
import com.example.danmuapiapp.data.service.SystemHeartbeatScheduler
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreDownloadProgress
import com.example.danmuapiapp.domain.model.CoreInfo
import com.example.danmuapiapp.domain.model.CoreVariantDisplayNames
import com.example.danmuapiapp.domain.model.KeepAliveHeartbeatMode
import com.example.danmuapiapp.domain.model.NightModePreference
import com.example.danmuapiapp.domain.model.ResolvedCustomCoreSource
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.model.formatCoreVersionValue
import com.example.danmuapiapp.domain.model.resolveCustomCoreSource
import com.example.danmuapiapp.ui.screen.push.PushLanScanner
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CompatModeUiState(
    val runtimeState: RuntimeState = RuntimeState(),
    val coreInfos: List<CoreInfo> = emptyList(),
    val downloadProgress: CoreDownloadProgress = CoreDownloadProgress(),
    val isCoreInfoLoading: Boolean = true,
    val isOperating: Boolean = false,
    val operationProgressTitle: String = "",
    val keepAlive: CompatKeepAliveUiState = CompatKeepAliveUiState(),
    val syncState: CompatTvConfigSyncServer.UiState = CompatTvConfigSyncServer.UiState(),
    val appUpdate: CompatAppUpdateUiState = CompatAppUpdateUiState(),
    val coreDisplayNames: CoreVariantDisplayNames = CoreVariantDisplayNames(),
    val customCoreSource: ResolvedCustomCoreSource = ResolvedCustomCoreSource(),
    val customRepo: String = "",
    val customRepoBranch: String = "",
    val nightMode: NightModePreference = NightModePreference.FollowSystem
)

data class CompatKeepAliveUiState(
    val summary: String = "正在读取后台运行状态",
    val detail: String = "",
    val actionLabel: String = "启用推荐方案",
    val actionEnabled: Boolean = true,
    val recommendedProfileEnabled: Boolean = false,
    val isRootMode: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val desiredRunning: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val heartbeatModeLabel: String = ""
)

data class CompatAppUpdateUiState(
    val currentVersion: String = "未知",
    val checkResult: AppUpdateService.CheckResult? = null,
    val isChecking: Boolean = false,
    val checkError: String = "",
    val isDownloading: Boolean = false,
    val downloadPercent: Int = -1,
    val downloadDetail: String = "",
    val downloadedApk: AppUpdateService.DownloadedApk? = null
)

class CompatModeViewModel(
    context: Context
) : ViewModel() {

    private val appContext = context.applicationContext
    private val graph = CompatRuntimeGraph.get(appContext)
    private val syncServer = CompatTvConfigSyncServer(
        envConfigRepository = graph.envConfigRepository,
        runtimeRepository = graph.runtimeRepository,
        settingsRepository = graph.settingsRepository,
        coreRepository = graph.coreRepository,
        githubProxyService = graph.githubProxyService
    )

    private val _uiState = MutableStateFlow(
        CompatModeUiState(
            runtimeState = graph.runtimeRepository.runtimeState.value,
            coreInfos = graph.coreRepository.coreInfoList.value,
            downloadProgress = graph.coreRepository.downloadProgress.value,
            isCoreInfoLoading = graph.coreRepository.isCoreInfoLoading.value,
            keepAlive = buildKeepAliveUiState(
                runtimeState = graph.runtimeRepository.runtimeState.value,
                isOperating = false
            ),
            appUpdate = CompatAppUpdateUiState(
                currentVersion = graph.appUpdateService.currentVersionName()
            ),
            coreDisplayNames = graph.settingsRepository.coreDisplayNames.value,
            customCoreSource = graph.settingsRepository.customCoreSource.value,
            customRepo = graph.settingsRepository.customRepo.value,
            customRepoBranch = graph.settingsRepository.customRepoBranch.value,
            nightMode = graph.settingsRepository.nightMode.value
        )
    )
    val uiState: StateFlow<CompatModeUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        observeState()
        val initialHost = resolveSyncHost(_uiState.value.runtimeState)
        syncServer.start(initialHost)
        graph.coreRepository.refreshCoreInfo()
        checkAppUpdate(showFreshToast = false)
    }

    fun onActivityResumed(activity: Activity) {
        graph.appUpdateService.tryResumePendingInstall(activity)
        refreshKeepAliveUi()
        viewModelScope.launch(Dispatchers.IO) {
            graph.coreRepository.checkAllUpdates()
        }
    }

    fun refreshCoreInfo() {
        graph.coreRepository.refreshCoreInfo()
        emitEvent("正在刷新核心信息")
    }

    fun startService() {
        if (_uiState.value.isOperating) return
        viewModelScope.launch {
            val runtimeState = _uiState.value.runtimeState
            val variant = runtimeState.variant
            val ready = withContext(Dispatchers.IO) {
                graph.coreRepository.isCoreReady(variant)
            }
            if (!ready) {
                val info = _uiState.value.coreInfos.find { it.variant == variant }
                emitEvent(
                    if (info?.sourceMismatch == true) {
                        "${resolveVariantLabel(variant)} 来源与设置不一致，请先重新下载核心"
                    } else {
                        "${resolveVariantLabel(variant)} 未安装，请先下载核心"
                    }
                )
                return@launch
            }
            graph.runtimeRepository.startService()
        }
    }

    fun restartService() {
        if (!_uiState.value.isOperating) {
            graph.runtimeRepository.restartService()
        }
    }

    fun stopService() {
        if (!_uiState.value.isOperating) {
            graph.runtimeRepository.stopService()
        }
    }

    fun toggleKeepAliveProfile() {
        if (_uiState.value.isOperating) return
        val runtimeState = _uiState.value.runtimeState
        if (runtimeState.runMode == RunMode.Root) {
            emitEvent("当前是 Root 模式，请使用 Root 开机自启")
            return
        }

        val recommendedProfileEnabled = graph.settingsRepository.autoStart.value &&
            graph.settingsRepository.keepAlive.value &&
            graph.settingsRepository.keepAliveHeartbeatEnabled.value &&
            graph.settingsRepository.keepAliveHeartbeatMode.value == KeepAliveHeartbeatMode.System &&
            NodeKeepAlivePrefs.getEffectiveSystemHeartbeatIntervalMinutes(appContext) ==
            NodeKeepAlivePrefs.HEARTBEAT_INTERVAL_SYSTEM_MIN_MINUTES

        if (recommendedProfileEnabled) {
            graph.settingsRepository.setAutoStart(false)
            graph.settingsRepository.setKeepAliveHeartbeatMode(KeepAliveHeartbeatMode.Accessibility)
            graph.settingsRepository.setKeepAliveHeartbeatEnabled(false)
            graph.settingsRepository.setKeepAlive(false)
            NodeKeepAlivePrefs.requestDisableAccessibilityService(appContext)
            SystemHeartbeatScheduler.refresh(appContext)
            refreshKeepAliveUi()
            emitEvent("已关闭 TV 实验保活")
            return
        }

        graph.settingsRepository.setAutoStart(true)
        graph.settingsRepository.setKeepAlive(true)
        graph.settingsRepository.setKeepAliveHeartbeatEnabled(true)
        graph.settingsRepository.setKeepAliveHeartbeatMode(KeepAliveHeartbeatMode.System)
        graph.settingsRepository.setKeepAliveHeartbeatIntervalMinutes(
            NodeKeepAlivePrefs.HEARTBEAT_INTERVAL_SYSTEM_MIN_MINUTES
        )
        SystemHeartbeatScheduler.refresh(appContext)
        refreshKeepAliveUi()

        val message = if (!NodeKeepAlivePrefs.isDesiredRunning(appContext)) {
            "已启用 TV 实验保活，请再手动启动一次服务"
        } else if (!NodeKeepAlivePrefs.hasPostNotificationsPermission(appContext)) {
            "已启用 TV 实验保活，请授予通知权限"
        } else {
            "已启用 TV 实验保活"
        }
        emitEvent(message)
    }

    fun switchVariant(variant: ApiVariant) {
        if (_uiState.value.isOperating) return
        val info = _uiState.value.coreInfos.find { it.variant == variant }
        if (info?.isInstalled != true) {
            emitEvent("${resolveVariantLabel(variant)} 未安装，请先下载核心")
            return
        }
        if (!info.isReady) {
            emitEvent("${resolveVariantLabel(variant)} 来源与设置不一致，请先重新下载核心")
            return
        }
        val wasRunning = _uiState.value.runtimeState.status == ServiceStatus.Running
        graph.runtimeRepository.updateVariant(variant)
        if (wasRunning) {
            graph.runtimeRepository.restartService()
            emitEvent("已切换到 ${resolveVariantLabel(variant)}，正在重启服务")
        } else {
            emitEvent("已切换到 ${resolveVariantLabel(variant)}")
        }
        graph.coreRepository.refreshCoreInfo()
    }

    fun installCore(variant: ApiVariant) {
        if (!canOperateVariant(variant)) return
        performCoreOperation("正在下载 ${resolveVariantLabel(variant)}") {
            graph.coreRepository.installCore(variant).fold(
                onSuccess = {
                    graph.coreRepository.refreshCoreInfo()
                    val state = _uiState.value.runtimeState
                    if (state.variant == variant && state.status == ServiceStatus.Running) {
                        graph.runtimeRepository.restartService()
                        emitEvent("${resolveVariantLabel(variant)} 下载完成，正在重启服务")
                    } else {
                        emitEvent("${resolveVariantLabel(variant)} 下载完成")
                    }
                },
                onFailure = {
                    emitEvent("${resolveVariantLabel(variant)} 下载失败：${it.message ?: "未知错误"}")
                }
            )
        }
    }

    fun updateCore(variant: ApiVariant) {
        if (!canOperateVariant(variant)) return
        performCoreOperation("正在更新 ${resolveVariantLabel(variant)}") {
            graph.coreRepository.updateCore(variant).fold(
                onSuccess = {
                    graph.coreRepository.refreshCoreInfo()
                    val state = _uiState.value.runtimeState
                    if (state.variant == variant && state.status == ServiceStatus.Running) {
                        graph.runtimeRepository.restartService()
                        emitEvent("${resolveVariantLabel(variant)} 更新完成，正在重启服务")
                    } else {
                        emitEvent("${resolveVariantLabel(variant)} 更新完成")
                    }
                },
                onFailure = {
                    emitEvent("${resolveVariantLabel(variant)} 更新失败：${it.message ?: "未知错误"}")
                }
            )
        }
    }

    fun checkCoreUpdate(variant: ApiVariant) {
        if (!canOperateVariant(variant)) return
        performCoreOperation("正在检查 ${resolveVariantLabel(variant)} 更新") {
            runCatching {
                graph.coreRepository.checkAndMarkUpdate(variant)
                val refreshed = graph.coreRepository.coreInfoList.value.find { it.variant == variant }
                when {
                    refreshed?.sourceMismatch == true -> {
                        emitEvent("${resolveVariantLabel(variant)} 需替换为 ${refreshed.desiredSource ?: "目标仓库"}")
                    }
                    refreshed?.hasVersionUpdate == true && !refreshed.availableVersion.isNullOrBlank() -> {
                        emitEvent(
                            "${resolveVariantLabel(variant)} 有新版本 ${
                                formatCoreVersionValue(refreshed.availableVersion)
                            }"
                        )
                    }
                    else -> emitEvent("${resolveVariantLabel(variant)} 已是最新版本")
                }
            }.onFailure {
                emitEvent("${resolveVariantLabel(variant)} 检查更新失败：${it.message ?: "未知错误"}")
            }
        }
    }

    fun deleteCore(variant: ApiVariant) {
        if (_uiState.value.isOperating) return
        if (_uiState.value.runtimeState.variant == variant) {
            emitEvent("当前正在使用此核心，请先切换到其他核心再删除")
            return
        }
        performCoreOperation("正在删除 ${resolveVariantLabel(variant)}") {
            graph.coreRepository.deleteCore(variant).fold(
                onSuccess = {
                    graph.coreRepository.refreshCoreInfo()
                    emitEvent("${resolveVariantLabel(variant)} 已删除")
                },
                onFailure = {
                    emitEvent("${resolveVariantLabel(variant)} 删除失败：${it.message ?: "未知错误"}")
                }
            )
        }
    }

    fun saveCustomCore(repo: String, branch: String) {
        val source = graph.settingsRepository.saveCustomCoreSource(
            repoInput = repo,
            branchInput = branch
        )
        graph.coreRepository.refreshCoreInfo()
        _uiState.update {
            it.copy(
                customCoreSource = source,
                customRepo = graph.settingsRepository.customRepo.value,
                customRepoBranch = graph.settingsRepository.customRepoBranch.value
            )
        }
        emitEvent(
            when {
                repo.isBlank() -> "已清除自定义仓库"
                source.sourceText.isNotBlank() -> "已保存自定义核心：${source.sourceText}"
                else -> "自定义仓库格式无效，请检查后重试"
            }
        )
    }

    fun checkAppUpdate(showFreshToast: Boolean = true) {
        if (_uiState.value.appUpdate.isChecking) return
        _uiState.update {
            it.copy(
                appUpdate = it.appUpdate.copy(
                    isChecking = true,
                    checkError = ""
                )
            )
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                graph.appUpdateService.checkLatestRelease()
            }
            result.fold(
                onSuccess = { info ->
                    _uiState.update {
                        it.copy(
                            appUpdate = it.appUpdate.copy(
                                currentVersion = info.currentVersion,
                                checkResult = info,
                                isChecking = false,
                                checkError = ""
                            )
                        )
                    }
                    if (showFreshToast && !info.hasUpdate) {
                        emitEvent("当前已是最新版本")
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            appUpdate = it.appUpdate.copy(
                                isChecking = false,
                                checkError = error.message ?: "检查失败"
                            )
                        )
                    }
                    if (showFreshToast) {
                        emitEvent("检查 App 更新失败：${error.message ?: "未知错误"}")
                    }
                }
            )
        }
    }

    fun downloadAppUpdate() {
        val result = _uiState.value.appUpdate.checkResult ?: return
        if (_uiState.value.appUpdate.isDownloading) return
        if (result.downloadUrls.isEmpty()) {
            emitEvent("未找到适合当前设备架构的安装包")
            return
        }
        _uiState.update {
            it.copy(
                appUpdate = it.appUpdate.copy(
                    isDownloading = true,
                    downloadPercent = -1,
                    downloadDetail = "准备下载...",
                    downloadedApk = null
                )
            )
        }

        viewModelScope.launch {
            val downloadResult = withContext(Dispatchers.IO) {
                val downloadUrls = graph.appUpdateService
                    .buildDownloadUrls(result.bestAsset)
                    .ifEmpty { result.downloadUrls }
                graph.appUpdateService.downloadApk(
                    urls = downloadUrls,
                    version = result.latestVersion
                ) { soFar, total ->
                    _uiState.update { state ->
                        val detail = if (total > 0) {
                            "${formatBytes(soFar)} / ${formatBytes(total)}"
                        } else {
                            "已下载 ${formatBytes(soFar)}"
                        }
                        state.copy(
                            appUpdate = state.appUpdate.copy(
                                downloadPercent = if (total > 0) {
                                    ((soFar * 100f) / total).toInt().coerceIn(0, 100)
                                } else {
                                    -1
                                },
                                downloadDetail = detail
                            )
                        )
                    }
                }
            }
            downloadResult.fold(
                onSuccess = { apk ->
                    _uiState.update {
                        it.copy(
                            appUpdate = it.appUpdate.copy(
                                isDownloading = false,
                                downloadedApk = apk
                            )
                        )
                    }
                    emitEvent("下载完成：${apk.displayName}")
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            appUpdate = it.appUpdate.copy(isDownloading = false)
                        )
                    }
                    emitEvent("下载失败：${error.message ?: "请稍后重试"}")
                }
            )
        }
    }

    fun installAppUpdate(activity: Activity) {
        val apk = _uiState.value.appUpdate.downloadedApk ?: return
        when (val result = graph.appUpdateService.installApk(activity, apk)) {
            is AppUpdateService.InstallResult.Launched -> {
                emitEvent("已打开安装器，请按系统提示完成安装")
            }
            is AppUpdateService.InstallResult.NeedUnknownSourcePermission -> {
                emitEvent("请完成「安装未知应用」授权，返回后将自动续装")
            }
            is AppUpdateService.InstallResult.Failed -> {
                emitEvent(result.message)
            }
        }
    }

    fun toggleNightMode() {
        val current = graph.settingsRepository.nightMode.value
        val next = when (current) {
            NightModePreference.Dark -> NightModePreference.Light
            NightModePreference.Light -> NightModePreference.Dark
            NightModePreference.FollowSystem -> NightModePreference.Dark
        }
        graph.settingsRepository.setNightMode(next)
    }

    override fun onCleared() {
        syncServer.stop()
        super.onCleared()
    }

    private fun observeState() {
        viewModelScope.launch {
            graph.runtimeRepository.runtimeState.collectLatest { state ->
                syncServer.updateHost(resolveSyncHost(state))
                _uiState.update {
                    it.copy(
                        runtimeState = state,
                        keepAlive = buildKeepAliveUiState(
                            runtimeState = state,
                            isOperating = it.isOperating
                        )
                    )
                }
            }
        }
        viewModelScope.launch {
            graph.coreRepository.coreInfoList.collectLatest { coreInfos ->
                _uiState.update { it.copy(coreInfos = coreInfos) }
            }
        }
        viewModelScope.launch {
            graph.coreRepository.downloadProgress.collectLatest { progress ->
                _uiState.update { it.copy(downloadProgress = progress) }
            }
        }
        viewModelScope.launch {
            graph.coreRepository.isCoreInfoLoading.collectLatest { loading ->
                _uiState.update { it.copy(isCoreInfoLoading = loading) }
            }
        }
        viewModelScope.launch {
            graph.settingsRepository.coreDisplayNames.collectLatest { names ->
                _uiState.update { it.copy(coreDisplayNames = names) }
            }
        }
        viewModelScope.launch {
            graph.settingsRepository.customCoreSource.collectLatest { source ->
                _uiState.update { it.copy(customCoreSource = source) }
            }
        }
        viewModelScope.launch {
            graph.settingsRepository.customRepo.collectLatest { repo ->
                _uiState.update { it.copy(customRepo = repo) }
            }
        }
        viewModelScope.launch {
            graph.settingsRepository.customRepoBranch.collectLatest { branch ->
                _uiState.update { it.copy(customRepoBranch = branch) }
            }
        }
        viewModelScope.launch {
            graph.settingsRepository.nightMode.collectLatest { mode ->
                _uiState.update { it.copy(nightMode = mode) }
            }
        }
        viewModelScope.launch {
            syncServer.uiState.collectLatest { syncState ->
                _uiState.update { it.copy(syncState = syncState) }
            }
        }

        val keepAliveFlows = listOf(
            graph.settingsRepository.autoStart,
            graph.settingsRepository.keepAlive,
            graph.settingsRepository.keepAliveHeartbeatEnabled,
            graph.settingsRepository.keepAliveHeartbeatMode,
            graph.settingsRepository.keepAliveHeartbeatIntervalMinutes
        )
        keepAliveFlows.forEach { flow ->
            viewModelScope.launch {
                flow.collectLatest {
                    refreshKeepAliveUi()
                }
            }
        }
    }

    private fun performCoreOperation(title: String, block: suspend () -> Unit) {
        if (_uiState.value.isOperating) return
        _uiState.update {
            it.copy(
                isOperating = true,
                operationProgressTitle = title,
                keepAlive = buildKeepAliveUiState(
                    runtimeState = it.runtimeState,
                    isOperating = true
                )
            )
        }
        viewModelScope.launch {
            try {
                block()
            } finally {
                _uiState.update {
                    it.copy(
                        isOperating = false,
                        operationProgressTitle = "",
                        keepAlive = buildKeepAliveUiState(
                            runtimeState = it.runtimeState,
                            isOperating = false
                        )
                    )
                }
            }
        }
    }

    private fun canOperateVariant(variant: ApiVariant): Boolean {
        if (variant != ApiVariant.Custom) return true
        val source = resolveCustomCoreSource(
            repoInput = graph.settingsRepository.customRepo.value,
            branchInput = graph.settingsRepository.customRepoBranch.value
        )
        if (source.isValidRepo) return true
        emitEvent(
            if (source.isConfigured) {
                "${resolveVariantLabel(ApiVariant.Custom)} 仓库格式无效，请检查后重试"
            } else {
                "${resolveVariantLabel(ApiVariant.Custom)} 未配置仓库，请先输入仓库地址"
            }
        )
        return false
    }

    private fun refreshKeepAliveUi() {
        _uiState.update {
            it.copy(
                keepAlive = buildKeepAliveUiState(
                    runtimeState = it.runtimeState,
                    isOperating = it.isOperating
                )
            )
        }
    }

    private fun buildKeepAliveUiState(
        runtimeState: RuntimeState,
        isOperating: Boolean
    ): CompatKeepAliveUiState {
        val autoStartEnabled = graph.settingsRepository.autoStart.value
        val keepAliveEnabled = graph.settingsRepository.keepAlive.value
        val heartbeatEnabled = graph.settingsRepository.keepAliveHeartbeatEnabled.value
        val heartbeatMode = graph.settingsRepository.keepAliveHeartbeatMode.value
        val heartbeatIntervalMinutes = if (heartbeatMode == KeepAliveHeartbeatMode.System) {
            NodeKeepAlivePrefs.getEffectiveSystemHeartbeatIntervalMinutes(appContext)
        } else {
            graph.settingsRepository.keepAliveHeartbeatIntervalMinutes.value
        }
        val desiredRunning = NodeKeepAlivePrefs.isDesiredRunning(appContext)
        val hasNotificationPermission = NodeKeepAlivePrefs.hasPostNotificationsPermission(appContext)
        val accessibilityEnabled = NodeKeepAlivePrefs.isAccessibilityServiceEnabled(appContext)
        val isRootMode = runtimeState.runMode == RunMode.Root
        val recommendedProfileEnabled = !isRootMode &&
            autoStartEnabled &&
            keepAliveEnabled &&
            heartbeatEnabled &&
            heartbeatMode == KeepAliveHeartbeatMode.System &&
            heartbeatIntervalMinutes == NodeKeepAlivePrefs.HEARTBEAT_INTERVAL_SYSTEM_MIN_MINUTES
        val hasPartialConfig = autoStartEnabled ||
            keepAliveEnabled ||
            heartbeatEnabled ||
            heartbeatMode == KeepAliveHeartbeatMode.System

        val summary = when {
            isRootMode -> "Root 模式优先使用 Root 开机自启"
            recommendedProfileEnabled ->
                "已启用后台恢复：系统心跳约 ${heartbeatIntervalMinutes} 分钟兜底一次"
            hasPartialConfig -> "后台恢复配置不完整，建议重新应用 TV 推荐方案"
            else -> "未启用后台恢复，服务被系统回收后不会自动恢复"
        }

        val detail = buildString {
            append("该能力用于掉线后兜底恢复，不承诺让 TV 后台长期常驻。")
            when {
                isRootMode -> {
                    append("\n当前是 Root 模式，稳定保活应使用完整设置页里的 Root 开机模块。")
                }
                recommendedProfileEnabled -> {
                    append("\n已配置：普通模式开机恢复、系统定时心跳、${heartbeatIntervalMinutes} 分钟恢复间隔。")
                    append("\n运行期间会保持 CPU 唤醒，降低部分盒子待机后服务被打断的概率。")
                    if (!desiredRunning) {
                        append("\n启用后请至少手动启动一次服务，系统才会按“期望运行”继续恢复。")
                    }
                    if (!hasNotificationPermission) {
                        append("\n当前缺少通知权限，系统恢复拉起前台服务可能失败。")
                    }
                    if (Build.VERSION.SDK_INT >= 35) {
                        append("\nAndroid 15 及以上限制开机直接拉起前台服务，仍以系统心跳兜底为主。")
                    }
                    if (accessibilityEnabled) {
                        append("\n检测到无障碍保活已启用，支持的设备上它也会一起参与恢复。")
                    }
                }
                else -> {
                    append("\n开启后会自动配置：普通模式开机恢复、系统定时心跳、15 分钟恢复间隔。")
                    if (hasPartialConfig) {
                        append(
                            "\n当前状态：开机恢复${if (autoStartEnabled) "开" else "关"}，" +
                                "保活${if (keepAliveEnabled) "开" else "关"}，" +
                                "心跳${if (heartbeatEnabled) "开" else "关"}，" +
                                "模式${heartbeatMode.label}。"
                        )
                    }
                    if (Build.VERSION.SDK_INT >= 35) {
                        append("\n新系统上开机恢复可能被限制，因此不要把它当成常驻保活。")
                    }
                }
            }
        }

        return CompatKeepAliveUiState(
            summary = summary,
            detail = detail,
            actionLabel = when {
                isRootMode -> "Root 模式无需此项"
                recommendedProfileEnabled -> "关闭后台恢复"
                hasPartialConfig -> "重新应用推荐方案"
                else -> "启用推荐方案"
            },
            actionEnabled = !isOperating && !isRootMode,
            recommendedProfileEnabled = recommendedProfileEnabled,
            isRootMode = isRootMode,
            hasNotificationPermission = hasNotificationPermission,
            desiredRunning = desiredRunning,
            accessibilityEnabled = accessibilityEnabled,
            heartbeatModeLabel = heartbeatMode.label
        )
    }

    private fun resolveVariantLabel(variant: ApiVariant): String {
        return graph.settingsRepository.coreDisplayNames.value.resolve(variant)
    }

    private fun resolveSyncHost(state: RuntimeState): String {
        return PushLanScanner.resolveSelfLanIpv4(state.lanUrl).orEmpty()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(Locale.getDefault(), "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.getDefault(), "%.2f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.getDefault(), "%.2f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    private fun emitEvent(message: String) {
        _events.tryEmit(message)
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CompatModeViewModel::class.java)) {
                return CompatModeViewModel(context.applicationContext) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
