package com.example.danmuapiapp.ui.screen.download

import com.example.danmuapiapp.domain.model.DanmuDownloadTask
import com.example.danmuapiapp.domain.model.DownloadQueueStatus
import com.example.danmuapiapp.domain.model.DownloadRecordStatus
import com.example.danmuapiapp.domain.model.canonicalSourceKey
import com.example.danmuapiapp.domain.model.extractSourceFromAnimeTitle
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
            imageUrl = imageUrl,
            sourceLabel = item.optString("source").ifBlank { extractSourceFromAnimeTitle(title) },
            typeDescription = item.optString("typeDescription").ifBlank { Regex("【([^】]+)】").find(title)?.groupValues?.get(1).orEmpty() }
        )
    }
    return out.distinctBy { it.animeId }
}

// Same precedence as the core's numeric /search/episodes filter.
private val titleNumberPatterns = listOf(
    Regex("""第(\d+)[集话回]"""),
    Regex("""[Ee][Pp]?(\d+)"""),
    Regex("""(?:^|\s)(\d+)(?:\s|$)""")
)

private fun titleEpisodeNumber(title: String): Int? {
    for (pattern in titleNumberPatterns) {
        val number = pattern.find(title)?.groupValues?.get(1) ?: continue
        return number.toIntOrNull()?.takeIf { it > 0 }
    }
    return null
}

private fun <K> readNumberedEpisodes(
    raw: String,
    distinctKey: (DownloadEpisodeCandidate) -> K
): List<DownloadEpisodeCandidate> {
    val root = runCatching { JSONObject(raw) }.getOrElse { return emptyList() }
    val bangumi = root.optJSONObject("bangumi") ?: root.optJSONObject("data")
    val episodes = bangumi?.optJSONArray("episodes") ?: root.optJSONArray("episodes") ?: return emptyList()
    val rows = buildList {
        for (index in 0 until episodes.length()) {
            val item = episodes.optJSONObject(index) ?: continue
            val id = readLong(item, "episodeId", "id", "cid")
            if (id <= 0) continue
            val number = readInt(item, "episodeNumber", "number", "sort", "index").takeIf { it > 0 } ?: (index + 1)
            val title = readString(item, "episodeTitle", "title", "name")
            add(DownloadEpisodeCandidate(id, number, title, readString(item, "source"),
                sourceUrl = readString(item, "url", "sourceUrl")) to titleEpisodeNumber(title))
        }
    }
    val counts = rows.mapNotNull { it.second }.groupingBy { it }.eachCount()
    val numbered = rows.map { (episode, explicit) ->
        val unique = explicit?.takeIf { counts[it] == 1 }
        episode.copy(episodeNumber = unique ?: episode.episodeNumber) to (unique != null)
    }
    // A fallback sequence number can coincide with a real title number. Jump to the real one first.
    return numbered.distinctBy { distinctKey(it.first) }.sortedWith(compareBy<Pair<DownloadEpisodeCandidate, Boolean>> { it.first.episodeNumber }
        .thenByDescending { it.second }.thenBy { it.first.episodeId }).map { it.first }
}

internal fun parsePushEpisodeCandidates(raw: String): List<DownloadEpisodeCandidate> =
    readNumberedEpisodes(raw) { it.episodeId }.map {
        it.copy(title = it.title.ifBlank { "第${it.episodeNumber}集" }, source = it.source.ifBlank { "unknown" })
    }

internal fun parseEpisodeCandidates(raw: String, fallbackSource: String = ""): List<DownloadEpisodeCandidate> =
    readNumberedEpisodes(raw) { episode ->
        val title = stripSourceTag(episode.title).ifBlank { "第${episode.episodeNumber}集" }
        val titleKey = normalizeEpisodeTitleForMatch(title)
        if (titleKey.isBlank()) "id-${episode.episodeId}" else "${episode.episodeNumber}|$titleKey"
    }.map { episode ->
        val tag = parseSource(episode.title)
        episode.copy(
            title = stripSourceTag(episode.title).ifBlank { "第${episode.episodeNumber}集" },
            source = if (tag != "unknown") tag else episode.source.ifBlank { fallbackSource.ifBlank { "unknown" } }
        )
    }

// Old queue/history entries can contain a filtered list position alongside the actual title.
internal class SavedEpisodeLookup(episodes: List<DownloadEpisodeCandidate>) {
    private val byNumber = episodes.groupBy { it.episodeNumber }
    private val byTitle = episodes.groupBy { normalizeEpisodeTitleForMatch(it.title) }
    private val byTitleNumber = episodes.groupBy { titleEpisodeNumber(it.title) }

    private fun uniqueTitleMatch(title: String) =
        titleEpisodeNumber(title)?.let { byTitleNumber[it]?.singleOrNull() }

    fun numberFor(title: String, storedNumber: Int): Int = uniqueTitleMatch(title)?.episodeNumber ?: storedNumber

    fun find(title: String, storedNumber: Int, id: Long): DownloadEpisodeCandidate? {
        uniqueTitleMatch(title)?.let { return it }
        val titleKey = normalizeEpisodeTitleForMatch(title)
        val exactTitles = if (titleKey.isBlank()) emptyList() else byTitle[titleKey].orEmpty()
        val candidates = if (storedNumber > 0) byNumber[storedNumber].orEmpty() else emptyList()
        return candidates.firstOrNull { it in exactTitles } ?: candidates.firstOrNull { it.episodeId == id }
            ?: candidates.firstOrNull() ?: exactTitles.singleOrNull()
    }
}

internal fun buildInitialEpisodeStates(
    animeTitle: String,
    episodes: List<DownloadEpisodeCandidate>,
    queueTasksSnapshot: List<DanmuDownloadTask>,
    recordsSnapshot: List<com.example.danmuapiapp.domain.model.DanmuDownloadRecord>,
    animeId: Long = 0L
): Map<Long, EpisodeDownloadUiState> {
    if (episodes.isEmpty()) return emptyMap()
    val animeKey = normalizeAnimeTitleForMatch(animeTitle)
    val filteredQueue = queueTasksSnapshot.filter { task ->
        animeKey.isBlank() || animeIdentityMatches(task.animeTitle, task.animeId, animeTitle, animeId)
    }
    val filteredRecords = recordsSnapshot.filter { record ->
        animeKey.isBlank() || animeIdentityMatches(record.animeTitle, record.animeId, animeTitle, animeId)
    }
    val lookup = SavedEpisodeLookup(episodes)
    val recordEpisodes = filteredRecords.associateWith { lookup.find(it.episodeTitle, it.episodeNo, it.episodeId) }
    val taskEpisodes = filteredQueue.associateWith { lookup.find(it.episodeTitle, it.episodeNo, it.episodeId) }
    val stateMap = LinkedHashMap<Long, EpisodeDownloadUiState>(episodes.size)
    episodes.forEach { episode ->
        val sourceKey = canonicalSourceKey(episode.source)
        val titleKey = normalizeEpisodeTitleForMatch(episode.title)
        val matchingRecords = filteredRecords
            .asSequence()
            .filter { record ->
                val sourceMatches = historySourceMatches(episode.source, record.source)
                val numberMatches = record.episodeNo > 0 && recordEpisodes[record] == episode
                val idMatches = record.episodeId > 0L && record.episodeId == episode.episodeId
                val titleMatches = titleKey.isNotBlank() &&
                    normalizeEpisodeTitleForMatch(record.episodeTitle) == titleKey
                sourceMatches && (numberMatches || idMatches || titleMatches)
            }
            .sortedByDescending { it.createdAt }
            .toList()
        val successfulRecords = matchingRecords.filter {
            it.statusEnum() == DownloadRecordStatus.Success
        }
        val downloadedBefore = successfulRecords.isNotEmpty()
        val lastDownloadedAt = successfulRecords.maxOfOrNull { it.createdAt }
        val queueTask = filteredQueue
            .asSequence()
            .filter { task ->
                val sourceMatches = sourceKey == "unknown" || canonicalSourceKey(task.source) == sourceKey
                val numberMatches = taskEpisodes[task] == episode
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
                detail = queueTask.lastDetail,
                downloadedBefore = downloadedBefore,
                downloadedRecordCount = successfulRecords.size,
                lastDownloadedAt = lastDownloadedAt
            )
            return@forEach
        }
        val record = matchingRecords.firstOrNull()
        if (record != null) {
            val mappedState = when (record.statusEnum()) {
                DownloadRecordStatus.Success -> EpisodeDownloadState.Success
                DownloadRecordStatus.Failed -> EpisodeDownloadState.Failed
                DownloadRecordStatus.Skipped -> EpisodeDownloadState.Skipped
            }
            stateMap[episode.episodeId] = EpisodeDownloadUiState(
                state = mappedState,
                progress = 1f,
                detail = record.relativePath.ifBlank { record.errorMessage ?: "" },
                downloadedBefore = downloadedBefore,
                downloadedRecordCount = successfulRecords.size,
                lastDownloadedAt = lastDownloadedAt
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
    val noType = noFrom.replace(Regex("【[^】]*】|\\[[^\\]]*]"), "")
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

internal fun extractAnimeKeywordForSearch(rawAnimeTitle: String): String {
    val noFrom = rawAnimeTitle.replace(Regex("\\s*from\\s+.*$", RegexOption.IGNORE_CASE), "")
    val noType = noFrom.replace(Regex("【[^】]*】|\\[[^\\]]*]"), "")
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
    val pool = if (sameSourceEpisodes.isNotEmpty()) sameSourceEpisodes else episodes
    fun findIn(candidates: List<DownloadEpisodeCandidate>): DownloadEpisodeCandidate? {
        return SavedEpisodeLookup(candidates).find(task.episodeTitle, task.episodeNo, task.episodeId)
    }
    return findIn(pool) ?: if (pool !== episodes) findIn(episodes) else null
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
