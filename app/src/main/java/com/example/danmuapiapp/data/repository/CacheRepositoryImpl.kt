package com.example.danmuapiapp.data.repository

import android.content.Context
import com.example.danmuapiapp.data.util.ParseUtils.parseTimestamp
import com.example.danmuapiapp.data.util.RuntimeApiAccess
import com.example.danmuapiapp.data.util.RuntimeApiAccessResolver
import com.example.danmuapiapp.data.util.applyRuntimeApiAuth
import com.example.danmuapiapp.domain.model.CacheEntry
import com.example.danmuapiapp.domain.model.CacheStats
import com.example.danmuapiapp.domain.repository.CacheRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * danmu_api 核心只提供 POST /api/cache/clear 端点，无 GET 统计接口。
 * 缓存条目数量从 /api/reqrecords 的 reqRecords 数组和 todayReqNum 获取。
 */
@Singleton
class CacheRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient
) : CacheRepository {

    companion object {
        private const val RUNTIME_PREFS_NAME = "runtime"
        private const val DEFAULT_PORT = 9321
    }


    private val runtimePrefs = context.getSharedPreferences(RUNTIME_PREFS_NAME, Context.MODE_PRIVATE)

    private val _cacheStats = MutableStateFlow(CacheStats())
    override val cacheStats: StateFlow<CacheStats> = _cacheStats.asStateFlow()

    private val _cacheEntries = MutableStateFlow<List<CacheEntry>>(emptyList())
    override val cacheEntries: StateFlow<List<CacheEntry>> = _cacheEntries.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    override suspend fun refresh() {
        _isLoading.value = true
        try {
            val result = fetchReqRecordStats()
            _cacheStats.value = result ?: CacheStats(isAvailable = false)
            if (result != null) {
                _cacheEntries.value = result.recentEntries
            }
        } finally {
            _isLoading.value = false
        }
    }

    override suspend fun clearAll(): Result<Unit> {
        return runCatching {
            val runtime = resolveRuntimeApiAccess()
            var lastMessage = "HTTP 403"

            runtime.tokenPaths.forEach { tokenPath ->
                val url = "http://127.0.0.1:${runtime.port}$tokenPath/api/cache/clear"
                val request = Request.Builder()
                    .url(url)
                    .applyRuntimeApiAuth(runtime)
                    .post("".toRequestBody("application/json".toMediaType()))
                    .build()

                val result = httpClient.newCall(request).execute().use { response ->
                    val body = runCatching { response.body.string() }.getOrDefault("")
                    response.code to body
                }
                val code = result.first
                val body = result.second
                if (code in 200..299) {
                    runCatching {
                        val root = JSONObject(body)
                        val items = root.optJSONObject("clearedItems")
                        val reqRecords = items?.optInt("reqRecords", 0) ?: 0
                        val todayReqNum = items?.optInt("todayReqNum", 0) ?: 0
                        _cacheStats.value = CacheStats(
                            reqRecordsCount = reqRecords,
                            todayReqNum = todayReqNum,
                            lastClearedAt = System.currentTimeMillis(),
                            isAvailable = true,
                            recentEntries = emptyList()
                        )
                        _cacheEntries.value = emptyList()
                    }
                    return@runCatching Unit
                }
                lastMessage = extractErrorMessage(body, code)
            }

            throw Exception(lastMessage)
        }
    }

    private fun resolveRuntimeApiAccess(): RuntimeApiAccess {
        return RuntimeApiAccessResolver.resolve(context, runtimePrefs, DEFAULT_PORT)
    }


    private fun fetchReqRecordStats(): CacheStats? {
        val runtime = resolveRuntimeApiAccess()
        runtime.tokenPaths.forEach { tokenPath ->
            val url = "http://127.0.0.1:${runtime.port}$tokenPath/api/reqrecords"
            val result = runCatching {
                val request = Request.Builder()
                    .url(url)
                    .applyRuntimeApiAuth(runtime)
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.code !in 200..299) return@use null
                    parseReqRecordsResponse(response.body.string())
                }
            }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    private fun extractErrorMessage(raw: String, code: Int): String {
        return runCatching {
            val obj = JSONObject(raw)
            obj.optString("message")
                .ifBlank { obj.optString("errorMessage") }
                .ifBlank { "HTTP $code" }
        }.getOrDefault("HTTP $code")
    }

    private fun parseReqRecordsResponse(raw: String): CacheStats? {
        val root = runCatching { JSONObject(raw) }.getOrElse { return null }
        val todayReqNum = root.optInt("todayReqNum", 0)
        val arr = root.optJSONArray("records")
        val reqRecordsCount = arr?.length() ?: 0

        val entries = mutableListOf<CacheEntry>()
        if (arr != null) {
            val limit = minOf(arr.length(), 8)
            for (i in 0 until limit) {
                val obj = arr.optJSONObject(i) ?: continue
                val method = obj.optString("method", "GET")
                val path = obj.optString("interface", "").ifBlank { "未知接口" }
                val timestamp = parseTimestamp(obj.optString("timestamp", ""))
                entries += CacheEntry(
                    key = path,
                    type = method,
                    sizeBytes = 0L,
                    hitCount = 1,
                    createdAt = timestamp
                )
            }
        }

        return CacheStats(
            reqRecordsCount = reqRecordsCount,
            todayReqNum = todayReqNum,
            lastClearedAt = null,
            isAvailable = true,
            recentEntries = entries
        )
    }
}
