package com.example.danmuapiapp.desktop.node

import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/** GitHub 加速线路选项（与 Android GithubProxyService 的候选一致）。 */
data class GithubProxyOption(
    val id: String,
    val label: String,
    val baseUrl: String,
    val isOriginal: Boolean = false,
)

/**
 * GitHub 代理线路与测速（对齐 Android GithubProxyService / GithubProxySpeedTester）：
 * - 线路候选与 Android 完全一致（直连 + 4 个 GH-Proxy 镜像）；
 * - 测速目标用 raw 资源，避免代理站对 github.com 页面 403 造成误判；
 * - 并行测速；快测 2.5s 超时 + 单候选慢测兜底；
 * - 下载 URL 按多候选变换逐个尝试，兼容 {url} / %s / ?url= / 路径前缀四类代理。
 */
object GithubProxyCatalog {

    const val ID_ORIGINAL = "original"

    /** 与 Android 一致的测速目标（raw 资源）。 */
    private const val LATENCY_TARGET_URL =
        "https://raw.githubusercontent.com/lilixu3/danmu_api/refs/heads/main/danmu_api/configs/globals.js"

    val options: List<GithubProxyOption> = listOf(
        GithubProxyOption(ID_ORIGINAL, "GitHub 官方（直连）", "", isOriginal = true),
        GithubProxyOption("gh_proxy_org", "GH-Proxy.org", "https://gh-proxy.org"),
        GithubProxyOption("hk_gh_proxy", "HK GH-Proxy", "https://hk.gh-proxy.org"),
        GithubProxyOption("cdn_gh_proxy", "CDN GH-Proxy", "https://cdn.gh-proxy.org"),
        GithubProxyOption("edgeone_gh_proxy", "EdgeOne GH-Proxy", "https://edgeone.gh-proxy.org"),
    )

    fun optionById(id: String): GithubProxyOption {
        return options.firstOrNull { it.id == id } ?: options.first()
    }

    /** 与 Android buildProxyCandidates 相同的候选变换。 */
    fun buildProxyCandidates(proxyBase: String, originalUrl: String): List<String> {
        val base = proxyBase.trim().trimEnd('/')
        if (base.isBlank()) return listOf(originalUrl)
        val noSchemeUrl = originalUrl.removePrefix("https://").removePrefix("http://")
        val encodedUrl = urlEncode(originalUrl)
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

    /** 按所选线路生成下载候选 URL 列表（直连线路返回原 URL）。 */
    fun downloadCandidates(proxyId: String, originalUrl: String): List<String> {
        val option = optionById(proxyId)
        return if (option.isOriginal) {
            listOf(originalUrl)
        } else {
            buildProxyCandidates(option.baseUrl, originalUrl)
        }
    }

    /** 并行测速全部线路；失败返回 -1。供设置页展示与自动选择。 */
    fun testAllLatencies(timeoutMsPerProbe: Long = 3_500L): Map<String, Long> {
        val pool: ExecutorService = Executors.newFixedThreadPool(options.size)
        try {
            val futures: List<Future<Pair<String, Long>>> = options.map { option ->
                pool.submit(
                    Callable {
                        option.id to probeLatency(option, timeoutMsPerProbe)
                    },
                )
            }
            return futures.associate { it.get() }
        } finally {
            pool.shutdownNow()
        }
    }

    /** 单线路延迟：raw 资源快测 + 单候选慢测兜底；失败 -1（对齐 Android）。 */
    fun probeLatency(option: GithubProxyOption, timeoutMs: Long): Long {
        val candidates = if (option.isOriginal) {
            listOf(LATENCY_TARGET_URL)
        } else {
            buildProxyCandidates(option.baseUrl, LATENCY_TARGET_URL)
        }
        probeLatencyWithTimeout(candidates, timeoutMs)?.let { return it }
        return probeLatencyWithTimeout(candidates.take(1), timeoutMs + 3_500L) ?: -1L
    }

    private fun probeLatencyWithTimeout(candidates: List<String>, timeoutMs: Long): Long? {
        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(timeoutMs))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
        candidates.distinct().forEach { targetUrl ->
            val start = System.currentTimeMillis()
            val ok = runCatching {
                val response = client.send(
                    HttpRequest.newBuilder(URI.create(targetUrl))
                        .timeout(Duration.ofMillis(timeoutMs))
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.discarding(),
                )
                response.statusCode() in 200..399
            }.getOrDefault(false)
            if (ok) return System.currentTimeMillis() - start
        }
        return null
    }

    private fun urlEncode(value: String): String {
        // URLEncoder 是表单编码（空格→+），代理站惯例接受 %20；这里做保守替换
        return URLDecoder.decode(value, Charsets.UTF_8).let { _ ->
            java.net.URLEncoder.encode(value, Charsets.UTF_8)
        }
    }
}

/**
 * 带 GitHub 线路回退的核心下载器：按所选线路的候选 URL 逐个尝试，
 * 全部失败再抛出（对无法直连 GitHub 的用户必须可用）。
 */
object GithubFileDownloader {

    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    fun download(
        originalUrl: String,
        proxyId: String,
        targetFile: File,
        timeoutSeconds: Long = 150L,
        maxBytes: Long = 128L * 1024L * 1024L,
    ) {
        val candidates = GithubProxyCatalog.downloadCandidates(proxyId, originalUrl)
        var lastError: IOException? = null
        candidates.forEach { url ->
            try {
                val bytes = fetchBytes(url, timeoutSeconds, maxBytes)
                targetFile.parentFile?.mkdirs()
                val part = File(targetFile.parentFile, targetFile.name + ".part")
                part.writeBytes(bytes)
                if (!part.renameTo(targetFile)) {
                    part.copyTo(targetFile, overwrite = true)
                    part.delete()
                }
                return
            } catch (t: Throwable) {
                lastError = IOException("线路失败 $url: ${t.message}", t)
            }
        }
        throw lastError ?: IOException("核心下载失败（所有线路均不可用）: $originalUrl")
    }

    private fun fetchBytes(url: String, timeoutSeconds: Long, maxBytes: Long): ByteArray {
        val response = HttpClient.newHttpClient().sendAsync(
            HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofByteArray(),
        ).orTimeout(timeoutSeconds, TimeUnit.SECONDS).join()
        if (response.statusCode() != 200) {
            throw IOException("HTTP ${response.statusCode()}")
        }
        val bytes = response.body()
        if (bytes.isEmpty()) throw IOException("响应为空")
        if (bytes.size > maxBytes) throw IOException("超过大小上限")
        return bytes
    }
}
