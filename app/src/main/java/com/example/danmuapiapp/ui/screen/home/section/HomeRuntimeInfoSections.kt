package com.example.danmuapiapp.ui.screen.home

import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.AppDialogStyle
import com.example.danmuapiapp.ui.component.AppDialogTone
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.domain.model.RuntimeListenMode
import androidx.compose.foundation.border
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.ServiceStatus

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
    lanIpv6Url: String,
    tunnelUrl: String,
    listenMode: RuntimeListenMode,
    token: String,
    maskedToken: String,
    tokenVisible: Boolean,
    onDismiss: () -> Unit
) {
    val displayLocal = maskRuntimeUrl(localUrl, token, maskedToken, tokenVisible)
    val displayLan = maskRuntimeUrl(lanUrl, token, maskedToken, tokenVisible)
    val displayLanIpv6 = maskRuntimeUrl(lanIpv6Url, token, maskedToken, tokenVisible)
    val displayTunnel = maskRuntimeUrl(tunnelUrl, token, maskedToken, tokenVisible)

    AppDialog(
        onDismissRequest = onDismiss,
        style = AppDialogStyle.Status,
        tone = AppDialogTone.Info,
        icon = { Icon(Icons.Rounded.HourglassBottom, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("服务运行信息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppGlassSurface(
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
                    AppGlassSurface(
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
                RuntimeInfoItem(label = "监听网络", value = listenMode.label)
                if (pid != null) {
                    RuntimeInfoItem(label = "进程 PID", value = pid.toString())
                }
                if (tunnelUrl.isNotBlank()) {
                    RuntimeInfoItem(label = "内网穿透", value = displayTunnel, mono = true)
                }
                RuntimeInfoItem(label = "本机地址", value = displayLocal, mono = true)
                RuntimeInfoItem(label = "IPv4 地址", value = displayLan, mono = true)
                if (listenMode == RuntimeListenMode.DualStack) {
                    RuntimeInfoItem(
                        label = "IPv6 地址",
                        value = displayLanIpv6.ifBlank { "当前网络未分配可用地址" },
                        mono = true
                    )
                }
            }
        },
        confirmButton = {
            AppGlassButton(onClick = onDismiss) {
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
    AppGlassSurface(
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
            SelectionContainer {
                Text(
                    text = if (value.isBlank()) "未生成" else value,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (value.contains('[')) 3 else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

