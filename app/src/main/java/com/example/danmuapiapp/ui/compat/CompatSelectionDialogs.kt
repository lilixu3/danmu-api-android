package com.example.danmuapiapp.ui.compat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.CoreBranchCatalog
import com.example.danmuapiapp.ui.component.remoteFocusHighlight

@Composable
internal fun CompatProxyDialog(state: CompatProxyPickerState, actions: CompatModeActions) {
    CompatDialog("选择下载线路", actions.onDismissProxyPicker, "保存线路", actions.onConfirmProxySelection,
        confirmEnabled = state.options.any { it.id == state.selectedId }) {
        Text("用于 GitHub 检查更新与下载。选择后保存，下次会继续使用。",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        CompatButton("重新测速", actions.onRetestProxySpeed, icon = Icons.Rounded.Refresh,
            enabled = state.testingIds.isEmpty())
        state.options.forEach { option ->
            val latency = state.latencyMap[option.id]
            val testing = option.id in state.testingIds
            val detail = when {
                testing -> "测速中…"
                latency == null -> "尚未测速"
                latency >= 0 -> "可用 · $latency ms"
                else -> "不可用"
            }
            val color = when {
                testing -> MaterialTheme.colorScheme.primary
                latency == null -> MaterialTheme.colorScheme.onSurfaceVariant
                latency >= 0 -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.error
            }
            CompatChoice(option.name, option.id == state.selectedId, { actions.onSelectProxy(option.id) }, detail, color)
        }
    }
}

@Composable
internal fun CompatBranchDialog(
    variantLabel: String,
    catalog: CoreBranchCatalog?,
    currentBranch: String,
    isLoading: Boolean,
    errorMessage: String?,
    actions: CompatModeActions
) {
    val initial = catalog?.branches?.firstOrNull { it.equals(currentBranch, ignoreCase = true) }
        ?: catalog?.defaultBranch.orEmpty()
    var selected by rememberSaveable(catalog?.repo, currentBranch, initial) { mutableStateOf(initial) }
    val canConfirm = !isLoading && errorMessage == null &&
        catalog?.branches?.any { it == selected } == true
    CompatDialog("选择核心分支", actions.onDismissBranchPicker, "切换并重装",
        { actions.onSwitchCoreBranch(selected) }, confirmEnabled = canConfirm) {
        Text(catalog?.repo?.ifBlank { variantLabel } ?: variantLabel,
            style = MaterialTheme.typography.bodyMedium)
        Text("切换分支后将重新下载该核心。", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            isLoading -> Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                Text("正在读取分支…")
            }
            errorMessage != null -> {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
                CompatButton("重新读取", actions.onRetryBranches, icon = Icons.Rounded.Refresh)
            }
            catalog?.branches.isNullOrEmpty() -> {
                Text("暂未读取到可用分支。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                CompatButton("重新读取", actions.onRetryBranches, icon = Icons.Rounded.Refresh)
            }
            else -> catalog.branches.forEach { branch ->
                CompatChoice(branch, branch == selected, { selected = branch },
                    detail = if (branch.equals(catalog.defaultBranch, ignoreCase = true)) "默认分支" else null)
            }
        }
    }
}

/** One focus target per option, with separate indicators for selection and focus. */
@Composable
private fun CompatChoice(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    detail: String? = null,
    detailColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth().clip(shape)
        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
        .remoteFocusHighlight(shape).selectable(selected, role = Role.RadioButton, onClick = onSelect)
        .heightIn(min = 52.dp).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = detailColor)
        }
        if (selected) Icon(Icons.Rounded.Check, "已选中", tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp))
    }
}
