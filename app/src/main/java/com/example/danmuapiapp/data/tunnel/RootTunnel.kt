package com.example.danmuapiapp.data.tunnel

import android.content.Context
import com.example.danmuapiapp.data.service.RootShell

/**
 * Root 模式下的 frpc 进程：su + setsid 拉起，PID 文件判活。
 *
 * 与 RootRuntimeController 的 Node 进程同思路：独立于 App 生命周期；
 * 开机自启由 App 自己的 RootAutoStart 启动脚本负责。
 */
object RootTunnel {

    fun start(context: Context): TunnelActionResult {
        if (!TunnelStore.kernelReady(context, rootMode = true)) {
            val message = "缺少 frpc 内核（libfrpc.so），请更新 App"
            TunnelStore.writeStatus(context, "error", "root", 0L, 0L, 0, message)
            TunnelStore.broadcastStatus(context)
            return TunnelActionResult(false, message)
        }
        val config = TunnelStore.configFile(context)
        if (!config.isFile || config.length() == 0L) {
            val message = "穿透配置为空，请先在设置里保存"
            TunnelStore.writeStatus(context, "error", "root", 0L, 0L, 0, message)
            TunnelStore.broadcastStatus(context)
            return TunnelActionResult(false, message)
        }
        if (isRunning(context)) {
            return TunnelActionResult(true, "穿透已在运行", readPid(context))
        }

        val dir = TunnelStore.dir(context)
        val script = buildString {
            appendLine("FRP_BIN=${shellQuote(TunnelStore.execKernelFile(context).absolutePath)}")
            appendLine("FRP_CFG=${shellQuote(config.absolutePath)}")
            appendLine("FRP_PID=${shellQuote(TunnelStore.rootPidFile(context).absolutePath)}")
            appendLine("FRP_LOG=${shellQuote(TunnelStore.logFile(context).absolutePath)}")
            appendLine("APP_UID=${android.os.Process.myUid()}")
            appendLine("umask 022")
            appendLine("mkdir -p ${shellQuote(dir.absolutePath)} 2>/dev/null || true")
            appendLine("[ -x \"\$FRP_BIN\" ] || { echo 'kernel not executable'; exit 2; }")
            appendLine("[ -f \"\$FRP_CFG\" ] || { echo 'config missing'; exit 3; }")
            appendLine("if [ -f \"\$FRP_PID\" ]; then")
            appendLine("  OLD=\$(cat \"\$FRP_PID\" 2>/dev/null | tr -d '\\r\\n')")
            appendLine("  if [ -n \"\$OLD\" ] && kill -0 \"\$OLD\" 2>/dev/null && tr '\\0' ' ' < \"/proc/\$OLD/cmdline\" 2>/dev/null | grep -q 'libfrpc.so'; then")
            appendLine("    echo \"already:\$OLD\"; exit 0;")
            appendLine("  fi")
            appendLine("fi")
            appendLine("rm -f \"\$FRP_PID\" 2>/dev/null || true")
            appendLine("if [ -f \"\$FRP_LOG\" ]; then")
            appendLine("  SZ=\$(wc -c < \"\$FRP_LOG\" 2>/dev/null)")
            appendLine("  if [ -n \"\$SZ\" ] && [ \"\$SZ\" -gt 1048576 ]; then")
            appendLine("    tail -c 262144 \"\$FRP_LOG\" > \"\$FRP_LOG.tmp\" 2>/dev/null && mv \"\$FRP_LOG.tmp\" \"\$FRP_LOG\" 2>/dev/null || true")
            appendLine("  fi")
            appendLine("fi")
            appendLine("if command -v setsid >/dev/null 2>&1; then")
            appendLine("  setsid \"\$FRP_BIN\" -c \"\$FRP_CFG\" >> \"\$FRP_LOG\" 2>&1 < /dev/null &")
            appendLine("elif command -v nohup >/dev/null 2>&1; then")
            appendLine("  nohup \"\$FRP_BIN\" -c \"\$FRP_CFG\" >> \"\$FRP_LOG\" 2>&1 < /dev/null &")
            appendLine("else")
            appendLine("  \"\$FRP_BIN\" -c \"\$FRP_CFG\" >> \"\$FRP_LOG\" 2>&1 < /dev/null &")
            appendLine("fi")
            appendLine("PID=\$!")
            appendLine("echo \"\$PID\" > \"\$FRP_PID\"")
            appendLine("sleep 2")
            appendLine("if kill -0 \"\$PID\" 2>/dev/null; then")
            appendLine("  chmod 0644 \"\$FRP_PID\" \"\$FRP_LOG\" 2>/dev/null || true")
            appendLine("  chown \"\$APP_UID:\$APP_UID\" \"\$FRP_PID\" \"\$FRP_LOG\" 2>/dev/null || true")
            appendLine("  echo \"started:\$PID\"")
            appendLine("  exit 0")
            appendLine("fi")
            appendLine("echo 'frpc exited early'")
            appendLine("tail -n 20 \"\$FRP_LOG\" 2>/dev/null")
            appendLine("rm -f \"\$FRP_PID\" 2>/dev/null || true")
            appendLine("exit 4")
        }

        val result = RootShell.exec(script, 20000L)
        val output = (result.stdout + " " + result.stderr).trim()
        if (!result.ok) {
            val detail = output.takeLast(300)
            TunnelStore.writeStatus(context, "error", "root", 0L, 0L, 0, detail)
            TunnelStore.broadcastStatus(context)
            return TunnelActionResult(false, "Root 模式启动穿透失败：$detail")
        }
        val pid = readPid(context)
        TunnelStore.writeStatus(context, "running", "root", pid, System.currentTimeMillis(), 0, "")
        TunnelStore.broadcastStatus(context)
        return TunnelActionResult(true, "已以 Root 身份启动穿透", pid)
    }

    fun stop(context: Context): TunnelActionResult {
        if (!RootShell.hasRoot(3000L)) {
            return TunnelActionResult(false, "未获得 Root 权限")
        }
        val pidFile = TunnelStore.rootPidFile(context)
        val script = buildString {
            appendLine("PID=\$(cat ${shellQuote(pidFile.absolutePath)} 2>/dev/null)")
            appendLine("if [ -n \"\$PID\" ]; then")
            appendLine("  kill \"\$PID\" 2>/dev/null || true")
            appendLine("  I=0")
            appendLine("  while [ \"\$I\" -lt 10 ] && kill -0 \"\$PID\" 2>/dev/null; do I=\$((I + 1)); sleep 0.3; done")
            appendLine("  kill -0 \"\$PID\" 2>/dev/null && kill -9 \"\$PID\" 2>/dev/null || true")
            appendLine("fi")
            appendLine("rm -f ${shellQuote(pidFile.absolutePath)} 2>/dev/null || true")
        }
        val result = RootShell.exec(script, 12000L)
        pidFile.delete()
        TunnelStore.writeStatus(context, "stopped", "root", 0L, 0L, 0, "")
        TunnelStore.broadcastStatus(context)
        return if (result.ok) {
            TunnelActionResult(true, "已停止 Root 穿透")
        } else {
            TunnelActionResult(false, "停止 Root 穿透失败：${result.stderr.takeLast(200)}")
        }
    }

    fun isRunning(context: Context): Boolean {
        if (readPid(context) <= 0) return false
        val pidFile = TunnelStore.rootPidFile(context)
        val script = buildString {
            appendLine("PID=\$(cat ${shellQuote(pidFile.absolutePath)} 2>/dev/null)")
            appendLine("[ -n \"\$PID\" ] && kill -0 \"\$PID\" 2>/dev/null && tr '\\0' ' ' < \"/proc/\$PID/cmdline\" 2>/dev/null | grep -q 'libfrpc.so'")
        }
        return RootShell.exec(script, 6000L).ok
    }

    /** 服务/开机启动时按开关拉起（RootRuntimeController 启动流程调用）。 */
    fun startIfEnabled(context: Context) {
        val settings = TunnelStore.readSettings(context)
        if (settings.enabled && settings.autoStart) start(context)
    }

    private fun readPid(context: Context): Long =
        TunnelStore.readText(TunnelStore.rootPidFile(context), 64).trim().toLongOrNull() ?: 0L

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
