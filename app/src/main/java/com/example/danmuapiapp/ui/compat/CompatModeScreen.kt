package com.example.danmuapiapp.ui.compat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.data.util.DeviceCompatMode
import com.example.danmuapiapp.data.util.InterfaceMode
import com.example.danmuapiapp.domain.model.*
import com.example.danmuapiapp.ui.component.CoreDependencyRepairHost
import com.example.danmuapiapp.ui.component.remoteFocusHighlight
import kotlinx.coroutines.launch

data class CompatModeActions(
    val onStartService: () -> Unit,
    val onRestartService: () -> Unit,
    val onStopService: () -> Unit,
    val onRefreshCoreInfo: () -> Unit,
    val onSwitchVariant: (ApiVariant) -> Unit,
    val onInstallCore: (ApiVariant) -> Unit,
    val onUpdateCore: (ApiVariant) -> Unit,
    val onCheckCoreUpdate: (ApiVariant) -> Unit,
    val onOpenBranchPicker: (ApiVariant) -> Unit,
    val onRetryBranches: () -> Unit,
    val onSwitchCoreBranch: (String) -> Unit,
    val onDismissBranchPicker: () -> Unit,
    val onDeleteCore: (ApiVariant) -> Unit,
    val onSaveCustomCore: (String, String) -> Unit,
    val onToggleKeepAliveProfile: () -> Unit,
    val onOpenNotificationPermission: () -> Unit,
    val onOpenBatterySettings: () -> Unit,
    val onCheckAppUpdate: () -> Unit,
    val onDownloadAppUpdate: () -> Unit,
    val onInstallAppUpdate: () -> Unit,
    val onToggleNightMode: () -> Unit,
    val onSetIpv6Enabled: (Boolean) -> Unit,
    val onSetAppDpiOverride: (Int) -> Unit,
    val onOpenProxyPicker: () -> Unit,
    val onSelectProxy: (String) -> Unit,
    val onRetestProxySpeed: () -> Unit,
    val onConfirmProxySelection: () -> Unit,
    val onDismissProxyPicker: () -> Unit,
    val onOpenDependencyRepair: () -> Unit,
    val onDismissDependencyRequired: () -> Unit,
    val onRepairDependenciesOnline: () -> Unit,
    val onRepairDependenciesFromArchive: (String) -> Unit,
    val onCancelPendingCoreMutation: () -> Unit,
    val onDismissDependencyRepair: () -> Unit,
    val onExitToBackground: () -> Unit,
    val onStopServiceAndExit: () -> Unit,
    val onSetInterfaceMode: (InterfaceMode) -> Unit
)

data class CompatProxyPickerState(
    val currentLabel: String,
    val options: List<GithubProxyOption>,
    val selectedId: String,
    val testingIds: Set<String>,
    val latencyMap: Map<String, Long>,
    val isVisible: Boolean
)

internal enum class CompatPage(val title: String, val subtitle: String, val icon: ImageVector) {
    // Keep the historical enum name so Android can unparcel saved state after an update.
    Home("概览", "服务运行与访问地址", Icons.Rounded.Dashboard),
    Cores("核心", "管理安装版本与来源", Icons.Rounded.Layers),
    Connection("同步", "设备配置同步", Icons.Rounded.Devices),
    Logs("日志", "实时日志与问题排查", Icons.Rounded.Terminal),
    Config("配置", "系统配置与管理员权限", Icons.Rounded.SettingsSuggest),
    Settings("设置", "后台运行、线路与界面", Icons.Rounded.Tune)
}

private enum class CompatModal { None, Exit, Stop, Restart, StopExit, Delete, Reinstall, Custom, Scale, Interface }

@Composable
internal fun CompatModeScreen(
    uiState: CompatModeUiState,
    proxyPickerState: CompatProxyPickerState,
    showDependencyRequiredPrompt: Boolean,
    showDependencyRepairDialog: Boolean,
    showLocalNetworkPermissionHint: Boolean,
    onOpenLocalNetworkPermission: () -> Unit,
    actions: CompatModeActions,
    management: CompatManagementState,
    logs: List<LogEntry>,
    onPageOpened: (CompatPage) -> Unit,
    onRefreshLogs: () -> Unit,
    onRefreshConfig: () -> Unit,
    onAdminLogin: (String) -> Unit,
    onSaveConfig: (CompatConfigEntry, String, Boolean) -> Unit
) {
    var page by rememberSaveable { mutableStateOf(CompatPage.Home) }
    var modal by rememberSaveable { mutableStateOf(CompatModal.None) }
    var deleteVariantKey by rememberSaveable { mutableStateOf(ApiVariant.Stable.key) }
    var reinstallVariantKey by rememberSaveable { mutableStateOf(ApiVariant.Stable.key) }
    val listStates = CompatPage.entries.map { rememberLazyListState() }
    val contentFocus = remember { FocusRequester() }
    val navigationFocus = remember { CompatPage.entries.map { FocusRequester() } }
    val scope = rememberCoroutineScope()
    val pageState = rememberSaveableStateHolder()
    val context = LocalContext.current
    val remoteDevice = remember(context) { DeviceCompatMode.isCompatModeDevice(context) }
    val busy = uiState.isOperating || uiState.runtimeState.status in setOf(ServiceStatus.Starting, ServiceStatus.Stopping)

    LaunchedEffect(page) { onPageOpened(page) }
    fun navigate(target: CompatPage) { page = target }
    BackHandler(enabled = modal == CompatModal.None && !proxyPickerState.isVisible &&
        uiState.branchDialogVariant == null && !showDependencyRequiredPrompt && !showDependencyRepairDialog) {
        if (page != CompatPage.Home) {
            page = CompatPage.Home
            scope.launch {
                withFrameNanos { }
                navigationFocus[0].requestFocus()
            }
        } else modal = CompatModal.Exit
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .safeDrawingPadding().imePadding()) {
        val layout = CompatLayoutPolicy.resolve(maxWidth.value, maxHeight.value, LocalDensity.current.fontScale)
        CompositionLocalProvider(LocalCompatLayout provides layout) {
            Row(Modifier.fillMaxSize().padding(layout.outerPadding.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                if (layout.useRail) {
                    CompatNavigationRail(page, uiState, navigationFocus, contentFocus, ::navigate)
                }
                Column(Modifier.weight(1f).fillMaxHeight()
                    .focusRequester(contentFocus).focusRestorer()
                    .focusProperties {
                        onExit = {
                            if (layout.useRail && requestedFocusDirection == FocusDirection.Left) {
                                navigationFocus[page.ordinal].requestFocus()
                            }
                        }
                    }.focusGroup(), verticalArrangement = Arrangement.spacedBy(layout.gap.dp)) {
                    if (!layout.useRail) {
                        CompatTopNavigation(page, navigationFocus, ::navigate)
                    } else {
                        CompatPageHeading(page.title, page.subtitle, uiState)
                    }
                    pageState.SaveableStateProvider(page.name) {
                        if (page == CompatPage.Home) {
                            CompatHomePage(uiState, busy, showLocalNetworkPermissionHint, onOpenLocalNetworkPermission,
                                onStart = actions.onStartService, onRestart = { modal = CompatModal.Restart },
                                onStop = { modal = CompatModal.Stop },
                                onCores = { navigate(CompatPage.Cores) }, onSettings = {
                                    pageState.removeState(CompatPage.Settings.name)
                                    navigate(CompatPage.Settings)
                                },
                                firstActionModifier = if (layout.useRail) Modifier.focusProperties {
                                    left = navigationFocus[page.ordinal]
                                } else Modifier,
                                listState = listStates[page.ordinal], modifier = Modifier.weight(1f).fillMaxWidth())
                        } else if (page == CompatPage.Logs) {
                            CompatLogsPage(logs, uiState.runtimeState, onRefreshLogs,
                                listStates[page.ordinal], Modifier.weight(1f).fillMaxWidth())
                        } else if (page == CompatPage.Config) {
                            CompatConfigPage(management, uiState.runtimeState, onRefreshConfig, onAdminLogin, onSaveConfig,
                                listStates[page.ordinal], Modifier.weight(1f).fillMaxWidth())
                        } else if (page == CompatPage.Settings) {
                            CompatSettingsPane(uiState, proxyPickerState, actions, busy,
                                interactionBlocked = modal != CompatModal.None || proxyPickerState.isVisible ||
                                    uiState.branchDialogVariant != null || showDependencyRequiredPrompt || showDependencyRepairDialog,
                                onScale = { modal = CompatModal.Scale }, onInterface = { modal = CompatModal.Interface },
                                onStopExit = { modal = CompatModal.StopExit }, modifier = Modifier.weight(1f).fillMaxWidth())
                        } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listStates[page.ordinal],
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(layout.gap.dp)) {
                            when (page) {
                                CompatPage.Home, CompatPage.Settings, CompatPage.Logs, CompatPage.Config -> Unit
                                CompatPage.Cores -> {
                                    item("core-header") {
                                        CompatSectionHeading("核心库", "选择版本，下载后即可使用", "刷新", actions.onRefreshCoreInfo)
                                    }
                                    if (uiState.coreInfos.isEmpty()) item("empty") {
                                        CompatEmptyCard(if (uiState.isCoreInfoLoading) "正在读取核心…" else "暂未读取到核心信息",
                                            "可以刷新重试。", "刷新", actions.onRefreshCoreInfo)
                                    }
                                    uiState.coreInfos.forEach { info ->
                                        item("core-${info.variant.key}") {
                                            CompatCoreCard(uiState, info, actions,
                                                onEditCustom = { modal = CompatModal.Custom },
                                                onDelete = { deleteVariantKey = info.variant.key; modal = CompatModal.Delete },
                                                onReinstall = { reinstallVariantKey = info.variant.key; modal = CompatModal.Reinstall })
                                        }
                                    }
                                }
                                CompatPage.Connection -> {
                                    item("sync") { CompatSyncCard(uiState.syncState) }
                                }
                            }
                            if (page == CompatPage.Cores && (uiState.isOperating || uiState.downloadProgress.inProgress)) item("operation-progress") {
                                CompatOperationCard(uiState)
                            }
                        }
                    }
                }
            }
            LaunchedEffect(remoteDevice) {
                if (remoteDevice) {
                    withFrameNanos { }
                    navigationFocus[page.ordinal].requestFocus()
                }
            }
        }
    }

    if (proxyPickerState.isVisible) CompatProxyDialog(proxyPickerState, actions)
    uiState.branchDialogVariant?.let { variant ->
        CompatBranchDialog(variantLabel = uiState.coreDisplayNames.resolve(variant),
            catalog = uiState.branchCatalog, currentBranch = uiState.coreBranchSelections.resolve(variant),
            isLoading = uiState.isLoadingBranches, errorMessage = uiState.branchLoadError,
            actions = actions)
    }
    CoreDependencyRepairHost(request = uiState.pendingDependencyRepair,
        showRequiredPrompt = showDependencyRequiredPrompt, showRepairDialog = showDependencyRepairDialog,
        onOpenRepair = actions.onOpenDependencyRepair, onDismissRequiredPrompt = actions.onDismissDependencyRequired,
        onOnlineRepair = actions.onRepairDependenciesOnline, onRepairFromArchive = actions.onRepairDependenciesFromArchive,
        onCancelMutation = actions.onCancelPendingCoreMutation, onDismissRepairDialog = actions.onDismissDependencyRepair)

    val dismiss = { modal = CompatModal.None }
    when (modal) {
        CompatModal.None -> Unit
        CompatModal.Exit -> CompatExitDialog(uiState.runtimeState.status, busy, dismiss,
            onKeepService = { dismiss(); actions.onExitToBackground() },
            onStopAndExit = { dismiss(); actions.onStopServiceAndExit() })
        CompatModal.Stop, CompatModal.StopExit -> CompatDialog("停止服务？", dismiss,
            if (modal == CompatModal.StopExit) "停止并退出" else "停止服务", {
                val exit = modal == CompatModal.StopExit
                dismiss()
                if (exit) actions.onStopServiceAndExit() else actions.onStopService()
            }, danger = true, confirmEnabled = !busy) {
            Text("停止后，其他设备将无法访问本机弹幕服务。需要时可再次手动启动。")
        }
        CompatModal.Restart -> CompatDialog("重启服务？", dismiss, "重启", {
            dismiss(); actions.onRestartService()
        }, confirmEnabled = !busy) { Text("重启期间访问会短暂中断，完成后恢复。") }
        CompatModal.Delete -> {
            val variant = ApiVariant.entries.firstOrNull { it.key == deleteVariantKey } ?: ApiVariant.Stable
            CompatDialog("删除${uiState.coreDisplayNames.resolve(variant)}？", dismiss, "删除", {
                dismiss(); actions.onDeleteCore(variant)
            }, danger = true, confirmEnabled = !busy &&
                uiState.pendingDependencyRepair?.variant != variant &&
                uiState.coreInfos.any { it.variant == variant && it.isInstalled }) {
                Text("删除已下载的核心文件，保留服务配置。需要使用时可以重新下载安装。")
                if (uiState.runtimeState.variant == variant && uiState.runtimeState.status == ServiceStatus.Running) {
                    Text("将先停止当前服务，其他设备的访问会中断。", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        CompatModal.Reinstall -> {
            val variant = ApiVariant.entries.firstOrNull { it.key == reinstallVariantKey } ?: ApiVariant.Stable
            CompatDialog("重装${uiState.coreDisplayNames.resolve(variant)}？", dismiss, "重新下载", {
                dismiss(); actions.onInstallCore(variant)
            }, confirmEnabled = !busy && uiState.pendingDependencyRepair?.variant != variant &&
                uiState.coreInfos.any { it.variant == variant && it.isInstalled }) {
                Text("将按当前仓库和分支重新下载核心文件，替换已安装版本。")
                Text("本地修改及已组合的 PR 会被替换。", color = MaterialTheme.colorScheme.tertiary)
                if (uiState.runtimeState.variant == variant && uiState.runtimeState.status == ServiceStatus.Running) {
                    Text("当前核心正在运行，完成后会重启服务，访问将短暂中断。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        CompatModal.Custom -> CompatCustomSourceDialog(uiState, actions, dismiss)
        CompatModal.Scale -> CompatScaleDialog(uiState.appDpiOverride, actions.onSetAppDpiOverride, dismiss)
        CompatModal.Interface -> CompatInterfaceDialog(actions.onSetInterfaceMode, dismiss)
    }
}

@Composable
private fun CompatNavigationRail(page: CompatPage, state: CompatModeUiState,
    focus: List<FocusRequester>, contentFocus: FocusRequester, onNavigate: (CompatPage) -> Unit) {
    Column(Modifier.width(172.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .focusRestorer().focusGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CompatBrand()
            Spacer(Modifier.height(12.dp))
            CompatPage.entries.forEach { target ->
                CompatNavItem(target, page == target, onClick = { onNavigate(target) },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus[target.ordinal])
                        .focusProperties { right = contentFocus })
            }
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("当前服务", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
            CompatBadge(compatStatusLabel(state.runtimeState.status), compatStatusColor(state.runtimeState.status))
            Text("${state.runtimeState.runMode.label} · TCP ${state.runtimeState.port}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CompatTopNavigation(page: CompatPage, focus: List<FocusRequester>, onNavigate: (CompatPage) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = maxWidth
        val showBrand = width >= 700.dp && LocalDensity.current.fontScale <= 1.3f
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (showBrand) CompatBrand()
            val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            val showIcon = width >= 700.dp && fontScale <= 1.3f
            val available = width.value - if (showBrand) 150f else 0f
            val scrollTabs = available / fontScale < CompatPage.entries.size * (if (showIcon) 88f else 64f)
            Row(Modifier.weight(1f).then(if (scrollTabs) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                .focusRestorer().focusGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompatPage.entries.forEach { target ->
                    CompatNavItem(target, page == target, onClick = { onNavigate(target) },
                        modifier = (if (scrollTabs) Modifier.width(((if (showIcon) 88f else 64f) * fontScale).dp)
                            else Modifier.weight(1f)).focusRequester(focus[target.ordinal]),
                        showIcon = showIcon)
                }
            }
        }
    }
}

@Composable
private fun CompatNavItem(page: CompatPage, selected: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, showIcon: Boolean = true) {
    val shape = RoundedCornerShape(12.dp)
    Row(modifier.clip(shape).background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
        .remoteFocusHighlight(shape).selectable(selected, role = Role.Tab, onClick = onClick)
        .heightIn(min = 48.dp).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        val ink = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        if (showIcon) Icon(page.icon, null, tint = ink, modifier = Modifier.size(20.dp))
        Text(page.title, color = ink, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CompatBrand() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Forum, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(21.dp))
        }
        Column {
            Text("弹幕 API", style = MaterialTheme.typography.titleMedium)
            Text("兼容控制台", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CompatPageHeading(title: String, subtitle: String, state: CompatModeUiState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        CompatBadge("TCP ${state.runtimeState.port}", MaterialTheme.colorScheme.secondary)
    }
}

@Composable
internal fun CompatLocalNetworkPermissionDialog(openSettings: Boolean, onGrant: () -> Unit, onContinueLocalOnly: () -> Unit) {
    CompatDialog("允许局域网访问", onContinueLocalOnly,
        if (openSettings) "前往设置" else "允许访问", onGrant, cancelText = "仅本机使用") {
        Text("允许后，同一 Wi-Fi 或有线网络中的设备才能访问本机弹幕服务。")
        Text("暂不允许仍可在本机使用，连接页会保留授权入口。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
