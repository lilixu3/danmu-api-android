package com.example.danmuapiapp.data.repository

import android.content.Context
import com.example.danmuapiapp.data.util.ParseUtils.decodeUtf8
import com.example.danmuapiapp.data.util.ParseUtils.parseTimestamp
import com.example.danmuapiapp.data.util.RuntimeApiAccessResolver
import com.example.danmuapiapp.data.util.applyRuntimeApiAuth
import com.example.danmuapiapp.domain.model.RequestRecord
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
    private val httpClient: OkHttpClient
) : RequestRecordRepository {

    companion object {
        private const val MAX_RECORDS = 200
        private const val RUNTIME_PREFS_NAME = "runtime"
        private const val DEFAULT_PORT = 9321
    }

    private val runtimePrefs = context.getSharedPreferences(RUNTIME_PREFS_NAME, Context.MODE_PRIVATE)
    private val _records = MutableStateFlow<List<RequestRecord>>(emptyList())
    override val records: StateFlow<List<RequestRecord>> = _records.asStateFlow()

    override suspend fun refreshFromService() {
        val fetched = fetchRemoteRecords() ?: return
        _records.value = fetched
            .sortedByDescending { it.timestamp }
            .distinctBy { it.id }
            .take(MAX_RECORDS)
    }

    override fun addRecord(record: RequestRecord) {
        // 仅保留远端记录源，避免本地记录与远端记录重复。
    }

    override fun clearRecords() {
        _records.value = emptyList()
    }

    private fun fetchRemoteRecords(): List<RequestRecord>? {
        val runtime = RuntimeApiAccessResolver.resolve(context, runtimePrefs, DEFAULT_PORT)
        runtime.tokenPaths.forEach { tokenPath ->
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

    private fun parseRemoteRecords(raw: String): List<RequestRecord> {
        val root = runCatching { JSONObject(raw) }.getOrElse { return emptyList() }
        val arr = root.optJSONArray("records") ?: JSONArray()
        if (arr.length() <= 0) return emptyList()

        val out = ArrayList<RequestRecord>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            out += mapRemoteRecord(obj, i)
        }
        return out
    }

    private fun mapRemoteRecord(obj: JSONObject, index: Int): RequestRecord {
        val method = obj.optString("method", "GET").uppercase(Locale.getDefault())
        val rawPath = obj.optString("interface", "")
        val decodedPath = decodeUtf8(rawPath).ifBlank { "未知接口" }
        val clientIp = obj.optString("clientIp", "").trim()
        val timestamp = parseTimestamp(obj.optString("timestamp", ""))
        val statusCode = obj.optInt("statusCode", Int.MIN_VALUE).takeIf { it in 100..599 }
        val success = when {
            obj.has("success") -> obj.optBoolean("success", statusCode?.let { it in 200..299 } ?: true)
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
        val errorMessage = obj.optString("errorMessage", "").trim().ifBlank { null }

        return RequestRecord(
            id = buildRemoteId(method, decodedPath, timestamp, clientIp, index),
            timestamp = timestamp,
            scene = if (clientIp.isBlank()) "外部调用" else "外部调用/$clientIp",
            method = method,
            url = decodedPath,
            statusCode = statusCode,
            durationMs = readDurationMs(obj),
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
        index: Int
    ): Long {
        val key = "$timestamp|$method|$path|$clientIp|$index"
        // 使用两个不同种子的哈希组合成 64 位，降低碰撞概率
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
