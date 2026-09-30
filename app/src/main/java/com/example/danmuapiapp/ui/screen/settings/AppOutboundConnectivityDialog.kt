package com.example.danmuapiapp.ui.screen.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.data.repository.runCatchingCancellable
import com.example.danmuapiapp.data.service.AppOutboundConnectivityResult
import com.example.danmuapiapp.data.network.GithubConnectivityProbe
import com.example.danmuapiapp.data.service.AppOutboundSettings
import com.example.danmuapiapp.data.service.AppOutboundSettingsStore
import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.AppDialogStyle
import com.example.danmuapiapp.ui.component.AppDialogTone
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val connectivityLabels = linkedMapOf(
    "bahamut" to "巴哈姆特", "tmdb" to "TMDB", "dandan" to "弹弹play", "animeko" to "Animeko"
)

@Composable
internal fun AppOutboundConnectivityDialog(github: Boolean = false, onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val connectivitySources = remember(github) {
        if (github) GithubConnectivityProbe.labels.keys.toList() else AppOutboundSettings.supportedSources.filter { it in AppOutboundSettingsStore.read(context).sources }
    }
    var runId by remember { mutableIntStateOf(0) }
    var testing by remember { mutableStateOf(connectivitySources.isNotEmpty()) }
    var results by remember { mutableStateOf<Map<String, AppOutboundConnectivityResult>>(emptyMap()) }

    LaunchedEffect(runId) {
        testing = connectivitySources.isNotEmpty()
        results = emptyMap()
        try {
            coroutineScope {
                connectivitySources.forEach { source ->
                    launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatchingCancellable {
                                if (github) GithubConnectivityProbe.test(context, source) else AppOutboundSettingsStore.diagnose(context, source)
                            }
                                .getOrElse { AppOutboundConnectivityResult(source, connected = false) }
                        }
                        results = results + (source to result)
                    }
                }
            }
        } finally {
            testing = false
        }
    }

    AppDialog(
        onDismissRequest = onDismiss,
        style = AppDialogStyle.Status,
        tone = AppDialogTone.Brand,
        icon = { Icon(Icons.Rounded.NetworkCheck, contentDescription = null) },
        title = { Text(if (github) "GitHub 连通测试" else "连通性测试") },
        supportingText = { Text(if (connectivitySources.isEmpty()) "请先选择需要增强的来源" else if (github) "按当前 GitHub 线路并行测试" else "已选来源并行测试") },
        text = {
            val completed = results.size
            val connected = results.values.count { it.connected }
            val allConnected = connectivitySources.isNotEmpty() && connected == connectivitySources.size
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = if (testing) "正在检测连接…" else if (allConnected) "连接正常" else "测试完成",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (testing) "${connectivitySources.size} 项同时检测" else "$connected / ${connectivitySources.size} 个来源已连通",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "$completed / ${connectivitySources.size}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            LinearProgressIndicator(
                progress = { if (connectivitySources.isEmpty()) 0f else completed.toFloat() / connectivitySources.size },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            )
            Spacer(Modifier.height(2.dp))
            connectivitySources.forEach { source ->
                ConnectivityResultCard(
                    label = if (github) GithubConnectivityProbe.labels.getValue(source) else connectivityLabels.getValue(source),
                    icon = if (source == "tmdb") Icons.Rounded.Public else Icons.Rounded.PlayCircle,
                    result = results[source]
                )
            }
        },
        dismissButton = { AppGlassButton(onClick = onDismiss) { Text("关闭") } },
        confirmButton = {
            AppGlassButton(
                onClick = {
                    results = emptyMap()
                    testing = true
                    runId++
                },
                enabled = !testing && connectivitySources.isNotEmpty(),
                tint = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("重新测试")
            }
        }
    )
}

@Composable
private fun ConnectivityResultCard(
    label: String,
    icon: ImageVector,
    result: AppOutboundConnectivityResult?
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val successColor = if (dark) Color(0xFF81C995) else Color(0xFF1F7A4D)
    val failureColor = if (dark) Color(0xFFFF8A80) else Color(0xFFB3261E)
    val accent by animateColorAsState(
        targetValue = when {
            result == null -> MaterialTheme.colorScheme.primary
            result.connected -> successColor
            else -> failureColor
        },
        label = "connectivity-result-color"
    )
    AppGlassSurface(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = if (dark) 0.10f else 0.05f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.20f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.12f),
                contentColor = accent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (result == null) {
                        CircularProgressIndicator(modifier = Modifier.size(13.dp), color = accent, strokeWidth = 1.5.dp)
                    } else {
                        Icon(
                            imageVector = if (result.connected) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = when {
                            result == null -> "检测中"
                            result.connected -> "已连通"
                            else -> "未连通"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = accent
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = if (result?.connected == true) result.durationMs?.toString() ?: "—" else "—",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (result == null) MaterialTheme.colorScheme.onSurfaceVariant else accent
                )
                Text(
                    text = if (result?.connected == true && result.durationMs != null) "ms" else "延迟",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
