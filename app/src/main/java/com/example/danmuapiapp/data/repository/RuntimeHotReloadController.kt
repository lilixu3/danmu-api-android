package com.example.danmuapiapp.data.repository

import android.content.Context
import android.os.Build
import android.os.FileObserver
import com.example.danmuapiapp.data.service.AppRuntimeAssets
import com.example.danmuapiapp.data.service.RootShell
import com.example.danmuapiapp.data.service.RuntimePaths
import com.example.danmuapiapp.data.util.ShellUtils.shellQuote
import com.example.danmuapiapp.domain.model.LogLevel
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.model.ServiceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * 工作目录热重载：普通模式递归文件监听、Root 目录指纹比对、去抖与抑制窗口。
 *
 * Repository 只提供当前运行状态、普通模式热更新开关、日志与"重启服务"回调。
 */
internal class RuntimeHotReloadController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<RuntimeState>,
    private val operationMutex: Mutex,
    private val hotReloadEnabled: () -> Boolean,
    private val log: (LogLevel, String) -> Unit,
    private val restartRuntime: suspend () -> Unit
) {

    private companion object {
        const val HOT_RELOAD_DEBOUNCE_MS = 800L
        const val HOT_RELOAD_MIN_INTERVAL_MS = 2500L
        const val HOT_RELOAD_SUPPRESS_WINDOW_MS = 5000L
        const val ROOT_FINGERPRINT_CHECK_INTERVAL_MS = 6000L
        const val ROOT_FINGERPRINT_TIMEOUT_MS = 4500L

        val HOT_RELOAD_WATCH_MASK = FileObserver.CLOSE_WRITE or
            FileObserver.MODIFY or
            FileObserver.ATTRIB or
            FileObserver.CREATE or
            FileObserver.MOVED_TO or
            FileObserver.MOVED_FROM or
            FileObserver.DELETE or
            FileObserver.DELETE_SELF or
            FileObserver.MOVE_SELF

        val NORMAL_RESTART_SKIP_PREFIXES = listOf(
            "config/",
            "danmu_api_stable/",
            "danmu_api_dev/",
            "danmu_api_custom/"
        )
    }

    private val normalRestartCriticalFiles by lazy {
        AppRuntimeAssets.relativePaths(context).mapTo(hashSetOf()) { it.lowercase() }
    }
    private var hotReloadWatcher: DirTreeWatcher? = null
    private var hotReloadJob: Job? = null
    private var rootFingerprintCheckJob: Job? = null
    private var pendingHotReloadReason: String? = null
    private var lastHotReloadAtMs: Long = 0L
    private var hotReloadSuppressUntilMs: Long = 0L
    private var rootWorkDirFingerprint: String? = null
    private var lastRootFingerprintCheckAtMs: Long = 0L

    fun onRuntimeStateChanged(state: RuntimeState) {
        if (state.status != ServiceStatus.Running) {
            stop()
            return
        }

        when (state.runMode) {
            RunMode.Normal -> {
                if (!hotReloadEnabled()) {
                    stopNormalWorkDirWatcher()
                    hotReloadJob?.cancel()
                    hotReloadJob = null
                    pendingHotReloadReason = null
                    rootWorkDirFingerprint = null
                    lastRootFingerprintCheckAtMs = 0L
                    return
                }
                ensureNormalWorkDirWatcher()
                rootWorkDirFingerprint = null
                lastRootFingerprintCheckAtMs = 0L
            }

            RunMode.Root -> {
                // Root 模式以 Root 工作目录为唯一热更新来源，避免普通目录变更干扰。
                stopNormalWorkDirWatcher()
                // 被动运行态刷新不再扫描 Root 工作目录；该扫描需要 su，容易在
                // 回前台、控制中心监听和周期状态纠正时触发 Root 管理器授权提示。
                // Root 目录变更改由明确的核心/配置操作负责重启或同步。
                cancelRootFingerprintCheck()
                rootWorkDirFingerprint = null
                lastRootFingerprintCheckAtMs = 0L
            }
        }
    }

    fun stop() {
        stopNormalWorkDirWatcher()
        cancelRootFingerprintCheck()
        hotReloadJob?.cancel()
        hotReloadJob = null
        pendingHotReloadReason = null
        rootWorkDirFingerprint = null
        lastRootFingerprintCheckAtMs = 0L
    }

    fun cancelRootFingerprintCheck() {
        rootFingerprintCheckJob?.cancel()
        rootFingerprintCheckJob = null
    }

    private fun ensureNormalWorkDirWatcher() {
        val projectDir = RuntimePaths.normalProjectDir(context)
        val projectPath = projectDir.absolutePath
        val currentWatcher = hotReloadWatcher
        if (currentWatcher != null && currentWatcher.rootPath == projectPath) {
            return
        }

        currentWatcher?.stop()
        hotReloadWatcher = null

        if (!hotReloadEnabled()) {
            return
        }

        if (!projectDir.exists() || !projectDir.isDirectory) {
            return
        }

        val watcher = DirTreeWatcher(
            rootDir = projectDir,
            mask = HOT_RELOAD_WATCH_MASK,
            shouldIgnore = { shouldIgnoreHotReloadPath(projectDir, it) }
        ) { changedPath ->
            val normalized = normalizeHotReloadPath(changedPath)
            if (!shouldRestartOnNormalPathChange(normalized)) {
                return@DirTreeWatcher
            }
            scheduleHotReload("检测到工作目录关键变更：$normalized")
        }
        watcher.start()
        hotReloadWatcher = watcher
    }

    private fun shouldIgnoreHotReloadPath(rootDir: File, target: File): Boolean {
        val rel = CoreDirWatchPolicy.relativePath(rootDir, target)
        if (rel == "." || rel.isBlank()) return false
        return rel == ".app_version" ||
            rel.startsWith("config/") ||
            rel == "config" ||
            rel.startsWith("danmu_api_stable/") ||
            rel == "danmu_api_stable" ||
            rel.startsWith("danmu_api_dev/") ||
            rel == "danmu_api_dev" ||
            rel.startsWith("danmu_api_custom/") ||
            rel == "danmu_api_custom" ||
            rel.startsWith("logs/") ||
            rel == "logs" ||
            rel.startsWith(".cache/") ||
            rel == ".cache" ||
            rel.startsWith("node_modules/") ||
            rel == "node_modules"
    }

    private fun normalizeHotReloadPath(path: String): String {
        return path.trim()
            .replace('\\', '/')
            .trimStart('/')
    }

    private fun shouldRestartOnNormalPathChange(path: String): Boolean {
        if (path.isBlank()) return false
        val normalized = path.lowercase()

        if (normalized in normalRestartCriticalFiles) {
            return true
        }
        if (NORMAL_RESTART_SKIP_PREFIXES.any { prefix ->
                normalized == prefix.removeSuffix("/") || normalized.startsWith(prefix)
            }) {
            // 核心目录与 config 变更由 Node 进程内热重载处理，避免整服务重启。
            return false
        }

        // 其余工作目录变更默认不触发服务重启，避免普通模式频繁抖动。
        return false
    }

    private fun stopNormalWorkDirWatcher() {
        hotReloadWatcher?.stop()
        hotReloadWatcher = null
    }

    private fun scheduleRootFingerprintCheck() {
        val runningJob = rootFingerprintCheckJob
        if (runningJob != null && runningJob.isActive) return
        val job = scope.launch {
            try {
                checkRootWorkDirFingerprint()
            } finally {
                if (rootFingerprintCheckJob === coroutineContext[Job]) {
                    rootFingerprintCheckJob = null
                }
            }
        }
        rootFingerprintCheckJob = job
    }

    private fun checkRootWorkDirFingerprint() {
        val now = System.currentTimeMillis()
        if (now - lastRootFingerprintCheckAtMs < ROOT_FINGERPRINT_CHECK_INTERVAL_MS) {
            return
        }
        lastRootFingerprintCheckAtMs = now

        val fingerprint = readRootWorkDirFingerprint() ?: return
        val previous = rootWorkDirFingerprint
        rootWorkDirFingerprint = fingerprint

        if (previous != null && previous != fingerprint) {
            scheduleHotReload("检测到 Root 工作目录变更")
        }
    }

    private fun scheduleHotReload(reason: String) {
        val now = System.currentTimeMillis()
        if (now < hotReloadSuppressUntilMs) return
        if (now - lastHotReloadAtMs < HOT_RELOAD_MIN_INTERVAL_MS) return
        val snapshot = state.value
        if (snapshot.status != ServiceStatus.Running) return
        if (snapshot.runMode == RunMode.Normal && !hotReloadEnabled()) return

        pendingHotReloadReason = reason
        hotReloadJob?.cancel()
        hotReloadJob = scope.launch {
            delay(HOT_RELOAD_DEBOUNCE_MS)
            val trigger = pendingHotReloadReason ?: return@launch
            pendingHotReloadReason = null

            operationMutex.withLock {
                val snapshot = state.value
                if (snapshot.status != ServiceStatus.Running) return@withLock

                val nowLocked = System.currentTimeMillis()
                if (nowLocked < hotReloadSuppressUntilMs) return@withLock
                if (nowLocked - lastHotReloadAtMs < HOT_RELOAD_MIN_INTERVAL_MS) return@withLock
                lastHotReloadAtMs = nowLocked
                hotReloadSuppressUntilMs = nowLocked + HOT_RELOAD_SUPPRESS_WINDOW_MS

                log(LogLevel.Info, "$trigger，正在热更新服务")
                if (snapshot.runMode != RunMode.Normal) {
                    // Root 模式直接基于 Root 工作目录重启，不再用普通目录覆盖 Root 目录。
                }
                restartRuntime()
            }
        }
    }

    private fun readRootWorkDirFingerprint(): String? {
        val dir = RuntimePaths.rootProjectDir(context).absolutePath
        val script = """
            DIR=${shellQuote(dir)}
            if [ ! -d "${'$'}DIR" ]; then
              echo "missing"
              exit 0
            fi
            find "${'$'}DIR" \
              \( -path "${'$'}DIR/logs" -o -path "${'$'}DIR/logs/*" -o -path "${'$'}DIR/.cache" -o -path "${'$'}DIR/.cache/*" -o -path "${'$'}DIR/node_modules" -o -path "${'$'}DIR/node_modules/*" \) -prune \
              -o -type f -print 2>/dev/null | while IFS= read -r F; do
                S=${'$'}(stat -c '%Y:%s' "${'$'}F" 2>/dev/null || toybox stat -c '%Y:%s' "${'$'}F" 2>/dev/null || echo '0:0')
                echo "${'$'}S:${'$'}F"
              done | sort | cksum | awk '{print "${'$'}1:${'$'}2"}'
        """.trimIndent()
        val result = RootShell.exec(script, timeoutMs = ROOT_FINGERPRINT_TIMEOUT_MS)
        if (!result.ok) return null
        return result.stdout.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() }
    }
}
