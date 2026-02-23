package com.example.danmuapiapp.ui.screen.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.example.danmuapiapp.domain.model.DownloadRecordStatus
import com.example.danmuapiapp.domain.model.renderFileNameTemplatePreview
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DanmuDownloadScreen(
    onBack: () -> Unit,
    onOpenDownloadSettings: () -> Unit,
    viewModel: DanmuDownloadViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val queueTasks by viewModel.queueTasks.collectAsStateWithLifecycle()
    val inEpisodeDetail = viewModel.currentAnime != null
    val visibleEpisodes = viewModel.visibleEpisodes()
    val episodeSummary = if (inEpisodeDetail) viewModel.visibleStateSummary() else EpisodeStateSummary()
    val queueSummary = viewModel.queueSummary()
    val queueCompleted = viewModel.queueCompletedCount()
    val queueRunningText = viewModel.queueRunningStatusText()
    val animeQueueGroups = viewModel.animeQueueGroups()
    val selectedVisibleCount = if (inEpisodeDetail) {
        visibleEpisodes.count { viewModel.selectedEpisodeIds.contains(it.episodeId) }
    } else {
        0
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var configExpanded by rememberSaveable { mutableStateOf(false) }

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
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledIconButton(
                        onClick = {
                            if (inEpisodeDetail && !viewModel.isDownloading) {
                                viewModel.backToAnimeList()
                            } else {
                                onBack()
                            }
                        },
                        colors = primaryActionIconButtonColors(),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", Modifier.size(18.dp))
                    }
                    Column {
                        Text(
                            if (inEpisodeDetail) viewModel.currentAnime?.title ?: "弹幕下载" else "弹幕下载",
                            style = MaterialTheme.typography.headlineLarge,
                            maxLines = 1
                        )
                        Text(
                            if (inEpisodeDetail) {
                                "可见 ${visibleEpisodes.size} 集 · 已选 ${viewModel.selectedEpisodeIds.size} 集"
                            } else {
                                "批量下载弹幕到本地目录"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                FilledIconButton(
                    onClick = onOpenDownloadSettings,
                    colors = primaryActionIconButtonColors(),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Rounded.Settings, "下载设置", Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
            ) {
                item {
                    DownloadConfigCard(
                        expanded = configExpanded,
                        onToggleExpanded = { configExpanded = !configExpanded },
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
                        conflictPolicyLabel = settings.policy().label
                    )
                }

                item {
                    QueueSummaryCard(
                        summary = queueSummary.copy(total = queueTasks.size),
                        completed = queueCompleted,
                        runningText = queueRunningText,
                        groups = animeQueueGroups,
                        busy = viewModel.isDownloading,
                        onResume = viewModel::resumePendingQueue,
                        onClearCompleted = viewModel::clearCompletedQueueTasks,
                        onClearQueue = viewModel::clearQueueTasks
                    )
                }

                if (!inEpisodeDetail) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    "搜索动漫",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = viewModel.keyword,
                                    onValueChange = viewModel::updateKeyword,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("关键词") },
                                    placeholder = { Text("示例：凡人修仙传") },
                                    singleLine = true
                                )
                                Button(
                                    onClick = viewModel::searchAnime,
                                    enabled = !viewModel.isSearching,
                                    colors = primaryActionButtonColors(),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (viewModel.isSearching) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("搜索中...")
                                    } else {
                                        Icon(Icons.Rounded.Search, null, Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("搜索")
                                    }
                                }
                                if (viewModel.isSearching) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                } else if (viewModel.hasSearchedAnime && viewModel.animeCandidates.isEmpty()) {
                                    Text(
                                        "未找到匹配动漫",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (viewModel.animeCandidates.isNotEmpty()) {
                        item {
                            Text(
                                "搜索结果",
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
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        anime.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 2
                                    )
                                    Text(
                                        "AnimeID: ${anime.animeId} · 官方 ${anime.episodeCount} 集",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChip(
                                            selected = viewModel.sourceFilter == null,
                                            onClick = { viewModel.selectSourceFilter(null) },
                                            colors = primarySelectionFilterChipColors(),
                                            label = { Text("全部来源") }
                                        )
                                        viewModel.sourceOptions().forEach { source ->
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
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        EpisodeStatBadge(
                                            label = "成功",
                                            value = episodeSummary.success,
                                            color = Color(0xFF2E7D32)
                                        )
                                        EpisodeStatBadge(
                                            label = "失败",
                                            value = episodeSummary.failed,
                                            color = Color(0xFFC62828)
                                        )
                                        EpisodeStatBadge(
                                            label = "跳过",
                                            value = episodeSummary.skipped,
                                            color = Color(0xFFF57C00)
                                        )
                                        EpisodeStatBadge(
                                            label = "未完成",
                                            value = episodeSummary.unfinished
                                        )
                                    }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = viewModel::toggleSelectAllVisible,
                                            enabled = !viewModel.isDownloading,
                                            colors = primaryActionButtonColors()
                                        ) {
                                            Icon(Icons.Rounded.Check, null, Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("全选/反选可见")
                                        }
                                        OutlinedButton(
                                            onClick = viewModel::clearSelection,
                                            enabled = !viewModel.isDownloading
                                        ) {
                                            Icon(Icons.Rounded.ClearAll, null, Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("清空选择")
                                        }
                                        OutlinedButton(
                                            onClick = viewModel::selectFailedVisibleEpisodes,
                                            enabled = !viewModel.isDownloading
                                        ) {
                                            Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("选择失败")
                                        }
                                        OutlinedButton(
                                            onClick = viewModel::selectUnfinishedVisibleEpisodes,
                                            enabled = !viewModel.isDownloading
                                        ) {
                                            Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("选择未完成")
                                        }
                                        Button(
                                            onClick = viewModel::retryFailedVisibleEpisodes,
                                            enabled = !viewModel.isDownloading && episodeSummary.failed > 0,
                                            colors = primaryActionButtonColors()
                                        ) {
                                            Icon(Icons.Rounded.RestartAlt, null, Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("重试失败项")
                                        }
                                    }
                                    Button(
                                        onClick = viewModel::startDownloadSelectedEpisodes,
                                        enabled = !viewModel.isDownloading && visibleEpisodes.isNotEmpty(),
                                        colors = primaryActionButtonColors(),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Rounded.CloudDownload, null, Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("下载已选剧集（$selectedVisibleCount）")
                                    }
                                    if (viewModel.isDownloading) {
                                        OutlinedButton(
                                            onClick = viewModel::cancelDownload,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("取消下载")
                                        }
                                    }
                                    LinearProgressIndicator(
                                        progress = { viewModel.overallProgress.coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        viewModel.progressSummary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (visibleEpisodes.isEmpty()) {
                            item {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(22.dp),
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
                            items(visibleEpisodes, key = { it.episodeId }) { episode ->
                                EpisodeDownloadRow(
                                    episode = episode,
                                    selected = viewModel.selectedEpisodeIds.contains(episode.episodeId),
                                    state = viewModel.episodeUiState(episode.episodeId),
                                    enabled = !viewModel.isDownloading,
                                    onSelect = { viewModel.toggleEpisodeSelection(episode.episodeId) }
                                )
                            }
                        }
                    }
                }

                item {
                    DownloadRecordSection(
                        records = records,
                        onClear = viewModel::clearRecords
                    )
                }
            }
        }
    }

    if (viewModel.errorMessage != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("操作失败") },
            text = { Text(viewModel.errorMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) {
                    Text("知道了")
                }
            }
        )
    }
}

@Composable
private fun DownloadConfigCard(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    sourceBase: String,
    onSourceBaseChange: (String) -> Unit,
    onUseLocal: () -> Unit,
    onUseLan: () -> Unit,
    selectedFormat: DanmuDownloadFormat,
    onSelectFormat: (DanmuDownloadFormat) -> Unit,
    fileNamePreview: String,
    saveDirName: String,
    conflictPolicyLabel: String
) {
    val summary = "保存目录：${saveDirName.ifBlank { "未设置" }} · 格式：${selectedFormat.label} · 冲突策略：$conflictPolicyLabel"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        "下载配置",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (expanded) "已展开高级配置" else "默认折叠，按需展开",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onToggleExpanded) {
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        null,
                        Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (expanded) "收起" else "展开")
                }
            }
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (expanded) {
                OutlinedTextField(
                    value = sourceBase,
                    onValueChange = onSourceBaseChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("弹幕源 Base URL") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    leadingIcon = { Icon(Icons.Rounded.Link, null) }
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
                    "命名模板请在“下载设置”中统一配置，避免重复设置入口。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "当前命名规则示例：凡人修仙传/$fileNamePreview",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QueueSummaryCard(
    summary: DownloadQueueSummary,
    completed: Int,
    runningText: String,
    groups: List<AnimeQueueGroup>,
    busy: Boolean,
    onResume: () -> Unit,
    onClearCompleted: () -> Unit,
    onClearQueue: () -> Unit
) {
    val overallProgress = if (summary.total <= 0) 0f else completed.toFloat() / summary.total.toFloat()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "任务队列",
                style = MaterialTheme.typography.labelLarge
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EpisodeStatBadge(label = "待处理", value = summary.pending)
                EpisodeStatBadge(label = "下载中", value = summary.running)
                EpisodeStatBadge(label = "失败", value = summary.failed, color = Color(0xFFC62828))
                EpisodeStatBadge(label = "成功", value = summary.success, color = Color(0xFF2E7D32))
                EpisodeStatBadge(label = "跳过", value = summary.skipped, color = Color(0xFFF57C00))
            }
            Text(
                "总任务 ${summary.total} · 活跃 ${summary.active}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LinearProgressIndicator(
                progress = { overallProgress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "总体进度：$completed/${summary.total.coerceAtLeast(0)} · $runningText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (groups.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { group ->
                        AnimeQueueGroupRow(group = group)
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onResume,
                    enabled = !busy && summary.pending > 0,
                    colors = primaryActionButtonColors()
                ) {
                    Icon(Icons.Rounded.CloudDownload, null, Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("恢复队列")
                }
                OutlinedButton(
                    onClick = onClearCompleted,
                    enabled = !busy
                ) {
                    Icon(Icons.Rounded.ClearAll, null, Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("清理已完成")
                }
                OutlinedButton(
                    onClick = onClearQueue,
                    enabled = !busy
                ) {
                    Icon(Icons.Rounded.RestartAlt, null, Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("清空队列")
                }
            }
        }
    }
}

@Composable
private fun AnimeQueueGroupRow(group: AnimeQueueGroup) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                group.animeTitle,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            LinearProgressIndicator(
                progress = { group.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "进度 ${group.completed}/${group.total} · 待处理${group.pending} · 下载中${group.running} · 失败${group.failed}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val stateText = when {
                group.runningEpisodeNo != null -> "正在下载第${group.runningEpisodeNo}集"
                group.pending > 0 -> "排队待下载"
                group.failed > 0 -> "已结束（含失败）"
                else -> "已结束"
            }
            Text(
                "$stateText · ${group.detail.ifBlank { "无详情" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(anime.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    "AnimeID:${anime.animeId} · ${anime.episodeCount} 集",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Rounded.Search, null, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun EpisodeDownloadRow(
    episode: DownloadEpisodeCandidate,
    selected: Boolean,
    state: EpisodeDownloadUiState,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    val (statusText, statusColor) = when (state.state) {
        EpisodeDownloadState.Idle -> "未开始" to MaterialTheme.colorScheme.onSurfaceVariant
        EpisodeDownloadState.Queued -> "排队中" to MaterialTheme.colorScheme.onSurfaceVariant
        EpisodeDownloadState.Running -> "下载中" to MaterialTheme.colorScheme.primary
        EpisodeDownloadState.Success -> "成功" to Color(0xFF2E7D32)
        EpisodeDownloadState.Failed -> "失败" to Color(0xFFC62828)
        EpisodeDownloadState.Skipped -> "跳过" to Color(0xFFF57C00)
        EpisodeDownloadState.Canceled -> "已取消" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        ),
        onClick = { if (enabled) onSelect() }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = selected,
                        onCheckedChange = { if (enabled) onSelect() },
                        enabled = enabled
                    )
                    Column {
                        Text(
                            "第${episode.episodeNumber}集  ${episode.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "EID:${episode.episodeId} · 来源:${episode.source}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }
            if (state.state == EpisodeDownloadState.Running) {
                LinearProgressIndicator(
                    progress = { state.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (state.detail.isNotBlank()) {
                Text(
                    state.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EpisodeStatBadge(
    label: String,
    value: Int,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.14f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            color.copy(alpha = 0.28f)
        )
    ) {
        Text(
            "$label $value",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

private enum class DownloadRecordFilter(val label: String) {
    All("全部"),
    Success("成功"),
    Failed("失败"),
    Skipped("跳过")
}

@Composable
private fun DownloadRecordSection(
    records: List<DanmuDownloadRecord>,
    onClear: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    var filter by rememberSaveable { mutableStateOf(DownloadRecordFilter.All) }
    val successCount = records.count { it.statusEnum() == DownloadRecordStatus.Success }
    val failedCount = records.count { it.statusEnum() == DownloadRecordStatus.Failed }
    val skippedCount = records.count { it.statusEnum() == DownloadRecordStatus.Skipped }
    val filteredRecords = when (filter) {
        DownloadRecordFilter.All -> records
        DownloadRecordFilter.Success -> records.filter { it.statusEnum() == DownloadRecordStatus.Success }
        DownloadRecordFilter.Failed -> records.filter { it.statusEnum() == DownloadRecordStatus.Failed }
        DownloadRecordFilter.Skipped -> records.filter { it.statusEnum() == DownloadRecordStatus.Skipped }
    }
    val displayRecords = filteredRecords.take(80)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "下载记录（本地落地）",
                    style = MaterialTheme.typography.titleSmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onClear, enabled = records.isNotEmpty()) {
                        Icon(Icons.Rounded.ClearAll, "清空记录")
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filter == DownloadRecordFilter.All,
                    onClick = { filter = DownloadRecordFilter.All },
                    colors = primarySelectionFilterChipColors(),
                    label = { Text("全部 ${records.size}") }
                )
                FilterChip(
                    selected = filter == DownloadRecordFilter.Success,
                    onClick = { filter = DownloadRecordFilter.Success },
                    colors = primarySelectionFilterChipColors(),
                    label = { Text("成功 $successCount") }
                )
                FilterChip(
                    selected = filter == DownloadRecordFilter.Failed,
                    onClick = { filter = DownloadRecordFilter.Failed },
                    colors = primarySelectionFilterChipColors(),
                    label = { Text("失败 $failedCount") }
                )
                FilterChip(
                    selected = filter == DownloadRecordFilter.Skipped,
                    onClick = { filter = DownloadRecordFilter.Skipped },
                    colors = primarySelectionFilterChipColors(),
                    label = { Text("跳过 $skippedCount") }
                )
            }

            if (filteredRecords.isEmpty()) {
                Text(
                    if (records.isEmpty()) "暂无下载记录" else "当前筛选下无记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                displayRecords.forEach { record ->
                    val status = record.statusEnum()
                    val color = when (status) {
                        DownloadRecordStatus.Success -> Color(0xFF2E7D32)
                        DownloadRecordStatus.Failed -> Color(0xFFC62828)
                        DownloadRecordStatus.Skipped -> Color(0xFFF57C00)
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${record.animeTitle} · E${record.episodeNo}",
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        when (status) {
                                            DownloadRecordStatus.Success -> Icons.Rounded.TaskAlt
                                            DownloadRecordStatus.Failed -> Icons.Rounded.ErrorOutline
                                            DownloadRecordStatus.Skipped -> Icons.Rounded.DownloadDone
                                        },
                                        null,
                                        tint = color,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        status.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = color
                                    )
                                }
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val detail = when {
                                record.relativePath.isNotBlank() -> record.relativePath
                                !record.errorMessage.isNullOrBlank() -> record.errorMessage
                                else -> "无详情"
                            }
                            Text(
                                detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (filteredRecords.size > displayRecords.size) {
                    Text(
                        "仅展示最近 ${displayRecords.size} 条，当前筛选共有 ${filteredRecords.size} 条",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

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
