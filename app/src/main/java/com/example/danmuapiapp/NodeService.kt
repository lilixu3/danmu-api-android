package com.example.danmuapiapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.net.HttpURLConnection
import java.io.File
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

class NodeService : Service() {

    companion object {
        private const val CHANNEL_ID = "danmuapi_node"
        private const val NOTIF_ID = 3001

        /** Start foreground Node service (default when action is null). */
        const val ACTION_START = "com.example.danmuapiapp.action.START_NODE"

        /** Request the embedded Node server to shutdown, then stop foreground service. */
        const val ACTION_STOP = "com.example.danmuapiapp.action.STOP_NODE"

        /** Refresh restart policy after settings changes (no side effects). */
        const val ACTION_REFRESH_POLICY = "com.example.danmuapiapp.action.REFRESH_POLICY"

        const val ACTION_NODE_STATUS = "com.example.danmuapiapp.NODE_STATUS"
        const val EXTRA_STATUS = "status"
        const val EXTRA_MESSAGE = "message"

        const val STATUS_STARTING = "starting"
        const val STATUS_RUNNING = "running"
        const val STATUS_STOPPED = "stopped"
        const val STATUS_ERROR = "error"
        const val STATUS_ALREADY_RUNNING = "already_running"

        private val started = AtomicBoolean(false)

        fun isRunning(): Boolean = started.get()
    }

    private val stopRequested = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        // 0) Settings changed: refresh restart policy without modifying desiredRunning.
        if (action == ACTION_REFRESH_POLICY) {
            if (started.get()) {
                // Keep notif up to date; do not broadcast status to avoid UI noise.
                startForeground(NOTIF_ID, buildNotification("Node 正在运行（前台服务）"))
            } else {
                // Nothing to refresh.
                stopSelf(startId)
            }
            return restartPolicy()
        }

        // 1) Explicit STOP: user intent to stop -> never auto-restart.
        if (action == ACTION_STOP) {
            NodeKeepAlive.setDesiredRunning(this, false)
            handleStopRequest(startId)
            return START_NOT_STICKY
        }

        // 2) Explicit START: user intent to start -> allow keep-alive (if enabled).
        if (action == ACTION_START) {
            NodeKeepAlive.setDesiredRunning(this, true)
        } else {
            // 3) action==null: can be either an explicit start (MainActivity uses no action)
            //    or a system re-delivery when the process was killed (START_STICKY => null intent).
            //    We MUST NOT override user's "desiredRunning" here.
            if (!NodeKeepAlive.isDesiredRunning(this)) {
                // User has stopped / exited -> do not resurrect.
                broadcastStatus(STATUS_STOPPED, "已停止")
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }

        if (started.get()) {
            startForeground(NOTIF_ID, buildNotification("Node 已在运行"))
            broadcastStatus(STATUS_ALREADY_RUNNING, "Node 已在运行（查看通知栏状态）")
            return restartPolicy()
        }

        startForeground(NOTIF_ID, buildNotification("Node 启动中…"))
        broadcastStatus(STATUS_STARTING, "启动中…（可在通知栏查看状态）")

        // New start cancels any previous stop request.
        stopRequested.set(false)

        if (started.compareAndSet(false, true)) {
            Thread {
                try {
                    val nodeProjectDir: File = AssetCopier.ensureNodeProjectExtracted(this)
                    val entryFile = File(nodeProjectDir, "main.js")
                    if (!entryFile.exists()) {
                        throw IllegalStateException(
                            "入口文件不存在: ${entryFile.absolutePath}（请确认 assets/nodejs-project/main.js 已打包）"
                        )
                    }

                    // If user requested stop while we were extracting/copying, cancel before starting Node.
                    if (stopRequested.get()) {
                        started.set(false)
                        updateNotification("启动已取消")
                        broadcastStatus(STATUS_STOPPED, "启动已取消")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                        } else {
                            @Suppress("DEPRECATION")
                            stopForeground(true)
                        }
                        stopSelf()
                        return@Thread
                    }

                    // 更新一次通知 + 广播，让主页有“已启动”的反馈
                    updateNotification("Node 正在运行（前台服务）")
                    broadcastStatus(STATUS_RUNNING, "Node 正在运行（前台服务）")
                    NodeKeepAlive.onNodeRunning(this)

                    val entry = entryFile.absolutePath
                    @Suppress("UNUSED_VARIABLE")
                    val code = NodeBridge.startNodeWithArguments(arrayOf("node", entry))

                    // Node 退出
                    started.set(false)
                    updateNotification("Node 已退出")
                    broadcastStatus(STATUS_STOPPED, "Node 已退出")

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                    stopSelf()
                } catch (t: Throwable) {
                    t.printStackTrace()
                    started.set(false)
                    val msg = (t.message ?: t.toString())
                    updateNotification("启动失败：$msg")
                    broadcastStatus(STATUS_ERROR, msg)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                    stopSelf()
                }
            }.start()
        }

        return restartPolicy()
    }

    /**
     * Only use START_STICKY when "无障碍保活" is enabled AND our accessibility service is enabled.
     * Otherwise, unexpected process kills should NOT resurrect the foreground service.
     */
    private fun restartPolicy(): Int {
        return if (
            NodeKeepAlive.isKeepAliveEnabled(this) &&
            NodeKeepAlive.isAccessibilityServiceEnabled(this) &&
            NodeKeepAlive.hasPostNotificationsPermission(this) &&
            NodeKeepAlive.isDesiredRunning(this)
        ) {
            START_STICKY
        } else {
            START_NOT_STICKY
        }
    }

    private fun handleStopRequest(startId: Int) {
        if (!started.get()) {
            // Not running: reflect stopped state for UI/tile, then stop immediately.
            broadcastStatus(STATUS_STOPPED, "未运行")
            stopSelf(startId)
            return
        }

        stopRequested.set(true)
        updateNotification("Node 正在停止…")

        // Request a graceful shutdown via loopback HTTP.
        Thread {
            try {
                requestNodeShutdownWithRetries()
            } catch (_: Throwable) {
            }

            // Give Node a moment to exit on its own.
            val deadline = System.currentTimeMillis() + 2500
            while (started.get() && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(100)
                } catch (_: Throwable) {
                }
            }

            // If still running, fall back to killing the process (same strategy as "关闭并退出").
            if (started.get()) {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }.start()
    }

    private fun requestNodeShutdownWithRetries() {
        val port = RuntimeConfig.getMainPort(this)
        var lastErr: Throwable? = null
        repeat(6) { attempt ->
            try {
                val url = URL("http://127.0.0.1:$port/__shutdown")
                val conn = (url.openConnection() as HttpURLConnection)
                conn.requestMethod = "GET"
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 700
                conn.readTimeout = 700
                conn.doInput = true
                try {
                    conn.connect()
                    runCatching { conn.inputStream.use { it.readBytes() } }
                } finally {
                    conn.disconnect()
                }
                return
            } catch (t: Throwable) {
                lastErr = t
                // Backoff: 0ms, 150ms, 250ms, 400ms...
                val sleepMs = when (attempt) {
                    0 -> 0L
                    1 -> 150L
                    2 -> 250L
                    else -> 400L
                }
                if (sleepMs > 0) {
                    try { Thread.sleep(sleepMs) } catch (_: Throwable) {}
                }
            }
        }
        if (lastErr != null) throw lastErr as Throwable
    }

    override fun onDestroy() {
        // 主界面的“关闭并退出”会先请求 /__shutdown，再结束 Service 并退出 App。
        // 这里把状态置回 false，允许下次重新启动。
        started.set(false)
        broadcastStatus(STATUS_STOPPED, "前台服务已停止")
        super.onDestroy()
    }

    private fun broadcastStatus(status: String, message: String) {
        val it = Intent(ACTION_NODE_STATUS)
            .setPackage(packageName)
            .putExtra(EXTRA_STATUS, status)
            .putExtra(EXTRA_MESSAGE, message)
        sendBroadcast(it)
    }

    private fun updateNotification(text: String) {
        @Suppress("DEPRECATION")
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            @Suppress("DEPRECATION")
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, openIntent, piFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("弹幕API")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_node)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "弹幕API 前台服务",
                NotificationManager.IMPORTANCE_LOW
            )
            mgr.createNotificationChannel(channel)
        }
    }
}
