package com.example.danmuapiapp.ui.screen.tools

import com.example.danmuapiapp.ui.component.AppDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File

@Composable internal fun ToolDirectoryPicker(initial: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    var path by remember { mutableStateOf(initial.ifBlank { "/storage/emulated/0/Download" }) }
    var generation by remember { mutableIntStateOf(0) }
    var newFolder by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listing by produceState<List<File>?>(null, path, generation) {
        value = null
        value = withContext(Dispatchers.IO) {
            val directory = File(path)
            val files = directory.listFiles()
            if (files == null) { error = "目录不可读，请检查权限或返回上一级"; emptyList() }
            else { error = null; files.filter { it.isDirectory }.sortedBy { it.name.lowercase() } }
        }
    }
    AppDialog(onDismissRequest = onDismiss, scrollContent = false, title = { ToolTitle("选择下载目录", size = 16) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolHint(path)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolButton("上一级", { File(path).parent?.let { path = it } }, outlined = true, enabled = File(path).parent != null)
                ToolButton("新建文件夹", { newFolder = true }, outlined = true)
            }
            error?.let { ToolError(it) }
            if (listing == null) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 330.dp)) {
                items(listing.orEmpty(), key = { it.absolutePath }) { folder ->
                    ToolListRow("目录", "", folder.name, "", { path = folder.absolutePath }, actions = {
                        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp))
                    })
                }
            }
        } }, dismissButton = { TextButton(onDismiss) { Text("取消") } },
        confirmButton = { ToolButton("使用此目录", { onSelect(path) }, enabled = error == null && listing != null) })
    if (newFolder) AppDialog(onDismissRequest = { newFolder = false }, title = { ToolTitle("新建文件夹", size = 16) },
        text = { ToolInput(folderName, { folderName = it }, "文件夹名称") }, dismissButton = { TextButton({ newFolder = false }) { Text("取消") } },
        confirmButton = { ToolButton("创建", {
            val name = folderName.trim()
            if (name.isBlank() || name in setOf(".", "..") || name.any { it == '/' || it == '\\' }) error = "请输入有效的文件夹名称"
            else scope.launch {
                val success = withContext(Dispatchers.IO) { File(path, name).mkdir() }
                if (!success) error = "创建失败，文件夹可能已存在或目录不可写"
                else { generation++; newFolder = false; folderName = "" }
            }
        }) })
}
