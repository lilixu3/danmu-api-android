package com.example.danmuapiapp.ui.common

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.danmuapiapp.data.service.AppUpdateService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class AppUpdateInstallerUiState(
    val showMethodDialog: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadPercent: Int = 0,
    val downloadDetail: String = "等待下载",
    val downloadedApk: AppUpdateService.DownloadedApk? = null,
    val showInstallDialog: Boolean = false
)

internal class AppUpdateInstallerController(
    private val scope: CoroutineScope,
    private val appUpdateService: AppUpdateService,
    private val postMessage: (String) -> Unit
) {
    var uiState by mutableStateOf(AppUpdateInstallerUiState())
        private set

    fun openMethodDialog() {
        uiState = uiState.copy(showMethodDialog = true)
    }

    fun dismissMethodDialog() {
        uiState = uiState.copy(showMethodDialog = false)
    }

    fun dismissInstallDialog() {
        uiState = uiState.copy(showInstallDialog = false)
    }

    fun reset() {
        uiState = AppUpdateInstallerUiState()
    }

    fun startDownload(
        urls: List<String>,
        latestVersion: String?,
        missingMessage: String
    ) {
        if (uiState.isDownloading) return
        val normalizedVersion = latestVersion?.trim().orEmpty()
        if (urls.isEmpty() || normalizedVersion.isBlank()) {
            postMessage(missingMessage)
            return
        }

        scope.launch {
            uiState = uiState.copy(
                isDownloading = true,
                showMethodDialog = false,
                downloadPercent = 0,
                downloadDetail = "准备下载..."
            )
            val result = appUpdateService.downloadApk(
                urls = urls,
                version = normalizedVersion
            ) { soFar, total ->
                scope.launch {
                    uiState = if (total <= 0L) {
                        uiState.copy(
                            downloadPercent = -1,
                            downloadDetail = "已下载 ${formatBytesText(soFar)}"
                        )
                    } else {
                        uiState.copy(
                            downloadPercent = ((soFar * 100f) / total).toInt().coerceIn(0, 100),
                            downloadDetail = "${formatBytesText(soFar)} / ${formatBytesText(total)}"
                        )
                    }
                }
            }

            result.fold(
                onSuccess = { apk ->
                    uiState = uiState.copy(
                        downloadedApk = apk,
                        showInstallDialog = true,
                        downloadDetail = "下载完成"
                    )
                    postMessage("下载完成：${apk.displayName}")
                },
                onFailure = {
                    uiState = uiState.copy(downloadDetail = "下载失败")
                    postMessage("下载失败：${it.message ?: "请稍后重试"}")
                }
            )
            uiState = uiState.copy(isDownloading = false)
        }
    }

    fun openBrowserDownload(
        activity: Activity,
        downloadUrls: List<String>,
        releasePage: String,
        fallbackReleasePage: String,
        beforeOpen: () -> Unit = {}
    ) {
        beforeOpen()
        uiState = uiState.copy(showMethodDialog = false)
        val url = downloadUrls.firstOrNull() ?: releasePage.ifBlank { fallbackReleasePage }
        appUpdateService.openUrl(activity, url)
        postMessage("已打开浏览器下载页面")
    }

    fun installDownloaded(activity: Activity) {
        val apk = uiState.downloadedApk ?: return
        when (val result = appUpdateService.installApk(activity, apk)) {
            is AppUpdateService.InstallResult.Launched -> {
                uiState = uiState.copy(showInstallDialog = false)
                postMessage("已打开安装器，请按系统提示完成安装")
            }
            is AppUpdateService.InstallResult.NeedUnknownSourcePermission -> {
                uiState = uiState.copy(showInstallDialog = false)
                postMessage("请完成“安装未知应用”授权，返回 App 后将自动续装")
            }
            is AppUpdateService.InstallResult.Failed -> {
                postMessage(result.message)
            }
        }
    }

    fun openDownloadsApp(activity: Activity) {
        appUpdateService.openDownloadsApp(activity)
    }
}
