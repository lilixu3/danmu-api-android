package com.example.danmuapiapp.data.service

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Current playback snapshot returned by FongMi / OK影视 compatible /media endpoint. */
data class VideoShellPlaybackMedia(
    val title: String,
    val episodeText: String,
    val url: String,
    val state: Int,
    val episodeNumber: Int? = null,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L
) {
    val displayEpisode: String
        get() = episodeText.ifBlank { episodeNumber?.let { "第${it}集" }.orEmpty() }

    val signature: String
        get() = listOf(title, displayEpisode, episodeNumber?.toString().orEmpty(), url)
            .joinToString("|") { it.trim() }
}

data class VideoShellAutoPushState(
    val enabled: Boolean = false,
    val running: Boolean = false,
    val discoveredPort: Int? = null,
    val currentMedia: VideoShellPlaybackMedia? = null,
    val lastCandidateUrl: String = "",
    val lastPushUrl: String = "",
    val lastMessage: String = "未启动",
    val lastError: String? = null,
    val lastUpdatedAt: Long = 0L,
    val pushCount: Int = 0
)

class VideoShellPushDedupe {
    private var lastPushedSignature: String? = null

    fun shouldPush(media: VideoShellPlaybackMedia): Boolean {
        val signature = media.signature
        if (signature.isBlank()) return false
        return signature != lastPushedSignature
    }

    fun markPushed(media: VideoShellPlaybackMedia) {
        lastPushedSignature = media.signature
    }

    fun reset() {
        lastPushedSignature = null
    }
}

object VideoShellAutoPushLogic {
    fun parseMediaJson(raw: String): VideoShellPlaybackMedia? {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val title = readString(root, "title", "name", "vodName", "vod_name")
        val url = readString(root, "url", "path", "playUrl", "play_url")
        val state = readInt(root, "state", "playState", "play_state") ?: 0
        val episodeNumber = readInt(root, "episode", "episodeNumber", "number", "sort", "index")
        val episodeText = readString(root, "artist", "episodeTitle", "episodeName", "remarks", "remark", "subtitle")
            .ifBlank { episodeNumber?.let { "第${it}集" }.orEmpty() }
        val duration = readLong(root, "duration", "durationMs", "duration_ms") ?: 0L
        val position = readLong(root, "position", "positionMs", "position_ms", "progress") ?: 0L

        if (title.isBlank() && url.isBlank()) return null
        if (title.isBlank()) return null

        return VideoShellPlaybackMedia(
            title = title,
            episodeText = episodeText,
            url = url,
            state = state,
            episodeNumber = episodeNumber,
            durationMs = duration,
            positionMs = position
        )
    }

    fun buildCoreFongmiDanmakuUrl(coreBaseUrl: String, media: VideoShellPlaybackMedia): String {
        val base = normalizeBaseUrl(coreBaseUrl)
        val name = urlEncode(media.title)
        val episode = urlEncode(media.displayEpisode.ifBlank { media.url })
        return "$base/api/v2/fongmi/danmaku?name=$name&episode=$episode"
    }

    fun selectCandidateUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        val array = when {
            trimmed.startsWith("[") -> runCatching { JSONArray(trimmed) }.getOrNull()
            else -> {
                val root = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
                root.optJSONArray("data")
                    ?: root.optJSONArray("items")
                    ?: root.optJSONArray("list")
                    ?: root.optJSONArray("results")
            }
        } ?: return null

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val url = readString(item, "url", "path", "danmaku", "danmu")
            if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                return url
            }
        }
        return null
    }

    fun buildShellPushUrl(port: Int, danmakuUrl: String): String {
        return "http://127.0.0.1:$port/action?do=refresh&type=danmaku&path=${urlEncode(danmakuUrl)}"
    }

    fun shellMediaUrl(port: Int): String = "http://127.0.0.1:$port/media"

    private fun normalizeBaseUrl(raw: String): String {
        val value = raw.trim().ifBlank { "http://127.0.0.1:9321" }
        val withScheme = if (
            value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)
        ) value else "http://$value"
        return withScheme.trimEnd('/')
    }

    private fun urlEncode(raw: String): String = URLEncoder.encode(raw, Charsets.UTF_8.name())

    private fun readString(obj: JSONObject, vararg keys: String): String {
        keys.forEach { key ->
            if (!obj.has(key)) return@forEach
            val value = obj.optString(key, "").trim()
            if (value.isNotBlank() && !value.equals("null", ignoreCase = true)) return value
        }
        return ""
    }

    private fun readInt(obj: JSONObject, vararg keys: String): Int? {
        keys.forEach { key ->
            if (!obj.has(key)) return@forEach
            val raw = obj.opt(key) ?: return@forEach
            when (raw) {
                is Number -> return raw.toInt()
                is String -> raw.trim().toIntOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun readLong(obj: JSONObject, vararg keys: String): Long? {
        keys.forEach { key ->
            if (!obj.has(key)) return@forEach
            val raw = obj.opt(key) ?: return@forEach
            when (raw) {
                is Number -> return raw.toLong()
                is String -> raw.trim().toLongOrNull()?.let { return it }
            }
        }
        return null
    }
}
