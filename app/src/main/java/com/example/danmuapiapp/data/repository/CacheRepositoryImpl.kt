package com.example.danmuapiapp.data.repository

import android.content.Context
import com.example.danmuapiapp.data.util.TokenDefaults
import com.example.danmuapiapp.domain.model.CacheEntry
import com.example.danmuapiapp.domain.model.CacheStats
import com.example.danmuapiapp.domain.repository.AdminSessionRepository
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
 * clearAll() 调用 POST /{token}/api/cache/clear，解析 clearedItems 返回结果。
 */
@Singleton
class CacheRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val adminSessionRepository: AdminSessionRepository
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

    /**
     * 从 /api/reqrecords 获取请求记录数量作为缓存概览数据。
     * 这是目前核心唯一可查询的缓存相关统计。
     */
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

    /**
     * 调用 POST /{token}/api/cache/clear 清理所有缓存。
     * 成功后解析 clearedItems 更新本地状态。
     */
    override suspend fun clearAll(): Result<Unit> {
        return runCatching {
            val port = runtimePrefs.getInt("port", DEFAULT_PORT)
            val tokenPath = resolveTokenPath()
            val url = "http://127.0.0.1:$port$tokenPath/api/cache/clear"

            val request = Request.Builder()
                .url(url)
                .post("".toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = runCatching { response.body.string() }.getOrDefault("")
                if (response.code !in 200..299) {
                    val msg = runCatching {
                        JSONObject(body).optString("message", "HTTP ${response.code}")
                    }.getOrDefault("HTTP ${response.code}")
                    throw Exception(msg)
                }
                // 解析 clearedItems 更新统计
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
            }
        }
    }

    private fun resolveTokenPath(): String {
        val token = TokenDefaults.resolveTokenFromPrefs(runtimePrefs, context)
        val adminToken = adminSessionRepository.currentAdminTokenOrNull()
        val isAdminMode = adminSessionRepository.sessionState.value.isAdminMode
        return when {
            isAdminMode && adminToken.isNotBlank() -> "/$adminToken"
            token.isNotBlank() -> "/$token"
            else -> ""
        }
    }

    private fun fetchReqRecordStats(): CacheStats? {
        val port = runtimePrefs.getInt("port", DEFAULT_PORT)
        val tokenPath = resolveTokenPath()
        val url = "http://127.0.0.1:$port$tokenPath/api/reqrecords"

        return runCatching {
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (response.code !in 200..299) return@use null
                parseReqRecordsResponse(response.body.string())
            }
        }.getOrNull()
    }

    private fun parseReqRecordsResponse(raw: String): CacheStats? {
        val root = runCatching { JSONObject(raw) }.getOrElse { return null }
        val todayReqNum = root.optInt("todayReqNum", 0)
        val arr = root.optJSONArray("records")
        val reqRecordsCount = arr?.length() ?: 0

        // 取最近几条记录作为缓存条目预览
        val entries = mutableListOf<CacheEntry>()
        if (arr != null) {
            val limit = minOf(arr.length(), 8)
            for (i in 0 until limit) {
                val obj = arr.optJSONObject(i) ?: continue
                val method = obj.optString("method", "GET")
                val path = obj.optString("interface", "").ifBlank { "未知接口" }
                val timestamp = parseTimestamp(obj.optString("timestamp", ""))
                val statusCode = obj.optInt("statusCode", 0).takeIf { it in 100..599 }
                entries += CacheEntry(
                    key = path,
                    type = method,
                    sizeBytes = 0L,
                    hitCount = statusCode ?: 0,
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

    private fun parseTimestamp(raw: String): Long {
        if (raw.isBlank()) return System.currentTimeMillis()
        return runCatching {
            java.time.Instant.parse(raw).toEpochMilli()
        }.getOrElse {
            runCatching {
                val local = java.time.LocalDateTime.parse(
                    raw,
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                )
                local.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrElse { System.currentTimeMillis() }
        }
    }
}
