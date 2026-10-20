package com.example.danmuapiapp.ui.screen.localdanmu

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.danmuapiapp.domain.model.LocalDanmuType
import com.example.danmuapiapp.ui.screen.tools.*

@Composable internal fun LocalDanmuBatchPanel(state: LocalDanmuBatchState, errorMessage: String?,
    onBack: () -> Unit, onToggleSelected: (Long) -> Unit, onRemove: (Long) -> Unit,
    onUpdateMetadata: (Long, String, Int?, LocalDanmuType, Int?, Int?) -> Unit,
    onRun: () -> Unit, onCancel: () -> Unit, onRetryFailed: () -> Unit, onDismissError: () -> Unit,
    onSetType: (LocalDanmuType?) -> Unit) {
    var editing by remember { mutableStateOf<LocalDanmuBatchItem?>(null) }
    BackHandler { if (state.isRunning) onCancel() else onBack() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ToolHeader("导入本地弹幕", { if (state.isRunning) onCancel() else onBack() }, "共 ${state.items.size} 个文件 · 待导入 ${state.pendingCount}")
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolHint("已导入过的默认跳过；点条目可订正标题、年份、类型、季与集数。")
            if (!state.isRunning) ImportTypeActions(onSetType)
            if (state.total > 0 || state.isRunning) {
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                ToolHint("${state.completed}/${state.total} · 成功 ${state.successCount} · 失败 ${state.failedCount}")
            }
            if (errorMessage != null) ToolError(errorMessage, onDismissError)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(14.dp, 10.dp, 14.dp, 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.items, key = { it.id }) { item ->
                ToolCard(onClick = { if (!state.isRunning) editing = item }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(item.selected, { onToggleSelected(item.id) }, Modifier.size(34.dp), enabled = !state.isRunning && item.status != LocalDanmuBatchStatus.Success)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ToolTitle(item.displayName, size = 12, maxLines = 1)
                            ToolHint("${item.title} · ${item.year ?: "年份待订正"} · ${item.type.label} · ${item.episodeLabel}")
                            val note = item.validationError ?: item.message.ifBlank { item.importedLabel.orEmpty() }
                            if (note.isNotBlank()) Text(note, fontSize = 10.5.sp,
                                color = if (item.validationError != null || item.status == LocalDanmuBatchStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        }
                        if (item.status == LocalDanmuBatchStatus.Uploading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else {
                            ToolIcon(Icons.Outlined.Edit, "订正元数据", { editing = item }, enabled = !state.isRunning)
                            ToolIcon(Icons.Outlined.Close, "移除", { onRemove(item.id) }, enabled = !state.isRunning)
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.isRunning) ToolButton(if (state.cancelRequested) "正在停止" else "取消导入", onCancel, Modifier.fillMaxWidth(), outlined = true, enabled = !state.cancelRequested)
            else {
                if (state.failedCount > 0) ToolButton("重试失败", onRetryFailed, outlined = true)
                ToolButton("导入 ${state.pendingCount} 个", onRun, Modifier.weight(1f), Icons.Outlined.UploadFile, enabled = state.pendingCount > 0)
            }
        }
    }
    editing?.let { item -> ImportMetadataDialog(item, { editing = null }) { title, year, type, season, episode ->
        onUpdateMetadata(item.id, title, year, type, season, episode); editing = null
    } }
}
