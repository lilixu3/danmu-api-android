package com.example.danmuapiapp.ui.screen.home

import com.example.danmuapiapp.ui.component.AppSnackbarHost

import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.AppDialogStyle
import com.example.danmuapiapp.ui.component.AppDialogTone
import com.example.danmuapiapp.ui.component.AppModalPanel
import com.example.danmuapiapp.ui.screen.core.CoreUpdateDetailsPanel

import android.Manifest
import android.app.Activity
import android.os.Build
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.danmuapiapp.data.service.NodeKeepAlivePrefs
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CacheStats
import com.example.danmuapiapp.domain.model.CoreSourceStatus
import com.example.danmuapiapp.domain.model.DownloadQueueStatus
import com.example.danmuapiapp.domain.model.DanmuDownloadTask
import com.example.danmuapiapp.domain.model.AppAnnouncement
import com.example.danmuapiapp.domain.model.AnnouncementSeverity
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.model.RuntimeTransitionKind
import com.example.danmuapiapp.domain.model.formatCoreVersionTransition
import com.example.danmuapiapp.ui.component.GithubProxyPickerDialog
import com.example.danmuapiapp.ui.component.CoreDependencyRepairHost
import com.example.danmuapiapp.ui.component.FloatingBottomBarContentSpacer
import com.example.danmuapiapp.ui.component.GradientButton
import com.example.danmuapiapp.ui.component.SimpleMarkdownText
import com.example.danmuapiapp.ui.component.StatusIndicator
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton
import com.example.danmuapiapp.ui.screen.download.DanmuDownloadViewModel
import com.example.danmuapiapp.ui.screen.download.DownloadQueueSummary
import com.example.danmuapiapp.ui.theme.appDangerTonalButtonColors
import com.example.danmuapiapp.ui.theme.appPrimaryButtonColors
import com.example.danmuapiapp.ui.startup.LocalNetworkPermissionAction
import com.example.danmuapiapp.ui.startup.LocalNetworkPermissionPolicy
import com.example.danmuapiapp.ui.startup.StartupPermissionGatePrefs
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

private enum class HomeOverlay {
    RunModePicker,
    RuntimeInfo,
    QuickPort,
    QuickToken,
    CoreUpdateConfirm,
    DownloadQueue,
    UnreadAnnouncements,
    CacheQuick
}

@Composable
fun HomeScreen(
    onOpenDanmuDownload: () -> Unit = {},
    onOpenCacheManagement: () -> Unit = {},
    onOpenCoreManagement: () -> Unit = {},
    onOpenAnnouncementRoute: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.runtimeState.collectAsStateWithLifecycle()
    val coreList by viewModel.coreInfoList.collectAsStateWithLifecycle()
    val isCoreInfoLoading by viewModel.isCoreInfoLoading.collectAsStateWithLifecycle()
    val pendingDependencyRepair by viewModel.pendingDependencyRepair.collectAsStateWithLifecycle()
    val coreDisplayNames by viewModel.coreDisplayNames.collectAsStateWithLifecycle()
    val customRepo by viewModel.customRepo.collectAsStateWithLifecycle()
    val customRepoBranch by viewModel.customRepoBranch.collectAsStateWithLifecycle()
    val tokenVisible by viewModel.tokenVisible.collectAsStateWithLifecycle()
    val cacheStats by viewModel.cacheStats.collectAsStateWithLifecycle()
    val isCacheLoading by viewModel.isCacheLoading.collectAsStateWithLifecycle()
    val unreadAnnouncements by viewModel.unreadAnnouncements.collectAsStateWithLifecycle()
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val currentVariantLabel = coreDisplayNames.resolve(state.variant)

    val clipboardManager = LocalClipboard.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { context.findActivity() }
    val componentActivity = activity as? ComponentActivity
    val downloadViewModel = if (componentActivity != null) {
        hiltViewModel<DanmuDownloadViewModel>(viewModelStoreOwner = componentActivity)
    } else {
        null
    }
    val emptyQueueState = remember { mutableStateOf<List<DanmuDownloadTask>>(emptyList()) }
    val downloadQueueTasks by (
        downloadViewModel?.queueTasks?.collectAsStateWithLifecycle()
            ?: emptyQueueState
        )
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    var activeOverlay by remember { mutableStateOf<HomeOverlay?>(null) }
    var pendingRunModeTarget by remember { mutableStateOf<RunMode?>(null) }
    var quickPortText by remember { mutableStateOf("") }
    var quickPortError by remember { mutableStateOf<String?>(null) }
    var quickTokenText by remember { mutableStateOf("") }
    var quickTokenError by remember { mutableStateOf<String?>(null) }
    var isBatteryWhitelisted by remember {
        mutableStateOf(NormalModeKeepAliveGuideNavigator.isIgnoringBatteryOptimizations(context))
    }
    var hasNotificationPermission by remember {
        mutableStateOf(NodeKeepAlivePrefs.hasPostNotificationsPermission(context))
    }
    var localNetworkPermissionState by remember {
        mutableStateOf(readHomeLocalNetworkPermissionState(context))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        hasNotificationPermission = NodeKeepAlivePrefs.hasPostNotificationsPermission(context)
    }
    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        localNetworkPermissionState = readHomeLocalNetworkPermissionState(context)
        if (granted) {
            viewModel.postMessage("已允许局域网访问")
        }
    }

    val runtimeTransition = state.transition
    val presentedStatus = if (runtimeTransition != null) {
        ServiceStatus.Starting
    } else {
        state.status
    }
    val runtimeTransitionTitle = when (runtimeTransition?.kind) {
        RuntimeTransitionKind.ApplyingPullRequests -> "正在应用 PR 组合"
        RuntimeTransitionKind.SwitchingRunMode -> "正在切换运行模式"
        RuntimeTransitionKind.Restarting -> "服务正在重启"
        RuntimeTransitionKind.RecoveringCore -> "正在恢复核心"
        null -> null
    }
    val isRunning = state.status == ServiceStatus.Running
    val isTransitioning = runtimeTransition != null ||
        state.status == ServiceStatus.Starting ||
        state.status == ServiceStatus.Stopping
    val currentCoreInfo = coreList.find { it.variant == state.variant }
    val isCoreInstalled = currentCoreInfo?.isInstalled == true
    val currentCoreVersion = currentCoreInfo?.version
    val hasVersionUpdate = currentCoreInfo?.hasVersionUpdate == true
    val sourceMismatch = currentCoreInfo?.sourceMismatch == true
    val sourceUnknownLegacy = currentCoreInfo?.sourceStatus == CoreSourceStatus.UnknownLegacy
    val availableVersion = currentCoreInfo?.availableVersion
    val isBusy = isTransitioning || viewModel.isInstallingCore ||
        viewModel.isSwitchingCore || viewModel.isUpdatingCore ||
        viewModel.isRepairingDependencies
    val isHeroChipBusy = isBusy || viewModel.isCheckingCoreUpdate
    val uptimeText = if (isRunning) viewModel.formatUptime(state.uptimeSeconds) else "00:00"
    val coreVersionText = when {
        isCoreInfoLoading -> "检测中"
        !isCoreInstalled -> "未安装"
        hasVersionUpdate && !availableVersion.isNullOrBlank() ->
            formatCoreVersionTransition(currentCoreVersion, availableVersion)
        !currentCoreVersion.isNullOrBlank() -> "v$currentCoreVersion"
        else -> "--"
    }
    val coreVersionBadge = when {
        isCoreInfoLoading -> "读取中"
        !isCoreInstalled -> "需安装"
        sourceMismatch -> "需替换"
        sourceUnknownLegacy -> "需刷新"
        hasVersionUpdate -> "有更新"
        else -> null
    }
    val coreVersionAccent = when {
        isCoreInfoLoading -> MaterialTheme.colorScheme.onSurfaceVariant
        !isCoreInstalled -> MaterialTheme.colorScheme.error
        sourceMismatch || sourceUnknownLegacy || hasVersionUpdate -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val maskedToken = when {
        tokenVisible -> state.token
        state.token.isBlank() -> "（未设置）"
        state.token.length <= 2 -> state.token + "****"
        else -> state.token.take(2) + "****"
    }
    val hasInAppDownload = viewModel.appUpdatePromptDownloadUrls.isNotEmpty() &&
        !viewModel.appUpdatePromptLatestVersion.isNullOrBlank()
    val queueSummary = downloadViewModel?.queueSummary() ?: DownloadQueueSummary(
        total = downloadQueueTasks.size
    )
    val queueCompletedCount = queueSummary.success + queueSummary.failed + queueSummary.skipped + queueSummary.canceled
    val queueProgress = if (queueSummary.total <= 0) {
        0f
    } else {
        queueCompletedCount.toFloat() / queueSummary.total.toFloat()
    }
    val queueLiveProgress = if (downloadViewModel?.isDownloading == true) {
        max(queueProgress, downloadViewModel.overallProgress.coerceIn(0f, 1f))
    } else {
        queueProgress
    }
    val isQueueDownloading = downloadViewModel?.isDownloading == true || queueSummary.running > 0
    val isQueuePaused = !isQueueDownloading && queueSummary.pending > 0
    val queueStatusText = when {
        isQueueDownloading -> "队列下载中"
        isQueuePaused -> "队列已暂停"
        else -> "下载队列为空"
    }
    val queueRunningDetail = downloadViewModel?.queueRunningStatusText().orEmpty()
    val queueProgressSummary = downloadViewModel?.progressSummary ?: "当前没有待处理任务"
    val queueThrottleHint = downloadViewModel?.throttleHint
    val hasQueueTasks = queueSummary.total > 0
    val unreadAnnouncementCount = unreadAnnouncements.size
    val cacheTileValue = when {
        runtimeTransition != null -> "服务切换中"
        !isRunning -> "服务未运行"
        cacheStats.reqRecordsCount > 0 -> "${cacheStats.reqRecordsCount} 条记录"
        else -> "暂无记录"
    }
    val cacheTileBadge = when {
        runtimeTransition != null -> "进行中"
        !isRunning -> null
        cacheStats.todayReqNum > 0 -> "今日 ${cacheStats.todayReqNum}"
        else -> "无数据"
    }
    val cacheTileAccent = when {
        runtimeTransition != null -> MaterialTheme.colorScheme.primary
        !isRunning -> MaterialTheme.colorScheme.onSurfaceVariant
        cacheStats.reqRecordsCount > 0 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val queueDialogGroups = remember(downloadQueueTasks) {
        buildDownloadQueueGroups(downloadQueueTasks)
    }
    var expandedQueueGroupKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(state.runMode) {
        hasNotificationPermission = NodeKeepAlivePrefs.hasPostNotificationsPermission(context)
        if (state.runMode == RunMode.Normal) {
            isBatteryWhitelisted = NormalModeKeepAliveGuideNavigator.isIgnoringBatteryOptimizations(context)
        }
    }

    LaunchedEffect(state.port, activeOverlay) {
        if (activeOverlay != HomeOverlay.QuickPort) {
            quickPortText = state.port.toString()
            quickPortError = null
        }
    }

    LaunchedEffect(state.token, activeOverlay) {
        if (activeOverlay != HomeOverlay.QuickToken) {
            quickTokenText = state.token
            quickTokenError = null
        }
    }

    DisposableEffect(lifecycleOwner, context, state.runMode) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshRuntimeState()
                hasNotificationPermission = NodeKeepAlivePrefs.hasPostNotificationsPermission(context)
                localNetworkPermissionState = readHomeLocalNetworkPermissionState(context)
                if (state.runMode == RunMode.Normal) {
                    isBatteryWhitelisted = NormalModeKeepAliveGuideNavigator.isIgnoringBatteryOptimizations(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel.appUpdateMessage) {
        viewModel.appUpdateMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissAppUpdateMessage()
        }
    }
    LaunchedEffect(downloadViewModel?.operationMessage) {
        val message = downloadViewModel?.operationMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            downloadViewModel.dismissMessage()
        }
    }
    LaunchedEffect(viewModel.showUpdatePromptDialog, viewModel.showProxyPickerDialog) {
        val coreUpdateFlowAdvanced = viewModel.showUpdatePromptDialog || viewModel.showProxyPickerDialog
        if (coreUpdateFlowAdvanced && activeOverlay == HomeOverlay.CoreUpdateConfirm) {
            activeOverlay = null
            viewModel.resetCoreUpdateCheckDialogState()
        }
    }
    LaunchedEffect(activeOverlay, queueDialogGroups) {
        if (activeOverlay != HomeOverlay.DownloadQueue) return@LaunchedEffect
        val validKeys = queueDialogGroups.map { it.key }.toSet()
        if (validKeys.isEmpty()) {
            expandedQueueGroupKeys = emptySet()
            return@LaunchedEffect
        }
        expandedQueueGroupKeys = if (expandedQueueGroupKeys.isEmpty()) {
            setOf(queueDialogGroups.first().key)
        } else {
            expandedQueueGroupKeys.intersect(validKeys).ifEmpty { setOf(queueDialogGroups.first().key) }
        }
    }
    val shouldShowRuntimePermissionHint = state.runMode == RunMode.Normal &&
        (!hasNotificationPermission || !isBatteryWhitelisted)
    val shouldShowLocalNetworkAddressHint =
        LocalNetworkPermissionPolicy.shouldShowAddressHint(localNetworkPermissionState)

    fun openNotificationPermissionQuickAction() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (hasNotificationPermission) return

        val shouldShowRationale = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(
                it,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } == true
        val requestedBefore = StartupPermissionGatePrefs.hasRequestedNotificationPermission(context)

        if (activity != null && (!requestedBefore || shouldShowRationale)) {
            StartupPermissionGatePrefs.markNotificationPermissionRequested(context)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        if (!openHomeNotificationSettings(context)) {
            viewModel.postMessage("无法打开通知设置，请手动进入应用详情开启通知")
        }
    }

    fun openLocalNetworkPermissionQuickAction() {
        val latestState = readHomeLocalNetworkPermissionState(context)
        localNetworkPermissionState = latestState
        val shouldShowRationale = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(
                it,
                LocalNetworkPermissionPolicy.PERMISSION
            )
        } == true

        when (
            LocalNetworkPermissionPolicy.resolveAction(
                state = latestState,
                hasActivity = activity != null,
                shouldShowRationale = shouldShowRationale
            )
        ) {
            LocalNetworkPermissionAction.Request -> {
                StartupPermissionGatePrefs.markLocalNetworkPermissionRequested(context)
                localNetworkPermissionLauncher.launch(LocalNetworkPermissionPolicy.PERMISSION)
            }

            LocalNetworkPermissionAction.Settings -> {
                if (!openHomeAppDetailsSettings(context)) {
                    viewModel.postMessage("无法打开应用设置，请手动开启局域网访问权限")
                }
            }

            null -> Unit
        }
    }

    fun openUnreadAnnouncementsEntry() {
        when (unreadAnnouncementCount) {
            0 -> Unit
            1 -> viewModel.openAnnouncementDetails(unreadAnnouncements.first())
            else -> activeOverlay = HomeOverlay.UnreadAnnouncements
        }
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
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
                    onOpenDownloadDialog = { activeOverlay = HomeOverlay.DownloadQueue },
                    onOpenUnreadAnnouncements = ::openUnreadAnnouncementsEntry
                )

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
                    onToggleRunMode = {
                        val options = RunMode.entries.filter { it != state.runMode }
                        pendingRunModeTarget = options.firstOrNull()
                        activeOverlay = HomeOverlay.RunModePicker
                    },
                    onOpenVariantPicker = viewModel::openVariantPicker,
                    onOpenRuntimeInfo = { activeOverlay = HomeOverlay.RuntimeInfo }
                )

                SnapshotStrip(
                    status = presentedStatus,
                    isDarkTheme = isDarkTheme,
                    runMode = state.runMode,
                    cacheTileValue = cacheTileValue,
                    cacheTileBadge = cacheTileBadge,
                    cacheTileAccent = cacheTileAccent,
                    onOpenCacheQuick = {
                        viewModel.prepareCacheClearSelection()
                        activeOverlay = HomeOverlay.CacheQuick
                    },
                    token = state.token,
                    maskedToken = maskedToken,
                    tokenVisible = tokenVisible,
                    onEditToken = {
                        quickTokenText = state.token
                        quickTokenError = null
                        activeOverlay = HomeOverlay.QuickToken
                    },
                    port = state.port,
                    coreVersionText = coreVersionText,
                    coreVersionBadge = coreVersionBadge,
                    coreVersionAccent = coreVersionAccent,
                    isActionBusy = isHeroChipBusy,
                    onEditPort = {
                        quickPortText = state.port.toString()
                        quickPortError = null
                        activeOverlay = HomeOverlay.QuickPort
                    },
                    onCheckCoreUpdate = {
                        viewModel.resetCoreUpdateCheckDialogState()
                        activeOverlay = HomeOverlay.CoreUpdateConfirm
                    },
                )

                AnimatedVisibility(
                    visible = shouldShowRuntimePermissionHint,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    RuntimePermissionHintCard(
                        notificationReady = hasNotificationPermission,
                        batteryRequired = state.runMode == RunMode.Normal,
                        batteryReady = state.runMode != RunMode.Normal || isBatteryWhitelisted,
                        onOpenNotificationSettings = ::openNotificationPermissionQuickAction,
                        onOpenBatterySettings = {
                            val opened = NormalModeKeepAliveGuideNavigator.requestIgnoreBatteryOptimization(context) ||
                                NormalModeKeepAliveGuideNavigator.openAppBatterySettings(context)
                            if (!opened) {
                                viewModel.postMessage("无法打开电池设置，请手动进入应用信息将电池改为不受限制")
                            }
                        }
                    )
                }

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

                AnimatedVisibility(
                    visible = isRunning,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    AccessGatewayPanel(
                        localUrl = state.localUrl,
                        lanUrl = state.lanUrl,
                        lanIpv6Url = state.lanIpv6Url,
                        token = state.token,
                        maskedToken = maskedToken,
                        tokenVisible = tokenVisible,
                        showLocalNetworkPermissionHint = shouldShowLocalNetworkAddressHint,
                        onOpenLocalNetworkPermission = ::openLocalNetworkPermissionQuickAction,
                        onToggleTokenVisible = viewModel::toggleTokenVisible,
                        onCopyLocal = {
                            clipboardManager.nativeClipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("本地地址", state.localUrl)
                            )
                        },
                        onCopyLan = {
                            clipboardManager.nativeClipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("局域网地址", state.lanUrl)
                            )
                        },
                        onCopyLanIpv6 = {
                            clipboardManager.nativeClipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText(
                                    "IPv6 局域网地址",
                                    state.lanIpv6Url
                                )
                            )
                        }
                    )
                }

                AnimatedVisibility(
                    visible = runtimeTransition == null && state.errorMessage != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
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

                FloatingBottomBarContentSpacer()
            }
        }
    }

    if (viewModel.showNoCoreDialog) {
        NoCoreDialog(
            currentVariant = state.variant,
            currentVariantLabel = currentVariantLabel,
            coreDisplayNames = coreDisplayNames,
            customRepo = customRepo,
            customRepoBranch = customRepoBranch,
            onDismiss = viewModel::dismissNoCoreDialog,
            onInstall = viewModel::installAndStart,
            onOpenCoreManagement = onOpenCoreManagement
        )
    }

    viewModel.unavailableVariant?.let { unavailable ->
        UnavailableVariantDialog(
            variant = unavailable,
            variantLabel = coreDisplayNames.resolve(unavailable),
            customRepoConfigured = customRepo.isNotBlank(),
            onDismiss = viewModel::dismissUnavailableVariantDialog,
            onInstall = viewModel::installUnavailableVariant,
            onOpenCoreManagement = {
                viewModel.consumeUnavailableVariantForSettings()
                onOpenCoreManagement()
            }
        )
    }

    CoreDependencyRepairHost(
        request = pendingDependencyRepair,
        showRequiredPrompt = viewModel.showDependencyRequiredPrompt,
        showRepairDialog = viewModel.showDependencyRepairDialog,
        onOpenRepair = viewModel::openDependencyRepairDialog,
        onDismissRequiredPrompt = viewModel::dismissDependencyRequiredPrompt,
        onOnlineRepair = viewModel::repairPendingDependenciesOnline,
        onRepairFromArchive = viewModel::repairPendingDependenciesFromArchive,
        onCancelMutation = viewModel::discardPendingCoreMutation,
        onDismissRepairDialog = viewModel::dismissDependencyRepairDialog
    )

    if (activeOverlay == HomeOverlay.RuntimeInfo) {
        ServiceRuntimeInfoDialog(
            status = presentedStatus,
            uptime = uptimeText,
            runMode = state.runMode,
            variantLabel = currentVariantLabel,
            port = state.port,
            pid = state.pid,
            localUrl = state.localUrl,
            lanUrl = state.lanUrl,
            lanIpv6Url = state.lanIpv6Url,
            listenMode = state.listenMode,
            token = state.token,
            maskedToken = maskedToken,
            tokenVisible = tokenVisible,
            onDismiss = { activeOverlay = null }
        )
    }

    if (activeOverlay == HomeOverlay.QuickPort) {
        QuickPortDialog(
            isBusy = isBusy,
            status = state.status,
            runMode = state.runMode,
            currentPort = state.port,
            quickPortText = quickPortText,
            quickPortError = quickPortError,
            onPortTextChange = {
                quickPortText = it
                quickPortError = null
            },
            onDismiss = { activeOverlay = null },
            onApply = { port ->
                viewModel.applyPortQuick(port)
                activeOverlay = null
            },
            onPortError = { quickPortError = it }
        )
    }

    if (activeOverlay == HomeOverlay.QuickToken) {
        QuickTokenDialog(
            isBusy = isBusy,
            status = state.status,
            currentToken = state.token,
            quickTokenText = quickTokenText,
            quickTokenError = quickTokenError,
            onTokenTextChange = {
                quickTokenText = it
                quickTokenError = null
            },
            onDismiss = { activeOverlay = null },
            onApply = { token ->
                viewModel.applyTokenQuick(token)
                activeOverlay = null
            },
            onTokenError = { quickTokenError = it }
        )
    }

    if (activeOverlay == HomeOverlay.CoreUpdateConfirm) {
        CoreUpdateConfirmDialog(
            variantLabel = currentVariantLabel,
            currentVersion = currentCoreVersion,
            availableVersion = availableVersion,
            isChecking = viewModel.isCheckingCoreUpdate,
            resultMessage = viewModel.coreUpdateCheckDialogMessage,
            resultIsError = viewModel.coreUpdateCheckDialogIsError,
            onDismiss = {
                activeOverlay = null
                viewModel.resetCoreUpdateCheckDialogState()
            },
            onConfirm = {
                viewModel.quickCheckCurrentCoreUpdate()
                if (viewModel.showProxyPickerDialog) {
                    activeOverlay = null
                    viewModel.resetCoreUpdateCheckDialogState()
                }
            }
        )
    }

    if (viewModel.showVariantPicker) {
        VariantPickerDialog(
            currentVariant = state.variant,
            coreList = coreList,
            isCoreInfoLoading = isCoreInfoLoading,
            coreDisplayNames = coreDisplayNames,
            customRepo = customRepo,
            customRepoBranch = customRepoBranch,
            isBusy = isBusy,
            onSelect = viewModel::switchVariant,
            onDismiss = viewModel::dismissVariantPicker
        )
    }

    if (viewModel.showProxyPickerDialog) {
        GithubProxyPickerDialog(
            title = "选择 GitHub 线路",
            subtitle = "首次下载核心前请先测速并选择线路",
            options = viewModel.proxyOptions,
            selectedId = viewModel.proxySelectedId,
            testingIds = viewModel.proxyTestingIds,
            resultMap = viewModel.proxyLatencyMap,
            onSelect = viewModel::selectProxy,
            onRetest = viewModel::retestProxySpeed,
            onConfirm = viewModel::confirmProxySelection,
            onDismiss = viewModel::dismissProxyPickerDialog,
            confirmText = "保存并继续"
        )
    }

    if (viewModel.showUpdatePromptDialog) {
        UpdatePromptDialog(
            variantLabel = viewModel.updatePromptVariant?.let { coreDisplayNames.resolve(it) },
            currentVersion = viewModel.updatePromptCurrentVersion,
            latestVersion = viewModel.updatePromptLatestVersion,
            remoteCommit = viewModel.updatePromptVariant?.let { variant ->
                coreList.firstOrNull { it.variant == variant }?.remoteCommit
            },
            sourceMismatch = viewModel.updatePromptSourceMismatch,
            sourceUnknownLegacy = viewModel.updatePromptSourceUnknownLegacy,
            desiredSource = viewModel.updatePromptDesiredSource,
            onShowDetails = viewModel::openUpdateDetailsFromPrompt,
            onUpdate = viewModel::updateFromPrompt,
            onDismiss = viewModel::dismissCurrentUpdatePrompt
        )
    }

    if (viewModel.showCoreUpdateDetails) {
        viewModel.coreUpdateDetailsVariant?.let { variant ->
            CoreUpdateDetailsPanel(
                displayName = coreDisplayNames.resolve(variant),
                info = coreList.firstOrNull { it.variant == variant },
                comparison = viewModel.coreUpdateComparison,
                isLoading = viewModel.isLoadingCoreUpdateComparison,
                errorMessage = viewModel.coreUpdateComparisonError,
                onRetry = viewModel::retryCoreUpdateComparison,
                onDismiss = viewModel::dismissCoreUpdateDetails,
                onUpdateNow = viewModel::updateFromCoreUpdateDetails
            )
        }
    }

    if (activeOverlay == HomeOverlay.RunModePicker) {
        val availableModes = RunMode.entries.filter { it != state.runMode }
        AppDialog(
            onDismissRequest = {
                activeOverlay = null
                pendingRunModeTarget = null
            },
            style = AppDialogStyle.Selection,
            tone = AppDialogTone.Brand,
            icon = { Icon(Icons.Rounded.PowerSettingsNew, null) },
            title = { Text("切换运行模式") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "当前模式：${state.runMode.label}，请选择要切换到的模式。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    availableModes.forEach { mode ->
                        val selected = pendingRunModeTarget == mode
                        AppGlassSurface(
                            onClick = { pendingRunModeTarget = mode },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                                    if (mode.requiresRoot) {
                                        Text(
                                            "需要 Root 权限",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                AppGlassButton(
                    enabled = !isHeroChipBusy && pendingRunModeTarget != null,
                    onClick = {
                        val target = pendingRunModeTarget ?: return@AppGlassButton
                        activeOverlay = null
                        pendingRunModeTarget = null
                        viewModel.switchRunModeQuick(target)
                    }
                ) {
                    Text("确认切换")
                }
            },
            dismissButton = {
                AppGlassButton(onClick = {
                    activeOverlay = null
                    pendingRunModeTarget = null
                }) {
                    Text("取消")
                }
            }
        )
    }

    if (viewModel.showAppUpdatePromptDialog && !viewModel.appUpdatePromptLatestVersion.isNullOrBlank()) {
        AppDialog(
            onDismissRequest = viewModel::dismissForegroundAppUpdatePrompt,
            style = AppDialogStyle.Status,
            tone = AppDialogTone.Info,
            icon = { Icon(Icons.Rounded.SystemUpdate, null) },
            title = { Text("发现应用更新") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "当前版本：v${viewModel.appUpdatePromptCurrentVersion}\n最新版本：v${viewModel.appUpdatePromptLatestVersion}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    val preview = viewModel.appUpdatePromptReleaseNotes.trim()
                    if (preview.isNotBlank()) {
                        SimpleMarkdownText(
                            markdown = preview
                        )
                    }
                }
            },
            confirmButton = {
                AppGlassButton(onClick = viewModel::openForegroundAppUpdateMethodDialog, tint = MaterialTheme.colorScheme.primary) {
                    Text("现在更新")
                }
            },
            dismissButton = {
                AppGlassButton(onClick = viewModel::dismissForegroundAppUpdatePrompt) {
                    Text("今日不提醒")
                }
            }
        )
    }

    if (activeOverlay == HomeOverlay.UnreadAnnouncements && unreadAnnouncementCount > 1) {
        UnreadAnnouncementListDialog(
            announcements = unreadAnnouncements,
            onDismissRequest = { activeOverlay = null },
            onOpenAnnouncement = { announcement ->
                activeOverlay = null
                viewModel.openAnnouncementDetails(announcement)
            },
            onAcknowledgeAll = {
                activeOverlay = null
                viewModel.acknowledgeAllUnreadAnnouncements()
            }
        )
    }

    val foregroundAnnouncement = viewModel.foregroundAnnouncementPrompt
    if (viewModel.showForegroundAnnouncementDialog && foregroundAnnouncement != null) {
        AnnouncementCenterDialog(
            announcement = foregroundAnnouncement,
            onDismissRequest = {
                if (!foregroundAnnouncement.forcePopup) {
                    viewModel.closeForegroundAnnouncementPrompt()
                }
            },
            onPrimaryAction = {
                val action = foregroundAnnouncement.primaryAction ?: return@AnnouncementCenterDialog
                val route = action.routeOrNull()
                if (route != null) {
                    onOpenAnnouncementRoute(route)
                    viewModel.acknowledgeForegroundAnnouncementPrompt()
                } else {
                    val alive = activity ?: return@AnnouncementCenterDialog
                    viewModel.openForegroundAnnouncementPrimaryAction(alive)
                }
            },
            onSecondaryAction = {
                val action = foregroundAnnouncement.secondaryAction ?: return@AnnouncementCenterDialog
                val route = action.routeOrNull()
                if (route != null) {
                    onOpenAnnouncementRoute(route)
                    viewModel.acknowledgeForegroundAnnouncementPrompt()
                } else {
                    val alive = activity ?: return@AnnouncementCenterDialog
                    viewModel.openForegroundAnnouncementSecondaryAction(alive)
                }
            },
            onMarkRead = viewModel::acknowledgeForegroundAnnouncementPrompt,
            onClose = viewModel::closeForegroundAnnouncementPrompt,
            onSnooze = viewModel::snoozeForegroundAnnouncementPrompt
        )
    }

    if (viewModel.showAppUpdateMethodDialog) {
        AppDialog(
            onDismissRequest = viewModel::dismissForegroundAppUpdateMethodDialog,
            style = AppDialogStyle.Selection,
            tone = AppDialogTone.Info,
            title = { Text("选择更新方式") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppGlassButton(
                        onClick = viewModel::startInAppUpdateDownload,
                        enabled = hasInAppDownload && !viewModel.isDownloadingAppUpdate,
                        modifier = Modifier.fillMaxWidth(),
                        tint = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("应用内下载")
                    }
                    AppGlassButton(
                        onClick = {
                            val alive = activity
                            if (alive != null) {
                                viewModel.openBrowserDownload(alive)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("浏览器下载")
                    }
                    if (!hasInAppDownload) {
                        Text(
                            "当前版本未找到可安装 APK，建议使用浏览器下载。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "首次安装新版本可能需要“安装未知应用”权限，授权后返回 App 会自动继续安装。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                AppGlassButton(onClick = viewModel::dismissForegroundAppUpdateMethodDialog) {
                    Text("取消")
                }
            }
        )
    }

    if (viewModel.isDownloadingAppUpdate) {
        AppDialog(
            onDismissRequest = {},
            style = AppDialogStyle.Status,
            tone = AppDialogTone.Neutral,
            title = { Text("正在下载更新") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val progress = viewModel.appUpdateDownloadPercent
                    if (progress in 0..100) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "$progress% · ${viewModel.appUpdateDownloadDetail}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            viewModel.appUpdateDownloadDetail,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
        )
    }

    if (viewModel.showInstallAppUpdateDialog && viewModel.downloadedAppUpdate != null) {
        val apk = viewModel.downloadedAppUpdate!!
        AppDialog(
            onDismissRequest = viewModel::dismissInstallAppUpdateDialog,
            style = AppDialogStyle.Form,
            tone = AppDialogTone.Brand,
            title = { Text("下载完成") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("文件：${apk.displayName}")
                    Text("位置：${apk.displayPath}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "大小：${formatBytes(apk.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    AppGlassButton(onClick = {
                        val alive = activity
                        if (alive != null) {
                            viewModel.openDownloadsApp(alive)
                        }
                    }) {
                        Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("打开系统下载")
                    }
                }
            },
            confirmButton = {
                AppGlassButton(onClick = {
                    val alive = activity
                    if (alive != null) {
                        viewModel.installDownloadedAppUpdate(alive)
                    }
                }) {
                    Text("立即安装")
                }
            },
            dismissButton = {
                AppGlassButton(onClick = viewModel::dismissInstallAppUpdateDialog) { Text("稍后") }
            }
        )
    }

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
            onExpandedQueueGroupKeysChange = { expandedQueueGroupKeys = it },
            onDismiss = { activeOverlay = null },
            onOpenDownloadPage = {
                activeOverlay = null
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
                activeOverlay = null
                onOpenCacheManagement()
            },
            onDismiss = { activeOverlay = null }
        )
    }

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


private fun openHomeNotificationSettings(context: Context): Boolean {
    val packageUri = Uri.parse("package:${context.packageName}")
    val candidates = listOf(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        },
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = packageUri
        }
    )
    return candidates.any { intent ->
        val finalIntent = Intent(intent).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            val resolved = finalIntent.resolveActivity(context.packageManager) != null
            if (!resolved) {
                false
            } else {
                context.startActivity(finalIntent)
                true
            }
        }.getOrDefault(false)
    }
}

private fun openHomeAppDetailsSettings(context: Context): Boolean {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = "package:${context.packageName}".toUri()
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching {
        if (intent.resolveActivity(context.packageManager) == null) {
            false
        } else {
            context.startActivity(intent)
            true
        }
    }.getOrDefault(false)
}

private fun readHomeLocalNetworkPermissionState(context: Context) =
    LocalNetworkPermissionPolicy.stateFor(
        sdkInt = Build.VERSION.SDK_INT,
        granted = Build.VERSION.SDK_INT < LocalNetworkPermissionPolicy.ANDROID_17_API_LEVEL ||
            ContextCompat.checkSelfPermission(
                context.applicationContext,
                LocalNetworkPermissionPolicy.PERMISSION
            ) == PackageManager.PERMISSION_GRANTED,
        requestAttempted = StartupPermissionGatePrefs.hasRequestedLocalNetworkPermission(context)
    )

private fun AppAnnouncement.dialogTone(): AppDialogTone {
    return when (severity) {
        AnnouncementSeverity.Info -> AppDialogTone.Info
        AnnouncementSeverity.Success -> AppDialogTone.Success
        AnnouncementSeverity.Warning -> AppDialogTone.Warning
        AnnouncementSeverity.Danger -> AppDialogTone.Danger
    }
}

private fun AppAnnouncement.dialogIcon(): ImageVector {
    return when (severity) {
        AnnouncementSeverity.Info -> Icons.Rounded.SystemUpdate
        AnnouncementSeverity.Success -> Icons.Rounded.CheckCircle
        AnnouncementSeverity.Warning -> Icons.Rounded.ErrorOutline
        AnnouncementSeverity.Danger -> Icons.Rounded.ErrorOutline
    }
}

private fun String.toAnnouncementPlainText(): String {
    return replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(Regex("[`#>*_\\[\\]]"), "")
        .replace(Regex("\\((https?://[^)]+)\\)"), "")
        .lines()
        .joinToString("\n") { line ->
            line.replace(Regex("[^\\S\\n]+"), " ").trim()
        }
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

private fun AppAnnouncement.previewText(): String {
    val raw = if (contentPreview.isNotBlank()) contentPreview else contentMarkdown
    return raw.toAnnouncementPlainText().take(320)
}

private fun AppAnnouncement.dialogBodyText(): String {
    val markdownBody = contentMarkdown.toAnnouncementPlainText()
    if (markdownBody.isNotBlank()) return markdownBody
    return contentPreview.toAnnouncementPlainText()
}

private data class AnnouncementTonePalette(
    val iconContainer: Color,
    val iconTint: Color,
    val accent: Color,
    val statusTint: Color
)

@Composable
private fun rememberAnnouncementTonePalette(tone: AppDialogTone): AnnouncementTonePalette {
    val c = MaterialTheme.colorScheme
    return when (tone) {
        AppDialogTone.Neutral -> AnnouncementTonePalette(
            iconContainer = c.surfaceContainerHighest,
            iconTint = c.onSurfaceVariant,
            accent = c.onSurfaceVariant,
            statusTint = c.surfaceContainerHigh
        )
        AppDialogTone.Brand -> AnnouncementTonePalette(
            iconContainer = c.primaryContainer,
            iconTint = c.primary,
            accent = c.primary,
            statusTint = c.primaryContainer
        )
        AppDialogTone.Success -> AnnouncementTonePalette(
            iconContainer = c.tertiaryContainer,
            iconTint = c.tertiary,
            accent = c.tertiary,
            statusTint = c.tertiaryContainer
        )
        AppDialogTone.Warning -> AnnouncementTonePalette(
            iconContainer = c.secondaryContainer,
            iconTint = c.onSecondaryContainer,
            accent = c.secondary,
            statusTint = c.secondaryContainer
        )
        AppDialogTone.Danger -> AnnouncementTonePalette(
            iconContainer = c.errorContainer,
            iconTint = c.error,
            accent = c.error,
            statusTint = c.errorContainer
        )
        AppDialogTone.Info -> AnnouncementTonePalette(
            iconContainer = c.secondaryContainer,
            iconTint = c.secondary,
            accent = c.secondary,
            statusTint = c.secondaryContainer
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
private fun AnnouncementCenterDialog(
    announcement: AppAnnouncement,
    onDismissRequest: () -> Unit,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: () -> Unit,
    onMarkRead: () -> Unit,
    onClose: () -> Unit,
    onSnooze: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val tonePalette = rememberAnnouncementTonePalette(announcement.dialogTone())
    val previewText = announcement.previewText()
    val dialogBodyText = announcement.dialogBodyText()
    val summaryText = announcement.summaryText(previewText)
    val primaryAction = announcement.primaryAction
    val secondaryAction = announcement.secondaryAction
    val hasBusinessActions = primaryAction != null || secondaryAction != null
    val showSnoozeAction = !announcement.forcePopup &&
        !announcement.isShortTerm() &&
        announcement.allowSnoozeToday
    AppModalPanel(
        onDismissRequest = {
            if (!announcement.forcePopup) onDismissRequest()
        },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Header: icon + title + meta ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = tonePalette.iconContainer
                ) {
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            announcement.dialogIcon(),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = tonePalette.iconTint
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = announcement.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = tonePalette.accent.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = announcement.severityLabel(),
                                modifier = Modifier.padding(
                                    horizontal = 8.dp,
                                    vertical = 2.dp
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = tonePalette.accent
                            )
                        }
                        announcement.publishedAt?.let { date ->
                            Text(
                                text = date,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showSnoozeAction) {
                    AppGlassIconButton(
                        onClick = onSnooze,
                        size = 38.dp,
                        surfaceColor = tonePalette.statusTint.copy(alpha = 0.3f),
                        contentColor = tonePalette.accent
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsOff,
                            contentDescription = "今日不提醒"
                        )
                    }
                }
                AppGlassIconButton(
                    onClick = onMarkRead,
                    size = 38.dp,
                    surfaceColor = tonePalette.statusTint.copy(alpha = 0.3f),
                    contentColor = tonePalette.accent
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "标记已读"
                    )
                }
                if (!announcement.forcePopup) {
                    AppGlassIconButton(onClick = onClose, size = 38.dp) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "关闭"
                        )
                    }
                }
            }
        }

        // ── Accent line ──
        Box(
            modifier = Modifier
                .height(2.dp)
                .widthIn(max = 120.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(tonePalette.accent.copy(alpha = 0.22f))
        )

        // ── Scrollable content ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cover image
            announcement.coverImageUrl?.let { url ->
                val context = LocalContext.current
                val normalizedUrl = remember(url) {
                    url.trim().let { u ->
                        if (u.startsWith("//")) "https:$u" else u
                    }
                }
                if (normalizedUrl.isNotBlank()) {
                    val request = remember(context, normalizedUrl) {
                        ImageRequest.Builder(context)
                            .data(normalizedUrl)
                            .crossfade(true)
                            .build()
                    }
                    val painter = rememberAsyncImagePainter(model = request)
                    val imageState = painter.state

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        if (imageState is AsyncImagePainter.State.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = tonePalette.accent
                            )
                        }
                        Image(
                            painter = painter,
                            contentDescription = announcement.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            // Summary
            if (summaryText != null) {
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
            }

            // Preview text card
            if (dialogBodyText.isNotBlank()) {
                AppGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = tonePalette.statusTint.copy(alpha = 0.36f),
                    border = BorderStroke(
                        1.dp,
                        tonePalette.accent.copy(alpha = 0.16f)
                    )
                ) {
                    Text(
                        text = dialogBodyText,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface.copy(alpha = 0.88f)
                    )
                }
            }
        }

        if (hasBusinessActions) {
            HorizontalDivider(
                color = colorScheme.outlineVariant.copy(alpha = 0.32f)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                secondaryAction?.let { action ->
                    AppGlassButton(
                        onClick = onSecondaryAction,
                        surfaceColor = colorScheme.surfaceContainerHighest.copy(alpha = 0.24f)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(action.text)
                    }
                }

                primaryAction?.let { action ->
                    AppGlassButton(
                        onClick = onPrimaryAction,
                        tint = tonePalette.accent
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(action.text)
                    }
                }
            }
        }
    }
}

@Composable
private fun UnreadAnnouncementListDialog(
    announcements: List<AppAnnouncement>,
    onDismissRequest: () -> Unit,
    onOpenAnnouncement: (AppAnnouncement) -> Unit,
    onAcknowledgeAll: () -> Unit,
) {
    AppDialog(
        onDismissRequest = onDismissRequest,
        style = AppDialogStyle.Selection,
        tone = AppDialogTone.Info,
        icon = { Icon(Icons.Rounded.NotificationsActive, null) },
        title = { Text("未读公告") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "当前有 ${announcements.size} 条未读公告，默认按最新发布时间排序。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                announcements.forEachIndexed { index, announcement ->
                    AppGlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = if (index == 0) {
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.34f)
                        },
                        onClick = { onOpenAnnouncement(announcement) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = announcement.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (index == 0) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = "最新",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                            }
                            val summaryText = announcement.summaryText(announcement.previewText())
                                ?: announcement.previewText()
                            if (summaryText.isNotBlank()) {
                                Text(
                                    text = summaryText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            announcement.publishedAt?.let { date ->
                                Text(
                                    text = date,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppGlassButton(onClick = onAcknowledgeAll) {
                Icon(
                    imageVector = Icons.Rounded.DoneAll,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("全部已读")
            }
        },
        dismissButton = {
            AppGlassButton(onClick = onDismissRequest) {
                Text("稍后查看")
            }
        }
    )
}

private fun AppAnnouncement.severityLabel(): String {
    return when (severity) {
        AnnouncementSeverity.Info -> "公告"
        AnnouncementSeverity.Success -> "更新"
        AnnouncementSeverity.Warning -> "提醒"
        AnnouncementSeverity.Danger -> "重要"
    }
}

private fun AppAnnouncement.summaryText(previewText: String): String? {
    val normalizedSummary = summary.trim()
    if (normalizedSummary.isBlank()) return null
    if (normalizedSummary == previewText.trim()) return null
    return normalizedSummary
}
