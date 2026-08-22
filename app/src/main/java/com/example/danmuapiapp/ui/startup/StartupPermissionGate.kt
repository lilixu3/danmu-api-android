package com.example.danmuapiapp.ui.startup

import com.example.danmuapiapp.ui.component.AppSnackbarHost
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.data.service.NodeKeepAlivePrefs
import com.example.danmuapiapp.data.util.DeviceCompatMode
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreDownloadProgress
import com.example.danmuapiapp.domain.model.CoreInfo
import com.example.danmuapiapp.domain.model.CoreDependencyRepairRequest
import com.example.danmuapiapp.domain.model.CoreVariantDisplayNames
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.ui.common.CustomCoreSettingsForm
import com.example.danmuapiapp.ui.common.rememberCustomCoreSettingsFormState
import com.example.danmuapiapp.ui.component.GithubProxyPickerDialog
import com.example.danmuapiapp.ui.component.CoreDependencyRepairHost
import com.example.danmuapiapp.ui.component.GradientButton
import com.example.danmuapiapp.ui.component.SettingsHintCard
import com.example.danmuapiapp.ui.component.SettingsPageHeader
import com.example.danmuapiapp.ui.theme.GlassBackdropScene
import com.example.danmuapiapp.ui.screen.home.NormalModeKeepAliveGuideNavigator
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

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

private enum class SetupStep {
    Mode,
    Core,
    Dependency,
    Permission
}

private enum class NotificationAction {
    Request,
    Settings
}

private data class StartupPermissionState(
    val runMode: RunMode,
    val notificationRequired: Boolean,
    val notificationGranted: Boolean,
    val notificationRequestAttempted: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val localNetwork: LocalNetworkPermissionState
) {
    val notificationReady: Boolean
        get() = notificationRequired.not() || notificationGranted

    val batteryRequired: Boolean
        get() = runMode == RunMode.Normal

    val batteryReady: Boolean
        get() = batteryRequired.not() || batteryOptimizationIgnored
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
        pendingDependencyRepair != null &&
        coreDeferredThisLaunch.not()
    val shouldShowPermissionStep = LocalNetworkPermissionPolicy.shouldShowSetupStep(
        runMode = runtimeState.runMode,
        notificationReady = permissionState.notificationReady,
        batteryReady = permissionState.batteryReady,
        localNetworkState = permissionState.localNetwork
    )

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
private fun StartupPermissionGateScreen(
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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
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

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onRefreshPermissionState()
    }
    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        localNetworkPermissionResultGeneration += 1
        onRefreshPermissionState()
    }

    LaunchedEffect(viewModel.operationMessage) {
        val message = viewModel.operationMessage
        if (message.isNullOrBlank()) return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissMessage()
    }

    fun openNotificationPermissionFlow() {
        when (notificationAction) {
            NotificationAction.Request -> {
                StartupPermissionGatePrefs.markNotificationPermissionRequested(context)
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            NotificationAction.Settings -> {
                if (openNotificationSettings(context).not()) {
                    scope.launch {
                        snackbarHostState.showSnackbar("当前设备无法直接打开通知设置，请在应用详情中手动开启")
                    }
                }
            }

            null -> onRefreshPermissionState()
        }
    }

    fun openLocalNetworkPermissionFlow() {
        val currentAction = resolveLocalNetworkAction(
            activity = activity,
            state = permissionState.localNetwork
        )
        when (currentAction) {
            LocalNetworkPermissionAction.Request -> {
                StartupPermissionGatePrefs.markLocalNetworkPermissionRequested(context)
                localNetworkPermissionLauncher.launch(LocalNetworkPermissionPolicy.PERMISSION)
            }

            LocalNetworkPermissionAction.Settings -> {
                if (openAppDetailsSettings(context).not()) {
                    scope.launch {
                        snackbarHostState.showSnackbar("当前设备无法直接打开应用设置，请手动开启局域网访问权限")
                    }
                }
            }

            null -> onRefreshPermissionState()
        }
    }

    fun openBatteryOptimizationFlow() {
        val opened = NormalModeKeepAliveGuideNavigator.requestIgnoreBatteryOptimization(context) ||
            NormalModeKeepAliveGuideNavigator.openAppBatterySettings(context)
        if (opened.not()) {
            scope.launch {
                snackbarHostState.showSnackbar("当前设备没有可用的电池优化设置入口")
            }
        }
    }

    val stepIndex = pendingSteps.indexOf(currentStep).coerceAtLeast(0) + 1
    val totalSteps = pendingSteps.size.coerceAtLeast(1)
    val progress = stepIndex.toFloat() / totalSteps.toFloat()
    val stepSubtitle = when (currentStep) {
        SetupStep.Mode -> "先确定这台设备用普通模式还是 Root 模式。这里不会直接启动或停止服务。"
        SetupStep.Core -> "把当前要用的核心准备好。下载只处理核心文件，不会在这里启动服务。"
        SetupStep.Dependency -> "检查核心运行依赖，缺失项修复并校验通过后才会进入下一步。"
        SetupStep.Permission -> if (permissionState.localNetwork.ready.not()) {
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
            snackbarHost = {
                AppSnackbarHost(
                    hostState = snackbarHostState,
                    aboveBottomBar = false
                )
            },
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
                            SetupStep.Permission -> if (permissionState.localNetwork.ready.not()) {
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
private fun StepProgressCard(
    current: Int,
    total: Int,
    progress: Float,
    compact: Boolean
) {
    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (compact) 14.dp else 16.dp,
                vertical = if (compact) 12.dp else 14.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "准备步骤",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$current / $total",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "当前只显示这一步，处理完会自动进入下一步。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RunModeStepContent(
    compact: Boolean,
    currentMode: RunMode,
    selectedMode: RunMode,
    isRunModeSwitching: Boolean,
    onSelectMode: (RunMode) -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)
    ) {
        SetupChoiceCard(
            icon = Icons.Rounded.Tune,
            accent = MaterialTheme.colorScheme.primary,
            title = "普通模式",
            summary = "兼容性更高，适合大多数设备。后面会继续提醒你把通知和电池优化补齐。",
            badge = when {
                selectedMode == RunMode.Normal -> "已选中"
                currentMode == RunMode.Normal -> "当前"
                else -> "推荐"
            },
            selected = selectedMode == RunMode.Normal,
            onClick = { onSelectMode(RunMode.Normal) }
        )

        SetupChoiceCard(
            icon = Icons.Rounded.Verified,
            accent = MaterialTheme.colorScheme.tertiary,
            title = "Root 模式",
            summary = "后台限制更少，需要 Root 授权。兼容 Magisk / KernelSU / APatch。",
            badge = when {
                selectedMode == RunMode.Root -> "已选中"
                currentMode == RunMode.Root -> "当前"
                else -> "需授权"
            },
            selected = selectedMode == RunMode.Root,
            onClick = { onSelectMode(RunMode.Root) }
        )

        SettingsHintCard(
            text = if (selectedMode == RunMode.Root && currentMode != RunMode.Root) {
                "点击下方后会弹出系统 Root 授权，允许后才会真正切换到 Root 模式。"
            } else {
                "这里只确定运行方式，不会在这个页面直接启动或停止服务。"
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        GradientButton(
            text = when {
                isRunModeSwitching && selectedMode == RunMode.Root -> "正在申请 Root..."
                isRunModeSwitching -> "正在切换..."
                selectedMode == currentMode && selectedMode == RunMode.Root -> "继续使用 Root 模式"
                selectedMode == currentMode -> "继续使用普通模式"
                selectedMode == RunMode.Root -> "申请 Root 并切换"
                else -> "切换到普通模式"
            },
            onClick = onConfirm,
            enabled = isRunModeSwitching.not(),
            modifier = Modifier.fillMaxWidth(),
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary
            )
        )
    }
}

@Composable
private fun CoreStepContent(
    compact: Boolean,
    runtimeState: RuntimeState,
    coreInfoList: List<CoreInfo>,
    isCoreInfoLoading: Boolean,
    downloadProgress: CoreDownloadProgress,
    selectedVariant: ApiVariant,
    coreDisplayNames: CoreVariantDisplayNames,
    customRepo: String,
    customRepoBranch: String,
    customRepoConfigured: Boolean,
    onSelectVariant: (ApiVariant) -> Unit,
    onUseSelectedVariant: () -> Unit,
    onInstallSelectedVariant: () -> Unit,
    onSaveCustomSettings: (displayName: String, repo: String, branch: String) -> Boolean,
    onSkipForNow: () -> Unit
) {
    val stableInfo = coreInfoList.find { it.variant == ApiVariant.Stable }
    val devInfo = coreInfoList.find { it.variant == ApiVariant.Dev }
    val customInfo = coreInfoList.find { it.variant == ApiVariant.Custom }
    val selectedInfo = coreInfoList.find { it.variant == selectedVariant }
    val selectedInstalled = selectedInfo?.isInstalled == true
    val downloadForSelected = downloadProgress.inProgress && downloadProgress.variant == selectedVariant
    val showCustomOption = true
    val stableLabel = coreDisplayNames.resolve(ApiVariant.Stable)
    val devLabel = coreDisplayNames.resolve(ApiVariant.Dev)
    val customLabel = coreDisplayNames.resolve(ApiVariant.Custom)
    val selectedVariantLabel = coreDisplayNames.resolve(selectedVariant)
    val customFormState = rememberCustomCoreSettingsFormState(
        initialDisplayName = coreDisplayNames.custom,
        initialRepo = customRepo,
        initialBranch = customRepoBranch
    )
    val customSettingsDirty = selectedVariant == ApiVariant.Custom && customFormState.isDirty
    val canSaveCustomSettings = customFormState.canSaveConfig
    val needsCustomRepo = selectedVariant == ApiVariant.Custom && customRepoConfigured.not()
    val canInstallSelectedCustom = if (selectedVariant == ApiVariant.Custom) {
        when {
            needsCustomRepo -> customFormState.canInstall
            customSettingsDirty -> customFormState.canInstall
            else -> true
        }
    } else {
        true
    }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)
        ) {
            SetupChoiceCard(
                icon = Icons.Rounded.CheckCircle,
                accent = MaterialTheme.colorScheme.primary,
                title = stableLabel,
                summary = "更适合长期使用，优先推荐新设备第一次先下这个版本。",
                badge = coreBadgeText(
                    variant = ApiVariant.Stable,
                    runtimeState = runtimeState,
                    info = stableInfo,
                    selectedVariant = selectedVariant
                ),
                selected = selectedVariant == ApiVariant.Stable,
                onClick = { onSelectVariant(ApiVariant.Stable) }
            )

            SetupChoiceCard(
                icon = Icons.Rounded.DownloadForOffline,
                accent = MaterialTheme.colorScheme.secondary,
                title = devLabel,
                summary = "更新更快，适合愿意第一时间体验新特性的用户。",
                badge = coreBadgeText(
                    variant = ApiVariant.Dev,
                    runtimeState = runtimeState,
                    info = devInfo,
                    selectedVariant = selectedVariant
                ),
                selected = selectedVariant == ApiVariant.Dev,
                onClick = { onSelectVariant(ApiVariant.Dev) }
            )

            if (showCustomOption) {
                SetupChoiceCard(
                    icon = Icons.Rounded.Tune,
                    accent = MaterialTheme.colorScheme.tertiary,
                    title = customLabel,
                    summary = "适合已经导入自定义仓库或本地核心的场景，首次使用前请先确认核心已准备好。",
                    badge = coreBadgeText(
                        variant = ApiVariant.Custom,
                        runtimeState = runtimeState,
                        info = customInfo,
                        selectedVariant = selectedVariant
                    ),
                    selected = selectedVariant == ApiVariant.Custom,
                    onClick = { onSelectVariant(ApiVariant.Custom) }
                )
            }

            if (selectedVariant == ApiVariant.Custom) {
                AppGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "自定义核心设置",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        CustomCoreSettingsForm(
                            state = customFormState,
                            displayNamePlaceholder = customLabel
                        )
                        if (customSettingsDirty) {
                            AppGlassButton(
                                onClick = {
                                    val input = customFormState.toInput()
                                    onSaveCustomSettings(
                                        input.displayName,
                                        input.repo,
                                        input.branch
                                    )
                                },
                                enabled = canSaveCustomSettings,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("仅保存配置")
                            }
                        }
                    }
                }
            }

            if (downloadProgress.inProgress) {
                DownloadProgressCard(
                    progress = downloadProgress,
                    compact = compact
                )
            } else {
                SettingsHintCard(
                    text = if (needsCustomRepo) {
                        "先在这里填好 $customLabel 的仓库和分支，再点下载即可。"
                    } else if (runtimeState.runMode == RunMode.Root) {
                        "Root 模式下准备完核心后，会直接进入首页，不再显示权限步骤。"
                    } else {
                        "首次下载前会先让你选 GitHub 线路。这里只准备核心，不会在这里启动服务。"
                    }
                )
            }

            Spacer(modifier = Modifier.height(if (compact) 12.dp else 16.dp))
        }

        GradientButton(
            text = when {
                isCoreInfoLoading -> "正在读取核心信息..."
                downloadForSelected -> "正在下载$selectedVariantLabel..."
                selectedVariant == ApiVariant.Custom && (needsCustomRepo || customSettingsDirty) ->
                    "保存并下载$customLabel"
                selectedInstalled -> "使用$selectedVariantLabel"
                else -> "下载$selectedVariantLabel"
            },
            onClick = {
                if (selectedVariant == ApiVariant.Custom && (needsCustomRepo || customSettingsDirty)) {
                    val input = customFormState.toInput()
                    val saved = onSaveCustomSettings(
                        input.displayName,
                        input.repo,
                        input.branch
                    )
                    if (saved) {
                        onInstallSelectedVariant()
                    }
                } else if (selectedInstalled) {
                    onUseSelectedVariant()
                } else {
                    onInstallSelectedVariant()
                }
            },
            enabled = isCoreInfoLoading.not() &&
                downloadProgress.inProgress.not() &&
                canInstallSelectedCustom,
            modifier = Modifier.fillMaxWidth(),
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary
            )
        )

        AppGlassButton(
            onClick = onSkipForNow,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("先去首页处理")
        }
    }
}

@Composable
private fun DownloadProgressCard(
    progress: CoreDownloadProgress,
    compact: Boolean
) {
    val percentText = progress.progress?.let { "${(it * 100f).toInt().coerceIn(0, 100)}%" } ?: "处理中"
    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (compact) 14.dp else 16.dp,
                vertical = if (compact) 12.dp else 14.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = progress.actionLabel.ifBlank { "正在准备核心" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = percentText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            LinearProgressIndicator(
                progress = { progress.progress ?: 0f },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = progress.stageText.ifBlank { "请稍等片刻，下载完成后会自动进入下一步。" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DependencyStepContent(
    compact: Boolean,
    request: CoreDependencyRepairRequest?,
    downloadProgress: CoreDownloadProgress,
    isRepairing: Boolean,
    onRepair: () -> Unit,
    onSkipForNow: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)
    ) {
        SetupNoticeCard(
            title = "核心文件已下载，运行依赖尚未齐全",
            summary = "依赖修复只处理当前候选核心；完成校验后会自动继续安装。",
            compact = compact
        )

        AppGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "待补充依赖",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = request?.missingDependencies
                        ?.joinToString("\n") { "• $it" }
                        .orEmpty()
                        .ifBlank { "正在重新检测依赖..." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        if (downloadProgress.inProgress || isRepairing) {
            DownloadProgressCard(
                progress = downloadProgress.takeIf { it.inProgress }
                    ?: CoreDownloadProgress(
                        inProgress = true,
                        actionLabel = "修复依赖",
                        stageText = "正在校验运行时依赖"
                    ),
                compact = compact
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        GradientButton(
            text = if (isRepairing) "正在修复依赖..." else "修复依赖",
            onClick = onRepair,
            enabled = request != null && !isRepairing,
            modifier = Modifier.fillMaxWidth(),
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary
            )
        )

        AppGlassButton(
            onClick = onSkipForNow,
            enabled = !isRepairing,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("先去首页处理")
        }
    }
}

@Composable
private fun PermissionStepContent(
    compact: Boolean,
    permissionState: StartupPermissionState,
    notificationAction: NotificationAction?,
    localNetworkAction: LocalNetworkPermissionAction?,
    onOpenLocalNetworkPermission: () -> Unit,
    onOpenNotificationPermission: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onContinueHome: () -> Unit
) {
    val showLocalNetwork = permissionState.localNetwork.ready.not()
    val showNotification = permissionState.notificationReady.not()
    val showBattery = permissionState.batteryRequired && permissionState.batteryReady.not()
    val itemSpacing = if (compact) 10.dp else 12.dp

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(itemSpacing)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(itemSpacing)
        ) {
            SetupNoticeCard(
                title = when {
                    showLocalNetwork -> "开启局域网访问，其他设备才能连接"
                    showNotification && showBattery -> "把这两项补齐后，普通模式会更省心"
                    showNotification -> "建议把通知权限补齐"
                    else -> "建议把电池优化设为不受限制"
                },
                summary = when {
                    showLocalNetwork -> "Android 17 会默认拦截局域网入站连接，授权后同一网络中的设备才能访问弹幕服务。"
                    showNotification && showBattery -> "启动、停止和运行状态会更清楚，后台被系统清理的概率也会更低。"
                    showNotification -> "这样更容易确认服务是不是已经真的启动成功。"
                    else -> "这样普通模式在后台会更稳定。"
                },
                compact = compact
            )

            if (showLocalNetwork) {
                PermissionStepCard(
                    compact = compact,
                    icon = Icons.Rounded.Wifi,
                    accent = MaterialTheme.colorScheme.tertiary,
                    title = "局域网访问",
                    summary = "允许同一 Wi-Fi 或有线网络中的设备访问本机弹幕服务。",
                    stateLabel = if (localNetworkAction == LocalNetworkPermissionAction.Settings) {
                        "前往设置"
                    } else {
                        "必须授权"
                    },
                    stateAccent = MaterialTheme.colorScheme.tertiary,
                    buttonText = if (localNetworkAction == LocalNetworkPermissionAction.Settings) {
                        "前往应用设置"
                    } else {
                        "允许局域网访问"
                    },
                    helper = if (localNetworkAction == LocalNetworkPermissionAction.Settings) {
                        "如果之前拒绝或系统不再弹窗，请在应用权限中允许局域网访问。"
                    } else {
                        "拒绝后服务仍可在本机使用，但其他设备访问时会连接超时。"
                    },
                    onClick = onOpenLocalNetworkPermission
                )
            }

            if (showLocalNetwork && (showNotification || showBattery)) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
                )
            }

            if (showNotification) {
                PermissionStepCard(
                    compact = compact,
                    icon = Icons.Rounded.NotificationsActive,
                    accent = MaterialTheme.colorScheme.primary,
                    title = "通知权限",
                    summary = "建议开启，方便确认服务是否真的已经启动。",
                    stateLabel = if (notificationAction == NotificationAction.Settings) "前往设置" else "待开启",
                    stateAccent = MaterialTheme.colorScheme.primary,
                    buttonText = if (notificationAction == NotificationAction.Settings) {
                        "前往设置"
                    } else {
                        "立即开启"
                    },
                    helper = if (notificationAction == NotificationAction.Settings) {
                        "如果之前点过拒绝，可以到系统设置里重新打开。"
                    } else {
                        "授权后回到应用，这里的状态会自动刷新。"
                    },
                    onClick = onOpenNotificationPermission
                )
            }

            if (showNotification && showBattery) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
                )
            }

            if (showBattery) {
                PermissionStepCard(
                    compact = compact,
                    icon = Icons.Rounded.BatterySaver,
                    accent = MaterialTheme.colorScheme.secondary,
                    title = "关闭电池优化",
                    summary = "建议改为不受限制，减少后台被系统清理。",
                    stateLabel = "建议处理",
                    stateAccent = MaterialTheme.colorScheme.secondary,
                    buttonText = "前往设置",
                    helper = "完成后回到应用，这里的状态会自动刷新。",
                    onClick = onOpenBatterySettings
                )
            }

            SettingsHintCard(
                text = if (showLocalNetwork) {
                    "暂不授权仍可进入首页，但只能在本机使用；下次启动会再次提醒。"
                } else {
                    "通知和电池项只影响提示与后台稳定，不影响你现在直接进入首页。"
                }
            )
        }

        GradientButton(
            text = if (showLocalNetwork) "仅本机使用并进入首页" else "进入首页",
            onClick = onContinueHome,
            enabled = true,
            modifier = Modifier.fillMaxWidth(),
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary
            )
        )
    }
}

@Composable
private fun SetupChoiceCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    summary: String,
    badge: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    AppGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(14.dp),
                color = accent.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = accent
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    SetupBadge(
                        text = badge,
                        accent = if (selected) MaterialTheme.colorScheme.primary else accent
                    )
                }
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SetupNoticeCard(
    title: String,
    summary: String,
    compact: Boolean
) {
    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (compact) 14.dp else 16.dp,
                vertical = if (compact) 12.dp else 14.dp
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PermissionStepCard(
    compact: Boolean,
    icon: ImageVector,
    accent: Color,
    title: String,
    summary: String,
    stateLabel: String,
    stateAccent: Color,
    buttonText: String,
    helper: String,
    onClick: () -> Unit
) {
    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (compact) 14.dp else 16.dp,
                vertical = if (compact) 12.dp else 14.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = accent.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = accent
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                SetupBadge(text = stateLabel, accent = stateAccent)
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick),
                shape = RoundedCornerShape(16.dp),
                color = accent.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = accent
                    )
                    Text(
                        text = buttonText,
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "去处理",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent.copy(alpha = 0.92f)
                    )
                }
            }

            Text(
                text = helper,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SetupBadge(
    text: String,
    accent: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
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

private fun coreBadgeText(
    variant: ApiVariant,
    runtimeState: RuntimeState,
    info: CoreInfo?,
    selectedVariant: ApiVariant
): String {
    return when {
        selectedVariant == variant -> "已选中"
        runtimeState.variant == variant -> "当前"
        info?.isInstalled == true -> "已安装"
        variant == ApiVariant.Stable -> "推荐"
        else -> "可选"
    }
}

private fun normalizeStartupVariant(variant: ApiVariant): ApiVariant {
    return when (variant) {
        ApiVariant.Dev -> ApiVariant.Dev
        ApiVariant.Custom -> ApiVariant.Custom
        else -> ApiVariant.Stable
    }
}

private fun readPermissionState(context: Context, runMode: RunMode): StartupPermissionState {
    val appContext = context.applicationContext
    val localNetworkRequired = Build.VERSION.SDK_INT >= LocalNetworkPermissionPolicy.ANDROID_17_API_LEVEL
    val localNetworkGranted = localNetworkRequired.not() || ContextCompat.checkSelfPermission(
        appContext,
        LocalNetworkPermissionPolicy.PERMISSION
    ) == PackageManager.PERMISSION_GRANTED
    return StartupPermissionState(
        runMode = runMode,
        notificationRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
        notificationGranted = NodeKeepAlivePrefs.hasPostNotificationsPermission(appContext),
        notificationRequestAttempted = StartupPermissionGatePrefs.hasRequestedNotificationPermission(appContext),
        batteryOptimizationIgnored = NormalModeKeepAliveGuideNavigator.isIgnoringBatteryOptimizations(appContext),
        localNetwork = LocalNetworkPermissionPolicy.stateFor(
            sdkInt = Build.VERSION.SDK_INT,
            granted = localNetworkGranted,
            requestAttempted = StartupPermissionGatePrefs.hasRequestedLocalNetworkPermission(appContext)
        )
    )
}

private fun resolveNotificationAction(
    activity: Activity?,
    permissionState: StartupPermissionState
): NotificationAction? {
    if (permissionState.notificationReady) return null
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    if (activity == null) return NotificationAction.Settings

    val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.POST_NOTIFICATIONS
    )
    return if (permissionState.notificationRequestAttempted.not() || shouldShowRationale) {
        NotificationAction.Request
    } else {
        NotificationAction.Settings
    }
}

private fun resolveLocalNetworkAction(
    activity: Activity?,
    state: LocalNetworkPermissionState
): LocalNetworkPermissionAction? {
    if (state.ready) return null
    val shouldShowRationale = activity?.let {
        ActivityCompat.shouldShowRequestPermissionRationale(
            it,
            LocalNetworkPermissionPolicy.PERMISSION
        )
    } == true
    return LocalNetworkPermissionPolicy.resolveAction(
        state = state,
        hasActivity = activity != null,
        shouldShowRationale = shouldShowRationale
    )
}

private fun openNotificationSettings(context: Context): Boolean {
    val packageUri = Uri.parse("package:${context.packageName}")
    val candidates = listOf(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        },
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = packageUri
        }
    )
    return candidates.any { launchIntent(context, it) }
}

private fun openAppDetailsSettings(context: Context): Boolean {
    return launchIntent(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
        }
    )
}

private fun launchIntent(context: Context, intent: Intent): Boolean {
    val finalIntent = Intent(intent).apply {
        if (context is Activity) {
            // keep current task
        } else {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    return runCatching {
        val resolved = finalIntent.resolveActivity(context.packageManager) != null
        if (resolved.not()) {
            false
        } else {
            context.startActivity(finalIntent)
            true
        }
    }.getOrDefault(false)
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
