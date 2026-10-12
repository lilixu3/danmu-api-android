package com.example.danmuapiapp.ui.screen.apitest

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Lazy items share one outline: only the controls and final row have rounded corners.
@Composable
internal fun DanmuListSurface(first: Boolean = false, last: Boolean = false, content: @Composable () -> Unit) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
    val shape = RoundedCornerShape(topStart = if (first) 20.dp else 0.dp, topEnd = if (first) 20.dp else 0.dp,
        bottomStart = if (last) 20.dp else 0.dp, bottomEnd = if (last) 20.dp else 0.dp)
    Surface(Modifier.fillMaxWidth().drawWithContent {
        drawContent()
        val stroke = 1.dp.toPx()
        val edge = stroke / 2
        val radius = 20.dp.toPx()
        val right = size.width - edge
        val bottom = size.height - edge
        val outline = Path()
        if (first) {
            outline.moveTo(edge, size.height)
            outline.lineTo(edge, radius)
            outline.arcTo(Rect(edge, edge, radius * 2 - edge, radius * 2 - edge), 180f, 90f, false)
            outline.lineTo(size.width - radius, edge)
            outline.arcTo(Rect(size.width - radius * 2 + edge, edge, right, radius * 2 - edge), 270f, 90f, false)
            outline.lineTo(right, size.height)
        } else if (last) {
            outline.moveTo(edge, 0f)
            outline.lineTo(edge, size.height - radius)
            outline.arcTo(Rect(edge, size.height - radius * 2 + edge, radius * 2 - edge, bottom), 180f, -90f, false)
            outline.lineTo(size.width - radius, bottom)
            outline.arcTo(Rect(size.width - radius * 2 + edge, size.height - radius * 2 + edge, right, bottom), 90f, -90f, false)
            outline.lineTo(right, 0f)
        } else {
            outline.moveTo(edge, 0f)
            outline.lineTo(edge, size.height)
            outline.moveTo(right, 0f)
            outline.lineTo(right, size.height)
        }
        drawPath(outline, borderColor, style = Stroke(stroke))
    }, shape = shape, color = MaterialTheme.colorScheme.surface, content = content)
}

@Composable
internal fun DanmuListControls(
    search: String,
    onSearch: (String) -> Unit,
    mode: DanmuCommentFilter,
    onMode: (DanmuCommentFilter) -> Unit,
    modeCounts: Map<DanmuCommentFilter, Int>,
    sourceCounts: List<Pair<String, Int>>,
    selectedSources: List<String>,
    onSource: (String) -> Unit,
    hasFilter: Boolean,
    onReset: () -> Unit,
    bucket: DanmuHeatBucket?,
    onClearBucket: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    DanmuListSurface(first = true) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp)) {
            TextField(search, onSearch, Modifier.fillMaxWidth(), singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp), shape = RoundedCornerShape(12.dp),
                placeholder = { Text("搜索弹幕内容", fontSize = 12.5.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(19.dp)) },
                trailingIcon = if (search.isNotEmpty()) ({
                    IconButton(onClick = { onSearch("") }) { Icon(Icons.Rounded.Close, "清空搜索", Modifier.size(17.dp)) }
                }) else null,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.surfaceContainerHigh.copy(alpha = 0.5f),
                    unfocusedContainerColor = colors.surfaceContainerHigh.copy(alpha = 0.5f),
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DanmuCommentFilter.entries.forEach { option ->
                    val selected = mode == option
                    Surface(shape = RoundedCornerShape(8.dp), color = if (selected) colors.primary.copy(alpha = 0.12f)
                        else colors.surfaceContainerHigh.copy(alpha = 0.45f)) {
                        Row(Modifier.clip(RoundedCornerShape(8.dp))
                            .selectable(selected, role = Role.Tab, onClick = { onMode(option) })
                            .heightIn(min = 32.dp).padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(option.label, fontSize = 11.sp, maxLines = 1,
                                color = if (selected) colors.primary else colors.onSurfaceVariant)
                            Text((modeCounts[option] ?: 0).toString(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                color = if (selected) colors.primary else colors.onSurface, maxLines = 1)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("来源", fontSize = 11.sp, color = colors.onSurfaceVariant, maxLines = 1)
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (sourceCounts.isEmpty()) Text("暂无来源", fontSize = 11.sp, color = colors.onSurfaceVariant, maxLines = 1)
                    sourceCounts.forEach { (source, count) ->
                        val selected = source in selectedSources
                        Surface(shape = RoundedCornerShape(8.dp), color = if (selected) colors.primary.copy(alpha = 0.12f)
                            else colors.surfaceContainerHigh.copy(alpha = 0.45f)) {
                            Row(Modifier.clip(RoundedCornerShape(8.dp))
                                .toggleable(selected, enabled = sourceCounts.size > 1, role = Role.Checkbox, onValueChange = { onSource(source) })
                                .heightIn(min = 32.dp).padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (selected) { Icon(Icons.Rounded.Check, null, Modifier.size(12.dp), tint = colors.primary); Spacer(Modifier.width(4.dp)) }
                                Text(detailSourceName(source), fontSize = 11.sp, maxLines = 1,
                                    color = if (selected) colors.primary else colors.onSurfaceVariant)
                                Spacer(Modifier.width(6.dp))
                                Text(count.toString(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                    color = if (selected) colors.primary else colors.onSurface)
                            }
                        }
                    }
                }
                if (hasFilter) TextButton(onClick = onReset, contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.heightIn(min = 32.dp)) { Text("重置", fontSize = 11.sp, maxLines = 1) }
            }
            if (bucket != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ApiHint("时间区间 ${apiTime(bucket.startSeconds)}–${apiTime(bucket.endSeconds)}", Modifier.weight(1f))
                    IconButton(onClick = onClearBucket, modifier = Modifier.size(30.dp)) { Icon(Icons.Rounded.Close, "清除时间区间", Modifier.size(15.dp)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
        }
    }
}
