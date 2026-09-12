package com.example.danmuapiapp.data.service

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.example.danmuapiapp.NodeBridge
import com.example.danmuapiapp.data.repository.determineRuntimeOwnershipFromHealth
import com.example.danmuapiapp.data.repository.isRuntimeOwnershipOwned
import com.example.danmuapiapp.data.util.DotEnvCodec
import com.example.danmuapiapp.data.util.PortProbe
import com.example.danmuapiapp.domain.model.ErrorHandler
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * :node 进程级运行时所有者。Service 重建只替换通知宿主，不取消启动工作、
 * 不丢弃 JNI 线程，也不在同一进程重复调用 node::Start。
 * 保留原有启动/停止流程，不添加运行中巡检或基于探测结果的自动重启。
 */
internal class NodeRuntimeController private constructor(private val context: Context) {
    companion object {
        private const val TAG = NodeService.TAG
        private const val STOP_SHUTDOWN_ATTEMPTS = 4
        private const val STOP_WAIT_TIMEOUT_MS = 2600L
        private const val SHUTDOWN_HTTP_TIMEOUT_MS = 450
        @Volatile private var instance: NodeRuntimeController? = null

        fun get(context: Context): NodeRuntimeController = instance ?: synchronized(this) {
            instance ?: NodeRuntimeController(context.applicationContext).also { instance = it }
        }
    }

    interface Host {
        fun onRuntimeMessage(message: String)
        fun onRuntimeStopped()
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val stateLock = Any()
    private val startRequestMutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hosts = NodeRuntimeHostRelay<Host> { mainHandler.post(it) }
    private val nativeInvocation = NodeRuntimeInvocationGate()
    private val runtimeGeneration = AtomicLong(0L)
    private val eventSequence = AtomicLong(0L)
    private val processStartedElapsedMs = android.os.Process.getStartElapsedRealtime()
    private val commands = NodeRuntimeCommandFence()
    private var nodeThread: Thread? = null
    private var isRunning = false
    private var isStopping = false
    private var runningPublishedGeneration = -1L
    private var startupStartedAtMs = 0L
    private var currentStartExplicit = false
    @Volatile private var serviceStopRequested = false
    private var startupJob: Job? = null

    fun attach(host: Host) { hosts.attach(host) }
    fun detach(host: Host) { hosts.detach(host) }

    fun hasActiveRuntime(): Boolean = synchronized(stateLock) {
        isRunning || isStopping || nodeThread?.isAlive == true || startupStartedAtMs > 0L
    }

    fun shouldHoldWakeLock(): Boolean = synchronized(stateLock) { isRunning && !isStopping }

    fun isUnexpectedServiceDestroy(stopRequested: Boolean, desiredRunning: Boolean): Boolean =
        synchronized(stateLock) {
            shouldReportUnexpectedNodeServiceDestroy(
                serviceStopRequested = stopRequested || serviceStopRequested,
                stopping = isStopping,
                desiredRunning = desiredRunning,
                running = isRunning,
                threadAlive = nodeThread?.isAlive == true,
                startupStarted = startupStartedAtMs > 0L
            )
        }

    fun currentForegroundMessage(): String = synchronized(stateLock) { runtimePhaseLocked().message }

    /** 仅记录触发路径和本地生命周期字段，便于区分“真启动”和“仅补通知”。 */
    fun logLifecycle(event: String) {
        val details = synchronized(stateLock) {
            "pid=${android.os.Process.myPid()} processStarted=$processStartedElapsedMs " +
                "generation=${runtimeGeneration.get()} phase=${runtimePhaseLocked()} " +
                "threadAlive=${nodeThread?.isAlive == true} " +
                "readyGeneration=$runningPublishedGeneration startupActive=${startupJob?.isActive == true} " +
                "nativeActive=${nativeInvocation.isActive} stopRequested=$serviceStopRequested"
        }
        AppDiagnosticLogger.i(context, TAG, "$event [$details]")
    }

    /** 前台通知已由 Service 同步建立；原有接管探测和幂等处理只在 IO 线程执行。 */
    fun requestStart(explicitStart: Boolean, notificationOnly: Boolean) {
        val epoch = synchronized(stateLock) {
            commands.recordStart()
        }
        scope.launch {
            startRequestMutex.withLock {
                try {
                    if (!canHandleStart(epoch)) return@withLock
                    if (hasActiveRuntime()) {
                        logLifecycle("保留进程级 Node 运行时，仅重新挂接前台宿主")
                        replayRuntimePhase(explicitStart)
                        return@withLock
                    }
                    synchronized(stateLock) {
                        // 通知恢复请求也可能发生在运行时已经被系统杀掉之后：
                        // 此时不能只补通知，必须回退为完整启动，否则会留下一个没有
                        // HTTP 监听端口的空 :node 进程。
                        if (!nativeInvocation.isActive) serviceStopRequested = false
                    }
                    if (adoptReachableRuntimeIfNeeded(explicitStart)) return@withLock
                    if (!canHandleStart(epoch)) return@withLock
                    if (notificationOnly) {
                        AppDiagnosticLogger.w(
                            context,
                            TAG,
                            "未发现可挂接的普通模式运行时，改为完整启动 Node"
                        )
                    }
                    if (shouldAcceptStartRequest() && canHandleStart(epoch)) {
                        synchronized(stateLock) { currentStartExplicit = explicitStart }
                        startNode(epoch)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    if (canHandleStart(epoch)) {
                        handleStartupFailure(runtimeGeneration.get(), buildErrorMessage(error), error)
                    }
                }
            }
        }
    }

    private fun canHandleStart(epoch: Long): Boolean = synchronized(stateLock) {
        commands.acceptsStart(epoch) && !isStopping && NodeKeepAlivePrefs.isDesiredRunning(context)
    }

    fun reportForegroundFailure(message: String) {
        synchronized(stateLock) {
            commands.recordStop()
            serviceStopRequested = true
        }
        startupJob?.cancel()
        broadcastStatus(NodeService.STATUS_ERROR, message = message, error = message)
    }

    private fun updateNotification(text: String) {
        val stamp = synchronized(stateLock) { commands.commandVersion to runtimeGeneration.get() }
        hosts.dispatch {
            val current = synchronized(stateLock) {
                commands.commandVersion == stamp.first && runtimeGeneration.get() == stamp.second
            }
            if (current) it.onRuntimeMessage(text)
        }
    }

    private fun stopForegroundAndSelf() {
        val version = synchronized(stateLock) {
            serviceStopRequested = true
            isStopping = false
            commands.commandVersion
        }
        hosts.dispatch {
            val current = synchronized(stateLock) {
                serviceStopRequested && commands.acceptsStopCallback(version)
            }
            if (current) it.onRuntimeStopped()
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

        updateNotification("服务运行中")
        broadcastStatus(
            NodeService.STATUS_RUNNING,
            message = "已重新挂接前台服务，接口保持可用",
            explicitStart = explicitStart
        )
        AppDiagnosticLogger.i(context, TAG, "已接管端口 $ownedPort 上的现有运行时并恢复前台通知")
        return true
    }

    private fun runtimePhaseLocked(): NodeRuntimePhase = nodeRuntimePhase(
        running = isRunning,
        stopping = isStopping,
        threadAlive = nodeThread?.isAlive == true,
        startupStarted = startupStartedAtMs > 0L,
        generation = runtimeGeneration.get(),
        readyPublishedGeneration = runningPublishedGeneration
    )

    /** 重放已有阶段，不把 Thread 存活等价为端口就绪，也不重新核验已运行实例。 */
    private fun replayRuntimePhase(explicitStart: Boolean) {
        // 快照与发布共用锁，避免等待阶段快照在真正就绪/停止之后才被发布。
        synchronized(stateLock) {
            if (serviceStopRequested || !NodeKeepAlivePrefs.isDesiredRunning(context)) return
            val phase = runtimePhaseLocked()
            when (phase) {
                NodeRuntimePhase.Stopping,
                NodeRuntimePhase.Ready -> updateNotification(phase.message)
                NodeRuntimePhase.Preparing,
                NodeRuntimePhase.WaitingForPort -> publishStarting(
                    message = phase.message,
                    generation = runtimeGeneration.get(),
                    explicitStart = explicitStart || currentStartExplicit
                )
                NodeRuntimePhase.Idle -> Unit
            }
        }
    }

    private fun runtimeProfile(): NormalModeRuntimeProfile {
        return NormalModeRuntimeProfiles.current(context)
    }

    private fun startNode(expectedEpoch: Long) {
        val generation: Long
        val startupIssuedAtMs = System.currentTimeMillis()
        val explicitStart: Boolean
        synchronized(stateLock) {
            val startingOrRunning = isRunning || nodeThread?.isAlive == true
            if (startingOrRunning || serviceStopRequested || !canHandleStart(expectedEpoch)) return
            isRunning = true
            isStopping = false
            this.startupStartedAtMs = startupIssuedAtMs
            generation = runtimeGeneration.incrementAndGet()
            runningPublishedGeneration = -1L
            explicitStart = currentStartExplicit
        }
        logLifecycle("开始新的 Node 启动流程")
        // 真正开始新的运行代次时，手动关闭状态不再沿用到新服务会话。
        NormalNotificationBehaviorPrefs.clearManuallyHidden(context)

        StartupFailureStore.clearNormal(context)

        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                publishStarting("正在准备运行环境…", generation, explicitStart)
                val projectDir = awaitPreparedProjectDir(
                    generation = generation,
                    startupStartedAtMs = startupIssuedAtMs
                ) ?: return@launch
                RuntimeIdentityStore.exportToEnv(context)
                // Node 24 运行时要求：启动前显式提供 TMPDIR/HOME，
                // 并可选启用 V8 编译缓存加快二次启动。
                NodeRuntimeEnv.install(
                    tmpDir = File(context.cacheDir, "tmp"),
                    homeDir = context.filesDir,
                    compileCacheDir = File(context.cacheDir, "node-compile-cache")
                )
                val startCanceled = synchronized(stateLock) {
                    runtimeGeneration.get() != generation || !isRunning || isStopping
                }
                if (startCanceled) {
                    AppDiagnosticLogger.i(context, TAG, "启动流程已取消，忽略后续启动 generation=$generation")
                    return@launch
                }

                val runtimeThread = Thread {
                    nativeInvocation.runClaimed {
                        var exitCode = 0
                        var crashThrowable: Throwable? = null
                        try {
                            exitCode = NodeBridge.startNodeWithArguments(
                                arrayOf("node", "${projectDir.absolutePath}/main.js")
                            )
                        } catch (t: Throwable) {
                            crashThrowable = t
                        } finally {
                            logLifecycle("Node JNI 调用已返回 exitCode=$exitCode crashed=${crashThrowable != null}")
                            handleNodeThreadExit(generation, projectDir, exitCode, crashThrowable)
                        }
                    }
                }.apply {
                    name = "NodeJS-Runtime"
                }
                synchronized(stateLock) {
                    // 注册线程与启动必须和停止命令互斥；不能在用户取消后再启动 JNI。
                    if (!canHandleStart(expectedEpoch) || serviceStopRequested || !isRunning ||
                        runtimeGeneration.get() != generation
                    ) return@launch
                    check(nativeInvocation.tryClaim()) { "禁止在同一进程重复初始化 Node/V8" }
                    try {
                        nodeThread = runtimeThread
                        publishStarting("运行环境已准备，正在启动服务…", generation, explicitStart)
                        runtimeThread.start()
                    } catch (error: Throwable) {
                        // Thread 尚未启动时没有 JNI finally 替我们归还调用权。
                        nodeThread = null
                        nativeInvocation.release()
                        throw error
                    }
                }

                // 启动慢机型上端口可能晚于首轮超时才就绪，因此超时后继续低频复检。
                launch {
                    publishStarting("正在等待服务端口就绪…", generation, explicitStart)
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

                    publishStarting("启动较慢，继续等待服务就绪…", generation, explicitStart)
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
                    context,
                    TAG,
                    "启动流程因停止命令而取消，generation=$generation"
                )
                throw cancelled
            } catch (t: Throwable) {
                if (!serviceStopRequested && runtimeGeneration.get() == generation) {
                    handleStartupFailure(generation, buildErrorMessage(t), t)
                }
            }
        }
        synchronized(stateLock) {
            if (canHandleStart(expectedEpoch) && !serviceStopRequested && isRunning &&
                runtimeGeneration.get() == generation
            ) {
                startupJob = job
                job.start()
            } else {
                job.cancel()
            }
        }
    }

    private fun handleNodeThreadExit(
        generation: Long,
        projectDir: File,
        exitCode: Int,
        crashThrowable: Throwable?
    ) {
        val exitAction = synchronized(stateLock) {
            if (runtimeGeneration.get() != generation) {
                AppDiagnosticLogger.i(context, TAG, "忽略旧实例退出广播，generation=$generation")
                return
            }
            val stopping = isStopping
            isRunning = false
            runningPublishedGeneration = -1L
            startupStartedAtMs = 0L
            if (nodeThread === Thread.currentThread()) nodeThread = null
            decideNodeRuntimeExitAction(stopping, exitCode, crashThrowable)
        }
        when (exitAction) {
            NodeRuntimeExitAction.ReportError -> {
                val startupFailure = StartupFailureStore.readNormal(context)
                val msg = crashThrowable?.let { buildErrorMessage(it) }
                    ?: startupFailure?.userMessage()
                    ?: "Node 进程异常退出，退出码：$exitCode"
                val detail = startupFailure?.detail?.takeIf { it.isNotBlank() } ?: msg
                AppDiagnosticLogger.e(context, TAG, "Node crashed: $detail", crashThrowable)
                RuntimeDependencyHealthChecker.recordModuleNotFoundIssue(
                    context = context,
                    projectDir = projectDir,
                    message = startupFailure?.detail ?: msg
                )
                serviceStopRequested = true
                broadcastStatus(NodeService.STATUS_ERROR, message = msg, error = msg)
                stopForegroundAndSelf()
            }
            NodeRuntimeExitAction.ReportStopped -> {
                serviceStopRequested = true
                broadcastStatus(NodeService.STATUS_STOPPED, message = "服务已停止")
                stopForegroundAndSelf()
            }
            NodeRuntimeExitAction.DeferToStopController -> AppDiagnosticLogger.i(
                context, TAG,
                "检测到受控停止流程，交由 stopNode/finalizeStop 收尾 generation=$generation"
            )
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
                            val projectDir = NodeProjectManager.ensureProjectExtracted(context)
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
                                context = context,
                                targetProjectDir = projectDir,
                                preferredVariantKey = envVariant
                            )
                            ensureGenerationCurrent(generation)
                            when (val health = RuntimeDependencyHealthChecker.inspectSelectedCore(
                                context = context,
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
                        context,
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
        if (synchronized(stateLock) {
                runtimeGeneration.get() != generation || serviceStopRequested || isStopping || !isRunning
            }
        ) {
            throw StartupGenerationStaleException("启动流程 generation=$generation 已过期")
        }
    }

    private fun publishStartingForGeneration(generation: Long, message: String) {
        if (runtimeGeneration.get() == generation) {
            publishStarting(message, generation)
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
        AppDiagnosticLogger.e(context, TAG, "Failed to start node: $message", throwable)
        if (isNodeThreadAlive()) {
            broadcastStatus(
                NodeService.STATUS_ERROR,
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
        broadcastStatus(NodeService.STATUS_ERROR, message = message, error = message)
        stopForegroundAndSelf()
    }

    private suspend fun handleStartupTimeout(generation: Long, message: String) {
        if (serviceStopRequested || runtimeGeneration.get() != generation) return
        val reachable = resolveCandidatePorts().any { it in 1..65535 && isPortOpen(it) }
        if (reachable && isNodeThreadAlive()) {
            AppDiagnosticLogger.w(context, TAG, "启动超时边界检测到端口已就绪，保留前台服务")
            publishRunningIfNeeded(generation)
            return
        }
        val startupFailure = StartupFailureStore.readNormal(context)
        val resolvedMessage = startupFailure?.userMessage() ?: message
        AppDiagnosticLogger.w(
            context,
            TAG,
            startupFailure?.detail?.takeIf { it.isNotBlank() } ?: resolvedMessage
        )
        val threadAlive = isNodeThreadAlive()
        if (threadAlive) {
            broadcastStatus(
                NodeService.STATUS_ERROR,
                message = "$resolvedMessage，正在回收残留运行时",
                error = resolvedMessage
            )
            AppDiagnosticLogger.w(context, TAG, "启动超时但 Node 线程仍存活，保持前台通知直至进程完成回收")
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
        broadcastStatus(NodeService.STATUS_ERROR, message = resolvedMessage, error = resolvedMessage)
        AppDiagnosticLogger.w(
            context,
            TAG,
            "普通模式启动超时且 Node 线程已退出，停止前台服务"
        )
        stopForegroundAndSelf()
    }

    fun stopNode() {
        val generation: Long
        synchronized(stateLock) {
            if (isStopping) return
            commands.recordStop()
            isStopping = true
            generation = runtimeGeneration.get()
            startupJob?.cancel()
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
            AppDiagnosticLogger.w(context, TAG, "普通模式停止超时，强制结束 :node 进程")
            publishStopping("停止较慢，正在强制回收服务进程…")
            serviceStopRequested = true
            broadcastStatus(NodeService.STATUS_STOPPED, message = "服务已停止")
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
        synchronized(stateLock) {
            val sameGeneration = runtimeGeneration.get() == generation
            if (!sameGeneration || serviceStopRequested || !NodeKeepAlivePrefs.isDesiredRunning(context)) return
            val canPublish = isRunning &&
                !isStopping &&
                nodeThread?.isAlive == true &&
                runningPublishedGeneration != generation
            if (canPublish) {
                runningPublishedGeneration = generation
                logLifecycle("本代次端口就绪，发布运行中")
                updateNotification("服务运行中")
                broadcastStatus(
                    NodeService.STATUS_RUNNING,
                    message = "接口已就绪，可直接在局域网访问",
                    explicitStart = currentStartExplicit
                )
            } else {
                return
            }
        }
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
        NormalNotificationBehaviorPrefs.clearManuallyHidden(context)
        // 主动广播停止，避免仓库层仅靠兜底超时判断导致“已停却报错”。
        broadcastStatus(NodeService.STATUS_STOPPED, message = "服务已停止")
        stopForegroundAndSelf()
    }

    private fun isRuntimeOwnedByApp(port: Int): Boolean {
        if (port !in 1..65535) return false
        val expectedIdentity = RuntimeIdentityStore.ensureInstanceId(context).trim()
        val expectedHome = NodeProjectManager.projectDir(context).absolutePath
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
        } catch (error: Exception) {
            AppDiagnosticLogger.w(context, TAG, "已有运行时接管请求未完成（端口 $port）：${error.javaClass.simpleName}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun isPortOpen(port: Int): Boolean {
        return PortProbe.isOpen(port = port)
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
                    context,
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
            val envFile = java.io.File(NodeProjectManager.projectDir(context), "config/.env")
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
        synchronized(stateLock) {
            context.sendBroadcast(Intent(NodeService.ACTION_STATUS).apply {
                setPackage(context.packageName)
                putExtra(NodeService.EXTRA_STATUS, status)
                putExtra(NodeService.EXTRA_PROCESS_STARTED_ELAPSED_MS, processStartedElapsedMs)
                putExtra(NodeService.EXTRA_RUNTIME_GENERATION, runtimeGeneration.get())
                putExtra(NodeService.EXTRA_EVENT_SEQUENCE, eventSequence.incrementAndGet())
                message?.let { putExtra(NodeService.EXTRA_MESSAGE, it) }
                error?.let { putExtra(NodeService.EXTRA_ERROR, it) }
                explicitStart?.let { putExtra(NodeService.EXTRA_EXPLICIT_START, it) }
            })
        }
    }

    private fun publishStarting(message: String, generation: Long, explicitStart: Boolean = currentExplicitStart()) {
        synchronized(stateLock) {
            if (runtimeGeneration.get() != generation || serviceStopRequested || isStopping || !isRunning ||
                !NodeKeepAlivePrefs.isDesiredRunning(context)
            ) return
            AppDiagnosticLogger.i(context, TAG, "启动阶段 generation=$generation: $message")
            updateNotification(message)
            broadcastStatus(NodeService.STATUS_STARTING, message = message, explicitStart = explicitStart)
        }
    }

    private fun publishStopping(message: String) {
        updateNotification(message)
        broadcastStatus(NodeService.STATUS_STOPPING, message = message)
    }

}
