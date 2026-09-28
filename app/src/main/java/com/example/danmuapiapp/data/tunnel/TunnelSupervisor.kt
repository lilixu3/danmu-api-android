package com.example.danmuapiapp.data.tunnel

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * 普通模式下的 frpc 进程监管（跑在 :node 前台服务进程里）。
 *
 * 生命周期跟着 NodeService：服务启动时按开关拉起，服务停止时一并结束；
 * 进程异常退出 5s 重拉；状态写入 status.json 供 UI 跨进程读取。
 */
class TunnelSupervisor(private val context: Context) {

    companion object {
        private const val TAG = "TunnelSupervisor"
        private const val RESTART_DELAY_MS = 5000L
        private const val HEARTBEAT_MS = 10_000L
    }

    private var process: Process? = null
    private var watchdog: Thread? = null
    private var heartbeat: Thread? = null

    @Volatile private var stopping = false
    @Volatile private var restarts = 0
    @Volatile private var since = 0L
    @Volatile private var lastError = ""

    @Synchronized
    fun start(): TunnelActionResult {
        if (isRunning()) {
            return TunnelActionResult(true, "穿透已在运行", readPidFile())
        }
        if (!TunnelStore.kernelReady(context)) {
            reportError("缺少 frpc 内核（libfrpc.so），请更新 App")
            return TunnelActionResult(false, lastError)
        }
        val config = TunnelStore.configFile(context)
        if (!config.isFile || config.length() == 0L) {
            reportError("穿透配置为空，请先在设置里保存")
            return TunnelActionResult(false, lastError)
        }
        stopping = false
        lastError = ""
        markStatus("starting")
        if (!spawn()) {
            return TunnelActionResult(false, lastError.ifEmpty { "frpc 启动失败" })
        }
        return TunnelActionResult(true, "穿透已启动", readPidFile())
    }

    @Synchronized
    fun stop() {
        stopping = true
        val current = process
        process = null
        if (current != null) {
            current.destroy()
            var waited = 0L
            while (current.isAlive && waited < 3000L) {
                Thread.sleep(50L)
                waited += 50L
            }
            if (current.isAlive) {
                runCatching { android.os.Process.killProcess(readPidFile().toInt()) }
            }
        }
        heartbeat?.interrupt()
        watchdog?.interrupt()
        heartbeat = null
        watchdog = null
        since = 0L
        TunnelStore.normalPidFile(context).delete()
        markStatus("stopped")
    }

    fun isRunning(): Boolean = process?.isAlive == true

    /** 服务启动时调用：只有"总开关 + 随服务启动"都打开才拉起。 */
    @Synchronized
    fun startIfEnabled() {
        val settings = TunnelStore.readSettings(context)
        if (settings.enabled && settings.autoStart) start()
    }

    private fun spawn(): Boolean {
        killOrphanProcess()
        // 每次启动都从空日志开始：避免上一次运行的旧错误一直挂在界面上
        TunnelStore.writeText(
            TunnelStore.logFile(context),
            "--- frpc start ${System.currentTimeMillis()} ---\n"
        )
        val kernel = TunnelStore.kernelFile(context)
        val config = TunnelStore.configFile(context)
        val pidFile = TunnelStore.normalPidFile(context)
        return try {
            // Android 的 java.lang.Process 没有 pid()：让 sh 先把自己的 PID 写进文件
            // 再 exec frpc（exec 后 PID 不变），孤儿回收与状态展示都靠这个文件。
            val command = "echo $$ > ${shellQuote(pidFile.absolutePath)}" +
                " && exec ${shellQuote(kernel.absolutePath)} -c ${shellQuote(config.absolutePath)}"
            val builder = ProcessBuilder("/system/bin/sh", "-c", command)
            builder.directory(TunnelStore.dir(context))
            builder.redirectErrorStream(true)
            val started = builder.start()
            process = started
            since = System.currentTimeMillis()
            pump(started.inputStream)
            Thread.sleep(600L)
            if (!started.isAlive) {
                lastError = summarizeLog()
                markStatus("error", lastError = lastError)
                return false
            }
            val pid = readPidFile()
            markStatus("running", pid = pid)
            startHeartbeat(started)
            startWatchdog(started)
            true
        } catch (error: Exception) {
            lastError = "frpc 启动失败: ${error.message}"
            Log.e(TAG, lastError)
            markStatus("error", lastError = lastError)
            false
        }
    }

    private fun startWatchdog(target: Process) {
        watchdog?.interrupt()
        watchdog = Thread {
            try {
                target.waitFor()
            } catch (_: InterruptedException) {
                return@Thread
            }
            if (stopping || process !== target) return@Thread
            restarts += 1
            lastError = summarizeLog()
            markStatus("retrying", lastError = lastError)
            Thread.sleep(RESTART_DELAY_MS)
            synchronized(this) {
                if (stopping || !TunnelStore.readSettings(context).enabled) return@Thread
                spawn()
            }
        }.also { it.name = "danmu-frpc-watchdog"; it.start() }
    }

    private fun startHeartbeat(target: Process) {
        heartbeat?.interrupt()
        heartbeat = Thread {
            while (!stopping && process === target && target.isAlive) {
                TunnelStore.writeStatus(
                    context, "running", "normal", readPidFile(), since, restarts, ""
                )
                try {
                    Thread.sleep(HEARTBEAT_MS)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
        }.also { it.isDaemon = true; it.name = "danmu-frpc-heartbeat"; it.start() }
    }

    private fun pump(input: InputStream) {
        Thread {
            try {
                input.use { source ->
                    FileOutputStream(TunnelStore.logFile(context), true).use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = source.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            output.flush()
                        }
                    }
                }
            } catch (error: Exception) {
                Log.w(TAG, "log pump stopped: ${error.message}")
            }
        }.also { it.isDaemon = true; it.name = "danmu-frpc-log"; it.start() }
    }

    private fun summarizeLog(): String {
        val tail = TunnelStore.readLogTail(context, 12).trimEnd()
        if (tail.isBlank()) return "frpc 已退出"
        return tail.split('\n').lastOrNull { it.isNotBlank() }?.trim() ?: "frpc 已退出"
    }

    private fun reportError(message: String) {
        lastError = message
        markStatus("error", lastError = message)
    }

    /** 写状态并广播（UI 被动刷新，不需要轮询）。 */
    private fun markStatus(state: String, pid: Long = 0L, lastError: String = "") {
        TunnelStore.writeStatus(context, state, "normal", pid, since, restarts, lastError)
        TunnelStore.broadcastStatus(context)
    }

    private fun readPidFile(): Long =
        TunnelStore.readText(TunnelStore.normalPidFile(context), 64).trim().toLongOrNull() ?: 0L

    /**
     * :node 被系统直接杀掉时，frpc 子进程会变成孤儿继续跑；下次启动前按
     * PID 文件 + /proc cmdline 校验后回收，避免新旧两个 frpc 抢同一个隧道名。
     */
    private fun killOrphanProcess() {
        val pidFile = TunnelStore.normalPidFile(context)
        val pid = readPidFile()
        if (pid > 0 && pid != android.os.Process.myPid().toLong()) {
            val cmdline = TunnelStore.readText(File("/proc/$pid/cmdline"), 256)
            if (cmdline.contains("libfrpc.so")) {
                runCatching { android.os.Process.killProcess(pid.toInt()) }
            }
        }
        pidFile.delete()
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
