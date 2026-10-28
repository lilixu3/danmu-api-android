package com.example.danmuapiapp.ui.screen.localdanmu

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.ui.component.longPressDragSelection
import com.example.danmuapiapp.ui.common.formatByteSize
import com.example.danmuapiapp.ui.screen.tools.*

@Composable internal fun LocalDanmuDirectoryBrowserPanel(state: LocalDanmuBrowserState, onBack: () -> Unit,
    onOpenParent: () -> Unit, onOpenDefaultDirectory: () -> Unit, onSelectionChange: (Set<String>) -> Unit,
    onSetDefaultDirectory: () -> Unit, onToggleSelection: (String) -> Unit, onImportSelected: () -> Unit,
    onRefresh: () -> Unit, onOpenDirectory: (String) -> Unit) {
    BackHandler { onBack() }
    val listState = rememberLazyListState()
    LaunchedEffect(state.path) { listState.scrollToItem(0) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ToolHeader("选择弹幕文件", onBack, state.path.ifBlank { "正在读取目录…" }) {
            ToolIcon(Icons.Outlined.Refresh, "刷新目录", onRefresh, busy = state.isLoading, size = 40)
        }
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ToolChip("上一级", onClick = { if (state.parent != null) onOpenParent() })
            ToolChip("弹幕目录", onClick = onOpenDefaultDirectory)
            ToolChip(if (state.isDefaultDirectory) "默认目录" else "设为默认", state.isDefaultDirectory, onSetDefaultDirectory)
        }
        SelectionToolbar(state.selectablePaths, state.selectedPaths, onSelectionChange,
            Modifier.padding(horizontal = 14.dp, vertical = 5.dp), enabled = !state.isLoading, unit = "个")
        state.error?.let { ToolError(it) }
        if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.truncated) ToolHint("目录文件较多，当前展示已达到上限", Modifier.padding(horizontal = 14.dp))
        LazyColumn(Modifier.weight(1f).longPressDragSelection(listState, state.selectablePaths, state.selectedPaths,
            enabled = !state.isLoading, onSelectionChange = onSelectionChange), state = listState,
            contentPadding = PaddingValues(14.dp, 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.rows, key = { it.entry.path }) { row ->
                if (row.entry.isDirectory) {
                    ToolCard(onClick = { onOpenDirectory(row.entry.path) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Folder, null, Modifier.size(32.dp).padding(6.dp), tint = MaterialTheme.colorScheme.primary)
                            ToolTitle(row.entry.name, Modifier.weight(1f), size = 13)
                            Icon(Icons.Outlined.ChevronRight, "打开目录", Modifier.size(22.dp))
                        }
                    }
                } else {
                    SelectionRow(row.selected, { onToggleSelection(row.entry.path) }, enabled = !state.isLoading) {
                        ToolTitle(row.entry.name, size = 13)
                        ToolHint("${formatByteSize(row.entry.sizeBytes)}${row.importedLabel?.let { " · 已导入 $it" }.orEmpty()}", maxLines = 1)
                    }
                }
            }
        }
        ToolButton("导入选中 ${state.selectedCount} 个", onImportSelected, Modifier.fillMaxWidth().padding(14.dp),
            Icons.Outlined.UploadFile, enabled = state.selectedCount > 0 && !state.isLoading)
    }
}
