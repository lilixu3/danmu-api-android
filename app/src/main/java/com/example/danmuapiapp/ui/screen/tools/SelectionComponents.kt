package com.example.danmuapiapp.ui.screen.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

@Composable internal fun <Key : Any> SelectionToolbar(
    keys: List<Key>,
    selectedKeys: Set<Key>,
    onSelectionChange: (Set<Key>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    unit: String = "项",
    showSummary: Boolean = true,
    onSelectUndownloaded: (() -> Unit)? = null
) {
    val available = keys.toSet()
    val count = selectedKeys.count { it in available }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton({ onSelectionChange(selectedKeys + available) }, Modifier.weight(1f).heightIn(min = 48.dp),
                    enabled = enabled && count < available.size, contentPadding = PaddingValues(0.dp)) {
                    Text("全选", fontSize = 12.sp, maxLines = 1)
                }
                TextButton({ onSelectionChange(selectedKeys - available) }, Modifier.weight(1f).heightIn(min = 48.dp),
                    enabled = enabled && count > 0, contentPadding = PaddingValues(0.dp)) {
                    Text("清空", fontSize = 12.sp, maxLines = 1)
                }
                TextButton({ onSelectionChange((selectedKeys - available) + (available - selectedKeys)) }, Modifier.weight(1f).heightIn(min = 48.dp),
                    enabled = enabled && available.isNotEmpty(), contentPadding = PaddingValues(0.dp)) {
                    Text("反选", fontSize = 12.sp, maxLines = 1)
                }
                if (onSelectUndownloaded != null) TextButton(onSelectUndownloaded, Modifier.weight(1.8f).heightIn(min = 48.dp),
                    enabled = enabled && available.isNotEmpty(), contentPadding = PaddingValues(0.dp)) {
                    Text("只选未下载", fontSize = 12.sp, maxLines = 2)
                }
            }
        }
        if (showSummary) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolHint("已选 $count / ${available.size} $unit", Modifier.weight(1f), maxLines = 1)
            ToolHint(if (enabled) "长按滑动多选" else "任务进行中", maxLines = 1)
        }
    }
}

@Composable internal fun SelectionRow(
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Surface(modifier.fillMaxWidth().clip(shape).toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() }),
        shape = shape,
        color = if (selected) colors.primaryContainer.copy(alpha = .45f) else colors.surface,
        border = BorderStroke(1.dp, if (selected) colors.primary.copy(alpha = .5f) else colors.outlineVariant.copy(alpha = .5f))) {
        Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(selected, onCheckedChange = null, enabled = enabled, modifier = Modifier.size(40.dp))
            Column(Modifier.weight(1f).padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
            trailing()
        }
    }
}
