package com.example.danmuapiapp.data.util

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

object DeviceCompatMode {
    fun shouldUseCompatMode(context: Context): Boolean {
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
