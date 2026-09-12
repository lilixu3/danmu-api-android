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
import com.example.danmuapiapp.BuildConfig
import com.example.danmuapiapp.R
import com.example.danmuapiapp.data.util.DeviceCompatMode
import com.example.danmuapiapp.data.util.DotEnvCodec
import com.example.danmuapiapp.data.util.PortProbe
import com.example.danmuapiapp.data.service.RuntimeIdentityStore
import com.example.danmuapiapp.domain.model.ErrorHandler
import com.example.danmuapiapp.domain.model.NormalNotificationBehavior
import kotlinx.coroutines.*
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
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
        const val EXTRA_PROCESS_STARTED_ELAPSED_MS = "process_started_elapsed_ms"
        const val EXTRA_RUNTIME_GENERATION = "runtime_generation"
        const val EXTRA_EVENT_SEQUENCE = "event_sequence"
        const val STATUS_STARTING = "starting"
        const val STATUS_RUNNING = "running"
        const val STATUS_STOPPING = "stopping"
        const val STATUS_STOPPED = "stopped"
        const val STATUS_ERROR = "error"
        const val EXTRA_ERROR = "error_message"
        val ACTION_COPY_LAN_ADDRESS: String
            get() = "$actionPrefix.COPY_LAN_ADDRESS"
        const val RUNTIME_WAKE_LOCK_TIMEOUT_MS = 6L * 60L * 60L * 1000L
        private const val STALE_PROCESS_POLL_INTERVAL_MS = 180L
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

        fun killProcessIfRunning(context: Context, expectedPid: Int? = null): Boolean {
            val pid = findProcessPid(context) ?: return false
            if (expectedPid != null && pid != expectedPid) return false
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
            val pid = findProcessPid(appContext) ?: return true
            // 慢机型的端口就绪时间可能远超确认窗口：进程刚拉起时绝不判僵死，避免误杀正常启动。
            if (!hasProcessExceededMinUptime(pid)) {
                AppDiagnosticLogger.i(
                    appContext,
                    TAG,
                    ":node 进程启动未满 ${STALE_PROCESS_MIN_UPTIME_MS}ms，跳过僵死回收"
                )
                return true
            }
            if (!confirmStaleProcess(appContext, port, confirmTimeoutMs, expectedPid = pid)) return true
            // 确认窗口内可能已有系统恢复的新进程；只能回收本轮观察的 PID，且再次尊重新进程保护期。
            if (findProcessPid(appContext) != pid || !hasProcessExceededMinUptime(pid)) return true
            if (!killProcessIfRunning(appContext, expectedPid = pid) && findProcessPid(appContext) == pid) return false
            return waitForProcessStop(appContext, port, stopTimeoutMs)
        }

        /** 进程不存在或读取失败时返回 true（维持原行为），仅在确认进程刚启动不久时返回 false。 */
        private fun hasProcessExceededMinUptime(pid: Int): Boolean {
            val startedElapsedMs = readProcessStartedElapsedMs(pid) ?: return true
            return hasNodeProcessExceededMinUptime(
                nowElapsedMs = SystemClock.elapsedRealtime(),
                startedElapsedMs = startedElapsedMs,
                minUptimeMs = STALE_PROCESS_MIN_UPTIME_MS
            )
        }

        private fun readProcessStartedElapsedMs(pid: Int): Long? {
            return runCatching {
                val stat = java.io.File("/proc/$pid/stat").readText()
                val clockTicksPerSecond = runCatching {
                    android.system.Os.sysconf(android.system.OsConstants._SC_CLK_TCK)
                }.getOrDefault(100L)
                parseNodeProcessStartedElapsedMs(stat, clockTicksPerSecond)
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

        private fun confirmStaleProcess(context: Context, port: Int, timeoutMs: Long, expectedPid: Int): Boolean {
            val deadline = SystemClock.elapsedRealtime() + timeoutMs.coerceAtLeast(0L)
            while (SystemClock.elapsedRealtime() < deadline) {
                if (findProcessPid(context) != expectedPid) return false
                if (port in 1..65535 && isPortReachable(port)) return false
                sleepQuietly(STALE_PROCESS_POLL_INTERVAL_MS)
            }
            return findProcessPid(context) == expectedPid &&
                (port !in 1..65535 || !isPortReachable(port))
        }

        private fun waitForProcessStop(context: Context, port: Int, timeoutMs: Long): Boolean {
            val deadline = SystemClock.elapsedRealtime() + timeoutMs.coerceAtLeast(0L)
            while (SystemClock.elapsedRealtime() < deadline) {
                if (!isProcessRunning(context) && (port !in 1..65535 || !isPortReachable(port))) {
                    return true
                }
                sleepQuietly(140L)
            }
            return !isProcessRunning(context) &&
                (port !in 1..65535 || !isPortReachable(port))
        }

        private fun isPortReachable(port: Int): Boolean {
            return PortProbe.isOpen(port = port)
        }

        private fun sleepQuietly(delayMs: Long) {
            runCatching { Thread.sleep(delayMs.coerceAtLeast(0L)) }
        }
    }

    // 这里只管理 Service 自身的通知/剪贴板任务，原生运行时由进程级控制器持有。
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val stateLock = Any()
    private lateinit var runtime: NodeRuntimeController
    private val taskRemovalStartRecovery = NodeTaskRemovalStartRecovery()
    private val runtimeHost = object : NodeRuntimeController.Host {
        override fun onRuntimeMessage(message: String) {
            if (serviceStopRequested) return
            updateNotification(message)
            syncRuntimeWakeLock()
        }

        override fun onRuntimeStopped() {
            stopForegroundAndSelf()
        }
    }
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
        runtime = NodeRuntimeController.get(applicationContext)
        runtime.attach(runtimeHost)
        runtime.logLifecycle("Service 创建 host=${System.identityHashCode(this)}")
        createNotificationChannel()
        syncEndpointInfoNetworkMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        taskRemovalStartRecovery.onStartCommandReceived()
        val action = intent?.action
        val explicitStart = intent?.getBooleanExtra(EXTRA_EXPLICIT_START, false) == true
        runtime.logLifecycle(
            "Service 收到命令 action=${action?.substringAfterLast('.') ?: "STICKY(null)"} " +
                "host=${System.identityHashCode(this)} startId=$startId flags=$flags explicit=$explicitStart"
        )
        when (action) {
            ACTION_STOP -> {
                NodeKeepAlivePrefs.setDesiredRunning(applicationContext, false)
                NormalNotificationBehaviorPrefs.clearManuallyHidden(applicationContext)
                SystemHeartbeatScheduler.refresh(applicationContext)
                runtime.stopNode()
                return START_NOT_STICKY
            }
            ACTION_COPY_LAN_ADDRESS -> {
                copyLanAddressToClipboard()
                return if (shouldPreserveNodeServiceSticky(
                        desiredRunning = NodeKeepAlivePrefs.isDesiredRunning(this),
                        stopRequested = serviceStopRequested
                    )
                ) START_STICKY else START_NOT_STICKY
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
                    if (!foregroundStarted) {
                        serviceStopRequested = true
                        stopSelf(startId)
                    }
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

        // 用户的停止指令优先于之前已排队的自动启动，不根据端口/健康探测做决定。
        if (!NodeKeepAlivePrefs.isDesiredRunning(this)) {
            serviceStopRequested = true
            if (tryEnterForeground("服务已停止", startId)) stopForegroundAndSelf(startId)
            return START_NOT_STICKY
        }
        serviceStopRequested = false
        // ACTION_START 来自 startForegroundService()。先进入前台，再把原有接管检查交给 IO 线程，
        // 确保重复启动、开机双广播及慢设备上都不会触发系统的 5 秒前台服务异常。
        if (!tryEnterForeground(currentForegroundMessage(), startId)) {
            return START_NOT_STICKY
        }

        runtime.requestStart(
            explicitStart = explicitStart,
            notificationOnly = action == ACTION_ENSURE_FOREGROUND
        )
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
        val hasActiveRuntime = runtime.hasActiveRuntime()
        if (!hasActiveRuntime) {
            stopForegroundAndSelf(startId)
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun currentForegroundMessage(): String = runtime.currentForegroundMessage()

    private fun tryEnterForeground(message: String, startId: Int?): Boolean {
        return try {
            if (foregroundStarted && NormalNotificationBehaviorPrefs.shouldSuppressNotification(this)) {
                cancelForegroundNotification()
                return true
            }
            startServiceInForeground(message)
            syncRuntimeWakeLock()
            true
        } catch (throwable: Throwable) {
            val detail = buildErrorMessage(throwable)
            AppDiagnosticLogger.e(this, TAG, "前台服务通知创建失败：$detail", throwable)
            serviceStopRequested = true
            runtime.reportForegroundFailure("无法启动前台服务：$detail")
            // Node 与 Service 同处 :node 进程。无法建立前台服务时必须结束整个进程，
            // 否则会再次形成“API 仍可用，但通知和前台服务已消失”的脱管状态。
            if (startId != null) stopSelf(startId) else stopSelf()
            android.os.Process.killProcess(android.os.Process.myPid())
            false
        }
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
        val serviceRunning = runtime.shouldHoldWakeLock()
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
        serviceStopRequested = true
        releaseRuntimeWakeLock()
        runtime.reportForegroundFailure("前台服务被系统超时限制，运行时已结束")
        stopForegroundAndSelf()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        onTimeout(startId)
    }

    private fun stopForegroundAndSelf(startId: Int? = null) {
        serviceStopRequested = true
        foregroundStarted = false
        releaseRuntimeWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (startId != null) stopSelf(startId) else stopSelf()
    }

    private fun readPortFromEnvFile(): Int {
        return runCatching {
            val envFile = File(NodeProjectManager.projectDir(this), "config/.env")
            DotEnvCodec.parse(envFile.readText(Charsets.UTF_8))["DANMU_API_PORT"]
                ?.trim()?.toIntOrNull() ?: 0
        }.getOrDefault(0)
    }

    private fun buildErrorMessage(t: Throwable): String = ErrorHandler.buildDetailedMessage(t)

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

    override fun onTaskRemoved(rootIntent: Intent?) {
        runtime.logLifecycle("任务被移除 host=${System.identityHashCode(this)}")
        super.onTaskRemoved(rootIntent)

        // 实机现场可能只收到 onCreate -> onTaskRemoved，没有 onStartCommand(null)。
        // 此时 :node 进程虽存在，Node 却完全没启动。只补齐这一次缺失的启动投递，
        // 不巡检、不停止旧实例；已有运行时仍由 Controller 的幂等启动路径保留。
        val result = taskRemovalStartRecovery.onTaskRemoved(
            normalMode = !NodeKeepAlivePrefs.isRootMode(applicationContext),
            desiredRunning = NodeKeepAlivePrefs.isDesiredRunning(applicationContext),
            stopRequested = serviceStopRequested,
            enterForeground = {
                NormalNotificationBehaviorPrefs.clearManuallyHidden(applicationContext)
                tryEnterForeground("正在恢复服务启动…", startId = null)
            },
            requestStart = { explicitStart, notificationOnly ->
                runtime.logLifecycle("任务移除先于启动命令：补交完整 Node 启动请求")
                // 不调用 NodeService.start()，避免它把用户并发写入的停止意图改回 true。
                runtime.requestStart(explicitStart, notificationOnly)
            }
        )
        runtime.logLifecycle("任务移除启动投递处理结果=$result")
    }

    override fun onDestroy() {
        val appContext = applicationContext
        val desiredRunning = NodeKeepAlivePrefs.isDesiredRunning(appContext)
        val unexpected = runtime.isUnexpectedServiceDestroy(serviceStopRequested, desiredRunning)
        runtime.logLifecycle(
            "Service 销毁 host=${System.identityHashCode(this)} unexpected=$unexpected " +
                "hostStopRequested=$serviceStopRequested desiredRunning=$desiredRunning"
        )
        // Service 销毁不代表 JNI 已退出。解绑宿主即可，保留控制器、线程及启动/停止任务。
        runtime.detach(runtimeHost)
        serviceStopRequested = true
        stopEndpointInfoNetworkMonitor()
        scope.cancel()
        releaseRuntimeWakeLock()
        val shouldReattach = unexpected && desiredRunning && claimUnexpectedForegroundReattach()
        if (unexpected) {
            // 仅记录生命周期事件，不把“通知宿主丢失”上报为核心故障并触发回退/停服。
            AppDiagnosticLogger.w(
                appContext, TAG,
                if (shouldReattach) "前台宿主意外销毁，保留 Node 运行时并重新挂接"
                else "前台宿主意外销毁，保留 Node 运行时，等待重新挂接"
            )
        }
        super.onDestroy()
        if (shouldReattach) {
            Handler(Looper.getMainLooper()).post {
                val requested = ensureForegroundNotification(appContext, force = true)
                AppDiagnosticLogger.i(
                    appContext, TAG,
                    if (requested) "异常销毁后已请求重新挂接前台服务"
                    else "异常销毁后无法重新挂接前台服务，等待用户返回应用"
                )
            }
        }
    }
}
