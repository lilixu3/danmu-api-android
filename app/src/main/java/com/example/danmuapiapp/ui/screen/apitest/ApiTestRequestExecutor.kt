package com.example.danmuapiapp.ui.screen.apitest

import com.example.danmuapiapp.data.network.newOutboundCall
import com.example.danmuapiapp.data.util.RuntimeTokenNormalizer
import com.example.danmuapiapp.domain.model.RequestRecord
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.repository.RequestRecordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

/**
 * 接口测试页共用的请求执行器：出站请求、请求记录落库与 Base URL 归一化。
 *
 * 调试、自动匹配、手动搜索、收藏与导出五条流程共用同一份实现，行为与拆分前一致。
 */
internal class ApiTestRequestExecutor(
    private val httpClient: OkHttpClient,
    private val recordRepository: RequestRecordRepository,
    private val runtimeState: StateFlow<RuntimeState>
) {
    suspend fun executeGet(url: String): Result<ApiRawResponse> {
        val request = Request.Builder().url(url).get().build()
        return executeRequest(request)
    }

    suspend fun executeLoggedDanmuGet(
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

    suspend fun executeJsonPost(
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

    suspend fun executeBytesGet(url: String): Result<ApiByteResponse> {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.newOutboundCall(Request.Builder().url(url).get().build()).execute().use { response ->
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

    suspend fun executeRequest(request: Request): Result<ApiRawResponse> {
        return withContext(Dispatchers.IO) {
            runCatching {
                httpClient.newOutboundCall(request).execute().use { response ->
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

    /** 请求二进制响应时用于记录/提示的可读片段。 */
    fun textSnippet(bytes: ByteArray, contentType: String): String {
        val normalizedType = contentType.lowercase(Locale.ROOT)
        return if (
            normalizedType.contains("json") ||
            normalizedType.contains("xml") ||
            normalizedType.startsWith("text/")
        ) {
            bytes.toString(Charsets.UTF_8).take(2_000)
        } else {
            "二进制响应：${bytes.size} 字节"
        }
    }

    fun resolveApiBaseUrl(baseUrl: String): String? {
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

    fun urlEncode(input: String): String {
        return URLEncoder.encode(input, Charsets.UTF_8.name())
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

    fun recordSuccess(
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

    fun recordFailure(
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
}

internal data class ApiRawResponse(
    val code: Int,
    val body: String,
    val bodySizeBytes: Int
)

internal data class ApiByteResponse(
    val code: Int,
    val bytes: ByteArray,
    val contentType: String
)
