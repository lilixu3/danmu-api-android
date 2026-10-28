package com.example.danmuapiapp.ui.screen.apitest

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.danmuapiapp.ui.screen.download.DownloadAnimeCandidate
import com.example.danmuapiapp.ui.screen.download.DownloadEpisodeCandidate
import com.example.danmuapiapp.ui.screen.download.parseAnimeCandidates
import com.example.danmuapiapp.ui.screen.download.parseEpisodeCandidates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * 弹幕测试页「手动搜索」子控制器：关键词/URL 搜索、剧集加载与手动取弹幕。
 *
 * 对外由 [ApiTestViewModel] 转发，行为与拆分前一致。
 */
internal class ApiTestManualSearchController(
    private val scope: CoroutineScope,
    private val executor: ApiTestRequestExecutor,
    private val httpClient: OkHttpClient,
    private val setError: (String?) -> Unit,
    private val markApiAction: (Long) -> Unit,
    private val reloadFavorites: suspend (String, Boolean) -> Boolean
) {
    var isSearchingAnime by mutableStateOf(false)
        private set

    var isLoadingEpisodes by mutableStateOf(false)
        private set

    var isLoadingManualDanmu by mutableStateOf(false)
        private set

    var loadingEpisodeId by mutableStateOf<Long?>(null)
        private set

    var manualHasSearched by mutableStateOf(false)
        private set

    var manualAnimeCandidates by mutableStateOf<List<DownloadAnimeCandidate>>(emptyList())
        private set

    var manualCurrentAnime by mutableStateOf<DownloadAnimeCandidate?>(null)
        private set

    var manualEpisodeCandidates by mutableStateOf<List<DownloadEpisodeCandidate>>(emptyList())
        private set

    var manualResult by mutableStateOf<DanmuInsight?>(null)
        private set

    var loadingAnimeId by mutableStateOf<Long?>(null)
        private set

    var successfulManualSearchKeyword by mutableStateOf("")
        private set

    private var manualOriginalInput: String = ""
    private var manualSelectedEpisode: DownloadEpisodeCandidate? = null
    private var episodeLoadJob: Job? = null
    private var manualDanmuJob: Job? = null

    fun searchAnime(baseUrl: String, keyword: String) {
        if (isSearchingAnime || isLoadingEpisodes) return

        val base = executor.resolveApiBaseUrl(baseUrl)
        if (base == null) {
            setError("弹幕源 Base URL 无效")
            return
        }

        val query = keyword.trim()
        if (query.isBlank()) {
            setError("请输入搜索关键词或视频 URL")
            return
        }

        val inputUrl = ApiTestInputResolver.extractHttpUrl(query)
        if (inputUrl != null) {
            successfulManualSearchKeyword = ""
            searchManualUrlCandidate(base, inputUrl)
            return
        }

        val url = "$base/api/v2/search/anime?keyword=${executor.urlEncode(query)}"

        scope.launch {
            isSearchingAnime = true
            setError(null)
            manualResult = null
            manualAnimeCandidates = emptyList()
            manualCurrentAnime = null
            manualEpisodeCandidates = emptyList()
            successfulManualSearchKeyword = ""
            val startedAt = System.currentTimeMillis()
            markApiAction(startedAt)
            val result = executor.executeGet(url)
            val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
            manualHasSearched = true
            manualOriginalInput = query

            result.fold(
                onSuccess = { (code, body) ->
                    executor.recordSuccess(
                        scene = "弹幕测试/手动搜索动漫",
                        method = "GET",
                        url = url,
                        statusCode = code,
                        durationMs = elapsed,
                        body = body
                    )
                    if (code in 200..299) {
                        manualAnimeCandidates = withContext(Dispatchers.Default) {
                            parseAnimeCandidates(body)
                        }
                        successfulManualSearchKeyword = query.takeIf {
                            manualAnimeCandidates.isNotEmpty()
                        }.orEmpty()
                        manualCurrentAnime = null
                        manualEpisodeCandidates = emptyList()
                        if (manualAnimeCandidates.isNotEmpty()) {
                            reloadFavorites(base, false)
                        }
                    } else {
                        manualAnimeCandidates = emptyList()
                        manualCurrentAnime = null
                        manualEpisodeCandidates = emptyList()
                        setError("搜索失败：HTTP $code")
                    }
                },
                onFailure = { throwable ->
                    val message = throwable.message ?: "搜索失败"
                    setError(message)
                    manualAnimeCandidates = emptyList()
                    manualCurrentAnime = null
                    manualEpisodeCandidates = emptyList()
                    executor.recordFailure(
                        scene = "弹幕测试/手动搜索动漫",
                        method = "GET",
                        url = url,
                        durationMs = elapsed,
                        message = message
                    )
                }
            )

            isSearchingAnime = false
        }
    }

    private fun searchManualUrlCandidate(base: String, inputUrl: String) {
        if (isSearchingAnime || isLoadingEpisodes) return

        scope.launch {
            isSearchingAnime = true
            setError(null)
            manualResult = null
            manualHasSearched = false
            successfulManualSearchKeyword = ""
            manualAnimeCandidates = emptyList()
            manualCurrentAnime = null
            manualEpisodeCandidates = emptyList()
            manualOriginalInput = inputUrl
            markApiAction(System.currentTimeMillis())

            val metadata = fetchUrlMetadata(inputUrl, base)
            val selection = withContext(Dispatchers.Default) {
                buildManualUrlDanmuSelection(
                    inputUrl = inputUrl,
                    metadata = metadata
                )
            }
            manualAnimeCandidates = listOf(selection.anime)
            manualCurrentAnime = null
            manualEpisodeCandidates = emptyList()
            manualHasSearched = true
            isSearchingAnime = false
        }
    }

    fun openManualAnimeDetail(baseUrl: String, anime: DownloadAnimeCandidate) {
        if (isSearchingAnime || isLoadingEpisodes) return

        if (anime.directUrl.isNotBlank()) {
            val episode = buildManualUrlEpisodeCandidate(anime)
            if (episode == null) {
                setError("URL 解析结果缺少直达地址")
                return
            }
            setError(null)
            manualResult = null
            manualCurrentAnime = anime
            manualEpisodeCandidates = anime.directEpisodes.ifEmpty { listOf(episode) }
            return
        }

        val base = executor.resolveApiBaseUrl(baseUrl)
        if (base == null) {
            setError("弹幕源 Base URL 无效")
            return
        }

        val url = "$base/api/v2/bangumi/${anime.animeId}"

        manualCurrentAnime = anime
        manualEpisodeCandidates = emptyList()

        episodeLoadJob = scope.launch {
            isLoadingEpisodes = true
            loadingAnimeId = anime.animeId
            setError(null)
            manualResult = null
            val startedAt = System.currentTimeMillis()
            markApiAction(startedAt)
            val result = executor.executeGet(url)
            val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)

            result.fold(
                onSuccess = { (code, body) ->
                    executor.recordSuccess(
                        scene = "弹幕测试/加载剧集",
                        method = "GET",
                        url = url,
                        statusCode = code,
                        durationMs = elapsed,
                        body = body
                    )
                    if (code in 200..299) {
                        val episodes = withContext(Dispatchers.Default) {
                            parseEpisodeCandidates(body)
                        }
                        manualCurrentAnime = anime
                        manualEpisodeCandidates = episodes
                    } else {
                        setError("加载剧集失败：HTTP $code")
                    }
                },
                onFailure = { throwable ->
                    val message = throwable.message ?: "加载剧集失败"
                    setError(message)
                    executor.recordFailure(
                        scene = "弹幕测试/加载剧集",
                        method = "GET",
                        url = url,
                        durationMs = elapsed,
                        message = message
                    )
                }
            )

            isLoadingEpisodes = false
            loadingAnimeId = null
        }
    }

    fun loadManualDanmu(
        baseUrl: String,
        anime: DownloadAnimeCandidate,
        episode: DownloadEpisodeCandidate
    ) {
        if (isLoadingManualDanmu) return

        if (manualSelectedEpisode != episode) manualResult = null
        manualSelectedEpisode = episode

        val base = executor.resolveApiBaseUrl(baseUrl)
        if (base == null) {
            setError("弹幕源 Base URL 无效")
            return
        }

        val isDirectUrlEpisode = episode.directUrl.isNotBlank()
        val sourceUrl = episode.sourceUrl.trim()
        val primaryUrl = if (isDirectUrlEpisode) {
            "$base/api/v2/comment?url=${executor.urlEncode(episode.directUrl)}&format=json&duration=true"
        } else {
            "$base/api/v2/comment/${episode.episodeId}?format=json&duration=true"
        }

        manualDanmuJob = scope.launch {
            isLoadingManualDanmu = true
            loadingEpisodeId = episode.episodeId
            setError(null)
            val startedAt = System.currentTimeMillis()
            markApiAction(startedAt)
            val primaryResult = executor.executeLoggedDanmuGet(
                scene = if (isDirectUrlEpisode) {
                    "弹幕测试/手动URL解析"
                } else {
                    "弹幕测试/手动获取弹幕"
                },
                url = primaryUrl
            )
            val usedSourceUrlFallback = !isDirectUrlEpisode &&
                sourceUrl.isNotBlank() &&
                primaryResult.isFailure
            val result = if (usedSourceUrlFallback) {
                val fallbackUrl = "$base/api/v2/comment?url=${executor.urlEncode(sourceUrl)}&format=json&duration=true"
                executor.executeLoggedDanmuGet(
                    scene = "弹幕测试/来源URL兜底",
                    url = fallbackUrl
                )
            } else {
                primaryResult
            }
            val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)

            result.fold(
                onSuccess = { response ->
                    manualResult = withContext(Dispatchers.Default) {
                        buildDanmuInsightOrFallback(
                            raw = response.body,
                            commentId = if (isDirectUrlEpisode || usedSourceUrlFallback) {
                                null
                            } else {
                                episode.episodeId
                            },
                            exportTarget = when {
                                isDirectUrlEpisode -> DanmuExportTarget.VideoUrl(episode.directUrl)
                                usedSourceUrlFallback -> DanmuExportTarget.VideoUrl(sourceUrl)
                                else -> DanmuExportTarget.Episode(episode.episodeId)
                            },
                            animeTitle = anime.title,
                            episodeTitle = episode.title,
                            source = episode.source,
                            sourceUrl = if (isDirectUrlEpisode) episode.directUrl else sourceUrl,
                            pathLabel = when {
                                isDirectUrlEpisode -> "手动URL解析"
                                usedSourceUrlFallback -> "手动匹配 · URL 兜底"
                                else -> "手动匹配"
                            },
                            requestDurationMs = elapsed,
                            posterUrl = episode.posterUrl.ifBlank { anime.imageUrl },
                            year = episode.year.ifBlank { anime.year },
                            resolvedEpisodeLabel = episode.resolvedEpisodeLabel.ifBlank { anime.episodeLabel }
                        )
                    }
                },
                onFailure = { throwable ->
                    val message = throwable.message ?: "获取弹幕失败"
                    setError(
                        if (usedSourceUrlFallback) {
                            "来源 URL 兜底失败：$message"
                        } else {
                            "获取弹幕失败：$message"
                        }
                    )
                }
            )

            isLoadingManualDanmu = false
            loadingEpisodeId = null
        }
    }

    fun clearManualDanmu() {
        manualDanmuJob?.cancel()
        manualDanmuJob = null
        isLoadingManualDanmu = false
        loadingEpisodeId = null
        manualResult = null
    }

    fun reloadManualDanmu(baseUrl: String) {
        val anime = manualCurrentAnime ?: return
        val episode = manualSelectedEpisode ?: return
        loadManualDanmu(baseUrl, anime, episode)
    }

    fun backManualStep() {
        when {
            manualResult != null -> manualResult = null
            manualCurrentAnime != null -> {
                episodeLoadJob?.cancel()
                episodeLoadJob = null
                isLoadingEpisodes = false
                manualCurrentAnime = null
                manualEpisodeCandidates = emptyList()
                loadingAnimeId = null
            }
        }
    }

    private suspend fun fetchUrlMetadata(inputUrl: String, base: String? = null): UrlDanmuMetadata? {
        return UrlDanmuMetadataResolver(httpClient).resolve(inputUrl, base)
    }
}
