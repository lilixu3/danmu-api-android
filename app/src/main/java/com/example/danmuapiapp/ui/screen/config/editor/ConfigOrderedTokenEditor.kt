package com.example.danmuapiapp.ui.screen.config

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OrderedTokenEditor(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    title: String,
    tokenLabel: String
) {
    val selected = remember(value) { parseCsvTokens(value) }
    val available = remember(options, selected) {
        options
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .filterNot { selected.contains(it) }
    }
    var customInput by remember { mutableStateOf("") }

    AppGlassSurface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                "顺序会直接影响匹配优先级。上移/下移可以快速调整。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (selected.isEmpty()) {
                Text(
                    "暂无已选 $tokenLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                selected.forEachIndexed { index, item ->
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
                            AppGlassSurface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(
                                    modifier = Modifier.size(22.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Text(
                                item,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp)
                            )

                            AppGlassIconButton(
                                onClick = {
                                    if (index > 0) {
                                        onValueChange(
                                            selected.move(index, index - 1).joinToString(",")
                                        )
                                    }
                                },
                                enabled = index > 0
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowUp, "上移")
                            }
                            AppGlassIconButton(
                                onClick = {
                                    if (index < selected.lastIndex) {
                                        onValueChange(
                                            selected.move(index, index + 1).joinToString(",")
                                        )
                                    }
                                },
                                enabled = index < selected.lastIndex
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowDown, "下移")
                            }
                            AppGlassIconButton(
                                onClick = {
                                    onValueChange(selected.filterIndexed { i, _ -> i != index }.joinToString(","))
                                }
                            ) {
                                Icon(Icons.Rounded.Close, "删除")
                            }
                        }
                    }
                }
            }

            if (available.isNotEmpty()) {
                Text(
                    "可选项（点击添加）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    available.forEach { option ->
                        AppGlassAssistChip(
                            onClick = { onValueChange((selected + option).joinToString(",")) },
                            label = { Text(option) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customInput,
                    onValueChange = { customInput = it },
                    label = { Text("自定义 $tokenLabel") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                AppGlassButton(
                    onClick = {
                        val token = customInput.trim()
                        if (token.isNotBlank() && !selected.contains(token)) {
                            onValueChange((selected + token).joinToString(","))
                            customInput = ""
                        }
                    }
                ) {
                    Text("添加")
                }
            }
        }
    }
}
