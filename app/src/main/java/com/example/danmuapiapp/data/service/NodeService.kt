package com.example.danmuapiapp.data.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import java.io.File
import androidx.core.app.NotificationCompat
import com.example.danmuapiapp.MainActivity
import com.example.danmuapiapp.NodeBridge
import com.example.danmuapiapp.BuildConfig
import com.example.danmuapiapp.R
import com.example.danmuapiapp.data.repository.determineRuntimeOwnershipFromHealth
import com.example.danmuapiapp.data.repository.isRuntimeOwnershipOwned
import com.example.danmuapiapp.data.util.DeviceCompatMode
import com.example.danmuapiapp.data.util.DotEnvCodec
import com.example.danmuapiapp.data.service.RuntimeIdentityStore
import com.example.danmuapiapp.domain.model.ErrorHandler
import com.example.danmuapiapp.domain.model.NormalNotificationBehavior
import kotlinx.coroutines.*
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

class NodeService : Service() {

    companion object {
        const val TAG = "NodeService"
        const val CHANNEL_ID = ServiceNotificationChannels.CHANNEL_ID
        const val NOTIFICATION_ID = 1
        private val actionPrefix: String
            get() = BuildConfig.APPLICATION_ID
        val ACTION_START: String
            get() = "$actionPrefix.START_NODE"
        val ACTION_STOP: String
            get() = "$actionPrefix.STOP_NODE"
        val ACTION_RESTART: String
            get() = "$actionPrefix.RESTART_NODE"
        val ACTION_ENSURE_FOREGROUND: String
            get() = "$actionPrefix.ENSURE_NODE_FOREGROUND"
        val ACTION_REFRESH_NOTIFICATION: String
            get() = "$actionPrefix.REFRESH_NODE_NOTIFICATION"
        val ACTION_NOTIFICATION_DISMISSED: String
            get() = "$actionPrefix.NODE_NOTIFICATION_DISMISSED"
        val ACTION_STATUS: String
            get() = "$actionPrefix.NODE_STATUS"
        const val EXTRA_STATUS = "status"
        const val EXTRA_MESSAGE = "status_message"
        const val EXTRA_EXPLICIT_START = "explicit_start"
        const val EXTRA_FORCE_FOREGROUND = "force_foreground"
        const val STATUS_STARTING = "starting"
        const val STATUS_RUNNING = "running"
        const val STATUS_STOPPING = "stopping"
        const val STATUS_STOPPED = "stopped"
        const val STATUS_ERROR = "error"
        const val EXTRA_ERROR = "error_message"
        val ACTION_COPY_LAN_ADDRESS: String
            get() = "$actionPrefix.COPY_LAN_ADDRESS"
        private val runtimeGeneration = AtomicLong(0L)
        const val RUNTIME_WAKE_LOCK_TIMEOUT_MS = 6L * 60L * 60L * 1000L
        private const val STOP_SHUTDOWN_ATTEMPTS = 4
        private const val STOP_WAIT_TIMEOUT_MS = 2600L
        private const val START_TIMEOUT_KILL_DELAY_MS = 350L
        private const val STALE_PROCESS_POLL_INTERVAL_MS = 180L
        private const val SHUTDOWN_HTTP_TIMEOUT_MS = 450
        private const val UNEXPECTED_FOREGROUND_REATTACH_MIN_INTERVAL_MS = 30_000L
        private const val NOTIFICATION_ENDPOINT_REFRESH_DEBOUNCE_MS = 300L
        /** 进程启动未满该时长时不允许判僵死，避免误杀慢机型的正常启动过程。 */
        private const val STALE_PROCESS_MIN_UPTIME_MS = 10_000L
        private val lastUnexpectedForegroundReattachAtMs = AtomicLong(0L)

        fun start(context: Context, userInitiated: Boolean = true): Boolean {
            // 在调用进程先写入期望状态，避免跨进程启动/停止竞态。
            val appContext = context.applicationContext
            NodeKeepAlivePrefs.setDesiredRunning(appContext, true)
            if (userInitiated) {
                NormalNotificationBehaviorPrefs.clearManuallyHidden(appContext)
            }
            RuntimeIdentityStore.ensureInstanceId(appContext)
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
            // 在调用进程先写入期望状态，确保 :node 进程立即可见“用户要停止”。
            val appContext = context.applicationContext
            NodeKeepAlivePrefs.setDesiredRunning(appContext, false)
            NormalNotificationBehaviorPrefs.clearManuallyHidden(appContext)
            SystemHeartbeatScheduler.refresh(appContext)
            val intent = Intent(context, NodeService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun ensureForegroundNotification(context: Context, force: Boolean = false): Boolean {
            val appContext = context.applicationContext
            if (!NodeKeepAlivePrefs.isDesiredRunning(appContext)) return false
            if (force) {
                NormalNotificationBehaviorPrefs.clearManuallyHidden(appContext)
            } else if (NormalNotificationBehaviorPrefs.shouldSuppressNotification(appContext)) {
                return false
            }
            val intent = Intent(appContext, NodeService::class.java).apply {
                action = ACTION_ENSURE_FOREGROUND
                putExtra(EXTRA_FORCE_FOREGROUND, force)
            }
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
                true
            }.onFailure {
                AppDiagnosticLogger.w(
                    appContext,
                    TAG,
                    "重新挂接普通模式前台通知失败：${it.message}",
                    it
                )
            }.getOrDefault(false)
        }

        fun refreshForegroundNotification(context: Context): Boolean {
            val appContext = context.applicationContext
            if (!NodeKeepAlivePrefs.isDesiredRunning(appContext)) return false
            val intent = Intent(appContext, NodeService::class.java).apply {
                action = ACTION_REFRESH_NOTIFICATION
            }
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
                true
            }.onFailure {
                AppDiagnosticLogger.w(
                    appContext,
                    TAG,
                    "刷新普通模式前台通知失败：${it.message}",
                    it
                )
            }.getOrDefault(false)
        }

        fun isForegroundNotificationActive(context: Context): Boolean {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return false
            return runCatching {
                manager.activeNotifications.any { notification ->
                    notification.id == NOTIFICATION_ID &&
                        (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                            notification.notification.channelId == CHANNEL_ID)
                }
            }.getOrDefault(false)
        }

        fun canDisplayForegroundNotification(context: Context): Boolean {
            if (!NodeKeepAlivePrefs.hasPostNotificationsPermission(context)) return false
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return false
            return runCatching {
                if (!manager.areNotificationsEnabled()) return@runCatching false
                Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    manager.getNotificationChannel(CHANNEL_ID)?.importance !=
                        NotificationManager.IMPORTANCE_NONE
            }.getOrDefault(false)
        }

        /** 接口信息开关切换后重建通知，让显示内容立即生效。 */
        fun applyNotificationDisplayPreference(context: Context): Boolean {
            val appContext = context.applicationContext
            ServiceNotificationChannels.ensureChannels(
                context = appContext,
                channelName = appContext.getString(R.string.notification_channel_name),
                channelDescription = appContext.getString(R.string.notification_channel_desc)
            )
            return refreshForegroundNotification(appContext)
        }

        private fun claimUnexpectedForegroundReattach(): Boolean {
            val now = SystemClock.elapsedRealtime()
            val previous = lastUnexpectedForegroundReattachAtMs.getAndSet(now)
            return previous <= 0L || now - previous >= UNEXPECTED_FOREGROUND_REATTACH_MIN_INTERVAL_MS
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
            // 慢机型的端口就绪时间可能远超确认窗口：进程刚拉起时绝不判僵死，避免误杀正常启动。
            if (!hasProcessExceededMinUptime(appContext)) {
                AppDiagnosticLogger.i(
                    appContext,
                    TAG,
                    ":node 进程启动未满 ${STALE_PROCESS_MIN_UPTIME_MS}ms，跳过僵死回收"
                )
                return true
            }
            if (!confirmStaleProcess(appContext, port, confirmTimeoutMs)) return true
            if (!killProcessIfRunning(appContext) && isProcessRunning(appContext)) return false
            return waitForProcessStop(appContext, port, stopTimeoutMs)
        }

        /** 进程不存在或读取失败时返回 true（维持原行为），仅在确认进程刚启动不久时返回 false。 */
        private fun hasProcessExceededMinUptime(context: Context): Boolean {
            val pid = findProcessPid(context) ?: return true
            val startedElapsedMs = readProcessStartedElapsedMs(pid) ?: return true
            return SystemClock.elapsedRealtime() - startedElapsedMs >= STALE_PROCESS_MIN_UPTIME_MS
        }

        private fun readProcessStartedElapsedMs(pid: Int): Long? {
            return runCatching {
                val stat = java.io.File("/proc/$pid/stat").readText()
                // comm 字段可含空格与括号，取最后一个 ')' 之后的内容；其后首个字段是 state（第 3 列）。
                val fields = stat.substringAfterLast(')').trim().split(' ')
                // starttime 是第 22 列，相对 state（第 3 列）的索引为 22 - 3 = 19。
                val startTimeTicks = fields.getOrNull(19)?.toLongOrNull() ?: return null
                val clockTicksPerSecond = runCatching {
                    android.system.Os.sysconf(android.system.OsConstants._SC_CLK_TCK)
                }.getOrDefault(100L)
                SystemClock.elapsedRealtime() - startTimeTicks * 1000L / clockTicksPerSecond
            }.getOrNull()
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
    private var runtimeWakeLock: PowerManager.WakeLock? = null
    @Volatile
    private var foregroundStarted = false
    @Volatile
    private var serviceStopRequested = false
    @Volatile
    private var displayedNotificationEndpoint: String? = null
    private var notificationNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var notificationEndpointRefreshJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        syncEndpointInfoNetworkMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val explicitStart = intent?.getBooleanExtra(EXTRA_EXPLICIT_START, false) == true
        when (action) {
            ACTION_STOP -> {
                NodeKeepAlivePrefs.setDesiredRunning(applicationContext, false)
                NormalNotificationBehaviorPrefs.clearManuallyHidden(applicationContext)
                SystemHeartbeatScheduler.refresh(applicationContext)
                stopNode()
                return START_NOT_STICKY
            }
            ACTION_COPY_LAN_ADDRESS -> {
                copyLanAddressToClipboard()
                return START_NOT_STICKY
            }
            ACTION_NOTIFICATION_DISMISSED -> {
                return handleNotificationDismissed(startId)
            }
            ACTION_REFRESH_NOTIFICATION -> {
                return handleNotificationRefresh(startId)
            }
            ACTION_START -> {
                // 明确启动以及新建的服务会开启一个新的通知会话；重复的开机广播
                // 不应覆盖用户刚刚选择的“尊重关闭”。
                if (explicitStart || !foregroundStarted) {
                    NormalNotificationBehaviorPrefs.clearManuallyHidden(applicationContext)
                }
            }
            ACTION_ENSURE_FOREGROUND -> {
                val forceForeground = intent.getBooleanExtra(EXTRA_FORCE_FOREGROUND, false)
                if (forceForeground) {
                    NormalNotificationBehaviorPrefs.clearManuallyHidden(applicationContext)
                } else if (NormalNotificationBehaviorPrefs.shouldSuppressNotification(this)) {
                    AppDiagnosticLogger.i(this, TAG, "按设置保留用户手动关闭的服务通知")
                    if (!foregroundStarted) stopSelf(startId)
                    return START_STICKY
                }
            }
            null -> {
                // START_STICKY 重建会传入 null intent，仅在用户期望运行时恢复。
                if (!NodeKeepAlivePrefs.isDesiredRunning(this)) {
                    serviceStopRequested = true
                    if (tryEnterForeground("服务已停止", startId)) {
                        stopForegroundAndSelf(startId)
                    }
                    return START_NOT_STICKY
                }
                if (!foregroundStarted) {
                    // null intent 表示 Service 被系统重新创建，视为新的服务会话。
                    NormalNotificationBehaviorPrefs.clearManuallyHidden(applicationContext)
                }
            }
            else -> return START_NOT_STICKY
        }

        // ACTION_START 来自 startForegroundService()。先进入前台，再做端口探测和幂等判定，
        // 确保重复启动、开机双广播及慢设备上都不会触发系统的 5 秒前台服务异常。
        if (!tryEnterForeground(currentForegroundMessage(), startId)) {
            return START_NOT_STICKY
        }

        if (adoptReachableRuntimeIfNeeded(explicitStart)) {
            return START_STICKY
        }

        if (action == ACTION_ENSURE_FOREGROUND) {
            val hasActiveRuntime = synchronized(stateLock) {
                isRunning || isStopping || nodeThread?.isAlive == true || startupStartedAtMs > 0L
            }
            if (hasActiveRuntime) {
                AppDiagnosticLogger.i(this, TAG, "前台服务通知已确认，保留现有运行时")
                return START_STICKY
            }
            AppDiagnosticLogger.w(this, TAG, "未发现可挂接的普通模式运行时，结束通知恢复请求")
            stopForegroundAndSelf(startId)
            return START_NOT_STICKY
        }

        val shouldStart = shouldAcceptStartRequest()
        if (shouldStart) {
            synchronized(stateLock) {
                currentStartExplicit = explicitStart
            }
            publishStarting("正在准备运行环境…", explicitStart = explicitStart)
            startNode()
        } else if (synchronized(stateLock) {
                shouldStopServiceAfterRejectedStart(
                    serviceStopRequested = serviceStopRequested,
                    running = isRunning,
                    stopping = isStopping,
                    threadAlive = nodeThread?.isAlive == true
                )
            }
        ) {
            stopForegroundAndSelf(startId)
        } else {
            AppDiagnosticLogger.i(this, TAG, "忽略重复启动请求，保留现有前台服务")
        }
        return START_STICKY
    }

    private fun handleNotificationDismissed(startId: Int): Int {
        when (NormalNotificationBehaviorPrefs.get(this)) {
            NormalNotificationBehavior.ForegroundRestore -> {
                NormalNotificationBehaviorPrefs.setManuallyHidden(this, hidden = true)
                AppDiagnosticLogger.i(this, TAG, "用户手动关闭服务通知，等待进入后台时恢复")
            }

            NormalNotificationBehavior.ImmediateRestore -> {
                NormalNotificationBehaviorPrefs.clearManuallyHidden(this)
                if (tryEnterForeground(currentForegroundMessage(), startId)) {
                    AppDiagnosticLogger.i(this, TAG, "用户手动关闭服务通知，已按设置立即恢复")
                }
            }

            NormalNotificationBehavior.RespectDismissal -> {
                NormalNotificationBehaviorPrefs.setManuallyHidden(this, hidden = true)
                cancelForegroundNotification()
                AppDiagnosticLogger.i(this, TAG, "用户手动关闭服务通知，按设置保持隐藏")
            }
        }
        return START_STICKY
    }

    private fun handleNotificationRefresh(startId: Int): Int {
        syncEndpointInfoNetworkMonitor()
        if (NormalNotificationBehaviorPrefs.shouldSuppressNotification(this)) {
            cancelForegroundNotification()
            return START_STICKY
        }
        if (!tryEnterForeground(currentForegroundMessage(), startId)) {
            return START_NOT_STICKY
        }
        val hasActiveRuntime = synchronized(stateLock) {
            isRunning || isStopping || nodeThread?.isAlive == true || startupStartedAtMs > 0L
        }
        if (!hasActiveRuntime) {
            stopForegroundAndSelf(startId)
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun currentForegroundMessage(): String {
        return synchronized(stateLock) {
            when {
                isStopping -> "正在停止服务…"
                isRunning && nodeThread?.isAlive == true -> "服务运行中"
                isRunning -> "正在启动服务…"
                else -> "正在同步服务状态…"
            }
        }
    }

    private fun tryEnterForeground(message: String, startId: Int): Boolean {
        return try {
            if (foregroundStarted && NormalNotificationBehaviorPrefs.shouldSuppressNotification(this)) {
                cancelForegroundNotification()
                return true
            }
            startServiceInForeground(message)
            true
        } catch (throwable: Throwable) {
            val detail = buildErrorMessage(throwable)
            AppDiagnosticLogger.e(this, TAG, "前台服务通知创建失败：$detail", throwable)
            synchronized(stateLock) {
                serviceStopRequested = true
            }
            broadcastStatus(
                STATUS_ERROR,
                message = "无法启动前台服务：$detail",
                error = "无法启动前台服务：$detail"
            )
            // Node 与 Service 同处 :node 进程。无法建立前台服务时必须结束整个进程，
            // 否则会再次形成“API 仍可用，但通知和前台服务已消失”的脱管状态。
            stopSelf(startId)
            android.os.Process.killProcess(android.os.Process.myPid())
            false
        }
    }

    private fun adoptReachableRuntimeIfNeeded(explicitStart: Boolean): Boolean {
        val shouldProbe = synchronized(stateLock) {
            !serviceStopRequested && !isRunning && !isStopping && nodeThread == null
        }
        if (!shouldProbe) return false

        val ownedPort = resolveCandidatePorts().firstOrNull { port ->
            port in 1..65535 && isPortOpen(port) && isRuntimeOwnedByApp(port)
        } ?: return false

        val adopted = synchronized(stateLock) {
            if (serviceStopRequested || isRunning || isStopping || nodeThread != null) {
                false
            } else {
                isRunning = true
                isStopping = false
                startupStartedAtMs = 0L
                currentStartExplicit = explicitStart
                runningPublishedGeneration = runtimeGeneration.get()
                true
            }
        }
        if (!adopted) return false

        syncRuntimeWakeLock()
        updateNotification("服务运行中")
        broadcastStatus(
            STATUS_RUNNING,
            message = "已重新挂接前台服务，接口保持可用",
            explicitStart = explicitStart
        )
        AppDiagnosticLogger.i(this, TAG, "已接管端口 $ownedPort 上的现有运行时并恢复前台通知")
        return true
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
        foregroundStarted = true
    }

    private fun syncRuntimeWakeLock() {
        val serviceRunning = synchronized(stateLock) { isRunning && !isStopping }
        val shouldHold = NodeKeepAlivePrefs.shouldHoldRuntimeWakeLock(
            isCompatModeDevice = DeviceCompatMode.shouldUseCompatMode(applicationContext),
            isRootMode = NodeKeepAlivePrefs.isRootMode(applicationContext),
            serviceRunning = serviceRunning
        )
        if (shouldHold) {
            acquireRuntimeWakeLock()
        } else {
            releaseRuntimeWakeLock()
        }
    }

    private fun acquireRuntimeWakeLock() {
        synchronized(stateLock) {
            if (runtimeWakeLock?.isHeld == true) return
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            val wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "$packageName:$TAG:runtime"
            ).apply {
                setReferenceCounted(false)
            }
            try {
                wakeLock.acquire(RUNTIME_WAKE_LOCK_TIMEOUT_MS)
                runtimeWakeLock = wakeLock
            } catch (t: Throwable) {
                runtimeWakeLock = null
                AppDiagnosticLogger.w(this, TAG, "启用 TV 兼容模式 CPU 唤醒锁失败：${t.message}")
                return
            }
            AppDiagnosticLogger.i(this, TAG, "TV 兼容模式运行中，已启用 CPU 唤醒锁")
        }
    }

    private fun releaseRuntimeWakeLock() {
        val wakeLock = synchronized(stateLock) {
            runtimeWakeLock.also { runtimeWakeLock = null }
        } ?: return
        runCatching {
            if (wakeLock.isHeld) {
                wakeLock.release()
                AppDiagnosticLogger.i(this, TAG, "已释放 TV 兼容模式 CPU 唤醒锁")
            }
        }.onFailure {
            AppDiagnosticLogger.w(this, TAG, "释放 TV 兼容模式 CPU 唤醒锁失败：${it.message}")
        }
    }

    override fun onTimeout(startId: Int) {
        AppDiagnosticLogger.e(this, TAG, "普通模式前台服务触发系统超时，结束 :node 进程")
        synchronized(stateLock) {
            isRunning = false
            isStopping = false
            runningPublishedGeneration = -1L
            startupStartedAtMs = 0L
            currentStartExplicit = false
            nodeThread = null
            serviceStopRequested = true
        }
        releaseRuntimeWakeLock()
        broadcastStatus(
            STATUS_ERROR,
            message = "前台服务被系统超时限制，运行时已结束",
            error = "前台服务被系统超时限制，运行时已结束"
        )
        stopForegroundAndSelf()
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
        syncRuntimeWakeLock()
        // 真正开始新的运行代次时，手动关闭状态不再沿用到新服务会话。
        NormalNotificationBehaviorPrefs.clearManuallyHidden(applicationContext)

        StartupFailureStore.clearNormal(this)

        scope.launch {
            try {
                publishStarting("正在准备运行环境…", explicitStart = explicitStart)
                val projectDir = awaitPreparedProjectDir(
                    generation = generation,
                    startupStartedAtMs = startupIssuedAtMs
                ) ?: return@launch
                RuntimeIdentityStore.exportToEnv(applicationContext)
                // Node 24 运行时要求：启动前显式提供 TMPDIR/HOME，
                // 并可选启用 V8 编译缓存加快二次启动。
                NodeRuntimeEnv.install(
                    tmpDir = File(applicationContext.cacheDir, "tmp"),
                    homeDir = applicationContext.filesDir,
                    compileCacheDir = File(applicationContext.cacheDir, "node-compile-cache")
                )
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
                                    RuntimeDependencyHealthChecker.recordModuleNotFoundIssue(
                                        context = this@NodeService,
                                        projectDir = projectDir,
                                        message = startupFailure?.detail ?: msg
                                    )
                                    serviceStopRequested = true
                                    broadcastStatus(STATUS_ERROR, message = msg, error = msg)
                                    stopForegroundAndSelf()
                                }

                                NodeRuntimeExitAction.ReportStopped -> {
                                    serviceStopRequested = true
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
            } catch (cancelled: CancellationException) {
                AppDiagnosticLogger.i(
                    this@NodeService,
                    TAG,
                    "启动流程因服务停止/销毁而取消，generation=$generation"
                )
                throw cancelled
            } catch (t: Throwable) {
                if (!serviceStopRequested && runtimeGeneration.get() == generation) {
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
        var prepareJob: Job? = null
        try {
            prepareJob = scope.launch {
                val prepared: Result<java.io.File> = try {
                    Result.success(
                        run {
                            ensureGenerationCurrent(generation)
                            publishStartingForGeneration(generation, "正在检查运行环境…")
                            val projectDir = NodeProjectManager.ensureProjectExtracted(this@NodeService)
                            NodeProjectManager.migrateAllCoreLayouts(projectDir)
                            ensureGenerationCurrent(generation)
                            publishStartingForGeneration(generation, "正在同步启动配置…")
                            // 从主进程已写入的 .env 中读取 variant，避免 :node 进程 SharedPreferences 跨进程不一致覆盖。
                            val envVariant = runCatching {
                                java.io.File(projectDir, "config/.env").takeIf { it.exists() }
                                    ?.readText(Charsets.UTF_8)
                                    ?.let { DotEnvCodec.parse(it)["DANMU_API_VARIANT"] }
                                    ?.trim()
                            }.getOrNull()
                            NodeProjectManager.writeRuntimeEnv(
                                context = this@NodeService,
                                targetProjectDir = projectDir,
                                preferredVariantKey = envVariant
                            )
                            ensureGenerationCurrent(generation)
                            when (val health = RuntimeDependencyHealthChecker.inspectSelectedCore(
                                context = this@NodeService,
                                projectDir = projectDir
                            )) {
                                RuntimeDependencyHealthChecker.Status.Ready -> Unit
                                RuntimeDependencyHealthChecker.Status.CoreUnavailable -> {
                                    throw IllegalStateException("当前核心未安装或文件不完整")
                                }
                                is RuntimeDependencyHealthChecker.Status.Missing -> {
                                    throw RuntimeDependenciesMissingException(
                                        variant = health.variant,
                                        missingDependencies = health.dependencies
                                    )
                                }
                            }
                            projectDir
                        }
                    )
                } catch (stale: StartupGenerationStaleException) {
                    AppDiagnosticLogger.i(
                        this@NodeService,
                        TAG,
                        "放弃过期的启动准备工作：${stale.message}"
                    )
                    return@launch
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (throwable: Throwable) {
                    Result.failure(throwable)
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
        } finally {
            // 超时/失败后终止仍在进行的准备工作，
            // 避免其用过期消息覆盖通知/UI 或与新启动流程并发写 config/.env。
            prepareJob?.cancel()
        }
    }

    private class StartupGenerationStaleException(message: String) : IllegalStateException(message)

    private fun ensureGenerationCurrent(generation: Long) {
        if (runtimeGeneration.get() != generation) {
            throw StartupGenerationStaleException("启动流程 generation=$generation 已过期")
        }
    }

    private fun publishStartingForGeneration(generation: Long, message: String) {
        if (runtimeGeneration.get() == generation) {
            publishStarting(message)
        }
    }

    private fun remainingStartupBudgetMs(
        startupStartedAtMs: Long,
        profile: NormalModeRuntimeProfile = runtimeProfile()
    ): Long {
        val elapsedMs = (System.currentTimeMillis() - startupStartedAtMs).coerceAtLeast(0L)
        return (profile.startupTotalTimeoutMs - elapsedMs).coerceAtLeast(0L)
    }

    private fun handleStartupFailure(generation: Long, message: String, throwable: Throwable? = null) {
        if (serviceStopRequested || throwable is CancellationException || runtimeGeneration.get() != generation) {
            return
        }
        AppDiagnosticLogger.e(this, TAG, "Failed to start node: $message", throwable)
        if (isNodeThreadAlive()) {
            broadcastStatus(
                STATUS_ERROR,
                message = "启动流程失败，正在回收残留运行时：$message",
                error = message
            )
            stopNode()
            return
        }
        synchronized(stateLock) {
            isRunning = false
            isStopping = false
            runningPublishedGeneration = -1L
            startupStartedAtMs = 0L
            if (nodeThread?.isAlive != true) {
                nodeThread = null
            }
            serviceStopRequested = true
        }
        updateNotification("启动失败：$message")
        broadcastStatus(STATUS_ERROR, message = message, error = message)
        stopForegroundAndSelf()
    }

    private suspend fun handleStartupTimeout(generation: Long, message: String) {
        if (serviceStopRequested || runtimeGeneration.get() != generation) return
        val reachable = resolveCandidatePorts().any { it in 1..65535 && isPortOpen(it) }
        if (reachable && isNodeThreadAlive()) {
            AppDiagnosticLogger.w(this, TAG, "启动超时边界检测到端口已就绪，保留前台服务")
            publishRunningIfNeeded(generation)
            return
        }
        val startupFailure = StartupFailureStore.readNormal(this)
        val resolvedMessage = startupFailure?.userMessage() ?: message
        AppDiagnosticLogger.w(
            this,
            TAG,
            startupFailure?.detail?.takeIf { it.isNotBlank() } ?: resolvedMessage
        )
        val threadAlive = isNodeThreadAlive()
        if (threadAlive) {
            broadcastStatus(
                STATUS_ERROR,
                message = "$resolvedMessage，正在回收残留运行时",
                error = resolvedMessage
            )
            AppDiagnosticLogger.w(this, TAG, "启动超时但 Node 线程仍存活，保持前台通知直至进程完成回收")
            stopNode()
            return
        }
        synchronized(stateLock) {
            if (runtimeGeneration.get() != generation) return
            isRunning = false
            isStopping = false
            runningPublishedGeneration = -1L
            startupStartedAtMs = 0L
            currentStartExplicit = false
            nodeThread = null
            serviceStopRequested = true
        }
        updateNotification("启动失败：$resolvedMessage")
        broadcastStatus(STATUS_ERROR, message = resolvedMessage, error = resolvedMessage)
        AppDiagnosticLogger.w(
            this,
            TAG,
            "普通模式启动超时且 Node 线程已退出，停止前台服务"
        )
        stopForegroundAndSelf()
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
            serviceStopRequested = true
            broadcastStatus(STATUS_STOPPED, message = "服务已停止")
            delay(350)
            releaseRuntimeWakeLock()
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
            serviceStopRequested = true
            isRunning = false
            isStopping = false
            runningPublishedGeneration = -1L
            startupStartedAtMs = 0L
            currentStartExplicit = false
            if (nodeThread?.isAlive != true) {
                nodeThread = null
            }
        }
        NormalNotificationBehaviorPrefs.clearManuallyHidden(applicationContext)
        // 主动广播停止，避免仓库层仅靠兜底超时判断导致“已停却报错”。
        broadcastStatus(STATUS_STOPPED, message = "服务已停止")
        stopForegroundAndSelf()
    }

    private fun stopForegroundAndSelf(startId: Int? = null) {
        synchronized(stateLock) {
            serviceStopRequested = true
            isStopping = false
        }
        foregroundStarted = false
        releaseRuntimeWakeLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        if (startId != null) {
            stopSelf(startId)
        } else {
            stopSelf()
        }
    }

    private fun isRuntimeOwnedByApp(port: Int): Boolean {
        if (port !in 1..65535) return false
        val expectedIdentity = RuntimeIdentityStore.ensureInstanceId(applicationContext).trim()
        val expectedHome = NodeProjectManager.projectDir(this).absolutePath
        val body = readRuntimeHealthBody(port) ?: return false
        return isRuntimeOwnershipOwned(
            determineRuntimeOwnershipFromHealth(
                body = body,
                expectedIdentity = expectedIdentity,
                expectedHome = expectedHome
            )
        )
    }

    private fun readRuntimeHealthBody(port: Int): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("http://127.0.0.1:$port/__health").openConnection() as HttpURLConnection).apply {
                connectTimeout = 450
                readTimeout = 700
                requestMethod = "GET"
            }
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
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
        if (serviceStopRequested) return false
        val staleTimeoutMs = runtimeProfile().startupStaleTimeoutMs
        val anyPortOpen = resolveCandidatePorts().any { it in 1..65535 && isPortOpen(it) }
        synchronized(stateLock) {
            if (serviceStopRequested) return false
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
            DotEnvCodec.parse(envFile.readText(Charsets.UTF_8))["DANMU_API_PORT"]
                ?.trim()
                ?.toIntOrNull() ?: 0
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
        ServiceNotificationChannels.ensureChannels(
            context = this,
            channelName = getString(R.string.notification_channel_name),
            channelDescription = getString(R.string.notification_channel_desc)
        )
    }

    private fun buildNotification(text: String): Notification {
        val pendingFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            pendingFlags
        )
        val stopIntent = Intent(this, NodeService::class.java).apply {
            action = ACTION_STOP
            setPackage(packageName)
        }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, pendingFlags)
        val restartIntent = Intent(this, NotificationRuntimeActionService::class.java).apply {
            action = ACTION_RESTART
            setPackage(packageName)
        }
        val restartPendingIntent = PendingIntent.getService(this, 4, restartIntent, pendingFlags)
        val copyLanIntent = Intent(this, NodeService::class.java).apply {
            action = ACTION_COPY_LAN_ADDRESS
            setPackage(packageName)
        }
        val copyLanPendingIntent = PendingIntent.getService(this, 2, copyLanIntent, pendingFlags)
        val dismissIntent = Intent(this, NodeService::class.java).apply {
            action = ACTION_NOTIFICATION_DISMISSED
            setPackage(packageName)
        }
        val dismissPendingIntent = PendingIntent.getService(this, 3, dismissIntent, pendingFlags)

        if (NotificationDisplayPrefs.isEndpointInfoEnabled(this)) {
            val endpointText = notificationEndpointText()
            displayedNotificationEndpoint = endpointText
            return RuntimeSceneNotification.build(
                context = this,
                title = getString(R.string.app_name),
                subtitle = text,
                infoTitle = getString(R.string.notification_scene_info_title),
                infoText = endpointText,
                contentIntent = pendingIntent,
                deleteIntent = dismissPendingIntent,
                actions = listOf(
                    RuntimeSceneNotification.Action(
                        iconResId = android.R.drawable.ic_menu_close_clear_cancel,
                        title = getString(R.string.notification_action_stop),
                        intent = stopPendingIntent
                    ),
                    RuntimeSceneNotification.Action(
                        iconResId = android.R.drawable.ic_popup_sync,
                        title = getString(R.string.notification_action_restart),
                        intent = restartPendingIntent
                    ),
                    RuntimeSceneNotification.Action(
                        iconResId = android.R.drawable.ic_menu_share,
                        title = getString(R.string.notification_action_copy_address),
                        intent = copyLanPendingIntent
                    )
                )
            )
        }

        displayedNotificationEndpoint = null
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_action_stop),
                stopPendingIntent
            )
            .addAction(
                android.R.drawable.ic_popup_sync,
                getString(R.string.notification_action_restart),
                restartPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_share,
                getString(R.string.notification_action_copy_address),
                copyLanPendingIntent
            )
            .setOngoing(true)
            .build()
    }

    private fun notificationEndpointText(): String {
        val endpoint = resolveLanUrl()
            .substringAfter("://", "")
            .substringBefore('/')
            .trim()
        if (endpoint.isNotBlank() && !endpoint.startsWith("0.0.0.0:")) {
            return endpoint
        }
        val port = readPortFromEnvFile().takeIf { it in 1..65535 } ?: 9321
        return "端口 $port"
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (NormalNotificationBehaviorPrefs.shouldSuppressNotification(this)) {
            nm.cancel(NOTIFICATION_ID)
            return
        }
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    /**
     * 接口地址只依赖本机网络地址，使用 ConnectivityManager 的系统事件刷新，不发起网络请求。
     * 300ms 防抖只用于合并 Wi-Fi/移动网络切换时连续到达的多个系统回调。
     */
    private fun syncEndpointInfoNetworkMonitor() {
        if (NotificationDisplayPrefs.isEndpointInfoEnabled(applicationContext)) {
            startEndpointInfoNetworkMonitor()
        } else {
            stopEndpointInfoNetworkMonitor()
        }
    }

    private fun startEndpointInfoNetworkMonitor() {
        if (notificationNetworkCallback != null) return
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scheduleEndpointInfoRefresh()
            }

            override fun onLost(network: Network) {
                scheduleEndpointInfoRefresh()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                scheduleEndpointInfoRefresh()
            }

            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: LinkProperties
            ) {
                scheduleEndpointInfoRefresh()
            }
        }

        val registered = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(callback)
            } else {
                connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
            }
            true
        }.onFailure {
            AppDiagnosticLogger.w(
                applicationContext,
                TAG,
                "注册接口信息网络监听失败：${it.message}",
                it
            )
        }.getOrDefault(false)

        if (registered) {
            notificationNetworkCallback = callback
            scheduleEndpointInfoRefresh()
        }
    }

    private fun stopEndpointInfoNetworkMonitor() {
        notificationEndpointRefreshJob?.cancel()
        notificationEndpointRefreshJob = null
        displayedNotificationEndpoint = null
        val callback = notificationNetworkCallback ?: return
        notificationNetworkCallback = null
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
    }

    private fun scheduleEndpointInfoRefresh() {
        notificationEndpointRefreshJob?.cancel()
        notificationEndpointRefreshJob = scope.launch {
            delay(NOTIFICATION_ENDPOINT_REFRESH_DEBOUNCE_MS)
            val endpoint = notificationEndpointText()
            if (
                NotificationEndpointRefreshPolicy.shouldRefresh(
                    endpointInfoEnabled = NotificationDisplayPrefs.isEndpointInfoEnabled(applicationContext),
                    foregroundStarted = foregroundStarted,
                    notificationSuppressed = NormalNotificationBehaviorPrefs.shouldSuppressNotification(this@NodeService),
                    displayedEndpoint = displayedNotificationEndpoint,
                    currentEndpoint = endpoint
                )
            ) {
                updateNotification(currentForegroundMessage())
            }
        }
    }

    private fun cancelForegroundNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.cancel(NOTIFICATION_ID)
    }

    private fun copyLanAddressToClipboard() {
        scope.launch(Dispatchers.IO) {
            val lanUrl = resolveLanUrl()
            Handler(Looper.getMainLooper()).post {
                val appCtx = applicationContext
                val isValid = lanUrl.isNotBlank() && !lanUrl.contains("0.0.0.0")
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                if (clipboard != null && isValid) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("局域网地址", lanUrl))
                    // 复制内容本就含真实 token，提示直接展示完整地址，避免“看起来被截断”的困惑。
                    val displayUrl = lanUrl
                    Toast.makeText(
                        appCtx,
                        "已复制：$displayUrl",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        appCtx,
                        "未获取到局域网地址",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun resolveLanUrl(): String {
        val port = readPortFromEnvFile().takeIf { it in 1..65535 } ?: 9321
        val token = readTokenFromEnvFile()
        val lanIp = RuntimeNetworkAddressResolver.resolve(applicationContext).ipv4
        return RuntimeNetworkAddressResolver.buildHttpUrl(lanIp, port, token)
    }

    private fun readTokenFromEnvFile(): String {
        return try {
            val envFile = java.io.File(NodeProjectManager.projectDir(this), "config/.env")
            if (!envFile.exists()) return ""
            DotEnvCodec.parse(envFile.readText(Charsets.UTF_8))["TOKEN"]?.trim().orEmpty()
        } catch (e: Exception) {
            AppDiagnosticLogger.w(applicationContext, TAG, "readTokenFromEnvFile 失败: ${e.message}", e)
            ""
        }
    }

    override fun onDestroy() {
        val desiredRunning = NodeKeepAlivePrefs.isDesiredRunning(applicationContext)
        val destroySnapshot = synchronized(stateLock) {
            val unexpected = shouldReportUnexpectedNodeServiceDestroy(
                serviceStopRequested = serviceStopRequested,
                stopping = isStopping,
                desiredRunning = desiredRunning,
                running = isRunning,
                threadAlive = nodeThread?.isAlive == true,
                startupStarted = startupStartedAtMs > 0L
            )
            val threadToInterrupt = nodeThread
            serviceStopRequested = true
            nodeThread = null
            isRunning = false
            isStopping = false
            runningPublishedGeneration = -1L
            startupStartedAtMs = 0L
            currentStartExplicit = false
            unexpected to threadToInterrupt
        }
        stopEndpointInfoNetworkMonitor()
        scope.cancel()
        destroySnapshot.second?.interrupt()
        releaseRuntimeWakeLock()
        val shouldReattach = destroySnapshot.first &&
            desiredRunning &&
            claimUnexpectedForegroundReattach()
        if (destroySnapshot.first) {
            val message = if (shouldReattach) {
                "普通模式前台服务意外终止，正在重新挂接"
            } else {
                "普通模式前台服务意外终止，请重新启动服务"
            }
            AppDiagnosticLogger.e(this, TAG, message)
            broadcastStatus(STATUS_ERROR, message = message, error = message)
        }
        super.onDestroy()
        if (shouldReattach) {
            Handler(Looper.getMainLooper()).post {
                val requested = ensureForegroundNotification(applicationContext, force = true)
                AppDiagnosticLogger.i(
                    applicationContext,
                    TAG,
                    if (requested) {
                        "异常销毁后已请求重新挂接前台服务"
                    } else {
                        "异常销毁后无法重新挂接前台服务，等待用户返回应用"
                    }
                )
            }
        }
    }
}
