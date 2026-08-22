package com.example.danmuapiapp.ui.screen.home

import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.AppDialogStyle
import com.example.danmuapiapp.ui.component.AppDialogTone
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.danmuapiapp.domain.model.CoreRemoteCommit
import com.example.danmuapiapp.domain.model.CoreSourceStatus
import com.example.danmuapiapp.domain.model.CoreVariantDisplayNames
import com.example.danmuapiapp.domain.model.DownloadQueueStatus
import com.example.danmuapiapp.domain.model.DanmuDownloadTask
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.model.resolveCoreVariantSourceText
import com.example.danmuapiapp.ui.component.GithubProxyPickerDialog
import com.example.danmuapiapp.ui.component.GradientButton
import com.example.danmuapiapp.ui.component.CoreUpdateAvailableDialog
import com.example.danmuapiapp.ui.component.StatusIndicator
import com.example.danmuapiapp.ui.component.shouldOfferCoreUpdateActions
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
internal fun UpdatePromptDialog(
    variantLabel: String?,
    currentVersion: String?,
    latestVersion: String?,
    remoteCommit: CoreRemoteCommit?,
    sourceMismatch: Boolean,
    sourceUnknownLegacy: Boolean,
    desiredSource: String?,
    onShowDetails: () -> Unit,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    if (variantLabel.isNullOrBlank()) return
    if (!sourceMismatch && !sourceUnknownLegacy && latestVersion.isNullOrBlank()) return
    if (shouldOfferCoreUpdateActions(
            hasVersionUpdate = !latestVersion.isNullOrBlank(),
            hasCheckError = false,
            sourceMismatch = sourceMismatch,
            sourceUnknownLegacy = sourceUnknownLegacy
        )
    ) {
        CoreUpdateAvailableDialog(
            variantLabel = variantLabel,
            currentVersion = currentVersion,
            latestVersion = latestVersion,
            remoteCommit = remoteCommit,
            onDismiss = onDismiss,
            onShowDetails = onShowDetails,
            onUpdateNow = onUpdate
        )
        return
    }
    AppDialog(
        onDismissRequest = onDismiss,
        style = AppDialogStyle.Confirm,
        tone = AppDialogTone.Brand,
        icon = { Icon(Icons.Rounded.SystemUpdateAlt, null) },
        title = {
            Text(
                when {
                    sourceMismatch -> "需要替换核心"
                    sourceUnknownLegacy -> "核心来源待确认"
                    else -> "发现核心更新"
                }
            )
        },
        text = {
            if (sourceMismatch) {
                Text("$variantLabel 当前来源与设置不一致，将替换为 ${desiredSource ?: "目标仓库"}")
            } else if (sourceUnknownLegacy) {
                Text("$variantLabel 是旧版安装，缺少来源标记；本地核心可继续启动，重新下载后会写入当前来源标记。")
            } else {
                Text(
                    "$variantLabel：${
                        com.example.danmuapiapp.domain.model.formatCoreVersionTransition(
                            currentVersion,
                            latestVersion
                        )
                    }"
                )
            }
        },
        confirmButton = {
            AppGlassButton(onClick = onUpdate, tint = MaterialTheme.colorScheme.primary) {
                Text(if (sourceMismatch || sourceUnknownLegacy) "重新下载" else "更新")
            }
        },
        dismissButton = {
            AppGlassButton(onClick = onDismiss) {
                Text("稍后")
            }
        }
    )
}

@Composable
internal fun NoCoreDialog(
    currentVariant: ApiVariant,
    currentVariantLabel: String,
    coreDisplayNames: CoreVariantDisplayNames,
    customRepo: String,
    customRepoBranch: String,
    onDismiss: () -> Unit,
    onInstall: (ApiVariant) -> Unit,
    onOpenCoreManagement: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        style = AppDialogStyle.Selection,
        tone = AppDialogTone.Warning,
        icon = { Icon(Icons.Rounded.DownloadForOffline, null) },
        title = { Text("核心未安装") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("当前选择的 $currentVariantLabel 尚未安装，请选择要下载的版本：")
                Text(
                    "下载完成后会自动切换到所选核心并启动服务。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AppGlassButton(
                    onClick = onOpenCoreManagement,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (currentVariant == ApiVariant.Custom && customRepo.isBlank()) {
                        "设置自定义仓库"
                    } else {
                        "打开核心管理"
                    })
                }
                ApiVariant.entries.filter { variant ->
                    variant != ApiVariant.Custom ||
                        resolveCoreVariantSourceText(variant, customRepo, customRepoBranch).isNotBlank()
                }.forEach { variant ->
                    val sourceText = resolveCoreVariantSourceText(variant, customRepo, customRepoBranch)
                    AppGlassSurface(
                        onClick = { onInstall(variant) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                variantIcon(variant),
                                null,
                                tint = variantAccent(variant),
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(coreDisplayNames.resolve(variant), style = MaterialTheme.typography.bodyLarge)
                                if (sourceText.isNotBlank()) {
                                    Text(
                                        sourceText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            AppGlassButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
internal fun UnavailableVariantDialog(
    variant: ApiVariant,
    variantLabel: String,
    customRepoConfigured: Boolean,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onOpenCoreManagement: () -> Unit
) {
    val needsCustomRepo = variant == ApiVariant.Custom && !customRepoConfigured
    AppDialog(
        onDismissRequest = onDismiss,
        style = AppDialogStyle.Confirm,
        tone = AppDialogTone.Warning,
        icon = { Icon(Icons.Rounded.DownloadForOffline, null) },
        title = { Text("$variantLabel 尚未安装") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (needsCustomRepo) {
                        "当前没有配置自定义仓库，因此不能切换。原核心会继续保持选中和运行。"
                    } else {
                        "当前版本没有可用核心文件，因此没有执行切换。可以现在安装，完成后会自动切换并启动。"
                    }
                )
                AppGlassButton(
                    onClick = onOpenCoreManagement,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (needsCustomRepo) "设置自定义仓库" else "打开核心管理")
                }
            }
        },
        confirmButton = if (needsCustomRepo) null else {
            {
                AppGlassButton(onClick = onInstall, tint = MaterialTheme.colorScheme.primary) {
                    Text("安装并切换")
                }
            }
        },
        dismissButton = {
            AppGlassButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun VariantPickerDialog(
    currentVariant: ApiVariant,
    coreList: List<com.example.danmuapiapp.domain.model.CoreInfo>,
    isCoreInfoLoading: Boolean,
    coreDisplayNames: CoreVariantDisplayNames,
    customRepo: String,
    customRepoBranch: String,
    isBusy: Boolean,
    onSelect: (ApiVariant) -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        style = AppDialogStyle.Selection,
        tone = AppDialogTone.Brand,
        icon = { Icon(Icons.Rounded.SwapHoriz, contentDescription = null) },
        title = { Text("切换 API 核心") },
        supportingText = {
            Text(if (isBusy) "正在切换，请稍候" else "选择要启用的核心来源")
        },
        text = {
            ApiVariant.entries.forEach { variant ->
                val info = coreList.find { it.variant == variant }
                val isSelected = variant == currentVariant
                val variantLabel = coreDisplayNames.resolve(variant)
                val sourceText = resolveCoreVariantSourceText(variant, customRepo, customRepoBranch)
                AppGlassSurface(
                    onClick = { if (!isBusy) onSelect(variant) },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            variantIcon(variant),
                            null,
                            tint = variantAccent(variant),
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(variantLabel, style = MaterialTheme.typography.titleMedium)
                            if (info?.version != null) {
                                val vText = if (info.hasVersionUpdate && info.availableVersion != null) {
                                    com.example.danmuapiapp.domain.model.formatCoreVersionTransition(
                                        info.version,
                                        info.availableVersion
                                    )
                                } else {
                                    com.example.danmuapiapp.domain.model.formatCoreVersionValue(info.version)
                                }
                                Text(
                                    vText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (info.hasVersionUpdate) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            if (sourceText.isNotBlank()) {
                                Text(
                                    sourceText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = when {
                                isCoreInfoLoading -> MaterialTheme.colorScheme.surfaceContainerHighest
                                info?.isInstalled == true && info.needsAttention -> MaterialTheme.colorScheme.primaryContainer
                                info?.isInstalled == true -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                else -> MaterialTheme.colorScheme.errorContainer
                            }
                        ) {
                            Text(
                                text = when {
                                    isCoreInfoLoading -> "加载中"
                                    info?.sourceMismatch == true -> "需替换"
                                    info?.sourceStatus == CoreSourceStatus.UnknownLegacy -> "需刷新"
                                    info?.isInstalled == true && info.hasVersionUpdate -> "有更新"
                                    info?.isInstalled == true -> "已安装"
                                    else -> "未安装"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    isCoreInfoLoading -> MaterialTheme.colorScheme.onSurfaceVariant
                                    info?.isInstalled == true && info.needsAttention -> MaterialTheme.colorScheme.primary
                                    info?.isInstalled == true -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.error
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            AppGlassButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

internal fun variantIcon(variant: ApiVariant): ImageVector {
    return when (variant) {
        ApiVariant.Stable -> Icons.Rounded.Verified
        ApiVariant.Dev -> Icons.Rounded.Science
        ApiVariant.Custom -> Icons.Rounded.Tune
    }
}

@Composable
internal fun variantAccent(variant: ApiVariant): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (variant) {
        ApiVariant.Stable -> if (dark) Color(0xFF4ADE80) else Color(0xFF43A047)
        ApiVariant.Dev    -> if (dark) Color(0xFFFBBF24) else Color(0xFFE0A106)
        ApiVariant.Custom -> if (dark) Color(0xFF7DCFFF) else Color(0xFF7E57C2)
    }
}

internal fun statusIcon(status: ServiceStatus): ImageVector {
    return when (status) {
        ServiceStatus.Running -> Icons.Rounded.PlayArrow
        ServiceStatus.Starting -> Icons.Rounded.HourglassTop
        ServiceStatus.Stopping -> Icons.Rounded.HourglassBottom
        ServiceStatus.Error -> Icons.Rounded.ErrorOutline
        ServiceStatus.Stopped -> Icons.Rounded.Stop
    }
}

internal fun statusTitle(
    status: ServiceStatus
): String {
    return when (status) {
        ServiceStatus.Running -> "服务运行中"
        ServiceStatus.Starting -> "服务启动中"
        ServiceStatus.Stopping -> "服务停止中"
        ServiceStatus.Error -> "服务异常"
        ServiceStatus.Stopped -> "服务已停止"
    }
}

internal fun statusSubtitle(
    status: ServiceStatus,
    statusMessage: String?
): String {
    return statusMessage?.takeIf { it.isNotBlank() } ?: when (status) {
        ServiceStatus.Running -> "接口已就绪，可直接在局域网访问"
        ServiceStatus.Starting -> "正在初始化运行环境，请稍候"
        ServiceStatus.Stopping -> "正在安全停止服务进程"
        ServiceStatus.Error -> "请查看日志或重新启动服务"
        ServiceStatus.Stopped -> "点击启动后将拉起服务进程"
    }
}

internal fun coreOperationStatus(
    isInstalling: Boolean,
    isSwitching: Boolean,
    isUpdating: Boolean
): String? {
    return when {
        isSwitching -> "正在切换核心，完成后会自动重启服务"
        isUpdating -> "正在更新核心，服务会自动停止并在完成后恢复"
        isInstalling -> "核心下载中，低性能设备首次安装会更久"
        else -> null
    }
}

internal fun statusShortLabel(status: ServiceStatus): String {
    return when (status) {
        ServiceStatus.Running -> "运行"
        ServiceStatus.Starting -> "启动中"
        ServiceStatus.Stopping -> "停止中"
        ServiceStatus.Error -> "异常"
        ServiceStatus.Stopped -> "停止"
    }
}

internal fun maskRuntimeUrl(
    rawUrl: String,
    token: String,
    maskedToken: String,
    tokenVisible: Boolean
): String {
    if (rawUrl.isBlank()) return ""
    val masked = if (tokenVisible || token.isBlank()) rawUrl else rawUrl.replace(token, maskedToken)
    return compactDefaultPort(masked)
}

internal fun compactDefaultPort(rawUrl: String): String {
    if (rawUrl.isBlank()) return rawUrl
    return runCatching {
        val uri = URI(rawUrl)
        val scheme = uri.scheme?.lowercase(Locale.ROOT).orEmpty()
        val defaultPort = when (scheme) {
            "http" -> 80
            "https" -> 443
            else -> -1
        }
        if (defaultPort <= 0 || uri.port <= 0 || uri.port != defaultPort) {
            return@runCatching rawUrl
        }

        val host = uri.host ?: return@runCatching rawUrl
        val userInfo = uri.rawUserInfo?.let { "$it@" }.orEmpty()
        val hostPart = if (host.contains(':')) "[$host]" else host
        val path = uri.rawPath.orEmpty()
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
        "$scheme://$userInfo$hostPart$path$query$fragment"
    }.getOrElse { rawUrl }
}

@Composable
internal fun statusAccentColor(status: ServiceStatus): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (status) {
        ServiceStatus.Running -> if (dark) Color(0xFF4ADE80) else Color(0xFF2E7D32)
        ServiceStatus.Starting,
        ServiceStatus.Stopping -> if (dark) Color(0xFFFBBF24) else Color(0xFFE0A106)
        ServiceStatus.Error -> if (dark) Color(0xFFF87171) else Color(0xFFD84315)
        ServiceStatus.Stopped -> if (dark) Color(0xFF94A3B8) else Color(0xFF6E7484)
    }
}

internal tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

internal fun formatBytes(v: Long): String {
    if (v <= 0) return "未知"
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        v >= gb -> String.format(Locale.getDefault(), "%.2fGB", v / gb)
        v >= mb -> String.format(Locale.getDefault(), "%.2fMB", v / mb)
        v >= kb -> String.format(Locale.getDefault(), "%.1fKB", v / kb)
        else -> "${v}B"
    }
}
