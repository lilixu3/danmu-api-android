package com.example.danmuapiapp.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.ui.component.FloatingBottomBarContentSpacer
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.screen.download.DownloadQueueSummary
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.model.RuntimeTransition
import com.example.danmuapiapp.domain.model.AccessEntryLayout
import com.example.danmuapiapp.domain.model.AccessEntryTab
import androidx.compose.foundation.lazy.LazyListState

@Composable
internal fun HomeScreenBody(
    viewModel: HomeViewModel,
    state: RuntimeState,
    listState: LazyListState,
    presentedStatus: ServiceStatus,
    runtimeTransition: RuntimeTransition?,
    runtimeTransitionTitle: String?,
    isRunning: Boolean,
    isBusy: Boolean,
    isHeroChipBusy: Boolean,
    isDarkTheme: Boolean,
    uptimeText: String,
    currentVariantLabel: String,
    isCoreInstalled: Boolean,
    isCoreInfoLoading: Boolean,
    hasVersionUpdate: Boolean,
    sourceMismatch: Boolean,
    sourceUnknownLegacy: Boolean,
    coreVersionText: String,
    coreVersionBadge: String?,
    coreVersionAccent: Color,
    maskedToken: String,
    tokenVisible: Boolean,
    cacheTileValue: String,
    cacheTileBadge: String?,
    cacheTileAccent: Color,
    unreadAnnouncementCount: Int,
    hasQueueTasks: Boolean,
    isQueueDownloading: Boolean,
    isQueuePaused: Boolean,
    queueSummary: DownloadQueueSummary,
    accessEntryLayout: AccessEntryLayout,
    accessEntryDefaultTab: AccessEntryTab,
    accessEntrySession: Int,
    tunnelUrl: String,
    shouldShowRuntimePermissionHint: Boolean,
    hasNotificationPermission: Boolean,
    notificationSupported: Boolean,
    batterySupported: Boolean,
    isBatteryWhitelisted: Boolean,
    shouldShowLocalNetworkAddressHint: Boolean,
    onOpenDownloadQueue: () -> Unit,
    onOpenUnreadAnnouncements: () -> Unit,
    onToggleRunMode: () -> Unit,
    onOpenRuntimeInfo: () -> Unit,
    onOpenCacheQuick: () -> Unit,
    onEditToken: () -> Unit,
    onEditPort: () -> Unit,
    onCheckCoreUpdate: () -> Unit,
    onOpenNotificationPermission: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenLocalNetworkPermission: () -> Unit,
    onCopyLocal: () -> Unit,
    onCopyLan: () -> Unit,
    onCopyLanIpv6: () -> Unit,
    onCopyTunnel: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // 间距放在每个 item 内部（HomeSectionSlot）：这样隐藏的
                // AnimatedVisibility 段（权限提示/错误卡）不会在两侧各留一次间距。
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
            ) {
                item(key = "homeTopHeader") {
                    HomeSectionSlot {
                HomeTopHeader(
                    status = presentedStatus,
                    isRunning = isRunning,
                    isTransitioning = runtimeTransition != null,
                    uptime = uptimeText,
                    unreadAnnouncementCount = unreadAnnouncementCount,
                    hasQueueTasks = hasQueueTasks,
                    isQueueDownloading = isQueueDownloading,
                    isQueuePaused = isQueuePaused,
                    queueSummary = queueSummary,
                    onOpenDownloadDialog = onOpenDownloadQueue,
                    onOpenUnreadAnnouncements = onOpenUnreadAnnouncements
                )
                    }
                }

                item(key = "missionControlHero") {
                    HomeSectionSlot {
                MissionControlHero(
                    status = presentedStatus,
                    statusMessage = runtimeTransition?.message ?: state.statusMessage,
                    transitionTitle = runtimeTransitionTitle,
                    isCoreInstalled = isCoreInstalled,
                    isCoreInfoLoading = isCoreInfoLoading,
                    runModeLabel = when (state.runMode) {
                        RunMode.Normal -> "普通"
                        RunMode.Root -> "Root"
                    },
                    uptime = uptimeText,
                    variantLabel = currentVariantLabel,
                    isRunning = isRunning,
                    isInstalling = viewModel.isInstallingCore,
                    isSwitching = viewModel.isSwitchingCore || runtimeTransition != null,
                    isUpdating = viewModel.isUpdatingCore,
                    isActionBusy = isHeroChipBusy,
                    isDarkTheme = isDarkTheme,
                    onToggleRunMode = onToggleRunMode,
                    onOpenVariantPicker = viewModel::openVariantPicker,
                    onOpenRuntimeInfo = onOpenRuntimeInfo
                )
                    }
                }

                item(key = "snapshotStrip") {
                    HomeSectionSlot {
                SnapshotStrip(
                    status = presentedStatus,
                    isDarkTheme = isDarkTheme,
                    runMode = state.runMode,
                    cacheTileValue = cacheTileValue,
                    cacheTileBadge = cacheTileBadge,
                    cacheTileAccent = cacheTileAccent,
                    onOpenCacheQuick = onOpenCacheQuick,
                    token = state.token,
                    maskedToken = maskedToken,
                    tokenVisible = tokenVisible,
                    onEditToken = onEditToken,
                    port = state.port,
                    coreVersionText = coreVersionText,
                    coreVersionBadge = coreVersionBadge,
                    coreVersionAccent = coreVersionAccent,
                    isActionBusy = isHeroChipBusy,
                    onEditPort = onEditPort,
                    onCheckCoreUpdate = onCheckCoreUpdate,
                )
                    }
                }

                item(key = "runtimePermissionHint") {
                AnimatedVisibility(
                    visible = shouldShowRuntimePermissionHint,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    HomeSectionSlot {
                    RuntimePermissionHintCard(
                        // These values only control hint visibility; real grants remain unchanged.
                        notificationReady = hasNotificationPermission || !notificationSupported,
                        batteryRequired = state.runMode == RunMode.Normal && batterySupported,
                        batteryReady = state.runMode != RunMode.Normal || isBatteryWhitelisted,
                        onOpenNotificationSettings = onOpenNotificationPermission,
                        onOpenBatterySettings = onOpenBatterySettings
                    )
                    }
                }
                }

                item(key = "actionDeck") {
                    HomeSectionSlot {
                ActionDeck(
                    status = presentedStatus,
                    isRunning = isRunning,
                    isTransitioning = isBusy,
                    isStarting = presentedStatus == ServiceStatus.Starting,
                    isInstalling = viewModel.isInstallingCore,
                    isSwitching = viewModel.isSwitchingCore || runtimeTransition != null,
                    isUpdating = viewModel.isUpdatingCore,
                    isCoreInfoLoading = isCoreInfoLoading,
                    isDarkTheme = isDarkTheme,
                    onToggle = viewModel::toggleService,
                    onRestart = viewModel::restartService,
                    onOpenVariantPicker = viewModel::openVariantPicker,
                    onOpenCoreDownload = viewModel::openCoreDownloadDialog,
                    onOpenUpdatePrompt = viewModel::openUpdatePromptFromCard,
                    isCoreInstalled = isCoreInstalled,
                    hasVersionUpdate = hasVersionUpdate,
                    sourceMismatch = sourceMismatch,
                    sourceUnknownLegacy = sourceUnknownLegacy,
                    coreOperationMessage = runtimeTransition?.message ?: coreOperationStatus(
                        isInstalling = viewModel.isInstallingCore,
                        isSwitching = viewModel.isSwitchingCore,
                        isUpdating = viewModel.isUpdatingCore
                    )
                )
                    }
                }

                item(key = "accessGateway") {
                AnimatedVisibility(
                    visible = isRunning,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    HomeSectionSlot {
                    AccessGatewayPanel(
                        layout = accessEntryLayout,
                        defaultTab = accessEntryDefaultTab,
                        selectionSession = accessEntrySession,
                        localUrl = state.localUrl,
                        lanUrl = state.lanUrl,
                        lanIpv6Url = state.lanIpv6Url,
                        tunnelUrl = tunnelUrl,
                        token = state.token,
                        maskedToken = maskedToken,
                        tokenVisible = tokenVisible,
                        showLocalNetworkPermissionHint = shouldShowLocalNetworkAddressHint,
                        onOpenLocalNetworkPermission = onOpenLocalNetworkPermission,
                        onToggleTokenVisible = viewModel::toggleTokenVisible,
                        onCopyLocal = onCopyLocal,
                        onCopyLan = onCopyLan,
                        onCopyLanIpv6 = onCopyLanIpv6,
                        onCopyTunnel = onCopyTunnel
                    )
                    }
                }
                }

                item(key = "runtimeError") {
                AnimatedVisibility(
                    visible = runtimeTransition == null && state.errorMessage != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    HomeSectionSlot {
                    AppGlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.ErrorOutline,
                                null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = state.errorMessage ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    }
                }
                }

                item(key = "bottomBarSpacer") {
                    HomeSectionSlot {
                FloatingBottomBarContentSpacer()
                    }
                }
            }
        }
    }
}

/**
 * 首页每个分区的统一底间距（14dp）。
 *
 * 间距放在 item 内部而不是 LazyColumn 的 verticalArrangement，
 * 这样「权限提示」「错误卡」这类隐藏的 AnimatedVisibility 段折叠后
 * 不会在两侧各留一次间距（表现为卡片之间忽大忽小）。
 */
@Composable
private fun HomeSectionSlot(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
    ) {
        content()
    }
}

