package com.example.danmuapiapp.ui.screen.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.data.service.*
import com.example.danmuapiapp.ui.component.SettingsPageHeader
import com.example.danmuapiapp.ui.component.probeGreen
import com.example.danmuapiapp.ui.screen.settings.routeTitle
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SourceProbeScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var catalog by remember { mutableStateOf(ProxySourceCatalog(emptyList(), emptySet())) }
    var settings by remember { mutableStateOf(AppOutboundSettings()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("全部") }
    val preferences = remember(context) { context.getSharedPreferences("source_probe_prefs", android.content.Context.MODE_PRIVATE) }
    var allSources by remember { mutableStateOf(preferences.getBoolean("show_all_sources", false)) }
    var showSettings by remember { mutableStateOf(false) }
    val results = remember { mutableStateMapOf<String, SourceProbeResult>() }
    val pending = remember { mutableStateListOf<String>() }
    val running = job?.isActive == true
    suspend fun load() {
        loading = true; error = null
        try {
            settings = withContext(Dispatchers.IO) { AppOutboundSettingsStore.read(context) }
            val local = withContext(Dispatchers.IO) { runCatching { AppProxySourceCatalog.load(context) }.getOrNull() }
            try { catalog = SourceProbeService.catalog(context).copy(legacyProxied = local?.legacyProxied.orEmpty()) }
            catch (cancel: CancellationException) { throw cancel }
            catch (e: Exception) { if (local != null) catalog = local; error = "服务未连接。启动服务后即可探测。" }
            results.clear()
        } finally { loading = false }
    }
    fun probe(sources: List<String>) {
        if (job?.isActive == true || sources.isEmpty()) return
        job = scope.launch {
            pending.addAll(sources)
            sources.forEach { results.remove(it) }
            val limit = Semaphore(3)
            try {
                sources.map { source -> launch {
                    limit.withPermit {
                        try { results[source] = SourceProbeService.probe(context, source) }
                        catch (cancel: CancellationException) { throw cancel }
                        catch (e: Exception) {
                            results[source] = SourceProbeResult(source, "failed", false, null,
                                routeTitle(settings.sourceRoute(source, catalog), settings.proxy, source), "", "未确认", "探测未完成", "请确认服务正在运行，再重试", SourceProbeService.externalNetwork(context))
                        } finally { pending.remove(source) }
                    }
                } }.joinAll()
            } finally { pending.clear(); job = null }
        }
    }
    LaunchedEffect(context) { load() }
    val sources = catalog.visibleSources(allSources)
    val scopedResults = sources.mapNotNull { results[it] }
    val ok = scopedResults.count { it.status == "ok" || it.status == "local" }
    val restricted = scopedResults.count { it.status == "restricted" }
    val failed = scopedResults.count { it.status == "failed" }
    val green = probeGreen()
    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).padding(top = 16.dp)) {
            SettingsPageHeader(title = "来源探测", subtitle = "按各来源当前线路检查连接", onBack = onBack) {
                Box {
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Rounded.Settings, "来源显示设置") }
                    DropdownMenu(expanded = showSettings, onDismissRequest = { showSettings = false }) {
                        listOf(false to "只显示启用", true to "显示全部来源").forEach { (showAll, label) ->
                            DropdownMenuItem(text = { Text(label) }, leadingIcon = { RadioButton(selected = allSources == showAll, onClick = null) }, onClick = {
                                allSources = showAll
                                preferences.edit().putBoolean("show_all_sources", showAll).apply()
                                showSettings = false
                            })
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Radar, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(if (running) "正在探测 · 剩余 ${pending.size}" else if (allSources) "${sources.size} 个来源 · 全部" else "${sources.size} 个已启用来源", style = MaterialTheme.typography.titleMedium)
                    Text(if (running) "每个来源沿用自己的连接线路" else "点击来源展开详情", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(enabled = !running && !loading, onClick = { scope.launch { load() } }) { Icon(Icons.Rounded.Refresh, "刷新来源") }
                FilledTonalIconButton(enabled = !loading && (running || sources.isNotEmpty()), onClick = { if (running) job?.cancel() else probe(sources) }) {
                    Icon(if (running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, if (running) "停止探测" else if (allSources) "探测全部来源" else "探测全部已启用来源")
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ProbeMetric("已响应", ok, green, Modifier.weight(1f))
                ProbeMetric("接口受限", restricted, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                ProbeMetric("未响应", failed, green, Modifier.weight(1f))
            }
            if (running || loading) LinearProgressIndicator(Modifier.fillMaxWidth()) else HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            error?.let { Text(it, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(top = 12.dp), placeholder = { Text("查找来源") }, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(20.dp)) }, shape = RoundedCornerShape(16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("全部", "已响应", "受限", "未响应", "未配置", "未探测").forEach { name ->
                    FilterChip(selected = filter == name, onClick = { filter = name }, label = { Text(name) })
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("来源 / 连接线路", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("状态 · 耗时", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val visible = sources.filter { source ->
                (AppProxySourceCatalog.label(source).contains(query, true) || source.contains(query, true)) && when (filter) {
                    "已响应" -> results[source]?.status in listOf("ok", "local")
                    "受限" -> results[source]?.status == "restricted"
                    "未响应" -> results[source]?.status == "failed"
                    "未配置" -> results[source]?.configurationRequired == true
                    "未探测" -> results[source] == null
                    else -> true
                }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
                if (visible.isEmpty() && !loading) item {
                    Text(if (sources.isEmpty()) { if (allSources) "尚未读取到来源，请安装核心后刷新。" else "尚未启用来源，可在标题右侧设置中显示全部，或到配置管理中启用。" } else "没有符合当前筛选的来源", Modifier.padding(vertical = 28.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(visible, key = { it }) { source ->
                    val result = results[source]
                    val probing = source in pending
                    val color = when { probing -> MaterialTheme.colorScheme.primary; result?.status == "failed" -> green; result?.status == "restricted" -> MaterialTheme.colorScheme.tertiary; result?.connected == true -> green; else -> MaterialTheme.colorScheme.outline }
                    val status = when {
                        probing -> "探测中"
                        result?.configurationRequired == true && result.connected -> "已响应 · API Key 未配置"
                        result?.configurationRequired == true -> "未配置"
                        else -> when (result?.status) { "ok" -> "已响应"; "restricted" -> "接口受限"; "local" -> "本地"; "unsupported" -> "未确认"; "failed" -> "未响应"; else -> "未探测" }
                    }
                    val route = result?.route ?: routeTitle(settings.sourceRoute(source, catalog), settings.proxy, source)
                    Column {
                        Row(Modifier.fillMaxWidth().clickable { expanded = if (expanded == source) null else source }.padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).background(color, CircleShape))
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(AppProxySourceCatalog.label(source), Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (allSources && source !in catalog.enabled) Text("未启用", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(route, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (result != null && source != "local") Text(
                                    if (result.regionEvidence == "same-domain") "${result.region} · 同域检测" else "出口地区未确认",
                                    style = MaterialTheme.typography.labelSmall, color = if (result.regionEvidence == "same-domain") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(Modifier.widthIn(max = 144.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(status, style = MaterialTheme.typography.labelMedium, color = color, textAlign = TextAlign.End)
                                Text(if (probing) "…" else result?.durationMs?.let { "$it ms" } ?: "—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(if (expanded == source) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (expanded == source) "收起详情" else "展开详情", Modifier.padding(start = 6.dp).size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AnimatedVisibility(expanded == source) {
                            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(result?.reason ?: "点击下方探测，只检查这个来源。", style = MaterialTheme.typography.bodyMedium)
                                result?.let {
                                    if (it.probeHost.isNotBlank()) ProbeDetail("目标域名", it.probeHost)
                                    it.httpStatus?.let { code -> ProbeDetail("接口响应", "HTTP $code") }
                                    ProbeDetail("App 线路", when (it.routeType) { "app" -> "本 App 配置的代理节点"; "core" -> "核心配置的代理"; "enhanced" -> "增强直连"; "direct" -> "普通直连"; else -> it.route })
                                    if (source != "local") {
                                        ProbeDetail("外部网络", it.external)
                                        ProbeDetail("出口地区", if (it.regionEvidence == "same-domain") "${it.region} · ${it.regionHost}" else "未确认")
                                        Text(it.regionNote.ifBlank { "未获取到源站的出口信息。" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    ProbeDetail("检测时间", SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it.testedAt)))
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(enabled = !running, onClick = { probe(listOf(source)) }) { Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(if (result == null) "探测此来源" else "重新探测") }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                    }
                }
                item { Text("同域检测表示该域名本次诊断连接的出口归属地。一个来源可能访问多个域名；外部 VPN 信号不能证明这个来源实际经过了代理。", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable private fun ProbeMetric(label: String, count: Int, color: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(count.toString().padStart(2, '0'), style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun ProbeDetail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
