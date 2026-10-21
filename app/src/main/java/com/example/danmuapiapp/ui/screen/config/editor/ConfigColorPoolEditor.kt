@file:OptIn(ExperimentalLayoutApi::class)

package com.example.danmuapiapp.ui.screen.config

import androidx.compose.foundation.clickable
import androidx.compose.material3.Slider
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.AppDialogStyle
import com.example.danmuapiapp.ui.component.AppDialogTone
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import kotlin.random.Random
import kotlin.math.roundToInt

/**
 * COLOR_POOL 颜色池编辑器，对齐核心前端 isColorPool 分支 / Flutter 版：
 *   颜色池（色块 + 十进制值 + 删除）
 *   色盘（环形色相 + 亮度滑块 + 预览色块与 #hex）+ [添加到颜色池] [随机添加]
 *   [批量添加] [恢复默认]
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColorPoolEditor(
    rememberKey: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var hue by remember(rememberKey) { mutableStateOf(0f) }
    var lightness by remember(rememberKey) { mutableStateOf(50f) }
    var batchVisible by remember(rememberKey) { mutableStateOf(false) }
    var batchInput by remember(rememberKey) { mutableStateOf("") }
    var resetVisible by remember(rememberKey) { mutableStateOf(false) }

    val colors = remember(value) { parseColorList(value) }
    val previewColor = remember(hue, lightness) { hslToDecimal(hue, 100f, lightness) }
    val batchColors = remember(batchInput) { parseColorList(batchInput) }
    val previewCompose = remember(previewColor) { Color(0xFF000000.toInt() or (previewColor and 0xFFFFFF)) }

    fun emit(next: List<Int>) {
        onValueChange(next.joinToString(","))
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "颜色池",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "${colors.size} 个",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AppGlassSurface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (colors.isEmpty()) {
                    Text(
                        "未配置，将使用默认颜色池",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        colors.forEachIndexed { index, color ->
                            ColorPoolSwatch(
                                color = color,
                                onRemove = {
                                    emit(colors.filterIndexed { i, _ -> i != index })
                                }
                            )
                        }
                    }
                }
            }
        }

        AppGlassSurface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HueWheel(hue = hue, color = previewColor, onHueChange = { hue = it })
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .background(previewCompose, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                colorHex(previewColor).removePrefix("#"),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (previewCompose.luminance() < 0.55f) Color.White else Color.Black
                            )
                        }
                        Text(
                            "亮度 ${lightness.roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = lightness,
                            onValueChange = { lightness = it },
                            valueRange = 10f..100f
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppGlassButton(
                        onClick = { emit(colors + (previewColor and 0xFFFFFF)) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("添加到颜色池")
                    }
                    AppGlassButton(
                        onClick = { emit(colors + Random.nextInt(0x1000000)) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Casino, null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("随机添加")
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppGlassButton(
                onClick = { batchVisible = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.PlaylistAdd, null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("批量添加")
            }
            AppGlassButton(
                onClick = { resetVisible = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("恢复默认")
            }
        }
    }

    if (batchVisible) {
        AppDialog(
            onDismissRequest = { batchVisible = false },
            style = AppDialogStyle.Form,
            tone = AppDialogTone.Brand,
            title = { Text("批量添加颜色") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        Text(
                            "预览：${batchColors.size} 个颜色",
                            style = MaterialTheme.typography.bodySmall
                        )
                        ColorPreviewChips(batchColors, maxCount = 24)
                    }
                }
            },
            confirmButton = {
                AppGlassButton(
                    onClick = {
                        if (batchColors.isNotEmpty()) emit(colors + batchColors)
                        batchInput = ""
                        batchVisible = false
                    },
                    enabled = batchColors.isNotEmpty()
                ) { Text("添加") }
            },
            dismissButton = {
                AppGlassButton(
                    onClick = {
                        batchInput = ""
                        batchVisible = false
                    }
                ) { Text("取消") }
            }
        )
    }

    if (resetVisible) {
        AppDialog(
            onDismissRequest = { resetVisible = false },
            style = AppDialogStyle.Confirm,
            tone = AppDialogTone.Warning,
            title = { Text("恢复默认") },
            text = { Text("清空颜色池后，核心会使用内置的默认颜色池。") },
            confirmButton = {
                AppGlassButton(
                    onClick = {
                        emit(emptyList())
                        resetVisible = false
                    }
                ) { Text("清空") }
            },
            dismissButton = {
                AppGlassButton(onClick = { resetVisible = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ColorPoolSwatch(color: Int, onRemove: () -> Unit) {
    val composeColor = Color(0xFF000000.toInt() or (color and 0xFFFFFF))
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 44.dp)
                    .background(composeColor, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    colorHex(color).removePrefix("#"),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (composeColor.luminance() < 0.55f) Color.White else Color.Black
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .size(20.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Close,
                    "删除",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onError
                )
            }
        }
        Text(
            color.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
