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
    val episodes: List<DownloadEpisodeCandidate>, val way: String)
internal data class MissingEpisodesState(val title: String, val anime: DownloadAnimeCandidate,
    val episodes: List<DownloadEpisodeCandidate>, val downloadedCount: Int, val sourceTotal: Int,
    val way: String, val selectedIds: Set<Long>)

internal fun missingDownloadedEpisodes(episodes: List<DownloadEpisodeCandidate>, records: List<DanmuDownloadRecord>): List<DownloadEpisodeCandidate> {
    val downloaded = records.filter { it.statusEnum() == DownloadRecordStatus.Success }.map { it.episodeNo }.toSet()
    return episodes.filter { it.episodeNumber !in downloaded }
}
internal fun seriesCandidateMatches(candidate: DownloadAnimeCandidate, record: DanmuDownloadRecord): Boolean {
    val expected = canonicalSourceKey(record.source.ifBlank { extractSourceFromAnimeTitle(record.animeTitle) })
    val unknown = expected in setOf("unknown", "目录同步", "同步", "本地", "本地导入", "未知", "local", "sync", "dir", "扫描", "扫描导入", "")
    val sources = (candidate.sourceLabel + "&" + extractSourceFromAnimeTitle(candidate.title)).split("&").map(::canonicalSourceKey).toSet()
    val year = DownloadLocalImportPlanner.metadata(record).year
    val hitYear = Regex("[（(]((?:19|20)\\d{2})[）)]").find(candidate.title)?.groupValues?.get(1)?.toIntOrNull()
    val wanted = LocalDanmuMetadataResolver.resolve(record.animeTitle + ".xml")
    val got = LocalDanmuMetadataResolver.resolve(candidate.title + ".xml")
    val wantTitle = normalizeAnimeTitleForMatch(wanted.title)
    val gotTitle = normalizeAnimeTitleForMatch(got.title)
    val titleMatches = wantTitle.isNotBlank() && gotTitle.isNotBlank() &&
        (wantTitle == gotTitle || wantTitle.contains(gotTitle) || gotTitle.contains(wantTitle))
    val seasonMatches = wanted.season == null || got.season == null || wanted.season == got.season
    return titleMatches && seasonMatches &&
        (unknown || expected.split("&").map(::canonicalSourceKey).any { it in sources }) && (year == null || hitYear == null || year == hitYear)
}
class SeriesRefreshService @Inject constructor(private val client: OkHttpClient) {
    internal suspend fun load(base: String, records: List<DanmuDownloadRecord>, mode: String): SeriesRefreshResult = withContext(Dispatchers.IO) {
        val record = records.firstOrNull() ?: error("没有下载记录")
        val title = extractAnimeKeywordForSearch(record.animeTitle)
        val source = record.source.ifBlank { extractSourceFromAnimeTitle(record.animeTitle) }
            .takeUnless { it in setOf("目录同步", "同步", "unknown", "local") }.orEmpty()
        val guess = DownloadLocalImportPlanner.metadata(record)
        fun read(path: String, body: String? = null): String {
            val request = Request.Builder().url("$base$path").apply {
                if (body != null) post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            }.build()
            return client.newOutboundCall(request).execute().use {
                if (!it.isSuccessful) error("HTTP ${it.code}")
                it.body.string()
            }
        }
        fun detail(candidate: DownloadAnimeCandidate, way: String): SeriesRefreshResult {
            val raw = read("/api/v2/bangumi/${candidate.animeId}")
            val episodes = parseEpisodeCandidates(raw, source.ifBlank { extractSourceFromAnimeTitle(candidate.title) })
            if (episodes.isEmpty()) error("集列表为空")
            return SeriesRefreshResult(candidate, episodes, way)
        }
        fun search(): SeriesRefreshResult {
            val candidates = parseAnimeCandidates(read("/api/v2/search/anime?keyword=${URLEncoder.encode(title, "UTF-8")}"))
                .filter { seriesCandidateMatches(it, record) }
                .sortedByDescending { candidate ->
                    val hit = LocalDanmuMetadataResolver.resolve(candidate.title + ".xml")
                    val expectedTitle = LocalDanmuMetadataResolver.resolve(record.animeTitle + ".xml").title
                    (if (hit.title == expectedTitle) 500 else 200) +
                        (if (guess.year != null && guess.year == hit.year) 300 else 0) +
                        (if (guess.season != null && guess.season == hit.season) 150 else 0) +
                        (if (guess.type == hit.type) 50 else 0)
                }
            if (candidates.isEmpty()) error("没找到同剧、同来源的结果；为避免下错版本，不切换来源")
            var failure: Throwable? = null
            for (candidate in candidates) {
                try { return detail(candidate, "纯剧名搜索") } catch (e: Exception) { failure = e }
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
        val attempts = if (mode == "search") listOf(::search, ::match) else listOf(::match, ::search)
        val failures = mutableListOf<String>()
        for (attempt in attempts) {
            try { return@withContext attempt() } catch (e: Exception) { failures += e.message.orEmpty() }
        }
        if (record.animeId > 0) {
            try { return@withContext detail(DownloadAnimeCandidate(record.animeId, record.animeTitle, 0, sourceLabel = source), "记录里的 animeId") }
            catch (e: Exception) { failures += e.message.orEmpty() }
        }
        error("刷新失败：${failures.distinct().joinToString("；")}")
    }
}
