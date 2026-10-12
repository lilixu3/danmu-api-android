package com.example.danmuapiapp.ui.screen.core

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMerge
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SwapVerticalCircle
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.*
import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.AppDialogStyle
import com.example.danmuapiapp.ui.component.AppDialogTone
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassDangerButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassPrimaryButton
import com.example.danmuapiapp.ui.theme.glassBorderColor
import com.example.danmuapiapp.ui.theme.glassSurfaceColor
import java.util.Locale

@Composable
internal fun CoreWorkspaceHeader(refreshing: Boolean, enabled: Boolean, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("核心", modifier = Modifier.weight(1f), fontSize = 26.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground)
        IconButton(onClick = onRefresh, enabled = enabled && !refreshing) {
            if (refreshing) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
            else Icon(Icons.Outlined.Refresh, "刷新核心信息", Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun VariantRail(
    selected: ApiVariant,
    active: ApiVariant,
    displayNames: CoreVariantDisplayNames,
    enabled: Boolean,
    onSelect: (ApiVariant) -> Unit
) {
    AppGlassSurface(shape = RoundedCornerShape(16.dp), color = glassSurfaceColor(),
        border = BorderStroke(1.dp, glassBorderColor())) {
        Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ApiVariant.entries.forEach { variant ->
                val selectedTab = variant == selected
                Surface(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = enabled, role = Role.Tab) { onSelect(variant) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedTab) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                ) {
                    Row(Modifier.heightIn(min = 34.dp).padding(horizontal = 7.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text(displayNames.resolve(variant), modifier = Modifier.weight(1f, fill = false),
                            fontSize = 13.sp, fontWeight = if (selectedTab) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selectedTab) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (active == variant) {
                            Spacer(Modifier.width(5.dp))
                            Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CoreControlPanel(
    info: CoreInfo,
    localInfoLoading: Boolean,
    hasLocalInfo: Boolean,
    runtimeVariant: ApiVariant,
    viewModel: CoreViewModel,
    pendingRepair: CoreDependencyRepairRequest?,
    customRepo: String,
    customBranch: String,
    branchSelections: CoreBranchSelections,
    displayNames: CoreVariantDisplayNames,
    routeLabel: String,
    onOpenPullRequestLab: (ApiVariant) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val repo = resolveCoreVariantRepo(info.variant, customRepo)
    val requestedBranch = resolveCoreVariantBranch(info.variant, customRepo, customBranch, branchSelections)
    val cachedRemote = viewModel.workspaceEntryFor(info.variant)
    val snapshot = cachedRemote?.second
    val remote = if ((info.updateCheckedAtEpochMillis ?: 0L) > (cachedRemote?.first ?: 0L)) {
        info.remoteCommit ?: snapshot?.commit
    } else snapshot?.commit ?: info.remoteCommit
    val current = info.variant == runtimeVariant
    val repairing = pendingRepair?.variant == info.variant && pendingRepair.missingDependencies.isNotEmpty()
    val busy = viewModel.isOperating || viewModel.isCheckingUpdate
    val coreActionsEnabled = !busy && !localInfoLoading
    val needsSource = repo.isBlank()
    val label = displayNames.resolve(info.variant)
    var menuExpanded by remember(info.variant) { mutableStateOf(false) }
    var deleteConfirm by remember(info.variant) { mutableStateOf(false) }
    val badgeColor = when {
        repairing || info.sourceMismatch -> colors.error
        current && info.isInstalled -> colors.primary
        else -> colors.onSurfaceVariant
    }
    val badgeText = when {
        !hasLocalInfo -> "—"
        repairing -> "待修复"
        info.sourceMismatch -> "来源变更"
        current && info.isInstalled -> "使用中"
        info.isInstalled -> "已安装"
        else -> "未安装"
    }

    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        color = glassSurfaceColor(),
        border = BorderStroke(if (current && info.isInstalled) 1.2.dp else 1.dp,
            if (current && info.isInstalled) colors.primary.copy(alpha = 0.5f) else glassBorderColor())
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, modifier = Modifier.weight(1f, fill = false), fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = coreActionsEnabled, role = Role.Button) { viewModel.openVariantSettingsDialog(info.variant) },
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Edit, "编辑核心名称", Modifier.size(14.dp), tint = colors.onSurfaceVariant)
                    }
                    if (hasLocalInfo) {
                        Surface(shape = RoundedCornerShape(6.dp), color = badgeColor.copy(alpha = 0.12f)) {
                            Text(badgeText, Modifier.padding(horizontal = 7.dp, vertical = 3.dp), fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold, color = badgeColor, maxLines = 1)
                        }
                    }
                }
                if (info.isInstalled && !info.version.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(formatCoreVersionValue(info.version), Modifier.widthIn(max = 90.dp), fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, color = colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (info.isInstalled) {
                    Box {
                        IconButton(onClick = { menuExpanded = true }, enabled = coreActionsEnabled, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Outlined.MoreVert, "更多核心操作", Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text("重新安装核心") }, onClick = {
                                menuExpanded = false; viewModel.reinstallCore(info.variant)
                            }, leadingIcon = { Icon(Icons.Outlined.Sync, null) })
                            DropdownMenuItem(text = { Text("删除核心") }, onClick = {
                                menuExpanded = false; deleteConfirm = true
                            }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = colors.error) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = info.variant == ApiVariant.Custom && coreActionsEnabled, role = Role.Button) {
                        viewModel.openVariantSettingsDialog(info.variant)
                    }, verticalAlignment = Alignment.CenterVertically) {
                    Text(repo.ifBlank { "未配置仓库，点此填写" }, Modifier.weight(1f, fill = false), fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace, color = if (needsSource) colors.primary else colors.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (info.variant == ApiVariant.Custom) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Outlined.Edit, null, Modifier.size(12.dp), tint = colors.onSurfaceVariant)
                    }
                }
                if (!needsSource) {
                    Surface(shape = RoundedCornerShape(8.dp), color = colors.primary.copy(alpha = 0.12f)) {
                        Row(Modifier.clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = info.isInstalled && coreActionsEnabled && !repairing, role = Role.Button) {
                                viewModel.openBranchDialog(info.variant)
                            }.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Outlined.AccountTree, null, Modifier.size(12.dp), tint = colors.primary)
                            Text(requestedBranch ?: snapshot?.branch ?: info.remoteBranch ?: "默认分支",
                                Modifier.widthIn(max = 92.dp), fontSize = 11.5.sp, fontFamily = FontFamily.Monospace,
                                color = colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.Outlined.UnfoldMore, "选择分支", Modifier.size(12.dp), tint = colors.primary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CoreCommitTile("远端最新", when {
                    needsSource -> "—"
                    remote != null -> remote.shortSha
                    viewModel.isRefreshingWorkspace -> "—"
                    viewModel.workspaceRefreshError != null -> "读取失败"
                    else -> "暂无"
                }, when {
                    needsSource -> "未配置仓库"
                    viewModel.workspaceRefreshError != null -> viewModel.workspaceRefreshError.orEmpty()
                    remote != null -> remote.title
                    else -> ""
                }, Modifier.weight(1f), viewModel.workspaceRefreshError != null)
                CoreCommitTile("已安装", when {
                    !hasLocalInfo -> "—"
                    !info.isInstalled -> "未安装"
                    info.sourceCommitSha.isNotBlank() -> info.sourceCommitSha.take(7)
                    else -> "本地版本"
                }, if (hasLocalInfo) coreInstalledSubtitle(info) else "", Modifier.weight(1f))
            }
            if (info.pullRequestNumbers.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(10.dp), color = colors.primary.copy(alpha = 0.1f)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.CallMerge, null, Modifier.size(13.dp), tint = colors.primary)
                        Text("已并入 PR：${info.pullRequestNumbers.joinToString(" ") { "#$it" }}", fontSize = 11.5.sp,
                            color = colors.primary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            val actionLabel = when {
                !hasLocalInfo -> "检查更新"
                repairing -> "修复核心依赖"
                needsSource -> "配置自定义仓库"
                !info.isInstalled -> "安装此核心"
                info.sourceMismatch || info.sourceStatus == CoreSourceStatus.UnknownLegacy -> "应用来源变更"
                !current -> "切换到此核心"
                info.hasVersionUpdate -> "立即更新"
                viewModel.isCheckingUpdate -> "检查中…"
                else -> "检查更新"
            }
            val actionIcon = when {
                !hasLocalInfo -> Icons.Outlined.Refresh
                repairing || needsSource -> Icons.Outlined.Settings
                !info.isInstalled -> Icons.Outlined.Download
                !current -> Icons.Outlined.SwapHoriz
                info.hasVersionUpdate -> Icons.Outlined.SystemUpdate
                else -> Icons.Outlined.Refresh
            }
            AppGlassPrimaryButton(onClick = {
                when {
                    repairing -> viewModel.openDependencyRepairDialog()
                    needsSource -> viewModel.openVariantSettingsDialog(info.variant)
                    !info.isInstalled -> viewModel.installCore(info.variant)
                    info.sourceMismatch || info.sourceStatus == CoreSourceStatus.UnknownLegacy -> viewModel.doUpdate(info.variant)
                    !current -> viewModel.updateVariant(info.variant)
                    info.hasVersionUpdate -> viewModel.doUpdate(info.variant)
                    else -> viewModel.checkUpdate(info.variant)
                }
            }, enabled = coreActionsEnabled, modifier = Modifier.fillMaxWidth().height(46.dp),
                height = 46.dp, shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
                else Icon(actionIcon, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(actionLabel, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            if (info.hasVersionUpdate) {
                TextButton(onClick = { viewModel.openUpdateDetails(info.variant) }, enabled = coreActionsEnabled,
                    modifier = Modifier.fillMaxWidth().height(36.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Icon(Icons.Outlined.Description, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("更新说明（${formatCoreVersionValue(info.availableVersion)}）", fontSize = 12.5.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CoreShortcutButton(Icons.Outlined.History, "版本回退", coreActionsEnabled && info.isInstalled && !needsSource,
                    { viewModel.openRollbackDialog(info.variant) }, Modifier.weight(1f))
                CoreShortcutButton(Icons.AutoMirrored.Outlined.CallMerge, "PR 实验室", coreActionsEnabled && !repairing && !needsSource,
                    { onOpenPullRequestLab(info.variant) }, Modifier.weight(1f))
                CoreShortcutButton(Icons.Outlined.SwapVerticalCircle, routeLabel, !busy,
                    viewModel::openProxyPickerManually, Modifier.weight(1f))
            }
        }
    }
    if (deleteConfirm) {
        AppDialog(onDismissRequest = { deleteConfirm = false }, style = AppDialogStyle.Confirm, tone = AppDialogTone.Danger,
            icon = { Icon(Icons.Outlined.DeleteOutline, null) }, title = { Text("删除 $label？") },
            text = { Text("将删除当前工作目录中的该核心文件。") },
            confirmButton = { AppGlassDangerButton(onClick = { deleteConfirm = false; viewModel.deleteCore(info.variant) }) { Text("删除") } },
            dismissButton = { AppGlassButton(onClick = { deleteConfirm = false }) { Text("取消") } })
    }
}

@Composable
private fun CoreCommitTile(title: String, value: String, subtitle: String, modifier: Modifier, error: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    AppGlassSurface(modifier = modifier, shape = RoundedCornerShape(12.dp),
        color = colors.surfaceContainerHighest.copy(alpha = 0.8f),
        border = BorderStroke(1.dp, glassBorderColor())) {
        Column(Modifier.heightIn(min = 68.dp).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(title, fontSize = 11.sp, lineHeight = 14.sp, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 13.5.sp, lineHeight = 17.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                color = if (error) colors.error else colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(subtitle.ifBlank { " " }, fontSize = 10.5.sp, lineHeight = 14.sp, color = colors.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CoreShortcutButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.height(40.dp), shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = if (enabled) 0.75f else 0.45f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.surfaceContainerHighest,
            contentColor = colors.primary,
            disabledContainerColor = colors.surfaceContainerHighest,
            disabledContentColor = colors.onSurfaceVariant.copy(alpha = 0.55f)
        ),
        contentPadding = PaddingValues(horizontal = 6.dp)) {
        Icon(icon, null, Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun GithubQuotaStrip(status: GithubAccountStatus, onRefresh: () -> Unit, onFillToken: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val remaining = status.coreRemaining
    val limit = status.coreLimit
    val known = remaining != null && limit != null && limit > 0
    val low = known && remaining <= (limit * 0.15)
    AppGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = glassSurfaceColor(),
        border = BorderStroke(1.dp, glassBorderColor())) {
        Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Cloud, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text("GitHub 额度", Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onFillToken, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(if (status.tokenConfigured) "更换 Token" else "设置 Token", fontSize = 12.5.sp)
                }
            }
            Spacer(Modifier.height(2.dp))
            if (known) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text("剩余 $remaining", fontSize = 20.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                        color = if (low) colors.error else colors.onSurface)
                    Text(" / $limit", Modifier.padding(bottom = 1.dp), fontSize = 12.5.sp,
                        fontFamily = FontFamily.Monospace, color = colors.onSurfaceVariant)
                    Text(coreQuotaResetHint(status), Modifier.weight(1f).padding(start = 8.dp, bottom = 1.dp),
                        fontSize = 11.5.sp, color = colors.onSurfaceVariant, textAlign = TextAlign.End,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { coreQuotaUsedRatio(status) },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)),
                    color = if (low) colors.error else colors.primary, trackColor = colors.surfaceContainerHighest,
                    gapSize = 0.dp, drawStopIndicator = {})
                Spacer(Modifier.height(6.dp))
                Text(if (status.tokenConfigured) "已使用 Token（$limit 次/小时）" else "匿名额度 $limit 次/小时，设置 Token 可提升额度",
                    fontSize = 11.5.sp, color = colors.onSurfaceVariant)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (status.isLoading) "正在读取额度…" else status.error ?: "额度尚未读取", Modifier.weight(1f),
                        fontSize = 12.5.sp, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (status.isLoading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else TextButton(onClick = onRefresh, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("重试", fontSize = 12.5.sp) }
                }
            }
            if (known && status.error != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(status.error, Modifier.weight(1f), fontSize = 11.sp, color = colors.error, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = onRefresh, enabled = !status.isLoading) { Text("重试", fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
internal fun DownloadProgressBlock(progress: CoreDownloadProgress) {
    if (!progress.inProgress) return
    AppGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = glassSurfaceColor(),
        border = BorderStroke(1.dp, glassBorderColor())) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("${progress.actionLabel} · ${progress.stageText}", Modifier.weight(1f), fontSize = 12.5.sp)
                progress.progress?.let { Text("${(it * 100).toInt().coerceIn(0, 100)}%", fontSize = 11.sp) }
            }
            progress.progress?.let {
                LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(6.dp)))
            }
            if (progress.downloadedBytes > 0) {
                Text(if (progress.totalBytes > 0) "${coreFormatBytes(progress.downloadedBytes)} / ${coreFormatBytes(progress.totalBytes)}"
                    else coreFormatBytes(progress.downloadedBytes), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun coreFormatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024))
    bytes >= 1024L -> String.format(Locale.getDefault(), "%.2f KB", bytes / 1024.0)
    else -> "$bytes B"
}
