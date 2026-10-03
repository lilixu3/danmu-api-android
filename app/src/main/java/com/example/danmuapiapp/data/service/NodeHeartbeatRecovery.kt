package com.example.danmuapiapp.data.service

import android.content.Context
import java.util.concurrent.locks.ReentrantLock
import com.example.danmuapiapp.data.util.PortProbe

internal enum class HeartbeatRecoveryAction { None, PortOccupied, ReattachHost, RecoverRuntime }

internal fun heartbeatRecoveryAction(
    desiredRunning: Boolean,
    isRootMode: Boolean,
    portOpen: Boolean,
    nodeProcessRunning: Boolean,
    foregroundHostRunning: Boolean
): HeartbeatRecoveryAction = when {
    !desiredRunning || isRootMode -> HeartbeatRecoveryAction.None
    portOpen && !nodeProcessRunning -> HeartbeatRecoveryAction.PortOccupied
    portOpen && !foregroundHostRunning -> HeartbeatRecoveryAction.ReattachHost
    portOpen -> HeartbeatRecoveryAction.None
    else -> HeartbeatRecoveryAction.RecoverRuntime
}

/** Only called by explicitly enabled heartbeats; no polling of a running runtime. */
internal object NodeHeartbeatRecovery {
    private val recoveryLock = ReentrantLock()

    fun tick(context: Context, source: String, canRecover: () -> Boolean) {
        if (!recoveryLock.tryLock()) return
        try {
            tickLocked(context, source, canRecover)
        } catch (error: Exception) {
            AppDiagnosticLogger.w(context, source, "后台恢复检查失败，将在下次心跳重试：${error.message}", error)
        } finally {
            recoveryLock.unlock()
        }
    }

    private fun tickLocked(context: Context, source: String, canRecover: () -> Boolean) {
        val appContext = context.applicationContext
        if (!canRecover() || !NodeKeepAlivePrefs.isDesiredRunning(appContext) || NodeKeepAlivePrefs.isRootMode(appContext)) return
        val port = appContext.getSharedPreferences("runtime", Context.MODE_PRIVATE).getInt("port", 9321)
        if (port !in 1..65535) return
        val portOpen = PortProbe.isOpen(port = port)
        val action = heartbeatRecoveryAction(
            desiredRunning = canRecover() && NodeKeepAlivePrefs.isDesiredRunning(appContext),
            isRootMode = NodeKeepAlivePrefs.isRootMode(appContext),
            portOpen = portOpen,
            nodeProcessRunning = portOpen && NodeService.isProcessRunning(appContext),
            foregroundHostRunning = !portOpen || NodeService.isForegroundHostRunning(appContext)
        )
        when (action) {
            HeartbeatRecoveryAction.None -> return
            HeartbeatRecoveryAction.PortOccupied -> {
                AppDiagnosticLogger.w(appContext, source, "恢复跳过：端口 $port 已占用，未发现本应用 Node 进程")
                return
            }
            HeartbeatRecoveryAction.ReattachHost -> {
                NodeService.ensureForegroundNotification(appContext)
                return
            }
            HeartbeatRecoveryAction.RecoverRuntime -> Unit
        }
        val projectDir = RuntimePaths.normalProjectDir(appContext)
        if (!NodeProjectManager.hasSelectedCoreInstalled(appContext, projectDir)) return
        try {
            if (!canRecover() || !NodeKeepAlivePrefs.isDesiredRunning(appContext)) return
            NodeProjectManager.syncRuntimeEnvIfProjectReady(appContext, projectDir)
            if (!canRecover() || !NodeKeepAlivePrefs.isDesiredRunning(appContext) || NodeKeepAlivePrefs.isRootMode(appContext)) return
            if (!NodeService.recoverStaleProcessIfNeeded(appContext, port)) {
                AppDiagnosticLogger.w(appContext, source, "旧 Node 进程尚未退出，本轮不重复启动")
                return
            }
            if (canRecover() && NodeService.requestRecoveryStart(appContext)) {
                AppDiagnosticLogger.i(appContext, source, "已投递后台恢复请求，等待运行时确认")
            }
        } catch (error: Exception) {
            AppDiagnosticLogger.w(appContext, source, "后台恢复失败，将在下次心跳或返回应用时重试：${error.message}", error)
        }
    }
}
