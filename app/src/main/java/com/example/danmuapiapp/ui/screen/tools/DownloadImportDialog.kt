package com.example.danmuapiapp.ui.screen.tools

import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.AppDialogStyle
import com.example.danmuapiapp.ui.component.longPressDragSelection
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.LocalDanmuBatchItem
import com.example.danmuapiapp.domain.model.LocalDanmuType

@Composable internal fun DownloadImportDialog(vm: DownloadImportViewModel) {
    var editing by remember { mutableStateOf<LocalDanmuBatchItem?>(null) }
    if (vm.planning) AppDialog(onDismissRequest = {}, title = { ToolTitle("准备导入") },
        text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); ToolHint("正在读取本地库，核对已导入的剧集…")
        } }, confirmButton = {})
    if (vm.batch.visible) {
        val batch = vm.batch
        val listState = rememberLazyListState()
        AppDialog(onDismissRequest = vm::dismiss, style = AppDialogStyle.Selection, scrollContent = false,
            icon = { Icon(Icons.Outlined.UploadFile, null) }, title = { ToolTitle(vm.title, size = 18) },
            supportingText = { ToolHint("共 ${batch.items.size} 个文件 · 可导入 ${batch.pendingCount} 个") },
            text = {
                if (batch.total > 0) {
                    LinearProgressIndicator(progress = { batch.progress }, modifier = Modifier.fillMaxWidth())
                    ToolHint("已处理 ${batch.completed}/${batch.total} · 成功 ${batch.successCount} · 失败 ${batch.failedCount}")
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp).weight(1f, fill = false)
                    .longPressDragSelection(listState, batch.selectableIds, batch.selectedIds,
                        enabled = !batch.isRunning, onSelectionChange = vm::selectItems),
                    state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item(key = "selection") {
                        SelectionToolbar(batch.selectableIds, batch.selectedIds, vm::selectItems, enabled = !batch.isRunning, unit = "个")
                    }
                    item(key = "import-help") {
                        ToolHint("铅笔可订正；已导入默认跳过，重选将覆盖同集弹幕。")
                    }
                    if (!batch.isRunning) item(key = "import-type") { ImportTypeActions(vm::setBatchType) }
                    vm.error?.let { error -> item(key = "import-error") { ToolError(error) } }
                    itemsIndexed(vm.blocked, key = { index, _ -> "blocked-$index" }) { _, reason -> ToolError(reason) }
                    if (batch.items.isEmpty()) item(key = "empty") { ToolHint("没有可导入的文件，请检查下载文件是否仍然存在。") }
                    items(batch.items, key = { it.id }) { item ->
                        ImportSelectionRow(item, batch.isRunning, { vm.toggle(item.id) }, { editing = item })
                    }
                }
            },
            actions = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!batch.isRunning && batch.failedCount > 0) TextButton(vm::retry) { Text("重试失败的 ${batch.failedCount} 个文件") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (batch.isRunning) ToolButton(if (batch.cancelRequested) "正在停止" else "取消导入", vm::cancel,
                            Modifier.fillMaxWidth(), outlined = true, enabled = !batch.cancelRequested)
                        else {
                            ToolButton("关闭", vm::dismiss, outlined = true)
                            ToolButton("导入 ${batch.pendingCount} 个", vm::run, Modifier.weight(1f), Icons.Outlined.UploadFile, enabled = batch.pendingCount > 0)
                        }
                    }
                }
            })
    }
    editing?.let { item -> ImportMetadataDialog(item, { editing = null }) { title, year, type, season, episode ->
        vm.edit(item.id, title, year, type, season, episode); editing = null
    } }
}
@Composable internal fun ImportTypeActions(onSetType: (LocalDanmuType?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ToolHint("选中类型")
        ToolChip("自动", false, { onSetType(null) })
        ToolChip("电影", false, { onSetType(LocalDanmuType.Movie) })
        ToolChip("电视剧", false, { onSetType(LocalDanmuType.Tv) })
    }
}
@Composable internal fun ImportMetadataDialog(item: LocalDanmuBatchItem, onDismiss: () -> Unit,
    onSave: (String, Int?, LocalDanmuType, Int?, Int?) -> Unit) {
    var title by remember(item.id) { mutableStateOf(item.title) }
    var year by remember(item.id) { mutableStateOf(item.year?.toString().orEmpty()) }
    var type by remember(item.id) { mutableStateOf(item.type) }
    var season by remember(item.id) { mutableStateOf((item.season ?: 1).toString()) }
    var episode by remember(item.id) { mutableStateOf(item.episode?.toString().orEmpty()) }
    AppDialog(onDismissRequest = onDismiss, title = { ToolTitle("订正导入信息", size = 16) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolInput(title, { title = it }, "剧名")
            ToolInput(year, { year = it }, "年份（必填）", number = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LocalDanmuType.entries.forEach { value -> ToolChip(value.label, type == value, { type = value }) }
            }
            if (type == LocalDanmuType.Tv) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolInput(season, { season = it }, "季", Modifier.weight(1f), number = true)
                ToolInput(episode, { episode = it }, "集", Modifier.weight(1f), number = true)
            }
            ToolHint(item.displayName)
        } }, confirmButton = { ToolButton("保存", { onSave(title, year.toIntOrNull(), type, season.toIntOrNull(), episode.toIntOrNull()) }) },
        dismissButton = { TextButton(onDismiss) { Text("取消") } })
}
