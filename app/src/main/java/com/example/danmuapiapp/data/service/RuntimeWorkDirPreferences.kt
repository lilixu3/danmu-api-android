package com.example.danmuapiapp.data.service

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.net.toUri
import com.example.danmuapiapp.data.util.safeGetString
import java.io.File

/**
 * 工作目录偏好存储。
 *
 * 只负责 `danmu_work_dir` 偏好中的自定义路径与 SAF URI 的读写；
 * 路径规范化与可用性判定交给 [RuntimePathResolver]。
 */
@SuppressLint("ApplySharedPref")
internal object RuntimeWorkDirPreferences {

    const val PREFS_NAME = "danmu_work_dir"
    const val KEY_CUSTOM_BASE_PATH = "custom_path"
    const val KEY_CUSTOM_BASE_URI = "custom_uri"

    fun isSelectionKey(key: String?): Boolean {
        return key == KEY_CUSTOM_BASE_PATH || key == KEY_CUSTOM_BASE_URI
    }

    fun readCustomBasePath(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.safeGetString(KEY_CUSTOM_BASE_PATH).trim()
        if (raw.isNotBlank()) return raw
        val uriText = prefs.safeGetString(KEY_CUSTOM_BASE_URI).trim()
        if (uriText.isBlank()) return null
        val uri = runCatching { uriText.toUri() }.getOrNull() ?: return null
        return RuntimePathResolver.resolveTreeUriToPath(uri)
    }

    fun readCustomBaseDir(context: Context): File? {
        val raw = readCustomBasePath(context) ?: return null
        return RuntimePathResolver.normalizeBaseDir(File(raw))
    }

    fun clearCustomBasePath(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CUSTOM_BASE_PATH)
            .remove(KEY_CUSTOM_BASE_URI)
            .commit()
    }

    fun setCustomBasePath(context: Context, path: String?): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val value = path?.trim().orEmpty()
        val editor = prefs.edit()
        if (value.isBlank()) {
            editor.remove(KEY_CUSTOM_BASE_PATH)
        } else {
            editor.putString(KEY_CUSTOM_BASE_PATH, value)
        }
        return editor.commit()
    }
}
