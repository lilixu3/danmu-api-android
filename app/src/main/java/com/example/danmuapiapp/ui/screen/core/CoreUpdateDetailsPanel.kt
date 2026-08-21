package com.example.danmuapiapp.ui.screen.core

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.domain.model.CoreInfo
import com.example.danmuapiapp.domain.model.CoreRemoteCommit
import com.example.danmuapiapp.domain.model.CoreUpdateComparison
import com.example.danmuapiapp.domain.model.CoreUpdateRelation
import com.example.danmuapiapp.domain.model.CoreVariantDisplayNames
import com.example.danmuapiapp.domain.model.formatCoreVersionValue
import com.example.danmuapiapp.ui.component.AppModalPanel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun CoreUpdateDetailsPanel(
    viewModel: CoreViewModel,
    displayNames: CoreVariantDisplayNames
) {
    val variant = viewModel.updateDetailsVariant ?: return
    val coreInfoList by viewModel.coreInfoList.collectAsStateWithLifecycle()
    val info = coreInfoList.firstOrNull { it.variant == variant }
    CoreUpdateDetailsPanel(
        displayName = displayNames.resolve(variant),
        info = info,
        comparison = viewModel.updateComparison,
        isLoading = viewModel.isLoadingUpdateComparison,
        errorMessage = viewModel.updateComparisonError,
        onRetry = viewModel::retryUpdateComparison,
        onDismiss = viewModel::dismissUpdateDetails,
        onUpdateNow = { viewModel.doUpdate(variant) }
    )
}

@Composable
internal fun CoreUpdateDetailsPanel(
    displayName: String,
    info: CoreInfo?,
    comparison: CoreUpdateComparison?,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onUpdateNow: () -> Unit
) {
    val canApply = comparison?.relation?.hasRemoteUpdate == true ||
        (comparison?.relation == CoreUpdateRelation.Unknown && info?.hasVersionUpdate == true)

    AppModalPanel(
        onDismissRequest = onDismiss,
        maxWidth = 980.dp,
        expanded = true,
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.SystemUpdate,
                            null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${displayName}更新详情",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${formatCoreVersionValue(info?.version)} → ${formatCoreVersionValue(info?.remoteVersion ?: info?.availableVersion)}" +
                            info?.remoteCommit?.shortSha?.let { " · $it" }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Close, "关闭更新详情")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            when {
                isLoading -> Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                errorMessage != null -> Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        errorMessage,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.size(12.dp))
                    OutlinedButton(onClick = onRetry) { Text("重新加载") }
                }
                comparison != null -> UpdateComparisonContent(comparison)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onDismiss) {
                    Text(if (canApply) "稍后" else "关闭")
                }
                if (canApply) {
                    Spacer(Modifier.width(10.dp))
                    Button(onClick = onUpdateNow) {
                        Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("立即更新")
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.UpdateComparisonContent(
    comparison: CoreUpdateComparison
) {
    val expandedFiles = remember(comparison.localCommitSha, comparison.remoteCommit.sha) {
        mutableStateMapOf<String, Boolean>()
    }
    LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "summary") {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("变更总结", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(comparison.summary.headline, style = MaterialTheme.typography.bodyLarge)
                if (comparison.summary.affectedAreas.isNotEmpty()) {
                    Text(
                        comparison.summary.affectedAreas.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DiffMetric(Icons.Rounded.History, "${comparison.totalCommits} 个提交", MaterialTheme.colorScheme.primary)
                        DiffMetric(Icons.Rounded.Code, "${comparison.changedFiles} 个文件", MaterialTheme.colorScheme.primary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DiffMetric(Icons.Rounded.Add, "+${comparison.additions}", diffAddedColor())
                        DiffMetric(Icons.Rounded.DeleteOutline, "-${comparison.deletions}", MaterialTheme.colorScheme.error)
                    }
                }
                if (comparison.isTruncated) {
                    Text(
                        "变更数量较多，GitHub 仅返回了本次可展示的部分详情。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        item(key = "remote") {
            RemoteCommitSummary(comparison.remoteCommit)
        }

        if (comparison.summary.highlights.isNotEmpty()) {
            item(key = "highlights-title") {
                Text("主要提交", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(
                items = comparison.summary.highlights,
                key = { "highlight:$it" }
            ) { title ->
                Text("• $title", style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (comparison.commits.isNotEmpty()) {
            item(key = "commits-title") {
                Text("提交记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(
                items = comparison.commits,
                key = { "commit:${it.sha}" }
            ) { commit ->
                CommitRow(commit)
            }
        }

        item(key = "files-title") {
            Text(
                "代码变更详情",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (comparison.files.isEmpty()) {
            item(key = "files-empty") {
                Text(
                    "没有可显示的文件差异",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(
                items = comparison.files,
                key = { "file:${it.status}:${it.path}" }
            ) { file ->
                CoreFileDiff(
                    file = file,
                    expanded = expandedFiles[file.path] == true,
                    onExpandedChange = { expanded ->
                        if (expanded) expandedFiles[file.path] = true else expandedFiles.remove(file.path)
                    }
                )
            }
        }
    }
}

@Composable
private fun RemoteCommitSummary(commit: CoreRemoteCommit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("远程最新提交", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(commit.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(
                "${commit.shortSha} · ${commit.author.ifBlank { "未知作者" }} · ${formatUpdateCommitTime(commit.committedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CommitRow(commit: CoreRemoteCommit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            commit.shortSha,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(commit.title, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${commit.author} · ${formatUpdateCommitTime(commit.committedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatUpdateCommitTime(raw: String): String {
    if (raw.isBlank()) return "时间未知"
    return runCatching {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.parse(raw))
    }.getOrDefault(raw.take(16).replace('T', ' '))
}
