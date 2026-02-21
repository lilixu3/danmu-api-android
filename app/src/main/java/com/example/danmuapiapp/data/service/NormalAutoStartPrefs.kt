package com.example.danmuapiapp.data.service

import android.content.Context

/**
 * 普通模式开机自启偏好。
 */
object NormalAutoStartPrefs {

    private const val PREFS = "normal_autostart"
    private const val KEY_BOOT = "boot_autostart"

    fun isBootAutoStartEnabled(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (sp.contains(KEY_BOOT)) {
            return sp.getBoolean(KEY_BOOT, false)
        }
        // 兼容旧键
        val legacy = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return legacy.getBoolean("auto_start", false)
    }

    fun setBootAutoStartEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BOOT, enabled)
            .apply()
        // 同步旧键，兼容历史逻辑
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("auto_start", enabled)
            .apply()
    }
}
