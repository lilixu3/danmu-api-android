package com.example.danmuapiapp.data.service

import android.content.Context
import androidx.core.content.edit
import com.example.danmuapiapp.data.util.SecureStringStore
import com.example.danmuapiapp.data.util.safeGetBoolean
import com.example.danmuapiapp.data.util.safeGetString
import com.example.danmuapiapp.domain.model.GithubProxyOption
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GithubProxyService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient
) {
    companion object {
        private const val KEY_GITHUB_PROXY = "github_proxy"
        private const val KEY_SELECTED_PROXY_ID = "selected_proxy"
        private const val KEY_HAS_USER_SELECTED = "has_user_selected_proxy"
        private const val KEY_AUTO_SELECT = "auto_select"
        private const val PROXY_ID_ORIGINAL = "original"
        private const val FAST_LATENCY_CONNECT_TIMEOUT_MS = 2500L
        private const val FAST_LATENCY_READ_TIMEOUT_MS = 2500L
        private const val FAST_LATENCY_CALL_TIMEOUT_MS = 3500L
        private const val SLOW_LATENCY_CONNECT_TIMEOUT_MS = 5000L
        private const val SLOW_LATENCY_READ_TIMEOUT_MS = 5000L
        private const val SLOW_LATENCY_CALL_TIMEOUT_MS = 7000L
        private const val SLOW_FALLBACK_CANDIDATE_LIMIT = 1
    }

    private val prefs = context.getSharedPreferences("github_proxy_prefs", Context.MODE_PRIVATE)
    private val githubAuthPrefs = context.getSharedPreferences("github_auth_prefs", Context.MODE_PRIVATE)
    private val githubTokenStore = SecureStringStore(githubAuthPrefs, "danmuapi_github_auth_v1")
    private val allOptions = listOf(
        GithubProxyOption(PROXY_ID_ORIGINAL, "GitHub 官方（直连）", "", isOriginal = true),
        GithubProxyOption("gh_proxy_org", "GH-Proxy.org", "https://gh-proxy.org"),
        GithubProxyOption("hk_gh_proxy", "HK GH-Proxy", "https://hk.gh-proxy.org"),
        GithubProxyOption("cdn_gh_proxy", "CDN GH-Proxy", "https://cdn.gh-proxy.org"),
        GithubProxyOption("edgeone_gh_proxy", "EdgeOne GH-Proxy", "https://edgeone.gh-proxy.org")
    )

    private val _selectedProxyId = MutableStateFlow(resolveInitialSelectedId())
    val selectedProxyId: StateFlow<String> = _selectedProxyId.asStateFlow()

    private val _hasUserSelected = MutableStateFlow(resolveInitialHasUserSelected())
    val hasUserSelected: StateFlow<Boolean> = _hasUserSelected.asStateFlow()

    init {
        if (_hasUserSelected.value) {
            persistSelection(_selectedProxyId.value, markSelected = true)
        }
    }

    fun proxyOptions(): List<GithubProxyOption> = allOptions

    fun currentSelectedOption(): GithubProxyOption {
        val id = _selectedProxyId.value
        return allOptions.firstOrNull { it.id == id } ?: allOptions.first()
    }

    fun hasUserSelectedProxy(): Boolean = _hasUserSelected.value

    fun isUsingProxy(): Boolean = hasUserSelectedProxy() && !currentSelectedOption().isOriginal

    fun setSelectedProxy(proxyId: String) {
        persistSelection(proxyId, markSelected = true)
    }

    fun clearUserSelection() {
        prefs.edit {
            putBoolean(KEY_HAS_USER_SELECTED, false)
            putString(KEY_SELECTED_PROXY_ID, PROXY_ID_ORIGINAL)
            putBoolean(KEY_AUTO_SELECT, true)
            putString(KEY_GITHUB_PROXY, "")
        }
        _hasUserSelected.value = false
        _selectedProxyId.value = PROXY_ID_ORIGINAL
    }

    fun buildUrlCandidates(originalUrl: String): List<String> {
        val option = currentSelectedOption()
        if (!hasUserSelectedProxy() || option.isOriginal) return listOf(originalUrl)
        return buildProxyCandidates(option.baseUrl, originalUrl)
    }

    fun applyGithubAuth(requestBuilder: Request.Builder, requestUrl: String) {
        val token = authHeaderValue(requestUrl) ?: return
        requestBuilder.header("Authorization", token)
    }

    private fun authHeaderValue(requestUrl: String): String? {
        if (!isGithubApiRequest(requestUrl)) return null
        val raw = githubTokenStore.get("github_token").trim()
        if (raw.isBlank()) return null
        if (raw.startsWith("Bearer ", ignoreCase = true) ||
            raw.startsWith("token ", ignoreCase = true)
        ) {
            return raw
        }
        return "Bearer $raw"
    }

    private fun isGithubApiRequest(url: String): Boolean {
        val host = runCatching {
            URI(url).host?.lowercase(Locale.ROOT).orEmpty()
        }.getOrDefault("")
        return host == "api.github.com"
    }

    suspend fun testLatency(option: GithubProxyOption): Long = withContext(Dispatchers.IO) {
        // 仅使用 raw 资源测速，避免代理站对 github.com/release 页面返回 403
        // 时把仍可用于下载配置/核心资源的线路整体误判为不可用。
        val targetUrl =
            "https://raw.githubusercontent.com/lilixu3/danmu_api/refs/heads/main/danmu_api/configs/globals.js"
        val candidates = if (option.isOriginal) {
            listOf(targetUrl)
        } else {
            buildProxyCandidates(option.baseUrl, targetUrl)
        }

        val fastLatency = probeLatency(buildLatencyClient(fast = true), candidates)
        if (fastLatency >= 0L) return@withContext fastLatency

        // 慢测只作为兜底，并限制候选数量，避免黑洞代理把整轮测速拖得过久。
        probeLatency(
            buildLatencyClient(fast = false),
            candidates.distinct().take(SLOW_FALLBACK_CANDIDATE_LIMIT)
        )
    }

    private fun buildLatencyClient(fast: Boolean): OkHttpClient {
        return httpClient.newBuilder()
            .connectTimeout(
                if (fast) FAST_LATENCY_CONNECT_TIMEOUT_MS else SLOW_LATENCY_CONNECT_TIMEOUT_MS,
                TimeUnit.MILLISECONDS
            )
            .readTimeout(
                if (fast) FAST_LATENCY_READ_TIMEOUT_MS else SLOW_LATENCY_READ_TIMEOUT_MS,
                TimeUnit.MILLISECONDS
            )
            .callTimeout(
                if (fast) FAST_LATENCY_CALL_TIMEOUT_MS else SLOW_LATENCY_CALL_TIMEOUT_MS,
                TimeUnit.MILLISECONDS
            )
            .build()
    }

    private fun probeLatency(client: OkHttpClient, candidates: List<String>): Long {
        candidates.distinct().forEach { targetUrl ->
            val start = System.currentTimeMillis()
            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "DanmuApiApp")
                .build()

            val latency = runCatching {
                client.newCall(request).execute().use { response ->
                    if (response.code !in 200..399) return@use -1L
                    runCatching { response.body.byteStream().use { it.read() } }
                    (System.currentTimeMillis() - start).coerceAtLeast(1L)
                }
            }.getOrDefault(-1L)
            if (latency >= 0L) return latency
        }
        return -1L
    }

    private fun resolveInitialSelectedId(): String {
        val saved = prefs.safeGetString(KEY_SELECTED_PROXY_ID).trim()
        if (saved.isNotBlank() && allOptions.any { it.id == saved }) return saved

        val legacyProxy = normalizeProxy(prefs.safeGetString(KEY_GITHUB_PROXY))
        if (legacyProxy.isBlank()) return PROXY_ID_ORIGINAL
        return allOptions.firstOrNull { normalizeProxy(it.baseUrl) == legacyProxy }?.id ?: PROXY_ID_ORIGINAL
    }

    private fun resolveInitialHasUserSelected(): Boolean {
        if (prefs.contains(KEY_HAS_USER_SELECTED)) {
            return prefs.safeGetBoolean(KEY_HAS_USER_SELECTED, false)
        }
        val selectedId = prefs.safeGetString(KEY_SELECTED_PROXY_ID).trim()
        val auto = prefs.safeGetBoolean(KEY_AUTO_SELECT, true)
        if (selectedId.isNotBlank() && selectedId != PROXY_ID_ORIGINAL) return true
        if (!auto && selectedId.isNotBlank()) return true
        return prefs.safeGetString(KEY_GITHUB_PROXY).trim().isNotBlank()
    }

    private fun persistSelection(proxyId: String, markSelected: Boolean) {
        val option = allOptions.firstOrNull { it.id == proxyId } ?: allOptions.first()
        prefs.edit {
            putString(KEY_SELECTED_PROXY_ID, option.id)
            putBoolean(KEY_HAS_USER_SELECTED, markSelected)
            putBoolean(KEY_AUTO_SELECT, !markSelected)
            putString(KEY_GITHUB_PROXY, if (option.isOriginal) "" else option.baseUrl)
        }

        _selectedProxyId.value = option.id
        _hasUserSelected.value = markSelected
    }

    private fun normalizeProxy(rawProxy: String): String {
        val trimmed = rawProxy.trim().trimEnd('/')
        if (trimmed.isBlank()) return ""
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    private fun buildProxyCandidates(proxyBase: String, originalUrl: String): List<String> {
        val base = normalizeProxy(proxyBase)
        if (base.isBlank()) return listOf(originalUrl)

        val noSchemeUrl = originalUrl.removePrefix("https://").removePrefix("http://")
        val encodedUrl = URLEncoder.encode(originalUrl, "UTF-8")

        return buildList {
            if (base.contains("{url}")) {
                add(base.replace("{url}", originalUrl))
            } else if (base.contains("%s")) {
                add(runCatching { String.format(base, originalUrl) }.getOrDefault("$base/$originalUrl"))
            }
            if (base.endsWith("=") || base.contains("url=")) {
                add("$base$encodedUrl")
            }
            add("$base/$originalUrl")
            add("$base/$noSchemeUrl")
        }.distinct()
    }
}
