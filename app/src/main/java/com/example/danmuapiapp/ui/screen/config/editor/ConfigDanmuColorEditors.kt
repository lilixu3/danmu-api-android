@file:OptIn(ExperimentalLayoutApi::class)

package com.example.danmuapiapp.ui.screen.config

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material3.Slider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassAssistChip
import com.example.danmuapiapp.ui.component.liquid.AppGlassFilterChip
import java.util.Locale
import kotlin.random.Random

private val QuickColorPalette = listOf(
    0xFFFFFF, 0xFF6699, 0xFFCC00, 0x66CC66,
    0x00AEEF, 0x8A2BE2, 0xFF5733, 0x2ECC71,
    0x3498DB, 0xF1C40F, 0xE91E63, 0x9B59B6,
    0x1ABC9C, 0xFF7F50, 0xC0C0C0, 0x000000
)

private fun parseColorToken(raw: String): Int? {
    var token = raw.trim()
    if (token.isBlank()) return null
    if (token.equals("default", true) || token.equals("white", true) || token.equals("color", true)) return null
    token = token.removePrefix("#")
    if (token.startsWith("0x", ignoreCase = true)) token = token.drop(2)
    if (token.length == 3 && token.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
        token = token.map { "$it$it" }.joinToString("")
    }
    val value = when {
        token.length == 6 && token.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } -> token.toIntOrNull(16)
        token.all { it.isDigit() } -> token.toIntOrNull(10)
        else -> null
    } ?: return null
    return value.takeIf { it in 0..0xFFFFFF }
}

internal fun parseColorList(raw: String): List<Int> {
    return raw.split(Regex("[,;\\s，、]+"))
        .mapNotNull { parseColorToken(it) }
}

internal fun colorHex(color: Int): String {
    return "#" + (color and 0xFFFFFF).toString(16).padStart(6, '0').uppercase(Locale.getDefault())
}

private fun colorCompose(color: Int): Color {
    return Color(0xFF000000.toInt() or (color and 0xFFFFFF))
}

private fun appendColorsToRaw(current: String, colors: List<Int>): String {
    val cleanColors = colors.filter { it in 0..0xFFFFFF }
    if (cleanColors.isEmpty()) return current
    val currentMode = current.trim().lowercase(Locale.getDefault())
    val baseColors = when (currentMode) {
        "white" -> listOf(16777215)
        "default", "color" -> emptyList()
        else -> parseColorList(current)
    }
    return (baseColors + cleanColors).joinToString(",")
}

@Composable
private fun ColorDot(color: Int, modifier: Modifier = Modifier.size(16.dp)) {
    Box(
        modifier = modifier
            .background(colorCompose(color), CircleShape)
    )
}

@Composable
internal fun ColorPreviewChips(colors: List<Int>, maxCount: Int = 32) {
    if (colors.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        colors.take(maxCount).forEach { color ->
            AppGlassAssistChip(
                onClick = {},
                leadingIcon = { ColorDot(color) },
                label = { Text(colorHex(color)) }
            )
        }
        if (colors.size > maxCount) {
            AppGlassAssistChip(onClick = {}, label = { Text("+${colors.size - maxCount}") })
        }
    }
}

@Composable
internal fun HueWheel(
    hue: Float,
    color: Int,
    onHueChange: (Float) -> Unit,
) {
    val centerColor = MaterialTheme.colorScheme.surface
    Canvas(
        modifier = Modifier
            .size(132.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val dx = change.position.x - centerX
                    val dy = change.position.y - centerY
                    val angle = ((atan2(dy, dx) * 180f / PI.toFloat()) + 90f + 360f) % 360f
                    onHueChange(angle)
                    change.consume()
                }
            }
    ) {
        val radius = min(size.width, size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            brush = Brush.sweepGradient(
                listOf(
                    Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
                    Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000)
                )
            ),
            radius = radius,
            center = center
        )
        drawCircle(color = centerColor, radius = radius * 0.42f, center = center)
        val rad = (hue - 90f) * PI.toFloat() / 180f
        val dotCenter = Offset(
            center.x + radius * 0.72f * cos(rad),
            center.y + radius * 0.72f * sin(rad)
        )
        drawCircle(color = Color.White, radius = 8.dp.toPx(), center = dotCenter)
        drawCircle(color = colorCompose(color), radius = 5.5.dp.toPx(), center = dotCenter)
    }
}

internal fun hslToDecimal(h: Float, saturationPercent: Float, lightnessPercent: Float): Int {
    val s = (saturationPercent / 100f).coerceIn(0f, 1f)
    val l = (lightnessPercent / 100f).coerceIn(0f, 1f)
    val a = s * kotlin.math.min(l, 1f - l)
    fun f(n: Float): Float {
        val k = (n + h / 30f) % 12f
        return l - a * maxOf(-1f, minOf(k - 3f, minOf(9f - k, 1f)))
    }
    val r = Math.round(f(0f) * 255f).coerceIn(0, 255)
    val g = Math.round(f(8f) * 255f).coerceIn(0, 255)
    val b = Math.round(f(4f) * 255f).coerceIn(0, 255)
    return (r shl 16) or (g shl 8) or b
}

@Composable
internal fun ColorListEditor(
    rememberKey: String,
    value: String,
    onValueChange: (String) -> Unit,
    title: String,
    envKey: String,
) {
    var panel by remember(rememberKey) { mutableStateOf("none") }
    var colorInput by remember(rememberKey) { mutableStateOf("#FFFFFF") }
    var batchInput by remember(rememberKey) { mutableStateOf("") }
    var hue by remember(rememberKey) { mutableStateOf(0f) }
    var lightness by remember(rememberKey) { mutableStateOf(50f) }

    val normalizedKey = remember(envKey) { envKey.trim().uppercase(Locale.ROOT) }
    val isConvertColor = normalizedKey == "CONVERT_COLOR"
    val parsedColors = remember(value) { parseColorList(value) }
    val rawMode = value.trim().lowercase(Locale.ROOT)
    val wheelColor = remember(hue, lightness) { hslToDecimal(hue, 100f, lightness) }
    val selectedColor = parseColorToken(colorInput)
    val batchColors = remember(batchInput) { parseColorList(batchInput) }
    val modeSummary = when {
        isConvertColor && (rawMode.isBlank() || rawMode == "default") -> "当前：default，不转换弹幕颜色。"
        isConvertColor && rawMode == "white" -> "当前：white，全部弹幕转白色。"
        isConvertColor && rawMode == "color" -> "当前：color，使用 COLOR_POOL 随机颜色池。"
        isConvertColor -> "当前：本变量直接保存颜色列表，已识别 ${parsedColors.size} 个颜色。"
        rawMode.isBlank() -> "当前：未配置，将使用核心默认颜色池。"
        else -> "当前：COLOR_POOL 已识别 ${parsedColors.size} 个颜色。"
    }

    fun appendColors(colors: List<Int>) {
        onValueChange(appendColorsToRaw(value, colors))
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(
            if (isConvertColor) {
                "按稳定版页面处理：优先用 default / white / color 三个模式；需要直接写颜色时再展开颜色盘。"
            } else {
                "按稳定版颜色池处理：先显示当前颜色，再用颜色盘、随机、批量添加。保存值始终是核心识别的十进制颜色。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isConvertColor) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppGlassFilterChip(selected = rawMode.isBlank() || rawMode == "default", onClick = { onValueChange("default"); panel = "none" }, label = { Text("default") })
                AppGlassFilterChip(selected = rawMode == "white", onClick = { onValueChange("white"); panel = "none" }, label = { Text("white") })
                AppGlassFilterChip(selected = rawMode == "color", onClick = { onValueChange("color"); panel = "none" }, label = { Text("color") })
            }
        }

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(if (isConvertColor) "原始值 / 颜色列表" else "颜色池原始值") },
            placeholder = { Text(if (isConvertColor) "default / white / color / 16777215,16744319" else "16777215,16744319,9498256") },
            singleLine = false,
            minLines = 2,
            maxLines = 5,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Text(modeSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (parsedColors.isNotEmpty()) {
            Text("当前颜色", style = MaterialTheme.typography.labelLarge)
            ColorPreviewChips(parsedColors, maxCount = 20)
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppGlassButton(onClick = { panel = if (panel == "color") "none" else "color" }) {
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(6.dp))
                Text(if (panel == "color") "收起颜色盘" else "颜色盘")
            }
            AppGlassButton(onClick = { appendColors(listOf(Random.nextInt(0x1000000))) }) { Text("随机添加") }
            AppGlassButton(onClick = { panel = if (panel == "batch") "none" else "batch" }) { Text("批量添加") }
            AppGlassButton(onClick = { onValueChange(if (isConvertColor) "default" else "") }) { Text("恢复默认") }
        }

        if (panel == "color") {
            AppGlassSurface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("颜色盘", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        HueWheel(hue = hue, color = wheelColor, onHueChange = { hue = it })
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ColorDot(wheelColor, modifier = Modifier.size(34.dp))
                                Text(colorHex(wheelColor), style = MaterialTheme.typography.bodyMedium)
                            }
                            Text("亮度", style = MaterialTheme.typography.labelMedium)
                            Slider(value = lightness, onValueChange = { lightness = it }, valueRange = 10f..90f)
                            AppGlassButton(onClick = { appendColors(listOf(wheelColor)); colorInput = colorHex(wheelColor) }) { Text("添加当前颜色") }
                        }
                    }

                    Text("常用色", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        QuickColorPalette.forEach { preset ->
                            AppGlassAssistChip(
                                onClick = { colorInput = colorHex(preset); appendColors(listOf(preset)) },
                                leadingIcon = { ColorDot(preset) },
                                label = { Text(colorHex(preset)) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        ColorDot(selectedColor ?: wheelColor, modifier = Modifier.size(28.dp))
                        OutlinedTextField(
                            value = colorInput,
                            onValueChange = { colorInput = it },
                            label = { Text("HEX / 十进制") },
                            placeholder = { Text("#FF6699 或 16737945") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppGlassButton(onClick = { selectedColor?.let { appendColors(listOf(it)) } }, enabled = selectedColor != null) { Text("添加输入颜色") }
                        AppGlassButton(onClick = { panel = "none" }) { Text("完成") }
                    }
                }
            }
        }

        if (panel == "batch") {
            AppGlassSurface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("批量添加颜色", style = MaterialTheme.typography.labelLarge)
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
                        Text("预览：${batchColors.size} 个颜色", style = MaterialTheme.typography.bodySmall)
                        ColorPreviewChips(batchColors, maxCount = 20)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppGlassButton(
                            onClick = {
                                appendColors(batchColors)
                                batchInput = ""
                                panel = "none"
                            },
                            enabled = batchColors.isNotEmpty()
                        ) { Text("确认添加") }
                        AppGlassButton(onClick = { panel = "none" }) { Text("取消") }
                    }
                }
            }
        }
    }
}
