package com.example.danmuapiapp.data.repository

import android.content.Context
import com.example.danmuapiapp.data.util.ParseUtils.decodeUtf8
import com.example.danmuapiapp.data.util.ParseUtils.parseTimestamp
import com.example.danmuapiapp.data.util.RuntimeApiAccess
import com.example.danmuapiapp.data.util.RuntimeApiAccessResolver
import com.example.danmuapiapp.data.util.RuntimeManagementPaths
import com.example.danmuapiapp.data.util.applyRuntimeApiAuth
import com.example.danmuapiapp.domain.model.RequestRecord
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
    private val localRecords = MutableStateFlow<List<RequestRecord>>(emptyList())
    private val remoteRecords = MutableStateFlow<List<RequestRecord>>(emptyList())

    override suspend fun refreshFromService() {
        fetchRemoteRecords()?.let { fetched ->
            remoteRecords.value = mergeRecords(fetched)
        }
        _records.value = mergeRecords(remoteRecords.value, localRecords.value)
    }

    override fun addRecord(record: RequestRecord) {
        localRecords.value = mergeRecords(listOf(record), localRecords.value)
        _records.value = mergeRecords(remoteRecords.value, localRecords.value)
    }

    override fun clearRecords() {
        localRecords.value = emptyList()
        remoteRecords.value = emptyList()
        _records.value = emptyList()
    }

    private fun fetchRemoteRecords(): List<RequestRecord>? {
        val runtime = RuntimeApiAccessResolver.resolve(context, runtimePrefs, DEFAULT_PORT)
        recordTokenPaths(runtime).forEach { tokenPath ->
            val url = "http://127.0.0.1:${runtime.port}$tokenPath/api/reqrecords"
            val records = runCatching {
                val request = Request.Builder()
                    .url(url)
                    .applyRuntimeApiAuth(runtime)
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.code !in 200..299) return@use null
                    val body = response.body.string()
                    parseRemoteRecords(body)
                }
            }.getOrNull()
            if (records != null) return records
        }

        return null
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

    private fun parseRemoteRecords(raw: String): List<RequestRecord> {
        val root = runCatching { JSONObject(raw) }.getOrElse { return emptyList() }
        val arr = root.optJSONArray("records") ?: JSONArray()
        if (arr.length() <= 0) return emptyList()

        val out = ArrayList<RequestRecord>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            out += mapRemoteRecord(obj)
        }
        return out
    }

    private fun mapRemoteRecord(obj: JSONObject): RequestRecord {
        val method = obj.optString("method", "GET").uppercase(Locale.getDefault())
        val rawPath = obj.optString("interface", "")
        val decodedPath = decodeUtf8(rawPath).ifBlank { "未知接口" }
        val clientIp = obj.optString("clientIp", "").trim()
        val timestamp = parseTimestamp(obj.optString("timestamp", ""))
        val statusCode = obj.optInt("statusCode", Int.MIN_VALUE).takeIf { it in 100..599 }
        val durationMs = readDurationMs(obj)
        val errorMessage = obj.optString("errorMessage", "").trim().ifBlank { null }
        val success = when {
            obj.has("success") -> obj.optBoolean("success", statusCode?.let { it in 200..299 } ?: (errorMessage == null))
            errorMessage != null -> false
            statusCode != null -> statusCode in 200..299
            else -> true
        }
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
            responseSnippet = extraInfo
        )
    }

    private fun buildRemoteId(
        method: String,
        path: String,
        timestamp: Long,
        clientIp: String,
        statusCode: Int?,
        durationMs: Long,
        errorMessage: String
    ): Long {
        val key = "$timestamp|$method|$path|$clientIp|${statusCode ?: 0}|$durationMs|$errorMessage"
        // 使用两个不同种子的哈希组合成 64 位，降低碰撞概率，同时避免依赖远端数组下标。
        val h1 = key.hashCode().toLong() and 0xFFFFFFFFL
        val h2 = key.reversed().hashCode().toLong() and 0xFFFFFFFFL
        return (h1 shl 32) or h2
    }

    private fun readDurationMs(obj: JSONObject): Long {
        val keys = listOf("durationMs", "duration", "elapsedMs")
        keys.forEach { key ->
            val value = obj.optLong(key, Long.MIN_VALUE)
            if (value != Long.MIN_VALUE && value >= 0L) return value
        }
        return 0L
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

}
