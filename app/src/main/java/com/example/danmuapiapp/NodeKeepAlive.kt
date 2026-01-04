package com.example.danmuapiapp

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo

/**
 * Keep-alive related preferences + helpers.
 *
 * 约定：
 * - keepAliveEnabled：用户在“设置-无障碍保活”里显式开启/关闭
 * - desiredRunning：只在用户“启动服务”后置为 true；用户手动停止/退出则置为 false
 *
 * 自动重启的判断条件（必须同时满足）：
 * 1) keepAliveEnabled == true
 * 2) desiredRunning == true
 * 3) 无障碍服务已在系统设置中启用
 */
object NodeKeepAlive {

    private const val PREFS_NAME = "danmu_keep_alive_prefs"
    private const val KEY_KEEP_ALIVE_ENABLED = "keep_alive_enabled"
    private const val KEY_DESIRED_RUNNING = "desired_running"

    // Backoff state (persisted, so process restart won't spam).
    private const val KEY_RESTART_ATTEMPT = "restart_attempt"
    private const val KEY_LAST_ATTEMPT_TS = "last_attempt_ts"

    /**
     * Expose restart attempt timestamps to the watchdog so it can schedule checks efficiently
     * (instead of waking up frequently and checking the backoff every few seconds).
     */
    fun getLastRestartAttemptTs(context: Context): Long =
        prefs(context).getLong(KEY_LAST_ATTEMPT_TS, 0L)

    /**
     * How long until we are allowed to attempt the next restart (based on the current backoff).
     */
    fun msUntilNextRestartAttempt(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ): Long {
        val delay = getBackoffMs(context)
        val last = getLastRestartAttemptTs(context)
        val nextAt = last + delay
        return (nextAt - nowMs).coerceAtLeast(0L)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isKeepAliveEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_KEEP_ALIVE_ENABLED, false)

    fun setKeepAliveEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_KEEP_ALIVE_ENABLED, enabled).apply()
        if (!enabled) resetBackoff(context)
    }

    fun isDesiredRunning(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DESIRED_RUNNING, false)

    /**
     * desired=false 表示“用户手动停止”，此时不应自动拉起。
     */
    fun setDesiredRunning(context: Context, desired: Boolean) {
        prefs(context).edit().putBoolean(KEY_DESIRED_RUNNING, desired).apply()
        if (!desired) resetBackoff(context)
    }

    fun resetBackoff(context: Context) {
        prefs(context).edit()
            .putInt(KEY_RESTART_ATTEMPT, 0)
            .putLong(KEY_LAST_ATTEMPT_TS, 0L)
            .apply()
    }

    /**
     * Exponential backoff: 2s, 4s, 8s, 16s... up to 60s.
     */
    fun getBackoffMs(context: Context): Long {
        val attempt = prefs(context).getInt(KEY_RESTART_ATTEMPT, 0).coerceAtLeast(0)
        if (attempt <= 0) return 0L
        val base = 2000L
        val exp = 1L shl (attempt.coerceAtMost(6) - 1) // 1,2,4,8,16,32,64
        val delay = base * exp
        return delay.coerceAtMost(60_000L)
    }

    fun shouldAttemptRestartNow(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val delay = getBackoffMs(context)
        val last = prefs(context).getLong(KEY_LAST_ATTEMPT_TS, 0L)
        return (nowMs - last) >= delay
    }

    fun markRestartAttempt(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val p = prefs(context)
        val nextAttempt = (p.getInt(KEY_RESTART_ATTEMPT, 0) + 1).coerceAtMost(10)
        p.edit()
            .putInt(KEY_RESTART_ATTEMPT, nextAttempt)
            .putLong(KEY_LAST_ATTEMPT_TS, nowMs)
            .apply()
    }

    fun onNodeRunning(context: Context) {
        // Success -> reset backoff
        resetBackoff(context)
    }

    /**
     * Android 13+ requires runtime POST_NOTIFICATIONS permission to reliably show foreground notif.
     * If user denies it, auto-restart from background is likely to fail (or be invisible),
     * so we conservatively block automatic start.
     */
    fun hasPostNotificationsPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Whether our accessibility keep-alive service is enabled in system Accessibility settings.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        // Primary: query AccessibilityManager for enabled services.
        // NOTE: Some OEM / Android versions report AccessibilityServiceInfo.id in different
        // formats (full class name vs "/.ShortName"). So we compare in a normalized way.
        runCatching {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val expectedPkg = context.packageName
            val expectedCls = KeepAliveAccessibilityService::class.java.name
            val expectedIdFull = "$expectedPkg/$expectedCls"
            val expectedIdShort = "$expectedPkg/.${KeepAliveAccessibilityService::class.java.simpleName}"

            val list = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            if (list != null) {
                for (info in list) {
                    // Best-effort: compare by resolveInfo.serviceInfo (most reliable).
                    val si = info.resolveInfo?.serviceInfo
                    if (si != null) {
                        val cls = normalizeClassName(si.name, si.packageName ?: expectedPkg)
                        if (si.packageName.equals(expectedPkg, ignoreCase = true) && cls == expectedCls) {
                            return true
                        }
                    }

                    // Fallback: compare id strings (handle both full & short formats).
                    val id = info.id
                    if (id.equals(expectedIdFull, ignoreCase = true) || id.equals(expectedIdShort, ignoreCase = true)) {
                        return true
                    }
                }
            }
        }

        // Fallback: read Settings.Secure (colon-separated component list).
        val enabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        )
        if (enabled != 1) return false

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val expected = ComponentName(context, KeepAliveAccessibilityService::class.java)
        return enabledServices
            .split(':')
            .asSequence()
            .mapNotNull { parseEnabledA11yComponent(it) }
            .any { it.packageName == expected.packageName && it.className == expected.className }
    }

    private fun parseEnabledA11yComponent(raw: String): ComponentName? {
        val s = raw.trim()
        val slash = s.indexOf('/')
        if (slash <= 0 || slash >= s.length - 1) return null

        val pkg = s.substring(0, slash)
        val clsRaw = s.substring(slash + 1)
        val cls = normalizeClassName(clsRaw, pkg)
        return runCatching { ComponentName(pkg, cls) }.getOrNull()
    }

    private fun normalizeClassName(rawClassName: String, pkg: String): String {
        val cls = rawClassName.trim()
        return if (cls.startsWith(".")) pkg + cls else cls
    }

    /**
     * Ask the AccessibilityService to disable itself, so the system settings UI
     * stays in sync when user关闭“无障碍保活”。
     */
    fun requestDisableAccessibilityService(context: Context) {
        runCatching {
            val it = android.content.Intent(KeepAliveAccessibilityService.ACTION_DISABLE_SELF)
                .setPackage(context.packageName)
            context.sendBroadcast(it)
        }
    }
}
