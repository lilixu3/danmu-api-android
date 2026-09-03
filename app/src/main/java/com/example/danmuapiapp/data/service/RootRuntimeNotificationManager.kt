package com.example.danmuapiapp.data.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.danmuapiapp.MainActivity
import com.example.danmuapiapp.R

/**
 * Root 模式的服务通知。
 *
 * Root 运行时由 su 起的独立进程承载，不走前台服务，因此这里用普通常驻通知展示状态；
 * 开启“显示接口信息”后，通知附带当前接口地址。
 */
object RootRuntimeNotificationManager {

    const val NOTIFICATION_ID = 7

    fun show(context: Context, text: String, endpoint: String = "") {
        val appContext = context.applicationContext
        if (!NotificationDisplayPrefs.isRootNotificationEnabled(appContext) ||
            !NodeKeepAlivePrefs.hasPostNotificationsPermission(appContext)
        ) {
            cancel(appContext)
            return
        }
        ServiceNotificationChannels.ensureChannels(
            context = appContext,
            channelName = appContext.getString(R.string.notification_channel_name),
            channelDescription = appContext.getString(R.string.notification_channel_desc)
        )
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as? android.app.NotificationManager ?: return
        runCatching {
            manager.notify(NOTIFICATION_ID, buildNotification(appContext, text, endpoint))
        }.onFailure {
            AppDiagnosticLogger.w(appContext, TAG, "Root 模式通知发布失败：${it.message}", it)
        }
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as? android.app.NotificationManager ?: return
        runCatching { manager.cancel(NOTIFICATION_ID) }
    }

    private fun buildNotification(context: Context, text: String, endpoint: String): Notification {
        val pendingFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getActivity(
            context, 10,
            Intent(context, MainActivity::class.java),
            pendingFlags
        )
        val stopIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_STOP_RUNTIME
            setPackage(context.packageName)
        }
        val stopPendingIntent = PendingIntent.getBroadcast(context, 11, stopIntent, pendingFlags)
        val restartIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NodeService.ACTION_RESTART
            setPackage(context.packageName)
        }
        val restartPendingIntent = PendingIntent.getBroadcast(context, 12, restartIntent, pendingFlags)
        val endpointInfoEnabled = NotificationDisplayPrefs.isEndpointInfoEnabled(context)
        if (endpointInfoEnabled) {
            return RuntimeSceneNotification.build(
                context = context,
                title = context.getString(R.string.app_name),
                subtitle = text,
                infoTitle = context.getString(R.string.notification_scene_info_title),
                infoText = endpointLabel(context, endpoint),
                contentIntent = pendingIntent,
                actions = listOf(
                    RuntimeSceneNotification.Action(
                        iconResId = android.R.drawable.ic_menu_close_clear_cancel,
                        title = context.getString(R.string.notification_action_stop),
                        intent = stopPendingIntent
                    ),
                    RuntimeSceneNotification.Action(
                        iconResId = android.R.drawable.ic_popup_sync,
                        title = context.getString(R.string.notification_action_restart),
                        intent = restartPendingIntent
                    )
                )
            )
        }
        return NotificationCompat.Builder(context, ServiceNotificationChannels.CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_root_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification_danmu)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.notification_action_stop),
                stopPendingIntent
            )
            .addAction(
                android.R.drawable.ic_popup_sync,
                context.getString(R.string.notification_action_restart),
                restartPendingIntent
            )
            .build()
    }

    private fun endpointLabel(context: Context, endpoint: String): String {
        val authority = endpoint
            .substringAfter("://", "")
            .substringBefore('/')
            .trim()
        return authority.ifBlank {
            context.getString(R.string.notification_scene_fallback_info)
        }
    }

    private const val TAG = "RootNotifManager"
}
