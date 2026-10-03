package com.example.danmuapiapp.ui.compat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.domain.model.*
import com.example.danmuapiapp.ui.component.remoteFocusHighlight
import java.util.UUID

private fun compatCategoryLabel(category: String): String = when (category) {
    "api" -> "接口与认证"; "source" -> "来源设置"; "match" -> "匹配规则"
    "danmu" -> "弹幕处理"; "cache" -> "缓存设置"; "system" -> "系统设置"
    else -> category.ifBlank { "其他设置" }
}

@Composable
internal fun CompatConfigPage(state: CompatManagementState, runtime: RuntimeState,
    onRefresh: () -> Unit, onLogin: (String) -> Unit, onSave: (CompatConfigEntry, String, Boolean) -> Unit,
    listState: LazyListState, modifier: Modifier = Modifier) {
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var editorKey by remember { mutableStateOf<String?>(null) }
    var showBrowser by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }
    val categories = remember(state.entries) { state.entries.map { it.definition.category }.distinct() }
    val entries = remember(state.entries, search, category) { state.entries.filter {
        (category.isBlank() || it.definition.category == category) &&
            (search.isBlank() || "${it.definition.key} ${it.definition.description}".contains(search.trim(), true))
    } }
    if (!state.admin.isAdminMode) {
        CompatAdminGate(state, onLogin, modifier)
        return
    }
    BoxWithConstraints(modifier) {
        val twoPane = maxWidth.value / LocalDensity.current.fontScale.coerceAtLeast(1f) >= 780f
        val toolbarHeight = (maxHeight * 0.5f).coerceAtLeast(72.dp)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth().heightIn(max = toolbarHeight)
                .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("搜索配置名称或说明") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Rounded.Close, "清空搜索") } })
                CompatActions {
                    CompatButton("刷新", onRefresh, icon = Icons.Rounded.Refresh, enabled = !state.loading && !state.saving)
                    if (!twoPane) CompatButton(if (category.isBlank()) "全部分类" else compatCategoryLabel(category),
                        { showCategories = true }, icon = Icons.Rounded.FilterList)
                    CompatButton("扫码配置", { showBrowser = true }, icon = Icons.Rounded.QrCode2,
                        enabled = !state.saving && !state.loading && runtime.status == ServiceStatus.Running && (runtime.lanUrl.isNotBlank() || runtime.lanIpv6Url.isNotBlank()))
                    CompatBadge("${entries.size} / ${state.entries.size} 项")
                }
                if (state.error.isNotBlank()) Text(state.error, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
                if (state.loading || state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (twoPane) Surface(Modifier.width(148.dp).fillMaxHeight(), shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        (listOf("") + categories).forEach { item ->
                            val selected = item == category
                            val shape = RoundedCornerShape(12.dp)
                            Row(Modifier.fillMaxWidth().clip(shape)
                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                                .remoteFocusHighlight(shape).selectable(selected, role = Role.Tab, onClick = { category = item })
                                .heightIn(min = 52.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (item.isBlank()) "全部配置" else compatCategoryLabel(item),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                LazyColumn(Modifier.weight(1f).fillMaxHeight(), state = listState,
                    contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!state.loading && entries.isEmpty()) item { CompatCard {
                        Text(if (state.entries.isEmpty()) "尚未读取到核心配置目录，请刷新或启动服务。" else "没有符合条件的配置项。")
                    } }
                    items(entries, key = { it.definition.key }) { entry ->
                        CompatConfigRow(entry, enabled = !state.saving && !state.loading,
                            onClick = { editorKey = entry.definition.key })
                    }
                    item("source-note") {
                        Text(if (state.fromApi) "来自当前核心接口 · 保存后立即热更新" else "来自当前核心配置目录 · 服务停止时保存，下次启动生效",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    val entry = state.entries.firstOrNull { it.definition.key == editorKey }
    if (entry != null) CompatConfigEditor(entry, state.saving, state.saveSequence, state.error, { editorKey = null }, onSave)
    if (showCategories) CompatDialog("配置分类", { showCategories = false }, "完成", { showCategories = false }) {
        (listOf("") + categories).forEach { item -> CompatButton(if (item.isBlank()) "全部配置" else compatCategoryLabel(item),
            { category = item; showCategories = false }, Modifier.fillMaxWidth(),
            tone = if (item == category) CompatActionTone.Primary else CompatActionTone.Neutral) }
    }
    if (showBrowser) {
        // Resolve the URL at display time; never persist an administrator link in saved state.
        val adminToken = state.entries.firstOrNull { it.definition.key == "ADMIN_TOKEN" }?.value.orEmpty()
        CompatBrowserDialog("扫码打开系统配置", compatWebUrl(runtime, "env", adminToken), { showBrowser = false },
            "手机连接同一局域网后扫码，直接进入核心网页的系统配置页。修改会保存到当前工作目录并热更新。")
    }
}

@Composable
private fun CompatAdminGate(state: CompatManagementState, onLogin: (String) -> Unit, modifier: Modifier) {
    var token by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val existing = state.admin.hasAdminTokenConfigured
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CompatCard {
            CompatCardTitle(if (existing) "验证管理员密钥" else "设置管理员密钥", "系统配置需要管理员权限", Icons.Rounded.AdminPanelSettings)
            Text(if (existing) "输入当前工作目录中的 ADMIN_TOKEN。验证后即可管理全部配置。"
                else "首次使用请设置 ADMIN_TOKEN。密钥保存到当前工作目录的 .env，用于 App 和网页管理。",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), label = { Text("管理员密钥") }, singleLine = true,
                enabled = !state.saving && !state.loading,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), trailingIcon = {
                    IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        if (visible) "隐藏密钥" else "显示密钥") }
                })
            Text(if (existing) "使用已保存的密钥验证，不会覆盖现有 ADMIN_TOKEN。"
                else "管理员密钥由你自行设置，不限制长度或字符组合；留空表示未配置。请妥善保管。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CompatActions {
                if (!existing) CompatButton("随机生成", { token = UUID.randomUUID().toString().replace("-", "") },
                    icon = Icons.Rounded.AutoAwesome, enabled = !state.saving && !state.loading)
                CompatButton(if (state.saving) "正在验证…" else if (existing) "验证并进入" else "保存并进入",
                    { onLogin(token) }, icon = Icons.Rounded.LockOpen, tone = CompatActionTone.Primary,
                    enabled = (!existing || token.isNotBlank()) && !state.saving && !state.loading)
            }
            if (state.loading || state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.error.isNotBlank()) Text(state.error, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun CompatConfigRow(entry: CompatConfigEntry, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(Modifier.fillMaxWidth().clip(shape).remoteFocusHighlight(shape, enabled)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = shape, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(entry.definition.key, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                CompatBadge(if (entry.configured) "已配置" else "默认值",
                    if (entry.configured) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            Text(entry.definition.description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (entry.definition.sensitive && entry.value.isNotBlank()) "••••••••" else entry.value.ifBlank { "（空）" },
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CompatConfigEditor(entry: CompatConfigEntry, busy: Boolean, saveSequence: Int, error: String, onDismiss: () -> Unit,
    onSave: (CompatConfigEntry, String, Boolean) -> Unit) {
    val def = entry.definition
    var value by remember(def.key) { mutableStateOf(entry.value) }
    var visible by remember { mutableStateOf(false) }
    var submittedSequence by remember(def.key) { mutableIntStateOf(saveSequence) }
    var pendingSave by remember { mutableStateOf(false) }
    LaunchedEffect(saveSequence, busy) {
        if (pendingSave && !busy && saveSequence > submittedSequence) {
            pendingSave = false
            if (error.isBlank()) onDismiss()
        }
    }
    val invalid = validateCompatConfigValue(def, value)
    CompatDialog("编辑 ${def.key}", onDismiss, "保存", { submittedSequence = saveSequence; pendingSave = true; onSave(entry, value, false) },
        confirmEnabled = !busy && invalid == null) {
        Text(def.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (def.type) {
            EnvType.BOOLEAN -> CompatActions {
                listOf("true" to "开启", "false" to "关闭").forEach { (key, label) ->
                    CompatButton(label, { value = key }, tone = if (value == key) CompatActionTone.Primary else CompatActionTone.Neutral)
                }
            }
            EnvType.SELECT -> if (def.options.isNotEmpty()) CompatActions {
                def.options.forEach { option -> CompatButton(option, { value = option },
                    tone = if (value == option) CompatActionTone.Primary else CompatActionTone.Neutral) }
            } else CompatValueField(value, { value = it }, def, visible, { visible = !visible })
            EnvType.MULTI_SELECT -> {
                val chosen = value.split(',').map(String::trim).filter(String::isNotBlank).toSet()
                CompatActions {
                    def.options.forEach { option -> CompatButton(option, {
                        value = (if (option in chosen) chosen - option else chosen + option).joinToString(",")
                    }, tone = if (option in chosen) CompatActionTone.Primary else CompatActionTone.Neutral) }
                }
                CompatValueField(value, { value = it }, def, visible, { visible = !visible })
            }
            else -> CompatValueField(value, { value = it }, def, visible, { visible = !visible })
        }
        if (error.isNotBlank() && !busy) Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        if (invalid != null) Text(invalid, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        if (entry.configured) CompatButton("恢复默认值", {
            submittedSequence = saveSequence; pendingSave = true; onSave(entry, "", true)
        }, icon = Icons.Rounded.Restore, enabled = !busy)
        Text("保存后应用到当前核心；密钥、Cookie 等敏感值请勿分享。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CompatValueField(value: String, onChange: (String) -> Unit, def: EnvVarDef,
    visible: Boolean, onToggleVisible: () -> Unit) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text("配置值") },
        singleLine = def.sensitive || def.type == EnvType.NUMBER,
        minLines = if (def.sensitive || def.type == EnvType.NUMBER) 1 else 3,
        maxLines = if (def.sensitive || def.type == EnvType.NUMBER) 1 else 8,
        visualTransformation = if (def.sensitive && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = when {
            def.sensitive -> KeyboardType.Password
            def.type == EnvType.NUMBER -> KeyboardType.Decimal
            else -> KeyboardType.Text
        }), trailingIcon = { if (def.sensitive) IconButton(onClick = onToggleVisible) {
            Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (visible) "隐藏值" else "显示值")
        } })
}
