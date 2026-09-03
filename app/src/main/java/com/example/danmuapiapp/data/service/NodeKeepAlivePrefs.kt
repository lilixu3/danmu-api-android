package com.example.danmuapiapp.data.service

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.AtomicFile
import android.view.accessibility.AccessibilityManager
import androidx.core.content.edit
import com.example.danmuapiapp.domain.model.KeepAliveHeartbeatMode
import com.example.danmuapiapp.domain.model.RunMode
import java.io.File

/**
 * 普通模式保活偏好、通知权限与 TV 唤醒锁策略工具。
 */
object NodeKeepAlivePrefs {

    private const val PREFS_SETTINGS = "danmu_keep_alive_prefs"
    private const val KEY_KEEP_ALIVE_ENABLED = "keep_alive_enabled"
    private const val KEY_DESIRED_RUNNING = "desired_running"
    private const val DESIRED_RUNNING_MIRROR_FILE = "node_desired_running"
    private const val KEY_HEARTBEAT_ENABLED = "heartbeat_enabled"
    private const val KEY_HEARTBEAT_MODE = "heartbeat_mode"
    private const val KEY_HEARTBEAT_INTERVAL_MINUTES = "heartbeat_interval_minutes"

    const val HEARTBEAT_INTERVAL_MIN_MINUTES = 1
    const val HEARTBEAT_INTERVAL_MAX_MINUTES = 24 * 60
    const val HEARTBEAT_INTERVAL_DEFAULT_MINUTES = 30
    const val HEARTBEAT_INTERVAL_SYSTEM_MIN_MINUTES = 10

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)

    fun isKeepAliveEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_KEEP_ALIVE_ENABLED, false)
    }

    fun setKeepAliveEnabled(context: Context, enabled: Boolean) {
        // This preference controls a separate service and scheduler, so make the
        // change synchronously visible before the caller refreshes either one.
        prefs(context).edit(commit = true) {
            putBoolean(KEY_KEEP_ALIVE_ENABLED, enabled)
        }
    }

    fun isDesiredRunning(context: Context): Boolean {
        readDesiredRunningMirror(File(context.noBackupFilesDir, DESIRED_RUNNING_MIRROR_FILE))?.let {
            return it
        }
        return prefs(context).getBoolean(KEY_DESIRED_RUNNING, false)
    }

    fun setDesiredRunning(context: Context, desired: Boolean) {
        writeDesiredRunningMirror(
            file = File(context.noBackupFilesDir, DESIRED_RUNNING_MIRROR_FILE),
            desired = desired
        )
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

    fun shouldEnableA11yKeepAlive(context: Context): Boolean {
        return !isRootMode(context) && isKeepAliveEnabled(context)
    }

    fun shouldAllowA11yRestart(context: Context): Boolean {
        return shouldEnableA11yKeepAlive(context) && isDesiredRunning(context)
    }

    fun isHeartbeatEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_HEARTBEAT_ENABLED, false)
    }

    fun setHeartbeatEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit(commit = true) {
            putBoolean(KEY_HEARTBEAT_ENABLED, enabled)
        }
    }

    fun getHeartbeatMode(context: Context): KeepAliveHeartbeatMode {
        val raw = prefs(context)
            .getString(KEY_HEARTBEAT_MODE, KeepAliveHeartbeatMode.Accessibility.key)
        return KeepAliveHeartbeatMode.fromKey(raw)
    }

    fun setHeartbeatMode(context: Context, mode: KeepAliveHeartbeatMode) {
        prefs(context).edit(commit = true) {
            putString(KEY_HEARTBEAT_MODE, mode.key)
        }
    }

    fun getHeartbeatIntervalMinutes(context: Context): Int {
        val raw = prefs(context)
            .getInt(KEY_HEARTBEAT_INTERVAL_MINUTES, HEARTBEAT_INTERVAL_DEFAULT_MINUTES)
        return normalizeHeartbeatIntervalMinutes(raw)
    }

    fun setHeartbeatIntervalMinutes(context: Context, minutes: Int) {
        prefs(context).edit(commit = true) {
            putInt(KEY_HEARTBEAT_INTERVAL_MINUTES, normalizeHeartbeatIntervalMinutes(minutes))
        }
    }

    fun normalizeHeartbeatIntervalMinutes(minutes: Int): Int {
        return minutes.coerceIn(
            HEARTBEAT_INTERVAL_MIN_MINUTES,
            HEARTBEAT_INTERVAL_MAX_MINUTES
        )
    }

    fun getEffectiveSystemHeartbeatIntervalMinutes(context: Context): Int {
        return getHeartbeatIntervalMinutes(context)
            .coerceAtLeast(HEARTBEAT_INTERVAL_SYSTEM_MIN_MINUTES)
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
            val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
                as? AccessibilityManager
            val expectedPackage = context.packageName
            val expectedClass = KeepAliveAccessibilityService::class.java.name
            val enabledServices = manager
                ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .orEmpty()

            enabledServices.forEach { info ->
                val serviceInfo = info.resolveInfo?.serviceInfo
                if (serviceInfo != null) {
                    val className = normalizeClassName(
                        rawClassName = serviceInfo.name,
                        packageName = serviceInfo.packageName ?: expectedPackage
                    )
                    if (serviceInfo.packageName.equals(expectedPackage, ignoreCase = true) &&
                        className == expectedClass
                    ) {
                        return true
                    }
                }

                val serviceId = info.id
                if (serviceId.equals("$expectedPackage/$expectedClass", ignoreCase = true) ||
                    serviceId.equals(
                        "$expectedPackage/.${KeepAliveAccessibilityService::class.java.simpleName}",
                        ignoreCase = true
                    )
                ) {
                    return true
                }
            }
        }

        val accessibilityEnabled = runCatching {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            )
        }.getOrDefault(0)
        if (accessibilityEnabled != 1) return false

        val expected = ComponentName(context, KeepAliveAccessibilityService::class.java)
        val enabledServices = runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
        }.getOrNull() ?: return false

        return enabledServices.split(':')
            .asSequence()
            .mapNotNull(::parseEnabledComponent)
            .any {
                it.packageName.equals(expected.packageName, ignoreCase = true) &&
                    it.className == expected.className
            }
    }

    private fun parseEnabledComponent(raw: String): ComponentName? {
        val value = raw.trim()
        val slash = value.indexOf('/')
        if (slash <= 0 || slash >= value.length - 1) return null
        val packageName = value.substring(0, slash)
        val className = normalizeClassName(value.substring(slash + 1), packageName)
        return runCatching { ComponentName(packageName, className) }.getOrNull()
    }

    private fun normalizeClassName(rawClassName: String, packageName: String): String {
        val className = rawClassName.trim()
        return if (className.startsWith('.')) packageName + className else className
    }

    internal fun parseDesiredRunningMirror(raw: String?): Boolean? {
        return when (raw?.trim()) {
            "1" -> true
            "0" -> false
            else -> null
        }
    }

    private fun readDesiredRunningMirror(file: File): Boolean? {
        if (!file.isFile) return null
        return runCatching { parseDesiredRunningMirror(file.readText(Charsets.UTF_8)) }
            .getOrNull()
    }

    private fun writeDesiredRunningMirror(file: File, desired: Boolean) {
        runCatching {
            file.parentFile?.mkdirs()
            val atomicFile = AtomicFile(file)
            val stream = atomicFile.startWrite()
            try {
                val value = if (desired) "1" else "0"
                stream.write(value.toByteArray(Charsets.UTF_8))
                atomicFile.finishWrite(stream)
            } catch (error: Throwable) {
                atomicFile.failWrite(stream)
                throw error
            }
        }
    }

}
