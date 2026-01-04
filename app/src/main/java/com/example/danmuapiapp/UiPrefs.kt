package com.example.danmuapiapp

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Very small UI preference helper (kept here so the UI zip stays self-contained).
 */
object UiPrefs {
    private const val PREFS_NAME = "danmu_ui_prefs"
    private const val KEY_DARK_THEME = "dark_theme"
    private const val KEY_HIDE_FROM_RECENTS = "hide_from_recents"

    fun isDarkTheme(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Default to light theme ("white theme"), as requested.
        return sp.getBoolean(KEY_DARK_THEME, false)
    }

    fun applyTheme(context: Context) {
        val dark = isDarkTheme(context)
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    fun setDarkTheme(context: Context, dark: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK_THEME, dark)
            .apply()
        applyTheme(context)
    }

    fun isHideFromRecents(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_HIDE_FROM_RECENTS, false)
    }

    fun setHideFromRecents(context: Context, hide: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HIDE_FROM_RECENTS, hide)
            .apply()
    }
}
