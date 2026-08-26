package com.example.danmuapiapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.danmuapiapp.data.util.AppAppearancePrefs
import com.example.danmuapiapp.data.util.SecureStringStore
import com.example.danmuapiapp.data.service.CoreUpdateCheckPolicy
import com.example.danmuapiapp.data.service.NormalAutoStartPrefs
import com.example.danmuapiapp.data.service.NormalModeStabilityPrefs
import com.example.danmuapiapp.data.service.NormalNotificationBehaviorPrefs
import com.example.danmuapiapp.data.service.RuntimePaths
import com.example.danmuapiapp.data.util.safeGetBoolean
import com.example.danmuapiapp.data.util.safeGetString
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.AppBackgroundPreference
import com.example.danmuapiapp.domain.model.CoreBranchSelections
import com.example.danmuapiapp.domain.model.CoreVariantDisplayNames
import com.example.danmuapiapp.domain.model.GlassMaterialPreference
import com.example.danmuapiapp.domain.model.GlassTuningPreference
import com.example.danmuapiapp.domain.model.NightModePreference
import com.example.danmuapiapp.domain.model.NormalModeStabilityMode
import com.example.danmuapiapp.domain.model.NormalNotificationBehavior
import com.example.danmuapiapp.domain.model.ResolvedCustomCoreConfig
import com.example.danmuapiapp.domain.model.ResolvedCustomCoreSource
import com.example.danmuapiapp.domain.model.normalizeGithubBranch
import com.example.danmuapiapp.domain.model.normalizeGithubRepo
import com.example.danmuapiapp.domain.model.resolveCustomCoreConfig
import com.example.danmuapiapp.domain.model.resolveCustomCoreSource
import com.example.danmuapiapp.domain.model.resolveRepoOnlyCustomCoreSource
import com.example.danmuapiapp.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {
    companion object {
        private const val DEFAULT_ANNOUNCEMENT_BASE_URL = "http://117.72.165.47:18086"
        private const val CORE_UPDATE_CHECK_INTERVAL_MINUTES_KEY =
            "core_update_check_interval_minutes"
    }

    private val settingsPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val uiPrefs = context.getSharedPreferences(AppAppearancePrefs.PREFS_UI_LEGACY, Context.MODE_PRIVATE)
    private val uiScalePrefs = context.getSharedPreferences(AppAppearancePrefs.PREFS_UI_SCALE_LEGACY, Context.MODE_PRIVATE)
    private val githubProxyPrefs = context.getSharedPreferences("github_proxy_prefs", Context.MODE_PRIVATE)
    private val githubAuthPrefs = context.getSharedPreferences("github_auth_prefs", Context.MODE_PRIVATE)
    private val githubTokenStore = SecureStringStore(
        githubAuthPrefs,
        "danmuapi_github_auth_v1",
        allowPlaintextFallback = false
    )
    private val legacyVariantPrefs = context.getSharedPreferences("danmu_api_variant", Context.MODE_PRIVATE)
    private val workDirPrefs = context.getSharedPreferences(RuntimePaths.PREFS_WORK_DIR, Context.MODE_PRIVATE)
    private val workDirCustomCorePreferences = WorkDirCustomCorePreferences(settingsPrefs)
    private val customCoreConfigLock = Any()
    private var activeWorkDirIdentity = RuntimePaths.normalWorkDirIdentity(context)

    init {
        val legacyConfig = resolveLegacyCustomCoreConfig()
        workDirCustomCorePreferences.migrateLegacyConfigIfNeeded(
            workDirIdentity = activeWorkDirIdentity,
            legacyConfig = legacyConfig,
            hasLegacyConfig = legacyConfig.hasAnyValue()
        )
    }

    private val _githubProxy = MutableStateFlow(
        githubProxyPrefs.safeGetString("selected_proxy", "original").ifBlank { "original" }
    )
    override val githubProxy: StateFlow<String> = _githubProxy.asStateFlow()

    private val _announcementBaseUrl = MutableStateFlow(DEFAULT_ANNOUNCEMENT_BASE_URL)
    override val announcementBaseUrl: StateFlow<String> = _announcementBaseUrl.asStateFlow()

    private val _autoStart = MutableStateFlow(NormalAutoStartPrefs.isBootAutoStartEnabled(context))
    override val autoStart: StateFlow<Boolean> = _autoStart.asStateFlow()

    private val _coreUpdateCheckIntervalMinutes = MutableStateFlow(
        CoreUpdateCheckPolicy.normalizeIntervalMinutes(
            settingsPrefs.getInt(
                CORE_UPDATE_CHECK_INTERVAL_MINUTES_KEY,
                CoreUpdateCheckPolicy.DEFAULT_INTERVAL_MINUTES
            )
        )
    )
    override val coreUpdateCheckIntervalMinutes: StateFlow<Int> =
        _coreUpdateCheckIntervalMinutes.asStateFlow()

    private val _normalModeStabilityMode = MutableStateFlow(NormalModeStabilityPrefs.get(context))
    override val normalModeStabilityMode: StateFlow<NormalModeStabilityMode> =
        _normalModeStabilityMode.asStateFlow()

    private val _normalNotificationBehavior =
        MutableStateFlow(NormalNotificationBehaviorPrefs.get(context))
    override val normalNotificationBehavior: StateFlow<NormalNotificationBehavior> =
        _normalNotificationBehavior.asStateFlow()

    private val _nightMode = MutableStateFlow(AppAppearancePrefs.readNightMode(uiPrefs))
    override val nightMode: StateFlow<NightModePreference> = _nightMode.asStateFlow()

    private val _glassMaterial = MutableStateFlow(AppAppearancePrefs.readGlassMaterial(uiPrefs))
    override val glassMaterial: StateFlow<GlassMaterialPreference> = _glassMaterial.asStateFlow()

    private val _glassTuning = MutableStateFlow(AppAppearancePrefs.readGlassTuning(uiPrefs))
    override val glassTuning: StateFlow<GlassTuningPreference> = _glassTuning.asStateFlow()

    private val _appBackground = MutableStateFlow(AppAppearancePrefs.readAppBackground(uiPrefs))
    override val appBackground: StateFlow<AppBackgroundPreference> = _appBackground.asStateFlow()

    private val _appDpiOverride = MutableStateFlow(AppAppearancePrefs.readAppDpiOverride(uiScalePrefs))
    override val appDpiOverride: StateFlow<Int> = _appDpiOverride.asStateFlow()

    private val _hideFromRecents = MutableStateFlow(AppAppearancePrefs.readHideFromRecents(uiPrefs))
    override val hideFromRecents: StateFlow<Boolean> = _hideFromRecents.asStateFlow()

    private val _coreDisplayNames = MutableStateFlow(resolveCoreDisplayNames())
    override val coreDisplayNames: StateFlow<CoreVariantDisplayNames> = _coreDisplayNames.asStateFlow()

    private val _customCoreSource = MutableStateFlow(resolveStoredCustomCoreSource())
    override val customCoreSource: StateFlow<ResolvedCustomCoreSource> = _customCoreSource.asStateFlow()

    private val _customRepo = MutableStateFlow(_customCoreSource.value.repo)
    override val customRepo: StateFlow<String> = _customRepo.asStateFlow()

    private val _customRepoBranch = MutableStateFlow(_customCoreSource.value.branch)
    override val customRepoBranch: StateFlow<String> = _customRepoBranch.asStateFlow()

    private val _customRepoDisplayName = MutableStateFlow(_coreDisplayNames.value.custom)
    override val customRepoDisplayName: StateFlow<String> = _customRepoDisplayName.asStateFlow()

    private val _coreBranchSelections = MutableStateFlow(resolveCoreBranchSelections())
    override val coreBranchSelections: StateFlow<CoreBranchSelections> =
        _coreBranchSelections.asStateFlow()

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

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (prefs !== settingsPrefs) return@OnSharedPreferenceChangeListener
        when {
            workDirCustomCorePreferences.isKeyForWorkDir(key, activeWorkDirIdentity) -> {
                synchronized(customCoreConfigLock) {
                    refreshActiveWorkDirCustomCoreStateLocked()
                }
            }
            key == displayNameKeyForVariant(ApiVariant.Stable) ||
                key == displayNameKeyForVariant(ApiVariant.Dev) -> {
                applyCoreDisplayNamesState(resolveCoreDisplayNames())
            }
            key == branchKeyForVariant(ApiVariant.Stable) ||
                key == branchKeyForVariant(ApiVariant.Dev) -> {
                applyCoreBranchSelectionsState(resolveCoreBranchSelections())
            }
        }
    }

    private val workDirPrefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (prefs === workDirPrefs && RuntimePaths.isWorkDirSelectionPreference(key)) {
            synchronized(customCoreConfigLock) {
                refreshActiveWorkDirCustomCoreStateLocked(mirrorLegacyValues = true)
            }
        }
    }

    init {
        settingsPrefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        workDirPrefs.registerOnSharedPreferenceChangeListener(workDirPrefChangeListener)
        // 统一禁用文件日志，日志只走 /api/logs。
        if (settingsPrefs.safeGetBoolean("file_log_enabled", false)) {
            settingsPrefs.edit { putBoolean("file_log_enabled", false) }
        }
        // 公告服务改为内置固定地址，忽略历史用户配置。
        if (settingsPrefs.contains("announcement_base_url")) {
            settingsPrefs.edit { remove("announcement_base_url") }
        }
        synchronized(customCoreConfigLock) {
            mirrorLegacyCustomCoreConfig(resolveStoredCustomCoreConfigLocked())
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
        check(githubTokenStore.put("github_token", normalized)) {
            "无法使用 Android Keystore 安全保存 GitHub Token"
        }
    }

    override fun setAutoStart(enabled: Boolean) {
        NormalAutoStartPrefs.setBootAutoStartEnabled(context, enabled)
        _autoStart.value = enabled
    }

    override fun setCoreUpdateCheckIntervalMinutes(minutes: Int) {
        val normalized = CoreUpdateCheckPolicy.normalizeIntervalMinutes(minutes)
        settingsPrefs.edit { putInt(CORE_UPDATE_CHECK_INTERVAL_MINUTES_KEY, normalized) }
        _coreUpdateCheckIntervalMinutes.value = normalized
    }

    override fun setNormalModeStabilityMode(mode: NormalModeStabilityMode) {
        NormalModeStabilityPrefs.set(context, mode)
        _normalModeStabilityMode.value = mode
    }

    override fun setNormalNotificationBehavior(behavior: NormalNotificationBehavior) {
        NormalNotificationBehaviorPrefs.set(context, behavior)
        _normalNotificationBehavior.value = behavior
    }

    override fun setNightMode(mode: NightModePreference) {
        AppAppearancePrefs.writeNightMode(uiPrefs, mode)
        _nightMode.value = mode
        AppAppearancePrefs.applyNightMode(mode)
    }

    override fun setGlassMaterial(material: GlassMaterialPreference) {
        AppAppearancePrefs.writeGlassMaterial(uiPrefs, material)
        _glassMaterial.value = material
    }

    override fun setGlassTuning(tuning: GlassTuningPreference) {
        val normalized = tuning.normalized()
        AppAppearancePrefs.writeGlassTuning(uiPrefs, normalized)
        _glassTuning.value = normalized
    }

    override fun setAppBackground(background: AppBackgroundPreference) {
        AppAppearancePrefs.writeAppBackground(uiPrefs, background)
        _appBackground.value = background
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

    override fun setVariantDisplayName(variant: ApiVariant, name: String) {
        val normalized = name.trim()
        if (variant == ApiVariant.Custom) {
            synchronized(customCoreConfigLock) {
                synchronizeActiveWorkDirLocked()
                val workDirIdentity = activeWorkDirIdentity
                val current = resolveStoredCustomCoreConfigLocked(workDirIdentity)
                workDirCustomCorePreferences.write(
                    workDirIdentity,
                    current.copy(displayName = normalized)
                )
                settingsPrefs.edit { putString(displayNameKeyForVariant(ApiVariant.Custom), normalized) }
                applyCoreDisplayNamesState(_coreDisplayNames.value.copy(custom = normalized))
            }
            return
        }
        settingsPrefs.edit { putString(displayNameKeyForVariant(variant), normalized) }
        applyCoreDisplayNamesState(
            when (variant) {
                ApiVariant.Stable -> _coreDisplayNames.value.copy(stable = normalized)
                ApiVariant.Dev -> _coreDisplayNames.value.copy(dev = normalized)
                ApiVariant.Custom -> error("Custom display name is handled above")
            }
        )
    }

    override fun setCoreBranch(variant: ApiVariant, branch: String) {
        val normalized = normalizeGithubBranch(branch)
        if (variant == ApiVariant.Custom) {
            setCustomRepoBranch(normalized)
            return
        }
        settingsPrefs.edit {
            if (normalized.isBlank()) remove(branchKeyForVariant(variant))
            else putString(branchKeyForVariant(variant), normalized)
        }
        applyCoreBranchSelectionsState(_coreBranchSelections.value.withSelection(variant, normalized))
    }

    override fun saveCustomCoreSource(
        repoInput: String,
        branchInput: String
    ): ResolvedCustomCoreSource = synchronized(customCoreConfigLock) {
        synchronizeActiveWorkDirLocked()
        val workDirIdentity = activeWorkDirIdentity
        val resolved = resolveCustomCoreSource(repoInput, branchInput)
        workDirCustomCorePreferences.write(
            workDirIdentity,
            resolveStoredCustomCoreConfigLocked(workDirIdentity).copy(
                repo = resolved.repo,
                branch = resolved.branch
            )
        )
        settingsPrefs.edit {
            putString("custom_repo", resolved.repo)
            if (resolved.repo.isBlank()) {
                remove("custom_repo_branch")
            } else {
                putString("custom_repo_branch", resolved.branch)
            }
        }
        saveLegacyCustomRepo(resolved.repo)
        applyCustomCoreSourceState(resolved)
        resolved
    }

    override fun saveCustomCoreConfig(
        displayName: String,
        repoInput: String,
        branchInput: String
    ): ResolvedCustomCoreConfig = synchronized(customCoreConfigLock) {
        synchronizeActiveWorkDirLocked()
        val workDirIdentity = activeWorkDirIdentity
        val resolvedConfig = resolveCustomCoreConfig(displayName, repoInput, branchInput)
        workDirCustomCorePreferences.write(
            workDirIdentity,
            StoredCustomCoreConfig(
                displayName = resolvedConfig.displayName,
                repo = resolvedConfig.repo,
                branch = resolvedConfig.branch
            )
        )
        settingsPrefs.edit {
            putString(displayNameKeyForVariant(ApiVariant.Custom), resolvedConfig.displayName)
            putString("custom_repo", resolvedConfig.repo)
            if (resolvedConfig.repo.isBlank()) {
                remove("custom_repo_branch")
            } else {
                putString("custom_repo_branch", resolvedConfig.branch)
            }
        }
        saveLegacyCustomRepo(resolvedConfig.repo)
        applyCoreDisplayNamesState(_coreDisplayNames.value.copy(custom = resolvedConfig.displayName))
        applyCustomCoreSourceState(
            resolveCustomCoreSource(resolvedConfig.repo, resolvedConfig.branch)
        )
        resolvedConfig
    }

    override fun setCustomRepo(repo: String) {
        synchronized(customCoreConfigLock) {
            synchronizeActiveWorkDirLocked()
            val resolved = resolveRepoOnlyCustomCoreSource(
                repoInput = repo,
                currentBranch = _customRepoBranch.value
            )
            saveCustomCoreSource(repoInput = resolved.repo, branchInput = resolved.branch)
        }
    }

    override fun setCustomRepoBranch(branch: String) {
        synchronized(customCoreConfigLock) {
            synchronizeActiveWorkDirLocked()
            saveCustomCoreSource(repoInput = _customRepo.value, branchInput = branch)
        }
    }

    override fun setCustomRepoDisplayName(name: String) {
        setVariantDisplayName(ApiVariant.Custom, name)
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

    override fun reloadFromStorage() {
        _githubProxy.value = githubProxyPrefs.safeGetString("selected_proxy", "original").ifBlank { "original" }
        _autoStart.value = NormalAutoStartPrefs.isBootAutoStartEnabled(context)
        _coreUpdateCheckIntervalMinutes.value = CoreUpdateCheckPolicy.normalizeIntervalMinutes(
            settingsPrefs.getInt(
                CORE_UPDATE_CHECK_INTERVAL_MINUTES_KEY,
                CoreUpdateCheckPolicy.DEFAULT_INTERVAL_MINUTES
            )
        )
        _normalModeStabilityMode.value = NormalModeStabilityPrefs.get(context)
        _normalNotificationBehavior.value = NormalNotificationBehaviorPrefs.reloadFromSettings(context)
        _nightMode.value = AppAppearancePrefs.readNightMode(uiPrefs)
        _glassMaterial.value = AppAppearancePrefs.readGlassMaterial(uiPrefs)
        _glassTuning.value = AppAppearancePrefs.readGlassTuning(uiPrefs)
        _appBackground.value = AppAppearancePrefs.readAppBackground(uiPrefs)
        _appDpiOverride.value = AppAppearancePrefs.readAppDpiOverride(uiScalePrefs)
        _hideFromRecents.value = AppAppearancePrefs.readHideFromRecents(uiPrefs)
        _tokenVisible.value = settingsPrefs.safeGetBoolean("token_visible", false)
        _logEnabled.value = settingsPrefs.safeGetBoolean("log_enabled", true)
        _logPreviewEnabled.value = settingsPrefs.safeGetBoolean("log_preview_enabled", true)
        _logMaxCount.value = settingsPrefs.getInt("log_max_count", 500).coerceIn(100, 2000)
        synchronized(customCoreConfigLock) {
            refreshActiveWorkDirCustomCoreStateLocked(mirrorLegacyValues = true)
        }
        applyCoreBranchSelectionsState(resolveCoreBranchSelections())
        AppAppearancePrefs.applyNightMode(_nightMode.value)
    }

    private fun resolveCoreDisplayNames(): CoreVariantDisplayNames {
        return CoreVariantDisplayNames(
            stable = settingsPrefs.safeGetString(displayNameKeyForVariant(ApiVariant.Stable)).trim(),
            dev = settingsPrefs.safeGetString(displayNameKeyForVariant(ApiVariant.Dev)).trim(),
            custom = resolveStoredCustomCoreConfigLocked(activeWorkDirIdentity).displayName.trim()
        )
    }

    private fun resolveCoreBranchSelections(): CoreBranchSelections {
        return CoreBranchSelections(
            stable = normalizeGithubBranch(settingsPrefs.safeGetString(branchKeyForVariant(ApiVariant.Stable))),
            dev = normalizeGithubBranch(settingsPrefs.safeGetString(branchKeyForVariant(ApiVariant.Dev))),
            custom = _customRepoBranch.value
        )
    }

    private fun resolveLegacyCustomRepo(): String {
        val direct = normalizeGithubRepo(settingsPrefs.safeGetString("custom_repo"))
        if (direct.isNotBlank()) return direct
        val owner = legacyVariantPrefs.safeGetString("custom_owner").trim()
        val repo = legacyVariantPrefs.safeGetString("custom_repo").trim()
        return normalizeGithubRepo(if (owner.isNotBlank() && repo.isNotBlank()) "$owner/$repo" else repo)
    }

    private fun resolveLegacyCustomCoreConfig(): StoredCustomCoreConfig {
        return StoredCustomCoreConfig(
            displayName = settingsPrefs.safeGetString(displayNameKeyForVariant(ApiVariant.Custom)).trim(),
            repo = resolveLegacyCustomRepo(),
            branch = settingsPrefs.safeGetString("custom_repo_branch").trim()
        )
    }

    private fun resolveStoredCustomCoreConfigLocked(
        workDirIdentity: String = activeWorkDirIdentity
    ): StoredCustomCoreConfig {
        return workDirCustomCorePreferences.read(workDirIdentity)
    }

    private fun resolveStoredCustomCoreSource(): ResolvedCustomCoreSource {
        val config = resolveStoredCustomCoreConfigLocked()
        return resolveCustomCoreSource(
            repoInput = config.repo,
            branchInput = config.branch
        )
    }

    private fun synchronizeActiveWorkDirLocked() {
        val currentIdentity = RuntimePaths.normalWorkDirIdentity(context)
        if (currentIdentity != activeWorkDirIdentity) {
            activeWorkDirIdentity = currentIdentity
            refreshActiveWorkDirCustomCoreStateLocked()
        }
    }

    private fun refreshActiveWorkDirCustomCoreStateLocked(mirrorLegacyValues: Boolean = false) {
        activeWorkDirIdentity = RuntimePaths.normalWorkDirIdentity(context)
        val config = resolveStoredCustomCoreConfigLocked()
        applyCoreDisplayNamesState(
            resolveCoreDisplayNames().copy(custom = config.displayName.trim())
        )
        applyCustomCoreSourceState(
            resolveCustomCoreSource(config.repo, config.branch)
        )
        if (mirrorLegacyValues) {
            mirrorLegacyCustomCoreConfig(config)
        }
    }

    private fun mirrorLegacyCustomCoreConfig(config: StoredCustomCoreConfig) {
        settingsPrefs.edit {
            putString(displayNameKeyForVariant(ApiVariant.Custom), config.displayName)
            putString("custom_repo", config.repo)
            if (config.repo.isBlank()) {
                remove("custom_repo_branch")
            } else {
                putString("custom_repo_branch", config.branch)
            }
        }
        saveLegacyCustomRepo(config.repo)
    }

    private fun applyCoreDisplayNamesState(names: CoreVariantDisplayNames) {
        _coreDisplayNames.value = names
        _customRepoDisplayName.value = names.custom
    }

    private fun applyCustomCoreSourceState(source: ResolvedCustomCoreSource) {
        _customCoreSource.value = source
        _customRepo.value = source.repo
        _customRepoBranch.value = source.branch
        applyCoreBranchSelectionsState(_coreBranchSelections.value.withSelection(ApiVariant.Custom, source.branch))
    }

    private fun applyCoreBranchSelectionsState(selections: CoreBranchSelections) {
        _coreBranchSelections.value = selections
    }

    private fun saveLegacyCustomRepo(value: String) {
        val normalized = normalizeGithubRepo(value)

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

    private fun displayNameKeyForVariant(variant: ApiVariant): String {
        return when (variant) {
            ApiVariant.Stable -> "stable_repo_display_name"
            ApiVariant.Dev -> "dev_repo_display_name"
            ApiVariant.Custom -> "custom_repo_display_name"
        }
    }

    private fun branchKeyForVariant(variant: ApiVariant): String {
        return when (variant) {
            ApiVariant.Stable -> "stable_repo_branch"
            ApiVariant.Dev -> "dev_repo_branch"
            ApiVariant.Custom -> "custom_repo_branch"
        }
    }

    private fun StoredCustomCoreConfig.hasAnyValue(): Boolean {
        return displayName.isNotBlank() || repo.isNotBlank() || branch.isNotBlank()
    }
}
