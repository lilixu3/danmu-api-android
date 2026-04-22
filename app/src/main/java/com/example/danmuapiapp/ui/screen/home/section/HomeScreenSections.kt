package com.example.danmuapiapp.ui.screen.home

import com.example.danmuapiapp.ui.component.AppBottomSheetDialog
import com.example.danmuapiapp.ui.component.AppBottomSheetStyle
import com.example.danmuapiapp.ui.component.AppBottomSheetTone

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CacheEntry
import com.example.danmuapiapp.domain.model.CacheStats
import com.example.danmuapiapp.domain.model.DownloadQueueStatus
import com.example.danmuapiapp.domain.model.DanmuDownloadTask
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.ui.component.GithubProxyPickerDialog
import com.example.danmuapiapp.ui.component.GradientButton
import com.example.danmuapiapp.ui.component.StatusIndicator
import com.example.danmuapiapp.ui.screen.download.DanmuDownloadViewModel
import com.example.danmuapiapp.ui.screen.download.DownloadQueueSummary
import com.example.danmuapiapp.ui.screen.home.support.resolveCoreActionButtonText
import com.example.danmuapiapp.ui.theme.appDangerTonalButtonColors
import com.example.danmuapiapp.ui.theme.appPrimaryButtonColors
import com.example.danmuapiapp.ui.theme.appTonalButtonColors
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
internal fun HomeTopHeader(
    status: ServiceStatus,
    isRunning: Boolean,
    uptime: String,
    unreadAnnouncementCount: Int = 0,
    hasQueueTasks: Boolean = false,
    isQueueDownloading: Boolean = false,
    isQueuePaused: Boolean = false,
    queueSummary: DownloadQueueSummary = DownloadQueueSummary(),
    onOpenDownloadSheet: () -> Unit = {},
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
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "LogVar 弹幕服务",
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.3.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusIndicator(status = status, modifier = Modifier.size(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = if (isRunning) "在线" else "离线",
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
                    .clickable(onClick = onOpenDownloadSheet)
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        velocity = 30.dp
                    )
            )
        } else if (isRunning) {
            Text(
                text = "已连续运行 $uptime",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun MissionControlHero(
    status: ServiceStatus,
    statusMessage: String?,
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
    val infiniteTransition = rememberInfiniteTransition(label = "heroOrb")
    val orbRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbRotation"
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
        statusTitle(status = status)
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

    Surface(
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
                            .rotate(if (isRunning) orbRotation else 0f)
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
                    Surface(
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
                    icon = Icons.Rounded.SwapHoriz,
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

@Composable
internal fun RuntimePermissionHintCard(
    notificationReady: Boolean,
    batteryRequired: Boolean,
    batteryReady: Boolean,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    val showNotification = !notificationReady
    val showBattery = batteryRequired && !batteryReady

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "运行环境提醒",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (showNotification && showBattery) {
                        "建议尽快补齐下面两项，启动状态会更清楚，普通模式后台也更稳定。"
                    } else if (showNotification) {
                        "建议把通知权限补齐，启动和运行状态会更清楚。"
                    } else {
                        "建议把电池优化设为不受限制，普通模式后台会更稳定。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (showNotification) {
                PermissionQuickActionRow(
                    icon = Icons.Rounded.NotificationsActive,
                    accent = MaterialTheme.colorScheme.primary,
                    title = "通知权限",
                    summary = "建议开启，避免误判是否真的启动成功",
                    ready = false,
                    readyText = "已开启",
                    pendingText = "去开启",
                    onClick = onOpenNotificationSettings
                )
            }

            if (showNotification && showBattery) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                )
            }

            if (showBattery) {
                PermissionQuickActionRow(
                    icon = Icons.Rounded.PowerSettingsNew,
                    accent = MaterialTheme.colorScheme.tertiary,
                    title = "电池优化",
                    summary = "建议改为不受限制，减少后台被清理",
                    ready = false,
                    readyText = "已优化",
                    pendingText = "去设置",
                    onClick = onOpenBatterySettings
                )
            }
        }
    }
}

@Composable
private fun PermissionQuickActionRow(
    icon: ImageVector,
    accent: Color,
    title: String,
    summary: String,
    ready: Boolean,
    readyText: String,
    pendingText: String,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = accent.copy(alpha = 0.14f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier
                    .padding(6.dp)
                    .size(16.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (ready) {
            FilledTonalButton(
                onClick = {},
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(readyText)
            }
        } else {
            FilledTonalButton(
                onClick = { onClick?.invoke() },
                shape = RoundedCornerShape(10.dp),
                colors = appTonalButtonColors()
            ) {
                Text(pendingText)
            }
        }
    }
}

@Composable
internal fun SnapshotStrip(
    status: ServiceStatus,
    isDarkTheme: Boolean,
    runMode: RunMode,
    cacheTileValue: String,
    cacheTileBadge: String?,
    cacheTileAccent: Color,
    onOpenCacheQuick: () -> Unit,
    token: String,
    maskedToken: String,
    tokenVisible: Boolean,
    onEditToken: () -> Unit,
    port: Int,
    coreVersionText: String,
    coreVersionBadge: String?,
    coreVersionAccent: Color,
    isActionBusy: Boolean,
    onEditPort: () -> Unit,
    onCheckCoreUpdate: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = if (isDarkTheme) {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    title = "缓存管理",
                    value = cacheTileValue,
                    badge = cacheTileBadge,
                    accent = cacheTileAccent,
                    onClick = onOpenCacheQuick
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    title = "端口",
                    value = "TCP $port",
                    badge = when {
                        runMode == RunMode.Root && port == 80 -> "默认端口"
                        status == ServiceStatus.Running -> "监听中"
                        else -> "未监听"
                    },
                    accent = MaterialTheme.colorScheme.onSurface,
                    enabled = !isActionBusy,
                    onClick = onEditPort
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TokenMetricTile(
                    modifier = Modifier.weight(1f),
                    token = token,
                    maskedToken = maskedToken,
                    tokenVisible = tokenVisible,
                    onEditToken = onEditToken
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    title = "核心版本",
                    value = coreVersionText,
                    badge = coreVersionBadge,
                    accent = coreVersionAccent,
                    enabled = !isActionBusy,
                    onClick = onCheckCoreUpdate
                )
            }
        }
    }
}

@Composable
internal fun ServiceRuntimeInfoDialog(
    status: ServiceStatus,
    uptime: String,
    runMode: RunMode,
    variantLabel: String,
    port: Int,
    pid: Int?,
    localUrl: String,
    lanUrl: String,
    token: String,
    maskedToken: String,
    tokenVisible: Boolean,
    onDismiss: () -> Unit
) {
    val displayLocal = maskRuntimeUrl(localUrl, token, maskedToken, tokenVisible)
    val displayLan = maskRuntimeUrl(lanUrl, token, maskedToken, tokenVisible)

    AppBottomSheetDialog(
        onDismissRequest = onDismiss,
        style = AppBottomSheetStyle.Status,
        tone = AppBottomSheetTone.Info,
        icon = { Icon(Icons.Rounded.HourglassBottom, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("服务运行信息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusAccentColor(status).copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = statusShortLabel(status),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusAccentColor(status),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "运行 $uptime",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                RuntimeInfoItem(label = "运行模式", value = runMode.label)
                RuntimeInfoItem(label = "核心通道", value = variantLabel)
                RuntimeInfoItem(label = "监听端口", value = "TCP $port")
                if (pid != null) {
                    RuntimeInfoItem(label = "进程 PID", value = pid.toString())
                }
                RuntimeInfoItem(label = "本机地址", value = displayLocal, mono = true)
                RuntimeInfoItem(label = "局域网地址", value = displayLan, mono = true)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("我知道了")
            }
        }
    )
}

@Composable
internal fun RuntimeInfoItem(
    label: String,
    value: String,
    mono: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (value.isBlank()) "未生成" else value,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun ActionDeck(
    status: ServiceStatus,
    isRunning: Boolean,
    isTransitioning: Boolean,
    isStarting: Boolean,
    isInstalling: Boolean,
    isSwitching: Boolean,
    isUpdating: Boolean,
    isCoreInfoLoading: Boolean,
    isDarkTheme: Boolean,
    onToggle: () -> Unit,
    onRestart: () -> Unit,
    onOpenVariantPicker: () -> Unit,
    onOpenCoreDownload: () -> Unit,
    onOpenUpdatePrompt: () -> Unit,
    isCoreInstalled: Boolean,
    hasVersionUpdate: Boolean,
    sourceMismatch: Boolean,
    availableVersion: String?,
    coreOperationMessage: String?
) {
    val isStopping = status == ServiceStatus.Stopping
    val coreActionEnabled = !isTransitioning && !isCoreInfoLoading &&
        (!isCoreInstalled || sourceMismatch || hasVersionUpdate)
    val coreActionText = resolveCoreActionButtonText(
        isCoreInfoLoading = isCoreInfoLoading,
        isCoreInstalled = isCoreInstalled,
        hasVersionUpdate = hasVersionUpdate,
        sourceMismatch = sourceMismatch,
        availableVersion = availableVersion,
        isInstalling = isInstalling,
        isUpdating = isUpdating
    )
    val serviceButtonText = when {
        isStarting && coreOperationMessage.isNullOrBlank() -> "取消启动"
        isStarting -> "启动中"
        isStopping -> "停止中"
        isRunning -> "停止服务"
        else -> "启动服务"
    }
    val serviceButtonEnabled = when {
        isStopping -> false
        isStarting && coreOperationMessage.isNullOrBlank() -> true
        else -> !isTransitioning
    }
    val serviceActionIsStop = isRunning || isStopping || (isStarting && coreOperationMessage.isNullOrBlank())

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDarkTheme) 0.34f else 0.24f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GradientButton(
                    text = serviceButtonText,
                    onClick = onToggle,
                    enabled = serviceButtonEnabled,
                    modifier = Modifier.weight(1f),
                    colors = if (serviceActionIsStop) {
                        if (isDarkTheme) {
                            listOf(Color(0xFFDC2626), Color(0xFFEA580C))
                        } else {
                            listOf(Color(0xFFD63E2F), Color(0xFFF06A3A))
                        }
                    } else {
                        if (isDarkTheme) {
                            listOf(Color(0xFF2563EB), Color(0xFF1D4ED8))
                        } else {
                            listOf(Color(0xFF1E88E5), Color(0xFF4457D2))
                        }
                    },
                    disabledColors = if (isStarting) {
                        if (isDarkTheme) {
                            listOf(Color(0xFF1A3A5C), Color(0xFF264064))
                        } else {
                            listOf(Color(0xFF899FC4), Color(0xFF9097C9))
                        }
                    } else {
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    }
                )
                AnimatedVisibility(
                    visible = isRunning,
                    enter = expandHorizontally() + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut()
                ) {
                    FilledTonalButton(
                        onClick = onRestart,
                        enabled = !isTransitioning,
                        modifier = Modifier.height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isDarkTheme) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.98f)
                            },
                            contentColor = if (isDarkTheme) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    ) {
                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("重启")
                    }
                }
            }

            if (!coreOperationMessage.isNullOrBlank()) {
                Text(
                    text = coreOperationMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenVariantPicker,
                    enabled = !isTransitioning,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.SwapHoriz, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("切换核心")
                }
                FilledTonalButton(
                    onClick = {
                        if (!isCoreInstalled) {
                            onOpenCoreDownload()
                        } else {
                            onOpenUpdatePrompt()
                        }
                    },
                    enabled = coreActionEnabled,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = if (!isCoreInfoLoading && (!isCoreInstalled || sourceMismatch || hasVersionUpdate)) {
                        appDangerTonalButtonColors()
                    } else {
                        appTonalButtonColors()
                    }
                ) {
                    if (isInstalling || isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = when {
                                isCoreInfoLoading -> Icons.Rounded.HourglassTop
                                !isCoreInstalled -> Icons.Rounded.Download
                                else -> Icons.Rounded.SystemUpdateAlt
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(coreActionText)
                }
            }
        }
    }
}

@Composable
internal fun AccessGatewayPanel(
    localUrl: String,
    lanUrl: String,
    token: String,
    maskedToken: String,
    tokenVisible: Boolean,
    onToggleTokenVisible: () -> Unit,
    onCopyLocal: () -> Unit,
    onCopyLan: () -> Unit
) {
    val displayLocal = maskRuntimeUrl(localUrl, token, maskedToken, tokenVisible)
    val displayLan = maskRuntimeUrl(lanUrl, token, maskedToken, tokenVisible)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "访问入口",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(
                    onClick = onToggleTokenVisible,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = if (tokenVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = "切换地址 Token 可见",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            GatewayItem(
                title = "局域网",
                subtitle = "推荐在同一 Wi-Fi 下使用",
                value = displayLan,
                onCopy = onCopyLan,
                emphasize = true
            )
            GatewayItem(
                title = "本机",
                subtitle = "仅当前设备可访问",
                value = displayLocal,
                onCopy = onCopyLocal,
                emphasize = false
            )
        }
    }
}

@Composable
internal fun GatewayItem(
    title: String,
    subtitle: String,
    value: String,
    onCopy: () -> Unit,
    emphasize: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (emphasize) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (emphasize) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                        ) {
                            Text(
                                "推荐",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = if (emphasize) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1
                )
            }
            FilledTonalIconButton(
                onClick = onCopy,
                modifier = Modifier.size(34.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
internal fun TokenMetricTile(
    modifier: Modifier,
    token: String,
    maskedToken: String,
    tokenVisible: Boolean,
    onEditToken: () -> Unit
) {
    val displayToken = if (tokenVisible) token.ifBlank { "（未设置）" } else maskedToken

    Surface(
        modifier = modifier.clickable(onClick = onEditToken),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Token",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = displayToken,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                ),
                color = if (token.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun MetricTile(
    modifier: Modifier,
    title: String,
    value: String,
    badge: String? = null,
    accent: Color,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val containerModifier = if (onClick != null) {
        modifier.clickable(enabled = enabled, onClick = onClick)
    } else {
        modifier
    }
    Surface(
        modifier = containerModifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
            alpha = if (enabled) 0.8f else 0.58f
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (enabled) 1f else 0.7f
                    )
                )
                if (!badge.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                ),
                color = accent.copy(alpha = if (enabled) 1f else 0.7f),
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun InfoChip(
    modifier: Modifier,
    label: String,
    value: String,
    icon: ImageVector,
    badge: String? = null,
    accent: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val containerModifier = if (onClick != null) {
        modifier.clickable(enabled = enabled, onClick = onClick)
    } else {
        modifier
    }
    Surface(
        modifier = containerModifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
            alpha = if (enabled) 0.72f else 0.55f
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.6f)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (enabled) 1f else 0.7f
                        )
                    )
                    if (!badge.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = accent.copy(alpha = if (enabled) 1f else 0.72f),
                    maxLines = 1
                )
            }
        }
    }
}
