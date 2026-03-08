package com.example.danmuapiapp.ui.screen.download

import com.example.danmuapiapp.ui.component.AppBottomSheetDialog
import com.example.danmuapiapp.ui.component.AppBottomSheetStyle
import com.example.danmuapiapp.ui.component.AppBottomSheetTone

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
internal fun SearchDownloadPage(
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
internal fun CompactConfigSection(
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

internal fun buildEpisodeStateSummaryFromMap(
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
internal fun AnimeInfoHeader(
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
internal fun AnimeEntryRow(
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
internal fun EpisodeRow(
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

