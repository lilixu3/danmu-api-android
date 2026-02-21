package com.example.danmuapiapp.data.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 无障碍保活服务：普通模式下在异常退出时自动拉起前台服务。
 */
class KeepAliveAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastEventTickUptimeMs = 0L
    private var lastPermToastMs = 0L
    private var isEventListeningEnabled = true
    private val activeEventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != NodeService.ACTION_STATUS) return
            val status = intent.getStringExtra(NodeService.EXTRA_STATUS).orEmpty()
            if (status == NodeService.STATUS_STOPPED || status == NodeService.STATUS_ERROR) {
                handler.post { runCatching { tickOnce() } }
            } else {
                handler.post { runCatching { refreshA11yEventListeningMode() } }
            }
        }
    }

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == NodeKeepAlivePrefs.disableSelfAction(packageName)) {
                disableSelfAndCleanup()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (!NodeKeepAlivePrefs.shouldEnableA11yKeepAlive(this)) {
            disableSelfAndCleanup()
            return
        }

        registerStatusReceiverSafe()
        registerControlReceiverSafe()
        refreshA11yEventListeningMode()
        handler.postDelayed({ runCatching { tickOnce() } }, 600L)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (NodeKeepAlivePrefs.isRootMode(this)) return

        if (!NodeKeepAlivePrefs.shouldAllowA11yRestart(this)) {
            if (!NodeKeepAlivePrefs.shouldEnableA11yKeepAlive(this)) {
                disableSelfAndCleanup()
            }
            return
        }

        if (isNodeRunning()) {
            refreshA11yEventListeningMode()
            return
        }

        val now = SystemClock.uptimeMillis()
        if (now - lastEventTickUptimeMs < 30_000L) return
        lastEventTickUptimeMs = now
        handler.post { runCatching { tickOnce() } }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        unregisterStatusReceiverSafe()
        unregisterControlReceiverSafe()
        super.onDestroy()
    }

    private fun tickOnce() {
        if (NodeKeepAlivePrefs.isRootMode(this)) {
            disableSelfAndCleanup()
            return
        }

        if (!NodeKeepAlivePrefs.shouldEnableA11yKeepAlive(this)) {
            disableSelfAndCleanup()
            return
        }

        if (!NodeKeepAlivePrefs.isDesiredRunning(this)) return

        if (!NodeKeepAlivePrefs.hasPostNotificationsPermission(this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val now = System.currentTimeMillis()
                if (now - lastPermToastMs > 10 * 60_000L) {
                    lastPermToastMs = now
                    Toast.makeText(
                        this,
                        "无障碍保活需要通知权限才能稳定拉起前台服务",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            return
        }

        if (isNodeRunning()) {
            refreshA11yEventListeningMode()
            return
        }

        val projectDir = runCatching {
            NodeProjectManager.ensureProjectExtracted(
                this,
                RuntimePaths.normalProjectDir(this)
            )
        }.getOrNull()
        if (projectDir == null || !NodeProjectManager.hasSelectedCoreInstalled(this, projectDir)) {
            return
        }

        runCatching { NodeService.start(this) }
        refreshA11yEventListeningMode()
    }

    private fun isNodeRunning(): Boolean {
        val port = getSharedPreferences("runtime", Context.MODE_PRIVATE).getInt("port", 9321)
        if (port !in 1..65535) return false
        var socket: Socket? = null
        return try {
            socket = Socket()
            socket.connect(InetSocketAddress("127.0.0.1", port), 220)
            true
        } catch (_: Throwable) {
            false
        } finally {
            runCatching { socket?.close() }
        }
    }

    private fun refreshA11yEventListeningMode() {
        val shouldListen = NodeKeepAlivePrefs.shouldAllowA11yRestart(this) && !isNodeRunning()
        if (shouldListen == isEventListeningEnabled) return
        val info = runCatching { serviceInfo }.getOrNull() ?: return
        info.eventTypes = if (shouldListen) activeEventTypes else 0
        runCatching { setServiceInfo(info) }
        isEventListeningEnabled = shouldListen
    }

    private fun disableSelfAndCleanup() {
        handler.removeCallbacksAndMessages(null)
        unregisterStatusReceiverSafe()
        unregisterControlReceiverSafe()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { disableSelf() }
        } else {
            runCatching { stopSelf() }
        }
    }

    private fun registerStatusReceiverSafe() {
        val filter = IntentFilter(NodeService.ACTION_STATUS)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(statusReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(statusReceiver, filter)
            }
        } catch (_: Throwable) {
        }
    }

    private fun unregisterStatusReceiverSafe() {
        runCatching { unregisterReceiver(statusReceiver) }
    }

    private fun registerControlReceiverSafe() {
        val filter = IntentFilter(NodeKeepAlivePrefs.disableSelfAction(packageName))
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(controlReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(controlReceiver, filter)
            }
        } catch (_: Throwable) {
        }
    }

    private fun unregisterControlReceiverSafe() {
        runCatching { unregisterReceiver(controlReceiver) }
    }
}
