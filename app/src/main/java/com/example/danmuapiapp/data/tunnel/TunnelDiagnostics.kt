package com.example.danmuapiapp.data.tunnel

import com.example.danmuapiapp.data.repository.runCatchingCancellable
import com.example.danmuapiapp.data.repository.useCancellableResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.Proxy
import java.util.concurrent.TimeUnit

internal data class TunnelProbe(
    val label: String,
    val code: Int = 0,
    val elapsedMs: Long = 0,
    val error: String = "",
    val pid: Long = 0,
    val runtimeIdentity: String = "",
    val bytesRead: Int = 0,
    val headerElapsedMs: Long = -1,
    val expectedBytes: Long = -1
) {
    val ok: Boolean get() = code in 200..299 && error.isBlank()
    fun display(): String = buildString {
        append("$label：")
        if (code > 0) append("HTTP $code")
        if (error.isNotBlank()) {
            if (code > 0) append(" · 正文")
            append(error)
        }
        append(" · ${elapsedMs} ms")
        if (headerElapsedMs >= 0) append(" · 响应头 ${headerElapsedMs} ms")
        if (bytesRead > 0 || expectedBytes >= 0) {
            append(" · ${(bytesRead + 1023) / 1024}")
            if (expectedBytes >= 0) append("/${(expectedBytes + 1023) / 1024}")
            append(" KB")
        }
    }
}

internal data class TunnelDiagnosticReport(val summary: String, val probes: List<TunnelProbe>) {
    fun text(): String = (listOf(summary) + probes.map { it.display() }).joinToString("\n")
}

internal fun sameTunnelRuntime(local: TunnelProbe, remote: TunnelProbe): Boolean =
    local.ok && remote.ok && local.pid > 0 && local.pid == remote.pid &&
        local.runtimeIdentity.isNotBlank() && local.runtimeIdentity != "null" &&
        local.runtimeIdentity == remote.runtimeIdentity

internal fun publicPageImproved(before: TunnelProbe, after: TunnelProbe): Boolean =
    after.ok && ((!before.ok && before.error == "请求超时") ||
        (before.ok && before.elapsedMs >= 2000L && after.elapsedMs < before.elapsedMs / 2))

/** Explicit user action only: no background public requests or automatic restarts. */
internal class TunnelDiagnostics(client: OkHttpClient) {
    private val direct = client.newBuilder().proxy(Proxy.NO_PROXY)
        .followRedirects(false).followSslRedirects(false)
        .callTimeout(5, TimeUnit.SECONDS).connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS).build()

    suspend fun check(port: Int, token: String, publicBase: String): TunnelDiagnosticReport = coroutineScope {
        val local = "http://127.0.0.1:$port".toHttpUrlOrNull()!!
        val public = (if (publicBase.contains("://")) publicBase else "http://$publicBase").toHttpUrlOrNull()
        if (public == null || public.username.isNotBlank() || public.password.isNotBlank()) {
            return@coroutineScope TunnelDiagnosticReport("公网地址无效，请检查连接设置。", emptyList())
        }
        val localHealth = async { probe("本机入口", endpoint(local, "__health"), health = true) }
        val publicHealth = async { probe("公网入口", endpoint(public, "__health"), health = true) }
        val localResult = localHealth.await()
        val remoteResult = publicHealth.await()
        val health = listOf(localResult, remoteResult)
        if (!localResult.ok) return@coroutineScope TunnelDiagnosticReport("本机服务入口异常，请先检查弹幕服务。", health)
        if (!remoteResult.ok) return@coroutineScope TunnelDiagnosticReport("本机入口正常，公网转发异常；请保留结果和穿透日志。", health)
        if (!sameTunnelRuntime(localResult, remoteResult)) {
            return@coroutineScope TunnelDiagnosticReport("公网入口指向另一服务实例，请检查同名隧道或端口占用。", health)
        }
        // Confirm the runtime first, then measure public transfers before warming local pages.
        val publicCore = async { probe("公网核心", endpoint(public, token.trim('/'), "api", "config")) }
        val publicPage = async { probe("公网页面（访问本机前）", endpoint(public, token.trim('/')), page = true) }
        val remoteCoreResult = publicCore.await()
        val remotePageBefore = publicPage.await()
        val localCore = async { probe("本机核心", endpoint(local, token.trim('/'), "api", "config")) }
        val localPage = async { probe("本机页面", endpoint(local, token.trim('/')), page = true) }
        val localCoreResult = localCore.await()
        val localPageResult = localPage.await()
        val remotePageAfter = probe("公网页面（访问本机后）", endpoint(public, token.trim('/')), page = true)
        val probes = health + listOf(remoteCoreResult, remotePageBefore, localCoreResult, localPageResult, remotePageAfter)
        val summary = when {
            listOf(localCoreResult, remoteCoreResult).any { it.code == 401 || it.code == 403 } ->
                "服务鉴权失败，请检查当前 Token 配置。"
            !localCoreResult.ok -> "本机核心响应异常，请保留结果和核心日志。"
            !localPageResult.ok -> "本机页面响应异常，请保留结果和核心日志。"
            publicPageImproved(remotePageBefore, remotePageAfter) ->
                "检测期间公网传输恢复或明显变快；已记录访问本机前后的差异，尚不能确定恢复原因。"
            !remoteCoreResult.ok -> "本机核心正常，公网访问核心异常；请保留结果和穿透日志。"
            !remotePageBefore.ok || !remotePageAfter.ok ->
                "本机页面正常，公网页面传输异常；请保留结果和穿透日志。"
            probes.any { it.elapsedMs >= 2000L } -> "访问可用但响应较慢，请保留本次结果与运行日志。"
            else -> "本机与公网访问均正常，公网入口指向当前服务。"
        }
        TunnelDiagnosticReport(summary, probes)
    }

    private fun endpoint(base: HttpUrl, vararg segments: String): HttpUrl = base.newBuilder()
        .query(null).fragment(null).apply { segments.filter { it.isNotBlank() }.forEach { addPathSegment(it) } }.build()

    private suspend fun probe(label: String, url: HttpUrl, health: Boolean = false, page: Boolean = false): TunnelProbe = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        fun elapsed() = (System.nanoTime() - started) / 1_000_000L
        var responseCode = 0
        var receivedBytes = 0
        var headerElapsedMs = -1L
        var expectedBytes = -1L
        fun result(code: Int = responseCode, error: String = "", pid: Long = 0, runtimeIdentity: String = "") =
            TunnelProbe(label, code, elapsed(), error, pid, runtimeIdentity, receivedBytes, headerElapsedMs, expectedBytes)
        runCatchingCancellable {
            direct.newCall(Request.Builder().url(url).header("Cache-Control", "no-cache").build()).useCancellableResponse { response ->
                responseCode = response.code
                headerElapsedMs = elapsed()
                expectedBytes = response.body.contentLength()
                val limit = if (page) 1024 * 1024 else 64 * 1024
                val body = response.body.byteStream().use { input ->
                    val bytes = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (bytes.size() <= limit) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        bytes.write(buffer, 0, count)
                        receivedBytes = bytes.size()
                    }
                    check(bytes.size() <= limit)
                    bytes.toByteArray()
                }
                if (!response.isSuccessful) result()
                else if (page) {
                    check(body.toString(Charsets.UTF_8).contains("</html>", ignoreCase = true))
                    result()
                } else {
                    val json = JSONObject(body.toString(Charsets.UTF_8))
                    if (health) {
                        check(json.optBoolean("ok") && json.optLong("pid") > 0)
                        result(pid = json.getLong("pid"), runtimeIdentity = json.optString("runtimeIdentity"))
                    } else result()
                }
            }
        }.getOrElse { error ->
            val detail = when (error) {
                is java.net.SocketTimeoutException, is java.io.InterruptedIOException -> "请求超时"
                is java.net.UnknownHostException -> "域名解析失败"
                is java.net.ConnectException -> "连接失败"
                is javax.net.ssl.SSLException -> "TLS 连接失败"
                else -> "响应异常"
            }
            // Exceptions can contain URLs/tokens; only persist this fixed classification.
            result(error = detail)
        }
    }
}
