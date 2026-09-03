package com.example.danmuapiapp.data.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/** 服务通知沿用远程版本已有的低重要性渠道，保留用户原来的渠道设置。 */
object ServiceNotificationChannels {

    const val CHANNEL_ID = "danmuapi_service"
    private const val FAILED_DEFAULT_CHANNEL_ID = "danmuapi_service_status_v2"
    private const val LEGACY_PINNED_CHANNEL_ID = "danmuapi_service_pinned"

    fun isServiceChannelId(channelId: String?): Boolean {
        return channelId == CHANNEL_ID ||
            channelId == FAILED_DEFAULT_CHANNEL_ID ||
            channelId == LEGACY_PINNED_CHANNEL_ID
    }

    fun ensureChannels(context: Context, channelName: String, channelDescription: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        runCatching {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    channelName,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = channelDescription
                    setShowBadge(false)
                }
            )
            // 清理由本轮失败方案创建的渠道，避免系统通知设置里继续出现重复项目。
            manager.deleteNotificationChannel(FAILED_DEFAULT_CHANNEL_ID)
            manager.deleteNotificationChannel(LEGACY_PINNED_CHANNEL_ID)
        }
    }
}
