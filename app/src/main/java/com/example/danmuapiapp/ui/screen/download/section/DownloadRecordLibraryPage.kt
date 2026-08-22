package com.example.danmuapiapp.ui.screen.download

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.DanmuDownloadRecord
import com.example.danmuapiapp.domain.model.DownloadRecordStatus
import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.AppDialogStyle
import com.example.danmuapiapp.ui.component.AppDialogTone
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassDangerButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassFilterChip
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class RecordDeleteRequest(
    val key: String,
    val title: String,
    val description: String,
    val recordIds: Set<Long>,
    val localFileCount: Int
)

@Composable
internal fun RecordsPage(
    records: List<DanmuDownloadRecord>,
    previewState: DanmuPreviewDialogState,
    isSyncing: Boolean,
    isDeleting: Boolean,
    canMutateRecords: Boolean,
    onDeleteRecords: (Set<Long>, Boolean) -> Unit,
    onSync: () -> Unit,
    onPreviewRecord: (DanmuDownloadRecord) -> Unit,
    onDismissPreview: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val groups = remember(records) { buildDownloadRecordAnimeGroups(records) }
    var filter by rememberSaveable { mutableStateOf(RecordFilter.All) }
    var selectedAnimeKey by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteRequest by remember { mutableStateOf<RecordDeleteRequest?>(null) }
    val selectedGroup = groups.firstOrNull { it.key == selectedAnimeKey }
    val filteredGroups = groups.filter { group ->
        filter == RecordFilter.All || group.records.any { filter.matches(it.statusEnum()) }
    }
    val successCount = records.count { it.statusEnum() == DownloadRecordStatus.Success }
    val failedCount = records.count { it.statusEnum() == DownloadRecordStatus.Failed }
    val skippedCount = records.count { it.statusEnum() == DownloadRecordStatus.Skipped }
    val downloadedEpisodeCount = groups.sumOf { group ->
        group.episodes.count { it.status == DownloadRecordStatus.Success }
    }
    val controlsEnabled = canMutateRecords && !isSyncing && !isDeleting

    LaunchedEffect(selectedAnimeKey, groups) {
        if (selectedAnimeKey != null && selectedGroup == null) {
            selectedAnimeKey = null
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (selectedGroup == null) {
            item {
                DownloadPanelCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("下载记录", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${groups.size} 部剧 · $downloadedEpisodeCount 集已下载 · ${records.size} 条记录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            AppGlassIconButton(
                                onClick = onSync,
                                enabled = controlsEnabled,
                                size = 36.dp
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Rounded.Refresh, "扫描已有弹幕", Modifier.size(19.dp))
                                }
                            }
                            AppGlassIconButton(
                                onClick = {
                                    deleteRequest = records.toDeleteRequest(
                                        key = "all",
                                        title = "清空全部下载记录",
                                        description = "将清理 App 中的 ${records.size} 条下载记录。"
                                    )
                                },
                                enabled = controlsEnabled && records.isNotEmpty(),
                                size = 36.dp
                            ) {
                                if (isDeleting) {
                                    CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Rounded.ClearAll, "清空全部记录", Modifier.size(19.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RecordFilterChip(filter, RecordFilter.All, "全部 ${records.size}") { filter = it }
                        RecordFilterChip(filter, RecordFilter.Success, "成功 $successCount") { filter = it }
                        RecordFilterChip(filter, RecordFilter.Failed, "失败 $failedCount") { filter = it }
                        RecordFilterChip(filter, RecordFilter.Skipped, "跳过 $skippedCount") { filter = it }
                    }
                }
            }

            if (filteredGroups.isEmpty()) {
                item {
                    RecordLibraryEmptyState(
                        text = if (records.isEmpty()) "暂无下载记录" else "当前筛选下没有剧集"
                    )
                }
            } else {
                items(filteredGroups, key = { it.key }) { group ->
                    AnimeRecordGroupRow(
                        group = group,
                        formatter = formatter,
                        enabled = controlsEnabled,
                        onOpen = { selectedAnimeKey = group.key },
                        onDelete = {
                            deleteRequest = group.records.toDeleteRequest(
                                key = "anime-${group.key}",
                                title = "删除《${group.title}》的记录",
                                description = "将清理这部剧的 ${group.episodes.size} 集、${group.records.size} 条 App 记录。"
                            )
                        }
                    )
                }
            }
        } else {
            item {
                DownloadPanelCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppGlassIconButton(
                            onClick = { selectedAnimeKey = null },
                            size = 36.dp
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回剧列表", Modifier.size(19.dp))
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                selectedGroup.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${selectedGroup.episodes.size} 集 · ${selectedGroup.records.size} 条记录 · ${formatLibraryBytes(selectedGroup.totalBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AppGlassIconButton(
                            onClick = {
                                deleteRequest = selectedGroup.records.toDeleteRequest(
                                    key = "anime-${selectedGroup.key}",
                                    title = "删除《${selectedGroup.title}》的记录",
                                    description = "将清理这部剧的 ${selectedGroup.episodes.size} 集、${selectedGroup.records.size} 条 App 记录。"
                                )
                            },
                            enabled = controlsEnabled,
                            size = 36.dp
                        ) {
                            if (isDeleting) {
                                CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    "删除整部剧记录",
                                    Modifier.size(19.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            items(selectedGroup.episodes, key = { it.key }) { episode ->
                EpisodeRecordGroupRow(
                    episode = episode,
                    formatter = formatter,
                    loadingPreview = previewState.loadingRecordId == episode.previewRecord?.id,
                    enabled = controlsEnabled,
                    onPreview = episode.previewRecord?.let { record ->
                        { onPreviewRecord(record) }
                    },
                    onDelete = {
                        val episodeLabel = if (episode.episodeNo > 0) {
                            "第 ${episode.episodeNo} 集"
                        } else {
                            episode.title
                        }
                        deleteRequest = episode.records.toDeleteRequest(
                            key = "episode-${selectedGroup.key}-${episode.key}",
                            title = "删除${episodeLabel}的记录",
                            description = "将清理这一集的 ${episode.records.size} 条 App 记录。"
                        )
                    }
                )
            }
        }
    }

    deleteRequest?.let { request ->
        RecordDeleteDialog(
            request = request,
            onDismiss = { if (!isDeleting) deleteRequest = null },
            onConfirm = { deleteLocalFiles ->
                deleteRequest = null
                onDeleteRecords(request.recordIds, deleteLocalFiles)
            }
        )
    }

    if (previewState.isVisible) {
        DanmuPreviewDialog(state = previewState, onDismiss = onDismissPreview)
    }
}

@Composable
private fun RecordFilterChip(
    selectedFilter: RecordFilter,
    filter: RecordFilter,
    label: String,
    onSelect: (RecordFilter) -> Unit
) {
    AppGlassFilterChip(
        selected = selectedFilter == filter,
        onClick = { onSelect(filter) },
        label = { Text(label) }
    )
}

@Composable
private fun AnimeRecordGroupRow(
    group: DownloadRecordAnimeGroup,
    formatter: SimpleDateFormat,
    enabled: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    AppGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 6.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(9.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Folder, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    group.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${group.episodes.size} 集 · ${group.records.size} 条记录 · 最近 ${formatter.format(Date(group.latestAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val downloadedEpisodes = group.episodes.count { it.status == DownloadRecordStatus.Success }
                    if (downloadedEpisodes > 0) {
                        RecordInlineStat("已下载", downloadedEpisodes, Color(0xFF2E7D32))
                    }
                    if (group.failedCount > 0) RecordInlineStat("失败", group.failedCount, Color(0xFFC62828))
                    if (group.skippedCount > 0) RecordInlineStat("跳过", group.skippedCount, Color(0xFFF57C00))
                }
            }
            AppGlassIconButton(onClick = onDelete, enabled = enabled, size = 34.dp) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    "删除这部剧",
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "查看分集",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EpisodeRecordGroupRow(
    episode: DownloadRecordEpisodeGroup,
    formatter: SimpleDateFormat,
    loadingPreview: Boolean,
    enabled: Boolean,
    onPreview: (() -> Unit)?,
    onDelete: () -> Unit
) {
    val statusColor = when (episode.status) {
        DownloadRecordStatus.Success -> Color(0xFF2E7D32)
        DownloadRecordStatus.Failed -> Color(0xFFC62828)
        DownloadRecordStatus.Skipped -> Color(0xFFF57C00)
    }
    val statusIcon = when (episode.status) {
        DownloadRecordStatus.Success -> Icons.Rounded.DownloadDone
        DownloadRecordStatus.Failed -> Icons.Rounded.ErrorOutline
        DownloadRecordStatus.Skipped -> Icons.Rounded.WarningAmber
    }
    val record = episode.representative

    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 11.dp, end = 6.dp, bottom = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(8.dp),
                color = statusColor.copy(alpha = 0.13f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(statusIcon, null, Modifier.size(17.dp), tint = statusColor)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    if (episode.episodeNo > 0) "第 ${episode.episodeNo} 集 · ${episode.title}" else episode.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append(if (episode.status == DownloadRecordStatus.Success) "已下载" else episode.status.label)
                        if (episode.successfulFileCount > 0) append(" ${episode.successfulFileCount} 个文件")
                        append(" · ${episode.records.size} 条记录")
                        append(" · ${formatter.format(Date(episode.latestAt))}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append(record.formatLabel())
                        if (record.source.isNotBlank()) append(" · ${record.source}")
                        record.danmuCount?.let { append(" · $it 条弹幕") }
                        if (record.bytes > 0L) append(" · ${formatLibraryBytes(record.bytes)}")
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(modifier = Modifier.width(72.dp), horizontalArrangement = Arrangement.End) {
                if (onPreview != null) {
                    AppGlassIconButton(onClick = onPreview, enabled = enabled && !loadingPreview, size = 34.dp) {
                        if (loadingPreview) {
                            CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Search, "查看弹幕", Modifier.size(18.dp))
                        }
                    }
                }
                AppGlassIconButton(onClick = onDelete, enabled = enabled, size = 34.dp) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        "删除这一集",
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordInlineStat(label: String, count: Int, color: Color) {
    Text(
        "$label $count",
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

@Composable
private fun RecordLibraryEmptyState(text: String) {
    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Rounded.Folder,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RecordDeleteDialog(
    request: RecordDeleteRequest,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit
) {
    var deleteLocalFiles by remember(request.key) { mutableStateOf(false) }
    AppDialog(
        onDismissRequest = onDismiss,
        style = AppDialogStyle.Confirm,
        tone = AppDialogTone.Danger,
        icon = { Icon(Icons.Rounded.DeleteOutline, null) },
        title = { Text(request.title) },
        supportingText = { Text("默认只清理 App 记录") },
        text = {
            Text(request.description)
            if (request.localFileCount > 0) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { deleteLocalFiles = !deleteLocalFiles },
                    shape = RoundedCornerShape(10.dp),
                    color = if (deleteLocalFiles) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.58f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.62f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (deleteLocalFiles) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = deleteLocalFiles,
                            onCheckedChange = { deleteLocalFiles = it }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "同时删除本地文件",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "将尝试删除 ${request.localFileCount} 个关联的弹幕文件",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                Text(
                    "这些记录没有关联的本地文件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (deleteLocalFiles) {
                Text(
                    "本地文件删除后无法通过 App 恢复。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            AppGlassButton(
                onClick = onDismiss,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) { Text("取消") }
        },
        confirmButton = {
            AppGlassDangerButton(
                onClick = { onConfirm(deleteLocalFiles) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(if (deleteLocalFiles) "删除记录和文件" else "仅清理记录")
            }
        }
    )
}

private fun RecordFilter.matches(status: DownloadRecordStatus): Boolean {
    return when (this) {
        RecordFilter.All -> true
        RecordFilter.Success -> status == DownloadRecordStatus.Success
        RecordFilter.Failed -> status == DownloadRecordStatus.Failed
        RecordFilter.Skipped -> status == DownloadRecordStatus.Skipped
    }
}

private fun List<DanmuDownloadRecord>.toDeleteRequest(
    key: String,
    title: String,
    description: String
): RecordDeleteRequest {
    return RecordDeleteRequest(
        key = key,
        title = title,
        description = description,
        recordIds = mapTo(linkedSetOf()) { it.id },
        localFileCount = asSequence()
            .map { it.fileUri.trim() }
            .filter(String::isNotBlank)
            .distinct()
            .count()
    )
}

private fun formatLibraryBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    if (bytes < 1024L) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes.toDouble() / 1024.0
    var unit = units.first()
    for (index in 1 until units.size) {
        if (value < 1024.0) break
        value /= 1024.0
        unit = units[index]
    }
    return "%.1f %s".format(Locale.getDefault(), value, unit)
}
