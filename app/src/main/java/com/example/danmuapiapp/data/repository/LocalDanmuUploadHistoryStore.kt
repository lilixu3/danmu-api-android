package com.example.danmuapiapp.data.repository

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 记住上一次手动选择的弹幕文件。
 *
 * 系统文件选择器（ACTION_OPEN_DOCUMENT）每次都会回到“最近使用”，厂商 ROM 上更明显，
 * 所以这里保留上一份文件的持久化读取授权，用于：
 * 1. 一键复用上次选中的文件；
 * 2. 作为 EXTRA_INITIAL_URI 让选择器尽量停在原来的目录。
 */
@Singleton
class LocalDanmuUploadHistoryStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class RecentFile(
        val uri: String,
        val displayName: String,
        val sizeBytes: Long = 0L,
        val formatHint: String = "",
        val mimeType: String = "",
        val updatedAt: Long = 0L
    )

    companion object {
        private const val PREFS_NAME = "local_danmu_upload_history"
        private const val KEY_URI = "recent_uri"
        private const val KEY_NAME = "recent_name"
        private const val KEY_SIZE = "recent_size"
        private const val KEY_FORMAT = "recent_format"
        private const val KEY_MIME = "recent_mime"
        private const val KEY_UPDATED_AT = "recent_updated_at"
        private const val KEY_DIRECTORY = "recent_directory"
        private const val KEY_CUSTOM_DIRECTORY = "custom_directory"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recent(): RecentFile? {
        val uri = prefs.getString(KEY_URI, "").orEmpty()
        if (uri.isBlank()) return null
        return RecentFile(
            uri = uri,
            displayName = prefs.getString(KEY_NAME, "").orEmpty(),
            sizeBytes = prefs.getLong(KEY_SIZE, 0L),
            formatHint = prefs.getString(KEY_FORMAT, "").orEmpty(),
            mimeType = prefs.getString(KEY_MIME, "").orEmpty(),
            updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L)
        )
    }

    fun save(file: RecentFile) {
        if (file.uri.isBlank()) return
        prefs.edit {
            putString(KEY_URI, file.uri)
            putString(KEY_NAME, file.displayName)
            putLong(KEY_SIZE, file.sizeBytes)
            putString(KEY_FORMAT, file.formatHint)
            putString(KEY_MIME, file.mimeType)
            putLong(KEY_UPDATED_AT, System.currentTimeMillis())
        }
    }

    fun clear() {
        prefs.edit { clear() }
    }

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
