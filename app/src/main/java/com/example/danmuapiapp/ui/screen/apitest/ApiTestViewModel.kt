package com.example.danmuapiapp.ui.screen.apitest

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.domain.model.RequestRecord
import com.example.danmuapiapp.domain.repository.RequestRecordRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
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

    var responseCode by mutableStateOf<Int?>(null)
        private set

    var responseBody by mutableStateOf("")
        private set

    var responseDurationMs by mutableStateOf<Long?>(null)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var requestUrl by mutableStateOf("")
        private set

    var curlCommand by mutableStateOf("")
        private set

    fun clearResult() {
        responseCode = null
        responseBody = ""
        responseDurationMs = null
        errorMessage = null
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
            responseCode = null
            responseBody = ""
            responseDurationMs = null

            val startedAt = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val requestBuilder = Request.Builder().url(built.url)
                    if (built.method == "GET") {
                        requestBuilder.get()
                    } else {
                        val mediaType = "application/json; charset=utf-8".toMediaType()
                        val payload = (built.body ?: "{}").toRequestBody(mediaType)
                        requestBuilder.method(built.method, payload)
                    }
                    httpClient.newCall(requestBuilder.build()).execute().use { response ->
                        val body = response.body.string()
                        response.code to body
                    }
                }
            }

            val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
            responseDurationMs = elapsed

            result.fold(
                onSuccess = { (code, body) ->
                    responseCode = code
                    responseBody = body
                    recordRepository.addRecord(
                        RequestRecord(
                            scene = "接口调试/${endpoint.title}",
                            method = built.method,
                            url = built.url,
                            statusCode = code,
                            durationMs = elapsed,
                            success = code in 200..299,
                            responseSnippet = body.take(2000)
                        )
                    )
                },
                onFailure = { throwable ->
                    val msg = throwable.message ?: "请求失败"
                    errorMessage = msg
                    recordRepository.addRecord(
                        RequestRecord(
                            scene = "接口调试/${endpoint.title}",
                            method = built.method,
                            url = built.url,
                            statusCode = null,
                            durationMs = elapsed,
                            success = false,
                            errorMessage = msg
                        )
                    )
                }
            )

            isLoading = false
        }
    }

    private data class BuiltRequest(
        val method: String,
        val url: String,
        val body: String?
    )

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

        endpoint.params.filter { it.required }.forEach { p ->
            require(!params[p.name].isNullOrBlank()) { "参数不能为空：${p.label}" }
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
                params[key]?.let { queryMap[key] = it }
            }
            endpoint.forceQueryParams.forEach { key -> params.remove(key) }
        }

        val url = buildString {
            append(base)
            append(if (path.startsWith('/')) path else "/$path")
            if (queryMap.isNotEmpty()) {
                append('?')
                append(queryMap.entries.joinToString("&") { (k, v) -> "${urlEncode(k)}=${urlEncode(v)}" })
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

    private fun urlEncode(input: String): String {
        return URLEncoder.encode(input, Charsets.UTF_8.name())
    }
}
