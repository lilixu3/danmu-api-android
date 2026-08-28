package com.example.danmuapiapp.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.desktop.node.GithubProxyCatalog
import com.example.danmuapiapp.desktop.runtime.DesktopPaths
import com.example.danmuapiapp.desktop.runtime.DesktopSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置页（基础功能）：运行目录自定义 + GitHub 线路测速与选择。
 * 运行目录更改保存后需重启应用生效；GitHub 线路即时生效（影响后续核心下载）。
 */
@Composable
fun SettingsPage(settings: DesktopSettings, paths: DesktopPaths) {
    val scope = rememberCoroutineScope()
    var runtimeRootText by remember { mutableStateOf(settings.runtimeRootOverride ?: "") }
    var savedHint by remember { mutableStateOf("") }
    var latencies by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var testing by remember { mutableStateOf(false) }
    var selectedProxy by remember { mutableStateOf(settings.githubProxyId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.PagePadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        Card(shape = RoundedCornerShape(Dimens.CardCorner), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "运行目录",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "当前生效：${paths.root.absolutePath}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = runtimeRootText,
                    onValueChange = { runtimeRootText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    label = { Text("自定义运行目录（留空恢复默认）") },
                    supportingText = { Text("运行时、.env 配置、.cache 缓存、日志与下载都会放在该目录下；更改保存后重启应用生效") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        settings.setRuntimeRoot(runtimeRootText.trim().ifBlank { null })
                        savedHint = "已保存。重启应用后生效。"
                    }) { Text("保存") }
                    OutlinedButton(onClick = {
                        runtimeRootText = ""
                        settings.setRuntimeRoot(null)
                        savedHint = "已恢复默认运行目录。重启应用后生效。"
                    }) { Text("恢复默认") }
                    if (savedHint.isNotBlank()) {
                        Text(
                            text = savedHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                }
            }
        }

        Card(shape = RoundedCornerShape(Dimens.CardCorner), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "GitHub 线路",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        if (testing) return@OutlinedButton
                        testing = true
                        latencies = emptyMap()
                        scope.launch {
                            latencies = withContext(Dispatchers.IO) {
                                GithubProxyCatalog.testAllLatencies()
                            }
                            testing = false
                        }
                    }) { Text(if (testing) "测速中…" else "并行测速") }
                }
                Text(
                    text = "核心与后续 GitHub 资源下载将走所选线路（多候选自动回退）；无法直连 GitHub 时选择镜像线路。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GithubProxyCatalog.options.forEach { option ->
                    val latency = latencies[option.id]
                    val bestId = latencies.filterValues { it >= 0 }.minByOrNull { it.value }?.key
                    Surface(
                        onClick = {
                            selectedProxy = option.id
                            settings.setGithubProxy(option.id)
                        },
                        shape = RoundedCornerShape(Dimens.ItemCorner),
                        color = if (selectedProxy == option.id) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(14.dp)
                                    .padding(3.dp)
                                    .background(
                                        if (selectedProxy == option.id) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                        CircleShape,
                                    ),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.label + if (selectedProxy == option.id) "（当前使用）" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selectedProxy == option.id) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                            when {
                                testing -> if (latencies.isEmpty()) {
                                    CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp))
                                }
                                latency == null -> {}
                                latency >= 0 -> Text(
                                    text = "$latency ms" + if (option.id == bestId) " · 最快" else "",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (option.id == bestId) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                else -> Text(
                                    text = "不可用",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
