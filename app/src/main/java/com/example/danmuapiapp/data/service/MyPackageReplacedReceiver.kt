package com.example.danmuapiapp.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.danmuapiapp.domain.model.RunMode

/**
 * App 覆盖安装后：按策略做一次低功耗预热，减少首次进入等待。
 */
class MyPackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val appContext = context.applicationContext
        SystemHeartbeatScheduler.refresh(appContext)
        RuntimeWarmupManager.markUpdatePending(appContext)

        val pending = goAsync()
        Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            try {
                val shouldAutoStart = RuntimeModePrefs.get(appContext) == RunMode.Normal &&
                    NodeKeepAlivePrefs.isKeepAliveEnabled(appContext) &&
                    NodeKeepAlivePrefs.isDesiredRunning(appContext)

                if (shouldAutoStart) {
                    val projectDir = RuntimePaths.normalProjectDir(appContext)
                    if (NodeProjectManager.hasSelectedCoreInstalled(appContext, projectDir)) {
                        val runtimeReady = runCatching {
                            NodeProjectManager.syncRuntimeEnvIfProjectReady(
                                context = appContext,
                                targetProjectDir = projectDir
                            )
                        }.getOrDefault(false)
                        if (runtimeReady) {
                            RuntimeWarmupManager.markWarmupCompleted(appContext, 0L)
                        }
                        val port = appContext.getSharedPreferences("runtime", Context.MODE_PRIVATE)
                            .getInt("port", 9321)
                        val recovered = runCatching {
                            NodeService.recoverStaleProcessIfNeeded(appContext, port)
                        }.getOrDefault(true)
                        if (!recovered) return@Thread
                        NodeService.start(appContext)
                        return@Thread
                    }
                }

                if (RuntimeWarmupManager.shouldAttemptReceiverWarmup(appContext)) {
                    RuntimeWarmupManager.runWarmup(appContext)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
