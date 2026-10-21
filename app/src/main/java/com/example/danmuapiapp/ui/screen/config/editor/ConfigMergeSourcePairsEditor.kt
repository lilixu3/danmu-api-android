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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassAssistChip
import com.example.danmuapiapp.ui.component.liquid.AppGlassFilterChip
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton
import com.example.danmuapiapp.domain.model.AnimeCacheItem

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MergeSourcePairsEditor(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    onFetchRecentAnimeCache: suspend () -> Result<List<AnimeCacheItem>>,
) {
    val groups = remember(value) { parseCsvTokens(value) }
    val allOptions = remember(options) {
        options.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }
    var staging by remember { mutableStateOf<List<String>>(emptyList()) }

    AppGlassSurface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("源合并组配置", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "规则格式：源1&源2。多个组使用逗号分隔。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (groups.isEmpty()) {
                Text(
                    "暂无合并组",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                groups.forEachIndexed { index, group ->
                    AppGlassSurface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${index + 1}.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                group,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            AppGlassIconButton(
                                onClick = {
                                    if (index > 0) {
                                        onValueChange(groups.move(index, index - 1).joinToString(","))
                                    }
                                },
                                enabled = index > 0
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowUp, "上移")
                            }
                            AppGlassIconButton(
                                onClick = {
                                    if (index < groups.lastIndex) {
                                        onValueChange(groups.move(index, index + 1).joinToString(","))
                                    }
                                },
                                enabled = index < groups.lastIndex
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowDown, "下移")
                            }
                            AppGlassIconButton(
                                onClick = {
                                    onValueChange(groups.filterIndexed { i, _ -> i != index }.joinToString(","))
                                }
                            ) {
                                Icon(Icons.Rounded.Close, "删除")
                            }
                        }
                    }
                }
            }

            Text(
                if (staging.isEmpty()) "暂存区：未选择" else "暂存区：${staging.joinToString(" & ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (staging.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    staging.forEach { token ->
                        AppGlassFilterChip(
                            selected = true,
                            onClick = { staging = staging.filterNot { it == token } },
                            label = { Text("$token ×") }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppGlassButton(
                    onClick = {
                        if (staging.isNotEmpty()) {
                            val group = staging.joinToString("&")
                            if (!groups.contains(group)) {
                                onValueChange((groups + group).joinToString(","))
                            }
                            staging = emptyList()
                        }
                    },
                    enabled = staging.isNotEmpty()
                ) {
                    Text("添加分组")
                }
                AppGlassButton(
                    onClick = { staging = emptyList() },
                    enabled = staging.isNotEmpty()
                ) {
                    Text("清空暂存")
                }
            }

            if (allOptions.isNotEmpty()) {
                Text(
                    "可选项（点击加入暂存区）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allOptions.forEach { option ->
                        AppGlassAssistChip(
                            onClick = {
                                if (!staging.contains(option)) {
                                    staging = staging + option
                                }
                            },
                            label = { Text(option) },
                            enabled = !staging.contains(option)
                        )
                    }
                }
            } else {
                Text(
                    "未读取到可选项，可直接在原始值里手工输入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("原始值（高级）") },
                singleLine = false,
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            RecentAnimeCachePanel(
                rememberKey = KEY_MERGE_SOURCE_PAIRS,
                currentKey = KEY_MERGE_SOURCE_PAIRS,
                onFetchRecentAnimeCache = onFetchRecentAnimeCache,
                onAddSourcePair = { source ->
                    val cleanSource = source.trim()
                    if (cleanSource.isNotBlank() && !groups.contains(cleanSource)) {
                        onValueChange((groups + cleanSource).joinToString(","))
                    }
                }
            )
        }
    }
}
