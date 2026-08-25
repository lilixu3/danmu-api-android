package com.example.danmuapiapp.data.util

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
    const val PREF_KEY_BACKGROUND_MODE = "background_mode"
    const val PREF_KEY_BACKGROUND_LOCAL_URI = "background_local_uri"
    const val PREF_KEY_BACKGROUND_ONLINE_URL = "background_online_url"
    const val PREF_KEY_BACKGROUND_RANDOM_URL = "background_random_url"
    const val PREF_KEY_BACKGROUND_RANDOM_REFRESH_POLICY = "background_random_refresh_policy"
    const val PREF_KEY_BACKGROUND_CUSTOM_REFRESH_SECONDS = "background_custom_refresh_seconds"
    const val PREF_KEY_BACKGROUND_RANDOM_URL_MIGRATED = "background_random_url_migrated"
    const val PREF_KEY_DARK_THEME_LEGACY = "dark_theme"
    const val PREF_KEY_HIDE_FROM_RECENTS = "hide_from_recents"
    const val PREF_KEY_APP_DPI_OVERRIDE = "app_dpi_override"

    // 仅影响应用内显示：小于等于 0 表示跟随系统。
    const val APP_DPI_SYSTEM = -1
    const val APP_DPI_MIN = 120
    const val APP_DPI_MAX = 960

    private val appearanceJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
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

    fun readAppDpiOverride(prefs: SharedPreferences): Int {
        val raw = prefs.safeGetInt(PREF_KEY_APP_DPI_OVERRIDE, APP_DPI_SYSTEM)
        return normalizeAppDpiOverride(raw)
    }

    fun writeAppDpiOverride(prefs: SharedPreferences, dpi: Int) {
        prefs.edit {
            putInt(PREF_KEY_APP_DPI_OVERRIDE, normalizeAppDpiOverride(dpi))
        }
    }

    fun normalizeAppDpiOverride(dpi: Int): Int {
        if (dpi <= 0) return APP_DPI_SYSTEM
        return dpi.coerceIn(APP_DPI_MIN, APP_DPI_MAX)
    }

    fun wrapContextWithAppDpi(base: Context, includeCompatMode: Boolean = false): Context {
        if (!includeCompatMode && DeviceCompatMode.shouldUseCompatMode(base)) return base
        val prefs = base.getSharedPreferences(PREFS_UI_SCALE_LEGACY, Context.MODE_PRIVATE)
        val overrideDpi = readAppDpiOverride(prefs)
        if (overrideDpi == APP_DPI_SYSTEM) return base
        val cfg = Configuration(base.resources.configuration)
        if (cfg.densityDpi == overrideDpi) return base
        cfg.densityDpi = overrideDpi
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
