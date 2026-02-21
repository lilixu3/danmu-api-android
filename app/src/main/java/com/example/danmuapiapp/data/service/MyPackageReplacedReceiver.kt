package com.example.danmuapiapp.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.danmuapiapp.domain.model.RunMode

/**
 * App 覆盖安装后：普通模式下按保活策略尝试恢复服务。
 */
class MyPackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        if (RuntimeModePrefs.get(context) != RunMode.Normal) return
        if (!NodeKeepAlivePrefs.isKeepAliveEnabled(context)) return
        if (!NodeKeepAlivePrefs.isDesiredRunning(context)) return
        val projectDir = runCatching {
            NodeProjectManager.ensureProjectExtracted(
                context,
                RuntimePaths.normalProjectDir(context)
            )
        }.getOrNull() ?: return
        if (!NodeProjectManager.hasSelectedCoreInstalled(context, projectDir)) return

        NodeService.start(context.applicationContext)
    }
}
