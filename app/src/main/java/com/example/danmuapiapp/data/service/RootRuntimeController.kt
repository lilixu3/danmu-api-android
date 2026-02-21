package com.example.danmuapiapp.data.service

import android.content.Context
import android.os.Looper
import com.example.danmuapiapp.domain.model.ApiVariant
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

/**
 * Root 模式控制器：负责独立 Root 进程的启动与停止。
 */
object RootRuntimeController {

    data class OpResult(
        val ok: Boolean,
        val message: String,
        val detail: String = ""
    )

    private const val PROCESS_NAME = "danmuapi_rootnode"
    private const val PID_FILE_NAME = "root_node.pid"
    private val mainClassName = RootNodeEntry::class.java.name

    private fun pidFile(context: Context): File = File(context.filesDir, PID_FILE_NAME)
    private fun shellQuote(input: String): String = "'" + input.replace("'", "'\"'\"'") + "'"

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

    fun isRunning(context: Context, port: Int): Boolean {
        if (isRunningFast(port)) return true

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
        if (isRunningFast(port)) {
            return OpResult(true, "Root 模式已在运行")
        }

        if (!RootShell.hasRoot(3000L)) {
            return OpResult(false, "Root 授权失败", "请确认设备已 Root，并允许本应用获取 Root 权限")
        }

        if (!skipSync || !rootProjectMainJsExists(context)) {
            // 快速路径：已有可启动 root 工程时跳过同步，减少开机耗时。
            val sync = syncWorkDirToRoot(context)
            if (!sync.ok) {
                val hasReadyRootProject = rootProjectMainJsExists(context)
                if (!hasReadyRootProject) {
                    return sync
                }
            }
        }

        val rootProject = rootProjectDir(context)
        val entryPath = "$rootProject/main.js"
        val pidPath = pidFile(context).absolutePath
        val pkgName = context.packageName
        val apkPathHint = context.applicationInfo.sourceDir
        val libDirHint = context.applicationInfo.nativeLibraryDir

        val startScript = """
            PKG=${shellQuote(pkgName)}
            APP_APK_HINT=${shellQuote(apkPathHint)}
            LIB_DIR_HINT=${shellQuote(libDirHint)}
            ENTRY=${shellQuote(entryPath)}
            PID_FILE=${shellQuote(pidPath)}
            MAIN_CLASS=${shellQuote(mainClassName)}
            NICE_NAME=${shellQuote(PROCESS_NAME)}

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
            cd "${'$'}(dirname "${'$'}ENTRY")" >/dev/null 2>&1 || true

            if command -v setsid >/dev/null 2>&1; then
              setsid "${'$'}APPPROC" /system/bin --nice-name="${'$'}NICE_NAME" "${'$'}MAIN_CLASS" --entry "${'$'}ENTRY" --pidfile "${'$'}PID_FILE" >/dev/null 2>&1 < /dev/null &
            elif command -v nohup >/dev/null 2>&1; then
              nohup "${'$'}APPPROC" /system/bin --nice-name="${'$'}NICE_NAME" "${'$'}MAIN_CLASS" --entry "${'$'}ENTRY" --pidfile "${'$'}PID_FILE" >/dev/null 2>&1 < /dev/null &
            else
              "${'$'}APPPROC" /system/bin --nice-name="${'$'}NICE_NAME" "${'$'}MAIN_CLASS" --entry "${'$'}ENTRY" --pidfile "${'$'}PID_FILE" >/dev/null 2>&1 < /dev/null &
            fi
            sleep 0.25
        """.trimIndent()

        val startResult = RootShell.exec(startScript, timeoutMs = 15000L)
        if (!startResult.ok) {
            val err = (startResult.stderr.ifBlank { startResult.stdout }).trim().take(400)
            return OpResult(false, "Root 模式启动失败", if (err.isBlank()) "未知错误" else err)
        }

        if (quickMode) {
            return OpResult(true, "Root 模式已触发启动")
        }

        val ready = waitForPort("127.0.0.1", port, wantOpen = true, timeoutMs = 12000L)
        return if (ready) {
            OpResult(true, "Root 模式已启动")
        } else {
            val detail = "端口 $port 未就绪，请在应用控制台查看 /api/logs 日志后重试"
            OpResult(false, "Root 模式启动超时", detail)
        }
    }

    fun stop(context: Context, port: Int): OpResult {
        requestShutdown(port)

        if (waitForPort("127.0.0.1", port, wantOpen = false, timeoutMs = 4000L)) {
            runCatching { pidFile(context).delete() }
            return OpResult(true, "已停止")
        }

        val pid = readPid(context)
        if (pid == null) {
            return OpResult(true, "已停止")
        }

        if (!RootShell.hasRoot(2500L)) {
            return OpResult(false, "停止失败", "缺少 Root 权限")
        }

        RootShell.exec("kill -TERM $pid 2>/dev/null || true", timeoutMs = 5000L)
        if (waitForPidExit(pid, timeoutMs = 3000L) || waitForPort("127.0.0.1", port, wantOpen = false, timeoutMs = 2500L)) {
            runCatching { pidFile(context).delete() }
            return OpResult(true, "已停止")
        }

        RootShell.exec("kill -KILL $pid 2>/dev/null || true", timeoutMs = 5000L)
        val stopped = waitForPidExit(pid, timeoutMs = 1500L) || !isPidAlive(pid)
        if (stopped) runCatching { pidFile(context).delete() }

        return if (stopped) {
            OpResult(true, "已停止")
        } else {
            OpResult(false, "停止失败", "进程未退出")
        }
    }

    fun restart(context: Context, port: Int): OpResult {
        stop(context, port)
        return start(context, port, quickMode = false)
    }

    fun getPid(context: Context): Int? = readPid(context)
    fun getPidFileLastModified(context: Context): Long? {
        val f = pidFile(context)
        if (!f.exists()) return null
        val ts = f.lastModified()
        return ts.takeIf { it > 0L }
    }

    fun syncRuntimeEnvOnly(context: Context): OpResult {
        val project = runCatching {
            val sourceProjectDir = RuntimePaths.normalProjectDir(context)
            val dir = NodeProjectManager.ensureProjectExtracted(context, sourceProjectDir)
            NodeProjectManager.writeRuntimeEnv(context, dir)
            dir
        }.getOrElse {
            return OpResult(false, "运行时准备失败", it.message ?: "无法初始化工作目录")
        }

        val envSyncResult = syncRuntimeEnvToRoot(context, project)
        if (!envSyncResult.ok) return envSyncResult
        val normalize = normalizeRootProjectPermissions(context)
        if (!normalize.ok) return normalize
        return OpResult(true, "Root 环境同步完成")
    }

    fun syncWorkDirToRoot(context: Context): OpResult {
        val project = runCatching {
            val sourceProjectDir = RuntimePaths.normalProjectDir(context)
            val dir = NodeProjectManager.ensureProjectExtracted(context, sourceProjectDir)
            mergeRootEnvToWorkDirIfNeeded(context, dir)
            NodeProjectManager.migrateAllCoreLayouts(dir)
            NodeProjectManager.writeRuntimeEnv(context, dir)
            dir
        }.getOrElse {
            return OpResult(false, "运行时准备失败", it.message ?: "无法初始化工作目录")
        }
        val syncResult = syncProjectToRoot(context, project)
        if (!syncResult.ok) return syncResult
        val envSyncResult = syncRuntimeEnvToRoot(context, project)
        if (!envSyncResult.ok) return envSyncResult
        val coreReady = ensureSelectedCoreReady(context, project)
        if (!coreReady.ok) return coreReady
        val normalize = normalizeRootProjectPermissions(context)
        if (!normalize.ok) return normalize
        return OpResult(true, "同步完成")
    }

    private fun syncProjectToRoot(context: Context, srcProjectDir: File): OpResult {
        val src = srcProjectDir.absolutePath
        val dst = rootProjectDir(context)
        val bootstrap = !rootProjectMainJsExists(context)

        val script = if (bootstrap) {
            // 首次引导：完整复制，确保 root 目录具备可启动的完整运行时。
            """
                SRC=${shellQuote(src)}
                DST=${shellQuote(dst)}
                rm -rf "${'$'}DST" 2>/dev/null || true
                mkdir -p "${'$'}DST" 2>/dev/null || true
                cp -a "${'$'}SRC/." "${'$'}DST/" 2>/dev/null || cp -r "${'$'}SRC/." "${'$'}DST/" 2>/dev/null || true
                test -f "${'$'}DST/main.js"
            """.trimIndent()
        } else {
            // 增量同步：仅同步 App 托管文件，保留 Root 工作目录中的 config 与 danmu_api_*。
            // 注意：config/.env 会在 syncRuntimeEnvToRoot 中单独同步，确保运行时关键变量生效。
            """
                SRC=${shellQuote(src)}
                DST=${shellQuote(dst)}
                TMP="${'$'}DST/.tmp_app_sync"
                mkdir -p "${'$'}DST" "${'$'}DST/config" "${'$'}DST/logs" 2>/dev/null || true
                rm -rf "${'$'}TMP" 2>/dev/null || true
                mkdir -p "${'$'}TMP" 2>/dev/null || true
                cp -a "${'$'}SRC/." "${'$'}TMP/" 2>/dev/null || cp -r "${'$'}SRC/." "${'$'}TMP/" 2>/dev/null || true
                rm -rf "${'$'}TMP/config" "${'$'}TMP/logs" "${'$'}TMP"/danmu_api_* 2>/dev/null || true
                cp -a "${'$'}TMP/." "${'$'}DST/" 2>/dev/null || cp -r "${'$'}TMP/." "${'$'}DST/" 2>/dev/null || true
                rm -rf "${'$'}TMP" 2>/dev/null || true
                test -f "${'$'}DST/main.js"
            """.trimIndent()
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

    private fun normalizeRootProjectPermissions(context: Context): OpResult {
        val rootProjectPath = rootProjectDir(context)
        val script = """
            DST=${shellQuote(rootProjectPath)}
            [ -d "${'$'}DST" ] || exit 0

            # 关键修复：统一 Root 运行目录权限，避免 system 进程发起 su 时出现 Permission denied。
            chown -R 0:0 "${'$'}DST" 2>/dev/null || true
            chmod -R u+rwX,go+rX "${'$'}DST" 2>/dev/null || true

            [ -d "${'$'}DST/config" ] && chmod 0755 "${'$'}DST/config" 2>/dev/null || true
            [ -f "${'$'}DST/config/.env" ] && chmod 0640 "${'$'}DST/config/.env" 2>/dev/null || true
            [ -d "${'$'}DST/logs" ] && chmod 0775 "${'$'}DST/logs" 2>/dev/null || true
            exit 0
        """.trimIndent()
        val result = RootShell.exec(script, timeoutMs = 15_000L)
        if (!result.ok) {
            val err = (result.stderr.ifBlank { result.stdout }).trim().take(400)
            return OpResult(false, "Root 目录权限修复失败", if (err.isBlank()) "未知错误" else err)
        }
        return OpResult(true, "Root 目录权限已修复")
    }

    private fun mergeRootEnvToWorkDirIfNeeded(context: Context, workProjectDir: File) {
        val rootEnvText = readRuntimeEnvFromRoot(context) ?: return
        if (rootEnvText.isBlank()) return
        val normalizedRootEnv = rootEnvText.replace("\r\n", "\n").let { text ->
            if (text.endsWith('\n')) text else "$text\n"
        }
        val workEnvFile = File(workProjectDir, "config/.env")
        val currentWorkEnv = runCatching {
            if (workEnvFile.exists()) workEnvFile.readText(Charsets.UTF_8) else ""
        }.getOrElse { "" }
        if (currentWorkEnv == normalizedRootEnv) return
        runCatching {
            workEnvFile.parentFile?.mkdirs()
            workEnvFile.writeText(normalizedRootEnv, Charsets.UTF_8)
        }
    }

    private fun readRuntimeEnvFromRoot(context: Context): String? {
        val candidates = buildRootEnvPathCandidates(context)
        for (path in candidates) {
            val script = """
                FILE=${shellQuote(path)}
                if [ ! -f "${'$'}FILE" ]; then
                  exit 3
                fi
                cat "${'$'}FILE"
            """.trimIndent()
            val result = RootShell.exec(script, timeoutMs = 5000L)
            if (result.ok) return result.stdout
        }
        return null
    }

    private fun buildRootEnvPathCandidates(context: Context): List<String> {
        return listOf("${rootProjectDir(context)}/config/.env")
    }

    private fun ensureSelectedCoreReady(context: Context, normalProjectDir: File): OpResult {
        val prefs = context.getSharedPreferences("runtime", Context.MODE_PRIVATE)
        val variant = ApiVariant.entries.find { it.key == prefs.getString("variant", "stable") }
            ?: ApiVariant.Stable
        val normalCoreDir = File(normalProjectDir, "danmu_api_${variant.key}")
        val rootCoreDirPath = "${rootProjectDir(context)}/danmu_api_${variant.key}"

        if (rootCoreHasWorker(rootCoreDirPath)) {
            return OpResult(true, "同步完成")
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
            [ -f "${'$'}DST/worker.js" ] || [ -f "${'$'}DST/danmu_api/worker.js" ] || [ -f "${'$'}DST/danmu-api/worker.js" ]
        """.trimIndent()
        val result = RootShell.exec(script, timeoutMs = 25_000L)
        if (!result.ok) {
            val err = (result.stderr.ifBlank { result.stdout }).trim().take(400)
            return OpResult(false, "补齐 Root 核心失败", if (err.isBlank()) "未知错误" else err)
        }
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
}
