package com.example.danmuapiapp.data.repository

import android.content.Context
import androidx.core.content.edit
import com.example.danmuapiapp.domain.model.LocalDanmuSourceKind
import com.example.danmuapiapp.domain.model.LocalDanmuSourceRef
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers where an imported resource came from so the detail page can preview it
 * without asking the core to return potentially huge comment arrays.
 */
@Singleton
class LocalDanmuSourceStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val PREFS_NAME = "local_danmu_sources"
        private const val KEY_SOURCES = "sources_json"
        private const val MAX_ENTRIES = 10_000
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized fun save(libraryId: String, ref: LocalDanmuSourceRef) {
        require(libraryId.isNotBlank())
        if (ref.resourceKey.isBlank() || ref.uri.isBlank()) return
        val current = readAll().toMutableMap()
        current[scopedKey(libraryId, ref.resourceKey)] = ref.copy(libraryId = libraryId, updatedAt = System.currentTimeMillis())
        val trimmed = current.entries
            .sortedByDescending { it.value.updatedAt }
            .take(MAX_ENTRIES)
            .associate { it.key to it.value }
        writeAll(trimmed)
    }

    @Synchronized fun get(libraryId: String, resourceKey: String): LocalDanmuSourceRef? {
        if (libraryId.isBlank()) return null
        if (resourceKey.isBlank()) return null
        return readAll()[scopedKey(libraryId, resourceKey)]
    }

    @Synchronized fun all(libraryId: String): List<LocalDanmuSourceRef> =
        readAll().values.filter { libraryId.isNotBlank() && it.libraryId == libraryId }

    @Synchronized fun remove(libraryId: String, resourceKeys: Collection<String>) {
        require(libraryId.isNotBlank())
        if (resourceKeys.isEmpty()) return
        val current = readAll().toMutableMap()
        var changed = false
        resourceKeys.forEach { key ->
            if (current.remove(scopedKey(libraryId, key)) != null) changed = true
        }
        if (changed) writeAll(current)
    }

    /**
     * 编辑元数据会重算 resourceKey（核心会删掉旧 key 文件），这里把来源记录迁到新 key，
     * 否则改完标题/集数后详情页预览会因为找不到 uri 而失效。
     */
    @Synchronized fun migrate(libraryId: String, mappings: Map<String, String>) {
        require(libraryId.isNotBlank())
        if (mappings.isEmpty()) return
        val current = readAll().toMutableMap()
        var changed = false
        val now = System.currentTimeMillis()
        mappings.forEach { (oldKey, newKey) ->
            if (oldKey.isBlank() || newKey.isBlank() || oldKey == newKey) return@forEach
            val ref = current.remove(scopedKey(libraryId, oldKey)) ?: return@forEach
            current[scopedKey(libraryId, newKey)] = ref.copy(resourceKey = newKey, updatedAt = now)
            changed = true
        }
        if (changed) writeAll(current)
    }

    private fun readAll(): Map<String, LocalDanmuSourceRef> {
        val raw = prefs.getString(KEY_SOURCES, "").orEmpty()
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val array = JSONArray(raw)
            buildMap {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val key = obj.optString("resourceKey").trim()
                    if (key.isBlank()) continue
                    put(
                        scopedKey(obj.optString("libraryId"), key),
                        LocalDanmuSourceRef(
                            resourceKey = key,
                            kind = runCatching {
                                LocalDanmuSourceKind.valueOf(obj.optString("kind"))
                            }.getOrDefault(LocalDanmuSourceKind.File),
                            uri = obj.optString("uri"),
                            entryName = obj.optString("entryName"),
                            displayName = obj.optString("displayName"),
                            format = obj.optString("format"),
                            sizeBytes = obj.optLong("sizeBytes", 0L),
                            updatedAt = obj.optLong("updatedAt", 0L),
                            libraryId = obj.optString("libraryId")
                        )
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun writeAll(values: Map<String, LocalDanmuSourceRef>) {
        val array = JSONArray()
        values.values.forEach { ref ->
            array.put(
                JSONObject()
                    .put("resourceKey", ref.resourceKey)
                    .put("libraryId", ref.libraryId)
                    .put("kind", ref.kind.name)
                    .put("uri", ref.uri)
                    .put("entryName", ref.entryName)
                    .put("displayName", ref.displayName)
                    .put("format", ref.format)
                    .put("sizeBytes", ref.sizeBytes)
                    .put("updatedAt", ref.updatedAt)
            )
        }
        prefs.edit { putString(KEY_SOURCES, array.toString()) }
    }

    private fun scopedKey(libraryId: String, resourceKey: String): String = scopedLocalResourceKey(libraryId, resourceKey)
}
