package com.example.danmuapiapp.ui.screen.config

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.example.danmuapiapp.data.util.DeviceCompatMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppGlassAssistChip
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton
import com.example.danmuapiapp.domain.model.AnimeCacheItem
import com.example.danmuapiapp.domain.model.EnvVarDef

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StableMultiSelectValueEditor(
    envKey: String,
    def: EnvVarDef,
    value: String,
    onValueChange: (String) -> Unit,
    onFetchRecentAnimeCache: suspend () -> Result<List<AnimeCacheItem>>,
) {
    val selected = remember(value) { parseCsvTokens(value) }
    val isMergePairs = envKey == KEY_MERGE_SOURCE_PAIRS
    val supportsMerge = isMergePairs || envKey == KEY_PLATFORM_ORDER
    var mergeMode by remember(envKey) { mutableStateOf(value.contains('&') || isMergePairs) }
    var staging by remember(envKey) { mutableStateOf(emptyList<String>()) }
    // MERGE_SOURCE_PAIRS 必须先开合并模式，否则完全不允许写入（与核心/Flutter 版一致）。
    val writeBlocked = isMergePairs && !mergeMode
    // 合并模式下只限制同一分组内重复；已用过的单源仍可再次参与分组（与核心前端一致）。
    val usedSources = selected.flatMap { it.split('&') }.map { it.trim() }.filter { it.isNotBlank() }.toSet()
    val available = remember(def.options, selected, staging, mergeMode, supportsMerge) {
        def.options.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            .filter { option ->
                if (supportsMerge && mergeMode) !staging.contains(option) else option !in usedSources
            }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("已选（长按标签拖动即可调整顺序）", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppGlassSurface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            // 输入框形态：框内是标签，点 × 删除，长按拖动换位（与核心前端一致）。
            DraggableTagFlow(
                tags = selected,
                emptyHint = "尚未选择",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp, max = 220.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                onDelete = { index ->
                    onValueChange(selected.filterIndexed { i, _ -> i != index }.joinToString(","))
                },
                onMove = { from, to ->
                    val next = selected.toMutableList()
                    val item = next.removeAt(from)
                    next.add(to.coerceIn(0, next.size), item)
                    onValueChange(next.joinToString(","))
                }
            )
        }

        if (supportsMerge) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Switch(
                    checked = mergeMode,
                    onCheckedChange = {
                        mergeMode = it
                        staging = emptyList()
                    }
                )
                Text(
                    when {
                        mergeMode && isMergePairs ->
                            "合并模式：点选多个源进暂存组，点 ✓ 加入输入框（组内第一个为主源）"
                        mergeMode ->
                            "合并模式：可组合成 a&b 这样的合并组一起返回"
                        isMergePairs ->
                            "未开启合并模式：该变量必须先开启合并模式才能配置"
                        else ->
                            "单源模式：只写入单个源"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (writeBlocked) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        if (supportsMerge && mergeMode) {
            Text(
                "暂存组（点下方可选源加入，长按可拖动调序）",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AppGlassSurface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DraggableTagFlow(
                        tags = staging,
                        emptyHint = "点下方可选源加入暂存组，再点右侧 ✓ 加入输入框",
                        showSeparator = true,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp, max = 180.dp),
                        onDelete = { index ->
                            staging = staging.filterIndexed { i, _ -> i != index }
                        },
                        onMove = { from, to ->
                            val next = staging.toMutableList()
                            val item = next.removeAt(from)
                            next.add(to.coerceIn(0, next.size), item)
                            staging = next
                        }
                    )
                    AppGlassIconButton(
                        onClick = {
                            if (staging.isNotEmpty()) {
                                val group = staging.joinToString("&")
                                if (group !in selected) {
                                    onValueChange((selected + group).joinToString(","))
                                }
                                staging = emptyList()
                            }
                        },
                        enabled = staging.isNotEmpty(),
                        size = 34.dp
                    ) {
                        Icon(Icons.Rounded.Check, "加入输入框", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Text("可选源", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            def.options.map { it.trim() }.filter { it.isNotBlank() }.distinct().forEach { option ->
                val staged = staging.contains(option)
                // 合并模式下只允许组内不重复；MERGE_SOURCE_PAIRS 未开合并模式时全部禁用。
                val optionEnabled = !writeBlocked && option in available
                AppGlassAssistChip(
                    onClick = {
                        if (supportsMerge && mergeMode) {
                            staging = if (staged) {
                                staging.filterNot { it == option }
                            } else {
                                staging + option
                            }
                        } else if (option !in selected) {
                            onValueChange((selected + option).joinToString(","))
                        }
                    },
                    enabled = optionEnabled,
                    label = { Text(option) }
                )
            }
        }

        if (writeBlocked) {
            Text(
                "开启合并模式后才能配置 MERGE_SOURCE_PAIRS。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (isMergePairs) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                RecentDataToggle(
                    rememberKey = envKey,
                    currentKey = envKey,
                    onFetchRecentAnimeCache = onFetchRecentAnimeCache,
                    onAddSourcePair = { source ->
                        val clean = source.trim()
                        if (clean.isNotBlank()) {
                            // 与手动点选同规则：合并模式进暂存组，未开合并模式时 MERGE_SOURCE_PAIRS 不允许写入。
                            if (supportsMerge && mergeMode) {
                                if (clean !in staging) staging = staging + clean
                            } else if (!writeBlocked && clean !in selected) {
                                onValueChange((selected + clean).joinToString(","))
                            }
                        }
                    },
                    compact = true
                )
            }
        }
    }
}

/**
 * 标签输入框：框内标签点 × 删除、长按拖动换位（对齐核心前端 multi-select 的交互）。
 * 暂存组用 & 分隔展示。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DraggableTagFlow(
    tags: List<String>,
    onDelete: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    emptyHint: String = "",
    showSeparator: Boolean = false
) {
    val remoteDevice = DeviceCompatMode.isCompatModeDevice(LocalContext.current)
    val bounds = remember { mutableStateMapOf<Int, Rect>() }
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var targetIndex by remember { mutableStateOf<Int?>(null) }
    var pointerStart by remember { mutableStateOf(Offset.Zero) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = modifier) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (tags.isEmpty() && emptyHint.isNotBlank()) {
                Text(
                    emptyHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                )
            }
            tags.forEachIndexed { index, tag ->
                if (showSeparator && index > 0) {
                    Text(
                        "&",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
                val dragging = dragIndex == index
                val targeted = targetIndex == index && dragIndex != null && dragIndex != index
                val isGroup = tag.contains('&')
                AppGlassSurface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isGroup) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            targeted -> MaterialTheme.colorScheme.primary
                            isGroup -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                    modifier = Modifier
                        .alpha(if (dragging) 0.6f else 1f)
                        .onGloballyPositioned { bounds[index] = it.boundsInRoot() }
                        .pointerInput(tags.size, index) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    dragIndex = index
                                    targetIndex = index
                                    dragDelta = Offset.Zero
                                    pointerStart = (bounds[index]?.topLeft ?: Offset.Zero) + offset
                                },
                                onDrag = { change, delta ->
                                    change.consume()
                                    dragDelta += delta
                                    nearestTagIndex(pointerStart + dragDelta, bounds, dragIndex)?.let {
                                        targetIndex = it
                                    }
                                },
                                onDragEnd = {
                                    val from = dragIndex
                                    val to = targetIndex
                                    if (from != null && to != null && from != to) onMove(from, to)
                                    dragIndex = null
                                    targetIndex = null
                                    dragDelta = Offset.Zero
                                },
                                onDragCancel = {
                                    dragIndex = null
                                    targetIndex = null
                                    dragDelta = Offset.Zero
                                }
                            )
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            tag,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = if (isGroup) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        if (remoteDevice) {
                            AppGlassIconButton(onClick = { onMove(index, index - 1) }, enabled = index > 0, size = 32.dp) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "前移", modifier = Modifier.size(16.dp))
                            }
                            AppGlassIconButton(onClick = { onMove(index, index + 1) }, enabled = index < tags.lastIndex, size = 32.dp) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, "后移", modifier = Modifier.size(16.dp))
                            }
                        }
                        AppGlassIconButton(onClick = { onDelete(index) }, size = if (remoteDevice) 32.dp else 24.dp) {
                            Icon(
                                Icons.Rounded.Close,
                                "删除",
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun nearestTagIndex(pointer: Offset, bounds: Map<Int, Rect>, exclude: Int?): Int? {
    var best: Int? = null
    var bestDistance = Float.MAX_VALUE
    bounds.forEach { (index, rect) ->
        if (index == exclude) return@forEach
        val distance = (rect.center - pointer).getDistance()
        if (distance < bestDistance) {
            bestDistance = distance
            best = index
        }
    }
    return best
}
