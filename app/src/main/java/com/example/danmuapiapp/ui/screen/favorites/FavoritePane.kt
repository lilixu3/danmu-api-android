package com.example.danmuapiapp.ui.screen.favorites

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.danmuapiapp.ui.component.AnimePosterThumbnail
import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.screen.apitest.ApiAction
import com.example.danmuapiapp.ui.screen.apitest.ApiCard
import com.example.danmuapiapp.ui.screen.apitest.ApiEmpty
import com.example.danmuapiapp.ui.screen.apitest.ApiHint
import com.example.danmuapiapp.ui.screen.apitest.ApiInput
import com.example.danmuapiapp.ui.screen.apitest.ApiLoading
import com.example.danmuapiapp.ui.screen.apitest.ApiTag
import com.example.danmuapiapp.ui.screen.apitest.ApiTestFavoriteItem
import com.example.danmuapiapp.ui.screen.apitest.ApiTestFavoriteSchedule
import com.example.danmuapiapp.ui.screen.apitest.FavoriteOperation
import com.example.danmuapiapp.ui.screen.apitest.FavoriteScheduleDraft
import com.example.danmuapiapp.ui.screen.apitest.FavoriteScheduleFrequency
import com.example.danmuapiapp.ui.screen.apitest.FavoriteSupportState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 收藏页共享实现：接口调试与弹幕推送复用同一套列表、卡片与定时刷新交互。
 *
 * 调用方只负责提供状态与增删/刷新/定时回调，打开收藏项后的跳转由 [onOpen] 决定。
 */
@Composable
internal fun FavoriteListPane(
    items: List<ApiTestFavoriteItem>,
    supportState: FavoriteSupportState,
    scheduledRefreshSupported: Boolean,
    loadError: String?,
    operation: FavoriteOperation?,
    operationKeyword: String?,
    emptyHint: String,
    openEnabled: Boolean = true,
    openLabel: String = "查看剧集",
    onLoad: () -> Unit,
    onOpen: (ApiTestFavoriteItem) -> Unit,
    onRefresh: (ApiTestFavoriteItem) -> Unit,
    onRemove: (ApiTestFavoriteItem) -> Unit,
    onSchedule: (ApiTestFavoriteItem, FavoriteScheduleDraft?) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var remove by remember { mutableStateOf<ApiTestFavoriteItem?>(null) }
    var refresh by remember { mutableStateOf<ApiTestFavoriteItem?>(null) }
    var schedule by remember { mutableStateOf<ApiTestFavoriteItem?>(null) }
    val visible = remember(items, query) {
        items.filter { "${it.keyword} ${it.animeTitle} ${it.source}".contains(query.trim(), ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize()) {
        ApiInput(query, { query = it }, "搜索收藏剧名或来源", Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            leading = Icons.Rounded.Search, trailing = {
                Row {
                    if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "清空搜索", Modifier.size(16.dp)) }
                    IconButton(onClick = onLoad, enabled = supportState != FavoriteSupportState.Loading) {
                        Icon(Icons.Rounded.Refresh, "刷新收藏列表", Modifier.size(18.dp))
                    }
                }
            })
        when (supportState) {
            FavoriteSupportState.Unknown, FavoriteSupportState.Loading -> ApiLoading()
            FavoriteSupportState.Unsupported -> ApiEmpty("当前核心不支持收藏", "更新核心后可以收藏搜索结果")
            FavoriteSupportState.Failed -> ApiEmpty("收藏加载失败", loadError.orEmpty()) {
                ApiAction("重试", Icons.Rounded.Refresh, onLoad)
            }
            FavoriteSupportState.Supported -> {
                if (visible.isEmpty()) ApiEmpty(if (items.isEmpty()) "还没有收藏" else "没有匹配的收藏", emptyHint)
                else LazyColumn(contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(visible, key = { it.keyword }) { item ->
                        val busy = operation != null && operationKeyword == item.keyword
                        ApiCard {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AnimePosterThumbnail(item.imageUrl, item.animeTitle, Modifier.width(58.dp).height(78.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(item.animeTitle, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    ApiHint("${item.source.ifBlank { "未知来源" }} · ${item.episodeCount} 集 · ${item.resultsCount} 个结果")
                                    ApiHint("最近刷新：${favoriteTime(item.lastRefreshAt)}")
                                    ApiHint("定时刷新：${item.refreshSchedule?.let { favoriteScheduleLabel(it) } ?: "未设置"}")
                                    item.refreshSchedule?.nextRunAt?.let { ApiHint("下次执行：${favoriteTime(it)}") }
                                    item.refreshSchedule?.lastError?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.error) }
                                }
                                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onOpen(item) }, enabled = openEnabled && !busy) { Text(openLabel, fontSize = 12.sp) }
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { schedule = item }, enabled = !busy && scheduledRefreshSupported) {
                                    Icon(Icons.Rounded.Schedule, "定时刷新", Modifier.size(18.dp))
                                }
                                IconButton(onClick = { refresh = item }, enabled = !busy) { Icon(Icons.Rounded.Refresh, "刷新收藏缓存", Modifier.size(18.dp)) }
                                IconButton(onClick = { remove = item }, enabled = !busy) {
                                    Icon(Icons.Rounded.DeleteOutline, "删除收藏", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    remove?.let { item -> AppDialog(onDismissRequest = { remove = null }, title = { Text("删除收藏") },
        text = { Text("确定删除「${item.animeTitle}」的收藏缓存？") },
        confirmButton = { TextButton(onClick = { remove = null; onRemove(item) }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { remove = null }) { Text("取消") } }) }
    refresh?.let { item -> AppDialog(onDismissRequest = { refresh = null }, title = { Text("刷新收藏") },
        text = { Text("重新获取「${item.animeTitle}」的整组搜索结果并更新收藏缓存。") },
        confirmButton = { TextButton(onClick = { refresh = null; onRefresh(item) }) { Text("刷新") } },
        dismissButton = { TextButton(onClick = { refresh = null }) { Text("取消") } }) }
    schedule?.let { item -> FavoriteScheduleDialog(item, { schedule = null }, { draft ->
        schedule = null; onSchedule(item, draft)
    }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FavoriteScheduleDialog(item: ApiTestFavoriteItem, onDismiss: () -> Unit, onSave: (FavoriteScheduleDraft?) -> Unit) {
    var weekly by remember { mutableStateOf(item.refreshSchedule?.frequency == FavoriteScheduleFrequency.Weekly) }
    var weekday by remember { mutableIntStateOf(item.refreshSchedule?.weekday ?: 1) }
    var time by remember { mutableStateOf(item.refreshSchedule?.time ?: "03:00") }
    val context = LocalContext.current
    AppDialog(onDismissRequest = onDismiss, title = { Text("定时刷新") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ApiHint(item.animeTitle)
            ApiHint("北京时间 · 核心服务运行时执行")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ApiTag("每天", selected = !weekly) { weekly = false }
                ApiTag("每周", selected = weekly) { weekly = true }
            }
            if (weekly) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").forEachIndexed { index, day ->
                    ApiTag(day, selected = weekday == index + 1) { weekday = index + 1 }
                }
            }
            OutlinedButton(onClick = {
                val parts = time.split(':')
                TimePickerDialog(context, { _, hour, minute -> time = String.format(Locale.ROOT, "%02d:%02d", hour, minute) },
                    parts[0].toInt(), parts[1].toInt(), true).show()
            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.AccessTime, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(time) }
            if (item.refreshSchedule != null) TextButton(onClick = { onSave(null) }) { Text("关闭定时刷新", color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = { onSave(FavoriteScheduleDraft(
        if (weekly) FavoriteScheduleFrequency.Weekly else FavoriteScheduleFrequency.Daily, time, weekday)) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

private fun favoriteTime(timestamp: Long): String = if (timestamp <= 0) "未知" else SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    .apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }.format(Date(timestamp))

private fun favoriteScheduleLabel(schedule: ApiTestFavoriteSchedule): String =
    if (schedule.frequency == FavoriteScheduleFrequency.Daily) "每天 ${schedule.time}" else
        "${listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").getOrElse((schedule.weekday ?: 1) - 1) { "每周" }} ${schedule.time}"
