package com.example.danmuapiapp.ui.screen.core

import com.example.danmuapiapp.ui.component.AppGlassSurface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.CorePullRequest
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton

@Composable
internal fun PullRequestWorkspaceSummary(
    installedNumbers: List<Int>,
    selectedNumbers: List<Int>
) {
    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    "当前组合",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (installedNumbers.isEmpty()) "未叠加 PR" else installedNumbers.joinToString(" + ") { "#$it" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (installedNumbers.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            VerticalDivider(
                modifier = Modifier.height(34.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Column(
                modifier = Modifier.width(82.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    "待合并",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (selectedNumbers.isEmpty()) "0 个 PR" else "${selectedNumbers.size} 个 PR",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selectedNumbers.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
internal fun SelectedPullRequestQueue(
    pullRequests: List<CorePullRequest>,
    enabled: Boolean,
    onMove: (Int, Int) -> Unit,
    onRemove: (CorePullRequest) -> Unit,
    onOpenDetails: (CorePullRequest) -> Unit
) {
    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.CallMerge,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("合并队列", style = MaterialTheme.typography.titleSmall)
                    Text(
                        pullRequests.joinToString("  ") { "#${it.number}" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "${pullRequests.size} 个",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            pullRequests.forEachIndexed { index, pullRequest ->
                SelectedPullRequestRow(
                    pullRequest = pullRequest,
                    position = index + 1,
                    canMoveUp = index > 0,
                    canMoveDown = index < pullRequests.lastIndex,
                    enabled = enabled,
                    onMoveUp = { onMove(index, -1) },
                    onMoveDown = { onMove(index, 1) },
                    onRemove = { onRemove(pullRequest) },
                    onOpenDetails = { onOpenDetails(pullRequest) }
                )
                if (index < pullRequests.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 48.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedPullRequestRow(
    pullRequest: CorePullRequest,
    position: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    enabled: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onOpenDetails: () -> Unit
) {
    Row(
        modifier = Modifier.padding(start = 10.dp, top = 7.dp, bottom = 7.dp, end = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppGlassSurface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                position.toString(),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.width(9.dp))
        Column(
            modifier = Modifier.weight(1f).clickable(onClick = onOpenDetails),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                "#${pullRequest.number} ${pullRequest.title}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                pullRequest.headSha.take(7),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AppGlassIconButton(
            onClick = onMoveUp,
            enabled = enabled && canMoveUp,
            size = 32.dp
        ) {
            Icon(Icons.Rounded.ArrowUpward, "上移 PR #${pullRequest.number}", Modifier.size(18.dp))
        }
        AppGlassIconButton(
            onClick = onMoveDown,
            enabled = enabled && canMoveDown,
            size = 32.dp
        ) {
            Icon(Icons.Rounded.ArrowDownward, "下移 PR #${pullRequest.number}", Modifier.size(18.dp))
        }
        AppGlassIconButton(
            onClick = onRemove,
            enabled = enabled,
            size = 32.dp,
            contentColor = MaterialTheme.colorScheme.error
        ) {
            Icon(Icons.Rounded.DeleteOutline, "移除 PR #${pullRequest.number}", Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun PullRequestPagination(
    page: Int,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AppGlassButton(
            onClick = onPrevious,
            enabled = canGoPrevious,
            modifier = Modifier.weight(1f).height(42.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            Icon(Icons.AutoMirrored.Rounded.NavigateBefore, null, Modifier.size(18.dp))
            Spacer(Modifier.width(3.dp))
            Text("上一页")
        }
        Text(
            text = "第 $page 页",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AppGlassButton(
            onClick = onNext,
            enabled = canGoNext,
            modifier = Modifier.weight(1f).height(42.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            Text("下一页")
            Spacer(Modifier.width(3.dp))
            Icon(Icons.AutoMirrored.Rounded.NavigateNext, null, Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun PullRequestBuildBar(
    selectedPullRequests: List<CorePullRequest>,
    isBuilding: Boolean,
    isActivating: Boolean,
    isPrivateRepository: Boolean,
    onBuild: () -> Unit
) {
    val busy = isBuilding || isActivating
    AppGlassSurface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = when {
                        busy -> "PR 组合处理中"
                        selectedPullRequests.isEmpty() -> "尚未选择 PR"
                        else -> "${selectedPullRequests.size} 个 PR 已加入队列"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        isPrivateRepository -> "私有仓库仅可查看"
                        selectedPullRequests.isEmpty() -> "当前组合保持不变"
                        else -> selectedPullRequests.joinToString(" + ") { "#${it.number}" }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            AppGlassButton(
                onClick = onBuild,
                enabled = selectedPullRequests.isNotEmpty() && !busy && !isPrivateRepository,
                modifier = Modifier.height(44.dp),
                tint = MaterialTheme.colorScheme.primary,
                contentPadding = PaddingValues(horizontal = 15.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.CallMerge, null, Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text(if (selectedPullRequests.isEmpty()) "合并 PR" else "合并 ${selectedPullRequests.size} 个")
            }
        }
    }
}
