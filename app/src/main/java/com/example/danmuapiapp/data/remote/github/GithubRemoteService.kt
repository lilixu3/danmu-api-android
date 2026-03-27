package com.example.danmuapiapp.data.remote.github

import com.example.danmuapiapp.data.service.CoreVersionParser
import com.example.danmuapiapp.data.service.GithubProxyService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GithubRemoteService @Inject constructor(
    httpClient: OkHttpClient,
    private val githubProxyService: GithubProxyService
) {
    companion object {
        const val UserAgent = "DanmuApiApp"
        private const val GithubAccept = "application/vnd.github+json"
    }

    data class ReleaseAsset(
        val name: String,
        val downloadUrl: String,
        val size: Long
    )

    data class ReleasePayload(
        val tagName: String,
        val name: String,
        val body: String,
        val publishedAt: String,
        val htmlUrl: String,
        val zipballUrl: String,
        val assets: List<ReleaseAsset> = emptyList()
    )

    data class TextResponsePayload(
        val finalUrl: String,
        val body: String
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val metadataHttpClient = httpClient.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    fun apiUrlCandidates(path: String): List<String> {
        return withProxyCandidates("https://api.github.com/$path")
    }

    fun rawUrlCandidates(repo: String, filePath: String): List<String> {
        return withProxyCandidates("https://raw.githubusercontent.com/$repo/$filePath")
    }

    fun withProxyCandidates(url: String): List<String> {
        return githubProxyService.buildUrlCandidates(url).plus(url).distinct()
    }

    fun requestText(urls: List<String>, headers: Map<String, String>): String? {
        return requestMapped(urls, headers) { body -> body.takeIf { it.isNotBlank() } }
    }

    fun requestTextResponse(urls: List<String>, headers: Map<String, String>): TextResponsePayload? {
        for (url in urls) {
            repeat(2) { attempt ->
                try {
                    val request = Request.Builder().url(url).apply {
                        headers.forEach { (key, value) -> header(key, value) }
                        githubProxyService.applyGithubAuth(this, url)
                    }.build()
                    metadataHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use
                        val body = response.body.string()
                        if (body.isBlank()) return@use
                        return TextResponsePayload(
                            finalUrl = response.request.url.toString(),
                            body = body
                        )
                    }
                } catch (_: Exception) {
                    if (attempt == 0) Thread.sleep(500)
                }
            }
        }
        return null
    }

    fun <T> requestMapped(
        urls: List<String>,
        headers: Map<String, String>,
        mapper: (String) -> T?
    ): T? {
        for (url in urls) {
            repeat(2) { attempt ->
                try {
                    val request = Request.Builder().url(url).apply {
                        headers.forEach { (key, value) -> header(key, value) }
                        githubProxyService.applyGithubAuth(this, url)
                    }.build()
                    metadataHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use
                        val body = response.body.string()
                        val mapped = mapper(body)
                        if (mapped != null) return mapped
                    }
                } catch (_: Exception) {
                    if (attempt == 0) Thread.sleep(500)
                }
            }
        }
        return null
    }

    fun fetchLatestRelease(repo: String): ReleasePayload? {
        return requestMapped(
            urls = apiUrlCandidates("repos/$repo/releases/latest"),
            headers = mapOf(
                "Accept" to GithubAccept,
                "User-Agent" to UserAgent
            )
        ) { body -> parseRelease(body) }
    }

    fun fetchReleaseList(repo: String, perPage: Int = 10): List<ReleasePayload> {
        return requestMapped(
            urls = apiUrlCandidates("repos/$repo/releases?per_page=$perPage"),
            headers = mapOf(
                "Accept" to GithubAccept,
                "User-Agent" to UserAgent
            )
        ) { body -> parseReleaseList(body).takeIf { it.isNotEmpty() } } ?: emptyList()
    }

    fun fetchVersionFromGlobals(repo: String): String? {
        val paths = listOf(
            "refs/heads/main/danmu_api/configs/globals.js",
            "refs/heads/main/danmu-api/configs/globals.js"
        )
        return paths.firstNotNullOfOrNull { path ->
            requestMapped(
                urls = rawUrlCandidates(repo, path),
                headers = mapOf("User-Agent" to UserAgent)
            ) { body ->
                CoreVersionParser.extractSourceVersion(body)?.trim()?.takeIf { it.isNotBlank() }
            }
        }
    }

    private fun parseReleaseList(jsonText: String): List<ReleasePayload> {
        val root = runCatching { json.parseToJsonElement(jsonText) }.getOrNull() as? JsonArray
            ?: return emptyList()
        return root.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            parseReleaseObject(obj)
        }
    }

    private fun parseRelease(jsonText: String): ReleasePayload? {
        val root = runCatching { json.parseToJsonElement(jsonText) }.getOrNull() as? JsonObject
            ?: return null
        return parseReleaseObject(root)
    }

    private fun parseReleaseObject(obj: JsonObject): ReleasePayload {
        val assets = (obj["assets"] as? JsonArray).orEmpty().mapNotNull { element ->
            val asset = element as? JsonObject ?: return@mapNotNull null
            val name = (asset["name"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            val downloadUrl = normalizeGithubUrl(
                (asset["browser_download_url"] as? JsonPrimitive)
                ?.contentOrNull
                ?.trim()
                .orEmpty()
            )
            if (name.isBlank() || downloadUrl.isBlank()) return@mapNotNull null
            ReleaseAsset(
                name = name,
                downloadUrl = downloadUrl,
                size = (asset["size"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L
            )
        }
        return ReleasePayload(
            tagName = (obj["tag_name"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            name = (obj["name"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            body = (obj["body"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            publishedAt = (obj["published_at"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            htmlUrl = normalizeGithubUrl((obj["html_url"] as? JsonPrimitive)?.contentOrNull.orEmpty()),
            zipballUrl = normalizeGithubUrl((obj["zipball_url"] as? JsonPrimitive)?.contentOrNull.orEmpty()),
            assets = assets
        )
    }

    private fun normalizeGithubUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""

        val directPrefixes = listOf(
            "https://github.com/",
            "https://api.github.com/",
            "https://raw.githubusercontent.com/",
            "https://uploads.github.com/"
        )
        directPrefixes.firstOrNull { trimmed.startsWith(it) }?.let { return trimmed }
        directPrefixes.firstOrNull { trimmed.contains(it) }?.let { prefix ->
            return trimmed.substring(trimmed.indexOf(prefix))
        }

        val barePrefixes = listOf(
            "github.com/",
            "api.github.com/",
            "raw.githubusercontent.com/",
            "uploads.github.com/"
        )
        barePrefixes.firstOrNull { trimmed.contains(it) }?.let { prefix ->
            return "https://${trimmed.substring(trimmed.indexOf(prefix))}"
        }

        return trimmed
    }
}
