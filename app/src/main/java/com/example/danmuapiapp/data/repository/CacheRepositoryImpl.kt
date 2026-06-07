package com.example.danmuapiapp.data.repository

import android.content.Context
import com.example.danmuapiapp.data.util.ParseUtils.decodeUtf8
import com.example.danmuapiapp.data.util.ParseUtils.parseTimestamp
import com.example.danmuapiapp.data.util.RuntimeApiAccess
import com.example.danmuapiapp.data.util.RuntimeApiAccessResolver
import com.example.danmuapiapp.data.util.RuntimeManagementPaths
import com.example.danmuapiapp.data.util.applyRuntimeApiAuth
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
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * danmu_api 核心只提供 POST /api/cache/clear 端点，无 GET 统计接口。
 * 缓存条目数量从 /api/reqrecords 的 reqRecords 数组和 todayReqNum 获取。
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

    override suspend fun refresh() {
        _isLoading.value = true
        try {
            val runtime = resolveRuntimeApiAccess()
            val previousStats = _cacheStats.value
            val result = fetchReqRecordStats(runtime)
            val animeSummary = fetchAnimeCacheSummary(runtime)
            _cacheStats.value = result?.let { stats ->
                val base = stats.copy(
                    lastClearedAt = stats.lastClearedAt ?: previousStats.lastClearedAt
                )
                if (animeSummary.available) {
                    base.copy(
                        animeCacheCount = animeSummary.animeCacheCount,
                        mergedSourceCount = animeSummary.mergedSourceCount,
                        episodeLinkCount = animeSummary.episodeLinkCount
                    )
                } else {
                    base.copy(
                        animeCacheCount = previousStats.animeCacheCount,
                        mergedSourceCount = previousStats.mergedSourceCount,
                        episodeLinkCount = previousStats.episodeLinkCount
                    )
                }
            } ?: previousStats.copy(
                isAvailable = animeSummary.available || previousStats.isAvailable,
                animeCacheCount = if (animeSummary.available) animeSummary.animeCacheCount else previousStats.animeCacheCount,
                mergedSourceCount = if (animeSummary.available) animeSummary.mergedSourceCount else previousStats.mergedSourceCount,
                episodeLinkCount = if (animeSummary.available) animeSummary.episodeLinkCount else previousStats.episodeLinkCount
            )
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
            val adminState = adminSessionRepository.sessionState.value
            val adminToken = adminSessionRepository.currentAdminTokenOrNull().trim('/')
            when {
                !adminState.hasAdminTokenConfigured -> {
                    throw Exception("当前核心要求 ADMIN_TOKEN，请先到 设置 > 管理员权限 配置")
                }
                !adminState.isAdminMode -> {
                    throw Exception("请先到 设置 > 管理员权限 开启管理员模式，再清理缓存")
                }
                adminToken.isBlank() -> {
                    throw Exception("管理员令牌为空，请重新进入管理员模式后重试")
                }
            }

            val url = "http://127.0.0.1:${runtime.port}/$adminToken/api/cache/clear"
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
                val clearedAt = System.currentTimeMillis()
                _cacheStats.value = CacheStats(
                    reqRecordsCount = 0,
                    todayReqNum = 0,
                    lastClearedAt = clearedAt,
                    isAvailable = true,
                    recentEntries = emptyList(),
                    animeCacheCount = 0,
                    mergedSourceCount = 0,
                    episodeLinkCount = 0
                )
                _cacheEntries.value = emptyList()
                refresh()
                _cacheStats.value = _cacheStats.value.copy(lastClearedAt = clearedAt)
                return@runCatching Unit
            }

            throw Exception(extractErrorMessage(body, code))
        }
    }

    private fun resolveRuntimeApiAccess(): RuntimeApiAccess {
        return RuntimeApiAccessResolver.resolve(context, runtimePrefs, DEFAULT_PORT)
    }


    private fun fetchReqRecordStats(runtime: RuntimeApiAccess): CacheStats? {
        recordTokenPaths(runtime).forEach { tokenPath ->
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

    private fun recordTokenPaths(runtime: RuntimeApiAccess): List<String> {
        val adminState = adminSessionRepository.sessionState.value
        return RuntimeManagementPaths.tokenPaths(
            runtimeTokenPaths = runtime.tokenPaths,
            adminMode = adminState.isAdminMode,
            adminToken = adminSessionRepository.currentAdminTokenOrNull()
        )
    }

    private data class AnimeCacheSummary(
        val available: Boolean = false,
        val animeCacheCount: Int = 0,
        val mergedSourceCount: Int = 0,
        val episodeLinkCount: Int = 0
    )

    private fun fetchAnimeCacheSummary(runtime: RuntimeApiAccess): AnimeCacheSummary {
        recordTokenPaths(runtime).forEach { tokenPath ->
            val url = "http://127.0.0.1:${runtime.port}$tokenPath/api/cache/animes"
            val summary = runCatching {
                val request = Request.Builder()
                    .url(url)
                    .applyRuntimeApiAuth(runtime)
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.code !in 200..299) return@use null
                    parseAnimeCacheSummary(response.body.string())
                }
            }.getOrNull()
            if (summary != null) return summary
        }
        return AnimeCacheSummary()
    }

    private fun parseAnimeCacheSummary(raw: String): AnimeCacheSummary? {
        val root = runCatching { JSONObject(raw) }.getOrElse { return null }
        val arr = root.optJSONArray("data") ?: return AnimeCacheSummary(available = true)
        var mergedCount = 0
        var linkCount = 0
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val links = item.optJSONArray("links")
            linkCount += links?.length() ?: 0
            val children = item.optJSONArray("mergedChildren")
            mergedCount += children?.length() ?: 0
            if (children != null) {
                for (j in 0 until children.length()) {
                    val child = children.optJSONObject(j) ?: continue
                    linkCount += child.optJSONArray("links")?.length() ?: 0
                }
            }
        }
        return AnimeCacheSummary(
            available = true,
            animeCacheCount = arr.length(),
            mergedSourceCount = mergedCount,
            episodeLinkCount = linkCount
        )
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
            val limit = minOf(arr.length(), 16)
            for (i in 0 until limit) {
                val obj = arr.optJSONObject(i) ?: continue
                val method = obj.optString("method", "GET").uppercase()
                val rawPath = obj.optString("interface", "")
                val decodedPath = decodeUtf8(rawPath).ifBlank { "未知接口" }
                val timestamp = parseTimestamp(obj.optString("timestamp", ""))
                val paramsObj = obj.opt("params")
                val paramsText = formatParams(paramsObj)
                val keyword = readParam(rawPath, paramsObj, "keyword", "fileName", "anime")
                val requestUrl = readParam(rawPath, paramsObj, "url")
                val fileName = readParam(rawPath, paramsObj, "fileName")
                val statusCode = obj.optInt("statusCode", Int.MIN_VALUE).takeIf { it in 100..599 }
                entries += CacheEntry(
                    key = decodedPath,
                    type = method,
                    sizeBytes = 0L,
                    hitCount = 1,
                    createdAt = timestamp,
                    statusCode = statusCode,
                    clientIp = obj.optString("clientIp", "").trim(),
                    keyword = keyword,
                    requestUrl = requestUrl,
                    fileName = fileName,
                    paramsText = paramsText
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

    private fun readParam(pathWithQuery: String, paramsObj: Any?, vararg names: String): String {
        names.forEach { name ->
            readBodyParam(paramsObj, name)?.let { return it }
            readQueryParam(pathWithQuery, name)?.let { return it }
        }
        return ""
    }

    private fun readBodyParam(paramsObj: Any?, name: String): String? {
        val obj = paramsObj as? JSONObject ?: return null
        val value = obj.opt(name) ?: return null
        if (value == JSONObject.NULL) return null
        return value.toString().trim().takeIf { it.isNotBlank() }
    }

    private fun readQueryParam(pathWithQuery: String, name: String): String? {
        val query = pathWithQuery.substringAfter('?', missingDelimiterValue = "")
        if (query.isBlank()) return null
        return query.split('&')
            .asSequence()
            .mapNotNull { part ->
                val key = part.substringBefore('=', "")
                val value = part.substringAfter('=', "")
                if (urlDecode(key) == name) urlDecode(value) else null
            }
            .firstOrNull { it.isNotBlank() }
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

    private fun urlDecode(raw: String): String {
        return runCatching { URLDecoder.decode(raw, Charsets.UTF_8.name()) }.getOrDefault(raw)
    }
}
