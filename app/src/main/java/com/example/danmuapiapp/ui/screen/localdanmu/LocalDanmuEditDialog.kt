package com.example.danmuapiapp.ui.screen.localdanmu

import com.example.danmuapiapp.ui.component.AppDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.data.repository.validateLocalDanmuEdit
import com.example.danmuapiapp.domain.model.*
import com.example.danmuapiapp.ui.screen.tools.*
import java.util.Calendar

@Composable internal fun LocalDanmuEditDialog(state: LocalDanmuEditState, groupFileCount: Int, onDismiss: () -> Unit,
    onScopeChange: (LocalDanmuEditScope) -> Unit, onTitleChange: (String) -> Unit, onYearChange: (String) -> Unit,
    onTypeChange: (LocalDanmuType) -> Unit, onSeasonChange: (String) -> Unit, onEpisodeChange: (String) -> Unit,
    onFilenameChange: (String) -> Unit, onSubmit: () -> Unit) {
    val validation = validateLocalDanmuEdit(state.scope, state.title, state.year, state.type, state.season, state.episode,
        state.filename, Calendar.getInstance().get(Calendar.YEAR))
    AppDialog(onDismissRequest = { if (!state.isSaving) onDismiss() }, scrollContent = false, title = { ToolTitle("编辑本地弹幕", size = 16) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LocalDanmuEditScope.entries.forEach { scope -> ToolChip(scope.label, scope == state.scope, { if (!state.isSaving) onScopeChange(scope) }) }
            }
            if (state.scope == LocalDanmuEditScope.Resource) {
                ToolHint("只修改这一个文件的集数与文件名。")
                ToolInput(state.episode?.toString().orEmpty(), onEpisodeChange, if (state.isMovie) "集数（电影可留空）" else "集数（必填）", number = true)
                ToolInput(state.filename, onFilenameChange, "文件名")
            } else {
                ToolHint("会更新该剧第${state.season ?: 1}季的全部 $groupFileCount 个文件。")
                ToolInput(state.title, onTitleChange, "标题")
                ToolInput(state.year?.toString().orEmpty(), onYearChange, "年份", number = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LocalDanmuType.entries.forEach { type -> ToolChip(type.label, type == state.type, { if (!state.isSaving) onTypeChange(type) }) }
                }
                ToolInput(state.season?.toString().orEmpty(), onSeasonChange, "季数", number = true)
            }
            (state.errorMessage ?: validation)?.let { ToolError(it) }
        } }, confirmButton = { ToolButton("保存", onSubmit, enabled = validation == null, busy = state.isSaving) },
        dismissButton = { TextButton(onDismiss, enabled = !state.isSaving) { Text("取消") } })
}
