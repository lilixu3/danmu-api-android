package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.data.network.newOutboundCall

import android.content.Context
import android.net.Uri
import com.example.danmuapiapp.data.util.ParseUtils.decodeUtf8
import com.example.danmuapiapp.data.util.ParseUtils.parseTimestamp
import com.example.danmuapiapp.data.util.RuntimeApiAccess
import com.example.danmuapiapp.data.util.RuntimeApiAccessResolver
import com.example.danmuapiapp.data.util.RuntimeApiUrls
import com.example.danmuapiapp.data.util.RuntimeManagementPaths
import com.example.danmuapiapp.data.util.applyRuntimeApiAuth
import com.example.danmuapiapp.domain.model.RequestRecord
import com.example.danmuapiapp.domain.model.FileNameMatchSummary
import com.example.danmuapiapp.domain.model.RequestTraceDetail
import com.example.danmuapiapp.domain.repository.AdminSessionRepository
import com.example.danmuapiapp.domain.repository.RequestRecordRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RequestRecordRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val adminSessionRepository: AdminSessionRepository
) : RequestRecordRepository {

    companion object {
        private const val MAX_RECORDS = 200
        private const val RUNTIME_PREFS_NAME = "runtime"
        private const val DEFAULT_PORT = 9321
    }

    private val runtimePrefs = context.getSharedPreferences(RUNTIME_PREFS_NAME, Context.MODE_PRIVATE)
    private val _records = MutableStateFlow<List<RequestRecord>>(emptyList())
    override val records: StateFlow<List<RequestRecord>> = _records.asStateFlow()
    private val _todayRequestCount = MutableStateFlow<Int?>(null)
    override val todayRequestCount = _todayRequestCount.asStateFlow()
    private val localRecords = MutableStateFlow<List<RequestRecord>>(emptyList())
    private val remoteRecords = MutableStateFlow<List<RequestRecord>>(emptyList())

    override suspend fun refreshFromService() {
        fetchRemoteRecords()?.let { fetched ->
            remoteRecords.value = mergeRecords(fetched.records)
            _todayRequestCount.value = fetched.todayCount
        }
        _records.value = mergeRecords(remoteRecords.value, localRecords.value)
    }

    override suspend fun fetchTraceDetail(requestId: String): Result<RequestTraceDetail> = runCatching {
        require(requestId.isNotBlank()) { "缺少请求追踪 ID" }
        fetchRemoteTraceDetail(requestId)
    }

    override fun addRecord(record: RequestRecord) {
        localRecords.value = mergeRecords(listOf(record), localRecords.value)
        _records.value = mergeRecords(remoteRecords.value, localRecords.value)
    }

    override fun clearRecords() {
        localRecords.value = emptyList()
        remoteRecords.value = emptyList()
        _records.value = emptyList()
        _todayRequestCount.value = null
    }


    private fun fetchRemoteRecords(): RemoteRequestPage? {
        val runtime = RuntimeApiAccessResolver.resolve(context, runtimePrefs, DEFAULT_PORT)
        recordTokenPaths(runtime).forEach { tokenPath ->
            val url = "http://127.0.0.1:${runtime.port}$tokenPath/api/reqrecords"
            val records = runCatching {
                val request = Request.Builder()
                    .url(url)
                    .applyRuntimeApiAuth(runtime)
                    .get()
                    .build()
                httpClient.newOutboundCall(request).execute().use { response ->
                    if (response.code !in 200..299) return@use null
                    val body = response.body.string()
                    parseRemoteRequestPage(body)
                }
            }.getOrNull()
            if (records != null) return records
        }

        return null
    }

    private fun fetchRemoteTraceDetail(requestId: String): RequestTraceDetail {
        val runtime = RuntimeApiAccessResolver.resolve(context, runtimePrefs, DEFAULT_PORT)
        var lastError: Exception? = null
        val encodedId = Uri.encode(requestId)
        recordTokenPaths(runtime).forEach { tokenPath ->
            val url = RuntimeApiUrls.local(
                runtime.port,
                tokenPath,
                "api/interface-detail/$encodedId"
            )
            val result = runCatching {
                val request = Request.Builder()
                    .url(url)
                    .applyRuntimeApiAuth(runtime)
                    .get()
                    .build()
                httpClient.newOutboundCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.code !in 200..299) {
                        val message = runCatching {
                            JSONObject(body).optString("errorMessage").takeIf { it.isNotBlank() }
                        }.getOrNull()
                        throw java.io.IOException(message ?: "读取请求处理详情失败（HTTP ${response.code}）")
                    }
                    parseRequestTraceDetail(body)
                        ?: throw java.io.IOException("核心返回的请求处理详情格式无效")
                }
            }
            result.onSuccess { return it }
                .onFailure { lastError = it as? Exception ?: java.io.IOException(it) }
        }
        throw lastError ?: java.io.IOException("无法连接核心请求详情接口")
    }

    private fun recordTokenPaths(runtime: RuntimeApiAccess): List<String> {
        val adminState = adminSessionRepository.sessionState.value
        return RuntimeManagementPaths.tokenPaths(
            runtimeTokenPaths = runtime.tokenPaths,
            adminMode = adminState.isAdminMode,
            adminToken = adminSessionRepository.currentAdminTokenOrNull()
        )
    }

    private fun mergeRecords(vararg lists: List<RequestRecord>): List<RequestRecord> {
        return lists
            .flatMap { it }
            .sortedByDescending { it.timestamp }
            .distinctBy { it.id }
            .take(MAX_RECORDS)
    }
}

internal data class RemoteRequestPage(val records: List<RequestRecord>, val todayCount: Int?)

internal fun parseRemoteRequestPage(raw: String): RemoteRequestPage? {
    val root = JSONObject(raw)
    val arr = root.optJSONArray("records") ?: return null
    val todayCount = root.optInt("todayReqNum", -1).takeIf { it >= 0 }

    val out = ArrayList<RequestRecord>(arr.length())
    for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        out += mapRemoteRecord(obj)
    }
    return RemoteRequestPage(out, todayCount)
}

private fun mapRemoteRecord(obj: JSONObject): RequestRecord {
    val method = obj.optString("method", "GET").uppercase(Locale.getDefault())
    val rawPath = obj.optString("interface", "")
    val decodedPath = decodeUtf8(rawPath).ifBlank { "未知接口" }
    val clientIp = obj.optString("clientIp", "").trim()
    val timestamp = parseTimestamp(obj.optString("timestamp", ""))
    val statusCode = obj.optInt("statusCode", Int.MIN_VALUE).takeIf { it in 100..599 }
    val durationMs = readDurationMs(obj)
    val requestId = obj.optString("requestId").takeUnless { it.isBlank() || it == "null" }
    val errorMessage = obj.optString("errorMessage", "").trim().ifBlank { null }
    val success = when {
        obj.has("success") -> obj.optBoolean("success", statusCode?.let { it in 200..299 } ?: (errorMessage == null))
        errorMessage != null -> false
        statusCode != null -> statusCode in 200..299
        else -> true
    }
    val requestParams = obj.optJSONObject("params")
    val paramsText = formatParams(obj.opt("params"))
    val extraInfo = buildString {
        if (clientIp.isNotBlank()) append("客户端IP: $clientIp")
        if (paramsText.isNotBlank()) {
            if (isNotEmpty()) append('\n')
            append("参数:\n")
            append(paramsText)
        }
    }.ifBlank { null }

    return RequestRecord(
        id = buildRemoteId(
            requestId = requestId,
            method = method,
            path = decodedPath,
            timestamp = timestamp,
            clientIp = clientIp,
            statusCode = statusCode,
            durationMs = durationMs,
            errorMessage = errorMessage.orEmpty()
        ),
        timestamp = timestamp,
        scene = if (clientIp.isBlank()) "外部调用" else "外部调用/$clientIp",
        method = method,
        url = decodedPath,
        statusCode = statusCode,
        durationMs = durationMs,
        success = success,
        errorMessage = errorMessage,
        responseSnippet = extraInfo,
        requestId = requestId,
        fileNameMatch = parseFileNameOnlyMatch(requestParams)
    )
}

private fun buildRemoteId(
    requestId: String?,
    method: String,
    path: String,
    timestamp: Long,
    clientIp: String,
    statusCode: Int?,
    durationMs: Long?,
    errorMessage: String
): Long {
    val key = requestId?.let { "request:$it" }
        ?: "$timestamp|$method|$path|$clientIp|${statusCode ?: 0}|$durationMs|$errorMessage"
    // 使用两个不同种子的哈希组合成 64 位，降低碰撞概率，同时避免依赖远端数组下标。
    val h1 = key.hashCode().toLong() and 0xFFFFFFFFL
    val h2 = key.reversed().hashCode().toLong() and 0xFFFFFFFFL
    return (h1 shl 32) or h2
}

private fun readDurationMs(obj: JSONObject): Long? {
    val keys = listOf("durationMs", "duration", "elapsedMs", "totalMs")
    keys.forEach { key ->
        val value = obj.optLong(key, Long.MIN_VALUE)
        if (value != Long.MIN_VALUE && value >= 0L) return value
    }
    return null
}

private fun formatParams(raw: Any?): String {
    val text = when (raw) {
        null, JSONObject.NULL -> ""
        is JSONObject -> raw.toString(2)
        is JSONArray -> raw.toString(2)
        else -> raw.toString()
    }.trim()
    return if (text.equals("null", true) || text.equals("undefined", true)) "" else text
}
