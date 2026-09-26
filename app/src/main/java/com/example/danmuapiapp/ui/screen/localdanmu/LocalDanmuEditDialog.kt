package com.example.danmuapiapp.ui.screen.localdanmu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.data.repository.validateLocalDanmuEdit
import com.example.danmuapiapp.domain.model.LocalDanmuEditScope
import com.example.danmuapiapp.domain.model.LocalDanmuType
import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.AppDialogStyle
import com.example.danmuapiapp.ui.component.AppDialogTone
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import java.util.Calendar

/**
 * 本地弹幕元数据编辑弹窗，对应核心 PATCH /api/v2/local-danmu/:resourceKey。
 *
 * 范围只有两档，和核心一致：
 * - 仅此文件：改集数、文件名
 * - 整个分组：改该剧第 N 季全部文件的标题、年份、类型、季数
 */
@Composable
internal fun LocalDanmuEditDialog(
    state: LocalDanmuEditState,
    groupFileCount: Int,
    onDismiss: () -> Unit,
    onScopeChange: (LocalDanmuEditScope) -> Unit,
    onTitleChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
    onTypeChange: (LocalDanmuType) -> Unit,
    onSeasonChange: (String) -> Unit,
    onEpisodeChange: (String) -> Unit,
    onFilenameChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val validation = validateLocalDanmuEdit(
        scope = state.scope,
        title = state.title,
        year = state.year,
        type = state.type,
        season = state.season,
        episode = state.episode,
        filename = state.filename,
        currentYear = currentYear
    )
    val busy = state.isSaving

    AppDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        style = AppDialogStyle.Form,
        tone = AppDialogTone.Brand,
        icon = { Icon(Icons.Rounded.Edit, null) },
        title = { Text("编辑本地弹幕") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "修改范围",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LocalDanmuEditScope.entries.forEach { scope ->
                        FilterChip(
                            selected = state.scope == scope,
                            onClick = { onScopeChange(scope) },
                            enabled = !busy,
                            label = { Text(scope.label) }
                        )
                    }
                }
                Text(
                    when (state.scope) {
                        LocalDanmuEditScope.Resource -> "只改这一个文件的集数与显示文件名，弹幕内容不动。"
                        LocalDanmuEditScope.Group ->
                            "会同时更新「${state.title.ifBlank { "当前分组" }}」第${state.season ?: 1}季" +
                                "的全部 $groupFileCount 个文件（标题、年份、类型、季数）。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when (state.scope) {
                    LocalDanmuEditScope.Resource -> {
                        OutlinedTextField(
                            value = state.episode?.toString().orEmpty(),
                            onValueChange = onEpisodeChange,
                            label = {
                                Text(if (state.isMovie) "集数（电影可留空）" else "集数（必填）")
                            },
                            singleLine = true,
                            enabled = !busy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = state.filename,
                            onValueChange = onFilenameChange,
                            label = { Text("文件名（必填）") },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    LocalDanmuEditScope.Group -> {
                        OutlinedTextField(
                            value = state.title,
                            onValueChange = onTitleChange,
                            label = { Text("标题（必填）") },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = state.year?.toString().orEmpty(),
                                onValueChange = onYearChange,
                                label = { Text("年份（必填）") },
                                singleLine = true,
                                enabled = !busy,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = state.season?.toString().orEmpty(),
                                onValueChange = onSeasonChange,
                                label = { Text("季（必填）") },
                                singleLine = true,
                                enabled = !busy,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Column {
                            Text(
                                "类型（必填）",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LocalDanmuType.entries.forEach { option ->
                                    FilterChip(
                                        selected = state.type == option,
                                        onClick = { onTypeChange(option) },
                                        enabled = !busy,
                                        label = { Text(option.label) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 优先显示核心返回的失败原因，其次是本地校验提示。
                val feedback = state.errorMessage ?: validation
                if (!feedback.isNullOrBlank()) {
                    Text(
                        feedback,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.size(2.dp))
            }
        },
        confirmButton = {
            AppGlassButton(
                onClick = onSubmit,
                enabled = !busy && validation == null
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.size(6.dp))
                }
                Text(if (busy) "保存中…" else "保存")
            }
        },
        dismissButton = {
            AppGlassButton(onClick = onDismiss, enabled = !busy) {
                Text("取消")
            }
        }
    )
}
