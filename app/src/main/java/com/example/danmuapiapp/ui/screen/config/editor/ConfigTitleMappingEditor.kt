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
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton

internal data class TitleMappingRow(
    val left: String = "",
    val right: String = "",
)

@Composable
internal fun TitleMappingTableEditor(
    value: String,
    onValueChange: (String) -> Unit
) {
    var rows by remember { mutableStateOf(parseTitleMappingRows(value).ifEmpty { listOf(TitleMappingRow()) }) }

    fun syncRows(next: List<TitleMappingRow>) {
        rows = next
        onValueChange(serializeTitleMappingRows(next))
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
            Text("剧名映射表", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "格式：原始剧名 -> 映射剧名，保存时自动转为 left->right;left->right。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            rows.forEachIndexed { index, row ->
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
                                "映射 ${index + 1}",
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
                                    syncRows(if (next.isEmpty()) listOf(TitleMappingRow()) else next)
                                }
                            ) { Icon(Icons.Rounded.DeleteOutline, "删除") }
                        }

                        OutlinedTextField(
                            value = row.left,
                            onValueChange = { text ->
                                syncRows(rows.replace(index, row.copy(left = text)))
                            },
                            label = { Text("原始剧名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = row.right,
                            onValueChange = { text ->
                                syncRows(rows.replace(index, row.copy(right = text)))
                            },
                            label = { Text("映射剧名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppGlassButton(onClick = { syncRows(rows + TitleMappingRow()) }) { Text("新增映射") }
                AppGlassButton(
                    onClick = {
                        val parsed = parseTitleMappingRows(value)
                        rows = if (parsed.isEmpty()) listOf(TitleMappingRow()) else parsed
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
}
