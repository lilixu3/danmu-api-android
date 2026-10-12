package com.example.danmuapiapp.ui.screen.apitest

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.ui.screen.console.buildLogExportText
import com.example.danmuapiapp.ui.screen.console.LogExportFormatter
import com.example.danmuapiapp.ui.screen.console.shareLogsAsTextFile
import androidx.compose.ui.platform.LocalContext
import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.AppDialogStyle

@Composable
internal fun ApiLogsDialog(viewModel: ApiTestViewModel, onCopy: (String) -> Unit,
    onExport: (String, String) -> Unit, onDismiss: () -> Unit) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val runtime by viewModel.runtimeState.collectAsStateWithLifecycle()
    var all by rememberSaveable { mutableStateOf(false) }
    val since = viewModel.lastApiActionStartedAtMs
    val visible = logs.filter { all || since == null || it.timestamp >= since }.takeLast(300)
    val context = LocalContext.current
    val exported = buildLogExportText(visible, if (all) "全部日志" else "本次请求", runtime)
    AppDialog(onDismissRequest = onDismiss, style = AppDialogStyle.Status, scrollContent = false, text = {
        Column(Modifier.fillMaxWidth()) {
            Row {
                ApiLabel("服务日志")
                Spacer(Modifier.weight(1f))
                IconButton(onClick = viewModel::refreshLogs) { Icon(Icons.Rounded.Refresh, "刷新日志", Modifier.size(18.dp)) }
                IconButton(onClick = { onCopy(exported) }) { Icon(Icons.Rounded.ContentCopy, "复制日志", Modifier.size(18.dp)) }
                IconButton(onClick = { onExport(LogExportFormatter.defaultFileName(), exported) }) { Icon(Icons.Rounded.FileDownload, "导出日志", Modifier.size(18.dp)) }
                IconButton(onClick = { shareLogsAsTextFile(context, LogExportFormatter.defaultFileName(), exported) }) { Icon(Icons.Rounded.Share, "分享日志", Modifier.size(18.dp)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ApiTag("本次请求", selected = !all) { all = false }
                ApiTag("全部日志", selected = all) { all = true }
            }
            Spacer(Modifier.height(12.dp))
            if (visible.isEmpty()) ApiHint("暂无日志")
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visible) { log ->
                    Text("[${log.level}] ${log.message}", fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, lineHeight = 16.sp)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}
