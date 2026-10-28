package com.example.danmuapiapp.ui.screen.download

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.AppDialogStyle
import com.example.danmuapiapp.ui.component.longPressDragSelection
import com.example.danmuapiapp.ui.screen.tools.*

@Composable internal fun MissingEpisodesDialog(state: MissingEpisodesState,
    onDismiss: () -> Unit, onSelectionChange: (Set<Long>) -> Unit,
    onToggle: (Long) -> Unit, onDownload: () -> Unit) {
    val listState = rememberLazyListState()
    val keys = state.episodes.map { it.episodeId }
    AppDialog(onDismissRequest = onDismiss, style = AppDialogStyle.Selection, scrollContent = false,
        icon = { Icon(Icons.Outlined.Download, null) },
        title = { ToolTitle("选择要下载的弹幕", size = 18) },
        supportingText = { ToolHint(state.title, maxLines = 2) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp).weight(1f, fill = false)
                .longPressDragSelection(listState, keys, state.selectedIds, onSelectionChange = onSelectionChange),
                state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "summary") { ToolHint("源里 ${state.sourceTotal} 集 · 已下载 ${state.downloadedCount} 集 · 缺失 ${state.episodes.size} 集") }
                item(key = "selection") {
                    SelectionToolbar(keys, state.selectedIds, onSelectionChange, unit = "集")
                }
                if (state.episodes.isEmpty()) item { ToolHint("本地已包含全部剧集，无需补充下载。") }
                items(state.episodes, key = { it.episodeId }) { episode ->
                    SelectionRow(episode.episodeId in state.selectedIds, { onToggle(episode.episodeId) }) {
                        ToolTitle("第 ${episode.episodeNumber} 集 · ${episode.title}", size = 13)
                        if (episode.source.isNotBlank()) ToolHint(episode.source, maxLines = 1)
                    }
                }
                item { ToolHint("${state.way} · 下载后可导入本地库") }
            }
        },
        actions = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolButton("取消", onDismiss, outlined = true)
                ToolButton("下载 ${state.selectedIds.size} 集", onDownload, Modifier.weight(1f), Icons.Outlined.Download,
                    enabled = state.selectedIds.isNotEmpty())
            }
        })
}
