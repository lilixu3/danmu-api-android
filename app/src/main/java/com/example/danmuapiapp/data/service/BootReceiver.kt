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

        SystemHeartbeatScheduler.refresh(context.applicationContext)

        if (!NormalAutoStartPrefs.isBootAutoStartEnabled(context)) return

        val pending = goAsync()
        Thread {
            try {
                val runMode = RuntimeModePrefs.get(context)
                // 高权限模式由对应模块负责触发，这里仅处理普通模式。
                if (runMode == RunMode.Normal) {
                    val projectDir = RuntimePaths.normalProjectDir(context)
                    if (NodeProjectManager.hasSelectedCoreInstalled(context, projectDir)) {
                        runCatching {
                            NodeProjectManager.syncRuntimeEnvIfProjectReady(
                                context = context,
                                targetProjectDir = projectDir
                            )
                        }
                        val port = context.getSharedPreferences("runtime", Context.MODE_PRIVATE)
                            .getInt("port", 9321)
                        val recovered = runCatching {
                            NodeService.recoverStaleProcessIfNeeded(context, port)
                        }.getOrDefault(true)
                        if (!recovered) return@Thread
                        NodeService.start(context.applicationContext)
                    }
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
