package com.example.danmuapiapp.ui.compat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.*
import com.example.danmuapiapp.ui.component.remoteFocusHighlight
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun compatWebUrl(runtime: RuntimeState, section: String, adminToken: String = ""): String {
    if (runtime.status != ServiceStatus.Running) return ""
    val base = (runtime.lanUrl.ifBlank { runtime.lanIpv6Url }).toHttpUrlOrNull() ?: return ""
    val token = if (section == "env") adminToken else runtime.token.trim().trim('/')
    if (section == "env" && token.isBlank()) return ""
    val builder = base.newBuilder().encodedPath("/").query(null).fragment(null)
    if (token.isNotBlank()) builder.addPathSegment(token)
    return builder.addQueryParameter("app_section", section).build().toString()
}

internal fun filterCompatLogs(logs: List<LogEntry>, level: String, source: String, query: String): List<LogEntry> =
    logs.filter { entry ->
        (level.isBlank() || entry.level.name == level) &&
            (source.isBlank() || LogTagClassifier.matchesSource(entry, source)) &&
            (query.isBlank() || "${entry.message} ${entry.tag} ${entry.category} ${entry.tags.joinToString(" ")} ${entry.source.label}"
                .contains(query.trim(), ignoreCase = true))
    }

@Composable
internal fun CompatLogsPage(logs: List<LogEntry>, runtime: RuntimeState, onRefresh: () -> Unit,
    listState: LazyListState, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf("") }
    var follow by rememberSaveable { mutableStateOf(true) }
    var showFilters by remember { mutableStateOf(false) }
    var showBrowser by remember { mutableStateOf(false) }
    val sources = remember(logs) { LogTagClassifier.sortTags(logs.map(LogTagClassifier::sourceFilterFor)) }
    val filtered = remember(logs, level, source, query) { filterCompatLogs(logs, level, source, query) }
    val dragging by listState.interactionSource.collectIsDraggedAsState()
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { 84.dp.toPx() }
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val webUrl = compatWebUrl(runtime, "logs")
    LaunchedEffect(Unit) { follow = true }
    LaunchedEffect(dragging) { if (dragging) follow = false }
    LaunchedEffect(filtered.size, filtered.lastOrNull(), follow) {
        if (follow && !dragging && filtered.isNotEmpty()) listState.scrollToItem(filtered.lastIndex)
    }
    BoxWithConstraints(modifier) {
        val toolbarHeight = (maxHeight * 0.5f).coerceAtLeast(72.dp)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth().heightIn(max = toolbarHeight)
                .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("搜索日志") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "清空搜索") } })
                CompatActions {
                    CompatButton("筛选${if (level.isNotBlank() || source.isNotBlank()) " · 已启用" else ""}", { showFilters = true },
                        icon = Icons.Rounded.FilterList)
                    CompatButton("刷新", onRefresh, icon = Icons.Rounded.Refresh)
                    CompatButton(if (follow) "暂停追踪" else "追踪最新", {
                        follow = !follow
                    }, icon = Icons.Rounded.VerticalAlignBottom, tone = if (follow) CompatActionTone.Primary else CompatActionTone.Neutral)
                    CompatButton("浏览器查看", { showBrowser = true }, icon = Icons.Rounded.QrCode2, enabled = webUrl.isNotBlank())
                }
            }
            Surface(Modifier.weight(1f).fillMaxWidth(), color = Color(0xFF0B111B), shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                val terminalShape = RoundedCornerShape(16.dp)
                LazyColumn(Modifier.fillMaxSize().remoteFocusHighlight(terminalShape)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && ((event.key == Key.DirectionUp && listState.canScrollBackward) ||
                            (event.key == Key.DirectionDown && listState.canScrollForward))) {
                            follow = false
                            scope.launch { listState.scrollBy(if (event.key == Key.DirectionUp) -step else step) }
                            true
                        } else false
                    }.focusable(), state = listState, contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (filtered.isEmpty()) item {
                        Text(if (logs.isEmpty()) "等待日志输出…" else "没有符合筛选条件的日志。",
                            color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                    }
                    items(filtered) { entry ->
                        val color = when (entry.level) {
                            LogLevel.Error -> Color(0xFFFF9CA6)
                            LogLevel.Warn -> Color(0xFFFFD18B)
                            LogLevel.Info -> Color(0xFFD6E2EF)
                        }
                        Text("${formatter.format(Date(entry.timestamp))} [${entry.level.name.uppercase()}] [${entry.source.label}] ${entry.message}",
                            color = color, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    }
                }
            }
            Text("${filtered.size} / ${logs.size} 条 · ${if (follow) "正在追踪最新日志" else "已暂停，可上下翻阅"}" +
                if (source.isNotBlank()) " · ${LogTagClassifier.labelFor(source)}" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    }
    if (showFilters) CompatDialog("日志筛选", { showFilters = false }, "完成", { showFilters = false }) {
        Text("日志级别", style = MaterialTheme.typography.titleMedium)
        CompatActions {
            (listOf("" to "全部") + LogLevel.entries.map { it.name to when (it) {
                LogLevel.Info -> "信息"; LogLevel.Warn -> "警告"; LogLevel.Error -> "错误"
            } }).forEach { (key, label) -> CompatButton(label, { level = key },
                tone = if (level == key) CompatActionTone.Primary else CompatActionTone.Neutral) }
        }
        Text("来源标签", style = MaterialTheme.typography.titleMedium)
        CompatActions {
            CompatButton("全部标签", { source = "" }, tone = if (source.isBlank()) CompatActionTone.Primary else CompatActionTone.Neutral)
            sources.forEach { tag -> CompatButton(LogTagClassifier.labelFor(tag), { source = tag },
                tone = if (source == tag) CompatActionTone.Primary else CompatActionTone.Neutral) }
        }
    }
    if (showBrowser) CompatBrowserDialog("浏览器查看日志", webUrl, { showBrowser = false },
        "手机连接同一局域网后扫码，打开核心网页日志页。网页显示核心日志，App 启动日志在本页查看。")
}

@Composable
internal fun CompatBrowserDialog(title: String, url: String, onDismiss: () -> Unit, description: String) {
    CompatDialog(title, onDismiss, "关闭", onDismiss, cancelText = "返回") {
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (url.isNotBlank()) {
            CompatQrCode(url, title, 176)
            CompatCopyButton("复制访问链接", url)
            Text("链接含访问授权，请仅分享给自己的设备。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary)
        } else Text("请先启动服务并连接局域网。", color = MaterialTheme.colorScheme.tertiary)
    }
}
