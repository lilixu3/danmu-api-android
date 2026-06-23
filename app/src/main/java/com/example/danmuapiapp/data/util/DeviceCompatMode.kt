package com.example.danmuapiapp.data.util

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.core.content.edit

object DeviceCompatMode {
    private const val PREFS_NAME = "device_compat_mode"
    private const val KEY_FORCE_NORMAL_MODE = "force_normal_mode"

    fun shouldUseCompatMode(context: Context): Boolean {
        if (isNormalModeForced(context)) return false
        return isCompatModeDevice(context)
    }

    fun isNormalModeForced(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_FORCE_NORMAL_MODE, false)
    }

    fun setNormalModeForced(context: Context, forced: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putBoolean(KEY_FORCE_NORMAL_MODE, forced)
            }
    }

    private fun isCompatModeDevice(context: Context): Boolean {
        val appContext = context.applicationContext
        val configuration = appContext.resources.configuration
        val packageManager = appContext.packageManager
        val isTelevision = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION
        val hasLeanback = packageManager.hasSystemFeature("android.software.leanback") ||
            packageManager.hasSystemFeature("android.software.leanback_only")
        val hasTouchscreen = packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        return isTelevision || hasLeanback || !hasTouchscreen
    }
}
