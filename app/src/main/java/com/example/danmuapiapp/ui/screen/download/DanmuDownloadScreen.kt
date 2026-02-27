package com.example.danmuapiapp.ui.screen.download

import com.example.danmuapiapp.ui.component.AppBottomSheetDialog

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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import com.example.danmuapiapp.domain.model.DanmuDownloadRecord
import com.example.danmuapiapp.domain.model.DownloadQueueStatus
import com.example.danmuapiapp.domain.model.DownloadRecordStatus
import com.example.danmuapiapp.domain.model.renderFileNameTemplatePreview
import com.example.danmuapiapp.ui.theme.appDangerButtonColors
import com.example.danmuapiapp.ui.theme.appPrimaryButtonColors
import com.example.danmuapiapp.ui.theme.appPrimaryIconButtonColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 16.dp, bottom = 8.dp),
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
                                if (inEpisodeDetail) viewModel.backToAnimeList() else onBack()
                            },
                            colors = primaryActionIconButtonColors(),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", Modifier.size(18.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (inEpisodeDetail) {
                                    viewModel.currentAnime?.title ?: "弹幕下载"
                                } else {
                                    "弹幕下载中心"
                                },
                                style = MaterialTheme.typography.headlineLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (inEpisodeDetail) {
                                    "剧集选择与批量下载"
                                } else {
                                    "搜索动漫并管理下载队列"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    FilledIconButton(
                        onClick = onOpenDownloadSettings,
                        colors = primaryActionIconButtonColors(),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Rounded.Settings, "下载设置", Modifier.size(18.dp))
                    }
                }

                DownloadPanelCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatBadge("活动", queueSummary.active)
                        StatBadge("队列", queueTasks.size)
                        StatBadge("记录", records.size, MaterialTheme.colorScheme.tertiary)
                    }
                    Spacer(Modifier.height(6.dp))
                    val summaryText = when {
                        queueSummary.running > 0 -> "队列运行中：${viewModel.queueRunningStatusText()}"
                        queueSummary.pending > 0 -> "队列已暂停，剩余 ${queueSummary.pending} 项待处理"
                        else -> "当前队列空闲，可在搜索页选择剧集后开始下载"
                    }
                    Text(
                        summaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.78f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f)
                    )
                ) {
                    val queueActive = queueSummary.active
                    PrimaryTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("搜索下载") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("任务队列")
                                    if (queueActive > 0) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Text("$queueActive")
                                        }
                                    }
                                }
                            }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("下载记录")
                                    if (records.isNotEmpty()) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.tertiary,
                                            contentColor = MaterialTheme.colorScheme.onTertiary
                                        ) {
                                            Text("${records.size}")
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> SearchDownloadPage(viewModel = viewModel, settings = settings)
                        1 -> QueuePage(viewModel = viewModel, queueTasks = queueTasks)
                        2 -> RecordsPage(records = records, onClear = viewModel::clearRecords)
                    }
                }
            }

            if (inEpisodeDetail) {
                val selectedCount = viewModel
                    .visibleEpisodes()
                    .count { viewModel.selectedEpisodeIds.contains(it.episodeId) }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                    tonalElevation = 6.dp,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "已选 $selectedCount 集",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "支持跨来源混合下载，重复项会自动去重",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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

    if (viewModel.errorMessage != null) {
        AppBottomSheetDialog(
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

@Composable
private fun SearchDownloadPage(
    viewModel: DanmuDownloadViewModel,
    settings: com.example.danmuapiapp.domain.model.DanmuDownloadSettings
) {
    val inDetail = viewModel.currentAnime != null
    val visibleEpisodes = viewModel.visibleEpisodes()
    val episodeSummary = if (inDetail) {
        buildEpisodeStateSummaryFromMap(visibleEpisodes, viewModel.episodeStates)
    } else {
        EpisodeStateSummary()
    }
    var configExpanded by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 2.dp,
            bottom = if (inDetail) 96.dp else 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
            item {
                DownloadPanelCard {
                    Text("动漫检索", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = viewModel.keyword,
                        onValueChange = viewModel::updateKeyword,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例如：凡人修仙传") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (viewModel.isSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(onClick = viewModel::searchAnime) {
                                    Icon(Icons.Rounded.Search, "搜索")
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp)
                    )
                    if (viewModel.isSearching) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "支持关键词模糊匹配，点选结果进入剧集下载页",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!viewModel.isSearching && viewModel.hasSearchedAnime && viewModel.animeCandidates.isEmpty()) {
                item {
                    DownloadPanelCard {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "未找到匹配动漫",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (viewModel.animeCandidates.isNotEmpty()) {
                item {
                    Text(
                        "搜索结果 ${viewModel.animeCandidates.size} 项",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
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
            val anime = viewModel.currentAnime
            if (anime != null) {
                item {
                    AnimeInfoHeader(
                        anime = anime,
                        visibleCount = visibleEpisodes.size,
                        sourceOptions = viewModel.sourceOptions(),
                        sourceFilter = viewModel.sourceFilter,
                        onSelectSource = viewModel::selectSourceFilter,
                        summary = episodeSummary,
                        isDownloading = viewModel.isDownloading,
                        onBackToSearch = viewModel::backToAnimeList,
                        onToggleSelectAll = viewModel::toggleSelectAllVisible,
                        onSelectFailed = viewModel::selectFailedVisibleEpisodes,
                        onSelectUnfinished = viewModel::selectUnfinishedVisibleEpisodes,
                        onClearSelection = viewModel::clearSelection,
                        onRetryFailed = if (episodeSummary.failed > 0) {
                            viewModel::retryFailedVisibleEpisodes
                        } else {
                            null
                        }
                    )
                }

                if (viewModel.isDownloading || viewModel.overallProgress > 0f) {
                    item {
                        DownloadPanelCard {
                            Text("下载进度", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { viewModel.overallProgress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                viewModel.progressSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                val hint = viewModel.throttleHint
                if (hint != null) {
                    item { ThrottleHintBanner(hint) }
                }

                if (viewModel.isLoadingEpisodes) {
                    item {
                        DownloadPanelCard {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        "正在加载剧集…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else if (visibleEpisodes.isEmpty()) {
                    item {
                        DownloadPanelCard {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "当前来源筛选下暂无剧集",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(
                        visibleEpisodes,
                        key = { episode ->
                            "${episode.source}|${episode.episodeId}|${episode.episodeNumber}|${episode.title}"
                        }
                    ) { episode ->
                        EpisodeRow(
                            episode = episode,
                            selected = viewModel.selectedEpisodeIds.contains(episode.episodeId),
                            state = viewModel.episodeUiState(episode),
                            enabled = !viewModel.isDownloading,
                            onSelect = { viewModel.toggleEpisodeSelection(episode.episodeId) }
                        )
                    }
                }
            }
        }
    }
}

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
    DownloadPanelCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Tune,
                            null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("下载配置", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${saveDirName.ifBlank { "未设置目录" }} · ${selectedFormat.label} · $conflictLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    "展开配置",
                    Modifier.size(18.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(onClick = onUseLocal, label = { Text("本机地址") })
                    AssistChip(onClick = onUseLan, label = { Text("局域网地址") })
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DanmuDownloadFormat.entries.forEach { format ->
                        FilterChip(
                            selected = selectedFormat == format,
                            onClick = { onSelectFormat(format) },
                            colors = primarySelectionFilterChipColors(),
                            label = { Text(format.label) },
                            leadingIcon = if (selectedFormat == format) {
                                { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                            } else {
                                null
                            }
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

        Spacer(Modifier.height(2.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.52f)
        ) {
            Text(
                text = "命名示例：凡人修仙传/$fileNamePreview",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun buildEpisodeStateSummaryFromMap(
    episodes: List<DownloadEpisodeCandidate>,
    stateMap: Map<Long, EpisodeDownloadUiState>
): EpisodeStateSummary {
    var idle = 0
    var queued = 0
    var running = 0
    var success = 0
    var failed = 0
    var skipped = 0
    var canceled = 0
    episodes.forEach { episode ->
        when (stateMap[episode.episodeId]?.state ?: EpisodeDownloadState.Idle) {
            EpisodeDownloadState.Idle -> idle++
            EpisodeDownloadState.Queued -> queued++
            EpisodeDownloadState.Running -> running++
            EpisodeDownloadState.Success -> success++
            EpisodeDownloadState.Failed -> failed++
            EpisodeDownloadState.Skipped -> skipped++
            EpisodeDownloadState.Canceled -> canceled++
        }
    }
    return EpisodeStateSummary(
        total = episodes.size,
        idle = idle,
        queued = queued,
        running = running,
        success = success,
        failed = failed,
        skipped = skipped,
        canceled = canceled
    )
}

@Composable
private fun AnimeInfoHeader(
    anime: DownloadAnimeCandidate,
    visibleCount: Int,
    sourceOptions: List<String>,
    sourceFilter: String?,
    onSelectSource: (String?) -> Unit,
    summary: EpisodeStateSummary,
    isDownloading: Boolean,
    onBackToSearch: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onSelectFailed: () -> Unit,
    onSelectUnfinished: () -> Unit,
    onClearSelection: () -> Unit,
    onRetryFailed: (() -> Unit)?
) {
    DownloadPanelCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(anime.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(
                    "AnimeID: ${anime.animeId} · 官方 ${anime.episodeCount} 集 · 当前可见 $visibleCount 集",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FilledIconButton(
                onClick = onBackToSearch,
                colors = primaryActionIconButtonColors(),
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    "返回搜索",
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (sourceOptions.size > 1) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = sourceFilter == null,
                    onClick = { onSelectSource(null) },
                    colors = primarySelectionFilterChipColors(),
                    label = { Text("全部来源") }
                )
                sourceOptions.forEach { source ->
                    FilterChip(
                        selected = sourceFilter == source,
                        onClick = { onSelectSource(source) },
                        colors = primarySelectionFilterChipColors(),
                        label = { Text(source) },
                        leadingIcon = if (sourceFilter == source) {
                            { Icon(Icons.Rounded.FilterAlt, null, Modifier.size(16.dp)) }
                        } else {
                            null
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatBadge("成功", summary.success, Color(0xFF2E7D32))
            StatBadge("失败", summary.failed, Color(0xFFC62828))
            StatBadge("跳过", summary.skipped, Color(0xFFF57C00))
            StatBadge("排队", summary.queued)
            StatBadge("下载中", summary.running, MaterialTheme.colorScheme.primary)
            StatBadge("未完成", summary.unfinished)
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = onToggleSelectAll,
                enabled = !isDownloading,
                label = { Text("全选/反选") },
                leadingIcon = { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
            )
            AssistChip(
                onClick = onSelectFailed,
                enabled = !isDownloading,
                label = { Text("选失败") },
                leadingIcon = { Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(16.dp)) }
            )
            AssistChip(
                onClick = onSelectUnfinished,
                enabled = !isDownloading,
                label = { Text("选未完成") },
                leadingIcon = { Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp)) }
            )
            AssistChip(
                onClick = onClearSelection,
                enabled = !isDownloading,
                label = { Text("清空") },
                leadingIcon = { Icon(Icons.Rounded.ClearAll, null, Modifier.size(16.dp)) }
            )
            if (onRetryFailed != null) {
                AssistChip(
                    onClick = onRetryFailed,
                    enabled = !isDownloading,
                    label = { Text("重试失败") },
                    leadingIcon = { Icon(Icons.Rounded.RestartAlt, null, Modifier.size(16.dp)) }
                )
            }
        }
    }
}

@Composable
private fun AnimeEntryRow(
    anime: DownloadAnimeCandidate,
    loading: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
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
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        "进入",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: DownloadEpisodeCandidate,
    selected: Boolean,
    state: EpisodeDownloadUiState,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    val (statusText, statusColor) = when (state.state) {
        EpisodeDownloadState.Idle -> "待选" to MaterialTheme.colorScheme.onSurfaceVariant
        EpisodeDownloadState.Queued -> "排队" to MaterialTheme.colorScheme.primary
        EpisodeDownloadState.Running -> "下载中" to Color(0xFF1565C0)
        EpisodeDownloadState.Success -> "成功" to Color(0xFF2E7D32)
        EpisodeDownloadState.Failed -> "失败" to Color(0xFFC62828)
        EpisodeDownloadState.Skipped -> "跳过" to Color(0xFFF57C00)
        EpisodeDownloadState.Canceled -> "取消" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
            }
        ),
        onClick = { if (enabled) onSelect() }
    ) {
        Column(
            modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
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
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "来源：${episode.source}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = statusColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        statusText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }
            if (state.state == EpisodeDownloadState.Running) {
                LinearProgressIndicator(
                    progress = { state.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 36.dp)
                )
            }
            if (state.detail.isNotBlank()) {
                Text(
                    state.detail,
                    modifier = Modifier.padding(start = 36.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DownloadPanelCard {
                Text("队列控制台", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatBadge("待处理", summary.pending)
                    StatBadge("下载中", summary.running, MaterialTheme.colorScheme.primary)
                    StatBadge("成功", summary.success, Color(0xFF2E7D32))
                    StatBadge("失败", summary.failed, Color(0xFFC62828))
                    StatBadge("跳过", summary.skipped, Color(0xFFF57C00))
                    StatBadge("取消", summary.canceled)
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { overallProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "进度 $completed/${summary.total.coerceAtLeast(0)} · $runningText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val hint = viewModel.throttleHint
                if (hint != null) {
                    Spacer(Modifier.height(8.dp))
                    ThrottleHintBanner(hint)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (viewModel.isDownloading) {
                        Button(
                            onClick = viewModel::pauseDownload,
                            colors = appDangerButtonColors()
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
                    OutlinedButton(
                        onClick = viewModel::clearCompletedQueueTasks,
                        enabled = !viewModel.isDownloading
                    ) {
                        Text("清理已完成")
                    }
                    OutlinedButton(
                        onClick = viewModel::clearQueueTasks,
                        enabled = !viewModel.isDownloading
                    ) {
                        Text("清空队列")
                    }
                }
            }
        }

        if (groups.isNotEmpty()) {
            item {
                Text(
                    "分组队列（点击展开剧集，箭头调整优先级）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
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
                DownloadPanelCard {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 26.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "队列为空",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
    val accentColor = when {
        group.running > 0 -> MaterialTheme.colorScheme.primary
        group.failed > 0 -> Color(0xFFC62828)
        group.pending > 0 -> MaterialTheme.colorScheme.tertiary
        else -> Color(0xFF2E7D32)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        group.animeTitle,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    LinearProgressIndicator(
                        progress = { group.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = accentColor
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
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatBadge("待", group.pending)
                        StatBadge("跑", group.running, accentColor)
                        StatBadge("成", group.success, Color(0xFF2E7D32))
                        if (group.failed > 0) StatBadge("败", group.failed, Color(0xFFC62828))
                    }
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
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
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
                    color = statusColor.copy(alpha = 0.14f)
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

private enum class RecordFilter(val label: String) {
    All("全部"),
    Success("成功"),
    Failed("失败"),
    Skipped("跳过")
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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            DownloadPanelCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("下载记录", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "最多展示最近 80 条，支持按状态筛选",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onClear,
                        enabled = records.isNotEmpty(),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Rounded.ClearAll, "清空记录", Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
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
        }

        if (filtered.isEmpty()) {
            item {
                DownloadPanelCard {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (records.isEmpty()) "暂无下载记录" else "当前筛选下无记录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
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
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = color.copy(alpha = 0.14f),
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        status.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Text(
                    record.episodeTitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${formatter.format(Date(record.createdAt))} · ${record.formatEnum().label} · ${record.source}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val detail = when {
                    record.relativePath.isNotBlank() -> record.relativePath
                    !record.errorMessage.isNullOrBlank() -> record.errorMessage
                    else -> null
                }
                if (!detail.isNullOrBlank()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ThrottleHintBanner(hint: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.24f)
        )
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
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
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
        border = BorderStroke(1.dp, color.copy(alpha = 0.22f))
    ) {
        Text(
            "$label $value",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun DownloadPanelCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = color,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content
        )
    }
}

@Composable
private fun primaryActionButtonColors() = appPrimaryButtonColors()

@Composable
private fun primaryActionIconButtonColors() = appPrimaryIconButtonColors()

@Composable
private fun primarySelectionFilterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
)
