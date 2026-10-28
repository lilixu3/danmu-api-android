package com.example.danmuapiapp.ui.screen.tools

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.danmuapiapp.domain.model.LocalDanmuBatchItem
import com.example.danmuapiapp.domain.model.LocalDanmuBatchStatus
import com.example.danmuapiapp.ui.screen.localdanmu.canSelect

@Composable internal fun ImportSelectionRow(item: LocalDanmuBatchItem, running: Boolean,
    onToggle: () -> Unit, onEdit: () -> Unit, onRemove: (() -> Unit)? = null) {
    SelectionRow(selected = item.selected && item.canSelect, onToggle = onToggle, enabled = !running && item.canSelect,
        trailing = {
            if (item.status == LocalDanmuBatchStatus.Uploading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else {
                if (item.status == LocalDanmuBatchStatus.Success) Icon(Icons.Outlined.TaskAlt, "已导入", Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                ToolIcon(Icons.Outlined.Edit, "订正 ${item.displayName} 的元数据", onEdit, enabled = !running, size = 40)
            }
            if (onRemove != null) ToolIcon(Icons.Outlined.Close, "移除 ${item.displayName}", onRemove, enabled = !running, size = 40)
        }) {
        ToolTitle("${item.episodeLabel} · ${item.title}", size = 13)
        ToolHint("${item.year ?: "年份待订正"} · ${item.type.label} · ${item.displayName}", maxLines = 1)
        val hint = item.validationError ?: item.message.ifBlank { item.importedLabel.orEmpty() }
        if (hint.isNotBlank()) Text(hint, fontSize = 11.sp,
            color = if (item.validationError != null || item.status == LocalDanmuBatchStatus.Failed)
                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    }
}
