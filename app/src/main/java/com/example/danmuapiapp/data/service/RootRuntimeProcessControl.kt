package com.example.danmuapiapp.data.service

import android.content.Context
import com.example.danmuapiapp.data.tunnel.RootTunnel
import com.example.danmuapiapp.data.util.ShellUtils.shellQuote
import com.example.danmuapiapp.data.repository.RuntimeOwnership

/**
 * Root 独立进程的启动、停止与重启。
 *
 * 启动前完成目录同步与依赖校验，停止时只向已校验归属的进程发送信号。
 */
internal object RootRuntimeProcessControl {
    /**
     * 兼容旧调用签名，避免增参后出现 NoSuchMethodError。
     */
    internal fun start(context: Context, port: Int, quickMode: Boolean): RootRuntimeController.OpResult {
        return start(context, port, quickMode, skipSync = false)
    }

    internal fun start(
        context: Context,
        port: Int,
        quickMode: Boolean = false,
        skipSync: Boolean = false
    ): RootRuntimeController.OpResult {
        if (RootRuntimeHealth.isRuntimeOwnedByAppPassive(context, port)) {
            RootTunnel.startIfEnabled(context)
            return RootRuntimeController.OpResult(
                ok = true,
                message = "Root 模式已在运行",
                startOutcome = RootRuntimeController.StartOutcome.AlreadyRunning
            )
        }

        AppDiagnosticLogger.i(context, "RootRuntimeController", "请求启动 Root 模式，端口=$port")
        val bootLogFile = AppDiagnosticLogger.prepareRootBootstrapLog(context)

        if (!RootShell.hasRoot(3000L)) {
            AppDiagnosticLogger.e(context, "RootRuntimeController", "Root 授权失败")
            return RootRuntimeController.OpResult(false, "Root 授权失败", "请确认设备已 Root，并允许本应用获取 Root 权限")
        }

        if (RootRuntimeHealth.isRunning(context, port)) {
            return RootRuntimeController.OpResult(
                ok = true,
                message = "Root 模式已在运行",
                startOutcome = RootRuntimeController.StartOutcome.AlreadyRunning
            )
        }

        if (RootRuntimeHealth.isRunningFast(port)) {
            val ownership = RootRuntimeHealth.readRuntimeOwnership(context, port)
            val explicitlyForeign = ownership == RuntimeOwnership.Foreign
            return RootRuntimeController.OpResult(
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
        val prepare = RootRuntimeSync.ensureRootRuntimeReady(
            context = context,
            refreshEnvWhenReady = !skipSync
        )
        if (!prepare.ok) {
            AppDiagnosticLogger.e(
                context,
                "RootRuntimeController",
                "Root 运行时准备失败：${prepare.detail.ifBlank { prepare.message }}"
            )
            return RootRuntimeController.OpResult(false, "Root 模式启动失败", prepare.detail.ifBlank { prepare.message })
        }

        val dependencyCheck = RootRuntimeSync.verifyRootRuntimeDependencies(context)
        if (!dependencyCheck.ok) {
            AppDiagnosticLogger.e(
                context,
                "RootRuntimeController",
                "Root 运行时依赖检查失败：${dependencyCheck.detail.ifBlank { dependencyCheck.message }}"
            )
            return dependencyCheck
        }

        val rootProject = RootRuntimeLocation.rootProjectDir(context)
        val entryPath = "$rootProject/main.js"
        val pidPath = RootRuntimeLocation.pidFile(context).absolutePath
        val startedAtPath = RootRuntimeLocation.startedAtFile(context).absolutePath
        val pkgName = context.packageName
        val apkPathHint = context.applicationInfo.sourceDir
        val libDirHint = context.applicationInfo.nativeLibraryDir
        val bootLogPath = bootLogFile.absolutePath
        val runtimeIdentity = RuntimeIdentityStore.ensureInstanceId(context)

        StartupFailureStore.clearRoot(context)

        val outboundConfigPath = AppOutboundSettingsStore.ensureConfig(context)
        val startScript = """
            PKG=${shellQuote(pkgName)}
            APP_APK_HINT=${shellQuote(apkPathHint)}
            LIB_DIR_HINT=${shellQuote(libDirHint)}
            ENTRY=${shellQuote(entryPath)}
            PID_FILE=${shellQuote(pidPath)}
            STARTED_AT_FILE=${shellQuote(startedAtPath)}
            BOOT_LOG=${shellQuote(bootLogPath)}
            MAIN_CLASS=${shellQuote(RootRuntimeLocation.mainClassName)}
            NICE_NAME=${shellQuote(RootRuntimeLocation.PROCESS_NAME)}

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
            export DANMU_APP_OUTBOUND_CONFIG=${shellQuote(outboundConfigPath)}
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
            return RootRuntimeController.OpResult(false, "Root 模式启动失败", detail)
        }

        if (quickMode) {
            AppDiagnosticLogger.i(context, "RootRuntimeController", "Root 模式已触发启动")
            RootTunnel.startIfEnabled(context)
            return RootRuntimeController.OpResult(
                ok = true,
                message = "Root 模式已触发启动",
                startOutcome = RootRuntimeController.StartOutcome.StartedNewProcess
            )
        }

        val startupWait = RootRuntimeHealth.waitForReadyOrFailure(context, port, timeoutMs = 12_000L)
        return if (startupWait.ready) {
            AppDiagnosticLogger.i(context, "RootRuntimeController", "Root 模式已启动，端口=$port")
            RootTunnel.startIfEnabled(context)
            RootRuntimeController.OpResult(
                ok = true,
                message = "Root 模式已启动",
                startOutcome = RootRuntimeController.StartOutcome.StartedNewProcess
            )
        } else {
            val detail = mergeRootBootstrapDetail(
                primary = startupWait.failureDetail
                    ?: "端口 $port 未就绪，请在应用控制台查看 Root 启动日志与 /api/logs 后重试",
                tail = AppDiagnosticLogger.readRootBootstrapTail(context)
            )
            AppDiagnosticLogger.e(context, "RootRuntimeController", "Root 模式启动超时：$detail")
            RootRuntimeController.OpResult(false, "Root 模式启动超时", detail)
        }
    }

    internal fun stop(context: Context, port: Int): RootRuntimeController.OpResult {
        val processes = RootRuntimeHealth.discoverProcesses(context)
            ?: return RootRuntimeController.OpResult(false, "停止失败", "无法校验 Root 进程，请确认 Root 授权")
        if (processes.isEmpty()) {
            if (RootRuntimeHealth.isRunningFast(port)) return RootRuntimeController.OpResult(false, "停止失败", "端口仍在监听，但未找到可确认归属的 Root 进程，已保留外部服务")
            clearRuntimeMarkers(context)
            return RootRuntimeController.OpResult(true, "已停止")
        }
        RootTunnel.stop(context)
        // Never send shutdown to an unverified port. Processes are validated by
        // root UID, exact project directory, app_process executable and birth tick.
        val term = RootShell.exec(RootRuntimeRecovery.buildSignalShell(RootRuntimeLocation.rootProjectDir(context), RootRuntimeLocation.mainClassName, processes, false), 5000L)
        if (!term.ok) return RootRuntimeController.OpResult(false, "停止失败", "无法停止已校验的 Root 进程")
        val deadline = System.currentTimeMillis() + 3500L
        var remaining = RootRuntimeHealth.discoverProcesses(context) ?: return RootRuntimeController.OpResult(false, "停止失败", "无法确认 Root 进程是否退出")
        while (remaining.isNotEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(180L)
            remaining = RootRuntimeHealth.discoverProcesses(context) ?: return RootRuntimeController.OpResult(false, "停止失败", "无法确认 Root 进程是否退出")
        }
        if (remaining.isNotEmpty()) {
            val kill = RootShell.exec(RootRuntimeRecovery.buildSignalShell(RootRuntimeLocation.rootProjectDir(context), RootRuntimeLocation.mainClassName, remaining, true), 5000L)
            if (!kill.ok) return RootRuntimeController.OpResult(false, "停止失败", "Root 进程未退出")
            Thread.sleep(180L)
            remaining = RootRuntimeHealth.discoverProcesses(context) ?: return RootRuntimeController.OpResult(false, "停止失败", "无法确认 Root 进程是否退出")
        }
        if (remaining.isNotEmpty()) return RootRuntimeController.OpResult(false, "停止失败", "Root 进程仍在运行，未清除控制记录")
        clearRuntimeMarkers(context)
        return RootRuntimeController.OpResult(true, "已停止")
    }

    internal fun restart(context: Context, port: Int): RootRuntimeController.OpResult {
        val recovered = RootRuntimeHealth.recoverRuntime(context, port)
        val actualPort = recovered?.port ?: port
        val stopResult = stop(context, actualPort)
        if (!stopResult.ok) return RootRuntimeController.OpResult(false, "重启失败", "停止阶段失败：${stopResult.detail.ifBlank { stopResult.message }}")
        if (RootRuntimeHealth.isRunningFast(actualPort)) {
            return RootRuntimeController.OpResult(false, "重启失败", "旧进程仍在运行，未执行新启动")
        }
        return start(context, actualPort, quickMode = false)
    }

    private fun clearRuntimeMarkers(context: Context) {
        runCatching { RootRuntimeLocation.pidFile(context).delete() }
        runCatching { RootRuntimeLocation.startedAtFile(context).delete() }
        val base = RootRuntimeLocation.rootBaseDir(context)
        RootShell.exec(
            RootRuntimeRecovery.buildScanShell(RootRuntimeLocation.rootProjectDir(context), RootRuntimeLocation.mainClassName) + "\n" + """
                MARKER=${shellQuote("$base/${RootRuntimeLocation.PID_FILE_NAME}")}
                OWNER_PID=${'$'}(cat "${'$'}MARKER" 2>/dev/null)
                if ! owned_root "${'$'}OWNER_PID"; then
                  rm -f "${'$'}MARKER" ${shellQuote("$base/${RootRuntimeLocation.STARTED_AT_FILE_NAME}")}
                fi
            """.trimIndent(), 4500L
        )
    }

    private fun mergeRootBootstrapDetail(primary: String, tail: String): String {
        val normalizedPrimary = primary.trim().ifBlank { "未知错误" }
        val normalizedTail = tail.trim()
        if (normalizedTail.isBlank()) return normalizedPrimary
        return "$normalizedPrimary\n最近 Root 引导日志：\n$normalizedTail"
    }
}
