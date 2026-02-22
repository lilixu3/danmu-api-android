package com.example.danmuapiapp.data.util

import android.content.Context
import android.content.res.Configuration
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import com.example.danmuapiapp.domain.model.NightModePreference

object AppAppearancePrefs {
    const val PREFS_UI_LEGACY = "danmu_ui_prefs"
    const val PREFS_UI_SCALE_LEGACY = "danmu_ui_scale_prefs"

    const val PREF_KEY_NIGHT_MODE = "night_mode_pref"
    const val PREF_KEY_DARK_THEME_LEGACY = "dark_theme"
    const val PREF_KEY_HIDE_FROM_RECENTS = "hide_from_recents"
    const val PREF_KEY_APP_DPI_OVERRIDE = "app_dpi_override"

    // 仅影响应用内显示：小于等于 0 表示跟随系统。
    const val APP_DPI_SYSTEM = -1
    const val APP_DPI_MIN = 120
    const val APP_DPI_MAX = 960

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
        prefs.edit()
            .putInt(PREF_KEY_NIGHT_MODE, mode.storageValue)
            .putBoolean(PREF_KEY_DARK_THEME_LEGACY, legacyDark)
            .apply()
    }

    fun readHideFromRecents(prefs: SharedPreferences): Boolean {
        return prefs.safeGetBoolean(PREF_KEY_HIDE_FROM_RECENTS, false)
    }

    fun writeHideFromRecents(prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit().putBoolean(PREF_KEY_HIDE_FROM_RECENTS, enabled).apply()
    }

    fun readAppDpiOverride(prefs: SharedPreferences): Int {
        val raw = prefs.safeGetInt(PREF_KEY_APP_DPI_OVERRIDE, APP_DPI_SYSTEM)
        return normalizeAppDpiOverride(raw)
    }

    fun writeAppDpiOverride(prefs: SharedPreferences, dpi: Int) {
        prefs.edit()
            .putInt(PREF_KEY_APP_DPI_OVERRIDE, normalizeAppDpiOverride(dpi))
            .apply()
    }

    fun normalizeAppDpiOverride(dpi: Int): Int {
        if (dpi <= 0) return APP_DPI_SYSTEM
        return dpi.coerceIn(APP_DPI_MIN, APP_DPI_MAX)
    }

    fun wrapContextWithAppDpi(base: Context): Context {
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
