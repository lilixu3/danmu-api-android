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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.LocalDanmuBatchItem
import com.example.danmuapiapp.domain.model.LocalDanmuType
import com.example.danmuapiapp.ui.component.longPressDragSelection
import com.example.danmuapiapp.ui.screen.tools.*

@Composable internal fun LocalDanmuBatchPanel(state: LocalDanmuBatchState, errorMessage: String?,
    onBack: () -> Unit, onToggleSelected: (Long) -> Unit, onRemove: (Long) -> Unit,
    onUpdateMetadata: (Long, String, Int?, LocalDanmuType, Int?, Int?) -> Unit,
    onRun: () -> Unit, onCancel: () -> Unit, onRetryFailed: () -> Unit, onDismissError: () -> Unit,
    onSetType: (LocalDanmuType?) -> Unit, onSelectionChange: (Set<Long>) -> Unit) {
    var editing by remember { mutableStateOf<LocalDanmuBatchItem?>(null) }
    val listState = rememberLazyListState()
    BackHandler { if (state.isRunning) onCancel() else onBack() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ToolHeader("导入本地弹幕", { if (state.isRunning) onCancel() else onBack() }, "共 ${state.items.size} 个文件 · 待导入 ${state.pendingCount}")
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectionToolbar(state.selectableIds, state.selectedIds, onSelectionChange, enabled = !state.isRunning, unit = "个")
            if (state.total > 0 || state.isRunning) {
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                ToolHint("${state.completed}/${state.total} · 成功 ${state.successCount} · 失败 ${state.failedCount}")
            }
            if (errorMessage != null) ToolError(errorMessage, onDismissError)
        }
        LazyColumn(Modifier.weight(1f).longPressDragSelection(listState, state.selectableIds, state.selectedIds,
            enabled = !state.isRunning, onSelectionChange = onSelectionChange), state = listState,
            contentPadding = PaddingValues(14.dp, 10.dp, 14.dp, 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "import-help") { ToolHint("铅笔可订正；已导入默认跳过，重选将覆盖同集弹幕。") }
            if (!state.isRunning) item(key = "import-type") { ImportTypeActions(onSetType) }
            items(state.items, key = { it.id }) { item ->
                ImportSelectionRow(item, state.isRunning, { onToggleSelected(item.id) }, { editing = item }, { onRemove(item.id) })
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
