package com.example.danmuapiapp.ui.screen.apitest

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.json.JSONObject

/**
 * 弹幕测试页「自动匹配」子控制器：文件名匹配与 URL 直达解析两条入口。
 *
 * 对外由 [ApiTestViewModel] 转发，行为与拆分前一致。
 */
internal class ApiTestAutoMatchController(
    private val scope: CoroutineScope,
    private val executor: ApiTestRequestExecutor,
    private val httpClient: OkHttpClient,
    private val currentError: () -> String?,
    private val setError: (String?) -> Unit,
    private val markApiAction: (Long) -> Unit
) {
    var isAutoMatching by mutableStateOf(false)
        private set

    var autoMatchResult by mutableStateOf<DanmuInsight?>(null)
        private set

    fun clearAutoResult() {
        autoMatchResult = null
    }

    fun runAutoMatch(baseUrl: String, fileName: String) {
        if (isAutoMatching) return

        val base = executor.resolveApiBaseUrl(baseUrl)
        if (base == null) {
            setError("弹幕源 Base URL 无效")
            return
        }

        val trimmedFileName = fileName.trim()
        if (trimmedFileName.isBlank()) {
            setError("请输入文件名")
            return
        }

        val inputUrl = ApiTestInputResolver.extractHttpUrl(trimmedFileName)
        if (inputUrl != null) {
            runAutoUrlDanmu(base = base, inputUrl = inputUrl)
            return
        }

        val matchUrl = "$base/api/v2/match"
        val matchBody = JSONObject().put("fileName", trimmedFileName).toString()

        scope.launch {
            isAutoMatching = true
            setError(null)
            autoMatchResult = null
            val flowStartedAt = System.currentTimeMillis()
            markApiAction(flowStartedAt)

            val matchResult = executor.executeJsonPost(matchUrl, matchBody)
            val matchElapsed = (System.currentTimeMillis() - flowStartedAt).coerceAtLeast(0L)

            val selection = matchResult.fold(
                onSuccess = { (code, body) ->
                    executor.recordSuccess(
                        scene = "弹幕测试/自动匹配",
                        method = "POST",
                        url = matchUrl,
                        statusCode = code,
                        durationMs = matchElapsed,
                        body = body
                    )
                    if (code !in 200..299) {
                        setError("自动匹配失败：HTTP $code")
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
                    setError(message)
                    executor.recordFailure(
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
                if (currentError() == null) {
                    setError("自动匹配成功，但返回结果里没有可用的弹幕 ID")
                }
                isAutoMatching = false
                return@launch
            }

            val commentUrl = "$base/api/v2/comment/${selection.commentId}?format=json&duration=true"
            val commentStartedAt = System.currentTimeMillis()
            val commentResult = executor.executeGet(commentUrl)
            val commentElapsed = (System.currentTimeMillis() - commentStartedAt).coerceAtLeast(0L)

            commentResult.fold(
                onSuccess = { (code, body) ->
                    executor.recordSuccess(
                        scene = "弹幕测试/自动匹配弹幕",
                        method = "GET",
                        url = commentUrl,
                        statusCode = code,
                        durationMs = commentElapsed,
                        body = body
                    )
                    if (code !in 200..299) {
                        setError("获取弹幕失败：HTTP $code")
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
                                sourceUrl = selection.sourceUrl,
                                pathLabel = "自动匹配",
                                requestDurationMs = totalElapsed,
                                posterUrl = selection.posterUrl,
                                year = selection.year
                            )
                        }
                    }
                },
                onFailure = { throwable ->
                    val message = throwable.message ?: "获取弹幕失败"
                    setError(message)
                    executor.recordFailure(
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

    private fun runAutoUrlDanmu(
        base: String,
        inputUrl: String
    ) {
        val commentUrl = "$base/api/v2/comment?url=${executor.urlEncode(inputUrl)}&format=json&duration=true"

        scope.launch {
            isAutoMatching = true
            autoMatchResult = null
            setError(null)
            val startedAt = System.currentTimeMillis()
            markApiAction(startedAt)
            val metadataDeferred = async(Dispatchers.IO) { fetchUrlMetadata(inputUrl) }
            val commentDeferred = async(Dispatchers.IO) { executor.executeGet(commentUrl) }
            val result = commentDeferred.await()
            val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
            val metadata = withTimeoutOrNull(700L) { metadataDeferred.await() }
            if (metadataDeferred.isActive) metadataDeferred.cancel()

            result.fold(
                onSuccess = { (code, body) ->
                    executor.recordSuccess(
                        scene = "弹幕测试/自动URL解析",
                        method = "GET",
                        url = commentUrl,
                        statusCode = code,
                        durationMs = elapsed,
                        body = body
                    )
                    if (code !in 200..299) {
                        setError("URL 弹幕解析失败：HTTP $code")
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
                    setError(message)
                    executor.recordFailure(
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

    private suspend fun fetchUrlMetadata(inputUrl: String, base: String? = null): UrlDanmuMetadata? {
        return UrlDanmuMetadataResolver(httpClient).resolve(inputUrl, base)
    }
}
