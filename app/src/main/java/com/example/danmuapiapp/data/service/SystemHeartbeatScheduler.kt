package com.example.danmuapiapp.data.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * 系统定时心跳（实验）调度器。
 *
 * 说明：
 * - 该模式不依赖无障碍服务。
 * - Android 省电策略会延迟触发时间，因此仅作为兜底方案。
 */
object SystemHeartbeatScheduler {

    private const val REQUEST_CODE = 31029

    fun heartbeatAction(packageName: String): String {
        return "$packageName.action.SYSTEM_HEARTBEAT_TICK"
    }

    fun refresh(context: Context) {
        val appContext = context.applicationContext
        if (!NodeKeepAlivePrefs.shouldScheduleSystemHeartbeat(appContext)) {
            cancel(appContext)
            return
        }
        scheduleNext(appContext, resetBeforeSchedule = true)
    }

    fun handleAlarm(context: Context) {
        val appContext = context.applicationContext
        if (!NodeKeepAlivePrefs.shouldScheduleSystemHeartbeat(appContext)) {
            cancel(appContext)
            return
        }

        scheduleNext(appContext, resetBeforeSchedule = false)
        tickOnce(appContext)
    }

    private fun tickOnce(context: Context) {
        if (!NodeKeepAlivePrefs.shouldScheduleSystemHeartbeat(context)) return
        NodeHeartbeatRecovery.tick(context, "SystemHeartbeat") {
            NodeKeepAlivePrefs.shouldScheduleSystemHeartbeat(context)
        }
    }

    private fun scheduleNext(context: Context, resetBeforeSchedule: Boolean) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pendingIntent = buildPendingIntent(context, create = true) ?: return
            if (resetBeforeSchedule) {
                alarmManager.cancel(pendingIntent)
            }

            val intervalMinutes = NodeKeepAlivePrefs.getEffectiveSystemHeartbeatIntervalMinutes(context)
            val triggerAt = SystemClock.elapsedRealtime() + intervalMinutes * 60_000L
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } catch (error: Exception) {
            AppDiagnosticLogger.w(context, "SystemHeartbeat", "无法调度系统心跳，不影响手动启停服务：${error.message}", error)
        }
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        try {
            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pendingIntent = buildPendingIntent(appContext, create = false) ?: return
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        } catch (error: Exception) {
            AppDiagnosticLogger.w(appContext, "SystemHeartbeat", "取消系统心跳失败；停止意图仍会阻止恢复", error)
        }
    }

    private fun buildPendingIntent(context: Context, create: Boolean): PendingIntent? {
        val flagsBase = PendingIntent.FLAG_IMMUTABLE
        val flags = if (create) {
            flagsBase or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            flagsBase or PendingIntent.FLAG_NO_CREATE
        }
        val intent = Intent(context, SystemHeartbeatReceiver::class.java).apply {
            action = heartbeatAction(context.packageName)
            setPackage(context.packageName)
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

}
