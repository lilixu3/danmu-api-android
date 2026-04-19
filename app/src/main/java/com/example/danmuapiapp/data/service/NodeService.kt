package com.example.danmuapiapp.data.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
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
        const val EXTRA_MESSAGE = "status_message"
        const val EXTRA_EXPLICIT_START = "explicit_start"
        const val STATUS_STARTING = "starting"
        const val STATUS_RUNNING = "running"
        const val STATUS_STOPPING = "stopping"
        const val STATUS_STOPPED = "stopped"
        const val STATUS_ERROR = "error"
        const val EXTRA_ERROR = "error_message"
        private val runtimeGeneration = AtomicLong(0L)
        private const val STOP_SHUTDOWN_ATTEMPTS = 4
        private const val STOP_WAIT_TIMEOUT_MS = 2600L
        private const val START_TIMEOUT_KILL_DELAY_MS = 350L
        private const val STALE_PROCESS_POLL_INTERVAL_MS = 180L
        private const val SHUTDOWN_HTTP_TIMEOUT_MS = 450

        fun start(context: Context, userInitiated: Boolean = true): Boolean {
            // 在调用进程先写入期望状态，避免跨进程停止/保活竞态。
            val appContext = context.applicationContext
            NodeKeepAlivePrefs.setDesiredRunning(appContext, true)
            if (userInitiated) {
                NodeKeepAlivePrefs.clearRestartBackoff(appContext)
            } else {
                val blockedMs = NodeKeepAlivePrefs.getRestartBlockRemainingMs(appContext)
                if (blockedMs > 0L) {
                    AppDiagnosticLogger.w(appContext, TAG, "普通模式后台恢复暂缓，剩余 ${blockedMs}ms")
                    SystemHeartbeatScheduler.refresh(appContext)
                    return false
                }
            }
            SystemHeartbeatScheduler.refresh(appContext)
            val intent = Intent(context, NodeService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_EXPLICIT_START, userInitiated)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            return true
        }

        fun stop(context: Context) {
            // 在调用进程先写入期望状态，确保保活侧立即可见“用户要停止”。
            val appContext = context.applicationContext
            NodeKeepAlivePrefs.setDesiredRunning(appContext, false)
            NodeKeepAlivePrefs.clearRestartBackoff(appContext)
            SystemHeartbeatScheduler.refresh(appContext)
            val intent = Intent(context, NodeService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun isProcessRunning(context: Context): Boolean {
            return findProcessPid(context) != null
        }

        fun killProcessIfRunning(context: Context): Boolean {
            val pid = findProcessPid(context) ?: return false
            if (pid == android.os.Process.myPid()) return false
            return runCatching {
                android.os.Process.killProcess(pid)
                true
            }.getOrElse { false }
        }

        fun recoverStaleProcessIfNeeded(
            context: Context,
            port: Int,
            confirmTimeoutMs: Long = 1500L,
            stopTimeoutMs: Long = 4000L
        ): Boolean {
            val appContext = context.applicationContext
            if (!confirmStaleProcess(appContext, port, confirmTimeoutMs)) return true
            if (!killProcessIfRunning(appContext) && isProcessRunning(appContext)) return false
            return waitForProcessStop(appContext, port, stopTimeoutMs)
        }

        private fun findProcessPid(context: Context): Int? {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return null
            return runCatching {
                val nodeProcess = "${context.packageName}:node"
                am.runningAppProcesses
                    ?.firstOrNull { it.processName == nodeProcess }
                    ?.pid
                    ?.takeIf { it > 0 }
            }.getOrNull()
        }

        private fun confirmStaleProcess(context: Context, port: Int, timeoutMs: Long): Boolean {
            val deadline = System.currentTimeMillis() + timeoutMs.coerceAtLeast(0L)
            while (System.currentTimeMillis() < deadline) {
                if (!isProcessRunning(context)) return false
                if (port in 1..65535 && isPortReachable(port)) return false
                sleepQuietly(STALE_PROCESS_POLL_INTERVAL_MS)
            }
            return isProcessRunning(context) &&
                (port !in 1..65535 || !isPortReachable(port))
        }

        private fun waitForProcessStop(context: Context, port: Int, timeoutMs: Long): Boolean {
            val deadline = System.currentTimeMillis() + timeoutMs.coerceAtLeast(0L)
            while (System.currentTimeMillis() < deadline) {
                if (!isProcessRunning(context) && (port !in 1..65535 || !isPortReachable(port))) {
                    return true
                }
                sleepQuietly(140L)
            }
            return !isProcessRunning(context) &&
                (port !in 1..65535 || !isPortReachable(port))
        }

        private fun isPortReachable(port: Int): Boolean {
            var socket: Socket? = null
            return try {
                socket = Socket()
                socket.soTimeout = 220
                socket.connect(InetSocketAddress("127.0.0.1", port), 220)
                true
            } catch (_: Exception) {
                false
            } finally {
                runCatching { socket?.close() }
            }
        }

        private fun sleepQuietly(delayMs: Long) {
            runCatching { Thread.sleep(delayMs.coerceAtLeast(0L)) }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val stateLock = Any()
    private var nodeThread: Thread? = null
    private var isRunning = false
    private var isStopping = false
    private var runningPublishedGeneration = -1L
    private var startupStartedAtMs = 0L
    private var currentStartExplicit = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val explicitStart = intent?.getBooleanExtra(EXTRA_EXPLICIT_START, false) == true
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

        val shouldStart = shouldAcceptStartRequest()
        if (shouldStart) {
            synchronized(stateLock) {
                currentStartExplicit = explicitStart
            }
            startServiceInForeground("正在启动...")
            publishStarting("正在准备运行环境…", explicitStart = explicitStart)
            startNode()
        }
        return START_STICKY
    }

    private fun runtimeProfile(): NormalModeRuntimeProfile {
        return NormalModeRuntimeProfiles.current(applicationContext)
    }

    private fun startServiceInForeground(message: String) {
        val notification = buildNotification(message)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onTimeout(startId: Int) {
        AppDiagnosticLogger.e(this, TAG, "普通模式前台服务触发系统超时，正在强制停止")
        synchronized(stateLock) {
            isRunning = false
            isStopping = false
            runningPublishedGeneration = -1L
            startupStartedAtMs = 0L
            currentStartExplicit = false
            nodeThread = null
        }
        broadcastStatus(
            STATUS_ERROR,
            message = "前台服务被系统超时限制，已停止",
            error = "前台服务被系统超时限制，已停止"
        )
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        onTimeout(startId)
    }

    private fun startNode() {
        val generation: Long
        val startupIssuedAtMs = System.currentTimeMillis()
        val explicitStart: Boolean
        synchronized(stateLock) {
            val startingOrRunning = isRunning || nodeThread?.isAlive == true
            if (startingOrRunning || isStopping) return
            isRunning = true
            isStopping = false
            this@NodeService.startupStartedAtMs = startupIssuedAtMs
            generation = runtimeGeneration.incrementAndGet()
            runningPublishedGeneration = -1L
            explicitStart = currentStartExplicit
        }

        StartupFailureStore.clearNormal(this)

        scope.launch {
            try {
                publishStarting("正在准备运行环境…", explicitStart = explicitStart)
                val projectDir = awaitPreparedProjectDir(
                    generation = generation,
                    startupStartedAtMs = startupIssuedAtMs
                ) ?: return@launch
                val startCanceled = synchronized(stateLock) {
                    runtimeGeneration.get() != generation || !isRunning || isStopping
                }
                if (startCanceled) {
                    AppDiagnosticLogger.i(this@NodeService, TAG, "启动流程已取消，忽略后续启动 generation=$generation")
                    return@launch
                }

                val runtimeThread = Thread {
                    var exitCode = 0
                    var crashThrowable: Throwable? = null
                    try {
                        exitCode = NodeBridge.startNodeWithArguments(
                            arrayOf("node", "${projectDir.absolutePath}/main.js")
                        )
                    } catch (t: Throwable) {
                        crashThrowable = t
                    } finally {
                        if (runtimeGeneration.get() == generation) {
                            val exitAction = synchronized(stateLock) {
                                val stopping = isStopping
                                if (runtimeGeneration.get() == generation) {
                                    isRunning = false
                                    if (!stopping) {
                                        isStopping = false
                                    }
                                    startupStartedAtMs = 0L
                                }
                                if (nodeThread === Thread.currentThread()) {
                                    nodeThread = null
                                }
                                decideNodeRuntimeExitAction(
                                    stopping = stopping,
                                    exitCode = exitCode,
                                    crashThrowable = crashThrowable
                                )
                            }
                            when (exitAction) {
                                NodeRuntimeExitAction.ReportError -> {
                                    val startupFailure = StartupFailureStore.readNormal(this@NodeService)
                                    val msg = crashThrowable?.let { buildErrorMessage(it) }
                                        ?: startupFailure?.userMessage()
                                        ?: "Node 进程异常退出，退出码：$exitCode"
                                    if (crashThrowable != null) {
                                        AppDiagnosticLogger.e(this@NodeService, TAG, "Node crashed: $msg", crashThrowable)
                                    } else if (startupFailure != null && startupFailure.detail.isNotBlank()) {
                                        AppDiagnosticLogger.e(
                                            this@NodeService,
                                            TAG,
                                            "Node crashed: ${startupFailure.detail}"
                                        )
                                    } else {
                                        AppDiagnosticLogger.e(this@NodeService, TAG, "Node crashed: $msg")
                                    }
                                    recordRecoveryFailure()
                                    SystemHeartbeatScheduler.refresh(applicationContext)
                                    broadcastStatus(STATUS_ERROR, message = msg, error = msg)
                                    stopForegroundAndSelf()
                                }

                                NodeRuntimeExitAction.ReportStopped -> {
                                    broadcastStatus(STATUS_STOPPED, message = "服务已停止")
                                    stopForegroundAndSelf()
                                }

                                NodeRuntimeExitAction.DeferToStopController -> {
                                    AppDiagnosticLogger.i(
                                        this@NodeService,
                                        TAG,
                                        "检测到受控停止流程，交由 stopNode/finalizeStop 收尾 generation=$generation"
                                    )
                                }
                            }
                        } else {
                            AppDiagnosticLogger.i(this@NodeService, TAG, "忽略旧实例退出广播，generation=$generation")
                        }
                    }
                }.apply {
                    name = "NodeJS-Runtime"
                }
                synchronized(stateLock) {
                    nodeThread = runtimeThread
                }
                publishStarting("运行环境已准备，正在启动服务…", explicitStart = explicitStart)
                runtimeThread.start()

                // 启动慢机型上端口可能晚于首轮超时才就绪，因此超时后继续低频复检。
                scope.launch {
                    publishStarting("正在等待服务端口就绪…", explicitStart = explicitStart)
                    val profile = runtimeProfile()
                    val initialReadyTimeoutMs = remainingStartupBudgetMs(startupStartedAtMs, profile)
                        .coerceAtMost(profile.startupReadyTimeoutMs)
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

                    publishStarting("启动较慢，继续等待服务就绪…", explicitStart = explicitStart)
                    while (isActive && runtimeGeneration.get() == generation) {
                        if (!isNodeThreadAlive()) return@launch
                        val ports = resolveCandidatePorts()
                        val nowReady = ports.any { it in 1..65535 && isPortOpen(it) }
                        if (nowReady) {
                            publishRunningIfNeeded(generation)
                            return@launch
                        }

                        val remainingBudgetMs = remainingStartupBudgetMs(startupStartedAtMs, profile)
                        if (remainingBudgetMs <= 0L) {
                            handleStartupTimeout(generation, "普通模式启动超时：服务进程仍在但端口未就绪")
                            return@launch
                        }
                        delay(minOf(profile.startupRecheckIntervalMs, remainingBudgetMs))
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
        val profile = runtimeProfile()
        val remainingBudgetMs = remainingStartupBudgetMs(startupStartedAtMs, profile)
        if (remainingBudgetMs <= 0L) {
            handleStartupTimeout(generation, "普通模式启动超时：运行环境准备未完成")
            return null
        }

        val preparedDeferred = CompletableDeferred<Result<java.io.File>>()
        scope.launch {
            val prepared = runCatching {
                publishStarting("正在检查运行环境…")
                val projectDir = NodeProjectManager.ensureProjectExtracted(this@NodeService)
                NodeProjectManager.migrateAllCoreLayouts(projectDir)
                publishStarting("正在同步启动配置…")
                // 从主进程已写入的 .env 中读取 variant，避免 :node 进程 SharedPreferences 跨进程不一致覆盖。
                val envVariant = runCatching {
                    java.io.File(projectDir, "config/.env").takeIf { it.exists() }
                        ?.readLines()
                        ?.firstOrNull { it.startsWith("DANMU_API_VARIANT=") }
                        ?.substringAfter("=")?.trim()
                }.getOrNull()
                NodeProjectManager.writeRuntimeEnv(
                    context = this@NodeService,
                    targetProjectDir = projectDir,
                    preferredVariantKey = envVariant
                )
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

    private fun remainingStartupBudgetMs(
        startupStartedAtMs: Long,
        profile: NormalModeRuntimeProfile = runtimeProfile()
    ): Long {
        val elapsedMs = (System.currentTimeMillis() - startupStartedAtMs).coerceAtLeast(0L)
        return (profile.startupTotalTimeoutMs - elapsedMs).coerceAtLeast(0L)
    }

    private fun recordRecoveryFailure() {
        NodeKeepAlivePrefs.recordRecoveryFailure(applicationContext)
    }

    private fun handleStartupFailure(generation: Long, message: String, throwable: Throwable? = null) {
        if (runtimeGeneration.get() != generation) return
        AppDiagnosticLogger.e(this, TAG, "Failed to start node: $message", throwable)
        synchronized(stateLock) {
            isRunning = false
            isStopping = false
            runningPublishedGeneration = -1L
            startupStartedAtMs = 0L
            if (nodeThread?.isAlive != true) {
                nodeThread = null
            }
        }
        recordRecoveryFailure()
        SystemHeartbeatScheduler.refresh(applicationContext)
        updateNotification("启动失败：$message")
        broadcastStatus(STATUS_ERROR, message = message, error = message)
        stopForegroundAndSelf()
    }

    private suspend fun handleStartupTimeout(generation: Long, message: String) {
        if (runtimeGeneration.get() != generation) return
        val startupFailure = StartupFailureStore.readNormal(this)
        val resolvedMessage = startupFailure?.userMessage() ?: message
        AppDiagnosticLogger.w(
            this,
            TAG,
            startupFailure?.detail?.takeIf { it.isNotBlank() } ?: resolvedMessage
        )
        val shouldKillProcess = synchronized(stateLock) {
            if (runtimeGeneration.get() != generation) return
            val threadAlive = nodeThread?.isAlive == true
            isRunning = false
            isStopping = false
            runningPublishedGeneration = -1L
            startupStartedAtMs = 0L
            currentStartExplicit = false
            if (!threadAlive) {
                nodeThread = null
            }
            threadAlive
        }
        recordRecoveryFailure()
        SystemHeartbeatScheduler.refresh(applicationContext)
        updateNotification("启动失败：$resolvedMessage")
        broadcastStatus(STATUS_ERROR, message = resolvedMessage, error = resolvedMessage)
        if (!shouldKillProcess) {
            stopForegroundAndSelf()
            return
        }
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
            publishStopping("正在安全停止服务…")
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
            AppDiagnosticLogger.w(this@NodeService, TAG, "普通模式停止超时，强制结束 :node 进程")
            publishStopping("停止较慢，正在强制回收服务进程…")
            broadcastStatus(STATUS_STOPPED, message = "服务已停止")
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
        val publishExplicitStart = synchronized(stateLock) {
            val sameGeneration = runtimeGeneration.get() == generation
            if (!sameGeneration) return@synchronized null
            val canPublish = isRunning &&
                !isStopping &&
                nodeThread?.isAlive == true &&
                runningPublishedGeneration != generation
            if (canPublish) {
                runningPublishedGeneration = generation
                currentStartExplicit
            } else {
                null
            }
        }
        if (publishExplicitStart == null) return
        NodeKeepAlivePrefs.noteSuccessfulStart(applicationContext)
        updateNotification("服务运行中")
        broadcastStatus(
            STATUS_RUNNING,
            message = "接口已就绪，可直接在局域网访问",
            explicitStart = publishExplicitStart
        )
    }

    private fun finalizeStop(generation: Long) {
        if (runtimeGeneration.get() != generation) return
        synchronized(stateLock) {
            if (runtimeGeneration.get() != generation) return
            isRunning = false
            isStopping = false
            runningPublishedGeneration = -1L
            startupStartedAtMs = 0L
            currentStartExplicit = false
            if (nodeThread?.isAlive != true) {
                nodeThread = null
            }
        }
        // 主动广播停止，避免仓库层仅靠兜底超时判断导致“已停却报错”。
        broadcastStatus(STATUS_STOPPED, message = "服务已停止")
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

    private fun shouldAcceptStartRequest(): Boolean {
        val staleTimeoutMs = runtimeProfile().startupStaleTimeoutMs
        val anyPortOpen = resolveCandidatePorts().any { it in 1..65535 && isPortOpen(it) }
        synchronized(stateLock) {
            val threadAlive = nodeThread?.isAlive == true
            val staleFlags = (isRunning || isStopping) &&
                !threadAlive &&
                !anyPortOpen &&
                startupStartedAtMs <= 0L
            val startupTimedOut = isRunning &&
                !threadAlive &&
                !isStopping &&
                startupStartedAtMs > 0L &&
                System.currentTimeMillis() - startupStartedAtMs >= staleTimeoutMs

            if (staleFlags || startupTimedOut) {
                AppDiagnosticLogger.w(
                    this,
                    TAG,
                    if (startupTimedOut) {
                        "检测到普通模式启动状态残留，已重置本地启动标记"
                    } else {
                        "检测到普通模式本地运行标记残留，已重置后接受新的启动请求"
                    }
                )
                isRunning = false
                isStopping = false
                runningPublishedGeneration = -1L
                startupStartedAtMs = 0L
                currentStartExplicit = false
                nodeThread = null
            }
            return !(isRunning || nodeThread?.isAlive == true || isStopping)
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

    private fun currentExplicitStart(): Boolean {
        return synchronized(stateLock) { currentStartExplicit }
    }

    private fun broadcastStatus(
        status: String,
        message: String? = null,
        error: String? = null,
        explicitStart: Boolean? = null
    ) {
        sendBroadcast(Intent(ACTION_STATUS).apply {
            setPackage(packageName)
            putExtra(EXTRA_STATUS, status)
            message?.let { putExtra(EXTRA_MESSAGE, it) }
            error?.let { putExtra(EXTRA_ERROR, it) }
            explicitStart?.let { putExtra(EXTRA_EXPLICIT_START, it) }
        })
    }

    private fun publishStarting(message: String, explicitStart: Boolean = currentExplicitStart()) {
        updateNotification(message)
        broadcastStatus(STATUS_STARTING, message = message, explicitStart = explicitStart)
    }

    private fun publishStopping(message: String) {
        updateNotification(message)
        broadcastStatus(STATUS_STOPPING, message = message)
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
            startupStartedAtMs = 0L
            currentStartExplicit = false
        }
        super.onDestroy()
    }
}
