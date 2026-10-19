package com.example.danmuapiapp.data.service

import android.content.Context
import androidx.core.content.edit
import com.example.danmuapiapp.data.util.pushTargetAddressKey
import com.example.danmuapiapp.data.util.resolvePushTargetAddress

/**
 * 推送成功的推送目标。只保留最近 [MAX_ENTRIES] 条，且按最近一次成功推送排序。
 * 保存配置默认不会写入这里，避免快速切换被未推送过的地址占满。
 */
class PushTargetHistoryStore(context: Context) {

    companion object {
        const val MAX_ENTRIES = 3
        private const val PREFS_NAME = "danmu_push_targets"
        private const val KEY_RECENT = "recent_successful_targets"
        private const val SEPARATOR = "\n"

        internal fun mergeRecent(existing: List<String>, target: String): List<String> {
            val normalized = target.trim()
            if (normalized.isBlank()) return normalizeRecent(existing)
            return normalizeRecent(listOf(normalized) + existing)
        }

        internal fun normalizeRecent(values: List<String>): List<String> {
            return values
                .map { it.trim() }
                .filter { resolvePushTargetAddress(it) != null }
                .distinctBy(::pushTargetAddressKey)
                .take(MAX_ENTRIES)
        }
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recent(): List<String> {
        val raw = prefs.getString(KEY_RECENT, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return normalizeRecent(raw.split(SEPARATOR))
    }

    fun remember(target: String): List<String> {
        val normalized = target.trim()
        if (normalized.isBlank()) return recent()
        val updated = mergeRecent(recent(), normalized)
        prefs.edit {
            putString(KEY_RECENT, updated.joinToString(SEPARATOR))
        }
        return updated
    }

    fun remove(target: String): List<String> {
        val normalized = target.trim()
        if (normalized.isBlank()) return recent()
        val updated = recent().filterNot { pushTargetAddressKey(it) == pushTargetAddressKey(normalized) }
        prefs.edit {
            putString(KEY_RECENT, updated.joinToString(SEPARATOR))
        }
        return updated
    }
}
