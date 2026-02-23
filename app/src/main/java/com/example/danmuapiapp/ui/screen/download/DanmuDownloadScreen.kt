package com.example.danmuapiapp.ui.screen.download

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import com.example.danmuapiapp.domain.model.DanmuDownloadRecord
import com.example.danmuapiapp.domain.model.DownloadQueueStatus
import com.example.danmuapiapp.domain.model.DownloadRecordStatus
import com.example.danmuapiapp.domain.model.renderFileNameTemplatePreview
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ═══════════════════════════════════════════════════════════════
//  Main Screen — Tab-based layout
// ═══════════════════════════════════════════════════════════════

@Composable
fun DanmuDownloadScreen(
    onBack: () -> Unit,
    onOpenDownloadSettings: () -> Unit,
    providedViewModel: DanmuDownloadViewModel? = null
) {
    val activity = LocalContext.current.findActivity()
    val viewModel = providedViewModel ?: hiltViewModel<DanmuDownloadViewModel>(
        viewModelStoreOwner = checkNotNull(activity) { "无法获取 Activity 作用域" }
    )
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val queueTasks by viewModel.queueTasks.collectAsStateWithLifecycle()
    val inEpisodeDetail = viewModel.currentAnime != null
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val queueSummary = viewModel.queueSummary()

    LaunchedEffect(viewModel.operationMessage) {
        val msg = viewModel.operationMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    FilledIconButton(
                        onClick = {
                            if (inEpisodeDetail) viewModel.backToAnimeList()
                            else onBack()
                        },
                        colors = primaryActionIconButtonColors(),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", Modifier.size(18.dp))
                    }
                    Text(
                        if (inEpisodeDetail) viewModel.currentAnime?.title ?: "弹幕下载" else "弹幕下载",
                        style = MaterialTheme.typography.headlineLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FilledIconButton(
                    onClick = onOpenDownloadSettings,
                    colors = primaryActionIconButtonColors(),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Rounded.Settings, "下载设置", Modifier.size(18.dp))
                }
            }
            // ── Tabs ──
            val queueActive = queueSummary.active
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text("搜索下载") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("任务队列")
                            if (queueActive > 0) {
                                Badge(containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) { Text("$queueActive") }
                            }
                        }
                    })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("下载记录")
                            if (records.isNotEmpty()) {
                                Badge(containerColor = MaterialTheme.colorScheme.tertiary,
                                    contentColor = MaterialTheme.colorScheme.onTertiary
                                ) { Text("${records.size}") }
                            }
                        }
                    })
            }

            // ── Tab Content ──
            when (selectedTab) {
                0 -> SearchDownloadPage(viewModel = viewModel, settings = settings)
                1 -> QueuePage(viewModel = viewModel, queueTasks = queueTasks)
                2 -> RecordsPage(records = records, onClear = viewModel::clearRecords)
            }
        }
    }

    if (viewModel.errorMessage != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("操作失败") },
            text = { Text(viewModel.errorMessage.orEmpty()) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("知道了") } }
        )
    }
}

private tailrec fun Context.findActivity(): ComponentActivity? {
    return when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

// ═══════════════════════════════════════════════════════════════
//  Tab 0 — Search & Download
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SearchDownloadPage(
    viewModel: DanmuDownloadViewModel,
    settings: com.example.danmuapiapp.domain.model.DanmuDownloadSettings
) {
    val inDetail = viewModel.currentAnime != null
    val visibleEpisodes = viewModel.visibleEpisodes()
    val episodeSummary = if (inDetail) viewModel.visibleStateSummary() else EpisodeStateSummary()
    val selectedCount = if (inDetail) {
        visibleEpisodes.count { viewModel.selectedEpisodeIds.contains(it.episodeId) }
    } else 0
    var configExpanded by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp, top = 12.dp,
                bottom = if (inDetail) 80.dp else 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Config Section ──
            item {
                CompactConfigSection(
                    expanded = configExpanded,
                    onToggle = { configExpanded = !configExpanded },
                    sourceBase = viewModel.sourceBase,
                    onSourceBaseChange = viewModel::updateSourceBase,
                    onUseLocal = viewModel::useLocalBase,
                    onUseLan = viewModel::useLanBase,
                    selectedFormat = viewModel.selectedFormat,
                    onSelectFormat = viewModel::updateFormat,
                    fileNamePreview = renderFileNameTemplatePreview(
                        template = settings.fileNameTemplate,
                        format = viewModel.selectedFormat
                    ),
                    saveDirName = settings.saveDirDisplayName,
                    conflictLabel = settings.policy().label
                )
            }
            if (!inDetail) {
                // ── Search Mode ──
                item {
                    OutlinedTextField(
                        value = viewModel.keyword,
                        onValueChange = viewModel::updateKeyword,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("搜索动漫，如：凡人修仙传") },
                        singleLine = true,
                        trailingIcon = {
                            if (viewModel.isSearching) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                IconButton(onClick = viewModel::searchAnime) {
                                    Icon(Icons.Rounded.Search, "搜索")
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp)
                    )
                }
                if (viewModel.isSearching) {
                    item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                } else if (viewModel.hasSearchedAnime && viewModel.animeCandidates.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center) {
                            Text("未找到匹配动漫",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (viewModel.animeCandidates.isNotEmpty()) {
                    item {
                        Text("搜索结果（${viewModel.animeCandidates.size}）",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(viewModel.animeCandidates, key = { it.animeId }) { anime ->
                        AnimeEntryRow(
                            anime = anime,
                            loading = viewModel.isLoadingEpisodes,
                            onClick = { viewModel.openAnimeDetail(anime) }
                        )
                    }
                }
            } else {
                // ── Anime Detail Mode ──
                val anime = viewModel.currentAnime
                if (anime != null) {
                item {
                    AnimeInfoHeader(
                        anime = anime,
                        visibleCount = visibleEpisodes.size,
                        onBackToSearch = viewModel::backToAnimeList
                    )
                }
                // Source filter chips (only show when multiple sources exist)
                val sourceOptions = viewModel.sourceOptions()
                if (sourceOptions.size > 1) {
                    item {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = viewModel.sourceFilter == null,
                                onClick = { viewModel.selectSourceFilter(null) },
                                colors = primarySelectionFilterChipColors(),
                                label = { Text("全部来源") }
                            )
                            sourceOptions.forEach { source ->
                                FilterChip(
                                    selected = viewModel.sourceFilter == source,
                                    onClick = { viewModel.selectSourceFilter(source) },
                                    colors = primarySelectionFilterChipColors(),
                                    label = { Text(source) },
                                    leadingIcon = if (viewModel.sourceFilter == source) {
                                        { Icon(Icons.Rounded.FilterAlt, null, Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }
                    }
                }
                // Stat badges + quick select
                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatBadge("成功", episodeSummary.success, Color(0xFF2E7D32))
                        StatBadge("失败", episodeSummary.failed, Color(0xFFC62828))
                        StatBadge("跳过", episodeSummary.skipped, Color(0xFFF57C00))
                        StatBadge("未完成", episodeSummary.unfinished)
                    }
                }
                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(onClick = viewModel::toggleSelectAllVisible,
                            enabled = !viewModel.isDownloading,
                            label = { Text("全选/反选") },
                            leadingIcon = { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) })
                        AssistChip(onClick = viewModel::selectFailedVisibleEpisodes,
                            enabled = !viewModel.isDownloading,
                            label = { Text("选失败") },
                            leadingIcon = { Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(16.dp)) })
                        AssistChip(onClick = viewModel::selectUnfinishedVisibleEpisodes,
                            enabled = !viewModel.isDownloading,
                            label = { Text("选未完成") },
                            leadingIcon = { Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp)) })
                        AssistChip(onClick = viewModel::clearSelection,
                            enabled = !viewModel.isDownloading,
                            label = { Text("清空") },
                            leadingIcon = { Icon(Icons.Rounded.ClearAll, null, Modifier.size(16.dp)) })
                        if (episodeSummary.failed > 0) {
                            AssistChip(onClick = viewModel::retryFailedVisibleEpisodes,
                                enabled = !viewModel.isDownloading,
                                label = { Text("重试失败") },
                                leadingIcon = { Icon(Icons.Rounded.RestartAlt, null, Modifier.size(16.dp)) })
                        }
                    }
                }
                // Progress
                if (viewModel.isDownloading || viewModel.overallProgress > 0f) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LinearProgressIndicator(
                                progress = { viewModel.overallProgress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(viewModel.progressSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                // Throttle hint banner
                val hint = viewModel.throttleHint
                if (hint != null) {
                    item { ThrottleHintBanner(hint) }
                }
                // Episode list
                if (visibleEpisodes.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center) {
                            Text("当前来源筛选下暂无剧集",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(visibleEpisodes, key = { it.episodeId }) { episode ->
                        EpisodeRow(
                            episode = episode,
                            selected = viewModel.selectedEpisodeIds.contains(episode.episodeId),
                            state = viewModel.episodeUiState(episode),
                            enabled = !viewModel.isDownloading,
                            onSelect = { viewModel.toggleEpisodeSelection(episode.episodeId) }
                        )
                    }
                }
                } // if (anime != null)
            }
        }
        // ── Bottom Action Bar (anime detail only) ──
        if (inDetail) {
            Surface(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("已选 $selectedCount 集",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (viewModel.isDownloading) {
                            OutlinedButton(onClick = viewModel::cancelDownload) {
                                Icon(Icons.Rounded.Close, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("取消")
                            }
                        }
                        Button(
                            onClick = viewModel::startDownloadSelectedEpisodes,
                            enabled = !viewModel.isDownloading && selectedCount > 0,
                            colors = primaryActionButtonColors()
                        ) {
                            Icon(Icons.Rounded.CloudDownload, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("下载（$selectedCount）")
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Compact Config Section
// ═══════════════════════════════════════════════════════════════

@Composable
private fun CompactConfigSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    sourceBase: String,
    onSourceBaseChange: (String) -> Unit,
    onUseLocal: () -> Unit,
    onUseLan: () -> Unit,
    selectedFormat: DanmuDownloadFormat,
    onSelectFormat: (DanmuDownloadFormat) -> Unit,
    fileNamePreview: String,
    saveDirName: String,
    conflictLabel: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("下载配置", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "${saveDirName.ifBlank { "未设置目录" }} · ${selectedFormat.label} · $conflictLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        "展开配置", Modifier.size(18.dp)
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sourceBase,
                        onValueChange = onSourceBaseChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("弹幕源 Base URL") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        leadingIcon = { Icon(Icons.Rounded.Link, null) },
                        shape = RoundedCornerShape(12.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = onUseLocal, label = { Text("本机地址") })
                        AssistChip(onClick = onUseLan, label = { Text("局域网地址") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DanmuDownloadFormat.entries.forEach { format ->
                            FilterChip(
                                selected = selectedFormat == format,
                                onClick = { onSelectFormat(format) },
                                colors = primarySelectionFilterChipColors(),
                                label = { Text(format.label) },
                                leadingIcon = if (selectedFormat == format) {
                                    { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                    Text(
                        "命名模板请在「下载设置」中统一配置",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                "命名示例：凡人修仙传/$fileNamePreview",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Anime Info Header
// ═══════════════════════════════════════════════════════════════

@Composable
private fun AnimeInfoHeader(
    anime: DownloadAnimeCandidate,
    visibleCount: Int,
    onBackToSearch: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(anime.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(
                    "AnimeID: ${anime.animeId} · 官方 ${anime.episodeCount} 集 · 可见 $visibleCount 集",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                onClick = onBackToSearch,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack, "返回搜索",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Anime Search Result Row
// ═══════════════════════════════════════════════════════════════

@Composable
private fun AnimeEntryRow(
    anime: DownloadAnimeCandidate,
    loading: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(anime.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    "ID:${anime.animeId} · ${anime.episodeCount} 集",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Rounded.Search, null, Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Episode Row
// ═══════════════════════════════════════════════════════════════

@Composable
private fun EpisodeRow(
    episode: DownloadEpisodeCandidate,
    selected: Boolean,
    state: EpisodeDownloadUiState,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    val (statusText, statusColor) = when (state.state) {
        EpisodeDownloadState.Idle -> "—" to MaterialTheme.colorScheme.onSurfaceVariant
        EpisodeDownloadState.Queued -> "排队" to MaterialTheme.colorScheme.onSurfaceVariant
        EpisodeDownloadState.Running -> "下载中" to MaterialTheme.colorScheme.primary
        EpisodeDownloadState.Success -> "成功" to Color(0xFF2E7D32)
        EpisodeDownloadState.Failed -> "失败" to Color(0xFFC62828)
        EpisodeDownloadState.Skipped -> "跳过" to Color(0xFFF57C00)
        EpisodeDownloadState.Canceled -> "取消" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        ),
        onClick = { if (enabled) onSelect() }
    ) {
        Column(
            modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { if (enabled) onSelect() },
                    enabled = enabled,
                    modifier = Modifier.size(36.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "第${episode.episodeNumber}集  ${episode.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "来源:${episode.source}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Status indicator
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        statusText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }
            if (state.state == EpisodeDownloadState.Running) {
                LinearProgressIndicator(
                    progress = { state.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(start = 36.dp)
                )
            }
            if (state.detail.isNotBlank()) {
                Text(
                    state.detail,
                    modifier = Modifier.padding(start = 36.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Tab 1 — Task Queue
// ═══════════════════════════════════════════════════════════════

@Composable
private fun QueuePage(
    viewModel: DanmuDownloadViewModel,
    queueTasks: List<com.example.danmuapiapp.domain.model.DanmuDownloadTask>
) {
    val summary = viewModel.queueSummary().copy(total = queueTasks.size)
    val completed = viewModel.queueCompletedCount()
    val runningText = viewModel.queueRunningStatusText()
    val groups = viewModel.animeQueueGroups()
    val overallProgress = if (summary.total <= 0) 0f else completed.toFloat() / summary.total.toFloat()
    var expandedGroupTitles by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(groups) {
        val validTitles = groups.map { it.animeTitle }.toSet()
        expandedGroupTitles = expandedGroupTitles.intersect(validTitles)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Summary card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("队列概览", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatBadge("待处理", summary.pending)
                        StatBadge("下载中", summary.running)
                        StatBadge("成功", summary.success, Color(0xFF2E7D32))
                        StatBadge("失败", summary.failed, Color(0xFFC62828))
                        StatBadge("跳过", summary.skipped, Color(0xFFF57C00))
                    }
                    LinearProgressIndicator(
                        progress = { overallProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "进度 $completed/${summary.total.coerceAtLeast(0)} · $runningText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val hint = viewModel.throttleHint
                    if (hint != null) {
                        ThrottleHintBanner(hint)
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (viewModel.isDownloading) {
                            Button(
                                onClick = viewModel::pauseDownload,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFC62828),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Rounded.Close, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("暂停")
                            }
                        } else {
                            Button(
                                onClick = viewModel::resumePendingQueue,
                                enabled = summary.pending > 0,
                                colors = primaryActionButtonColors()
                            ) {
                                Icon(Icons.Rounded.CloudDownload, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("恢复队列")
                            }
                        }
                        OutlinedButton(
                            onClick = viewModel::retryFailedQueueTasks,
                            enabled = !viewModel.isDownloading && summary.failed > 0
                        ) {
                            Icon(Icons.Rounded.RestartAlt, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("重试失败项")
                        }
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = viewModel::clearCompletedQueueTasks,
                            enabled = !viewModel.isDownloading
                        ) { Text("清理已完成") }
                        OutlinedButton(
                            onClick = viewModel::clearQueueTasks,
                            enabled = !viewModel.isDownloading
                        ) { Text("清空") }
                    }
                }
            }
        }

        // Queue groups
        if (groups.isNotEmpty()) {
            item {
                Text("按动漫分组（长按上下箭头调整优先级）", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(groups, key = { it.animeTitle }) { group ->
                QueueGroupRow(
                    group = group,
                    isFirst = group == groups.first(),
                    isLast = group == groups.last(),
                    expanded = expandedGroupTitles.contains(group.animeTitle),
                    onToggleExpand = {
                        expandedGroupTitles = if (expandedGroupTitles.contains(group.animeTitle)) {
                            expandedGroupTitles - group.animeTitle
                        } else {
                            expandedGroupTitles + group.animeTitle
                        }
                    },
                    onMoveUp = { viewModel.moveQueueGroupUp(group.animeTitle) },
                    onMoveDown = { viewModel.moveQueueGroupDown(group.animeTitle) }
                )
            }
        }

        if (queueTasks.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center) {
                    Text("队列为空", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun QueueGroupRow(
    group: AnimeQueueGroup,
    isFirst: Boolean,
    isLast: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(group.animeTitle, style = MaterialTheme.typography.labelLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    LinearProgressIndicator(
                        progress = { group.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    val stateText = when {
                        group.runningEpisodeNo != null -> "正在下载第${group.runningEpisodeNo}集"
                        group.pending > 0 -> "排队待下载"
                        group.failed > 0 -> "已结束（含失败）"
                        else -> "已结束"
                    }
                    Text(
                        "${group.completed}/${group.total} · $stateText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (group.detail.isNotBlank()) {
                        Text(
                            group.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onToggleExpand, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            if (expanded) "收起" else "展开",
                            Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onMoveUp, enabled = !isFirst, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.KeyboardArrowUp, "上移", Modifier.size(20.dp))
                    }
                    IconButton(onClick = onMoveDown, enabled = !isLast, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.KeyboardArrowDown, "下移", Modifier.size(20.dp))
                    }
                }
            }
            AnimatedVisibility(
                visible = expanded && group.episodes.isNotEmpty(),
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    group.episodes.forEach { episode ->
                        QueueEpisodeTaskRow(task = episode)
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueEpisodeTaskRow(task: AnimeQueueEpisodeItem) {
    val statusColor = when (task.status) {
        DownloadQueueStatus.Pending -> MaterialTheme.colorScheme.primary
        DownloadQueueStatus.Running -> Color(0xFF1565C0)
        DownloadQueueStatus.Success -> Color(0xFF2E7D32)
        DownloadQueueStatus.Failed -> Color(0xFFC62828)
        DownloadQueueStatus.Skipped -> Color(0xFFF57C00)
        DownloadQueueStatus.Canceled -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val sourceText = task.source.ifBlank { "unknown" }
    val titleText = task.episodeTitle.ifBlank { "未命名剧集" }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.52f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "第${task.episodeNo}集  $titleText",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        task.status.label,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }
            Text(
                "来源:$sourceText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (task.detail.isNotBlank()) {
                Text(
                    task.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Tab 2 — Download Records
// ═══════════════════════════════════════════════════════════════

private enum class RecordFilter(val label: String) {
    All("全部"), Success("成功"), Failed("失败"), Skipped("跳过")
}

@Composable
private fun RecordsPage(
    records: List<DanmuDownloadRecord>,
    onClear: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    var filter by rememberSaveable { mutableStateOf(RecordFilter.All) }
    val successCount = records.count { it.statusEnum() == DownloadRecordStatus.Success }
    val failedCount = records.count { it.statusEnum() == DownloadRecordStatus.Failed }
    val skippedCount = records.count { it.statusEnum() == DownloadRecordStatus.Skipped }
    val filtered = when (filter) {
        RecordFilter.All -> records
        RecordFilter.Success -> records.filter { it.statusEnum() == DownloadRecordStatus.Success }
        RecordFilter.Failed -> records.filter { it.statusEnum() == DownloadRecordStatus.Failed }
        RecordFilter.Skipped -> records.filter { it.statusEnum() == DownloadRecordStatus.Skipped }
    }
    val display = filtered.take(80)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header + filter
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("下载记录", style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onClear, enabled = records.isNotEmpty(),
                    modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.ClearAll, "清空记录", Modifier.size(18.dp))
                }
            }
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filter == RecordFilter.All,
                    onClick = { filter = RecordFilter.All },
                    colors = primarySelectionFilterChipColors(),
                    label = { Text("全部 ${records.size}") }
                )
                FilterChip(
                    selected = filter == RecordFilter.Success,
                    onClick = { filter = RecordFilter.Success },
                    colors = primarySelectionFilterChipColors(),
                    label = { Text("成功 $successCount") }
                )
                FilterChip(
                    selected = filter == RecordFilter.Failed,
                    onClick = { filter = RecordFilter.Failed },
                    colors = primarySelectionFilterChipColors(),
                    label = { Text("失败 $failedCount") }
                )
                FilterChip(
                    selected = filter == RecordFilter.Skipped,
                    onClick = { filter = RecordFilter.Skipped },
                    colors = primarySelectionFilterChipColors(),
                    label = { Text("跳过 $skippedCount") }
                )
            }
        }

        if (filtered.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center) {
                    Text(
                        if (records.isEmpty()) "暂无下载记录" else "当前筛选下无记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(display, key = { it.id }) { record ->
                RecordItem(record = record, formatter = formatter)
            }
            if (filtered.size > display.size) {
                item {
                    Text(
                        "仅展示最近 ${display.size} 条，共 ${filtered.size} 条",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordItem(record: DanmuDownloadRecord, formatter: SimpleDateFormat) {
    val status = record.statusEnum()
    val color = when (status) {
        DownloadRecordStatus.Success -> Color(0xFF2E7D32)
        DownloadRecordStatus.Failed -> Color(0xFFC62828)
        DownloadRecordStatus.Skipped -> Color(0xFFF57C00)
    }
    val icon = when (status) {
        DownloadRecordStatus.Success -> Icons.Rounded.TaskAlt
        DownloadRecordStatus.Failed -> Icons.Rounded.ErrorOutline
        DownloadRecordStatus.Skipped -> Icons.Rounded.DownloadDone
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Status icon
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = color.copy(alpha = 0.12f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(icon, null, Modifier.size(16.dp), tint = color)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${record.animeTitle} · E${record.episodeNo}",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(status.label, style = MaterialTheme.typography.labelSmall, color = color)
                }
                Text(
                    record.episodeTitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${formatter.format(Date(record.createdAt))} · ${record.formatEnum().label} · ${record.source}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val detail = when {
                    record.relativePath.isNotBlank() -> record.relativePath
                    !record.errorMessage.isNullOrBlank() -> record.errorMessage
                    else -> null
                }
                if (detail != null) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Shared Components
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ThrottleHintBanner(hint: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                hint,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun StatBadge(
    label: String,
    value: Int,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.24f))
    ) {
        Text(
            "$label $value",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  Color Helpers
// ═══════════════════════════════════════════════════════════════

@Composable
private fun primaryActionButtonColors() = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.primary,
    contentColor = MaterialTheme.colorScheme.onPrimary,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
)

@Composable
private fun primaryActionIconButtonColors() = IconButtonDefaults.filledIconButtonColors(
    containerColor = MaterialTheme.colorScheme.primary,
    contentColor = MaterialTheme.colorScheme.onPrimary,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
)

@Composable
private fun primarySelectionFilterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
)
