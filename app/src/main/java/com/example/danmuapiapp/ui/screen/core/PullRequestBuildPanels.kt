package com.example.danmuapiapp.ui.screen.core

import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppGlassPrimaryButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.CoreDownloadProgress
import com.example.danmuapiapp.domain.model.CorePullRequest
import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.AppDialogStyle
import com.example.danmuapiapp.ui.component.AppDialogTone
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton

@Composable
internal fun PullRequestBuildProgress(
    progress: CoreDownloadProgress,
    isActivating: Boolean,
    onCancel: () -> Unit,
    canCancel: Boolean
) {
    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.2.dp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (isActivating) "正在切换并验证核心" else "正在构建 PR 组合",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        if (isActivating) "等待服务返回启动结果" else progress.stageText.ifBlank { "准备本地 Git 工作区" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canCancel) {
                    AppGlassButton(onClick = onCancel) { Text("取消") }
                }
            }
            progress.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
internal fun BuildPullRequestStackDialog(
    pullRequests: List<CorePullRequest>,
    activateAfterInstall: Boolean,
    onActivateChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val firstContributorPullRequests = pullRequests.filter { it.firstContribution != null }
    AppDialog(
        onDismissRequest = onDismiss,
        style = AppDialogStyle.Confirm,
        tone = AppDialogTone.Warning,
        icon = { Icon(Icons.Rounded.Warning, null) },
        title = { Text("构建本地 PR 组合") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 390.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("PR 中的代码将在本机执行。构建只读取 GitHub，不会修改或合并远程仓库。")
                if (firstContributorPullRequests.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                "包含首次贡献者的 PR",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                firstContributorPullRequests.joinToString("\n") { pullRequest ->
                                    "#${pullRequest.number} @${pullRequest.author} · " +
                                        requireNotNull(pullRequest.firstContribution).label
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                AppGlassSurface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        pullRequests.forEachIndexed { index, pullRequest ->
                            Text(
                                "${index + 1}. #${pullRequest.number} ${pullRequest.title}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("安装后切换", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "服务运行时会安全重启并保留恢复点",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = activateAfterInstall, onCheckedChange = onActivateChange)
                }
            }
        },
        confirmButton = {
            AppGlassPrimaryButton(onClick = onConfirm) { Text("开始构建") }
        },
        dismissButton = {
            AppGlassButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
