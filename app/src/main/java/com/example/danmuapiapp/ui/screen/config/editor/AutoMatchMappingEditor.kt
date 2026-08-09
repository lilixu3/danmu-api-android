package com.example.danmuapiapp.ui.screen.config

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun AutoMatchMappingTableEditor(
    rememberKey: String,
    value: String,
    onValueChange: (String) -> Unit,
    platformOptions: List<String>
) {
    var rows by remember(rememberKey) {
        mutableStateOf(parseAutoMatchMappingDrafts(value).ifEmpty { listOf(AutoMatchMappingDraft()) })
    }
    var previewFileName by remember(rememberKey) { mutableStateOf("") }
    val platforms = remember(platformOptions) {
        platformOptions.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
    }
    val tableValidation = remember(value, platforms) {
        validateAutoMatchMappingTable(value, platforms)
    }
    val preview = remember(value, previewFileName, platforms) {
        previewAutoMatchMapping(value, previewFileName, platforms)
    }

    fun syncRows(next: List<AutoMatchMappingDraft>) {
        rows = next
        onValueChange(serializeAutoMatchMappingDrafts(next))
    }

    fun updateRow(index: Int, transform: (AutoMatchMappingDraft) -> AutoMatchMappingDraft) {
        if (index !in rows.indices) return
        val next = rows.toMutableList()
        next[index] = transform(next[index])
        syncRows(next)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEachIndexed { index, row ->
            key(index) {
                val rowValidation = validateAutoMatchDraft(row, platforms)
                val ranged = row.sourceEndEpisode.isNotBlank() || row.targetEndEpisode.isNotBlank()
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Route,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                "映射 ${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Text("范围", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.width(6.dp))
                            Switch(
                                checked = ranged,
                                onCheckedChange = { enabled ->
                                    updateRow(index) {
                                        if (enabled) {
                                            it.copy(
                                                sourceEndEpisode = it.sourceStartEpisode.ifBlank { "1" },
                                                targetEndEpisode = it.targetStartEpisode.ifBlank { "1" }
                                            )
                                        } else {
                                            it.copy(sourceEndEpisode = "", targetEndEpisode = "")
                                        }
                                    }
                                }
                            )
                            IconButton(onClick = {
                                val next = rows.filterIndexed { rowIndex, _ -> rowIndex != index }
                                syncRows(if (next.isEmpty()) listOf(AutoMatchMappingDraft()) else next)
                            }) {
                                Icon(Icons.Rounded.Close, "删除映射")
                            }
                        }

                        AutoMatchSideFields(
                            label = "来源",
                            title = row.sourceTitle,
                            season = row.sourceSeason,
                            startEpisode = row.sourceStartEpisode,
                            endEpisode = row.sourceEndEpisode,
                            ranged = ranged,
                            onTitleChange = { text -> updateRow(index) { it.copy(sourceTitle = text) } },
                            onSeasonChange = { text -> updateRow(index) { it.copy(sourceSeason = text.digitsOnly()) } },
                            onStartEpisodeChange = { text ->
                                updateRow(index) { it.copy(sourceStartEpisode = text.digitsOnly()) }
                            },
                            onEndEpisodeChange = { text ->
                                updateRow(index) { it.copy(sourceEndEpisode = text.digitsOnly()) }
                            }
                        )

                        AutoMatchSideFields(
                            label = "目标",
                            title = row.targetTitle,
                            season = row.targetSeason,
                            startEpisode = row.targetStartEpisode,
                            endEpisode = row.targetEndEpisode,
                            ranged = ranged,
                            onTitleChange = { text -> updateRow(index) { it.copy(targetTitle = text) } },
                            onSeasonChange = { text -> updateRow(index) { it.copy(targetSeason = text.digitsOnly()) } },
                            onStartEpisodeChange = { text ->
                                updateRow(index) { it.copy(targetStartEpisode = text.digitsOnly()) }
                            },
                            onEndEpisodeChange = { text ->
                                updateRow(index) { it.copy(targetEndEpisode = text.digitsOnly()) }
                            }
                        )

                        AutoMatchPlatformSelector(
                            value = row.targetPlatform,
                            options = platforms,
                            onValueChange = { platform ->
                                updateRow(index) { it.copy(targetPlatform = platform) }
                            }
                        )

                        if (!row.isEmpty) {
                            ValidationLine(rowValidation)
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { syncRows(rows + AutoMatchMappingDraft()) }) {
                Icon(Icons.Rounded.Add, null, Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("新增映射")
            }
            TextButton(onClick = {
                rows = parseAutoMatchMappingDrafts(value).ifEmpty { listOf(AutoMatchMappingDraft()) }
            }) {
                Text("重新解析")
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("映射预览", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = previewFileName,
                    onValueChange = { previewFileName = it },
                    label = { Text("视频文件名") },
                    placeholder = { Text("永生.S05E03.1080p.mkv") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (previewFileName.isNotBlank()) {
                    val previewMessage = when {
                        !tableValidation.valid -> tableValidation.message
                        preview != null -> "${preview.sourceLabel}  ->  ${preview.targetLabel}"
                        else -> "未命中映射规则"
                    }
                    Text(
                        text = previewMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (preview != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        OutlinedTextField(
            value = value,
            onValueChange = { raw ->
                onValueChange(raw)
                rows = parseAutoMatchMappingDrafts(raw).ifEmpty { listOf(AutoMatchMappingDraft()) }
            },
            label = { Text("原始值（高级）") },
            singleLine = false,
            minLines = 2,
            maxLines = 5,
            isError = !tableValidation.valid,
            supportingText = {
                Text(tableValidation.message.ifBlank { "未配置映射" })
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AutoMatchSideFields(
    label: String,
    title: String,
    season: String,
    startEpisode: String,
    endEpisode: String,
    ranged: Boolean,
    onTitleChange: (String) -> Unit,
    onSeasonChange: (String) -> Unit,
    onStartEpisodeChange: (String) -> Unit,
    onEndEpisodeChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text(if (label == "目标") "目标标题（可含年份/类型）" else "标题") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AutoMatchNumberField("季", season, onSeasonChange, Modifier.weight(1f))
            AutoMatchNumberField("起始集", startEpisode, onStartEpisodeChange, Modifier.weight(1f))
            if (ranged) {
                AutoMatchNumberField("结束集", endEpisode, onEndEpisodeChange, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AutoMatchNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    )
}

@Composable
private fun AutoMatchPlatformSelector(
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("目标平台", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(value.ifBlank { "自动" }, maxLines = 1)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.ExpandMore, null, Modifier.size(17.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("自动") },
                    onClick = {
                        expanded = false
                        onValueChange("")
                    }
                )
                options.forEach { platform ->
                    DropdownMenuItem(
                        text = { Text(platform) },
                        onClick = {
                            expanded = false
                            onValueChange(platform)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ValidationLine(validation: AutoMatchMappingValidation) {
    val color = if (validation.valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (validation.valid) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = color
        )
        Text(validation.message, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

private fun String.digitsOnly(): String = filter(Char::isDigit).take(5)
