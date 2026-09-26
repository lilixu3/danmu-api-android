package com.example.danmuapiapp.data.repository

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 记住本地弹幕导入时浏览过的目录。
 *
 * 用于两件事：
 * 1. 下次打开文件列表时回到上次浏览的目录；
 * 2. 记住用户显式指定的默认目录（优先级高于下载页配置）。
 */
@Singleton
class LocalDanmuUploadHistoryStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val PREFS_NAME = "local_danmu_upload_history"
        private const val KEY_DIRECTORY = "recent_directory"
        private const val KEY_CUSTOM_DIRECTORY = "custom_directory"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recentDirectory(): String = prefs.getString(KEY_DIRECTORY, "").orEmpty()

    fun saveRecentDirectory(path: String) {
        if (path.isBlank()) return
        prefs.edit { putString(KEY_DIRECTORY, path) }
    }

    /** 用户显式指定的默认目录（优先级高于下载页配置）。 */
    fun customDirectory(): String = prefs.getString(KEY_CUSTOM_DIRECTORY, "").orEmpty()

    fun saveCustomDirectory(path: String) {
        if (path.isBlank()) return
        prefs.edit {
            putString(KEY_CUSTOM_DIRECTORY, path)
            putString(KEY_DIRECTORY, path)
        }
    }

}
