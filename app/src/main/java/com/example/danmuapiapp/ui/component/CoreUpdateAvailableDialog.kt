package com.example.danmuapiapp.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.CoreRemoteCommit
import com.example.danmuapiapp.domain.model.formatCoreVersionValue

internal fun shouldOfferCoreUpdateActions(
    hasVersionUpdate: Boolean,
    hasCheckError: Boolean,
    sourceMismatch: Boolean,
    sourceUnknownLegacy: Boolean
): Boolean {
    return hasVersionUpdate &&
        !hasCheckError &&
        !sourceMismatch &&
        !sourceUnknownLegacy
}

@Composable
internal fun CoreUpdateAvailableDialog(
    variantLabel: String,
    currentVersion: String?,
    latestVersion: String?,
    remoteCommit: CoreRemoteCommit?,
    onDismiss: () -> Unit,
    onShowDetails: () -> Unit,
    onUpdateNow: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        style = AppDialogStyle.Status,
        tone = AppDialogTone.Info,
        icon = { Icon(Icons.Rounded.SystemUpdate, null) },
        title = { Text("发现核心更新") },
        supportingText = { Text(variantLabel) },
        text = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    VersionColumn(
                        label = "当前版本",
                        version = currentVersion,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    VersionColumn(
                        label = "最新版本",
                        version = latestVersion,
                        modifier = Modifier.weight(1f),
                        highlight = true
                    )
                }
            }

            remoteCommit?.let { commit ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            modifier = Modifier.size(19.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "${commit.shortSha} · 远程最新提交",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = commit.title.ifBlank { "提交说明暂不可用" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onShowDetails, shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Rounded.Visibility, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("更新详情")
            }
        },
        actions = {
            Button(onClick = onUpdateNow, shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("立即更新")
            }
        }
    )
}

@Composable
private fun VersionColumn(
    label: String,
    version: String?,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatCoreVersionValue(version),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (highlight) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
