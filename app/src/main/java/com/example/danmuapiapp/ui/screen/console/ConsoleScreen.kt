package com.example.danmuapiapp.ui.screen.console

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.danmuapiapp.domain.model.AppLogSource
import com.example.danmuapiapp.domain.model.LogEntry
import com.example.danmuapiapp.domain.model.LogLevel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScreen(viewModel: ConsoleViewModel = hiltViewModel()) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val logEnabled by viewModel.logEnabled.collectAsStateWithLifecycle()
    val logPreviewEnabled by viewModel.logPreviewEnabled.collectAsStateWithLifecycle()
    val logMaxCount by viewModel.logMaxCount.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val adminState by viewModel.adminSessionState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboard.current
    var filterLevel by remember { mutableStateOf<LogLevel?>(null) }
    var filterSource by remember { mutableStateOf<AppLogSource?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

    val dark = isSystemInDarkTheme()
    val chipErrorColor = if (dark) Color(0xFFF87171) else Color(0xFFE53935)
    val chipWarnColor  = if (dark) Color(0xFFFBBF24) else Color(0xFFFFC107)

    val filteredLogs = logs
        .let { list -> if (filterLevel != null) list.filter { it.level == filterLevel } else list }
        .let { list -> if (filterSource != null) list.filter { it.source == filterSource } else list }
        .let { list ->
            if (searchQuery.isNotBlank()) list.filter {
                it.message.contains(searchQuery, ignoreCase = true) ||
                    it.tag.contains(searchQuery, ignoreCase = true) ||
                    it.source.label.contains(searchQuery, ignoreCase = true)
            } else list
        }

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

    LaunchedEffect(adminState.isAdminMode) {
        viewModel.refreshLogs()
    }

    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    // Settings bottom sheet
    if (showSettings) {
        val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        val settingsSheetMaxHeight = (screenHeight * 0.9f).coerceAtLeast(320.dp)
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = settingsSheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            dragHandle = {
                BottomSheetDefaults.DragHandle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.34f)
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = settingsSheetMaxHeight)
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("日志设置", style = MaterialTheme.typography.titleMedium)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("开启日志", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "关闭后不拉取和显示日志",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = logEnabled, onCheckedChange = viewModel::setLogEnabled)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("日志预览", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "在工具页显示日志预览卡片",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = logPreviewEnabled, onCheckedChange = viewModel::setLogPreviewEnabled)
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("日志上限", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "$logMaxCount 条",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = logMaxCount.toFloat(),
                        onValueChange = { viewModel.setLogMaxCount((it / 100).roundToInt() * 100) },
                        valueRange = 100f..2000f,
                        steps = 18
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("2000", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("运行日志", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "${logs.size} 条日志" +
                        (if (errorCount > 0) " / $errorCount 错误" else "") +
                        (if (warnCount > 0) " / $warnCount 警告" else ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalIconButton(
                    onClick = { showSearch = !showSearch },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Rounded.Search, "搜索日志", Modifier.size(18.dp))
                }
                FilledTonalIconButton(
                    onClick = { showSettings = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Rounded.Settings, "日志设置", Modifier.size(18.dp))
                }
                FilledTonalIconButton(
                    onClick = viewModel::refreshLogs,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, "刷新日志", Modifier.size(18.dp))
                }
                // Copy all logs
                FilledTonalIconButton(
                    onClick = {
                        val text = logs.joinToString("\n") {
                            buildString {
                                append('[')
                                append(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it.timestamp)))
                                append("][")
                                append(it.source.label)
                                append(']')
                                if (it.tag.isNotBlank()) {
                                    append('[')
                                    append(it.tag)
                                    append(']')
                                }
                                append('[')
                                append(it.level.name)
                                append("] ")
                                append(it.message)
                            }
                        }
                        clipboardManager.nativeClipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("日志", text)
                        )
                    },
                    enabled = logs.isNotEmpty(),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, "复制日志", Modifier.size(18.dp))
                }
                FilledTonalIconButton(
                    onClick = viewModel::clearLogs,
                    enabled = logs.isNotEmpty(),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Rounded.ClearAll, "清除日志", Modifier.size(18.dp))
                }
            }
        }

        // Search bar
        AnimatedVisibility(
            visible = showSearch,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = { Text("搜索日志...") },
                leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Rounded.Close, "清除搜索", Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterLevel == null,
                onClick = { filterLevel = null },
                label = { Text("全部") },
                leadingIcon = if (filterLevel == null) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                } else null
            )
            FilterChip(
                selected = filterLevel == LogLevel.Error,
                onClick = {
                    filterLevel = if (filterLevel == LogLevel.Error) null else LogLevel.Error
                },
                label = { Text("错误") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = chipErrorColor.copy(alpha = 0.15f)
                ),
                leadingIcon = if (filterLevel == LogLevel.Error) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                } else null
            )
            FilterChip(
                selected = filterLevel == LogLevel.Warn,
                onClick = {
                    filterLevel = if (filterLevel == LogLevel.Warn) null else LogLevel.Warn
                },
                label = { Text("警告") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = chipWarnColor.copy(alpha = 0.15f)
                ),
                leadingIcon = if (filterLevel == LogLevel.Warn) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                } else null
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterSource == null,
                onClick = { filterSource = null },
                label = { Text("全部来源") },
                leadingIcon = if (filterSource == null) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                } else null
            )
            FilterChip(
                selected = filterSource == AppLogSource.Core,
                onClick = {
                    filterSource = if (filterSource == AppLogSource.Core) null else AppLogSource.Core
                },
                label = { Text(AppLogSource.Core.label) },
                leadingIcon = if (filterSource == AppLogSource.Core) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                } else null
            )
            FilterChip(
                selected = filterSource == AppLogSource.App,
                onClick = {
                    filterSource = if (filterSource == AppLogSource.App) null else AppLogSource.App
                },
                label = { Text(AppLogSource.App.label) },
                leadingIcon = if (filterSource == AppLogSource.App) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                } else null
            )
            FilterChip(
                selected = filterSource == AppLogSource.RootBootstrap,
                onClick = {
                    filterSource =
                        if (filterSource == AppLogSource.RootBootstrap) null else AppLogSource.RootBootstrap
                },
                label = { Text(AppLogSource.RootBootstrap.label) },
                leadingIcon = if (filterSource == AppLogSource.RootBootstrap) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                } else null
            )
        }

        // Log list or disabled state
        if (!logEnabled) {
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.VisibilityOff, null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "日志已关闭",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "可在设置中重新开启",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                )
            ) {
                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.Terminal, null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                when {
                                    searchQuery.isNotBlank() -> "没有匹配的日志"
                                    filterLevel != null || filterSource != null -> "没有匹配的日志"
                                    else -> "暂无日志"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(
                            items = filteredLogs,
                            key = { index, entry ->
                                "${entry.timestamp}-${entry.level}-${entry.message.hashCode()}-$index"
                            }
                        ) { _, entry ->
                            LogEntryRow(entry)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun LogEntryRow(entry: LogEntry) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val dark = isSystemInDarkTheme()
    val errorColor = if (dark) Color(0xFFF87171) else Color(0xFFE53935)
    val warnColor  = if (dark) Color(0xFFFBBF24) else Color(0xFFFFC107)
    val color = when (entry.level) {
        LogLevel.Error -> errorColor
        LogLevel.Warn -> warnColor
        LogLevel.Info -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(
                when (entry.level) {
                    LogLevel.Error -> errorColor.copy(alpha = if (dark) 0.12f else 0.08f)
                    LogLevel.Warn -> warnColor.copy(alpha = if (dark) 0.10f else 0.05f)
                    else -> Color.Transparent
                }
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timeFormat.format(Date(entry.timestamp)),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = entry.source.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entry.tag.isNotBlank()) {
                    Text(
                        text = entry.tag,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = entry.message,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                color = color
            )
        }
    }
}
