package com.example.danmuapiapp.data.service

import android.content.Context
import com.example.danmuapiapp.data.util.SecureStringStore
import com.example.danmuapiapp.domain.model.GithubAccountStatus
import com.example.danmuapiapp.domain.model.GithubCoreRateLimit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GithubAccountService @Inject constructor(
    @ApplicationContext context: Context,
    httpClient: OkHttpClient
) {
    companion object {
        private const val USER_URL = "https://api.github.com/user"
        private const val RATE_LIMIT_URL = "https://api.github.com/rate_limit"
        private const val GITHUB_ACCEPT = "application/vnd.github+json"
        private const val USER_AGENT = "DanmuApiApp"
    }

    private data class HttpPayload(
        val code: Int,
        val body: String
    )

    private val tokenStore = SecureStringStore(
        context.getSharedPreferences("github_auth_prefs", Context.MODE_PRIVATE),
        "danmuapi_github_auth_v1",
        allowPlaintextFallback = false
    )
    private val directHttpClient = httpClient.newBuilder()
        // Authentication requests must never follow a redirect to a non-GitHub host.
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
    private val refreshMutex = Mutex()
    private val _status = MutableStateFlow(
        GithubAccountStatus(tokenConfigured = storedToken().isNotBlank())
    )

    val status: StateFlow<GithubAccountStatus> = _status.asStateFlow()

    /**
     * Refreshes the account and rate-limit snapshot from GitHub's official API.
     * Pass a non-null value to validate an unsaved token; null reads the stored token.
     */
    suspend fun refresh(tokenOverride: String? = null): GithubAccountStatus = refreshMutex.withLock {
        val token = normalizeToken(tokenOverride ?: storedToken())
        val configured = token.isNotBlank()
        _status.value = GithubAccountStatus(
            isLoading = true,
            tokenConfigured = configured
        )

        val refreshed = withContext(Dispatchers.IO) {
            loadStatus(token)
        }
        _status.value = refreshed
        refreshed
    }

    private fun loadStatus(token: String): GithubAccountStatus {
        val configured = token.isNotBlank()
        var tokenValid: Boolean? = null
        var login: String? = null
        var authenticatedRateLimit = false
        val errors = mutableListOf<String>()

        if (configured) {
            execute(USER_URL, token).fold(
                onSuccess = { payload ->
                    when {
                        payload.code in 200..299 -> {
                            login = GithubAccountPayloadParser.parseLogin(payload.body)
                            if (login != null) {
                                tokenValid = true
                                authenticatedRateLimit = true
                            } else {
                                errors += "GitHub 身份响应缺少登录名"
                            }
                        }
                        payload.code == 401 -> {
                            tokenValid = false
                            errors += apiError("GitHub Token 验证失败", payload)
                        }
                        else -> errors += apiError("暂时无法验证 GitHub Token", payload)
                    }
                },
                onFailure = { error ->
                    errors += networkError("GitHub Token 验证失败", error)
                }
            )
        }

        var rateLimit: GithubCoreRateLimit? = null
        execute(RATE_LIMIT_URL, token.takeIf { authenticatedRateLimit }).fold(
            onSuccess = { payload ->
                if (payload.code in 200..299) {
                    rateLimit = GithubAccountPayloadParser.parseCoreRateLimit(payload.body)
                    if (rateLimit == null) {
                        errors += "GitHub 额度响应格式无效"
                    }
                } else {
                    errors += apiError("GitHub 额度查询失败", payload)
                }
            },
            onFailure = { error ->
                errors += networkError("GitHub 额度查询失败", error)
            }
        )

        return GithubAccountStatus(
            isLoading = false,
            tokenConfigured = configured,
            tokenValid = tokenValid,
            login = login,
            coreLimit = rateLimit?.limit,
            coreRemaining = rateLimit?.remaining,
            coreResetEpochSeconds = rateLimit?.resetEpochSeconds,
            error = errors.distinct().joinToString("\n").takeIf { it.isNotBlank() }
        )
    }

    private fun execute(url: String, token: String?): Result<HttpPayload> = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("Accept", GITHUB_ACCEPT)
            .header("User-Agent", USER_AGENT)
            .apply {
                if (!token.isNullOrBlank()) {
                    header("Authorization", "Bearer $token")
                }
            }
            .build()

        directHttpClient.newCall(request).execute().use { response ->
            HttpPayload(
                code = response.code,
                body = response.body.string()
            )
        }
    }

    private fun apiError(prefix: String, payload: HttpPayload): String {
        val message = GithubAccountPayloadParser.parseErrorMessage(payload.body)
        return buildString {
            append(prefix)
            append("（HTTP ")
            append(payload.code)
            append('）')
            if (!message.isNullOrBlank()) {
                append("：")
                append(message)
            }
        }
    }

    private fun networkError(prefix: String, error: Throwable): String {
        val detail = error.message?.trim().orEmpty()
        return if (detail.isBlank()) prefix else "$prefix：$detail"
    }

    private fun storedToken(): String = tokenStore.get("github_token").trim()

    private fun normalizeToken(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("Bearer ", ignoreCase = true) -> trimmed.substring(7).trim()
            trimmed.startsWith("token ", ignoreCase = true) -> trimmed.substring(6).trim()
            else -> trimmed
        }
    }
}

internal object GithubAccountPayloadParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseLogin(jsonText: String): String? {
        val root = parseObject(jsonText) ?: return null
        return (root["login"] as? JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun parseCoreRateLimit(jsonText: String): GithubCoreRateLimit? {
        val root = parseObject(jsonText) ?: return null
        val resources = root["resources"] as? JsonObject ?: return null
        val core = resources["core"] as? JsonObject ?: return null
        val limit = (core["limit"] as? JsonPrimitive)?.intOrNull ?: return null
        val remaining = (core["remaining"] as? JsonPrimitive)?.intOrNull ?: return null
        val reset = (core["reset"] as? JsonPrimitive)?.longOrNull ?: return null
        if (limit < 0 || remaining < 0 || reset < 0L) return null
        return GithubCoreRateLimit(
            limit = limit,
            remaining = remaining,
            resetEpochSeconds = reset
        )
    }

    fun parseErrorMessage(jsonText: String): String? {
        val root = parseObject(jsonText) ?: return null
        return (root["message"] as? JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun parseObject(jsonText: String): JsonObject? {
        return runCatching { json.parseToJsonElement(jsonText) }.getOrNull() as? JsonObject
    }
}
