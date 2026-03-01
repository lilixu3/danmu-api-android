package com.example.danmuapiapp.data.repository

import android.content.Context
import androidx.core.content.edit
import com.example.danmuapiapp.data.util.AppAppearancePrefs
import com.example.danmuapiapp.data.service.NodeKeepAlivePrefs
import com.example.danmuapiapp.data.service.NormalAutoStartPrefs
import com.example.danmuapiapp.data.util.safeGetBoolean
import com.example.danmuapiapp.data.util.safeGetString
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.KeepAliveHeartbeatMode
import com.example.danmuapiapp.domain.model.NightModePreference
import com.example.danmuapiapp.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private val settingsPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val uiPrefs = context.getSharedPreferences(AppAppearancePrefs.PREFS_UI_LEGACY, Context.MODE_PRIVATE)
    private val uiScalePrefs = context.getSharedPreferences(AppAppearancePrefs.PREFS_UI_SCALE_LEGACY, Context.MODE_PRIVATE)
    private val githubProxyPrefs = context.getSharedPreferences("github_proxy_prefs", Context.MODE_PRIVATE)
    private val githubAuthPrefs = context.getSharedPreferences("github_auth_prefs", Context.MODE_PRIVATE)
    private val legacyVariantPrefs = context.getSharedPreferences("danmu_api_variant", Context.MODE_PRIVATE)

    private val _githubProxy = MutableStateFlow(
        githubProxyPrefs.safeGetString("selected_proxy", "original").ifBlank { "original" }
    )
    override val githubProxy: StateFlow<String> = _githubProxy.asStateFlow()

    private val _githubToken = MutableStateFlow(githubAuthPrefs.safeGetString("github_token"))
    override val githubToken: StateFlow<String> = _githubToken.asStateFlow()

    private val _autoStart = MutableStateFlow(NormalAutoStartPrefs.isBootAutoStartEnabled(context))
    override val autoStart: StateFlow<Boolean> = _autoStart.asStateFlow()

    private val _keepAlive = MutableStateFlow(NodeKeepAlivePrefs.isKeepAliveEnabled(context))
    override val keepAlive: StateFlow<Boolean> = _keepAlive.asStateFlow()

    private val _keepAliveHeartbeatEnabled =
        MutableStateFlow(NodeKeepAlivePrefs.isHeartbeatEnabled(context))
    override val keepAliveHeartbeatEnabled: StateFlow<Boolean> = _keepAliveHeartbeatEnabled.asStateFlow()

    private val _keepAliveHeartbeatMode =
        MutableStateFlow(NodeKeepAlivePrefs.getHeartbeatMode(context))
    override val keepAliveHeartbeatMode: StateFlow<KeepAliveHeartbeatMode> = _keepAliveHeartbeatMode.asStateFlow()

    private val _keepAliveHeartbeatIntervalMinutes =
        MutableStateFlow(NodeKeepAlivePrefs.getHeartbeatIntervalMinutes(context))
    override val keepAliveHeartbeatIntervalMinutes: StateFlow<Int> =
        _keepAliveHeartbeatIntervalMinutes.asStateFlow()

    private val _nightMode = MutableStateFlow(AppAppearancePrefs.readNightMode(uiPrefs))
    override val nightMode: StateFlow<NightModePreference> = _nightMode.asStateFlow()

    private val _appDpiOverride = MutableStateFlow(AppAppearancePrefs.readAppDpiOverride(uiScalePrefs))
    override val appDpiOverride: StateFlow<Int> = _appDpiOverride.asStateFlow()

    private val _hideFromRecents = MutableStateFlow(AppAppearancePrefs.readHideFromRecents(uiPrefs))
    override val hideFromRecents: StateFlow<Boolean> = _hideFromRecents.asStateFlow()

    private val _customRepo = MutableStateFlow(resolveCustomRepo())
    override val customRepo: StateFlow<String> = _customRepo.asStateFlow()

    private val _customRepoDisplayName = MutableStateFlow(resolveCustomRepoDisplayName())
    override val customRepoDisplayName: StateFlow<String> = _customRepoDisplayName.asStateFlow()

    private val _tokenVisible = MutableStateFlow(settingsPrefs.safeGetBoolean("token_visible", false))
    override val tokenVisible: StateFlow<Boolean> = _tokenVisible.asStateFlow()

    private val _fileLogEnabled = MutableStateFlow(false)
    override val fileLogEnabled: StateFlow<Boolean> = _fileLogEnabled.asStateFlow()

    private val _logEnabled = MutableStateFlow(settingsPrefs.safeGetBoolean("log_enabled", true))
    override val logEnabled: StateFlow<Boolean> = _logEnabled.asStateFlow()

    private val _logPreviewEnabled = MutableStateFlow(settingsPrefs.safeGetBoolean("log_preview_enabled", true))
    override val logPreviewEnabled: StateFlow<Boolean> = _logPreviewEnabled.asStateFlow()

    private val _logMaxCount = MutableStateFlow(settingsPrefs.getInt("log_max_count", 500))
    override val logMaxCount: StateFlow<Int> = _logMaxCount.asStateFlow()

    init {
        // 统一禁用文件日志，日志只走 /api/logs。
        if (settingsPrefs.safeGetBoolean("file_log_enabled", false)) {
            settingsPrefs.edit { putBoolean("file_log_enabled", false) }
        }
        AppAppearancePrefs.applyNightMode(_nightMode.value)
    }

    override fun setGithubProxy(proxy: String) {
        val normalized = proxy.trim().ifBlank { "original" }
        githubProxyPrefs.edit {
            putString("selected_proxy", normalized)
            putBoolean("has_user_selected_proxy", normalized != "original")
        }
        _githubProxy.value = normalized
    }

    override fun setGithubToken(token: String) {
        val normalized = token.trim()
        githubAuthPrefs.edit { putString("github_token", normalized) }
        _githubToken.value = normalized
    }

    override fun setAutoStart(enabled: Boolean) {
        NormalAutoStartPrefs.setBootAutoStartEnabled(context, enabled)
        _autoStart.value = enabled
    }

    override fun setKeepAlive(enabled: Boolean) {
        NodeKeepAlivePrefs.setKeepAliveEnabled(context, enabled)
        _keepAlive.value = enabled
    }

    override fun setKeepAliveHeartbeatEnabled(enabled: Boolean) {
        NodeKeepAlivePrefs.setHeartbeatEnabled(context, enabled)
        _keepAliveHeartbeatEnabled.value = enabled
    }

    override fun setKeepAliveHeartbeatMode(mode: KeepAliveHeartbeatMode) {
        NodeKeepAlivePrefs.setHeartbeatMode(context, mode)
        _keepAliveHeartbeatMode.value = mode
    }

    override fun setKeepAliveHeartbeatIntervalMinutes(minutes: Int) {
        val normalized = NodeKeepAlivePrefs.normalizeHeartbeatIntervalMinutes(minutes)
        NodeKeepAlivePrefs.setHeartbeatIntervalMinutes(context, normalized)
        _keepAliveHeartbeatIntervalMinutes.value = normalized
    }

    override fun setNightMode(mode: NightModePreference) {
        AppAppearancePrefs.writeNightMode(uiPrefs, mode)
        _nightMode.value = mode
        AppAppearancePrefs.applyNightMode(mode)
    }

    override fun setAppDpiOverride(dpi: Int) {
        val normalized = AppAppearancePrefs.normalizeAppDpiOverride(dpi)
        AppAppearancePrefs.writeAppDpiOverride(uiScalePrefs, normalized)
        _appDpiOverride.value = normalized
    }

    override fun setHideFromRecents(enabled: Boolean) {
        AppAppearancePrefs.writeHideFromRecents(uiPrefs, enabled)
        _hideFromRecents.value = enabled
    }

    override fun setCustomRepo(repo: String) {
        val normalized = repo.trim()
        settingsPrefs.edit { putString("custom_repo", normalized) }
        saveLegacyCustomRepo(normalized)
        _customRepo.value = normalized
    }

    override fun setCustomRepoDisplayName(name: String) {
        val normalized = name.trim()
        settingsPrefs.edit { putString("custom_repo_display_name", normalized) }
        _customRepoDisplayName.value = normalized
    }

    override fun setTokenVisible(visible: Boolean) {
        settingsPrefs.edit { putBoolean("token_visible", visible) }
        _tokenVisible.value = visible
    }

    override fun setFileLogEnabled(enabled: Boolean) {
        settingsPrefs.edit { putBoolean("file_log_enabled", false) }
        _fileLogEnabled.value = false
    }

    override fun setLogEnabled(enabled: Boolean) {
        settingsPrefs.edit { putBoolean("log_enabled", enabled) }
        _logEnabled.value = enabled
    }

    override fun setLogPreviewEnabled(enabled: Boolean) {
        settingsPrefs.edit { putBoolean("log_preview_enabled", enabled) }
        _logPreviewEnabled.value = enabled
    }

    override fun setLogMaxCount(count: Int) {
        val normalized = count.coerceIn(100, 2000)
        settingsPrefs.edit { putInt("log_max_count", normalized) }
        _logMaxCount.value = normalized
    }

    override fun getIgnoredUpdateVersion(variant: ApiVariant): String? {
        return settingsPrefs.safeGetString("ignored_update_${variant.key}").ifBlank { null }
    }

    override fun setIgnoredUpdateVersion(variant: ApiVariant, version: String?) {
        val key = "ignored_update_${variant.key}"
        settingsPrefs.edit {
            if (version.isNullOrBlank()) remove(key) else putString(key, version.trim())
        }
    }

    private fun resolveCustomRepoDisplayName(): String {
        return settingsPrefs.safeGetString("custom_repo_display_name").trim()
    }

    private fun resolveCustomRepo(): String {
        val direct = settingsPrefs.safeGetString("custom_repo").trim()
        if (direct.isNotBlank()) return direct
        val owner = legacyVariantPrefs.safeGetString("custom_owner").trim()
        val repo = legacyVariantPrefs.safeGetString("custom_repo").trim()
        return if (owner.isNotBlank() && repo.isNotBlank()) "$owner/$repo" else repo
    }

    private fun saveLegacyCustomRepo(value: String) {
        val normalized = value
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .trim()
            .trim('/')

        if (normalized.isBlank()) {
            legacyVariantPrefs.edit {
                putString("custom_owner", "")
                putString("custom_repo", "")
            }
            return
        }

        val parts = normalized.split('/').filter { it.isNotBlank() }
        val owner = if (parts.size >= 2) parts[0] else ""
        val repo = if (parts.size >= 2) parts[1] else parts[0]
        legacyVariantPrefs.edit {
            putString("custom_owner", owner)
            putString("custom_repo", repo)
        }
    }
}
