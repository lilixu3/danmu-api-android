package com.example.danmuapiapp.data.network

import com.example.danmuapiapp.data.service.GithubApiQuota
import com.example.danmuapiapp.data.service.GithubQuotaExhaustedException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

internal data class GithubQuotaRequestMode(val automatic: Boolean)

internal fun githubApiQuotaResource(url: HttpUrl): String? {
    val apiUrl = if (url.host == "api.github.com") url else {
        val path = url.encodedPath.removePrefix("/")
        val target = if (path.startsWith("api.github.com/")) "https://$path" else path
        target.toHttpUrlOrNull()?.takeIf { it.host == "api.github.com" }
    } ?: return null
    if (apiUrl.scheme != "https" || apiUrl.encodedPath.trimEnd('/') == "/rate_limit") return null
    return if (apiUrl.encodedPath.startsWith("/search/")) "search" else "core"
}

internal class GithubQuotaInterceptor(
    private val requireAvailable: (String, Boolean) -> Unit,
    private val observe: (String, Int, Long?) -> Unit
) : Interceptor {
    constructor(quota: GithubApiQuota) : this(quota::requireAvailable, quota::observe)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val resource = githubApiQuotaResource(request.url)
        val anonymous = request.header("Authorization").isNullOrBlank()
        val notify = request.tag(GithubQuotaRequestMode::class.java)?.automatic != true
        if (anonymous && resource != null) requireAvailable(resource, notify)
        val response = chain.proceed(request)
        var hop: Response? = response
        while (anonymous && resource != null && hop != null) {
            if (hop.request.url.host == "api.github.com" && hop.request.header("Authorization").isNullOrBlank()) {
                val responseResource = hop.header("X-RateLimit-Resource") ?: resource
                val remaining = hop.header("X-RateLimit-Remaining")?.toIntOrNull()
                if (remaining != null) {
                    observe(responseResource, remaining, hop.header("X-RateLimit-Reset")?.toLongOrNull())
                    if (hop === response && remaining == 0 && response.code in setOf(403, 429)) {
                        try {
                            requireAvailable(responseResource, notify)
                        } catch (exhausted: GithubQuotaExhaustedException) {
                            response.close()
                            throw exhausted
                        }
                    }
                }
            }
            hop = hop.priorResponse
        }
        return response
    }
}
