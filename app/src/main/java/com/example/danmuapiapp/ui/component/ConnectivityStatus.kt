package com.example.danmuapiapp.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.example.danmuapiapp.data.service.AppOutboundConnectivityResult

@Composable
internal fun probeGreen(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < .5f) Color(0xFF81C784) else Color(0xFF2E7D32)

internal fun connectivityStatusLabel(result: AppOutboundConnectivityResult?, pending: Boolean): String = when {
    pending -> "检测中…"
    result?.unconfirmed == true -> "未确认"
    result?.configurationRequired == true && result.connected -> "已响应 · API Key 未配置"
    result?.configurationRequired == true -> "未配置"
    result?.connected != true -> "未连通"
    result.source == "local" -> "无需联网"
    result.durationMs != null -> "${result.durationMs} ms"
    else -> "已连通"
}

@Composable
internal fun ConnectivityStatus(result: AppOutboundConnectivityResult?, pending: Boolean) {
    if (result == null && !pending) return
    Column(Modifier.widthIn(max = 156.dp).semantics { liveRegion = LiveRegionMode.Polite }, horizontalAlignment = Alignment.End) {
        Text(
            text = connectivityStatusLabel(result, pending),
            color = when {
                pending -> MaterialTheme.colorScheme.onSurfaceVariant
                result?.unconfirmed == true -> MaterialTheme.colorScheme.onSurfaceVariant
                result?.connected == true -> probeGreen()
                result?.configurationRequired == true -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.error
            },
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.End
        )
        if (!pending && result?.connected == true && result.configurationRequired && result.durationMs != null) {
            Text("${result.durationMs} ms", style = MaterialTheme.typography.labelSmall, color = probeGreen())
        }
    }
}
