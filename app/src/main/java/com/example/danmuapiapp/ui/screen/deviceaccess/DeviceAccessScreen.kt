package com.example.danmuapiapp.ui.screen.deviceaccess

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.domain.model.DeviceAccessDevice
import com.example.danmuapiapp.domain.model.DeviceAccessMode
import com.example.danmuapiapp.domain.model.DeviceAccessSnapshot
import com.example.danmuapiapp.domain.model.DeviceAccessSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceAccessScreen(
    onBack: () -> Unit,
    onOpenAdminMode: () -> Unit,
    viewModel: DeviceAccessViewModel = hiltViewModel()
) {
    val snapshot = viewModel.snapshot.collectAsStateWithLifecycle().value
    val adminState = viewModel.adminSessionState.collectAsStateWithLifecycle().value
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.operationMessage) {
        viewModel.operationMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    val adminEnabled = adminState.isAdminMode
    val blacklistEnabled = snapshot.config.mode == DeviceAccessMode.Blacklist

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp)
        ) {
            HeaderBar(
                onBack = onBack,
                onRefresh = viewModel::refresh,
                refreshing = viewModel.isRefreshing,
                busy = viewModel.isSaving
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    GuideCard(
                        adminEnabled = adminEnabled,
                        onOpenAdminMode = onOpenAdminMode
                    )
                }

                item {
                    BlacklistSwitchCard(
                        snapshot = snapshot,
                        adminEnabled = adminEnabled,
                        busy = viewModel.isSaving,
                        onCheckedChange = viewModel::setBlacklistEnabled
                    )
                }

                item {
                    ActionCard(
                        showLanNeighbors = viewModel.showLanNeighbors,
                        lanScannedCount = snapshot.lanScannedCount,
                        lastLanScanAtMs = snapshot.lastLanScanAtMs,
                        refreshing = viewModel.isRefreshing,
                        busy = viewModel.isSaving,
                        onScanLan = viewModel::scanLanDevices,
                        onHideLan = viewModel::hideLanDevices
                    )
                }

                if (!blacklistEnabled) {
                    item {
                        DisabledHintCard()
                    }
                }

                if (snapshot.devices.isEmpty()) {
                    item {
                        EmptyStateCard(showLanNeighbors = viewModel.showLanNeighbors)
                    }
                } else {
                    itemsIndexed(snapshot.devices, key = { _, item -> item.ip }) { index, device ->
                        DeviceCard(
                            rank = index + 1,
                            blacklistEnabled = blacklistEnabled,
                            device = device,
                            adminEnabled = adminEnabled,
                            busy = viewModel.isSaving,
                            onToggleBlock = { viewModel.toggleBlock(device.ip) }
                        )
                    }
                }

                if (adminEnabled) {
                    item {
                        AdminActionsCard(
                            busy = viewModel.isSaving,
                            onClearBlacklist = viewModel::clearBlacklist,
                            onClearStats = viewModel::clearDeviceStats
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
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
                    "访问设备拉黑与局域网检测",
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

@Composable
private fun GuideCard(
    adminEnabled: Boolean,
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
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("新手指引", style = MaterialTheme.typography.titleSmall)
            Text(
                "1. 开启黑名单防护\n2. 在设备列表点击“拉黑设备”",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!adminEnabled) {
                Spacer(modifier = Modifier.height(2.dp))
                FilledTonalButton(
                    onClick = onOpenAdminMode,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Rounded.AdminPanelSettings, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("进入管理员模式")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlacklistSwitchCard(
    snapshot: DeviceAccessSnapshot,
    adminEnabled: Boolean,
    busy: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val enabled = snapshot.config.mode == DeviceAccessMode.Blacklist
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
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
                    Column {
                        Text("黑名单防护", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (enabled) "已开启：黑名单 IP 会被拦截"
                            else "已关闭：当前仅统计设备访问",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = enabled,
                    enabled = adminEnabled && !busy,
                    onCheckedChange = onCheckedChange
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(onClick = {}, label = { Text("访问设备 ${snapshot.trackedDevices}") })
                AssistChip(onClick = {}, label = { Text("黑名单 ${snapshot.blacklistCount}") })
                AssistChip(onClick = {}, label = { Text("累计拦截 ${snapshot.totalBlockedRequests}") })
            }
        }
    }
}

@Composable
private fun ActionCard(
    showLanNeighbors: Boolean,
    lanScannedCount: Int,
    lastLanScanAtMs: Long,
    refreshing: Boolean,
    busy: Boolean,
    onScanLan: () -> Unit,
    onHideLan: () -> Unit
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onScanLan,
                    enabled = !refreshing && !busy,
                    colors = primaryActionButtonColors(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("检测局域网设备")
                }
                OutlinedButton(
                    onClick = onHideLan,
                    enabled = showLanNeighbors && !refreshing && !busy,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (showLanNeighbors) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                        contentColor = if (showLanNeighbors) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (showLanNeighbors) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.64f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        }
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("仅看访问设备")
                }
            }

            val scanText = if (showLanNeighbors) {
                "当前展示访问设备 + 局域网检测结果（$lanScannedCount 台）"
            } else {
                "当前仅展示访问过 API 的设备"
            }
            Text(
                scanText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (lastLanScanAtMs > 0L) {
                Text(
                    "上次局域网检测：${formatTime(lastLanScanAtMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DisabledHintCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "黑名单防护已关闭，设备不会被拦截。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyStateCard(showLanNeighbors: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("暂时没有设备记录", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (showLanNeighbors) {
                    "可稍后再次检测局域网，或让设备访问一次 API 后再刷新。"
                } else {
                    "让设备访问一次 API，或点击“检测局域网设备”。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceCard(
    rank: Int,
    blacklistEnabled: Boolean,
    device: DeviceAccessDevice,
    adminEnabled: Boolean,
    busy: Boolean,
    onToggleBlock: () -> Unit
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier.size(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {}
                        Text(
                            text = rank.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        device.ip,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            if (device.effectiveBlocked) "已拦截" else "可访问"
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (device.effectiveBlocked) Icons.Rounded.Block else Icons.Rounded.CheckCircle,
                            null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(sourceText(device.source)) }
                )
                if (device.inBlacklist) {
                    AssistChip(
                        onClick = {},
                        label = { Text("黑名单") },
                        leadingIcon = { Icon(Icons.Rounded.Block, null, Modifier.size(16.dp)) }
                    )
                }
            }

            Text(
                "请求 ${device.totalRequests} · 最近 ${formatAgo(device.lastSeenAtMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!blacklistEnabled && device.inBlacklist) {
                Text(
                    "提示：当前黑名单防护关闭，该规则暂不生效。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val actionLabel = if (device.inBlacklist) "解除拉黑" else "拉黑设备"
            if (adminEnabled) {
                Button(
                    onClick = onToggleBlock,
                    enabled = !busy,
                    colors = primaryActionButtonColors(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Rounded.Block, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(actionLabel)
                }
            } else {
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("需管理员模式才能修改")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminActionsCard(
    busy: Boolean,
    onClearBlacklist: () -> Unit,
    onClearStats: () -> Unit
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
                "管理员操作",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = onClearBlacklist,
                    enabled = !busy,
                    label = { Text("清空黑名单") },
                    leadingIcon = { Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = onClearStats,
                    enabled = !busy,
                    label = { Text("清空统计") },
                    leadingIcon = { Icon(Icons.Rounded.WarningAmber, null, Modifier.size(16.dp)) }
                )
            }
        }
    }
}

@Composable
private fun primaryActionButtonColors() = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.primary,
    contentColor = MaterialTheme.colorScheme.onPrimary,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
)

private fun sourceText(source: DeviceAccessSource): String {
    return when (source) {
        DeviceAccessSource.AccessRecord -> "访问记录"
        DeviceAccessSource.LanScan -> "局域网检测"
        DeviceAccessSource.BlacklistRule -> "黑名单规则"
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

private fun formatTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return "-"
    val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
    return fmt.format(Date(timestampMs))
}
