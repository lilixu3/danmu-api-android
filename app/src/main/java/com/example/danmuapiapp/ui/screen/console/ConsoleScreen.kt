package com.example.danmuapiapp.ui.screen.console

import android.content.Intent
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.danmuapiapp.BuildConfig
import com.example.danmuapiapp.domain.model.LogEntry
import com.example.danmuapiapp.domain.model.LogLevel
import com.example.danmuapiapp.domain.model.LogTagClassifier
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.ui.component.AppPanelDialog
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScreen(viewModel: ConsoleViewModel = hiltViewModel()) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val runtimeState by viewModel.runtimeState.collectAsStateWithLifecycle()
    val logEnabled by viewModel.logEnabled.collectAsStateWithLifecycle()
    val logPreviewEnabled by viewModel.logPreviewEnabled.collectAsStateWithLifecycle()
    val logMaxCount by viewModel.logMaxCount.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val adminState by viewModel.adminSessionState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboard.current
    val context = LocalContext.current
    var filterLevel by remember { mutableStateOf<LogLevel?>(null) }
    var filterSource by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var moreMenuExpanded by remember { mutableStateOf(false) }
    var pendingExportText by remember { mutableStateOf("") }
    val saveLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && pendingExportText.isNotBlank()) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(pendingExportText.toByteArray(Charsets.UTF_8))
                } ?: error("无法打开导出文件")
            }.onSuccess {
                Toast.makeText(context, "日志已导出", Toast.LENGTH_SHORT).show()
            }.onFailure { throwable ->
                Toast.makeText(context, throwable.message ?: "导出日志失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val dark = isSystemInDarkTheme()
    val chipErrorColor = if (dark) Color(0xFFF87171) else Color(0xFFE53935)
    val chipWarnColor  = if (dark) Color(0xFFFBBF24) else Color(0xFFFFC107)

    val availableSources = remember(logs) {
        LogTagClassifier.sortTags(logs.map { entry -> LogTagClassifier.sourceFilterFor(entry) })
    }
    val sourceCounts = remember(logs, availableSources) {
        availableSources.associateWith { source ->
            logs.count { entry -> LogTagClassifier.matchesSource(entry, source) }
        }
    }

    LaunchedEffect(availableSources) {
        val selected = filterSource
        if (selected != null && !availableSources.contains(selected)) {
            filterSource = null
        }
    }

    val filteredLogs = logs
        .let { list -> if (filterLevel != null) list.filter { it.level == filterLevel } else list }
        .let { list -> if (filterSource != null) list.filter { LogTagClassifier.matchesSource(it, filterSource!!) } else list }
        .let { list ->
            if (searchQuery.isNotBlank()) list.filter {
                it.message.contains(searchQuery, ignoreCase = true) ||
                    it.tag.contains(searchQuery, ignoreCase = true) ||
                    it.category.contains(searchQuery, ignoreCase = true) ||
                    it.tags.any { tag -> tag.contains(searchQuery, ignoreCase = true) } ||
                    LogTagClassifier.labelFor(it.category).contains(searchQuery, ignoreCase = true) ||
                    it.source.label.contains(searchQuery, ignoreCase = true)
            } else list
        }
    val hasActiveFilters = filterLevel != null || filterSource != null || searchQuery.isNotBlank()
    val exportScopeLabel = if (hasActiveFilters) "当前筛选日志" else "全部日志"
    val exportText = remember(filteredLogs, runtimeState, exportScopeLabel) {
        buildLogExportText(
            logs = filteredLogs,
            scopeLabel = exportScopeLabel,
            runtimeState = runtimeState
        )
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
        AppPanelDialog(
            onDismissRequest = { showSettings = false },
            horizontalPadding = 20.dp,
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "运行日志",
                    style = MaterialTheme.typography.headlineLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        if (hasActiveFilters) {
                            append("${filteredLogs.size}/${logs.size} 条日志")
                        } else {
                            append("${logs.size} 条日志")
                        }
                        if (errorCount > 0) append(" / $errorCount 错误")
                        if (warnCount > 0) append(" / $warnCount 警告")
                    },
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
                    onClick = viewModel::refreshLogs,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, "刷新日志", Modifier.size(18.dp))
                }
                // Copy visible logs after current filters/search.
                FilledTonalIconButton(
                    onClick = {
                        val text = LogExportFormatter.toClipboardText(filteredLogs)
                        clipboardManager.nativeClipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("当前筛选日志", text)
                        )
                    },
                    enabled = filteredLogs.isNotEmpty(),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, "复制当前筛选日志", Modifier.size(18.dp))
                }
                FilledTonalIconButton(
                    onClick = viewModel::clearLogs,
                    enabled = logs.isNotEmpty(),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Rounded.ClearAll, "清除日志", Modifier.size(18.dp))
                }
                Box {
                    FilledTonalIconButton(
                        onClick = { moreMenuExpanded = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Rounded.MoreVert, "更多日志操作", Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = moreMenuExpanded,
                        onDismissRequest = { moreMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("日志设置") },
                            leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                            onClick = {
                                moreMenuExpanded = false
                                showSettings = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("导出为 txt") },
                            leadingIcon = { Icon(Icons.Rounded.FileDownload, contentDescription = null) },
                            enabled = filteredLogs.isNotEmpty(),
                            onClick = {
                                moreMenuExpanded = false
                                pendingExportText = exportText
                                saveLogLauncher.launch(LogExportFormatter.defaultFileName())
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("分享 txt") },
                            leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                            enabled = filteredLogs.isNotEmpty(),
                            onClick = {
                                moreMenuExpanded = false
                                shareLogsAsTextFile(
                                    context = context,
                                    fileName = LogExportFormatter.defaultFileName(),
                                    text = exportText
                                )
                            }
                        )
                    }
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
            FilterChip(
                selected = filterLevel == LogLevel.Info,
                onClick = {
                    filterLevel = if (filterLevel == LogLevel.Info) null else LogLevel.Info
                },
                label = { Text("信息") },
                leadingIcon = if (filterLevel == LogLevel.Info) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                } else null
            )
        }

        if (availableSources.isNotEmpty()) {
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
                availableSources.forEach { source ->
                    FilterChip(
                        selected = filterSource == source,
                        onClick = { filterSource = if (filterSource == source) null else source },
                        label = {
                            Text("${LogTagClassifier.labelFor(source)} ${sourceCounts[source] ?: 0}")
                        },
                        leadingIcon = if (filterSource == source) {
                            { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }
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

internal fun buildLogExportText(
    logs: List<LogEntry>,
    scopeLabel: String,
    runtimeState: RuntimeState
): String {
    return LogExportFormatter.buildExportText(
        logs = logs,
        scopeLabel = scopeLabel,
        versionName = BuildConfig.VERSION_NAME,
        runModeLabel = runtimeState.runMode.label,
        statusLabel = runtimeState.status.toDisplayLabel(),
        port = runtimeState.port
    )
}

internal fun shareLogsAsTextFile(
    context: Context,
    fileName: String,
    text: String
) {
    runCatching {
        val dir = File(context.cacheDir, "log-exports").apply { mkdirs() }
        val file = File(dir, fileName).apply { writeText(text, Charsets.UTF_8) }
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "分享日志文件"))
    }.onFailure { throwable ->
        Toast.makeText(context, throwable.message ?: "分享日志失败", Toast.LENGTH_SHORT).show()
    }
}

private fun ServiceStatus.toDisplayLabel(): String {
    return when (this) {
        ServiceStatus.Stopped -> "已停止"
        ServiceStatus.Starting -> "启动中"
        ServiceStatus.Running -> "运行中"
        ServiceStatus.Stopping -> "停止中"
        ServiceStatus.Error -> "异常"
    }
}
