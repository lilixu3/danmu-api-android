package com.example.danmuapiapp.ui.screen.settings

import com.example.danmuapiapp.ui.component.AppToast

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.data.network.AppProxyNetwork
import com.example.danmuapiapp.data.service.*
import com.example.danmuapiapp.ui.component.*
import com.example.danmuapiapp.ui.common.ConnectivityChecks
import com.example.danmuapiapp.ui.common.rememberConnectivityChecks
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxySettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(0) }
    var settings by remember { mutableStateOf(AppOutboundSettings()) }
    val config = settings.proxy
    var catalog by remember { mutableStateOf(ProxySourceCatalog(emptyList(), emptySet())) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var allSources by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AppProxyNode?>(null) }
    var routeSource by rememberSaveable { mutableStateOf<String?>(null) }
    var showImport by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var runtimeNotice by remember { mutableStateOf<String?>(null) }
    val sourceChecks = rememberConnectivityChecks(settings, catalog)
    val nodeChecks = rememberConnectivityChecks(config.nodes)
    fun probeNodes(nodes: List<AppProxyNode>) {
        val byId = nodes.associateBy { it.id }
        nodeChecks.start(nodes.map { it.id }) { id -> AppProxyNetwork.probeNode(byId.getValue(id)) }
    }
    fun notify(message: String) { AppToast.show(message) }
    fun save(next: AppProxySettings) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                settings = withContext(Dispatchers.IO) {
                    AppOutboundSettingsStore.saveProxy(context, next)
                    AppOutboundSettingsStore.read(context)
                }
            } catch (e: Exception) { notify(e.message ?: "保存失败") }
            finally { busy = false }
        }
    }
    suspend fun load() {
        loading = true
        try {
            settings = withContext(Dispatchers.IO) { AppOutboundSettingsStore.read(context) }
            runtimeNotice = NetworkRuntimeStatus.notice(context)
            catalog = withContext(Dispatchers.IO) { AppProxySourceCatalog.load(context) }
        } catch (e: Exception) { notify("来源目录暂未读取，可安装核心后刷新") }
        finally { loading = false }
    }
    LaunchedEffect(context) { load() }
    val sourceIds = (catalog.sources + catalog.enabled + settings.proxy.rules.keys).distinct().filterNot { it == "github" }
    fun route(source: String) = settings.sourceRoute(source, catalog)
    val visibleSources = sourceIds.filter { (allSources || it in catalog.enabled) &&
        (AppProxySourceCatalog.label(it).contains(search, true) || it.contains(search, true)) }
        .sortedBy { if (it in catalog.enabled) 0 else 1 }
    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (tab == 1) FloatingActionButton(onClick = { editing = AppProxyNode() }) { Icon(Icons.Rounded.Add, "添加节点") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).padding(top = 16.dp)) {
            SettingsPageHeader(title = "网络设置", subtitle = "为弹幕来源选择线路与代理节点", onBack = onBack) {
                IconButton(enabled = !loading && !busy, onClick = { scope.launch { load() } }) { Icon(Icons.Rounded.Refresh, "刷新来源与线路") }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(18.dp)).padding(4.dp)) {
                listOf("来源线路", "代理节点").forEachIndexed { index, label ->
                    Surface(onClick = { tab = index; search = "" }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                        color = if (tab == index) MaterialTheme.colorScheme.primaryContainer else Color.Transparent) {
                        Text(label, Modifier.padding(vertical = 12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge, color = if (tab == index) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            runtimeNotice?.let { message ->
                Surface(color=MaterialTheme.colorScheme.tertiaryContainer,shape=RoundedCornerShape(14.dp)) {
                    Text(message,Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onTertiaryContainer)
                }
                Spacer(Modifier.height(8.dp))
            }
            if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 100.dp)) {
                item {
                    OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().padding(vertical = 6.dp), singleLine = true,
                        placeholder = { Text(if (tab == 0) "查找来源" else "查找节点") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(20.dp)) }, shape = RoundedCornerShape(16.dp))
                }
                if (tab == 0) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                            Text("${catalog.enabled.size} 已启用  ·  ${sourceIds.count { route(it) == "enhanced" }} 增强  ·  ${sourceIds.count { route(it).startsWith("node:") }} 代理",
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                TextButton(enabled = !loading && !busy && (sourceChecks.running || visibleSources.isNotEmpty()), onClick = {
                                    if (sourceChecks.running) sourceChecks.cancel()
                                    else sourceChecks.start(visibleSources) { SourceProbeService.connectivity(context, it) }
                                }) {
                                    Icon(if (sourceChecks.running) Icons.Rounded.Stop else Icons.Rounded.NetworkCheck, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (sourceChecks.running) "停止检测" else "连通性检测")
                                }
                                TextButton(onClick = { allSources = !allSources }) { Text(if (allSources) "只看已启用" else "显示全部") }
                            }
                        }
                    }
                    if (visibleSources.isEmpty()) item { ProxyNote(if (search.isBlank()) "尚未启用来源，请在配置管理中选择来源。" else "没有匹配的来源") }
                    items(visibleSources, key = { it }) { source ->
                        SourceRouteRow(source, route(source), config, source in catalog.enabled, enabled = !loading && !busy,
                            result = sourceChecks.results[source], probing = source in sourceChecks.pending) { routeSource = source }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                    }
                    item { ProxyNote("检测当前列表中的来源，沿用各自线路；延迟表示接口响应耗时。来源的启用与停用在配置管理中设置。") }
                } else if (tab == 1) {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${config.nodes.size} 个节点", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                            TextButton(onClick = { showImport = true }) { Icon(Icons.Rounded.ContentPaste, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("粘贴导入") }
                        }
                    }
                    val nodes = config.nodes.filter { it.name.contains(search, true) || it.host.contains(search, true) }
                    if (nodes.isEmpty()) item { ProxyEmpty("准备一个代理出口", "支持 HTTP、HTTPS、SOCKS5。添加后，在来源线路中选择它。", Icons.Rounded.Dns) }
                    items(nodes, key = { it.id }) { node ->
                        Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { editing = node }.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            ProxyIcon(Icons.Rounded.Dns)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(node.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    ConnectivityStatus(nodeChecks.results[node.id], node.id in nodeChecks.pending)
                                }
                                Text("${node.type.uppercase()} · ${node.host}:${node.port}", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val bound = sourceIds.filter { route(it) == "node:${node.id}" }
                                Text(if (bound.isEmpty()) "尚未分配来源" else bound.joinToString("、") { AppProxySourceCatalog.label(it) },
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(enabled = !busy && !nodeChecks.running, onClick = { probeNodes(listOf(node)) }) { Icon(Icons.Rounded.Speed, "测试节点 ${node.name}") }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
                    }
                }
            }
        }
    }
    editing?.let { node -> ProxyNodeEditor(node, onDismiss = { editing = null }, onDelete = { save(config.copy(nodes = config.nodes.filter { it.id != node.id })); editing = null },
        onSave = { next -> save(config.copy(nodes = config.nodes.filter { it.id != node.id } + next)); editing = null }) }
    routeSource?.let { source ->
        SourceRouteEditor(source, route(source), config, busy, nodeChecks,
            onProbeNodes = { nodes -> probeNodes(nodes) }, onDismiss = { if (!busy) routeSource = null },
            onAddNode = { routeSource = null; tab = 1; editing = AppProxyNode() }, onAdvanced = { showAdvanced = true }) { selected ->
            busy = true
            scope.launch {
                try {
                    settings = withContext(Dispatchers.IO) { AppOutboundSettingsStore.saveSourceRoute(context, source, selected) }
                    routeSource = null; notify(if(source != "github" && runtimeNotice != null) "线路已保存，请按页面提示启动或重启服务" else "${AppProxySourceCatalog.label(source)}已使用${routeTitle(selected, settings.proxy, source)}")
                } catch (e: Exception) { notify(e.message ?: "线路保存失败") }
                finally { busy = false }
            }
        }
    }
    if (showImport) ProxyTextImport(onDismiss = { showImport = false }) { text ->
        val nodes = ProxyNodeLinks.parse(text)
        save(config.copy(nodes = config.nodes + nodes))
        showImport = false; notify("导入 ${nodes.size} 个节点")
    }

    if (showAdvanced) ConnectionParametersDialog(settings, onDismiss = { showAdvanced = false }) { protocol, doh, timeout ->
        scope.launch {
            try {
                settings = withContext(Dispatchers.IO) {
                    val next = AppOutboundSettingsStore.read(context).copy(httpVersion = protocol, dohUrl = doh, connectTimeoutMs = timeout)
                    AppOutboundSettingsStore.save(context, next); next
                }
                showAdvanced = false
            } catch (e: Exception) { notify(e.message ?: "保存失败") }
        }
    }
}

internal fun routeTitle(route: String, config: AppProxySettings, source: String): String = when (route) {
    "inherit" -> AppProxySourceCatalog.inheritLabel(source)
    "direct" -> "设备当前网络"
    "local" -> "本地文件"; "legacy" -> "沿用核心配置 · 核心代理"; "mirror" -> "下载镜像"; else -> config.label(route)
}

@Composable
private fun SourceRouteRow(source: String, route: String, config: AppProxySettings, active: Boolean, enabled: Boolean,
    result: AppOutboundConnectivityResult?, probing: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled && source != "local", onClick = onClick).padding(vertical = 17.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .65f)) {
            Text(AppProxySourceCatalog.label(source).take(1), Modifier.padding(horizontal = 13.dp, vertical = 10.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(AppProxySourceCatalog.label(source), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp))
                if (result != null || probing) ConnectivityStatus(result, probing)
                else Text(if (source == "github") "更新与下载" else if (active) "已启用" else "未启用", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(if (route.startsWith("node:")) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(routeTitle(route, config, source), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (source != "local") Icon(Icons.Rounded.ArrowForward, "更换线路", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SourceRouteEditor(source: String, initial: String, config: AppProxySettings, busy: Boolean, nodeChecks: ConnectivityChecks,
    onProbeNodes: (List<AppProxyNode>) -> Unit, onDismiss: () -> Unit, onAddNode: () -> Unit, onAdvanced: () -> Unit, onSave: (String) -> Unit) {
    var selected by remember(source) { mutableStateOf(if (initial in listOf("legacy", "mirror")) "inherit" else initial) }
    var query by remember { mutableStateOf("") }
    val visibleNodes = config.nodes.filter { it.name.contains(query, true) || it.host.contains(query, true) }
    val valid = selected in listOf("inherit", "enhanced", "direct") || config.nodes.any { selected == "node:${it.id}" }
    AppDialog(onDismissRequest = onDismiss, style = AppDialogStyle.Selection, scrollContent = false,
        title = { Text(AppProxySourceCatalog.label(source)) }, text = {
        Column(Modifier.fillMaxWidth()) {
            Text("这条来源只使用下面的一条线路", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.Smartphone, null, Modifier.size(24.dp))
                    HorizontalDivider(Modifier.width(20.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(routeTitle(selected, config, source), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(if (selected.startsWith("node:")) "通过此节点访问" else if (selected == "direct") "绕过核心和 App 代理，使用设备默认网络" else if (selected == "enhanced") "优化直连，不更换出口地区" else if (source in AppProxySourceCatalog.coreProxySources) "按核心配置访问" else if (source == "github") "沿用下载与更新线路" else "使用设备当前网络", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.ArrowForward, null, Modifier.size(18.dp))
                    Icon(Icons.Rounded.Public, null, Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { RouteMethod(AppProxySourceCatalog.inheritLabel(source), if (source == "github") "沿用下载与更新设置中的线路" else if (source in AppProxySourceCatalog.coreProxySources) "使用核心代理设置；未配置时使用设备当前网络" else "通过设备当前网络访问此来源", Icons.Rounded.Route, selected == "inherit", !busy) { selected = "inherit" } }
                if (source in AppOutboundSettings.supportedSources || source == "github") item {
                    RouteMethod("增强直连", if (source == "github") "优化 GitHub 域名解析，仍使用当前出口" else "优化域名解析与安全连接，仍使用当前出口", Icons.Rounded.Bolt, selected == "enhanced", !busy) { selected = "enhanced" }
                }
                if (source in AppProxySourceCatalog.coreProxySources) item {
                    RouteMethod("设备当前网络", "绕过核心和 App 代理；设备 VPN 或路由策略仍可能影响连接", Icons.Rounded.Smartphone, selected == "direct", !busy) { selected = "direct" }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("通过代理节点", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        TextButton(enabled = !busy && (nodeChecks.running || visibleNodes.isNotEmpty()), onClick = {
                            if (nodeChecks.running) nodeChecks.cancel() else onProbeNodes(visibleNodes)
                        }) { Text(if (nodeChecks.running) "停止检测" else "连通性检测") }
                        TextButton(enabled = !busy, onClick = onAddNode) { Text("添加节点") }
                    }
                }
                if (config.nodes.isNotEmpty()) item { Text("检测节点到测试站的 HTTPS 连接；来源能否访问可在保存线路后检测。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (config.nodes.isEmpty()) item { Text("还没有节点。添加后即可为这个来源指定出口。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (config.nodes.size > 5) item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("搜索节点") }, singleLine = true) }
                items(visibleNodes, key = { it.id }) { node ->
                    RouteMethod(node.name, "${node.type.uppercase()} · ${node.host}:${node.port}", Icons.Rounded.Dns, selected == "node:${node.id}", !busy,
                        result = nodeChecks.results[node.id], probing = node.id in nodeChecks.pending) { selected = "node:${node.id}" }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(if (source in AppProxySourceCatalog.coreProxySources) "默认沿用核心代理配置；可单独选择设备当前网络或指定 App 节点。" else "选择 App 代理节点时优先使用该节点；未指定节点时使用该来源的默认线路。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selected == "enhanced" && source != "github") IconButton(enabled = !busy, onClick = onAdvanced) { Icon(Icons.Rounded.Tune, "增强直连连接参数") }
                Button(enabled = valid && !busy, onClick = { onSave(selected) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (busy) "正在保存…" else "使用这条线路") }
            }
        }
    })
}

@Composable
private fun RouteMethod(title: String, description: String, icon: ImageVector, selected: Boolean, enabled: Boolean,
    result: AppOutboundConnectivityResult? = null, probing: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ConnectivityStatus(result, probing)
                }
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (selected) Icon(Icons.Rounded.CheckCircle, "已选线路", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ConnectionParametersDialog(settings: AppOutboundSettings, onDismiss: () -> Unit, onSave: (String, String, Int) -> Unit) {
    var protocol by remember { mutableStateOf(settings.httpVersion) }
    var doh by remember { mutableStateOf(settings.dohUrl) }
    var timeout by remember { mutableStateOf(settings.connectTimeoutMs.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    AppDialog(onDismissRequest = onDismiss, title = { Text("增强直连参数") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("所有使用增强直连的来源共享这些连接参数。", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("auto", "h2", "h3").forEach { p ->
                FilterChip(selected = protocol == p, onClick = { protocol = p }, label = { Text(if (p == "auto") "自动" else p.uppercase()) })
            } }
            Text("强制 H3 失败时不会回退。GitHub 下载仅使用下方的解析与超时设置。", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(doh, { doh = it }, label = { Text("DoH 地址（留空使用内置策略）") }, singleLine = true)
            OutlinedTextField(timeout, { timeout = it.filter(Char::isDigit) }, label = { Text("连接超时（毫秒）") }, singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        runCatching { settings.copy(httpVersion = protocol, dohUrl = doh.trim(), connectTimeoutMs = timeout.toIntOrNull() ?: 0).validate() }
            .onSuccess { onSave(it.httpVersion, it.dohUrl, it.connectTimeoutMs) }.onFailure { error = it.message }
    }) { Text("保存参数") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable internal fun ProxyBadge(text: String) {
    Surface(color=MaterialTheme.colorScheme.primary.copy(alpha=.1f),shape=RoundedCornerShape(9.dp)) {
        Text(text,Modifier.padding(horizontal=9.dp,vertical=5.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
    }
}
@Composable internal fun ProxyIcon(icon: ImageVector) {
    Surface(color=MaterialTheme.colorScheme.primary.copy(alpha=.1f),shape=RoundedCornerShape(14.dp)) {Icon(icon,null,Modifier.padding(12.dp).size(23.dp),tint=MaterialTheme.colorScheme.primary)}
}
@Composable internal fun ProxyNote(text: String) {Text(text,Modifier.padding(horizontal=6.dp,vertical=8.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
@Composable internal fun ProxyEmpty(title: String,subtitle: String,icon: ImageVector) {
    AppGlassSurface(shape=RoundedCornerShape(24.dp)) {Column(Modifier.fillMaxWidth().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)) {ProxyIcon(icon);Text(title,style=MaterialTheme.typography.titleMedium);Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
}

@Composable internal fun ProxyRoutePicker(title: String,config: AppProxySettings,selected: String,allowEnhanced: Boolean=false,source: String="",onDismiss: () -> Unit,onSelect: (String)->Unit) {
    var search by remember {mutableStateOf("")}
    AppDialog(onDismissRequest=onDismiss,style=AppDialogStyle.Selection,scrollContent=false,title={Text(title)},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(search,{search=it},placeholder={Text("搜索节点")},singleLine=true,modifier=Modifier.fillMaxWidth())
            val options=listOf("inherit" to AppProxySourceCatalog.inheritLabel(source))+(if(allowEnhanced)listOf("enhanced" to "增强直连") else emptyList())+config.nodes.map {"node:${it.id}" to it.name}
            LazyColumn(Modifier.heightIn(max=360.dp)) {items(options.filter {it.second.contains(search,true)},key={it.first}) {(key,label) ->
                Surface(onClick={onSelect(key)},shape=RoundedCornerShape(14.dp),color=if(selected==key)MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent) {
                    Row(Modifier.fillMaxWidth().padding(vertical=12.dp,horizontal=10.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(if(key.startsWith("node:"))Icons.Rounded.Dns else Icons.Rounded.Route,null,Modifier.size(21.dp))
                        Text(label,Modifier.weight(1f).padding(horizontal=12.dp),style=MaterialTheme.typography.bodyLarge)
                        if(selected==key)Icon(Icons.Rounded.Check,null,tint=MaterialTheme.colorScheme.primary)
                    }
                }
            }}
            if(config.nodes.isEmpty())Text("添加代理节点后，可在这里指定出口。",style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={},dismissButton={AppGlassButton(onClick=onDismiss){Text("关闭")}})
}

@Composable internal fun ProxyNodeEditor(node: AppProxyNode,onDismiss: ()->Unit,onDelete: ()->Unit,onSave: (AppProxyNode)->Unit) {
    var name by remember(node.id) { mutableStateOf(node.name) }
    var host by remember(node.id) { mutableStateOf(node.host) }
    var port by remember(node.id) { mutableStateOf(node.port.toString()) }
    var type by remember(node.id) { mutableStateOf(node.type) }
    var user by remember(node.id) { mutableStateOf(node.username) }
    var pass by remember(node.id) { mutableStateOf(node.password) }
    var tls by remember(node.id) { mutableStateOf(node.tls) }
    var serverName by remember(node.id) { mutableStateOf(node.tlsServerName) }
    var insecure by remember(node.id) { mutableStateOf(node.skipCertVerify) }
    var advanced by remember(node.id) { mutableStateOf(node.tls || serverName.isNotBlank() || insecure) }
    var error by remember { mutableStateOf<String?>(null) }
    val usesTls = type == "https" || (type == "socks5" && tls)
    AppDialog(onDismissRequest=onDismiss,style=AppDialogStyle.Form,title={Text(if(node.host.isBlank())"添加代理节点" else "编辑代理节点")},text={
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {AppProxyNode.supportedTypes.forEach {p->
                FilterChip(selected=type==p,onClick={type=p},label={Text(p.uppercase())})
            }}
            OutlinedTextField(name,{name=it},label={Text("节点名称")},singleLine=true)
            OutlinedTextField(host,{host=it},label={Text("服务器地址")},supportingText={Text("可填其他代理客户端提供的本机地址，如 127.0.0.1")},singleLine=true)
            OutlinedTextField(port,{port=it.filter(Char::isDigit)},label={Text("端口")},singleLine=true)
            OutlinedTextField(user,{user=it},label={Text("用户名（可选）")},singleLine=true)
            OutlinedTextField(pass,{pass=it},label={Text("密码（可选）")},singleLine=true,visualTransformation=PasswordVisualTransformation())
            if(type != "http") TextButton(onClick={advanced=!advanced}) {Text(if(advanced) "收起 TLS 设置" else "TLS 设置")}
            if(advanced && type != "http") {
                if(type=="socks5") Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(tls,{tls=it});Text("代理服务器使用 TLS")
                }
                if(usesTls) {
                    OutlinedTextField(serverName,{serverName=it},label={Text("TLS 服务器名称（可选）")},singleLine=true)
                    Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(insecure,{insecure=it});Text("跳过代理服务器证书验证")}
                }
            }
            error?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        }
    },confirmButton={AppGlassButton(onClick={runCatching {
        node.copy(name=name.trim(),host=host.trim(),port=port.toIntOrNull() ?: 0,type=type,username=user,password=pass,
            tls=type=="socks5" && tls,tlsServerName=if(usesTls) serverName.trim() else "",skipCertVerify=usesTls && insecure).validate()
    }.onSuccess(onSave).onFailure {error=it.message}}){Text("保存")}},
        dismissButton={Row {if(node.host.isNotBlank())TextButton(onClick=onDelete){Text("移除",color=MaterialTheme.colorScheme.error)};TextButton(onClick=onDismiss){Text("取消")}}})
}
@Composable private fun ProxyTextImport(onDismiss: ()->Unit,onImport: (String)->Unit) {
    var text by remember {mutableStateOf("")};var error by remember {mutableStateOf<String?>(null)}
    AppDialog(onDismissRequest=onDismiss,style=AppDialogStyle.Form,title={Text("导入代理节点")},text={Column {
        OutlinedTextField(text,{text=it},label={Text("每行一个 HTTP、HTTPS 或 SOCKS5 节点链接")},minLines=4,maxLines=8,visualTransformation=PasswordVisualTransformation())
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={AppGlassButton(onClick={runCatching {onImport(text)}.onFailure {error=it.message}}){Text("导入")}},dismissButton={TextButton(onClick=onDismiss){Text("取消")}})
}
