package com.example.danmuapiapp.data.service

import android.content.Context
import android.os.Looper
import com.example.danmuapiapp.data.repository.RuntimeOwnership
import com.example.danmuapiapp.data.repository.determineRuntimeOwnershipFromHealth
import com.example.danmuapiapp.data.repository.isRuntimeOwnershipOwned
import com.example.danmuapiapp.domain.model.ApiVariant
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import com.example.danmuapiapp.data.repository.isRootPassiveLivenessLikely
import com.example.danmuapiapp.data.util.RuntimeTokenNormalizer
import com.example.danmuapiapp.data.util.ShellUtils.shellQuote
import java.net.URL

/**
 * Root 模式控制器：负责独立 Root 进程的启动与停止。
 */
object RootRuntimeController {

    data class OpResult(
        val ok: Boolean,
        val message: String,
        val detail: String = "",
        val startOutcome: StartOutcome = StartOutcome.NotStarted
    )

    enum class StartOutcome {
        NotStarted,
        AlreadyRunning,
        StartedNewProcess
    }

    private const val PROCESS_NAME = "danmuapi_rootnode"
    private const val PID_FILE_NAME = "root_node.pid"
    private const val STARTED_AT_FILE_NAME = "root_node_started_at_ms"
    private val mainClassName = RootNodeEntry::class.java.name

    private data class RuntimeEnvSnapshot(
        val variant: String,
        val port: Int,
        val logLevel: String,
        val tokenConfigured: Boolean,
        val token: String
    )

    private fun pidFile(context: Context): File = File(context.filesDir, PID_FILE_NAME)
    private fun startedAtFile(context: Context): File = File(context.filesDir, STARTED_AT_FILE_NAME)

    private fun rootBaseDir(context: Context): String {
        return RuntimePaths.rootBaseDir(context).absolutePath
    }

    private fun rootProjectDir(context: Context): String {
        return "${rootBaseDir(context)}/nodejs-project"
    }

    private fun rootProjectMainJsExists(context: Context): Boolean {
        val script = """
            FILE=${shellQuote("${rootProjectDir(context)}/main.js")}
            [ -f "${'$'}FILE" ]
        """.trimIndent()
        return RootShell.exec(script, timeoutMs = 2500L).ok
    }

    fun isRunningFast(port: Int): Boolean {
        return isPortOpen("127.0.0.1", port, 220)
    }

    private fun isRuntimeOwnedByApp(context: Context, port: Int): Boolean {
        return isRuntimeOwnershipOwned(readRuntimeOwnership(context, port))
    }

    private fun readRuntimeOwnership(context: Context, port: Int): RuntimeOwnership {
        if (port !in 1..65535) return RuntimeOwnership.Foreign
        val expectedIdentity = RuntimeIdentityStore.ensureInstanceId(context).trim()
        val expectedHome = RuntimePaths.rootProjectDir(context).absolutePath
        val body = readRuntimeHealthBody(port) ?: return RuntimeOwnership.Foreign
        return determineRuntimeOwnershipFromHealth(
            body = body,
            expectedIdentity = expectedIdentity,
            expectedHome = expectedHome
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

    fun isRunning(context: Context, port: Int): Boolean {
        if (isRuntimeOwnedByApp(context, port)) return true

        val pid = readPid(context) ?: return false
        if (Looper.getMainLooper().thread === Thread.currentThread()) {
            return false
        }

        val checkScript = """
            PID=${shellQuote(pid.toString())}
            if [ ! -d /proc/${'$'}PID ]; then
              exit 1
            fi
            CMDLINE=${'$'}(tr '\\0' ' ' < /proc/${'$'}PID/cmdline 2>/dev/null || true)
            echo "${'$'}CMDLINE" | grep -q ${shellQuote(mainClassName)}
        """.trimIndent()

        val result = RootShell.exec(checkScript, timeoutMs = 2500L)
        if (result.ok) return true

        runCatching { pidFile(context).delete() }
        return false
    }

    /**
     * Passive liveness hint used by UI/state reconciliation paths.
     *
     * This deliberately avoids RootShell/su so foreground resume, QS tile listening,
     * and periodic status reconciliation do not trigger Root manager authorization
     * notifications. Active start/stop paths still use [isRunning] when they need an
     * exact answer.
     */
    fun isProbablyRunning(context: Context, port: Int): Boolean {
        val portOpen = isRunningFast(port)
        if (portOpen) return true
        return isRootPassiveLivenessLikely(
            portOpen = false,
            pidPresent = readPid(context) != null,
            startedAtMs = getProcessStartedAtMs(context),
            nowMs = System.currentTimeMillis()
        )
    }

    /**
     * 兼容旧调用签名，避免增参后出现 NoSuchMethodError。
     */
    fun start(context: Context, port: Int, quickMode: Boolean): OpResult {
        return start(context, port, quickMode, skipSync = false)
    }

    fun start(
        context: Context,
        port: Int,
        quickMode: Boolean = false,
        skipSync: Boolean = false
    ): OpResult {
        if (isRuntimeOwnedByApp(context, port)) {
            return OpResult(
                ok = true,
                message = "Root 模式已在运行",
                startOutcome = StartOutcome.AlreadyRunning
            )
        }

        AppDiagnosticLogger.i(context, "RootRuntimeController", "请求启动 Root 模式，端口=$port")
        val bootLogFile = AppDiagnosticLogger.prepareRootBootstrapLog(context)

        if (!RootShell.hasRoot(3000L)) {
            AppDiagnosticLogger.e(context, "RootRuntimeController", "Root 授权失败")
            return OpResult(false, "Root 授权失败", "请确认设备已 Root，并允许本应用获取 Root 权限")
        }

        if (isRuntimeOwnedByApp(context, port)) {
            return OpResult(
                ok = true,
                message = "Root 模式已在运行",
                startOutcome = StartOutcome.AlreadyRunning
            )
        }

        if (isRunningFast(port)) {
            return OpResult(
                ok = false,
                message = "Root 端口已被其他实例占用",
                detail = "端口 $port 已有其他实例在运行，请先停止外部进程后再启动"
            )
        }

        // Root 与普通模式目录要彻底隔离：
        // 1) 仅在 Root 目录缺失时才从普通目录做完整引导；
        // 2) Root 目录已存在时只同步 App 托管包装层、必要依赖与 Root 自身环境变量，
        //    不回灌 Root 独立的 config、core、cache 数据。
        val prepare = ensureRootRuntimeReady(
            context = context,
            refreshEnvWhenReady = !skipSync
        )
        if (!prepare.ok) {
            AppDiagnosticLogger.e(
                context,
                "RootRuntimeController",
                "Root 运行时准备失败：${prepare.detail.ifBlank { prepare.message }}"
            )
            return OpResult(false, "Root 模式启动失败", prepare.detail.ifBlank { prepare.message })
        }

        val rootProject = rootProjectDir(context)
        val entryPath = "$rootProject/main.js"
        val pidPath = pidFile(context).absolutePath
        val startedAtPath = startedAtFile(context).absolutePath
        val pkgName = context.packageName
        val apkPathHint = context.applicationInfo.sourceDir
        val libDirHint = context.applicationInfo.nativeLibraryDir
        val bootLogPath = bootLogFile.absolutePath
        val runtimeIdentity = RuntimeIdentityStore.ensureInstanceId(context)

        StartupFailureStore.clearRoot(context)

        val startScript = """
            PKG=${shellQuote(pkgName)}
            APP_APK_HINT=${shellQuote(apkPathHint)}
            LIB_DIR_HINT=${shellQuote(libDirHint)}
            ENTRY=${shellQuote(entryPath)}
            PID_FILE=${shellQuote(pidPath)}
            STARTED_AT_FILE=${shellQuote(startedAtPath)}
            BOOT_LOG=${shellQuote(bootLogPath)}
            MAIN_CLASS=${shellQuote(mainClassName)}
            NICE_NAME=${shellQuote(PROCESS_NAME)}

            ts() { date '+%Y-%m-%d %H:%M:%S'; }
            mkdir -p "${'$'}(dirname "${'$'}BOOT_LOG")" >/dev/null 2>&1 || true
            printf '%s [INFO] Root 启动脚本开始\n' "${'$'}(ts)" >> "${'$'}BOOT_LOG" 2>/dev/null || true
            printf '%s [INFO] 目标入口：%s\n' "${'$'}(ts)" "${'$'}ENTRY" >> "${'$'}BOOT_LOG" 2>/dev/null || true

            APP_APK="${'$'}APP_APK_HINT"
            if [ -z "${'$'}APP_APK" ] || [ ! -f "${'$'}APP_APK" ]; then
              APP_APK="${'$'}(pm path "${'$'}PKG" 2>/dev/null | head -n 1 | cut -d: -f2)"
            fi
            if [ -z "${'$'}APP_APK" ] || [ ! -f "${'$'}APP_APK" ]; then
              echo 'pm path failed' >&2
              exit 2
            fi

            LIB_DIR="${'$'}LIB_DIR_HINT"
            if [ -z "${'$'}LIB_DIR" ] || [ ! -d "${'$'}LIB_DIR" ]; then
              LIB_DIR="${'$'}(dumpsys package "${'$'}PKG" 2>/dev/null | grep -m1 'nativeLibraryDir=' | cut -d= -f2 | cut -d' ' -f1)"
            fi
            if [ -z "${'$'}LIB_DIR" ] || [ ! -d "${'$'}LIB_DIR" ]; then
              APP_DIR="${'$'}(dirname "${'$'}APP_APK")"
              LIB_DIR="${'$'}(ls -d "${'$'}APP_DIR"/lib/* 2>/dev/null | head -n 1)"
            fi
            if [ -z "${'$'}LIB_DIR" ] || [ ! -d "${'$'}LIB_DIR" ]; then
              echo 'nativeLibraryDir not found' >&2
              exit 3
            fi

            APPPROC='/system/bin/app_process'
            if echo "${'$'}LIB_DIR" | grep -q 'arm64'; then
              [ -x /system/bin/app_process64 ] && APPPROC='/system/bin/app_process64'
            else
              [ -x /system/bin/app_process32 ] && APPPROC='/system/bin/app_process32'
            fi
            [ -x "${'$'}APPPROC" ] || APPPROC='/system/bin/app_process'

            export CLASSPATH="${'$'}APP_APK"
            export DANMUAPI_LIBDIR="${'$'}LIB_DIR"
            if [ -n "${'$'}LD_LIBRARY_PATH" ]; then
              export LD_LIBRARY_PATH="${'$'}LIB_DIR:${'$'}LD_LIBRARY_PATH"
            else
              export LD_LIBRARY_PATH="${'$'}LIB_DIR"
            fi

            mkdir -p "${'$'}(dirname "${'$'}PID_FILE")" >/dev/null 2>&1 || true
            export DANMU_API_HOME="${'$'}(dirname "${'$'}ENTRY")"
            export DANMU_API_RUNTIME_IDENTITY=${shellQuote(runtimeIdentity)}
            cd "${'$'}DANMU_API_HOME" >/dev/null 2>&1 || true
            printf '%s [INFO] DANMU_API_HOME=%s\n' "${'$'}(ts)" "${'$'}DANMU_API_HOME" >> "${'$'}BOOT_LOG" 2>/dev/null || true

            if command -v setsid >/dev/null 2>&1; then
              printf '%s [INFO] 使用 setsid 拉起 Root 运行时\n' "${'$'}(ts)" >> "${'$'}BOOT_LOG" 2>/dev/null || true
              setsid "${'$'}APPPROC" /system/bin --nice-name="${'$'}NICE_NAME" "${'$'}MAIN_CLASS" --entry "${'$'}ENTRY" --pidfile "${'$'}PID_FILE" --started-at-file "${'$'}STARTED_AT_FILE" >> "${'$'}BOOT_LOG" 2>&1 < /dev/null &
            elif command -v nohup >/dev/null 2>&1; then
              printf '%s [INFO] 使用 nohup 拉起 Root 运行时\n' "${'$'}(ts)" >> "${'$'}BOOT_LOG" 2>/dev/null || true
              nohup "${'$'}APPPROC" /system/bin --nice-name="${'$'}NICE_NAME" "${'$'}MAIN_CLASS" --entry "${'$'}ENTRY" --pidfile "${'$'}PID_FILE" --started-at-file "${'$'}STARTED_AT_FILE" >> "${'$'}BOOT_LOG" 2>&1 < /dev/null &
            else
              printf '%s [INFO] 直接拉起 Root 运行时\n' "${'$'}(ts)" >> "${'$'}BOOT_LOG" 2>/dev/null || true
              "${'$'}APPPROC" /system/bin --nice-name="${'$'}NICE_NAME" "${'$'}MAIN_CLASS" --entry "${'$'}ENTRY" --pidfile "${'$'}PID_FILE" --started-at-file "${'$'}STARTED_AT_FILE" >> "${'$'}BOOT_LOG" 2>&1 < /dev/null &
            fi
            sleep 0.25
            printf '%s [INFO] Root 启动命令已发出\n' "${'$'}(ts)" >> "${'$'}BOOT_LOG" 2>/dev/null || true
        """.trimIndent()

        val startResult = RootShell.exec(startScript, timeoutMs = 15000L)
        if (!startResult.ok) {
            val err = (startResult.stderr.ifBlank { startResult.stdout }).trim().take(400)
            val detail = mergeRootBootstrapDetail(
                primary = if (err.isBlank()) "未知错误" else err,
                tail = AppDiagnosticLogger.readRootBootstrapTail(context)
            )
            AppDiagnosticLogger.e(context, "RootRuntimeController", "Root 模式启动失败：$detail")
            return OpResult(false, "Root 模式启动失败", detail)
        }

        if (quickMode) {
            AppDiagnosticLogger.i(context, "RootRuntimeController", "Root 模式已触发启动")
            return OpResult(
                ok = true,
                message = "Root 模式已触发启动",
                startOutcome = StartOutcome.StartedNewProcess
            )
        }

        val startupWait = waitForReadyOrFailure(context, port, timeoutMs = 12_000L)
        return if (startupWait.ready) {
            AppDiagnosticLogger.i(context, "RootRuntimeController", "Root 模式已启动，端口=$port")
            OpResult(
                ok = true,
                message = "Root 模式已启动",
                startOutcome = StartOutcome.StartedNewProcess
            )
        } else {
            val detail = mergeRootBootstrapDetail(
                primary = startupWait.failureDetail
                    ?: "端口 $port 未就绪，请在应用控制台查看 Root 启动日志与 /api/logs 后重试",
                tail = AppDiagnosticLogger.readRootBootstrapTail(context)
            )
            AppDiagnosticLogger.e(context, "RootRuntimeController", "Root 模式启动超时：$detail")
            OpResult(false, "Root 模式启动超时", detail)
        }
    }

    fun stop(context: Context, port: Int): OpResult {
        requestShutdown(port)

        if (waitForPort("127.0.0.1", port, wantOpen = false, timeoutMs = 4000L)) {
            clearRuntimeMarkers(context)
            return OpResult(true, "已停止")
        }

        val pid = readPid(context)
        if (pid == null) {
            clearRuntimeMarkers(context)
            return OpResult(true, "已停止")
        }

        if (!RootShell.hasRoot(2500L)) {
            return OpResult(false, "停止失败", "缺少 Root 权限")
        }

        RootShell.exec("kill -TERM $pid 2>/dev/null || true", timeoutMs = 5000L)
        if (waitForPidExit(pid, timeoutMs = 3000L) || waitForPort("127.0.0.1", port, wantOpen = false, timeoutMs = 2500L)) {
            clearRuntimeMarkers(context)
            return OpResult(true, "已停止")
        }

        RootShell.exec("kill -KILL $pid 2>/dev/null || true", timeoutMs = 5000L)
        val stopped = waitForPidExit(pid, timeoutMs = 1500L) || !isPidAlive(pid)
        if (stopped) clearRuntimeMarkers(context)

        return if (stopped) {
            OpResult(true, "已停止")
        } else {
            OpResult(false, "停止失败", "进程未退出")
        }
    }

    fun restart(context: Context, port: Int): OpResult {
        val beforePid = readPid(context)
        val stopResult = stop(context, port)
        if (!stopResult.ok) {
            return OpResult(
                false,
                "重启失败",
                "停止阶段失败：${stopResult.detail.ifBlank { stopResult.message }}"
            )
        }

        // 兜底确认：端口仍被占用时说明旧进程未完全退出，避免误判“已重启”。
        if (isRunningFast(port)) {
            val pid = beforePid ?: readPid(context)
            if (pid != null && RootShell.hasRoot(1500L)) {
                RootShell.exec("kill -KILL $pid 2>/dev/null || true", timeoutMs = 3500L)
                waitForPidExit(pid, timeoutMs = 1800L)
                waitForPort("127.0.0.1", port, wantOpen = false, timeoutMs = 1800L)
            }
        }

        if (isRunningFast(port)) {
            return OpResult(false, "重启失败", "旧进程仍在运行，未执行新启动")
        }
        return start(context, port, quickMode = false)
    }

    fun getPid(context: Context): Int? = readPid(context)
    fun getProcessStartedAtMs(context: Context): Long? {
        val f = startedAtFile(context)
        if (!f.exists()) return null
        val raw = runCatching { f.readText(Charsets.UTF_8) }.getOrNull()?.trim() ?: return null
        return raw.toLongOrNull()?.takeIf { it > 0L }
    }

    internal fun buildClearRuntimeMarkersShell(pidPath: String, startedAtPath: String): String {
        return """
            PID_FILE=${shellQuote(pidPath)}
            STARTED_AT_FILE=${shellQuote(startedAtPath)}
            rm -f "${'$'}PID_FILE" "${'$'}STARTED_AT_FILE" 2>/dev/null || true
        """.trimIndent()
    }

    private fun clearRuntimeMarkers(context: Context) {
        runCatching { pidFile(context).delete() }
        runCatching { startedAtFile(context).delete() }
    }

    fun getPidFileLastModified(context: Context): Long? {
        val f = pidFile(context)
        if (!f.exists()) return null
        val ts = f.lastModified()
        return ts.takeIf { it > 0L }
    }

    fun syncRuntimeEnvOnly(context: Context): OpResult {
        if (!RootShell.hasRoot(2500L)) {
            return OpResult(false, "同步 Root 环境失败", "缺少 Root 权限")
        }

        val envSyncResult = syncRuntimeEnvToRootFromPrefs(context)
        if (!envSyncResult.ok) return envSyncResult
        val normalize = normalizeRootProjectPermissions(context, fullScan = false)
        if (!normalize.ok) return normalize
        return OpResult(true, "Root 环境同步完成")
    }

    fun ensureRootRuntimeReady(
        context: Context,
        refreshEnvWhenReady: Boolean = true
    ): OpResult {
        return if (rootProjectMainJsExists(context)) {
            if (refreshEnvWhenReady) {
                val normalProject = runCatching {
                    val sourceProjectDir = RuntimePaths.normalProjectDir(context)
                    val dir = NodeProjectManager.ensureProjectExtracted(context, sourceProjectDir)
                    NodeProjectManager.migrateAllCoreLayouts(dir)
                    dir
                }.getOrElse {
                    return OpResult(false, "运行时准备失败", it.message ?: "无法初始化工作目录")
                }

                val depsSyncResult = syncRootNodeModulesIfNeeded(context, normalProject)
                if (!depsSyncResult.ok) return depsSyncResult

                val projectSyncResult = syncProjectToRoot(context, normalProject, bootstrap = false)
                if (!projectSyncResult.ok) return projectSyncResult

                val coreReady = ensureSelectedCoreReady(context, normalProject)
                if (!coreReady.ok) return coreReady

                syncRuntimeEnvOnly(context)
            } else {
                OpResult(true, "Root 运行目录已就绪")
            }
        } else {
            syncWorkDirToRoot(context)
        }
    }
    fun syncWorkDirToRoot(context: Context): OpResult {
        val project = runCatching {
            val sourceProjectDir = RuntimePaths.normalProjectDir(context)
            val dir = NodeProjectManager.ensureProjectExtracted(context, sourceProjectDir)
            NodeProjectManager.migrateAllCoreLayouts(dir)
            NodeProjectManager.writeRuntimeEnv(context, dir)
            dir
        }.getOrElse {
            return OpResult(false, "运行时准备失败", it.message ?: "无法初始化工作目录")
        }
        val syncResult = syncProjectToRoot(context, project, bootstrap = true)
        if (!syncResult.ok) return syncResult
        val depsSyncResult = syncRootNodeModulesIfNeeded(context, project)
        if (!depsSyncResult.ok) return depsSyncResult
        val envSyncResult = syncRuntimeEnvToRoot(context, project)
        if (!envSyncResult.ok) return envSyncResult
        val coreReady = ensureSelectedCoreReady(context, project)
        if (!coreReady.ok) return coreReady
        val normalize = normalizeRootProjectPermissions(context, fullScan = true)
        if (!normalize.ok) return normalize
        return OpResult(true, "同步完成")
    }

    private fun syncRuntimeEnvToRootFromPrefs(context: Context): OpResult {
        val snapshot = buildRuntimeEnvSnapshot(context)
        val rootEnvPath = "${rootProjectDir(context)}/config/.env"
        val tokenConfigured = if (snapshot.tokenConfigured) "1" else "0"

        val script = """
            ENV_FILE=${shellQuote(rootEnvPath)}
            VARIANT=${shellQuote(snapshot.variant)}
            PORT=${shellQuote(snapshot.port.toString())}
            LOG_LEVEL=${shellQuote(snapshot.logLevel)}
            TOKEN_CONFIGURED=${shellQuote(tokenConfigured)}
            TOKEN_VALUE=${shellQuote(snapshot.token)}
            TMP_PREFIX="${'$'}ENV_FILE.tmp"

            mkdir -p "${'$'}(dirname "${'$'}ENV_FILE")" >/dev/null 2>&1 || true
            [ -f "${'$'}ENV_FILE" ] || touch "${'$'}ENV_FILE"

            upsert_env() {
              K="${'$'}1"
              V="${'$'}2"
              TMP="${'$'}TMP_PREFIX.${'$'}$"
              awk -v k="${'$'}K" -v v="${'$'}V" '
                BEGIN { done = 0 }
                $0 ~ "^[[:space:]]*" k "=" {
                  if (!done) {
                    print k "=" v
                    done = 1
                  }
                  next
                }
                { print }
                END {
                  if (!done) print k "=" v
                }
              ' "${'$'}ENV_FILE" > "${'$'}TMP" && mv "${'$'}TMP" "${'$'}ENV_FILE"
            }

            remove_env() {
              K="${'$'}1"
              TMP="${'$'}TMP_PREFIX.${'$'}$"
              awk -v k="${'$'}K" '
                $0 ~ "^[[:space:]]*" k "=" { next }
                { print }
              ' "${'$'}ENV_FILE" > "${'$'}TMP" && mv "${'$'}TMP" "${'$'}ENV_FILE"
            }

            ensure_env_default() {
              K="${'$'}1"
              V="${'$'}2"
              if grep -Eq "^[[:space:]]*${'$'}K=" "${'$'}ENV_FILE" 2>/dev/null; then
                return 0
              fi
              upsert_env "${'$'}K" "${'$'}V"
            }

            upsert_env "DANMU_API_VARIANT" "${'$'}VARIANT"
            upsert_env "DANMU_API_PORT" "${'$'}PORT"
            upsert_env "LOG_LEVEL" "${'$'}LOG_LEVEL"
            ensure_env_default "DANMU_API_LOG_TO_FILE" "0"
            ensure_env_default "DANMU_API_LOG_MAX_BYTES" "1048576"
            ensure_env_default "APP_LOG_TO_FILE" "0"
            ensure_env_default "APP_LOG_MAX_BYTES" "1048576"

            if [ "${'$'}TOKEN_CONFIGURED" = "1" ]; then
              if [ -n "${'$'}TOKEN_VALUE" ]; then
                upsert_env "TOKEN" "${'$'}TOKEN_VALUE"
              else
                remove_env "TOKEN"
              fi
            fi

            [ -f "${'$'}ENV_FILE" ]
        """.trimIndent()

        val result = RootShell.exec(script, timeoutMs = 12_000L)
        if (!result.ok) {
            val err = (result.stderr.ifBlank { result.stdout }).trim().take(400)
            return OpResult(false, "同步 Root 环境失败", if (err.isBlank()) "未知错误" else err)
        }
        return OpResult(true, "Root 环境同步完成")
    }

    private fun buildRuntimeEnvSnapshot(context: Context): RuntimeEnvSnapshot {
        val prefs = context.getSharedPreferences("runtime", Context.MODE_PRIVATE)
        val rawVariant = prefs.getString("variant", "stable").orEmpty().trim().lowercase()
        val variant = when (rawVariant) {
            "dev", "develop", "development" -> "dev"
            "custom" -> "custom"
            else -> "stable"
        }
        val port = prefs.getInt("port", 9321).coerceIn(1, 65535)
        val logLevel = prefs.getString("log_level", "info").orEmpty().trim().ifBlank { "info" }
        val tokenConfigured = prefs.contains("token")
        val token = RuntimeTokenNormalizer.normalizeInput(prefs.getString("token", ""))
        return RuntimeEnvSnapshot(
            variant = variant,
            port = port,
            logLevel = logLevel,
            tokenConfigured = tokenConfigured,
            token = token
        )
    }

    internal fun buildRootProjectIncrementalSyncShell(srcProjectPath: String, dstProjectPath: String): String {
        return """
            SRC=${shellQuote(srcProjectPath)}
            DST=${shellQuote(dstProjectPath)}
            mkdir -p "${'$'}DST" "${'$'}DST/config" "${'$'}DST/logs" 2>/dev/null || true

            # 热启动只需要同步 App 托管的顶层包装文件；不要递归复制 node_modules/core/cache。
            # 使用普通 glob 兼容 Android toybox/mksh，避免依赖 find/rsync。
            for FILE in "${'$'}SRC"/* "${'$'}SRC"/.[!.]* "${'$'}SRC"/..?*; do
              [ -f "${'$'}FILE" ] || continue
              NAME="${'$'}{FILE##*/}"
              cp -f "${'$'}FILE" "${'$'}DST/${'$'}NAME" 2>/dev/null || cat "${'$'}FILE" > "${'$'}DST/${'$'}NAME"
            done

            test -f "${'$'}DST/main.js"
        """.trimIndent()
    }

    private fun syncProjectToRoot(
        context: Context,
        srcProjectDir: File,
        bootstrap: Boolean = !rootProjectMainJsExists(context)
    ): OpResult {
        val src = srcProjectDir.absolutePath
        val dst = rootProjectDir(context)

        val script = if (bootstrap) {
            // 首次引导：复制运行时文件，但不继承普通模式缓存。
            """
                SRC=${shellQuote(src)}
                DST=${shellQuote(dst)}
                rm -rf "${'$'}DST" 2>/dev/null || true
                mkdir -p "${'$'}DST" 2>/dev/null || true
                cp -a "${'$'}SRC/." "${'$'}DST/" 2>/dev/null || cp -r "${'$'}SRC/." "${'$'}DST/" 2>/dev/null || true
                rm -rf "${'$'}DST/.cache" 2>/dev/null || true
                mkdir -p "${'$'}DST/.cache" 2>/dev/null || true
                test -f "${'$'}DST/main.js"
            """.trimIndent()
        } else {
            // 增量同步：只同步 App 托管的顶层包装文件，保留 Root 工作目录中的
            // config、danmu_api_*、node_modules 与 .cache。避免每次启动都复制整个 core/node_modules 到临时目录。
            buildRootProjectIncrementalSyncShell(srcProjectPath = src, dstProjectPath = dst)
        }

        val result = RootShell.exec(script, timeoutMs = 25000L)
        if (!result.ok) {
            val err = (result.stderr.ifBlank { result.stdout }).trim().take(400)
            return OpResult(false, "同步 Root 运行目录失败", if (err.isBlank()) "未知错误" else err)
        }
        return OpResult(true, "同步完成")
    }

    private fun syncRuntimeEnvToRoot(context: Context, srcProjectDir: File): OpResult {
        val srcEnv = File(srcProjectDir, "config/.env")
        if (!srcEnv.exists() || !srcEnv.isFile) {
            return OpResult(false, "同步 Root 环境失败", "未找到运行时配置文件：${srcEnv.absolutePath}")
        }

        val script = """
            SRC=${shellQuote(srcEnv.absolutePath)}
            DST=${shellQuote("${rootProjectDir(context)}/config/.env")}
            mkdir -p "${'$'}(dirname "${'$'}DST")" >/dev/null 2>&1 || true
            cp -f "${'$'}SRC" "${'$'}DST" 2>/dev/null || cat "${'$'}SRC" > "${'$'}DST"
            test -f "${'$'}DST"
        """.trimIndent()
        val result = RootShell.exec(script, timeoutMs = 10000L)
        if (!result.ok) {
            val err = (result.stderr.ifBlank { result.stdout }).trim().take(400)
            return OpResult(false, "同步 Root 环境失败", if (err.isBlank()) "未知错误" else err)
        }
        return OpResult(true, "Root 环境同步完成")
    }

    internal fun buildRootProjectPermissionNormalizeShell(
        rootProjectPath: String,
        fullScan: Boolean
    ): String {
        val commonTail = """
            [ -d "${'$'}DST/config" ] && chmod 0755 "${'$'}DST/config" 2>/dev/null || true
            [ -f "${'$'}DST/config/.env" ] && chmod 0640 "${'$'}DST/config/.env" 2>/dev/null || true
            [ -d "${'$'}DST/logs" ] && chmod 0775 "${'$'}DST/logs" 2>/dev/null || true
            exit 0
        """.trimIndent()

        return if (fullScan) {
            """
                DST=${shellQuote(rootProjectPath)}
                [ -d "${'$'}DST" ] || exit 0

                # 首次引导/大同步后才做递归权限归一，避免每次热启动扫描整个 core/node_modules。
                chown -R 0:0 "${'$'}DST" 2>/dev/null || true
                chmod -R u+rwX,go+rX "${'$'}DST" 2>/dev/null || true

                $commonTail
            """.trimIndent()
        } else {
            """
                DST=${shellQuote(rootProjectPath)}
                [ -d "${'$'}DST" ] || exit 0
                mkdir -p "${'$'}DST/config" "${'$'}DST/logs" 2>/dev/null || true

                # 热启动只修正启动必需的浅层文件和配置/日志目录。
                for NAME in main.js android-server.js worker-proxy.js startup-failure.js package.json package-lock.json .app_version; do
                  [ -f "${'$'}DST/${'$'}NAME" ] && chmod 0644 "${'$'}DST/${'$'}NAME" 2>/dev/null || true
                done

                $commonTail
            """.trimIndent()
        }
    }

    private fun normalizeRootProjectPermissions(context: Context, fullScan: Boolean): OpResult {
        val script = buildRootProjectPermissionNormalizeShell(
            rootProjectPath = rootProjectDir(context),
            fullScan = fullScan
        )
        val result = RootShell.exec(script, timeoutMs = if (fullScan) 15_000L else 5_000L)
        if (!result.ok) {
            val err = (result.stderr.ifBlank { result.stdout }).trim().take(400)
            return OpResult(false, "Root 目录权限修复失败", if (err.isBlank()) "未知错误" else err)
        }
        return OpResult(true, "Root 目录权限已修复")
    }

    private fun ensureSelectedCoreReady(context: Context, normalProjectDir: File): OpResult {
        val prefs = context.getSharedPreferences("runtime", Context.MODE_PRIVATE)
        val variant = ApiVariant.entries.find { it.key == prefs.getString("variant", "stable") }
            ?: ApiVariant.Stable
        val normalCoreDir = File(normalProjectDir, "danmu_api_${variant.key}")
        val rootCoreDirPath = "${rootProjectDir(context)}/danmu_api_${variant.key}"

        if (rootCoreHasWorker(rootCoreDirPath)) {
            return syncRootCoreNodeModulesIfNeeded(normalCoreDir, rootCoreDirPath)
        }

        // Root 缺少当前核心时，仅补齐当前核心，避免恢复时覆盖用户已修改的其它核心。
        if (!NodeProjectManager.hasValidCore(normalCoreDir)) {
            return OpResult(
                false,
                "Root 缺少当前核心",
                "当前核心 ${variant.label} 未安装或不完整，请先安装后再启动 Root 模式"
            )
        }

        val script = """
            SRC=${shellQuote(normalCoreDir.absolutePath)}
            DST=${shellQuote(rootCoreDirPath)}
            rm -rf "${'$'}DST" 2>/dev/null || true
            mkdir -p "${'$'}DST" 2>/dev/null || true
            cp -a "${'$'}SRC/." "${'$'}DST/" 2>/dev/null || cp -r "${'$'}SRC/." "${'$'}DST/" 2>/dev/null || true
            chmod -R u+rwX,go+rX "${'$'}DST" 2>/dev/null || true
            [ -f "${'$'}DST/worker.js" ] || [ -f "${'$'}DST/danmu_api/worker.js" ] || [ -f "${'$'}DST/danmu-api/worker.js" ]
        """.trimIndent()
        val result = RootShell.exec(script, timeoutMs = 25_000L)
        if (!result.ok) {
            val err = (result.stderr.ifBlank { result.stdout }).trim().take(400)
            return OpResult(false, "补齐 Root 核心失败", if (err.isBlank()) "未知错误" else err)
        }
        val depsSync = syncRootCoreNodeModulesIfNeeded(normalCoreDir, rootCoreDirPath)
        if (!depsSync.ok) return depsSync
        return OpResult(true, "同步完成")
    }

    private fun rootCoreHasWorker(rootCoreDirPath: String): Boolean {
        val script = """
            DIR=${shellQuote(rootCoreDirPath)}
            [ -f "${'$'}DIR/worker.js" ] && exit 0
            [ -f "${'$'}DIR/danmu_api/worker.js" ] && exit 0
            [ -f "${'$'}DIR/danmu-api/worker.js" ] && exit 0
            exit 1
        """.trimIndent()
        return RootShell.exec(script, timeoutMs = 3000L).ok
    }

    internal fun buildNodeModuleIntegrityProbeShell(
        srcNodeModulesVar: String,
        dstNodeModulesVar: String
    ): String {
        return buildNodeModulePackageVisitShell(
            srcNodeModulesVar = srcNodeModulesVar,
            dstNodeModulesVar = dstNodeModulesVar,
            actionOnMismatch = "NEED_SYNC=1"
        )
    }

    internal fun buildNodeModulePackageRepairShell(
        srcNodeModulesVar: String,
        dstNodeModulesVar: String
    ): String {
        return buildNodeModulePackageVisitShell(
            srcNodeModulesVar = srcNodeModulesVar,
            dstNodeModulesVar = dstNodeModulesVar,
            actionOnMismatch = """
                DST_PACKAGE_DIR="${'$'}DST_ROOT/${'$'}PKG"
                rm -rf "${'$'}DST_PACKAGE_DIR" 2>/dev/null || true
                mkdir -p "${'$'}(dirname "${'$'}DST_PACKAGE_DIR")" 2>/dev/null || true
                cp -a "${'$'}SRC_ROOT/${'$'}PKG" "${'$'}DST_PACKAGE_DIR" 2>/dev/null || cp -r "${'$'}SRC_ROOT/${'$'}PKG" "${'$'}DST_PACKAGE_DIR" 2>/dev/null || true
                chmod -R u+rwX,go+rX "${'$'}DST_PACKAGE_DIR" 2>/dev/null || true
            """.trimIndent()
        )
    }

    internal fun buildNodeModuleIntegrityVerifyShell(
        srcNodeModulesVar: String,
        dstNodeModulesVar: String
    ): String {
        return buildNodeModulePackageVisitShell(
            srcNodeModulesVar = srcNodeModulesVar,
            dstNodeModulesVar = dstNodeModulesVar,
            actionOnMismatch = "exit 2"
        )
    }

    private fun buildNodeModulePackageVisitShell(
        srcNodeModulesVar: String,
        dstNodeModulesVar: String,
        actionOnMismatch: String
    ): String {
        val indentedAction = actionOnMismatch.prependIndent("      ")
        return """
            SRC_ROOT="${'$'}$srcNodeModulesVar"
            DST_ROOT="${'$'}$dstNodeModulesVar"

            check_node_package() {
              PKG="${'$'}1"
              SRC_DEP="${'$'}SRC_ROOT/${'$'}PKG/package.json"
              DST_DEP="${'$'}DST_ROOT/${'$'}PKG/package.json"
              if [ -f "${'$'}SRC_DEP" ]; then
                SRC_DEP_SUM="${'$'}(cksum "${'$'}SRC_DEP" 2>/dev/null | tr ' ' ':' | cut -d: -f1-2)"
                DST_DEP_SUM="${'$'}(cksum "${'$'}DST_DEP" 2>/dev/null | tr ' ' ':' | cut -d: -f1-2)"
                if [ -n "${'$'}SRC_DEP_SUM" ] && [ "${'$'}SRC_DEP_SUM" != "${'$'}DST_DEP_SUM" ]; then
$indentedAction
                fi
              fi
            }

            [ -d "${'$'}SRC_ROOT" ] || exit 0
            mkdir -p "${'$'}DST_ROOT" 2>/dev/null || true
            for ENTRY in "${'$'}SRC_ROOT"/*; do
              [ -d "${'$'}ENTRY" ] || continue
              BASE="${'$'}{ENTRY##*/}"
              case "${'$'}BASE" in
                .* ) continue ;;
                @* )
                  for SCOPED_ENTRY in "${'$'}ENTRY"/*; do
                    [ -d "${'$'}SCOPED_ENTRY" ] || continue
                    SCOPED_NAME="${'$'}{SCOPED_ENTRY##*/}"
                    [ -n "${'$'}SCOPED_NAME" ] && check_node_package "${'$'}BASE/${'$'}SCOPED_NAME"
                  done
                  ;;
                * )
                  check_node_package "${'$'}BASE"
                  ;;
              esac
            done
        """.trimIndent()
    }

    private fun syncRootNodeModulesIfNeeded(context: Context, normalProjectDir: File): OpResult {
        val srcProjectPath = normalProjectDir.absolutePath
        val dstProjectPath = rootProjectDir(context)
        val packageRepair = buildNodeModulePackageRepairShell(
            srcNodeModulesVar = "SRC_NM",
            dstNodeModulesVar = "DST_NM"
        ).prependIndent("            ")
        val integrityVerify = buildNodeModuleIntegrityVerifyShell(
            srcNodeModulesVar = "SRC_NM",
            dstNodeModulesVar = "DST_NM"
        ).prependIndent("            ")

        val script = """
            SRC=${shellQuote(srcProjectPath)}
            DST=${shellQuote(dstProjectPath)}
            SRC_NM="${'$'}SRC/node_modules"
            DST_NM="${'$'}DST/node_modules"

            [ -d "${'$'}SRC_NM" ] || exit 0

$packageRepair

$integrityVerify

            exit 0
        """.trimIndent()

        val result = RootShell.exec(script, timeoutMs = 25_000L)
        if (!result.ok) {
            val err = (result.stderr.ifBlank { result.stdout }).trim().take(400)
            val detail = if (err.isBlank()) "同步 Root 依赖失败（node_modules）" else err
            return OpResult(false, "同步 Root 依赖失败", detail)
        }
        return OpResult(true, "Root 依赖已同步")
    }

    private fun syncRootCoreNodeModulesIfNeeded(normalCoreDir: File, rootCoreDirPath: String): OpResult {
        val srcCorePath = normalCoreDir.absolutePath
        val dstCorePath = rootCoreDirPath
        val packageRepair = buildNodeModulePackageRepairShell(
            srcNodeModulesVar = "SRC_NM",
            dstNodeModulesVar = "DST_NM"
        ).prependIndent("            ")
        val integrityVerify = buildNodeModuleIntegrityVerifyShell(
            srcNodeModulesVar = "SRC_NM",
            dstNodeModulesVar = "DST_NM"
        ).prependIndent("            ")

        val script = """
            SRC=${shellQuote(srcCorePath)}
            DST=${shellQuote(dstCorePath)}
            SRC_NM="${'$'}SRC/node_modules"
            DST_NM="${'$'}DST/node_modules"

            [ -d "${'$'}SRC" ] || exit 0
            [ -d "${'$'}DST" ] || exit 0
            [ -d "${'$'}SRC_NM" ] || exit 0

$packageRepair

$integrityVerify

            exit 0
        """.trimIndent()

        val result = RootShell.exec(script, timeoutMs = 25_000L)
        if (!result.ok) {
            val err = (result.stderr.ifBlank { result.stdout }).trim().take(400)
            val detail = if (err.isBlank()) "同步 Root 核心依赖失败（core node_modules）" else err
            return OpResult(false, "同步 Root 核心依赖失败", detail)
        }
        return OpResult(true, "Root 核心依赖已同步")
    }
    private fun readPid(context: Context): Int? {
        val f = pidFile(context)
        if (!f.exists()) return null
        val raw = runCatching { f.readText(Charsets.UTF_8) }.getOrNull()?.trim() ?: return null
        return raw.toIntOrNull()
    }

    private fun isPidAlive(pid: Int): Boolean {
        val script = """
            PID=${shellQuote(pid.toString())}
            [ -d /proc/${'$'}PID ]
        """.trimIndent()
        return RootShell.exec(script, timeoutMs = 1200L).ok
    }

    private fun waitForPidExit(pid: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!isPidAlive(pid)) return true
            runCatching { Thread.sleep(180) }
        }
        return !isPidAlive(pid)
    }

    private fun mergeRootBootstrapDetail(primary: String, tail: String): String {
        val normalizedPrimary = primary.trim().ifBlank { "未知错误" }
        val normalizedTail = tail.trim()
        if (normalizedTail.isBlank()) return normalizedPrimary
        return "$normalizedPrimary\n最近 Root 引导日志：\n$normalizedTail"
    }

    private fun requestShutdown(port: Int) {
        runCatching {
            val conn = (URL("http://127.0.0.1:$port/__shutdown").openConnection() as HttpURLConnection).apply {
                connectTimeout = 800
                readTimeout = 800
                requestMethod = "GET"
            }
            conn.responseCode
            conn.disconnect()
        }
    }

    private fun isPortOpen(host: String, port: Int, timeoutMs: Int): Boolean {
        var socket: Socket? = null
        return try {
            socket = Socket()
            socket.soTimeout = timeoutMs
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            true
        } catch (_: Throwable) {
            false
        } finally {
            runCatching { socket?.close() }
        }
    }

    private fun waitForPort(host: String, port: Int, wantOpen: Boolean, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val open = isPortOpen(host, port, 220)
            if (open == wantOpen) return true
            runCatching { Thread.sleep(180) }
        }
        return false
    }

    private data class StartupWaitResult(
        val ready: Boolean,
        val failureDetail: String? = null
    )

    private fun waitForReadyOrFailure(context: Context, port: Int, timeoutMs: Long): StartupWaitResult {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isRunningFast(port)) {
                return StartupWaitResult(ready = true)
            }
            val startupFailure = StartupFailureStore.readRoot(context)
            if (startupFailure != null) {
                return StartupWaitResult(
                    ready = false,
                    failureDetail = startupFailure.userMessage()
                )
            }
            val pid = readPid(context)
            if (pid != null && !isPidAlive(pid)) {
                return StartupWaitResult(ready = false)
            }
            runCatching { Thread.sleep(180) }
        }
        val startupFailure = StartupFailureStore.readRoot(context)
        return StartupWaitResult(
            ready = false,
            failureDetail = startupFailure?.userMessage()
        )
    }
}
