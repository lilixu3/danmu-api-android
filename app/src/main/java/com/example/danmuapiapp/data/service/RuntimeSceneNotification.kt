package com.example.danmuapiapp.data.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.danmuapiapp.R

/**
 * 使用系统模板的运行状态通知。
 *
 * MIUI/Android 会为自定义通知补上系统头部，自定义 RemoteViews 容易造成标题和内容重复、
 * 展开区域高度异常。这里让系统负责折叠和操作区布局，只提供简洁摘要及展开后的两行信息。
 */
internal object RuntimeSceneNotification {

    data class Action(
        val iconResId: Int,
        val title: CharSequence,
        val intent: PendingIntent
    )

    fun build(
        context: Context,
        title: CharSequence,
        subtitle: CharSequence,
        infoTitle: CharSequence,
        infoText: CharSequence,
        contentIntent: PendingIntent,
        actions: List<Action> = emptyList(),
        deleteIntent: PendingIntent? = null
    ): Notification {
        val summary = NotificationEndpointTextPolicy.compact(
            status = subtitle,
            infoTitle = infoTitle,
            infoText = infoText
        )
        val details = NotificationEndpointTextPolicy.expanded(
            status = subtitle,
            infoTitle = infoTitle,
            infoText = infoText
        )

        val builder = NotificationCompat.Builder(context, ServiceNotificationChannels.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_danmu)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setShowWhen(false)
            .setAutoCancel(false)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOngoing(true)

        if (deleteIntent != null) {
            builder.setDeleteIntent(deleteIntent)
        }

        actions.forEach { action ->
            builder.addAction(action.iconResId, action.title, action.intent)
        }

        // 前台服务标记由 startForeground() 负责设置；这里不手动伪造系统通知标记。
        return builder.build()
    }
}
