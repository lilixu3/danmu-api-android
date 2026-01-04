package com.example.danmuapiapp

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * A minimal accessibility service used as a "keep-alive" watchdog.
 *
 * Important:
 * - This service does NOT read UI content (canRetrieveWindowContent=false).
 * - It only checks whether NodeService should be running and restarts it when needed.
 */
class KeepAliveAccessibilityService : AccessibilityService() {

    companion object {
        /**
         * App 内关闭“无障碍保活”后，让系统无障碍列表里的本服务也同步关闭。
         *
         * 说明：Activity 无法直接关闭系统无障碍开关，但 AccessibilityService 本身可以
         * 调用 disableSelf() 将自己从系统设置中关闭。
         */
        const val ACTION_DISABLE_SELF = "com.example.danmuapiapp.action.DISABLE_A11Y_KEEPALIVE"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastPermToastMs: Long = 0L

    @Volatile
    private var disabledByApp: Boolean = false

    // Throttle event-driven checks (window changes can be frequent).
    private var lastEventTickUptimeMs: Long = 0L

    private val powerManager: PowerManager? by lazy {
        runCatching { getSystemService(PowerManager::class.java) }.getOrNull()
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != NodeService.ACTION_NODE_STATUS) return
            val status = intent.getStringExtra(NodeService.EXTRA_STATUS) ?: return

            // If Node stopped/crashed, attempt a restart immediately (subject to backoff).
            if (status == NodeService.STATUS_STOPPED || status == NodeService.STATUS_ERROR) {
                handler.post { runCatching { tickOnce() } }
            }

            // Any status change -> recompute the next schedule.
            scheduleNextCheck()
        }
    }

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_DISABLE_SELF) {
                // User explicitly turned off keep-alive inside the App.
                disableSelfAndCleanup()
            }
        }
    }

    private val periodic = object : Runnable {
        override fun run() {
            runCatching { tickOnce() }
            scheduleNextCheck()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        // If user did NOT enable keep-alive in App settings, immediately disable this
        // accessibility service to avoid unnecessary wakeups and keep the state consistent.
        if (!NodeKeepAlive.isKeepAliveEnabled(this)) {
            disableSelfAndCleanup()
            return
        }

        registerStatusReceiverSafe()
        registerControlReceiverSafe()
        handler.removeCallbacks(periodic)

        // Try once shortly after connect (in case desiredRunning=true).
        handler.postDelayed({ runCatching { tickOnce() } }, 600L)
        scheduleNextCheck()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // When keep-alive is OFF or user manually stopped the service, do nothing.
        if (!NodeKeepAlive.isKeepAliveEnabled(this) || !NodeKeepAlive.isDesiredRunning(this)) {
            // If the preference is OFF, make sure the system Accessibility toggle is also OFF.
            if (!NodeKeepAlive.isKeepAliveEnabled(this)) {
                disableSelfAndCleanup()
            }
            return
        }

        // Window state changes happen when the user is actively using the phone.
        // Use it as an opportunity to (lightly) verify the foreground service state,
        // instead of waking up on a tight timer.
        val now = SystemClock.uptimeMillis()
        if (now - lastEventTickUptimeMs < 15_000L) return
        lastEventTickUptimeMs = now
        handler.post { runCatching { tickOnce() } }
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onDestroy() {
        handler.removeCallbacks(periodic)
        unregisterStatusReceiverSafe()
        unregisterControlReceiverSafe()
        super.onDestroy()
    }

    private fun scheduleNextCheck() {
        handler.removeCallbacks(periodic)

        // Keep-alive is OFF -> no need to schedule anything.
        if (!NodeKeepAlive.isKeepAliveEnabled(this)) {
            disableSelfAndCleanup()
            return
        }

        // User manually stopped -> stay idle (no periodic wakeups).
        if (!NodeKeepAlive.isDesiredRunning(this)) {
            return
        }
        handler.postDelayed(periodic, computeNextDelayMs())
    }

    private fun computeNextDelayMs(): Long {
        // If user didn't enable keep-alive OR user manually stopped: don't waste battery.
        if (!NodeKeepAlive.isKeepAliveEnabled(this) || !NodeKeepAlive.isDesiredRunning(this)) {
            return 5 * 60_000L // 5 minutes
        }

        // When Node is running: check infrequently.
        if (NodeService.isRunning()) {
            val interactive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                powerManager?.isInteractive ?: true
            } else {
                @Suppress("DEPRECATION")
                powerManager?.isScreenOn ?: true
            }
            return if (interactive) 2 * 60_000L else 4 * 60_000L
        }

        // Not running: follow backoff, but still avoid tight polling.
        val until = NodeKeepAlive.msUntilNextRestartAttempt(this)
        return until.coerceIn(10_000L, 60_000L)
    }

    private fun tickOnce() {
        // User didn't enable keep-alive -> disable self (sync system toggle + save battery).
        if (!NodeKeepAlive.isKeepAliveEnabled(this)) {
            disableSelfAndCleanup()
            return
        }

        // User manually stopped -> do nothing.
        if (!NodeKeepAlive.isDesiredRunning(this)) return

        // Android 13+ without POST_NOTIFICATIONS: foreground service restart is unreliable.
        if (!NodeKeepAlive.hasPostNotificationsPermission(this)) {
            if (Build.VERSION.SDK_INT >= 33) {
                val now = System.currentTimeMillis()
                if (now - lastPermToastMs > 30_000L) {
                    lastPermToastMs = now
                    Toast.makeText(
                        this,
                        "无障碍保活：需要通知权限，才能自动重启前台服务",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            return
        }

        // If already running, reset backoff and exit.
        if (NodeService.isRunning()) {
            NodeKeepAlive.onNodeRunning(this)
            return
        }

        // If we are not allowed to restart yet (backoff), wait.
        if (!NodeKeepAlive.shouldAttemptRestartNow(this)) return

        NodeKeepAlive.markRestartAttempt(this)

        // Restart Node foreground service.
        val it = Intent(this, NodeService::class.java).setAction(NodeService.ACTION_START)
        try {
            // Always use startForegroundService on 26+.
            ContextCompat.startForegroundService(this, it)
        } catch (_: Throwable) {
            // Swallow; next tick will retry with backoff.
        }
    }

    private fun registerStatusReceiverSafe() {
        val filter = IntentFilter(NodeService.ACTION_NODE_STATUS)
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(statusReceiver, filter)
            }
        } catch (_: Throwable) {
        }
    }

    private fun unregisterStatusReceiverSafe() {
        try {
            unregisterReceiver(statusReceiver)
        } catch (_: Throwable) {
        }
    }

    private fun registerControlReceiverSafe() {
        val filter = IntentFilter(ACTION_DISABLE_SELF)
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(controlReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(controlReceiver, filter)
            }
        } catch (_: Throwable) {
        }
    }

    private fun unregisterControlReceiverSafe() {
        try {
            unregisterReceiver(controlReceiver)
        } catch (_: Throwable) {
        }
    }

    private fun disableSelfAndCleanup() {
        if (disabledByApp) return
        disabledByApp = true

        handler.removeCallbacks(periodic)
        unregisterStatusReceiverSafe()
        unregisterControlReceiverSafe()

        // Disable this service in system Accessibility settings.
        runCatching { disableSelf() }
        // Also stop ourselves (disableSelf() will typically stop the service too, but this is safe).
        runCatching { stopSelf() }
    }
}
