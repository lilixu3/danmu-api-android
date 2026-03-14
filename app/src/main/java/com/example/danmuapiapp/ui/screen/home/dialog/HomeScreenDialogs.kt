@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

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
import com.example.danmuapiapp.ui.theme.appDangerTonalButtonColors
import com.example.danmuapiapp.ui.theme.appPrimaryButtonColors
import com.example.danmuapiapp.ui.theme.appTonalButtonColors
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
internal fun HomePanelDialog(
    onDismissRequest: () -> Unit,
    icon: ImageVector,
    title: String,
    subtitle: String,
    canDismiss: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit
) {
    val panelSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val panelMaxHeight = (screenHeight * 0.9f).coerceAtLeast(320.dp)

    ModalBottomSheet(
        onDismissRequest = {
            if (canDismiss) onDismissRequest()
        },
        sheetState = panelSheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.34f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = panelMaxHeight)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(top = 4.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
                ) {
                    Box(
                        modifier = Modifier.size(36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .height(2.dp)
                    .widthIn(max = 120.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = actions
            )
        }
    }
}

@Composable
internal fun DialogActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    primary: Boolean = false,
    loading: Boolean = false
) {
    val actualEnabled = enabled && !loading
    if (primary) {
        Button(
            onClick = onClick,
            enabled = actualEnabled,
            shape = RoundedCornerShape(12.dp),
            colors = appPrimaryButtonColors()
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(icon, null, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(text)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = actualEnabled,
            shape = RoundedCornerShape(12.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(icon, null, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(text)
        }
    }
}

@Composable
internal fun CacheQuickDialog(
    cacheStats: CacheStats,
    cacheEntries: List<CacheEntry>,
    isLoading: Boolean,
    isClearing: Boolean,
    onRefresh: () -> Unit,
    onQuickClear: () -> Unit,
    onOpenCacheManagement: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    val subtitle = if (cacheStats.isAvailable) {
        "${cacheStats.reqRecordsCount} 条记录 · 今日 ${cacheStats.todayReqNum} 次请求"
    } else {
        "服务未运行，暂无缓存数据"
    }

    HomePanelDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Rounded.Storage,
        title = "缓存概览",
        subtitle = subtitle,
        content = {
            val dialogScroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(dialogScroll),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (cacheStats.isAvailable) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CacheStatBadge(
                            modifier = Modifier.weight(1f),
                            label = "请求记录",
                            value = "${cacheStats.reqRecordsCount}"
                        )
                        CacheStatBadge(
                            modifier = Modifier.weight(1f),
                            label = "今日请求",
                            value = "${cacheStats.todayReqNum}"
                        )
                        CacheStatBadge(
                            modifier = Modifier.weight(1f),
                            label = "上次清理",
                            value = cacheStats.lastClearedAt?.let {
                                dateFormat.format(Date(it))
                            } ?: "从未"
                        )
                    }

                    if (cacheEntries.isNotEmpty()) {
                        Text(
                            text = "最近请求",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            cacheEntries.take(4).forEach { entry ->
                                CacheEntryPreviewRow(entry = entry)
                            }
                        }
                    } else {
                        Text(
                            text = "暂无请求记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.CloudOff,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "启动服务后可查看缓存数据",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        actions = {
            DialogActionButton(
                text = "关闭",
                icon = Icons.Rounded.Close,
                onClick = onDismiss
            )
            if (isLoading) {
                DialogActionButton(
                    text = "刷新中",
                    icon = Icons.Rounded.Refresh,
                    onClick = {},
                    enabled = false
                )
            } else {
                DialogActionButton(
                    text = "刷新",
                    icon = Icons.Rounded.Refresh,
                    onClick = onRefresh
                )
            }
            DialogActionButton(
                text = if (isClearing) "清理中…" else "快速清理",
                icon = Icons.Rounded.DeleteSweep,
                onClick = onQuickClear,
                enabled = cacheStats.isAvailable && !isClearing && !isLoading
            )
            DialogActionButton(
                text = "缓存管理",
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                onClick = onOpenCacheManagement,
                primary = true
            )
        }
    )
}

@Composable
internal fun CacheStatBadge(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.78f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun CacheEntryPreviewRow(entry: CacheEntry) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val methodColor = when (entry.type.uppercase()) {
        "GET" -> MaterialTheme.colorScheme.primary
        "POST" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusCode = entry.hitCount.takeIf { it in 100..599 }
    val statusColor = when {
        statusCode != null && statusCode in 200..299 -> MaterialTheme.colorScheme.primary
        statusCode != null && statusCode >= 400 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (entry.type.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = methodColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        entry.type.uppercase(),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = methodColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                entry.key.ifBlank { "未知接口" },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (statusCode != null) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        "$statusCode",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }
            Text(
                dateFormat.format(Date(entry.createdAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
internal fun QuickPortDialog(
    isBusy: Boolean,
    status: ServiceStatus,
    runMode: RunMode,
    currentPort: Int,
    quickPortText: String,
    quickPortError: String?,
    onPortTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit,
    onPortError: (String) -> Unit
) {
    val modeHint = if (runMode == RunMode.Normal) {
        "普通模式支持 1024 – 65535（Root 支持 1 – 65535）"
    } else {
        "Root 模式支持 1 – 65535"
    }
    val subtitle = if (status == ServiceStatus.Running) {
        "保存后自动重启服务以应用新端口"
    } else {
        "当前未运行，保存后写入启动配置"
    }

    HomePanelDialog(
        onDismissRequest = onDismiss,
        canDismiss = !isBusy,
        icon = Icons.Rounded.Lan,
        title = "快速修改端口",
        subtitle = subtitle,
        content = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
                )
            ) {
                Text(
                    text = "当前端口：TCP $currentPort · $modeHint",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val quickPorts = if (runMode == RunMode.Root) {
                listOf("80", "9321", "2233")
            } else {
                listOf("9321", "2233", "5000")
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickPorts.forEach { target ->
                    DialogActionButton(
                        text = target,
                        icon = Icons.Rounded.Tune,
                        onClick = { onPortTextChange(target) },
                        enabled = !isBusy && quickPortText != target,
                        primary = quickPortText == target
                    )
                }
            }

            OutlinedTextField(
                value = quickPortText,
                onValueChange = onPortTextChange,
                label = { Text("端口") },
                singleLine = true,
                isError = quickPortError != null,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                supportingText = {
                    Text(quickPortError ?: modeHint)
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        actions = {
            DialogActionButton(
                text = "取消",
                icon = Icons.Rounded.Close,
                onClick = onDismiss,
                enabled = !isBusy
            )
            DialogActionButton(
                text = if (status == ServiceStatus.Running) "保存并重启" else "保存",
                icon = if (status == ServiceStatus.Running) {
                    Icons.Rounded.PowerSettingsNew
                } else {
                    Icons.Rounded.CheckCircle
                },
                onClick = {
                    val port = quickPortText.trim().toIntOrNull()
                    if (port == null || port !in 1..65535) {
                        onPortError("请输入有效端口（1-65535）")
                        return@DialogActionButton
                    }
                    if (runMode == RunMode.Normal && port in 1..1023) {
                        onPortError("普通模式仅支持 1024-65535，请切换 Root 模式后再使用低位端口")
                        return@DialogActionButton
                    }
                    if (port == currentPort) {
                        onPortError("端口未变化")
                        return@DialogActionButton
                    }
                    onApply(port)
                },
                enabled = !isBusy,
                primary = true
            )
        }
    )
}

@Composable
internal fun QuickTokenDialog(
    isBusy: Boolean,
    status: ServiceStatus,
    currentToken: String,
    quickTokenText: String,
    quickTokenError: String?,
    onTokenTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
    onTokenError: (String) -> Unit
) {
    val subtitle = if (status == ServiceStatus.Running) {
        "保存后会热更新到运行中服务"
    } else {
        "保存后会写入运行配置"
    }
    val preview = when {
        quickTokenText.isBlank() -> "空（将恢复核心默认 Token）"
        quickTokenText.length <= 8 -> quickTokenText
        else -> "${quickTokenText.take(4)}****${quickTokenText.takeLast(2)}"
    }

    HomePanelDialog(
        onDismissRequest = onDismiss,
        canDismiss = !isBusy,
        icon = Icons.Rounded.Tune,
        title = "快速修改 Token",
        subtitle = subtitle,
        content = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "当前：${if (currentToken.isBlank()) "空（默认）" else currentToken}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "待保存：$preview",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            OutlinedTextField(
                value = quickTokenText,
                onValueChange = onTokenTextChange,
                label = { Text("Token（可留空）") },
                singleLine = true,
                isError = quickTokenError != null,
                shape = RoundedCornerShape(14.dp),
                supportingText = {
                    Text(quickTokenError ?: "留空将恢复核心默认 Token")
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        actions = {
            DialogActionButton(
                text = "取消",
                icon = Icons.Rounded.Close,
                onClick = onDismiss,
                enabled = !isBusy
            )
            DialogActionButton(
                text = "保存",
                icon = Icons.Rounded.CheckCircle,
                onClick = {
                    val normalized = quickTokenText.trim()
                    if (normalized == currentToken) {
                        onTokenError("Token 未变化")
                        return@DialogActionButton
                    }
                    onApply(normalized)
                },
                enabled = !isBusy,
                primary = true
            )
        }
    )
}

@Composable
internal fun CoreUpdateConfirmDialog(
    variantLabel: String,
    currentVersion: String?,
    latestVersion: String?,
    isChecking: Boolean,
    resultMessage: String?,
    resultIsError: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val resultTint = if (resultIsError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val resultContainer = if (resultIsError) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.62f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    }
    val resultIcon = if (resultIsError) {
        Icons.Rounded.ErrorOutline
    } else {
        Icons.Rounded.CheckCircle
    }
    val confirmText = when {
        isChecking -> "正在检查"
        !resultMessage.isNullOrBlank() -> "重新检查"
        else -> "继续检查"
    }

    HomePanelDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Rounded.SystemUpdateAlt,
        title = "检查核心更新",
        subtitle = "检查当前核心分支是否有可用更新",
        content = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "分支：$variantLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "当前版本：${currentVersion?.let { "v$it" } ?: "--"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "远程版本：${latestVersion?.let { "v$it" } ?: "待查询"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.64f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (resultMessage.isNullOrBlank()) {
                                    Icons.Rounded.HourglassBottom
                                } else {
                                    resultIcon
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (resultMessage.isNullOrBlank()) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    resultTint
                                }
                            )
                        }
                        Text(
                            text = when {
                                isChecking -> "正在连接更新源并检查版本"
                                !resultMessage.isNullOrBlank() -> "本次检查结果"
                                else -> "点击下方按钮开始检查"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (isChecking) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        )
                        Text(
                            text = "弹窗会保持打开，检查完成后直接在这里显示结果。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (!resultMessage.isNullOrBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = resultContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = resultIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = resultTint
                                )
                                Text(
                                    text = resultMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (resultIsError) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    }
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "若检测到新版本，将自动跳转到更新确认界面。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        actions = {
            DialogActionButton(
                text = "关闭",
                icon = Icons.Rounded.Close,
                onClick = onDismiss
            )
            DialogActionButton(
                text = confirmText,
                icon = Icons.Rounded.SystemUpdate,
                onClick = onConfirm,
                primary = true,
                loading = isChecking
            )
        }
    )
}
