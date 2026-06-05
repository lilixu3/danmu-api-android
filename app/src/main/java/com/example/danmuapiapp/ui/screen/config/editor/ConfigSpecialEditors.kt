package com.example.danmuapiapp.ui.screen.config

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ImageNotSupported
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.danmuapiapp.domain.model.AnimeCacheItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CustomMergeRulesEditor(
    rememberKey: String,
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    onFetchRecentAnimeCache: suspend () -> Result<List<AnimeCacheItem>>,
) {
    var rows by remember(rememberKey) {
        mutableStateOf(parseCustomMergeRules(value).ifEmpty { listOf(CustomMergeRuleRow()) })
    }
    val sourceOptions = remember(options) {
        options.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }

    fun syncRows(next: List<CustomMergeRuleRow>) {
        rows = next
        onValueChange(serializeCustomMergeRules(next))
    }

    fun updateRow(index: Int, block: (CustomMergeRuleRow) -> CustomMergeRuleRow) {
        if (index !in rows.indices) return
        syncRows(rows.replace(index, block(rows[index])))
    }

    fun fillEntity(role: String, title: String, source: String) {
        val cleanTitle = cleanCacheAnimeTitle(title)
        val cleanSource = source.trim().lowercase()
        if (cleanTitle.isBlank() || cleanSource.isBlank()) return
        val targetIndex = rows.indexOfFirst { row ->
            if (role == "prim") {
                row.primaryTitle.isBlank() || row.primarySource.isBlank()
            } else {
                row.secondaryTitle.isBlank() || row.secondarySource.isBlank()
            }
        }.takeIf { it >= 0 } ?: rows.size

        val nextRows = rows.toMutableList()
        if (targetIndex == rows.size) nextRows += CustomMergeRuleRow()
        val row = nextRows[targetIndex]
        nextRows[targetIndex] = if (role == "prim") {
            row.copy(primaryTitle = cleanTitle, primarySource = cleanSource)
        } else {
            row.copy(secondaryTitle = cleanTitle, secondarySource = cleanSource)
        }
        syncRows(nextRows)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("自定义合并规则", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "支持强制合并、阻断合并和集数路由。格式示例：结果A剧名/S01@bilibili -> 结果B剧名/S03@dandan | E25~E35>E1~E11",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            rows.forEachIndexed { index, row ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "规则 ${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = row.action != "block",
                                onClick = {
                                    updateRow(index) { it.copy(action = "merge") }
                                },
                                label = { Text("强制合并") }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            FilterChip(
                                selected = row.action == "block",
                                onClick = {
                                    updateRow(index) { it.copy(action = "block") }
                                },
                                label = { Text("阻断合并") }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = {
                                val next = rows.filterIndexed { i, _ -> i != index }
                                syncRows(if (next.isEmpty()) listOf(CustomMergeRuleRow()) else next)
                            }) {
                                Icon(Icons.Rounded.Close, "删除")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            EntityEditorCard(
                                titleLabel = "结果 A",
                                title = row.secondaryTitle,
                                season = row.secondarySeason,
                                source = row.secondarySource,
                                sourceOptions = sourceOptions,
                                onTitleChange = { text -> updateRow(index) { it.copy(secondaryTitle = text) } },
                                onSeasonChange = { text -> updateRow(index) { it.copy(secondarySeason = text) } },
                                onSourceChange = { text -> updateRow(index) { it.copy(secondarySource = text) } },
                                modifier = Modifier.weight(1f)
                            )

                            Box(
                                modifier = Modifier
                                    .padding(top = 40.dp)
                                    .align(Alignment.CenterVertically)
                            ) {
                                Text(
                                    text = if (row.action == "block") "×" else "→",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            EntityEditorCard(
                                titleLabel = "结果 B",
                                title = row.primaryTitle,
                                season = row.primarySeason,
                                source = row.primarySource,
                                sourceOptions = sourceOptions,
                                onTitleChange = { text -> updateRow(index) { it.copy(primaryTitle = text) } },
                                onSeasonChange = { text -> updateRow(index) { it.copy(primarySeason = text) } },
                                onSourceChange = { text -> updateRow(index) { it.copy(primarySource = text) } },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (row.action != "block") {
                            OutlinedTextField(
                                value = row.routesRaw,
                                onValueChange = { text -> updateRow(index) { it.copy(routesRaw = text) } },
                                label = { Text("集数路由（可选）") },
                                placeholder = { Text("E25~E35>E1~E11, E36>E12") },
                                singleLine = false,
                                minLines = 2,
                                maxLines = 4,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { syncRows(rows + CustomMergeRuleRow()) }) { Text("新增规则") }
                OutlinedButton(onClick = {
                    val parsed = parseCustomMergeRules(value)
                    rows = if (parsed.isEmpty()) listOf(CustomMergeRuleRow()) else parsed
                }) {
                    Text("从原始值解析")
                }
            }

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("原始值（高级）") },
                singleLine = false,
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            RecentAnimeCachePanel(
                rememberKey = rememberKey,
                currentKey = KEY_CUSTOM_MERGE_RULES,
                onFetchRecentAnimeCache = onFetchRecentAnimeCache,
                onFillMergeEntity = { role, title, source -> fillEntity(role, title, source) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DanmuOffsetEditor(
    rememberKey: String,
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    onFetchRecentAnimeCache: suspend () -> Result<List<AnimeCacheItem>>,
) {
    var rows by remember(rememberKey) {
        mutableStateOf(parseTimelineOffsetRules(value).ifEmpty { listOf(TimelineOffsetRuleRow()) })
    }
    val sourceOptions = remember(options) { normalizePlatformOptions(options) }

    fun syncRows(next: List<TimelineOffsetRuleRow>) {
        rows = next
        onValueChange(serializeTimelineOffsetRules(next))
    }

    fun updateRow(index: Int, block: (TimelineOffsetRuleRow) -> TimelineOffsetRuleRow) {
        if (index !in rows.indices) return
        syncRows(rows.replace(index, block(rows[index])))
    }

    fun fillOffsetEntity(title: String, source: String) {
        val cleanTitle = cleanCacheAnimeTitle(title)
        val cleanSource = source.trim().lowercase()
        if (cleanTitle.isBlank()) return
        val targetIndex = rows.indexOfFirst { it.title.isBlank() || it.offset.isBlank() }.takeIf { it >= 0 } ?: rows.size
        val nextRows = rows.toMutableList()
        if (targetIndex == rows.size) nextRows += TimelineOffsetRuleRow()
        val row = nextRows[targetIndex]
        val selectedSources = if (cleanSource.isBlank()) row.selectedSources else listOf(cleanSource)
        nextRows[targetIndex] = row.copy(title = cleanTitle, selectedSources = selectedSources)
        syncRows(nextRows)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("弹幕时间轴偏移", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "格式：剧名/季/集@来源1&来源2%:偏移值。正数向后偏移，负数向前偏移，百分比模式会按视频时长比例换算。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            rows.forEachIndexed { index, row ->
                val selectedSources = canonicalizePlatformSelection(row.selectedSources, sourceOptions)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "规则 ${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = !row.usePercent,
                                onClick = { updateRow(index) { it.copy(usePercent = false) } },
                                label = { Text("秒偏移") }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            FilterChip(
                                selected = row.usePercent,
                                onClick = { updateRow(index) { it.copy(usePercent = true) } },
                                label = { Text("百分比") }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = {
                                val next = rows.filterIndexed { i, _ -> i != index }
                                syncRows(if (next.isEmpty()) listOf(TimelineOffsetRuleRow()) else next)
                            }) {
                                Icon(Icons.Rounded.Close, "删除")
                            }
                        }

                        OutlinedTextField(
                            value = row.title,
                            onValueChange = { text -> updateRow(index) { it.copy(title = text) } },
                            label = { Text("剧名") },
                            placeholder = { Text("例如：庆余年") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = row.season,
                                onValueChange = { text -> updateRow(index) { it.copy(season = normalizeNumericInput(text)) } },
                                label = { Text("季（可选）") },
                                placeholder = { Text("S01 或 1") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = row.episode,
                                onValueChange = { text -> updateRow(index) { it.copy(episode = normalizeNumericInput(text)) } },
                                label = { Text("集（可选）") },
                                placeholder = { Text("E01 或 1") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedTextField(
                            value = row.offset,
                            onValueChange = { text -> updateRow(index) { it.copy(offset = text) } },
                            label = { Text(if (row.usePercent) "百分比值" else "偏移秒数") },
                            placeholder = { Text(if (row.usePercent) "10" else "-5") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "来源（可多选，all 表示全部来源）",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            sourceOptions.forEach { option ->
                                val isSelected = selectedSources.any { it.equals(option, true) }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        val next = togglePlatformSelection(selectedSources, option, sourceOptions)
                                        updateRow(index) { it.copy(selectedSources = next.ifEmpty { listOf("all") }) }
                                    },
                                    label = { Text(if (option.equals("all", true)) "全部" else option) }
                                )
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { syncRows(rows + TimelineOffsetRuleRow()) }) { Text("新增规则") }
                OutlinedButton(onClick = {
                    val parsed = parseTimelineOffsetRules(value)
                    rows = if (parsed.isEmpty()) listOf(TimelineOffsetRuleRow()) else parsed
                }) {
                    Text("从原始值解析")
                }
            }

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("原始值（高级）") },
                singleLine = false,
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            RecentAnimeCachePanel(
                rememberKey = rememberKey,
                currentKey = KEY_DANMU_OFFSET,
                onFetchRecentAnimeCache = onFetchRecentAnimeCache,
                onFillOffsetEntity = { title, source -> fillOffsetEntity(title, source) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MatchPlatformRulesEditor(
    rememberKey: String,
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
) {
    var rows by remember(rememberKey) {
        mutableStateOf(parseMatchPlatformRules(value).ifEmpty { listOf(MatchPlatformRuleRow()) })
    }
    val platformOptions = remember(options) {
        options.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }

    fun syncRows(next: List<MatchPlatformRuleRow>) {
        rows = next
        onValueChange(serializeMatchPlatformRules(next))
    }

    fun updateRow(index: Int, block: (MatchPlatformRuleRow) -> MatchPlatformRuleRow) {
        if (index !in rows.indices) return
        syncRows(rows.replace(index, block(rows[index])))
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("平台优先级规则", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "格式：剧名->平台1,平台2；剧名/S01->平台1&平台2,平台3。平台名沿用 PLATFORM_ORDER，可用文件名显式 @平台 优先。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            rows.forEachIndexed { index, row ->
                val selectedPlatforms = parseCsvTokens(row.platformsRaw)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "规则 ${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                val next = rows.filterIndexed { i, _ -> i != index }
                                syncRows(if (next.isEmpty()) listOf(MatchPlatformRuleRow()) else next)
                            }) {
                                Icon(Icons.Rounded.Close, "删除")
                            }
                        }

                        OutlinedTextField(
                            value = row.title,
                            onValueChange = { text -> updateRow(index) { it.copy(title = text) } },
                            label = { Text("剧名") },
                            placeholder = { Text("例如：葬送的芙莉莲") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = row.season,
                            onValueChange = { text -> updateRow(index) { it.copy(season = normalizeNumericInput(text)) } },
                            label = { Text("季（可选）") },
                            placeholder = { Text("S01 或 1") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = row.platformsRaw,
                            onValueChange = { text -> updateRow(index) { it.copy(platformsRaw = text) } },
                            label = { Text("平台优先级") },
                            placeholder = { Text("bilibili1,animeko") },
                            singleLine = false,
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (platformOptions.isNotEmpty()) {
                            Text(
                                text = "可选平台（点击快速追加/移除）",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                platformOptions.forEach { option ->
                                    val isSelected = selectedPlatforms.any { it.equals(option, true) }
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            val next = if (isSelected) {
                                                selectedPlatforms.filterNot { it.equals(option, true) }
                                            } else {
                                                selectedPlatforms + option
                                            }
                                            updateRow(index) { it.copy(platformsRaw = next.distinctBy { token -> token.lowercase() }.joinToString(",")) }
                                        },
                                        label = { Text(option) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { syncRows(rows + MatchPlatformRuleRow()) }) { Text("新增规则") }
                OutlinedButton(onClick = {
                    val parsed = parseMatchPlatformRules(value)
                    rows = if (parsed.isEmpty()) listOf(MatchPlatformRuleRow()) else parsed
                }) {
                    Text("从原始值解析")
                }
            }

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("原始值（高级）") },
                singleLine = false,
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntityEditorCard(
    titleLabel: String,
    title: String,
    season: String,
    source: String,
    sourceOptions: List<String>,
    onTitleChange: (String) -> Unit,
    onSeasonChange: (String) -> Unit,
    onSourceChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(titleLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                label = { Text("剧名") },
                placeholder = { Text("例如：我推的孩子") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = season,
                    onValueChange = onSeasonChange,
                    label = { Text("季") },
                    placeholder = { Text("S01 或 1") },
                    singleLine = true,
                    modifier = Modifier.weight(0.7f)
                )
                OutlinedTextField(
                    value = source,
                    onValueChange = onSourceChange,
                    label = { Text("来源") },
                    placeholder = { Text("bilibili") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            if (sourceOptions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sourceOptions.forEach { option ->
                        val isSelected = source.equals(option, true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSourceChange(option) },
                            label = { Text(option) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun RecentAnimeCachePanel(
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
    val scope = rememberCoroutineScope()
    var expanded by remember(rememberKey) { mutableStateOf(false) }
    var loading by remember(rememberKey) { mutableStateOf(false) }
    var error by remember(rememberKey) { mutableStateOf<String?>(null) }
    var cacheItems by remember(rememberKey) { mutableStateOf<List<AnimeCacheItem>>(emptyList()) }

    val helpText = when (currentKey) {
        KEY_CUSTOM_MERGE_RULES -> "缓存卡片可直接填 A / 填 B；最近数据只做辅助，写入后仍可手动调整。"
        KEY_DANMU_OFFSET -> "点击“填入”可把剧名和来源带入偏移规则。"
        KEY_MERGE_SOURCE_PAIRS -> "点击“加入来源”可把来源加入当前组合暂存区。"
        else -> "点击缓存卡片可查看最近 animes 缓存，辅助填写当前配置。"
    }

    fun load(force: Boolean = false) {
        if (loading) return
        if (!force && cacheItems.isNotEmpty()) return
        loading = true
        error = null
        scope.launch {
            val result = onFetchRecentAnimeCache()
            loading = false
            result.onSuccess {
                cacheItems = it
            }.onFailure {
                error = it.message ?: "请求失败"
                cacheItems = emptyList()
            }
        }
    }

    if (compact && !expanded) {
        OutlinedButton(
            onClick = {
                expanded = true
                load(force = true)
            }
        ) {
            Icon(Icons.Rounded.Tune, null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("查看最近数据")
        }
        return
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "最近数据",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = {
                        expanded = !expanded
                        if (expanded) load(force = true)
                    }
                ) {
                    Icon(Icons.Rounded.Tune, null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (expanded) "收起" else "查看最近数据")
                }
                FilledTonalIconButton(onClick = { load(force = true) }, enabled = expanded && !loading) {
                    Icon(Icons.Rounded.Refresh, null)
                }
            }

            Text(
                helpText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (expanded) {
                when {
                    loading -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("数据加载中…", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    error != null -> {
                        Text(
                            error.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    cacheItems.isEmpty() -> {
                        Text(
                            "暂无番剧缓存。请先在核心里完成一次搜索/匹配/弹幕请求后再回来查看。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 420.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 4.dp)
                        ) {
                            items(items = cacheItems) { item ->
                                AnimeCacheCard(
                                    item = item,
                                    currentKey = currentKey,
                                    onFillMergeEntity = onFillMergeEntity,
                                    mergeFillRole = mergeFillRole,
                                    mergeFillLabel = mergeFillLabel,
                                    onFillOffsetEntity = onFillOffsetEntity,
                                    onAddSourcePair = onAddSourcePair
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeCacheCard(
    item: AnimeCacheItem,
    currentKey: String,
    onFillMergeEntity: ((String, String, String) -> Unit)?,
    mergeFillRole: String? = null,
    mergeFillLabel: String = "填入",
    onFillOffsetEntity: ((String, String) -> Unit)?,
    onAddSourcePair: ((String) -> Unit)?,
    compact: Boolean = false,
) {
    val cleanTitle = cleanCacheAnimeTitle(item.animeTitle)
    val source = item.source.trim()
    val episodeCount = item.episodes.takeIf { it > 0 } ?: item.episodeCount.takeIf { it > 0 } ?: item.links.size
    val imageUrl = item.imageUrl.trim()
    val coverSize = if (compact) 54.dp else 72.dp
    val titleStyle = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall
    val subtitleStyle = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (compact) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 8.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AnimeCacheCover(imageUrl = imageUrl, size = coverSize)

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        cleanTitle.ifBlank { item.animeTitle.ifBlank { "未命名" } },
                        style = titleStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "[$source] · $episodeCount 集",
                        style = subtitleStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val buttons = buildList {
                when (currentKey) {
                    KEY_CUSTOM_MERGE_RULES -> {
                        if (mergeFillRole != null) {
                            add(mergeFillLabel to { onFillMergeEntity?.invoke(mergeFillRole, cleanTitle, source) })
                        } else {
                            add("填 A" to { onFillMergeEntity?.invoke("sec", cleanTitle, source) })
                            add("填 B" to { onFillMergeEntity?.invoke("prim", cleanTitle, source) })
                        }
                    }

                    KEY_DANMU_OFFSET -> {
                        add("填入" to { onFillOffsetEntity?.invoke(cleanTitle, source) })
                    }

                    KEY_MERGE_SOURCE_PAIRS -> {
                        onAddSourcePair?.let { callback ->
                            add("加入来源" to { callback(source) })
                        }
                    }
                }
            }

            if (buttons.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    buttons.forEach { (label, action) ->
                        AssistChip(onClick = { action() }, label = { Text(label) })
                    }
                }
            }

            val children = item.mergedChildren.filterNot { it.isHiddenChild }
            if (children.isNotEmpty()) {
                Text(
                    "已合并来源 ${children.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 8.dp)) {
                    children.forEach { child ->
                        AnimeCacheCard(
                            item = child,
                            currentKey = currentKey,
                            onFillMergeEntity = onFillMergeEntity,
                            mergeFillRole = mergeFillRole,
                            mergeFillLabel = mergeFillLabel,
                            onFillOffsetEntity = onFillOffsetEntity,
                            onAddSourcePair = onAddSourcePair,
                            compact = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeCacheCover(imageUrl: String, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    if (imageUrl.isBlank()) {
        Box(
            modifier = Modifier
                .size(width = size, height = size * 1.35f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.ImageNotSupported, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(width = size, height = size * 1.35f)
            .clip(RoundedCornerShape(12.dp))
    )
}
