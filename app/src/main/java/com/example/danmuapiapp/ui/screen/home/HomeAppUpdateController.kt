package com.example.danmuapiapp.ui.screen.home

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.danmuapiapp.data.service.AppForegroundAnnouncementChecker
import com.example.danmuapiapp.data.service.AppForegroundUpdateChecker
import com.example.danmuapiapp.data.service.AppUpdateService
import com.example.danmuapiapp.data.service.GithubProxyService
import com.example.danmuapiapp.domain.model.AppAnnouncement
import com.example.danmuapiapp.ui.common.AppUpdateInstallerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 首页「应用更新与前台公告」子控制器：应用更新提示、下载入口与公告弹窗状态。
 *
 * 对外由 [HomeViewModel] 转发，行为与拆分前一致。
 */
internal class HomeAppUpdateController(
    private val scope: CoroutineScope,
    private val appUpdateService: AppUpdateService,
    private val githubProxyService: GithubProxyService,
    private val foregroundUpdateChecker: AppForegroundUpdateChecker,
    private val foregroundAnnouncementChecker: AppForegroundAnnouncementChecker,
    private val appUpdateInstaller: AppUpdateInstallerController,
    private val postMessage: (String) -> Unit,
    private val isCoreUpdatePromptVisible: () -> Boolean
) {
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
    var isRefreshingAppUpdatePrompt by mutableStateOf(false)
        private set
    var foregroundAnnouncementPrompt by mutableStateOf<AppAnnouncement?>(null)
        private set

    private val unreadAnnouncements: StateFlow<List<AppAnnouncement>> =
        foregroundAnnouncementChecker.unreadAnnouncements

    /** 注册前台更新与前台公告监听。 */
    fun observeForegroundAppUpdate() {
        scope.launch {
            foregroundUpdateChecker.latestUpdate.collect { info ->
                if (info == null || !info.hasUpdate) return@collect

                applyForegroundAppUpdateInfo(info)

                if (!showAppUpdateMethodDialog && !isDownloadingAppUpdate && !showInstallAppUpdateDialog) {
                    showAppUpdatePromptDialog = true
                }
            }
        }
    }

    /** 注册前台公告监听。 */
    fun observeForegroundAnnouncement() {
        scope.launch {
            foregroundAnnouncementChecker.latestAnnouncement.collect { announcement ->
                if (announcement == null) {
                    return@collect
                }

                if (
                    !isCoreUpdatePromptVisible() &&
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

    fun openForegroundAppUpdateMethodDialog() {
        if (appUpdatePromptLatestVersion.isNullOrBlank()) return
        showAppUpdatePromptDialog = false
        appUpdateInstaller.openMethodDialog()
        foregroundUpdateChecker.consumeLatestPrompt(appUpdatePromptLatestVersion)
        if (appUpdatePromptDownloadUrls.isEmpty()) refreshForegroundAppUpdatePrompt()
    }

    fun refreshForegroundAppUpdatePrompt() {
        if (isRefreshingAppUpdatePrompt || isDownloadingAppUpdate) return
        if (!githubProxyService.apiQuota.allowManualOperation()) return
        val expectedVersion = appUpdatePromptLatestVersion ?: return
        isRefreshingAppUpdatePrompt = true
        scope.launch {
            try {
                appUpdateService.checkLatestRelease().fold(
                    onSuccess = { info ->
                        if (appUpdatePromptLatestVersion != expectedVersion) return@fold
                        if (!info.hasUpdate) {
                            showAppUpdatePromptDialog = false
                            appUpdateInstaller.dismissMethodDialog()
                            postMessage("当前已是最新版本")
                        } else {
                            applyForegroundAppUpdateInfo(info)
                            if (info.downloadUrls.isEmpty()) postMessage("暂未获取到兼容的安装包，可重试检查或使用浏览器下载")
                        }
                    },
                    onFailure = {
                        if (it !is com.example.danmuapiapp.data.service.GithubQuotaExhaustedException) {
                            postMessage("获取安装包失败：${it.message ?: "请稍后重试"}")
                        }
                    }
                )
            } finally {
                isRefreshingAppUpdatePrompt = false
            }
        }
    }

    private fun applyForegroundAppUpdateInfo(info: AppUpdateService.CheckResult) {
        appUpdatePromptCurrentVersion = info.currentVersion
        appUpdatePromptLatestVersion = info.latestVersion
        appUpdatePromptReleaseNotes = info.releaseNotes
        appUpdatePromptReleasePage = info.releasePage
        appUpdatePromptDownloadUrls = info.downloadUrls
    }

    fun dismissForegroundAppUpdatePrompt() {
        showAppUpdatePromptDialog = false
        foregroundUpdateChecker.snoozeReminderForToday()
        foregroundUpdateChecker.consumeLatestPrompt(appUpdatePromptLatestVersion)
        postMessage("已设置今日不提醒，24 小时内不再弹出更新提示")
    }

    fun dismissForegroundAppUpdateMethodDialog() {
        appUpdateInstaller.dismissMethodDialog()
    }

    fun acknowledgeForegroundAnnouncementPrompt() {
        val announcement = foregroundAnnouncementPrompt ?: return
        showForegroundAnnouncementDialog = false
        foregroundAnnouncementChecker.acknowledgeAnnouncement(announcement.id)
    }

    fun acknowledgeAllUnreadAnnouncements() {
        val announcementIds = unreadAnnouncements.value.map { it.id }
        if (announcementIds.isEmpty()) return
        showForegroundAnnouncementDialog = false
        foregroundAnnouncementChecker.acknowledgeAnnouncements(announcementIds)
        if (announcementIds.size > 1) {
            postMessage("未读公告已全部标记为已读")
        }
    }

    fun closeForegroundAnnouncementPrompt() {
        val announcement = foregroundAnnouncementPrompt ?: return
        showForegroundAnnouncementDialog = false
        foregroundAnnouncementChecker.consumeLatestPrompt(announcement.id)
    }

    fun snoozeForegroundAnnouncementPrompt() {
        val announcement = foregroundAnnouncementPrompt ?: return
        showForegroundAnnouncementDialog = false
        foregroundAnnouncementChecker.snoozeForToday(announcement.id)
        postMessage("该公告已设置今日不提醒，24 小时内不会再次弹出")
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

    private val showAppUpdateMethodDialog: Boolean
        get() = appUpdateInstaller.uiState.showMethodDialog
    private val isDownloadingAppUpdate: Boolean
        get() = appUpdateInstaller.uiState.isDownloading
    private val showInstallAppUpdateDialog: Boolean
        get() = appUpdateInstaller.uiState.showInstallDialog
}
