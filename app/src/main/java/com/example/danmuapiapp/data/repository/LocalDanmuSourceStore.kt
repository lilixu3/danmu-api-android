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

    fun save(ref: LocalDanmuSourceRef) {
        if (ref.resourceKey.isBlank() || ref.uri.isBlank()) return
        val current = readAll().toMutableMap()
        current[ref.resourceKey] = ref.copy(updatedAt = System.currentTimeMillis())
        val trimmed = current.entries
            .sortedByDescending { it.value.updatedAt }
            .take(MAX_ENTRIES)
            .associate { it.key to it.value }
        writeAll(trimmed)
    }

    fun get(resourceKey: String): LocalDanmuSourceRef? {
        if (resourceKey.isBlank()) return null
        return readAll()[resourceKey]
    }

    fun all(): List<LocalDanmuSourceRef> = readAll().values.toList()

    fun remove(resourceKeys: Collection<String>) {
        if (resourceKeys.isEmpty()) return
        val current = readAll().toMutableMap()
        var changed = false
        resourceKeys.forEach { key ->
            if (current.remove(key) != null) changed = true
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
                        key,
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
                            updatedAt = obj.optLong("updatedAt", 0L)
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
}
