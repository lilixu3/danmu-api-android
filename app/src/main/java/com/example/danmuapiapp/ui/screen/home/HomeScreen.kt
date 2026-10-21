package com.example.danmuapiapp.ui.screen.home

import com.example.danmuapiapp.ui.component.AppToast
import android.Manifest
import android.app.Activity
import android.os.Build
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.danmuapiapp.data.service.NodeKeepAlivePrefs
import com.example.danmuapiapp.domain.model.CoreSourceStatus
import com.example.danmuapiapp.domain.model.DanmuDownloadTask
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.model.RuntimeTransitionKind
import com.example.danmuapiapp.domain.model.formatCoreVersionTransition
import com.example.danmuapiapp.ui.screen.download.DanmuDownloadViewModel
import com.example.danmuapiapp.ui.screen.download.DownloadQueueSummary
import com.example.danmuapiapp.ui.startup.LocalNetworkPermissionAction
import com.example.danmuapiapp.ui.startup.LocalNetworkPermissionPolicy
import com.example.danmuapiapp.ui.startup.StartupPermissionGatePrefs
import com.example.danmuapiapp.ui.startup.StartupPermissionItem
import com.example.danmuapiapp.ui.startup.StartupPermissionSupport
import kotlin.math.max

internal enum class HomeOverlay {
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
    val tunnelState by viewModel.tunnelState.collectAsStateWithLifecycle()
    val tunnelUrl = if (tunnelState.running) {
        com.example.danmuapiapp.data.tunnel.buildPublicApiUrl(
            tunnelState.publicBase,
            state.token
        )
    } else {
        ""
    }
    val coreList by viewModel.coreInfoList.collectAsStateWithLifecycle()
    val isCoreInfoLoading by viewModel.isCoreInfoLoading.collectAsStateWithLifecycle()
    val pendingDependencyRepair by viewModel.pendingDependencyRepair.collectAsStateWithLifecycle()
    val coreDisplayNames by viewModel.coreDisplayNames.collectAsStateWithLifecycle()
    val customRepo by viewModel.customRepo.collectAsStateWithLifecycle()
    val customRepoBranch by viewModel.customRepoBranch.collectAsStateWithLifecycle()
    val accessEntryDefaultTab by viewModel.accessEntryDefaultTab.collectAsStateWithLifecycle()
    var accessEntrySession by remember { mutableStateOf(0) }
    androidx.lifecycle.compose.LifecycleEventEffect(Lifecycle.Event.ON_START) {
        accessEntrySession += 1
    }
    val accessEntryLayout by viewModel.accessEntryLayout.collectAsStateWithLifecycle()
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
    val listState = rememberLazyListState()
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
        mutableStateOf(readHomeNotificationPermission(context))
    }
    val notificationSupported = remember(context) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            StartupPermissionSupport.runtimePermissionSupported(context, Manifest.permission.POST_NOTIFICATIONS)
    }
    val batterySupported = remember(context) { StartupPermissionSupport.batterySupported(context) }
    val localNetworkSupported = remember(context) {
        Build.VERSION.SDK_INT < LocalNetworkPermissionPolicy.ANDROID_17_API_LEVEL ||
            StartupPermissionSupport.runtimePermissionSupported(context, LocalNetworkPermissionPolicy.PERMISSION)
    }
    var localNetworkPermissionState by remember {
        mutableStateOf(readHomeLocalNetworkPermissionState(context))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        hasNotificationPermission = readHomeNotificationPermission(context)
        if (!hasNotificationPermission) {
            StartupPermissionGatePrefs.skip(context, StartupPermissionItem.Notification)
            viewModel.postMessage("通知权限未获授权，已完成启动检查，可稍后手动设置")
        }
    }
    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        localNetworkPermissionState = readHomeLocalNetworkPermissionState(context)
        if (localNetworkPermissionState.granted) {
            viewModel.postMessage("已允许局域网访问")
        } else {
            StartupPermissionGatePrefs.skip(context, StartupPermissionItem.LocalNetwork)
            viewModel.postMessage("局域网权限未获授权，已完成启动检查，其他设备连接可能受限")
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
        hasNotificationPermission = readHomeNotificationPermission(context)
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
                viewModel.refreshTunnel()
                hasNotificationPermission = readHomeNotificationPermission(context)
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
            AppToast.show(message)
            viewModel.dismissAppUpdateMessage()
        }
    }
    LaunchedEffect(downloadViewModel?.operationMessage) {
        val message = downloadViewModel?.operationMessage
        if (!message.isNullOrBlank()) {
            AppToast.show(message)
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
    LaunchedEffect(queueDialogGroups) {
        val validKeys = queueDialogGroups.map { it.key }.toSet()
        expandedQueueGroupKeys = expandedQueueGroupKeys.intersect(validKeys)
    }
    val shouldShowRuntimePermissionHint = state.runMode == RunMode.Normal &&
        ((notificationSupported && !hasNotificationPermission) || (batterySupported && !isBatteryWhitelisted))
    val shouldShowLocalNetworkAddressHint = localNetworkSupported &&
        LocalNetworkPermissionPolicy.shouldShowAddressHint(localNetworkPermissionState)

    fun openNotificationPermissionQuickAction() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (hasNotificationPermission) return
        if (!StartupPermissionSupport.runtimePermissionSupported(context, Manifest.permission.POST_NOTIFICATIONS)) {
            StartupPermissionGatePrefs.skip(context, StartupPermissionItem.Notification)
            viewModel.postMessage("未找到可用的通知权限入口，已完成启动检查，可继续使用")
            return
        }

        val shouldShowRationale = activity?.let {
            runCatching { ActivityCompat.shouldShowRequestPermissionRationale(
                it, Manifest.permission.POST_NOTIFICATIONS
            ) }.getOrDefault(false)
        } == true
        val requestedBefore = StartupPermissionGatePrefs.hasRequestedNotificationPermission(context)

        if (activity != null && (!requestedBefore || shouldShowRationale)) {
            StartupPermissionGatePrefs.markNotificationPermissionRequested(context)
            if (runCatching { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }.isFailure) {
                StartupPermissionGatePrefs.skip(context, StartupPermissionItem.Notification)
                if (!openHomeNotificationSettings(context)) {
                    viewModel.postMessage("权限申请未能打开，已完成启动检查，可在系统设置中手动检查")
                }
            }
            return
        }

        if (!openHomeNotificationSettings(context)) {
            StartupPermissionGatePrefs.skip(context, StartupPermissionItem.Notification)
            viewModel.postMessage("未找到可用的通知设置入口，已完成启动检查，可继续使用")
        }
    }

    fun openLocalNetworkPermissionQuickAction() {
        val latestState = readHomeLocalNetworkPermissionState(context)
        localNetworkPermissionState = latestState
        if (!latestState.required || latestState.granted) return
        if (!StartupPermissionSupport.runtimePermissionSupported(context, LocalNetworkPermissionPolicy.PERMISSION)) {
            StartupPermissionGatePrefs.skip(context, StartupPermissionItem.LocalNetwork)
            viewModel.postMessage("未找到可用的局域网权限入口，已完成启动检查，可继续使用")
            return
        }
        val shouldShowRationale = activity?.let {
            runCatching { ActivityCompat.shouldShowRequestPermissionRationale(
                it, LocalNetworkPermissionPolicy.PERMISSION
            ) }.getOrDefault(false)
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
                if (runCatching { localNetworkPermissionLauncher.launch(LocalNetworkPermissionPolicy.PERMISSION) }.isFailure) {
                    StartupPermissionGatePrefs.skip(context, StartupPermissionItem.LocalNetwork)
                    if (!openHomeAppDetailsSettings(context)) {
                        viewModel.postMessage("权限申请未能打开，已完成启动检查，局域网连接可能受限")
                    }
                }
            }

            LocalNetworkPermissionAction.Settings -> {
                if (!openHomeAppDetailsSettings(context)) {
                    StartupPermissionGatePrefs.skip(context, StartupPermissionItem.LocalNetwork)
                    viewModel.postMessage("未找到可用的应用设置入口，已完成启动检查，局域网连接可能受限")
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

    HomeScreenBody(
        viewModel = viewModel,
        state = state,
        listState = listState,
        presentedStatus = presentedStatus,
        runtimeTransition = runtimeTransition,
        runtimeTransitionTitle = runtimeTransitionTitle,
        isRunning = isRunning,
        isBusy = isBusy,
        isHeroChipBusy = isHeroChipBusy,
        isDarkTheme = isDarkTheme,
        uptimeText = uptimeText,
        currentVariantLabel = currentVariantLabel,
        isCoreInstalled = isCoreInstalled,
        isCoreInfoLoading = isCoreInfoLoading,
        hasVersionUpdate = hasVersionUpdate,
        sourceMismatch = sourceMismatch,
        sourceUnknownLegacy = sourceUnknownLegacy,
        coreVersionText = coreVersionText,
        coreVersionBadge = coreVersionBadge,
        coreVersionAccent = coreVersionAccent,
        maskedToken = maskedToken,
        tokenVisible = tokenVisible,
        cacheTileValue = cacheTileValue,
        cacheTileBadge = cacheTileBadge,
        cacheTileAccent = cacheTileAccent,
        unreadAnnouncementCount = unreadAnnouncementCount,
        hasQueueTasks = hasQueueTasks,
        isQueueDownloading = isQueueDownloading,
        isQueuePaused = isQueuePaused,
        queueSummary = queueSummary,
        accessEntryLayout = accessEntryLayout,
        accessEntryDefaultTab = accessEntryDefaultTab,
        accessEntrySession = accessEntrySession,
        tunnelUrl = tunnelUrl,
        shouldShowRuntimePermissionHint = shouldShowRuntimePermissionHint,
        hasNotificationPermission = hasNotificationPermission,
        notificationSupported = notificationSupported,
        batterySupported = batterySupported,
        isBatteryWhitelisted = isBatteryWhitelisted,
        shouldShowLocalNetworkAddressHint = shouldShowLocalNetworkAddressHint,
        onOpenDownloadQueue = {
            expandedQueueGroupKeys = emptySet()
            activeOverlay = HomeOverlay.DownloadQueue
        },
        onOpenUnreadAnnouncements = ::openUnreadAnnouncementsEntry,
        onToggleRunMode = {
            val options = RunMode.entries.filter { it != state.runMode }
            pendingRunModeTarget = options.firstOrNull()
            activeOverlay = HomeOverlay.RunModePicker
        },
        onOpenRuntimeInfo = { activeOverlay = HomeOverlay.RuntimeInfo },
        onOpenCacheQuick = {
            viewModel.prepareCacheClearSelection()
            activeOverlay = HomeOverlay.CacheQuick
        },
        onEditToken = {
            quickTokenText = state.token
            quickTokenError = null
            activeOverlay = HomeOverlay.QuickToken
        },
        onEditPort = {
            quickPortText = state.port.toString()
            quickPortError = null
            activeOverlay = HomeOverlay.QuickPort
        },
        onCheckCoreUpdate = {
            viewModel.resetCoreUpdateCheckDialogState()
            activeOverlay = HomeOverlay.CoreUpdateConfirm
        },
        onOpenNotificationPermission = ::openNotificationPermissionQuickAction,
        onOpenBatterySettings = {
            val opened = NormalModeKeepAliveGuideNavigator.requestIgnoreBatteryOptimization(context) ||
                NormalModeKeepAliveGuideNavigator.openAppBatterySettings(context)
            if (!opened) {
                StartupPermissionGatePrefs.skip(context, StartupPermissionItem.Battery)
                viewModel.postMessage("未找到可用的电池设置入口，已完成启动检查，可继续使用")
            }
        },
        onOpenLocalNetworkPermission = ::openLocalNetworkPermissionQuickAction,
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
        },
        onCopyTunnel = {
            clipboardManager.nativeClipboard.setPrimaryClip(
                android.content.ClipData.newPlainText("内网穿透地址", tunnelUrl)
            )
        }
    )

    HomeNoCoreDialogHost(
        viewModel = viewModel,
        state = state,
        currentVariantLabel = currentVariantLabel,
        coreDisplayNames = coreDisplayNames,
        customRepo = customRepo,
        customRepoBranch = customRepoBranch,
        onOpenCoreManagement = onOpenCoreManagement
    )

    HomeUnavailableVariantDialogHost(
        viewModel = viewModel,
        coreDisplayNames = coreDisplayNames,
        customRepo = customRepo,
        onOpenCoreManagement = onOpenCoreManagement
    )

    HomeCoreDependencyHosts(viewModel = viewModel)

    HomeRuntimeInfoDialogHost(
        viewModel = viewModel,
        state = state,
        presentedStatus = presentedStatus,
        uptimeText = uptimeText,
        currentVariantLabel = currentVariantLabel,
        tunnelUrl = tunnelUrl,
        maskedToken = maskedToken,
        tokenVisible = tokenVisible,
        activeOverlay = activeOverlay,
        onDismissOverlay = { activeOverlay = null }
    )

    HomeQuickPortDialogHost(
        viewModel = viewModel,
        state = state,
        isBusy = isBusy,
        quickPortText = quickPortText,
        quickPortError = quickPortError,
        activeOverlay = activeOverlay,
        onDismissOverlay = { activeOverlay = null },
        onQuickPortTextChange = { quickPortText = it },
        onQuickPortErrorChange = { quickPortError = it }
    )

    HomeQuickTokenDialogHost(
        viewModel = viewModel,
        state = state,
        isBusy = isBusy,
        quickTokenText = quickTokenText,
        quickTokenError = quickTokenError,
        activeOverlay = activeOverlay,
        onDismissOverlay = { activeOverlay = null },
        onQuickTokenTextChange = { quickTokenText = it },
        onQuickTokenErrorChange = { quickTokenError = it }
    )

    HomeCoreUpdateConfirmDialogHost(
        viewModel = viewModel,
        currentVariantLabel = currentVariantLabel,
        currentCoreVersion = currentCoreVersion,
        availableVersion = availableVersion,
        activeOverlay = activeOverlay,
        onDismissOverlay = { activeOverlay = null }
    )

    HomeVariantPickerDialogHost(
        viewModel = viewModel,
        state = state,
        coreList = coreList,
        isCoreInfoLoading = isCoreInfoLoading,
        coreDisplayNames = coreDisplayNames,
        customRepo = customRepo,
        customRepoBranch = customRepoBranch,
        isBusy = isBusy
    )

    HomeGithubProxyPickerDialogHost(viewModel = viewModel)

    HomeUpdatePromptDialogHost(
        viewModel = viewModel,
        coreList = coreList,
        coreDisplayNames = coreDisplayNames
    )

    HomeCoreUpdateDetailsHost(
        viewModel = viewModel,
        coreList = coreList,
        coreDisplayNames = coreDisplayNames
    )

    HomeRunModePickerDialogHost(
        viewModel = viewModel,
        state = state,
        isHeroChipBusy = isHeroChipBusy,
        pendingRunModeTarget = pendingRunModeTarget,
        activeOverlay = activeOverlay,
        onDismissOverlay = { activeOverlay = null },
        onPendingRunModeTargetChange = { pendingRunModeTarget = it }
    )

    HomeAppUpdatePromptDialogHost(viewModel = viewModel)

    HomeUnreadAnnouncementsDialogHost(
        viewModel = viewModel,
        unreadAnnouncements = unreadAnnouncements,
        unreadAnnouncementCount = unreadAnnouncementCount,
        activeOverlay = activeOverlay,
        onDismissOverlay = { activeOverlay = null }
    )

    HomeForegroundAnnouncementDialogHost(
        viewModel = viewModel,
        activity = activity,
        onOpenAnnouncementRoute = onOpenAnnouncementRoute
    )

    HomeAppUpdateMethodDialogHost(
        viewModel = viewModel,
        activity = activity,
        hasInAppDownload = hasInAppDownload
    )

    HomeAppUpdateDownloadingDialogHost(viewModel = viewModel)

    HomeAppUpdateInstallDialogHost(
        viewModel = viewModel,
        activity = activity
    )

    HomeDownloadQueueDialogHost(
        viewModel = viewModel,
        downloadViewModel = downloadViewModel,
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
        activeOverlay = activeOverlay,
        onDismissOverlay = { activeOverlay = null },
        onExpandedQueueGroupKeysChange = { expandedQueueGroupKeys = it },
        onOpenDanmuDownload = onOpenDanmuDownload
    )

    HomeCacheQuickDialogHost(
        viewModel = viewModel,
        cacheStats = cacheStats,
        isCacheLoading = isCacheLoading,
        activeOverlay = activeOverlay,
        onDismissOverlay = { activeOverlay = null },
        onOpenCacheManagement = onOpenCacheManagement
    )

    HomeCacheAdminRequiredDialogHost(viewModel = viewModel)
}
private fun readHomeNotificationPermission(context: Context): Boolean =
    runCatching { NodeKeepAlivePrefs.hasPostNotificationsPermission(context) }.getOrDefault(false)


private fun openHomeNotificationSettings(context: Context): Boolean =
    StartupPermissionSupport.notificationIntents(context).any { launchHomeSettings(context, it) }


private fun openHomeAppDetailsSettings(context: Context): Boolean =
    launchHomeSettings(context, StartupPermissionSupport.appDetails(context))


private fun launchHomeSettings(context: Context, intent: Intent): Boolean = runCatching {
    val finalIntent = Intent(intent).apply {
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    // Queries can be visibility-filtered; the actual launch is the definitive check.
    context.startActivity(finalIntent)
    true
}.getOrDefault(false)


private fun readHomeLocalNetworkPermissionState(context: Context) =
    LocalNetworkPermissionPolicy.stateFor(
        sdkInt = Build.VERSION.SDK_INT,
        granted = Build.VERSION.SDK_INT < LocalNetworkPermissionPolicy.ANDROID_17_API_LEVEL ||
            runCatching { ContextCompat.checkSelfPermission(
                context.applicationContext,
                LocalNetworkPermissionPolicy.PERMISSION
            ) == PackageManager.PERMISSION_GRANTED }.getOrDefault(false),
        requestAttempted = StartupPermissionGatePrefs.hasRequestedLocalNetworkPermission(context)
    )
