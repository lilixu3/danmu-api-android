package com.example.danmuapiapp.ui.screen.apitest

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.DanmuDownloadFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ApiTestExportCard(
    insight: DanmuInsight,
    exporting: Boolean,
    onExport: (DanmuInsight, DanmuDownloadFormat) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var selectedFormatValue by rememberSaveable(
        insight.commentId,
        insight.episodeTitle,
        insight.exportTarget.toString()
    ) {
        mutableStateOf(DanmuDownloadFormat.Json.value)
    }
    val selectedFormat = DanmuDownloadFormat.fromValueOrNull(selectedFormatValue)
        ?: DanmuDownloadFormat.Json
    val selectedOption = ApiTestExportCatalog.options.first { it.format == selectedFormat }

    WorkbenchCard(
        title = "导出弹幕",
        subtitle = "选择后端输出格式并保存到设备"
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedOption.displayName,
                onValueChange = {},
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                readOnly = true,
                singleLine = true,
                label = { Text("导出格式") },
                supportingText = {
                    Text(
                        text = selectedFormat.value,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
                    )
                },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DanmuExportGroup.entries.forEachIndexed { groupIndex, group ->
                    if (groupIndex > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        )
                    }
                    Text(
                        text = group.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                    ApiTestExportCatalog.options
                        .filter { it.group == group }
                        .forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = option.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = option.format.value,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                trailingIcon = if (option.format == selectedFormat) {
                                    {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else {
                                    null
                                },
                                onClick = {
                                    selectedFormatValue = option.format.value
                                    expanded = false
                                }
                            )
                        }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onExport(insight, selectedFormat) },
            enabled = !exporting,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (exporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
                Text("正在准备导出")
            } else {
                Icon(Icons.Rounded.Download, null, Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text("导出 ${selectedOption.displayName}")
            }
        }
    }
}
