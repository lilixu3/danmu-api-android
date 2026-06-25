package com.example.danmuapiapp.ui.screen.download

import com.example.danmuapiapp.ui.component.AppBottomSheetDialog
import com.example.danmuapiapp.ui.component.AppBottomSheetStyle
import com.example.danmuapiapp.ui.component.AppBottomSheetTone
import com.example.danmuapiapp.ui.component.AppPanelDialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import com.example.danmuapiapp.domain.model.DanmuDownloadRecord
import com.example.danmuapiapp.domain.model.DanmuFilePreview
import com.example.danmuapiapp.domain.model.DanmuPreviewFilter
import com.example.danmuapiapp.domain.model.DanmuPreviewItem
import com.example.danmuapiapp.domain.model.DownloadRecordStatus
import com.example.danmuapiapp.domain.model.previewFilter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

internal enum class RecordFilter(val label: String) {
    All("全部"),
    Success("成功"),
    Failed("失败"),
    Skipped("跳过")
}

@Composable
internal fun RecordsPage(
    records: List<DanmuDownloadRecord>,
    previewState: DanmuPreviewDialogState,
    onClear: () -> Unit,
    onPreviewRecord: (DanmuDownloadRecord) -> Unit,
    onDismissPreview: () -> Unit
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
                            "最多展示最近 80 条；成功的 XML/JSON 可查看弹幕数和内容",
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
                RecordItem(
                    record = record,
                    formatter = formatter,
                    loadingPreview = previewState.loadingRecordId == record.id,
                    onPreview = { onPreviewRecord(record) }
                )
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

    if (previewState.isVisible) {
        DanmuPreviewDialog(
            state = previewState,
            onDismiss = onDismissPreview
        )
    }
}

@Composable
internal fun RecordItem(
    record: DanmuDownloadRecord,
    formatter: SimpleDateFormat,
    loadingPreview: Boolean,
    onPreview: () -> Unit
) {
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
                val canPreview = record.statusEnum() == DownloadRecordStatus.Success &&
                    record.fileUri.isNotBlank() &&
                    (record.formatEnum() == DanmuDownloadFormat.Xml || record.formatEnum() == DanmuDownloadFormat.Json)
                if (canPreview) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = record.danmuCount?.let { "弹幕 $it 条" } ?: "弹幕数待读取",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = onPreview,
                            enabled = !loadingPreview,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            if (loadingPreview) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Rounded.Search, null, Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(if (loadingPreview) "读取中" else "查看")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DanmuPreviewDialog(
    state: DanmuPreviewDialogState,
    onDismiss: () -> Unit
) {
    AppPanelDialog(
        onDismissRequest = onDismiss,
        sheetGesturesEnabled = false,
        showDragHandle = false,
        minHeight = 480.dp,
        sheetMaxHeightFraction = 0.92f,
        popupMaxHeightFraction = 0.9f,
        horizontalPadding = 12.dp,
        sheetTopPadding = 0.dp,
        sheetBottomPadding = 6.dp,
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (state.error != null) "读取弹幕失败" else "弹幕内容预览",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
        Spacer(Modifier.height(8.dp))
        when {
            state.loadingRecordId != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("正在读取弹幕文件…")
                    }
                }
            }
            state.error != null -> {
                val errorMsg = state.error.orEmpty()
                val parseHint = state.preview?.parseError
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        errorMsg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    if (!parseHint.isNullOrBlank() && parseHint != errorMsg) {
                        Text(
                            parseHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            state.preview != null -> {
                DanmuPreviewContent(preview = state.preview)
            }
        }
    }
}

@Composable
private fun DanmuPreviewContent(preview: DanmuFilePreview) {
    val pageSize = 500
    var selectedFilter by remember { mutableStateOf(DanmuPreviewFilter.All) }
    var visibleCount by remember { mutableIntStateOf(pageSize) }
    val listState = rememberLazyListState()

    val filterCounts = remember(preview.items) {
        DanmuPreviewFilter.entries.associateWith { filter ->
            when (filter) {
                DanmuPreviewFilter.All -> preview.items.size
                else -> preview.items.count { it.previewFilter() == filter }
            }
        }
    }
    val filteredItems = remember(preview.items, selectedFilter) {
        when (selectedFilter) {
            DanmuPreviewFilter.All -> preview.items
            else -> preview.items.filter { it.previewFilter() == selectedFilter }
        }
    }
    val displayedItems = remember(filteredItems, visibleCount) {
        filteredItems.take(visibleCount)
    }
    val canLoadMore = displayedItems.size < filteredItems.size
    val loadMoreVisible by remember {
        derivedStateOf {
            if (!canLoadMore || displayedItems.isEmpty()) return@derivedStateOf false
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= displayedItems.lastIndex
        }
    }

    LaunchedEffect(selectedFilter) {
        visibleCount = pageSize
        listState.scrollToItem(0)
    }

    // 切到新文件时重置筛选和滚动状态
    LaunchedEffect(preview.relativePath) {
        selectedFilter = DanmuPreviewFilter.All
        visibleCount = pageSize
        listState.scrollToItem(0)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PreviewStatBadge(preview.format.label, preview.count, MaterialTheme.colorScheme.primary)
            PreviewStatBadge("预览", displayedItems.size, MaterialTheme.colorScheme.tertiary)
            if (preview.truncated) {
                PreviewStatBadge("文件", preview.items.size, MaterialTheme.colorScheme.secondary)
            }
        }
        val metaText = buildString {
            append(preview.relativePath.ifBlank { preview.fileName.ifBlank { "未命名文件" } })
            if (preview.bytes > 0L) append(" · ${formatBytesForPreview(preview.bytes)}")
        }
        if (metaText.isNotBlank()) {
            Text(
                metaText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        DanmuPreviewFilterBar(
            selectedFilter = selectedFilter,
            counts = filterCounts,
            onSelect = { selectedFilter = it }
        )
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        ) {
            if (filteredItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "当前筛选下没有弹幕",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        items(
                            items = displayedItems,
                            key = { it.index }
                        ) { item ->
                            DanmuPreviewRow(item = item)
                        }
                    }
                    if (canLoadMore && loadMoreVisible) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { visibleCount += pageSize },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("加载更多 500 条")
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DanmuPreviewFilterBar(
    selectedFilter: DanmuPreviewFilter,
    counts: Map<DanmuPreviewFilter, Int>,
    onSelect: (DanmuPreviewFilter) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DanmuPreviewFilter.entries.forEach { filter ->
            CompactPreviewFilterButton(
                modifier = Modifier.weight(1f),
                selected = selectedFilter == filter,
                text = "${filter.label} ${counts[filter] ?: 0}",
                onClick = { onSelect(filter) }
            )
        }
    }
}

@Composable
private fun CompactPreviewFilterButton(
    modifier: Modifier = Modifier,
    selected: Boolean,
    text: String,
    onClick: () -> Unit
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier.height(32.dp),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        onClick = onClick
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            val fontSize = when {
                maxWidth < 72.dp || text.length >= 8 -> 9.sp
                maxWidth < 84.dp || text.length >= 7 -> 10.sp
                else -> 11.sp
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = fontSize),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DanmuPreviewRow(item: DanmuPreviewItem) {
    val filter = item.previewFilter()
    val filterColor = when (filter) {
        DanmuPreviewFilter.Scroll -> MaterialTheme.colorScheme.primary
        DanmuPreviewFilter.Top -> MaterialTheme.colorScheme.tertiary
        DanmuPreviewFilter.Bottom -> MaterialTheme.colorScheme.secondary
        DanmuPreviewFilter.All -> MaterialTheme.colorScheme.primary
    }
    val danmuColor = parsePreviewColor(item.color)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.source.isNotBlank()) {
                    PreviewBadge(text = item.source, color = MaterialTheme.colorScheme.secondary)
                }
                PreviewBadge(text = formatPreviewTime(item.timeSeconds), color = MaterialTheme.colorScheme.primary)
                PreviewBadge(text = filter.label, color = filterColor)
                if (danmuColor != null) {
                    PreviewColorSwatch(color = danmuColor)
                }
            }
            Text(
                text = item.text.ifBlank { "（空弹幕）" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PreviewBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.20f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PreviewStatBadge(label: String, value: Int, color: Color) {
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
private fun PreviewColorSwatch(color: Color) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f))
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .padding(3.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

private fun parsePreviewColor(colorStr: String): Color? {
    val value = colorStr.toLongOrNull() ?: return null
    if (value <= 0 || value > 0xFFFFFF) return null
    return Color(0xFF000000 or value)
}

private fun formatPreviewTime(seconds: Double?): String {
    if (seconds == null || !seconds.isFinite()) return "--:--.--"
    val totalCentis = (seconds.coerceAtLeast(0.0) * 100.0).roundToLong()
    val minutes = totalCentis / 6000
    val sec = (totalCentis % 6000) / 100
    val centis = totalCentis % 100
    return "%02d:%02d.%02d".format(Locale.getDefault(), minutes, sec, centis)
}

private fun formatBytesForPreview(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes.toDouble() / 1024.0
    var unit = units.first()
    for (i in 1 until units.size) {
        if (value < 1024.0) break
        value /= 1024.0
        unit = units[i]
    }
    return "%.1f %s".format(Locale.getDefault(), value, unit)
}
