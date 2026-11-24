package com.example.danmuapiapp.ui.screen.config

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassFilterChip
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton
import com.example.danmuapiapp.domain.model.AnimeCacheItem
import com.example.danmuapiapp.domain.model.EnvVarDef
import java.util.Locale

@Composable
internal fun StableBooleanValueEditor(
    def: EnvVarDef,
    value: String,
    onValueChange: (String) -> Unit,
) {
    // 与核心前端一致：LIKE_SWITCH / REMEMBER_LAST_SELECT 未配置时视为开启。
    val defaultValueOn = def.key == "LIKE_SWITCH" || def.key == "REMEMBER_LAST_SELECT"
    val checked = value.lowercase(Locale.ROOT).let {
        it == "true" || it == "1" || (defaultValueOn && it.isBlank())
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("值", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = checked,
                onCheckedChange = { onValueChange(if (it) "true" else "false") }
            )
            Text(if (checked) "启用" else "禁用", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun StableNumberValueEditor(
    def: EnvVarDef,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val current = value.toIntOrNull()
    val minValue = def.min ?: 0
    val maxValue = def.max ?: maxOf(minValue + 100, current ?: minValue)
    val safeValue = (current ?: minValue).coerceIn(minValue, maxValue)

    fun setNumber(next: Int) {
        onValueChange(next.coerceIn(minValue, maxValue).toString())
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("值 (${minValue}-${maxValue})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 核心前端的数字滚轮：左侧上下两个按钮，右侧大字显示当前值。
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AppGlassIconButton(
                    onClick = { setNumber(safeValue + 1) },
                    enabled = safeValue < maxValue,
                    size = 30.dp
                ) {
                    Icon(Icons.Rounded.KeyboardArrowUp, "增加", modifier = Modifier.size(18.dp))
                }
                AppGlassIconButton(
                    onClick = { setNumber(safeValue - 1) },
                    enabled = safeValue > minValue,
                    size = 30.dp
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "减少", modifier = Modifier.size(18.dp))
                }
            }
            Text(
                safeValue.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (maxValue > minValue) {
            Slider(
                value = safeValue.toFloat(),
                onValueChange = { setNumber(it.toInt()) },
                valueRange = minValue.toFloat()..maxValue.toFloat(),
                steps = (maxValue - minValue - 1).coerceAtLeast(0).coerceAtMost(200)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StableSelectValueEditor(
    def: EnvVarDef,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("选择值", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (def.options.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                def.options.forEach { option ->
                    AppGlassFilterChip(
                        selected = value == option,
                        onClick = { onValueChange(option) },
                        label = { Text(option) }
                    )
                }
            }
        } else {
            Text(
                "未读取到可选项，请检查核心版本。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal data class StableMapRow(
    val left: String = "",
    val right: String = "",
)

private fun parseStableMapRows(raw: String): List<StableMapRow> {
    return MappingTableCodec.splitEntries(raw)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { item ->
            val arrow = MappingTableCodec.findArrow(item)
            if (arrow != null) {
                StableMapRow(
                    MappingTableCodec.unescape(item.substring(0, arrow).trim()),
                    MappingTableCodec.unescape(item.substring(arrow + 2).trim())
                )
            } else {
                StableMapRow(MappingTableCodec.unescape(item), "")
            }
        }
}

private fun serializeStableMapRows(rows: List<StableMapRow>): String {
    return rows.mapNotNull { row ->
        val left = MappingTableCodec.escape(row.left.trim())
        val right = MappingTableCodec.escape(row.right.trim())
        when {
            left.isBlank() && right.isBlank() -> null
            right.isBlank() -> left
            else -> "$left->${right}"
        }
    }.joinToString(";")
}

internal fun parseTitlePlatformRows(raw: String): List<StableMapRow> {
    val mappings = linkedMapOf<String, String>()
    for (entry in raw.split(';', '\r', '\n')) {
        val arrow = entry.indexOf("->")
        if (arrow < 0) continue
        val title = entry.substring(0, arrow).trim()
        val source = entry.substring(arrow + 2).trim()
        if (title.isNotEmpty() && source.isNotEmpty()) mappings[title] = source
    }
    return mappings.map { (title, source) -> StableMapRow(title, source) }
}

internal fun serializeTitlePlatformRows(rows: List<StableMapRow>): String =
    rows.filter { it.left.isNotBlank() && it.right.isNotBlank() }
        .associate { it.left.trim() to it.right.trim() }
        .entries.joinToString(";") { (title, source) -> "$title->$source" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchSourceDropdown(
    value: String,
    options: List<String>,
    modifier: Modifier,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("来源", maxLines = 1)
                    Icon(
                        if (expanded) Icons.Rounded.ArrowDropUp else Icons.Rounded.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            },
            placeholder = { Text("请选择", maxLines = 1) },
            readOnly = true,
            singleLine = true,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded, onDismissRequest = { expanded = false }, matchAnchorWidth = false) {
            // Keep existing rules editable when a configured group has since been removed.
            val choices = (listOf("") + options + value).distinct()
            choices.forEach { option ->
                DropdownMenuItem(text = { Text(option.ifBlank { "选择匹配来源" }) }, onClick = {
                    onSelect(option)
                    expanded = false
                })
            }
        }
    }
}

@Composable
internal fun StableMapValueEditor(
    def: EnvVarDef,
    value: String,
    onValueChange: (String) -> Unit,
    onFetchRecentAnimeCache: (suspend () -> Result<List<AnimeCacheItem>>)? = null,
) {
    val titlePlatformMap = def.key == KEY_TITLE_PLATFORM_MAP
    fun parseRows(text: String) =
        if (titlePlatformMap) parseTitlePlatformRows(text) else parseStableMapRows(text)

    var rows by remember(def.key) { mutableStateOf(parseRows(value)) }
    var bulkText by remember(def.key) { mutableStateOf(value) }

    fun syncRows(next: List<StableMapRow>) {
        rows = next
        bulkText = if (titlePlatformMap) serializeTitlePlatformRows(next) else serializeStableMapRows(next)
        onValueChange(bulkText)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("映射配置", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = bulkText,
            onValueChange = { text ->
                bulkText = text
                rows = parseRows(text)
                onValueChange(if (titlePlatformMap) serializeTitlePlatformRows(rows) else text)
            },
            placeholder = { Text(if (titlePlatformMap) "宝珠->imgo;航海王->dandan&bilibili1" else "原值->映射值;原值2->映射值2") },
            singleLine = false,
            minLines = 4,
            maxLines = 6,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        AppGlassButton(onClick = { syncRows(parseRows(bulkText)) }) {
            Text("解析并更新列表")
        }
        if (def.key == KEY_AUTO_MATCH_MAPPING_TABLE && value.isNotBlank()) {
            val validation = remember(value, def.options) { validateAutoMatchMappingTable(value, def.options) }
            if (validation.valid) {
                var filename by remember(def.key) { mutableStateOf("") }
                val preview = remember(value, filename, def.options) { previewAutoMatchMapping(value, filename, def.options) }
                OutlinedTextField(
                    value = filename,
                    onValueChange = { filename = it },
                    label = { Text("文件名预览（可选）") },
                    placeholder = { Text("例如 灵笼.S00E01.mkv") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (filename.isNotBlank()) {
                    Text(preview?.let { "${it.sourceLabel} → ${it.targetLabel}" } ?: "未匹配到规则",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = row.left,
                    onValueChange = { syncRows(rows.replace(index, row.copy(left = it))) },
                    label = { Text(if (titlePlatformMap) "剧名" else "原始值", maxLines = 1) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (titlePlatformMap && def.options.isNotEmpty()) {
                    MatchSourceDropdown(row.right, def.options, Modifier.weight(1f)) { source ->
                        syncRows(rows.replace(index, row.copy(right = source)))
                    }
                } else {
                    OutlinedTextField(
                        value = row.right,
                        onValueChange = { syncRows(rows.replace(index, row.copy(right = it))) },
                        label = { Text(if (titlePlatformMap) "来源" else "映射值", maxLines = 1) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
                AppGlassIconButton(onClick = {
                    syncRows(rows.filterIndexed { i, _ -> i != index })
                }) { Icon(Icons.Rounded.DeleteOutline, "删除第 ${index + 1} 条映射") }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppGlassButton(onClick = { syncRows(rows + StableMapRow()) }) { Text("添加映射项") }
            // 与核心前端一致：「查看最近数据」放在「添加映射项」同一行的右侧。
            if (onFetchRecentAnimeCache != null) {
                RecentDataToggle(
                    rememberKey = def.key,
                    currentKey = def.key,
                    onFetchRecentAnimeCache = onFetchRecentAnimeCache,
                    compact = true
                )
            }
        }
    }
}

@Composable
internal fun StableTextValueEditor(
    def: EnvVarDef,
    value: String,
    showPassword: Boolean,
    onTogglePassword: () -> Unit,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("变量值") },
        visualTransformation = if (def.sensitive && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (def.sensitive) {
            {
                AppGlassIconButton(onClick = onTogglePassword) {
                    Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "切换可见")
                }
            }
        } else null,
        singleLine = false,
        minLines = if (value.length > 50) 3 else 1,
        maxLines = 8,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
}
