package com.example.danmuapiapp.ui.screen.push

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.example.danmuapiapp.data.util.RuntimeUrlParser
import com.example.danmuapiapp.ui.component.AppModalPanel
import com.example.danmuapiapp.ui.component.AppToast
import com.example.danmuapiapp.ui.component.AppToastKind
import com.example.danmuapiapp.ui.screen.apitest.AnimeResultsPage
import com.example.danmuapiapp.ui.screen.apitest.ApiSegmentedTabs
import com.example.danmuapiapp.ui.screen.favorites.FavoriteListPane
import com.example.danmuapiapp.ui.screen.tools.*
import kotlinx.coroutines.launch

@Composable
fun PushDanmuScreen(onBack: () -> Unit, viewModel: PushDanmuViewModel = hiltViewModel()) {
    val runtime by viewModel.runtimeState.collectAsStateWithLifecycle()
    val env by viewModel.envVars.collectAsStateWithLifecycle()
    val adminState by viewModel.adminSessionState.collectAsStateWithLifecycle()
    val defaultFont = env["DANMU_FONT_SIZE"]?.toIntOrNull()
    val localBase = RuntimeUrlParser.extractBase(runtime.localUrl)
    val lanBase = RuntimeUrlParser.extractBase(runtime.lanUrl)
    val lanIp = remember(runtime.lanUrl) { PushLanScanner.resolveSelfLanIpv4(runtime.lanUrl).orEmpty() }
    val savedTarget by viewModel.savedDefaultTarget.collectAsStateWithLifecycle()
    val defaultTargetLoaded by viewModel.defaultTargetLoaded.collectAsStateWithLifecycle()
    val recentTargets by viewModel.recentTargets.collectAsStateWithLifecycle()
    var target by rememberSaveable { mutableStateOf("") }
    val source = lanBase.ifBlank { localBase }
    var keyword by rememberSaveable { mutableStateOf("") }
    var offset by rememberSaveable { mutableStateOf("0") }
    var font by rememberSaveable { mutableStateOf("") }
    var showTarget by rememberSaveable { mutableStateOf(false) }
    var targetInitialized by rememberSaveable { mutableStateOf(false) }
    var topTab by rememberSaveable { mutableIntStateOf(0) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    LaunchedEffect(lanIp, defaultTargetLoaded, savedTarget) {
        if (!targetInitialized && defaultTargetLoaded) {
            target = resolveDefaultPushTarget(savedTarget, lanIp)
            targetInitialized = true
        }
    }
    LaunchedEffect(topTab, source) {
        if (topTab == 1) viewModel.loadFavorites(source)
    }
    LaunchedEffect(viewModel.uiNotice?.id) {
        viewModel.uiNotice?.let { notice ->
            AppToast.show(notice.message, if (notice.isError) AppToastKind.Error else null)
            viewModel.consumeUiNotice(notice.id)
        }
    }
    val back: () -> Unit = {
        when (route) {
            "home" -> onBack()
            "episodes" -> {
                viewModel.backToAnimeList()
                viewModel.clearError()
                nav.popBackStack()
            }
            "results" -> {
                viewModel.clearError()
                nav.popBackStack()
            }
            else -> nav.popBackStack()
        }
    }
    val search: (String) -> Unit = { query ->
        val q = query.trim()
        if (q.isBlank()) AppToast.warning("请输入搜索关键词")
        else if (!viewModel.isSearching) {
            keyboard?.hide()
            keyword = q
            viewModel.searchAnime(source, q)
            if (route != "results") nav.navigate("results") { launchSingleTop = true }
        }
    }
    val copyResult: (String) -> Unit = { text ->
        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("推送结果", text))) }
    }

    NavHost(navController = nav, startDestination = "home", modifier = Modifier.fillMaxSize(),
        enterTransition = { slideInHorizontally(tween(240)) { it / 4 } + fadeIn(tween(200)) },
        exitTransition = { fadeOut(tween(160)) }, popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = { slideOutHorizontally(tween(240)) { it / 4 } + fadeOut(tween(160)) }) {
        composable("home") {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                ToolHeader("弹幕推送", back)
                ApiSegmentedTabs(
                    labels = listOf("搜索", "收藏"),
                    selected = topTab,
                    onSelect = { topTab = it; viewModel.clearError() },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
                if (topTab == 1) {
                    FavoriteListPane(
                        items = viewModel.favoriteItems,
                        supportState = viewModel.favoriteSupportState,
                        scheduledRefreshSupported = viewModel.scheduledFavoriteRefreshSupported,
                        loadError = viewModel.favoriteLoadError,
                        operation = viewModel.favoriteOperation,
                        operationKeyword = viewModel.favoriteOperationKeyword,
                        emptyHint = "在搜索结果页点收藏即可添加",
                        openEnabled = !viewModel.isSearching,
                        openLabel = "选择剧集",
                        onLoad = { viewModel.loadFavorites(source) },
                        onOpen = { item -> search(item.keyword) },
                        onRefresh = { viewModel.refreshFavorite(source, it) },
                        onRemove = { viewModel.removeFavorite(source, it) },
                        onSchedule = { item, draft -> viewModel.updateFavoriteSchedule(source, item, draft) }
                    )
                } else {
                    SearchTab(
                        viewModel = viewModel,
                        source = source,
                        target = target,
                        keyword = keyword,
                        offset = offset,
                        font = font,
                        defaultFont = defaultFont,
                        lastSearchKeyword = viewModel.lastSearchKeyword,
                        resultCount = viewModel.animeCandidates.size,
                        onKeywordChange = { keyword = it },
                        onOffsetChange = { offset = it },
                        onFontChange = { font = it },
                        onSearch = { search(keyword) },
                        onShowTarget = { showTarget = true },
                        onOpenLastResult = { nav.navigate("results") { launchSingleTop = true } }
                    )
                }
            }
        }
        composable("results") {
            AnimeResultsPage(
                keyword = viewModel.lastSearchKeyword,
                candidates = viewModel.animeCandidates,
                isSearching = viewModel.isSearching,
                errorMessage = viewModel.errorMessage,
                grid = false,
                isSavingLayout = false,
                showLayoutToggle = false,
                favorite = viewModel.favoriteForKeyword(viewModel.lastSearchKeyword),
                favoriteBusy = viewModel.favoriteOperation != null,
                onBack = back,
                onRetry = { viewModel.searchAnime(source, viewModel.lastSearchKeyword) },
                onToggleLayout = {},
                onToggleFavorite = { viewModel.toggleSearchFavorite(source) },
                onSelect = { anime ->
                    viewModel.openAnimeDetail(source, anime)
                    nav.navigate("episodes") { launchSingleTop = true }
                }
            )
        }
        composable("episodes") {
            PushEpisodePage(
                viewModel = viewModel,
                source = source,
                target = target,
                offset = offset,
                font = font,
                defaultFont = defaultFont,
                onCopyResult = copyResult,
                onBack = back
            )
        }
    }
    // Register after the nested NavHost so system back also performs page cleanup.
    BackHandler(enabled = route != "home", onBack = back)
    if (showTarget) AppModalPanel(
        onDismissRequest = { showTarget = false },
        maxWidth = 560.dp,
        contentPadding = PaddingValues(0.dp)
    ) {
        PushTargetDialog(
            target = target,
            onTargetChange = {
                viewModel.clearTargetSaveMessage()
                targetInitialized = true
                target = it
            },
            lanIp = lanIp,
            savedDefaultTarget = savedTarget,
            effectiveDefaultTarget = resolveDefaultPushTarget(savedTarget, lanIp),
            recentTargets = recentTargets,
            isAdminMode = adminState.isAdminMode,
            isSavingDefault = viewModel.isSavingDefaultTarget,
            saveMessage = viewModel.targetSaveMessage,
            saveFailed = viewModel.targetSaveFailed,
            onClearSaveMessage = viewModel::clearTargetSaveMessage,
            onForgetTarget = viewModel::forgetTarget,
            onSaveDefaultTarget = viewModel::saveDefaultTarget,
            onScan = { viewModel.scanLanDevices(runtime.lanUrl) },
            isScanning = viewModel.isScanningLan,
            scanStatus = viewModel.lanScanStatus,
            devices = viewModel.lanDevices,
            onClearScan = viewModel::clearLanScan,
            onCopy = { value -> scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("推送地址", value))) } },
            onDismissRequest = { showTarget = false }
        )
    }
}

@Composable
private fun SearchTab(
    viewModel: PushDanmuViewModel,
    source: String,
    target: String,
    keyword: String,
    offset: String,
    font: String,
    defaultFont: Int?,
    lastSearchKeyword: String,
    resultCount: Int,
    onKeywordChange: (String) -> Unit,
    onOffsetChange: (String) -> Unit,
    onFontChange: (String) -> Unit,
    onSearch: () -> Unit,
    onShowTarget: () -> Unit,
    onOpenLastResult: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ToolCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ToolTitle("搜索动漫", Modifier.weight(1f))
                    Surface(shape = RoundedCornerShape(7.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .10f)) {
                        Text("仅支持 OK影视", Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 10.5.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                ToolInput(keyword, onKeywordChange, "例如：凡人修仙传", icon = Icons.Outlined.Search, onSubmit = onSearch)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ToolHint("时间偏移"); ToolInput(offset, onOffsetChange, "0", number = true); ToolHint("秒，可负数")
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ToolHint("弹幕大小"); ToolInput(font, onFontChange, defaultFont?.toString() ?: "默认", number = true)
                        ToolHint(defaultFont?.let { "默认 $it" } ?: "留空=默认")
                    }
                }
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.DevicesOther, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text("目标：${summarizePushHost(target)}", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            ToolHint("源：${summarizePushHost(source)}", maxLines = 1)
                        }
                        TextButton(onShowTarget, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("更改", fontSize = 12.sp) }
                    }
                }
                ToolButton("搜索", onSearch, Modifier.fillMaxWidth(), Icons.Outlined.Search, busy = viewModel.isSearching)
            }
        }
        viewModel.errorMessage?.let { error ->
            item(key = "push-search-error") { ToolError(error) { viewModel.clearError() } }
        }
        if (lastSearchKeyword.isNotBlank()) {
            item(key = "push-last-search") {
                ToolCard(onClick = onOpenLastResult) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.History, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            ToolTitle(lastSearchKeyword, size = 13)
                            ToolHint("上次搜索 · $resultCount 个结果")
                        }
                        Text("查看结果", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if (lastSearchKeyword.isBlank()) {
            item(key = "push-search-empty") {
                ToolEmpty("搜索后选择番剧与剧集", Icons.Outlined.Cast, "搜索结果与剧集列表会作为独立页面打开，收藏页可直接回到搜索")
            }
        }
    }
}
