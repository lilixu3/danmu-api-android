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

                var preparedByAutoPath = false
                if (shouldAutoStart) {
                    val projectDir = runCatching {
                        NodeProjectManager.ensureProjectExtracted(
                            appContext,
                            RuntimePaths.normalProjectDir(appContext)
                        )
                    }.getOrNull()

                    if (projectDir != null) {
                        preparedByAutoPath = true
                        runCatching {
                            NodeProjectManager.writeRuntimeEnv(
                                context = appContext,
                                targetProjectDir = projectDir
                            )
                        }
                        RuntimeWarmupManager.markWarmupCompleted(appContext, 0L)

                        if (NodeProjectManager.hasSelectedCoreInstalled(appContext, projectDir)) {
                            NodeService.start(appContext)
                            return@Thread
                        }
                    }
                }

                if (!preparedByAutoPath && RuntimeWarmupManager.shouldAttemptReceiverWarmup(appContext)) {
                    RuntimeWarmupManager.runWarmup(appContext)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
