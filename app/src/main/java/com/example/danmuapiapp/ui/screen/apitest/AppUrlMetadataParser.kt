package com.example.danmuapiapp.ui.screen.apitest

import org.json.JSONObject

/** Only consume the versioned App extension, never the core's synthetic URL search row. */
internal fun parseAppUrlMetadata(body: String, inputUrl: String): UrlDanmuMetadata? {
    val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
    if (!root.optBoolean("success")) return null
    val data = root.optJSONObject("appUrlMetadata") ?: return null
    if (data.optInt("version") != 1 || data.optString("inputUrl") != inputUrl) return null
    fun text(key: String) = data.optString(key).takeUnless { it == "null" }.orEmpty()
    val canonical = text("canonicalUrl").ifBlank { inputUrl }
    val array = data.optJSONArray("episodes")
    val episodes = (0 until (array?.length() ?: 0)).mapNotNull { index ->
        val ep = array?.optJSONObject(index) ?: return@mapNotNull null
        val url = ep.optString("url")
        val id = ep.optString("id")
        if (id.isBlank() || !(url.startsWith("https://") || url.startsWith("http://"))) return@mapNotNull null
        UrlDanmuEpisode(id, ep.optString("title"), url,
            ep.optInt("number", index + 1), ep.optString("label"), ep.optString("group"), ep.optString("posterUrl"))
    }.distinctBy { it.url }
    if (text("title").isBlank() && text("posterUrl").isBlank() && episodes.isEmpty()) return null
    return UrlDanmuMetadata(
        title = text("title"), episodeTitle = text("episodeTitle"), posterUrl = text("posterUrl"),
        year = text("year"), episodeLabel = text("episodeLabel"), platformLabel = text("platformLabel").ifBlank { "URL" },
        canonicalUrl = canonical, episodes = episodes, selectedEpisodeId = text("selectedEpisodeId"),
        episodesComplete = data.optBoolean("episodesComplete"), notice = text("notice")
    )
}
