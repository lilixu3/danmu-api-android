package com.example.danmuapiapp.ui.screen.download

import com.example.danmuapiapp.data.network.newOutboundCall
import com.example.danmuapiapp.data.repository.DownloadLocalImportPlanner
import com.example.danmuapiapp.data.repository.LocalDanmuMetadataResolver
import com.example.danmuapiapp.domain.model.DanmuDownloadRecord
import com.example.danmuapiapp.domain.model.DownloadRecordStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject

internal data class SeriesRefreshResult(val anime: DownloadAnimeCandidate,
    val episodes: List<DownloadEpisodeCandidate>, val way: String, val confirmedFresh: Boolean = false)
internal data class MissingEpisodesState(val title: String, val anime: DownloadAnimeCandidate,
    val episodes: List<DownloadEpisodeCandidate>, val downloadedCount: Int, val sourceTotal: Int,
    val way: String, val selectedIds: Set<Long>)

internal fun recordRefreshSource(record: DanmuDownloadRecord): String {
    fun usable(value: String): String = value.split('&').map(::canonicalSourceKey)
        .filterNot { it in setOf("unknown", "目录同步", "目录导入", "同步", "本地", "本地导入", "未知", "local", "sync", "dir", "扫描", "扫描导入", "") }
        .joinToString("&")
    return usable(record.source).ifBlank {
        com.example.danmuapiapp.data.repository.DownloadRecordRecovery.source(record.fileName, record.relativePath)
    }.ifBlank { usable(extractSourceFromAnimeTitle(record.animeTitle)) }
}

internal fun refreshReferenceRecord(records: List<DanmuDownloadRecord>): DanmuDownloadRecord = records
    .maxWithOrNull(compareBy<DanmuDownloadRecord> {
        (if (recordRefreshSource(it).isNotBlank()) 1000 else 0) +
            (if (it.statusEnum() == DownloadRecordStatus.Success) 300 else 0) +
            (if (it.animeId > 0) 100 else 0) +
            (if (DownloadLocalImportPlanner.metadata(it).year != null) 50 else 0)
    }.thenBy { it.createdAt }) ?: error("没有下载记录")

internal fun missingDownloadedEpisodes(
    episodes: List<DownloadEpisodeCandidate>,
    records: List<DanmuDownloadRecord>,
    availableRecordIds: Set<Long> = records.map { it.id }.toSet()
): List<DownloadEpisodeCandidate> {
    val downloaded = records.filter { it.statusEnum() == DownloadRecordStatus.Success && it.id in availableRecordIds }
    val numbers = downloaded.mapNotNull { record ->
        record.episodeNo.takeIf { it > 0 } ?: DownloadLocalImportPlanner.metadata(record).let {
            it.episode.takeIf { _ -> it.notes.none { note -> "未识别到集数" in note } }
        }
    }.toSet()
    return episodes.filter { episode ->
        if (episode.episodeNumber > 0) episode.episodeNumber !in numbers
        else downloaded.none { record ->
            (episode.episodeId > 0 && record.episodeId == episode.episodeId) ||
                (episode.title.isNotBlank() && normalizeEpisodeTitleForMatch(episode.title) == normalizeEpisodeTitleForMatch(record.episodeTitle))
        }
    }
}

internal fun seriesCandidateMatches(candidate: DownloadAnimeCandidate, record: DanmuDownloadRecord): Boolean {
    val expected = recordRefreshSource(record)
    val sources = (candidate.sourceLabel + "&" + extractSourceFromAnimeTitle(candidate.title))
        .split("&").map(::canonicalSourceKey).filterNot { it == "unknown" }.toSet()
    // A local-library result only contains what was imported, never the source's latest episodes.
    if (sources.isEmpty() || sources.all { it in setOf("local", "本地", "本地弹幕") }) return false
    val wanted = DownloadLocalImportPlanner.metadata(record)
    val got = LocalDanmuMetadataResolver.resolve(candidate.title + ".xml")
    val wantTitle = normalizeAnimeTitleForMatch(wanted.title)
    val gotTitle = normalizeAnimeTitleForMatch(got.title)
    val titleMatches = wantTitle.isNotBlank() && gotTitle.isNotBlank() &&
        (wantTitle == gotTitle || wantTitle.contains(gotTitle) || gotTitle.contains(wantTitle))
    val seasonMatches = wanted.season == null || got.season == null || wanted.season == got.season
    return titleMatches && seasonMatches &&
        (expected.isBlank() || expected.split("&").any { it in sources }) &&
        (wanted.year == null || got.year == null || wanted.year == got.year) && wanted.type == got.type
}
class SeriesRefreshService @Inject constructor(private val client: OkHttpClient) {
    internal suspend fun load(base: String, records: List<DanmuDownloadRecord>, mode: String): SeriesRefreshResult = withContext(Dispatchers.IO) {
        val record = refreshReferenceRecord(records)
        val source = recordRefreshSource(record)
        val guess = DownloadLocalImportPlanner.metadata(record)
        val title = guess.title + if (guess.type != "movie" && (guess.season ?: 1) > 1) " 第${guess.season}季" else ""
        fun read(path: String, body: String? = null): String {
            val request = Request.Builder().url("${base.trimEnd('/')}$path")
                .header("Cache-Control", "no-cache").apply {
                if (body != null) post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            }.build()
            return client.newOutboundCall(request).execute().use {
                if (!it.isSuccessful) error("HTTP ${it.code}")
                it.body.string()
            }
        }
        fun detail(candidate: DownloadAnimeCandidate, way: String, snapshot: String? = null, fresh: Boolean = false): SeriesRefreshResult {
            if (!seriesCandidateMatches(candidate, record)) error("候选结果与剧名、年份、季或原来源不符")
            val raw = snapshot ?: read("/api/v2/bangumi/${candidate.animeId}")
            val root = JSONObject(raw)
            if (root.optBoolean("success", true).not()) error(root.optString("errorMessage").ifBlank { "集列表获取失败" })
            val identity = root.optJSONObject("bangumi")
            val actualTitle = identity?.optString("animeTitle").orEmpty()
            val actualSource = identity?.optString("source").orEmpty()
            if (actualTitle.isNotBlank() && !seriesCandidateMatches(candidate.copy(
                    title = actualTitle, sourceLabel = actualSource.ifBlank { extractSourceFromAnimeTitle(actualTitle) }
                ), record)) error("详情属于其他剧集或本地弹幕，已跳过")
            val episodes = parseEpisodeCandidates(raw, candidate.sourceLabel.ifBlank { extractSourceFromAnimeTitle(candidate.title) })
            if (episodes.isEmpty()) error("集列表为空")
            val knownLastEpisode = records.filter { it.statusEnum() == DownloadRecordStatus.Success }
                .maxOfOrNull { it.episodeNo } ?: 0
            if (knownLastEpisode > (episodes.maxOfOrNull { it.episodeNumber } ?: 0)) {
                error("源端返回的集列表少于已有下载记录，可能尚未更新完整，请稍后重试")
            }
            return SeriesRefreshResult(candidate, episodes, way, fresh)
        }
        fun search(preferred: DownloadAnimeCandidate? = null): SeriesRefreshResult {
            val nextEpisode = (records.maxOfOrNull { it.episodeNo } ?: 0).coerceAtLeast(0) + 1
            val query = buildString {
                append("/api/v2/search/anime?keyword=${URLEncoder.encode(title, "UTF-8")}&app_refresh=1")
                if (guess.type != "movie") append("&season=${guess.season ?: 1}&episode=$nextEpisode")
            }
            val raw = read(query)
            val root = JSONObject(raw)
            val fresh = root.optBoolean("appRefreshFresh", false)
            val candidates = parseAnimeCandidates(raw)
                .filter { seriesCandidateMatches(it, record) }
                .sortedByDescending { candidate ->
                    val hit = LocalDanmuMetadataResolver.resolve(candidate.title + ".xml")
                    (if (normalizeAnimeTitleForMatch(hit.title) == normalizeAnimeTitleForMatch(guess.title)) 500 else 200) +
                        (if (guess.year != null && guess.year == hit.year) 300 else 0) +
                        (if (guess.season != null && guess.season == hit.season) 150 else 0) +
                        (if (candidate.animeId == preferred?.animeId) 100 else 0)
                }
            if (candidates.isEmpty()) error("没找到同剧、同来源的远端结果；请检查来源网络，或用搜索页确认")
            val snapshots = root.optJSONArray("appBangumis")
            var failure: Throwable? = null
            for (candidate in candidates) {
                try {
                    val snapshot = (0 until (snapshots?.length() ?: 0)).firstNotNullOfOrNull { i ->
                        snapshots?.optJSONObject(i)?.takeIf {
                            it.optLong("animeId") == candidate.animeId &&
                                canonicalSourceKey(it.optString("source")) == canonicalSourceKey(candidate.sourceLabel)
                        }?.optJSONObject("detail")?.toString()
                    }
                    return detail(candidate, "纯剧名搜索", snapshot, fresh && snapshot != null)
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { failure = e }
            }
            throw failure ?: IllegalStateException("没有可用的集列表")
        }
        fun match(): SeriesRefreshResult {
            val fileName = buildString {
                append(title)
                guess.year?.let { append(".$it") }
                if (guess.type != "movie") append(".S${(guess.season ?: 1).toString().padStart(2, '0')}E${record.episodeNo.coerceAtLeast(1).toString().padStart(2, '0')}")
                append(".1080p")
                val platform = when (canonicalSourceKey(source.substringBefore('&'))) {
                    "tencent" -> "qq"
                    "iqiyi" -> "qiyi"
                    "bilibili" -> "bilibili1"
                    else -> source.substringBefore('&')
                }
                if (platform in setOf("qiyi", "bilibili1", "imgo", "youku", "qq", "migu", "renren", "hanjutv", "sohu", "leshi", "xigua", "maiduidui", "aiyifan", "hongguo", "dandan", "bahamut", "animeko", "custom")) append(" @$platform")
            }
            val raw = JSONObject(read("/api/v2/match", JSONObject().put("fileName", fileName).toString()))
            val matches = raw.optJSONArray("matches") ?: error("自动匹配没有结果")
            for (i in 0 until matches.length()) {
                val hit = matches.optJSONObject(i) ?: continue
                val candidate = DownloadAnimeCandidate(hit.optLong("animeId"), hit.optString("animeTitle"), 0,
                    imageUrl = hit.optString("imageUrl"), sourceLabel = hit.optString("source"))
                if (candidate.animeId > 0 && seriesCandidateMatches(candidate, record)) return detail(candidate, "自动匹配（match）")
            }
            error("自动匹配结果与剧名 / 年份 / 原来源不符")
        }
        val failures = mutableListOf<String>()
        var matched: SeriesRefreshResult? = null
        if (mode != "search") {
            try { matched = match() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { failures += e.message.orEmpty() }
        }
        // Even a successful match may point at a cached snapshot. Refresh search before deciding no new episodes exist.
        try { return@withContext search(matched?.anime) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { failures += e.message.orEmpty() }
        matched?.let { return@withContext it }
        if (mode == "search") {
            try { return@withContext match() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { failures += e.message.orEmpty() }
        }
        if (record.animeId > 0 && source.isNotBlank()) {
            try { return@withContext detail(DownloadAnimeCandidate(record.animeId, record.animeTitle, 0, sourceLabel = source), "记录里的 animeId（缓存）") }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { failures += e.message.orEmpty() }
        }
        error("刷新失败：${failures.distinct().joinToString("；")}")
    }
}
