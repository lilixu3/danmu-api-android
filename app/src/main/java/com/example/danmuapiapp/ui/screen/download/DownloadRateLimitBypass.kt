package com.example.danmuapiapp.ui.screen.download

import com.example.danmuapiapp.data.network.newOutboundCall
import com.example.danmuapiapp.data.util.RuntimeTokenNormalizer
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.repository.AdminSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal data class RateLimitBypassSession(
    val originalValue: String,
    val bypassApplied: Boolean
)

/**
 * 下载期间的限流旁路：开跑前把 RATE_LIMIT_MAX_REQUESTS 临时置 0，收尾时恢复原值。
 *
 * 只通过本机控制接口读写配置，失败一律降级为“不旁路”，不影响下载主流程。
 */
internal class DownloadRateLimitBypass(
    private val httpClient: OkHttpClient,
    private val adminSessionRepository: AdminSessionRepository,
    private val runtimeState: () -> RuntimeState
) {
    private companion object {
        const val RATE_LIMIT_ENV_KEY = "RATE_LIMIT_MAX_REQUESTS"
        const val RATE_LIMIT_BYPASS_VALUE = "0"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    suspend fun prepare(): RateLimitBypassSession? {
        val originalValue = fetchRateLimitEnvValue().getOrElse { return null }
        val normalized = originalValue.trim()
        if (normalized == RATE_LIMIT_BYPASS_VALUE) {
            return RateLimitBypassSession(
                originalValue = normalized,
                bypassApplied = false
            )
        }
        val setResult = setRateLimitEnvValue(RATE_LIMIT_BYPASS_VALUE)
        if (setResult.isFailure) {
            return null
        }
        return RateLimitBypassSession(
            originalValue = originalValue,
            bypassApplied = true
        )
    }

    suspend fun restore(session: RateLimitBypassSession?): String? {
        if (session == null || !session.bypassApplied) return null
        val restoreValue = session.originalValue
        val result = setRateLimitEnvValue(restoreValue)
        return result.exceptionOrNull()?.message
    }

    private suspend fun fetchRateLimitEnvValue(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(buildLocalControlApiUrl("/api/config"))
                .get()
                .build()
            httpClient.newOutboundCall(request).execute().use { response ->
                val raw = response.body.string()
                if (!response.isSuccessful) {
                    val fallback = "读取 $RATE_LIMIT_ENV_KEY 失败：HTTP ${response.code}"
                    val message = runCatching {
                        JSONObject(raw).optString("message").trim().ifBlank { fallback }
                    }.getOrDefault(fallback)
                    error(message)
                }
                val root = if (raw.isBlank()) JSONObject() else JSONObject(raw)
                val value = extractEnvValueFromConfig(root, RATE_LIMIT_ENV_KEY)
                    ?: error("读取 $RATE_LIMIT_ENV_KEY 失败：配置项不存在")
                value
            }
        }
    }

    private suspend fun setRateLimitEnvValue(value: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject()
                .put("key", RATE_LIMIT_ENV_KEY)
                .put("value", value)
                .toString()
                .toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(buildLocalControlApiUrl("/api/env/set"))
                .post(payload)
                .build()
            httpClient.newOutboundCall(request).execute().use { response ->
                val raw = response.body.string()
                val fallback = "设置 $RATE_LIMIT_ENV_KEY 失败：HTTP ${response.code}"
                if (!response.isSuccessful) {
                    val message = runCatching {
                        JSONObject(raw).optString("message").trim().ifBlank { fallback }
                    }.getOrDefault(fallback)
                    error(message)
                }
                if (raw.isBlank()) return@use
                val root = runCatching { JSONObject(raw) }.getOrNull() ?: return@use
                if (root.has("success") && !root.optBoolean("success", false)) {
                    val message = root.optString("message").trim().ifBlank { "设置 $RATE_LIMIT_ENV_KEY 失败" }
                    error(message)
                }
            }
        }
    }

    private fun buildLocalControlApiUrl(path: String): String {
        val state = runtimeState()
        val runtimeToken = RuntimeTokenNormalizer.normalizeInput(state.token).trim('/')
        val adminToken = RuntimeTokenNormalizer.normalizeInput(
            adminSessionRepository.currentAdminTokenOrNull()
        ).trim('/')
        val tokenPath = when {
            adminSessionRepository.sessionState.value.isAdminMode && adminToken.isNotBlank() -> "/$adminToken"
            runtimeToken.isNotBlank() -> "/$runtimeToken"
            else -> ""
        }
        return "http://127.0.0.1:${state.port}$tokenPath$path"
    }

    private fun extractEnvValueFromConfig(root: JSONObject, key: String): String? {
        val original = root.optJSONObject("originalEnvVars")?.opt(key)
        envValueToString(original)?.let { return it }

        val envs = root.optJSONObject("envs")?.opt(key)
        envValueToString(envs)?.let { return it }

        return null
    }

    private fun envValueToString(raw: Any?): String? {
        if (raw == null || raw == JSONObject.NULL) return null
        return when (raw) {
            is JSONObject -> {
                val inner = raw.opt("value")
                if (inner == null || inner == JSONObject.NULL) null else inner.toString()
            }
            is Number -> raw.toString()
            is Boolean -> raw.toString()
            else -> raw.toString()
        }?.trim()
    }
}
