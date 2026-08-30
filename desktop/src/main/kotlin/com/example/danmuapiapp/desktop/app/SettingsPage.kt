package com.example.danmuapiapp.desktop.app

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.danmuapiapp.desktop.app.settings.DesktopSettingsSnapshot
import com.example.danmuapiapp.desktop.app.settings.SettingsCategoryId
import com.example.danmuapiapp.desktop.app.settings.SettingsCategoryRegistry
import com.example.danmuapiapp.desktop.app.settings.SettingsDraft
import com.example.danmuapiapp.desktop.app.settings.SettingsDraftEdit
import com.example.danmuapiapp.desktop.app.settings.SettingsDraftField
import com.example.danmuapiapp.desktop.app.settings.SettingsDraftReducer
import com.example.danmuapiapp.desktop.app.settings.SettingsValidation
import com.example.danmuapiapp.desktop.node.GithubProxyCatalog
import com.example.danmuapiapp.desktop.node.GithubProxyOption
import com.example.danmuapiapp.desktop.runtime.AutostartManager
import com.example.danmuapiapp.desktop.runtime.DesktopPaths
import com.example.danmuapiapp.desktop.runtime.DesktopRuntimeConfig
import com.example.danmuapiapp.desktop.runtime.DesktopRuntimeConfigResolver
import com.example.danmuapiapp.desktop.runtime.DesktopRuntimeEnv
import com.example.danmuapiapp.desktop.runtime.DesktopSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser

/**
 * Desktop settings use a category index and a detail panel rather than one long form.
 * Values are edited in [SettingsDraft] and are persisted only after explicit validation and save.
 */
@Composable
fun SettingsPage(
    settings: DesktopSettings,
    paths: DesktopPaths,
    themePreference: ThemePreference,
    onThemeChange: (ThemePreference) -> Unit,
    onRuntimeConfigChanged: () -> Unit,
    showHeader: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val initialSnapshot = remember(settings) { settingsSnapshot(settings) }
    var baselineSnapshot by remember(settings) { mutableStateOf(initialSnapshot) }
    var baselineDraft by remember(settings) { mutableStateOf(SettingsDraft.from(initialSnapshot)) }
    var draft by remember(settings) { mutableStateOf(SettingsDraft.from(initialSnapshot)) }
    var validation by remember(settings) { mutableStateOf(SettingsValidation.validate(draft)) }
    var selectedCategory by remember(settings) { mutableStateOf<SettingsCategoryId?>(null) }
    var feedback by remember(settings) { mutableStateOf<String?>(null) }
    var restartRequired by remember(settings) { mutableStateOf(false) }
    var tokenInput by remember(settings) { mutableStateOf("") }
    var tokenVisible by remember(settings) { mutableStateOf(false) }
    var adminTokenInput by remember(settings) { mutableStateOf("") }
    var adminTokenVisible by remember(settings) { mutableStateOf(false) }

    var autostartSupported by remember(settings) { mutableStateOf<Boolean?>(null) }
    var savedAutostart by remember(settings) { mutableStateOf<Boolean?>(null) }
    var autostartDraft by remember(settings) { mutableStateOf(false) }
    var autostartBusy by remember(settings) { mutableStateOf(false) }
    var autostartError by remember(settings) { mutableStateOf<String?>(null) }
    var latencies by remember(settings) { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var testingLatencies by remember(settings) { mutableStateOf(false) }
    var latencyError by remember(settings) { mutableStateOf<String?>(null) }
    var configuredRuntime by remember(settings, paths.root.absolutePath) {
        mutableStateOf(resolveRuntime(settings, paths))
    }

    LaunchedEffect(settings) {
        val state = withContext(Dispatchers.IO) {
            val supported = AutostartManager.isSupported()
            supported to if (supported) AutostartManager.isEnabled() else false
        }
        autostartSupported = state.first
        savedAutostart = state.second
        autostartDraft = state.second
    }

    val hasUnsavedChanges = SettingsDraftReducer.dirty(draft, baselineDraft) ||
        (savedAutostart != null && autostartDraft != savedAutostart) ||
        tokenInput.isNotBlank() || adminTokenInput.isNotBlank()

    fun edit(change: SettingsDraftEdit) {
        draft = SettingsDraftReducer.reduce(draft, change)
        validation = SettingsDraftReducer.validate(draft)
        feedback = null
    }

    fun cancelChanges() {
        draft = SettingsDraftReducer.reset(baselineDraft)
        validation = SettingsDraftReducer.validate(draft)
        autostartDraft = savedAutostart ?: autostartDraft
        tokenInput = ""
        tokenVisible = false
        adminTokenInput = ""
        adminTokenVisible = false
        feedback = "已取消未保存的更改。"
    }

    fun restoreDefaults() {
        draft = SettingsDraftReducer.reset(SettingsDraft())
        validation = SettingsDraftReducer.validate(draft)
        autostartDraft = false
        tokenInput = ""
        tokenVisible = false
        adminTokenInput = ""
        adminTokenVisible = false
        feedback = "已恢复默认值，点击保存后应用。"
    }

    fun saveChanges() {
        val result = SettingsDraftReducer.validate(draft)
        validation = result
        if (!result.isValid) {
            feedback = "请先修正 ${result.messages.size} 项设置。"
            return
        }
        val old = baselineSnapshot
        val saved = draft.toSnapshot()
        val runtimeRootChanged = old.runtimeRootOverride != saved.runtimeRootOverride
        val serviceChanged = old.portOverride != saved.portOverride ||
            old.listenHostOverride != saved.listenHostOverride ||
            old.ipv6Enabled != saved.ipv6Enabled ||
            old.variantOverride != saved.variantOverride
        val adminTokenChanged = old.adminTokenConfigured != saved.adminTokenConfigured ||
            adminTokenInput.isNotBlank()
        val adminTokenValue = when {
            adminTokenInput.isNotBlank() -> adminTokenInput.trim()
            old.adminTokenConfigured && !saved.adminTokenConfigured -> ""
            else -> null
        }
        val adminTokenApplied = if (adminTokenChanged) {
            try {
                val targetPaths = DesktopPaths(
                    saved.runtimeRootOverride?.let(::File),
                )
                DesktopRuntimeEnv.applyAdminToken(
                    scriptDir = File(targetPaths.runtimeDir, "nodejs-project"),
                    token = adminTokenValue,
                )
            } catch (error: Throwable) {
                feedback = "管理员令牌未保存：${error.message ?: error::class.java.simpleName}"
                return
            }
        } else {
            null
        }
        settings.setRuntimeRoot(saved.runtimeRootOverride)
        if (saved.githubProxyId != old.githubProxyId) {
            settings.setGithubProxy(saved.githubProxyId)
            settings.setGithubProxyConfirmed(false)
        } else {
            settings.setGithubProxy(saved.githubProxyId)
        }
        settings.setTheme(saved.theme)
        if (saved.theme != old.theme) onThemeChange(ThemePreference.fromKey(saved.theme))
        settings.setPortOverride(saved.portOverride)
        settings.setListenHostOverride(saved.listenHostOverride)
        settings.setIpv6Enabled(saved.ipv6Enabled)
        settings.setVariantOverride(saved.variantOverride)
        settings.setCloseAction(saved.closeAction)
        if (tokenInput.isNotBlank()) settings.setGithubToken(tokenInput.trim())
        else if (!saved.githubTokenConfigured) settings.setGithubToken("")
        if (adminTokenInput.isNotBlank()) {
            settings.setAdminTokenOverride(adminTokenInput.trim())
        } else if (old.adminTokenConfigured && !saved.adminTokenConfigured) {
            settings.setAdminTokenOverride("")
        }
        val persisted = saved.copy(
            githubTokenConfigured = settings.githubToken.isNotBlank(),
            adminTokenConfigured = settings.adminTokenOverride?.isNotBlank() == true,
        )
        baselineSnapshot = persisted
        baselineDraft = SettingsDraft.from(persisted)
        draft = SettingsDraft.from(persisted)
        tokenInput = ""
        tokenVisible = false
        adminTokenInput = ""
        adminTokenVisible = false
        configuredRuntime = if (runtimeRootChanged) configuredRuntime else resolveRuntime(settings, paths)
        validation = SettingsValidation.validate(draft)
        restartRequired = restartRequired || runtimeRootChanged
        if (serviceChanged && !runtimeRootChanged) onRuntimeConfigChanged()
        feedback = when {
            runtimeRootChanged && adminTokenChanged && adminTokenApplied == false ->
                "已保存。运行目录将在重启应用后生效；管理员令牌会在新运行目录建立后写入核心 .env。"
            runtimeRootChanged -> "已保存。运行目录将在重启应用后生效。"
            serviceChanged -> "已保存。运行中的服务正在按新配置重启。"
            adminTokenChanged && adminTokenApplied == true ->
                "管理员令牌已写入当前核心 .env；运行中的核心会按自身热加载机制刷新。"
            adminTokenChanged && adminTokenApplied == false ->
                "管理员令牌已保存到桌面设置；当前运行目录尚未建立，将在下次启动核心时写入 .env。"
            else -> "已保存。"
        }
        val targetAutostart = autostartDraft
        if (savedAutostart != null && targetAutostart != savedAutostart) {
            autostartBusy = true
            autostartError = null
            feedback = "设置已保存，正在更新开机自启…"
            scope.launch {
                val error = withContext(Dispatchers.IO) {
                    if (targetAutostart) AutostartManager.enable() else AutostartManager.disable()
                }
                autostartBusy = false
                if (error == null) {
                    savedAutostart = targetAutostart
                    feedback = "设置已保存，开机自启已更新。"
                } else {
                    autostartDraft = savedAutostart ?: false
                    autostartError = error
                    feedback = "其他设置已保存，但开机自启更新失败。"
                }
            }
        }
    }

    fun testGithubLatencies() {
        if (testingLatencies) return
        testingLatencies = true
        latencyError = null
        latencies = emptyMap()
        scope.launch {
            try {
                latencies = withContext(Dispatchers.IO) { GithubProxyCatalog.testAllLatencies() }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                latencyError = "测速失败：${error.message ?: error::class.simpleName ?: "未知错误"}"
            } finally {
                testingLatencies = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(DesktopTokens.PagePadding),
        verticalArrangement = Arrangement.spacedBy(DesktopTokens.PageGap),
    ) {
        if (showHeader) DesktopPageHeader(
            title = "设置",
            subtitle = "按分类管理弹幕API的运行、网络与应用偏好",
            status = if (hasUnsavedChanges) DesktopStatus.Warning else DesktopStatus.Success,
        )
        if (restartRequired) DesktopRestartBanner(message = "运行目录已变更。请关闭并重新打开应用，新的运行时路径才会生效。")
        val category = selectedCategory
        if (category == null) {
            CategoryIndex(
                selected = null,
                draft = draft,
                baselineDraft = baselineDraft,
                autostartDraft = autostartDraft,
                savedAutostart = savedAutostart,
                autostartSupported = autostartSupported,
                tokenInput = tokenInput,
                adminTokenInput = adminTokenInput,
                configuredRuntime = configuredRuntime,
                testingLatencies = testingLatencies,
                onSelect = { selectedCategory = it },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        } else {
            SettingsDetail(
                category = category,
                draft = draft,
                validation = validation,
                autostartSupported = autostartSupported,
                autostartDraft = autostartDraft,
                autostartBusy = autostartBusy,
                autostartError = autostartError,
                tokenInput = tokenInput,
                tokenVisible = tokenVisible,
                adminTokenInput = adminTokenInput,
                adminTokenVisible = adminTokenVisible,
                configuredRuntime = configuredRuntime,
                paths = paths,
                latencies = latencies,
                testingLatencies = testingLatencies,
                latencyError = latencyError,
                hasUnsavedChanges = hasUnsavedChanges,
                feedback = feedback,
                onBack = { selectedCategory = null },
                onEdit = ::edit,
                onBrowseRuntimeDirectory = {
                    chooseRuntimeDirectory(draft.runtimeRootOverride, paths.root.parentFile)
                        .onSuccess { selected -> if (selected != null) edit(SettingsDraftEdit.RuntimeRootOverride(selected.absolutePath)) }
                        .onFailure { error -> feedback = "选择运行目录失败：${error.message ?: error::class.java.simpleName}" }
                },
                onAutostartChange = { autostartDraft = it; feedback = null },
                onTokenChange = { value ->
                    tokenInput = value
                    if (value.isNotBlank()) edit(SettingsDraftEdit.GithubTokenConfigured(true))
                    else if (!baselineSnapshot.githubTokenConfigured) edit(SettingsDraftEdit.GithubTokenConfigured(false))
                    else feedback = null
                },
                onTokenRemove = {
                    tokenInput = ""
                    tokenVisible = false
                    edit(SettingsDraftEdit.GithubTokenConfigured(false))
                },
                onTokenVisibilityChange = { tokenVisible = it },
                onAdminTokenChange = { value ->
                    adminTokenInput = value
                    if (value.isNotBlank()) edit(SettingsDraftEdit.AdminTokenConfigured(true))
                    else if (!baselineSnapshot.adminTokenConfigured) edit(SettingsDraftEdit.AdminTokenConfigured(false))
                    else feedback = null
                },
                onAdminTokenRemove = {
                    adminTokenInput = ""
                    adminTokenVisible = false
                    edit(SettingsDraftEdit.AdminTokenConfigured(false))
                },
                onAdminTokenVisibilityChange = { adminTokenVisible = it },
                onSave = ::saveChanges,
                onCancel = ::cancelChanges,
                onRestoreDefaults = ::restoreDefaults,
                onTestLatencies = ::testGithubLatencies,
            )
        }
    }
}

@Composable
private fun CategoryIndex(
    selected: SettingsCategoryId?,
    draft: SettingsDraft,
    baselineDraft: SettingsDraft,
    autostartDraft: Boolean,
    savedAutostart: Boolean?,
    autostartSupported: Boolean?,
    tokenInput: String,
    adminTokenInput: String,
    configuredRuntime: DesktopRuntimeConfig,
    testingLatencies: Boolean,
    onSelect: (SettingsCategoryId) -> Unit,
    modifier: Modifier = Modifier,
) {
    DesktopSurface(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("设置分类", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
            SettingsCategoryRegistry.ordered.forEach { spec ->
                val dirty = categoryDirty(spec.id, draft, baselineDraft, autostartDraft, savedAutostart, tokenInput, adminTokenInput)
                DesktopSurface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (selected == spec.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    contentColor = if (selected == spec.id) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                    shape = DesktopTokens.ItemShape,
                    onClick = { onSelect(spec.id) },
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DesktopIcon(iconFor(spec.id), tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 20.sp)
                            Text(spec.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            CategoryBadge(spec.id, dirty, draft, autostartDraft, savedAutostart, autostartSupported, testingLatencies)
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(categorySummary(spec.id, draft, autostartDraft, savedAutostart, autostartSupported, tokenInput, configuredRuntime, adminTokenInput), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBadge(category: SettingsCategoryId, dirty: Boolean, draft: SettingsDraft, autostartDraft: Boolean, savedAutostart: Boolean?, autostartSupported: Boolean?, testingLatencies: Boolean) {
    when {
        dirty -> DesktopStatusBadge(DesktopStatus.Warning, "未保存", compact = true)
        category == SettingsCategoryId.GeneralStartup && autostartSupported == null -> DesktopStatusBadge(DesktopStatus.Loading, "读取中", compact = true)
        category == SettingsCategoryId.GeneralStartup && autostartSupported == false -> DesktopStatusBadge(DesktopStatus.Neutral, "开发版", compact = true)
        category == SettingsCategoryId.GeneralStartup && autostartDraft -> DesktopStatusBadge(DesktopStatus.Success, "自启已开", compact = true)
        category == SettingsCategoryId.GeneralStartup -> DesktopStatusBadge(DesktopStatus.Neutral, "自启未开", compact = true)
        category == SettingsCategoryId.Service -> DesktopStatusBadge(DesktopStatus.Info, "按需生效", compact = true)
        category == SettingsCategoryId.PathsRuntime && draft.runtimeRootOverride.isNotBlank() -> DesktopStatusBadge(DesktopStatus.Info, "自定义", compact = true)
        category == SettingsCategoryId.PathsRuntime -> DesktopStatusBadge(DesktopStatus.Neutral, "默认", compact = true)
        category == SettingsCategoryId.NetworkDownload && testingLatencies -> DesktopStatusBadge(DesktopStatus.Loading, "测速中", compact = true)
        category == SettingsCategoryId.NetworkDownload -> DesktopStatusBadge(DesktopStatus.Info, if (draft.githubProxyId == DesktopSettingsSnapshot.DEFAULT_GITHUB_PROXY_ID) "直连" else "镜像", compact = true)
        category == SettingsCategoryId.SecurityAdmin && draft.adminTokenConfigured -> DesktopStatusBadge(DesktopStatus.Success, "已配置", compact = true)
        category == SettingsCategoryId.SecurityAdmin -> DesktopStatusBadge(DesktopStatus.Neutral, "未配置", compact = true)
        category == SettingsCategoryId.Diagnostics || category == SettingsCategoryId.UpdatesAbout -> DesktopStatusBadge(DesktopStatus.Neutral, "未实现", compact = true)
    }
}

private fun categoryDirty(category: SettingsCategoryId, draft: SettingsDraft, baseline: SettingsDraft, autostartDraft: Boolean, savedAutostart: Boolean?, tokenInput: String, adminTokenInput: String): Boolean = when (category) {
    SettingsCategoryId.GeneralStartup -> draft.theme != baseline.theme || draft.closeAction != baseline.closeAction || (savedAutostart != null && autostartDraft != savedAutostart)
    SettingsCategoryId.Service -> draft.portOverride != baseline.portOverride || draft.listenHostOverride != baseline.listenHostOverride || draft.ipv6Enabled != baseline.ipv6Enabled || draft.variantOverride != baseline.variantOverride
    SettingsCategoryId.PathsRuntime -> draft.runtimeRootOverride != baseline.runtimeRootOverride
    SettingsCategoryId.NetworkDownload -> draft.githubProxyId != baseline.githubProxyId || draft.githubTokenConfigured != baseline.githubTokenConfigured || tokenInput.isNotBlank()
    SettingsCategoryId.SecurityAdmin -> draft.adminTokenConfigured != baseline.adminTokenConfigured || adminTokenInput.isNotBlank()
    SettingsCategoryId.Diagnostics, SettingsCategoryId.UpdatesAbout -> false
}

private fun categorySummary(category: SettingsCategoryId, draft: SettingsDraft, autostartDraft: Boolean, savedAutostart: Boolean?, autostartSupported: Boolean?, tokenInput: String, configuredRuntime: DesktopRuntimeConfig, adminTokenInput: String = ""): String = when (category) {
    SettingsCategoryId.GeneralStartup -> "${ThemePreference.fromKey(draft.theme).label} · ${closeActionLabel(draft.closeAction)} · " + when {
        autostartSupported == null -> "开机自启读取中"
        autostartSupported == false -> "开发版不可用"
        autostartDraft -> "开机自启已开启"
        savedAutostart == false -> "开机自启未开启"
        else -> "开机自启已关闭"
    }
    SettingsCategoryId.Service -> "${if (draft.ipv6Enabled) "IPv4 + IPv6" else "仅 IPv4"} · ${draft.listenHostOverride.trim().ifBlank { configuredRuntime.listenHost }}:${draft.portOverride.trim().ifBlank { configuredRuntime.port.toString() }} · 核心 ${draft.variantOverride.trim().ifBlank { configuredRuntime.variant }}"
    SettingsCategoryId.PathsRuntime -> if (draft.runtimeRootOverride.isBlank()) "使用默认运行目录：${shortenPath(configuredRuntimePath(configuredRuntime))}" else "自定义运行目录：${shortenPath(draft.runtimeRootOverride.trim())}"
    SettingsCategoryId.NetworkDownload -> "${GithubProxyCatalog.optionById(draft.githubProxyId).label} · " + when { tokenInput.isNotBlank() -> "Token待保存"; draft.githubTokenConfigured -> "Token已配置"; else -> "匿名访问" }
    SettingsCategoryId.SecurityAdmin -> when {
        adminTokenInput.isNotBlank() -> "ADMIN_TOKEN 待保存 · 保存后直接写入核心 .env"
        draft.adminTokenConfigured -> "ADMIN_TOKEN 已配置 · 管理员会话由后续管理页面使用"
        else -> "未配置 ADMIN_TOKEN · 核心管理写操作不可用"
    }
    SettingsCategoryId.Diagnostics -> "日志、健康检查与故障排查工具尚未提供"
    SettingsCategoryId.UpdatesAbout -> "核心更新与版本信息页面尚未提供"
}

private fun configuredRuntimePath(configuredRuntime: DesktopRuntimeConfig): String = "按当前运行目录解析的 ${configuredRuntime.listenHost}:${configuredRuntime.port} 配置"
private fun shortenPath(path: String, maxLength: Int = 48): String = if (path.length <= maxLength) path else "…" + path.takeLast(maxLength - 1)

@Composable
private fun SettingsDetail(
    category: SettingsCategoryId,
    draft: SettingsDraft,
    validation: SettingsValidation,
    autostartSupported: Boolean?,
    autostartDraft: Boolean,
    autostartBusy: Boolean,
    autostartError: String?,
    tokenInput: String,
    tokenVisible: Boolean,
    adminTokenInput: String,
    adminTokenVisible: Boolean,
    configuredRuntime: DesktopRuntimeConfig,
    paths: DesktopPaths,
    latencies: Map<String, Long>,
    testingLatencies: Boolean,
    latencyError: String?,
    hasUnsavedChanges: Boolean,
    feedback: String?,
    onBack: () -> Unit,
    onEdit: (SettingsDraftEdit) -> Unit,
    onBrowseRuntimeDirectory: () -> Unit,
    onAutostartChange: (Boolean) -> Unit,
    onTokenChange: (String) -> Unit,
    onTokenRemove: () -> Unit,
    onTokenVisibilityChange: (Boolean) -> Unit,
    onAdminTokenChange: (String) -> Unit,
    onAdminTokenRemove: () -> Unit,
    onAdminTokenVisibilityChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onRestoreDefaults: () -> Unit,
    onTestLatencies: () -> Unit,
) {
    DesktopSurface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(DesktopTokens.CardPadding)) {
            val spec = SettingsCategoryRegistry[category]
            DesktopPageHeader(
                title = spec.title,
                subtitle = spec.description,
                        leadingContent = {
                            DesktopIconButtonGlyph(
                                icon = DesktopIcons.Back,
                                onClick = onBack,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                contentDescription = "返回设置分类",
                            )
                        },

                status = if (hasUnsavedChanges) DesktopStatus.Warning else DesktopStatus.Success,
                actions = {
                    DesktopActionButton("保存", onSave, enabled = hasUnsavedChanges && !autostartBusy)
                    DesktopActionButton("取消", onCancel, enabled = hasUnsavedChanges && !autostartBusy, style = DesktopActionButtonStyle.Outlined)
                    DesktopActionButton("恢复默认", onRestoreDefaults, enabled = !autostartBusy, style = DesktopActionButtonStyle.Tonal)
                },
            )
            Spacer(Modifier.height(DesktopTokens.PageGap))
            if (hasUnsavedChanges) DesktopStatusBadge(DesktopStatus.Warning, "有未保存更改", modifier = Modifier.padding(bottom = 8.dp))
            if (!feedback.isNullOrBlank()) Text(feedback, style = MaterialTheme.typography.bodySmall, color = if (autostartError.isNullOrBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(DesktopTokens.PageGap)) {
                when (category) {
                    SettingsCategoryId.GeneralStartup -> GeneralStartupDetail(draft, autostartSupported, autostartDraft, autostartBusy, autostartError, onEdit, onAutostartChange)
                    SettingsCategoryId.Service -> ServiceDetail(draft, validation, configuredRuntime, onEdit)
                    SettingsCategoryId.PathsRuntime -> PathsRuntimeDetail(draft, validation, paths, onEdit, onBrowseRuntimeDirectory)
                    SettingsCategoryId.NetworkDownload -> NetworkDownloadDetail(draft, tokenInput, tokenVisible, latencies, testingLatencies, latencyError, onEdit, onTokenChange, onTokenRemove, onTokenVisibilityChange, onTestLatencies)
                    SettingsCategoryId.SecurityAdmin -> SecurityAdminDetail(draft, adminTokenInput, adminTokenVisible, onEdit, onAdminTokenChange, onAdminTokenRemove, onAdminTokenVisibilityChange)
                    SettingsCategoryId.Diagnostics -> DiagnosticsDetail()
                    SettingsCategoryId.UpdatesAbout -> UpdatesAboutDetail()
                }
            }
        }
    }
}

@Composable
private fun GeneralStartupDetail(draft: SettingsDraft, autostartSupported: Boolean?, autostartDraft: Boolean, autostartBusy: Boolean, autostartError: String?, onEdit: (SettingsDraftEdit) -> Unit, onAutostartChange: (Boolean) -> Unit) {
    DesktopSectionCard("外观", supportingText = "主题在保存后应用到整个桌面应用。") { DesktopSegmentedChoice(ThemePreference.entries.map { DesktopSegmentedOption(it.key, it.label) }, draft.theme, { onEdit(SettingsDraftEdit.Theme(it)) }) }
    DesktopSectionCard("关闭窗口行为", supportingText = "后台运行会保留托盘服务；退出并关闭服务会停止 node.exe。") { DesktopSegmentedChoice(listOf(DesktopSegmentedOption("ask", "每次询问"), DesktopSegmentedOption("tray", "后台运行"), DesktopSegmentedOption("exit", "退出并关闭服务")), draft.closeAction, { onEdit(SettingsDraftEdit.CloseAction(it)) }) }
    DesktopSectionCard("开机自启", supportingText = "开机后台启动弹幕服务；打开应用后可接管并管理服务。") {
        DesktopSettingRow(
            title = "开机自动启动服务",
            description = when (autostartSupported) {
                null -> "正在读取 Windows 启动项状态…"
                true -> "仅保存到当前 Windows 用户的启动项。"
                false -> "开发运行模式没有独立可执行文件，无法配置开机自启。"
            },
            enabled = autostartSupported != false,
        ) {
            when { autostartSupported == null || autostartBusy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); else -> Switch(autostartDraft, onAutostartChange, enabled = autostartSupported == true) }
        }
        if (!autostartError.isNullOrBlank()) Text(autostartError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        Text("开关更改会在点击“保存”后写入启动项。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ServiceDetail(draft: SettingsDraft, validation: SettingsValidation, configuredRuntime: DesktopRuntimeConfig, onEdit: (SettingsDraftEdit) -> Unit) {
    DesktopSectionCard("监听与端口", supportingText = "IPv6 开启后使用核心双栈监听（DANMU_API_HOST=::）；运行中保存会重启服务。") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(draft.portOverride, { onEdit(SettingsDraftEdit.PortOverride(it)) }, Modifier.weight(1f), singleLine = true, isError = validation.portError != null, label = { Text("端口覆盖") }, placeholder = { Text(configuredRuntime.port.toString()) }, textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), supportingText = { Text(validation.portError ?: "有效范围：1–65535。") })
            OutlinedTextField(draft.listenHostOverride, { onEdit(SettingsDraftEdit.ListenHostOverride(it)) }, Modifier.weight(1f), singleLine = true, isError = validation.hostError != null, enabled = !draft.ipv6Enabled, label = { Text("监听地址覆盖") }, placeholder = { Text(configuredRuntime.listenHost) }, textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), supportingText = { Text(validation.hostError ?: if (draft.ipv6Enabled) "IPv6 双栈已启用，监听地址固定为 ::。" else "支持 IPv4、IPv6 或主机名。") })
        }
        Spacer(Modifier.height(10.dp))
        DesktopSettingRow(
            title = "启用 IPv6 双栈监听",
            description = "打开后核心同时接受 IPv4 与 IPv6 连接，首页会显示可用的 IPv6 API 地址。",
            enabled = true,
        ) {
            Switch(
                checked = draft.ipv6Enabled,
                onCheckedChange = { onEdit(SettingsDraftEdit.Ipv6Enabled(it)) },
            )
        }
    }
    DesktopSectionCard("核心变体", supportingText = "留空沿用当前运行目录配置；stable、dev、custom 是唯一有效变体。") {
        DesktopSegmentedChoice(listOf(DesktopSegmentedOption("", "默认 (${configuredRuntime.variant})"), DesktopSegmentedOption("stable", "Stable"), DesktopSegmentedOption("dev", "Dev"), DesktopSegmentedOption("custom", "Custom")), draft.variantOverride.trim(), { onEdit(SettingsDraftEdit.VariantOverride(it)) })
        validation.variantError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun PathsRuntimeDetail(draft: SettingsDraft, validation: SettingsValidation, paths: DesktopPaths?, onEdit: (SettingsDraftEdit) -> Unit, onBrowseRuntimeDirectory: () -> Unit) {
    DesktopSectionCard("运行目录", supportingText = "运行时、config、日志、缓存和下载文件均使用该目录。更改后必须重启应用。") {
        DesktopInfoRow("当前生效", paths?.root?.absolutePath ?: "重启后按保存的路径生效", monospace = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            OutlinedTextField(draft.runtimeRootOverride, { onEdit(SettingsDraftEdit.RuntimeRootOverride(it)) }, Modifier.weight(1f), singleLine = true, isError = validation.pathError != null, label = { Text("自定义运行目录") }, placeholder = { Text("留空使用默认目录") }, textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), supportingText = { Text(validation.pathError ?: "必须是绝对路径；目录不存在时会在运行时创建。") })
            DesktopActionButton("浏览…", onBrowseRuntimeDirectory, style = DesktopActionButtonStyle.Outlined, icon = DesktopIcons.Folder, modifier = Modifier.padding(top = 8.dp))
        }
    }
    DesktopSectionCard("运行时目录结构") { DesktopEmptyState("目录结构由运行时自动维护", "应用会在启动时准备 runtime、data、logs 和 core-cache；此处不提供虚假的手动清理或迁移操作。", icon = DesktopIcons.Folder) }
}

@Composable
private fun NetworkDownloadDetail(draft: SettingsDraft, tokenInput: String, tokenVisible: Boolean, latencies: Map<String, Long>, testingLatencies: Boolean, latencyError: String?, onEdit: (SettingsDraftEdit) -> Unit, onTokenChange: (String) -> Unit, onTokenRemove: () -> Unit, onTokenVisibilityChange: (Boolean) -> Unit, onTestLatencies: () -> Unit) {
    DesktopSectionCard("GitHub 线路", supportingText = "核心安装与后续 GitHub 下载使用所选线路；下载器仍会按候选 URL 逐个回退并在全部失败时报告错误。", trailingContent = { DesktopActionButton(if (testingLatencies) "测速中…" else "并行测速", onTestLatencies, enabled = !testingLatencies, style = DesktopActionButtonStyle.Outlined) }) {
        GithubProxyCatalog.options.forEach { option -> ProxyOptionRow(option, draft.githubProxyId == option.id, latencies[option.id], latencies.filterValues { it >= 0 }.minByOrNull { it.value }?.key, testingLatencies) { onEdit(SettingsDraftEdit.GithubProxyId(option.id)) } }
        latencyError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
    DesktopSectionCard("GitHub Token", supportingText = "仅用于提高 GitHub API 限额，保存在本机设置文件中。") {
        OutlinedTextField(tokenInput, onTokenChange, Modifier.fillMaxWidth(), singleLine = true, visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(), label = { Text("新的 GitHub Token") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TextButton({ onTokenVisibilityChange(!tokenVisible) }) { Text(if (tokenVisible) "隐藏" else "显示") }; TextButton(onTokenRemove) { Text("清除") } }
    }
}

@Composable
private fun ProxyOptionRow(option: GithubProxyOption, selected: Boolean, latency: Long?, bestId: String?, testing: Boolean, onClick: () -> Unit) {
    DesktopSurface(Modifier.fillMaxWidth(), color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface, shape = DesktopTokens.ItemShape, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.material3.RadioButton(selected, onClick)
            Column(Modifier.weight(1f)) { Text(option.label, fontWeight = FontWeight.SemiBold); Text(if (option.isOriginal) "直接连接 api.github.com / codeload.github.com" else "使用该线路提供的候选地址，不会把 GitHub Token 发给镜像", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(when { testing -> "测速中…"; latency == null -> "未测速"; latency < 0 -> "失败"; bestId == option.id -> "最快 ${latency} ms"; else -> "${latency} ms" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SecurityAdminDetail(
    draft: SettingsDraft,
    tokenInput: String,
    tokenVisible: Boolean,
    onEdit: (SettingsDraftEdit) -> Unit,
    onTokenChange: (String) -> Unit,
    onTokenRemove: () -> Unit,
    onTokenVisibilityChange: (Boolean) -> Unit,
) {
    DesktopSectionCard(
        "管理员模式",
        supportingText = "对应核心 config/.env 的 ADMIN_TOKEN。用于未来缓存清理、工具和敏感配置页面；不会显示在普通 API 地址或日志中。",
    ) {
        DesktopInfoRow(
            label = "当前状态",
            value = if (draft.adminTokenConfigured) "已配置（令牌已隐藏）" else "未配置",
            supportingText = if (draft.adminTokenConfigured) {
                "保存后直接写入当前核心 config/.env，运行中的核心会按自身热加载机制刷新。"
            } else {
                "未配置时核心管理写操作不可用，普通 API 服务不受影响。"
            },
            leadingContent = { DesktopIcon(DesktopIcons.Tools, tint = MaterialTheme.colorScheme.primary) },
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = tokenInput,
            onValueChange = { value ->
                onTokenChange(value)
                onEdit(SettingsDraftEdit.AdminTokenConfigured(value.isNotBlank()))
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("ADMIN_TOKEN") },
            placeholder = { Text(if (draft.adminTokenConfigured) "留空保持当前配置；点击清除可关闭" else "输入核心管理员令牌") },
            visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onTokenVisibilityChange(!tokenVisible) }) { Text(if (tokenVisible) "隐藏" else "显示") }
            TextButton(onClick = onTokenRemove, enabled = draft.adminTokenConfigured || tokenInput.isNotBlank()) { Text("清除") }
        }
        Text(
            "保存令牌后不会在设置摘要中回显。当前核心已运行时会直接更新 config/.env，并由核心自身热加载；尚未建立运行目录时将在下次启动核心时写入。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable private fun DiagnosticsDetail() { DesktopSectionCard("诊断工具") { DesktopEmptyState("诊断功能尚未实现", "当前不会伪造健康检查、日志导出或自动修复结果。请直接查看运行目录中的 logs 文件排查问题。", icon = DesktopIcons.Tools) } }
@Composable private fun UpdatesAboutDetail() { DesktopSectionCard("更新与关于") { DesktopEmptyState("更新与关于功能尚未实现", "核心在线安装由服务启动流程负责；设置页暂不提供版本检查、更新按钮或许可证管理。", icon = DesktopIcons.About) } }

private fun settingsSnapshot(settings: DesktopSettings): DesktopSettingsSnapshot = DesktopSettingsSnapshot(
    runtimeRootOverride = settings.runtimeRootOverride,
    githubProxyId = settings.githubProxyId,
    theme = settings.theme,
    githubTokenConfigured = settings.githubToken.isNotBlank(),
    adminTokenConfigured = settings.adminTokenOverride?.isNotBlank() == true,
    portOverride = settings.portOverride,
    listenHostOverride = settings.listenHostOverride,
    ipv6Enabled = settings.ipv6Enabled,
    variantOverride = settings.variantOverride,
    closeAction = settings.closeAction,
)
private fun resolveRuntime(settings: DesktopSettings, paths: DesktopPaths): DesktopRuntimeConfig = DesktopRuntimeConfigResolver.resolve(settings, File(paths.runtimeDir, "nodejs-project"))
private fun iconFor(category: SettingsCategoryId): DesktopIconGlyph = when (category) {
    SettingsCategoryId.GeneralStartup -> DesktopIcons.Settings
    SettingsCategoryId.Service -> DesktopIcons.Core
    SettingsCategoryId.PathsRuntime -> DesktopIcons.Folder
    SettingsCategoryId.NetworkDownload -> DesktopIcons.Downloads
    SettingsCategoryId.SecurityAdmin -> DesktopIcons.Tools
    SettingsCategoryId.Diagnostics -> DesktopIcons.Tools
    SettingsCategoryId.UpdatesAbout -> DesktopIcons.About
}
private fun closeActionLabel(value: String): String = when (value) { "tray" -> "后台运行"; "exit" -> "退出并关闭服务"; else -> "每次询问" }

private fun chooseRuntimeDirectory(current: String, fallback: File?): Result<File?> = try {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
        dialogTitle = "选择弹幕API工作目录"
        val currentDir = current.trim().takeIf { it.isNotEmpty() }?.let(::File)
        currentDirectory = when { currentDir?.isDirectory == true -> currentDir; fallback?.isDirectory == true -> fallback; else -> File(System.getProperty("user.home")) }
    }
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) Result.success(null)
    else chooser.selectedFile?.let { if (it.isDirectory) Result.success(it) else Result.failure(IllegalStateException("选择的路径不是文件夹：${it.absolutePath}")) }
        ?: Result.failure(IllegalStateException("文件夹选择器未返回目录"))
} catch (error: Throwable) { Result.failure(error) }
