package com.example.danmuapiapp.ui.screen.push

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.danmuapiapp.data.util.resolvePushTargetAddress

/** The address stays a draft until the user explicitly uses it or saves it as the default. */
@Composable
internal fun PushTargetDialog(
    target: String,
    onTargetChange: (String) -> Unit,
    lanIp: String,
    savedDefaultTarget: String,
    effectiveDefaultTarget: String,
    recentTargets: List<String>,
    isAdminMode: Boolean,
    isSavingDefault: Boolean,
    saveMessage: String?,
    saveFailed: Boolean,
    onClearSaveMessage: () -> Unit,
    onForgetTarget: (String) -> Unit,
    onSaveDefaultTarget: (String) -> Unit,
    onScan: () -> Unit,
    isScanning: Boolean,
    scanStatus: String,
    devices: List<PushLanDeviceCandidate>,
    onClearScan: () -> Unit,
    onCopy: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    var draft by rememberSaveable { mutableStateOf(target) }
    var previousTarget by rememberSaveable { mutableStateOf(target) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showAddress by rememberSaveable { mutableStateOf(false) }
    val resolved = remember(draft) { resolvePushTargetAddress(draft) }
    val invalid = draft.isNotBlank() && resolved == null
    val recent = remember(recentTargets) { buildPushTargetChoices(recentTargets) }
    val selection = remember(resolved, savedDefaultTarget, effectiveDefaultTarget, recentTargets) {
        resolvePushTargetSelection(resolved.orEmpty(), savedDefaultTarget, effectiveDefaultTarget, recentTargets)
    }
    LaunchedEffect(target) {
        // Configuration can finish loading after the dialog opens; preserve any user edits.
        if (draft == previousTarget) draft = target
        previousTarget = target
    }
    LaunchedEffect(Unit) { if (!isSavingDefault) onClearSaveMessage() }

    fun updateDraft(value: String) {
        draft = value
        onClearSaveMessage()
    }

    fun choose(value: String) {
        focus.clearFocus()
        updateDraft(value)
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = colors.primaryContainer) {
                Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Cast, null, Modifier.size(24.dp), tint = colors.onPrimaryContainer)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("推送目标", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("连接接收弹幕的电视或盒子", fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
            IconButton(onClick = onDismissRequest) {
                Icon(Icons.Outlined.Close, "关闭推送目标弹窗", Modifier.size(22.dp))
            }
        }

        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("接收地址", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (resolved == null) "手动输入" else currentTargetLabel(selection),
                        fontSize = 11.sp,
                        color = colors.primary
                    )
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = ::updateDraft,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSavingDefault,
                    singleLine = true,
                    isError = invalid,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(14.dp),
                    placeholder = { Text("IP、IP:端口或完整地址", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Outlined.Link, null, Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (draft.isNotEmpty()) IconButton(onClick = { updateDraft("") }, enabled = !isSavingDefault) {
                            Icon(Icons.Outlined.Cancel, "清空地址", Modifier.size(19.dp))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() })
                )
                Text(
                    if (invalid) "地址无效，请检查 IP、协议和端口（1–65535）"
                    else "只填 IP 时自动使用 9978 端口，也可粘贴完整地址。",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = if (invalid) colors.error else colors.onSurfaceVariant
                )
                if (!samePushTarget(resolved.orEmpty(), effectiveDefaultTarget)) {
                    Surface(
                        onClick = { choose(effectiveDefaultTarget) },
                        enabled = !isSavingDefault,
                        color = colors.surfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Outlined.SettingsBackupRestore, null, Modifier.size(19.dp), tint = colors.primary)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("使用${if (savedDefaultTarget.isBlank()) "推荐地址" else "配置默认"}", fontSize = 12.sp)
                                Text(summarizePushHost(effectiveDefaultTarget), fontSize = 11.sp, color = colors.onSurfaceVariant)
                            }
                            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                        }
                    }
                }
                if (resolved != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showAddress = !showAddress }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                            Text(if (showAddress) "收起完整地址" else "查看完整地址", fontSize = 11.sp)
                            Icon(if (showAddress) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { onCopy(resolved) }) {
                            Icon(Icons.Outlined.ContentCopy, "复制完整推送地址", Modifier.size(18.dp))
                        }
                    }
                    if (showAddress) SelectionContainer {
                        Text(resolved, fontSize = 11.sp, lineHeight = 16.sp, color = colors.onSurfaceVariant)
                    }
                }
            }

            Column {
                PrimaryTabRow(selectedTabIndex = tab, containerColor = colors.surfaceContainerLow) {
                    Tab(
                        selected = tab == 0,
                        onClick = { tab = 0; focus.clearFocus() },
                        text = { Text("最近推送${if (recent.isNotEmpty()) " · ${recent.size}" else ""}", fontSize = 13.sp) }
                    )
                    Tab(
                        selected = tab == 1,
                        onClick = { tab = 1; focus.clearFocus() },
                        text = { Text("发现设备", fontSize = 13.sp) }
                    )
                }
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (tab == 0) {
                        if (recent.isEmpty()) TargetEmptyState(
                            icon = Icons.Outlined.History,
                            title = "还没有推送记录",
                            detail = "推送成功后，最近 3 个地址会保存在这里。"
                        ) else recent.forEach { choice ->
                            TargetDeviceRow(
                                address = choice.address,
                                subtitle = choice.address,
                                icon = Icons.Outlined.History,
                                selected = resolved != null && samePushTarget(choice.address, resolved),
                                enabled = !isSavingDefault,
                                onClick = { choose(choice.address) },
                                trailing = {
                                    IconButton(onClick = { onForgetTarget(choice.address) }, enabled = !isSavingDefault) {
                                        Icon(Icons.Outlined.Close, "移除记录 ${summarizePushHost(choice.address)}", Modifier.size(18.dp))
                                    }
                                }
                            )
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("同一 Wi-Fi 下的设备", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    if (lanIp.isBlank()) "连接 Wi-Fi 后可扫描" else "本机 $lanIp",
                                    fontSize = 11.sp,
                                    color = colors.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(
                                onClick = { focus.clearFocus(); onScan() },
                                enabled = lanIp.isNotBlank() && !isScanning,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp)
                            ) {
                                if (isScanning) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Outlined.Radar, null, Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (isScanning) "扫描中" else if (devices.isEmpty()) "扫描" else "重扫", fontSize = 12.sp)
                            }
                        }
                        if (scanStatus != "未扫描") Text(
                            scanStatus,
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = colors.onSurfaceVariant
                        )
                        if (devices.isEmpty()) TargetEmptyState(
                            icon = Icons.Outlined.Tv,
                            title = if (isScanning) "正在寻找接收设备" else "扫描附近的电视与盒子",
                            detail = "请让设备与手机连接同一 Wi-Fi，并开启 OK影视推送接口。"
                        ) else devices.forEach { device ->
                            TargetDeviceRow(
                                address = device.targetTemplate,
                                subtitle = (if (device.isSelf) "本机 · " else "") + device.supportLabel,
                                icon = if (device.isSelf) Icons.Outlined.PhoneAndroid else Icons.Outlined.Tv,
                                selected = resolved != null && samePushTarget(device.targetTemplate, resolved),
                                enabled = !isSavingDefault,
                                onClick = { choose(device.targetTemplate) }
                            )
                        }
                        if (devices.isNotEmpty() && !isScanning) {
                            TextButton(onClick = onClearScan, modifier = Modifier.align(Alignment.End)) {
                                Text("清空扫描结果", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = colors.outlineVariant.copy(alpha = .6f))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (saveMessage != null) {
                Row(
                    Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        if (saveFailed) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
                        null,
                        Modifier.size(17.dp),
                        tint = if (saveFailed) colors.error else colors.primary
                    )
                    Text(saveMessage, fontSize = 12.sp, lineHeight = 17.sp, color = if (saveFailed) colors.error else colors.primary)
                }
            } else if (!isAdminMode) {
                Text("开启管理员模式后可保存默认地址，当前可临时使用。", fontSize = 11.sp, lineHeight = 16.sp, color = colors.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        resolved?.let { address ->
                            focus.clearFocus()
                            onTargetChange(address)
                            onSaveDefaultTarget(address)
                        }
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    enabled = resolved != null && isAdminMode && !isSavingDefault && !samePushTarget(resolved, savedDefaultTarget),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    if (isSavingDefault) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(if (isAdminMode) Icons.Outlined.BookmarkBorder else Icons.Outlined.Lock, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isSavingDefault) "保存中…" else "设为默认", fontSize = 13.sp)
                }
                Button(
                    onClick = {
                        resolved?.let { address ->
                            focus.clearFocus()
                            onTargetChange(address)
                            onDismissRequest()
                        }
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    enabled = resolved != null && !isSavingDefault,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Text("使用此目标", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun TargetDeviceRow(
    address: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected; role = Role.RadioButton },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colors.primaryContainer.copy(alpha = .45f) else colors.surfaceContainerHigh,
        border = if (selected) BorderStroke(1.dp, colors.primary.copy(alpha = .5f)) else null
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = if (trailing == null) 14.dp else 0.dp, top = 6.dp, bottom = 6.dp).heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, null, Modifier.size(21.dp), tint = if (selected) colors.primary else colors.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(summarizePushHost(address), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 11.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (selected) Icon(Icons.Outlined.CheckCircle, "已选中", Modifier.size(19.dp), tint = colors.primary)
            trailing?.invoke()
        }
    }
}

@Composable
private fun TargetEmptyState(icon: ImageVector, title: String, detail: String) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, Modifier.size(30.dp), tint = colors.onSurfaceVariant.copy(alpha = .65f))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(detail, fontSize = 11.sp, lineHeight = 17.sp, color = colors.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
