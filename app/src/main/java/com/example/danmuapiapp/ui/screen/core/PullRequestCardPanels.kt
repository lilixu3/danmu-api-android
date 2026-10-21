package com.example.danmuapiapp.ui.screen.core

import com.example.danmuapiapp.ui.component.AppGlassSurface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.CorePullRequest
import com.example.danmuapiapp.domain.model.CorePullRequestInclusion
import com.example.danmuapiapp.domain.model.CorePullRequestStatus
import com.example.danmuapiapp.domain.model.PullRequestFirstContribution
import com.example.danmuapiapp.domain.model.canApplyToCurrentCore
import com.example.danmuapiapp.domain.model.effectiveStatus

@Composable
internal fun PullRequestCard(
    pullRequest: CorePullRequest,
    selectedPosition: Int?,
    alreadyIncluded: Boolean,
    showOpenState: Boolean,
    enabled: Boolean,
    enrichmentKey: Long,
    enrichmentEnabled: Boolean,
    onVisible: () -> Unit,
    onToggle: () -> Unit,
    onOpenDetails: () -> Unit
) {
    LaunchedEffect(enrichmentKey, enrichmentEnabled, pullRequest.number) {
        if (enrichmentEnabled) onVisible()
    }
    val selected = selectedPosition != null
    val locallyIncluded = alreadyIncluded ||
        pullRequest.currentCoreInclusion == CorePullRequestInclusion.LocalMerge
    val effectiveStatus = pullRequest.effectiveStatus(locallyIncluded)
    val selectable = pullRequest.canApplyToCurrentCore(alreadyIncluded)
    val statusBadge = when {
        selectedPosition != null -> "队列 $selectedPosition" to StatusBadgeTone.Open
        locallyIncluded -> "本地已并入" to StatusBadgeTone.Merged
        effectiveStatus == CorePullRequestStatus.Merged -> when (pullRequest.currentCoreInclusion) {
            CorePullRequestInclusion.Included -> "当前版本已包含" to StatusBadgeTone.Merged
            CorePullRequestInclusion.NotIncluded -> "已合并 · 未包含" to StatusBadgeTone.Warning
            else -> "已合并 · 待确认" to StatusBadgeTone.Merged
        }
        effectiveStatus == CorePullRequestStatus.Closed -> "已关闭" to StatusBadgeTone.Closed
        pullRequest.draft -> "草稿" to StatusBadgeTone.Warning
        showOpenState -> "开放" to StatusBadgeTone.Open
        else -> null
    }
    AppGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetails),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 9.dp, end = 8.dp, bottom = 9.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                enabled = enabled && selectable,
                modifier = Modifier.size(40.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "#${pullRequest.number}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    statusBadge?.let { (label, tone) -> StatusBadge(label, tone) }
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Rounded.ChevronRight,
                        "查看 PR #${pullRequest.number} 变更详情",
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    pullRequest.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                pullRequest.firstContribution?.let { firstContribution ->
                    FirstContributorBadge(firstContribution)
                }
                pullRequest.body.lineSequence().firstOrNull { it.isNotBlank() }?.let { summary ->
                    Text(
                        summary.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "${pullRequest.author} · ${formatPullRequestTime(pullRequest.updatedAt)} · ${pullRequest.headSha.take(7)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun FirstContributorBadge(firstContribution: PullRequestFirstContribution) {
    AppGlassSurface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                Icons.Rounded.PersonAdd,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                firstContribution.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

private enum class StatusBadgeTone {
    Open,
    Merged,
    Closed,
    Warning
}

@Composable
private fun StatusBadge(text: String, tone: StatusBadgeTone) {
    val backgroundColor = when (tone) {
        StatusBadgeTone.Open -> MaterialTheme.colorScheme.primaryContainer
        StatusBadgeTone.Merged -> MaterialTheme.colorScheme.tertiaryContainer
        StatusBadgeTone.Closed -> MaterialTheme.colorScheme.errorContainer
        StatusBadgeTone.Warning -> MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = when (tone) {
        StatusBadgeTone.Open -> MaterialTheme.colorScheme.onPrimaryContainer
        StatusBadgeTone.Merged -> MaterialTheme.colorScheme.onTertiaryContainer
        StatusBadgeTone.Closed -> MaterialTheme.colorScheme.onErrorContainer
        StatusBadgeTone.Warning -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    AppGlassSurface(
        shape = RoundedCornerShape(6.dp),
        color = backgroundColor
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}
