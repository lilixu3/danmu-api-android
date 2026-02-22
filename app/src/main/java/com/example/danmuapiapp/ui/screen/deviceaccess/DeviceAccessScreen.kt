package com.example.danmuapiapp.ui.screen.deviceaccess

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.domain.model.DeviceAccessDevice
import com.example.danmuapiapp.domain.model.DeviceAccessMode
import com.example.danmuapiapp.domain.model.DeviceAccessSnapshot
import java.util.concurrent.TimeUnit

private enum class DeviceFilter {
    All,
    InRule,
    OutRule
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceAccessScreen(
    onBack: () -> Unit,
    onOpenAdminMode: () -> Unit,
    viewModel: DeviceAccessViewModel = hiltViewModel()
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val adminState by viewModel.adminSessionState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(DeviceFilter.All) }

    LaunchedEffect(viewModel.operationMessage) {
        viewModel.operationMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    LaunchedEffect(snapshot.config.mode) {
        filter = DeviceFilter.All
        if (snapshot.config.mode == DeviceAccessMode.Off) {
            showAdvanced = false
        }
    }

    val mode = snapshot.config.mode
    val adminEnabled = adminState.isAdminMode
    val devices = remember(snapshot.devices, mode, filter) {
        if (mode == DeviceAccessMode.Off) {
            snapshot.devices
                .sortedWith(
                    compareByDescending<DeviceAccessDevice> { it.totalRequests }
                        .thenByDescending { it.lastSeenAtMs }
                        .thenBy { it.ip }
                )
        } else {
            val sorted = snapshot.devices
                .sortedWith(
                    compareByDescending<DeviceAccessDevice> { ruleMembership(it, mode) }
                        .thenByDescending { it.totalRequests }
                        .thenByDescending { it.lastSeenAtMs }
                        .thenBy { it.ip }
                )
            when (filter) {
                DeviceFilter.All -> sorted
                DeviceFilter.InRule -> sorted.filter { ruleMembership(it, mode) }
                DeviceFilter.OutRule -> sorted.filter { !ruleMembership(it, mode) }
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HeaderBar(
                onBack = onBack,
                onRefresh = viewModel::refresh,
                refreshing = viewModel.isRefreshing,
                busy = viewModel.isSaving
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    ModeOverviewCard(
                        snapshot = snapshot,
                        busy = viewModel.isSaving,
                        editable = adminEnabled,
                        onSelectMode = viewModel::applyMode
                    )
                }

                if (!adminEnabled) {
                    item {
                        AdminModeHintCard(onOpenAdminMode = onOpenAdminMode)
                    }
                }

                if (mode == DeviceAccessMode.Off) {
                    item {
                        OffModeHintCard()
                    }
                } else {
                    item {
                        ActiveModeToolbar(
                            mode = mode,
                            showAdvanced = showAdvanced,
                            onToggleAdvanced = { showAdvanced = !showAdvanced },
                            onAddIp = { showAddDialog = true },
                            editable = adminEnabled,
                            busy = viewModel.isSaving
                        )
                    }
                    if (showAdvanced) {
                        item {
                            AdvancedPanel(
                                mode = mode,
                                filter = filter,
                                onFilterChange = { filter = it },
                                onClearRules = viewModel::clearRules,
                                onClearStats = viewModel::clearDeviceStats,
                                editable = adminEnabled
                            )
                        }
                    }
                }

                if (devices.isEmpty()) {
                    item {
                        EmptyCard(mode = mode)
                    }
                } else {
                    itemsIndexed(devices, key = { _, device -> device.ip }) { index, device ->
                        if (mode == DeviceAccessMode.Off) {
                            RankingCard(
                                rank = index + 1,
                                device = device
                            )
                        } else {
                            RuleCard(
                                rank = index + 1,
                                mode = mode,
                                device = device,
                                editable = adminEnabled,
                                onToggleRule = {
                                    when (mode) {
                                        DeviceAccessMode.Blacklist -> {
                                            viewModel.toggleRule(device.ip, DeviceRuleList.Blacklist)
                                        }

                                        DeviceAccessMode.Whitelist -> {
                                            viewModel.toggleRule(device.ip, DeviceRuleList.Whitelist)
                                        }

                                        DeviceAccessMode.Off -> Unit
                                    }
                                }
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }

    if (showAddDialog && adminEnabled) {
        AddIpDialog(
            mode = mode,
            busy = viewModel.isSaving,
            onDismiss = { showAddDialog = false },
            onSubmit = { ip ->
                when (mode) {
                    DeviceAccessMode.Blacklist -> {
                        viewModel.addManualIp(ip, DeviceRuleList.Blacklist)
                    }

                    DeviceAccessMode.Whitelist -> {
                        viewModel.addManualIp(ip, DeviceRuleList.Whitelist)
                    }

                    DeviceAccessMode.Off -> Unit
                }
                showAddDialog = false
            }
        )
    }

    if (viewModel.errorMessage != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("操作失败") },
            text = { Text(viewModel.errorMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) { Text("知道了") }
            }
        )
    }
}

@Composable
private fun HeaderBar(
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    refreshing: Boolean,
    busy: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalIconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", Modifier.size(18.dp))
            }
            Column {
                Text("设备控制", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "访问控制模式与设备排行",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        FilledTonalIconButton(
            onClick = onRefresh,
            enabled = !refreshing && !busy,
            modifier = Modifier.size(36.dp)
        ) {
            if (refreshing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Rounded.Refresh, "刷新", Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeOverviewCard(
    snapshot: DeviceAccessSnapshot,
    busy: Boolean,
    editable: Boolean,
    onSelectMode: (DeviceAccessMode) -> Unit
) {
    val mode = snapshot.config.mode
    val modeText = when (mode) {
        DeviceAccessMode.Off -> "当前状态：关闭（全部设备可访问）"
        DeviceAccessMode.Blacklist -> "当前状态：黑名单（名单内拦截）"
        DeviceAccessMode.Whitelist -> "当前状态：白名单（仅名单内可访问）"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.primary)
                    Text(modeText, style = MaterialTheme.typography.titleSmall)
                }
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeSwitchButton(
                    title = "关闭",
                    selected = mode == DeviceAccessMode.Off,
                    enabled = editable,
                    onClick = { onSelectMode(DeviceAccessMode.Off) },
                    modifier = Modifier.weight(1f)
                )
                ModeSwitchButton(
                    title = "黑名单",
                    selected = mode == DeviceAccessMode.Blacklist,
                    enabled = editable,
                    onClick = { onSelectMode(DeviceAccessMode.Blacklist) },
                    modifier = Modifier.weight(1f)
                )
                ModeSwitchButton(
                    title = "白名单",
                    selected = mode == DeviceAccessMode.Whitelist,
                    enabled = editable,
                    onClick = { onSelectMode(DeviceAccessMode.Whitelist) },
                    modifier = Modifier.weight(1f)
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text("设备 ${snapshot.trackedDevices}") },
                    leadingIcon = { Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = {},
                    label = { Text("白名单 ${snapshot.whitelistCount}") },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAddCheck, null, Modifier.size(16.dp))
                    }
                )
                AssistChip(
                    onClick = {},
                    label = { Text("黑名单 ${snapshot.blacklistCount}") },
                    leadingIcon = { Icon(Icons.Rounded.Block, null, Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = {},
                    label = { Text("累计拦截 ${snapshot.totalBlockedRequests}") },
                    leadingIcon = { Icon(Icons.Rounded.WarningAmber, null, Modifier.size(16.dp)) }
                )
            }
        }
    }
}

@Composable
private fun AdminModeHintCard(
    onOpenAdminMode: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "当前为普通模式",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "设备真实 IP 可能被隐藏，规则修改操作已禁用。请先进入管理员模式。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(
                onClick = onOpenAdminMode,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("进入管理员模式")
            }
        }
    }
}

@Composable
private fun ModeSwitchButton(
    title: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier
            .height(44.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = container,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) textColor else textColor.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun OffModeHintCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("访问控制已关闭", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "当前不应用黑白名单规则，下方仅展示访问设备排行。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActiveModeToolbar(
    mode: DeviceAccessMode,
    showAdvanced: Boolean,
    onToggleAdvanced: () -> Unit,
    onAddIp: () -> Unit,
    editable: Boolean,
    busy: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (mode == DeviceAccessMode.Blacklist) "黑名单设备列表" else "白名单设备列表",
                style = MaterialTheme.typography.titleSmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onAddIp,
                    enabled = !busy && editable,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("添加IP")
                }
                FilledTonalIconButton(
                    onClick = onToggleAdvanced,
                    enabled = editable,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (showAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        "高级选项",
                        Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedPanel(
    mode: DeviceAccessMode,
    filter: DeviceFilter,
    onFilterChange: (DeviceFilter) -> Unit,
    onClearRules: () -> Unit,
    onClearStats: () -> Unit,
    editable: Boolean
) {
    val inRuleLabel = if (mode == DeviceAccessMode.Blacklist) "名单内(拦截)" else "名单内(放行)"
    val outRuleLabel = if (mode == DeviceAccessMode.Blacklist) "名单外(放行)" else "名单外(拦截)"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "高级选项",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filter == DeviceFilter.All,
                    enabled = editable,
                    onClick = { onFilterChange(DeviceFilter.All) },
                    label = { Text("全部") }
                )
                FilterChip(
                    selected = filter == DeviceFilter.InRule,
                    enabled = editable,
                    onClick = { onFilterChange(DeviceFilter.InRule) },
                    label = { Text(inRuleLabel) }
                )
                FilterChip(
                    selected = filter == DeviceFilter.OutRule,
                    enabled = editable,
                    onClick = { onFilterChange(DeviceFilter.OutRule) },
                    label = { Text(outRuleLabel) }
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = onClearRules,
                    enabled = editable,
                    label = { Text("清空名单") },
                    leadingIcon = { Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = onClearStats,
                    enabled = editable,
                    label = { Text("清空统计") },
                    leadingIcon = { Icon(Icons.Rounded.WarningAmber, null, Modifier.size(16.dp)) }
                )
            }
        }
    }
}

@Composable
private fun EmptyCard(mode: DeviceAccessMode) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 36.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (mode == DeviceAccessMode.Off) "暂无访问设备" else "暂无设备记录",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (mode == DeviceAccessMode.Off) {
                    "让客户端访问一次接口后刷新即可看到排行。"
                } else {
                    "可先添加 IP，或让客户端访问一次接口后刷新。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RankingCard(
    rank: Int,
    device: DeviceAccessDevice
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {}
                Text("#$rank", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    device.ip,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "请求 ${device.totalRequests} · 最近 ${formatAgo(device.lastSeenAtMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RuleCard(
    rank: Int,
    mode: DeviceAccessMode,
    device: DeviceAccessDevice,
    editable: Boolean,
    onToggleRule: () -> Unit
) {
    val inRule = ruleMembership(device, mode)
    val statusText = if (device.effectiveBlocked) "已拦截" else "可访问"
    val actionText = if (mode == DeviceAccessMode.Blacklist) {
        if (inRule) "移出黑名单" else "加入黑名单"
    } else {
        if (inRule) "移出白名单" else "加入白名单"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "#$rank",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        device.ip,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(statusText) },
                    leadingIcon = {
                        Icon(
                            if (device.effectiveBlocked) Icons.Rounded.Block else Icons.Rounded.CheckCircle,
                            null,
                            Modifier.size(16.dp)
                        )
                    }
                )
            }

            Text(
                "请求 ${device.totalRequests} · 最近 ${formatAgo(device.lastSeenAtMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FilledTonalButton(
                onClick = onToggleRule,
                enabled = editable,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    if (mode == DeviceAccessMode.Blacklist) Icons.Rounded.Block
                    else Icons.AutoMirrored.Rounded.PlaylistAddCheck,
                    null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(actionText)
            }
        }
    }
}

@Composable
private fun AddIpDialog(
    mode: DeviceAccessMode,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var ipText by remember { mutableStateOf("") }
    val targetLabel = if (mode == DeviceAccessMode.Blacklist) "添加到黑名单" else "添加到白名单"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加设备 IP") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = ipText,
                    onValueChange = { ipText = it },
                    label = { Text("IPv4 / IPv6") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    targetLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(ipText) },
                enabled = ipText.isNotBlank() && !busy
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun ruleMembership(device: DeviceAccessDevice, mode: DeviceAccessMode): Boolean {
    return when (mode) {
        DeviceAccessMode.Blacklist -> device.inBlacklist
        DeviceAccessMode.Whitelist -> device.inWhitelist
        DeviceAccessMode.Off -> false
    }
}

private fun formatAgo(timestampMs: Long): String {
    if (timestampMs <= 0L) return "未访问"
    val diff = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    if (minutes < 1) return "刚刚"
    if (minutes < 60) return "${minutes} 分钟前"
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    if (hours < 24) return "${hours} 小时前"
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return "${days} 天前"
}
