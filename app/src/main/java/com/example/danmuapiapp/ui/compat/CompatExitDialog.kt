package com.example.danmuapiapp.ui.compat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.danmuapiapp.data.util.DeviceCompatMode
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.ui.component.remoteFocusHighlight

/** Both exit choices belong to the footer and perform their action in one step. */
@Composable
internal fun CompatExitDialog(
    status: ServiceStatus,
    busy: Boolean,
    onDismiss: () -> Unit,
    onKeepService: () -> Unit,
    onStopAndExit: () -> Unit
) {
    val cancelFocus = remember { FocusRequester() }
    val context = LocalContext.current
    val remoteDevice = remember(context) { DeviceCompatMode.isCompatModeDevice(context) }
    val active = status == ServiceStatus.Running || status == ServiceStatus.Starting
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false
    )) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp),
            contentAlignment = Alignment.Center) {
            val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            val sideBySide = minOf(maxWidth.value, 680f) / scale >= 540f
            val shortWindow = maxHeight.value / scale < if (sideBySide) 300f else 400f
            val scroll = rememberScrollState()
            Surface(Modifier.widthIn(max = 680.dp).fillMaxWidth().heightIn(max = maxHeight),
                shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                // In a short landscape window all actions remain reachable by touch or D-pad.
                Column((if (shortWindow) Modifier.verticalScroll(scroll) else Modifier).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.Logout, null, tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("退出界面", style = MaterialTheme.typography.titleLarge)
                            Text(if (active) "选择退出后是否继续提供弹幕服务。" else "关闭界面并返回桌面。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val choices: @Composable (Modifier) -> Unit = { itemModifier ->
                        CompatExitChoice("停止服务并退出", "其他设备将无法继续访问", Icons.Rounded.StopCircle,
                            onStopAndExit, itemModifier, enabled = !busy, danger = true)
                        CompatExitChoice(if (active) "保留服务并退出" else "退到后台",
                            if (active) "弹幕服务继续在后台运行" else "保留当前服务状态",
                            Icons.Rounded.Minimize, onKeepService, itemModifier)
                    }
                    if (sideBySide) {
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            choices(Modifier.weight(1f).fillMaxHeight())
                        }
                    } else {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            choices(Modifier.fillMaxWidth())
                        }
                    }
                    CompatButton("继续使用", onDismiss, Modifier.fillMaxWidth().focusRequester(cancelFocus))
                }
            }
        }
        LaunchedEffect(remoteDevice) {
            if (remoteDevice) {
                withFrameNanos { }
                cancelFocus.requestFocus()
            }
        }
    }
}

@Composable
private fun CompatExitChoice(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean = true,
    danger: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val ink = when {
        !enabled -> colors.onSurfaceVariant.copy(alpha = 0.55f)
        danger -> colors.onErrorContainer
        else -> colors.onPrimaryContainer
    }
    Surface(modifier.clip(shape).remoteFocusHighlight(shape, enabled)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = shape, color = when {
            !enabled -> colors.surfaceContainerHigh
            danger -> colors.errorContainer
            else -> colors.primaryContainer
        }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = ink, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, color = ink, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(subtitle, color = ink, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
