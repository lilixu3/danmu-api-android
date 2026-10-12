package com.example.danmuapiapp.ui.screen.tools

import com.example.danmuapiapp.ui.component.AppDialog
import androidx.compose.foundation.clickable
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
import com.example.danmuapiapp.ui.screen.localdanmu.LocalDanmuBatchItem
import com.example.danmuapiapp.ui.screen.localdanmu.LocalDanmuBatchStatus

@Composable internal fun DownloadImportDialog(vm: DownloadImportViewModel) {
    var editing by remember { mutableStateOf<LocalDanmuBatchItem?>(null) }
    if (vm.planning) AppDialog(onDismissRequest = {}, title = { ToolTitle("准备导入") },
        text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); ToolHint("正在读取本地库，核对已导入的剧集…")
        } }, confirmButton = {})
    if (vm.batch.visible) {
        val batch = vm.batch
        AppDialog(onDismissRequest = vm::dismiss, scrollContent = false, title = { ToolTitle(vm.title, size = 16) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolHint(if (batch.isRunning) "正在导入 ${batch.completed}/${batch.total} · 成功 ${batch.successCount} · 失败 ${batch.failedCount}"
                    else "共 ${batch.items.size} 个文件 · 待导入 ${batch.pendingCount} · 已导入默认跳过，点条目可订正年份 / 季 / 集数")
                if (batch.total > 0) LinearProgressIndicator(progress = { batch.progress }, modifier = Modifier.fillMaxWidth())
                vm.error?.let { ToolError(it) }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    items(vm.blocked) { ToolError(it) }
                    items(batch.items, key = { it.id }) { item ->
                        Row(Modifier.fillMaxWidth().clickable(enabled = !batch.isRunning) { editing = item }.padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = item.selected, onCheckedChange = { vm.toggle(item.id) },
                                enabled = !batch.isRunning && item.status != LocalDanmuBatchStatus.Success, modifier = Modifier.size(34.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                ToolTitle(item.title, size = 12)
                                ToolHint("${item.year ?: "年份待订正"} · ${item.type.label} · ${item.episodeLabel}")
                                ToolHint(item.displayName, maxLines = 1)
                                val hint = item.validationError ?: item.message.ifBlank { item.importedLabel.orEmpty() }
                                if (hint.isNotBlank()) Text(hint, fontSize = 10.5.sp,
                                    color = if (item.validationError != null || item.status == LocalDanmuBatchStatus.Failed)
                                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            }
                            if (item.status == LocalDanmuBatchStatus.Uploading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else if (item.status == LocalDanmuBatchStatus.Success) Icon(Icons.Outlined.TaskAlt, "已导入", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            else ToolIcon(Icons.Outlined.Edit, "订正元数据", { editing = item }, enabled = !batch.isRunning)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
                    }
                }
            } },
            dismissButton = { TextButton(if (batch.isRunning) vm::cancel else vm::dismiss) {
                Text(if (batch.isRunning) if (batch.cancelRequested) "正在停止" else "取消导入" else "关闭", fontSize = 12.sp)
            } },
            confirmButton = {
                if (!batch.isRunning) {
                    if (batch.failedCount > 0) TextButton(vm::retry) { Text("重试失败", fontSize = 12.sp) }
                    ToolButton("导入 ${batch.pendingCount} 个", vm::run, enabled = batch.pendingCount > 0)
                }
            })
    }
    editing?.let { item -> ImportMetadataDialog(item, { editing = null }) { title, year, type, season, episode ->
        vm.edit(item.id, title, year, type, season, episode); editing = null
    } }
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
