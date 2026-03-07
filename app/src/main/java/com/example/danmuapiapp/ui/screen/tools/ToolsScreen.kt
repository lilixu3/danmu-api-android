package com.example.danmuapiapp.ui.screen.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.domain.model.LogEntry
import com.example.danmuapiapp.domain.model.LogLevel
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.ui.component.SectionHeader
import com.example.danmuapiapp.ui.component.StatusIndicator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

@Composable
fun ToolsScreen(
    onOpenApiTest: () -> Unit,
    onOpenPushDanmu: () -> Unit,
    onOpenDanmuDownload: () -> Unit,
    onOpenRequestRecords: () -> Unit,
    onOpenConsole: () -> Unit,
    onOpenConfig: () -> Unit,
    onOpenDeviceAccess: () -> Unit,
    onOpenAdminMode: () -> Unit,
    onOpenCacheManagement: () -> Unit,
    viewModel: ToolsViewModel = hiltViewModel()
) {
    val runtimeState by viewModel.runtimeState.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val adminState by viewModel.adminSessionState.collectAsStateWithLifecycle()
    val logPreviewEnabled by viewModel.logPreviewEnabled.collectAsStateWithLifecycle()
    val logEnabled by viewModel.logEnabled.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val recentLogs = logs.takeLast(6).reversed()
    val errorCount = logs.count { it.level == LogLevel.Error }
    val warnCount = logs.count { it.level == LogLevel.Warn }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.refreshLogs()
                viewModel.refreshAdminState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "工具",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold
            )
            AdminModeStatusChip(
                enabled = adminState.isAdminMode,
                onClick = onOpenAdminMode
            )
        }
        Text(
            text = "高频操作在前，排查与追踪在后",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ToolsOverviewCard(
            status = runtimeState.status,
            port = runtimeState.port,
            variantLabel = runtimeState.variant.label,
            adminEnabled = adminState.isAdminMode,
            logEnabled = logEnabled,
            logCount = logs.size,
            errorCount = errorCount,
            warnCount = warnCount,
            onOpenConsole = onOpenConsole
        )

        SectionHeader(title = "高频操作")
        ToolEntryCard(
            title = "配置管理",
            subtitle = "修改 .env、端口与服务行为，适合先从这里开始",
            imageVector = Icons.Rounded.Settings,
            accent = MaterialTheme.colorScheme.primary,
            badge = "推荐",
            onClick = onOpenConfig
        )
        ToolEntryCard(
            title = "接口调试",
            subtitle = "自定义请求参数并直接查看返回结果",
            imageVector = Icons.Rounded.Link,
            accent = MaterialTheme.colorScheme.tertiary,
            badge = "常用",
            onClick = onOpenApiTest
        )
        ToolEntryCard(
            title = "弹幕下载",
            subtitle = "下载弹幕到本地目录并记录历史，适合批量处理",
            imageVector = Icons.Rounded.CloudDownload,
            accent = MaterialTheme.colorScheme.primary,
            onClick = onOpenDanmuDownload
        )
        ToolEntryCard(
            title = "弹幕推送",
            subtitle = "把弹幕链接发送到本地播放器，快速进入播放场景",
            imageVector = Icons.Rounded.CloudUpload,
            accent = MaterialTheme.colorScheme.secondary,
            onClick = onOpenPushDanmu
        )

        SectionHeader(title = "排查与追踪")
        AnimatedVisibility(
            visible = logPreviewEnabled && logEnabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            LogPreviewCard(
                logs = recentLogs,
                totalCount = logs.size,
                errorCount = errorCount,
                warnCount = warnCount,
                onViewAll = onOpenConsole,
                onRefresh = viewModel::refreshLogs
            )
        }
        AnimatedVisibility(
            visible = !(logPreviewEnabled && logEnabled),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            ToolEntryCard(
                title = "运行日志",
                subtitle = if (!logEnabled) {
                    "日志已关闭，进入后可查看当前状态与设置"
                } else {
                    "查看完整运行日志与最近错误信息"
                },
                imageVector = Icons.Rounded.Terminal,
                accent = MaterialTheme.colorScheme.primary,
                badge = if (!logEnabled) "已关闭" else null,
                onClick = onOpenConsole
            )
        }
        ToolEntryCard(
            title = "请求记录",
            subtitle = "查看最近接口调用历史，适合排查异常请求",
            imageVector = Icons.Rounded.History,
            accent = MaterialTheme.colorScheme.tertiary,
            onClick = onOpenRequestRecords
        )
        ToolEntryCard(
            title = "缓存管理",
            subtitle = "查看与清理核心缓存数据，避免旧状态影响结果",
            imageVector = Icons.Rounded.Storage,
            accent = MaterialTheme.colorScheme.secondary,
            onClick = onOpenCacheManagement
        )

        SectionHeader(title = "设备与权限")
        ToolEntryCard(
            title = "设备控制",
            subtitle = "黑名单管理、访问排行与局域网检测",
            imageVector = Icons.Rounded.Shield,
            accent = MaterialTheme.colorScheme.secondary,
            onClick = onOpenDeviceAccess
        )
        ToolEntryCard(
            title = "管理员模式",
            subtitle = if (adminState.isAdminMode) {
                "当前已开启，可进入敏感配置与高级能力"
            } else {
                "当前未开启，进入后可输入 ADMIN_TOKEN"
            },
            imageVector = if (adminState.isAdminMode) {
                Icons.Rounded.VerifiedUser
            } else {
                Icons.Rounded.AdminPanelSettings
            },
            accent = if (adminState.isAdminMode) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            badge = if (adminState.isAdminMode) "已开启" else "未开启",
            onClick = onOpenAdminMode
        )
    }
}

@Composable
private fun AdminModeStatusChip(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (enabled) Icons.Rounded.VerifiedUser else Icons.Rounded.AdminPanelSettings,
                contentDescription = if (enabled) "已开启管理员模式" else "未开启管理员模式",
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = if (enabled) "已开启管理员模式" else "未开启管理员模式",
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ToolsOverviewCard(
    status: ServiceStatus,
    port: Int,
    variantLabel: String,
    adminEnabled: Boolean,
    logEnabled: Boolean,
    logCount: Int,
    errorCount: Int,
    warnCount: Int,
    onOpenConsole: () -> Unit
) {
    val accent = when (status) {
        ServiceStatus.Running -> MaterialTheme.colorScheme.primary
        ServiceStatus.Starting, ServiceStatus.Stopping -> MaterialTheme.colorScheme.tertiary
        ServiceStatus.Error -> MaterialTheme.colorScheme.error
        ServiceStatus.Stopped -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val subtitle = when (status) {
        ServiceStatus.Running -> "服务已启动 · 端口 $port · $variantLabel，适合直接进入调试与下载"
        ServiceStatus.Starting -> "服务正在启动，日志会优先反映当前进度"
        ServiceStatus.Stopping -> "服务正在停止，可先查看日志确认收尾情况"
        ServiceStatus.Error -> "服务异常，建议优先查看运行日志与请求记录"
        ServiceStatus.Stopped -> "服务未启动，可先配置参数，再进入调试或下载"
    }
    val logSummary = when {
        !logEnabled -> "已关闭"
        logCount <= 0 -> "暂无"
        errorCount > 0 -> "$errorCount 错误"
        warnCount > 0 -> "$warnCount 警告"
        else -> "$logCount 条"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = 0.1f),
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(accent.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        StatusIndicator(status = status, modifier = Modifier.size(14.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "工作台概览",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                FilledTonalIconButton(
                    onClick = onOpenConsole,
                    modifier = Modifier.size(34.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f)
                    )
                ) {
                    Icon(
                        Icons.Rounded.Terminal,
                        contentDescription = "查看日志",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OverviewStatChip(
                    modifier = Modifier.weight(1f),
                    label = "当前状态",
                    value = when (status) {
                        ServiceStatus.Running -> "运行中"
                        ServiceStatus.Starting -> "启动中"
                        ServiceStatus.Stopping -> "停止中"
                        ServiceStatus.Error -> "异常"
                        ServiceStatus.Stopped -> "未启动"
                    },
                    accent = accent
                )
                OverviewStatChip(
                    modifier = Modifier.weight(1f),
                    label = "日志概况",
                    value = logSummary,
                    accent = if (errorCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                OverviewStatChip(
                    modifier = Modifier.weight(1f),
                    label = "管理员",
                    value = if (adminEnabled) "已开启" else "未开启",
                    accent = if (adminEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OverviewStatChip(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accent: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.82f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun LogPreviewCard(
    logs: List<LogEntry>,
    totalCount: Int,
    errorCount: Int,
    warnCount: Int,
    onViewAll: () -> Unit,
    onRefresh: () -> Unit
) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onViewAll),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "运行日志摘要",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "最近 ${min(totalCount, 6)} 条日志会先显示在这里，点击可进入完整日志页",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(30.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                        )
                    ) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "刷新日志",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LogSummaryPill(label = "总数", value = "$totalCount 条")
                if (errorCount > 0) {
                    LogSummaryPill(
                        label = "错误",
                        value = "$errorCount 条",
                        accent = if (isSystemInDarkTheme()) Color(0xFFF87171) else Color(0xFFE53935)
                    )
                }
                if (warnCount > 0) {
                    LogSummaryPill(
                        label = "警告",
                        value = "$warnCount 条",
                        accent = if (isSystemInDarkTheme()) Color(0xFFFBBF24) else Color(0xFFFFC107)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (logs.isEmpty()) {
                Text(
                    text = "暂无日志",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        logs.forEach { entry ->
                            val dark = isSystemInDarkTheme()
                            val color = when (entry.level) {
                                LogLevel.Error -> if (dark) Color(0xFFF87171) else Color(0xFFE53935)
                                LogLevel.Warn -> if (dark) Color(0xFFFBBF24) else Color(0xFFFFC107)
                                LogLevel.Info -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = timeFormat.format(Date(entry.timestamp)),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                )
                                Text(
                                    text = entry.message,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    ),
                                    color = color,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogSummaryPill(
    label: String,
    value: String,
    accent: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = accent
            )
        }
    }
}

@Composable
private fun ToolEntryCard(
    title: String,
    subtitle: String,
    imageVector: ImageVector,
    onClick: () -> Unit,
    accent: Color,
    badge: String? = null,
    highlight: Boolean = false
) {
    val containerColor = if (highlight) {
        accent.copy(alpha = if (isSystemInDarkTheme()) 0.13f else 0.08f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val borderColor = if (highlight) {
        accent.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(14.dp),
                color = accent.copy(alpha = if (highlight) 0.18f else 0.12f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.16f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = imageVector,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!badge.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = accent.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
