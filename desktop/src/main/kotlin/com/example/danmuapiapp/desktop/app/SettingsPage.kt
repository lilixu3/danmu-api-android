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
import androidx.compose.material3.Switch
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
import com.example.danmuapiapp.desktop.runtime.AutostartManager
import com.example.danmuapiapp.desktop.runtime.DesktopPaths
import com.example.danmuapiapp.desktop.runtime.DesktopRuntimeConfigResolver
import com.example.danmuapiapp.desktop.runtime.DesktopSettings
import kotlinx.coroutines.Dispatchers
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置页（基础功能）：运行目录自定义 + GitHub 线路测速与选择。
 * 运行目录更改保存后需重启应用生效；GitHub 线路即时生效（影响后续核心下载）。
 */
@Composable
fun SettingsPage(
    settings: DesktopSettings,
    paths: DesktopPaths,
    themePreference: ThemePreference,
    onThemeChange: (ThemePreference) -> Unit,
    onRuntimeConfigChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val configuredRuntime = remember(settings, paths.root.absolutePath) {
        DesktopRuntimeConfigResolver.resolve(
            settings,
            File(paths.runtimeDir, "nodejs-project"),
        )
    }
    var runtimeRootText by remember { mutableStateOf(settings.runtimeRootOverride ?: "") }
    var portText by remember { mutableStateOf(settings.portOverride?.toString() ?: configuredRuntime.port.toString()) }
    var listenHostText by remember { mutableStateOf(settings.listenHostOverride ?: configuredRuntime.listenHost) }
    var savedHint by remember { mutableStateOf("") }
    var latencies by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var testing by remember { mutableStateOf(false) }
    var selectedProxy by remember { mutableStateOf(settings.githubProxyId) }
    var themePref by remember { mutableStateOf(themePreference) }
    var closeActionPref by remember { mutableStateOf(settings.closeAction) }
    var autostartEnabled by remember { mutableStateOf(AutostartManager.isEnabled()) }
    var autostartError by remember { mutableStateOf<String?>(null) }
    var githubTokenText by remember { mutableStateOf(settings.githubToken) }
    var tokenHint by remember { mutableStateOf("") }

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
                    text = "外观",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemePreference.entries.forEach { pref ->
                        Surface(
                            onClick = {
                                themePref = pref
                                settings.setTheme(pref.key)
                                onThemeChange(pref)
                            },
                            shape = RoundedCornerShape(Dimens.ItemCorner),
                            color = if (themePref == pref) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            contentColor = if (themePref == pref) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        ) {
                            Text(
                                text = pref.label,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }

        Card(shape = RoundedCornerShape(Dimens.CardCorner), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "关闭窗口行为",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "ask" to "每次询问",
                        "tray" to "后台运行",
                        "exit" to "退出并关闭服务",
                    ).forEach { (key, label) ->
                        Surface(
                            onClick = {
                                settings.setCloseAction(key)
                                closeActionPref = key
                            },
                            shape = RoundedCornerShape(Dimens.ItemCorner),
                            color = if (closeActionPref == key) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            contentColor = if (closeActionPref == key) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                Text(
                    text = "「后台运行」关闭窗口后服务继续，托盘图标可恢复窗口；「退出并关闭服务」会停止 node.exe。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(shape = RoundedCornerShape(Dimens.CardCorner), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "通用",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "开机自启", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "开机后台自动启动弹幕服务（无窗口常驻）；打开应用即可管理服务",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autostartEnabled,
                        onCheckedChange = { checked ->
                            val error = if (checked) AutostartManager.enable() else AutostartManager.disable()
                            autostartError = error
                            autostartEnabled = if (error == null) checked else AutostartManager.isEnabled()
                        },
                        enabled = AutostartManager.isSupported(),
                    )
                }
                if (!AutostartManager.isSupported()) {
                    Text(
                        text = "开发运行模式不支持开机自启，请使用打包版应用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!autostartError.isNullOrBlank()) {
                    Text(
                        text = autostartError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Card(shape = RoundedCornerShape(Dimens.CardCorner), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "GitHub Token",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = githubTokenText,
                    onValueChange = { githubTokenText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    label = { Text("Personal Access Token（可选）") },
                    supportingText = { Text("用于提升 GitHub API 限额（核心版本检查/资源下载）；仅保存在本机设置文件中") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        settings.setGithubToken(githubTokenText)
                        tokenHint = "已保存。"
                    }) { Text("保存") }
                    if (githubTokenText.isBlank()) {
                        Text(
                            text = "未设置（匿名限额）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                    if (tokenHint.isNotBlank()) {
                        Text(
                            text = tokenHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                }
            }
        }

        Card(shape = RoundedCornerShape(Dimens.CardCorner), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "服务配置",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("端口") },
                        placeholder = { Text("9321") },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        supportingText = { Text("默认 9321；运行中修改将重启服务") },
                    )
                    OutlinedTextField(
                        value = listenHostText,
                        onValueChange = { listenHostText = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("监听地址") },
                        placeholder = { Text("0.0.0.0") },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        supportingText = { Text("默认 0.0.0.0；运行中修改将重启服务") },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        settings.setPortOverride(portText.toIntOrNull()?.takeIf { it in 1..65535 })
                        settings.setListenHostOverride(listenHostText.trim().ifBlank { null })
                        onRuntimeConfigChanged()
                        savedHint = "已保存。运行中会重启服务，未运行则下次启动生效。"
                    }) { Text("保存服务配置") }
                    Text(
                        text = "配置优先级：设置值 → 运行目录 config/.env → 默认值",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
        }

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
