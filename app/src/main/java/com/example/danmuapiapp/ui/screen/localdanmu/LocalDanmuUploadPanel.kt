package com.example.danmuapiapp.ui.screen.localdanmu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.data.repository.LocalDanmuResourceKey
import com.example.danmuapiapp.data.repository.LocalDanmuUploadHistoryStore
import com.example.danmuapiapp.domain.model.LocalDanmuType
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton
import java.util.Calendar

@Composable
internal fun LocalDanmuUploadPanel(
    state: LocalDanmuUploadUiState,
    recentFile: LocalDanmuUploadHistoryStore.RecentFile?,
    errorMessage: String?,
    allFilesAccessGranted: Boolean,
    onPickFile: () -> Unit,
    onReuseRecent: () -> Unit,
    onOpenDirectoryBrowser: () -> Unit,
    onImportMultiple: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onDismissError: () -> Unit,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
    onTypeChange: (LocalDanmuType) -> Unit,
    onSeasonChange: (String) -> Unit,
    onEpisodeChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val validation = when {
        !state.hasFile -> "请选择弹幕文件"
        state.isPreparing -> null
        else -> LocalDanmuResourceKey.validateUpload(
            title = state.title,
            year = state.year,
            type = state.type,
            season = state.season,
            episode = state.episode,
            currentYear = currentYear
        )
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppGlassIconButton(
                    onClick = onBack,
                    enabled = !state.isUploading,
                    size = 36.dp
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text("上传本地弹幕", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "自动解析文件名，可手动修正后上传",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AppGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                state.displayName.ifBlank { "尚未选择文件" },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                state.sizeBytes?.takeIf { it > 0L }?.let(::formatLocalDanmuSize)
                                    ?: "支持 XML / JSON / ASS / SSA / CSV / TXT，最大 10 MB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AppGlassButton(
                            onClick = onPickFile,
                            enabled = !state.isUploading
                        ) {
                            if (state.isPreparing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.UploadFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Spacer(Modifier.size(6.dp))
                            Text(if (state.hasFile) "重新选择" else "选择文件")
                        }
                    }
                }

                if (!state.isUploading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (allFilesAccessGranted) {
                                    "目录直读已开启：可直接浏览弹幕下载目录"
                                } else {
                                    "开启「所有文件访问」后可直接浏览弹幕目录，不再每次授权"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (allFilesAccessGranted) {
                            AppGlassButton(
                                onClick = onOpenDirectoryBrowser,
                                enabled = !state.isPreparing
                            ) {
                                Icon(
                                    Icons.Rounded.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.size(6.dp))
                                Text("目录浏览")
                            }
                        } else {
                            AppGlassButton(
                                onClick = onRequestAllFilesAccess,
                                enabled = !state.isPreparing
                            ) {
                                Icon(
                                    Icons.Rounded.LockOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.size(6.dp))
                                Text("去开启")
                            }
                        }
                        AppGlassButton(
                            onClick = onImportMultiple,
                            enabled = !state.isPreparing
                        ) {
                            Icon(
                                Icons.Rounded.Upload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.size(6.dp))
                            Text("批量导入")
                        }
                    }
                }

                if (!state.hasFile && !state.isUploading && recentFile != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "上次：${recentFile.displayName.ifBlank { "已选文件" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        AppGlassButton(
                            onClick = onReuseRecent,
                            enabled = !state.isPreparing
                        ) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.size(6.dp))
                            Text("重新使用")
                        }
                    }
                }

                OutlinedTextField(
                    value = state.title,
                    onValueChange = onTitleChange,
                    label = { Text("标题（必填）") },
                    placeholder = { Text("自动从文件名解析") },
                    singleLine = true,
                    enabled = !state.isUploading,
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
                        enabled = !state.isUploading,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Column(modifier = Modifier.weight(1.2f)) {
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
                                    enabled = !state.isUploading,
                                    label = { Text(option.label) }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = state.season?.toString().orEmpty(),
                        onValueChange = onSeasonChange,
                        label = {
                            Text(if (state.type == LocalDanmuType.Tv) "季（必填）" else "季（可选）")
                        },
                        singleLine = true,
                        enabled = !state.isUploading,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = state.episode?.toString().orEmpty(),
                        onValueChange = onEpisodeChange,
                        label = {
                            Text(if (state.type == LocalDanmuType.Tv) "集（必填）" else "集（可选）")
                        },
                        singleLine = true,
                        enabled = !state.isUploading,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    "支持格式：XML、JSON、ASS、SSA、CSV、TXT。同一季同一集重复上传会覆盖原文件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.notes.isNotEmpty()) {
                    Text(
                        state.notes.joinToString("；"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (validation != null) {
                    Text(
                        validation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (errorMessage != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        AppGlassButton(onClick = onDismissError) { Text("知道了") }
                    }
                }
                if (state.isUploading) {
                    if (state.progress >= 0.99f) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { state.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            AppGlassButton(
                onClick = onSubmit,
                enabled = validation == null && !state.isUploading && !state.isPreparing,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp)
                    .height(50.dp)
            ) {
                if (state.isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Rounded.UploadFile,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.size(7.dp))
                Text(if (state.isUploading) "正在上传…" else "上传并解析")
            }
        }
    }
}
