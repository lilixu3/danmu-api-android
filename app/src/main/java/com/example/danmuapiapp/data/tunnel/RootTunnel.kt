package com.example.danmuapiapp.data.tunnel

import android.content.Context
import java.io.File
import com.example.danmuapiapp.data.service.RootShell
import com.example.danmuapiapp.data.service.RootAutoStartModule

/**
 * Root 模式下的 frpc 进程：su + setsid 拉起，PID 文件判活。
 *
 * 与 RootRuntimeController 的 Node 进程同思路：独立于 App 生命周期；
 * 开机自启由 App 自己的 RootAutoStart 启动脚本负责。
 */
object RootTunnel {

    @Synchronized
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
        val bootScript = RootAutoStartModule.refreshInstalledServiceScript(context)
        if (!bootScript.ok) return TunnelActionResult(false, bootScript.message)

        val dir = TunnelStore.dir(context)
        val script = buildString {
            appendLine("FRP_DIR=${shellQuote(dir.absolutePath)}")
            appendLine("FRP_LIB=${shellQuote(TunnelStore.kernelFile(context).absolutePath)}")
            appendLine(RootTunnelScripts.selectKernel())
            appendLine("FRP_BIN=\"\$FRP_LIB\"")
            appendLine("FRP_CFG=${shellQuote(config.absolutePath)}")
            appendLine("FRP_PID=${shellQuote(TunnelStore.rootPidFile(context).absolutePath)}")
            appendLine("FRP_LOG=${shellQuote(TunnelStore.logFile(context).absolutePath)}")
            appendLine("FRP_PIDFILE=\"\$FRP_PID\"")
            appendLine(RootTunnelScripts.lock())
            appendLine(RootTunnelScripts.identityFunctions())
            appendLine(RootTunnelScripts.statusFunctions())
            appendLine("APP_UID=${android.os.Process.myUid()}")
            appendLine("umask 022")
            appendLine("mkdir -p ${shellQuote(dir.absolutePath)} 2>/dev/null || true")
            appendLine("[ -x \"\$FRP_BIN\" ] || { echo 'kernel not executable'; exit 2; }")
            appendLine("[ -f \"\$FRP_CFG\" ] || { echo 'config missing'; exit 3; }")
            appendLine("PIDS=\$(frpc_pids)")
            appendLine("set -- \$PIDS")
            appendLine("if [ \"\$#\" -gt 1 ]; then echo '检测到重复穿透进程，请点击重启清理' >&2; exit 5; fi")
            appendLine("if [ \"\$#\" -eq 1 ]; then")
            appendLine("  OLD=\$(cat \"\$FRP_PID\" 2>/dev/null)")
            appendLine("  if [ \"\$OLD\" != \"\$1\" ]; then echo '[app] frpc process adopted' >> \"\$FRP_LOG\"; fi")
            appendLine("  frpc_adopt_instance \"\$1\" || exit 6")
            appendLine("  remember_frpc \"\$1\"")
            appendLine("  frpc_write_status running \"\$1\" || exit 6")
            appendLine("  echo \"already:\$1\"; exit 0")
            appendLine("fi")
            appendLine("rm -f \"\$FRP_PID\" \"\$FRP_PID.start\" \"\$FRP_PID.version\" 2>/dev/null || true")
            appendLine("FRP_LOG_HELPER=${shellQuote(File(context.applicationInfo.nativeLibraryDir, "libdanmu_outbound.so").absolutePath)}")
            appendLine("frpc_new_instance || exit 6")
            appendLine(RootTunnelScripts.logSink(trackLink = true))
            appendLine("if command -v setsid >/dev/null 2>&1; then")
            appendLine("  setsid \"\$FRP_BIN\" -c \"\$FRP_CFG\" > \"\$FRP_PIPE\" 2>&1 < /dev/null 9>&- &")
            appendLine("elif command -v nohup >/dev/null 2>&1; then")
            appendLine("  nohup \"\$FRP_BIN\" -c \"\$FRP_CFG\" > \"\$FRP_PIPE\" 2>&1 < /dev/null 9>&- &")
            appendLine("else")
            appendLine("  \"\$FRP_BIN\" -c \"\$FRP_CFG\" > \"\$FRP_PIPE\" 2>&1 < /dev/null 9>&- &")
            appendLine("fi")
            appendLine("PID=\$!")
            appendLine("echo \"\$PID\" > \"\$FRP_PID\"")
            appendLine("frpc_ticks \"\$PID\" > \"\$FRP_PID.start\"")
            appendLine("\"\$FRP_BIN\" --version > \"\$FRP_PID.version\" 2>/dev/null")
            appendLine("frpc_write_status starting \"\$PID\" || true")
            appendLine("sleep 2")
            appendLine("rm -f \"\$FRP_PIPE\"")
            appendLine("if owns_frpc \"\$PID\"; then")
            appendLine("  frpc_write_status running \"\$PID\" || exit 6")
            appendLine("  chmod 0644 \"\$FRP_PID\" \"\$FRP_PID.start\" \"\$FRP_PID.version\" \"\$FRP_LOG\" 2>/dev/null || true")
            appendLine("  chown \"\$APP_UID:\$APP_UID\" \"\$FRP_PID\" \"\$FRP_PID.start\" \"\$FRP_PID.version\" \"\$FRP_LOG\" 2>/dev/null || true")
            appendLine("  echo \"started:\$PID\"")
            appendLine("  exit 0")
            appendLine("fi")
            appendLine("if [ -n \"\$(cat \"\$FRP_PID.start\" 2>/dev/null)\" ] && [ \"\$(frpc_ticks \"\$PID\")\" = \"\$(cat \"\$FRP_PID.start\" 2>/dev/null)\" ]; then kill \"\$PID\" 2>/dev/null || true; fi")
            appendLine("kill \"\$FRP_LOG_PID\" 2>/dev/null || true")
            appendLine("echo 'frpc exited early'")
            appendLine("tail -n 20 \"\$FRP_LOG\" 2>/dev/null")
            appendLine("rm -f \"\$FRP_PID\" \"\$FRP_PID.start\" \"\$FRP_PID.version\" 2>/dev/null || true")
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
        // The shared shell publisher also serves boot startup and process adoption.
        TunnelStore.broadcastStatus(context)
        return TunnelActionResult(true, "已以 Root 身份启动穿透", pid)
    }

    @Synchronized
    fun stop(context: Context): TunnelActionResult {
        if (!RootShell.hasRoot(3000L)) {
            return TunnelActionResult(false, "未获得 Root 权限")
        }
        val script = RootTunnelScripts.stop(
            TunnelStore.dir(context).absolutePath,
            TunnelStore.kernelFile(context).absolutePath
        )
        val result = RootShell.exec(script, 30000L)
        if (!result.ok) {
            return TunnelActionResult(false, "停止 Root 穿透失败：${result.stderr.takeLast(200)}")
        }
        TunnelStore.writeStatus(context, "stopped", "root", 0L, 0L, 0, "")
        TunnelStore.broadcastStatus(context)
        return TunnelActionResult(true, "已停止 Root 穿透")
    }

    fun isRunning(context: Context): Boolean = runningPid(context) > 0L

    /** 不依赖状态 JSON；升级遗留进程和丢失 PID 文件也必须显示为运行中。 */
    fun runningPid(context: Context): Long = runningIdentity(context).pid

    internal fun runningIdentity(context: Context): TunnelProcessIdentity {
        val script = buildString {
            appendLine("FRP_DIR=${shellQuote(TunnelStore.dir(context).absolutePath)}")
            appendLine("FRP_LIB=${shellQuote(TunnelStore.kernelFile(context).absolutePath)}")
            appendLine("FRP_PIDFILE=${shellQuote(TunnelStore.rootPidFile(context).absolutePath)}")
            appendLine(RootTunnelScripts.identityFunctions())
            appendLine("PID=\$(cat \"\$FRP_PIDFILE\" 2>/dev/null)")
            appendLine("owns_frpc \"\$PID\" || PID=\$(frpc_pids | head -n 1)")
            appendLine("[ -n \"\$PID\" ] || exit 0")
            appendLine("TICKS=\$(frpc_ticks \"\$PID\")")
            appendLine("BOOT=\$(cat /proc/sys/kernel/random/boot_id)")
            appendLine("INSTANCE=''")
            appendLine("if [ \"\$PID\" = \"\$(cat \"\$FRP_PIDFILE\" 2>/dev/null)\" ] && [ \"\$TICKS\" = \"\$(cat \"\$FRP_PIDFILE.start\" 2>/dev/null)\" ] && [ \"\$BOOT\" = \"\$(cat \"\$FRP_PIDFILE.boot\" 2>/dev/null)\" ]; then")
            appendLine("  INSTANCE=\$(cat \"\$FRP_PIDFILE.instance\" 2>/dev/null)")
            appendLine("fi")
            appendLine("printf '%s|%s|%s|%s\\n' \"\$PID\" \"\$TICKS\" \"\$BOOT\" \"\$INSTANCE\"")
        }
        val result = RootShell.exec(script, 10000L)
        if (!result.ok) return TunnelProcessIdentity()
        val fields = result.stdout.trim().split('|')
        return TunnelProcessIdentity(fields.getOrNull(0)?.toLongOrNull() ?: 0L,
            fields.getOrNull(1).orEmpty(), fields.getOrNull(2).orEmpty(), fields.getOrNull(3).orEmpty())
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
