package com.example.danmuapiapp.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class SystemHeartbeatReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != SystemHeartbeatScheduler.heartbeatAction(context.packageName)) return

        val pending = goAsync()
        Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            try {
                runCatching {
                    SystemHeartbeatScheduler.handleAlarm(context.applicationContext)
                }.onFailure {
                    Log.e("HeartbeatReceiver", "系统心跳恢复执行失败", it)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
