package com.example.danmuapiapp.domain.model

/**
 * 普通模式前台服务通知被用户手动划掉后的处理方式。
 */
enum class NormalNotificationBehavior(
    val storageValue: String,
    val label: String,
    val description: String
) {
    ForegroundRestore(
        storageValue = "foreground_restore",
        label = "前台恢复",
        description = "手动划掉后暂不立即补发，下一次离开应用时恢复通知"
    ),
    ImmediateRestore(
        storageValue = "immediate_restore",
        label = "划掉即恢复",
        description = "手动划掉后立即重新显示服务通知"
    ),
    RespectDismissal(
        storageValue = "respect_dismissal",
        label = "尊重关闭",
        description = "手动划掉后保持隐藏，重新启动服务或切换策略后恢复"
    );

    companion object {
        fun fromStorageValue(value: String?): NormalNotificationBehavior {
            return entries.firstOrNull { it.storageValue == value?.trim()?.lowercase() }
                ?: ForegroundRestore
        }
    }
}
