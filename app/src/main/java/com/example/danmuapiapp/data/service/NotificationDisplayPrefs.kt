package com.example.danmuapiapp.data.service

import android.content.Context
import androidx.core.content.edit
import java.io.File

/**
 * 服务通知的接口信息展示开关。
 *
 * 该开关需要被 :node 进程读取（NodeService 构建通知时决定是否展示接口信息），
 * 因此除 SharedPreferences 外保留文件镜像，避免跨进程缓存陈旧。
 *
 * 文件名和 SharedPreferences 键沿用旧版的 pinned 命名，只为兼容已安装版本的数据。
 */
object NotificationDisplayPrefs {

    private const val PREFS_SETTINGS = "settings"
    private const val LEGACY_KEY_PINNED = "service_notification_pinned"
    private const val KEY_ROOT_NOTIFICATION = "root_mode_notification_enabled"
    private const val LEGACY_PINNED_FILE = "service_notification_pinned"

    fun isEndpointInfoEnabled(context: Context): Boolean {
        readMirror(context, LEGACY_PINNED_FILE)?.let { return it }
        return context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            .getBoolean(LEGACY_KEY_PINNED, false)
    }

    fun setEndpointInfoEnabled(context: Context, enabled: Boolean) {
        writeMirror(context, LEGACY_PINNED_FILE, enabled)
        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE).edit(commit = true) {
            putBoolean(LEGACY_KEY_PINNED, enabled)
        }
    }

    /**
     * 旧命名仅保留给可能仍在使用旧接口的代码；它不代表 Android 通知会被系统置顶。
     */
    @Deprecated("Use isEndpointInfoEnabled")
    fun isNotificationPinned(context: Context): Boolean = isEndpointInfoEnabled(context)

    @Deprecated("Use setEndpointInfoEnabled")
    fun setNotificationPinned(context: Context, pinned: Boolean) {
        setEndpointInfoEnabled(context, pinned)
    }

    fun isRootNotificationEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ROOT_NOTIFICATION, false)
    }

    fun setRootNotificationEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE).edit(commit = true) {
            putBoolean(KEY_ROOT_NOTIFICATION, enabled)
        }
    }

    fun reloadEndpointInfoFromSettings(context: Context): Boolean {
        val preferenceValue = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            .getBoolean(LEGACY_KEY_PINNED, false)
        writeMirror(context, LEGACY_PINNED_FILE, preferenceValue)
        return preferenceValue
    }

    @Deprecated("Use reloadEndpointInfoFromSettings")
    fun reloadFromSettings(context: Context): Boolean = reloadEndpointInfoFromSettings(context)

    /** 镜像文件只接受 1/0，其他内容视为未写入以便回落到 SharedPreferences。 */
    internal fun parseMirror(value: String?): Boolean? {
        return when (value?.trim()) {
            "1" -> true
            "0" -> false
            else -> null
        }
    }

    private fun readMirror(context: Context, name: String): Boolean? {
        return runCatching {
            parseMirror(
                File(context.filesDir, name).takeIf { it.isFile }?.readText()
            )
        }.getOrNull()
    }

    private fun writeMirror(context: Context, name: String, value: Boolean) {
        runCatching {
            val target = File(context.filesDir, name)
            target.parentFile?.mkdirs()
            val temp = File(context.filesDir, "$name.tmp")
            temp.writeText(if (value) "1" else "0")
            if (!temp.renameTo(target)) {
                target.writeText(if (value) "1" else "0")
                temp.delete()
            }
        }
    }
}
