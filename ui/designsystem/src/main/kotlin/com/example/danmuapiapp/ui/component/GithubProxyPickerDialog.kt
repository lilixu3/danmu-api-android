package com.example.danmuapiapp.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.GithubProxyOption
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassPrimaryButton

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
    AppDialog(
        onDismissRequest = onDismiss,
        style = AppDialogStyle.Selection,
        tone = AppDialogTone.Info,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                options.forEach { option ->
                    val latency = resultMap[option.id]
                    val optionSelected = selectedId == option.id
                    val latencyText = when {
                        option.id in testingIds -> "测速中..."
                        latency == null -> "未测速"
                        latency >= 0 -> "${latency} ms"
                        else -> "超时"
                    }

                    AppDialogOption(
                        selected = optionSelected,
                        onClick = { onSelect(option.id) }
                    ) {
                        RadioButton(
                            selected = optionSelected,
                            onClick = { onSelect(option.id) }
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
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
                                    optionSelected -> MaterialTheme.colorScheme.onPrimaryContainer
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
            AppGlassPrimaryButton(onClick = onConfirm) {
                Text(confirmText)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppGlassButton(onClick = onRetest) { Text("重新测速") }
                AppGlassButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}
