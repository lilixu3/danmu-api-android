package com.example.danmuapiapp.data.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.edit
import com.example.danmuapiapp.domain.model.RunMode
import java.io.File

/**
 * 统一管理“更新后运行时预热”状态与执行。
 */
object RuntimeWarmupManager {

    private const val PREFS_NAME = "runtime_warmup"
    private const val KEY_PENDING_VERSION = "pending_version"
    private const val KEY_LAST_COMPLETED_VERSION = "last_completed_version"
    private const val KEY_LAST_COMPLETED_AT = "last_completed_at"
    private const val KEY_LAST_DURATION_MS = "last_duration_ms"
    private const val KEY_LAST_ERROR = "last_error"
    private const val KEY_LAST_SEEN_VERSION = "last_seen_version"

    enum class Step {
        Checking,
        Extracting,
        Syncing
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 兜底版本识别：即使系统升级广播未触发，也能在首次启动发现新版本并标记预热。
     */
    fun ensurePendingFlagForCurrentVersion(context: Context) {
        val version = currentAppVersion(context)
        val sp = prefs(context)
        val seen = sp.getString(KEY_LAST_SEEN_VERSION, null)
        if (seen == version) return

        val completed = sp.getString(KEY_LAST_COMPLETED_VERSION, null)
        sp.edit {
            putString(KEY_LAST_SEEN_VERSION, version)
            if (completed != version) {
                putString(KEY_PENDING_VERSION, version)
            }
        }
    }

    fun markUpdatePending(context: Context) {
        val version = currentAppVersion(context)
        prefs(context).edit {
            putString(KEY_LAST_SEEN_VERSION, version)
            putString(KEY_PENDING_VERSION, version)
        }
    }

    fun isUpdatePending(context: Context): Boolean {
        val version = currentAppVersion(context)
        val sp = prefs(context)
        val pending = sp.getString(KEY_PENDING_VERSION, null)
        val done = sp.getString(KEY_LAST_COMPLETED_VERSION, null)
        return pending == version && done != version
    }

    fun needsForegroundWarmup(context: Context): Boolean {
        ensurePendingFlagForCurrentVersion(context)
        if (RuntimeModePrefs.get(context) != RunMode.Normal) return false
        if (!isUpdatePending(context)) return false
        if (isProjectReadyForCurrentVersion(context)) {
            markWarmupCompleted(context, 0L)
            return false
        }
        return true
    }

    fun shouldAttemptReceiverWarmup(context: Context): Boolean {
        if (!isUpdatePending(context)) return false
        if (RuntimeModePrefs.get(context) != RunMode.Normal) return false

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm?.isPowerSaveMode == true) return false

        if (isCharging(context)) return true

        val level = batteryLevelPercent(context)
        return level == null || level >= 45
    }

    fun runWarmup(
        context: Context,
        onStep: ((Step) -> Unit)? = null
    ): Result<Long> {
        val appContext = context.applicationContext
        val startedAt = SystemClock.elapsedRealtime()
        return runCatching {
            onStep?.invoke(Step.Checking)
            if (isProjectReadyForCurrentVersion(appContext)) {
                markWarmupCompleted(appContext, 0L)
                return@runCatching 0L
            }

            onStep?.invoke(Step.Extracting)
            val projectDir = NodeProjectManager.ensureProjectExtracted(
                appContext,
                RuntimePaths.normalProjectDir(appContext)
            )

            onStep?.invoke(Step.Syncing)
            NodeProjectManager.writeRuntimeEnv(
                context = appContext,
                targetProjectDir = projectDir
            )

            val duration = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
            markWarmupCompleted(appContext, duration)
            duration
        }.onFailure { throwable ->
            val reason = throwable.message ?: throwable.javaClass.simpleName
            recordWarmupError(appContext, reason)
        }
    }

    fun markWarmupCompleted(context: Context, durationMs: Long) {
        val version = currentAppVersion(context)
        prefs(context).edit {
            putString(KEY_LAST_SEEN_VERSION, version)
            putString(KEY_LAST_COMPLETED_VERSION, version)
            remove(KEY_PENDING_VERSION)
            putLong(KEY_LAST_COMPLETED_AT, System.currentTimeMillis())
            putLong(KEY_LAST_DURATION_MS, durationMs.coerceAtLeast(0L))
            remove(KEY_LAST_ERROR)
        }
    }

    fun skipPendingForCurrentVersion(context: Context, reason: String) {
        recordWarmupError(context, reason)
        val version = currentAppVersion(context)
        val sp = prefs(context)
        if (sp.getString(KEY_PENDING_VERSION, null) == version) {
            sp.edit {
                remove(KEY_PENDING_VERSION)
            }
        }
    }

    private fun recordWarmupError(context: Context, reason: String) {
        prefs(context).edit {
            putString(KEY_LAST_ERROR, reason)
        }
    }

    private fun isProjectReadyForCurrentVersion(context: Context): Boolean {
        val targetDir = RuntimePaths.normalProjectDir(context)
        if (!targetDir.exists() || !targetDir.isDirectory) return false

        val entryFile = File(targetDir, "main.js")
        if (!entryFile.exists()) return false

        val versionFile = File(targetDir, ".app_version")
        if (!versionFile.exists()) return false

        val currentVersion = currentAppVersion(context)
        val existingVersion = runCatching {
            versionFile.readText().trim()
        }.getOrDefault("")

        return existingVersion == currentVersion
    }

    private fun currentAppVersion(context: Context): String {
        return runCatching {
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkg.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkg.versionCode.toLong()
            }
            "$code-${pkg.lastUpdateTime}"
        }.getOrElse { "0" }
    }

    private fun batteryLevelPercent(context: Context): Int? {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
        val value = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return value.takeIf { it in 1..100 }
    }

    private fun isCharging(context: Context): Boolean {
        val intent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull() ?: return false
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }
}
