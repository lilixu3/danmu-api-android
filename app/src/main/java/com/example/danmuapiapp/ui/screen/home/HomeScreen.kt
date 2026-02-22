package com.example.danmuapiapp.ui.screen.home

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.ui.component.GithubProxyPickerDialog
import com.example.danmuapiapp.ui.component.GradientButton
import com.example.danmuapiapp.ui.component.StatusIndicator
import java.net.URI
import java.util.Locale

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.runtimeState.collectAsStateWithLifecycle()
    val coreList by viewModel.coreInfoList.collectAsStateWithLifecycle()
    val isCoreInfoLoading by viewModel.isCoreInfoLoading.collectAsStateWithLifecycle()
    val tokenVisible by viewModel.tokenVisible.collectAsStateWithLifecycle()
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val clipboardManager = LocalClipboard.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { context.findActivity() }
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
                    uptime = uptimeText
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
                    onCheckCoreUpdate = { showCoreUpdateConfirmDialog = true }
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
        AlertDialog(
            onDismissRequest = {
                if (!isBusy) {
                    showQuickPortDialog = false
                }
            },
            icon = { Icon(Icons.Rounded.Lan, null) },
            title = { Text("快速修改端口") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (state.status == ServiceStatus.Running) {
                            "保存后会先停止旧端口，再启动新端口。"
                        } else {
                            "服务未运行，保存后将直接更新端口配置。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = quickPortText,
                        onValueChange = {
                            quickPortText = it
                            quickPortError = null
                        },
                        label = { Text("端口") },
                        singleLine = true,
                        isError = quickPortError != null,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        supportingText = {
                            val hint = if (state.runMode == RunMode.Normal) {
                                "普通模式支持 1024 – 65535（Root 支持 1 – 65535）"
                            } else {
                                "范围 1 – 65535"
                            }
                            Text(quickPortError ?: hint)
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isBusy,
                    onClick = {
                        val port = quickPortText.trim().toIntOrNull()
                        if (port == null || port !in 1..65535) {
                            quickPortError = "请输入有效端口（1-65535）"
                            return@TextButton
                        }
                        if (state.runMode == RunMode.Normal && port in 1..1023) {
                            quickPortError = "普通模式仅支持 1024-65535，请切换 Root 模式后再使用低位端口"
                            return@TextButton
                        }
                        if (port == state.port) {
                            quickPortError = "端口未变化"
                            return@TextButton
                        }
                        viewModel.applyPortQuick(port)
                        showQuickPortDialog = false
                    }
                ) {
                    Text(if (state.status == ServiceStatus.Running) "保存并重启" else "保存")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isBusy,
                    onClick = { showQuickPortDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
    }

    if (showQuickTokenDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isBusy) {
                    showQuickTokenDialog = false
                }
            },
            icon = { Icon(Icons.Rounded.Tune, null) },
            title = { Text("快速修改 Token") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (state.status == ServiceStatus.Running) {
                            "保存后将热更新到运行中的服务，无需重启。"
                        } else {
                            "保存后将写入运行配置，启动时自动生效。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = quickTokenText,
                        onValueChange = {
                            quickTokenText = it
                            quickTokenError = null
                        },
                        label = { Text("Token（可留空）") },
                        singleLine = true,
                        isError = quickTokenError != null,
                        supportingText = {
                            Text(quickTokenError ?: "留空将恢复核心默认 Token")
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isBusy,
                    onClick = {
                        val normalized = quickTokenText.trim()
                        if (normalized == state.token) {
                            quickTokenError = "Token 未变化"
                            return@TextButton
                        }
                        viewModel.applyTokenQuick(normalized)
                        showQuickTokenDialog = false
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isBusy,
                    onClick = { showQuickTokenDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
    }

    if (showCoreUpdateConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCoreUpdateConfirmDialog = false },
            icon = { Icon(Icons.Rounded.SystemUpdateAlt, null) },
            title = { Text("检查核心更新") },
            text = {
                Text(
                    text = "将检查 ${state.variant.label} 是否有新版本。为避免误触，是否继续？",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCoreUpdateConfirmDialog = false
                        viewModel.quickCheckCurrentCoreUpdate()
                    }
                ) {
                    Text("继续检查")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCoreUpdateConfirmDialog = false }) {
                    Text("取消")
                }
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
}

@Composable
private fun HomeTopHeader(
    status: ServiceStatus,
    isRunning: Boolean,
    uptime: String
) {
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
        if (isRunning) {
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
                    title = "服务状态",
                    value = statusShortLabel(status),
                    accent = statusAccentColor(status)
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
