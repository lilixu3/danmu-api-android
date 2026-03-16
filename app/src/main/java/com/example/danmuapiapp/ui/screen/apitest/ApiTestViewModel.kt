package com.example.danmuapiapp.ui.screen.apitest

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.domain.model.RequestRecord
import com.example.danmuapiapp.domain.repository.RequestRecordRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.ui.screen.download.DownloadAnimeCandidate
import com.example.danmuapiapp.ui.screen.download.DownloadEpisodeCandidate
import com.example.danmuapiapp.ui.screen.download.parseAnimeCandidates
import com.example.danmuapiapp.ui.screen.download.parseEpisodeCandidates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ApiTestViewModel @Inject constructor(
    runtimeRepository: RuntimeRepository,
    private val recordRepository: RequestRecordRepository,
    private val httpClient: OkHttpClient
) : ViewModel() {

    val runtimeState = runtimeRepository.runtimeState
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

    fun dismissError() {
        errorMessage = null
    }

    fun clearDebugResponse() {
        debugResponse = null
        requestUrl = ""
        curlCommand = ""
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
            buildRequest(endpoint, baseUrl, paramValues, rawBody)
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
                onSuccess = { (code, body) ->
                    debugResponse = withContext(Dispatchers.Default) {
                        buildDebugResponse(
                            endpoint = endpoint,
                            responseCode = code,
                            responseBody = body,
                            responseDurationMs = elapsed
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

        val base = normalizeBaseUrl(baseUrl)
        if (base == null) {
            errorMessage = "弹幕源 Base URL 无效"
            return
        }

        val trimmedFileName = fileName.trim()
        if (trimmedFileName.isBlank()) {
            errorMessage = "请输入文件名"
            return
        }

        val matchUrl = "$base/api/v2/match"
        val matchBody = JSONObject().put("fileName", trimmedFileName).toString()

        viewModelScope.launch {
            isAutoMatching = true
            errorMessage = null
            autoMatchResult = null
            val flowStartedAt = System.currentTimeMillis()

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

            val commentUrl = "$base/api/v2/comment/${selection.commentId}?format=json"
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

        val base = normalizeBaseUrl(baseUrl)
        if (base == null) {
            errorMessage = "弹幕源 Base URL 无效"
            return
        }

        val query = keyword.trim()
        if (query.isBlank()) {
            errorMessage = "请输入搜索关键词"
            return
        }

        val url = "$base/api/v2/search/anime?keyword=${urlEncode(query)}"

        viewModelScope.launch {
            isSearchingAnime = true
            errorMessage = null
            manualResult = null
            val startedAt = System.currentTimeMillis()
            val result = executeGet(url)
            val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
            manualHasSearched = true

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
                        manualCurrentAnime = null
                        manualEpisodeCandidates = emptyList()
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

    fun openManualAnimeDetail(baseUrl: String, anime: DownloadAnimeCandidate) {
        if (isSearchingAnime || isLoadingEpisodes) return

        val base = normalizeBaseUrl(baseUrl)
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

        val base = normalizeBaseUrl(baseUrl)
        if (base == null) {
            errorMessage = "弹幕源 Base URL 无效"
            return
        }

        val url = "$base/api/v2/comment/${episode.episodeId}?format=json"

        viewModelScope.launch {
            isLoadingManualDanmu = true
            loadingEpisodeId = episode.episodeId
            errorMessage = null
            val startedAt = System.currentTimeMillis()
            val result = executeGet(url)
            val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)

            result.fold(
                onSuccess = { (code, body) ->
                    recordSuccess(
                        scene = "弹幕测试/手动获取弹幕",
                        method = "GET",
                        url = url,
                        statusCode = code,
                        durationMs = elapsed,
                        body = body
                    )
                    if (code !in 200..299) {
                        errorMessage = "获取弹幕失败：HTTP $code"
                    } else {
                        manualResult = withContext(Dispatchers.Default) {
                            buildDanmuInsightOrFallback(
                                raw = body,
                                commentId = episode.episodeId,
                                animeTitle = anime.title,
                                episodeTitle = episode.title,
                                source = episode.source,
                                pathLabel = "手动匹配",
                                requestDurationMs = elapsed
                            )
                        }
                    }
                },
                onFailure = { throwable ->
                    val message = throwable.message ?: "获取弹幕失败"
                    errorMessage = message
                    recordFailure(
                        scene = "弹幕测试/手动获取弹幕",
                        method = "GET",
                        url = url,
                        durationMs = elapsed,
                        message = message
                    )
                }
            )

            isLoadingManualDanmu = false
            loadingEpisodeId = null
        }
    }

    private data class BuiltRequest(
        val method: String,
        val url: String,
        val body: String?
    )

    private fun buildDebugResponse(
        endpoint: ApiEndpointConfig,
        responseCode: Int,
        responseBody: String,
        responseDurationMs: Long
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
            bodySizeBytes = responseBody.toByteArray(Charsets.UTF_8).size,
            danmuInsight = null
        )
    }

    private fun shouldCollapseDebugResponse(endpoint: ApiEndpointConfig): Boolean {
        return endpoint.key == "getComment" || endpoint.key == "getSegmentComment"
    }

    private fun buildDanmuInsightOrFallback(
        raw: String,
        commentId: Long?,
        animeTitle: String,
        episodeTitle: String,
        source: String,
        pathLabel: String,
        requestDurationMs: Long?
    ): DanmuInsight {
        return parseDanmuInsight(
            raw = raw,
            commentId = commentId,
            animeTitle = animeTitle,
            episodeTitle = episodeTitle,
            source = source,
            pathLabel = pathLabel,
            matchedAtMillis = System.currentTimeMillis(),
            requestDurationMs = requestDurationMs
        ) ?: run {
            val preview = buildTextPreview(raw, 4_000)
            DanmuInsight(
                commentId = commentId,
                animeTitle = animeTitle,
                episodeTitle = episodeTitle,
                source = source,
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
                comments = emptyList()
            )
        }
    }

    private fun buildRequest(
        endpoint: ApiEndpointConfig,
        baseUrl: String,
        rawParams: Map<String, String>,
        rawBody: String
    ): BuiltRequest {
        val base = baseUrl.trim().trimEnd('/')
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

    private suspend fun executeGet(url: String): Result<Pair<Int, String>> {
        val request = Request.Builder().url(url).get().build()
        return executeRequest(request)
    }

    private suspend fun executeJsonPost(
        url: String,
        jsonBody: String
    ): Result<Pair<Int, String>> {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(mediaType))
            .build()
        return executeRequest(request)
    }

    private suspend fun executeRequest(request: Request): Result<Pair<Int, String>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.newCall(request).execute().use { response ->
                    response.code to response.body.string()
                }
            }
        }
    }

    private fun normalizeBaseUrl(baseUrl: String): String? {
        val raw = baseUrl.trim()
        if (raw.isBlank()) return null
        return if (
            raw.startsWith("http://", ignoreCase = true) ||
            raw.startsWith("https://", ignoreCase = true)
        ) {
            raw.trimEnd('/')
        } else {
            "http://$raw".trimEnd('/')
        }
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
