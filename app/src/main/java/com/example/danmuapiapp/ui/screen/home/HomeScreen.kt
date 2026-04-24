package com.example.danmuapiapp.ui.screen.home

import com.example.danmuapiapp.ui.component.AppBottomSheetDialog
import com.example.danmuapiapp.ui.component.AppBottomSheetStyle
import com.example.danmuapiapp.ui.component.AppBottomSheetTone

import android.Manifest
import android.app.Activity
import android.os.Build
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.BottomSheetDefaults
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
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
import com.example.danmuapiapp.data.service.NodeKeepAlivePrefs
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CacheEntry
import com.example.danmuapiapp.domain.model.CacheStats
import com.example.danmuapiapp.domain.model.CoreSourceStatus
import com.example.danmuapiapp.domain.model.DownloadQueueStatus
import com.example.danmuapiapp.domain.model.DanmuDownloadTask
import com.example.danmuapiapp.domain.model.AppAnnouncement
import com.example.danmuapiapp.domain.model.AnnouncementSeverity
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.model.formatCoreVersionTransition
import com.example.danmuapiapp.ui.component.GithubProxyPickerDialog
import com.example.danmuapiapp.ui.component.GradientButton
import com.example.danmuapiapp.ui.component.SimpleMarkdownText
import com.example.danmuapiapp.ui.component.StatusIndicator
import com.example.danmuapiapp.ui.screen.download.DanmuDownloadViewModel
import com.example.danmuapiapp.ui.screen.download.DownloadQueueSummary
import com.example.danmuapiapp.ui.theme.appDangerTonalButtonColors
import com.example.danmuapiapp.ui.theme.appPrimaryButtonColors
import com.example.danmuapiapp.ui.startup.StartupPermissionGatePrefs
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun HomeScreen(
    onOpenDanmuDownload: () -> Unit = {},
    onOpenCacheManagement: () -> Unit = {},
    onOpenAnnouncementRoute: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.runtimeState.collectAsStateWithLifecycle()
    val coreList by viewModel.coreInfoList.collectAsStateWithLifecycle()
    val isCoreInfoLoading by viewModel.isCoreInfoLoading.collectAsStateWithLifecycle()
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
    var showRunModePickerDialog by remember { mutableStateOf(false) }
    var pendingRunModeTarget by remember { mutableStateOf<RunMode?>(null) }
    var showRuntimeInfoDialog by remember { mutableStateOf(false) }
    var showQuickPortDialog by remember { mutableStateOf(false) }
    var quickPortText by remember { mutableStateOf("") }
    var quickPortError by remember { mutableStateOf<String?>(null) }
    var showQuickTokenDialog by remember { mutableStateOf(false) }
    var quickTokenText by remember { mutableStateOf("") }
    var quickTokenError by remember { mutableStateOf<String?>(null) }
    var showCoreUpdateConfirmDialog by remember { mutableStateOf(false) }
    var showDownloadQueueSheet by remember { mutableStateOf(false) }
    var showUnreadAnnouncementListDialog by remember { mutableStateOf(false) }
    var showCacheQuickDialog by remember { mutableStateOf(false) }
    var isBatteryWhitelisted by remember {
        mutableStateOf(NormalModeKeepAliveGuideNavigator.isIgnoringBatteryOptimizations(context))
    }
    var hasNotificationPermission by remember {
        mutableStateOf(NodeKeepAlivePrefs.hasPostNotificationsPermission(context))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        hasNotificationPermission = NodeKeepAlivePrefs.hasPostNotificationsPermission(context)
    }

    val isRunning = state.status == ServiceStatus.Running
    val isTransitioning = state.status == ServiceStatus.Starting ||
        state.status == ServiceStatus.Stopping
    val currentCoreInfo = coreList.find { it.variant == state.variant }
    val isCoreInstalled = currentCoreInfo?.isInstalled == true
    val currentCoreVersion = currentCoreInfo?.version
    val hasVersionUpdate = currentCoreInfo?.hasVersionUpdate == true
    val sourceMismatch = currentCoreInfo?.sourceMismatch == true
    val sourceUnknownLegacy = currentCoreInfo?.sourceStatus == CoreSourceStatus.UnknownLegacy
    val availableVersion = currentCoreInfo?.availableVersion
    val isBusy = isTransitioning || viewModel.isInstallingCore ||
        viewModel.isSwitchingCore || viewModel.isUpdatingCore
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
        !isRunning -> "服务未运行"
        cacheStats.reqRecordsCount > 0 -> "${cacheStats.reqRecordsCount} 条记录"
        else -> "暂无记录"
    }
    val cacheTileBadge = when {
        !isRunning -> null
        cacheStats.todayReqNum > 0 -> "今日 ${cacheStats.todayReqNum}"
        else -> "无数据"
    }
    val cacheTileAccent = when {
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

    LaunchedEffect(state.port, showQuickPortDialog) {
        if (!showQuickPortDialog) {
            quickPortText = state.port.toString()
            quickPortError = null
        }
    }

    LaunchedEffect(state.token, showQuickTokenDialog) {
        if (!showQuickTokenDialog) {
            quickTokenText = state.token
            quickTokenError = null
        }
    }

    DisposableEffect(lifecycleOwner, context, state.runMode) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshRuntimeState()
                hasNotificationPermission = NodeKeepAlivePrefs.hasPostNotificationsPermission(context)
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
    LaunchedEffect(viewModel.showUpdatePromptDialog) {
        if (viewModel.showUpdatePromptDialog && showCoreUpdateConfirmDialog) {
            showCoreUpdateConfirmDialog = false
            viewModel.resetCoreUpdateCheckDialogState()
        }
    }
    LaunchedEffect(showDownloadQueueSheet, queueDialogGroups) {
        if (!showDownloadQueueSheet) return@LaunchedEffect
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
    val backgroundGradient = if (isDarkTheme) {
        listOf(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
            MaterialTheme.colorScheme.surfaceContainerHigh
        )
    } else {
        listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f),
            MaterialTheme.colorScheme.surface
        )
    }
    val primaryGlowAlpha = if (isDarkTheme) 0.11f else 0.17f
    val tertiaryGlowAlpha = if (isDarkTheme) 0.08f else 0.13f
    val shouldShowRuntimePermissionHint = state.runMode == RunMode.Normal &&
        (!hasNotificationPermission || !isBatteryWhitelisted)

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

    fun openUnreadAnnouncementsEntry() {
        when (unreadAnnouncementCount) {
            0 -> Unit
            1 -> viewModel.openAnnouncementDetails(unreadAnnouncements.first())
            else -> showUnreadAnnouncementListDialog = true
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        backgroundGradient
                    )
                )
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 28.dp, end = 16.dp)
                    .align(Alignment.TopEnd)
                    .size(180.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = primaryGlowAlpha),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .padding(start = 24.dp, top = 220.dp)
                    .align(Alignment.TopStart)
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.tertiary.copy(alpha = tertiaryGlowAlpha),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                HomeTopHeader(
                    status = state.status,
                    isRunning = isRunning,
                    uptime = uptimeText,
                    unreadAnnouncementCount = unreadAnnouncementCount,
                    hasQueueTasks = hasQueueTasks,
                    isQueueDownloading = isQueueDownloading,
                    isQueuePaused = isQueuePaused,
                    queueSummary = queueSummary,
                    onOpenDownloadSheet = { showDownloadQueueSheet = true },
                    onOpenUnreadAnnouncements = ::openUnreadAnnouncementsEntry
                )

                MissionControlHero(
                    status = state.status,
                    statusMessage = state.statusMessage,
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
                    isSwitching = viewModel.isSwitchingCore,
                    isUpdating = viewModel.isUpdatingCore,
                    isActionBusy = isHeroChipBusy,
                    isDarkTheme = isDarkTheme,
                    onToggleRunMode = {
                        val options = RunMode.entries.filter { it != state.runMode }
                        pendingRunModeTarget = options.firstOrNull()
                        showRunModePickerDialog = true
                    },
                    onOpenVariantPicker = viewModel::openVariantPicker,
                    onOpenRuntimeInfo = { showRuntimeInfoDialog = true }
                )

                SnapshotStrip(
                    status = state.status,
                    isDarkTheme = isDarkTheme,
                    runMode = state.runMode,
                    cacheTileValue = cacheTileValue,
                    cacheTileBadge = cacheTileBadge,
                    cacheTileAccent = cacheTileAccent,
                    onOpenCacheQuick = { showCacheQuickDialog = true },
                    token = state.token,
                    maskedToken = maskedToken,
                    tokenVisible = tokenVisible,
                    onEditToken = {
                        quickTokenText = state.token
                        quickTokenError = null
                        showQuickTokenDialog = true
                    },
                    port = state.port,
                    coreVersionText = coreVersionText,
                    coreVersionBadge = coreVersionBadge,
                    coreVersionAccent = coreVersionAccent,
                    isActionBusy = isHeroChipBusy,
                    onEditPort = {
                        quickPortText = state.port.toString()
                        quickPortError = null
                        showQuickPortDialog = true
                    },
                    onCheckCoreUpdate = {
                        viewModel.resetCoreUpdateCheckDialogState()
                        showCoreUpdateConfirmDialog = true
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
                    status = state.status,
                    isRunning = isRunning,
                    isTransitioning = isBusy,
                    isStarting = state.status == ServiceStatus.Starting,
                    isInstalling = viewModel.isInstallingCore,
                    isSwitching = viewModel.isSwitchingCore,
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
                    availableVersion = availableVersion,
                    coreOperationMessage = coreOperationStatus(
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
                        token = state.token,
                        maskedToken = maskedToken,
                        tokenVisible = tokenVisible,
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
                        }
                    )
                }

                AnimatedVisibility(
                    visible = state.errorMessage != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
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
    }

    if (viewModel.showNoCoreDialog) {
        NoCoreDialog(
            currentVariant = state.variant,
            currentVariantLabel = currentVariantLabel,
            coreDisplayNames = coreDisplayNames,
            customRepo = customRepo,
            customRepoBranch = customRepoBranch,
            onDismiss = viewModel::dismissNoCoreDialog,
            onInstall = viewModel::installAndStart
        )
    }

    if (showRuntimeInfoDialog) {
        ServiceRuntimeInfoDialog(
            status = state.status,
            uptime = uptimeText,
            runMode = state.runMode,
            variantLabel = currentVariantLabel,
            port = state.port,
            pid = state.pid,
            localUrl = state.localUrl,
            lanUrl = state.lanUrl,
            token = state.token,
            maskedToken = maskedToken,
            tokenVisible = tokenVisible,
            onDismiss = { showRuntimeInfoDialog = false }
        )
    }

    if (showQuickPortDialog) {
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
            onDismiss = { showQuickPortDialog = false },
            onApply = { port ->
                viewModel.applyPortQuick(port)
                showQuickPortDialog = false
            },
            onPortError = { quickPortError = it }
        )
    }

    if (showQuickTokenDialog) {
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
            onDismiss = { showQuickTokenDialog = false },
            onApply = { token ->
                viewModel.applyTokenQuick(token)
                showQuickTokenDialog = false
            },
            onTokenError = { quickTokenError = it }
        )
    }

    if (showCoreUpdateConfirmDialog) {
        CoreUpdateConfirmDialog(
            variantLabel = currentVariantLabel,
            currentVersion = currentCoreVersion,
            availableVersion = availableVersion,
            isChecking = viewModel.isCheckingCoreUpdate,
            resultMessage = viewModel.coreUpdateCheckDialogMessage,
            resultIsError = viewModel.coreUpdateCheckDialogIsError,
            onDismiss = {
                showCoreUpdateConfirmDialog = false
                viewModel.resetCoreUpdateCheckDialogState()
            },
            onConfirm = {
                viewModel.quickCheckCurrentCoreUpdate()
                if (viewModel.showProxyPickerDialog) {
                    showCoreUpdateConfirmDialog = false
                    viewModel.resetCoreUpdateCheckDialogState()
                }
            }
        )
    }

    if (viewModel.showVariantPicker) {
        VariantPickerSheet(
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
            confirmText = "保存并下载"
        )
    }

    if (viewModel.showUpdatePromptDialog) {
        UpdatePromptDialog(
            variantLabel = viewModel.updatePromptVariant?.let { coreDisplayNames.resolve(it) },
            currentVersion = viewModel.updatePromptCurrentVersion,
            latestVersion = viewModel.updatePromptLatestVersion,
            sourceMismatch = viewModel.updatePromptSourceMismatch,
            sourceUnknownLegacy = viewModel.updatePromptSourceUnknownLegacy,
            desiredSource = viewModel.updatePromptDesiredSource,
            onUpdate = viewModel::updateFromPrompt,
            onIgnore = viewModel::ignoreCurrentUpdatePrompt
        )
    }

    if (showRunModePickerDialog) {
        val availableModes = RunMode.entries.filter { it != state.runMode }
        AppBottomSheetDialog(
            onDismissRequest = {
                showRunModePickerDialog = false
                pendingRunModeTarget = null
            },
            style = AppBottomSheetStyle.Selection,
            tone = AppBottomSheetTone.Brand,
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
                        OutlinedCard(
                            onClick = { pendingRunModeTarget = mode },
                            modifier = Modifier.fillMaxWidth(),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
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
                TextButton(
                    enabled = !isHeroChipBusy && pendingRunModeTarget != null,
                    onClick = {
                        val target = pendingRunModeTarget ?: return@TextButton
                        showRunModePickerDialog = false
                        pendingRunModeTarget = null
                        viewModel.switchRunModeQuick(target)
                    }
                ) {
                    Text("确认切换")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRunModePickerDialog = false
                    pendingRunModeTarget = null
                }) {
                    Text("取消")
                }
            }
        )
    }

    if (viewModel.showAppUpdatePromptDialog && !viewModel.appUpdatePromptLatestVersion.isNullOrBlank()) {
        AppBottomSheetDialog(
            onDismissRequest = viewModel::dismissForegroundAppUpdatePrompt,
            style = AppBottomSheetStyle.Status,
            tone = AppBottomSheetTone.Info,
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
                TextButton(onClick = viewModel::openForegroundAppUpdateMethodDialog) {
                    Text("现在更新")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissForegroundAppUpdatePrompt) {
                    Text("今日不提醒")
                }
            }
        )
    }

    if (showUnreadAnnouncementListDialog && unreadAnnouncementCount > 1) {
        UnreadAnnouncementListDialog(
            announcements = unreadAnnouncements,
            onDismissRequest = { showUnreadAnnouncementListDialog = false },
            onOpenAnnouncement = { announcement ->
                showUnreadAnnouncementListDialog = false
                viewModel.openAnnouncementDetails(announcement)
            },
            onAcknowledgeAll = {
                showUnreadAnnouncementListDialog = false
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
        AppBottomSheetDialog(
            onDismissRequest = viewModel::dismissForegroundAppUpdateMethodDialog,
            style = AppBottomSheetStyle.Selection,
            tone = AppBottomSheetTone.Info,
            title = { Text("选择更新方式") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = viewModel::startInAppUpdateDownload,
                        enabled = hasInAppDownload && !viewModel.isDownloadingAppUpdate,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("应用内下载")
                    }
                    OutlinedButton(
                        onClick = {
                            val alive = activity
                            if (alive != null) {
                                viewModel.openBrowserDownload(alive)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
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
                TextButton(onClick = viewModel::dismissForegroundAppUpdateMethodDialog) {
                    Text("取消")
                }
            }
        )
    }

    if (viewModel.isDownloadingAppUpdate) {
        AppBottomSheetDialog(
            onDismissRequest = {},
            style = AppBottomSheetStyle.Status,
            tone = AppBottomSheetTone.Neutral,
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
        AppBottomSheetDialog(
            onDismissRequest = viewModel::dismissInstallAppUpdateDialog,
            style = AppBottomSheetStyle.Form,
            tone = AppBottomSheetTone.Brand,
            title = { Text("下载完成") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("文件：${apk.displayName}")
                    Text("位置：${apk.displayPath}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "大小：${formatBytes(apk.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = {
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
                TextButton(onClick = {
                    val alive = activity
                    if (alive != null) {
                        viewModel.installDownloadedAppUpdate(alive)
                    }
                }) {
                    Text("立即安装")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissInstallAppUpdateDialog) { Text("稍后") }
            }
        )
    }

    if (showDownloadQueueSheet) {
        DownloadQueueSheet(
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
            onDismiss = { showDownloadQueueSheet = false },
            onOpenDownloadPage = {
                showDownloadQueueSheet = false
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

    if (viewModel.showCacheAdminRequiredDialog) {
        AppBottomSheetDialog(
            onDismissRequest = viewModel::dismissCacheAdminRequiredDialog,
            style = AppBottomSheetStyle.Confirm,
            tone = AppBottomSheetTone.Warning,
            icon = { Icon(Icons.Rounded.AdminPanelSettings, null) },
            title = { Text("需要管理员模式") },
            text = {
                Text(
                    viewModel.cacheAdminRequiredMessage,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissCacheAdminRequiredDialog) {
                    Text("知道了")
                }
            }
        )
    }

    if (showCacheQuickDialog) {
        CacheQuickDialog(
            cacheStats = cacheStats,
            cacheEntries = viewModel.cacheEntries.collectAsStateWithLifecycle().value,
            isLoading = isCacheLoading,
            isClearing = viewModel.isClearingCache,
            onRefresh = viewModel::refreshCache,
            onQuickClear = viewModel::quickClearCache,
            onOpenCacheManagement = {
                showCacheQuickDialog = false
                onOpenCacheManagement()
            },
            onDismiss = { showCacheQuickDialog = false }
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

private fun AppAnnouncement.sheetTone(): AppBottomSheetTone {
    return when (severity) {
        AnnouncementSeverity.Info -> AppBottomSheetTone.Info
        AnnouncementSeverity.Success -> AppBottomSheetTone.Success
        AnnouncementSeverity.Warning -> AppBottomSheetTone.Warning
        AnnouncementSeverity.Danger -> AppBottomSheetTone.Danger
    }
}

private fun AppAnnouncement.sheetIcon(): ImageVector {
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
private fun rememberAnnouncementTonePalette(tone: AppBottomSheetTone): AnnouncementTonePalette {
    val c = MaterialTheme.colorScheme
    return when (tone) {
        AppBottomSheetTone.Neutral -> AnnouncementTonePalette(
            iconContainer = c.surfaceContainerHighest,
            iconTint = c.onSurfaceVariant,
            accent = c.onSurfaceVariant,
            statusTint = c.surfaceContainerHigh
        )
        AppBottomSheetTone.Brand -> AnnouncementTonePalette(
            iconContainer = c.primaryContainer,
            iconTint = c.primary,
            accent = c.primary,
            statusTint = c.primaryContainer
        )
        AppBottomSheetTone.Success -> AnnouncementTonePalette(
            iconContainer = c.tertiaryContainer,
            iconTint = c.tertiary,
            accent = c.tertiary,
            statusTint = c.tertiaryContainer
        )
        AppBottomSheetTone.Warning -> AnnouncementTonePalette(
            iconContainer = c.secondaryContainer,
            iconTint = c.onSecondaryContainer,
            accent = c.secondary,
            statusTint = c.secondaryContainer
        )
        AppBottomSheetTone.Danger -> AnnouncementTonePalette(
            iconContainer = c.errorContainer,
            iconTint = c.error,
            accent = c.error,
            statusTint = c.errorContainer
        )
        AppBottomSheetTone.Info -> AnnouncementTonePalette(
            iconContainer = c.secondaryContainer,
            iconTint = c.secondary,
            accent = c.secondary,
            statusTint = c.secondaryContainer
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
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
    val tonePalette = rememberAnnouncementTonePalette(announcement.sheetTone())
    val previewText = announcement.previewText()
    val dialogBodyText = announcement.dialogBodyText()
    val summaryText = announcement.summaryText(previewText)
    val primaryAction = announcement.primaryAction
    val secondaryAction = announcement.secondaryAction
    val hasBusinessActions = primaryAction != null || secondaryAction != null
    val showSnoozeAction = !announcement.forcePopup &&
        !announcement.isShortTerm() &&
        announcement.allowSnoozeToday
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            if (!announcement.forcePopup) onDismissRequest()
        },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = colorScheme.surfaceContainerLow,
        contentColor = colorScheme.onSurface,
        tonalElevation = 1.dp,
        dragHandle = if (!announcement.forcePopup) {
            {
                BottomSheetDefaults.DragHandle(
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.34f)
                )
            }
        } else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.85f)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(
                    top = if (announcement.forcePopup) 16.dp else 4.dp,
                    bottom = 10.dp
                ),
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
                                announcement.sheetIcon(),
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
                        FilledTonalIconButton(
                            onClick = onSnooze,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = tonePalette.statusTint.copy(alpha = 0.6f),
                                contentColor = tonePalette.accent
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsOff,
                                contentDescription = "今日不提醒"
                            )
                        }
                    }
                    FilledTonalIconButton(
                        onClick = onMarkRead,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = tonePalette.statusTint.copy(alpha = 0.6f),
                            contentColor = tonePalette.accent
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "标记已读"
                        )
                    }
                    if (!announcement.forcePopup) {
                        IconButton(onClick = onClose) {
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
                    Surface(
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    secondaryAction?.let { action ->
                        OutlinedButton(
                            onClick = onSecondaryAction,
                            shape = RoundedCornerShape(12.dp)
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
                        Button(
                            onClick = onPrimaryAction,
                            shape = RoundedCornerShape(12.dp),
                            colors = appPrimaryButtonColors()
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
}

@Composable
private fun UnreadAnnouncementListDialog(
    announcements: List<AppAnnouncement>,
    onDismissRequest: () -> Unit,
    onOpenAnnouncement: (AppAnnouncement) -> Unit,
    onAcknowledgeAll: () -> Unit,
) {
    AppBottomSheetDialog(
        onDismissRequest = onDismissRequest,
        style = AppBottomSheetStyle.Selection,
        tone = AppBottomSheetTone.Info,
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
                    OutlinedCard(
                        onClick = { onOpenAnnouncement(announcement) },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(
                            1.dp,
                            if (index == 0) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.outlineVariant
                        ),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (index == 0) {
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
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
            TextButton(onClick = onAcknowledgeAll) {
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
            TextButton(onClick = onDismissRequest) {
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
