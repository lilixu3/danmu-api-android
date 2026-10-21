package com.example.danmuapiapp.ui.screen.settings

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.danmuapiapp.data.service.AppUpdateService
import com.example.danmuapiapp.data.service.GithubProxyService
import com.example.danmuapiapp.ui.common.AppUpdateInstallerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 设置页「应用更新」子控制器：检查更新、下载、安装与相关弹窗状态。
 *
 * 只组合更新服务与安装控制器，对外由 [SettingsViewModel] 转发，行为与拆分前一致。
 */
internal class SettingsAppUpdateController(
    private val scope: CoroutineScope,
    private val appUpdateService: AppUpdateService,
    private val githubProxyService: GithubProxyService,
    private val appUpdateInstaller: AppUpdateInstallerController,
    private val postMessage: (String) -> Unit
) {
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

    fun checkAppUpdate() {
        if (isCheckingAppUpdate) return
        if (!githubProxyService.apiQuota.allowManualOperation()) return
        scope.launch {
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
                        postMessage("发现新版本 v${info.latestVersion}")
                    } else {
                        appUpdateHasUpdate = false
                        showAppUpdateAvailableDialog = false
                        appUpdateInstaller.reset()
                        postMessage("当前已是最新版本（v${info.currentVersion}）")
                    }
                },
                onFailure = {
                    showAppUpdateAvailableDialog = false
                    appUpdateInstaller.dismissMethodDialog()
                    if (it !is com.example.danmuapiapp.data.service.GithubQuotaExhaustedException) {
                        postMessage("检查更新失败：${it.message ?: "请稍后重试"}")
                    }
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

    fun startInAppUpdateDownload() {
        appUpdateInstaller.dismissMethodDialog()
        downloadLatestAppUpdate()
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

    fun openAppUpdateReleasePage(activity: Activity) {
        val url = appUpdateReleasePage.ifBlank { "https://github.com/lilixu3/danmu-api-android/releases/latest" }
        appUpdateService.openUrl(activity, url)
    }

}
