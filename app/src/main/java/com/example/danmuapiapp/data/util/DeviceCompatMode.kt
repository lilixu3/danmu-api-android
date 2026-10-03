package com.example.danmuapiapp.data.util

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.core.content.edit

enum class InterfaceMode(val key: String, val label: String) {
    Auto("auto", "自动选择"),
    Normal("normal", "普通界面"),
    Compat("compat", "兼容界面");

    companion object {
        fun resolve(raw: String?, legacyForceNormal: Boolean): InterfaceMode =
            entries.firstOrNull { it.key == raw }
                ?: if (legacyForceNormal) Normal else Auto
    }
}

object DeviceCompatMode {
    private const val PREFS_NAME = "device_compat_mode"
    private const val KEY_FORCE_NORMAL_MODE = "force_normal_mode"

    private const val KEY_INTERFACE_MODE = "interface_mode"

    fun shouldUseCompatMode(context: Context): Boolean = when (getInterfaceMode(context)) {
        InterfaceMode.Auto -> isCompatModeDevice(context)
        InterfaceMode.Normal -> false
        InterfaceMode.Compat -> true
    }

    fun getInterfaceMode(context: Context): InterfaceMode {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return InterfaceMode.resolve(
            prefs.getString(KEY_INTERFACE_MODE, null),
            prefs.getBoolean(KEY_FORCE_NORMAL_MODE, false)
        )
    }

    fun setInterfaceMode(context: Context, mode: InterfaceMode) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_INTERFACE_MODE, mode.key)
            putBoolean(KEY_FORCE_NORMAL_MODE, mode == InterfaceMode.Normal)
        }
    }

    fun isNormalModeForced(context: Context): Boolean = getInterfaceMode(context) == InterfaceMode.Normal

    fun setNormalModeForced(context: Context, forced: Boolean) =
        setInterfaceMode(context, if (forced) InterfaceMode.Normal else InterfaceMode.Auto)

    /** Hardware policy must not change when the user switches the UI. */
    fun isCompatModeDevice(context: Context): Boolean {
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
