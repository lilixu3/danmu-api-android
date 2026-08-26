package com.example.danmuapiapp.ui.compat

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.data.service.AppUpdateService
import com.example.danmuapiapp.data.util.AppAppearancePrefs
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.AppBackgroundPreference
import com.example.danmuapiapp.domain.model.CoreDownloadProgress
import com.example.danmuapiapp.domain.model.CoreBranchCatalog
import com.example.danmuapiapp.domain.model.CoreBranchSelections
import com.example.danmuapiapp.domain.model.CoreInfo
import com.example.danmuapiapp.domain.model.CoreDependencyRepairRequiredException
import com.example.danmuapiapp.domain.model.CoreDependencyRepairRequest
import com.example.danmuapiapp.domain.model.CoreDependencyRepairOrigin
import com.example.danmuapiapp.domain.model.CoreVariantDisplayNames
import com.example.danmuapiapp.domain.model.GithubProxyOption
import com.example.danmuapiapp.domain.model.GlassMaterialPreference
import com.example.danmuapiapp.domain.model.GlassTuningPreference
import com.example.danmuapiapp.domain.model.NightModePreference
import com.example.danmuapiapp.domain.model.ResolvedCustomCoreSource
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.model.formatCoreVersionValue
import com.example.danmuapiapp.domain.model.resolveCustomCoreSource
import com.example.danmuapiapp.domain.model.normalizeGithubBranch
import com.example.danmuapiapp.ui.common.ProxyPickerController
import com.example.danmuapiapp.ui.common.CoreDependencyRepairController
import com.example.danmuapiapp.ui.common.continueAfterDependencyRepair
import com.example.danmuapiapp.ui.screen.push.PushLanScanner
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val syncState: CompatTvConfigSyncServer.UiState = CompatTvConfigSyncServer.UiState(),
    val appUpdate: CompatAppUpdateUiState = CompatAppUpdateUiState(),
    val coreDisplayNames: CoreVariantDisplayNames = CoreVariantDisplayNames(),
    val coreBranchSelections: CoreBranchSelections = CoreBranchSelections(),
    val customCoreSource: ResolvedCustomCoreSource = ResolvedCustomCoreSource(),
    val customRepo: String = "",
    val customRepoBranch: String = "",
    val nightMode: NightModePreference = NightModePreference.FollowSystem,
    val glassMaterial: GlassMaterialPreference = GlassMaterialPreference.Default,
    val glassTuning: GlassTuningPreference = GlassTuningPreference(),
    val appBackground: AppBackgroundPreference = AppBackgroundPreference(),
    val appDpiOverride: Int = AppAppearancePrefs.APP_DPI_SYSTEM,
    val pendingDependencyRepair: CoreDependencyRepairRequest? = null,
    val branchDialogVariant: ApiVariant? = null,
    val branchCatalog: CoreBranchCatalog? = null,
    val isLoadingBranches: Boolean = false,
    val branchLoadError: String? = null,
    val localNetworkGuideDismissedThisLaunch: Boolean = false
)

internal fun CompatModeUiState.dismissLocalNetworkGuideForThisLaunch(): CompatModeUiState {
    return copy(localNetworkGuideDismissedThisLaunch = true)
}

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
    val proxyOptions: List<GithubProxyOption> = graph.githubProxyService.proxyOptions()

    private val proxyPickerController = ProxyPickerController(
        githubProxyService = graph.githubProxyService,
        githubProxySpeedTester = graph.githubProxySpeedTester,
        scope = viewModelScope,
        proxyOptionsProvider = { proxyOptions }
    )
    private var pendingProxyAction: PendingProxyAction? = null
    private var branchLoadJob: Job? = null
    private var branchLoadGeneration = 0L

    val showProxyPickerDialog: Boolean
        get() = proxyPickerController.uiState.isVisible
    val proxySelectedId: String
        get() = proxyPickerController.uiState.selectedId
    val proxyTestingIds: Set<String>
        get() = proxyPickerController.uiState.testingIds
    val proxyLatencyMap: Map<String, Long>
        get() = proxyPickerController.uiState.latencyMap
    val showDependencyRequiredPrompt: Boolean
        get() = dependencyRepairController.showRequiredPrompt
    val showDependencyRepairDialog: Boolean
        get() = dependencyRepairController.showRepairDialog

    private sealed interface PendingProxyAction {
        data class Install(val variant: ApiVariant) : PendingProxyAction
        data class Update(val variant: ApiVariant) : PendingProxyAction
        data class CheckUpdate(val variant: ApiVariant) : PendingProxyAction
        data class SwitchBranch(val variant: ApiVariant, val branch: String) : PendingProxyAction
        data object RepairDependenciesOnline : PendingProxyAction
    }

    private val _uiState = MutableStateFlow(
        CompatModeUiState(
            runtimeState = graph.runtimeRepository.runtimeState.value,
            coreInfos = graph.coreRepository.coreInfoList.value,
            downloadProgress = graph.coreRepository.downloadProgress.value,
            isCoreInfoLoading = graph.coreRepository.isCoreInfoLoading.value,
            appUpdate = CompatAppUpdateUiState(
                currentVersion = graph.appUpdateService.currentVersionName()
            ),
            coreDisplayNames = graph.settingsRepository.coreDisplayNames.value,
            coreBranchSelections = graph.settingsRepository.coreBranchSelections.value,
            customCoreSource = graph.settingsRepository.customCoreSource.value,
            customRepo = graph.settingsRepository.customRepo.value,
            customRepoBranch = graph.settingsRepository.customRepoBranch.value,
            nightMode = graph.settingsRepository.nightMode.value,
            glassMaterial = graph.settingsRepository.glassMaterial.value,
            appBackground = graph.settingsRepository.appBackground.value,
            appDpiOverride = graph.settingsRepository.appDpiOverride.value,
            pendingDependencyRepair = graph.coreRepository.pendingDependencyRepair.value
        )
    )
    val uiState: StateFlow<CompatModeUiState> = _uiState.asStateFlow()

    private val dependencyRepairController = CoreDependencyRepairController(
        scope = viewModelScope,
        repository = graph.coreRepository,
        setOperating = { operating ->
            _uiState.update { state ->
                state.copy(
                    isOperating = operating,
                    operationProgressTitle = if (operating) "正在修复运行时依赖" else ""
                )
            }
        },
        postMessage = ::emitEvent,
        onApplied = ::onCompatDependenciesApplied,
        onDiscarded = { request ->
            if (request.origin == CoreDependencyRepairOrigin.WorkDirectory) {
                "已取消依赖修复，当前工作目录保持不变"
            } else {
                "已取消${resolveVariantLabel(request.variant)}${request.actionLabel}"
            }
        }
    )

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
        graph.runtimeRepository.setAppForeground(true)
        graph.appUpdateService.tryResumePendingInstall(activity)
        graph.updateChecker.onAppResume()
    }

    fun onActivityStopped() {
        graph.runtimeRepository.setAppForeground(false)
    }

    fun dismissLocalNetworkGuideForThisLaunch() {
        _uiState.update(CompatModeUiState::dismissLocalNetworkGuideForThisLaunch)
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

    fun openBranchDialog(variant: ApiVariant) {
        if (_uiState.value.isOperating) return
        if (_uiState.value.coreInfos.firstOrNull { it.variant == variant }?.isInstalled != true) {
            emitEvent("请先安装 ${resolveVariantLabel(variant)}")
            return
        }
        if (!canOperateVariant(variant)) return
        _uiState.update {
            it.copy(
                branchDialogVariant = variant,
                branchCatalog = null,
                branchLoadError = null
            )
        }
        loadBranches(variant)
    }

    fun retryLoadBranches() {
        _uiState.value.branchDialogVariant?.let(::loadBranches)
    }

    fun dismissBranchDialog() {
        branchLoadGeneration += 1
        branchLoadJob?.cancel()
        branchLoadJob = null
        _uiState.update {
            it.copy(
                branchDialogVariant = null,
                branchCatalog = null,
                isLoadingBranches = false,
                branchLoadError = null
            )
        }
    }

    private fun loadBranches(variant: ApiVariant) {
        branchLoadGeneration += 1
        val generation = branchLoadGeneration
        branchLoadJob?.cancel()
        _uiState.update { it.copy(isLoadingBranches = true, branchLoadError = null) }
        branchLoadJob = viewModelScope.launch {
            graph.coreRepository.fetchBranches(variant).fold(
                onSuccess = { catalog ->
                    if (generation != branchLoadGeneration) return@fold
                    _uiState.update { it.copy(branchCatalog = catalog) }
                },
                onFailure = { error ->
                    if (generation != branchLoadGeneration) return@fold
                    _uiState.update {
                        it.copy(branchLoadError = error.message ?: "无法读取仓库分支")
                    }
                }
            )
            if (generation == branchLoadGeneration) {
                _uiState.update { it.copy(isLoadingBranches = false) }
                branchLoadJob = null
            }
        }
    }

    fun switchCoreBranch(branch: String) {
        val variant = _uiState.value.branchDialogVariant ?: return
        val normalized = normalizeGithubBranch(branch)
        if (normalized.isBlank()) return
        val current = graph.settingsRepository.coreBranchSelections.value.resolve(variant)
        dismissBranchDialog()
        if (normalized.equals(current, ignoreCase = true)) {
            emitEvent("${resolveVariantLabel(variant)} 当前已使用 $normalized 分支")
            return
        }
        if (!graph.githubProxyService.hasUserSelectedProxy()) {
            pendingProxyAction = PendingProxyAction.SwitchBranch(variant, normalized)
            emitEvent("切换分支前，请先选择 GitHub 线路")
            proxyPickerController.open()
            return
        }
        doSwitchCoreBranch(variant, normalized)
    }

    private fun doSwitchCoreBranch(variant: ApiVariant, branch: String) {
        performCoreOperation("正在切换 ${resolveVariantLabel(variant)} 分支") {
            graph.coreRepository.switchCoreBranch(variant, branch).fold(
                onSuccess = {
                    graph.coreRepository.refreshCoreInfo()
                    val state = _uiState.value.runtimeState
                    if (state.variant == variant && state.status == ServiceStatus.Running) {
                        graph.runtimeRepository.restartService()
                        emitEvent("${resolveVariantLabel(variant)} 已切换到 $branch，正在重启服务")
                    } else {
                        emitEvent("${resolveVariantLabel(variant)} 已切换到 $branch 分支")
                    }
                },
                onFailure = { error ->
                    if (error is CoreDependencyRepairRequiredException) {
                        emitEvent("${resolveVariantLabel(variant)}切换已暂停，等待修复依赖")
                    } else {
                        emitEvent("切换分支失败：${error.message ?: "未知错误"}")
                    }
                    if (error !is CoreDependencyRepairRequiredException && graph.githubProxyService.isUsingProxy()) {
                        pendingProxyAction = PendingProxyAction.SwitchBranch(variant, branch)
                        proxyPickerController.open()
                    }
                }
            )
        }
    }

    fun installCore(variant: ApiVariant) {
        if (!canOperateVariant(variant)) return
        if (!graph.githubProxyService.hasUserSelectedProxy()) {
            pendingProxyAction = PendingProxyAction.Install(variant)
            emitEvent("首次下载前，请先选择 GitHub 线路")
            proxyPickerController.open()
            return
        }
        doInstallCore(variant)
    }

    private fun doInstallCore(variant: ApiVariant) {
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
                onFailure = { error ->
                    if (error is CoreDependencyRepairRequiredException) {
                        emitEvent("${resolveVariantLabel(variant)}安装已暂停，等待修复依赖")
                    } else {
                        emitEvent("${resolveVariantLabel(variant)} 下载失败：${error.message ?: "未知错误"}")
                    }
                    if (error !is CoreDependencyRepairRequiredException && graph.githubProxyService.isUsingProxy()) {
                        pendingProxyAction = PendingProxyAction.Install(variant)
                        proxyPickerController.open()
                    }
                }
            )
        }
    }

    fun updateCore(variant: ApiVariant) {
        if (!canOperateVariant(variant)) return
        if (!graph.githubProxyService.hasUserSelectedProxy()) {
            pendingProxyAction = PendingProxyAction.Update(variant)
            emitEvent("更新核心前，请先选择 GitHub 线路")
            proxyPickerController.open()
            return
        }
        doUpdateCore(variant)
    }

    private fun doUpdateCore(variant: ApiVariant) {
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
                onFailure = { error ->
                    if (error is CoreDependencyRepairRequiredException) {
                        emitEvent("${resolveVariantLabel(variant)}更新已暂停，等待修复依赖")
                    } else {
                        emitEvent("${resolveVariantLabel(variant)} 更新失败：${error.message ?: "未知错误"}")
                    }
                    if (error !is CoreDependencyRepairRequiredException && graph.githubProxyService.isUsingProxy()) {
                        pendingProxyAction = PendingProxyAction.Update(variant)
                        proxyPickerController.open()
                    }
                }
            )
        }
    }

    fun checkCoreUpdate(variant: ApiVariant) {
        if (!canOperateVariant(variant)) return
        if (!graph.githubProxyService.hasUserSelectedProxy()) {
            pendingProxyAction = PendingProxyAction.CheckUpdate(variant)
            emitEvent("检查更新前，请先选择 GitHub 线路")
            proxyPickerController.open()
            return
        }
        doCheckCoreUpdate(variant)
    }

    private fun doCheckCoreUpdate(variant: ApiVariant) {
        performCoreOperation("正在检查 ${resolveVariantLabel(variant)} 更新") {
            runCatching {
                graph.coreRepository.checkAndMarkUpdate(variant)
                val refreshed = graph.coreRepository.coreInfoList.value.find { it.variant == variant }
                when {
                    refreshed?.updateCheckError != null -> {
                        emitEvent("${resolveVariantLabel(variant)} 检查更新失败：${refreshed.updateCheckError}")
                    }
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
                if (graph.githubProxyService.isUsingProxy()) {
                    pendingProxyAction = PendingProxyAction.CheckUpdate(variant)
                    proxyPickerController.open()
                }
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

    fun currentProxyLabel(): String {
        return graph.githubProxyService.currentSelectedOption().name
    }

    fun openProxyPicker() {
        proxyPickerController.open()
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
        val action = pendingProxyAction
        proxyPickerController.confirm {
            pendingProxyAction = null
            when (action) {
                is PendingProxyAction.Install -> doInstallCore(action.variant)
                is PendingProxyAction.Update -> doUpdateCore(action.variant)
                is PendingProxyAction.CheckUpdate -> doCheckCoreUpdate(action.variant)
                is PendingProxyAction.SwitchBranch -> doSwitchCoreBranch(action.variant, action.branch)
                PendingProxyAction.RepairDependenciesOnline -> {
                    dependencyRepairController.repairOnlineNow()
                }
                null -> emitEvent("GitHub 线路已保存")
            }
        }
    }

    fun dismissDependencyRequiredPrompt() = dependencyRepairController.dismissRequiredPrompt()

    fun openDependencyRepairDialog() = dependencyRepairController.openRepairDialog()

    fun dismissDependencyRepairDialog() = dependencyRepairController.dismissRepairDialog()

    fun repairPendingDependenciesOnline() {
        if (graph.githubProxyService.hasUserSelectedProxy()) {
            dependencyRepairController.repairOnlineNow()
        } else {
            pendingProxyAction = PendingProxyAction.RepairDependenciesOnline
            emitEvent("在线修复前，请先选择 GitHub 线路")
            proxyPickerController.open()
        }
    }

    fun repairPendingDependenciesFromArchive(archiveUri: String) {
        dependencyRepairController.repairFromArchive(archiveUri)
    }

    fun discardPendingCoreMutation() = dependencyRepairController.discardPendingMutation()

    private suspend fun onCompatDependenciesApplied(
        request: CoreDependencyRepairRequest
    ): String {
        graph.coreRepository.refreshCoreInfo()
        val state = _uiState.value.runtimeState
        val continuation = graph.runtimeRepository.continueAfterDependencyRepair(request)
        return if (continuation != null) {
            "${resolveVariantLabel(request.variant)}依赖已修复，$continuation"
        } else if (state.variant == request.variant && state.status == ServiceStatus.Running) {
            graph.runtimeRepository.restartService()
            "${resolveVariantLabel(request.variant)}${request.actionLabel}成功，正在重启服务"
        } else {
            "${resolveVariantLabel(request.variant)}${request.actionLabel}成功"
        }
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

    fun setAppDpiOverride(activity: Activity?, dpi: Int) {
        val normalized = AppAppearancePrefs.normalizeAppDpiOverride(dpi)
        if (normalized == _uiState.value.appDpiOverride) {
            emitEvent(
                if (normalized == AppAppearancePrefs.APP_DPI_SYSTEM) {
                    "当前已是跟随系统 DPI"
                } else {
                    "当前已是 ${normalized} DPI"
                }
            )
            return
        }
        graph.settingsRepository.setAppDpiOverride(normalized)
        emitEvent(
            if (normalized == AppAppearancePrefs.APP_DPI_SYSTEM) {
                "已恢复跟随系统 DPI"
            } else {
                "已应用 ${normalized} DPI"
            }
        )
        activity?.recreate()
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
                    it.copy(runtimeState = state)
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
            graph.coreRepository.pendingDependencyRepair.collectLatest { request ->
                _uiState.update { it.copy(pendingDependencyRepair = request) }
            }
        }
        viewModelScope.launch {
            graph.settingsRepository.coreDisplayNames.collectLatest { names ->
                _uiState.update { it.copy(coreDisplayNames = names) }
            }
        }
        viewModelScope.launch {
            graph.settingsRepository.coreBranchSelections.collectLatest { selections ->
                _uiState.update { it.copy(coreBranchSelections = selections) }
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
            graph.settingsRepository.glassMaterial.collectLatest { material ->
                _uiState.update { it.copy(glassMaterial = material) }
            }
        }
        viewModelScope.launch {
            graph.settingsRepository.glassTuning.collectLatest { tuning ->
                _uiState.update { it.copy(glassTuning = tuning) }
            }
        }
        viewModelScope.launch {
            graph.settingsRepository.appBackground.collectLatest { background ->
                _uiState.update { it.copy(appBackground = background) }
            }
        }
        viewModelScope.launch {
            graph.settingsRepository.appDpiOverride.collectLatest { dpi ->
                _uiState.update { it.copy(appDpiOverride = dpi) }
            }
        }
        viewModelScope.launch {
            syncServer.uiState.collectLatest { syncState ->
                _uiState.update { it.copy(syncState = syncState) }
            }
        }
    }

    private fun performCoreOperation(title: String, block: suspend () -> Unit) {
        if (_uiState.value.isOperating) return
        _uiState.update {
            it.copy(
                isOperating = true,
                operationProgressTitle = title
            )
        }
        viewModelScope.launch {
            try {
                block()
            } finally {
                _uiState.update {
                    it.copy(
                        isOperating = false,
                        operationProgressTitle = ""
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
