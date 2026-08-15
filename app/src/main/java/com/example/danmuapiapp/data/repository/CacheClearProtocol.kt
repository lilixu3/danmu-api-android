package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.CacheClearItem
import com.example.danmuapiapp.domain.model.CacheClearSupport
import org.json.JSONArray
import org.json.JSONObject

internal object CacheClearProtocol {
    data class Response(
        val success: Boolean?,
        val message: String?,
        val clearedItems: Set<String>?
    )

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

    fun parseResponse(raw: String): Response {
        val root = runCatching { JSONObject(raw) }.getOrNull()
            ?: return Response(success = null, message = null, clearedItems = null)
        val success = if (root.has("success") && !root.isNull("success")) {
            root.optBoolean("success")
        } else {
            null
        }
        val message = root.optString("message")
            .ifBlank { root.optString("errorMessage") }
            .takeIf { it.isNotBlank() }
        val cleared = root.optJSONObject("clearedItems")
            ?.keys()
            ?.asSequence()
            ?.toSet()
        return Response(success = success, message = message, clearedItems = cleared)
    }
}
