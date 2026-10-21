package com.example.danmuapiapp.ui.screen.download

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.danmuapiapp.domain.model.*
import com.example.danmuapiapp.ui.common.formatByteSize
import com.example.danmuapiapp.ui.screen.tools.*
import java.util.Locale

@Composable internal fun DanmuPreviewDialog(state: DanmuPreviewDialogState, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.88f).padding(horizontal = 14.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.background) {
            Column {
                ToolHeader(state.preview?.fileName?.ifBlank { "弹幕预览" } ?: "弹幕预览", onDismiss)
                if (state.loadingRecordId != null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                state.error?.let { ToolError(it) }
                state.preview?.let { preview ->
                    var search by remember(preview.fileName) { mutableStateOf("") }
                    var mode by remember(preview.fileName) { mutableStateOf(DanmuPreviewFilter.All) }
                    val filtered = remember(preview, mode, search) {
                        preview.items.filter { (mode == DanmuPreviewFilter.All || it.previewFilter() == mode) &&
                            (search.isBlank() || it.text.contains(search, ignoreCase = true)) }
                    }
                    val counts = remember(preview, search) { preview.items.filter { search.isBlank() || it.text.contains(search, ignoreCase = true) }
                        .groupingBy { it.previewFilter() }.eachCount() }
                    Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolHint("${preview.count} 条弹幕 · ${preview.displayFormatLabel} · ${formatByteSize(preview.bytes)}")
                        if (preview.truncated) ToolHint("文件较大，展示前 ${preview.items.size} 条；总数 ${preview.count} 条")
                        preview.parseError?.let { ToolError(it) }
                        ToolInput(search, { search = it }, "搜索弹幕内容", icon = Icons.Outlined.Search)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DanmuPreviewFilter.entries.forEach { filter ->
                                ToolChip("${filter.label} ${if (filter == DanmuPreviewFilter.All) counts.values.sum() else counts[filter] ?: 0}", mode == filter, { mode = filter })
                            }
                        }
                        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                            items(filtered, key = { it.index }) { item ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Text(previewClock(item.timeSeconds), Modifier.width(42.dp), fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(item.previewFilter().label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    val color = previewColor(item.color)
                                    Box(Modifier.size(9.dp).background(color ?: MaterialTheme.colorScheme.outlineVariant, CircleShape))
                                    Column(Modifier.weight(1f)) {
                                        Text(item.text, fontSize = 12.sp, lineHeight = 17.sp)
                                        if (item.source.isNotBlank()) ToolHint(item.source)
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
                            }
                        }
                    }
                }
            }
        }
    }
}
private fun previewClock(seconds: Double?): String = seconds?.let {
    val n = it.toLong().coerceAtLeast(0)
    String.format(Locale.ROOT, "%02d:%02d", n / 60, n % 60)
} ?: "--:--"
private fun previewColor(text: String): Color? {
    val value = if (text.startsWith('#')) text.drop(1).toLongOrNull(16) else text.toLongOrNull()
    if (value == null || value == 0xffffffL) return null
    return Color((value or 0xff000000L).toInt())
}
