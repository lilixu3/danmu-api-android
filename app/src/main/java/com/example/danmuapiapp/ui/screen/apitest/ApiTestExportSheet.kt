package com.example.danmuapiapp.ui.screen.apitest

import com.example.danmuapiapp.ui.component.AppDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.danmuapiapp.domain.model.DanmuDownloadFormat

@Composable
internal fun ApiTestExportDialog(insight: DanmuInsight, exporting: Boolean, onDismiss: () -> Unit,
    onExport: (DanmuDownloadFormat) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(DanmuDownloadFormat.Json.value) }
    AppDialog(onDismissRequest = onDismiss, scrollContent = false, title = { Text("导出弹幕") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            ApiHint(insight.episodeTitle.ifBlank { insight.animeTitle })
            DanmuExportGroup.entries.forEach { group ->
                Text(group.label, Modifier.padding(top = 12.dp, bottom = 6.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                ApiTestExportCatalog.options.filter { it.group == group }.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected == option.format.value, { selected = option.format.value })
                        Column {
                            Text(option.displayName, fontSize = 12.sp)
                            ApiHint(option.format.value)
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { onExport(DanmuDownloadFormat.fromValueOrNull(selected) ?: DanmuDownloadFormat.Json) },
        enabled = !exporting) { Text("导出并保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
