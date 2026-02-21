package com.example.danmuapiapp.data.repository

import android.content.Context
import com.example.danmuapiapp.data.util.TokenDefaults
import com.example.danmuapiapp.domain.model.RequestRecord
import com.example.danmuapiapp.domain.repository.RequestRecordRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RequestRecordRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient
) : RequestRecordRepository {

    companion object {
        private const val PREFS_NAME = "request_records"
        private const val KEY_JSON = "records_json"
        private const val MAX_RECORDS = 200
        private const val RUNTIME_PREFS_NAME = "runtime"
        private const val DEFAULT_PORT = 9321
    }

    private val localPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val runtimePrefs = context.getSharedPreferences(RUNTIME_PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val localRecords = loadLocalRecords().toMutableList()
    private var remoteRecords: List<RequestRecord> = emptyList()
    private var hasRemoteSnapshot = false

    private val _records = MutableStateFlow(localRecords.toList())
    override val records: StateFlow<List<RequestRecord>> = _records.asStateFlow()

    override suspend fun refreshFromService() {
        val fetched = fetchRemoteRecords() ?: return
        remoteRecords = fetched
        hasRemoteSnapshot = true
        publishRecords()
    }

    override fun addRecord(record: RequestRecord) {
        val merged = (listOf(record) + localRecords)
            .sortedByDescending { it.timestamp }
            .distinctBy { it.id }
            .take(MAX_RECORDS)
        localRecords.clear()
        localRecords.addAll(merged)
        saveLocalRecords(merged)
        publishRecords()
    }

    override fun clearRecords() {
        localRecords.clear()
        remoteRecords = emptyList()
        hasRemoteSnapshot = false
        _records.value = emptyList()
        localPrefs.edit().remove(KEY_JSON).apply()
    }

    private fun publishRecords() {
        val source = if (hasRemoteSnapshot) remoteRecords else localRecords
        _records.value = source
            .sortedByDescending { it.timestamp }
            .distinctBy { it.id }
            .take(MAX_RECORDS)
    }

    private fun fetchRemoteRecords(): List<RequestRecord>? {
        val port = runtimePrefs.getInt("port", DEFAULT_PORT)
        val token = TokenDefaults.resolveTokenFromPrefs(runtimePrefs, context)
        val tokenPath = if (token.isBlank()) "" else "/$token"
        val url = "http://127.0.0.1:$port$tokenPath/api/reqrecords"

        return runCatching {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.code !in 200..299) return null
                val body = response.body.string()
                parseRemoteRecords(body)
            }
        }.getOrNull()
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
        return key.hashCode().toLong()
    }

    private fun readDurationMs(obj: JSONObject): Long {
        val keys = listOf("durationMs", "duration", "elapsedMs")
        keys.forEach { key ->
            val value = obj.optLong(key, Long.MIN_VALUE)
            if (value != Long.MIN_VALUE && value >= 0L) return value
        }
        return 0L
    }

    private fun parseTimestamp(raw: String): Long {
        if (raw.isBlank()) return System.currentTimeMillis()

        return runCatching {
            Instant.parse(raw).toEpochMilli()
        }.getOrElse {
            runCatching {
                val local = LocalDateTime.parse(
                    raw,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                )
                local.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrElse { System.currentTimeMillis() }
        }
    }

    private fun decodeUtf8(raw: String): String {
        if (raw.isBlank()) return ""
        return runCatching {
            URLDecoder.decode(raw, Charsets.UTF_8.name())
        }.getOrDefault(raw)
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

    private fun loadLocalRecords(): List<RequestRecord> {
        val raw = localPrefs.getString(KEY_JSON, "") ?: ""
        if (raw.isBlank()) return emptyList()

        return runCatching {
            json.decodeFromString(ListSerializer(RequestRecord.serializer()), raw)
                .sortedByDescending { it.timestamp }
                .take(MAX_RECORDS)
        }.getOrElse { emptyList() }
    }

    private fun saveLocalRecords(records: List<RequestRecord>) {
        val body = runCatching {
            json.encodeToString(ListSerializer(RequestRecord.serializer()), records)
        }.getOrElse { "[]" }
        localPrefs.edit().putString(KEY_JSON, body).apply()
    }
}
