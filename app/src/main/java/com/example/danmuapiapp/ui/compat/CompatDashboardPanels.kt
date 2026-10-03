package com.example.danmuapiapp.ui.compat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.*
import com.example.danmuapiapp.ui.component.remoteFocusHighlight
import java.util.Locale

@Composable
internal fun CompatSectionHeading(title: String, subtitle: String, action: String, onAction: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth.value / LocalDensity.current.fontScale.coerceAtLeast(1f) >= 380f) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CompatButton(action, onAction, icon = Icons.Rounded.Refresh)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                CompatButton(action, onAction, icon = Icons.Rounded.Refresh)
            }
        }
    }
}

@Composable
internal fun CompatCoreCard(state: CompatModeUiState, info: CoreInfo, actions: CompatModeActions,
    onEditCustom: () -> Unit, onDelete: () -> Unit, onReinstall: () -> Unit) {
    val active = state.runtimeState.variant == info.variant
    val pendingRepair = state.pendingDependencyRepair?.variant == info.variant
    val busy = state.isOperating || info.isUpdating || state.downloadProgress.inProgress ||
        state.runtimeState.status in setOf(ServiceStatus.Starting, ServiceStatus.Stopping)
    val label = state.coreDisplayNames.resolve(info.variant)
    val badge = when {
        pendingRepair -> "待修复依赖"
        info.sourceMismatch -> "来源已变更"
        info.hasVersionUpdate -> "有更新"
        active && info.isInstalled -> "当前使用"
        info.isInstalled -> "已安装"
        else -> "未安装"
    }
    CompatCard(emphasized = active) {
        CompatCardTitle(label, compatCoreVersion(info, state.isCoreInfoLoading), Icons.Rounded.Code)
        CompatBadge(badge, if (pendingRepair || info.sourceMismatch) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.primary)
        Text(resolveCoreVariantSourceText(info.variant, state.customRepo, state.customRepoBranch, state.coreBranchSelections),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        info.updateCheckError?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (state.downloadProgress.inProgress && state.downloadProgress.variant == info.variant) {
            CompatProgress(state.downloadProgress.stageText, state.downloadProgress.progress,
                compatByteProgress(state.downloadProgress))
        }
        CompatActions {
            CompatButton(when {
                pendingRepair -> "修复依赖"
                !info.isInstalled -> "下载核心"
                info.sourceMismatch -> "重新下载"
                info.hasVersionUpdate -> "更新核心"
                else -> "检查更新"
            }, onClick = {
                when {
                    pendingRepair -> actions.onOpenDependencyRepair()
                    !info.isInstalled -> actions.onInstallCore(info.variant)
                    info.needsAttention -> actions.onUpdateCore(info.variant)
                    else -> actions.onCheckCoreUpdate(info.variant)
                }
            }, icon = if (pendingRepair) Icons.Rounded.Build else Icons.Rounded.CloudDownload,
                tone = CompatActionTone.Primary, enabled = !busy)
            if (!active) CompatButton("切换使用", { actions.onSwitchVariant(info.variant) }, icon = Icons.Rounded.Sync,
                enabled = info.isReady && !busy && !pendingRepair)
            if (info.variant != ApiVariant.Custom || resolveCustomCoreSource(state.customRepo, state.customRepoBranch).isValidRepo) {
                CompatButton("选择分支", { actions.onOpenBranchPicker(info.variant) }, icon = Icons.Rounded.AccountTree,
                    enabled = !busy && !pendingRepair)
            }
            if (info.variant == ApiVariant.Custom) CompatButton("编辑来源", onEditCustom, icon = Icons.Rounded.Edit, enabled = !busy)
            if (info.isInstalled) {
                CompatButton("重装", onReinstall, icon = Icons.Rounded.Refresh, enabled = !busy && !pendingRepair)
                CompatButton("删除", onDelete, icon = Icons.Rounded.Delete,
                    enabled = !busy && !pendingRepair, tone = CompatActionTone.Danger)
            }
        }
        if (active && info.isInstalled) Text("删除当前核心时会先停止服务；配置文件保留，可重新下载安装。",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun CompatKeepAliveCard(state: CompatModeUiState, actions: CompatModeActions) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val keepAlive = state.keepAlive
    CompatCard {
        CompatCardTitle("后台运行", keepAlive.summary, Icons.Rounded.Shield)
        CompatBadge(if (keepAlive.recommendedProfileEnabled) "恢复方案已配置" else "按需开启",
            if (keepAlive.recommendedProfileEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text("系统可能限制待机网络或后台启动。保持界面在前台、允许后台运行，有助于减少中断。",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CompatActions {
            CompatButton(keepAlive.actionLabel, actions.onToggleKeepAliveProfile, icon = Icons.Rounded.PowerSettingsNew,
                enabled = keepAlive.actionEnabled, tone = CompatActionTone.Primary)
            if (!keepAlive.isRootMode && !keepAlive.hasNotificationPermission) {
                CompatButton("通知权限", actions.onOpenNotificationPermission, icon = Icons.Rounded.Notifications)
            }
            if (!keepAlive.isRootMode && !keepAlive.batteryOptimizationIgnored) {
                CompatButton("后台权限", actions.onOpenBatterySettings, icon = Icons.Rounded.BatteryChargingFull)
            }
            CompatButton(if (expanded) "收起说明" else "查看详情", { expanded = !expanded }, icon = Icons.Rounded.Info)
        }
        if (expanded) Text(keepAlive.detail, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun CompatIpv6Card(state: CompatModeUiState, busy: Boolean, onSetEnabled: (Boolean) -> Unit) {
    val runtime = state.runtimeState
    val enabled = runtime.listenMode == RuntimeListenMode.DualStack
    val shape = RoundedCornerShape(16.dp)
    CompatCard {
        CompatCardTitle("IPv6 访问", "允许播放器通过 IPv6 连接本机服务", Icons.Rounded.Lan)
        Row(Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .remoteFocusHighlight(shape, !busy)
            .toggleable(value = enabled, enabled = !busy, role = Role.Switch, onValueChange = onSetEnabled)
            .heightIn(min = 64.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("开启 IPv6", style = MaterialTheme.typography.titleMedium)
                Text(if (enabled) "同时监听 IPv4 与 IPv6" else "仅监听 IPv4",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // The entire row owns one focus target and one toggle action for the remote.
            Switch(checked = enabled, onCheckedChange = null, enabled = !busy)
        }
        Text("切换后自动保存；服务运行时会重启，访问将短暂中断。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("公网 IPv6 可能允许外网访问，请保留访问 Token 与管理员认证。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        if (enabled && runtime.lanIpv6Url.isBlank()) Text("当前网络尚未提供可用 IPv6 地址，仍可通过 IPv4 访问。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun CompatNetworkCard(proxy: CompatProxyPickerState, onOpen: () -> Unit) {
    CompatCard {
        CompatCardTitle("下载线路", "用于 GitHub 检查更新与资源下载", Icons.Rounded.Public)
        Text(proxy.currentLabel, style = MaterialTheme.typography.titleMedium)
        CompatButton("测速并选择线路", onOpen, icon = Icons.Rounded.Speed)
    }
}

@Composable
internal fun CompatAppearanceCard(state: CompatModeUiState, actions: CompatModeActions,
    onScale: () -> Unit) {
    CompatCard {
        CompatCardTitle("界面与显示", "缩放仅作用于兼容界面", Icons.Rounded.Tv)
        CompatActions {
            CompatBadge(when (state.nightMode) {
                NightModePreference.FollowSystem -> "外观跟随系统"
                NightModePreference.Dark -> "深色外观"
                NightModePreference.Light -> "浅色外观"
            })
            CompatBadge(if (state.appDpiOverride > 0) "兼容 DPI ${state.appDpiOverride}" else "兼容缩放跟随系统")
        }
        CompatActions {
            CompatButton("切换深浅色", actions.onToggleNightMode, icon = Icons.Rounded.Contrast)
            CompatButton("调整兼容缩放", onScale, icon = Icons.Rounded.FormatSize)
        }
        Text("普通界面的 DPI 单独保存，两种界面可以分别恢复跟随系统。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun CompatAppUpdateCard(update: CompatAppUpdateUiState, actions: CompatModeActions) {
    var showNotes by rememberSaveable { mutableStateOf(false) }
    val result = update.checkResult
    val canDownload = result?.hasUpdate == true && result.downloadUrls.isNotEmpty()
    CompatCard {
        CompatCardTitle("应用更新", "当前版本 ${update.currentVersion}", Icons.Rounded.SystemUpdate)
        CompatBadge(when {
            update.isChecking -> "正在检查"
            update.checkError.isNotBlank() -> "检查失败"
            result?.hasUpdate == true -> "新版本 ${result.latestVersion}"
            result != null -> "当前为最新版本"
            else -> "尚未检查"
        }, if (update.checkError.isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        if (update.checkError.isNotBlank()) Text(update.checkError, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall)
        if (update.isDownloading) CompatProgress(update.downloadDetail,
            update.downloadPercent.takeIf { it in 0..100 }?.div(100f), "")
        CompatActions {
            CompatButton("检查更新", actions.onCheckAppUpdate, icon = Icons.Rounded.Refresh,
                enabled = !update.isChecking && !update.isDownloading)
            if (update.downloadedApk != null) {
                CompatButton("安装更新", actions.onInstallAppUpdate, icon = Icons.Rounded.InstallMobile,
                    tone = CompatActionTone.Primary, enabled = !update.isDownloading)
            } else if (result?.hasUpdate == true) {
                CompatButton("下载更新", actions.onDownloadAppUpdate, icon = Icons.Rounded.Download,
                    tone = CompatActionTone.Primary, enabled = canDownload && !update.isDownloading)
            }
            if (!result?.releaseNotes.isNullOrBlank()) CompatButton(if (showNotes) "收起更新内容" else "查看更新内容",
                { showNotes = !showNotes }, icon = Icons.Rounded.Description)
        }
        if (result?.hasUpdate == true && !canDownload && update.downloadedApk == null) {
            Text("当前版本尚未提供适合此设备的安装包，可以稍后重新检查。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showNotes) Text(result?.releaseNotes.orEmpty(), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun CompatOperationCard(state: CompatModeUiState) {
    CompatCard {
        CompatCardTitle(state.operationProgressTitle.ifBlank { "正在处理" }, icon = Icons.Rounded.HourglassTop)
        CompatProgress(state.downloadProgress.stageText, state.downloadProgress.progress, compatByteProgress(state.downloadProgress))
    }
}

@Composable
internal fun CompatProgress(text: String, progress: Float?, detail: String) {
    if (progress != null) LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.bodySmall)
    if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun CompatEmptyCard(title: String, message: String, action: String, onAction: () -> Unit) {
    CompatCard { CompatCardTitle(title, message, Icons.Rounded.Info); CompatButton(action, onAction) }
}

internal fun compatStatusLabel(status: ServiceStatus): String = when (status) {
    ServiceStatus.Stopped -> "已停止"
    ServiceStatus.Starting -> "启动中"
    ServiceStatus.Running -> "运行中"
    ServiceStatus.Stopping -> "停止中"
    ServiceStatus.Error -> "异常"
}

@Composable
internal fun compatStatusColor(status: ServiceStatus): Color = when (status) {
    ServiceStatus.Running -> MaterialTheme.colorScheme.secondary
    ServiceStatus.Error -> MaterialTheme.colorScheme.error
    ServiceStatus.Starting, ServiceStatus.Stopping -> MaterialTheme.colorScheme.tertiary
    ServiceStatus.Stopped -> MaterialTheme.colorScheme.onSurfaceVariant
}

internal fun compatCoreVersion(info: CoreInfo?, loading: Boolean): String = when {
    info == null -> if (loading) "读取中" else "未知版本"
    !info.isInstalled -> "未安装"
    info.version.isNullOrBlank() -> "未知版本"
    else -> formatCoreVersionTransition(info.version, if (info.hasVersionUpdate) info.availableVersion else null)
}

private fun compatByteProgress(progress: CoreDownloadProgress): String {
    fun bytes(value: Long): String = when {
        value >= 1024 * 1024 -> String.format(Locale.ROOT, "%.1f MB", value / (1024.0 * 1024))
        value >= 1024 -> String.format(Locale.ROOT, "%.1f KB", value / 1024.0)
        else -> "${value.coerceAtLeast(0)} B"
    }
    if (progress.downloadedBytes <= 0 && progress.totalBytes <= 0) return ""
    return bytes(progress.downloadedBytes) + if (progress.totalBytes > 0) " / ${bytes(progress.totalBytes)}" else ""
}
