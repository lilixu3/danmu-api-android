package com.example.danmuapiapp.desktop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.desktop.node.GithubProxyCatalog

@Composable
fun GithubRoutePickerDialog(
    title: String,
    description: String,
    selectedId: String,
    onSelected: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    DesktopDialogFrame(
        spec = DesktopDialogSpec(
            title = title,
            description = description,
            tone = DesktopDialogTone.Info,
            dismissOnClickOutside = false,
        ),
        onDismissRequest = onDismiss,
        leadingIcon = DesktopIcons.Link,
        modifier = Modifier.fillMaxWidth(),
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GithubProxyCatalog.options.forEach { option ->
                    DesktopSurface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (option.id == selectedId) {
                            androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            androidx.compose.material3.MaterialTheme.colorScheme.surface
                        },
                        shape = DesktopTokens.ItemShape,
                        onClick = { onSelected(option.id) },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RadioButton(
                                selected = option.id == selectedId,
                                onClick = { onSelected(option.id) },
                            )
                            Column {
                                Text(option.label, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (option.isOriginal) "直接连接 api.github.com / codeload.github.com"
                                    else "使用该线路提供的候选地址，不会把 GitHub Token 发给镜像",
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        actions = {
            DesktopDialogButton(
                action = DesktopDialogAction("取消"),
                onClick = onDismiss,
            )
            DesktopDialogButton(
                action = DesktopDialogAction("确认并继续", isPrimary = true),
                onClick = onConfirm,
            )
        },
    )
}
