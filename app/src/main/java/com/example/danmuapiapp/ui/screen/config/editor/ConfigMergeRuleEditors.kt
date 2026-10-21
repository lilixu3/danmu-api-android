@file:OptIn(ExperimentalLayoutApi::class)

package com.example.danmuapiapp.ui.screen.config

import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassAssistChip
import com.example.danmuapiapp.ui.component.liquid.AppGlassFilterChip
import com.example.danmuapiapp.domain.model.AnimeCacheItem

private fun entityText(title: String, season: String, source: String): String {
    val cleanTitle = title.trim()
    val cleanSource = normalizeSource(source)
    if (cleanTitle.isBlank() || cleanSource.isBlank()) return ""
    val seasonPart = formatIndexedSegment("S", season)
    return buildString {
        append(cleanTitle)
        if (seasonPart.isNotBlank()) append('/').append(seasonPart)
        append('@').append(cleanSource)
    }
}

private fun buildCustomMergeRule(
    action: String,
    secondaryTitle: String,
    secondarySeason: String,
    secondarySource: String,
    primaryTitle: String,
    primarySeason: String,
    primarySource: String,
    routesRaw: String,
): String {
    val secondary = entityText(secondaryTitle, secondarySeason, secondarySource)
    val primary = entityText(primaryTitle, primarySeason, primarySource)
    if (secondary.isBlank() || primary.isBlank()) return ""
    val connector = if (action == "block") " × " else " -> "
    val routes = routesRaw.trim()
    return if (action == "merge" && routes.isNotBlank()) {
        "$secondary$connector$primary | $routes"
    } else {
        "$secondary$connector$primary"
    }
}

private fun normalizeMergeEntity(raw: String): String {
    return raw.trim()
        .replace('，', ',')
        .replace("＠", "@")
}

private fun buildCustomMergeRuleFromEntities(
    action: String,
    entityA: String,
    entityB: String,
    routesRaw: String,
): String {
    val a = normalizeMergeEntity(entityA)
    val b = normalizeMergeEntity(entityB)
    if (a.isBlank() || b.isBlank() || !a.contains('@') || !b.contains('@')) return ""
    val connector = if (action == "block") " × " else " -> "
    val routes = routesRaw.trim()
    return if (action == "merge" && routes.isNotBlank()) "$a$connector$b | $routes" else "$a$connector$b"
}

private fun appendSourceToMergeEntity(raw: String, source: String): String {
    val cleanSource = normalizeSource(source)
    if (cleanSource.isBlank()) return raw
    val text = raw.trim()
    if (text.isBlank()) return "@$cleanSource"
    val atIndex = text.lastIndexOf('@')
    if (atIndex < 0) return "$text@$cleanSource"
    val currentSources = text.substring(atIndex + 1)
        .split('&', ',', '，', '、')
        .map { normalizeSource(it) }
        .filter { it.isNotBlank() }
    if (currentSources.any { it.equals(cleanSource, ignoreCase = true) }) return text
    return "$text&$cleanSource"
}

private fun buildTimelineRule(
    title: String,
    season: String,
    episode: String,
    sourcesRaw: String,
    offset: String,
    percent: Boolean,
): String {
    val cleanTitle = title.trim()
    val cleanOffset = offset.trim()
    if (cleanTitle.isBlank() || cleanOffset.isBlank()) return ""
    val path = buildList {
        add(cleanTitle)
        formatIndexedSegment("S", season).takeIf { it.isNotBlank() }?.let { add(it) }
        formatIndexedSegment("E", episode).takeIf { it.isNotBlank() }?.let { add(it) }
    }.joinToString("/")
    val sources = normalizePlatforms(sourcesRaw).ifBlank { "all" }
    return "$path@$sources${if (percent) "%" else ""}:$cleanOffset"
}

@Composable
internal fun CompactCustomMergeRulesEditor(
    rememberKey: String,
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    onFetchRecentAnimeCache: suspend () -> Result<List<AnimeCacheItem>>,
) {
    var showBuilder by remember(rememberKey) { mutableStateOf(false) }
    var action by remember(rememberKey) { mutableStateOf("merge") }
    var focusedEntity by remember(rememberKey) { mutableStateOf("secondary") }
    var secondaryEntity by remember(rememberKey) { mutableStateOf("") }
    var primaryEntity by remember(rememberKey) { mutableStateOf("") }
    var routesRaw by remember(rememberKey) { mutableStateOf("") }
    val sourceOptions = remember(options) { options.map { it.trim() }.filter { it.isNotBlank() }.distinct() }
    val preview = buildCustomMergeRuleFromEntities(action, secondaryEntity, primaryEntity, routesRaw)

    fun appendRule(rule: String) {
        val cleanRule = rule.trim()
        if (cleanRule.isBlank()) return
        onValueChange(appendSegment(value, cleanRule, "; "))
        secondaryEntity = ""
        primaryEntity = ""
        routesRaw = ""
    }

    fun fillFromCache(role: String, title: String, source: String) {
        val cleanTitle = cleanCacheAnimeTitle(title)
        val cleanSource = normalizeSource(source)
        val entity = if (cleanTitle.isBlank() || cleanSource.isBlank()) "" else "$cleanTitle@$cleanSource"
        if (entity.isBlank()) return
        if (role == "prim" || role == "b") {
            primaryEntity = entity
            focusedEntity = "primary"
        } else {
            secondaryEntity = entity
            focusedEntity = "secondary"
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("变量值", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("变量值") },
            placeholder = { Text("格式：副源 -> 主源 | 路由规则 或 副源 × 主源") },
            singleLine = false,
            minLines = 4,
            maxLines = 8,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppGlassButton(onClick = { showBuilder = !showBuilder }) {
                Text(if (showBuilder) "收起规则表单" else "添加规则")
            }
            RecentDataToggle(
                rememberKey = rememberKey,
                currentKey = KEY_CUSTOM_MERGE_RULES,
                onFetchRecentAnimeCache = onFetchRecentAnimeCache,
                onFillMergeEntity = ::fillFromCache,
                compact = true
            )
        }

        if (showBuilder) {
            AppGlassSurface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = secondaryEntity,
                            onValueChange = { secondaryEntity = it },
                            label = { Text("副源实体（副源剧名@源）") },
                            placeholder = { Text("例: 我推的孩子/S01@bahamut") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { if (it.isFocused) focusedEntity = "secondary" }
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("关系", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            AppGlassFilterChip(
                                selected = action == "merge",
                                onClick = { action = if (action == "merge") "block" else "merge" },
                                label = { Text(if (action == "merge") "->" else "×") }
                            )
                            Text(if (action == "merge") "合并" else "阻断", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    OutlinedTextField(
                        value = primaryEntity,
                        onValueChange = { primaryEntity = it },
                        label = { Text("主源实体（主源剧名@源）") },
                        placeholder = { Text("例: 我推的孩子/S03@dandan") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (it.isFocused) focusedEntity = "primary" }
                    )

                    if (action == "merge") {
                        OutlinedTextField(
                            value = routesRaw,
                            onValueChange = { routesRaw = it },
                            label = { Text("集数路由规则（选填，可多组。例如：E01>E01,E25~E35>E25~E35）") },
                            placeholder = { Text("留空则交由系统自动计算偏移") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Text(
                        "快速追加来源至当前聚焦的输入框（没有 @ 则追加 @xxx，已存在 @ 则追加 &xxx 合并写法）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (sourceOptions.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            sourceOptions.forEach { option ->
                                AppGlassAssistChip(
                                    onClick = {
                                        if (focusedEntity == "primary") {
                                            primaryEntity = appendSourceToMergeEntity(primaryEntity, option)
                                        } else {
                                            secondaryEntity = appendSourceToMergeEntity(secondaryEntity, option)
                                        }
                                    },
                                    label = { Text(option) }
                                )
                            }
                        }
                    }

                    AppGlassSurface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            preview.ifBlank { "填写副源实体和主源实体后生成预览" },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (preview.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        AppGlassButton(onClick = { showBuilder = false }, modifier = Modifier.weight(1f)) { Text("取消") }
                        AppGlassButton(
                            onClick = { appendRule(preview) },
                            enabled = preview.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) { Text("确认添加") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CompactDanmuOffsetEditor(
    rememberKey: String,
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    onFetchRecentAnimeCache: suspend () -> Result<List<AnimeCacheItem>>,
) {
    SimpleQuickAppendEditor(
        rememberKey = rememberKey,
        value = value,
        onValueChange = onValueChange,
        title = "弹幕时间轴偏移",
        description = "直接编辑偏移规则。新增规则只需要剧名、来源和偏移值，季/集可不填。",
        textLabel = "偏移规则",
        placeholder = "剧名/S01/E02@bilibili&qq:-1.5",
        minLines = 3,
        maxLines = 8,
        quickTitle = "添加偏移规则"
    ) { appendRule, closePanel ->
        var title by remember(rememberKey) { mutableStateOf("") }
        var season by remember(rememberKey) { mutableStateOf("") }
        var episode by remember(rememberKey) { mutableStateOf("") }
        var sourcesRaw by remember(rememberKey) { mutableStateOf("all") }
        var offset by remember(rememberKey) { mutableStateOf("") }
        var percent by remember(rememberKey) { mutableStateOf(false) }

        SimpleField(title, { title = it }, "剧名", "例如：孤独摇滚")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = season,
                onValueChange = { season = it },
                label = { Text("季，可空") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = episode,
                onValueChange = { episode = it },
                label = { Text("集，可空") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
        }
        SimpleField(sourcesRaw, { sourcesRaw = it }, "来源", "all 或 bilibili&qq")
        OptionAssistChips(normalizePlatformOptions(options), onPick = { picked ->
            sourcesRaw = if (picked.equals("all", true)) "all" else appendToken(sourcesRaw.takeUnless { it == "all" }.orEmpty(), picked, "&")
        })
        SimpleField(offset, { offset = it }, "偏移秒数", "-1.5 / 2.0")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = percent, onCheckedChange = { percent = it })
            Text("按百分比偏移", style = MaterialTheme.typography.bodySmall)
        }
        RecentDataToggle(
            rememberKey = rememberKey,
            currentKey = KEY_DANMU_OFFSET,
            onFetchRecentAnimeCache = onFetchRecentAnimeCache,
            onFillOffsetEntity = { cacheTitle, source ->
                title = cleanCacheAnimeTitle(cacheTitle)
                val cleanSource = normalizeSource(source)
                if (cleanSource.isNotBlank()) sourcesRaw = cleanSource
            }
        )
        QuickActionRow(
            onAppend = { appendRule(buildTimelineRule(title, season, episode, sourcesRaw, offset, percent), ",") },
            onCancel = closePanel
        )
    }
}
