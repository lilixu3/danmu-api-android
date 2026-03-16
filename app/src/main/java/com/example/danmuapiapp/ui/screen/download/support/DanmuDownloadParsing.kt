package com.example.danmuapiapp.ui.screen.download

import com.example.danmuapiapp.domain.model.DanmuDownloadTask
import com.example.danmuapiapp.domain.model.DownloadQueueStatus
import com.example.danmuapiapp.domain.model.DownloadRecordStatus
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

internal fun parseAnimeCandidates(raw: String): List<DownloadAnimeCandidate> {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return emptyList()
    val arr = when {
        trimmed.startsWith("[") -> runCatching { JSONArray(trimmed) }.getOrElse { return emptyList() }
        else -> {
            val root = runCatching { JSONObject(trimmed) }.getOrElse { return emptyList() }
            root.optJSONArray("animes") ?: root.optJSONArray("data") ?: JSONArray()
        }
    }
    val out = ArrayList<DownloadAnimeCandidate>(arr.length())
    for (i in 0 until arr.length()) {
        val item = arr.optJSONObject(i) ?: continue
        val animeId = readLong(item, "animeId", "id")
        val title = readString(item, "animeTitle", "title", "name")
        if (animeId <= 0L || title.isBlank()) continue
        val count = readInt(item, "episodeCount", "totalEpisodes", "count").coerceAtLeast(0)
        val imageUrl = readString(item, "imageUrl", "cover", "thumb", "poster", "pic").ifBlank {
            item.optJSONObject("image")?.let { image ->
                readString(image, "thumb", "poster", "url")
            }.orEmpty()
        }.ifBlank {
            item.optJSONObject("images")?.let { images ->
                readString(images, "common", "large", "medium", "small")
            }.orEmpty()
        }
        out += DownloadAnimeCandidate(
            animeId = animeId,
            title = title,
            episodeCount = count,
            imageUrl = imageUrl
        )
    }
    return out.distinctBy { it.animeId }
}

internal fun parseEpisodeCandidates(raw: String, fallbackSource: String = ""): List<DownloadEpisodeCandidate> {
    val root = runCatching { JSONObject(raw) }.getOrElse { return emptyList() }
    val bangumi = root.optJSONObject("bangumi")
        ?: root.optJSONObject("data")
        ?: JSONObject()
    val episodes = bangumi.optJSONArray("episodes")
        ?: root.optJSONArray("episodes")
        ?: JSONArray()

    val out = ArrayList<DownloadEpisodeCandidate>(episodes.length())
    for (i in 0 until episodes.length()) {
        val item = episodes.optJSONObject(i) ?: continue
        val episodeId = readLong(item, "episodeId", "id", "cid")
        if (episodeId <= 0L) continue
        val number = readInt(item, "episodeNumber", "number", "sort", "index")
            .takeIf { it > 0 } ?: (i + 1)
        val rawTitle = readString(item, "episodeTitle", "title", "name")
        val parsedSource = parseSource(rawTitle)
        val source = if (parsedSource == "unknown") {
            fallbackSource.ifBlank { "unknown" }
        } else {
            parsedSource
        }
        val title = stripSourceTag(rawTitle).ifBlank { "第${number}集" }
        out += DownloadEpisodeCandidate(
            episodeId = episodeId,
            episodeNumber = number,
            title = title,
            source = source
        )
    }
    val dedupByEpisode = LinkedHashMap<String, DownloadEpisodeCandidate>(out.size)
    out.forEach { episode ->
        val titleKey = normalizeEpisodeTitleForMatch(episode.title)
        val key = if (titleKey.isNotBlank()) {
            "${episode.episodeNumber}|$titleKey"
        } else {
            "id-${episode.episodeId}"
        }
        dedupByEpisode.putIfAbsent(key, episode)
    }
    return dedupByEpisode.values
        .sortedWith(compareBy<DownloadEpisodeCandidate> { it.episodeNumber }.thenBy { it.episodeId })
}

internal fun buildInitialEpisodeStates(
    animeTitle: String,
    episodes: List<DownloadEpisodeCandidate>,
    queueTasksSnapshot: List<DanmuDownloadTask>,
    recordsSnapshot: List<com.example.danmuapiapp.domain.model.DanmuDownloadRecord>
): Map<Long, EpisodeDownloadUiState> {
    if (episodes.isEmpty()) return emptyMap()
    val animeKey = normalizeAnimeTitleForMatch(animeTitle)
    val filteredQueue = queueTasksSnapshot.filter { task ->
        animeKey.isBlank() || normalizeAnimeTitleForMatch(task.animeTitle) == animeKey
    }
    val filteredRecords = recordsSnapshot.filter { record ->
        animeKey.isBlank() || normalizeAnimeTitleForMatch(record.animeTitle) == animeKey
    }
    val stateMap = LinkedHashMap<Long, EpisodeDownloadUiState>(episodes.size)
    episodes.forEach { episode ->
        val sourceKey = canonicalSourceKey(episode.source)
        val titleKey = normalizeEpisodeTitleForMatch(episode.title)
        val queueTask = filteredQueue
            .asSequence()
            .filter { task ->
                val sourceMatches = sourceKey == "unknown" || canonicalSourceKey(task.source) == sourceKey
                val numberMatches = task.episodeNo == episode.episodeNumber
                val idMatches = task.episodeId == episode.episodeId
                val titleMatches = titleKey.isNotBlank() &&
                    normalizeEpisodeTitleForMatch(task.episodeTitle) == titleKey
                sourceMatches && (numberMatches || idMatches || titleMatches)
            }
            .maxByOrNull { it.updatedAt }
        if (queueTask != null) {
            val mappedState = when (queueTask.statusEnum()) {
                DownloadQueueStatus.Pending -> EpisodeDownloadState.Queued
                DownloadQueueStatus.Running -> EpisodeDownloadState.Running
                DownloadQueueStatus.Success -> EpisodeDownloadState.Success
                DownloadQueueStatus.Failed -> EpisodeDownloadState.Failed
                DownloadQueueStatus.Skipped -> EpisodeDownloadState.Skipped
                DownloadQueueStatus.Canceled -> EpisodeDownloadState.Canceled
            }
            val progress = when (mappedState) {
                EpisodeDownloadState.Success,
                EpisodeDownloadState.Failed,
                EpisodeDownloadState.Skipped,
                EpisodeDownloadState.Canceled -> 1f
                EpisodeDownloadState.Running -> 0.15f
                EpisodeDownloadState.Queued,
                EpisodeDownloadState.Idle -> 0f
            }
            stateMap[episode.episodeId] = EpisodeDownloadUiState(
                state = mappedState,
                progress = progress,
                detail = queueTask.lastDetail
            )
            return@forEach
        }
        val record = filteredRecords
            .asSequence()
            .filter { record ->
                val sourceMatches = sourceKey == "unknown" || canonicalSourceKey(record.source) == sourceKey
                val numberMatches = record.episodeNo == episode.episodeNumber
                val idMatches = record.episodeId == episode.episodeId
                val titleMatches = titleKey.isNotBlank() &&
                    normalizeEpisodeTitleForMatch(record.episodeTitle) == titleKey
                sourceMatches && (numberMatches || idMatches || titleMatches)
            }
            .maxByOrNull { it.createdAt }
        if (record != null) {
            val mappedState = when (record.statusEnum()) {
                DownloadRecordStatus.Success -> EpisodeDownloadState.Success
                DownloadRecordStatus.Failed -> EpisodeDownloadState.Failed
                DownloadRecordStatus.Skipped -> EpisodeDownloadState.Skipped
            }
            stateMap[episode.episodeId] = EpisodeDownloadUiState(
                state = mappedState,
                progress = 1f,
                detail = record.relativePath.ifBlank { record.errorMessage ?: "" }
            )
        }
    }
    return stateMap
}

internal fun parseSource(rawTitle: String): String {
    val match = Regex("^\\s*【([^】]+)】\\s*").find(rawTitle)
    return match?.groupValues?.getOrNull(1)?.trim().orEmpty().ifBlank { "unknown" }
}

internal fun stripSourceTag(rawTitle: String): String {
    return rawTitle.replace(Regex("^\\s*【[^】]+】\\s*"), "").trim()
}

internal fun normalizeAnimeTitleForMatch(raw: String): String {
    if (raw.isBlank()) return ""
    val noFrom = raw.replace(Regex("\\s*from\\s+.*$", RegexOption.IGNORE_CASE), "")
    val noType = noFrom.replace(Regex("【[^】]*】"), "")
    val noYear = noType.replace(Regex("[（(]\\d{4}[)）]"), "")
    return noYear
        .replace(Regex("[\\s\\p{Punct}　【】（）()\\[\\]「」]"), "")
        .lowercase()
        .trim()
}

internal fun normalizeEpisodeTitleForMatch(raw: String): String {
    if (raw.isBlank()) return ""
    return stripSourceTag(raw)
        .replace(Regex("[\\s\\p{Punct}　【】（）()\\[\\]「」]"), "")
        .lowercase()
        .trim()
}

internal fun extractSourceFromAnimeTitle(raw: String): String {
    val matched = Regex("from\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE).find(raw)
    return matched?.groupValues?.getOrNull(1)
        ?.trim()
        ?.trim('【', '】', '[', ']')
        .orEmpty()
}

internal fun canonicalSourceKey(raw: String): String {
    val key = raw.trim()
        .replace(Regex("^from\\s+", RegexOption.IGNORE_CASE), "")
        .trim()
        .lowercase()
    if (key.isBlank()) return "unknown"
    return when (key) {
        "qq", "tencent" -> "tencent"
        "bilibili", "bilibili1", "bili", "b23" -> "bilibili"
        "iqiyi", "qiyi" -> "iqiyi"
        "imgo", "mango", "mgtv", "hunantv" -> "imgo"
        "douyin", "xigua" -> "xigua"
        else -> key
    }
}

internal fun extractAnimeKeywordForSearch(rawAnimeTitle: String): String {
    val noFrom = rawAnimeTitle.replace(Regex("\\s*from\\s+.*$", RegexOption.IGNORE_CASE), "")
    val noType = noFrom.replace(Regex("【[^】]*】"), "")
    val noYear = noType.replace(Regex("[（(]\\d{4}[)）]"), "")
    val keyword = noYear.trim()
    return if (keyword.isNotBlank()) keyword else noFrom.trim()
}

internal fun pickEpisodeForTask(
    task: DanmuDownloadTask,
    episodes: List<DownloadEpisodeCandidate>
): DownloadEpisodeCandidate? {
    if (episodes.isEmpty()) return null
    val taskSource = canonicalSourceKey(task.source)
    val sameSourceEpisodes = if (taskSource == "unknown") {
        episodes
    } else {
        episodes.filter { canonicalSourceKey(it.source) == taskSource }
    }
    val taskTitleKey = normalizeEpisodeTitleForMatch(task.episodeTitle)
    val pool = if (sameSourceEpisodes.isNotEmpty()) sameSourceEpisodes else episodes
    pool.firstOrNull { it.episodeNumber == task.episodeNo }?.let { return it }
    if (taskTitleKey.isNotBlank()) {
        pool.firstOrNull { normalizeEpisodeTitleForMatch(it.title) == taskTitleKey }?.let { return it }
    }
    episodes.firstOrNull { it.episodeNumber == task.episodeNo }?.let { return it }
    if (taskTitleKey.isNotBlank()) {
        episodes.firstOrNull { normalizeEpisodeTitleForMatch(it.title) == taskTitleKey }?.let { return it }
    }
    return null
}

internal fun ensureHttpPrefix(url: String): String {
    if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
        return url
    }
    return "http://$url"
}

internal fun urlEncode(raw: String): String {
    return URLEncoder.encode(raw, Charsets.UTF_8.name())
}

private fun readString(obj: JSONObject, vararg keys: String): String {
    keys.forEach { key ->
        val value = obj.optString(key, "").trim()
        if (value.isNotBlank() && !value.equals("null", true)) {
            return value
        }
    }
    return ""
}

private fun readInt(obj: JSONObject, vararg keys: String): Int {
    keys.forEach { key ->
        if (obj.has(key)) {
            val n = obj.optInt(key, Int.MIN_VALUE)
            if (n != Int.MIN_VALUE) return n
            obj.optString(key, "").trim().toIntOrNull()?.let { return it }
        }
    }
    return 0
}

private fun readLong(obj: JSONObject, vararg keys: String): Long {
    keys.forEach { key ->
        if (obj.has(key)) {
            val n = obj.optLong(key, Long.MIN_VALUE)
            if (n != Long.MIN_VALUE) return n
            obj.optString(key, "").trim().toLongOrNull()?.let { return it }
        }
    }
    return -1L
}
