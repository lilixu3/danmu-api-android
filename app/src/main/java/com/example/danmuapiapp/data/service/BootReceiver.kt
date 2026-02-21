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

        if (!NormalAutoStartPrefs.isBootAutoStartEnabled(context)) return

        val pending = goAsync()
        Thread {
            try {
                val runMode = RuntimeModePrefs.get(context)
                // 高权限模式由对应模块负责触发，这里仅处理普通模式。
                if (runMode == RunMode.Normal) {
                    val projectDir = runCatching {
                        NodeProjectManager.ensureProjectExtracted(
                            context,
                            RuntimePaths.normalProjectDir(context)
                        )
                    }.getOrNull()
                    if (projectDir != null && NodeProjectManager.hasSelectedCoreInstalled(context, projectDir)) {
                        NodeService.start(context.applicationContext)
                    }
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
