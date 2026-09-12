package com.example.danmuapiapp.ui.screen.localdanmu

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator as Progress
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.LocalDanmuType
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton

/**
 * 批量导入面板：可以一次选任意多个文件，逐个顺序上传；
 * 每个条目的元数据来自文件名自动解析，识别不准时可以就地修正。
 */
@Composable
internal fun LocalDanmuBatchPanel(
    state: LocalDanmuBatchState,
    errorMessage: String?,
    onBack: () -> Unit,
    onToggleSelected: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onUpdateMetadata: (Long, String, Int?, LocalDanmuType, Int?, Int?) -> Unit,
    onRun: () -> Unit,
    onCancel: () -> Unit,
    onRetryFailed: () -> Unit,
    onDismissError: () -> Unit
) {
    var editingId by remember { mutableStateOf<Long?>(null) }

    BackHandler { if (!state.isRunning) onBack() }

    Scaffold(
        containerColor = Color.Transparent,
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
                    .padding(top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppGlassIconButton(onClick = onBack, enabled = !state.isRunning, size = 36.dp) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("批量导入", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "共 ${state.items.size} 个文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.items.isEmpty()) {
                Text(
                    "没有待导入的文件，返回后重新选择。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 18.dp)
                )
                return@Scaffold
            }

            if (state.isRunning || state.total > 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (state.isRunning) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(
                        "进度 ${state.completed}/${state.total} · 成功 ${state.successCount} · 失败 ${state.failedCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            errorMessage?.let { error ->
                AppGlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        AppGlassButton(onClick = onDismissError) { Text("知道了") }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(items = state.items, key = { it.id }) { item ->
                    LocalDanmuBatchItemCard(
                        item = item,
                        editing = editingId == item.id,
                        enabled = !state.isRunning,
                        onToggleEdit = { editingId = if (editingId == item.id) null else item.id },
                        onToggleSelected = { onToggleSelected(item.id) },
                        onRemove = { onRemove(item.id) },
                        onUpdateMetadata = { title, year, type, season, episode ->
                            onUpdateMetadata(item.id, title, year, type, season, episode)
                        }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.failedCount > 0 && !state.isRunning) {
                    AppGlassButton(onClick = onRetryFailed) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("重试失败 ${state.failedCount}")
                    }
                }
                Spacer(Modifier.weight(1f))
                if (state.isRunning) {
                    AppGlassButton(onClick = onCancel) {
                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("取消导入")
                    }
                } else if (state.hasResult && state.pendingCount == 0) {
                    AppGlassButton(onClick = onBack) { Text("完成") }
                } else {
                    AppGlassButton(
                        onClick = onRun,
                        enabled = state.pendingCount > 0
                    ) {
                        Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("导入选中的 ${state.pendingCount} 个")
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalDanmuBatchItemCard(
    item: LocalDanmuBatchItem,
    editing: Boolean,
    enabled: Boolean,
    onToggleEdit: () -> Unit,
    onToggleSelected: () -> Unit,
    onRemove: () -> Unit,
    onUpdateMetadata: (String, Int?, LocalDanmuType, Int?, Int?) -> Unit
) {
    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            when (item.status) {
                LocalDanmuBatchStatus.Success -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                LocalDanmuBatchStatus.Failed -> MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    if (item.selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = "选择",
                    tint = if (item.selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .size(20.dp)
                        .then(if (enabled) Modifier.clickable(onClick = onToggleSelected) else Modifier)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${item.title} · ${item.episodeLabel} · ${
                            formatLocalDanmuSize(item.sizeBytes ?: 0L)
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    item.importedLabel?.let { label ->
                        Text(
                            "$label（重新导入会覆盖）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                BatchStatusChip(item)
                AppGlassIconButton(onClick = onToggleEdit, enabled = enabled, size = 32.dp) {
                    Icon(
                        if (editing) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = "编辑",
                        modifier = Modifier.size(16.dp)
                    )
                }
                AppGlassIconButton(onClick = onRemove, enabled = enabled, size = 32.dp) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "移除",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (item.progress in 0f..0.999f && item.status == LocalDanmuBatchStatus.Uploading) {
                Progress(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else if (item.status == LocalDanmuBatchStatus.Uploading) {
                LinearProgressIndicator(
                    progress = { item.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item.message.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (item.status) {
                        LocalDanmuBatchStatus.Failed -> MaterialTheme.colorScheme.error
                        LocalDanmuBatchStatus.Success -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            if (editing) {
                BatchMetadataEditor(item = item, enabled = enabled, onChange = onUpdateMetadata)
            }
        }
    }
}

@Composable
private fun BatchStatusChip(item: LocalDanmuBatchItem) {
    val (text, color) = when (item.status) {
        LocalDanmuBatchStatus.Ready -> item.status.label to MaterialTheme.colorScheme.onSurfaceVariant
        LocalDanmuBatchStatus.Uploading -> item.status.label to MaterialTheme.colorScheme.primary
        LocalDanmuBatchStatus.Success -> item.status.label to MaterialTheme.colorScheme.primary
        LocalDanmuBatchStatus.Failed -> item.status.label to MaterialTheme.colorScheme.error
        LocalDanmuBatchStatus.Skipped -> item.status.label to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
private fun BatchMetadataEditor(
    item: LocalDanmuBatchItem,
    enabled: Boolean,
    onChange: (String, Int?, LocalDanmuType, Int?, Int?) -> Unit
) {
    var title by remember(item.id, item.title) { mutableStateOf(item.title) }
    var year by remember(item.id, item.year) { mutableStateOf(item.year?.toString().orEmpty()) }
    var season by remember(item.id, item.season) { mutableStateOf(item.season?.toString().orEmpty()) }
    var episode by remember(item.id, item.episode) { mutableStateOf(item.episode?.toString().orEmpty()) }
    val type = item.type

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = {
                title = it
                onChange(it, year.toIntOrNull(), type, season.toIntOrNull(), episode.toIntOrNull())
            },
            label = { Text("标题") },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = year,
                onValueChange = {
                    year = it.filter(Char::isDigit).take(4)
                    onChange(title, year.toIntOrNull(), type, season.toIntOrNull(), episode.toIntOrNull())
                },
                label = { Text("年份") },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = season,
                onValueChange = {
                    season = it.filter(Char::isDigit).take(3)
                    onChange(title, year.toIntOrNull(), type, season.toIntOrNull(), episode.toIntOrNull())
                },
                label = { Text("季") },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = episode,
                onValueChange = {
                    episode = it.filter(Char::isDigit).take(4)
                    onChange(title, year.toIntOrNull(), type, season.toIntOrNull(), episode.toIntOrNull())
                },
                label = { Text("集") },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LocalDanmuType.entries.forEach { option ->
                FilterChip(
                    selected = type == option,
                    onClick = {
                        val nextSeason = if (option == LocalDanmuType.Movie) null else season.toIntOrNull() ?: 1
                        val nextEpisode = if (option == LocalDanmuType.Movie) null else episode.toIntOrNull() ?: 1
                        season = nextSeason?.toString().orEmpty()
                        episode = nextEpisode?.toString().orEmpty()
                        onChange(title, year.toIntOrNull(), option, nextSeason, nextEpisode)
                    },
                    enabled = enabled,
                    label = { Text(option.label) }
                )
            }
        }
        item.validationError?.let { error ->
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(2.dp))
    }
}
