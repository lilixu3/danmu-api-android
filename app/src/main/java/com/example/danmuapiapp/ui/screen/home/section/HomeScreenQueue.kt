@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.danmuapiapp.ui.screen.home

import com.example.danmuapiapp.ui.component.AppBottomSheetDialog
import com.example.danmuapiapp.ui.component.AppBottomSheetStyle
import com.example.danmuapiapp.ui.component.AppBottomSheetTone

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CacheEntry
import com.example.danmuapiapp.domain.model.CacheStats
import com.example.danmuapiapp.domain.model.DownloadQueueStatus
import com.example.danmuapiapp.domain.model.DanmuDownloadTask
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.ui.component.GithubProxyPickerDialog
import com.example.danmuapiapp.ui.component.GradientButton
import com.example.danmuapiapp.ui.component.StatusIndicator
import com.example.danmuapiapp.ui.screen.download.DanmuDownloadViewModel
import com.example.danmuapiapp.ui.screen.download.DownloadQueueSummary
import com.example.danmuapiapp.ui.theme.appDangerTonalButtonColors
import com.example.danmuapiapp.ui.theme.appPrimaryButtonColors
import com.example.danmuapiapp.ui.theme.appTonalButtonColors
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
internal fun DownloadQueueSheet(
    queueSummary: DownloadQueueSummary,
    queueLiveProgress: Float,
    queueStatusText: String,
    queueRunningDetail: String,
    queueProgressSummary: String,
    queueThrottleHint: String?,
    isQueueDownloading: Boolean,
    isQueuePaused: Boolean,
    queueDialogGroups: List<DownloadQueueDialogGroup>,
    expandedQueueGroupKeys: Set<String>,
    onExpandedQueueGroupKeysChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    onOpenDownloadPage: () -> Unit,
    onTogglePauseResume: () -> Unit,
    onClearQueue: () -> Unit
) {
    val completed = queueSummary.success + queueSummary.skipped + queueSummary.canceled
    val displayProgress = queueLiveProgress.coerceIn(0f, 1f)
    val displayPercent = (displayProgress * 100f).toInt().coerceIn(0, 100)
    val showRunningDetail = queueRunningDetail.isNotBlank() &&
        queueRunningDetail != "当前无运行中的任务"
    val accentColor = if (isQueueDownloading) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    val queueSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val queueSheetMaxHeight = (screenHeight * 0.9f).coerceAtLeast(320.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = queueSheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.34f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = queueSheetMaxHeight)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(top = 4.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor.copy(alpha = 0.14f)
                ) {
                    Box(
                        modifier = Modifier.size(36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DownloadForOffline,
                            contentDescription = null,
                            tint = accentColor
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "下载任务队列",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "总任务 ${queueSummary.total} · 待处理 ${queueSummary.pending} · 运行 ${queueSummary.running}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Status badges
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        isQueueDownloading -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        isQueuePaused -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    }
                ) {
                    Text(
                        queueStatusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isQueueDownloading -> MaterialTheme.colorScheme.primary
                            isQueuePaused -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        "总进度 $displayPercent%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Progress bar
            LinearProgressIndicator(
                progress = { displayProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp)),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                color = accentColor
            )
            Text(
                text = "已完成 $completed/${queueSummary.total.coerceAtLeast(0)} · 失败 ${queueSummary.failed}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (showRunningDetail) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = queueRunningDetail,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = queueProgressSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!queueThrottleHint.isNullOrBlank()) {
                Text(
                    text = queueThrottleHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Metric badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QueueMetricBadge(modifier = Modifier.weight(1f), label = "待处理", value = queueSummary.pending)
                QueueMetricBadge(modifier = Modifier.weight(1f), label = "运行", value = queueSummary.running)
                QueueMetricBadge(modifier = Modifier.weight(1f), label = "完成", value = completed)
                QueueMetricBadge(modifier = Modifier.weight(1f), label = "失败", value = queueSummary.failed)
            }

            // Task groups
            val sheetScroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(sheetScroll),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (queueDialogGroups.isEmpty()) {
                    Text(
                        text = "队列暂无任务",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "任务分组",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    queueDialogGroups.forEach { group ->
                        val expanded = expandedQueueGroupKeys.contains(group.key)
                        DownloadQueueGroupCard(
                            group = group,
                            expanded = expanded,
                            onToggle = {
                                onExpandedQueueGroupKeysChange(
                                    if (expanded) {
                                        expandedQueueGroupKeys - group.key
                                    } else {
                                        expandedQueueGroupKeys + group.key
                                    }
                                )
                            }
                        )
                    }
                }
            }

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DialogActionButton(
                    text = "下载页",
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    onClick = onOpenDownloadPage
                )
                DialogActionButton(
                    text = if (isQueueDownloading) "暂停" else "继续",
                    icon = if (isQueueDownloading) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    onClick = onTogglePauseResume,
                    enabled = if (isQueueDownloading) true else isQueuePaused,
                    primary = isQueueDownloading || isQueuePaused
                )
                DialogActionButton(
                    text = "清空",
                    icon = Icons.Rounded.CloudOff,
                    onClick = onClearQueue,
                    enabled = !isQueueDownloading && queueSummary.total > 0
                )
            }
        }
    }
}

internal data class DownloadQueueDialogGroup(
    val key: String,
    val animeTitle: String,
    val total: Int,
    val pending: Int,
    val running: Int,
    val completed: Int,
    val failed: Int,
    val tasks: List<DanmuDownloadTask>
)

internal fun buildDownloadQueueGroups(tasks: List<DanmuDownloadTask>): List<DownloadQueueDialogGroup> {
    if (tasks.isEmpty()) return emptyList()

    val indexed = tasks.withIndex()
    val grouped = indexed.groupBy { indexedTask ->
        indexedTask.value.animeTitle.trim().ifBlank { "未命名剧集" }
    }
    return grouped.entries
        .map { (title, entries) ->
            val rawTasks = entries.map { it.value }
            var pending = 0
            var running = 0
            var success = 0
            var skipped = 0
            var canceled = 0
            var failed = 0
            rawTasks.forEach { task ->
                when (task.statusEnum()) {
                    DownloadQueueStatus.Pending -> pending++
                    DownloadQueueStatus.Running -> running++
                    DownloadQueueStatus.Success -> success++
                    DownloadQueueStatus.Skipped -> skipped++
                    DownloadQueueStatus.Canceled -> canceled++
                    DownloadQueueStatus.Failed -> failed++
                }
            }
            DownloadQueueDialogGroup(
                key = title,
                animeTitle = title,
                total = rawTasks.size,
                pending = pending,
                running = running,
                completed = success + skipped + canceled,
                failed = failed,
                tasks = rawTasks.sortedWith(
                    compareBy<DanmuDownloadTask> { it.episodeNo }
                        .thenBy { it.source.lowercase() }
                        .thenByDescending { it.updatedAt }
                )
            ) to (entries.minOfOrNull { it.index } ?: Int.MAX_VALUE)
        }
        .sortedBy { it.second }
        .map { it.first }
}

@Composable
internal fun DownloadQueueGroupCard(
    group: DownloadQueueDialogGroup,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = group.animeTitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = "共 ${group.total} · 待 ${group.pending} · 运行 ${group.running} · 完成 ${group.completed} · 失败 ${group.failed}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    group.tasks.forEach { task ->
                        DownloadQueueTaskRow(task = task)
                    }
                }
            }
        }
    }
}

@Composable
internal fun QueueMetricBadge(
    modifier: Modifier = Modifier,
    label: String,
    value: Int
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.78f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value.coerceAtLeast(0).toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
internal fun DownloadQueueTaskRow(task: DanmuDownloadTask) {
    val status = task.statusEnum()
    val statusText = when (status) {
        DownloadQueueStatus.Pending -> "待处理"
        DownloadQueueStatus.Running -> "运行中"
        DownloadQueueStatus.Success -> "成功"
        DownloadQueueStatus.Failed -> "失败"
        DownloadQueueStatus.Skipped -> "跳过"
        DownloadQueueStatus.Canceled -> "已取消"
    }
    val statusColor = when (status) {
        DownloadQueueStatus.Pending -> MaterialTheme.colorScheme.tertiary
        DownloadQueueStatus.Running -> MaterialTheme.colorScheme.primary
        DownloadQueueStatus.Success -> Color(0xFF2E7D32)
        DownloadQueueStatus.Failed -> MaterialTheme.colorScheme.error
        DownloadQueueStatus.Skipped,
        DownloadQueueStatus.Canceled -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val displayTitle = task.animeTitle.trim().ifBlank { "未命名剧集" }
    val displaySource = task.source.trim().ifBlank { "unknown" }
    val detailText = task.lastDetail.trim()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$displayTitle · 第${task.episodeNo}集",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = statusColor.copy(alpha = 0.13f)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Text(
                text = "来源 $displaySource · 尝试 ${task.attempts} 次",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            if (detailText.isNotBlank()) {
                Text(
                    text = detailText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}
