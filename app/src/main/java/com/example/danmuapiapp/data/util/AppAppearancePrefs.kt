package com.example.danmuapiapp.data.util

import com.example.danmuapiapp.domain.model.AccessEntryTab
import com.example.danmuapiapp.domain.model.AccessEntryLayout
import android.content.Context
import android.content.res.Configuration
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.appcompat.app.AppCompatDelegate
import com.example.danmuapiapp.domain.model.AppBackgroundMode
import com.example.danmuapiapp.domain.model.AppBackgroundPreference
import com.example.danmuapiapp.domain.model.AppBackgroundRefreshPolicy
import com.example.danmuapiapp.domain.model.GlassMaterialPreference
import com.example.danmuapiapp.domain.model.GlassTuningPreference
import com.example.danmuapiapp.domain.model.NightModePreference
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object AppAppearancePrefs {
    const val PREFS_UI_LEGACY = "danmu_ui_prefs"
    const val PREFS_UI_SCALE_LEGACY = "danmu_ui_scale_prefs"

    const val PREF_KEY_NIGHT_MODE = "night_mode_pref"
    const val PREF_KEY_GLASS_MATERIAL = "glass_material_pref"
    const val PREF_KEY_GLASS_TUNING = "glass_tuning_v1"
    /** 液态玻璃底栏单独开关：只让底部导航栏使用玻璃效果。 */
    const val PREF_KEY_GLASS_BOTTOM_BAR = "glass_bottom_bar_enabled"
    const val PREF_KEY_BACKGROUND_MODE = "background_mode"
    const val PREF_KEY_BACKGROUND_LOCAL_URI = "background_local_uri"
    const val PREF_KEY_BACKGROUND_ONLINE_URL = "background_online_url"
    const val PREF_KEY_BACKGROUND_RANDOM_URL = "background_random_url"
    const val PREF_KEY_BACKGROUND_RANDOM_REFRESH_POLICY = "background_random_refresh_policy"
    const val PREF_KEY_BACKGROUND_CUSTOM_REFRESH_SECONDS = "background_custom_refresh_seconds"
    const val PREF_KEY_BACKGROUND_RANDOM_URL_MIGRATED = "background_random_url_migrated"
    const val PREF_KEY_DARK_THEME_LEGACY = "dark_theme"
    const val PREF_KEY_HIDE_FROM_RECENTS = "hide_from_recents"
    // The historical key belongs to the normal interface. Compat starts at system DPI.
    const val PREF_KEY_APP_DPI_OVERRIDE = "app_dpi_override"
    const val PREF_KEY_COMPAT_DPI_OVERRIDE = "compat_dpi_override"

    // 仅影响应用内显示：小于等于 0 表示跟随系统。
    const val APP_DPI_SYSTEM = -1
    const val APP_DPI_MIN = 120
    const val APP_DPI_MAX = 960

    private val appearanceJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private const val PREF_KEY_ACCESS_ENTRY_DEFAULT_TAB = "access_entry_default_tab"

    fun readAccessEntryDefaultTab(prefs: SharedPreferences): AccessEntryTab =
        AccessEntryTab.fromStorageValue(prefs.safeGetString(PREF_KEY_ACCESS_ENTRY_DEFAULT_TAB))

    fun writeAccessEntryDefaultTab(prefs: SharedPreferences, tab: AccessEntryTab) {
        prefs.edit { putString(PREF_KEY_ACCESS_ENTRY_DEFAULT_TAB, tab.storageValue) }
    }

    private const val PREF_KEY_ACCESS_ENTRY_LAYOUT = "access_entry_layout"

    fun readAccessEntryLayout(prefs: SharedPreferences): AccessEntryLayout =
        AccessEntryLayout.fromStorageValue(prefs.safeGetString(PREF_KEY_ACCESS_ENTRY_LAYOUT))

    fun writeAccessEntryLayout(prefs: SharedPreferences, layout: AccessEntryLayout) {
        prefs.edit { putString(PREF_KEY_ACCESS_ENTRY_LAYOUT, layout.storageValue) }
    }

    fun readNightMode(prefs: SharedPreferences): NightModePreference {
        if (!prefs.contains(PREF_KEY_NIGHT_MODE)) {
            if (prefs.contains(PREF_KEY_DARK_THEME_LEGACY)) {
                return if (prefs.safeGetBoolean(PREF_KEY_DARK_THEME_LEGACY, false)) {
                    NightModePreference.Dark
                } else {
                    NightModePreference.Light
                }
            }
            return NightModePreference.FollowSystem
        }
        val raw = prefs.safeGetInt(
            PREF_KEY_NIGHT_MODE,
            NightModePreference.FollowSystem.storageValue
        )
        return NightModePreference.fromStorageValue(raw)
    }

    fun writeNightMode(prefs: SharedPreferences, mode: NightModePreference) {
        val legacyDark = when (mode) {
            NightModePreference.Dark -> true
            NightModePreference.Light -> false
            NightModePreference.FollowSystem -> prefs.safeGetBoolean(PREF_KEY_DARK_THEME_LEGACY, false)
        }
        prefs.edit {
            putInt(PREF_KEY_NIGHT_MODE, mode.storageValue)
            putBoolean(PREF_KEY_DARK_THEME_LEGACY, legacyDark)
        }
    }

    fun readGlassMaterial(prefs: SharedPreferences): GlassMaterialPreference {
        val raw = prefs.safeGetInt(
            PREF_KEY_GLASS_MATERIAL,
            GlassMaterialPreference.Default.storageValue
        )
        return GlassMaterialPreference.fromStorageValue(raw)
    }

    fun writeGlassMaterial(
        prefs: SharedPreferences,
        material: GlassMaterialPreference
    ) {
        prefs.edit { putInt(PREF_KEY_GLASS_MATERIAL, material.storageValue) }
    }

    fun readGlassBottomBar(prefs: SharedPreferences): Boolean {
        return prefs.safeGetBoolean(PREF_KEY_GLASS_BOTTOM_BAR, false)
    }

    fun writeGlassBottomBar(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit { putBoolean(PREF_KEY_GLASS_BOTTOM_BAR, enabled) }
    }

    fun readGlassTuning(prefs: SharedPreferences): GlassTuningPreference {
        val raw = prefs.safeGetString(PREF_KEY_GLASS_TUNING)
        if (raw.isBlank()) return GlassTuningPreference()
        return runCatching {
            appearanceJson.decodeFromString<GlassTuningPreference>(raw).normalized()
        }.getOrDefault(GlassTuningPreference())
    }

    fun writeGlassTuning(
        prefs: SharedPreferences,
        tuning: GlassTuningPreference
    ) {
        val normalized = tuning.normalized()
        prefs.edit {
            if (normalized.isDefault) {
                remove(PREF_KEY_GLASS_TUNING)
            } else {
                putString(PREF_KEY_GLASS_TUNING, appearanceJson.encodeToString(normalized))
            }
        }
    }

    fun readAppBackground(prefs: SharedPreferences): AppBackgroundPreference {
        val storedRandomImageUrl = prefs.safeGetString(
            PREF_KEY_BACKGROUND_RANDOM_URL,
            AppBackgroundPreference.DEFAULT_RANDOM_IMAGE_URL
        ).ifBlank { AppBackgroundPreference.DEFAULT_RANDOM_IMAGE_URL }
        val randomImageUrl = if (
            storedRandomImageUrl == AppBackgroundPreference.PICSUM_BACKUP_IMAGE_URL &&
            !prefs.safeGetBoolean(PREF_KEY_BACKGROUND_RANDOM_URL_MIGRATED, false)
        ) {
            AppBackgroundPreference.DEFAULT_RANDOM_IMAGE_URL
        } else {
            storedRandomImageUrl
        }
        return AppBackgroundPreference(
            mode = AppBackgroundMode.fromStorageValue(
                prefs.safeGetInt(PREF_KEY_BACKGROUND_MODE, AppBackgroundMode.Solid.storageValue)
            ),
            localImageUri = prefs.safeGetString(PREF_KEY_BACKGROUND_LOCAL_URI),
            onlineImageUrl = prefs.safeGetString(PREF_KEY_BACKGROUND_ONLINE_URL),
            randomImageUrl = randomImageUrl,
            randomRefreshPolicy = AppBackgroundRefreshPolicy.fromStorageValue(
                prefs.safeGetInt(
                    PREF_KEY_BACKGROUND_RANDOM_REFRESH_POLICY,
                    AppBackgroundRefreshPolicy.OnForeground.storageValue
                )
            ),
            customRandomRefreshSeconds = prefs.safeGetString(
                PREF_KEY_BACKGROUND_CUSTOM_REFRESH_SECONDS
            ).toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        )
    }

    fun writeAppBackground(
        prefs: SharedPreferences,
        background: AppBackgroundPreference
    ) {
        prefs.edit {
            putInt(PREF_KEY_BACKGROUND_MODE, background.mode.storageValue)
            putString(PREF_KEY_BACKGROUND_LOCAL_URI, background.localImageUri)
            putString(PREF_KEY_BACKGROUND_ONLINE_URL, background.onlineImageUrl)
            putString(PREF_KEY_BACKGROUND_RANDOM_URL, background.randomImageUrl)
            putBoolean(PREF_KEY_BACKGROUND_RANDOM_URL_MIGRATED, true)
            putInt(
                PREF_KEY_BACKGROUND_RANDOM_REFRESH_POLICY,
                background.randomRefreshPolicy.storageValue
            )
            putString(
                PREF_KEY_BACKGROUND_CUSTOM_REFRESH_SECONDS,
                background.customRandomRefreshSeconds.toString()
            )
        }
    }

    fun readHideFromRecents(prefs: SharedPreferences): Boolean {
        return prefs.safeGetBoolean(PREF_KEY_HIDE_FROM_RECENTS, false)
    }

    fun writeHideFromRecents(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit { putBoolean(PREF_KEY_HIDE_FROM_RECENTS, enabled) }
    }

    fun readAppDpiOverride(prefs: SharedPreferences, compat: Boolean = false): Int {
        val raw = prefs.safeGetInt(dpiPreferenceKey(compat), APP_DPI_SYSTEM)
        return normalizeAppDpiOverride(raw)
    }

    fun writeAppDpiOverride(prefs: SharedPreferences, dpi: Int, compat: Boolean = false) {
        prefs.edit {
            putInt(dpiPreferenceKey(compat), normalizeAppDpiOverride(dpi))
        }
    }

    fun normalizeAppDpiOverride(dpi: Int): Int {
        if (dpi <= 0) return APP_DPI_SYSTEM
        return dpi.coerceIn(APP_DPI_MIN, APP_DPI_MAX)
    }

    internal fun dpiPreferenceKey(compat: Boolean): String =
        if (compat) PREF_KEY_COMPAT_DPI_OVERRIDE else PREF_KEY_APP_DPI_OVERRIDE

    fun systemDensityDpi(context: Context): Int =
        context.applicationContext.resources.displayMetrics.densityDpi

    internal fun shouldRecreateForDpi(savedDpi: Int, targetDpi: Int, actualDpi: Int, systemDpi: Int): Boolean =
        savedDpi != targetDpi || actualDpi != if (targetDpi > 0) targetDpi else systemDpi

    /** Each Activity chooses its own scale explicitly, independent of the mode preference. */
    fun wrapContextWithAppDpi(base: Context, compat: Boolean = false): Context {
        val prefs = base.getSharedPreferences(PREFS_UI_SCALE_LEGACY, Context.MODE_PRIVATE)
        val overrideDpi = readAppDpiOverride(prefs, compat)
        // Derive resets from the application resources, never the scaled Activity.
        val targetDpi = if (overrideDpi > 0) overrideDpi else systemDensityDpi(base)
        if (base.resources.configuration.densityDpi == targetDpi) return base
        // Override density only. Copying the full configuration would also pin the
        // old orientation, window size, font scale, locale and night mode.
        val cfg = Configuration().apply { densityDpi = targetDpi }
        return base.createConfigurationContext(cfg)
    }

    fun applyNightMode(mode: NightModePreference) {
        val delegateMode = when (mode) {
            NightModePreference.FollowSystem -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            NightModePreference.Light -> AppCompatDelegate.MODE_NIGHT_NO
            NightModePreference.Dark -> AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(delegateMode)
    }
}
