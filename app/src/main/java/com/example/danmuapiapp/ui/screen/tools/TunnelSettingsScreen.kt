package com.example.danmuapiapp.ui.screen.tools

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.data.tunnel.*
import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.theme.appPrimaryButtonColors

@Composable
fun TunnelSettingsScreen(onBack: () -> Unit, viewModel: TunnelViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val kernelUpdate by viewModel.kernelUpdate.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val saved by viewModel.savedDraft.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var attempted by rememberSaveable { mutableStateOf(false) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var showKernel by rememberSaveable { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    val dirty = draft != null && draft != saved
    val leave = { if (!saving) { if (dirty) dialog = "leave" else onBack() } }
    BackHandler(enabled = dirty || saving) { leave() }
    fun save() {
        focus.clearFocus()
        viewModel.saveDraft { result ->
            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
            if (result.ok) attempted = false
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TunnelTopBar("连接设置", if (dirty) "FRPC · 有未保存的更改" else "FRPC · 配置你的远程连接", leave)
        val value = draft
        if (value == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val check = remember(value, state.servicePort) { value.check(state.servicePort) }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TunnelChoice(
                    labels = listOf("可视化配置", "导入配置"), selected = if (value.mode == TunnelMode.Form) 0 else 1,
                    onSelect = { viewModel.editDraft(value.copy(mode = if (it == 0) TunnelMode.Form else TunnelMode.Paste)); attempted = false }
                )
                if (value.mode == TunnelMode.Form) {
                    TunnelServerSection(value, attempted, viewModel::editDraft)
                    TunnelMappingSection(value, state.servicePort, attempted, viewModel::editDraft)
                } else {
                    TunnelImportSection(value, state.servicePort, attempted, viewModel::editDraft)
                }
                TunnelCard {
                    TunnelSectionTitle("运行选项", Icons.Rounded.PowerSettingsNew)
                    TunnelToggle("启用内网穿透", "保存后生效", value.enabled) { viewModel.editDraft(value.copy(enabled = it)) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    TunnelToggle("自动启动", if (state.rootMode) "跟随 Root 开机自启脚本。" else "跟随弹幕服务启动",
                        value.autoStart) { viewModel.editDraft(value.copy(autoStart = it)) }
                }
                TunnelCard {
                    TunnelDisclosure("高级选项", "传输加密、DNS 与公网地址", Icons.Rounded.Security, showAdvanced) { showAdvanced = !showAdvanced }
                    AnimatedVisibility(showAdvanced) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (value.mode == TunnelMode.Form) TunnelToggle("TLS 加密", "加密手机与 frp 服务器之间的传输。", value.tlsEnabled) {
                                viewModel.editDraft(value.copy(tlsEnabled = it))
                            }
                            TunnelField(value.dnsServer, { viewModel.editDraft(value.copy(dnsServer = it)) }, "DNS 服务器", "223.5.5.5",
                                helper = if (value.mode == TunnelMode.Form) "用于解析 frp 服务器域名。" else "开启自动补全时，填入配置缺失的 DNS。")
                            TunnelField(value.publicAddress, { viewModel.editDraft(value.copy(publicAddress = it)) }, "公网地址覆盖", "https://danmu.example.com",
                                keyboardType = KeyboardType.Uri, helper = "可选。使用域名反代时填写，留空则自动生成。")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    TunnelDisclosure("frpc 内核", "当前 ${state.kernelVersion} · ${if (state.rootMode) "支持在线更新" else "随应用更新"}",
                        Icons.Rounded.Memory, showKernel) { showKernel = !showKernel }
                    AnimatedVisibility(showKernel) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (state.rootMode) "检查新版本并更新独立运行的 frpc 内核。" else "普通模式使用应用内置的 frpc 内核，升级应用即可更新。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            // 检查/更新只在 Root 模式提供；普通模式无法执行下载的内核，只显示版本与说明
                            if (state.rootMode) {
                                OutlinedButton(onClick = { viewModel.checkKernelUpdate() }, enabled = !kernelUpdate.checking && !kernelUpdate.applying,
                                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                                    if (kernelUpdate.checking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp)); Text(if (kernelUpdate.checking) "正在检查…" else "检查更新")
                                }
                                if (kernelUpdate.message.isNotBlank()) Text(kernelUpdate.message, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (kernelUpdate.available) Button(onClick = { dialog = "update" }, enabled = !kernelUpdate.applying,
                                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                                    if (kernelUpdate.applying) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp)); Text(if (kernelUpdate.applying) "正在更新…" else "更新至 ${kernelUpdate.latest}")
                                }
                            }
                        }
                    }
                }
                if (attempted && !check.ok) TunnelNote(check.errors.joinToString("\n"), isError = true)
                Spacer(Modifier.height(4.dp))
            }
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(when {
                        attempted && !check.ok -> check.errors.first()
                        saving -> "正在保存配置…"
                        state.running && dirty -> "保存将重新连接隧道"
                        dirty -> "有未保存的更改"
                        !tunnelConfigured(state.settings) -> "填写连接信息后保存"
                        else -> "配置已保存"
                    }, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        color = if (attempted && !check.ok) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = {
                        attempted = true
                        focus.clearFocus()
                        if (check.ok) { if (state.running) dialog = "save" else save() }
                        else showAdvanced = true
                    }, enabled = !saving && dirty, modifier = Modifier.heightIn(min = 44.dp), shape = RoundedCornerShape(12.dp),
                        colors = appPrimaryButtonColors()) {
                        if (saving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text(if (saving) "保存中…" else "保存配置")
                    }
                }
            }
        }
    }
    dialog?.let { action ->
        AppDialog(onDismissRequest = { dialog = null },
            title = { Text(when (action) { "leave" -> "保留这次修改？"; "update" -> "更新 frpc 内核？"; else -> "应用新的连接配置？" }) },
            text = { Text(when (action) {
                "leave" -> "当前有未保存的更改，离开此页面后将丢弃。"
                "update" -> "将下载并替换 frpc 内核。正在运行的公网连接可能短暂中断。"
                else -> if (draft?.enabled == false) "保存后将停用隧道，公网地址暂时无法访问。" else "保存后隧道将重新连接，公网访问会短暂中断。"
            }) },
            confirmButton = { TextButton(onClick = {
                dialog = null
                when (action) { "leave" -> onBack(); "update" -> viewModel.applyKernelUpdate(); else -> save() }
            }) { Text(if (action == "leave") "放弃并离开" else "确认") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(if (action == "leave") "继续编辑" else "取消") } }
        )
    }
}

@Composable
private fun TunnelServerSection(value: TunnelDraft, attempted: Boolean, edit: (TunnelDraft) -> Unit) {
    TunnelCard {
        TunnelSectionTitle("服务器", Icons.Rounded.Dns)
        TunnelField(value.serverAddr, { edit(value.copy(serverAddr = it)) }, "服务器地址", "frp.example.com",
            error = if (attempted && value.serverAddr.isBlank()) "请填写服务器地址" else null)
        TunnelField(value.serverPort, { edit(value.copy(serverPort = it)) }, "服务端口", "7000", keyboardType = KeyboardType.Number,
            error = if (attempted && value.serverPort.toIntOrNull() !in 1..65535) "端口范围为 1–65535" else null)
        TunnelField(value.authToken, { edit(value.copy(authToken = it)) }, "认证 Token", "填写服务商提供的令牌", secret = true,
            helper = "服务商未要求认证时可留空。")
    }
}

@Composable
private fun TunnelMappingSection(value: TunnelDraft, port: Int, attempted: Boolean, edit: (TunnelDraft) -> Unit) {
    TunnelCard {
        TunnelSectionTitle("端口映射", Icons.Rounded.Lan)
        TunnelChoice(listOf("TCP 端口", "HTTP 域名"), if (value.proxyType == TunnelProxyType.Tcp) 0 else 1,
            onSelect = { edit(value.copy(proxyType = if (it == 0) TunnelProxyType.Tcp else TunnelProxyType.Http)) })
        if (value.proxyType == TunnelProxyType.Tcp) {
            TunnelField(value.remotePort, { edit(value.copy(remotePort = it)) }, "公网端口", "服务商分配的远程端口", keyboardType = KeyboardType.Number,
                error = if (attempted && value.remotePort.toIntOrNull() !in 1..65535) "端口范围为 1–65535" else null)
        } else {
            TunnelField(value.customDomains, { edit(value.copy(customDomains = it)) }, "自定义域名", "danmu.example.com",
                helper = "多个域名用逗号分隔，与子域名至少填写一项。",
                error = if (attempted && value.customDomains.isBlank() && value.subdomain.isBlank()) "请填写域名或子域名" else null)
            TunnelField(value.subdomain, { edit(value.copy(subdomain = it)) }, "子域名", "服务商分配的子域名前缀")
        }
        TunnelField(value.proxyName, { edit(value.copy(proxyName = it)) }, "隧道名称", "danmu-api", helper = "用于在服务器上识别这条隧道。")
        TunnelNote("本机 127.0.0.1:$port · 自动跟随服务端口", icon = Icons.Rounded.Smartphone)
    }
}

@Composable
private fun TunnelImportSection(value: TunnelDraft, port: Int, attempted: Boolean, edit: (TunnelDraft) -> Unit) {
    val context = LocalContext.current
    val summary = remember(value.pasteText) { parseFrpcConfig(value.pasteText) }
    val check = remember(value, port) { value.check(port) }
    TunnelCard {
        TunnelSectionTitle("导入配置", Icons.Rounded.Code)
        OutlinedTextField(value = value.pasteText, onValueChange = { edit(value.copy(pasteText = it)) },
            modifier = Modifier.fillMaxWidth(), minLines = 5, maxLines = 10, shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            label = { Text("TOML / INI / JSON / YAML") }, placeholder = { Text("将服务商提供的完整配置粘贴到这里", style = MaterialTheme.typography.bodySmall) },
            isError = attempted && !check.ok, keyboardOptions = KeyboardOptions(autoCorrectEnabled = false))
        OutlinedButton(onClick = {
            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = manager?.primaryClip
            val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(context).toString() else ""
            if (text.isNotBlank()) edit(value.copy(pasteText = text))
            else Toast.makeText(context, "剪贴板中没有文本", Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.heightIn(min = 44.dp), shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Rounded.ContentPaste, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("从剪贴板粘贴")
        }
        if (value.pasteText.isNotBlank()) {
            Text("${summary.format.uppercase()}  ·  识别到 ${summary.proxies.size} 条映射", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            if (summary.serverAddr.isNotBlank()) TunnelDetail("远程服务器", "${summary.serverAddr}:${summary.serverPort ?: "—"}")
            if (attempted && check.errors.isNotEmpty()) TunnelNote(check.errors.joinToString("\n"), isError = true)
            if (check.warnings.isNotEmpty()) TunnelNote(check.warnings.joinToString("\n"))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        TunnelToggle("自动补全", "补充缺失的 DNS 与断线重连配置。", value.autoFill) { edit(value.copy(autoFill = it)) }
        TunnelNote("本机端口 $port · 自动跟随服务端口", icon = Icons.Rounded.Smartphone)
    }
}

@Composable
private fun TunnelDisclosure(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    expanded: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (expanded) "收起" else "展开")
    }
}

@Composable
private fun TunnelField(
    value: String, onValueChange: (String) -> Unit, label: String, hint: String,
    keyboardType: KeyboardType = KeyboardType.Text, helper: String? = null, error: String? = null, secret: Boolean = false
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), singleLine = true,
        shape = RoundedCornerShape(12.dp), label = { Text(label) }, placeholder = { Text(hint, style = MaterialTheme.typography.bodySmall) },
        textStyle = MaterialTheme.typography.bodyMedium, isError = error != null,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboardType, autoCorrectEnabled = false),
        visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (secret) { { IconButton(onClick = { visible = !visible }) {
            Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (visible) "隐藏 Token" else "显示 Token", Modifier.size(20.dp))
        } } } else null,
        supportingText = if (error != null || helper != null) { { Text(error ?: helper.orEmpty(), style = MaterialTheme.typography.bodySmall) } } else null
    )
}
