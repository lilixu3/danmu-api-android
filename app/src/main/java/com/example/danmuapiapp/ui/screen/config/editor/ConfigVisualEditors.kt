@file:OptIn(ExperimentalLayoutApi::class)

package com.example.danmuapiapp.ui.screen.config

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material3.Slider
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.AnimeCacheItem
import java.util.Locale
import kotlin.random.Random

@Composable
private fun SimpleQuickAppendEditor(
    rememberKey: String,
    value: String,
    onValueChange: (String) -> Unit,
    title: String,
    description: String,
    textLabel: String,
    placeholder: String,
    addButtonText: String = "添加规则",
    minLines: Int = 3,
    maxLines: Int = 8,
    quickTitle: String = "快速添加",
    quickContent: @Composable (appendRule: (String, String) -> Unit, closePanel: () -> Unit) -> Unit,
) {
    var showQuick by remember(rememberKey) { mutableStateOf(false) }

    fun appendRule(rule: String, separator: String) {
        val cleanRule = rule.trim()
        if (cleanRule.isBlank()) return
        onValueChange(appendSegment(value, cleanRule, separator))
        showQuick = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(textLabel) },
            placeholder = { Text(placeholder) },
            singleLine = false,
            minLines = minLines,
            maxLines = maxLines,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = { showQuick = true }) {
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(6.dp))
                Text(addButtonText)
            }
            Text(
                "可直接手写；快速添加只负责生成一条并追加到输入框。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        if (showQuick) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Tune, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(quickTitle, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showQuick = false }) {
                            Icon(Icons.Rounded.Close, "关闭")
                        }
                    }
                    quickContent(::appendRule) { showQuick = false }
                }
            }
        }
    }
}

@Composable
private fun SimpleField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 4,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder.isNotBlank()) ({ Text(placeholder) }) else null,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun OptionAssistChips(
    options: List<String>,
    onPick: (String) -> Unit,
    maxCount: Int = 14,
) {
    val cleanOptions = options.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(maxCount)
    if (cleanOptions.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cleanOptions.forEach { option ->
            AssistChip(onClick = { onPick(option) }, label = { Text(option) })
        }
    }
}

@Composable
private fun QuickActionRow(
    onAppend: () -> Unit,
    appendLabel: String = "写入输入框",
    onCancel: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(onClick = onAppend) { Text(appendLabel) }
        TextButton(onClick = onCancel) { Text("取消") }
    }
}

@Composable
private fun RecentDataToggle(
    rememberKey: String,
    currentKey: String,
    onFetchRecentAnimeCache: suspend () -> Result<List<AnimeCacheItem>>,
    onFillMergeEntity: ((String, String, String) -> Unit)? = null,
    mergeFillRole: String? = null,
    mergeFillLabel: String = "填入",
    onFillOffsetEntity: ((String, String) -> Unit)? = null,
    onAddSourcePair: ((String) -> Unit)? = null,
    compact: Boolean = false,
) {
    RecentAnimeCachePanel(
        rememberKey = "quick-$rememberKey-$currentKey",
        currentKey = currentKey,
        onFetchRecentAnimeCache = onFetchRecentAnimeCache,
        onFillMergeEntity = onFillMergeEntity,
        mergeFillRole = mergeFillRole,
        mergeFillLabel = mergeFillLabel,
        onFillOffsetEntity = onFillOffsetEntity,
        onAddSourcePair = onAddSourcePair,
        compact = compact
    )
}

internal fun appendSegment(current: String, segment: String, separator: String): String {
    val clean = segment.trim()
    if (clean.isBlank()) return current
    val base = current.trim().trimEnd(',', ';')
    if (base.isBlank()) return clean
    return base + separator + clean
}

private fun appendToken(current: String, token: String, separator: String = ","): String {
    val clean = token.trim()
    if (clean.isBlank()) return current
    val parts = current.split(Regex("[,&，、\\s]+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toMutableList()
    if (parts.none { it.equals(clean, ignoreCase = true) }) parts += clean
    return parts.joinToString(separator)
}

private fun looseTokens(raw: String): List<String> {
    return raw.split(Regex("[,&，、\\s]+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase(Locale.getDefault()) }
}

private fun normalizeSource(raw: String): String {
    return raw.trim().lowercase(Locale.getDefault())
}

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

private fun buildMatchPlatformRule(title: String, season: String, platformsRaw: String): String {
    val cleanTitle = title.trim()
    val platforms = parseCsvTokens(platformsRaw).joinToString(",")
    if (cleanTitle.isBlank() || platforms.isBlank()) return ""
    val seasonPart = formatIndexedSegment("S", season)
    val key = if (seasonPart.isBlank()) cleanTitle else "$cleanTitle/$seasonPart"
    return "$key->$platforms"
}

private fun buildTitleMappingRule(left: String, right: String): String {
    val from = left.trim()
    val to = right.trim()
    return if (from.isBlank() || to.isBlank()) "" else "$from->$to"
}

private fun buildTitlePlatformOffsetRule(title: String, platformsRaw: String, offset: String): String {
    val cleanTitle = title.trim()
    val platforms = normalizePlatforms(platformsRaw).ifBlank { "all" }
    val cleanOffset = offset.trim()
    return if (cleanTitle.isBlank() || cleanOffset.isBlank()) "" else "$cleanTitle@$platforms@$cleanOffset"
}

private fun buildMergeSourcePair(primary: String, secondariesRaw: String): String {
    val cleanPrimary = normalizeSource(primary)
    val secondaries = looseTokens(secondariesRaw).map { normalizeSource(it) }.filter { it.isNotBlank() }
    if (cleanPrimary.isBlank() || secondaries.isEmpty()) return ""
    return (listOf(cleanPrimary) + secondaries).distinct().joinToString("&")
}

@Composable
internal fun CompactCustomMergeRulesEditor(
    rememberKey: String,
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    onFetchRecentAnimeCache: suspend () -> Result<List<AnimeCacheItem>>,
) {
    var showBuilder by remember(rememberKey) { mutableStateOf(true) }
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
            OutlinedButton(onClick = { showBuilder = !showBuilder }) {
                Text(if (showBuilder) "收起" else "展开")
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
            Surface(
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
                            FilterChip(
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
                                AssistChip(
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

                    Surface(
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
                        OutlinedButton(onClick = { showBuilder = false }, modifier = Modifier.weight(1f)) { Text("取消") }
                        FilledTonalButton(
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

@Composable
internal fun CompactMatchPlatformRulesEditor(
    rememberKey: String,
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
) {
    SimpleQuickAppendEditor(
        rememberKey = rememberKey,
        value = value,
        onValueChange = onValueChange,
        title = "匹配平台优先级",
        description = "按剧名指定平台顺序。平台输入框支持手写，选项只是辅助。",
        textLabel = "匹配规则",
        placeholder = "孤独摇滚/S01->bilibili,dandan",
        minLines = 3,
        maxLines = 8,
        quickTitle = "添加平台优先级规则"
    ) { appendRule, closePanel ->
        var title by remember(rememberKey) { mutableStateOf("") }
        var season by remember(rememberKey) { mutableStateOf("") }
        var platformsRaw by remember(rememberKey) { mutableStateOf("") }

        SimpleField(title, { title = it }, "剧名", "例如：孤独摇滚")
        SimpleField(season, { season = it }, "季（可空）", "1")
        SimpleField(platformsRaw, { platformsRaw = it }, "平台顺序", "bilibili,dandan,qq")
        OptionAssistChips(options, onPick = { picked -> platformsRaw = appendToken(platformsRaw, picked, ",") })
        QuickActionRow(
            onAppend = { appendRule(buildMatchPlatformRule(title, season, platformsRaw), ";") },
            onCancel = closePanel
        )
    }
}

@Composable
internal fun CompactTitleMappingTableEditor(
    rememberKey: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    SimpleQuickAppendEditor(
        rememberKey = rememberKey,
        value = value,
        onValueChange = onValueChange,
        title = "剧名映射表",
        description = "适合别名、译名、繁简名映射。几个映射类保持同一种简单输入方式。",
        textLabel = "映射规则",
        placeholder = "孤独摇滚->BOCCHI THE ROCK!",
        minLines = 3,
        maxLines = 8,
        quickTitle = "添加剧名映射"
    ) { appendRule, closePanel ->
        var left by remember(rememberKey) { mutableStateOf("") }
        var right by remember(rememberKey) { mutableStateOf("") }
        SimpleField(left, { left = it }, "原剧名 / 别名", "孤独摇滚")
        SimpleField(right, { right = it }, "映射到", "BOCCHI THE ROCK!")
        QuickActionRow(
            onAppend = { appendRule(buildTitleMappingRule(left, right), ";") },
            onCancel = closePanel
        )
    }
}

@Composable
internal fun CompactTitlePlatformOffsetTableEditor(
    rememberKey: String,
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
) {
    SimpleQuickAppendEditor(
        rememberKey = rememberKey,
        value = value,
        onValueChange = onValueChange,
        title = "剧名平台偏移表",
        description = "按剧名 + 平台设置固定偏移。输入框可手写，平台选项只是辅助填入。",
        textLabel = "偏移规则",
        placeholder = "孤独摇滚@bilibili&qq@-1.5",
        minLines = 3,
        maxLines = 8,
        quickTitle = "添加剧名平台偏移"
    ) { appendRule, closePanel ->
        var title by remember(rememberKey) { mutableStateOf("") }
        var platformsRaw by remember(rememberKey) { mutableStateOf("all") }
        var offset by remember(rememberKey) { mutableStateOf("") }
        SimpleField(title, { title = it }, "剧名", "孤独摇滚")
        SimpleField(platformsRaw, { platformsRaw = it }, "平台", "all 或 bilibili&qq")
        OptionAssistChips(normalizePlatformOptions(options), onPick = { picked ->
            platformsRaw = if (picked.equals("all", true)) "all" else appendToken(platformsRaw.takeUnless { it == "all" }.orEmpty(), picked, "&")
        })
        SimpleField(offset, { offset = it }, "偏移秒数", "-1.5")
        QuickActionRow(
            onAppend = { appendRule(buildTitlePlatformOffsetRule(title, platformsRaw, offset), ";") },
            onCancel = closePanel
        )
    }
}

@Composable
internal fun CompactMergeSourcePairsEditor(
    rememberKey: String,
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    onFetchRecentAnimeCache: suspend () -> Result<List<AnimeCacheItem>>,
) {
    var staging by remember(rememberKey) { mutableStateOf<List<String>>(emptyList()) }
    var mergeMode by remember(rememberKey) { mutableStateOf(false) }
    var showCustom by remember(rememberKey) { mutableStateOf(false) }
    var customSource by remember(rememberKey) { mutableStateOf("") }
    val groups = remember(value) { parseCsvTokens(value) }
    val cleanOptions = remember(options) { options.map { it.trim() }.filter { it.isNotBlank() }.distinct() }

    fun setGroups(next: List<String>) {
        onValueChange(next.map { it.trim() }.filter { it.isNotBlank() }.joinToString(","))
    }

    fun addToStaging(raw: String) {
        val clean = normalizeSource(raw)
        if (clean.isNotBlank() && !staging.any { it.equals(clean, ignoreCase = true) }) {
            staging = staging + clean
        }
    }

    fun addOption(raw: String) {
        if (!mergeMode) return
        val clean = normalizeSource(raw)
        if (clean.isBlank()) return
        addToStaging(clean)
    }

    fun confirmStaging() {
        if (staging.size < 2) return
        val group = staging.joinToString("&")
        if (!groups.any { it.equals(group, ignoreCase = true) }) {
            setGroups(groups + group)
        }
        staging = emptyList()
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("来源合并组", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(
            "参考前端合并模式：开启后把来源点进暂存区，组合好再点确认组合。组内第一个来源优先。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text("已添加合并组", style = MaterialTheme.typography.labelLarge)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (groups.isEmpty()) {
                Text(
                    "暂无合并组，开启合并模式后选择来源并确认组合。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            } else {
                FlowRow(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    groups.forEach { group ->
                        FilterChip(
                            selected = true,
                            onClick = { setGroups(groups.filterNot { it == group }) },
                            label = { Text("$group ×") }
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = mergeMode,
                onClick = {
                    mergeMode = !mergeMode
                    if (!mergeMode) staging = emptyList()
                },
                label = { Text(if (mergeMode) "🔗 合并模式已开启，点击关闭" else "🔗 开启合并模式") }
            )
            Text(
                if (mergeMode) "点击来源会加入暂存区，组合完成后点确认组合。" else "默认关闭；先开启合并模式，再选择需要组合的来源。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }

        if (mergeMode) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "合并组暂存区：",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalIconButton(
                            onClick = ::confirmStaging,
                            enabled = staging.size >= 2,
                            modifier = Modifier.size(36.dp)
                        ) { Text("✓", maxLines = 1) }
                    }
                    if (staging.isEmpty()) {
                        Text(
                            "请点击下方选项进行组合…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            staging.forEachIndexed { index, source ->
                                if (index > 0) Text("&", color = MaterialTheme.colorScheme.primary, maxLines = 1)
                                FilterChip(
                                    selected = true,
                                    onClick = { staging = staging.filterNot { it == source } },
                                    label = {
                                        Text(
                                            "$source ×",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Text("可选项（点击添加）", style = MaterialTheme.typography.labelLarge)
        if (cleanOptions.isEmpty()) {
            Text("当前核心没有提供来源选项，可点自定义来源手动添加。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                cleanOptions.forEach { option ->
                    val staged = staging.any { it.equals(option, ignoreCase = true) }
                    AssistChip(
                        onClick = { addOption(option) },
                        enabled = mergeMode && !staged,
                        label = { Text(option) }
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showCustom = !showCustom }) { Text(if (showCustom) "收起自定义" else "自定义来源") }
            if (staging.isNotEmpty()) TextButton(onClick = { staging = emptyList() }) { Text("清空暂存") }
        }

        if (showCustom) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = customSource,
                    onValueChange = { customSource = it },
                    label = { Text("来源名") },
                    placeholder = { Text("bilibili") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(
                    onClick = { addOption(customSource); customSource = "" },
                    enabled = mergeMode
                ) { Text("添加") }
            }
        }

        RecentDataToggle(
            rememberKey = rememberKey,
            currentKey = KEY_MERGE_SOURCE_PAIRS,
            onFetchRecentAnimeCache = onFetchRecentAnimeCache,
            onAddSourcePair = if (mergeMode) ({ source: String -> addOption(source) }) else null,
            compact = true
        )

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("原始值") },
            placeholder = { Text("tencent&migu,bilibili&dandan") },
            singleLine = false,
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun VodServersEditor(
    rememberKey: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    SimpleQuickAppendEditor(
        rememberKey = rememberKey,
        value = value,
        onValueChange = onValueChange,
        title = "VOD 站点",
        description = "直接维护站点列表；快速添加只生成“名称@地址”。",
        textLabel = "站点列表",
        placeholder = "站点名@https://example.com/api.php/provide/vod",
        minLines = 3,
        maxLines = 8,
        quickTitle = "添加 VOD 站点"
    ) { appendRule, closePanel ->
        var name by remember(rememberKey) { mutableStateOf("") }
        var url by remember(rememberKey) { mutableStateOf("") }
        SimpleField(name, { name = it }, "名称（可空）", "我的站点")
        SimpleField(url, { url = it }, "接口地址", "https://example.com/api.php/provide/vod")
        QuickActionRow(
            onAppend = {
                val cleanUrl = url.trim()
                val rule = if (cleanUrl.isBlank()) "" else if (name.isBlank()) cleanUrl else "${name.trim()}@$cleanUrl"
                appendRule(rule, ",")
            },
            onCancel = closePanel
        )
    }
}

@Composable
internal fun SourceConcurrencyBySourceEditor(
    rememberKey: String,
    value: String,
    onValueChange: (String) -> Unit,
    sourceOptions: List<String>,
) {
    SimpleQuickAppendEditor(
        rememberKey = rememberKey,
        value = value,
        onValueChange = onValueChange,
        title = "按来源并发数",
        description = "给某个来源单独设置并发数。可以直接手写 source:数量。",
        textLabel = "并发覆盖规则",
        placeholder = "bilibili:2,qq:3",
        minLines = 2,
        maxLines = 6,
        quickTitle = "添加来源并发规则"
    ) { appendRule, closePanel ->
        var source by remember(rememberKey) { mutableStateOf("") }
        var concurrency by remember(rememberKey) { mutableStateOf("") }
        SimpleField(source, { source = it }, "来源", "bilibili")
        OptionAssistChips(sourceOptions, onPick = { source = it })
        SimpleField(concurrency, { concurrency = it }, "并发数", "2")
        QuickActionRow(
            onAppend = {
                val rule = if (source.isBlank() || concurrency.isBlank()) "" else "${normalizeSource(source)}:${concurrency.trim()}"
                appendRule(rule, ",")
            },
            onCancel = closePanel
        )
    }
}

@Composable
internal fun IpBlacklistEditor(
    rememberKey: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    SimpleQuickAppendEditor(
        rememberKey = rememberKey,
        value = value,
        onValueChange = onValueChange,
        title = "IP 黑名单",
        description = "支持单个 IP、CIDR 或正则。默认直接追加到列表。",
        textLabel = "黑名单列表",
        placeholder = "1.2.3.4,10.0.0.0/24,/^192\\.168\\./",
        minLines = 3,
        maxLines = 8,
        quickTitle = "添加 IP 规则"
    ) { appendRule, closePanel ->
        var kind by remember(rememberKey) { mutableStateOf("exact") }
        var valueText by remember(rememberKey) { mutableStateOf("") }
        var flags by remember(rememberKey) { mutableStateOf("") }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = kind == "exact", onClick = { kind = "exact" }, label = { Text("IP") })
            FilterChip(selected = kind == "cidr", onClick = { kind = "cidr" }, label = { Text("CIDR") })
            FilterChip(selected = kind == "regex", onClick = { kind = "regex" }, label = { Text("正则") })
        }
        SimpleField(valueText, { valueText = it }, if (kind == "regex") "正则内容" else "IP / 网段", if (kind == "cidr") "10.0.0.0" else "1.2.3.4")
        if (kind == "cidr") SimpleField(flags, { flags = it }, "掩码位", "24")
        if (kind == "regex") SimpleField(flags, { flags = it }, "正则 flags（可空）", "i")
        QuickActionRow(
            onAppend = {
                val clean = valueText.trim()
                val rule = when {
                    clean.isBlank() -> ""
                    kind == "cidr" -> "$clean/${flags.trim().ifBlank { "24" }}"
                    kind == "regex" -> "/$clean/${flags.trim()}"
                    else -> clean
                }
                appendRule(rule, ",")
            },
            onCancel = closePanel
        )
    }
}

private val DefaultDanmuColorPool = listOf(
    16777215, 16777215, 16777215, 16777215,
    16777215, 16777215, 16777215, 16777215,
    16744319, 16752762, 16774799, 9498256,
    8388564, 8900346, 14204888, 16758465
)

private val QuickColorPalette = listOf(
    0xFFFFFF, 0xFF6699, 0xFFCC00, 0x66CC66,
    0x00AEEF, 0x8A2BE2, 0xFF5733, 0x2ECC71,
    0x3498DB, 0xF1C40F, 0xE91E63, 0x9B59B6,
    0x1ABC9C, 0xFF7F50, 0xC0C0C0, 0x000000
)

private fun parseColorToken(raw: String): Int? {
    var token = raw.trim()
    if (token.isBlank()) return null
    if (token.equals("default", true) || token.equals("white", true) || token.equals("color", true)) return null
    token = token.removePrefix("#")
    if (token.startsWith("0x", ignoreCase = true)) token = token.drop(2)
    if (token.length == 3 && token.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
        token = token.map { "$it$it" }.joinToString("")
    }
    val value = when {
        token.length == 6 && token.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } -> token.toIntOrNull(16)
        token.all { it.isDigit() } -> token.toIntOrNull(10)
        else -> null
    } ?: return null
    return value.takeIf { it in 0..0xFFFFFF }
}

private fun parseColorList(raw: String): List<Int> {
    return raw.split(Regex("[,;\\s，、]+"))
        .mapNotNull { parseColorToken(it) }
}

private fun colorHex(color: Int): String {
    return "#" + (color and 0xFFFFFF).toString(16).padStart(6, '0').uppercase(Locale.getDefault())
}

private fun colorCompose(color: Int): Color {
    return Color(0xFF000000.toInt() or (color and 0xFFFFFF))
}

private fun appendColorsToRaw(current: String, colors: List<Int>): String {
    val cleanColors = colors.filter { it in 0..0xFFFFFF }
    if (cleanColors.isEmpty()) return current
    val currentMode = current.trim().lowercase(Locale.getDefault())
    val baseColors = when (currentMode) {
        "white" -> listOf(16777215)
        "default", "color" -> emptyList()
        else -> parseColorList(current)
    }
    return (baseColors + cleanColors).joinToString(",")
}

private fun replaceWithDefaultColorPool(): String = DefaultDanmuColorPool.joinToString(",")

@Composable
private fun ColorDot(color: Int, modifier: Modifier = Modifier.size(16.dp)) {
    Box(
        modifier = modifier
            .background(colorCompose(color), CircleShape)
    )
}

@Composable
private fun ColorPreviewChips(colors: List<Int>, maxCount: Int = 32) {
    if (colors.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        colors.take(maxCount).forEach { color ->
            AssistChip(
                onClick = {},
                leadingIcon = { ColorDot(color) },
                label = { Text(colorHex(color)) }
            )
        }
        if (colors.size > maxCount) {
            AssistChip(onClick = {}, label = { Text("+${colors.size - maxCount}") })
        }
    }
}

@Composable
private fun HueWheel(
    hue: Float,
    color: Int,
    onHueChange: (Float) -> Unit,
) {
    val centerColor = MaterialTheme.colorScheme.surface
    Canvas(
        modifier = Modifier
            .size(132.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val dx = change.position.x - centerX
                    val dy = change.position.y - centerY
                    val angle = ((atan2(dy, dx) * 180f / PI.toFloat()) + 90f + 360f) % 360f
                    onHueChange(angle)
                    change.consume()
                }
            }
    ) {
        val radius = min(size.width, size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            brush = Brush.sweepGradient(
                listOf(
                    Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
                    Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000)
                )
            ),
            radius = radius,
            center = center
        )
        drawCircle(color = centerColor, radius = radius * 0.42f, center = center)
        val rad = (hue - 90f) * PI.toFloat() / 180f
        val dotCenter = Offset(
            center.x + radius * 0.72f * cos(rad),
            center.y + radius * 0.72f * sin(rad)
        )
        drawCircle(color = Color.White, radius = 8.dp.toPx(), center = dotCenter)
        drawCircle(color = colorCompose(color), radius = 5.5.dp.toPx(), center = dotCenter)
    }
}

private fun hslToDecimal(h: Float, saturationPercent: Float, lightnessPercent: Float): Int {
    val s = (saturationPercent / 100f).coerceIn(0f, 1f)
    val l = (lightnessPercent / 100f).coerceIn(0f, 1f)
    val a = s * kotlin.math.min(l, 1f - l)
    fun f(n: Float): Float {
        val k = (n + h / 30f) % 12f
        return l - a * maxOf(-1f, minOf(k - 3f, minOf(9f - k, 1f)))
    }
    val r = Math.round(f(0f) * 255f).coerceIn(0, 255)
    val g = Math.round(f(8f) * 255f).coerceIn(0, 255)
    val b = Math.round(f(4f) * 255f).coerceIn(0, 255)
    return (r shl 16) or (g shl 8) or b
}

@Composable
internal fun ColorListEditor(
    rememberKey: String,
    value: String,
    onValueChange: (String) -> Unit,
    title: String,
    envKey: String,
) {
    var panel by remember(rememberKey) { mutableStateOf("none") }
    var colorInput by remember(rememberKey) { mutableStateOf("#FFFFFF") }
    var batchInput by remember(rememberKey) { mutableStateOf("") }
    var hue by remember(rememberKey) { mutableStateOf(0f) }
    var lightness by remember(rememberKey) { mutableStateOf(50f) }

    val normalizedKey = remember(envKey) { envKey.trim().uppercase(Locale.getDefault()) }
    val isConvertColor = normalizedKey == "CONVERT_COLOR"
    val parsedColors = remember(value) { parseColorList(value) }
    val rawMode = value.trim().lowercase(Locale.getDefault())
    val wheelColor = remember(hue, lightness) { hslToDecimal(hue, 100f, lightness) }
    val selectedColor = parseColorToken(colorInput)
    val batchColors = remember(batchInput) { parseColorList(batchInput) }
    val modeSummary = when {
        isConvertColor && (rawMode.isBlank() || rawMode == "default") -> "当前：default，不转换弹幕颜色。"
        isConvertColor && rawMode == "white" -> "当前：white，全部弹幕转白色。"
        isConvertColor && rawMode == "color" -> "当前：color，使用 COLOR_POOL 随机颜色池。"
        isConvertColor -> "当前：本变量直接保存颜色列表，已识别 ${parsedColors.size} 个颜色。"
        rawMode.isBlank() -> "当前：未配置，将使用核心默认颜色池。"
        else -> "当前：COLOR_POOL 已识别 ${parsedColors.size} 个颜色。"
    }

    fun appendColors(colors: List<Int>) {
        onValueChange(appendColorsToRaw(value, colors))
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(
            if (isConvertColor) {
                "按稳定版页面处理：优先用 default / white / color 三个模式；需要直接写颜色时再展开颜色盘。"
            } else {
                "按稳定版颜色池处理：先显示当前颜色，再用颜色盘、随机、批量添加。保存值始终是核心识别的十进制颜色。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isConvertColor) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = rawMode.isBlank() || rawMode == "default", onClick = { onValueChange("default"); panel = "none" }, label = { Text("default") })
                FilterChip(selected = rawMode == "white", onClick = { onValueChange("white"); panel = "none" }, label = { Text("white") })
                FilterChip(selected = rawMode == "color", onClick = { onValueChange("color"); panel = "none" }, label = { Text("color") })
            }
        }

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(if (isConvertColor) "原始值 / 颜色列表" else "颜色池原始值") },
            placeholder = { Text(if (isConvertColor) "default / white / color / 16777215,16744319" else "16777215,16744319,9498256") },
            singleLine = false,
            minLines = 2,
            maxLines = 5,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Text(modeSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (parsedColors.isNotEmpty()) {
            Text("当前颜色", style = MaterialTheme.typography.labelLarge)
            ColorPreviewChips(parsedColors, maxCount = 20)
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalButton(onClick = { panel = if (panel == "color") "none" else "color" }) {
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(6.dp))
                Text(if (panel == "color") "收起颜色盘" else "颜色盘")
            }
            OutlinedButton(onClick = { appendColors(listOf(Random.nextInt(0x1000000))) }) { Text("随机添加") }
            OutlinedButton(onClick = { panel = if (panel == "batch") "none" else "batch" }) { Text("批量添加") }
            TextButton(onClick = { onValueChange(if (isConvertColor) "default" else "") }) { Text("恢复默认") }
        }

        if (panel == "color") {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("颜色盘", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        HueWheel(hue = hue, color = wheelColor, onHueChange = { hue = it })
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ColorDot(wheelColor, modifier = Modifier.size(34.dp))
                                Text(colorHex(wheelColor), style = MaterialTheme.typography.bodyMedium)
                            }
                            Text("亮度", style = MaterialTheme.typography.labelMedium)
                            Slider(value = lightness, onValueChange = { lightness = it }, valueRange = 10f..90f)
                            FilledTonalButton(onClick = { appendColors(listOf(wheelColor)); colorInput = colorHex(wheelColor) }) { Text("添加当前颜色") }
                        }
                    }

                    Text("常用色", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        QuickColorPalette.forEach { preset ->
                            AssistChip(
                                onClick = { colorInput = colorHex(preset); appendColors(listOf(preset)) },
                                leadingIcon = { ColorDot(preset) },
                                label = { Text(colorHex(preset)) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        ColorDot(selectedColor ?: wheelColor, modifier = Modifier.size(28.dp))
                        OutlinedTextField(
                            value = colorInput,
                            onValueChange = { colorInput = it },
                            label = { Text("HEX / 十进制") },
                            placeholder = { Text("#FF6699 或 16737945") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(onClick = { selectedColor?.let { appendColors(listOf(it)) } }, enabled = selectedColor != null) { Text("添加输入颜色") }
                        TextButton(onClick = { panel = "none" }) { Text("完成") }
                    }
                }
            }
        }

        if (panel == "batch") {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("批量添加颜色", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = batchInput,
                        onValueChange = { batchInput = it },
                        label = { Text("颜色列表") },
                        placeholder = { Text("#FFFFFF, FF6699, 16777215\n支持逗号、空格、换行") },
                        singleLine = false,
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (batchColors.isNotEmpty()) {
                        Text("预览：${batchColors.size} 个颜色", style = MaterialTheme.typography.bodySmall)
                        ColorPreviewChips(batchColors, maxCount = 20)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = {
                                appendColors(batchColors)
                                batchInput = ""
                                panel = "none"
                            },
                            enabled = batchColors.isNotEmpty()
                        ) { Text("确认添加") }
                        TextButton(onClick = { panel = "none" }) { Text("取消") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun KeywordListEditor(
    rememberKey: String,
    value: String,
    onValueChange: (String) -> Unit,
    title: String,
    subtitle: String,
) {
    SimpleQuickAppendEditor(
        rememberKey = rememberKey,
        value = value,
        onValueChange = onValueChange,
        title = title,
        description = subtitle.ifBlank { "直接维护关键词列表。" },
        textLabel = "关键词列表",
        placeholder = "关键词1,关键词2",
        minLines = 2,
        maxLines = 6,
        quickTitle = "添加关键词"
    ) { appendRule, closePanel ->
        var keyword by remember(rememberKey) { mutableStateOf("") }
        SimpleField(keyword, { keyword = it }, "关键词", "广告")
        QuickActionRow(
            onAppend = { appendRule(keyword, ",") },
            onCancel = closePanel
        )
    }
}
