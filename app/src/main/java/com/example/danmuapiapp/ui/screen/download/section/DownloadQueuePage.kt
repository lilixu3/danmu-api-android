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
internal fun QueuePage(
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
internal fun QueueGroupRow(
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
internal fun QueueEpisodeTaskRow(task: AnimeQueueEpisodeItem) {
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

