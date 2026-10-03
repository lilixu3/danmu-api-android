package com.example.danmuapiapp.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.danmuapiapp.domain.model.RunMode

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_USER_UNLOCKED
        ) return

        val appContext = context.applicationContext
        SystemHeartbeatScheduler.refresh(appContext)
        if (!NormalAutoStartPrefs.isBootAutoStartEnabled(appContext)) return

        val pending = goAsync()
        Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            try {
                runCatching {
                    val runMode = RuntimeModePrefs.get(appContext)
                    // 高权限模式由对应模块负责触发，这里仅处理普通模式。
                    if (runMode == RunMode.Normal) {
                        val projectDir = RuntimePaths.normalProjectDir(appContext)
                        if (NodeProjectManager.hasSelectedCoreInstalled(appContext, projectDir)) {
                            if (!NormalAutoStartPrefs.isBootAutoStartEnabled(appContext)) return@runCatching
                            // Record boot intent before slow preparation. A manual stop
                            // during preparation must win over the eventual start command.
                            NodeKeepAlivePrefs.setDesiredRunning(appContext, true)
                            SystemHeartbeatScheduler.refresh(appContext)
                            runCatching {
                                NodeProjectManager.syncRuntimeEnvIfProjectReady(
                                    context = appContext,
                                    targetProjectDir = projectDir
                                )
                            }
                            val port = appContext.getSharedPreferences("runtime", Context.MODE_PRIVATE)
                                .getInt("port", 9321)
                            if (!NodeKeepAlivePrefs.isDesiredRunning(appContext) ||
                                !NormalAutoStartPrefs.isBootAutoStartEnabled(appContext) ||
                                RuntimeModePrefs.get(appContext) != RunMode.Normal
                            ) return@runCatching
                            val recovered = runCatching {
                                NodeService.recoverStaleProcessIfNeeded(appContext, port)
                            }.getOrDefault(true)
                            if (!recovered) return@runCatching
                            NodeService.requestRecoveryStart(appContext)
                        }
                    }
                }.onFailure {
                    AppDiagnosticLogger.e(appContext, "BootReceiver", "普通模式开机恢复失败", it)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
