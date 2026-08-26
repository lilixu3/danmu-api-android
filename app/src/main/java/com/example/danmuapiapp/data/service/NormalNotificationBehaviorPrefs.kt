package com.example.danmuapiapp.data.service

import android.content.Context
import androidx.core.content.edit
import com.example.danmuapiapp.domain.model.NormalNotificationBehavior
import java.io.File

/**
 * 普通模式通知策略及跨进程可见的手动关闭标记。
 *
 * SettingsRepository 在主进程写入，NodeService 在 :node 进程读取，因此除了
 * SharedPreferences 外再保留一个小型文件镜像，避免跨进程 SharedPreferences 缓存陈旧。
 */
object NormalNotificationBehaviorPrefs {

    private const val PREFS_SETTINGS = "settings"
    private const val KEY_BEHAVIOR = "normal_notification_behavior"
    private const val BEHAVIOR_FILE = "normal_notification_behavior"
    private const val DISMISSED_FILE = "normal_notification_manually_hidden"

    fun get(context: Context): NormalNotificationBehavior {
        val fileValue = readFile(context, BEHAVIOR_FILE)
        if (!fileValue.isNullOrBlank()) {
            return NormalNotificationBehavior.fromStorageValue(fileValue)
        }
        val preferenceValue = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            .getString(KEY_BEHAVIOR, NormalNotificationBehavior.ForegroundRestore.storageValue)
        return NormalNotificationBehavior.fromStorageValue(preferenceValue)
    }

    fun reloadFromSettings(context: Context): NormalNotificationBehavior {
        val preferenceValue = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            .getString(KEY_BEHAVIOR, NormalNotificationBehavior.ForegroundRestore.storageValue)
        val behavior = NormalNotificationBehavior.fromStorageValue(preferenceValue)
        writeFile(context, BEHAVIOR_FILE, behavior.storageValue)
        return behavior
    }

    fun set(context: Context, behavior: NormalNotificationBehavior) {
        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE).edit(commit = true) {
            putString(KEY_BEHAVIOR, behavior.storageValue)
        }
        writeFile(context, BEHAVIOR_FILE, behavior.storageValue)
    }

    fun isManuallyHidden(context: Context): Boolean {
        return File(context.filesDir, DISMISSED_FILE).isFile
    }

    fun setManuallyHidden(context: Context, hidden: Boolean) {
        val marker = File(context.filesDir, DISMISSED_FILE)
        if (hidden) {
            runCatching {
                marker.parentFile?.mkdirs()
                if (!marker.exists()) marker.createNewFile()
            }
        } else {
            runCatching { marker.delete() }
        }
    }

    fun clearManuallyHidden(context: Context) {
        setManuallyHidden(context, hidden = false)
    }

    fun shouldSuppressNotification(context: Context): Boolean {
        // 三种策略都需要暂时阻止普通的状态刷新把用户刚划掉的通知立刻带回；
        // 前台恢复策略会在下一次离开应用时清除该标记，尊重关闭策略则持续保留。
        return isManuallyHidden(context)
    }

    private fun readFile(context: Context, name: String): String? {
        return runCatching {
            File(context.filesDir, name).takeIf { it.isFile }?.readText(Charsets.UTF_8)?.trim()
        }.getOrNull()
    }

    private fun writeFile(context: Context, name: String, value: String) {
        runCatching {
            val target = File(context.filesDir, name)
            target.parentFile?.mkdirs()
            val temp = File(context.filesDir, "$name.tmp")
            temp.writeText(value, Charsets.UTF_8)
            if (!temp.renameTo(target)) {
                target.writeText(value, Charsets.UTF_8)
                temp.delete()
            }
        }
    }
}
