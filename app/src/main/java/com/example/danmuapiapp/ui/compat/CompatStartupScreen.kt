package com.example.danmuapiapp.ui.compat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.data.service.RuntimeWarmupCoordinator
import com.example.danmuapiapp.domain.model.GlassMaterialPreference
import com.example.danmuapiapp.ui.theme.DanmuApiTheme

@Composable
internal fun CompatStartupScreen(darkTheme: Boolean, state: RuntimeWarmupCoordinator.UiState,
    error: String? = null, onRetry: () -> Unit = {}) {
    DanmuApiTheme(darkTheme = darkTheme, glassMaterial = GlassMaterialPreference.Off) {
        CompatConsoleTheme(dark = darkTheme) { CompatStartupContent(state, error, onRetry) }
    }
}

@Composable
internal fun CompatStartupContent(state: RuntimeWarmupCoordinator.UiState,
    error: String? = null, onRetry: () -> Unit = {}) {
    val running = state as? RuntimeWarmupCoordinator.UiState.Running
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp).verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 520.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(Icons.Rounded.Terminal, null, Modifier.padding(12.dp).size(28.dp),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("弹幕 API", style = MaterialTheme.typography.titleLarge)
                            Text("兼容控制台", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (error == null) {
                        Text(running?.title ?: "正在准备控制台", style = MaterialTheme.typography.titleMedium)
                        Text(running?.detail ?: "正在读取本机配置，即将显示服务管理界面。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("首次安装或更新后需要准备运行环境。低性能设备可能稍慢，请保持应用打开。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("暂时无法完成初始化", style = MaterialTheme.typography.titleMedium)
                        Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        CompatButton("重试", onRetry, Modifier.fillMaxWidth(), tone = CompatActionTone.Primary)
                    }
                }
            }
        }
    }
}
