package com.example.danmuapiapp.ui.screen.apitest

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale

/**
 * 接口调试页子控制器：自选接口的参数拼装、请求发送、cURL 生成与调试响应状态。
 *
 * 对外由 [ApiTestViewModel] 转发，行为与拆分前一致。
 */
internal class ApiTestDebugController(
    private val scope: CoroutineScope,
    private val executor: ApiTestRequestExecutor,
    private val setError: (String?) -> Unit,
    private val markApiAction: (Long) -> Unit
) {
    var isLoading by mutableStateOf(false)
        private set

    var debugResponse by mutableStateOf<ApiDebugResponse?>(null)
        private set

    var debugCalls by mutableStateOf<List<ApiDebugCall>>(emptyList())
        private set

    var requestUrl by mutableStateOf("")
        private set

    var curlCommand by mutableStateOf("")
        private set

    fun clearDebugResponse() {
        debugResponse = null
        requestUrl = ""
        curlCommand = ""
    }

    fun clearDebugCalls() {
        debugCalls = emptyList()
    }

    fun sendRequest(
        endpoint: ApiEndpointConfig,
        baseUrl: String,
        paramValues: Map<String, String>,
        rawBody: String
    ) {
        if (isLoading) return

        val built = runCatching {
            buildRequest(endpoint, executor.resolveApiBaseUrl(baseUrl), paramValues, rawBody)
        }.getOrElse {
            setError(it.message ?: "请求参数错误")
            return
        }

        requestUrl = built.url
        curlCommand = buildCurlCommand(built.method, built.url, built.body)

        scope.launch {
            isLoading = true
            setError(null)
            debugResponse = null

            val startedAt = System.currentTimeMillis()
            markApiAction(startedAt)
            val result = executor.executeRequest(
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
                    debugCalls = (listOf(ApiDebugCall(built.method, built.url, code, elapsed,
                        debugResponse?.previewText.orEmpty())) + debugCalls).take(30)
                    executor.recordSuccess(
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
                    setError(message)
                    debugCalls = (listOf(ApiDebugCall(built.method, built.url, null, elapsed, message)) + debugCalls).take(30)
                    executor.recordFailure(
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
            path = path.replace(":$key", executor.urlEncode(value))
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
                    "${executor.urlEncode(key)}=${executor.urlEncode(value)}"
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

    private data class BuiltRequest(
        val method: String,
        val url: String,
        val body: String?
    )
}
