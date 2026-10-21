package com.example.danmuapiapp.ui.screen.home

import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.AppDialogStyle
import com.example.danmuapiapp.ui.component.AppDialogTone
import com.example.danmuapiapp.ui.component.AppModalPanel
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.AppAnnouncement
import com.example.danmuapiapp.domain.model.AnnouncementSeverity
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import kotlin.math.max

internal fun AppAnnouncement.dialogTone(): AppDialogTone {
    return when (severity) {
        AnnouncementSeverity.Info -> AppDialogTone.Info
        AnnouncementSeverity.Success -> AppDialogTone.Success
        AnnouncementSeverity.Warning -> AppDialogTone.Warning
        AnnouncementSeverity.Danger -> AppDialogTone.Danger
    }
}

internal fun AppAnnouncement.dialogIcon(): ImageVector {
    return when (severity) {
        AnnouncementSeverity.Info -> Icons.Rounded.SystemUpdate
        AnnouncementSeverity.Success -> Icons.Rounded.CheckCircle
        AnnouncementSeverity.Warning -> Icons.Rounded.ErrorOutline
        AnnouncementSeverity.Danger -> Icons.Rounded.ErrorOutline
    }
}

internal fun String.toAnnouncementPlainText(): String {
    return replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(Regex("[`#>*_\\[\\]]"), "")
        .replace(Regex("\\((https?://[^)]+)\\)"), "")
        .lines()
        .joinToString("\n") { line ->
            line.replace(Regex("[^\\S\\n]+"), " ").trim()
        }
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

internal fun AppAnnouncement.previewText(): String {
    val raw = if (contentPreview.isNotBlank()) contentPreview else contentMarkdown
    return raw.toAnnouncementPlainText().take(320)
}

internal fun AppAnnouncement.dialogBodyText(): String {
    val markdownBody = contentMarkdown.toAnnouncementPlainText()
    if (markdownBody.isNotBlank()) return markdownBody
    return contentPreview.toAnnouncementPlainText()
}

internal data class AnnouncementTonePalette(
    val iconContainer: Color,
    val iconTint: Color,
    val accent: Color,
    val statusTint: Color
)

@Composable
internal fun rememberAnnouncementTonePalette(tone: AppDialogTone): AnnouncementTonePalette {
    val c = MaterialTheme.colorScheme
    return when (tone) {
        AppDialogTone.Neutral -> AnnouncementTonePalette(
            iconContainer = c.surfaceContainerHighest,
            iconTint = c.onSurfaceVariant,
            accent = c.onSurfaceVariant,
            statusTint = c.surfaceContainerHigh
        )
        AppDialogTone.Brand -> AnnouncementTonePalette(
            iconContainer = c.primaryContainer,
            iconTint = c.primary,
            accent = c.primary,
            statusTint = c.primaryContainer
        )
        AppDialogTone.Success -> AnnouncementTonePalette(
            iconContainer = c.tertiaryContainer,
            iconTint = c.tertiary,
            accent = c.tertiary,
            statusTint = c.tertiaryContainer
        )
        AppDialogTone.Warning -> AnnouncementTonePalette(
            iconContainer = c.secondaryContainer,
            iconTint = c.onSecondaryContainer,
            accent = c.secondary,
            statusTint = c.secondaryContainer
        )
        AppDialogTone.Danger -> AnnouncementTonePalette(
            iconContainer = c.errorContainer,
            iconTint = c.error,
            accent = c.error,
            statusTint = c.errorContainer
        )
        AppDialogTone.Info -> AnnouncementTonePalette(
            iconContainer = c.secondaryContainer,
            iconTint = c.secondary,
            accent = c.secondary,
            statusTint = c.secondaryContainer
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
internal fun AnnouncementCenterDialog(
    announcement: AppAnnouncement,
    onDismissRequest: () -> Unit,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: () -> Unit,
    onMarkRead: () -> Unit,
    onClose: () -> Unit,
    onSnooze: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val tonePalette = rememberAnnouncementTonePalette(announcement.dialogTone())
    val previewText = announcement.previewText()
    val dialogBodyText = announcement.dialogBodyText()
    val summaryText = announcement.summaryText(previewText)
    val primaryAction = announcement.primaryAction
    val secondaryAction = announcement.secondaryAction
    val hasBusinessActions = primaryAction != null || secondaryAction != null
    val showSnoozeAction = !announcement.forcePopup &&
        !announcement.isShortTerm() &&
        announcement.allowSnoozeToday
    AppModalPanel(
        onDismissRequest = {
            if (!announcement.forcePopup) onDismissRequest()
        },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Header: icon + title + meta ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = tonePalette.iconContainer
                ) {
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            announcement.dialogIcon(),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = tonePalette.iconTint
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = announcement.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = tonePalette.accent.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = announcement.severityLabel(),
                                modifier = Modifier.padding(
                                    horizontal = 8.dp,
                                    vertical = 2.dp
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = tonePalette.accent
                            )
                        }
                        announcement.publishedAt?.let { date ->
                            Text(
                                text = date,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showSnoozeAction) {
                    AppGlassIconButton(
                        onClick = onSnooze,
                        size = 38.dp,
                        surfaceColor = tonePalette.statusTint.copy(alpha = 0.3f),
                        contentColor = tonePalette.accent
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsOff,
                            contentDescription = "今日不提醒"
                        )
                    }
                }
                AppGlassIconButton(
                    onClick = onMarkRead,
                    size = 38.dp,
                    surfaceColor = tonePalette.statusTint.copy(alpha = 0.3f),
                    contentColor = tonePalette.accent
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "标记已读"
                    )
                }
                if (!announcement.forcePopup) {
                    AppGlassIconButton(onClick = onClose, size = 38.dp) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "关闭"
                        )
                    }
                }
            }
        }

        // ── Accent line ──
        Box(
            modifier = Modifier
                .height(2.dp)
                .widthIn(max = 120.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(tonePalette.accent.copy(alpha = 0.22f))
        )

        // ── Scrollable content ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cover image
            announcement.coverImageUrl?.let { url ->
                val context = LocalContext.current
                val normalizedUrl = remember(url) {
                    url.trim().let { u ->
                        if (u.startsWith("//")) "https:$u" else u
                    }
                }
                if (normalizedUrl.isNotBlank()) {
                    val request = remember(context, normalizedUrl) {
                        ImageRequest.Builder(context)
                            .data(normalizedUrl)
                            .crossfade(true)
                            .build()
                    }
                    val painter = rememberAsyncImagePainter(model = request)
                    val imageState = painter.state

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        if (imageState is AsyncImagePainter.State.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = tonePalette.accent
                            )
                        }
                        Image(
                            painter = painter,
                            contentDescription = announcement.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            // Summary
            if (summaryText != null) {
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
            }

            // Preview text card
            if (dialogBodyText.isNotBlank()) {
                AppGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = tonePalette.statusTint.copy(alpha = 0.36f),
                    border = BorderStroke(
                        1.dp,
                        tonePalette.accent.copy(alpha = 0.16f)
                    )
                ) {
                    Text(
                        text = dialogBodyText,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface.copy(alpha = 0.88f)
                    )
                }
            }
        }

        if (hasBusinessActions) {
            HorizontalDivider(
                color = colorScheme.outlineVariant.copy(alpha = 0.32f)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                secondaryAction?.let { action ->
                    AppGlassButton(
                        onClick = onSecondaryAction,
                        surfaceColor = colorScheme.surfaceContainerHighest.copy(alpha = 0.42f)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(action.text)
                    }
                }

                primaryAction?.let { action ->
                    AppGlassButton(
                        onClick = onPrimaryAction,
                        tint = tonePalette.accent
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(action.text)
                    }
                }
            }
        }
    }
}

@Composable
internal fun UnreadAnnouncementListDialog(
    announcements: List<AppAnnouncement>,
    onDismissRequest: () -> Unit,
    onOpenAnnouncement: (AppAnnouncement) -> Unit,
    onAcknowledgeAll: () -> Unit,
) {
    AppDialog(
        onDismissRequest = onDismissRequest,
        style = AppDialogStyle.Selection,
        tone = AppDialogTone.Info,
        icon = { Icon(Icons.Rounded.NotificationsActive, null) },
        title = { Text("未读公告") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "当前有 ${announcements.size} 条未读公告，默认按最新发布时间排序。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                announcements.forEachIndexed { index, announcement ->
                    AppGlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = if (index == 0) {
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.34f)
                        },
                        onClick = { onOpenAnnouncement(announcement) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = announcement.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (index == 0) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = "最新",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                            }
                            val summaryText = announcement.summaryText(announcement.previewText())
                                ?: announcement.previewText()
                            if (summaryText.isNotBlank()) {
                                Text(
                                    text = summaryText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            announcement.publishedAt?.let { date ->
                                Text(
                                    text = date,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppGlassButton(onClick = onAcknowledgeAll) {
                Icon(
                    imageVector = Icons.Rounded.DoneAll,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("全部已读")
            }
        },
        dismissButton = {
            AppGlassButton(onClick = onDismissRequest) {
                Text("稍后查看")
            }
        }
    )
}

internal fun AppAnnouncement.severityLabel(): String {
    return when (severity) {
        AnnouncementSeverity.Info -> "公告"
        AnnouncementSeverity.Success -> "更新"
        AnnouncementSeverity.Warning -> "提醒"
        AnnouncementSeverity.Danger -> "重要"
    }
}

internal fun AppAnnouncement.summaryText(previewText: String): String? {
    val normalizedSummary = summary.trim()
    if (normalizedSummary.isBlank()) return null
    if (normalizedSummary == previewText.trim()) return null
    return normalizedSummary
}
