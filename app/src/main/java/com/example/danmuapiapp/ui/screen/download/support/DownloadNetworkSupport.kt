package com.example.danmuapiapp.ui.screen.download.support

import com.example.danmuapiapp.data.network.newOutboundCall
import com.example.danmuapiapp.data.util.RuntimeTokenNormalizer
import com.example.danmuapiapp.ui.screen.download.ensureHttpPrefix
import java.net.URI
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 弹幕源基础地址：补全协议、去掉尾部斜杠，并保证运行 token 只出现在路径里一次。
 */
internal fun resolveDownloadApiBaseUrl(sourceBase: String, runtimeToken: String): String? {
    val raw = sourceBase.trim()
    if (raw.isBlank()) return null
    val normalized = ensureHttpPrefix(raw).trimEnd('/')
    val token = RuntimeTokenNormalizer.normalizeInput(runtimeToken).trim('/')
    if (token.isBlank()) return normalized

    val uri = runCatching { URI(normalized) }.getOrNull() ?: return normalized
    val segments = uri.path
        ?.split('/')
        ?.filter { it.isNotBlank() }
        .orEmpty()
    if (segments.firstOrNull() == token) {
        return normalized
    }
    return "$normalized/$token"
}

/** 带出站统计的 GET 请求：返回 (HTTP 状态码, 响应体)。 */
internal fun downloadHttpGet(client: OkHttpClient, url: String): Result<Pair<Int, String>> {
    return runCatching {
        val request = Request.Builder().url(url).get().build()
        client.newOutboundCall(request).execute().use { response ->
            response.code to response.body.string()
        }
    }
}
