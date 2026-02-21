package com.example.danmuapiapp.ui.screen.home

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.data.service.AppForegroundUpdateChecker
import com.example.danmuapiapp.data.service.AppUpdateService
import com.example.danmuapiapp.data.service.GithubProxyService
import com.example.danmuapiapp.data.service.RootShell
import com.example.danmuapiapp.domain.model.*
import com.example.danmuapiapp.domain.repository.CoreRepository
import com.example.danmuapiapp.domain.repository.RequestRecordRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val runtimeRepo: RuntimeRepository,
    private val coreRepo: CoreRepository,
    private val requestRecordRepo: RequestRecordRepository,
    private val settingsRepo: SettingsRepository,
    private val githubProxyService: GithubProxyService,
    private val appForegroundUpdateChecker: AppForegroundUpdateChecker,
    private val appUpdateService: AppUpdateService
) : ViewModel() {

    val runtimeState = runtimeRepo.runtimeState
    val coreInfoList = coreRepo.coreInfoList
    val isCoreInfoLoading = coreRepo.isCoreInfoLoading
    val tokenVisible = settingsRepo.tokenVisible
    val proxyOptions = githubProxyService.proxyOptions()
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
    var showProxyPickerDialog by mutableStateOf(false)
        private set
    var showUpdatePromptDialog by mutableStateOf(false)
        private set
    var updatePromptVariant by mutableStateOf<ApiVariant?>(null)
        private set
    var updatePromptCurrentVersion by mutableStateOf<String?>(null)
        private set
    var updatePromptLatestVersion by mutableStateOf<String?>(null)
        private set
    var proxySelectedId by mutableStateOf(githubProxyService.currentSelectedOption().id)
        private set
    var proxyTestingIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var proxyLatencyMap by mutableStateOf<Map<String, Long>>(emptyMap())
        private set
    var showAppUpdatePromptDialog by mutableStateOf(false)
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
    var showAppUpdateMethodDialog by mutableStateOf(false)
        private set
    var isDownloadingAppUpdate by mutableStateOf(false)
        private set
    var appUpdateDownloadPercent by mutableStateOf(0)
        private set
    var appUpdateDownloadDetail by mutableStateOf("等待下载")
        private set
    var downloadedAppUpdate by mutableStateOf<AppUpdateService.DownloadedApk?>(null)
        private set
    var showInstallAppUpdateDialog by mutableStateOf(false)
        private set
    var appUpdateMessage by mutableStateOf<String?>(null)
        private set

    private val ignoredUpdateVersionMap = mutableMapOf<ApiVariant, String?>()
    private var proxyTestJob: Job? = null
    private var pendingProxyAction: PendingProxyAction? = null

    private sealed interface PendingProxyAction {
        data class Install(val variant: ApiVariant) : PendingProxyAction
        data class Update(val variant: ApiVariant) : PendingProxyAction
        data class CheckUpdate(val variant: ApiVariant) : PendingProxyAction
    }

    init {
        coreRepo.refreshCoreInfo()
        loadIgnoredUpdateVersions()
        observeUpdatePrompt()
        observeForegroundAppUpdate()
        observeRuntimeDrivenCoreRefresh()
        observeRuntimeDrivenRequestRecordRefresh()
        refreshRequestRecords()
    }

    private fun observeRuntimeDrivenCoreRefresh() {
        viewModelScope.launch {
            runtimeState
                .map { Triple(it.runMode, it.status, it.variant) }
                .distinctUntilChanged()
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
                        refreshRequestRecords()
                    }
                }
        }
    }

    private fun refreshRequestRecords() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                requestRecordRepo.refreshFromService()
            }
        }
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
                    runtimeRepo.addLog(LogLevel.Info, "${variant.label} 安装成功")
                    isInstallingCore = false
                    runtimeRepo.startService()
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

        val variant = runtimeState.value.variant
        val info = coreInfoList.value.find { it.variant == variant }
        if (info?.isInstalled != true) {
            appUpdateMessage = "${variant.label} 未安装，无法检查更新"
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
        viewModelScope.launch {
            val checked = runCatching {
                settingsRepo.setIgnoredUpdateVersion(variant, null)
                ignoredUpdateVersionMap[variant] = null
                coreRepo.checkAndMarkUpdate(variant)
            }.onFailure {
                appUpdateMessage = "检查更新失败：${it.message ?: "请稍后重试"}"
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
                appUpdateMessage = "${variant.label} 已是最新版本"
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
        showAppUpdateMethodDialog = true
        appForegroundUpdateChecker.consumeLatestPrompt(appUpdatePromptLatestVersion)
    }

    fun dismissForegroundAppUpdatePrompt() {
        showAppUpdatePromptDialog = false
        appForegroundUpdateChecker.snoozeReminderForToday()
        appForegroundUpdateChecker.consumeLatestPrompt(appUpdatePromptLatestVersion)
        appUpdateMessage = "已设置今日不提醒，24 小时内不再弹出更新提示"
    }

    fun dismissForegroundAppUpdateMethodDialog() {
        showAppUpdateMethodDialog = false
    }

    fun startInAppUpdateDownload() {
        if (isDownloadingAppUpdate) return
        val urls = appUpdatePromptDownloadUrls
        val latest = appUpdatePromptLatestVersion?.trim().orEmpty()
        if (urls.isEmpty() || latest.isBlank()) {
            appUpdateMessage = "未找到可用安装包，请使用浏览器下载"
            return
        }

        viewModelScope.launch {
            isDownloadingAppUpdate = true
            showAppUpdateMethodDialog = false
            appUpdateDownloadPercent = 0
            appUpdateDownloadDetail = "准备下载..."
            val result = appUpdateService.downloadApk(
                urls = urls,
                version = latest
            ) { soFar, total ->
                viewModelScope.launch {
                    if (total <= 0L) {
                        appUpdateDownloadPercent = -1
                        appUpdateDownloadDetail = "已下载 ${humanBytes(soFar)}"
                    } else {
                        val pct = ((soFar * 100f) / total).toInt().coerceIn(0, 100)
                        appUpdateDownloadPercent = pct
                        appUpdateDownloadDetail = "${humanBytes(soFar)} / ${humanBytes(total)}"
                    }
                }
            }

            result.fold(
                onSuccess = { apk ->
                    downloadedAppUpdate = apk
                    showInstallAppUpdateDialog = true
                    appUpdateMessage = "下载完成：${apk.displayName}"
                    appUpdateDownloadDetail = "下载完成"
                },
                onFailure = {
                    appUpdateMessage = "下载失败：${it.message ?: "请稍后重试"}"
                    appUpdateDownloadDetail = "下载失败"
                }
            )
            isDownloadingAppUpdate = false
        }
    }

    fun openBrowserDownload(activity: Activity) {
        val url = appUpdatePromptDownloadUrls.firstOrNull()
            ?: appUpdatePromptReleasePage.ifBlank { "https://github.com/lilixu3/danmu-api-android/releases/latest" }
        showAppUpdateMethodDialog = false
        appUpdateService.openUrl(activity, url)
        appUpdateMessage = "已打开浏览器下载页面"
    }

    fun installDownloadedAppUpdate(activity: Activity) {
        val apk = downloadedAppUpdate ?: return
        when (val result = appUpdateService.installApk(activity, apk)) {
            is AppUpdateService.InstallResult.Launched -> {
                showInstallAppUpdateDialog = false
                appUpdateMessage = "已打开安装器，请按系统提示完成安装"
            }
            is AppUpdateService.InstallResult.NeedUnknownSourcePermission -> {
                showInstallAppUpdateDialog = false
                appUpdateMessage = "请完成“安装未知应用”授权，返回 App 后将自动续装"
            }
            is AppUpdateService.InstallResult.Failed -> {
                appUpdateMessage = result.message
            }
        }
    }

    fun dismissInstallAppUpdateDialog() {
        showInstallAppUpdateDialog = false
    }

    fun openDownloadsApp(activity: Activity) {
        appUpdateService.openDownloadsApp(activity)
    }

    fun dismissAppUpdateMessage() {
        appUpdateMessage = null
    }

    fun postMessage(message: String) {
        appUpdateMessage = message
    }

    fun stopService() = runtimeRepo.stopService()
    fun restartService() = runtimeRepo.restartService()

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
            ServiceStatus.Running -> stopService()
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
                    runtimeRepo.addLog(LogLevel.Warn, "${variant.label} 未安装，请先下载核心")
                    showNoCoreDialog = true
                    return@launch
                }

                runtimeRepo.addLog(LogLevel.Info, "切换核心到 ${variant.label}")
                runtimeRepo.updateVariant(variant)

                if (wasRunning) {
                    runtimeRepo.addLog(LogLevel.Info, "正在停止服务以应用核心切换...")
                    runtimeRepo.stopService()
                    waitForStatus(
                        statuses = setOf(ServiceStatus.Stopped, ServiceStatus.Error),
                        timeoutMs = 12_000
                    )
                    delay(300)
                    runtimeRepo.addLog(LogLevel.Info, "正在启动新核心...")
                    runtimeRepo.startService()
                    val started = waitForStatus(
                        statuses = setOf(ServiceStatus.Running, ServiceStatus.Error, ServiceStatus.Stopped),
                        timeoutMs = 18_000
                    )
                    if (started != ServiceStatus.Running && current.runMode == RunMode.Normal) {
                        runtimeRepo.addLog(LogLevel.Warn, "切换后首次启动未就绪，自动重试一次")
                        runtimeRepo.startService()
                        val retry = waitForStatus(
                            statuses = setOf(ServiceStatus.Running, ServiceStatus.Error, ServiceStatus.Stopped),
                            timeoutMs = 12_000
                        )
                        if (retry != ServiceStatus.Running) {
                            runtimeRepo.addLog(LogLevel.Error, "切换后服务未成功启动，请查看日志")
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
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }

    private suspend fun waitForStatus(statuses: Set<ServiceStatus>, timeoutMs: Long): ServiceStatus? {
        return withTimeoutOrNull(timeoutMs) {
            runtimeState.first { it.status in statuses }.status
        }
    }

    fun dismissProxyPickerDialog() {
        showProxyPickerDialog = false
        stopProxySpeedTest()
        pendingProxyAction = null
    }

    fun selectProxy(proxyId: String) {
        proxySelectedId = proxyId
    }

    fun retestProxySpeed() {
        startProxySpeedTest()
    }

    fun confirmProxySelection() {
        githubProxyService.setSelectedProxy(proxySelectedId)
        showProxyPickerDialog = false
        stopProxySpeedTest()
        pendingProxyAction?.let { action ->
            pendingProxyAction = null
            when (action) {
                is PendingProxyAction.Install -> doInstallAndStart(action.variant)
                is PendingProxyAction.Update -> doUpdateCurrentVariant(action.variant)
                is PendingProxyAction.CheckUpdate -> doQuickCheckCurrentCoreUpdate(action.variant)
            }
        }
    }

    private fun openProxyPickerDialog() {
        showProxyPickerDialog = true
        proxySelectedId = githubProxyService.currentSelectedOption().id
        startProxySpeedTest()
    }

    private fun startProxySpeedTest() {
        stopProxySpeedTest()
        proxyLatencyMap = emptyMap()
        proxyTestingIds = proxyOptions.map { it.id }.toSet()
        proxyTestJob = viewModelScope.launch {
            proxyOptions.forEach { option ->
                launch {
                    val latency = githubProxyService.testLatency(option)
                    proxyLatencyMap = proxyLatencyMap + (option.id to latency)
                    proxyTestingIds = proxyTestingIds - option.id
                }
            }
        }
    }

    private fun stopProxySpeedTest() {
        proxyTestJob?.cancel()
        proxyTestJob = null
        proxyTestingIds = emptySet()
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
            return
        }

        val latest = info.latestVersion.trim()
        val ignored = ignoredUpdateVersionMap[currentVariant]?.trim().orEmpty()
        if (ignored.isNotBlank() && ignored != latest) {
            settingsRepo.setIgnoredUpdateVersion(currentVariant, null)
            ignoredUpdateVersionMap[currentVariant] = null
        }

        val effectiveIgnored = ignoredUpdateVersionMap[currentVariant]?.trim().orEmpty()
        if (effectiveIgnored == latest) return

        val samePromptShown = showUpdatePromptDialog &&
            updatePromptVariant == currentVariant &&
            updatePromptLatestVersion == latest
        if (samePromptShown) return

        updatePromptVariant = currentVariant
        updatePromptCurrentVersion = info.version
        updatePromptLatestVersion = latest
        showUpdatePromptDialog = true
    }

    private fun buildRootSwitchDeniedMessage(result: RootShell.Result): String {
        if (result.timedOut) {
            return "Root 授权超时，请在授权弹窗中允许后重试"
        }
        val detail = (result.stderr.ifBlank { result.stdout })
            .lineSequence()
            .firstOrNull()
            ?.trim()
            .orEmpty()
        if (detail.contains("not found", ignoreCase = true)) {
            return "未检测到 su，无法切换到高权限模式"
        }
        if (detail.contains("denied", ignoreCase = true)) {
            return "未授予 Root 权限，无法切换到高权限模式"
        }
        return if (detail.isBlank()) {
            "未获得 Root 权限，无法切换到高权限模式"
        } else {
            "未获得 Root 权限，无法切换：$detail"
        }
    }

    private fun humanBytes(v: Long): String {
        if (v <= 0) return "0B"
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            v >= gb -> String.format("%.2fGB", v / gb)
            v >= mb -> String.format("%.2fMB", v / mb)
            v >= kb -> String.format("%.1fKB", v / kb)
            else -> "${v}B"
        }
    }
}
