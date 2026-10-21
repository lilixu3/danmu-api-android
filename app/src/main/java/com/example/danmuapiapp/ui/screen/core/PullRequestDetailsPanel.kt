package com.example.danmuapiapp.ui.screen.core

import com.example.danmuapiapp.ui.component.AppGlassSurface
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.CorePullRequest
import com.example.danmuapiapp.domain.model.CorePullRequestFilePage
import com.example.danmuapiapp.domain.model.CorePullRequestInclusion
import com.example.danmuapiapp.domain.model.CorePullRequestStatus
import com.example.danmuapiapp.domain.model.effectiveStatus
import com.example.danmuapiapp.ui.component.AppModalPanel
import com.example.danmuapiapp.ui.component.SimpleMarkdownText
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton
import com.example.danmuapiapp.ui.component.rememberSimpleMarkdownState

@Composable
internal fun PullRequestDetailsPanel(
    pullRequest: CorePullRequest,
    locallyMerged: Boolean,
    filePage: CorePullRequestFilePage?,
    isLoading: Boolean,
    error: String?,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onClose: () -> Unit
) {
    val expandedFiles = remember(
        pullRequest.baseRepository,
        pullRequest.number
    ) {
        mutableStateMapOf<String, Boolean>()
    }
    val retainedMarkdownState = rememberSimpleMarkdownState(pullRequest.body)
    AppModalPanel(
        onDismissRequest = onClose,
        maxWidth = 980.dp,
        expanded = true,
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppGlassIconButton(onClick = onClose, size = 40.dp) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回 PR 列表")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "PR #${pullRequest.number}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${pullRequest.baseRef} <- ${pullRequest.headLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AppGlassIconButton(onClick = onClose, size = 40.dp) {
                    Icon(Icons.Rounded.Close, "关闭")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "pr-summary", contentType = "pr-summary") {
                    PullRequestDetailsSummary(
                        pullRequest = pullRequest,
                        locallyMerged = locallyMerged
                    )
                }
                if (pullRequest.body.isNotBlank()) {
                    item(key = "pr-description", contentType = "pr-description") {
                        AppGlassSurface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            glassAlpha = 0.78f
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    "变更说明",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                SimpleMarkdownText(
                                    markdown = pullRequest.body,
                                    modifier = Modifier.fillMaxWidth(),
                                    retainedMarkdownState = retainedMarkdownState
                                )
                            }
                        }
                    }
                }
                item(key = "pr-metrics", contentType = "pr-metrics") {
                    PullRequestDiffMetrics(pullRequest = pullRequest, filePage = filePage)
                }
                item(key = "files-title", contentType = "diff-heading") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "变更文件",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        filePage?.let {
                            Text(
                                "第 ${it.page} 页 · 每页 15 个",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (isLoading) {
                    item(key = "details-progress") {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                if (error != null) {
                    item(key = "details-error") {
                        Text(
                            error,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                items(
                    items = filePage?.files.orEmpty(),
                    key = { "${it.status}:${it.path}" },
                    contentType = { "core-diff-file" }
                ) { file ->
                    CoreFileDiff(
                        file = file,
                        expanded = expandedFiles[file.path] == true,
                        onExpandedChange = { expanded ->
                            if (expanded) {
                                expandedFiles[file.path] = true
                            } else {
                                expandedFiles.remove(file.path)
                            }
                        }
                    )
                }
                if (!isLoading && error == null && filePage?.files.isNullOrEmpty()) {
                    item(key = "no-files") {
                        Text(
                            "没有可显示的文件变更",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                filePage?.let { page ->
                    item(key = "file-pagination") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AppGlassButton(
                                onClick = onPreviousPage,
                                enabled = page.hasPreviousPage && !isLoading,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.NavigateBefore, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("上一页")
                            }
                            AppGlassButton(
                                onClick = onNextPage,
                                enabled = page.hasNextPage && !isLoading,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("下一页")
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Rounded.NavigateNext, null, Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PullRequestDetailsSummary(
    pullRequest: CorePullRequest,
    locallyMerged: Boolean
) {
    val locallyIncluded = locallyMerged ||
        pullRequest.currentCoreInclusion == CorePullRequestInclusion.LocalMerge
    val effectiveStatus = pullRequest.effectiveStatus(locallyIncluded)
    val statusLabel = when {
        locallyIncluded -> "本地已并入"
        effectiveStatus == CorePullRequestStatus.Merged -> when (pullRequest.currentCoreInclusion) {
            CorePullRequestInclusion.Included -> "GitHub 已合并 · 当前核心已包含"
            CorePullRequestInclusion.NotIncluded -> "GitHub 已合并 · 当前核心未包含"
            CorePullRequestInclusion.LocalMerge -> "本地已并入"
            CorePullRequestInclusion.Unknown -> "GitHub 已合并 · 本地状态待确认"
        }
        effectiveStatus == CorePullRequestStatus.Closed -> "已关闭"
        pullRequest.draft -> "草稿"
        else -> "开放"
    }
    val statusColor = when (effectiveStatus) {
        CorePullRequestStatus.Open -> if (pullRequest.draft) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.primary
        }
        CorePullRequestStatus.Merged -> MaterialTheme.colorScheme.tertiary
        CorePullRequestStatus.Closed -> MaterialTheme.colorScheme.error
    }
    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    statusLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor
                )
                Text(
                    pullRequest.headSha.take(7),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                pullRequest.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${pullRequest.author} · ${formatPullRequestTime(pullRequest.updatedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            pullRequest.firstContribution?.let { firstContribution ->
                FirstContributorBadge(firstContribution)
            }
        }
    }
}

@Composable
private fun PullRequestDiffMetrics(
    pullRequest: CorePullRequest,
    filePage: CorePullRequestFilePage?
) {
    val visibleAdditions = pullRequest.additions ?: filePage?.files?.sumOf { it.additions } ?: 0
    val visibleDeletions = pullRequest.deletions ?: filePage?.files?.sumOf { it.deletions } ?: 0
    val visibleFiles = pullRequest.changedFiles ?: filePage?.files?.size ?: 0
    val hasRepositoryTotals = pullRequest.changedFiles != null &&
        pullRequest.additions != null &&
        pullRequest.deletions != null
    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DiffMetric(
                Icons.Rounded.Code,
                if (hasRepositoryTotals) "$visibleFiles 个文件" else "本页 $visibleFiles 个文件",
                MaterialTheme.colorScheme.primary
            )
            DiffMetric(Icons.Rounded.Add, "+$visibleAdditions", diffAddedColor())
            DiffMetric(Icons.Rounded.DeleteOutline, "-$visibleDeletions", MaterialTheme.colorScheme.error)
        }
    }
}
