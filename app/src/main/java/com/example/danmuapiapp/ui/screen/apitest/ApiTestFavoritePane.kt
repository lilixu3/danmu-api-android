package com.example.danmuapiapp.ui.screen.apitest

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.ui.component.AnimePosterThumbnail
import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.AppDialogStyle
import com.example.danmuapiapp.ui.component.AppDialogTone
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassFilterChip
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
internal fun ApiTestFavoritePane(
    supportState: FavoriteSupportState,
    favorites: List<ApiTestFavoriteItem>,
    scheduledRefreshSupported: Boolean,
    loadError: String?,
    operation: FavoriteOperation?,
    operationKeyword: String?,
    onReload: () -> Unit,
    onOpen: (ApiTestFavoriteItem) -> Unit,
    onRefresh: (ApiTestFavoriteItem) -> Unit,
    onRemove: (ApiTestFavoriteItem) -> Unit,
    onSchedule: (ApiTestFavoriteItem, FavoriteScheduleDraft?) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var refreshCandidate by remember { mutableStateOf<ApiTestFavoriteItem?>(null) }
    var removeCandidate by remember { mutableStateOf<ApiTestFavoriteItem?>(null) }
    var scheduleCandidate by remember { mutableStateOf<ApiTestFavoriteItem?>(null) }
    val filtered = remember(favorites, query) {
        val normalized = query.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) favorites else favorites.filter { item ->
            listOf(item.keyword, item.animeTitle, item.source)
                .joinToString(" ")
                .lowercase(Locale.ROOT)
                .contains(normalized)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WorkbenchCard(
            title = "收藏",
            subtitle = if (supportState == FavoriteSupportState.Supported) {
                "${filtered.size} / ${favorites.size} 项"
            } else {
                null
            },
            action = {
                AppGlassIconButton(
                    onClick = onReload,
                    enabled = supportState != FavoriteSupportState.Loading,
                    size = 36.dp
                ) {
                    if (supportState == FavoriteSupportState.Loading) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Refresh, "刷新收藏", Modifier.size(18.dp))
                    }
                }
            }
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp)) },
                placeholder = { Text("搜索收藏") },
                shape = RoundedCornerShape(14.dp)
            )
        }

        when (supportState) {
            FavoriteSupportState.Unknown,
            FavoriteSupportState.Loading -> LoadingHintCard(
                title = "正在加载收藏",
                subtitle = ""
            )

            FavoriteSupportState.Unsupported -> InfoHintCard(
                title = "当前核心版本不支持收藏",
                subtitle = "请更新到包含永久收藏接口的核心版本"
            )

            FavoriteSupportState.Failed -> InfoHintCard(
                title = "收藏加载失败",
                subtitle = loadError.orEmpty().ifBlank { "请检查连接后重试" },
                action = {
                    AppGlassButton(onClick = onReload) { Text("重试") }
                }
            )

            FavoriteSupportState.Supported -> when {
                favorites.isEmpty() -> InfoHintCard(
                    title = "暂无收藏",
                    subtitle = "在手动匹配中搜索动漫后即可收藏"
                )
                filtered.isEmpty() -> InfoHintCard(
                    title = "没有匹配的收藏",
                    subtitle = ""
                )
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.keyword }) { item ->
                        FavoriteItemCard(
                            item = item,
                            busy = operation != null && operationKeyword == item.keyword,
                            scheduledRefreshSupported = scheduledRefreshSupported,
                            onOpen = { onOpen(item) },
                            onRefresh = { refreshCandidate = item },
                            onRemove = { removeCandidate = item },
                            onSchedule = { scheduleCandidate = item }
                        )
                    }
                }
            }
        }
    }

    refreshCandidate?.let { item ->
        AppDialog(
            onDismissRequest = { refreshCandidate = null },
            style = AppDialogStyle.Confirm,
            tone = AppDialogTone.Warning,
            title = { Text("刷新收藏") },
            text = { Text("将绕过现有搜索缓存，重新获取「${item.animeTitle}」的整组搜索结果。") },
            dismissButton = {
                AppGlassButton(onClick = { refreshCandidate = null }) { Text("取消") }
            },
            confirmButton = {
                AppGlassButton(
                    onClick = {
                    refreshCandidate = null
                    onRefresh(item)
                    },
                    tint = MaterialTheme.colorScheme.primary
                ) { Text("刷新") }
            }
        )
    }

    removeCandidate?.let { item ->
        AppDialog(
            onDismissRequest = { removeCandidate = null },
            style = AppDialogStyle.Confirm,
            tone = AppDialogTone.Danger,
            title = { Text("删除收藏") },
            text = { Text("确定删除「${item.animeTitle}」及其永久搜索缓存吗？") },
            dismissButton = {
                AppGlassButton(onClick = { removeCandidate = null }) { Text("取消") }
            },
            confirmButton = {
                AppGlassButton(
                    onClick = {
                    removeCandidate = null
                    onRemove(item)
                    },
                    surfaceColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) { Text("删除") }
            }
        )
    }

    scheduleCandidate?.let { item ->
        FavoriteScheduleDialog(
            item = item,
            onDismiss = { scheduleCandidate = null },
            onSave = { draft ->
                scheduleCandidate = null
                onSchedule(item, draft)
            },
            onDisable = {
                scheduleCandidate = null
                onSchedule(item, null)
            }
        )
    }
}

@Composable
private fun FavoriteItemCard(
    item: ApiTestFavoriteItem,
    busy: Boolean,
    scheduledRefreshSupported: Boolean,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
    onRemove: () -> Unit,
    onSchedule: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = apiTestPanelColor(),
        border = BorderStroke(1.dp, apiTestOutlineColor())
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                AnimePosterThumbnail(
                    imageUrl = item.imageUrl,
                    title = item.animeTitle,
                    modifier = Modifier.size(width = 52.dp, height = 70.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.animeTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.source.isNotBlank()) {
                        Text(
                            text = item.source,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${item.resultsCount} 个结果 · ${item.episodeCount} 集",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "最近刷新 ${formatFavoriteTimestamp(item.lastRefreshAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Box {
                        AppGlassIconButton(onClick = { showMenu = true }, size = 34.dp) {
                            Icon(Icons.Rounded.MoreVert, "更多操作")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.86f),
                            tonalElevation = 0.dp
                        ) {
                            DropdownMenuItem(
                                text = { Text("删除") },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                onClick = {
                                    showMenu = false
                                    onRemove()
                                }
                            )
                        }
                    }
                }
            }
            item.refreshSchedule?.let { schedule ->
                FavoriteScheduleStatus(schedule)
            }
            HorizontalDivider(color = apiTestOutlineColor())
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.refreshSchedule != null) {
                    AppGlassButton(
                        onClick = onSchedule,
                        enabled = !busy && scheduledRefreshSupported,
                        modifier = Modifier.weight(1f),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                    ) {
                        FavoriteScheduleButtonContent(formatFavoriteScheduleLabel(item.refreshSchedule))
                    }
                } else {
                    AppGlassButton(
                        onClick = onSchedule,
                        enabled = !busy && scheduledRefreshSupported,
                        modifier = Modifier.weight(1f),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                    ) {
                        FavoriteScheduleButtonContent(
                            if (scheduledRefreshSupported) "定时刷新" else "当前模式不支持"
                        )
                    }
                }
                AppGlassButton(onClick = onOpen, enabled = !busy) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("打开")
                }
                AppGlassIconButton(onClick = onRefresh, enabled = !busy, size = 34.dp) {
                    Icon(Icons.Rounded.Refresh, "刷新收藏", Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
private fun FavoriteScheduleButtonContent(label: String) {
    Icon(Icons.Rounded.Schedule, null, Modifier.size(17.dp))
    Spacer(Modifier.width(6.dp))
    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun FavoriteScheduleStatus(schedule: ApiTestFavoriteSchedule) {
    val failed = schedule.lastStatus == "failed"
    val succeeded = schedule.lastStatus == "success"
    val statusColor = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f))
            .padding(horizontal = 11.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Rounded.Schedule,
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = formatFavoriteScheduleLabel(schedule),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            schedule.nextRunAt?.let { timestamp ->
                Text(
                    text = "下次执行 ${formatFavoriteNextRun(timestamp)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (failed || succeeded) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = if (failed) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = statusColor
                    )
                    Text(
                        text = if (failed) {
                            "上次失败${schedule.lastError.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()}"
                        } else {
                            "上次刷新成功"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (failed) {
                schedule.retryAt?.let { timestamp ->
                    Text(
                        text = "自动重试 ${formatFavoriteNextRun(timestamp)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FavoriteScheduleDialog(
    item: ApiTestFavoriteItem,
    onDismiss: () -> Unit,
    onSave: (FavoriteScheduleDraft) -> Unit,
    onDisable: () -> Unit
) {
    val existing = item.refreshSchedule
    var frequency by remember(item.keyword) {
        mutableStateOf(existing?.frequency ?: FavoriteScheduleFrequency.Daily)
    }
    var time by remember(item.keyword) { mutableStateOf(existing?.time ?: "03:00") }
    var weekday by remember(item.keyword) { mutableIntStateOf(existing?.weekday ?: 1) }
    val context = LocalContext.current
    val weekdayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val normalizedTime = normalizeFavoriteTime(time) ?: "03:00"
    val timeParts = normalizedTime.split(':')
    val hour = timeParts[0].toInt()
    val minute = timeParts[1].toInt()

    AppDialog(
        onDismissRequest = onDismiss,
        style = AppDialogStyle.Form,
        tone = AppDialogTone.Brand,
        icon = { Icon(Icons.Rounded.Schedule, null, Modifier.size(20.dp)) },
        title = { Text("定时刷新") },
        supportingText = { Text(item.animeTitle) },
        text = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f))
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatFavoriteScheduleDraft(frequency, normalizedTime, weekday),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "北京时间 · 核心服务运行时执行",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "刷新频率",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                FavoriteScheduleFrequency.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = frequency == option,
                        onClick = { frequency = option },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = FavoriteScheduleFrequency.entries.size
                        )
                    ) {
                        Text(option.label)
                    }
                }
            }

            if (frequency == FavoriteScheduleFrequency.Weekly) {
                Text(
                    text = "执行日期",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    weekdayNames.forEachIndexed { index, label ->
                        AppGlassFilterChip(
                            selected = weekday == index + 1,
                            onClick = { weekday = index + 1 },
                            label = { Text(label) }
                        )
                    }
                }
            }

            Text(
                text = "执行时间",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            AppGlassButton(
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, selectedHour, selectedMinute ->
                            time = "%02d:%02d".format(Locale.ROOT, selectedHour, selectedMinute)
                        },
                        hour,
                        minute,
                        true
                    ).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                contentPadding = ButtonDefaults.ContentPadding
            ) {
                Icon(Icons.Rounded.AccessTime, null, Modifier.size(19.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(
                        text = normalizedTime,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "24 小时制",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(19.dp))
            }

            existing?.nextRunAt?.let { timestamp ->
                Text(
                    text = "当前计划下次执行：${formatFavoriteNextRun(timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            if (existing != null) {
                AppGlassButton(
                    onClick = onDisable,
                    tint = MaterialTheme.colorScheme.error,
                    surfaceColor = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                    contentColor = MaterialTheme.colorScheme.error
                ) {
                    Text("关闭定时")
                }
            }
            AppGlassButton(onClick = onDismiss) { Text("取消") }
            AppGlassButton(
                onClick = {
                onSave(FavoriteScheduleDraft(frequency, normalizedTime, weekday))
                },
                tint = MaterialTheme.colorScheme.primary
            ) { Text("保存") }
        }
    )
}

private fun formatFavoriteTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return "未知"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    }.format(Date(timestamp))
}

private fun formatFavoriteScheduleLabel(schedule: ApiTestFavoriteSchedule): String {
    val weekday = schedule.weekday?.let {
        listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").getOrNull(it - 1)
    }
    return if (schedule.frequency == FavoriteScheduleFrequency.Weekly) {
        "${weekday ?: "每周"} ${schedule.time}"
    } else {
        "每天 ${schedule.time}"
    }
}

private fun formatFavoriteScheduleDraft(
    frequency: FavoriteScheduleFrequency,
    time: String,
    weekday: Int
): String {
    return if (frequency == FavoriteScheduleFrequency.Weekly) {
        val day = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
            .getOrElse(weekday - 1) { "周一" }
        "每周 · $day · $time"
    } else {
        "每天 · $time"
    }
}

private fun formatFavoriteNextRun(timestamp: Long): String {
    return SimpleDateFormat("M月d日 HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    }.format(Date(timestamp))
}
