package com.example.danmuapiapp.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.data.network.AppProxyNetwork
import com.example.danmuapiapp.data.service.*
import com.example.danmuapiapp.ui.common.rememberConnectivityChecks
import com.example.danmuapiapp.ui.component.*
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DownloadUpdateSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val interval by viewModel.coreUpdateCheckIntervalMinutes.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedGithubProxyId.collectAsStateWithLifecycle()
    val officialRoute = viewModel.proxyOptions.firstOrNull { it.id == selectedId }?.name.orEmpty()
    var settings by remember { mutableStateOf(AppOutboundSettings()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var showInterval by remember { mutableStateOf(false) }
    var showRoute by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AppProxyNode?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val nodeChecks = rememberConnectivityChecks(settings.proxy.nodes)
    val fallback = when (selectedId) {
        "github_enhanced" -> "enhanced"
        "original" -> "inherit"
        else -> "mirror"
    }
    val route = settings.sourceRoute("github", ProxySourceCatalog(emptyList(), emptySet()), fallback)
    val routeLabel = if (route in listOf("inherit", "mirror")) officialRoute else settings.proxy.label(route)

    fun report(message: String) { scope.launch { snackbar.showSnackbar(message) } }
    fun saveRoute(selected: String) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                settings = withContext(Dispatchers.IO) { AppOutboundSettingsStore.saveSourceRoute(context, "github", selected) }
                showRoute = false
                report("GitHub 连接方式已保存")
            } catch (error: Exception) { report(error.message ?: "保存失败") }
            finally { busy = false }
        }
    }
    LaunchedEffect(context) {
        try { settings = withContext(Dispatchers.IO) { AppOutboundSettingsStore.read(context) } }
        catch (error: Exception) { report(error.message ?: "下载线路读取失败") }
        finally { loading = false }
    }
    LaunchedEffect(viewModel.operationMessage) {
        viewModel.operationMessage?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() }
    }

    Scaffold(containerColor = Color.Transparent, snackbarHost = { AppSnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SettingsPageHeader("下载与更新", "GitHub 连接、下载镜像与核心更新检查", onBack)
            if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            SettingsGroup(title = "GitHub 连接") {
                SettingsItem(title = "连接方式", subtitle = "当前：$routeLabel", icon = Icons.Rounded.Public,
                    onClick = if (loading || busy) null else { { showRoute = true } })
                SettingsDivider()
                SettingsItem(title = "官方与下载镜像",
                    subtitle = if (route.startsWith("node:") || (settings.proxy.enabled && settings.proxy.rules["github"] == "enhanced"))
                        "切换后使用所选官方线路或下载镜像" else officialRoute,
                    icon = Icons.Rounded.CloudDownload,
                    onClick = if (loading || busy) null else viewModel::openProxyPicker)
                SettingsDivider()
                SettingsItem(title = "连通性检测", subtitle = "检测 GitHub API、配置文件和下载资源的连接",
                    icon = Icons.Rounded.NetworkCheck, onClick = if (loading || busy) null else { { testing = true } })
            }
            SettingsHintCard("连接方式可选增强直连或 App 代理节点。选择代理节点后优先使用该节点，节点不可用时会报告失败。")

            SettingsGroup(title = "核心更新") {
                SettingsValueItem(title = "自动检查间隔", value = "$interval 分钟", icon = Icons.Rounded.Update,
                    onClick = { showInterval = true })
                Text("应用进入前台时，达到所选间隔才检查核心更新。", Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SettingsHintCard("GitHub Token 在设置首页单独管理。弹幕来源的访问线路请到「网络设置」调整。")
        }
    }

    if (testing) AppOutboundConnectivityDialog(github = true, onDismiss = { testing = false })
    if (showInterval) AppDialog(onDismissRequest = { showInterval = false }, style = AppDialogStyle.Selection,
        title = { Text("自动检查间隔") }, supportingText = { Text("应用进入前台后检查核心更新的最短间隔") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CoreUpdateCheckPolicy.intervalOptionsMinutes.forEach { minutes ->
                    AppDialogOption(selected = interval == minutes, onClick = {
                        viewModel.setCoreUpdateCheckIntervalMinutes(minutes); showInterval = false
                    }) { RadioButton(selected = interval == minutes, onClick = null); Text("$minutes 分钟") }
                }
            }
        }, dismissButton = { AppGlassButton(onClick = { showInterval = false }) { Text("取消") } })

    if (showRoute) SourceRouteEditor("github", route, settings.proxy, busy, nodeChecks,
        onProbeNodes = { nodes ->
            val byId = nodes.associateBy { it.id }
            nodeChecks.start(nodes.map { it.id }) { AppProxyNetwork.probeNode(byId.getValue(it)) }
        }, onDismiss = { if (!busy) showRoute = false },
        onAddNode = { showRoute = false; editing = AppProxyNode() }, onAdvanced = {}, onSave = ::saveRoute)

    editing?.let { node ->
        ProxyNodeEditor(node, onDismiss = { if (!busy) editing = null }, onDelete = {}, onSave = { next ->
            if (!busy) {
                busy = true
                scope.launch {
                    try {
                        settings = withContext(Dispatchers.IO) {
                            val current = AppOutboundSettingsStore.read(context)
                            AppOutboundSettingsStore.saveProxy(context, current.proxy.copy(nodes = current.proxy.nodes + next))
                            AppOutboundSettingsStore.read(context)
                        }
                        editing = null; showRoute = true
                    } catch (error: Exception) { report(error.message ?: "节点保存失败") }
                    finally { busy = false }
                }
            }
        })
    }
    if (viewModel.showProxyPickerDialog) GithubProxyPickerDialog(
        title = "官方与下载镜像", subtitle = "为核心下载和应用更新选择线路，保存后立即使用所选线路。",
        options = viewModel.proxyOptions, selectedId = viewModel.proxySelectedId,
        testingIds = viewModel.proxyTestingIds, resultMap = viewModel.proxyLatencyMap,
        onSelect = viewModel::selectProxy, onRetest = viewModel::retestProxySpeed,
        onConfirm = {
            if (!busy) {
                busy = true
                scope.launch {
                    try {
                        settings = withContext(Dispatchers.IO) { AppOutboundSettingsStore.saveSourceRoute(context, "github", "inherit") }
                        viewModel.confirmProxySelection()
                        report("GitHub 下载线路已保存")
                    } catch (error: Exception) { report(error.message ?: "下载线路保存失败") }
                    finally { busy = false }
                }
            }
        }, onDismiss = viewModel::dismissProxyPickerDialog, confirmText = "保存线路")
}
