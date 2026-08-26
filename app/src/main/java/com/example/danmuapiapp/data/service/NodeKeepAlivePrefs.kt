package com.example.danmuapiapp.data.service

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.edit
import com.example.danmuapiapp.domain.model.RunMode

/**
 * 普通模式运行期望、通知权限与 TV 唤醒锁策略工具。
 */
object NodeKeepAlivePrefs {

    private const val PREFS_SETTINGS = "danmu_keep_alive_prefs"
    private const val KEY_DESIRED_RUNNING = "desired_running"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)

    fun isDesiredRunning(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_DESIRED_RUNNING, false)
    }

    fun setDesiredRunning(context: Context, desired: Boolean) {
        setDesiredRunning(prefs(context), desired)
    }

    internal fun setDesiredRunning(sharedPrefs: SharedPreferences, desired: Boolean) {
        sharedPrefs.edit(commit = true) {
            putBoolean(KEY_DESIRED_RUNNING, desired)
        }
    }

    fun isRootMode(context: Context): Boolean {
        return RuntimeModePrefs.get(context) != RunMode.Normal
    }

    fun shouldHoldRuntimeWakeLock(
        isCompatModeDevice: Boolean,
        isRootMode: Boolean,
        serviceRunning: Boolean
    ): Boolean {
        return isCompatModeDevice &&
            !isRootMode &&
            serviceRunning
    }

    fun hasPostNotificationsPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

}
