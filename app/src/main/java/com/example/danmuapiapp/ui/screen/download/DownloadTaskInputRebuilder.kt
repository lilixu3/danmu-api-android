package com.example.danmuapiapp.ui.screen.download

import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import com.example.danmuapiapp.domain.model.DanmuDownloadInput
import com.example.danmuapiapp.domain.model.DanmuDownloadTask
import com.example.danmuapiapp.domain.model.DownloadConflictPolicy
import com.example.danmuapiapp.domain.model.canonicalSourceKey
import com.example.danmuapiapp.domain.model.extractSourceFromAnimeTitle
import com.example.danmuapiapp.ui.screen.download.support.downloadHttpGet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * 队列任务链路重建：旧链路失效时按剧名重新搜索并匹配剧集，得到新的下载输入。
 */
internal class DownloadTaskInputRebuilder(
    private val httpClient: OkHttpClient,
    private val apiBase: () -> String?
) {

    private fun requestGet(url: String): Result<Pair<Int, String>> {
        return downloadHttpGet(httpClient, url)
    }

    suspend fun rebuild(task: DanmuDownloadTask): Result<DanmuDownloadInput> {
        return runCatching {
            val apiBase = apiBase() ?: task.apiBaseUrl.trim().ifBlank {
                error("弹幕源地址无效")
            }
            if (task.directUrl.isNotBlank()) {
                return@runCatching DanmuDownloadInput(
                    apiBaseUrl = apiBase,
                    animeTitle = task.animeTitle,
                    episodeTitle = task.episodeTitle,
                    episodeId = task.episodeId,
                    episodeNo = task.episodeNo,
                    source = task.source,
                    format = DanmuDownloadFormat.fromValue(task.format),
                    fileNameTemplate = task.fileNameTemplate,
                    conflictPolicy = DownloadConflictPolicy.fromKey(task.conflictPolicy),
                    animeId = task.animeId,
                    mediaType = task.mediaType,
                    directUrl = task.directUrl
                )
            }
            val keyword = extractAnimeKeywordForSearch(task.animeTitle)
            if (keyword.isBlank()) {
                error("剧名为空，无法重建链路")
            }
            val searchUrl = "$apiBase/api/v2/search/anime?keyword=${urlEncode(keyword)}"
            val searchResp = withContext(Dispatchers.IO) { requestGet(searchUrl) }.getOrElse { throw it }
            val searchCode = searchResp.first
            if (searchCode !in 200..299) {
                error("搜索失败：HTTP $searchCode")
            }
            val animes = parseAnimeCandidates(searchResp.second)
            if (animes.isEmpty()) {
                error("未搜索到匹配动漫")
            }

            val taskAnimeKey = normalizeAnimeTitleForMatch(task.animeTitle)
            val taskSourceKey = canonicalSourceKey(
                task.source.ifBlank { extractSourceFromAnimeTitle(task.animeTitle) }
            )
            val exactMatches = animes.filter { candidate ->
                val titleMatches = normalizeAnimeTitleForMatch(candidate.title) == taskAnimeKey
                val sourceMatches = taskSourceKey == "unknown" ||
                    canonicalSourceKey(extractSourceFromAnimeTitle(candidate.title)) == taskSourceKey
                titleMatches && sourceMatches
            }
            val titleMatches = animes.filter { candidate ->
                normalizeAnimeTitleForMatch(candidate.title) == taskAnimeKey
            }
            val sourceMatches = animes.filter { candidate ->
                taskSourceKey != "unknown" &&
                    canonicalSourceKey(extractSourceFromAnimeTitle(candidate.title)) == taskSourceKey
            }
            val ordered = (exactMatches + titleMatches + sourceMatches + animes).distinctBy { it.animeId }

            for (candidate in ordered) {
                val bangumiUrl = "$apiBase/api/v2/bangumi/${candidate.animeId}"
                val bangumiResp = withContext(Dispatchers.IO) { requestGet(bangumiUrl) }.getOrElse { continue }
                val bangumiCode = bangumiResp.first
                if (bangumiCode !in 200..299) continue
                val episodes = parseEpisodeCandidates(
                    bangumiResp.second,
                    fallbackSource = extractSourceFromAnimeTitle(candidate.title)
                )
                val matchedEpisode = pickEpisodeForTask(task, episodes) ?: continue
                return@runCatching DanmuDownloadInput(
                    apiBaseUrl = apiBase,
                    animeTitle = candidate.title.ifBlank { task.animeTitle },
                    episodeTitle = matchedEpisode.title.ifBlank { task.episodeTitle },
                    episodeId = matchedEpisode.episodeId,
                    episodeNo = matchedEpisode.episodeNumber.takeIf { it > 0 } ?: task.episodeNo,
                    source = matchedEpisode.source.ifBlank { task.source },
                    format = DanmuDownloadFormat.fromValue(task.format),
                    fileNameTemplate = task.fileNameTemplate,
                    conflictPolicy = DownloadConflictPolicy.fromKey(task.conflictPolicy),
                    animeId = candidate.animeId,
                    mediaType = com.example.danmuapiapp.domain.model.LocalDanmuType.fromDescription(candidate.typeDescription)?.wire ?: task.mediaType
                )
            }
            error("未找到匹配剧集：第${task.episodeNo}集（${task.source.ifBlank { "unknown" }}）")
        }
    }
}
