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
import com.example.danmuapiapp.data.util.PortProbe
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 无障碍保活服务：普通模式下在异常退出时自动拉起前台服务。
 *
 * 注意：socket 探测与核心文件扫描都是阻塞操作（单次可达数百毫秒），
 * 一律在 [probeExecutor] 后台线程执行；主线程只做偏好读取与调度。
 */
class KeepAliveAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "KeepAliveA11y"
    }

    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var destroyed = false
    private var lastKnownNodeRunning = false
    private var lastEventTickUptimeMs = 0L
    private var isEventListeningEnabled = true
    private val activeEventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
    private val probeExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "a11y-keepalive-probe").apply { isDaemon = true }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != NodeService.ACTION_STATUS) return
            val status = intent.getStringExtra(NodeService.EXTRA_STATUS).orEmpty()
            if (status == NodeService.STATUS_STOPPED || status == NodeService.STATUS_ERROR) {
                handler.post {
                    runCatching {
                        refreshA11yEventListeningMode(nodeRunning = false)
                        tickOnce()
                    }
                }
            } else {
                handler.post {
                    runCatching {
                        if (status == NodeService.STATUS_RUNNING) {
                            refreshA11yEventListeningMode(nodeRunning = true)
                        } else {
                            refreshA11yEventListeningMode()
                        }
                        refreshHeartbeatSchedule()
                    }
                }
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

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            runCatching { tickOnce() }
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
        refreshHeartbeatSchedule()
        handler.postDelayed({ runCatching { tickOnce() } }, 600L)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (NodeKeepAlivePrefs.isRootMode(this)) {
            refreshHeartbeatSchedule()
            return
        }

        if (!NodeKeepAlivePrefs.shouldAllowA11yRestart(this)) {
            if (!NodeKeepAlivePrefs.shouldEnableA11yKeepAlive(this)) {
                disableSelfAndCleanup()
            }
            refreshHeartbeatSchedule()
            return
        }

        // 先做 30 秒节流，再交给后台线程探测。
        // 探测绝不能在主线程进行：服务停止期间每个窗口事件都会到来，
        // 否则每个事件都要做一次最多 220ms 的 socket 连接尝试。
        val now = SystemClock.uptimeMillis()
        if (now - lastEventTickUptimeMs < 30_000L) return
        lastEventTickUptimeMs = now
        handler.post { runCatching { tickOnce() } }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        probeExecutor.shutdownNow()
        unregisterStatusReceiverSafe()
        unregisterControlReceiverSafe()
        super.onDestroy()
    }

    private fun tickOnce() {
        if (destroyed) return
        try {
            if (NodeKeepAlivePrefs.isRootMode(this)) {
                disableSelfAndCleanup()
                return
            }

            if (!NodeKeepAlivePrefs.shouldEnableA11yKeepAlive(this)) {
                disableSelfAndCleanup()
                return
            }

            if (!NodeKeepAlivePrefs.isDesiredRunning(this)) return

            // socket 探测与核心文件检查都是阻塞 IO，放到后台线程执行。
            probeExecutor.execute {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                val appContext = applicationContext
                if (destroyed) return@execute
                NodeHeartbeatRecovery.tick(appContext, "AccessibilityKeepAlive") {
                    !destroyed && NodeKeepAlivePrefs.shouldAllowA11yRestart(appContext)
                }
                if (destroyed) return@execute
                val running = runCatching { isNodeRunning() }.getOrDefault(true)
                handler.post { runCatching { refreshA11yEventListeningMode(nodeRunning = running) } }
            }
        } finally {
            refreshHeartbeatSchedule()
        }
    }

    private fun isNodeRunning(): Boolean {
        val port = getSharedPreferences("runtime", Context.MODE_PRIVATE).getInt("port", 9321)
        return PortProbe.isOpen(port = port)
    }

    /**
     * @param nodeRunning 后台探测或运行时广播结果。主线程只读取缓存，不连接 socket。
     */
    private fun refreshA11yEventListeningMode(nodeRunning: Boolean = lastKnownNodeRunning) {
        if (destroyed) return
        lastKnownNodeRunning = nodeRunning
        val shouldListen = NodeKeepAlivePrefs.shouldAllowA11yRestart(this) && !nodeRunning
        if (shouldListen == isEventListeningEnabled) return
        val info = runCatching { serviceInfo }.getOrNull() ?: return
        info.eventTypes = if (shouldListen) activeEventTypes else 0
        runCatching { setServiceInfo(info) }
        isEventListeningEnabled = shouldListen
    }

    private fun refreshHeartbeatSchedule() {
        handler.removeCallbacks(heartbeatRunnable)
        if (destroyed) return
        if (!NodeKeepAlivePrefs.shouldRunA11yHeartbeat(this)) return
        val delayMs = NodeKeepAlivePrefs.getHeartbeatIntervalMinutes(this) * 60_000L
        handler.postDelayed(heartbeatRunnable, delayMs)
    }

    private fun disableSelfAndCleanup() {
        destroyed = true
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
