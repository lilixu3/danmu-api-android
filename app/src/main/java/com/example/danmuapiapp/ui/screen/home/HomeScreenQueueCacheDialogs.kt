package com.example.danmuapiapp.ui.screen.home

import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.AppDialogStyle
import com.example.danmuapiapp.ui.component.AppDialogTone
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.domain.model.CacheStats
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.screen.download.DanmuDownloadViewModel
import com.example.danmuapiapp.ui.screen.download.DownloadQueueSummary
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun HomeDownloadQueueDialogHost(
    viewModel: HomeViewModel,
    downloadViewModel: DanmuDownloadViewModel?,
    queueSummary: DownloadQueueSummary,
    queueLiveProgress: Float,
    queueStatusText: String,
    queueRunningDetail: String,
    queueProgressSummary: String,
    queueThrottleHint: String?,
    isQueueDownloading: Boolean,
    isQueuePaused: Boolean,
    queueDialogGroups: List<DownloadQueueDialogGroup>,
    expandedQueueGroupKeys: Set<String>,
    activeOverlay: HomeOverlay?,
    onDismissOverlay: () -> Unit,
    onExpandedQueueGroupKeysChange: (Set<String>) -> Unit,
    onOpenDanmuDownload: () -> Unit,
) {
    if (activeOverlay == HomeOverlay.DownloadQueue) {
        DownloadQueueDialog(
            queueSummary = queueSummary,
            queueLiveProgress = queueLiveProgress,
            queueStatusText = queueStatusText,
            queueRunningDetail = queueRunningDetail,
            queueProgressSummary = queueProgressSummary,
            queueThrottleHint = queueThrottleHint,
            isQueueDownloading = isQueueDownloading,
            isQueuePaused = isQueuePaused,
            queueDialogGroups = queueDialogGroups,
            expandedQueueGroupKeys = expandedQueueGroupKeys,
            onExpandedQueueGroupKeysChange = onExpandedQueueGroupKeysChange,
            onDismiss = { onDismissOverlay() },
            onOpenDownloadPage = {
                onDismissOverlay()
                onOpenDanmuDownload()
            },
            onTogglePauseResume = {
                if (isQueueDownloading) {
                    downloadViewModel?.pauseDownload()
                } else {
                    downloadViewModel?.resumePendingQueue()
                }
            },
            onClearQueue = { downloadViewModel?.clearQueueTasks() }
        )
    }
}

@Composable
internal fun HomeCacheQuickDialogHost(
    viewModel: HomeViewModel,
    cacheStats: CacheStats,
    isCacheLoading: Boolean,
    activeOverlay: HomeOverlay?,
    onDismissOverlay: () -> Unit,
    onOpenCacheManagement: () -> Unit,
) {
    if (activeOverlay == HomeOverlay.CacheQuick) {
        val cacheCapability by viewModel.cacheClearCapability.collectAsStateWithLifecycle()
        CacheQuickDialog(
            cacheStats = cacheStats,
            capability = cacheCapability,
            selectedItems = viewModel.selectedCacheClearItems,
            isLoading = isCacheLoading,
            isClearing = viewModel.isClearingCache,
            onToggleItem = viewModel::toggleCacheClearItem,
            onSelectAll = viewModel::selectAllCacheClearItems,
            onSelectNone = viewModel::clearCacheClearSelection,
            onClear = viewModel::clearSelectedCache,
            onOpenCacheManagement = {
                onDismissOverlay()
                onOpenCacheManagement()
            },
            onDismiss = { onDismissOverlay() }
        )
    }
}

@Composable
internal fun HomeCacheAdminRequiredDialogHost(viewModel: HomeViewModel) {
    if (viewModel.showCacheAdminRequiredDialog) {
        AppDialog(
            onDismissRequest = viewModel::dismissCacheAdminRequiredDialog,
            style = AppDialogStyle.Confirm,
            tone = AppDialogTone.Warning,
            icon = { Icon(Icons.Rounded.AdminPanelSettings, null) },
            title = { Text("需要管理员模式") },
            text = {
                Text(
                    viewModel.cacheAdminRequiredMessage,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                AppGlassButton(onClick = viewModel::dismissCacheAdminRequiredDialog) {
                    Text("知道了")
                }
            }
        )
    }
}

