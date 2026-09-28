package com.example.danmuapiapp.ui.screen.tools

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.data.tunnel.TunnelLinkState
import com.example.danmuapiapp.data.tunnel.TunnelUiState
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton
import com.example.danmuapiapp.ui.theme.appPrimaryButtonColors

@Composable
internal fun TunnelConnectionCard(
    state: TunnelUiState, busy: Boolean, onSettings: () -> Unit,
    onStart: () -> Unit, onStop: () -> Unit, onRestart: () -> Unit
) {
    val status = tunnelStatus(state)
    val configured = tunnelConfigured(state.settings)
    val needsSettings = !configured || !state.settings.enabled
    val active = state.running || state.state == "starting" || state.state == "retrying"
    val connected = state.running && state.linkState == TunnelLinkState.Connected
    val localTone = if (state.serviceRunning) TunnelTone.Active else TunnelTone.Quiet
    val serverTone = when {
        connected -> TunnelTone.Active
        status.tone == TunnelTone.Error -> TunnelTone.Error
        active -> TunnelTone.Pending
        else -> TunnelTone.Quiet
    }
    val serverLabel = when {
        connected -> "已连接"
        state.running && state.linkState == TunnelLinkState.Unknown -> "待确认"
        status.tone == TunnelTone.Error -> "连接异常"
        state.state == "retrying" -> "重连中"
        active -> "连接中"
        !configured -> "待配置"
        else -> "未连接"
    }
    val publicLabel = when {
        connected && state.serviceRunning -> "隧道就绪"
        connected -> "等待服务"
        active -> "等待链路"
        else -> "未连接"
    }
    TunnelCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(status.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            TunnelBadge(status.badge, tunnelStatusColor(status.tone))
        }
        Text(status.description, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Lines sit behind the three equal-width nodes and align with the icon centers.
        // Only a confirmed link can light the route; a running frpc process alone is insufficient.
        val firstColor by animateColorAsState(tunnelStatusColor(
            if (connected && state.serviceRunning) TunnelTone.Active else serverTone
        ), label = "tunnelLocalRoute")
        val secondColor by animateColorAsState(tunnelStatusColor(serverTone), label = "tunnelPublicRoute")
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Canvas(Modifier.fillMaxWidth().height(36.dp)) {
                val y = size.height / 2f
                val nodeGap = 25.dp.toPx()
                val lineWidth = 1.5.dp.toPx()
                val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
                fun route(from: Float, to: Float, color: Color, ready: Boolean) {
                    if (to <= from) return
                    drawLine(color.copy(alpha = if (ready) 0.65f else 0.3f),
                        Offset(from, y), Offset(to, y), strokeWidth = lineWidth,
                        cap = StrokeCap.Round, pathEffect = if (ready) null else dash)
                }
                route(size.width / 6f + nodeGap, size.width / 2f - nodeGap,
                    firstColor, connected && state.serviceRunning)
                route(size.width / 2f + nodeGap, size.width * 5f / 6f - nodeGap,
                    secondColor, connected)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                TunnelConnectionNode(
                    icon = Icons.Rounded.Smartphone, title = "本机服务",
                    detail = if (state.serviceRunning) ":${state.servicePort}" else "未启动",
                    tone = localTone, modifier = Modifier.weight(1f)
                )
                TunnelConnectionNode(
                    icon = Icons.Rounded.Dns, title = "frp 服务器", detail = serverLabel,
                    tone = serverTone, modifier = Modifier.weight(1f)
                )
                TunnelConnectionNode(
                    icon = Icons.Rounded.Public, title = "公网入口", detail = publicLabel,
                    tone = when {
                        connected && state.serviceRunning -> TunnelTone.Active
                        connected -> TunnelTone.Pending
                        else -> TunnelTone.Quiet
                    }, modifier = Modifier.weight(1f)
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = when { active -> onStop; needsSettings -> onSettings; else -> onStart },
                enabled = !busy && (active || needsSettings || (state.kernelReady && (state.rootMode || state.serviceRunning))),
                modifier = Modifier.weight(1f).heightIn(min = 44.dp), shape = RoundedCornerShape(12.dp),
                colors = appPrimaryButtonColors()
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else Icon(when { active -> Icons.Rounded.Stop; needsSettings -> Icons.Rounded.Tune; else -> Icons.Rounded.PowerSettingsNew }, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(when { busy -> "正在处理…"; active -> "断开连接"; !configured -> "配置隧道"; needsSettings -> "前往启用"; else -> "连接隧道" })
            }
            if (state.running) {
                AppGlassIconButton(onClick = onRestart, enabled = !busy, size = 44.dp) {
                    Icon(Icons.Rounded.Refresh, "重新连接", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun TunnelConnectionNode(
    icon: ImageVector, title: String, detail: String, tone: TunnelTone, modifier: Modifier
) {
    val color by animateColorAsState(tunnelStatusColor(tone), label = "tunnelNode")
    Column(modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Surface(
            modifier = Modifier.size(36.dp), shape = RoundedCornerShape(12.dp),
            color = color.copy(alpha = if (tone == TunnelTone.Quiet) 0.06f else 0.1f),
            border = BorderStroke(1.dp, color.copy(alpha = if (tone == TunnelTone.Quiet) 0.12f else 0.22f))
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(19.dp), tint = color) }
        }
        Text(title, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 2.dp))
        Text(detail, style = MaterialTheme.typography.labelSmall, color = color, textAlign = TextAlign.Center)
    }
}
