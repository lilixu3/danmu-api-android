package com.example.danmuapiapp.desktop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.danmuapiapp.desktop.logs.DesktopLogEntry
import com.example.danmuapiapp.desktop.logs.DesktopLogFilter
import com.example.danmuapiapp.desktop.logs.DesktopLogReadResult
import com.example.danmuapiapp.desktop.logs.DesktopLogReader
import com.example.danmuapiapp.desktop.logs.DesktopLogSourceResult
import com.example.danmuapiapp.desktop.logs.DesktopLogSourceStatus
import com.example.danmuapiapp.desktop.logs.displayLogTimestamp
import com.example.danmuapiapp.desktop.logs.filterLogEntries
import com.example.danmuapiapp.desktop.logs.formatLogEntriesForExport
import com.example.danmuapiapp.desktop.logs.isEmpty
import com.example.danmuapiapp.desktop.logs.updateLogViewport
import com.example.danmuapiapp.desktop.runtime.DesktopPaths
import com.example.danmuapiapp.desktop.runtime.HealthReadState
import com.example.danmuapiapp.desktop.runtime.RuntimeHealthClient
import com.example.danmuapiapp.desktop.runtime.ServicePhase
import com.example.danmuapiapp.desktop.runtime.ServiceUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val MAX_VISIBLE_LOG_LINES = 1000

@Composable
fun LogsPage(
    paths: DesktopPaths,
    state: ServiceUiState,
) {
    val scope = rememberCoroutineScope()
    val reader = remember { DesktopLogReader() }
    val healthClient = remember { RuntimeHealthClient() }
    var readResult by remember { mutableStateOf<DesktopLogReadResult?>(null) }
    var readError by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var refreshNonce by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf(DesktopLogFilter()) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var actionBusy by remember { mutableStateOf(false) }
    var terminalAtLatest by remember { mutableStateOf(true) }
    var pendingNewCount by remember { mutableStateOf(0) }
    var scrollToLatestKey by remember { mutableStateOf(0) }

    LaunchedEffect(paths.root.absolutePath, state.phase, state.port, refreshNonce) {
        while (isActive) {
            if (readResult == null) refreshing = true
            val result = try {
                val healthState = if (state.phase == ServicePhase.Running && state.port != null) {
                    healthClient.read(state.port).fold(
                        onSuccess = { HealthReadState.Ready(it) },
                        onFailure = { HealthReadState.Unavailable("读取核心日志路径失败：${logDiagnostic(it)}", it) },
                    )
                } else {
                    HealthReadState.Unavailable("服务未运行，核心日志按运行目录约定探测。")
                }
                withContext(Dispatchers.IO) { reader.read(paths, healthState) }
            } catch (error: Throwable) {
                readError = "日志刷新失败：${logDiagnostic(error)}"
                null
            }
            if (result != null) {
                val previousCount = readResult?.entries?.size ?: 0
                val viewport = updateLogViewport(
                    previousTotal = previousCount,
                    currentTotal = result.entries.size,
                    wasAtLatest = terminalAtLatest,
                )
                readResult = result
                pendingNewCount = viewport.pendingNewCount
                terminalAtLatest = viewport.isAtLatest
                readError = null
                feedback = null
            }
            refreshing = false
            delay(5_000)
        }
    }

    val allEntries = readResult?.entries.orEmpty()
    val filteredEntries = filterLogEntries(allEntries, filter)
    val sourceResults = readResult?.sources.orEmpty()
    val levels = allEntries.mapNotNull { it.level }.distinct().sorted()
    val tags = allEntries.mapNotNull { it.tag }.distinct().sorted()
    val sourceOptions = sourceResults.map { it.source }
    var sourceMenuOpen by remember { mutableStateOf(false) }
    var levelMenuOpen by remember { mutableStateOf(false) }
    var tagMenuOpen by remember { mutableStateOf(false) }

    fun clearFilters() {
        filter = DesktopLogFilter()
        feedback = "已清除日志筛选。"
    }

    fun copyCurrentResults() {
        if (filteredEntries.isEmpty()) {
            feedback = "当前筛选结果为空，未复制。"
            return
        }
        actionBusy = true
        scope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(
                        StringSelection(formatLogEntriesForExport(filteredEntries)),
                        null,
                    )
                }
                Result.success(Unit)
            } catch (error: Throwable) {
                Result.failure<Unit>(error)
            }
            feedback = result.fold(
                onSuccess = { "已复制当前结果：${filteredEntries.size} 行。" },
                onFailure = { "复制失败：${logDiagnostic(it)}" },
            )
            actionBusy = false
        }
    }

    fun exportCurrentResults() {
        if (filteredEntries.isEmpty()) {
            feedback = "当前筛选结果为空，未导出。"
            return
        }
        val destination = chooseExportFile(paths.logsDir)
        destination.fold(
            onSuccess = { file ->
                if (file == null) {
                    feedback = "已取消导出。"
                    return@fold
                }
                actionBusy = true
                scope.launch {
                    val result = try {
                        withContext(Dispatchers.IO) {
                            file.parentFile?.mkdirs()
                            file.writeText(formatLogEntriesForExport(filteredEntries), Charsets.UTF_8)
                        }
                        Result.success(Unit)
                    } catch (error: Throwable) {
                        Result.failure<Unit>(error)
                    }
                    feedback = result.fold(
                        onSuccess = { "已导出当前结果：${filteredEntries.size} 行 · ${file.absolutePath}" },
                        onFailure = { "导出失败：${logDiagnostic(it)}" },
                    )
                    actionBusy = false
                }
            },
            onFailure = { feedback = "导出失败：${logDiagnostic(it)}" },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(DesktopTokens.PagePadding),
        verticalArrangement = Arrangement.spacedBy(DesktopTokens.PageGap),
    ) {
        DesktopSectionCard(
            title = "日志工具栏",
            supportingText = "搜索和筛选只影响当前展示结果；复制、导出会严格使用筛选后的结果。",
        ) {
            OutlinedTextField(
                value = filter.query,
                onValueChange = { filter = filter.copy(query = it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("搜索日志") },
                placeholder = { Text("关键字、来源、级别、标签或原始内容") },
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterMenuButton(
                    label = filterButtonLabel("来源", filter.sourceIds.size),
                    onClick = { sourceMenuOpen = true },
                    modifier = Modifier.weight(1f),
                )
                DropdownMenu(expanded = sourceMenuOpen, onDismissRequest = { sourceMenuOpen = false }) {
                    sourceOptions.forEach { source ->
                        val checked = source.id in filter.sourceIds
                        DropdownMenuItem(
                            text = { Text(source.label) },
                            onClick = {
                                filter = filter.copy(sourceIds = toggleValue(filter.sourceIds, source.id))
                            },
                            leadingIcon = { Checkbox(checked = checked, onCheckedChange = null) },
                        )
                    }
                    if (sourceOptions.isEmpty()) DropdownMenuItem(text = { Text("暂无来源") }, onClick = { sourceMenuOpen = false })
                }
                FilterMenuButton(
                    label = filterButtonLabel("级别", filter.levels.size),
                    onClick = { levelMenuOpen = true },
                    modifier = Modifier.weight(1f),
                )
                DropdownMenu(expanded = levelMenuOpen, onDismissRequest = { levelMenuOpen = false }) {
                    levels.forEach { level ->
                        val checked = level in filter.levels
                        DropdownMenuItem(
                            text = { Text(level) },
                            onClick = { filter = filter.copy(levels = toggleValue(filter.levels, level)) },
                            leadingIcon = { Checkbox(checked = checked, onCheckedChange = null) },
                        )
                    }
                    if (levels.isEmpty()) DropdownMenuItem(text = { Text("暂无级别") }, onClick = { levelMenuOpen = false })
                }
                FilterMenuButton(
                    label = filterButtonLabel("标签", filter.tags.size),
                    onClick = { tagMenuOpen = true },
                    modifier = Modifier.weight(1f),
                )
                DropdownMenu(expanded = tagMenuOpen, onDismissRequest = { tagMenuOpen = false }) {
                    tags.forEach { tag ->
                        val checked = tag in filter.tags
                        DropdownMenuItem(
                            text = { Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            onClick = { filter = filter.copy(tags = toggleValue(filter.tags, tag)) },
                            leadingIcon = { Checkbox(checked = checked, onCheckedChange = null) },
                        )
                    }
                    if (tags.isEmpty()) DropdownMenuItem(text = { Text("暂无标签") }, onClick = { tagMenuOpen = false })
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DesktopActionButton(
                    label = if (refreshing) "刷新中…" else "刷新日志",
                    onClick = { refreshNonce++ },
                    enabled = !refreshing && !actionBusy,
                    style = DesktopActionButtonStyle.Tonal,
                    icon = DesktopIcons.Restart,
                )
                DesktopActionButton(
                    label = "复制当前结果",
                    onClick = ::copyCurrentResults,
                    enabled = !refreshing && !actionBusy,
                    style = DesktopActionButtonStyle.Outlined,
                    icon = DesktopIcons.Copy,
                )
                DesktopActionButton(
                    label = "导出当前结果",
                    onClick = ::exportCurrentResults,
                    enabled = !refreshing && !actionBusy,
                    style = DesktopActionButtonStyle.Outlined,
                    icon = DesktopIcons.Folder,
                )
                DesktopActionButton(
                    label = "清除筛选",
                    onClick = ::clearFilters,
                    enabled = !filter.isEmpty(),
                    style = DesktopActionButtonStyle.Outlined,
                    icon = DesktopIcons.Tools,
                )
            }
        }

        readError?.let { error ->
            DesktopSurface(color = MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DesktopIcon(DesktopIcons.Error, tint = MaterialTheme.colorScheme.error)
                    Text(error, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        feedback?.let { message ->
            DesktopSurface(color = MaterialTheme.colorScheme.primaryContainer) {
                Text(message, Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        DesktopSectionCard(
            title = "终端日志",
            supportingText = resultSummary(readResult, allEntries.size, filteredEntries.size, filter),
            trailingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (pendingNewCount > 0 && !terminalAtLatest) {
                        DesktopActionButton(
                            label = "新增 $pendingNewCount 条 · 查看最新",
                            onClick = { terminalAtLatest = true; pendingNewCount = 0; scrollToLatestKey++ },
                            style = DesktopActionButtonStyle.Tonal,
                        )
                    }
                    if (refreshing) CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                }
            },
        ) {
            if (readResult == null && refreshing) {
                DesktopEmptyState(title = "正在读取日志…", description = "首次读取不会清空页面，完成后会保留当前筛选条件。", icon = DesktopIcons.Activity)
            } else if (filteredEntries.isEmpty()) {
                DesktopEmptyState(
                    title = if (allEntries.isEmpty()) "暂无可显示日志" else "筛选结果为空",
                    description = if (allEntries.isEmpty()) "请查看下方各日志源状态；源不存在、空文件和读取失败会分别显示。" else "调整搜索关键字或筛选条件后重试。",
                    icon = if (allEntries.isEmpty()) DesktopIcons.Empty else DesktopIcons.Tools,
                )
            } else {
                val visibleEntries = filteredEntries.takeLast(MAX_VISIBLE_LOG_LINES)
                if (filteredEntries.size > visibleEntries.size) {
                    Text(
                        "终端仅展示最近 $MAX_VISIBLE_LOG_LINES 行；复制和导出包含全部 ${filteredEntries.size} 行。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                DesktopTerminalLog(
                    entries = visibleEntries,
                    followLatest = terminalAtLatest,
                    scrollToLatestKey = scrollToLatestKey,
                    onAtLatestChanged = { atLatest ->
                        terminalAtLatest = atLatest
                        if (atLatest) pendingNewCount = 0
                    },
                )
            }
        }

        DesktopSectionCard(
            title = "日志来源",
            supportingText = "每个来源单独报告读取状态，不会因单个文件缺失而掩盖其他日志。",
        ) {
            if (sourceResults.isEmpty()) {
                Text("尚未完成首次读取。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                sourceResults.forEachIndexed { index, source ->
                    if (index > 0) DesktopDivider()
                    LogSourceStatusRow(source)
                }
            }
            readResult?.diagnostics?.forEach { diagnostic ->
                Spacer(Modifier.height(6.dp))
                Text(diagnostic, style = MaterialTheme.typography.bodySmall, color = LocalDesktopThemePalette.current.warning.content)
            }
        }
    }
}

@Composable
private fun FilterMenuButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    DesktopActionButton(label = label, onClick = onClick, modifier = modifier, style = DesktopActionButtonStyle.Outlined, icon = DesktopIcons.Tools)
}

@Composable
private fun DesktopTerminalLog(
    entries: List<DesktopLogEntry>,
    followLatest: Boolean,
    scrollToLatestKey: Int,
    onAtLatestChanged: (Boolean) -> Unit,
) {
    val terminalScroll = rememberScrollState()
    LaunchedEffect(entries.size, scrollToLatestKey, followLatest) {
        if (followLatest || scrollToLatestKey > 0) {
            terminalScroll.scrollTo(terminalScroll.maxValue)
        }
    }
    LaunchedEffect(terminalScroll.value, terminalScroll.maxValue) {
        onAtLatestChanged(terminalScroll.maxValue == 0 || terminalScroll.value >= terminalScroll.maxValue - 8)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().height(420.dp),
        color = androidx.compose.ui.graphics.Color(0xFF101318),
        contentColor = androidx.compose.ui.graphics.Color(0xFFE7EAF0),
        shape = DesktopTokens.ItemShape,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(terminalScroll).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            entries.forEach { entry ->
                val color = logLevelColor(entry.level)
                val prefix = buildString {
                    append(displayLogTimestamp(entry.timestamp))
                    append("  ")
                    append(entry.sourceLabel)
                    entry.level?.let { append("  ").append(it) }
                    entry.tag?.let { append("  [").append(it).append(']') }
                    append("  ")
                }
                Text(
                    text = prefix + entry.message,
                    color = color,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    softWrap = true,
                )
            }
        }
    }
}

internal fun logLevelColorName(level: String?): String = when (level?.uppercase()) {
    "ERROR", "FATAL" -> "error"
    "WARN", "WARNING" -> "warning"
    "INFO" -> "normal"
    "DEBUG", "TRACE" -> "debug"
    else -> "unknown"
}

@Composable
private fun logLevelColor(level: String?): androidx.compose.ui.graphics.Color = when (logLevelColorName(level)) {
    "error" -> MaterialTheme.colorScheme.error
    "warning" -> LocalDesktopThemePalette.current.warning.content
    "normal" -> LocalDesktopThemePalette.current.success.content
    "debug" -> LocalDesktopThemePalette.current.info.content
    else -> androidx.compose.ui.graphics.Color(0xFFB5BAC5)
}

@Composable
private fun LogSourceStatusRow(result: DesktopLogSourceResult) {
    val (status, label, detail) = when (val sourceStatus = result.status) {
        DesktopLogSourceStatus.Missing -> Triple(DesktopStatus.Neutral, "未生成", "文件尚不存在：${result.source.file.absolutePath}")
        DesktopLogSourceStatus.Empty -> Triple(DesktopStatus.Neutral, "空文件", result.source.file.absolutePath)
        is DesktopLogSourceStatus.Readable -> Triple(
            if (sourceStatus.truncated) DesktopStatus.Warning else DesktopStatus.Success,
            if (sourceStatus.truncated) "已读取（已截取）" else "已读取",
            "${sourceStatus.lineCount} 行 · ${result.source.file.absolutePath}",
        )
        is DesktopLogSourceStatus.Failed -> Triple(DesktopStatus.Error, "读取失败", sourceStatus.reason)
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DesktopStatusBadge(status, label = label, compact = true)
            Text(result.source.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Text(result.source.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(detail, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun logLevelStatus(level: String): DesktopStatus = when (level.uppercase()) {
    "ERROR", "FATAL" -> DesktopStatus.Error
    "WARN", "WARNING" -> DesktopStatus.Warning
    "INFO" -> DesktopStatus.Success
    "DEBUG", "TRACE" -> DesktopStatus.Info
    else -> DesktopStatus.Neutral
}

private fun filterButtonLabel(name: String, count: Int): String = if (count == 0) name else "$name · $count"

private fun <T> toggleValue(values: Set<T>, value: T): Set<T> = if (value in values) values - value else values + value

private fun resultSummary(
    result: DesktopLogReadResult?,
    total: Int,
    filtered: Int,
    filter: DesktopLogFilter,
): String {
    val time = result?.readAt?.atZone(java.time.ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    val criteria = buildList {
        if (filter.query.isNotBlank()) add("关键字：${filter.query.trim()}")
        if (filter.sourceIds.isNotEmpty()) add("来源 ${filter.sourceIds.size}")
        if (filter.levels.isNotEmpty()) add("级别 ${filter.levels.size}")
        if (filter.tags.isNotEmpty()) add("标签 ${filter.tags.size}")
    }
    return buildString {
        append("匹配 $filtered / $total 行")
        if (criteria.isNotEmpty()) append(" · ").append(criteria.joinToString(" · "))
        if (time != null) append(" · 最近读取 $time")
    }
}

private fun chooseExportFile(defaultDirectory: File): Result<File?> = try {
    val dialog = FileDialog(null as Frame?, "导出当前日志结果", FileDialog.SAVE)
    val directory = if (defaultDirectory.isDirectory) defaultDirectory else defaultDirectory.parentFile
    if (directory != null) dialog.directory = directory.absolutePath
    dialog.file = "danmu-api-logs-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.log"
    dialog.isVisible = true
    val selected = dialog.file ?: return Result.success(null)
    Result.success(File(dialog.directory ?: defaultDirectory.absolutePath, selected))
} catch (error: Throwable) {
    Result.failure(error)
}

private fun logDiagnostic(error: Throwable): String = error.message?.trim()?.takeIf { it.isNotEmpty() } ?: error::class.java.simpleName
