package com.example.danmuapiapp.ui.screen.apitest

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.data.repository.DanmuPayloadInspector
import com.example.danmuapiapp.data.util.RuntimeTokenNormalizer
import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import com.example.danmuapiapp.domain.model.RequestRecord
import com.example.danmuapiapp.domain.repository.RequestRecordRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.ui.screen.download.DownloadAnimeCandidate
import com.example.danmuapiapp.ui.screen.download.DownloadEpisodeCandidate
import com.example.danmuapiapp.ui.screen.download.parseAnimeCandidates
import com.example.danmuapiapp.ui.screen.download.parseEpisodeCandidates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ApiTestViewModel @Inject constructor(
    private val runtimeRepository: RuntimeRepository,
    private val recordRepository: RequestRecordRepository,
    private val httpClient: OkHttpClient
) : ViewModel() {

    val runtimeState = runtimeRepository.runtimeState
    val logs = runtimeRepository.logs
    val endpoints = ApiTestCatalog.endpoints

    var isLoading by mutableStateOf(false)
        private set

    var debugResponse by mutableStateOf<ApiDebugResponse?>(null)
        private set

    var requestUrl by mutableStateOf("")
        private set

    var curlCommand by mutableStateOf("")
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var isAutoMatching by mutableStateOf(false)
        private set

    var autoMatchResult by mutableStateOf<DanmuInsight?>(null)
        private set

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

    var lastApiActionStartedAtMs by mutableStateOf<Long?>(null)
        private set

    var favoriteSupportState by mutableStateOf(FavoriteSupportState.Unknown)
        private set

    var favoriteItems by mutableStateOf<List<ApiTestFavoriteItem>>(emptyList())
        private set

    var scheduledFavoriteRefreshSupported by mutableStateOf(false)
        private set

    var favoriteLoadError by mutableStateOf<String?>(null)
        private set

    var favoriteOperation by mutableStateOf<FavoriteOperation?>(null)
        private set

    var favoriteOperationKeyword by mutableStateOf<String?>(null)
        private set

    var successfulManualSearchKeyword by mutableStateOf("")
        private set

    var isExportingDanmu by mutableStateOf(false)
        private set

    var preparedExport by mutableStateOf<DanmuExportPayload?>(null)
        private set

    var uiNotice by mutableStateOf<ApiTestUiNotice?>(null)
        private set

    private var manualOriginalInput: String = ""
    private var uiEventSequence = 0L

    fun dismissError() {
        errorMessage = null
    }

    fun clearDebugResponse() {
        debugResponse = null
        requestUrl = ""
        curlCommand = ""
    }

    fun refreshLogs() = runtimeRepository.refreshLogs()

    fun consumeUiNotice(id: Long) {
        if (uiNotice?.id == id) uiNotice = null
    }

    fun consumePreparedExport(id: Long) {
        if (preparedExport?.id == id) preparedExport = null
    }

    fun favoriteForKeyword(keyword: String): ApiTestFavoriteItem? {
        return findFavoriteForKeyword(favoriteItems, keyword)
    }

    fun clearAutoResult() {
        autoMatchResult = null
    }

    fun backManualStep() {
        when {
            manualResult != null -> manualResult = null
            manualCurrentAnime != null -> {
                manualCurrentAnime = null
                manualEpisodeCandidates = emptyList()
                loadingAnimeId = null
            }
        }
    }

    fun sendRequest(
        endpoint: ApiEndpointConfig,
        baseUrl: String,
        paramValues: Map<String, String>,
        rawBody: String
    ) {
        if (isLoading) return

        val built = runCatching {
            buildRequest(endpoint, resolveApiBaseUrl(baseUrl), paramValues, rawBody)
        }.getOrElse {
            errorMessage = it.message ?: "请求参数错误"
            return
        }

        requestUrl = built.url
        curlCommand = buildCurlCommand(built.method, built.url, built.body)

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            debugResponse = null

            val startedAt = System.currentTimeMillis()
            lastApiActionStartedAtMs = startedAt
            val result = executeRequest(
                Request.Builder().url(built.url).apply {
                    if (built.method == "GET") {
                        get()
                    } else {
                        val mediaType = "application/json; charset=utf-8".toMediaType()
                        val payload = (built.body ?: "{}").toRequestBody(mediaType)
                        method(built.method, payload)
                    }
                }.build()
            )
            val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)

            result.fold(
                onSuccess = { response ->
                    val code = response.code
                    val body = response.body
                    debugResponse = withContext(Dispatchers.Default) {
                        buildDebugResponse(
                            endpoint = endpoint,
                            responseCode = code,
                            responseBody = body,
                            responseDurationMs = elapsed,
                            bodySizeBytes = response.bodySizeBytes
                        )
                    }
                    recordSuccess(
                        scene = "接口调试/${endpoint.title}",
                        method = built.method,
                        url = built.url,
                        statusCode = code,
                        durationMs = elapsed,
                        body = body
                    )
                },
                onFailure = { throwable ->
                    val message = throwable.message ?: "请求失败"
                    errorMessage = message
                    recordFailure(
                        scene = "接口调试/${endpoint.title}",
                        method = built.method,
                        url = built.url,
                        durationMs = elapsed,
                        message = message
                    )
                }
            )

            isLoading = false
        }
    }

    fun runAutoMatch(baseUrl: String, fileName: String) {
        if (isAutoMatching) return

        val base = resolveApiBaseUrl(baseUrl)
        if (base == null) {
            errorMessage = "弹幕源 Base URL 无效"
            return
        }

        val trimmedFileName = fileName.trim()
        if (trimmedFileName.isBlank()) {
            errorMessage = "请输入文件名"
            return
        }

        val inputUrl = ApiTestInputResolver.extractHttpUrl(trimmedFileName)
        if (inputUrl != null) {
            runAutoUrlDanmu(base = base, inputUrl = inputUrl)
            return
        }

        val matchUrl = "$base/api/v2/match"
        val matchBody = JSONObject().put("fileName", trimmedFileName).toString()

        viewModelScope.launch {
            isAutoMatching = true
            errorMessage = null
            autoMatchResult = null
            val flowStartedAt = System.currentTimeMillis()
            lastApiActionStartedAtMs = flowStartedAt

            val matchResult = executeJsonPost(matchUrl, matchBody)
            val matchElapsed = (System.currentTimeMillis() - flowStartedAt).coerceAtLeast(0L)

            val selection = matchResult.fold(
                onSuccess = { (code, body) ->
                    recordSuccess(
                        scene = "弹幕测试/自动匹配",
                        method = "POST",
                        url = matchUrl,
                        statusCode = code,
                        durationMs = matchElapsed,
                        body = body
                    )
                    if (code !in 200..299) {
                        errorMessage = "自动匹配失败：HTTP $code"
                        null
                    } else {
                        parseMatchSelection(body)?.let { parsed ->
                            if (parsed.episodeTitle.isBlank() && parsed.animeTitle.isBlank()) {
                                parsed.copy(episodeTitle = trimmedFileName)
                            } else {
                                parsed
                            }
                        }
                    }
                },
                onFailure = { throwable ->
                    val message = throwable.message ?: "自动匹配失败"
                    errorMessage = message
                    recordFailure(
                        scene = "弹幕测试/自动匹配",
                        method = "POST",
                        url = matchUrl,
                        durationMs = matchElapsed,
                        message = message
                    )
                    null
                }
            )

            if (selection == null) {
                if (errorMessage == null) {
                    errorMessage = "自动匹配成功，但返回结果里没有可用的弹幕 ID"
                }
                isAutoMatching = false
                return@launch
            }

            val commentUrl = "$base/api/v2/comment/${selection.commentId}?format=json&duration=true"
            val commentStartedAt = System.currentTimeMillis()
            val commentResult = executeGet(commentUrl)
            val commentElapsed = (System.currentTimeMillis() - commentStartedAt).coerceAtLeast(0L)

            commentResult.fold(
                onSuccess = { (code, body) ->
                    recordSuccess(
                        scene = "弹幕测试/自动匹配弹幕",
                        method = "GET",
                        url = commentUrl,
                        statusCode = code,
                        durationMs = commentElapsed,
                        body = body
                    )
                    if (code !in 200..299) {
                        errorMessage = "获取弹幕失败：HTTP $code"
                    } else {
                        val totalElapsed = (System.currentTimeMillis() - flowStartedAt).coerceAtLeast(0L)
                        autoMatchResult = withContext(Dispatchers.Default) {
                            buildDanmuInsightOrFallback(
                                raw = body,
                                commentId = selection.commentId,
                                exportTarget = DanmuExportTarget.Episode(selection.commentId),
                                animeTitle = selection.animeTitle,
                                episodeTitle = selection.episodeTitle.ifBlank { trimmedFileName },
                                source = selection.source,
                                pathLabel = "自动匹配",
                                requestDurationMs = totalElapsed
                            )
                        }
                    }
                },
                onFailure = { throwable ->
                    val message = throwable.message ?: "获取弹幕失败"
                    errorMessage = message
                    recordFailure(
                        scene = "弹幕测试/自动匹配弹幕",
                        method = "GET",
                        url = commentUrl,
                        durationMs = commentElapsed,
                        message = message
                    )
                }
            )

            isAutoMatching = false
        }
    }

    fun searchAnime(baseUrl: String, keyword: String) {
        if (isSearchingAnime || isLoadingEpisodes) return

        val base = resolveApiBaseUrl(baseUrl)
        if (base == null) {
            errorMessage = "弹幕源 Base URL 无效"
            return
        }

        val query = keyword.trim()
        if (query.isBlank()) {
            errorMessage = "请输入搜索关键词或视频 URL"
            return
        }

        val inputUrl = ApiTestInputResolver.extractHttpUrl(query)
        if (inputUrl != null) {
            successfulManualSearchKeyword = ""
            searchManualUrlCandidate(inputUrl)
            return
        }

        val url = "$base/api/v2/search/anime?keyword=${urlEncode(query)}"

        viewModelScope.launch {
            isSearchingAnime = true
            errorMessage = null
            manualResult = null
            successfulManualSearchKeyword = ""
            val startedAt = System.currentTimeMillis()
            lastApiActionStartedAtMs = startedAt
            val result = executeGet(url)
            val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
            manualHasSearched = true
            manualOriginalInput = query

            result.fold(
                onSuccess = { (code, body) ->
                    recordSuccess(
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
                            loadFavoritesResolved(base, showLoading = false)
                        }
                    } else {
                        manualAnimeCandidates = emptyList()
                        manualCurrentAnime = null
                        manualEpisodeCandidates = emptyList()
                        errorMessage = "搜索失败：HTTP $code"
                    }
                },
                onFailure = { throwable ->
                    val message = throwable.message ?: "搜索失败"
                    errorMessage = message
                    manualAnimeCandidates = emptyList()
                    manualCurrentAnime = null
                    manualEpisodeCandidates = emptyList()
                    recordFailure(
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

    private fun searchManualUrlCandidate(inputUrl: String) {
        if (isSearchingAnime || isLoadingEpisodes) return

        viewModelScope.launch {
            isSearchingAnime = true
            errorMessage = null
            manualResult = null
            manualHasSearched = false
            successfulManualSearchKeyword = ""
            manualAnimeCandidates = emptyList()
            manualCurrentAnime = null
            manualEpisodeCandidates = emptyList()
            manualOriginalInput = inputUrl
            lastApiActionStartedAtMs = System.currentTimeMillis()

            val metadata = fetchUrlMetadata(inputUrl)
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
                errorMessage = "URL 解析结果缺少直达地址"
                return
            }
            loadManualDanmu(baseUrl = baseUrl, anime = anime, episode = episode)
            return
        }

        val base = resolveApiBaseUrl(baseUrl)
        if (base == null) {
            errorMessage = "弹幕源 Base URL 无效"
            return
        }

        val url = "$base/api/v2/bangumi/${anime.animeId}"

        viewModelScope.launch {
            isLoadingEpisodes = true
            loadingAnimeId = anime.animeId
            errorMessage = null
            manualResult = null
            val startedAt = System.currentTimeMillis()
            lastApiActionStartedAtMs = startedAt
            val result = executeGet(url)
            val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)

            result.fold(
                onSuccess = { (code, body) ->
                    recordSuccess(
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
                        errorMessage = "加载剧集失败：HTTP $code"
                    }
                },
                onFailure = { throwable ->
                    val message = throwable.message ?: "加载剧集失败"
                    errorMessage = message
                    recordFailure(
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

        val base = resolveApiBaseUrl(baseUrl)
        if (base == null) {
            errorMessage = "弹幕源 Base URL 无效"
            return
        }

        val isDirectUrlEpisode = episode.directUrl.isNotBlank()
        val sourceUrl = episode.sourceUrl.trim()
        val primaryUrl = if (isDirectUrlEpisode) {
            "$base/api/v2/comment?url=${urlEncode(episode.directUrl)}&format=json&duration=true"
        } else {
            "$base/api/v2/comment/${episode.episodeId}?format=json&duration=true"
        }

        viewModelScope.launch {
            isLoadingManualDanmu = true
            loadingEpisodeId = episode.episodeId
            errorMessage = null
            val startedAt = System.currentTimeMillis()
            lastApiActionStartedAtMs = startedAt
            val primaryResult = executeLoggedDanmuGet(
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
                val fallbackUrl = "$base/api/v2/comment?url=${urlEncode(sourceUrl)}&format=json&duration=true"
                executeLoggedDanmuGet(
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
                    errorMessage = if (usedSourceUrlFallback) {
                        "来源 URL 兜底失败：$message"
                    } else {
                        "获取弹幕失败：$message"
                    }
                }
            )

            isLoadingManualDanmu = false
            loadingEpisodeId = null
        }
    }

    private fun runAutoUrlDanmu(
        base: String,
        inputUrl: String
    ) {
        val commentUrl = "$base/api/v2/comment?url=${urlEncode(inputUrl)}&format=json&duration=true"

        viewModelScope.launch {
            isAutoMatching = true
            autoMatchResult = null
            errorMessage = null
            val startedAt = System.currentTimeMillis()
            lastApiActionStartedAtMs = startedAt
            val metadataDeferred = async(Dispatchers.IO) { fetchUrlMetadata(inputUrl) }
            val commentDeferred = async(Dispatchers.IO) { executeGet(commentUrl) }
            val result = commentDeferred.await()
            val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
            val metadata = withTimeoutOrNull(700L) { metadataDeferred.await() }
            if (metadataDeferred.isActive) metadataDeferred.cancel()

            result.fold(
                onSuccess = { (code, body) ->
                    recordSuccess(
                        scene = "弹幕测试/自动URL解析",
                        method = "GET",
                        url = commentUrl,
                        statusCode = code,
                        durationMs = elapsed,
                        body = body
                    )
                    if (code !in 200..299) {
                        errorMessage = "URL 弹幕解析失败：HTTP $code"
                    } else {
                        val insight = withContext(Dispatchers.Default) {
                            buildDanmuInsightOrFallback(
                                raw = body,
                                commentId = null,
                                exportTarget = DanmuExportTarget.VideoUrl(inputUrl),
                                animeTitle = metadata?.title?.ifBlank { null } ?: inputUrl,
                                episodeTitle = metadata?.episodeTitle?.ifBlank { null } ?: metadata?.title?.ifBlank { null } ?: inputUrl,
                                source = metadata?.platformLabel?.ifBlank { null } ?: "URL",
                                sourceUrl = inputUrl,
                                pathLabel = "自动URL解析",
                                requestDurationMs = elapsed,
                                posterUrl = metadata?.posterUrl.orEmpty(),
                                year = metadata?.year.orEmpty(),
                                resolvedEpisodeLabel = metadata?.episodeLabel.orEmpty()
                            )
                        }
                        autoMatchResult = insight
                    }
                },
                onFailure = { throwable ->
                    val message = throwable.message ?: "URL 弹幕解析失败"
                    errorMessage = message
                    recordFailure(
                        scene = "弹幕测试/自动URL解析",
                        method = "GET",
                        url = commentUrl,
                        durationMs = elapsed,
                        message = message
                    )
                }
            )

            isAutoMatching = false
        }
    }

    private suspend fun fetchUrlMetadata(inputUrl: String): UrlDanmuMetadata? {
        return UrlDanmuMetadataResolver(httpClient).resolve(inputUrl)
    }

    fun loadFavorites(baseUrl: String) {
        if (favoriteSupportState == FavoriteSupportState.Loading) return
        val base = resolveApiBaseUrl(baseUrl)
        if (base == null) {
            favoriteSupportState = FavoriteSupportState.Failed
            favoriteLoadError = "弹幕源 Base URL 无效"
            return
        }
        viewModelScope.launch {
            loadFavoritesResolved(base, showLoading = true)
        }
    }

    fun toggleManualFavorite(baseUrl: String, currentQuery: String) {
        val query = currentQuery.trim()
        if (
            query.isBlank() ||
            query != successfulManualSearchKeyword ||
            manualAnimeCandidates.isEmpty() ||
            favoriteOperation != null
        ) {
            emitNotice("请先完成一次有效的关键词搜索", isError = true)
            return
        }
        val existing = findFavoriteForKeyword(favoriteItems, query)
        val operation = if (existing == null) FavoriteOperation.Add else FavoriteOperation.Remove
        val keyword = existing?.keyword ?: query
        runFavoriteMutation(
            baseUrl = baseUrl,
            keyword = keyword,
            operation = operation,
            path = if (operation == FavoriteOperation.Add) "add" else "remove",
            body = buildFavoriteKeywordBody(keyword),
            fallback = if (operation == FavoriteOperation.Add) "收藏成功" else "已取消收藏"
        )
    }

    fun refreshFavorite(baseUrl: String, item: ApiTestFavoriteItem) {
        runFavoriteMutation(
            baseUrl = baseUrl,
            keyword = item.keyword,
            operation = FavoriteOperation.Refresh,
            path = "refresh",
            body = buildFavoriteKeywordBody(item.keyword),
            fallback = "收藏刷新成功"
        )
    }

    fun removeFavorite(baseUrl: String, item: ApiTestFavoriteItem) {
        runFavoriteMutation(
            baseUrl = baseUrl,
            keyword = item.keyword,
            operation = FavoriteOperation.Remove,
            path = "remove",
            body = buildFavoriteKeywordBody(item.keyword),
            fallback = "收藏已删除"
        )
    }

    fun updateFavoriteSchedule(
        baseUrl: String,
        item: ApiTestFavoriteItem,
        schedule: FavoriteScheduleDraft?
    ) {
        val body = runCatching { buildFavoriteScheduleBody(item.keyword, schedule) }
            .getOrElse { throwable ->
                emitNotice(throwable.message ?: "定时刷新设置无效", isError = true)
                return
            }
        runFavoriteMutation(
            baseUrl = baseUrl,
            keyword = item.keyword,
            operation = FavoriteOperation.Schedule,
            path = "schedule",
            body = body,
            fallback = if (schedule == null) "已关闭定时刷新" else "定时刷新设置成功"
        )
    }

    fun prepareDanmuExport(
        baseUrl: String,
        insight: DanmuInsight,
        format: DanmuDownloadFormat
    ) {
        if (isExportingDanmu) return
        val base = resolveApiBaseUrl(baseUrl)
        val target = insight.exportTarget
        if (base == null || target == null) {
            emitNotice("当前弹幕结果缺少可导出的请求信息", isError = true)
            return
        }
        val url = runCatching { buildDanmuExportUrl(base, target, format) }
            .getOrElse { throwable ->
                emitNotice(throwable.message ?: "无法构建导出请求", isError = true)
                return
            }

        viewModelScope.launch {
            isExportingDanmu = true
            val startedAt = System.currentTimeMillis()
            val result = executeBytesGet(url)
            val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
            result.fold(
                onSuccess = { response ->
                    val responseSnippet = response.bytes.toTextSnippet(response.contentType)
                    recordSuccess(
                        scene = "弹幕测试/导出/${format.value}",
                        method = "GET",
                        url = url,
                        statusCode = response.code,
                        durationMs = elapsed,
                        body = responseSnippet
                    )
                    if (response.code !in 200..299) {
                        emitNotice(
                            favoriteErrorMessage(responseSnippet, "导出失败：HTTP ${response.code}"),
                            isError = true
                        )
                    } else {
                        val inspection = withContext(Dispatchers.Default) {
                            DanmuPayloadInspector.inspect(
                                payload = response.bytes,
                                format = format,
                                contentType = response.contentType
                            )
                        }
                        if (!inspection.valid) {
                            emitNotice(inspection.error, isError = true)
                        } else {
                            val normalizedBytes = withContext(Dispatchers.Default) {
                                normalizeDanmuExportPayload(response.bytes, format)
                            }
                            preparedExport = DanmuExportPayload(
                                id = nextUiEventId(),
                                bytes = normalizedBytes,
                                format = format,
                                fileName = buildDanmuExportFileName(
                                    animeTitle = insight.animeTitle,
                                    episodeTitle = insight.episodeTitle,
                                    target = target,
                                    format = format
                                ),
                                contentType = response.contentType.ifBlank { format.mimeType }
                            )
                        }
                    }
                },
                onFailure = { throwable ->
                    val message = throwable.message ?: "导出请求失败"
                    emitNotice(message, isError = true)
                    recordFailure(
                        scene = "弹幕测试/导出/${format.value}",
                        method = "GET",
                        url = url,
                        durationMs = elapsed,
                        message = message
                    )
                }
            )
            isExportingDanmu = false
        }
    }

    fun onExportSaved(fileName: String) {
        emitNotice("已导出 $fileName")
    }

    fun onExportSaveFailed(message: String) {
        emitNotice(message, isError = true)
    }

    private suspend fun loadFavoritesResolved(base: String, showLoading: Boolean): Boolean {
        if (favoriteSupportState == FavoriteSupportState.Loading) return false
        if (showLoading) favoriteSupportState = FavoriteSupportState.Loading
        favoriteLoadError = null
        val url = "$base/api/v2/favorite/list"
        val startedAt = System.currentTimeMillis()
        val result = executeGet(url)
        val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
        var loaded = false
        result.fold(
            onSuccess = { (code, body) ->
                recordSuccess(
                    scene = "弹幕测试/收藏列表",
                    method = "GET",
                    url = url,
                    statusCode = code,
                    durationMs = elapsed,
                    body = body
                )
                when {
                    code == 404 -> {
                        favoriteSupportState = FavoriteSupportState.Unsupported
                        favoriteItems = emptyList()
                        scheduledFavoriteRefreshSupported = false
                    }
                    code !in 200..299 -> {
                        favoriteSupportState = FavoriteSupportState.Failed
                        favoriteLoadError = favoriteErrorMessage(body, "收藏列表加载失败：HTTP $code")
                    }
                    else -> {
                        runCatching { parseFavoriteListResponse(body) }
                            .onSuccess { parsed ->
                                favoriteItems = parsed.favorites
                                scheduledFavoriteRefreshSupported = parsed.scheduledRefreshSupported
                                favoriteSupportState = FavoriteSupportState.Supported
                                loaded = true
                            }
                            .onFailure { throwable ->
                                favoriteSupportState = FavoriteSupportState.Failed
                                favoriteLoadError = throwable.message ?: "收藏列表响应无效"
                            }
                    }
                }
            },
            onFailure = { throwable ->
                val message = throwable.message ?: "收藏列表加载失败"
                favoriteSupportState = FavoriteSupportState.Failed
                favoriteLoadError = message
                recordFailure(
                    scene = "弹幕测试/收藏列表",
                    method = "GET",
                    url = url,
                    durationMs = elapsed,
                    message = message
                )
            }
        )
        return loaded
    }

    private fun runFavoriteMutation(
        baseUrl: String,
        keyword: String,
        operation: FavoriteOperation,
        path: String,
        body: String,
        fallback: String
    ) {
        if (favoriteOperation != null) return
        val base = resolveApiBaseUrl(baseUrl)
        if (base == null) {
            emitNotice("弹幕源 Base URL 无效", isError = true)
            return
        }
        val url = "$base/api/v2/favorite/$path"
        viewModelScope.launch {
            favoriteOperation = operation
            favoriteOperationKeyword = keyword
            val startedAt = System.currentTimeMillis()
            val result = executeJsonPost(url, body)
            val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
            result.fold(
                onSuccess = { (code, responseBody) ->
                    recordSuccess(
                        scene = "弹幕测试/收藏/${operation.name.lowercase(Locale.ROOT)}",
                        method = "POST",
                        url = url,
                        statusCode = code,
                        durationMs = elapsed,
                        body = responseBody
                    )
                    if (code in 200..299) {
                        runCatching { parseFavoriteMutationMessage(responseBody, fallback) }
                            .onSuccess { message ->
                                emitNotice(message)
                                loadFavoritesResolved(base, showLoading = false)
                            }
                            .onFailure { throwable ->
                                emitNotice(throwable.message ?: fallback, isError = true)
                            }
                    } else {
                        emitNotice(
                            favoriteErrorMessage(responseBody, "$fallback：HTTP $code"),
                            isError = true
                        )
                    }
                },
                onFailure = { throwable ->
                    val message = throwable.message ?: "$fallback 失败"
                    emitNotice(message, isError = true)
                    recordFailure(
                        scene = "弹幕测试/收藏/${operation.name.lowercase(Locale.ROOT)}",
                        method = "POST",
                        url = url,
                        durationMs = elapsed,
                        message = message
                    )
                }
            )
            favoriteOperation = null
            favoriteOperationKeyword = null
        }
    }

    private fun emitNotice(message: String, isError: Boolean = false) {
        uiNotice = ApiTestUiNotice(
            id = nextUiEventId(),
            message = message,
            isError = isError
        )
    }

    private fun nextUiEventId(): Long {
        uiEventSequence += 1L
        return (System.currentTimeMillis() shl 8) xor uiEventSequence
    }

    private data class BuiltRequest(
        val method: String,
        val url: String,
        val body: String?
    )

    private data class ApiRawResponse(
        val code: Int,
        val body: String,
        val bodySizeBytes: Int
    )

    private data class ApiByteResponse(
        val code: Int,
        val bytes: ByteArray,
        val contentType: String
    )

    private fun buildDebugResponse(
        endpoint: ApiEndpointConfig,
        responseCode: Int,
        responseBody: String,
        responseDurationMs: Long,
        bodySizeBytes: Int
    ): ApiDebugResponse {
        val fullText = prettyPrintJson(responseBody)
        val preview = if (shouldCollapseDebugResponse(endpoint)) {
            buildTextPreview(raw = responseBody, limit = 4_000)
        } else {
            TextPreview(text = fullText, isTruncated = false)
        }
        return ApiDebugResponse(
            responseCode = responseCode,
            responseBody = responseBody,
            responseDurationMs = responseDurationMs,
            previewText = preview.text,
            fullText = fullText,
            previewTruncated = preview.isTruncated,
            bodySizeBytes = bodySizeBytes,
            danmuInsight = null
        )
    }

    private fun shouldCollapseDebugResponse(endpoint: ApiEndpointConfig): Boolean {
        return endpoint.key == "getComment" || endpoint.key == "getCommentByUrl" || endpoint.key == "getSegmentComment"
    }

    private fun buildDanmuInsightOrFallback(
        raw: String,
        commentId: Long?,
        exportTarget: DanmuExportTarget? = commentId?.let(DanmuExportTarget::Episode),
        animeTitle: String,
        episodeTitle: String,
        source: String,
        sourceUrl: String = "",
        pathLabel: String,
        requestDurationMs: Long?,
        posterUrl: String = "",
        year: String = "",
        resolvedEpisodeLabel: String = ""
    ): DanmuInsight {
        return parseDanmuInsight(
            raw = raw,
            commentId = commentId,
            exportTarget = exportTarget,
            animeTitle = animeTitle,
            episodeTitle = episodeTitle,
            source = source,
            sourceUrl = sourceUrl,
            pathLabel = pathLabel,
            matchedAtMillis = System.currentTimeMillis(),
            requestDurationMs = requestDurationMs,
            posterUrl = posterUrl,
            year = year,
            resolvedEpisodeLabel = resolvedEpisodeLabel
        ) ?: run {
            val preview = buildTextPreview(raw, 4_000)
            DanmuInsight(
                commentId = commentId,
                exportTarget = exportTarget,
                animeTitle = animeTitle,
                episodeTitle = episodeTitle,
                source = source,
                sourceUrl = sourceUrl,
                pathLabel = pathLabel,
                matchedAtMillis = System.currentTimeMillis(),
                totalCount = 0,
                durationSeconds = 0.0,
                maxHeatCount = 0,
                requestDurationMs = requestDurationMs,
                rawPreview = preview.text,
                rawPreviewTruncated = preview.isTruncated,
                heatBuckets = emptyList(),
                highMoments = emptyList(),
                comments = emptyList(),
                posterUrl = posterUrl,
                year = year,
                resolvedEpisodeLabel = resolvedEpisodeLabel
            )
        }
    }

    private fun buildRequest(
        endpoint: ApiEndpointConfig,
        resolvedBaseUrl: String?,
        rawParams: Map<String, String>,
        rawBody: String
    ): BuiltRequest {
        val base = resolvedBaseUrl?.trimEnd('/').orEmpty()
        require(base.isNotBlank()) { "Base URL 不能为空" }

        val method = endpoint.method.uppercase(Locale.ROOT)
        val params = rawParams
            .mapValues { it.value.trim() }
            .filterValues { it.isNotBlank() }
            .toMutableMap()

        endpoint.params.filter { it.required }.forEach { param ->
            require(!params[param.name].isNullOrBlank()) { "参数不能为空：${param.label}" }
        }

        var path = endpoint.pathTemplate
        val pathKeys = Regex(":([A-Za-z0-9_]+)").findAll(path)
            .map { it.groupValues[1] }
            .toList()

        pathKeys.forEach { key ->
            val value = params[key] ?: throw IllegalArgumentException("缺少路径参数：$key")
            path = path.replace(":$key", urlEncode(value))
            params.remove(key)
        }

        val queryMap = linkedMapOf<String, String>()
        if (method == "GET") {
            queryMap.putAll(params)
        } else {
            endpoint.forceQueryParams.forEach { key ->
                params[key]?.let { value -> queryMap[key] = value }
            }
            endpoint.forceQueryParams.forEach { key -> params.remove(key) }
        }

        val url = buildString {
            append(base)
            append(if (path.startsWith('/')) path else "/$path")
            if (queryMap.isNotEmpty()) {
                append('?')
                append(queryMap.entries.joinToString("&") { (key, value) ->
                    "${urlEncode(key)}=${urlEncode(value)}"
                })
            }
        }

        val body = if (method == "GET") {
            null
        } else if (endpoint.hasRawBody) {
            val payload = rawBody.trim()
            require(payload.isNotBlank()) { "该接口需要 JSON 请求体" }
            runCatching { JSONObject(payload) }
                .getOrElse { throw IllegalArgumentException("请求体不是有效 JSON") }
            payload
        } else {
            JSONObject(params as Map<*, *>).toString()
        }

        return BuiltRequest(
            method = method,
            url = url,
            body = body
        )
    }

    private fun buildCurlCommand(method: String, url: String, body: String?): String {
        return if (method == "GET") {
            "curl -X GET '$url'"
        } else {
            val escapedBody = (body ?: "{}").replace("'", "'\"'\"'")
            "curl -X $method '$url' -H 'Content-Type: application/json' -d '$escapedBody'"
        }
    }

    private suspend fun executeGet(url: String): Result<ApiRawResponse> {
        val request = Request.Builder().url(url).get().build()
        return executeRequest(request)
    }

    private suspend fun executeLoggedDanmuGet(
        scene: String,
        url: String
    ): Result<ApiRawResponse> {
        val startedAt = System.currentTimeMillis()
        val result = executeGet(url)
        val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
        return result.fold(
            onSuccess = { response ->
                recordSuccess(
                    scene = scene,
                    method = "GET",
                    url = url,
                    statusCode = response.code,
                    durationMs = elapsed,
                    body = response.body
                )
                if (response.code in 200..299) {
                    Result.success(response)
                } else {
                    Result.failure(IllegalStateException("HTTP ${response.code}"))
                }
            },
            onFailure = { throwable ->
                val message = throwable.message ?: "请求失败"
                recordFailure(
                    scene = scene,
                    method = "GET",
                    url = url,
                    durationMs = elapsed,
                    message = message
                )
                Result.failure(throwable)
            }
        )
    }

    private suspend fun executeJsonPost(
        url: String,
        jsonBody: String
    ): Result<ApiRawResponse> {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(mediaType))
            .build()
        return executeRequest(request)
    }

    private suspend fun executeBytesGet(url: String): Result<ApiByteResponse> {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                    val contentType = response.body.contentType()?.toString().orEmpty()
                    val bytes = response.body.bytes()
                    ApiByteResponse(
                        code = response.code,
                        bytes = bytes,
                        contentType = contentType
                    )
                }
            }
        }
    }

    private suspend fun executeRequest(request: Request): Result<ApiRawResponse> {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.newCall(request).execute().use { response ->
                    val mediaType = response.body.contentType()
                    val bytes = response.body.bytes()
                    val isBinary = mediaType?.type == "application" && mediaType.subtype == "octet-stream"
                    val body = if (isBinary) {
                        formatBinaryResponse(bytes)
                    } else {
                        bytes.toString(mediaType?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
                    }
                    ApiRawResponse(
                        code = response.code,
                        body = body,
                        bodySizeBytes = bytes.size
                    )
                }
            }
        }
    }

    private fun formatBinaryResponse(bytes: ByteArray): String {
        val preview = bytes
            .take(256)
            .joinToString(" ") { byte -> "%02X".format(byte.toInt() and 0xFF) }
        return buildString {
            append("二进制响应：${bytes.size} 字节")
            if (preview.isNotBlank()) {
                append("\nHEX（前 ${minOf(bytes.size, 256)} 字节）：\n")
                append(preview)
                if (bytes.size > 256) append("\n…")
            }
        }
    }

    private fun ByteArray.toTextSnippet(contentType: String): String {
        val normalizedType = contentType.lowercase(Locale.ROOT)
        return if (
            normalizedType.contains("json") ||
            normalizedType.contains("xml") ||
            normalizedType.startsWith("text/")
        ) {
            toString(Charsets.UTF_8).take(2_000)
        } else {
            "二进制响应：$size 字节"
        }
    }

    private fun resolveApiBaseUrl(baseUrl: String): String? {
        val raw = baseUrl.trim()
        if (raw.isBlank()) return null
        val normalized = if (
            raw.startsWith("http://", ignoreCase = true) ||
            raw.startsWith("https://", ignoreCase = true)
        ) {
            raw.trimEnd('/')
        } else {
            "http://$raw".trimEnd('/')
        }

        val token = RuntimeTokenNormalizer.normalizeInput(runtimeState.value.token).trim('/')
        if (token.isBlank()) return normalized

        val uri = runCatching { URI(normalized) }.getOrNull() ?: return normalized
        val segments = uri.path
            ?.split('/')
            ?.filter { it.isNotBlank() }
            .orEmpty()
        if (segments.firstOrNull() == token) {
            return normalized
        }
        return "$normalized/$token"
    }

    private fun recordSuccess(
        scene: String,
        method: String,
        url: String,
        statusCode: Int,
        durationMs: Long,
        body: String
    ) {
        recordRepository.addRecord(
            RequestRecord(
                scene = scene,
                method = method,
                url = url,
                statusCode = statusCode,
                durationMs = durationMs,
                success = statusCode in 200..299,
                responseSnippet = body.take(2000)
            )
        )
    }

    private fun recordFailure(
        scene: String,
        method: String,
        url: String,
        durationMs: Long,
        message: String
    ) {
        recordRepository.addRecord(
            RequestRecord(
                scene = scene,
                method = method,
                url = url,
                statusCode = null,
                durationMs = durationMs,
                success = false,
                errorMessage = message
            )
        )
    }

    private fun urlEncode(input: String): String {
        return URLEncoder.encode(input, Charsets.UTF_8.name())
    }
}
