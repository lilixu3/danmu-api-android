package com.example.danmuapiapp.ui.screen.records

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.TraceChange
import com.example.danmuapiapp.domain.model.TraceTable
import com.example.danmuapiapp.domain.model.TraceValue

private fun TraceValue.inlineText(key: String): String? = when (this) {
    TraceValue.Empty -> "—"
    is TraceValue.Flag -> if (enabled) "是" else "否"
    is TraceValue.Scalar -> when {
        key == "origin" && text == "local" -> "本机规则"
        key == "origin" && text == "remote" -> "远程规则"
        else -> text.ifBlank { "—" }
    }
    is TraceValue.Items -> if (values.all { it is TraceValue.Scalar || it is TraceValue.Flag || it == TraceValue.Empty }) {
        values.joinToString("、") { it.inlineText(key).orEmpty() }.ifBlank { "—" }
    } else null
    is TraceValue.Fields -> if (entries.isEmpty()) "—" else null
}

@Composable
internal fun TraceValueBlock(label: String, value: TraceValue?) {
    if (value == null) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        TraceValueContent(value)
    }
}

@Composable
private fun TraceValueContent(value: TraceValue, field: String = "") {
    val text = value.inlineText(field)
    if (text != null) {
        var expanded by remember(text) { mutableStateOf(false) }
        var overflows by remember(text) { mutableStateOf(false) }
        Text(text, style = MaterialTheme.typography.bodySmall, maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis, onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow })
        if (expanded || overflows) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起内容" else "展开内容") }
        }
    } else when (value) {
        is TraceValue.Fields -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            value.entries.forEach { (key, item) ->
                val name = traceFieldLabels[key] ?: key
                if (item is TraceValue.Fields || (item is TraceValue.Items && item.values.any { it is TraceValue.Fields || it is TraceValue.Items })) {
                    Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.padding(start = 8.dp)) { TraceValueContent(item, key) }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(name, Modifier.weight(.4f), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(.6f)) { TraceValueContent(item, key) }
                    }
                }
            }
        }
        is TraceValue.Items -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            value.values.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider()
                TraceValueContent(item, field)
            }
        }
        else -> Unit
    }
}

@Composable
internal fun TraceDataTable(table: TraceTable) {
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Row(Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(5.dp))) {
            table.columns.forEach { key ->
                Text(traceFieldLabels[key] ?: key, Modifier.width(96.dp).padding(6.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        table.rows.forEach { row ->
            Row {
                row.forEachIndexed { index, cell ->
                    Column(Modifier.width(96.dp).padding(6.dp)) { TraceValueContent(cell, table.columns[index]) }
                }
            }
            HorizontalDivider(Modifier.width(96.dp * table.columns.size), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
internal fun TraceChangeCard(change: TraceChange) {
    val decision = change.kind == "decision"
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(change.label ?: change.config ?: "配置变化", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                if (change.hit != null && (!decision || change.hit == true)) {
                    Text(if (change.hit == true) "命中" else "未命中", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            if (change.config != null && change.label != null) Text(change.config.orEmpty(), style = MaterialTheme.typography.labelSmall)
            change.summary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            TraceValueBlock(if (decision) "判定条件" else "检索条件", change.context)
            if (change.before != null || change.after != null) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth >= 300.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) { TraceValueBlock("处理前", change.before ?: TraceValue.Empty) }
                            Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(Modifier.weight(1f)) { TraceValueBlock("处理后", change.after ?: TraceValue.Empty) }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            TraceValueBlock("处理前", change.before ?: TraceValue.Empty)
                            TraceValueBlock("处理后", change.after ?: TraceValue.Empty)
                        }
                    }
                }
            }
            change.table?.let { TraceDataTable(it) }
            CandidateList(if (decision) "标题命中" else "保留", change.kept, change.keptCount)
            CandidateList(if (decision) "标题未命中" else "剔除", change.removed, change.removedCount)
        }
    }
}

@Composable
private fun CandidateList(label: String, items: List<TraceValue>, count: Int) {
    if (count == 0) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("$label · $count 项", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            if (expanded) "收起" else "展开", Modifier.size(18.dp))
    }
    if (expanded) {
        if (items.isNotEmpty()) TraceValueContent(TraceValue.Items(items))
        if (count > items.size) Text("另有 ${count - items.size} 项未返回明细", style = MaterialTheme.typography.bodySmall)
    }
}

private val traceFieldLabels = mapOf(
    "q" to "搜索词", "keyword" to "搜索词", "season" to "季",
    "episode" to "集", "year" to "年份", "animeId" to "剧集 ID",
    "fileName" to "文件名", "sources" to "检索源", "rawCount" to "原始候选",
    "removedBy" to "剔除", "filtersApplied" to "启用的过滤", "beforeCount" to "过滤前数量",
    "filteredCount" to "过滤后数量", "cachedCount" to "缓存条数", "hit" to "命中",
    "cacheKey" to "缓存键", "ttlMinutes" to "有效期（分钟）", "finalCount" to "最终条数",
    "searchCacheWritten" to "已写入搜索缓存", "mergedCount" to "合并后条数", "crossSeasonApplied" to "跨季扩展",
    "mappingHit" to "映射命中", "candidate" to "映射结果", "mappingApplied" to "应用映射",
    "finalSubjectId" to "最终剧集 ID", "isMatched" to "是否匹配", "matchCount" to "匹配条数",
    "matched" to "是否匹配", "episodeId" to "集 ID", "startSeason" to "起始季",
    "endSeason" to "结束季", "boundaryCount" to "季边界数", "title" to "标题",
    "matchedKey" to "匹配键", "origin" to "规则来源", "localRules" to "本机规则数",
    "remoteRules" to "远程规则数", "platform" to "平台", "rule" to "命中规则",
    "url" to "地址", "triedKeys" to "尝试过的键", "titleMappingTable" to "本机映射规则数",
    "titleMappingTableUrl" to "远程标题映射地址", "autoMatchMappingTableUrl" to "远程季集映射地址", "useBangumiData" to "使用 Bangumi Data",
    "titleToChinese" to "标题转中文", "anime" to "剧名", "animeTitle" to "剧名",
    "requestedEpisode" to "请求集数", "searchedAnime" to "搜索到的剧集数", "matchedEpisodeTitles" to "命中集标题",
    "animeCount" to "剧名数", "episodeCount" to "集数", "fromRequestStore" to "来自本次请求缓存",
    "sourceKey" to "源标识", "linkCount" to "链接数", "commentId" to "弹幕库 ID",
    "episodeTitle" to "集标题", "resourceKey" to "本地资源键", "found" to "是否找到",
    "mergedLinks" to "合并链接", "segment" to "分段请求", "format" to "输出格式",
    "duration" to "视频时长", "attachDuration" to "附带视频时长", "segmentStart" to "片段起点（秒）",
    "segmentEnd" to "片段终点（秒）", "linkOffset" to "链接偏移", "linkOffsetPercent" to "链接偏移按百分比",
    "ruleOffset" to "规则偏移", "percent" to "按百分比", "matchedTitle" to "参与匹配的剧名",
    "keywords" to "检索关键词", "candidateKeywords" to "候选关键词", "candidateCount" to "候选数",
    "favoriteName" to "收藏名", "topScore" to "最高匹配分", "favoriteSupported" to "收藏功能可用",
    "schedule" to "刷新计划", "refreshSchedule" to "已生效的刷新计划", "count" to "数量",
    "error" to "错误", "source" to "源", "removed" to "已删除",
    "value" to "候选项", "aliases" to "别名", "reason" to "原因",
    "autoMatchMappingTable" to "自动匹配规则",
)
