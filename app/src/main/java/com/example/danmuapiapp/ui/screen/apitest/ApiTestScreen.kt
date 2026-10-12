package com.example.danmuapiapp.ui.screen.apitest

import com.example.danmuapiapp.ui.component.AppDialog
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.example.danmuapiapp.data.util.RuntimeUrlParser
import com.example.danmuapiapp.domain.model.ServiceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ApiTestScreen(onBack: () -> Unit, viewModel: ApiTestViewModel = hiltViewModel()) {
    val runtime by viewModel.runtimeState.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "test"
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var useLan by rememberSaveable { mutableStateOf(false) }
    var customBase by rememberSaveable { mutableStateOf("") }
    val localBase = RuntimeUrlParser.extractBase(runtime.localUrl)
    val lanBase = RuntimeUrlParser.extractBase(runtime.lanUrl)
    val base = customBase.ifBlank { if (useLan) lanBase else localBase }.ifBlank { "http://127.0.0.1:9321" }
    var topTab by rememberSaveable { mutableIntStateOf(0) }
    var subTab by rememberSaveable { mutableIntStateOf(0) }
    var fileName by rememberSaveable { mutableStateOf("") }
    var keyword by rememberSaveable { mutableStateOf("") }
    var awaitAuto by rememberSaveable { mutableStateOf(false) }
    var showConnection by rememberSaveable { mutableStateOf(false) }
    var showLogs by rememberSaveable { mutableStateOf(false) }
    var exportInsight by remember { mutableStateOf<DanmuInsight?>(null) }
    var pendingExport by remember { mutableStateOf<DanmuExportPayload?>(null) }
    var pendingText by remember { mutableStateOf("") }
    val copy: (String) -> Unit = { text ->
        clipboard.nativeClipboard.setPrimaryClip(ClipData.newPlainText("接口调试", text))
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    }
    val saveText = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                context.contentResolver.openOutputStream(uri, "w")?.use { it.write(pendingText.toByteArray()) }
                    ?: error("无法打开文件")
            } }
            Toast.makeText(context, result.fold({ "已导出" }, { "导出失败：${it.message}" }), Toast.LENGTH_LONG).show()
        }
    }
    val saveDanmu = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val payload = pendingExport
        val uri = result.data?.data
        if (payload != null && result.resultCode == Activity.RESULT_OK && uri != null) scope.launch {
            val saved = withContext(Dispatchers.IO) { runCatching {
                context.contentResolver.openOutputStream(uri, "w")?.use { it.write(payload.bytes) }
                    ?: error("无法打开文件")
            } }
            saved.onSuccess { viewModel.onExportSaved(payload.fileName) }
                .onFailure { viewModel.onExportSaveFailed(it.message ?: "写入失败") }
            pendingExport = null
        } else pendingExport = null
    }
    val exportText: (String, String) -> Unit = { name, text -> pendingText = text; saveText.launch(name) }
    LaunchedEffect(viewModel.preparedExport?.id) {
        viewModel.preparedExport?.let { payload ->
            viewModel.consumePreparedExport(payload.id)
            pendingExport = payload
            saveDanmu.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = payload.format.mimeType
                putExtra(Intent.EXTRA_TITLE, payload.fileName)
            })
        }
    }
    LaunchedEffect(viewModel.uiNotice?.id) {
        viewModel.uiNotice?.let {
            Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
            viewModel.consumeUiNotice(it.id)
        }
    }
    LaunchedEffect(awaitAuto, viewModel.isAutoMatching, viewModel.autoMatchResult) {
        if (awaitAuto && !viewModel.isAutoMatching) {
            if (viewModel.autoMatchResult != null) {
                awaitAuto = false
                nav.navigate("detail/auto") { launchSingleTop = true }
            } else if (viewModel.errorMessage != null) awaitAuto = false
        }
    }
    LaunchedEffect(topTab, subTab, base) { if (topTab == 0 && subTab == 2) viewModel.loadFavorites(base) }
    val back: () -> Unit = {
        viewModel.dismissError()
        when (route) {
            "test" -> onBack()
            "detail/auto" -> { viewModel.clearAutoResult(); nav.popBackStack() }
            "detail/manual" -> { viewModel.clearManualDanmu(); nav.popBackStack() }
            "episodes" -> { viewModel.backManualStep(); nav.popBackStack() }
            else -> { nav.popBackStack() }
        }
    }
    val search: (String) -> Unit = { query ->
        if (query.isNotBlank() && !viewModel.isSearchingAnime) {
            keyboard?.hide()
            keyword = query
            viewModel.searchAnime(base, query)
            nav.navigate("results") { launchSingleTop = true }
        } else if (query.isBlank()) Toast.makeText(context, "请输入搜索关键词或视频 URL", Toast.LENGTH_SHORT).show()
    }

    NavHost(navController = nav, startDestination = "test", modifier = Modifier.fillMaxSize(),
        enterTransition = { slideInHorizontally(tween(240)) { it / 4 } + fadeIn(tween(200)) },
        exitTransition = { fadeOut(tween(160)) }, popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { slideOutHorizontally(tween(240)) { it / 4 } + fadeOut(tween(160)) }) {
        composable("test") {
            Column(Modifier.fillMaxSize()) {
                ApiPageHeader("接口调试", onBack, actions = {
                    TextButton(onClick = {
                        if (!useLan && lanBase.isBlank()) Toast.makeText(context, "没有检测到局域网地址", Toast.LENGTH_SHORT).show()
                        else { useLan = !useLan; customBase = "" }
                    }) {
                        Icon(if (useLan) Icons.Rounded.Lan else Icons.Rounded.PhoneAndroid, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(if (useLan) "局域网" else "本机", fontSize = 12.5.sp)
                    }
                    IconButton(onClick = { showConnection = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Tune, "连接设置", Modifier.size(18.dp))
                    }
                })
                ApiTopSegments(topTab) { topTab = it }
                if (runtime.status != ServiceStatus.Running) {
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f))
                        .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(6.dp))
                        Text("服务未运行，请先在首页启用服务", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
                if (topTab == 1) ApiDebugPane(viewModel, base, copy, exportText, { viewModel.refreshLogs(); showLogs = true })
                else {
                    ApiSubTabs(subTab) { subTab = it; viewModel.dismissError() }
                    if (subTab == 2) ApiTestFavoritePane(viewModel, base, onOpen = { item -> subTab = 1; search(item.keyword) })
                    else {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ApiCard {
                                ApiLabel(if (subTab == 0) "文件名" else "搜索关键字")
                                ApiInput(if (subTab == 0) fileName else keyword,
                                    { if (subTab == 0) fileName = it else keyword = it },
                                    if (subTab == 0) "示例：凡人修仙传 S01E01 2160p WEB-DL" else "示例：凡人修仙传，或粘贴视频 URL",
                                    onSubmit = { if (subTab == 0) { keyboard?.hide(); awaitAuto = true; viewModel.runAutoMatch(base, fileName) }
                                        else search(keyword) })
                                if (subTab == 0) {
                                    ApiAction(if (viewModel.isAutoMatching) "匹配并获取弹幕…" else "开始匹配", Icons.Rounded.PlayArrow,
                                        { keyboard?.hide(); awaitAuto = true; viewModel.runAutoMatch(base, fileName) },
                                        Modifier.fillMaxWidth(), busy = viewModel.isAutoMatching)
                                    ApiHint("文件名 → 自动匹配 → 获取弹幕，成功后进入弹幕详情页；也支持直接解析视频 URL。")
                                } else {
                                    val favored = viewModel.favoriteForKeyword(keyword) != null
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        ApiAction(if (favored) "已收藏" else "收藏", if (favored) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                            { viewModel.toggleManualFavorite(base, keyword) }, Modifier.weight(1f),
                                            busy = viewModel.favoriteOperation != null, outlined = true)
                                        ApiAction("搜索", Icons.Rounded.Search, { search(keyword) }, Modifier.weight(1f), busy = viewModel.isSearchingAnime)
                                    }
                                    ApiHint("搜索 → 选番剧 → 选剧集 → 弹幕详情，每一级都可以返回；收藏会缓存整组搜索结果。")
                                }
                            }
                            viewModel.errorMessage?.let { ApiErrorCard(it, viewModel::dismissError) }
                            if (subTab == 1 && viewModel.manualAnimeCandidates.isNotEmpty()) ApiCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.History, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    ApiHint("上次搜索：${viewModel.successfulManualSearchKeyword.ifBlank { keyword }}（${viewModel.manualAnimeCandidates.size} 个结果）", Modifier.weight(1f))
                                    TextButton(onClick = { search(keyword) }) { Text("再搜一次", fontSize = 12.sp) }
                                }
                            }
                            ApiCard { ApiHint("请求地址：$base") }
                        }
                    }
                }
            }
        }
        composable("results") {
            ApiAnimeResultsPage(viewModel, keyword, back, onRetry = { viewModel.searchAnime(base, keyword) },
                onFavorite = { viewModel.toggleManualFavorite(base, keyword) },
                onSelect = { anime -> viewModel.openManualAnimeDetail(base, anime); nav.navigate("episodes") })
        }
        composable("episodes") {
            ApiEpisodePage(viewModel, back,
                onRetry = { viewModel.manualCurrentAnime?.let { viewModel.openManualAnimeDetail(base, it) } },
                onSelect = { anime, episode -> viewModel.loadManualDanmu(base, anime, episode); nav.navigate("detail/manual") })
        }
        composable("detail/auto") {
            ApiDanmuDetailPage(viewModel.autoMatchResult, viewModel.isAutoMatching, viewModel.errorMessage,
                back, { viewModel.runAutoMatch(base, fileName) }, copy,
                onExport = { exportInsight = it }, apiUrl = { viewModel.danmuRequestUrl(base, it) }, fileName = fileName)
        }
        composable("detail/manual") {
            ApiDanmuDetailPage(viewModel.manualResult, viewModel.isLoadingManualDanmu, viewModel.errorMessage,
                back, { viewModel.reloadManualDanmu(base) }, copy,
                onExport = { exportInsight = it }, apiUrl = { viewModel.danmuRequestUrl(base, it) })
        }
    }
    // Register after the nested NavHost so system back also performs page cleanup.
    BackHandler(enabled = route != "test", onBack = back)
    if (showConnection) ApiConnectionDialog(base, { customBase = it }, localBase, lanBase, { showConnection = false })
    if (showLogs) ApiLogsDialog(viewModel, copy, exportText, { showLogs = false })
    exportInsight?.let { insight -> ApiTestExportDialog(insight, viewModel.isExportingDanmu || pendingExport != null,
        { exportInsight = null }, { format -> exportInsight = null; viewModel.prepareDanmuExport(base, insight, format) }) }
}

@Composable
private fun ApiTopSegments(selected: Int, onSelect: (Int) -> Unit) {
    Surface(Modifier.padding(horizontal = 14.dp, vertical = 6.dp).fillMaxWidth(), shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))) {
        Row(Modifier.height(36.dp).padding(2.dp)) {
            listOf("弹幕测试", "接口调试").forEachIndexed { index, text ->
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(9.dp))
                    .background(if (index == selected) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(index) }, contentAlignment = Alignment.Center) {
                    Text(text, fontSize = 12.5.sp, fontWeight = if (selected == index) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected == index) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ApiSubTabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.padding(horizontal = 14.dp).fillMaxWidth()) {
        listOf("自动匹配", "手动匹配", "收藏").forEachIndexed { index, text ->
            Column(Modifier.weight(1f).clickable { onSelect(index) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text, Modifier.padding(vertical = 10.dp), fontSize = 13.sp,
                    fontWeight = if (selected == index) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.fillMaxWidth().height(2.dp).background(if (selected == index) MaterialTheme.colorScheme.primary
                    else androidx.compose.ui.graphics.Color.Transparent))
            }
        }
    }
}

@Composable
internal fun ApiErrorCard(message: String, onDismiss: () -> Unit) {
    ApiCard(accent = MaterialTheme.colorScheme.error) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "关闭错误提示", Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun ApiConnectionDialog(base: String, onChange: (String) -> Unit, local: String, lan: String, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(base) }
    AppDialog(onDismissRequest = onDismiss, title = { Text("连接设置") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ApiInput(value, { value = it }, "http://127.0.0.1:9321")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { value = local }, enabled = local.isNotBlank()) { Text("本机") }
                TextButton(onClick = { value = lan }, enabled = lan.isNotBlank()) { Text("局域网") }
            }
        }
    }, confirmButton = { TextButton(onClick = { onChange(value.trim()); onDismiss() }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
