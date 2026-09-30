package com.example.danmuapiapp.ui.screen.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.danmuapiapp.data.tunnel.*
import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton
import kotlinx.coroutines.delay

@Composable
fun TunnelScreen(onBack: () -> Unit, onOpenSettings: () -> Unit, viewModel: TunnelViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val log by viewModel.log.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var busy by remember { mutableStateOf(false) }
    var showLog by rememberSaveable { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(lifecycleOwner, showLog) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.refresh().join()
                if (showLog) viewModel.refreshLog().join()
                delay(if (state.rootMode) 15_000L else 5_000L)
            }
        }
    }
    fun notify(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    fun runAction(action: String) {
        busy = true
        val callback: (TunnelActionResult, Boolean) -> Unit = { result, converged ->
            busy = false
            notify(if (result.ok && converged) {
                if (action == "stop") "穿透已停止" else "frpc 已启动，正在确认连接状态"
            } else result.message)
        }
        when (action) {
            "stop" -> viewModel.stopAndWait(callback)
            "restart" -> viewModel.restartAndWait(callback)
            else -> viewModel.startAndWait(callback)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TunnelTopBar("内网穿透", "FRPC · 远程连接", onBack) {
            AppGlassIconButton(onClick = onOpenSettings, size = 36.dp) {
                Icon(Icons.Rounded.Tune, "连接设置", Modifier.size(18.dp))
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TunnelConnectionCard(state, busy, onOpenSettings,
                onStart = { runAction("start") }, onStop = { confirmAction = "stop" },
                onRestart = { confirmAction = "restart" })
            if (!state.kernelReady) TunnelNote("缺少 frpc 内核，请更新应用后再连接。", isError = true)
            if (!state.serviceRunning) TunnelNote(
                if (state.rootMode) "本机弹幕服务未运行。隧道可独立连接，访问弹幕仍需启动服务。"
                else "先在首页启动弹幕服务，再连接隧道。", icon = Icons.Rounded.Dns
            )
            if ((state.state == "error" || state.state == "retrying") && state.lastError.isNotBlank()) {
                TunnelNote(state.lastError, isError = state.state == "error")
            }
            TunnelAddressCard(state, onOpenSettings) { value ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("弹幕公网地址", value))
                if (clipboard != null) notify("已复制完整播放器地址")
            }
            TunnelRouteCard(state, onOpenSettings)
            TunnelLogs(log, showLog, onExpand = { showLog = !showLog },
                onRefresh = { viewModel.refreshLog() }, onClear = { confirmAction = "clear" })
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.Center) {
                Text("frpc ${state.kernelVersion}  ·  ${if (state.rootMode) "Root 运行" else "普通运行"}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    confirmAction?.let { action ->
        AppDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(when (action) { "clear" -> "清空运行日志？"; "restart" -> "重新连接隧道？"; else -> "断开隧道？" }) },
            text = { Text(when (action) {
                "clear" -> "当前 frpc 日志将被清空，后续运行会继续记录。"
                "restart" -> "公网访问会短暂中断，随后使用已保存的配置重新连接。"
                else -> "公网地址将暂时无法访问，本机弹幕服务继续运行。"
            }) },
            confirmButton = { TextButton(onClick = {
                confirmAction = null
                if (action == "clear") viewModel.clearLog { notify(it.message) } else runAction(action)
            }) { Text("确认") } },
            dismissButton = { TextButton(onClick = { confirmAction = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun TunnelAddressCard(state: TunnelUiState, onSettings: () -> Unit, onCopy: (String) -> Unit) {
    var reveal by remember { mutableStateOf(false) }
    val address = derivePublicAddress(state.settings)
    val url = buildPublicApiUrl(address, state.serviceToken)
    val display = if (reveal || state.serviceToken.isBlank()) url else buildPublicApiUrl(address, "••••••••")
    TunnelCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Link, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text("公网地址", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (state.serviceToken.isNotBlank() && url.isNotBlank()) IconButton(onClick = { reveal = !reveal }) {
                Icon(if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (reveal) "隐藏访问令牌" else "显示完整地址", Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (url.isNotBlank()) IconButton(onClick = { onCopy(url) }) {
                Icon(Icons.Rounded.ContentCopy, "复制播放器地址", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            } else TextButton(onClick = onSettings) { Text("配置") }
        }
        if (url.isNotBlank()) {
            Text(display, Modifier.fillMaxWidth(), fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            Text(when {
                !state.running -> "连接隧道后可用，点击右上角复制完整地址。"
                state.linkState == TunnelLinkState.Conflict -> "同名隧道已被占用；此地址可能仍由已有实例提供服务。"
                state.linkState != TunnelLinkState.Connected -> "地址已生成，等待远程链路确认。"
                !state.serviceRunning -> "远程链路已连接，请启动本机弹幕服务。"
                else -> "复制完整地址，填入播放器弹幕接口。"
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("配置完成后显示公网地址，可在高级选项中指定反代地址。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TunnelRouteCard(state: TunnelUiState, onSettings: () -> Unit) {
    val settings = state.settings
    val summary = remember(settings) { if (settings.mode == TunnelMode.Paste) parseFrpcConfig(settings.configText) else null }
    val server = if (settings.mode == TunnelMode.Form) settings.form.serverAddr else summary?.serverAddr.orEmpty()
    val port = if (settings.mode == TunnelMode.Form) settings.form.serverPort else summary?.serverPort
    TunnelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Dns, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text("连接详情", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = onSettings) { Text("管理"); Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp)) }
        }
        TunnelDetail("服务器", if (server.isBlank()) "尚未配置" else "$server:${port ?: "—"}")
        TunnelDetail("映射方式", if (settings.mode == TunnelMode.Form) settings.form.proxyType.label else "导入配置 · ${summary?.proxies?.size ?: 0} 条")
        TunnelDetail("自动启动", if (settings.autoStart) "跟随服务" else "手动连接")
        if (state.pid > 0 || state.restarts > 0) Text(
            "进程 ${state.pid.takeIf { it > 0 } ?: "—"}  /  累计重启 ${state.restarts} 次",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
