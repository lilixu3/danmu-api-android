package com.example.danmuapiapp.data.network

import com.example.danmuapiapp.data.network.newOutboundCall

import android.content.Context
import com.example.danmuapiapp.data.repository.useCancellableResponse
import com.example.danmuapiapp.data.repository.runCatchingCancellable
import com.example.danmuapiapp.data.service.AppOutboundConnectivityResult
import com.example.danmuapiapp.data.service.GithubProxyService
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Actual HTTP checks, not DNS/TLS-only checks. Download follows the APK asset redirects. */
internal object GithubConnectivityProbe {
    val labels = linkedMapOf("github_api" to "更新接口", "github_raw" to "配置文件", "github_download" to "安装包下载")
    private const val RELEASE = "https://api.github.com/repos/lilixu3/danmu-api-android/releases/latest"
    private const val RAW = "https://raw.githubusercontent.com/lilixu3/danmu_api/refs/heads/main/danmu_api/configs/globals.js"

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (output.size() < limit) {
            val count = read(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    suspend fun test(context: Context, source: String): AppOutboundConnectivityResult =
        kotlinx.coroutines.withTimeoutOrNull(20_000) { testWithinBudget(context, source) }
            ?: AppOutboundConnectivityResult(source, connected = false)

    private suspend fun testWithinBudget(context: Context, source: String): AppOutboundConnectivityResult {
        require(source in labels)
        val start = System.nanoTime()
        val client = GithubOutboundNetwork.createClient(context).newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS).build()
        val proxy = GithubProxyService(context, client)
        suspend fun probe(url: String, metadata: Boolean): ByteArray? {
            val candidates = proxy.buildUrlCandidates(url).distinct()
            for (candidate in candidates) {
                currentCoroutineContext().ensureActive()
                val data = runCatchingCancellable {
                    val request = Request.Builder().url(candidate).header("User-Agent", "DanmuApiApp")
                    if (metadata) request.header("Accept", "application/vnd.github+json")
                    else request.header("Range", "bytes=0-1023")
                    proxy.applyGithubAuth(request, candidate)
                    client.newOutboundCall(request.build()).useCancellableResponse { response ->
                        if (!response.isSuccessful) return@useCancellableResponse null
                        val body = response.body
                        if (metadata) {
                            // Release JSON should stay small; never accidentally read an artifact.
                            body.byteStream().readNBytesCompat(1048576)
                        } else {
                            body.byteStream().readNBytesCompat(1024).takeIf { it.isNotEmpty() }
                        }
                    }
                }.getOrNull()
                if (data != null) return data
            }
            return null
        }
        val success = when (source) {
            "github_api" -> probe(RELEASE, true)?.let { bytes ->
                runCatching { JSONObject(bytes.toString(Charsets.UTF_8)).optString("tag_name").isNotBlank() }.getOrDefault(false)
            } ?: false
            "github_raw" -> probe(RAW, false)?.let { bytes ->
                val text = bytes.toString(Charsets.UTF_8).trimStart()
                text.isNotBlank() && !text.startsWith("<")
            } ?: false
            else -> {
                val release = probe(RELEASE, true)?.let { runCatching { JSONObject(it.toString(Charsets.UTF_8)) }.getOrNull() }
                val assets = release?.optJSONArray("assets")
                val candidates = (0 until (assets?.length() ?: 0)).mapNotNull { assets?.optJSONObject(it) }
                    .filter { it.optString("name").endsWith(".apk") }
                val asset = candidates.firstOrNull { "arm64" in it.optString("name") } ?: candidates.firstOrNull()
                val url = asset?.optString("browser_download_url").orEmpty()
                if (url.isEmpty() || !GithubOutboundNetwork.isTarget(java.net.URI(url))) false else probe(url, false)?.let { it.size >= 4 && it[0] == 0x50.toByte() && it[1] == 0x4b.toByte() } ?: false
            }
        }
        return AppOutboundConnectivityResult(source, success, ((System.nanoTime() - start) / 1_000_000).takeIf { success })
    }
}
