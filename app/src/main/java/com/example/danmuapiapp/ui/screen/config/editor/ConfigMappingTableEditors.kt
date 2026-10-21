@file:OptIn(ExperimentalLayoutApi::class)

package com.example.danmuapiapp.ui.screen.config

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassAssistChip
import com.example.danmuapiapp.ui.component.liquid.AppGlassFilterChip
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton
import com.example.danmuapiapp.domain.model.AnimeCacheItem

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
        AppGlassSurface(
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
                        AppGlassFilterChip(
                            selected = true,
                            onClick = { setGroups(groups.filterNot { it == group }) },
                            label = { Text("$group ×") }
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppGlassFilterChip(
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
            AppGlassSurface(
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
                        AppGlassIconButton(
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
                                AppGlassFilterChip(
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
                    AppGlassAssistChip(
                        onClick = { addOption(option) },
                        enabled = mergeMode && !staged,
                        label = { Text(option) }
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppGlassButton(onClick = { showCustom = !showCustom }) { Text(if (showCustom) "收起自定义" else "自定义来源") }
            if (staging.isNotEmpty()) AppGlassButton(onClick = { staging = emptyList() }) { Text("清空暂存") }
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
                AppGlassButton(
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
