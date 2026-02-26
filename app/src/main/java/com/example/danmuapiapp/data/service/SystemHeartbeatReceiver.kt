package com.example.danmuapiapp.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SystemHeartbeatReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != SystemHeartbeatScheduler.heartbeatAction(context.packageName)) return

        val pending = goAsync()
        Thread {
            try {
                SystemHeartbeatScheduler.handleAlarm(context.applicationContext)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
