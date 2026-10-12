package com.example.danmuapiapp.ui.screen.apitest

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.danmuapiapp.ui.component.AnimePosterThumbnail
import java.util.Locale

@Composable
internal fun ApiDanmuDetailPage(insight: DanmuInsight?, loading: Boolean, error: String?, onBack: () -> Unit,
    onRefresh: () -> Unit, onCopy: (String) -> Unit, onExport: (DanmuInsight) -> Unit,
    apiUrl: (DanmuInsight) -> String, fileName: String = "") {
    var search by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableIntStateOf(DanmuCommentFilter.All.ordinal) }
    var sources by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var bucketIndex by rememberSaveable { mutableIntStateOf(-1) }
    var previewBucketIndex by remember { mutableIntStateOf(-1) }
    var visibleCount by rememberSaveable { mutableIntStateOf(200) }
    val selectedMode = DanmuCommentFilter.entries.getOrNull(mode) ?: DanmuCommentFilter.All
    val presentation by produceState<DanmuListIndex?>(null, insight) {
        value = insight?.let { withContext(Dispatchers.Default) { buildDanmuListIndex(it.comments, it.source) } }
    }
    val selectedBucket = insight?.heatBuckets?.getOrNull(bucketIndex)
    val bucket = insight?.heatBuckets?.getOrNull(previewBucketIndex) ?: selectedBucket
    val lastBucketIndex = insight?.heatBuckets?.lastOrNull()?.index
    val filterResult by produceState<DanmuListFilterResult?>(null, presentation, search, selectedMode, sources, bucket) {
        val data = presentation
        value = if (data == null) null else withContext(Dispatchers.Default) {
            filterDanmuList(data, search, selectedMode, sources.toSet(), bucket,
                includeRangeEnd = bucket != null && bucket.index == lastBucketIndex)
        }
    }
    LaunchedEffect(search, mode, sources, bucketIndex) { visibleCount = 200 }
    LaunchedEffect(insight) { previewBucketIndex = -1 }
    val hasFilter = search.isNotBlank() || selectedMode != DanmuCommentFilter.All || sources.isNotEmpty() || selectedBucket != null
    val resetFilters: () -> Unit = { mode = DanmuCommentFilter.All.ordinal; sources = emptyList(); bucketIndex = -1; previewBucketIndex = -1; search = "" }
    Column(Modifier.fillMaxSize()) {
        ApiPageHeader("弹幕详情", onBack, actions = {
            IconButton(onClick = onRefresh, enabled = !loading) { Icon(Icons.Rounded.Refresh, "刷新弹幕", Modifier.size(21.dp)) }
        })
        when {
            insight == null && loading -> ApiLoading()
            insight == null -> ApiEmpty("获取弹幕失败", error ?: "请返回选择剧集") { ApiAction("重试", Icons.Rounded.Refresh, onRefresh) }
            presentation == null || filterResult == null -> ApiLoading()
            else -> {
                val data = presentation!!
                val result = filterResult!!
                val filtered = result.comments
                val visible = filtered.take(visibleCount)
                LazyColumn(contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 28.dp)) {
                    item("hero") {
                        DetailHero(insight, data.sourceKeys.size, fileName, onCopy, { onExport(insight) }, apiUrl(insight))
                        Spacer(Modifier.height(12.dp))
                    }
                    item("stats") { DetailStats(insight, data.coloredCount); Spacer(Modifier.height(12.dp)) }
                    if (loading) item("refreshing") { LinearProgressIndicator(Modifier.fillMaxWidth()); Spacer(Modifier.height(12.dp)) }
                    if (error != null) item("error") { ApiHint("刷新失败：$error"); Spacer(Modifier.height(12.dp)) }
                    if (insight.heatBuckets.isNotEmpty()) item("heat") {
                        DetailHeatmap(insight, bucketIndex, previewBucketIndex,
                            onPreview = { previewBucketIndex = it }, onSelect = { bucketIndex = it; previewBucketIndex = -1 })
                        Spacer(Modifier.height(12.dp))
                    }
                    item("filters") {
                        DanmuListControls(search, { search = it }, selectedMode, { mode = it.ordinal }, result.modeCounts,
                            result.sourceCounts, sources, { source -> sources = if (source in sources) sources - source else sources + source },
                            hasFilter, resetFilters, selectedBucket, { bucketIndex = -1; previewBucketIndex = -1 })
                    }
                    if (visible.isEmpty()) item("empty") {
                        DanmuListSurface(last = true) {
                            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                ApiHint(if (hasFilter || bucket != null) "没有符合筛选条件的弹幕" else "这一集暂无弹幕")
                                if (hasFilter) TextButton(onClick = resetFilters) { Text("清空筛选") }
                            }
                        }
                    } else itemsIndexed(visible, key = { index, comment -> "$index:${comment.uniqueId}" }) { index, comment ->
                        DetailCommentRow(comment, index == visible.lastIndex && visible.size == filtered.size, data.sourceKeys.size > 1)
                    }
                    if (filtered.size > visible.size) item("more") {
                        DanmuListSurface(last = true) {
                            TextButton(onClick = { visibleCount += 200 }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text("加载更多 · 剩余 ${filtered.size - visible.size} 条", fontSize = 12.sp)
                            }
                        }
                    }
                    item("request") {
                        Spacer(Modifier.height(12.dp))
                        ApiCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Link, null, Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                ApiLabel("实际请求")
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { onCopy(apiUrl(insight)) }) { Text("复制", fontSize = 12.sp) }
                            }
                            SelectionContainer { Text(apiUrl(insight), fontFamily = FontFamily.Monospace, fontSize = 10.5.sp,
                                lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            ApiHint("集 ID ${insight.commentId ?: "URL"} · 耗时 ${insight.requestDurationMs ?: 0}ms · 返回 ${insight.totalCount} 条")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailHero(insight: DanmuInsight, sourceCount: Int, fileName: String, onCopy: (String) -> Unit,
    onExport: () -> Unit, url: String) {
    val accent = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(accent.copy(alpha = 0.10f), accent.copy(alpha = 0.02f))))
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AnimePosterThumbnail(insight.posterUrl, insight.animeTitle, Modifier.width(76.dp).height(102.dp))
                Column(Modifier.weight(1f).heightIn(min = 104.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(insight.animeTitle.ifBlank { "弹幕详情" }, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val episode = insight.resolvedEpisodeLabel.ifBlank { insight.episodeTitle }
                        if (episode.isNotBlank()) ApiTag(episode.take(38))
                        if (insight.year.isNotBlank()) ApiTag(insight.year, MaterialTheme.colorScheme.onSurfaceVariant)
                        ApiTag(insight.pathLabel)
                        if (sourceCount > 1) ApiTag("合并源 $sourceCount", MaterialTheme.colorScheme.tertiary)
                    }
                    Text("弹幕 ${insight.totalCount} 条 · 时长 ${apiTime(insight.durationSeconds)}", fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (fileName.isNotBlank()) Text("匹配文件名：$fileName", fontSize = 11.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val links = remember(insight.sourceUrl, insight.source) { detailSourceLinks(insight.sourceUrl, insight.source) }
            if (links.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                ApiHint(if (links.size > 1) "来源地址 · 合并源 ${links.size} 个" else "来源地址")
                links.forEach { (source, target) ->
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))) {
                        Row(Modifier.padding(start = 10.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (source.isNotBlank()) { ApiTag(source); Spacer(Modifier.width(8.dp)) }
                            Text(target, Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { onCopy(target) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.ContentCopy, "复制来源地址", Modifier.size(17.dp)) }
                            val canOpen = target.startsWith("https://") || target.startsWith("http://")
                            IconButton(onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, target.toUri())) }
                                    .onFailure { Toast.makeText(context, "无法打开来源地址", Toast.LENGTH_SHORT).show() }
                            }, enabled = canOpen, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, "打开来源地址", Modifier.size(17.dp))
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ApiAction("导出弹幕", Icons.Rounded.IosShare, onExport, Modifier.weight(1f), enabled = insight.exportTarget != null)
                ApiAction("复制接口地址", Icons.Rounded.Link, { onCopy(url) }, Modifier.weight(1f), outlined = true)
            }
            ApiHint("支持 10 种弹幕格式；合并源按当前这一集的实际弹幕导出。")
        }
    }
}

@Composable
private fun DetailStats(insight: DanmuInsight, coloredCount: Int) {
    val metrics = listOf("弹幕数" to insight.totalCount.toString(), "时长" to apiTime(insight.durationSeconds),
        "高能时刻" to (insight.highMoments.maxByOrNull { it.count }?.let { apiTime(it.startSeconds) } ?: "—"),
        "平均密度" to if (insight.durationSeconds > 0) String.format(Locale.ROOT, "%.1f 条/分", insight.totalCount * 60.0 / insight.durationSeconds) else "—",
        "请求耗时" to "${insight.requestDurationMs ?: 0}ms",
        "彩色占比" to if (insight.comments.isEmpty()) "0%" else String.format(Locale.ROOT, "%.1f%%", coloredCount * 100.0 / insight.comments.size))
    ApiCard {
        repeat(2) { row ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                repeat(3) { column ->
                    if (column > 0) Box(Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)))
                    Column(Modifier.weight(1f).padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(metrics[row * 3 + column].second, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(metrics[row * 3 + column].first, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailHeatmap(insight: DanmuInsight, selected: Int, preview: Int, onPreview: (Int) -> Unit, onSelect: (Int) -> Unit) {
    val buckets = insight.heatBuckets
    val active = buckets.getOrNull(preview) ?: buckets.getOrNull(selected)
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val peak = buckets.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    ApiCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ApiLabel("弹幕分布")
            Spacer(Modifier.weight(1f))
            ApiHint(active?.let { "${apiTime(it.startSeconds)}–${apiTime(it.endSeconds)} · ${it.count} 条" } ?: "横滑预览 · 点击筛选")
        }
        Canvas(Modifier.fillMaxWidth().height(90.dp)
            .pointerInput(buckets) {
                detectHorizontalDragGestures(onDragStart = { position -> onPreview((position.x / size.width * buckets.size).toInt().coerceIn(0, buckets.lastIndex)) },
                    onDragEnd = { onPreview(-1) }, onDragCancel = { onPreview(-1) }) { change, _ ->
                    change.consume()
                    onPreview((change.position.x / size.width * buckets.size).toInt().coerceIn(0, buckets.lastIndex))
                }
            }.pointerInput(buckets, selected) {
                detectTapGestures { position ->
                    val index = (position.x / size.width * buckets.size).toInt().coerceIn(0, buckets.lastIndex)
                    onSelect(if (selected == index) -1 else index)
                }
            }) {
            val step = size.width / buckets.size
            buckets.forEachIndexed { index, bucket ->
                val height = (bucket.count.toFloat() / peak * (size.height - 6.dp.toPx())).coerceAtLeast(2.dp.toPx())
                val barColor = when (index) {
                    selected -> accent
                    preview -> accent.copy(alpha = 0.65f)
                    else -> accent.copy(alpha = 0.36f)
                }
                drawRoundRect(barColor,
                    topLeft = Offset(index * step + 1.dp.toPx(), size.height - height),
                    size = Size((step - 2.dp.toPx()).coerceAtLeast(1f), height), cornerRadius = CornerRadius(2.dp.toPx()))
            }
            drawLine(muted, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        }
        Row { ApiHint("00:00"); Spacer(Modifier.weight(1f)); ApiHint(apiTime(insight.durationSeconds)) }
        if (selected >= 0) TextButton(onClick = { onSelect(-1) }) { Text("清除时间筛选", fontSize = 11.sp) }
    }
}

@Composable
private fun DetailCommentRow(comment: DanmuCommentItem, last: Boolean, showSource: Boolean) {
    var multiline by remember(comment.text) { mutableStateOf(false) }
    DanmuListSurface(last = last) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically) {
                Text(apiTime(comment.timeSeconds), Modifier.width(if (comment.timeSeconds >= 3600) 54.dp else 44.dp), fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(detailMode(comment.mode), Modifier.clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 5.dp, vertical = 1.dp),
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                val colorValue = comment.colorValue
                val colored = colorValue?.let { (it and 0xffffffL) != 0xffffffL } == true
                val hasSource = showSource && comment.sourceLabel.isNotBlank()
                Row(Modifier.padding(top = if (multiline) { if (hasSource) 2.dp else 4.dp } else 0.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(Modifier.size(11.dp), shape = CircleShape,
                        color = if (colored) Color(0xff000000L or (colorValue and 0xffffffL)) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {}
                    if (hasSource) {
                        Text(comment.sourceLabel.split('&', '＆').map(::detailSourceName).distinct().joinToString(" · "),
                            Modifier.widthIn(max = 76.dp), fontSize = 10.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(comment.text, Modifier.weight(1f), fontSize = 12.5.sp, lineHeight = 17.sp,
                    onTextLayout = { multiline = it.lineCount > 1 })
            }
            if (!last) HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}

private fun detailMode(mode: Int): String = when (mode) { 4 -> "底部"; 5 -> "顶部"; 6 -> "逆向"; 7 -> "高级"; 8 -> "代码"; else -> "滚动" }
