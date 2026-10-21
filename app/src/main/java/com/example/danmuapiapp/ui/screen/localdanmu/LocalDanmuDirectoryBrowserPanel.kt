package com.example.danmuapiapp.ui.screen.localdanmu

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import com.example.danmuapiapp.ui.common.formatByteSize
import com.example.danmuapiapp.ui.screen.tools.*

@Composable internal fun LocalDanmuDirectoryBrowserPanel(state: LocalDanmuBrowserState, onBack: () -> Unit,
    onOpenParent: () -> Unit, onOpenDefaultDirectory: () -> Unit, onToggleSelectAll: () -> Unit,
    onSetDefaultDirectory: () -> Unit, onToggleSelection: (String) -> Unit, onImportSelected: () -> Unit,
    onRefresh: () -> Unit, onOpenDirectory: (String) -> Unit) {
    BackHandler { onBack() }
    val latest by rememberUpdatedState(state)
    val latestToggle by rememberUpdatedState(onToggleSelection)
    val rowHeight = with(LocalDensity.current) { 48.dp.toPx() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ToolHeader("选择弹幕文件", onBack, state.path.ifBlank { "正在读取目录…" }) {
            ToolIcon(Icons.Outlined.Refresh, "刷新目录", onRefresh, busy = state.isLoading, size = 40)
        }
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ToolChip("上一级", onClick = { if (state.parent != null) onOpenParent() })
            ToolChip("弹幕目录", onClick = onOpenDefaultDirectory)
            ToolChip(if (state.isDefaultDirectory) "默认目录" else "设为默认", state.isDefaultDirectory, onSetDefaultDirectory)
            ToolChip(if (state.allSelected) "取消全选" else "全选", onClick = onToggleSelectAll)
        }
        ToolHint("已选 ${state.selectedCount} 个 · 勾选框区域可上下拖动连续选择", Modifier.padding(horizontal = 14.dp, vertical = 5.dp))
        state.error?.let { ToolError(it) }
        if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.truncated) ToolHint("目录文件较多，当前展示已达到上限", Modifier.padding(horizontal = 14.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 14.dp)) {
            itemsIndexed(state.rows, key = { _, row -> row.entry.path }) { index, row ->
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (row.entry.isDirectory) {
                        Icon(Icons.Outlined.Folder, null, Modifier.size(32.dp).padding(6.dp), tint = MaterialTheme.colorScheme.primary)
                    } else {
                        // Only the checkbox column captures drag selection. Text remains normal scrolling.
                        Box(Modifier.width(36.dp).fillMaxHeight().pointerInput(state.path, index) {
                            var startY = 0f
                            var dragSelecting = true
                            var lastRange = emptySet<Int>()
                            detectDragGestures(onDragStart = { pos ->
                                startY = pos.y
                                dragSelecting = row.entry.path !in latest.selectedPaths
                                lastRange = emptySet()
                            }, onDrag = { change, _ ->
                                change.consume()
                                val offset = ((change.position.y - startY) / rowHeight).toInt()
                                val target = (index + offset).coerceIn(0, latest.rows.lastIndex)
                                val range = (minOf(index, target)..maxOf(index, target)).toSet()
                                (range - lastRange).forEach { i ->
                                    val current = latest.rows[i]
                                    if (!current.entry.isDirectory && (current.entry.path in latest.selectedPaths) != dragSelecting) latestToggle(current.entry.path)
                                }
                                lastRange = range
                            })
                        }, contentAlignment = Alignment.Center) {
                            Checkbox(row.selected, { onToggleSelection(row.entry.path) }, Modifier.size(32.dp))
                        }
                    }
                    Column(Modifier.weight(1f).clickable {
                        if (row.entry.isDirectory) onOpenDirectory(row.entry.path) else onToggleSelection(row.entry.path)
                    }.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(row.entry.name, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        if (!row.entry.isDirectory) ToolHint("${formatByteSize(row.entry.sizeBytes)}${row.importedLabel?.let { " · 已导入 $it" }.orEmpty()}", maxLines = 1)
                    }
                    if (row.entry.isDirectory) ToolIcon(Icons.Outlined.ChevronRight, "打开目录", { onOpenDirectory(row.entry.path) })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
            }
        }
        ToolButton("导入选中 ${state.selectedCount} 个", onImportSelected, Modifier.fillMaxWidth().padding(14.dp),
            Icons.Outlined.UploadFile, enabled = state.selectedCount > 0 && !state.isLoading)
    }
}
