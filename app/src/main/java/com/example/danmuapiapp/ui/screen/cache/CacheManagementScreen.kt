package com.example.danmuapiapp.ui.screen.cache

import com.example.danmuapiapp.ui.component.AppSnackbarHost

import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.AppDialogStyle
import com.example.danmuapiapp.ui.component.AppDialogTone
import com.example.danmuapiapp.ui.component.AppGlassSurface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.danmuapiapp.domain.model.CacheEntry
import com.example.danmuapiapp.domain.model.CacheClearSupport
import com.example.danmuapiapp.domain.model.CacheStats
import com.example.danmuapiapp.ui.component.AdminModeRequiredDialog
import com.example.danmuapiapp.ui.component.AdminModeRequiredTarget
import com.example.danmuapiapp.ui.component.CacheClearCapabilityNotice
import com.example.danmuapiapp.ui.component.CacheClearSelectionList
import com.example.danmuapiapp.ui.component.CacheClearSelectionToolbar
import com.example.danmuapiapp.ui.component.adminModeRequiredPrompt
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton
import com.example.danmuapiapp.ui.theme.appDangerButtonColors
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CacheManagementScreen(
    onBack: () -> Unit,
    onOpenAdminMode: () -> Unit,
    viewModel: CacheViewModel = hiltViewModel()
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val adminState by viewModel.adminSessionState.collectAsStateWithLifecycle()
    val clearCapability by viewModel.clearCapability.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppGlassIconButton(
                        onClick = onBack,
                        size = 36.dp
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", Modifier.size(18.dp))
                    }
                    Column {
                        Text("缓存管理", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            if (stats.isAvailable)
                                "${stats.reqRecordsCount} 条记录 · 今日 ${stats.todayReqNum} 次请求"
                            else
                                "查看与清理核心缓存数据",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                AppGlassIconButton(
                    onClick = viewModel::refresh,
                    enabled = !isLoading,
                    size = 38.dp
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Refresh, "刷新", Modifier.size(19.dp))
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 22.dp)
            ) {
                if (!stats.isAvailable && !isLoading) {
                    item(key = "offline") {
                        AppGlassSurface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "启动服务后可读取并清理核心缓存。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (stats.isAvailable) {
                    item(key = "stats") { CacheStatsRow(stats = stats) }
                }

                item(key = "clear-heading") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("清理范围", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "选择本次需要重置的核心缓存，收藏数据不会受影响",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item(key = "capability") { CacheClearCapabilityNotice(clearCapability) }
                item(key = "toolbar") {
                    CacheClearSelectionToolbar(
                        selectedCount = viewModel.selectedItems.size,
                        selectionEnabled = clearCapability.supportsSelective && !viewModel.isClearing,
                        onSelectAll = viewModel::selectAll,
                        onSelectNone = viewModel::selectNone
                    )
                }
                item(key = "selection") {
                    CacheClearSelectionList(
                        selectedItems = viewModel.selectedItems,
                        selectionEnabled = clearCapability.supportsSelective && !viewModel.isClearing,
                        onToggle = viewModel::toggleClearItem
                    )
                }
                item(key = "clear-action") {
                    AppGlassButton(
                        onClick = viewModel::requestClear,
                        enabled = stats.isAvailable && viewModel.selectedItems.isNotEmpty() &&
                            !viewModel.isClearing && !isLoading,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        tint = MaterialTheme.colorScheme.error,
                        surfaceColor = MaterialTheme.colorScheme.error.copy(alpha = 0.18f),
                        contentColor = MaterialTheme.colorScheme.error
                    ) {
                        if (viewModel.isClearing) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(19.dp))
                        }
                        Spacer(Modifier.width(7.dp))
                        Text(
                            if (clearCapability.support == CacheClearSupport.Selective) {
                                "清理已选 ${viewModel.selectedItems.size} 项"
                            } else {
                                "兼容清理全部缓存"
                            }
                        )
                    }
                }

                item(key = "records-heading") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text("最近请求", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "显示 ${entries.size} 条",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (entries.isNotEmpty()) {
                    itemsIndexed(
                        items = entries,
                        key = { index, entry ->
                            "${entry.type}|${entry.key}|${entry.createdAt}|$index"
                        }
                    ) { _, entry ->
                        CacheEntryCard(entry = entry)
                    }
                } else if (stats.isAvailable && !isLoading) {
                    item(key = "records-empty") {
                        AppGlassSurface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    "当前无请求记录",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }


    if (viewModel.showAdminRequiredDialog) {
        AdminModeRequiredDialog(
            prompt = adminModeRequiredPrompt(
                target = AdminModeRequiredTarget.ClearCache,
                hasAdminTokenConfigured = adminState.hasAdminTokenConfigured
            ),
            onOpenAdminMode = onOpenAdminMode,
            onDismiss = viewModel::dismissAdminRequiredDialog
        )
    }

    if (viewModel.showClearConfirmDialog) {
        AppDialog(
            onDismissRequest = viewModel::dismissClearConfirm,
            style = AppDialogStyle.Confirm,
            tone = AppDialogTone.Danger,
            icon = { Icon(Icons.Rounded.DeleteSweep, null) },
            title = { Text("确认清理缓存") },
            text = {
                Text(
                    if (clearCapability.support == CacheClearSupport.Selective) {
                        "将清理已选择的 ${viewModel.selectedItems.size} 项缓存，未选择的数据会保留。"
                    } else {
                        "当前核心不支持按项清理，将清除全部八项核心缓存。"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                AppGlassButton(
                    onClick = viewModel::clearSelected,
                    tint = MaterialTheme.colorScheme.error,
                    surfaceColor = MaterialTheme.colorScheme.error.copy(alpha = 0.18f),
                    contentColor = MaterialTheme.colorScheme.error
                ) {
                    Text("确认清理")
                }
            },
            dismissButton = {
                AppGlassButton(onClick = viewModel::dismissClearConfirm) { Text("取消") }
            }
        )
    }
}

@Composable
private fun CacheStatsRow(stats: CacheStats) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatCell(label = "请求记录", value = "${stats.reqRecordsCount}")
                StatCell(label = "今日请求", value = "${stats.todayReqNum}")
                StatCell(label = "番剧缓存", value = "${stats.animeCacheCount}")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatCell(label = "被合并源", value = "${stats.mergedSourceCount}")
                StatCell(label = "剧集映射", value = "${stats.episodeLinkCount}")
                StatCell(
                    label = "上次清理",
                    value = stats.lastClearedAt?.let { dateFormat.format(Date(it)) } ?: "从未"
                )
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CacheEntryCard(entry: CacheEntry) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    val methodColor = when (entry.type.uppercase()) {
        "GET" -> MaterialTheme.colorScheme.primary
        "POST" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusCode = entry.statusCode ?: entry.hitCount.takeIf { it in 100..599 }
    val statusColor = when {
        statusCode != null && statusCode in 200..299 -> MaterialTheme.colorScheme.primary
        statusCode != null && statusCode >= 400 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val inputLine = when {
        entry.requestUrl.isNotBlank() -> "URL：${entry.requestUrl}"
        entry.fileName.isNotBlank() -> "文件名：${entry.fileName}"
        entry.keyword.isNotBlank() -> "关键词：${entry.keyword}"
        else -> ""
    }

    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (entry.type.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = methodColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                entry.type.uppercase(),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = methodColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (statusCode != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = statusColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                "$statusCode",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor
                            )
                        }
                    }
                    if (entry.clientIp.isNotBlank()) {
                        Text(
                            entry.clientIp,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    dateFormat.format(Date(entry.createdAt)),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            Text(
                text = "真实 API：${entry.key.ifBlank { "未知接口" }}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (inputLine.isNotBlank()) {
                Text(
                    text = inputLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (entry.paramsText.isNotBlank() && inputLine.isBlank()) {
                Text(
                    text = "参数：${entry.paramsText}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> String.format(Locale.getDefault(), "%.2f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.getDefault(), "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.getDefault(), "%.1f KB", bytes / kb)
        else -> "$bytes B"
    }
}
