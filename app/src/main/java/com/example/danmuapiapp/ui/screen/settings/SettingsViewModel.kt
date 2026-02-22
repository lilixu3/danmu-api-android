package com.example.danmuapiapp.ui.screen.settings

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.content.res.Resources
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.data.service.AppUpdateService
import com.example.danmuapiapp.data.service.GithubProxyService
import com.example.danmuapiapp.data.service.NodeKeepAlivePrefs
import com.example.danmuapiapp.data.service.NodeProjectManager
import com.example.danmuapiapp.data.service.NormalAutoStartPrefs
import com.example.danmuapiapp.data.service.RootAutoStartModule
import com.example.danmuapiapp.data.service.RootAutoStartPrefs
import com.example.danmuapiapp.data.service.RootShell
import com.example.danmuapiapp.data.service.RuntimePaths
import com.example.danmuapiapp.data.service.WebDavService
import com.example.danmuapiapp.data.util.AppAppearancePrefs
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.LogLevel
import com.example.danmuapiapp.domain.model.NightModePreference
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.model.WebDavConfig
import com.example.danmuapiapp.domain.repository.CoreRepository
import com.example.danmuapiapp.domain.repository.EnvConfigRepository
import com.example.danmuapiapp.domain.repository.AdminSessionRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.domain.repository.SettingsRepository
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val webDavService: WebDavService,
    private val appUpdateService: AppUpdateService
) : ViewModel() {

    private val envConfigRepo: EnvConfigRepository
        get() = envConfigRepoLazy.get()

    val runtimeState = runtimeRepo.runtimeState
    val githubProxy = settingsRepo.githubProxy
    val githubToken = settingsRepo.githubToken
    val customRepo = settingsRepo.customRepo
    val tokenVisible = settingsRepo.tokenVisible
    val keepAlive = settingsRepo.keepAlive
    val nightMode = settingsRepo.nightMode
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
    var isDownloadingAppUpdate by mutableStateOf(false)
        private set
    var appUpdateDownloadPercent by mutableStateOf(0)
        private set
    var appUpdateDownloadDetail by mutableStateOf("等待下载")
        private set
    var showAppUpdateAvailableDialog by mutableStateOf(false)
        private set
    var showAppUpdateMethodDialog by mutableStateOf(false)
        private set
    var downloadedAppUpdate by mutableStateOf<AppUpdateService.DownloadedApk?>(null)
        private set
    var showInstallAppUpdateDialog by mutableStateOf(false)
        private set

    var showProxyPickerDialog by mutableStateOf(false)
        private set
    var proxySelectedId by mutableStateOf(githubProxyService.currentSelectedOption().id)
        private set
    var proxyTestingIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var proxyLatencyMap by mutableStateOf<Map<String, Long>>(emptyMap())
        private set
    var operationMessage by mutableStateOf<String?>(null)
        private set
    var isWebDavOperating by mutableStateOf(false)
        private set
    var webDavOperatingText by mutableStateOf("")
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

    private var proxyTestJob: Job? = null

    fun adminModeSummary(): String {
        val state = adminSessionState.value
        return when {
            state.isAdminMode -> "已开启 · ${state.tokenHint}"
            state.hasAdminTokenConfigured -> "未开启 · 点击输入 ADMIN_TOKEN"
            else -> "未配置 ADMIN_TOKEN"
        }
    }

    fun saveServiceConfig(port: Int, token: String) {
        val normalizedToken = token.trim()
        val old = runtimeState.value
        if (old.runMode == RunMode.Normal && port in 1..1023) {
            operationMessage = "普通模式无法监听 1-1023 端口，请切换 Root 模式或改用 1024+ 端口"
            return
        }

        val changed = old.port != port || old.token != normalizedToken
        if (!changed) {
            operationMessage = "配置未变化"
            return
        }

        runtimeRepo.applyServiceConfig(
            port = port,
            token = normalizedToken,
            restartIfRunning = true
        )
        operationMessage = if (old.status == ServiceStatus.Running || old.status == ServiceStatus.Starting) {
            "配置已保存，服务正在切换到新端口"
        } else {
            "配置已保存"
        }
    }

    fun restartService() = runtimeRepo.restartService()

    fun updateVariant(variant: ApiVariant) {
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
        operationMessage = if (enabled) {
            "已开启无障碍保活，请在系统无障碍中启用服务"
        } else {
            "已关闭无障碍保活"
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
                        showAppUpdateMethodDialog = false
                        operationMessage = "发现新版本 v${info.latestVersion}"
                    } else {
                        appUpdateHasUpdate = false
                        showAppUpdateAvailableDialog = false
                        showAppUpdateMethodDialog = false
                        downloadedAppUpdate = null
                        showInstallAppUpdateDialog = false
                        operationMessage = "当前已是最新版本（v${info.currentVersion}）"
                    }
                },
                onFailure = {
                    showAppUpdateAvailableDialog = false
                    showAppUpdateMethodDialog = false
                    operationMessage = "检查更新失败：${it.message ?: "请稍后重试"}"
                }
            )
            isCheckingAppUpdate = false
        }
    }

    fun downloadLatestAppUpdate() {
        if (isDownloadingAppUpdate || isCheckingAppUpdate) return
        val urls = appUpdateDownloadUrls
        val latest = appUpdateLatestVersion?.trim().orEmpty()
        if (urls.isEmpty() || latest.isBlank()) {
            operationMessage = "请先检查更新"
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
                    operationMessage = "下载完成：${apk.displayName}"
                    appUpdateDownloadDetail = "下载完成"
                },
                onFailure = {
                    operationMessage = "下载失败：${it.message ?: "请稍后重试"}"
                    appUpdateDownloadDetail = "下载失败"
                }
            )
            isDownloadingAppUpdate = false
        }
    }

    fun dismissAppUpdateAvailableDialog() {
        showAppUpdateAvailableDialog = false
    }

    fun openAppUpdateMethodDialog() {
        showAppUpdateAvailableDialog = false
        showAppUpdateMethodDialog = true
    }

    fun dismissAppUpdateMethodDialog() {
        showAppUpdateMethodDialog = false
    }

    fun startInAppUpdateDownload() {
        showAppUpdateMethodDialog = false
        downloadLatestAppUpdate()
    }

    fun installDownloadedAppUpdate(activity: Activity) {
        val apk = downloadedAppUpdate ?: return
        when (val result = appUpdateService.installApk(activity, apk)) {
            is AppUpdateService.InstallResult.Launched -> {
                showInstallAppUpdateDialog = false
                operationMessage = "已打开安装器，请按系统提示完成安装"
            }
            is AppUpdateService.InstallResult.NeedUnknownSourcePermission -> {
                showInstallAppUpdateDialog = false
                operationMessage = "请完成“安装未知应用”授权，返回 App 后将自动续装"
            }
            is AppUpdateService.InstallResult.Failed -> {
                operationMessage = result.message
            }
        }
    }

    fun openBrowserDownload(activity: Activity) {
        val url = appUpdateDownloadUrls.firstOrNull()
            ?: appUpdateReleasePage.ifBlank { "https://github.com/lilixu3/danmu-api-android/releases/latest" }
        showAppUpdateAvailableDialog = false
        showAppUpdateMethodDialog = false
        appUpdateService.openUrl(activity, url)
        operationMessage = "已打开浏览器下载页面"
    }

    fun dismissInstallAppUpdateDialog() {
        showInstallAppUpdateDialog = false
    }

    fun openAppUpdateReleasePage(activity: Activity) {
        val url = appUpdateReleasePage.ifBlank { "https://github.com/lilixu3/danmu-api-android/releases/latest" }
        appUpdateService.openUrl(activity, url)
    }

    fun openDownloadsApp(activity: Activity) {
        appUpdateService.openDownloadsApp(activity)
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

    fun githubTokenSummary(): String {
        val token = githubToken.value.trim()
        if (token.isBlank()) return "未配置"
        return "已配置（${token.length} 位）"
    }

    fun saveGithubToken(token: String) {
        settingsRepo.setGithubToken(token.trim())
        operationMessage = if (token.isBlank()) "已清空 GitHub Token" else "GitHub Token 已保存"
    }

    fun clearGithubToken() {
        settingsRepo.setGithubToken("")
        operationMessage = "已清空 GitHub Token"
    }

    fun openProxyPicker() {
        showProxyPickerDialog = true
        proxySelectedId = githubProxyService.currentSelectedOption().id
        startProxySpeedTest()
    }

    fun dismissProxyPickerDialog() {
        showProxyPickerDialog = false
        stopProxySpeedTest()
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
    }

    fun envFilePath(): String = envConfigRepo.getEnvFilePath()

    fun refreshWorkDirInfo() {
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { loadWorkDirInfoSafe() }
            workDirInfo = info
        }
    }

    fun applyWorkDirPath(inputPath: String) {
        val path = inputPath.trim().ifBlank { null }
        applyWorkDirInternal(path)
    }

    fun restoreDefaultWorkDir() {
        applyWorkDirInternal(null)
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

    fun exportEnvContent(): String {
        envConfigRepo.reload()
        return envConfigRepo.rawContent.value.ifBlank { "# DanmuApiApp .env\n" }
    }

    fun importEnvContent(content: String) {
        envConfigRepo.saveRawContent(content)
        applyRuntimeFromEnv(content)
        operationMessage = "导入成功，已覆盖当前 .env，建议重启服务"
        runtimeRepo.addLog(LogLevel.Info, "已导入 .env 配置，建议重启服务")
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
            webDavOperatingText = "正在上传 .env 到 WebDAV..."
            envConfigRepo.reload()
            val content = envConfigRepo.rawContent.value
            webDavService.backupEnv(content).fold(
                onSuccess = {
                    operationMessage = "WebDAV 备份成功：$it"
                },
                onFailure = {
                    operationMessage = "WebDAV 备份失败：${it.message}"
                }
            )
            isWebDavOperating = false
            webDavOperatingText = ""
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
            webDavOperatingText = "正在从 WebDAV 下载 .env..."
            webDavService.restoreEnv().fold(
                onSuccess = { content ->
                    envConfigRepo.saveRawContent(content)
                    applyRuntimeFromEnv(content)
                    operationMessage = "WebDAV 恢复成功，已覆盖当前 .env，建议重启服务"
                    runtimeRepo.addLog(LogLevel.Info, "已从 WebDAV 恢复配置，建议重启服务")
                },
                onFailure = {
                    operationMessage = "WebDAV 恢复失败：${it.message}"
                }
            )
            isWebDavOperating = false
            webDavOperatingText = ""
        }
    }

    fun dismissMessage() {
        operationMessage = null
    }

    fun postMessage(message: String) {
        operationMessage = message
    }

    private fun applyWorkDirInternal(targetPath: String?) {
        if (isApplyingWorkDir) return
        viewModelScope.launch {
            isApplyingWorkDir = true
            val result = withContext(Dispatchers.IO) {
                RuntimePaths.applyCustomBaseDir(context, targetPath)
            }
            if (result.ok) {
                val previousVariant = runtimeState.value.variant
                var resolvedVariant: ApiVariant? = null
                withContext(Dispatchers.IO) {
                    runCatching {
                        val projectDir = NodeProjectManager.ensureProjectExtracted(
                            context,
                            RuntimePaths.normalProjectDir(context)
                        )
                        resolvedVariant = syncRuntimeVariantFromEnv(projectDir)
                        NodeProjectManager.writeRuntimeEnv(context, projectDir)
                    }
                }
                coreRepo.refreshCoreInfo()
                envConfigRepo.reload()
                refreshWorkDirInfo()
                val selectedVariant = resolvedVariant
                val variantMessage = when {
                    selectedVariant == null -> "当前目录未检测到可用核心，请先下载核心"
                    selectedVariant != previousVariant -> "已自动切换核心为 ${selectedVariant.label}"
                    else -> null
                }
                if (runtimeState.value.status == ServiceStatus.Running && selectedVariant != null) {
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
                    }
                } else {
                    if (runtimeState.value.status == ServiceStatus.Running && selectedVariant == null) {
                        runtimeRepo.addLog(LogLevel.Warn, "工作目录已切换，但新目录没有可用核心，已跳过自动重启")
                    }
                    operationMessage = buildString {
                        append(result.message)
                        if (!variantMessage.isNullOrBlank()) {
                            append("，")
                            append(variantMessage)
                        }
                    }
                }
            } else {
                operationMessage = result.message
            }
            isApplyingWorkDir = false
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
            val env = parseEnvMap(text)
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

    private fun applyRuntimeFromEnv(content: String) {
        val env = parseEnvMap(content)

        val current = runtimeState.value
        val port = env["DANMU_API_PORT"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: current.port
        runtimeRepo.applyServiceConfig(
            port = port,
            token = env["TOKEN"].orEmpty(),
            restartIfRunning = false
        )
        env["DANMU_API_VARIANT"]?.lowercase()?.let { raw ->
            ApiVariant.entries.firstOrNull { it.key == raw }?.let { runtimeRepo.updateVariant(it) }
        }
        settingsRepo.setFileLogEnabled(false)
    }

    private fun parseEnvMap(content: String): Map<String, String> {
        val map = linkedMapOf<String, String>()
        content.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            val idx = trimmed.indexOf('=')
            if (idx <= 0) return@forEach
            val key = trimmed.substring(0, idx).trim()
            var value = trimmed.substring(idx + 1).trim()
            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                (value.startsWith("'") && value.endsWith("'"))
            ) {
                value = value.substring(1, value.length - 1)
            }
            map[key] = value
        }
        return map
    }

    private fun humanBytes(v: Long): String {
        if (v <= 0) return "0B"
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            v >= gb -> String.format(Locale.getDefault(), "%.2fGB", v / gb)
            v >= mb -> String.format(Locale.getDefault(), "%.2fMB", v / mb)
            v >= kb -> String.format(Locale.getDefault(), "%.1fKB", v / kb)
            else -> "${v}B"
        }
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
