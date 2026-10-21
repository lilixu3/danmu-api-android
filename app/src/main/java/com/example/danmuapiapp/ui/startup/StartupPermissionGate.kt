package com.example.danmuapiapp.ui.startup

import com.example.danmuapiapp.ui.component.AppToast
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.data.util.DeviceCompatMode
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreDownloadProgress
import com.example.danmuapiapp.domain.model.CoreInfo
import com.example.danmuapiapp.domain.model.CoreDependencyRepairRequest
import com.example.danmuapiapp.domain.model.CoreVariantDisplayNames
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.ui.component.GithubProxyPickerDialog
import com.example.danmuapiapp.ui.component.CoreDependencyRepairHost
import com.example.danmuapiapp.ui.component.SettingsPageHeader
import com.example.danmuapiapp.ui.theme.GlassBackdropScene
import kotlinx.coroutines.launch

object StartupPermissionGatePrefs {
    private const val PREFS_NAME = "startup_permission_gate"
    private const val KEY_NOTIFICATION_REQUESTED = "notification_requested"
    private const val KEY_LOCAL_NETWORK_REQUESTED = "local_network_requested"
    private const val KEY_MODE_ACKNOWLEDGED = "mode_acknowledged"

    fun hasRequestedNotificationPermission(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_NOTIFICATION_REQUESTED, false)
    }

    fun markNotificationPermissionRequested(context: Context) {
        prefs(context).edit().putBoolean(KEY_NOTIFICATION_REQUESTED, true).apply()
    }

    fun hasRequestedLocalNetworkPermission(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_LOCAL_NETWORK_REQUESTED, false)
    }

    fun markLocalNetworkPermissionRequested(context: Context) {
        prefs(context).edit {
            putBoolean(KEY_LOCAL_NETWORK_REQUESTED, true)
        }
    }

    fun clearLegacyGuideDismissed(context: Context) {
        prefs(context).edit().remove("guide_dismissed").apply()
    }

    fun isModeAcknowledged(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_MODE_ACKNOWLEDGED, false)
    }

    fun setModeAcknowledged(context: Context, acknowledged: Boolean) {
        prefs(context).edit().putBoolean(KEY_MODE_ACKNOWLEDGED, acknowledged).apply()
    }

    internal fun hasSkipped(context: Context, item: StartupPermissionItem): Boolean =
        prefs(context).getBoolean("skipped_${item.key}", false)

    internal fun skip(context: Context, item: StartupPermissionItem) {
        prefs(context).edit { putBoolean("skipped_${item.key}", true) }
    }

    internal fun showSkipNotice(context: Context, item: StartupPermissionItem, detail: String) {
        AppToast.warning("${item.label}：$detail，已跳过启动检查。")
        prefs(context).edit { putBoolean("skip_notice_${item.key}", true) }
    }

    internal fun showUnsupportedNoticeOnce(context: Context, items: List<StartupPermissionItem>) {
        val unseen = items.filter { !prefs(context).getBoolean("skip_notice_${it.key}", false) }
        if (unseen.isEmpty()) return
        AppToast.warning("未发现可用的${unseen.joinToString("、") { it.label }}设置入口，已跳过，可继续使用。")
        prefs(context).edit { unseen.forEach { putBoolean("skip_notice_${it.key}", true) } }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

@Composable
fun StartupPermissionGateHost(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    if (DeviceCompatMode.shouldUseCompatMode(context)) {
        content()
        return
    }
    val viewModel: StartupSetupViewModel = hiltViewModel()
    val runtimeState by viewModel.runtimeState.collectAsStateWithLifecycle()
    val coreInfoList by viewModel.coreInfoList.collectAsStateWithLifecycle()
    val isCoreInfoLoading by viewModel.isCoreInfoLoading.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val pendingDependencyRepair by viewModel.pendingDependencyRepair.collectAsStateWithLifecycle()
    val coreDisplayNames by viewModel.coreDisplayNames.collectAsStateWithLifecycle()
    val customCoreSource by viewModel.customCoreSource.collectAsStateWithLifecycle()
    val customRepo by viewModel.customRepo.collectAsStateWithLifecycle()
    val customRepoBranch by viewModel.customRepoBranch.collectAsStateWithLifecycle()

    var permissionState by remember(runtimeState.runMode) {
        mutableStateOf(readPermissionState(context, runtimeState.runMode))
    }
    var dismissedThisLaunch by rememberSaveable { mutableStateOf(false) }
    var modeAcknowledged by remember { mutableStateOf(StartupPermissionGatePrefs.isModeAcknowledged(context)) }
    var coreDeferredThisLaunch by rememberSaveable { mutableStateOf(false) }
    var requestedRunModeKey by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedRunModeKey by rememberSaveable { mutableStateOf(runtimeState.runMode.key) }
    var selectedVariantKey by rememberSaveable {
        mutableStateOf(normalizeStartupVariant(runtimeState.variant).key)
    }
    var activeStepName by rememberSaveable { mutableStateOf<String?>(null) }
    val requestedRunMode = requestedRunModeKey?.let(RunMode::fromKey)
    val selectedRunMode = RunMode.fromKey(selectedRunModeKey) ?: runtimeState.runMode
    val selectedVariant = ApiVariant.entries.firstOrNull { it.key == selectedVariantKey }
        ?: normalizeStartupVariant(runtimeState.variant)
    val activeStep = activeStepName?.let { name ->
        runCatching { enumValueOf<SetupStep>(name) }.getOrNull()
    }

    ObserveResumeRefresh {
        permissionState = readPermissionState(context, runtimeState.runMode)
    }

    LaunchedEffect(Unit) {
        StartupPermissionGatePrefs.clearLegacyGuideDismissed(context)
    }

    LaunchedEffect(permissionState.unsupportedItems) {
        StartupPermissionGatePrefs.showUnsupportedNoticeOnce(context, permissionState.unsupportedItems)
    }

    LaunchedEffect(runtimeState.runMode) {
        permissionState = readPermissionState(context, runtimeState.runMode)
        if (requestedRunMode == runtimeState.runMode) {
            modeAcknowledged = true
            StartupPermissionGatePrefs.setModeAcknowledged(context, true)
            requestedRunModeKey = null
        }
        if (requestedRunMode == null) {
            selectedRunModeKey = runtimeState.runMode.key
        }
    }

    LaunchedEffect(viewModel.isRunModeSwitching, requestedRunMode, runtimeState.runMode) {
        val target = requestedRunMode
        if (target != null && viewModel.isRunModeSwitching.not() && runtimeState.runMode != target) {
            requestedRunModeKey = null
        }
    }

    LaunchedEffect(runtimeState.variant, downloadProgress.inProgress) {
        if (downloadProgress.inProgress.not()) {
            selectedVariantKey = normalizeStartupVariant(runtimeState.variant).key
        }
    }

    val canConfigureSetup = runtimeState.status == ServiceStatus.Stopped ||
        runtimeState.status == ServiceStatus.Error
    val currentCoreReady = coreInfoList.find { it.variant == runtimeState.variant }?.isReady == true
    val shouldShowModeStep = canConfigureSetup && modeAcknowledged.not()
    val shouldShowCoreStep = canConfigureSetup &&
        isCoreInfoLoading.not() &&
        currentCoreReady.not() &&
        pendingDependencyRepair == null &&
        coreDeferredThisLaunch.not()
    val shouldShowDependencyStep = canConfigureSetup &&
        pendingDependencyRepair?.requiresUserRepair == true &&
        coreDeferredThisLaunch.not()
    val shouldShowPermissionStep = !permissionState.localNetworkReady ||
        (runtimeState.runMode == RunMode.Normal && (!permissionState.notificationReady || !permissionState.batteryReady))

    val pendingSteps = buildList {
        if (shouldShowModeStep) add(SetupStep.Mode)
        if (shouldShowCoreStep) add(SetupStep.Core)
        if (shouldShowDependencyStep) add(SetupStep.Dependency)
        if (shouldShowPermissionStep) add(SetupStep.Permission)
    }

    LaunchedEffect(pendingSteps) {
        activeStepName = when {
            pendingSteps.isEmpty() -> null
            activeStep in pendingSteps -> activeStep?.name
            else -> pendingSteps.first().name
        }
    }

    if (dismissedThisLaunch.not() && pendingSteps.isNotEmpty()) {
        GlassBackdropScene(modifier = Modifier.fillMaxSize()) {
            StartupPermissionGateScreen(
                runtimeState = runtimeState,
                coreInfoList = coreInfoList,
                isCoreInfoLoading = isCoreInfoLoading,
                downloadProgress = downloadProgress,
                pendingDependencyRepair = pendingDependencyRepair,
                permissionState = permissionState,
                pendingSteps = pendingSteps,
                currentStep = activeStep ?: pendingSteps.first(),
                selectedRunMode = selectedRunMode,
                onSelectRunMode = { selectedRunModeKey = it.key },
                onConfirmRunMode = {
                    if (selectedRunMode == runtimeState.runMode) {
                        modeAcknowledged = true
                        StartupPermissionGatePrefs.setModeAcknowledged(context, true)
                    } else {
                        requestedRunModeKey = selectedRunMode.key
                        viewModel.switchRunMode(selectedRunMode)
                    }
                },
                isRunModeSwitching = viewModel.isRunModeSwitching,
                selectedVariant = selectedVariant,
                coreDisplayNames = coreDisplayNames,
                customRepo = customRepo,
                customRepoBranch = customRepoBranch,
                customRepoConfigured = customCoreSource.isValidRepo,
                onSelectVariant = { selectedVariantKey = it.key },
                onUseSelectedVariant = { viewModel.chooseVariant(selectedVariant) },
                onInstallSelectedVariant = { viewModel.installCore(selectedVariant) },
                onSaveCustomSettings = viewModel::saveCustomCoreSettings,
                onSkipCoreForNow = {
                    coreDeferredThisLaunch = true
                    dismissedThisLaunch = true
                },
                onRefreshPermissionState = {
                    permissionState = readPermissionState(context, runtimeState.runMode)
                },
                onContinueHome = {
                    dismissedThisLaunch = true
                },
                viewModel = viewModel
            )
        }
    } else {
        content()
    }
}

@Composable
internal fun StartupPermissionGateScreen(
    runtimeState: RuntimeState,
    coreInfoList: List<CoreInfo>,
    isCoreInfoLoading: Boolean,
    downloadProgress: CoreDownloadProgress,
    pendingDependencyRepair: CoreDependencyRepairRequest?,
    permissionState: StartupPermissionState,
    pendingSteps: List<SetupStep>,
    currentStep: SetupStep,
    selectedRunMode: RunMode,
    onSelectRunMode: (RunMode) -> Unit,
    onConfirmRunMode: () -> Unit,
    isRunModeSwitching: Boolean,
    selectedVariant: ApiVariant,
    coreDisplayNames: CoreVariantDisplayNames,
    customRepo: String,
    customRepoBranch: String,
    customRepoConfigured: Boolean,
    onSelectVariant: (ApiVariant) -> Unit,
    onUseSelectedVariant: () -> Unit,
    onInstallSelectedVariant: () -> Unit,
    onSaveCustomSettings: (displayName: String, repo: String, branch: String) -> Boolean,
    onSkipCoreForNow: () -> Unit,
    onRefreshPermissionState: () -> Unit,
    onContinueHome: () -> Unit,
    viewModel: StartupSetupViewModel
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val notificationAction = remember(permissionState, activity) {
        resolveNotificationAction(activity = activity, permissionState = permissionState)
    }
    var localNetworkPermissionResultGeneration by remember { mutableIntStateOf(0) }
    val localNetworkAction = remember(
        permissionState.localNetwork,
        activity,
        localNetworkPermissionResultGeneration
    ) {
        resolveLocalNetworkAction(activity = activity, state = permissionState.localNetwork)
    }

    fun skipPermission(item: StartupPermissionItem, detail: String) {
        StartupPermissionGatePrefs.skip(context, item)
        StartupPermissionGatePrefs.showSkipNotice(context, item, detail)
        onRefreshPermissionState()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) skipPermission(StartupPermissionItem.Notification, "系统未授予权限")
        else onRefreshPermissionState()
    }
    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        localNetworkPermissionResultGeneration += 1
        if (!granted) skipPermission(StartupPermissionItem.LocalNetwork, "系统未授予权限，局域网连接可能受限")
        else onRefreshPermissionState()
    }
    var settingsItemName by rememberSaveable { mutableStateOf<String?>(null) }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val item = StartupPermissionItem.entries.firstOrNull { it.name == settingsItemName }
        settingsItemName = null
        val current = readPermissionState(context, runtimeState.runMode)
        val granted = when (item) {
            StartupPermissionItem.Notification -> current.notificationGranted
            StartupPermissionItem.Battery -> current.batteryOptimizationIgnored
            StartupPermissionItem.LocalNetwork -> current.localNetwork.granted
            null -> true
        }
        if (!granted && item != null) {
            val detail = if (item == StartupPermissionItem.Battery) {
                "未检测到标准电池豁免，厂商后台设置请以系统显示为准"
            } else "未检测到授权，请以系统设置显示为准"
            skipPermission(item, detail)
        } else onRefreshPermissionState()
    }

    LaunchedEffect(viewModel.operationMessage) {
        val message = viewModel.operationMessage
        if (message.isNullOrBlank()) return@LaunchedEffect
        AppToast.show(message)
        viewModel.dismissMessage()
    }

    fun openSettings(item: StartupPermissionItem, candidates: List<Intent>) {
        if (activity == null) {
            skipPermission(item, "当前没有可用的权限设置入口")
            return
        }
        for (intent in candidates) {
            // Visibility and OEM activity aliases can hide a usable entry. At the
            // user's explicit request, try each candidate and catch launch failures.
            settingsItemName = item.name
            if (runCatching { settingsLauncher.launch(intent) }.isSuccess) return
        }
        settingsItemName = null
        skipPermission(item, "当前系统没有可用的设置入口")
    }

    fun openNotificationPermissionFlow() {
        if (!StartupPermissionSupport.runtimePermissionSupported(context, Manifest.permission.POST_NOTIFICATIONS)) {
            skipPermission(StartupPermissionItem.Notification, "当前系统未提供此权限")
            return
        }
        when (notificationAction) {
            NotificationAction.Request -> {
                StartupPermissionGatePrefs.markNotificationPermissionRequested(context)
                if (runCatching { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }.isFailure) {
                    skipPermission(StartupPermissionItem.Notification, "权限申请未能打开，可在系统设置中手动检查")
                }
            }
            NotificationAction.Settings -> openSettings(StartupPermissionItem.Notification, StartupPermissionSupport.notificationIntents(context))
            null -> onRefreshPermissionState()
        }
    }

    fun openLocalNetworkPermissionFlow() {
        if (!StartupPermissionSupport.runtimePermissionSupported(context, LocalNetworkPermissionPolicy.PERMISSION)) {
            skipPermission(StartupPermissionItem.LocalNetwork, "当前系统未提供此权限")
            return
        }
        val currentAction = resolveLocalNetworkAction(activity, permissionState.localNetwork)
        when (currentAction) {
            LocalNetworkPermissionAction.Request -> {
                StartupPermissionGatePrefs.markLocalNetworkPermissionRequested(context)
                if (runCatching { localNetworkPermissionLauncher.launch(LocalNetworkPermissionPolicy.PERMISSION) }.isFailure) {
                    skipPermission(StartupPermissionItem.LocalNetwork, "权限申请未能打开，可在系统设置中手动检查")
                }
            }
            LocalNetworkPermissionAction.Settings -> openSettings(StartupPermissionItem.LocalNetwork, listOf(StartupPermissionSupport.appDetails(context)))
            null -> onRefreshPermissionState()
        }
    }

    fun openBatteryOptimizationFlow() {
        openSettings(StartupPermissionItem.Battery, StartupPermissionSupport.batteryIntents(context))
    }

    val stepIndex = pendingSteps.indexOf(currentStep).coerceAtLeast(0) + 1
    val totalSteps = pendingSteps.size.coerceAtLeast(1)
    val progress = stepIndex.toFloat() / totalSteps.toFloat()
    val stepSubtitle = when (currentStep) {
        SetupStep.Mode -> "先确定这台设备用普通模式还是 Root 模式。这里不会直接启动或停止服务。"
        SetupStep.Core -> "把当前要用的核心准备好。下载只处理核心文件，不会在这里启动服务。"
        SetupStep.Dependency -> "检查核心运行依赖，缺失项修复并校验通过后才会进入下一步。"
        SetupStep.Permission -> if (permissionState.localNetworkReady.not()) {
            "Android 17 默认拦截局域网连接，授权后其他设备才能访问弹幕服务。"
        } else {
            "普通模式建议把提醒和后台权限补齐，启动反馈会更清楚，后台也更稳。"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { padding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                val compact = maxHeight < 760.dp
                val spacing = if (compact) 10.dp else 12.dp
                val horizontalPadding = if (compact) 18.dp else 22.dp
                val verticalPadding = if (compact) 12.dp else 16.dp

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                    verticalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    SettingsPageHeader(
                        title = when (currentStep) {
                            SetupStep.Mode -> "先选运行模式"
                            SetupStep.Core -> "再准备核心"
                            SetupStep.Dependency -> "补齐运行依赖"
                            SetupStep.Permission -> if (permissionState.localNetworkReady.not()) {
                                "授权局域网访问"
                            } else {
                                "最后补齐提醒"
                            }
                        },
                        subtitle = stepSubtitle
                    )

                    StepProgressCard(
                        current = stepIndex,
                        total = totalSteps,
                        progress = progress,
                        compact = compact
                    )

                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        modifier = Modifier.weight(1f),
                        label = "startup_step"
                    ) { step ->
                        when (step) {
                            SetupStep.Mode -> RunModeStepContent(
                                compact = compact,
                                currentMode = runtimeState.runMode,
                                selectedMode = selectedRunMode,
                                isRunModeSwitching = isRunModeSwitching,
                                onSelectMode = onSelectRunMode,
                                onConfirm = onConfirmRunMode
                            )

                            SetupStep.Core -> CoreStepContent(
                                compact = compact,
                                runtimeState = runtimeState,
                                coreInfoList = coreInfoList,
                                isCoreInfoLoading = isCoreInfoLoading,
                                downloadProgress = downloadProgress,
                                selectedVariant = selectedVariant,
                                coreDisplayNames = coreDisplayNames,
                                customRepo = customRepo,
                                customRepoBranch = customRepoBranch,
                                customRepoConfigured = customRepoConfigured,
                                onSelectVariant = onSelectVariant,
                                onUseSelectedVariant = onUseSelectedVariant,
                                onInstallSelectedVariant = onInstallSelectedVariant,
                                onSaveCustomSettings = onSaveCustomSettings,
                                onSkipForNow = onSkipCoreForNow
                            )

                            SetupStep.Dependency -> DependencyStepContent(
                                compact = compact,
                                request = pendingDependencyRepair,
                                downloadProgress = downloadProgress,
                                isRepairing = viewModel.isRepairingDependencies,
                                onRepair = viewModel::openDependencyRepairDialog,
                                onSkipForNow = onSkipCoreForNow
                            )

                            SetupStep.Permission -> PermissionStepContent(
                                compact = compact,
                                permissionState = permissionState,
                                notificationAction = notificationAction,
                                localNetworkAction = localNetworkAction,
                                onOpenLocalNetworkPermission = ::openLocalNetworkPermissionFlow,
                                onOpenNotificationPermission = ::openNotificationPermissionFlow,
                                onOpenBatterySettings = ::openBatteryOptimizationFlow,
                                onContinueHome = onContinueHome
                            )
                        }
                    }
                }
            }
        }
    }

    if (viewModel.showProxyPickerDialog) {
        GithubProxyPickerDialog(
            title = "选择 GitHub 线路",
            subtitle = "首次下载核心前先选一条稳定线路，后续下载会更省心。",
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

    CoreDependencyRepairHost(
        request = pendingDependencyRepair,
        showRequiredPrompt = false,
        showRepairDialog = viewModel.showDependencyRepairDialog,
        onOpenRepair = viewModel::openDependencyRepairDialog,
        onDismissRequiredPrompt = {},
        onOnlineRepair = viewModel::repairPendingDependenciesOnline,
        onRepairFromArchive = viewModel::repairPendingDependenciesFromArchive,
        onCancelMutation = viewModel::discardPendingCoreMutation,
        onDismissRepairDialog = viewModel::dismissDependencyRepairDialog
    )
}

@Composable
private fun ObserveResumeRefresh(
    onRefresh: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        onRefresh()
    }
    DisposableEffect(lifecycleOwner, onRefresh) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

private fun normalizeStartupVariant(variant: ApiVariant): ApiVariant {
    return when (variant) {
        ApiVariant.Dev -> ApiVariant.Dev
        ApiVariant.Custom -> ApiVariant.Custom
        else -> ApiVariant.Stable
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
