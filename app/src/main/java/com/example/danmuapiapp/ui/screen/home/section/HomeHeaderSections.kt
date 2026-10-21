package com.example.danmuapiapp.ui.screen.home

import com.example.danmuapiapp.ui.component.AppGlassSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.ui.component.StatusIndicator
import com.example.danmuapiapp.ui.component.rememberAmbientLoopState
import com.example.danmuapiapp.ui.screen.download.DownloadQueueSummary

@Composable
internal fun HomeTopHeader(
    status: ServiceStatus,
    isRunning: Boolean,
    isTransitioning: Boolean = false,
    uptime: String,
    unreadAnnouncementCount: Int = 0,
    hasQueueTasks: Boolean = false,
    isQueueDownloading: Boolean = false,
    isQueuePaused: Boolean = false,
    queueSummary: DownloadQueueSummary = DownloadQueueSummary(),
    onOpenDownloadDialog: () -> Unit = {},
    onOpenUnreadAnnouncements: () -> Unit = {}
) {
    val downloadText = when {
        isQueueDownloading -> "正在下载 · 待 ${queueSummary.pending} 运行 ${queueSummary.running.coerceAtLeast(1)}"
        isQueuePaused -> "下载已暂停 · 待 ${queueSummary.pending}"
        else -> "队列 ${queueSummary.total} 个任务 · 完成 ${queueSummary.success}"
    }
    val announcementText = when (unreadAnnouncementCount) {
        1 -> "有 1 条未读公告"
        else -> "有 $unreadAnnouncementCount 条未读公告"
    }
    val downloadAccent = when {
        isQueueDownloading -> Color(0xFF4CAF50)
        isQueuePaused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val announcementAccent = MaterialTheme.colorScheme.tertiary

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "弹幕 API",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "LogVar 弹幕服务",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    letterSpacing = 1.3.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusIndicator(status = status, modifier = Modifier.size(12.dp))
                AppGlassSurface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = when {
                            isTransitioning -> "切换中"
                            isRunning -> "在线"
                            else -> "离线"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = statusAccentColor(status)
                    )
                }
            }
        }
        if (unreadAnnouncementCount > 0) {
            Row(
                modifier = Modifier
                    .clickable(onClick = onOpenUnreadAnnouncements)
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    tint = announcementAccent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = announcementText,
                    style = MaterialTheme.typography.bodySmall,
                    color = announcementAccent,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        velocity = 30.dp
                    )
                )
            }
        } else if (hasQueueTasks) {
            Text(
                text = downloadText,
                style = MaterialTheme.typography.bodySmall,
                color = downloadAccent,
                maxLines = 1,
                modifier = Modifier
                    .clickable(onClick = onOpenDownloadDialog)
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        velocity = 30.dp
                    )
            )
        } else if (isRunning) {
            Text(
                text = "已连续运行 $uptime",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun MissionControlHero(
    status: ServiceStatus,
    statusMessage: String?,
    transitionTitle: String? = null,
    isCoreInstalled: Boolean,
    isCoreInfoLoading: Boolean,
    runModeLabel: String,
    uptime: String,
    variantLabel: String,
    isRunning: Boolean,
    isInstalling: Boolean,
    isSwitching: Boolean,
    isUpdating: Boolean,
    isActionBusy: Boolean,
    isDarkTheme: Boolean,
    onToggleRunMode: () -> Unit,
    onOpenVariantPicker: () -> Unit,
    onOpenRuntimeInfo: () -> Unit
) {
    // 装饰性旋转：前台可见时才跑，30fps 更新，并在绘制阶段读取进度，
    // 避免逐帧重组合与整页 backdrop 重录（6 秒一圈，肉眼无差别）。
    val orbRotationProgress = rememberAmbientLoopState(
        durationMillis = 6000,
        maxFps = 30,
        active = isRunning
    )
    val showMissingCore = !isCoreInfoLoading && !isCoreInstalled
    val heroAccent = if (showMissingCore) {
        MaterialTheme.colorScheme.error
    } else {
        statusAccentColor(status)
    }
    val heroTitle = if (showMissingCore) {
        "核心未安装"
    } else {
        transitionTitle ?: statusTitle(status = status)
    }
    val heroSubtitle = if (showMissingCore) {
        "当前${variantLabel}尚未安装，请先下载核心后再启动服务"
    } else {
        statusSubtitle(status = status, statusMessage = statusMessage)
    }
    val heroIcon = if (showMissingCore) {
        Icons.Rounded.DownloadForOffline
    } else {
        statusIcon(status)
    }

    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = if (isDarkTheme) {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.84f)
        },
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(
                alpha = if (isDarkTheme) 0.42f else 0.32f
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(
                                alpha = if (isDarkTheme) 0.08f else 0.13f
                            ),
                            MaterialTheme.colorScheme.tertiary.copy(
                                alpha = if (isDarkTheme) 0.05f else 0.09f
                            ),
                            Color.Transparent
                        )
                    )
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = heroTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = heroSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .graphicsLayer {
                                rotationZ = if (isRunning) orbRotationProgress.value * 360f else 0f
                            }
                            .clip(CircleShape)
                            .background(
                                Brush.sweepGradient(
                                    listOf(
                                        heroAccent.copy(alpha = 0.28f),
                                        Color.Transparent,
                                        heroAccent.copy(alpha = 0.08f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    AppGlassSurface(
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        color = if (isDarkTheme) {
                            MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.98f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isInstalling || isSwitching || isUpdating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(26.dp),
                                    strokeWidth = 2.6.dp
                                )
                            } else {
                                Icon(
                                    imageVector = heroIcon,
                                    contentDescription = null,
                                    tint = heroAccent,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoChip(
                    modifier = Modifier.weight(1f),
                    label = "模式",
                    value = runModeLabel,
                    icon = Icons.Rounded.PowerSettingsNew,
                    enabled = !isActionBusy,
                    onClick = onToggleRunMode
                )
                InfoChip(
                    modifier = Modifier.weight(1f),
                    label = "核心",
                    value = variantLabel,
                    icon = Icons.Rounded.Storage,
                    enabled = !isActionBusy,
                    onClick = onOpenVariantPicker
                )
                InfoChip(
                    modifier = Modifier.weight(1f),
                    label = "已运行",
                    value = uptime,
                    icon = Icons.Rounded.HourglassTop,
                    accent = statusAccentColor(status),
                    onClick = onOpenRuntimeInfo
                )
            }
        }
    }
}

