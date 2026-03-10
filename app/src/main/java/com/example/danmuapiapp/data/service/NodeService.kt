package com.example.danmuapiapp.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.danmuapiapp.MainActivity
import com.example.danmuapiapp.NodeBridge
import com.example.danmuapiapp.BuildConfig
import com.example.danmuapiapp.R
import com.example.danmuapiapp.domain.model.ErrorHandler
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

class NodeService : Service() {

    companion object {
        const val TAG = "NodeService"
        const val CHANNEL_ID = "danmuapi_service"
        const val NOTIFICATION_ID = 1
        private val actionPrefix: String
            get() = BuildConfig.APPLICATION_ID
        val ACTION_START: String
            get() = "$actionPrefix.START_NODE"
        val ACTION_STOP: String
            get() = "$actionPrefix.STOP_NODE"
        val ACTION_STATUS: String
            get() = "$actionPrefix.NODE_STATUS"
        const val EXTRA_STATUS = "status"
        const val STATUS_RUNNING = "running"
        const val STATUS_STOPPED = "stopped"
        const val STATUS_ERROR = "error"
        const val EXTRA_ERROR = "error_message"
        private val runtimeGeneration = AtomicLong(0L)
        private const val STOP_SHUTDOWN_ATTEMPTS = 4
        private const val STOP_WAIT_TIMEOUT_MS = 2600L
        private const val START_TOTAL_TIMEOUT_MS = 35_000L
        private const val START_READY_TIMEOUT_MS = 20_000L
        private const val START_READY_RECHECK_INTERVAL_MS = 2000L
        private const val START_TIMEOUT_KILL_DELAY_MS = 350L
        private const val SHUTDOWN_HTTP_TIMEOUT_MS = 450

        fun start(context: Context) {
            // 在调用进程先写入期望状态，避免跨进程停止/保活竞态。
            val appContext = context.applicationContext
            NodeKeepAlivePrefs.setDesiredRunning(appContext, true)
            SystemHeartbeatScheduler.refresh(appContext)
            val intent = Intent(context, NodeService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            // 在调用进程先写入期望状态，确保保活侧立即可见“用户要停止”。
            val appContext = context.applicationContext
            NodeKeepAlivePrefs.setDesiredRunning(appContext, false)
            SystemHeartbeatScheduler.refresh(appContext)
            val intent = Intent(context, NodeService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val stateLock = Any()
    private var nodeThread: Thread? = null
    private var isRunning = false
    private var isStopping = false
    private var runningPublishedGeneration = -1L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_STOP -> {
                stopNode()
                return START_NOT_STICKY
            }
            ACTION_START -> Unit
            null -> {
                // START_STICKY 重建会传入 null intent，仅在用户期望运行时恢复。
                if (!NodeKeepAlivePrefs.isDesiredRunning(this)) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
            }
            else -> return START_NOT_STICKY
        }

        val shouldStart = synchronized(stateLock) {
            !(isRunning || nodeThread?.isAlive == true || isStopping)
        }
        if (shouldStart) {
            startForeground(NOTIFICATION_ID, buildNotification("正在启动..."))
            startNode()
        }
        return START_STICKY
    }

    private fun startNode() {
        val generation: Long
        val startupStartedAtMs = System.currentTimeMillis()
        synchronized(stateLock) {
            val startingOrRunning = isRunning || nodeThread?.isAlive == true
            if (startingOrRunning || isStopping) return
            isRunning = true
            isStopping = false
            generation = runtimeGeneration.incrementAndGet()
            runningPublishedGeneration = -1L
        }

        scope.launch {
            try {
                val projectDir = awaitPreparedProjectDir(
                    generation = generation,
                    startupStartedAtMs = startupStartedAtMs
                ) ?: return@launch

                val runtimeThread = Thread {
                    try {
                        NodeBridge.startNodeWithArguments(
                            arrayOf("node", "${projectDir.absolutePath}/main.js")
                        )
                    } catch (t: Throwable) {
                        if (runtimeGeneration.get() == generation) {
                            val msg = buildErrorMessage(t)
                            Log.e(TAG, "Node crashed: $msg", t)
                            broadcastStatus(STATUS_ERROR, msg)
                        }
                    } finally {
                        if (runtimeGeneration.get() == generation) {
                            synchronized(stateLock) {
                                if (runtimeGeneration.get() == generation) {
                                    isRunning = false
                                    isStopping = false
                                }
                                if (nodeThread === Thread.currentThread()) {
                                    nodeThread = null
                                }
                            }
                            broadcastStatus(STATUS_STOPPED)
                            stopForegroundAndSelf()
                        } else {
                            Log.i(TAG, "忽略旧实例退出广播，generation=$generation")
                        }
                    }
                }.apply {
                    name = "NodeJS-Runtime"
                }
                synchronized(stateLock) {
                    nodeThread = runtimeThread
                }
                runtimeThread.start()

                // 启动慢机型上端口可能晚于首轮超时才就绪，因此超时后继续低频复检。
                scope.launch {
                    val initialReadyTimeoutMs = remainingStartupBudgetMs(startupStartedAtMs)
                        .coerceAtMost(START_READY_TIMEOUT_MS)
                    if (initialReadyTimeoutMs <= 0L) {
                        handleStartupTimeout(generation, "普通模式启动超时：运行环境准备未完成")
                        return@launch
                    }

                    val ready = waitForRuntimeReady(
                        ports = resolveCandidatePorts(),
                        generation = generation,
                        timeoutMs = initialReadyTimeoutMs
                    )
                    if (ready) {
                        publishRunningIfNeeded(generation)
                        return@launch
                    }

                    while (isActive && runtimeGeneration.get() == generation) {
                        if (!isNodeThreadAlive()) return@launch
                        val ports = resolveCandidatePorts()
                        val nowReady = ports.any { it in 1..65535 && isPortOpen(it) }
                        if (nowReady) {
                            publishRunningIfNeeded(generation)
                            return@launch
                        }

                        val remainingBudgetMs = remainingStartupBudgetMs(startupStartedAtMs)
                        if (remainingBudgetMs <= 0L) {
                            handleStartupTimeout(generation, "普通模式启动超时：服务进程仍在但端口未就绪")
                            return@launch
                        }
                        delay(minOf(START_READY_RECHECK_INTERVAL_MS, remainingBudgetMs))
                    }
                }
            } catch (t: Throwable) {
                if (runtimeGeneration.get() == generation) {
                    handleStartupFailure(generation, buildErrorMessage(t), t)
                }
            }
        }
    }

    private suspend fun awaitPreparedProjectDir(
        generation: Long,
        startupStartedAtMs: Long
    ): java.io.File? {
        val remainingBudgetMs = remainingStartupBudgetMs(startupStartedAtMs)
        if (remainingBudgetMs <= 0L) {
            handleStartupTimeout(generation, "普通模式启动超时：运行环境准备未完成")
            return null
        }

        val preparedDeferred = CompletableDeferred<Result<java.io.File>>()
        scope.launch {
            val prepared = runCatching {
                val projectDir = NodeProjectManager.ensureProjectExtracted(this@NodeService)
                NodeProjectManager.migrateAllCoreLayouts(projectDir)
                projectDir
            }
            preparedDeferred.complete(prepared)
        }

        val prepared = withTimeoutOrNull(remainingBudgetMs) {
            preparedDeferred.await()
        }
        if (prepared == null) {
            handleStartupTimeout(generation, "普通模式启动超时：运行环境准备未完成")
            return null
        }
        return prepared.getOrElse { throwable ->
            handleStartupFailure(generation, buildErrorMessage(throwable), throwable)
            null
        }
    }

    private fun remainingStartupBudgetMs(startupStartedAtMs: Long): Long {
        val elapsedMs = (System.currentTimeMillis() - startupStartedAtMs).coerceAtLeast(0L)
        return (START_TOTAL_TIMEOUT_MS - elapsedMs).coerceAtLeast(0L)
    }

    private fun handleStartupFailure(generation: Long, message: String, throwable: Throwable? = null) {
        if (runtimeGeneration.get() != generation) return
        if (throwable != null) {
            Log.e(TAG, "Failed to start node: $message", throwable)
        } else {
            Log.e(TAG, "Failed to start node: $message")
        }
        synchronized(stateLock) {
            isRunning = false
            isStopping = false
            runningPublishedGeneration = -1L
            if (nodeThread?.isAlive != true) {
                nodeThread = null
            }
        }
        NodeKeepAlivePrefs.setDesiredRunning(applicationContext, false)
        SystemHeartbeatScheduler.refresh(applicationContext)
        broadcastStatus(STATUS_ERROR, message)
        stopForegroundAndSelf()
    }

    private suspend fun handleStartupTimeout(generation: Long, message: String) {
        if (runtimeGeneration.get() != generation) return
        Log.w(TAG, message)
        synchronized(stateLock) {
            if (runtimeGeneration.get() != generation) return
            isRunning = false
            isStopping = false
            runningPublishedGeneration = -1L
        }
        NodeKeepAlivePrefs.setDesiredRunning(applicationContext, false)
        SystemHeartbeatScheduler.refresh(applicationContext)
        broadcastStatus(STATUS_ERROR, message)
        delay(START_TIMEOUT_KILL_DELAY_MS)
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun stopNode() {
        val generation: Long
        synchronized(stateLock) {
            if (isStopping) return
            isStopping = true
            generation = runtimeGeneration.get()
        }
        scope.launch {
            val ports = resolveCandidatePorts()
            val alreadyStopped = !isNodeThreadAlive() && ports.none { it in 1..65535 && isPortOpen(it) }

            if (!alreadyStopped) {
                requestShutdownWithRetries(ports, generation)
            }

            val stopped = waitForNodeStopped(ports, timeoutMs = STOP_WAIT_TIMEOUT_MS, generation = generation)

            if (runtimeGeneration.get() != generation) {
                return@launch
            }
            if (stopped) {
                finalizeStop(generation)
                return@launch
            }

            // Node/V8 停不干净时，直接终止 :node 进程，避免后续无法重启。
            Log.w(TAG, "普通模式停止超时，强制结束 :node 进程")
            broadcastStatus(STATUS_STOPPED)
            delay(350)
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    private fun resolveCandidatePorts(): Set<Int> {
        val envPort = readPortFromEnvFile()

        // 跨进程读取 SharedPreferences 不安全，优先以 .env 文件为准，兜底默认端口。
        return linkedSetOf<Int>().apply {
            if (envPort in 1..65535) add(envPort)
            add(9321)
        }
    }

    private suspend fun requestShutdownWithRetries(ports: Set<Int>, generation: Long) {
        val validPorts = ports.filter { it in 1..65535 }
        repeat(STOP_SHUTDOWN_ATTEMPTS) { attempt ->
            if (runtimeGeneration.get() != generation) return

            // 只对当前可达端口发送关闭请求，避免在无效端口上消耗长超时。
            val openPorts = validPorts.filter { isPortOpen(it) }
            openPorts.forEach { port ->
                tryShutdownAt(port)
            }

            if (waitForNodeStopped(ports, timeoutMs = 320L, generation = generation)) {
                return
            }

            val sleepMs = when (attempt) {
                0 -> 0L
                1 -> 140L
                2 -> 220L
                else -> 320L
            }
            if (sleepMs > 0) {
                delay(sleepMs)
            }
        }
    }

    private suspend fun waitForNodeStopped(ports: Set<Int>, timeoutMs: Long, generation: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (runtimeGeneration.get() != generation) return true
            val threadAlive = isNodeThreadAlive()
            val anyOpen = ports.any { it in 1..65535 && isPortOpen(it) }
            if (!threadAlive && !anyOpen) return true
            delay(140)
        }
        if (runtimeGeneration.get() != generation) return true
        val threadAlive = isNodeThreadAlive()
        val anyOpen = ports.any { it in 1..65535 && isPortOpen(it) }
        return !threadAlive && !anyOpen
    }

    private suspend fun waitForRuntimeReady(ports: Set<Int>, generation: Long, timeoutMs: Long): Boolean {
        val validPorts = ports.filter { it in 1..65535 }.ifEmpty { listOf(9321) }
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (runtimeGeneration.get() != generation) return false
            if (!isNodeThreadAlive()) return false
            if (validPorts.any { isPortOpen(it) }) return true
            delay(140)
        }
        if (runtimeGeneration.get() != generation) return false
        if (!isNodeThreadAlive()) return false
        return validPorts.any { isPortOpen(it) }
    }

    private fun isNodeThreadAlive(): Boolean {
        synchronized(stateLock) {
            return nodeThread?.isAlive == true
        }
    }

    private fun publishRunningIfNeeded(generation: Long) {
        val shouldPublish = synchronized(stateLock) {
            val sameGeneration = runtimeGeneration.get() == generation
            if (!sameGeneration) return@synchronized false
            val canPublish = isRunning &&
                !isStopping &&
                nodeThread?.isAlive == true &&
                runningPublishedGeneration != generation
            if (canPublish) {
                runningPublishedGeneration = generation
            }
            canPublish
        }
        if (!shouldPublish) return
        broadcastStatus(STATUS_RUNNING)
        updateNotification("服务运行中")
    }

    private fun finalizeStop(generation: Long) {
        if (runtimeGeneration.get() != generation) return
        synchronized(stateLock) {
            if (runtimeGeneration.get() != generation) return
            isRunning = false
            isStopping = false
            runningPublishedGeneration = -1L
            if (nodeThread?.isAlive != true) {
                nodeThread = null
            }
        }
        // 主动广播停止，避免仓库层仅靠兜底超时判断导致“已停却报错”。
        broadcastStatus(STATUS_STOPPED)
        stopForegroundAndSelf()
    }

    private fun stopForegroundAndSelf() {
        synchronized(stateLock) {
            isStopping = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun isPortOpen(port: Int): Boolean {
        var socket: Socket? = null
        return try {
            socket = Socket()
            socket.connect(InetSocketAddress("127.0.0.1", port), 220)
            true
        } catch (_: Exception) {
            false
        } finally {
            runCatching { socket?.close() }
        }
    }

    private fun tryShutdownAt(port: Int): Boolean {
        return try {
            val url = URL("http://127.0.0.1:$port/__shutdown")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = SHUTDOWN_HTTP_TIMEOUT_MS
                readTimeout = SHUTDOWN_HTTP_TIMEOUT_MS
                requestMethod = "GET"
            }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (_: Exception) {
            false
        }
    }

    private fun readPortFromEnvFile(): Int {
        return try {
            val envFile = java.io.File(NodeProjectManager.projectDir(this), "config/.env")
            if (!envFile.exists()) return 0
            val line = envFile.readLines().firstOrNull {
                it.trim().startsWith("DANMU_API_PORT=")
            } ?: return 0
            line.substringAfter("=").trim().toIntOrNull() ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private fun buildErrorMessage(t: Throwable): String {
        return ErrorHandler.buildDetailedMessage(t)
    }

    private fun broadcastStatus(status: String, error: String? = null) {
        sendBroadcast(Intent(ACTION_STATUS).apply {
            setPackage(packageName)
            putExtra(EXTRA_STATUS, status)
            error?.let { putExtra(EXTRA_ERROR, it) }
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        createNotificationChannel26()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel26() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pendingFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            pendingFlags
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        scope.cancel()
        synchronized(stateLock) {
            nodeThread?.interrupt()
            nodeThread = null
            isRunning = false
            isStopping = false
            runningPublishedGeneration = -1L
        }
        super.onDestroy()
    }
}
