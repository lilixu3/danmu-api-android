package com.example.danmuapiapp.ui.screen.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.danmuapiapp.domain.model.LogEntry
import com.example.danmuapiapp.domain.model.LogLevel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ToolsScreen(
    onOpenApiTest: () -> Unit,
    onOpenPushDanmu: () -> Unit,
    onOpenRequestRecords: () -> Unit,
    onOpenConsole: () -> Unit,
    onOpenConfig: () -> Unit,
    viewModel: ToolsViewModel = hiltViewModel()
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val recentLogs = logs.takeLast(22).reversed()
    val errorCount = logs.count { it.level == LogLevel.Error }
    val warnCount = logs.count { it.level == LogLevel.Warn }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.refreshLogs()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("工具", style = MaterialTheme.typography.headlineLarge)
        Text(
            "接口调试、弹幕推送与请求追踪",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Log preview card (first)
        LogPreviewCard(
            logs = recentLogs,
            totalCount = logs.size,
            errorCount = errorCount,
            warnCount = warnCount,
            onViewAll = onOpenConsole,
            onRefresh = viewModel::refreshLogs
        )

        ToolEntryCard(
            title = "配置管理",
            subtitle = "可视化编辑 .env 环境变量",
            icon = { Icon(Icons.Rounded.Settings, null) },
            onClick = onOpenConfig
        )

        ToolEntryCard(
            title = "接口调试",
            subtitle = "自定义请求参数并查看返回结果",
            icon = { Icon(Icons.Rounded.Link, null) },
            onClick = onOpenApiTest
        )

        ToolEntryCard(
            title = "弹幕推送",
            subtitle = "将弹幕链接推送到本地播放器",
            icon = { Icon(Icons.Rounded.CloudUpload, null) },
            onClick = onOpenPushDanmu
        )

        ToolEntryCard(
            title = "请求记录",
            subtitle = "查看最近接口调用历史",
            icon = { Icon(Icons.Rounded.History, null) },
            onClick = onOpenRequestRecords
        )
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
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.Terminal, null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("运行日志", style = MaterialTheme.typography.titleMedium)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(28.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
                        )
                    ) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "刷新日志",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        buildString {
                            append("$totalCount 条")
                            if (errorCount > 0) append(" · $errorCount 错误")
                            if (warnCount > 0) append(" · $warnCount 警告")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        Icons.Rounded.ChevronRight, null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Log preview lines
            if (logs.isEmpty()) {
                Text(
                    "暂无日志",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tonalElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        logs.forEach { entry ->
                            val dark = isSystemInDarkTheme()
                            val color = when (entry.level) {
                                LogLevel.Error -> if (dark) Color(0xFFF87171) else Color(0xFFE53935)
                                LogLevel.Warn  -> if (dark) Color(0xFFFBBF24) else Color(0xFFFFC107)
                                LogLevel.Info -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    timeFormat.format(Date(entry.timestamp)),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace, fontSize = 10.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                Text(
                                    entry.message,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace, fontSize = 10.sp
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

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "点击查看全部日志",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun ToolEntryCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                icon()
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.Rounded.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
