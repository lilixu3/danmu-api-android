package com.example.danmuapiapp.data.service

import android.content.Context

/**
 * Root 模式开机自启偏好（模块方案）。
 */
object RootAutoStartPrefs {

    private const val PREFS_NAME = "danmu_root_autostart"
    private const val KEY_BOOT_AUTOSTART_ENABLED = "boot_autostart_enabled"

    fun isBootAutoStartEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_BOOT_AUTOSTART_ENABLED, false)
    }

    fun setBootAutoStartEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BOOT_AUTOSTART_ENABLED, enabled)
            .apply()
    }
}
