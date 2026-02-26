package com.example.danmuapiapp.ui.screen.home

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
        AlertDialog(
            onDismissRequest = {
                showRunModePickerDialog = false
                pendingRunModeTarget = null
            },
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
        AlertDialog(
            onDismissRequest = viewModel::dismissForegroundAppUpdatePrompt,
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
        AlertDialog(
            onDismissRequest = viewModel::dismissForegroundAppUpdateMethodDialog,
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
        AlertDialog(
            onDismissRequest = {},
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
            confirmButton = {}
        )
    }

    if (viewModel.showInstallAppUpdateDialog && viewModel.downloadedAppUpdate != null) {
        val apk = viewModel.downloadedAppUpdate!!
        AlertDialog(
            onDismissRequest = viewModel::dismissInstallAppUpdateDialog,
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

@Composable
private fun HomePanelDialog(
    onDismissRequest: () -> Unit,
    icon: ImageVector,
    title: String,
    subtitle: String,
    canDismiss: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit
) {
    Dialog(
        onDismissRequest = {
            if (canDismiss) onDismissRequest()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .widthIn(max = 540.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
            shadowElevation = 20.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f)
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.88f)
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                content()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content = actions
                )
            }
        }
    }
}

@Composable
private fun DialogActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    primary: Boolean = false
) {
    if (primary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text)
        }
    }
}

@Composable
private fun CacheQuickDialog(
    cacheStats: CacheStats,
    cacheEntries: List<CacheEntry>,
    isLoading: Boolean,
    isClearing: Boolean,
    onRefresh: () -> Unit,
    onQuickClear: () -> Unit,
    onOpenCacheManagement: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val subtitle = if (cacheStats.isAvailable) {
        "${cacheStats.reqRecordsCount} 条记录 · 今日 ${cacheStats.todayReqNum} 次请求"
    } else {
        "服务未运行，暂无缓存数据"
    }

    HomePanelDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Rounded.Storage,
        title = "缓存概览",
        subtitle = subtitle,
        content = {
            val dialogScroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(dialogScroll),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (cacheStats.isAvailable) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CacheStatBadge(
                            modifier = Modifier.weight(1f),
                            label = "请求记录",
                            value = "${cacheStats.reqRecordsCount}"
                        )
                        CacheStatBadge(
                            modifier = Modifier.weight(1f),
                            label = "今日请求",
                            value = "${cacheStats.todayReqNum}"
                        )
                        CacheStatBadge(
                            modifier = Modifier.weight(1f),
                            label = "上次清理",
                            value = cacheStats.lastClearedAt?.let {
                                dateFormat.format(Date(it))
                            } ?: "从未"
                        )
                    }

                    if (cacheEntries.isNotEmpty()) {
                        Text(
                            text = "最近请求",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            cacheEntries.take(4).forEach { entry ->
                                CacheEntryPreviewRow(entry = entry)
                            }
                        }
                    } else {
                        Text(
                            text = "暂无请求记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.CloudOff,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "启动服务后可查看缓存数据",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        actions = {
            DialogActionButton(
                text = "关闭",
                icon = Icons.Rounded.Close,
                onClick = onDismiss
            )
            if (isLoading) {
                DialogActionButton(
                    text = "刷新中",
                    icon = Icons.Rounded.Refresh,
                    onClick = {},
                    enabled = false
                )
            } else {
                DialogActionButton(
                    text = "刷新",
                    icon = Icons.Rounded.Refresh,
                    onClick = onRefresh
                )
            }
            DialogActionButton(
                text = if (isClearing) "清理中…" else "快速清理",
                icon = Icons.Rounded.DeleteSweep,
                onClick = onQuickClear,
                enabled = cacheStats.isAvailable && !isClearing && !isLoading
            )
            DialogActionButton(
                text = "缓存管理",
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                onClick = onOpenCacheManagement,
                primary = true
            )
        }
    )
}

@Composable
private fun CacheStatBadge(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.78f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CacheEntryPreviewRow(entry: CacheEntry) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val methodColor = when (entry.type.uppercase()) {
        "GET" -> MaterialTheme.colorScheme.primary
        "POST" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusCode = entry.hitCount.takeIf { it in 100..599 }
    val statusColor = when {
        statusCode != null && statusCode in 200..299 -> MaterialTheme.colorScheme.primary
        statusCode != null && statusCode >= 400 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (entry.type.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = methodColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        entry.type.uppercase(),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = methodColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                entry.key.ifBlank { "未知接口" },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (statusCode != null) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        "$statusCode",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }
            Text(
                dateFormat.format(Date(entry.createdAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun QuickPortDialog(
    isBusy: Boolean,
    status: ServiceStatus,
    runMode: RunMode,
    currentPort: Int,
    quickPortText: String,
    quickPortError: String?,
    onPortTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit,
    onPortError: (String) -> Unit
) {
    val modeHint = if (runMode == RunMode.Normal) {
        "普通模式支持 1024 – 65535（Root 支持 1 – 65535）"
    } else {
        "Root 模式支持 1 – 65535"
    }
    val subtitle = if (status == ServiceStatus.Running) {
        "保存后自动重启服务以应用新端口"
    } else {
        "当前未运行，保存后写入启动配置"
    }

    HomePanelDialog(
        onDismissRequest = onDismiss,
        canDismiss = !isBusy,
        icon = Icons.Rounded.Lan,
        title = "快速修改端口",
        subtitle = subtitle,
        content = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
                )
            ) {
                Text(
                    text = "当前端口：TCP $currentPort · $modeHint",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val quickPorts = if (runMode == RunMode.Root) {
                listOf("80", "9321", "2233")
            } else {
                listOf("9321", "2233", "5000")
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickPorts.forEach { target ->
                    DialogActionButton(
                        text = target,
                        icon = Icons.Rounded.Tune,
                        onClick = { onPortTextChange(target) },
                        enabled = !isBusy && quickPortText != target,
                        primary = quickPortText == target
                    )
                }
            }

            OutlinedTextField(
                value = quickPortText,
                onValueChange = onPortTextChange,
                label = { Text("端口") },
                singleLine = true,
                isError = quickPortError != null,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                supportingText = {
                    Text(quickPortError ?: modeHint)
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        actions = {
            DialogActionButton(
                text = "取消",
                icon = Icons.Rounded.Close,
                onClick = onDismiss,
                enabled = !isBusy
            )
            DialogActionButton(
                text = if (status == ServiceStatus.Running) "保存并重启" else "保存",
                icon = if (status == ServiceStatus.Running) {
                    Icons.Rounded.PowerSettingsNew
                } else {
                    Icons.Rounded.CheckCircle
                },
                onClick = {
                    val port = quickPortText.trim().toIntOrNull()
                    if (port == null || port !in 1..65535) {
                        onPortError("请输入有效端口（1-65535）")
                        return@DialogActionButton
                    }
                    if (runMode == RunMode.Normal && port in 1..1023) {
                        onPortError("普通模式仅支持 1024-65535，请切换 Root 模式后再使用低位端口")
                        return@DialogActionButton
                    }
                    if (port == currentPort) {
                        onPortError("端口未变化")
                        return@DialogActionButton
                    }
                    onApply(port)
                },
                enabled = !isBusy,
                primary = true
            )
        }
    )
}

@Composable
private fun QuickTokenDialog(
    isBusy: Boolean,
    status: ServiceStatus,
    currentToken: String,
    quickTokenText: String,
    quickTokenError: String?,
    onTokenTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
    onTokenError: (String) -> Unit
) {
    val subtitle = if (status == ServiceStatus.Running) {
        "保存后会热更新到运行中服务"
    } else {
        "保存后会写入运行配置"
    }
    val preview = when {
        quickTokenText.isBlank() -> "空（将恢复核心默认 Token）"
        quickTokenText.length <= 8 -> quickTokenText
        else -> "${quickTokenText.take(4)}****${quickTokenText.takeLast(2)}"
    }

    HomePanelDialog(
        onDismissRequest = onDismiss,
        canDismiss = !isBusy,
        icon = Icons.Rounded.Tune,
        title = "快速修改 Token",
        subtitle = subtitle,
        content = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "当前：${if (currentToken.isBlank()) "空（默认）" else currentToken}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "待保存：$preview",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            OutlinedTextField(
                value = quickTokenText,
                onValueChange = onTokenTextChange,
                label = { Text("Token（可留空）") },
                singleLine = true,
                isError = quickTokenError != null,
                shape = RoundedCornerShape(14.dp),
                supportingText = {
                    Text(quickTokenError ?: "留空将恢复核心默认 Token")
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        actions = {
            DialogActionButton(
                text = "取消",
                icon = Icons.Rounded.Close,
                onClick = onDismiss,
                enabled = !isBusy
            )
            DialogActionButton(
                text = "保存",
                icon = Icons.Rounded.CheckCircle,
                onClick = {
                    val normalized = quickTokenText.trim()
                    if (normalized == currentToken) {
                        onTokenError("Token 未变化")
                        return@DialogActionButton
                    }
                    onApply(normalized)
                },
                enabled = !isBusy,
                primary = true
            )
        }
    )
}

@Composable
private fun CoreUpdateConfirmDialog(
    variantLabel: String,
    currentVersion: String?,
    latestVersion: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    HomePanelDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Rounded.SystemUpdateAlt,
        title = "检查核心更新",
        subtitle = "检查当前核心分支是否有可用更新",
        content = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "分支：$variantLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "当前版本：${currentVersion?.let { "v$it" } ?: "--"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "远程版本：${latestVersion?.let { "v$it" } ?: "待查询"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        actions = {
            DialogActionButton(
                text = "取消",
                icon = Icons.Rounded.Close,
                onClick = onDismiss
            )
            DialogActionButton(
                text = "继续检查",
                icon = Icons.Rounded.SystemUpdate,
                onClick = onConfirm,
                primary = true
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadQueueSheet(
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
    onExpandedQueueGroupKeysChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    onOpenDownloadPage: () -> Unit,
    onTogglePauseResume: () -> Unit,
    onClearQueue: () -> Unit
) {
    val completed = queueSummary.success + queueSummary.skipped + queueSummary.canceled
    val displayProgress = queueLiveProgress.coerceIn(0f, 1f)
    val displayPercent = (displayProgress * 100f).toInt().coerceIn(0, 100)
    val showRunningDetail = queueRunningDetail.isNotBlank() &&
        queueRunningDetail != "当前无运行中的任务"
    val accentColor = if (isQueueDownloading) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor.copy(alpha = 0.14f)
                ) {
                    Box(
                        modifier = Modifier.size(36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DownloadForOffline,
                            contentDescription = null,
                            tint = accentColor
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "下载任务队列",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "总任务 ${queueSummary.total} · 待处理 ${queueSummary.pending} · 运行 ${queueSummary.running}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Status badges
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        isQueueDownloading -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        isQueuePaused -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    }
                ) {
                    Text(
                        queueStatusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isQueueDownloading -> MaterialTheme.colorScheme.primary
                            isQueuePaused -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        "总进度 $displayPercent%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Progress bar
            LinearProgressIndicator(
                progress = { displayProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp)),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                color = accentColor
            )
            Text(
                text = "已完成 $completed/${queueSummary.total.coerceAtLeast(0)} · 失败 ${queueSummary.failed}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (showRunningDetail) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = queueRunningDetail,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = queueProgressSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!queueThrottleHint.isNullOrBlank()) {
                Text(
                    text = queueThrottleHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Metric badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QueueMetricBadge(modifier = Modifier.weight(1f), label = "待处理", value = queueSummary.pending)
                QueueMetricBadge(modifier = Modifier.weight(1f), label = "运行", value = queueSummary.running)
                QueueMetricBadge(modifier = Modifier.weight(1f), label = "完成", value = completed)
                QueueMetricBadge(modifier = Modifier.weight(1f), label = "失败", value = queueSummary.failed)
            }

            // Task groups
            val sheetScroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(sheetScroll),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (queueDialogGroups.isEmpty()) {
                    Text(
                        text = "队列暂无任务",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "任务分组",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    queueDialogGroups.forEach { group ->
                        val expanded = expandedQueueGroupKeys.contains(group.key)
                        DownloadQueueGroupCard(
                            group = group,
                            expanded = expanded,
                            onToggle = {
                                onExpandedQueueGroupKeysChange(
                                    if (expanded) {
                                        expandedQueueGroupKeys - group.key
                                    } else {
                                        expandedQueueGroupKeys + group.key
                                    }
                                )
                            }
                        )
                    }
                }
            }

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DialogActionButton(
                    text = "下载页",
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    onClick = onOpenDownloadPage
                )
                DialogActionButton(
                    text = if (isQueueDownloading) "暂停" else "继续",
                    icon = if (isQueueDownloading) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    onClick = onTogglePauseResume,
                    enabled = if (isQueueDownloading) true else isQueuePaused,
                    primary = isQueueDownloading || isQueuePaused
                )
                DialogActionButton(
                    text = "清空",
                    icon = Icons.Rounded.CloudOff,
                    onClick = onClearQueue,
                    enabled = !isQueueDownloading && queueSummary.total > 0
                )
            }
        }
    }
}

private data class DownloadQueueDialogGroup(
    val key: String,
    val animeTitle: String,
    val total: Int,
    val pending: Int,
    val running: Int,
    val completed: Int,
    val failed: Int,
    val tasks: List<DanmuDownloadTask>
)

private fun buildDownloadQueueGroups(tasks: List<DanmuDownloadTask>): List<DownloadQueueDialogGroup> {
    if (tasks.isEmpty()) return emptyList()

    val indexed = tasks.withIndex()
    val grouped = indexed.groupBy { indexedTask ->
        indexedTask.value.animeTitle.trim().ifBlank { "未命名剧集" }
    }
    return grouped.entries
        .map { (title, entries) ->
            val rawTasks = entries.map { it.value }
            var pending = 0
            var running = 0
            var success = 0
            var skipped = 0
            var canceled = 0
            var failed = 0
            rawTasks.forEach { task ->
                when (task.statusEnum()) {
                    DownloadQueueStatus.Pending -> pending++
                    DownloadQueueStatus.Running -> running++
                    DownloadQueueStatus.Success -> success++
                    DownloadQueueStatus.Skipped -> skipped++
                    DownloadQueueStatus.Canceled -> canceled++
                    DownloadQueueStatus.Failed -> failed++
                }
            }
            DownloadQueueDialogGroup(
                key = title,
                animeTitle = title,
                total = rawTasks.size,
                pending = pending,
                running = running,
                completed = success + skipped + canceled,
                failed = failed,
                tasks = rawTasks.sortedWith(
                    compareBy<DanmuDownloadTask> { it.episodeNo }
                        .thenBy { it.source.lowercase() }
                        .thenByDescending { it.updatedAt }
                )
            ) to (entries.minOfOrNull { it.index } ?: Int.MAX_VALUE)
        }
        .sortedBy { it.second }
        .map { it.first }
}

@Composable
private fun DownloadQueueGroupCard(
    group: DownloadQueueDialogGroup,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = group.animeTitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = "共 ${group.total} · 待 ${group.pending} · 运行 ${group.running} · 完成 ${group.completed} · 失败 ${group.failed}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    group.tasks.forEach { task ->
                        DownloadQueueTaskRow(task = task)
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueMetricBadge(
    modifier: Modifier = Modifier,
    label: String,
    value: Int
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.78f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value.coerceAtLeast(0).toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DownloadQueueTaskRow(task: DanmuDownloadTask) {
    val status = task.statusEnum()
    val statusText = when (status) {
        DownloadQueueStatus.Pending -> "待处理"
        DownloadQueueStatus.Running -> "运行中"
        DownloadQueueStatus.Success -> "成功"
        DownloadQueueStatus.Failed -> "失败"
        DownloadQueueStatus.Skipped -> "跳过"
        DownloadQueueStatus.Canceled -> "已取消"
    }
    val statusColor = when (status) {
        DownloadQueueStatus.Pending -> MaterialTheme.colorScheme.tertiary
        DownloadQueueStatus.Running -> MaterialTheme.colorScheme.primary
        DownloadQueueStatus.Success -> Color(0xFF2E7D32)
        DownloadQueueStatus.Failed -> MaterialTheme.colorScheme.error
        DownloadQueueStatus.Skipped,
        DownloadQueueStatus.Canceled -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val displayTitle = task.animeTitle.trim().ifBlank { "未命名剧集" }
    val displaySource = task.source.trim().ifBlank { "unknown" }
    val detailText = task.lastDetail.trim()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$displayTitle · 第${task.episodeNo}集",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = statusColor.copy(alpha = 0.13f)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Text(
                text = "来源 $displaySource · 尝试 ${task.attempts} 次",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            if (detailText.isNotBlank()) {
                Text(
                    text = detailText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeTopHeader(
    status: ServiceStatus,
    isRunning: Boolean,
    uptime: String,
    hasQueueTasks: Boolean = false,
    isQueueDownloading: Boolean = false,
    isQueuePaused: Boolean = false,
    queueSummary: DownloadQueueSummary = DownloadQueueSummary(),
    onOpenDownloadSheet: () -> Unit = {}
) {
    val downloadText = when {
        isQueueDownloading -> "正在下载 · 待 ${queueSummary.pending} 运行 ${queueSummary.running.coerceAtLeast(1)}"
        isQueuePaused -> "下载已暂停 · 待 ${queueSummary.pending}"
        else -> "队列 ${queueSummary.total} 个任务 · 完成 ${queueSummary.success}"
    }
    val downloadAccent = when {
        isQueueDownloading -> Color(0xFF4CAF50)
        isQueuePaused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "弹幕 API",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "LogVar 弹幕服务",
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.3.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusIndicator(status = status, modifier = Modifier.size(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = if (isRunning) "在线" else "离线",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = statusAccentColor(status)
                    )
                }
            }
        }
        if (hasQueueTasks) {
            Text(
                text = downloadText,
                style = MaterialTheme.typography.bodySmall,
                color = downloadAccent,
                maxLines = 1,
                modifier = Modifier
                    .clickable(onClick = onOpenDownloadSheet)
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        velocity = 30.dp
                    )
            )
        } else if (isRunning) {
            Text(
                text = "已连续运行 $uptime",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MissionControlHero(
    status: ServiceStatus,
    isCoreInstalled: Boolean,
    isCoreInfoLoading: Boolean,
    runModeLabel: String,
    uptime: String,
    variantLabel: String,
    isRunning: Boolean,
    isInstalling: Boolean,
    isSwitching: Boolean,
    isUpdating: Boolean,
    isActionBusy: Boolean,
    isDarkTheme: Boolean,
    onToggleRunMode: () -> Unit,
    onOpenVariantPicker: () -> Unit,
    onOpenRuntimeInfo: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "heroOrb")
    val orbRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbRotation"
    )
    val showMissingCore = !isCoreInfoLoading && !isCoreInstalled
    val heroAccent = if (showMissingCore) {
        MaterialTheme.colorScheme.error
    } else {
        statusAccentColor(status)
    }
    val heroTitle = if (showMissingCore) {
        "核心未安装"
    } else {
        statusTitle(
            status = status,
            isInstalling = isInstalling,
            isSwitching = isSwitching,
            isUpdating = isUpdating
        )
    }
    val heroSubtitle = if (showMissingCore) {
        "当前${variantLabel}尚未安装，请先下载核心后再启动服务"
    } else {
        statusSubtitle(
            status = status,
            isInstalling = isInstalling,
            isSwitching = isSwitching,
            isUpdating = isUpdating
        )
    }
    val heroIcon = if (showMissingCore) {
        Icons.Rounded.DownloadForOffline
    } else {
        statusIcon(status)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = if (isDarkTheme) {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.84f)
        },
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(
                alpha = if (isDarkTheme) 0.42f else 0.32f
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(
                                alpha = if (isDarkTheme) 0.08f else 0.13f
                            ),
                            MaterialTheme.colorScheme.tertiary.copy(
                                alpha = if (isDarkTheme) 0.05f else 0.09f
                            ),
                            Color.Transparent
                        )
                    )
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = heroTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = heroSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .rotate(if (isRunning) orbRotation else 0f)
                            .clip(CircleShape)
                            .background(
                                Brush.sweepGradient(
                                    listOf(
                                        heroAccent.copy(alpha = 0.28f),
                                        Color.Transparent,
                                        heroAccent.copy(alpha = 0.08f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        color = if (isDarkTheme) {
                            MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.98f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isInstalling || isSwitching || isUpdating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(26.dp),
                                    strokeWidth = 2.6.dp
                                )
                            } else {
                                Icon(
                                    imageVector = heroIcon,
                                    contentDescription = null,
                                    tint = heroAccent,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoChip(
                    modifier = Modifier.weight(1f),
                    label = "模式",
                    value = runModeLabel,
                    icon = Icons.Rounded.PowerSettingsNew,
                    enabled = !isActionBusy,
                    onClick = onToggleRunMode
                )
                InfoChip(
                    modifier = Modifier.weight(1f),
                    label = "核心",
                    value = variantLabel,
                    icon = Icons.Rounded.SwapHoriz,
                    enabled = !isActionBusy,
                    onClick = onOpenVariantPicker
                )
                InfoChip(
                    modifier = Modifier.weight(1f),
                    label = "已运行",
                    value = uptime,
                    icon = Icons.Rounded.HourglassTop,
                    accent = statusAccentColor(status),
                    onClick = onOpenRuntimeInfo
                )
            }
        }
    }
}

@Composable
private fun NormalModeBatteryHintCard(
    onOpenBatterySettings: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(6.dp)
                        .size(16.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "普通模式建议关闭电池优化",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "请将本应用电池策略设为“不受限制”，减少后台被系统清理。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(
                onClick = onOpenBatterySettings,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("去设置")
            }
        }
    }
}

@Composable
private fun SnapshotStrip(
    status: ServiceStatus,
    isDarkTheme: Boolean,
    runMode: RunMode,
    cacheTileValue: String,
    cacheTileBadge: String?,
    cacheTileAccent: Color,
    onOpenCacheQuick: () -> Unit,
    token: String,
    maskedToken: String,
    tokenVisible: Boolean,
    onEditToken: () -> Unit,
    port: Int,
    coreVersionText: String,
    coreVersionBadge: String?,
    coreVersionAccent: Color,
    isActionBusy: Boolean,
    onEditPort: () -> Unit,
    onCheckCoreUpdate: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = if (isDarkTheme) {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    title = "缓存管理",
                    value = cacheTileValue,
                    badge = cacheTileBadge,
                    accent = cacheTileAccent,
                    onClick = onOpenCacheQuick
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    title = "端口",
                    value = "TCP $port",
                    badge = when {
                        runMode == RunMode.Root && port == 80 -> "默认端口"
                        status == ServiceStatus.Running -> "监听中"
                        else -> "未监听"
                    },
                    accent = MaterialTheme.colorScheme.onSurface,
                    enabled = !isActionBusy,
                    onClick = onEditPort
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TokenMetricTile(
                    modifier = Modifier.weight(1f),
                    token = token,
                    maskedToken = maskedToken,
                    tokenVisible = tokenVisible,
                    onEditToken = onEditToken
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    title = "核心版本",
                    value = coreVersionText,
                    badge = coreVersionBadge,
                    accent = coreVersionAccent,
                    enabled = !isActionBusy,
                    onClick = onCheckCoreUpdate
                )
            }
        }
    }
}

@Composable
private fun ServiceRuntimeInfoDialog(
    status: ServiceStatus,
    uptime: String,
    runMode: RunMode,
    variant: ApiVariant,
    port: Int,
    pid: Int?,
    localUrl: String,
    lanUrl: String,
    token: String,
    maskedToken: String,
    tokenVisible: Boolean,
    onDismiss: () -> Unit
) {
    val displayLocal = maskRuntimeUrl(localUrl, token, maskedToken, tokenVisible)
    val displayLan = maskRuntimeUrl(lanUrl, token, maskedToken, tokenVisible)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.HourglassBottom, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("服务运行信息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusAccentColor(status).copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = statusShortLabel(status),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusAccentColor(status),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "运行 $uptime",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                RuntimeInfoItem(label = "运行模式", value = runMode.label)
                RuntimeInfoItem(label = "核心通道", value = variant.label)
                RuntimeInfoItem(label = "监听端口", value = "TCP $port")
                if (pid != null) {
                    RuntimeInfoItem(label = "进程 PID", value = pid.toString())
                }
                RuntimeInfoItem(label = "本机地址", value = displayLocal, mono = true)
                RuntimeInfoItem(label = "局域网地址", value = displayLan, mono = true)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("我知道了")
            }
        }
    )
}

@Composable
private fun RuntimeInfoItem(
    label: String,
    value: String,
    mono: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (value.isBlank()) "未生成" else value,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ActionDeck(
    isRunning: Boolean,
    isTransitioning: Boolean,
    isStarting: Boolean,
    isInstalling: Boolean,
    isSwitching: Boolean,
    isUpdating: Boolean,
    isDarkTheme: Boolean,
    onToggle: () -> Unit,
    onRestart: () -> Unit,
    onOpenVariantPicker: () -> Unit,
    onOpenCoreDownload: () -> Unit,
    onOpenUpdatePrompt: () -> Unit,
    isCoreInstalled: Boolean,
    hasUpdate: Boolean,
    latestVersion: String?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDarkTheme) 0.34f else 0.24f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GradientButton(
                    text = when {
                        isSwitching -> "核心切换中"
                        isUpdating -> "核心更新中"
                        isInstalling -> "核心下载中"
                        isRunning -> "停止服务"
                        else -> "启动服务"
                    },
                    onClick = onToggle,
                    enabled = !isTransitioning,
                    modifier = Modifier.weight(1f),
                    colors = if (isRunning) {
                        if (isDarkTheme) {
                            listOf(Color(0xFFDC2626), Color(0xFFEA580C))
                        } else {
                            listOf(Color(0xFFD63E2F), Color(0xFFF46B3B))
                        }
                    } else {
                        if (isDarkTheme) {
                            listOf(Color(0xFF2563EB), Color(0xFF6366F1))
                        } else {
                            listOf(Color(0xFF1E88E5), Color(0xFF4E5BDD))
                        }
                    },
                    disabledColors = if (isStarting) {
                        if (isDarkTheme) {
                            listOf(Color(0xFF1A3A5C), Color(0xFF2D3566))
                        } else {
                            listOf(Color(0xFF93AACC), Color(0xFF9A9DD0))
                        }
                    } else {
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    }
                )
                AnimatedVisibility(
                    visible = isRunning,
                    enter = expandHorizontally() + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut()
                ) {
                    FilledTonalButton(
                        onClick = onRestart,
                        enabled = !isTransitioning,
                        modifier = Modifier.height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isDarkTheme) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.17f)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
                            },
                            contentColor = if (isDarkTheme) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    ) {
                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("重启")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenVariantPicker,
                    enabled = !isTransitioning,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.SwapHoriz, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("切换核心")
                }
                FilledTonalButton(
                    onClick = {
                        if (!isCoreInstalled) {
                            onOpenCoreDownload()
                        } else {
                            onOpenUpdatePrompt()
                        }
                    },
                    enabled = (!isTransitioning) && (!isCoreInstalled || hasUpdate),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (!isCoreInstalled || hasUpdate) {
                            if (isDarkTheme) Color(0xFFDC2626) else Color(0xFFD63E2F)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        contentColor = if (!isCoreInstalled || hasUpdate) {
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                ) {
                    Icon(
                        imageVector = if (!isCoreInstalled) Icons.Rounded.Download else Icons.Rounded.SystemUpdateAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (!isCoreInstalled) {
                            "点击下载"
                        } else if (hasUpdate) {
                            "更新 ${latestVersion?.let { "v$it" } ?: "核心"}"
                        } else {
                            "暂无更新"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AccessGatewayPanel(
    localUrl: String,
    lanUrl: String,
    token: String,
    maskedToken: String,
    tokenVisible: Boolean,
    onToggleTokenVisible: () -> Unit,
    onCopyLocal: () -> Unit,
    onCopyLan: () -> Unit
) {
    val displayLocal = maskRuntimeUrl(localUrl, token, maskedToken, tokenVisible)
    val displayLan = maskRuntimeUrl(lanUrl, token, maskedToken, tokenVisible)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "访问入口",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(
                    onClick = onToggleTokenVisible,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = if (tokenVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = "切换地址 Token 可见",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            GatewayItem(
                title = "局域网",
                subtitle = "推荐在同一 Wi-Fi 下使用",
                value = displayLan,
                onCopy = onCopyLan,
                emphasize = true
            )
            GatewayItem(
                title = "本机",
                subtitle = "仅当前设备可访问",
                value = displayLocal,
                onCopy = onCopyLocal,
                emphasize = false
            )
        }
    }
}

@Composable
private fun GatewayItem(
    title: String,
    subtitle: String,
    value: String,
    onCopy: () -> Unit,
    emphasize: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (emphasize) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (emphasize) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        ) {
                            Text(
                                "推荐",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = if (emphasize) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1
                )
            }
            FilledTonalIconButton(
                onClick = onCopy,
                modifier = Modifier.size(34.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun TokenMetricTile(
    modifier: Modifier,
    token: String,
    maskedToken: String,
    tokenVisible: Boolean,
    onEditToken: () -> Unit
) {
    val displayToken = if (tokenVisible) token.ifBlank { "（未设置）" } else maskedToken

    Surface(
        modifier = modifier.clickable(onClick = onEditToken),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Token",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = displayToken,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                ),
                color = if (token.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MetricTile(
    modifier: Modifier,
    title: String,
    value: String,
    badge: String? = null,
    accent: Color,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val containerModifier = if (onClick != null) {
        modifier.clickable(enabled = enabled, onClick = onClick)
    } else {
        modifier
    }
    Surface(
        modifier = containerModifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
            alpha = if (enabled) 0.8f else 0.58f
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (enabled) 1f else 0.7f
                    )
                )
                if (!badge.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                ),
                color = accent.copy(alpha = if (enabled) 1f else 0.7f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun InfoChip(
    modifier: Modifier,
    label: String,
    value: String,
    icon: ImageVector,
    badge: String? = null,
    accent: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val containerModifier = if (onClick != null) {
        modifier.clickable(enabled = enabled, onClick = onClick)
    } else {
        modifier
    }
    Surface(
        modifier = containerModifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
            alpha = if (enabled) 0.72f else 0.55f
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.6f)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (enabled) 1f else 0.7f
                        )
                    )
                    if (!badge.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = accent.copy(alpha = if (enabled) 1f else 0.72f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun UpdatePromptDialog(
    variant: ApiVariant?,
    currentVersion: String?,
    latestVersion: String?,
    onUpdate: () -> Unit,
    onIgnore: () -> Unit
) {
    if (variant == null || latestVersion.isNullOrBlank()) return
    AlertDialog(
        onDismissRequest = onIgnore,
        icon = { Icon(Icons.Rounded.SystemUpdateAlt, null) },
        title = { Text("发现核心更新") },
        text = {
            val from = currentVersion?.takeIf { it.isNotBlank() } ?: "?"
            Text("${variant.label}：v$from → v$latestVersion")
        },
        confirmButton = { TextButton(onClick = onUpdate) { Text("更新") } },
        dismissButton = { TextButton(onClick = onIgnore) { Text("忽略") } }
    )
}

@Composable
private fun NoCoreDialog(
    currentVariant: ApiVariant,
    onDismiss: () -> Unit,
    onInstall: (ApiVariant) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.DownloadForOffline, null) },
        title = { Text("核心未安装") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("当前选择的 ${currentVariant.label} 尚未安装，请选择要下载的版本：")
                Text(
                    "下载完成后会自动切换到所选核心并启动服务。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ApiVariant.entries.filter { it.repo.isNotBlank() }.forEach { variant ->
                    OutlinedCard(
                        onClick = { onInstall(variant) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                variantIcon(variant),
                                null,
                                tint = variantAccent(variant),
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(variant.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    variant.repo,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun VariantPickerSheet(
    currentVariant: ApiVariant,
    coreList: List<com.example.danmuapiapp.domain.model.CoreInfo>,
    isCoreInfoLoading: Boolean,
    isBusy: Boolean,
    onSelect: (ApiVariant) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "切换 API 核心",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            if (isBusy) {
                Text(
                    "正在切换，请稍候…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }
            ApiVariant.entries.forEach { variant ->
                val info = coreList.find { it.variant == variant }
                val isSelected = variant == currentVariant
                Card(
                    onClick = { if (!isBusy) onSelect(variant) },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            variantIcon(variant),
                            null,
                            tint = variantAccent(variant),
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(variant.label, style = MaterialTheme.typography.titleMedium)
                            if (info?.version != null) {
                                val vText = if (info.hasUpdate && info.latestVersion != null) {
                                    "v${info.version} → v${info.latestVersion}"
                                } else {
                                    "v${info.version}"
                                }
                                Text(
                                    vText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (info.hasUpdate) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            if (variant.repo.isNotBlank()) {
                                Text(
                                    variant.repo,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = when {
                                isCoreInfoLoading -> MaterialTheme.colorScheme.surfaceContainerHighest
                                info?.isInstalled == true && info.hasUpdate -> MaterialTheme.colorScheme.primaryContainer
                                info?.isInstalled == true -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                else -> MaterialTheme.colorScheme.errorContainer
                            }
                        ) {
                            Text(
                                text = when {
                                    isCoreInfoLoading -> "加载中"
                                    info?.isInstalled == true && info.hasUpdate -> "有更新"
                                    info?.isInstalled == true -> "已安装"
                                    else -> "未安装"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    isCoreInfoLoading -> MaterialTheme.colorScheme.onSurfaceVariant
                                    info?.isInstalled == true && info.hasUpdate -> MaterialTheme.colorScheme.primary
                                    info?.isInstalled == true -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.error
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun variantIcon(variant: ApiVariant): ImageVector {
    return when (variant) {
        ApiVariant.Stable -> Icons.Rounded.Verified
        ApiVariant.Dev -> Icons.Rounded.Science
        ApiVariant.Custom -> Icons.Rounded.Tune
    }
}

@Composable
private fun variantAccent(variant: ApiVariant): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (variant) {
        ApiVariant.Stable -> if (dark) Color(0xFF4ADE80) else Color(0xFF43A047)
        ApiVariant.Dev    -> if (dark) Color(0xFFFBBF24) else Color(0xFFE0A106)
        ApiVariant.Custom -> if (dark) Color(0xFFA78BFA) else Color(0xFF7E57C2)
    }
}

private fun statusIcon(status: ServiceStatus): ImageVector {
    return when (status) {
        ServiceStatus.Running -> Icons.Rounded.PlayArrow
        ServiceStatus.Starting -> Icons.Rounded.HourglassTop
        ServiceStatus.Stopping -> Icons.Rounded.HourglassBottom
        ServiceStatus.Error -> Icons.Rounded.ErrorOutline
        ServiceStatus.Stopped -> Icons.Rounded.Stop
    }
}

private fun statusTitle(
    status: ServiceStatus,
    isInstalling: Boolean,
    isSwitching: Boolean,
    isUpdating: Boolean
): String {
    return when {
        isSwitching -> "正在切换核心"
        isUpdating -> "正在更新核心"
        isInstalling -> "正在下载核心"
        else -> when (status) {
            ServiceStatus.Running -> "服务运行中"
            ServiceStatus.Starting -> "服务启动中"
            ServiceStatus.Stopping -> "服务停止中"
            ServiceStatus.Error -> "服务异常"
            ServiceStatus.Stopped -> "服务已停止"
        }
    }
}

private fun statusSubtitle(
    status: ServiceStatus,
    isInstalling: Boolean,
    isSwitching: Boolean,
    isUpdating: Boolean
): String {
    return when {
        isSwitching -> "将自动重启并切换到目标核心"
        isUpdating -> "核心包下载并替换中"
        isInstalling -> "首次安装期间请保持网络畅通"
        else -> when (status) {
            ServiceStatus.Running -> "接口已就绪，可直接在局域网访问"
            ServiceStatus.Starting -> "正在初始化运行环境，请稍候"
            ServiceStatus.Stopping -> "正在安全停止服务进程"
            ServiceStatus.Error -> "请查看日志或重新启动服务"
            ServiceStatus.Stopped -> "点击启动后将拉起服务进程"
        }
    }
}

private fun statusShortLabel(status: ServiceStatus): String {
    return when (status) {
        ServiceStatus.Running -> "运行"
        ServiceStatus.Starting -> "启动中"
        ServiceStatus.Stopping -> "停止中"
        ServiceStatus.Error -> "异常"
        ServiceStatus.Stopped -> "停止"
    }
}

private fun maskRuntimeUrl(
    rawUrl: String,
    token: String,
    maskedToken: String,
    tokenVisible: Boolean
): String {
    if (rawUrl.isBlank()) return ""
    val masked = if (tokenVisible || token.isBlank()) rawUrl else rawUrl.replace(token, maskedToken)
    return compactDefaultPort(masked)
}

private fun compactDefaultPort(rawUrl: String): String {
    if (rawUrl.isBlank()) return rawUrl
    return runCatching {
        val uri = URI(rawUrl)
        val scheme = uri.scheme?.lowercase(Locale.ROOT).orEmpty()
        val defaultPort = when (scheme) {
            "http" -> 80
            "https" -> 443
            else -> -1
        }
        if (defaultPort <= 0 || uri.port <= 0 || uri.port != defaultPort) {
            return@runCatching rawUrl
        }

        val host = uri.host ?: return@runCatching rawUrl
        val userInfo = uri.rawUserInfo?.let { "$it@" }.orEmpty()
        val hostPart = if (host.contains(':')) "[$host]" else host
        val path = uri.rawPath.orEmpty()
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
        "$scheme://$userInfo$hostPart$path$query$fragment"
    }.getOrElse { rawUrl }
}

@Composable
private fun statusAccentColor(status: ServiceStatus): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (status) {
        ServiceStatus.Running -> if (dark) Color(0xFF4ADE80) else Color(0xFF2E7D32)
        ServiceStatus.Starting,
        ServiceStatus.Stopping -> if (dark) Color(0xFFFBBF24) else Color(0xFFE0A106)
        ServiceStatus.Error -> if (dark) Color(0xFFF87171) else Color(0xFFD84315)
        ServiceStatus.Stopped -> if (dark) Color(0xFF94A3B8) else Color(0xFF6E7484)
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private fun formatBytes(v: Long): String {
    if (v <= 0) return "未知"
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
