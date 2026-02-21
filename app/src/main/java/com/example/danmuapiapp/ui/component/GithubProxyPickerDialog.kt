package com.example.danmuapiapp.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.GithubProxyOption

@Composable
fun GithubProxyPickerDialog(
    title: String,
    subtitle: String,
    options: List<GithubProxyOption>,
    selectedId: String,
    testingIds: Set<String>,
    resultMap: Map<String, Long>,
    onSelect: (String) -> Unit,
    onRetest: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "使用并继续"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                options.forEach { option ->
                    val latency = resultMap[option.id]
                    val latencyText = when {
                        option.id in testingIds -> "测速中..."
                        latency == null -> "未测速"
                        latency >= 0 -> "${latency} ms"
                        else -> "超时"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedId == option.id,
                                onClick = { onSelect(option.id) }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedId == option.id,
                            onClick = { onSelect(option.id) }
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelect(option.id) }
                                .padding(start = 4.dp)
                        ) {
                            Text(
                                text = option.name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = latencyText,
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    option.id in testingIds -> MaterialTheme.colorScheme.primary
                                    latency == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                    latency >= 0 -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRetest) { Text("重新测速") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

