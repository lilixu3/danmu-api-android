package com.example.danmuapiapp.data.repository

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import com.example.danmuapiapp.domain.model.DanmuDownloadRecord
import com.example.danmuapiapp.domain.model.DanmuDownloadSettings
import com.example.danmuapiapp.domain.model.DanmuDownloadTask
import com.example.danmuapiapp.domain.model.DownloadConflictPolicy
import com.example.danmuapiapp.domain.model.DownloadThrottlePreset
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 下载设置、下载记录与下载队列的本地持久化（SharedPreferences + JSON）。
 *
 * 只做读写与序列化，内存状态与业务规则仍由 [DanmuDownloadRepositoryImpl] 持有；
 * 序列化失败时保留上一次持久化内容，不覆写为空。
 */
internal class DanmuDownloadStore(context: Context) {

    companion object {
        private const val PREFS_NAME = "danmu_download"
        private const val KEY_SAVE_TREE_URI = "save_tree_uri"
        private const val KEY_SAVE_DIR_DISPLAY = "save_dir_display"
        private const val KEY_DEFAULT_FORMAT = "default_format"
        private const val KEY_FILE_TEMPLATE = "file_template"
        private const val KEY_CONFLICT_POLICY = "conflict_policy"
        private const val KEY_THROTTLE_PRESET = "throttle_preset"
        private const val KEY_THROTTLE_CUSTOM_BASE_DELAY = "throttle_custom_base_delay_ms"
        private const val KEY_THROTTLE_CUSTOM_JITTER = "throttle_custom_jitter_ms"
        private const val KEY_THROTTLE_CUSTOM_BATCH_SIZE = "throttle_custom_batch_size"
        private const val KEY_THROTTLE_CUSTOM_BATCH_REST = "throttle_custom_batch_rest_ms"
        private const val KEY_THROTTLE_CUSTOM_BACKOFF_BASE = "throttle_custom_backoff_base_ms"
        private const val KEY_THROTTLE_CUSTOM_BACKOFF_MAX = "throttle_custom_backoff_max_ms"
        private const val KEY_RECORDS_JSON = "records_json"
        private const val KEY_QUEUE_JSON = "queue_json"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun persistSettings(next: DanmuDownloadSettings) {
        prefs.edit {
            putString(KEY_SAVE_TREE_URI, next.saveTreeUri)
            putString(KEY_SAVE_DIR_DISPLAY, next.saveDirDisplayName)
            putString("save_dir_path", next.saveDirPath)
            putString("match_mode", next.matchMode)
            putBoolean("auto_import_local", next.autoImportToLocal)
            putBoolean("delete_source_after_import", next.deleteSourceAfterImport)
            putString(KEY_DEFAULT_FORMAT, next.defaultFormat)
            putString(KEY_FILE_TEMPLATE, next.fileNameTemplate)
            putString(KEY_CONFLICT_POLICY, next.conflictPolicy)
            putString(KEY_THROTTLE_PRESET, next.throttlePreset)
            putLong(KEY_THROTTLE_CUSTOM_BASE_DELAY, next.customBaseDelayMs)
            putLong(KEY_THROTTLE_CUSTOM_JITTER, next.customJitterMaxMs)
            putInt(KEY_THROTTLE_CUSTOM_BATCH_SIZE, next.customBatchSize)
            putLong(KEY_THROTTLE_CUSTOM_BATCH_REST, next.customBatchRestMs)
            putLong(KEY_THROTTLE_CUSTOM_BACKOFF_BASE, next.customBackoffBaseMs)
            putLong(KEY_THROTTLE_CUSTOM_BACKOFF_MAX, next.customBackoffMaxMs)
        }
    }

    fun persistRecords(records: List<DanmuDownloadRecord>) {
        val payload = runCatching {
            json.encodeToString(ListSerializer(DanmuDownloadRecord.serializer()), records)
        }.onFailure {
            Log.w("DanmuDownloadRepo", "序列化下载记录失败，不覆写持久化", it)
        }.getOrNull()
        if (payload != null) {
            prefs.edit { putString(KEY_RECORDS_JSON, payload) }
        }
    }

    fun persistQueueTasks(tasks: List<DanmuDownloadTask>) {
        val payload = runCatching {
            json.encodeToString(ListSerializer(DanmuDownloadTask.serializer()), tasks)
        }.onFailure {
            Log.w("DanmuDownloadRepo", "序列化队列任务失败，不覆写持久化", it)
        }.getOrNull()
        if (payload != null) {
            prefs.edit { putString(KEY_QUEUE_JSON, payload) }
        }
    }

    fun loadSettings(): DanmuDownloadSettings {
        return DanmuDownloadSettings(
            saveTreeUri = prefs.getString(KEY_SAVE_TREE_URI, "").orEmpty(),
            saveDirDisplayName = prefs.getString(KEY_SAVE_DIR_DISPLAY, "").orEmpty(),
            saveDirPath = prefs.getString("save_dir_path", "").orEmpty(),
            matchMode = prefs.getString("match_mode", "match").orEmpty(),
            autoImportToLocal = prefs.getBoolean("auto_import_local", false),
            deleteSourceAfterImport = prefs.getBoolean("delete_source_after_import", false),
            defaultFormat = prefs.getString(KEY_DEFAULT_FORMAT, DanmuDownloadFormat.Xml.value).orEmpty(),
            fileNameTemplate = prefs.getString(KEY_FILE_TEMPLATE, DanmuDownloadSettings().fileNameTemplate).orEmpty(),
            conflictPolicy = prefs.getString(KEY_CONFLICT_POLICY, DownloadConflictPolicy.Rename.key).orEmpty(),
            throttlePreset = prefs.getString(KEY_THROTTLE_PRESET, DownloadThrottlePreset.Conservative.key).orEmpty(),
            customBaseDelayMs = prefs.getLong(
                KEY_THROTTLE_CUSTOM_BASE_DELAY,
                DanmuDownloadSettings().customBaseDelayMs
            ),
            customJitterMaxMs = prefs.getLong(
                KEY_THROTTLE_CUSTOM_JITTER,
                DanmuDownloadSettings().customJitterMaxMs
            ),
            customBatchSize = prefs.getInt(
                KEY_THROTTLE_CUSTOM_BATCH_SIZE,
                DanmuDownloadSettings().customBatchSize
            ),
            customBatchRestMs = prefs.getLong(
                KEY_THROTTLE_CUSTOM_BATCH_REST,
                DanmuDownloadSettings().customBatchRestMs
            ),
            customBackoffBaseMs = prefs.getLong(
                KEY_THROTTLE_CUSTOM_BACKOFF_BASE,
                DanmuDownloadSettings().customBackoffBaseMs
            ),
            customBackoffMaxMs = prefs.getLong(
                KEY_THROTTLE_CUSTOM_BACKOFF_MAX,
                DanmuDownloadSettings().customBackoffMaxMs
            )
        )
    }

    fun loadRecords(): List<DanmuDownloadRecord> {
        val raw = prefs.getString(KEY_RECORDS_JSON, "[]").orEmpty()
        return runCatching {
            json.decodeFromString(ListSerializer(DanmuDownloadRecord.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    fun loadQueueTasks(): List<DanmuDownloadTask> {
        val raw = prefs.getString(KEY_QUEUE_JSON, "[]").orEmpty()
        return runCatching {
            json.decodeFromString(ListSerializer(DanmuDownloadTask.serializer()), raw)
        }.getOrDefault(emptyList())
    }
}
