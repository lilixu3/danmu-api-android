package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.CacheClearItem
import com.example.danmuapiapp.domain.model.CacheClearSupport
import org.json.JSONArray
import org.json.JSONObject

internal object CacheClearProtocol {
    private val selectiveMarkers = listOf(
        "clearActions",
        "Array.isArray",
        "effectiveItems",
        "parsed.items"
    )

    fun detectSupport(systemApiSource: String?, workerSource: String?): CacheClearSupport {
        val source = systemApiSource?.takeIf { it.isNotBlank() }
            ?: return CacheClearSupport.Unknown
        val worker = workerSource?.takeIf { it.isNotBlank() }
            ?: return CacheClearSupport.Unknown
        val hasProtocol = selectiveMarkers.all(source::contains) &&
            CacheClearItem.entries.all { item ->
                Regex("""\b${Regex.escape(item.wireKey)}\s*:""").containsMatchIn(source)
            } &&
            worker.contains("/api/cache/clear") &&
            Regex("""handleClearCache\s*\(\s*req\s*\)""").containsMatchIn(worker)
        return if (hasProtocol) CacheClearSupport.Selective else CacheClearSupport.LegacyAllOnly
    }

    fun requestBody(items: Set<CacheClearItem>): String {
        val ordered = CacheClearItem.entries.filter(items::contains)
        return JSONObject()
            .put("items", JSONArray(ordered.map(CacheClearItem::wireKey)))
            .toString()
    }

    fun parseClearedItems(raw: String): Set<String>? {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val cleared = root.optJSONObject("clearedItems") ?: return null
        return cleared.keys().asSequence().toSet()
    }
}
