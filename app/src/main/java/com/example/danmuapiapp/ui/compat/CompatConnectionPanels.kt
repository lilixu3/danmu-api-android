package com.example.danmuapiapp.ui.compat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.data.service.TvConfigSyncCodec
import com.example.danmuapiapp.domain.model.ServiceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun CompatAddressSection(
    state: CompatModeUiState,
    permissionMissing: Boolean,
    onGrant: () -> Unit
) {
    val runtime = state.runtimeState
    val status = CompatAccessAddressStatusPolicy.resolve(runtime.status, runtime.localUrl.isNotBlank(),
        runtime.lanUrl.isNotBlank(), runtime.lanIpv6Url.isNotBlank(), permissionMissing)
    val lanEntries = listOf("IPv4" to runtime.lanUrl, "IPv6" to runtime.lanIpv6Url)
        .filter { it.second.isNotBlank() }
    val layout = LocalCompatLayout.current
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CompatHomeCardHeader("访问地址", "复制链接或扫码连接播放器", Icons.Rounded.Lan)
        if (lanEntries.isEmpty()) {
            CompatCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.WifiOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (permissionMissing) "允许访问局域网后，可供其他设备连接" else "暂未获取局域网地址",
                            style = MaterialTheme.typography.bodyMedium)
                        Text(if (runtime.status != ServiceStatus.Running) "启动服务后会显示可用地址。"
                            else "请连接 Wi-Fi 或有线网络。", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else if (layout.twoColumns && lanEntries.size == 2) {
            val cardWidth = (layout.contentWidth - 12f) / 2f
            // Only these content-sized cards share a height. The dashboard itself never stretches.
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                lanEntries.forEach { (label, url) ->
                    CompatLanAddress(label, url, (cardWidth - 32f) / fontScale >= 300f,
                        Modifier.weight(1f).fillMaxHeight())
                }
            }
        } else {
            lanEntries.forEach { (label, url) ->
                CompatLanAddress(label, url, (layout.contentWidth - 32f) / fontScale >= 300f)
            }
        }
        if (runtime.localUrl.isNotBlank()) CompatLocalAddress(runtime.localUrl)
        val hint = when {
            runtime.status != ServiceStatus.Running && (lanEntries.isNotEmpty() || runtime.localUrl.isNotBlank()) ->
                "服务当前未运行，上次地址仅供参考。"
            status == CompatAccessAddressStatus.LocalOnly -> "局域网访问尚未允许，目前仅本机可用。"
            status == CompatAccessAddressStatus.Ready -> "其他设备需连接同一 Wi-Fi 或有线网络；本机地址仅供当前设备使用。"
            runtime.localUrl.isNotBlank() -> "本机地址仅供当前设备上的播放器使用。"
            else -> "地址会在服务准备就绪后显示。"
        }
        Text(hint, style = MaterialTheme.typography.bodySmall,
            color = if (runtime.status != ServiceStatus.Running || permissionMissing) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurfaceVariant)
        if (permissionMissing) CompatButton("允许局域网访问", onGrant, icon = Icons.Rounded.Wifi)
    }
}

@Composable
private fun CompatLanAddress(label: String, url: String, sideBySide: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Lan, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text("局域网 $label", style = MaterialTheme.typography.titleMedium)
            }
            val detail: @Composable ColumnScope.() -> Unit = {
                SelectionContainer {
                    Text(url, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                }
                CompatCopyButton("复制地址", url)
            }
            if (sideBySide) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), content = detail)
                    CompatQrCode(url, "局域网 $label 地址二维码", 132)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    detail()
                    CompatQrCode(url, "局域网 $label 地址二维码", 132)
                }
            }
        }
    }
}

@Composable
private fun CompatLocalAddress(url: String) {
    val stacked = (LocalCompatLayout.current.contentWidth - 32f) /
        LocalDensity.current.fontScale.coerceAtLeast(1f) < 420f
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        val detail: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.PhoneAndroid, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("本机访问", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SelectionContainer {
                        Text(url, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                    }
                }
            }
        }
        if (stacked) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                detail()
                CompatCopyButton("复制本机地址", url)
            }
        } else {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) { detail() }
                CompatCopyButton("复制本机地址", url)
            }
        }
    }
}

@Composable
internal fun CompatSyncCard(sync: CompatTvConfigSyncServer.UiState) {
    CompatCard {
        CompatCardTitle("设备配置同步", "从另一台手机传入核心配置", Icons.Rounded.Devices)
        CompatBadge(if (sync.isReady && sync.inviteUrl.isNotBlank()) "同步码已准备" else "等待网络",
            if (sync.isReady) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val sideBySide = maxWidth.value / LocalDensity.current.fontScale.coerceAtLeast(1f) >= 600f
            val detail: @Composable ColumnScope.() -> Unit = {
                Text(sync.statusText, style = MaterialTheme.typography.bodyMedium)
                Text("在另一台设备的弹幕 API App 中打开“备份与恢复”，扫描这里的同步码，将配置发送到本机。",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (sync.lastSyncSummary.isNotBlank()) Text(sync.lastSyncSummary,
                    color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                if (sync.inviteUrl.isNotBlank()) {
                    CompatCopyButton("复制同步链接", sync.inviteUrl)
                    Text("同步链接包含本次会话授权，请仅分享给自己的设备。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (sideBySide) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), content = detail)
                    CompatQrCode(sync.inviteUrl, "配置同步二维码", 176)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    detail()
                    CompatQrCode(sync.inviteUrl, "配置同步二维码", 176)
                }
            }
        }
    }
}

@Composable
internal fun CompatCopyButton(label: String, value: String) {
    val context = LocalContext.current
    CompatButton(label, {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    }, icon = Icons.Rounded.ContentCopy)
}

@Composable
internal fun CompatQrCode(value: String, description: String, size: Int) {
    // A source URL change clears the previous code immediately while generation runs.
    // That prevents an old endpoint or an expired sync invitation from being scanned.
    key(value) {
        val image by produceState<ImageBitmap?>(initialValue = null, value) {
            if (value.isNotBlank()) {
                this.value = withContext(Dispatchers.Default) {
                    runCatching { TvConfigSyncCodec.buildQrBitmap(value, 384).asImageBitmap() }.getOrNull()
                }
            }
        }
        Box(Modifier.size(size.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp),
            contentAlignment = Alignment.Center) {
            if (image != null) Image(image!!, description, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Rounded.QrCode2, null, tint = Color(0xFF64748B), modifier = Modifier.size(56.dp))
        }
    }
}
