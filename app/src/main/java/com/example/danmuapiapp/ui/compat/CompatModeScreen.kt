package com.example.danmuapiapp.ui.compat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Upgrade
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.danmuapiapp.data.service.TvConfigSyncCodec
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreDownloadProgress
import com.example.danmuapiapp.domain.model.CoreInfo
import com.example.danmuapiapp.domain.model.GithubProxyOption
import com.example.danmuapiapp.domain.model.NightModePreference
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.model.formatCoreVersionTransition
import com.example.danmuapiapp.domain.model.resolveCoreVariantSourceText
import com.example.danmuapiapp.ui.component.GithubProxyPickerDialog
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CompatModeActions(
    val onStartService: () -> Unit,
    val onRestartService: () -> Unit,
    val onStopService: () -> Unit,
    val onRefreshCoreInfo: () -> Unit,
    val onSwitchVariant: (ApiVariant) -> Unit,
    val onInstallCore: (ApiVariant) -> Unit,
    val onUpdateCore: (ApiVariant) -> Unit,
    val onCheckCoreUpdate: (ApiVariant) -> Unit,
    val onDeleteCore: (ApiVariant) -> Unit,
    val onSaveCustomCore: (String, String) -> Unit,
    val onToggleKeepAliveProfile: () -> Unit,
    val onCheckAppUpdate: () -> Unit,
    val onDownloadAppUpdate: () -> Unit,
    val onInstallAppUpdate: () -> Unit,
    val onToggleNightMode: () -> Unit,
    val onOpenProxyPicker: () -> Unit,
    val onSelectProxy: (String) -> Unit,
    val onRetestProxySpeed: () -> Unit,
    val onConfirmProxySelection: () -> Unit,
    val onDismissProxyPicker: () -> Unit,
    val onExitToBackground: () -> Unit,
    val onStopServiceAndExit: () -> Unit,
    val onExitCompatMode: () -> Unit
)

data class CompatProxyPickerState(
    val currentLabel: String,
    val options: List<GithubProxyOption>,
    val selectedId: String,
    val testingIds: Set<String>,
    val latencyMap: Map<String, Long>,
    val isVisible: Boolean
)

private enum class CompatPage {
    Home,
    Settings
}

@Composable
fun CompatModeScreen(
    uiState: CompatModeUiState,
    proxyPickerState: CompatProxyPickerState,
    actions: CompatModeActions
) {
    var currentPage by rememberSaveable { mutableStateOf(CompatPage.Home) }
    var showExitDialog by rememberSaveable { mutableStateOf(false) }
    var showExitCompatModeDialog by rememberSaveable { mutableStateOf(false) }
    val background = if (uiState.nightMode == NightModePreference.Dark) {
        Brush.verticalGradient(
            listOf(
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.surfaceContainerLow
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.surfaceContainerLow
            )
        )
    }

    BackHandler {
        if (currentPage == CompatPage.Settings) {
            currentPage = CompatPage.Home
        } else {
            showExitDialog = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 32.dp)
        ) {
            val wideLayout = maxWidth >= 960.dp
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                CompatHeader(
                    uiState = uiState,
                    actions = actions,
                    isWide = wideLayout,
                    currentPage = currentPage,
                    onOpenSettings = { currentPage = CompatPage.Settings },
                    onBackHome = { currentPage = CompatPage.Home }
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    item {
                        if (currentPage == CompatPage.Settings) {
                            CompatSettingsPage(
                                uiState = uiState,
                                proxyPickerState = proxyPickerState,
                                actions = actions,
                                wideLayout = wideLayout,
                                onRequestExitCompatMode = { showExitCompatModeDialog = true }
                            )
                        } else {
                            CompatHomePage(
                                uiState = uiState,
                                actions = actions
                            )
                        }
                    }
                }
            }
        }

        if (proxyPickerState.isVisible) {
            GithubProxyPickerDialog(
                title = "选择 GitHub 线路",
                subtitle = "首次下载核心前请先测速并选择线路",
                options = proxyPickerState.options,
                selectedId = proxyPickerState.selectedId,
                testingIds = proxyPickerState.testingIds,
                resultMap = proxyPickerState.latencyMap,
                onSelect = actions.onSelectProxy,
                onRetest = actions.onRetestProxySpeed,
                onConfirm = actions.onConfirmProxySelection,
                onDismiss = actions.onDismissProxyPicker,
                confirmText = "保存线路"
            )
        }

        if (showExitDialog) {
            ExitConfirmDialog(
                isRunning = uiState.runtimeState.status == ServiceStatus.Running,
                onDismiss = { showExitDialog = false },
                onBackground = {
                    showExitDialog = false
                    actions.onExitToBackground()
                },
                onStopAndExit = {
                    showExitDialog = false
                    actions.onStopServiceAndExit()
                }
            )
        }

        if (showExitCompatModeDialog) {
            ExitCompatModeDialog(
                onDismiss = { showExitCompatModeDialog = false },
                onConfirm = {
                    showExitCompatModeDialog = false
                    actions.onExitCompatMode()
                }
            )
        }
    }
}

@Composable
private fun CompatHomePage(
    uiState: CompatModeUiState,
    actions: CompatModeActions
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        ServiceHeroCard(uiState, actions)
        RuntimeStatusStrip(uiState)
        OperationProgressCard(uiState)
        CoreManagementCard(uiState, actions)
        SyncCard(uiState)
    }
}

@Composable
private fun CompatSettingsPage(
    uiState: CompatModeUiState,
    proxyPickerState: CompatProxyPickerState,
    actions: CompatModeActions,
    wideLayout: Boolean,
    onRequestExitCompatMode: () -> Unit
) {
    if (wideLayout) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                AppUpdateCard(uiState, actions)
                GithubProxyCard(proxyPickerState, actions)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                KeepAliveCard(uiState, actions)
                CompatModeExitCard(
                    onRequestExitCompatMode = onRequestExitCompatMode
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            AppUpdateCard(uiState, actions)
            KeepAliveCard(uiState, actions)
            GithubProxyCard(proxyPickerState, actions)
            CompatModeExitCard(
                onRequestExitCompatMode = onRequestExitCompatMode
            )
        }
    }
}

@Composable
private fun ExitConfirmDialog(
    isRunning: Boolean,
    onDismiss: () -> Unit,
    onBackground: () -> Unit,
    onStopAndExit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("退出弹幕 API？") },
        text = {
            Text(
                text = if (isRunning) {
                    "服务正在运行。选择后台运行会只关闭界面，服务继续提供访问；选择关闭退出会先停止服务再退出 App。"
                } else {
                    "当前服务未运行。你可以退到后台保留界面状态，也可以直接关闭退出。"
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onStopAndExit) {
                Text("关闭退出")
            }
        },
        dismissButton = {
            TextButton(onClick = onBackground) {
                Text("后台运行")
            }
        }
    )
}

@Composable
private fun ExitCompatModeDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("退出兼容模式？") },
        text = {
            Text(
                text = "TV 或一些旧设备退出兼容模式后，普通首页可能打不开或出现闪退。确认后会立即切换到普通首页，并在下次启动继续使用普通模式。"
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("仍要退出")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("继续使用兼容模式")
            }
        }
    )
}

@Composable
private fun CompatModeExitCard(
    onRequestExitCompatMode: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.34f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.42f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "兼容模式",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "当前正在使用 TV / 旧设备兼容首页。退出后会改用普通首页。",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(
                    text = "高风险",
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text(
                text = "如果普通首页在当前设备上无法渲染，App 可能打不开或闪退。建议只在确认普通模式可用时退出。",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            TvActionButton(
                text = "退出兼容模式",
                icon = Icons.Rounded.PowerSettingsNew,
                tone = ButtonTone.Danger,
                onClick = onRequestExitCompatMode
            )
        }
    }
}

@Composable
private fun CompatHeader(
    uiState: CompatModeUiState,
    actions: CompatModeActions,
    isWide: Boolean,
    currentPage: CompatPage,
    onOpenSettings: () -> Unit,
    onBackHome: () -> Unit
) {
    val runtime = uiState.runtimeState
    val statusColor = statusColor(runtime.status)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (isWide) 28.dp else 22.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "弹幕 API",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = if (isWide) 34.sp else 30.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (currentPage == CompatPage.Settings) "兼容模式设置" else "兼容模式控制台",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusPill(
                text = statusLabel(runtime.status),
                color = statusColor
            )
            NightModeButton(
                label = nightModeLabel(uiState.nightMode),
                onClick = actions.onToggleNightMode,
                focusEnabled = false
            )
            TvActionButton(
                text = if (currentPage == CompatPage.Settings) "首页" else "设置",
                icon = if (currentPage == CompatPage.Settings) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.Settings,
                tone = ButtonTone.Secondary,
                onClick = if (currentPage == CompatPage.Settings) onBackHome else onOpenSettings,
                focusEnabled = false
            )
            TvActionButton(
                text = "刷新",
                icon = Icons.Rounded.Refresh,
                tone = ButtonTone.Secondary,
                onClick = actions.onRefreshCoreInfo,
                focusEnabled = false
            )
        }
    }
}

@Composable
private fun ServiceHeroCard(
    uiState: CompatModeUiState,
    actions: CompatModeActions
) {
    val runtime = uiState.runtimeState
    val statusColor = statusColor(runtime.status)
    val isRunning = runtime.status == ServiceStatus.Running
    val isBusy = uiState.isOperating
    val primaryActionRequester = remember { FocusRequester() }
    LaunchedEffect(primaryActionRequester) {
        primaryActionRequester.requestFocus()
    }
    val cardTone = when (runtime.status) {
        ServiceStatus.Running -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f)
        ServiceStatus.Starting, ServiceStatus.Stopping -> MaterialTheme.colorScheme.primaryContainer
        ServiceStatus.Error -> MaterialTheme.colorScheme.errorContainer
        ServiceStatus.Stopped -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    ElevatedCard(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = cardTone),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "服务状态",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = statusLabel(runtime.status),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 30.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = statusColor
                    )
                }
                StatusPill(text = runtime.runMode.label, color = MaterialTheme.colorScheme.secondary)
            }

            Text(
                text = runtime.statusMessage?.takeIf { it.isNotBlank() }
                    ?: runtime.errorMessage?.takeIf { it.isNotBlank() }
                    ?: when (runtime.status) {
                        ServiceStatus.Running -> "服务正在正常提供局域网与本机访问地址。"
                        ServiceStatus.Starting -> "正在启动中，界面会继续刷新运行状态。"
                        ServiceStatus.Stopping -> "正在停止中，等待服务进程回收。"
                        ServiceStatus.Error -> "服务异常，建议先查看核心与运行模式。"
                        ServiceStatus.Stopped -> "当前尚未启动服务，可以直接启动或切换核心后再启动。"
                    },
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup()
            ) {
                TvActionButton(
                    text = if (isRunning) "重启服务" else "启动服务",
                    icon = if (isRunning) Icons.Rounded.RestartAlt else Icons.Rounded.PlayArrow,
                    tone = ButtonTone.Primary,
                    enabled = !isBusy,
                    onClick = if (isRunning) actions.onRestartService else actions.onStartService,
                    modifier = Modifier.focusRequester(primaryActionRequester)
                )
                TvActionButton(
                    text = "停止服务",
                    icon = Icons.Rounded.Stop,
                    tone = ButtonTone.Secondary,
                    enabled = isRunning && !isBusy,
                    onClick = actions.onStopService
                )
                TvActionButton(
                    text = if (uiState.keepAlive.recommendedProfileEnabled) "后台恢复 开" else "后台恢复 关",
                    icon = if (uiState.keepAlive.recommendedProfileEnabled) Icons.Rounded.Shield else Icons.Rounded.Security,
                    tone = ButtonTone.Secondary,
                    enabled = uiState.keepAlive.actionEnabled && !isBusy,
                    onClick = actions.onToggleKeepAliveProfile
                )
            }
        }
    }
}

@Composable
private fun RuntimeStatusStrip(uiState: CompatModeUiState) {
    val runtime = uiState.runtimeState
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compact = maxWidth < 680.dp
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricTile(
                            label = "当前核心",
                            value = resolveVariantLabel(uiState, runtime.variant),
                            icon = Icons.Rounded.Memory,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            label = "核心版本",
                            value = coreVersionText(
                                info = uiState.coreInfos.find { it.variant == runtime.variant },
                                isLoading = uiState.isCoreInfoLoading
                            ),
                            icon = Icons.Rounded.Upgrade,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricTile(
                            label = "运行模式",
                            value = if (runtime.runMode == RunMode.Root) "兼容 / Root" else "兼容 / 普通",
                            icon = Icons.Rounded.Security,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            label = "端口",
                            value = runtime.port.toString(),
                            icon = Icons.Rounded.Settings,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricTile(
                        label = "当前核心",
                        value = resolveVariantLabel(uiState, runtime.variant),
                        icon = Icons.Rounded.Memory,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = "核心版本",
                        value = coreVersionText(
                            info = uiState.coreInfos.find { it.variant == runtime.variant },
                            isLoading = uiState.isCoreInfoLoading
                        ),
                        icon = Icons.Rounded.Upgrade,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = "运行模式",
                        value = if (runtime.runMode == RunMode.Root) "兼容 / Root" else "兼容 / 普通",
                        icon = Icons.Rounded.Security,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = "端口",
                        value = runtime.port.toString(),
                        icon = Icons.Rounded.Settings,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        AccessAddressPanel(
            localUrl = runtime.localUrl,
            lanUrl = runtime.lanUrl
        )
    }
}

@Composable
private fun AccessAddressPanel(
    localUrl: String,
    lanUrl: String
) {
    val hasLocal = localUrl.isNotBlank()
    val hasLan = lanUrl.isNotBlank()
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "访问地址",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (hasLocal || hasLan) {
                            "本机和局域网地址都保留完整显示，方便直接输入或扫码。"
                        } else {
                            "服务启动后会显示本机与局域网访问地址。"
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(
                    text = if (hasLan) "已就绪" else "等待启动",
                    color = if (hasLan) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                )
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val compact = maxWidth < 620.dp
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AddressEntry(
                            label = "本机访问",
                            value = localUrl.ifBlank { "等待服务启动后生成" },
                            icon = Icons.Rounded.Settings,
                            accent = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth()
                        )
                        AddressEntry(
                            label = "局域网访问",
                            value = lanUrl.ifBlank { "等待局域网地址" },
                            icon = Icons.Rounded.Wifi,
                            accent = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AddressEntry(
                            label = "本机访问",
                            value = localUrl.ifBlank { "等待服务启动后生成" },
                            icon = Icons.Rounded.Settings,
                            accent = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        AddressEntry(
                            label = "局域网访问",
                            value = lanUrl.ifBlank { "等待局域网地址" },
                            icon = Icons.Rounded.Wifi,
                            accent = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddressEntry(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                lineHeight = 20.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun OperationProgressCard(uiState: CompatModeUiState) {
    val progress = uiState.downloadProgress
    val visible = progress.inProgress || uiState.isOperating
    AnimatedVisibility(visible = visible) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = progress.actionLabel.ifBlank {
                        uiState.operationProgressTitle.ifBlank { "处理中" }
                    },
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = buildString {
                        val stage = progress.stageText.ifBlank {
                            if (uiState.isOperating) "请稍候" else "正在准备资源"
                        }
                        append(stage)
                        val bytesText = formatByteProgress(progress)
                        if (bytesText.isNotBlank()) {
                            append("\n")
                            append(bytesText)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (progress.progress == null) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { progress.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun KeepAliveCard(
    uiState: CompatModeUiState,
    actions: CompatModeActions
) {
    val state = uiState.keepAlive
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.86f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "后台运行健康度",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.summary,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(
                    text = if (state.recommendedProfileEnabled) "推荐方案已启用" else "可配置",
                    color = if (state.recommendedProfileEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = state.detail,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvActionButton(
                    text = state.actionLabel,
                    icon = if (state.recommendedProfileEnabled) Icons.Rounded.Shield else Icons.Rounded.Security,
                    tone = ButtonTone.Primary,
                    enabled = state.actionEnabled,
                    onClick = actions.onToggleKeepAliveProfile
                )
                if (state.isRootMode) {
                    StatusChip(
                        text = "Root 模式",
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    StatusChip(
                        text = "心跳：${state.heartbeatModeLabel}",
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun GithubProxyCard(
    proxyPickerState: CompatProxyPickerState,
    actions: CompatModeActions
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.86f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "GitHub 加速",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "用于核心下载、更新和版本检查。",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(
                    text = proxyPickerState.currentLabel,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvActionButton(
                    text = "测速并选择",
                    icon = Icons.Rounded.Speed,
                    tone = ButtonTone.Primary,
                    onClick = actions.onOpenProxyPicker
                )
                TvActionButton(
                    text = "重新测速",
                    icon = Icons.Rounded.Refresh,
                    tone = ButtonTone.Secondary,
                    onClick = actions.onOpenProxyPicker
                )
            }

            Text(
                text = when {
                    proxyPickerState.testingIds.isNotEmpty() -> "正在测速 ${proxyPickerState.testingIds.size} 条线路"
                    proxyPickerState.latencyMap.isNotEmpty() -> "测速结果已更新，可继续切换线路"
                    else -> "当前线路已保存，必要时可重新测速。"
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AppUpdateCard(
    uiState: CompatModeUiState,
    actions: CompatModeActions
) {
    val update = uiState.appUpdate.checkResult
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.86f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "App 更新",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.appUpdate.checkError.takeIf { it.isNotBlank() }
                            ?: if (uiState.appUpdate.isChecking) {
                                "正在检查版本..."
                            } else if (update?.hasUpdate == true) {
                                "发现新版本 v${update.latestVersion}，当前 v${update.currentVersion}"
                            } else {
                                "当前版本 v${uiState.appUpdate.currentVersion}"
                            },
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(
                    text = when {
                        uiState.appUpdate.isChecking -> "检查中"
                        update?.hasUpdate == true -> "有更新"
                        else -> "最新"
                    },
                    color = when {
                        uiState.appUpdate.isChecking -> MaterialTheme.colorScheme.secondary
                        update?.hasUpdate == true -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }

            if (update?.hasUpdate == true) {
                Text(
                    text = update.releaseNotes.takeIf { it.isNotBlank() } ?: "未提供更新说明",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
                if (uiState.appUpdate.isDownloading) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = {
                                if (uiState.appUpdate.downloadPercent in 0..100) {
                                    uiState.appUpdate.downloadPercent / 100f
                                } else {
                                    0f
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                        )
                        Text(
                            text = uiState.appUpdate.downloadDetail,
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (uiState.appUpdate.downloadedApk == null) {
                        TvActionButton(
                            text = "下载更新",
                            icon = Icons.Rounded.CloudDownload,
                            tone = ButtonTone.Primary,
                            enabled = !uiState.appUpdate.isDownloading,
                            onClick = actions.onDownloadAppUpdate
                        )
                    } else {
                        TvActionButton(
                            text = "安装更新",
                            icon = Icons.Rounded.Upgrade,
                            tone = ButtonTone.Primary,
                            onClick = actions.onInstallAppUpdate
                        )
                    }
                    TvActionButton(
                        text = "检查版本",
                        icon = Icons.Rounded.Refresh,
                        tone = ButtonTone.Secondary,
                        enabled = !uiState.appUpdate.isChecking,
                        onClick = actions.onCheckAppUpdate
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvActionButton(
                        text = "检查版本",
                        icon = Icons.Rounded.Refresh,
                        tone = ButtonTone.Primary,
                        enabled = !uiState.appUpdate.isChecking,
                        onClick = actions.onCheckAppUpdate
                    )
                    if (uiState.appUpdate.downloadedApk != null) {
                        TvActionButton(
                            text = "安装缓存包",
                            icon = Icons.Rounded.Upgrade,
                            tone = ButtonTone.Secondary,
                            onClick = actions.onInstallAppUpdate
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoreManagementCard(
    uiState: CompatModeUiState,
    actions: CompatModeActions
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.86f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "核心管理",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "安装、更新、切换和删除都在这里完成。",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TvActionButton(
                    text = "刷新",
                    icon = Icons.Rounded.Refresh,
                    tone = ButtonTone.Secondary,
                    onClick = actions.onRefreshCoreInfo
                )
            }

            if (uiState.isCoreInfoLoading) {
                Text(
                    text = "正在读取核心信息...",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                uiState.coreInfos.forEach { info ->
                    CoreVariantCard(
                        uiState = uiState,
                        info = info,
                        actions = actions
                    )
                }
            }
        }
    }
}

@Composable
private fun CoreVariantCard(
    uiState: CompatModeUiState,
    info: CoreInfo,
    actions: CompatModeActions
) {
    val runtime = uiState.runtimeState
    val isActive = runtime.variant == info.variant
    val surfaceColor = if (isActive) {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.95f)
    }
    val badge = when {
        isActive -> "使用中"
        info.sourceMismatch -> "需替换"
        info.hasVersionUpdate -> "可更新"
        info.isInstalled -> "已安装"
        else -> "未安装"
    }
    val badgeColor = when {
        isActive -> MaterialTheme.colorScheme.primary
        info.sourceMismatch -> MaterialTheme.colorScheme.error
        info.hasVersionUpdate -> MaterialTheme.colorScheme.tertiary
        info.isInstalled -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline
    }
    val sourceText = resolveVariantSource(uiState, info.variant)
    val mainText = when {
        !info.isInstalled -> "下载核心"
        info.sourceMismatch -> "重新下载"
        info.hasVersionUpdate -> "立即更新"
        else -> "检查更新"
    }
    val mainIcon = when {
        !info.isInstalled -> Icons.Rounded.CloudDownload
        info.sourceMismatch || info.hasVersionUpdate -> Icons.Rounded.Upgrade
        else -> Icons.Rounded.Refresh
    }
    val mainPrimary = !info.isInstalled || info.sourceMismatch || info.hasVersionUpdate
    val canDelete = info.isInstalled && !isActive
    val activeProgress = uiState.downloadProgress.takeIf {
        it.inProgress && it.variant == info.variant
    }
    var isCustomEditing by rememberSaveable(info.variant.name) { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = surfaceColor,
        border = BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = resolveVariantLabel(uiState, info.variant),
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        StatusChip(text = badge, color = badgeColor)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = coreVersionText(info, uiState.isCoreInfoLoading),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        color = if (info.needsAttention) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (sourceText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = sourceText,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (info.variant == ApiVariant.Custom) {
                    StatusChip(
                        text = "自定义",
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            if (info.variant == ApiVariant.Custom) {
                CustomCoreEditor(
                    uiState = uiState,
                    actions = actions,
                    isEditing = isCustomEditing,
                    onEditingChange = { isCustomEditing = it }
                )
            }

            if (activeProgress != null) {
                CoreVariantProgress(progress = activeProgress)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (info.variant == ApiVariant.Custom) {
                    TvActionButton(
                        text = if (isCustomEditing) "收起编辑" else "编辑配置",
                        icon = if (isCustomEditing) Icons.Rounded.ExpandLess else Icons.Rounded.Edit,
                        tone = ButtonTone.Secondary,
                        enabled = !uiState.isOperating,
                        onClick = { isCustomEditing = !isCustomEditing }
                    )
                }
                if (!isActive) {
                    TvActionButton(
                        text = "切换使用",
                        icon = Icons.Rounded.Sync,
                        tone = ButtonTone.Secondary,
                        enabled = info.isReady && !uiState.isOperating,
                        onClick = { actions.onSwitchVariant(info.variant) }
                    )
                }
                TvActionButton(
                    text = mainText,
                    icon = mainIcon,
                    tone = if (mainPrimary) ButtonTone.Primary else ButtonTone.Secondary,
                    enabled = !uiState.isOperating,
                    onClick = {
                        when {
                            !info.isInstalled -> actions.onInstallCore(info.variant)
                            info.needsAttention -> actions.onUpdateCore(info.variant)
                            else -> actions.onCheckCoreUpdate(info.variant)
                        }
                    }
                )
                if (canDelete) {
                    TvActionButton(
                        text = "删除",
                        icon = Icons.Rounded.Delete,
                        tone = ButtonTone.Danger,
                        enabled = !uiState.isOperating,
                        onClick = { actions.onDeleteCore(info.variant) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CoreVariantProgress(progress: CoreDownloadProgress) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = progress.actionLabel.ifBlank { "正在处理核心" },
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                val detail = buildString {
                    if (progress.stageText.isNotBlank()) append(progress.stageText)
                    val bytes = formatByteProgress(progress)
                    if (bytes.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(bytes)
                    }
                }
                if (detail.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (progress.progress != null) {
                Text(
                    text = "${(progress.progress.coerceIn(0f, 1f) * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (progress.progress == null) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
            )
        } else {
            LinearProgressIndicator(
                progress = { progress.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
            )
        }
    }
}

@Composable
private fun CustomCoreEditor(
    uiState: CompatModeUiState,
    actions: CompatModeActions,
    isEditing: Boolean,
    onEditingChange: (Boolean) -> Unit
) {
    val source = uiState.customCoreSource
    val focusManager = LocalFocusManager.current
    var repoText by rememberSaveable(uiState.customRepo) { mutableStateOf(uiState.customRepo) }
    var branchText by rememberSaveable(uiState.customRepoBranch) { mutableStateOf(uiState.customRepoBranch) }

    LaunchedEffect(uiState.customRepo, uiState.customRepoBranch) {
        repoText = uiState.customRepo
        branchText = uiState.customRepoBranch
    }

    val saveAction = {
        actions.onSaveCustomCore(repoText.trim(), branchText.trim())
        focusManager.clearFocus(force = true)
        onEditingChange(false)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                .padding(14.dp)
        ) {
            val compact = maxWidth < 540.dp
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = when {
                            source.isValidRepo -> "当前来源：${source.sourceText}"
                            source.isConfigured -> "仓库已配置，等待有效分支"
                            else -> "未配置自定义仓库"
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "自定义核心来源",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                source.isValidRepo -> "当前来源：${source.sourceText}"
                                source.isConfigured -> "仓库已配置，等待有效分支"
                                else -> "未配置自定义仓库"
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = isEditing) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val compact = maxWidth < 560.dp
                    if (compact) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = repoText,
                                onValueChange = { repoText = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("仓库") },
                                placeholder = { Text("owner/repo 或 GitHub 地址") },
                                singleLine = true,
                                maxLines = 1,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onDone = { saveAction() })
                            )
                            OutlinedTextField(
                                value = branchText,
                                onValueChange = { branchText = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("分支") },
                                placeholder = { Text(source.suggestedBranch.ifBlank { "main" }) },
                                singleLine = true,
                                maxLines = 1,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { saveAction() })
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = repoText,
                                onValueChange = { repoText = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("仓库") },
                                placeholder = { Text("owner/repo 或 GitHub 地址") },
                                singleLine = true,
                                maxLines = 1,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onDone = { saveAction() })
                            )
                            OutlinedTextField(
                                value = branchText,
                                onValueChange = { branchText = it },
                                modifier = Modifier.widthIn(min = 170.dp).weight(0.55f),
                                label = { Text("分支") },
                                placeholder = { Text(source.suggestedBranch.ifBlank { "main" }) },
                                singleLine = true,
                                maxLines = 1,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { saveAction() })
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvActionButton(
                        text = "保存自定义核心",
                        icon = Icons.Rounded.Settings,
                        tone = ButtonTone.Primary,
                        enabled = !uiState.isOperating,
                        onClick = saveAction
                    )
                    TvActionButton(
                        text = "取消编辑",
                        icon = Icons.Rounded.ExpandLess,
                        tone = ButtonTone.Secondary,
                        enabled = !uiState.isOperating,
                        onClick = {
                            repoText = uiState.customRepo
                            branchText = uiState.customRepoBranch
                            focusManager.clearFocus(force = true)
                            onEditingChange(false)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncCard(uiState: CompatModeUiState) {
    val sync = uiState.syncState
    val inviteUrl = sync.inviteUrl
    val qrBitmap by produceState(initialValue = null as androidx.compose.ui.graphics.ImageBitmap?, inviteUrl) {
        value = if (inviteUrl.isBlank()) {
            null
        } else {
            withContext(Dispatchers.Default) {
                TvConfigSyncCodec.buildQrBitmap(inviteUrl, 540).asImageBitmap()
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.86f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "手机同步",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (inviteUrl.isNotBlank()) {
                            "手机端进入备份与恢复，扫码后即可推送当前配置。"
                        } else {
                            "请让电视和手机接入同一 Wi-Fi，获取局域网地址后会自动生成同步码。"
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(
                    text = if (inviteUrl.isNotBlank()) "可扫码" else "等待局域网",
                    color = if (inviteUrl.isNotBlank()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(184.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = qrBitmap!!,
                            contentDescription = "手机同步二维码",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .padding(10.dp)
                                .fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.QrCode2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(72.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = sync.statusText,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (sync.lastSyncSummary.isNotBlank()) {
                        Text(
                            text = sync.lastSyncSummary,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (inviteUrl.isNotBlank()) {
                            "配对地址：${sync.host}:${sync.port}"
                        } else {
                            "当前未检测到可用局域网地址"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "同步时会自动覆盖电视侧配置，适合在手机上集中整理后一次性推送。",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            color = color,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = color,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun NightModeButton(
    label: String,
    onClick: () -> Unit,
    focusEnabled: Boolean = true
) {
    TvActionButton(
        text = label,
        icon = if (label.contains("浅")) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
        tone = ButtonTone.Secondary,
        onClick = onClick,
        focusEnabled = focusEnabled
    )
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier.heightIn(min = 112.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                lineHeight = 19.sp,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

enum class ButtonTone {
    Primary,
    Secondary,
    Danger
}

@Composable
private fun TvActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tone: ButtonTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusEnabled: Boolean = true
) {
    val baseColor = when (tone) {
        ButtonTone.Primary -> MaterialTheme.colorScheme.primary
        ButtonTone.Secondary -> MaterialTheme.colorScheme.surfaceContainerHighest
        ButtonTone.Danger -> MaterialTheme.colorScheme.error
    }
    val contentColor = when (tone) {
        ButtonTone.Primary -> MaterialTheme.colorScheme.onPrimary
        ButtonTone.Secondary -> MaterialTheme.colorScheme.onSurface
        ButtonTone.Danger -> MaterialTheme.colorScheme.onError
    }
    val disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
    val disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    var focused by remember { mutableStateOf(false) }
    val focusedScale by animateFloatAsState(
        targetValue = if (enabled && focused) 1.05f else 1f,
        label = "tv_button_scale"
    )
    val shape = RoundedCornerShape(18.dp)

    Surface(
        shape = shape,
        color = if (enabled) baseColor else disabledContainerColor,
        contentColor = if (enabled) contentColor else disabledContentColor,
        border = BorderStroke(
            if (focused && enabled) 2.dp else 1.dp,
            when (tone) {
                ButtonTone.Primary -> {
                    if (!enabled) {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    } else if (focused) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.95f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
                    }
                }
                ButtonTone.Secondary -> {
                    if (!enabled) {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    } else if (focused) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)
                    }
                }
                ButtonTone.Danger -> {
                    if (!enabled) {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    } else if (focused) {
                        MaterialTheme.colorScheme.onError.copy(alpha = 0.95f)
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.95f)
                    }
                }
            }
        ),
        modifier = modifier
            .widthIn(min = 118.dp)
            .heightIn(min = 54.dp)
            .graphicsLayer {
                scaleX = if (enabled) focusedScale else 1f
                scaleY = if (enabled) focusedScale else 1f
            }
            .shadow(if (focused && enabled) 10.dp else if (enabled) 2.dp else 0.dp, shape = shape, clip = false)
            .focusProperties { canFocus = enabled && focusEnabled }
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .focusable(enabled && focusEnabled)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun resolveVariantLabel(
    uiState: CompatModeUiState,
    variant: ApiVariant
): String {
    return uiState.coreDisplayNames.resolve(variant)
}

private fun resolveVariantSource(
    uiState: CompatModeUiState,
    variant: ApiVariant
): String {
    return resolveCoreVariantSourceText(
        variant = variant,
        customRepo = uiState.customRepo,
        customBranch = uiState.customRepoBranch
    )
}

private fun coreVersionText(info: CoreInfo?, isLoading: Boolean): String {
    return when {
        info == null -> if (isLoading) "读取中" else "未知"
        !info.isInstalled -> "未安装"
        info.hasVersionUpdate && !info.version.isNullOrBlank() ->
            formatCoreVersionTransition(info.version, info.availableVersion)
        !info.version.isNullOrBlank() -> formatCoreVersionTransition(info.version, null)
        else -> "版本未知"
    }
}

private fun statusLabel(status: ServiceStatus): String = when (status) {
    ServiceStatus.Stopped -> "已停止"
    ServiceStatus.Starting -> "启动中"
    ServiceStatus.Running -> "运行中"
    ServiceStatus.Stopping -> "停止中"
    ServiceStatus.Error -> "异常"
}

private fun statusColor(status: ServiceStatus): Color = when (status) {
    ServiceStatus.Stopped -> Color(0xFF748197)
    ServiceStatus.Starting -> Color(0xFF4F8CFF)
    ServiceStatus.Running -> Color(0xFF48C78E)
    ServiceStatus.Stopping -> Color(0xFFFFC857)
    ServiceStatus.Error -> Color(0xFFFF7C8A)
}

private fun nightModeLabel(mode: NightModePreference): String {
    return when (mode) {
        NightModePreference.Dark -> "浅色"
        NightModePreference.Light -> "深色"
        NightModePreference.FollowSystem -> "深色"
    }
}

private fun formatByteProgress(progress: CoreDownloadProgress): String {
    if (progress.downloadedBytes <= 0L && progress.totalBytes <= 0L) return ""
    return buildString {
        append(formatBytes(progress.downloadedBytes))
        if (progress.totalBytes > 0L) {
            append(" / ")
            append(formatBytes(progress.totalBytes))
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb -> String.format(Locale.getDefault(), "%.2f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.getDefault(), "%.2f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.getDefault(), "%.2f KB", bytes / kb)
        else -> "$bytes B"
    }
}
