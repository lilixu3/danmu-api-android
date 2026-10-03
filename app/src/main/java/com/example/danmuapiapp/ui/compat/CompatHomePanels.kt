package com.example.danmuapiapp.ui.compat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.*
import com.example.danmuapiapp.ui.component.remoteFocusHighlight

@Composable
internal fun CompatHomePage(
    state: CompatModeUiState, busy: Boolean, permissionMissing: Boolean,
    onGrant: () -> Unit, onStart: () -> Unit, onRestart: () -> Unit, onStop: () -> Unit,
    onCores: () -> Unit, onSettings: () -> Unit,
    listState: LazyListState, firstActionModifier: Modifier = Modifier, modifier: Modifier = Modifier
) {
    LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(LocalCompatLayout.current.gap.dp)) {
        item("service") { CompatHomeServiceCard(state, busy, onStart, onRestart, onStop, firstActionModifier) }
        if (state.isOperating || state.downloadProgress.inProgress) item("operation") { CompatOperationCard(state) }
        item("addresses") { CompatAddressSection(state, permissionMissing, onGrant) }
        item("shortcuts") { CompatOverviewShortcuts(state, onCores, onSettings) }
    }
}

@Composable
private fun CompatHomeServiceCard(
    state: CompatModeUiState,
    busy: Boolean,
    onStart: () -> Unit,
    onRestart: () -> Unit,
    onStop: () -> Unit,
    firstActionModifier: Modifier
) {
    val runtime = state.runtimeState
    val running = runtime.status == ServiceStatus.Running
    val installed = state.coreInfos.firstOrNull { it.variant == runtime.variant }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val usableWidth = (maxWidth.value - LocalCompatLayout.current.cardPadding * 2f) /
            LocalDensity.current.fontScale.coerceAtLeast(1f)
        val inlineControls = usableWidth >= 760f
        val controlButtons: @Composable () -> Unit = {
            CompatButton(if (running) "重启服务" else "启动服务", if (running) onRestart else onStart,
                modifier = firstActionModifier, icon = if (running) Icons.Rounded.RestartAlt else Icons.Rounded.PlayArrow,
                tone = CompatActionTone.Primary, enabled = !busy)
            if (running) CompatButton("停止服务", onStop, icon = Icons.Rounded.Stop,
                enabled = !busy, tone = CompatActionTone.Danger)
        }
        val controls: @Composable () -> Unit = {
            if (inlineControls) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { controlButtons() }
            else CompatActions { controlButtons() }
        }
        CompatCard {
            if (inlineControls) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        CompatHomeCardHeader("弹幕服务", "运行信息与服务控制", Icons.Rounded.PowerSettingsNew,
                            compatStatusLabel(runtime.status), compatStatusColor(runtime.status))
                    }
                    controls()
                }
            } else {
                CompatHomeCardHeader("弹幕服务", "运行信息与服务控制", Icons.Rounded.PowerSettingsNew,
                    compatStatusLabel(runtime.status), compatStatusColor(runtime.status))
            }
            if (usableWidth >= 600f) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CompatHomeMetric("当前核心", state.coreDisplayNames.resolve(runtime.variant), Icons.Rounded.Layers,
                        Modifier.weight(1f).fillMaxHeight())
                    CompatHomeMetric("核心版本", compatCoreVersion(installed, state.isCoreInfoLoading), Icons.Rounded.Code,
                        Modifier.weight(1f).fillMaxHeight())
                    CompatHomeMetric("运行方式", runtime.runMode.label, Icons.Rounded.Memory,
                        Modifier.weight(1f).fillMaxHeight())
                    CompatHomeMetric("运行时长", if (running) compatHomeUptime(runtime.uptimeSeconds) else "—",
                        Icons.Rounded.Schedule, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CompatHomeMetric("当前核心", state.coreDisplayNames.resolve(runtime.variant), Icons.Rounded.Layers,
                            Modifier.weight(1f).fillMaxHeight())
                        CompatHomeMetric("核心版本", compatCoreVersion(installed, state.isCoreInfoLoading), Icons.Rounded.Code,
                            Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CompatHomeMetric("运行方式", runtime.runMode.label, Icons.Rounded.Memory,
                            Modifier.weight(1f).fillMaxHeight())
                        CompatHomeMetric("运行时长", if (running) compatHomeUptime(runtime.uptimeSeconds) else "—",
                            Icons.Rounded.Schedule, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
            val detail = runtime.errorMessage?.takeIf { runtime.status == ServiceStatus.Error && it.isNotBlank() }
                ?: runtime.statusMessage?.takeIf { it.isNotBlank() && !running }
            if (detail != null) Text(detail,
                color = if (runtime.status == ServiceStatus.Error) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            if (!inlineControls) controls()
        }
    }
}

@Composable
internal fun CompatHomeCardHeader(
    title: String, subtitle: String, icon: ImageVector,
    badge: String? = null, badgeColor: Color = MaterialTheme.colorScheme.primary
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (badge != null) CompatBadge(badge, badgeColor)
    }
}

@Composable
private fun CompatHomeMetric(label: String, value: String, icon: ImageVector,
    modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
        .padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
    }
}

@Composable
internal fun CompatOverviewShortcuts(state: CompatModeUiState, onCores: () -> Unit, onSettings: () -> Unit) {
    val wideEnough = LocalCompatLayout.current.contentWidth / LocalDensity.current.fontScale.coerceAtLeast(1f) >= 560f
    if (wideEnough) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(LocalCompatLayout.current.gap.dp)) {
            CompatHomeShortcut("核心管理", "下载、更新与切换", Icons.Rounded.Layers, onCores,
                Modifier.weight(1f).fillMaxHeight())
            CompatHomeShortcut("后台运行", if (state.keepAlive.recommendedProfileEnabled) "恢复方案已配置" else "查看保活与权限",
                Icons.Rounded.Shield, onSettings, Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CompatHomeShortcut("核心管理", "下载、更新与切换", Icons.Rounded.Layers, onCores)
            CompatHomeShortcut("后台运行", if (state.keepAlive.recommendedProfileEnabled) "恢复方案已配置" else "查看保活与权限",
                Icons.Rounded.Shield, onSettings)
        }
    }
}

@Composable
private fun CompatHomeShortcut(title: String, subtitle: String, icon: ImageVector,
    onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Surface(modifier = modifier.fillMaxWidth().remoteFocusHighlight(shape), onClick = onClick,
        shape = shape, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun compatHomeUptime(seconds: Long): String {
    val duration = seconds.coerceAtLeast(0)
    return when {
        duration >= 3600 -> "${duration / 3600} 小时 ${(duration % 3600) / 60} 分"
        duration >= 60 -> "${duration / 60} 分 ${duration % 60} 秒"
        else -> "${duration} 秒"
    }
}
