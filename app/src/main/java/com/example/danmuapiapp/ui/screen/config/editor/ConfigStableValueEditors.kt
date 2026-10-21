package com.example.danmuapiapp.ui.screen.config

import androidx.compose.foundation.BorderStroke
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
import com.example.danmuapiapp.ui.component.AppGlassSurface
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

private data class StableMapRow(
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

@Composable
internal fun StableMapValueEditor(
    envKey: String,
    value: String,
    onValueChange: (String) -> Unit,
    onFetchRecentAnimeCache: suspend () -> Result<List<AnimeCacheItem>>,
) {
    var rows by remember(value) { mutableStateOf(parseStableMapRows(value)) }
    var bulkText by remember(value) { mutableStateOf(value) }

    fun syncRows(next: List<StableMapRow>, updateBulk: Boolean = true) {
        rows = next
        val text = serializeStableMapRows(next)
        if (updateBulk) bulkText = text
        onValueChange(text)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("映射配置", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = bulkText,
            onValueChange = { bulkText = it },
            placeholder = { Text("原值->映射值;原值2->映射值2") },
            singleLine = false,
            minLines = 4,
            maxLines = 6,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        AppGlassButton(onClick = { syncRows(parseStableMapRows(bulkText)) }) {
            Text("解析并更新列表")
        }
        rows.forEachIndexed { index, row ->
            AppGlassSurface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("映射 ${index + 1}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        AppGlassIconButton(onClick = {
                            val next = rows.filterIndexed { i, _ -> i != index }
                            syncRows(if (next.isEmpty()) listOf(StableMapRow()) else next)
                        }) { Icon(Icons.Rounded.DeleteOutline, "删除") }
                    }
                    OutlinedTextField(
                        value = row.left,
                        onValueChange = { syncRows(rows.replace(index, row.copy(left = it))) },
                        label = { Text("原始值") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = row.right,
                        onValueChange = { syncRows(rows.replace(index, row.copy(right = it))) },
                        label = { Text("映射值") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppGlassButton(onClick = { syncRows(rows + StableMapRow()) }) { Text("添加映射项") }
            // 与核心前端一致：「查看最近数据」放在「添加映射项」同一行的右侧。
            RecentDataToggle(
                rememberKey = envKey,
                currentKey = envKey,
                onFetchRecentAnimeCache = onFetchRecentAnimeCache,
                compact = true
            )
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
