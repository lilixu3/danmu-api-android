package com.example.danmuapiapp.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.data.service.AppOutboundSettings
import com.example.danmuapiapp.data.service.AppOutboundSettingsStore
import com.example.danmuapiapp.ui.component.*
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AppOutboundSettingsSection() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(AppOutboundSettingsStore.read(context)) }
    var status by remember { mutableStateOf("正在读取状态…") }
    var message by remember { mutableStateOf<String?>(null) }
    var advanced by remember { mutableStateOf(false) }
    var doh by remember { mutableStateOf(settings.dohUrl) }
    var timeout by remember { mutableStateOf(settings.connectTimeoutMs.toString()) }
    var saving by remember { mutableStateOf(false) }
    var showConnectivityDialog by remember { mutableStateOf(false) }

    fun save(next: AppOutboundSettings) {
        if (saving) return
        saving = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { AppOutboundSettingsStore.save(context, next) } }
            if (result.isSuccess) {
                settings = next
                message = "已保存；运行中的服务会自动应用，新的 GitHub 连接使用新配置"
            } else message = result.exceptionOrNull()?.message ?: "保存失败"
            saving = false
        }
    }
    LaunchedEffect(context) {
        while (true) {
            status = withContext(Dispatchers.IO) { AppOutboundSettingsStore.statusText(context) }
            delay(3000)
        }
    }
    SettingsGroup(title = "增强直连") {
        SettingsSwitchItem(
            title = "App 增强直连",
            subtitle = "App 独立提供 DNS、ECH 与 H2/H3，无需核心支持",
            icon = Icons.Rounded.NetworkCheck,
            checked = settings.enabled,
            enabled = !saving,
            onCheckedChange = { save(settings.copy(enabled = it)) }
        )
        SettingsDivider()
        SettingsValueItem(title = "运行状态", value = status, icon = Icons.Rounded.Public)
        SettingsDivider()
        for ((source, label) in listOf("bahamut" to "巴哈姆特", "tmdb" to "TMDB", "dandan" to "弹弹play", "animeko" to "Animeko")) {
            SettingsSwitchItem(
                title = label,
                subtitle = when (source) {
                    "bahamut" -> "必须协商 ECH；不会降级为普通 SNI"
                    "dandan" -> "增强弹幕接口连接，支持 ECH"
                    "animeko" -> "按节点增强连接，保留自动切换"
                    else -> "增强解析，不强制 ECH"
                },
                checked = source in settings.sources,
                enabled = !saving,
                onCheckedChange = { enabled ->
                    save(settings.copy(sources = if (enabled) settings.sources + source else settings.sources - source))
                }
            )
        }
        SettingsDivider()
        SettingsItem(
            title = "连接协议",
            subtitle = "auto 自动选择；h3 强制且失败不回退",
            trailing = { Text(settings.httpVersion) },
            onClick = {
                val values = listOf("auto", "h2", "h3")
                save(settings.copy(httpVersion = values[(values.indexOf(settings.httpVersion) + 1) % values.size]))
            }
        )
        SettingsDivider()
        SettingsItem(
            title = "连通性测试",
            subtitle = "并行检测已选来源的连接和延迟",
            icon = Icons.Rounded.NetworkCheck,
            onClick = { showConnectivityDialog = true }
        )
        SettingsDivider()
        SettingsItem(title = "高级设置", subtitle = "DoH 与连接超时", icon = Icons.Rounded.Settings,
            onClick = { doh = settings.dohUrl; timeout = settings.connectTimeoutMs.toString(); advanced = true })
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("处理所选来源的源站请求；已配置的代理、反代优先。开关不改变搜索来源。", style = MaterialTheme.typography.bodySmall)
            message?.let { Spacer(Modifier.height(8.dp)); Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (showConnectivityDialog) {
        AppOutboundConnectivityDialog(onDismiss = { showConnectivityDialog = false })
    }
    if (advanced) {
        AppDialog(
            onDismissRequest = { advanced = false },
            title = { Text("增强直连高级设置") },
            text = {
                Column {
                    OutlinedTextField(value = doh, onValueChange = { doh = it }, label = { Text("DoH HTTPS 地址") },
                        supportingText = { Text("留空使用内置解析策略") }, singleLine = true)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = timeout, onValueChange = { timeout = it }, label = { Text("单次连接超时（毫秒）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                }
            },
            confirmButton = {
                AppGlassButton(onClick = {
                    val value = timeout.toIntOrNull()
                    if (value == null) message = "请输入有效连接超时" else {
                        val next = settings.copy(dohUrl = doh.trim(), connectTimeoutMs = value)
                        val valid = runCatching { next.validate() }
                        if (valid.isSuccess) { save(next); advanced = false }
                        else message = valid.exceptionOrNull()?.message
                    }
                }) { Text("保存") }
            },
            dismissButton = { AppGlassButton(onClick = { advanced = false }) { Text("取消") } }
        )
    }
}
