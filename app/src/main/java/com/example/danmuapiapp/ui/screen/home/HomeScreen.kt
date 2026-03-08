package com.example.danmuapiapp.ui.screen.home

import com.example.danmuapiapp.ui.component.AppBottomSheetDialog
import com.example.danmuapiapp.ui.component.AppBottomSheetStyle
import com.example.danmuapiapp.ui.component.AppBottomSheetTone

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lan
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
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CacheEntry
import com.example.danmuapiapp.domain.model.CacheStats
import com.example.danmuapiapp.domain.model.DownloadQueueStatus
import com.example.danmuapiapp.domain.model.DanmuDownloadTask
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.ui.component.GithubProxyPickerDialog
import com.example.danmuapiapp.ui.component.GradientButton
import com.example.danmuapiapp.ui.component.StatusIndicator
import com.example.danmuapiapp.ui.screen.download.DanmuDownloadViewModel
import com.example.danmuapiapp.ui.screen.download.DownloadQueueSummary
import com.example.danmuapiapp.ui.theme.appDangerTonalButtonColors
import com.example.danmuapiapp.ui.theme.appPrimaryButtonColors
import com.example.danmuapiapp.ui.theme.appTonalButtonColors
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun HomeScreen(
    onOpenDanmuDownload: () -> Unit = {},
    onOpenCacheManagement: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.runtimeState.collectAsStateWithLifecycle()
    val coreList by viewModel.coreInfoList.collectAsStateWithLifecycle()
    val isCoreInfoLoading by viewModel.isCoreInfoLoading.collectAsStateWithLifecycle()
    val tokenVisible by viewModel.tokenVisible.collectAsStateWithLifecycle()
    val cacheStats by viewModel.cacheStats.collectAsStateWithLifecycle()
    val isCacheLoading by viewModel.isCacheLoading.collectAsStateWithLifecycle()
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

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
    var showCacheQuickDialog by remember { mutableStateOf(false) }
    var isBatteryWhitelisted by remember {
        mutableStateOf(NormalModeKeepAliveGuideNavigator.isIgnoringBatteryOptimizations(context))
    }

    val isRunning = state.status == ServiceStatus.Running
    val isTransitioning = state.status == ServiceStatus.Starting ||
        state.status == ServiceStatus.Stopping
    val currentCoreInfo = coreList.find { it.variant == state.variant }
    val isCoreInstalled = currentCoreInfo?.isInstalled == true
    val currentCoreVersion = currentCoreInfo?.version
    val hasUpdate = currentCoreInfo?.hasUpdate == true
    val latestVersion = currentCoreInfo?.latestVersion
    val isBusy = isTransitioning || viewModel.isInstallingCore ||
        viewModel.isSwitchingCore || viewModel.isUpdatingCore
    val isHeroChipBusy = isBusy || viewModel.isCheckingCoreUpdate
    val uptimeText = if (isRunning) viewModel.formatUptime(state.uptimeSeconds) else "00:00"
    val coreVersionText = when {
        isCoreInfoLoading -> "检测中"
        !isCoreInstalled -> "未安装"
        hasUpdate && !currentCoreVersion.isNullOrBlank() && !latestVersion.isNullOrBlank() ->
            "v$currentCoreVersion→v$latestVersion"
        !currentCoreVersion.isNullOrBlank() -> "v$currentCoreVersion"
        else -> "--"
    }
    val coreVersionBadge = when {
        isCoreInfoLoading -> "读取中"
        !isCoreInstalled -> "需安装"
        hasUpdate -> "有更新"
        else -> null
    }
    val coreVersionAccent = when {
        isCoreInfoLoading -> MaterialTheme.colorScheme.onSurfaceVariant
        !isCoreInstalled -> MaterialTheme.colorScheme.error
        hasUpdate -> MaterialTheme.colorScheme.primary
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
            if (event == Lifecycle.Event.ON_RESUME && state.runMode == RunMode.Normal) {
                isBatteryWhitelisted = NormalModeKeepAliveGuideNavigator.isIgnoringBatteryOptimizations(context)
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
                    hasQueueTasks = hasQueueTasks,
                    isQueueDownloading = isQueueDownloading,
                    isQueuePaused = isQueuePaused,
                    queueSummary = queueSummary,
                    onOpenDownloadSheet = { showDownloadQueueSheet = true }
                )

                MissionControlHero(
                    status = state.status,
                    isCoreInstalled = isCoreInstalled,
                    isCoreInfoLoading = isCoreInfoLoading,
                    runModeLabel = when (state.runMode) {
                        RunMode.Normal -> "普通"
                        RunMode.Root -> "Root"
                    },
                    uptime = uptimeText,
                    variantLabel = state.variant.label,
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
                    onCheckCoreUpdate = { showCoreUpdateConfirmDialog = true },
                )

                AnimatedVisibility(
                    visible = state.runMode == RunMode.Normal && !isBatteryWhitelisted,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    NormalModeBatteryHintCard(
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
                    hasUpdate = hasUpdate,
                    latestVersion = latestVersion
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
            onDismiss = viewModel::dismissNoCoreDialog,
            onInstall = viewModel::installAndStart
        )
    }

    if (showRuntimeInfoDialog) {
        ServiceRuntimeInfoDialog(
            status = state.status,
            uptime = uptimeText,
            runMode = state.runMode,
            variant = state.variant,
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
            variantLabel = state.variant.label,
            currentVersion = currentCoreVersion,
            latestVersion = latestVersion,
            onDismiss = { showCoreUpdateConfirmDialog = false },
            onConfirm = {
                showCoreUpdateConfirmDialog = false
                viewModel.quickCheckCurrentCoreUpdate()
            }
        )
    }

    if (viewModel.showVariantPicker) {
        VariantPickerSheet(
            currentVariant = state.variant,
            coreList = coreList,
            isCoreInfoLoading = isCoreInfoLoading,
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
            variant = viewModel.updatePromptVariant,
            currentVersion = viewModel.updatePromptCurrentVersion,
            latestVersion = viewModel.updatePromptLatestVersion,
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
                    val preview = viewModel.appUpdatePromptReleaseNotes.trim().take(260)
                    if (preview.isNotBlank()) {
                        Text(
                            preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
