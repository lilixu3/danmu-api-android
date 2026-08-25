package com.example.danmuapiapp.data.service

import android.content.Context
import android.os.Looper
import com.example.danmuapiapp.data.repository.RuntimeOwnership
import com.example.danmuapiapp.data.repository.determineRuntimeOwnershipFromHealth
import com.example.danmuapiapp.data.repository.isRuntimeOwnershipAcceptedForRoot
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.RuntimeListenMode
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import com.example.danmuapiapp.data.repository.isRootPassiveLivenessLikely
import com.example.danmuapiapp.data.util.RuntimeTokenNormalizer
import com.example.danmuapiapp.data.util.ShellUtils.shellQuote
import java.net.URL
import java.security.MessageDigest

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
    private const val ROOT_SYNC_TIMEOUT_MS = 90_000L
    private val mainClassName = RootNodeEntry::class.java.name

    private data class RuntimeEnvSnapshot(
        val variant: String,
        val port: Int,
        val listenHost: String,
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

    internal fun buildAtomicCoreSyncShell(
        sourcePath: String,
        destinationPath: String,
        operationToken: String
    ): String {
        return """
            SRC=${shellQuote(sourcePath)}
            DST=${shellQuote(destinationPath)}
            NEW="${'$'}DST.new-${operationToken}"
            BACKUP="${'$'}DST.backup-${operationToken}"
            SRC_LIST="${'$'}DST.source-${operationToken}.cksum"
            DST_LIST="${'$'}DST.target-${operationToken}.cksum"
            HAD_DST=0
            SWAPPED=0

            cleanup_sync() {
              STATUS=${'$'}?
              trap - EXIT HUP INT TERM
              rm -rf "${'$'}NEW" 2>/dev/null || true
              rm -f "${'$'}SRC_LIST" "${'$'}DST_LIST" 2>/dev/null || true
              if [ "${'$'}STATUS" -ne 0 ] && [ "${'$'}SWAPPED" = "1" ]; then
                rm -rf "${'$'}DST" 2>/dev/null || true
                if [ "${'$'}HAD_DST" = "1" ] && [ -e "${'$'}BACKUP" ]; then
                  mv "${'$'}BACKUP" "${'$'}DST" 2>/dev/null || true
                fi
              fi
              exit "${'$'}STATUS"
            }
            trap cleanup_sync EXIT HUP INT TERM

            [ -d "${'$'}SRC" ] || exit 2
            mkdir -p "${'$'}(dirname "${'$'}DST")" || exit 3
            rm -rf "${'$'}NEW" "${'$'}BACKUP" || exit 4
            mkdir -p "${'$'}NEW" || exit 5
            if ! cp -a "${'$'}SRC/." "${'$'}NEW/" 2>/dev/null; then
              rm -rf "${'$'}NEW" || exit 6
              mkdir -p "${'$'}NEW" || exit 7
              cp -r "${'$'}SRC/." "${'$'}NEW/" || exit 8
            fi

            ([ -f "${'$'}NEW/worker.js" ] || [ -f "${'$'}NEW/danmu_api/worker.js" ] || [ -f "${'$'}NEW/danmu-api/worker.js" ]) || exit 9
            (cd "${'$'}SRC" && find . -type f -exec cksum {} + | LC_ALL=C sort) > "${'$'}SRC_LIST" || exit 10
            (cd "${'$'}NEW" && find . -type f -exec cksum {} + | LC_ALL=C sort) > "${'$'}DST_LIST" || exit 11
            cmp -s "${'$'}SRC_LIST" "${'$'}DST_LIST" || exit 12
            rm -f "${'$'}SRC_LIST" "${'$'}DST_LIST" || exit 13

            if [ -e "${'$'}DST" ]; then
              mv "${'$'}DST" "${'$'}BACKUP" || exit 14
              HAD_DST=1
            fi
            SWAPPED=1
            mv "${'$'}NEW" "${'$'}DST" || exit 15
            chmod -R u+rwX,go+rX "${'$'}DST" || exit 16
            SWAPPED=0
            rm -rf "${'$'}BACKUP" 2>/dev/null || true
            trap - EXIT HUP INT TERM
            exit 0
        """.trimIndent()
    }

    internal fun buildAtomicProjectBootstrapShell(
        sourcePath: String,
        destinationPath: String,
        operationToken: String
    ): String {
        return """
            SRC=${shellQuote(sourcePath)}
            DST=${shellQuote(destinationPath)}
            NEW="${'$'}DST.new-${operationToken}"
            BACKUP="${'$'}DST.backup-${operationToken}"
            SRC_LIST="${'$'}DST.source-${operationToken}.cksum"
            DST_LIST="${'$'}DST.target-${operationToken}.cksum"
            HAD_DST=0
            SWAPPED=0
            cleanup_sync() {
              STATUS=${'$'}?
              trap - EXIT HUP INT TERM
              rm -rf "${'$'}NEW" 2>/dev/null || true
              rm -f "${'$'}SRC_LIST" "${'$'}DST_LIST" 2>/dev/null || true
              if [ "${'$'}STATUS" -ne 0 ] && [ "${'$'}SWAPPED" = "1" ]; then
                rm -rf "${'$'}DST" 2>/dev/null || true
                if [ "${'$'}HAD_DST" = "1" ] && [ -e "${'$'}BACKUP" ]; then
                  mv "${'$'}BACKUP" "${'$'}DST" 2>/dev/null || true
                fi
              fi
              exit "${'$'}STATUS"
            }
            trap cleanup_sync EXIT HUP INT TERM

            [ -f "${'$'}SRC/main.js" ] || exit 2
            mkdir -p "${'$'}(dirname "${'$'}DST")" || exit 3
            rm -rf "${'$'}NEW" "${'$'}BACKUP" || exit 4
            mkdir -p "${'$'}NEW" || exit 5
            if ! cp -a "${'$'}SRC/." "${'$'}NEW/" 2>/dev/null; then
              rm -rf "${'$'}NEW" || exit 6
              mkdir -p "${'$'}NEW" || exit 7
              cp -r "${'$'}SRC/." "${'$'}NEW/" || exit 8
            fi
            rm -rf "${'$'}NEW/.cache" || exit 9
            mkdir -p "${'$'}NEW/.cache" || exit 10
            if [ -f "${'$'}SRC/.cache/${FavoriteCacheStore.FILE_NAME}" ]; then
              cp -f "${'$'}SRC/.cache/${FavoriteCacheStore.FILE_NAME}" "${'$'}NEW/.cache/${FavoriteCacheStore.FILE_NAME}" 2>/dev/null || \
                cat "${'$'}SRC/.cache/${FavoriteCacheStore.FILE_NAME}" > "${'$'}NEW/.cache/${FavoriteCacheStore.FILE_NAME}" || exit 11
            fi
            [ -f "${'$'}NEW/main.js" ] || exit 12

            (cd "${'$'}SRC" && find . -path './.cache' -prune -o -type f -exec cksum {} + | LC_ALL=C sort) > "${'$'}SRC_LIST" || exit 13
            (cd "${'$'}NEW" && find . -path './.cache' -prune -o -type f -exec cksum {} + | LC_ALL=C sort) > "${'$'}DST_LIST" || exit 14
            cmp -s "${'$'}SRC_LIST" "${'$'}DST_LIST" || exit 15
            rm -f "${'$'}SRC_LIST" "${'$'}DST_LIST" || exit 16

            if [ -e "${'$'}DST" ]; then
              mv "${'$'}DST" "${'$'}BACKUP" || exit 17
              HAD_DST=1
            fi
            SWAPPED=1
            mv "${'$'}NEW" "${'$'}DST" || exit 18
            SWAPPED=0
            rm -rf "${'$'}BACKUP" 2>/dev/null || true
            trap - EXIT HUP INT TERM
            exit 0
        """.trimIndent()
    }

    fun syncCoreDirectoryFromNormal(sourceDir: File, destinationPath: String): OpResult {
        val token = "${android.os.Process.myPid()}-${System.currentTimeMillis()}"
        val result = RootShell.exec(
            buildAtomicCoreSyncShell(
                sourcePath = sourceDir.absolutePath,
                destinationPath = destinationPath,
                operationToken = token
            ),
            timeoutMs = ROOT_SYNC_TIMEOUT_MS
        )
        if (!result.ok) {
            val detail = (result.stderr.ifBlank { result.stdout }).trim().take(500)
                .ifBlank { "复制或完整性校验失败" }
            return OpResult(false, "同步 Root 核心失败", detail)
        }
        return OpResult(true, "Root 核心已原子同步")
    }

    internal fun buildNodeModulesFingerprint(
        nodeModulesDir: File,
        identityFiles: List<File>
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun addFile(label: String, file: File) {
            digest.update(label.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            if (file.isFile) {
                file.inputStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                    }
                }
            }
            digest.update(0.toByte())
        }

        identityFiles.sortedBy { it.absolutePath }.forEach { file ->
            addFile("identity:${file.name}", file)
        }
        nodeModulesDir.listFiles().orEmpty()
            .filter { it.isDirectory && !it.name.startsWith('.') }
            .flatMap { entry ->
                if (entry.name.startsWith('@')) {
                    entry.listFiles().orEmpty().filter { it.isDirectory }
                } else {
                    listOf(entry)
                }
            }
            .map { packageDir -> File(packageDir, "package.json") }
            .filter { it.isFile }
            .sortedBy { it.relativeTo(nodeModulesDir).invariantSeparatorsPath }
            .forEach { packageJson ->
                addFile(packageJson.relativeTo(nodeModulesDir).invariantSeparatorsPath, packageJson)
            }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    internal fun buildAtomicNodeModulesSyncShell(
        sourcePath: String,
        destinationPath: String,
        fingerprint: String,
        operationToken: String
    ): String {
        return """
            SRC=${shellQuote(sourcePath)}
            DST=${shellQuote(destinationPath)}
            EXPECTED=${shellQuote(fingerprint)}
            MARKER="${'$'}DST/.danmuapiapp-sync-fingerprint"
            [ -d "${'$'}SRC" ] || exit 0
            if [ -f "${'$'}MARKER" ] && [ -d "${'$'}DST" ]; then
              MARKER_FINGERPRINT=${'$'}(sed -n '1p' "${'$'}MARKER" 2>/dev/null)
              MARKER_FILE_COUNT=${'$'}(sed -n '2p' "${'$'}MARKER" 2>/dev/null)
              CURRENT_FILE_COUNT=${'$'}(find "${'$'}DST" -type f ! -name .danmuapiapp-sync-fingerprint 2>/dev/null | wc -l | tr -d '[:space:]')
              if [ "${'$'}MARKER_FINGERPRINT" = "${'$'}EXPECTED" ] &&
                 [ -n "${'$'}MARKER_FILE_COUNT" ] &&
                 [ "${'$'}MARKER_FILE_COUNT" = "${'$'}CURRENT_FILE_COUNT" ]; then
                exit 0
              fi
            fi

            NEW="${'$'}DST.new-${operationToken}"
            BACKUP="${'$'}DST.backup-${operationToken}"
            SRC_LIST="${'$'}DST.source-${operationToken}.cksum"
            DST_LIST="${'$'}DST.target-${operationToken}.cksum"
            HAD_DST=0
            SWAPPED=0
            cleanup_sync() {
              STATUS=${'$'}?
              trap - EXIT HUP INT TERM
              rm -rf "${'$'}NEW" 2>/dev/null || true
              rm -f "${'$'}SRC_LIST" "${'$'}DST_LIST" 2>/dev/null || true
              if [ "${'$'}STATUS" -ne 0 ] && [ "${'$'}SWAPPED" = "1" ]; then
                rm -rf "${'$'}DST" 2>/dev/null || true
                if [ "${'$'}HAD_DST" = "1" ] && [ -e "${'$'}BACKUP" ]; then
                  mv "${'$'}BACKUP" "${'$'}DST" 2>/dev/null || true
                fi
              fi
              exit "${'$'}STATUS"
            }
            trap cleanup_sync EXIT HUP INT TERM

            (cd "${'$'}SRC" && find . -type f ! -name .danmuapiapp-sync-fingerprint -exec cksum {} + | LC_ALL=C sort) > "${'$'}SRC_LIST" || exit 2
            if [ -d "${'$'}DST" ]; then
              (cd "${'$'}DST" && find . -type f ! -name .danmuapiapp-sync-fingerprint -exec cksum {} + | LC_ALL=C sort) > "${'$'}DST_LIST" || exit 3
              if cmp -s "${'$'}SRC_LIST" "${'$'}DST_LIST"; then
                VERIFIED_FILE_COUNT=${'$'}(find "${'$'}DST" -type f ! -name .danmuapiapp-sync-fingerprint | wc -l | tr -d '[:space:]')
                printf '%s\n%s\n' "${'$'}EXPECTED" "${'$'}VERIFIED_FILE_COUNT" > "${'$'}MARKER" || exit 4
                rm -f "${'$'}SRC_LIST" "${'$'}DST_LIST" || exit 5
                trap - EXIT HUP INT TERM
                exit 0
              fi
              rm -f "${'$'}DST_LIST" || exit 6
            fi

            mkdir -p "${'$'}(dirname "${'$'}DST")" || exit 7
            rm -rf "${'$'}NEW" "${'$'}BACKUP" || exit 8
            mkdir -p "${'$'}NEW" || exit 9
            if ! cp -a "${'$'}SRC/." "${'$'}NEW/" 2>/dev/null; then
              rm -rf "${'$'}NEW" || exit 10
              mkdir -p "${'$'}NEW" || exit 11
              cp -r "${'$'}SRC/." "${'$'}NEW/" || exit 12
            fi
            rm -f "${'$'}NEW/.danmuapiapp-sync-fingerprint" 2>/dev/null || true

            (cd "${'$'}NEW" && find . -type f ! -name .danmuapiapp-sync-fingerprint -exec cksum {} + | LC_ALL=C sort) > "${'$'}DST_LIST" || exit 14
            cmp -s "${'$'}SRC_LIST" "${'$'}DST_LIST" || exit 15
            VERIFIED_FILE_COUNT=${'$'}(find "${'$'}NEW" -type f ! -name .danmuapiapp-sync-fingerprint | wc -l | tr -d '[:space:]')
            printf '%s\n%s\n' "${'$'}EXPECTED" "${'$'}VERIFIED_FILE_COUNT" > "${'$'}NEW/.danmuapiapp-sync-fingerprint" || exit 16
            rm -f "${'$'}SRC_LIST" "${'$'}DST_LIST" || exit 17

            if [ -e "${'$'}DST" ]; then
              mv "${'$'}DST" "${'$'}BACKUP" || exit 18
              HAD_DST=1
            fi
            SWAPPED=1
            mv "${'$'}NEW" "${'$'}DST" || exit 19
            chmod -R u+rwX,go+rX "${'$'}DST" || exit 20
            SWAPPED=0
            rm -rf "${'$'}BACKUP" 2>/dev/null || true
            trap - EXIT HUP INT TERM
            exit 0
        """.trimIndent()
    }

    private fun syncNodeModulesAtomically(
        sourceDir: File,
        destinationPath: String,
        identityFiles: List<File>,
        failureMessage: String
    ): OpResult {
        if (!sourceDir.isDirectory) return OpResult(true, "没有需要同步的本地依赖")
        val fingerprint = buildNodeModulesFingerprint(sourceDir, identityFiles)
        val token = "${android.os.Process.myPid()}-${System.currentTimeMillis()}"
        val result = RootShell.exec(
            buildAtomicNodeModulesSyncShell(
                sourcePath = sourceDir.absolutePath,
                destinationPath = destinationPath,
                fingerprint = fingerprint,
                operationToken = token
            ),
            timeoutMs = ROOT_SYNC_TIMEOUT_MS
        )
        if (!result.ok) {
            val detail = (result.stderr.ifBlank { result.stdout }).trim().take(500)
                .ifBlank { "复制或完整性校验失败" }
            return OpResult(false, failureMessage, detail)
        }
        return OpResult(true, "Root 依赖已同步")
    }

    fun isRunningFast(port: Int): Boolean {
        return isPortOpen("127.0.0.1", port, 220)
    }

    fun isRuntimeOwnedByAppPassive(context: Context, port: Int): Boolean {
        return isRuntimeOwnershipAcceptedForRoot(readRuntimeOwnership(context, port))
    }

    private fun readRuntimeOwnership(context: Context, port: Int): RuntimeOwnership {
        if (port !in 1..65535) return RuntimeOwnership.Foreign
        val expectedIdentity = RuntimeIdentityStore.ensureInstanceId(context).trim()
        val expectedHome = RuntimePaths.rootProjectDir(context).absolutePath
        val body = readRuntimeHealthBody(port) ?: return RuntimeOwnership.Unknown
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
        if (isRuntimeOwnedByAppPassive(context, port)) return true

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

        // 如果没有 Root 权限、授权超时或 su 会话不可用，不要在状态探测路径里
        // 删除 pid 文件或把仍可能存活的 Root 运行时判死。保留非 su 的被动
        // liveness 结果，避免“没 Root 权限 + 身份暂时不匹配”导致 UI 异常停止。
        if (result.timedOut || result.exitCode == -1) {
            return isProbablyRunning(context, port)
        }

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

    /** Cheap startup hint that never opens a socket or a Root shell. */
    fun hasPersistedRuntimeHint(context: Context): Boolean {
        return readPid(context) != null && (
            getProcessStartedAtMs(context) != null || pidFile(context).lastModified() > 0L
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
        if (isRuntimeOwnedByAppPassive(context, port)) {
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

        if (isRunning(context, port)) {
            return OpResult(
                ok = true,
                message = "Root 模式已在运行",
                startOutcome = StartOutcome.AlreadyRunning
            )
        }

        if (isRunningFast(port)) {
            val ownership = readRuntimeOwnership(context, port)
            val explicitlyForeign = ownership == RuntimeOwnership.Foreign
            return OpResult(
                ok = false,
                message = if (explicitlyForeign) {
                    "Root 端口已被其他实例占用"
                } else {
                    "无法确认 Root 端口归属"
                },
                detail = if (explicitlyForeign) {
                    "端口 $port 已有其他实例在运行，请先停止外部进程后再启动"
                } else {
                    "端口 $port 正在监听，但健康检查暂时不可用；请稍后重试，避免重复启动"
                }
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

        val dependencyCheck = verifyRootRuntimeDependencies(context)
        if (!dependencyCheck.ok) {
            AppDiagnosticLogger.e(
                context,
                "RootRuntimeController",
                "Root 运行时依赖检查失败：${dependencyCheck.detail.ifBlank { dependencyCheck.message }}"
            )
            return dependencyCheck
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

        if (!pidSafeToSignal(context, pid)) {
            // pid 文件已过期（原进程退出后系统复用了该 PID），
            // 不能对陌生进程发信号；端口侧的 __shutdown 已尝试过，按停止成功收尾。
            AppDiagnosticLogger.w(context, "RootRuntimeController", "pid=$pid 的 cmdline 与 Root 运行时不匹配，按过期 pid 记录处理")
            clearRuntimeMarkers(context)
            return OpResult(true, "已停止")
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
            if (pid != null && RootShell.hasRoot(1500L) && pidSafeToSignal(context, pid)) {
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
            LISTEN_HOST=${shellQuote(snapshot.listenHost)}
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
            upsert_env "DANMU_API_HOST" "${'$'}LISTEN_HOST"
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
        val listenHost = RuntimeListenMode.fromKey(
            prefs.getString(RuntimeListenMode.PREFERENCE_KEY, null)
        )?.bindHost ?: RuntimeListenMode.Ipv4Only.bindHost
        val logLevel = prefs.getString("log_level", "info").orEmpty().trim().ifBlank { "info" }
        val tokenConfigured = prefs.contains("token")
        val token = RuntimeTokenNormalizer.normalizeInput(prefs.getString("token", ""))
        return RuntimeEnvSnapshot(
            variant = variant,
            port = port,
            listenHost = listenHost,
            logLevel = logLevel,
            tokenConfigured = tokenConfigured,
            token = token
        )
    }

    internal fun buildRootProjectIncrementalSyncShell(srcProjectPath: String, dstProjectPath: String): String {
        return """
            SRC=${shellQuote(srcProjectPath)}
            DST=${shellQuote(dstProjectPath)}
            mkdir -p "${'$'}DST" "${'$'}DST/config" "${'$'}DST/logs" || exit 2

            # 热启动只需要同步 App 托管的顶层包装文件；不要递归复制 node_modules/core/cache。
            # 使用普通 glob 兼容 Android toybox/mksh，避免依赖 find/rsync。
            for FILE in "${'$'}SRC"/* "${'$'}SRC"/.[!.]* "${'$'}SRC"/..?*; do
              [ -f "${'$'}FILE" ] || continue
              NAME="${'$'}{FILE##*/}"
              TMP="${'$'}DST/.wrapper-${'$'}$-${'$'}NAME"
              rm -f "${'$'}TMP" 2>/dev/null || true
              if ! cp -f "${'$'}FILE" "${'$'}TMP" 2>/dev/null; then
                cat "${'$'}FILE" > "${'$'}TMP" || exit 3
              fi
              cmp -s "${'$'}FILE" "${'$'}TMP" || exit 4
              mv -f "${'$'}TMP" "${'$'}DST/${'$'}NAME" || exit 5
            done

            test -f "${'$'}DST/main.js" || exit 6
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
            // 首次引导使用同目录 staging，完整校验后再切换，不破坏旧 Root 目录。
            buildAtomicProjectBootstrapShell(
                sourcePath = src,
                destinationPath = dst,
                operationToken = "${android.os.Process.myPid()}-${System.currentTimeMillis()}"
            )
        } else {
            // 增量同步：只同步 App 托管的顶层包装文件，保留 Root 工作目录中的
            // config、danmu_api_*、node_modules 与 .cache。避免每次启动都复制整个 core/node_modules 到临时目录。
            buildRootProjectIncrementalSyncShell(srcProjectPath = src, dstProjectPath = dst)
        }

        val result = RootShell.exec(script, timeoutMs = if (bootstrap) ROOT_SYNC_TIMEOUT_MS else 25_000L)
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
            val requirementsSync = syncRootCoreRequirements(normalCoreDir, rootCoreDirPath)
            if (!requirementsSync.ok) return requirementsSync
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

        val coreSync = syncCoreDirectoryFromNormal(normalCoreDir, rootCoreDirPath)
        if (!coreSync.ok) return coreSync
        val depsSync = syncRootCoreNodeModulesIfNeeded(normalCoreDir, rootCoreDirPath)
        if (!depsSync.ok) return depsSync
        return OpResult(true, "同步完成")
    }

    private fun syncRootCoreRequirements(normalCoreDir: File, rootCoreDirPath: String): OpResult {
        val source = File(normalCoreDir, NodeProjectManager.CORE_RUNTIME_REQUIREMENTS_FILE)
        if (!source.isFile) return OpResult(true, "核心依赖清单无需同步")
        val destination = "$rootCoreDirPath/${NodeProjectManager.CORE_RUNTIME_REQUIREMENTS_FILE}"
        val script = """
            SRC=${shellQuote(source.absolutePath)}
            DST=${shellQuote(destination)}
            TMP="${'$'}DST.tmp-${System.nanoTime()}"
            [ -f "${'$'}SRC" ] || exit 0
            [ -d "${'$'}(dirname "${'$'}DST")" ] || exit 2
            cp "${'$'}SRC" "${'$'}TMP" || exit 3
            cmp -s "${'$'}SRC" "${'$'}TMP" || exit 4
            chmod 0644 "${'$'}TMP" || exit 5
            mv "${'$'}TMP" "${'$'}DST" || exit 6
        """.trimIndent()
        val result = RootShell.exec(script, timeoutMs = 5000L)
        if (!result.ok) {
            val detail = (result.stderr.ifBlank { result.stdout }).trim().take(400)
            return OpResult(
                false,
                "同步 Root 核心依赖清单失败",
                detail.ifBlank { "Root 文件写入失败" }
            )
        }
        return OpResult(true, "Root 核心依赖清单已同步")
    }

    internal fun buildRootRuntimeDependencyProbeShell(
        rootProjectPath: String,
        variantKey: String
    ): String {
        return """
            PROJECT=${shellQuote(rootProjectPath)}
            CORE=${shellQuote("$rootProjectPath/danmu_api_$variantKey")}
            REQUIREMENTS="${'$'}CORE/${NodeProjectManager.CORE_RUNTIME_REQUIREMENTS_FILE}"
            [ -f "${'$'}REQUIREMENTS" ] || exit 0
            MISSING=0
            while IFS= read -r DEP || [ -n "${'$'}DEP" ]; do
              DEP="${'$'}(printf '%s' "${'$'}DEP" | tr -d '\r')"
              [ -n "${'$'}DEP" ] || continue
              if ! printf '%s' "${'$'}DEP" | grep -Eq '^(@[A-Za-z0-9._-]+/)?[A-Za-z0-9._-]+${'$'}'; then
                printf '%s\n' '__invalid_dependency_name__'
                MISSING=1
                continue
              fi
              if [ ! -f "${'$'}CORE/node_modules/${'$'}DEP/package.json" ] && \
                 [ ! -f "${'$'}PROJECT/node_modules/${'$'}DEP/package.json" ]; then
                printf '%s\n' "${'$'}DEP"
                MISSING=1
              fi
            done < "${'$'}REQUIREMENTS"
            [ "${'$'}MISSING" = "0" ]
        """.trimIndent()
    }

    private fun verifyRootRuntimeDependencies(context: Context): OpResult {
        val prefs = context.getSharedPreferences("runtime", Context.MODE_PRIVATE)
        val variant = ApiVariant.entries.find { it.key == prefs.getString("variant", "stable") }
            ?: ApiVariant.Stable
        val result = RootShell.exec(
            buildRootRuntimeDependencyProbeShell(rootProjectDir(context), variant.key),
            timeoutMs = 5000L
        )
        if (result.ok) return OpResult(true, "Root 运行时依赖完整")

        val missing = result.stdout.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .filterNot { it == "__invalid_dependency_name__" }
            .distinct()
            .sorted()
            .toList()
        if (missing.isNotEmpty()) {
            return OpResult(
                false,
                "Root 运行时依赖缺失",
                RuntimeDependencyHealthChecker.missingMessage(missing)
            )
        }
        val detail = (result.stderr.ifBlank { result.stdout }).trim().take(400)
        return OpResult(
            false,
            "Root 运行时依赖检查失败",
            detail.ifBlank { "Root 依赖清单无效或无法读取" }
        )
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
        return syncNodeModulesAtomically(
            sourceDir = File(normalProjectDir, "node_modules"),
            destinationPath = "${rootProjectDir(context)}/node_modules",
            identityFiles = listOf(
                File(normalProjectDir, "package-lock.json"),
                File(normalProjectDir, "package.json"),
                File(normalProjectDir, ".app_version"),
                File(normalProjectDir, "runtime_asset_layout.txt")
            ),
            failureMessage = "同步 Root 依赖失败"
        )
    }

    private fun syncRootCoreNodeModulesIfNeeded(normalCoreDir: File, rootCoreDirPath: String): OpResult {
        return syncNodeModulesAtomically(
            sourceDir = File(normalCoreDir, "node_modules"),
            destinationPath = "$rootCoreDirPath/node_modules",
            identityFiles = listOf(
                File(normalCoreDir, "package.json"),
                File(normalCoreDir, ".danmuapiapp-runtime-pack.json"),
                File(normalCoreDir, ".danmuapiapp-runtime-import.json")
            ),
            failureMessage = "同步 Root 核心依赖失败"
        )
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

    /**
     * kill 前校验 /proc/$PID/cmdline 是否为本应用的 Root 运行时，防止 pid 文件残留
     * 且系统已把该 PID 复用给无关进程时以 root 权限误杀。与 [isRunning] 的身份校验一致。
     *
     * 返回 false 仅表示“cmdline 明确不匹配”（应按过期 pid 记录处理）；
     * 进程已退出或校验通道不可用（无 root/超时）时返回 true 以保持原有可用性。
     */
    private fun pidSafeToSignal(context: Context, pid: Int): Boolean {
        val script = """
            PID=${shellQuote(pid.toString())}
            if [ ! -d /proc/${'$'}PID ]; then
              echo IDENT_GONE
              exit 0
            fi
            CMDLINE=${'$'}(tr '\\0' ' ' < /proc/${'$'}PID/cmdline 2>/dev/null || true)
            if echo "${'$'}CMDLINE" | grep -q ${shellQuote(mainClassName)}; then
              echo IDENT_OK
            else
              echo IDENT_FAIL
            fi
        """.trimIndent()
        val result = RootShell.exec(script, timeoutMs = 2500L)
        if (!result.ok) {
            AppDiagnosticLogger.w(
                context,
                "RootRuntimeController",
                "pid=$pid 身份校验不可用(exit=${result.exitCode})，保守放行"
            )
            return true
        }
        return !result.stdout.contains("IDENT_FAIL")
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
