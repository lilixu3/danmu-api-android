package com.example.danmuapiapp.ui.screen.config

import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.AppDialogStyle
import com.example.danmuapiapp.ui.component.AppDialogTone
import com.example.danmuapiapp.ui.component.AppDialogOption
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassAssistChip
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton

internal data class TitleOffsetRow(
    val title: String = "",
    val platformsRaw: String = "",
    val offset: String = "",
)

internal data class PlatformPickerState(
    val rowIndex: Int,
    val selected: List<String>,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TitlePlatformOffsetTableEditor(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>
) {
    val platformOptions = remember(options) { normalizePlatformOptions(options) }
    var rows by remember { mutableStateOf(parseTitleOffsetRows(value).ifEmpty { listOf(TitleOffsetRow()) }) }
    var platformPicker by remember { mutableStateOf<PlatformPickerState?>(null) }

    fun syncRows(next: List<TitleOffsetRow>) {
        rows = next
        onValueChange(serializeTitleOffsetRows(next))
    }

    AppGlassSurface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("剧名平台偏移表", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "格式：剧名@平台1&平台2@偏移秒，多个规则使用分号分隔。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            rows.forEachIndexed { index, row ->
                val selectedPlatforms = parsePlatformTokens(row.platformsRaw)
                AppGlassSurface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "规则 ${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            AppGlassIconButton(
                                onClick = {
                                    if (index > 0) syncRows(rows.move(index, index - 1))
                                },
                                enabled = index > 0
                            ) { Icon(Icons.Rounded.KeyboardArrowUp, "上移") }
                            AppGlassIconButton(
                                onClick = {
                                    if (index < rows.lastIndex) syncRows(rows.move(index, index + 1))
                                },
                                enabled = index < rows.lastIndex
                            ) { Icon(Icons.Rounded.KeyboardArrowDown, "下移") }
                            AppGlassIconButton(
                                onClick = {
                                    val next = rows.filterIndexed { i, _ -> i != index }
                                    syncRows(if (next.isEmpty()) listOf(TitleOffsetRow()) else next)
                                }
                            ) { Icon(Icons.Rounded.DeleteOutline, "删除") }
                        }

                        OutlinedTextField(
                            value = row.title,
                            onValueChange = { text ->
                                syncRows(rows.replace(index, row.copy(title = text)))
                            },
                            label = { Text("剧名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = row.offset,
                            onValueChange = { text ->
                                syncRows(rows.replace(index, row.copy(offset = text)))
                            },
                            label = { Text("偏移秒（可负数）") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (platformOptions.isNotEmpty()) {
                            val selectedSummary = formatPlatformSummary(selectedPlatforms)
                            Text(
                                "平台（可多选，all 表示全部）",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            AppGlassSurface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        "已选平台：$selectedSummary",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (selectedPlatforms.isNotEmpty()) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            selectedPlatforms.forEach { platform ->
                                                AppGlassAssistChip(
                                                    onClick = {},
                                                    label = {
                                                        Text(if (platform.equals("all", true)) "全部" else platform)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppGlassButton(
                                    onClick = {
                                        platformPicker = PlatformPickerState(
                                            rowIndex = index,
                                            selected = selectedPlatforms
                                        )
                                    }
                                ) {
                                    Text("选择平台")
                                }
                                AppGlassButton(
                                    onClick = {
                                        syncRows(rows.replace(index, row.copy(platformsRaw = "")))
                                    },
                                    enabled = selectedPlatforms.isNotEmpty()
                                ) {
                                    Text("清空")
                                }
                            }

                            Text(
                                "提示：选择“全部”后会自动清空其它平台，避免配置冲突。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            OutlinedTextField(
                                value = row.platformsRaw,
                                onValueChange = { text ->
                                    syncRows(rows.replace(index, row.copy(platformsRaw = text)))
                                },
                                label = { Text("平台（使用 & 分隔）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppGlassButton(onClick = { syncRows(rows + TitleOffsetRow()) }) { Text("新增规则") }
                AppGlassButton(
                    onClick = {
                        val parsed = parseTitleOffsetRows(value)
                        rows = if (parsed.isEmpty()) listOf(TitleOffsetRow()) else parsed
                    }
                ) {
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

    val picker = platformPicker
    if (picker != null) {
        PlatformMultiSelectDialog(
            options = platformOptions,
            initialSelected = picker.selected,
            onDismiss = { platformPicker = null },
            onConfirm = { selected ->
                val rowIndex = picker.rowIndex
                if (rowIndex !in rows.indices) {
                    platformPicker = null
                    return@PlatformMultiSelectDialog
                }
                val normalized = canonicalizePlatformSelection(selected, platformOptions)
                val platformsRaw = normalizePlatforms(normalized.joinToString("&"))
                syncRows(rows.replace(rowIndex, rows[rowIndex].copy(platformsRaw = platformsRaw)))
                platformPicker = null
            }
        )
    }
}

@Composable
internal fun PlatformMultiSelectDialog(
    options: List<String>,
    initialSelected: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var selected by remember(options, initialSelected) {
        mutableStateOf(canonicalizePlatformSelection(initialSelected, options))
    }

    AppDialog(
        onDismissRequest = onDismiss,
        style = AppDialogStyle.Selection,
        tone = AppDialogTone.Info,
        title = { Text("选择平台") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "可多选。若选择“全部”，其它平台将自动取消。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                options.forEach { option ->
                    val normalizedOption = option.trim().ifBlank { option }
                    val isAll = normalizedOption.equals("all", true)
                    val isSelected = selected.any { it.equals(normalizedOption, true) }
                    val hasAll = selected.any { it.equals("all", true) }
                    val canSelect = !hasAll || isAll || isSelected

                    AppDialogOption(
                        selected = isSelected,
                        onClick = {
                            if (canSelect) {
                                selected = togglePlatformSelection(selected, normalizedOption, options)
                            }
                        },
                        enabled = canSelect,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = {
                                selected = togglePlatformSelection(selected, normalizedOption, options)
                            },
                            enabled = canSelect
                        )
                        Text(
                            text = if (isAll) "全部" else normalizedOption,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (isAll) {
                            Text(
                                "all",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }

                Text(
                    "当前选择：${formatPlatformSummary(selected)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            AppGlassButton(onClick = { onConfirm(selected) }) {
                Text("确认")
            }
        },
        dismissButton = {
            AppGlassButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
