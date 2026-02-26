package com.example.danmuapiapp.data.service

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.example.danmuapiapp.domain.model.KeepAliveHeartbeatMode
import com.example.danmuapiapp.domain.model.RunMode

/**
 * 普通模式无障碍保活偏好与状态工具。
 */
object NodeKeepAlivePrefs {

    private const val PREFS_SETTINGS = "danmu_keep_alive_prefs"
    private const val KEY_KEEP_ALIVE_ENABLED = "keep_alive_enabled"
    private const val KEY_DESIRED_RUNNING = "desired_running"
    private const val KEY_HEARTBEAT_ENABLED = "heartbeat_enabled"
    private const val KEY_HEARTBEAT_MODE = "heartbeat_mode"
    private const val KEY_HEARTBEAT_INTERVAL_MINUTES = "heartbeat_interval_minutes"

    const val HEARTBEAT_INTERVAL_MIN_MINUTES = 1
    const val HEARTBEAT_INTERVAL_MAX_MINUTES = 24 * 60
    const val HEARTBEAT_INTERVAL_DEFAULT_MINUTES = 30
    const val HEARTBEAT_INTERVAL_SYSTEM_MIN_MINUTES = 15

    fun isKeepAliveEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            .getBoolean(KEY_KEEP_ALIVE_ENABLED, false)
    }

    fun setKeepAliveEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_KEEP_ALIVE_ENABLED, enabled)
            .apply()
    }

    fun isDesiredRunning(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DESIRED_RUNNING, false)
    }

    fun setDesiredRunning(context: Context, desired: Boolean) {
        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DESIRED_RUNNING, desired)
            .apply()
    }

    fun isRootMode(context: Context): Boolean {
        return RuntimeModePrefs.get(context) != RunMode.Normal
    }

    fun shouldEnableA11yKeepAlive(context: Context): Boolean {
        return !isRootMode(context) && isKeepAliveEnabled(context)
    }

    fun shouldAllowA11yRestart(context: Context): Boolean {
        return shouldEnableA11yKeepAlive(context) && isDesiredRunning(context)
    }

    fun isHeartbeatEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            .getBoolean(KEY_HEARTBEAT_ENABLED, false)
    }

    fun setHeartbeatEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HEARTBEAT_ENABLED, enabled)
            .apply()
    }

    fun getHeartbeatMode(context: Context): KeepAliveHeartbeatMode {
        val raw = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            .getString(KEY_HEARTBEAT_MODE, KeepAliveHeartbeatMode.Accessibility.key)
        return KeepAliveHeartbeatMode.fromKey(raw)
    }

    fun setHeartbeatMode(context: Context, mode: KeepAliveHeartbeatMode) {
        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HEARTBEAT_MODE, mode.key)
            .apply()
    }

    fun getHeartbeatIntervalMinutes(context: Context): Int {
        val raw = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            .getInt(KEY_HEARTBEAT_INTERVAL_MINUTES, HEARTBEAT_INTERVAL_DEFAULT_MINUTES)
        return normalizeHeartbeatIntervalMinutes(raw)
    }

    fun setHeartbeatIntervalMinutes(context: Context, minutes: Int) {
        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_HEARTBEAT_INTERVAL_MINUTES, normalizeHeartbeatIntervalMinutes(minutes))
            .apply()
    }

    fun normalizeHeartbeatIntervalMinutes(minutes: Int): Int {
        return minutes.coerceIn(
            HEARTBEAT_INTERVAL_MIN_MINUTES,
            HEARTBEAT_INTERVAL_MAX_MINUTES
        )
    }

    fun getEffectiveSystemHeartbeatIntervalMinutes(context: Context): Int {
        return getHeartbeatIntervalMinutes(context).coerceAtLeast(HEARTBEAT_INTERVAL_SYSTEM_MIN_MINUTES)
    }

    fun shouldRunA11yHeartbeat(context: Context): Boolean {
        return shouldAllowA11yRestart(context) &&
            isHeartbeatEnabled(context) &&
            getHeartbeatMode(context) == KeepAliveHeartbeatMode.Accessibility
    }

    fun shouldScheduleSystemHeartbeat(context: Context): Boolean {
        return !isRootMode(context) &&
            isKeepAliveEnabled(context) &&
            isDesiredRunning(context) &&
            hasPostNotificationsPermission(context) &&
            isHeartbeatEnabled(context) &&
            getHeartbeatMode(context) == KeepAliveHeartbeatMode.System
    }

    fun hasPostNotificationsPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun disableSelfAction(packageName: String): String {
        return "$packageName.action.DISABLE_A11Y_KEEPALIVE"
    }

    fun requestDisableAccessibilityService(context: Context) {
        runCatching {
            context.sendBroadcast(
                Intent(disableSelfAction(context.packageName)).setPackage(context.packageName)
            )
        }
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        runCatching {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val expectedPkg = context.packageName
            val expectedCls = KeepAliveAccessibilityService::class.java.name
            val enabled = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .orEmpty()
            enabled.forEach { info ->
                val si = info.resolveInfo?.serviceInfo
                if (si != null) {
                    val cls = normalizeClassName(si.name, si.packageName ?: expectedPkg)
                    if (si.packageName.equals(expectedPkg, ignoreCase = true) && cls == expectedCls) {
                        return true
                    }
                }
                val id = info.id
                if (id.equals("$expectedPkg/$expectedCls", ignoreCase = true) ||
                    id.equals("$expectedPkg/.${KeepAliveAccessibilityService::class.java.simpleName}", ignoreCase = true)
                ) {
                    return true
                }
            }
        }

        val enabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        )
        if (enabled != 1) return false

        val expected = ComponentName(context, KeepAliveAccessibilityService::class.java)
        val services = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return services.split(':')
            .asSequence()
            .mapNotNull { parseEnabledComponent(it) }
            .any { it.packageName == expected.packageName && it.className == expected.className }
    }

    private fun parseEnabledComponent(raw: String): ComponentName? {
        val s = raw.trim()
        val slash = s.indexOf('/')
        if (slash <= 0 || slash >= s.length - 1) return null
        val pkg = s.substring(0, slash)
        val cls = normalizeClassName(s.substring(slash + 1), pkg)
        return runCatching { ComponentName(pkg, cls) }.getOrNull()
    }

    private fun normalizeClassName(rawClassName: String, pkg: String): String {
        val cls = rawClassName.trim()
        return if (cls.startsWith(".")) pkg + cls else cls
    }
}
