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
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class NodeService : Service() {

    companion object {
        private const val CHANNEL_ID = "danmuapi_node"
        private const val NOTIF_ID = 3001

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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (started.get()) {
            startForeground(NOTIF_ID, buildNotification("Node 已在运行"))
            broadcastStatus(STATUS_ALREADY_RUNNING, "Node 已在运行（查看通知栏状态）")
            return START_STICKY
        }

        startForeground(NOTIF_ID, buildNotification("Node 启动中…"))
        broadcastStatus(STATUS_STARTING, "启动中…（可在通知栏查看状态）")

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

                    // 更新一次通知 + 广播，让主页有“已启动”的反馈
                    updateNotification("Node 正在运行（前台服务）")
                    broadcastStatus(STATUS_RUNNING, "Node 正在运行（前台服务）")

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

        return START_STICKY
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
