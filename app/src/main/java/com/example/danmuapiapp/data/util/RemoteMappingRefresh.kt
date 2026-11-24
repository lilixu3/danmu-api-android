package com.example.danmuapiapp.data.util

import com.example.danmuapiapp.data.network.newOutboundCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal enum class RemoteMapping(val key: String, private val endpoint: String) {
    Title("TITLE_MAPPING_TABLE_URL", "title-mapping"),
    Season("AUTO_MATCH_MAPPING_TABLE_URL", "auto-match-mapping");

    suspend fun refresh(client: OkHttpClient, port: Int, adminToken: String, savedUrl: String): Int =
        withContext(Dispatchers.IO) {
            check(adminToken.isNotBlank()) { "请先验证管理员密钥" }
            check(savedUrl.isNotBlank()) { "请先保存映射表地址" }
            val base = RuntimeApiUrls.local(port).toHttpUrl().newBuilder()
                .addPathSegment(adminToken).addPathSegment("api").build()
            fun request(path: String, post: Boolean = false): JSONObject {
                val builder = Request.Builder().url(base.newBuilder().addPathSegments(path).build())
                if (post) builder.post(ByteArray(0).toRequestBody())
                return client.newOutboundCall(builder.build()).execute().use { response ->
                    val result = runCatching { JSONObject(response.body.string()) }.getOrNull()
                    check(response.isSuccessful && result?.optBoolean("success", true) != false) {
                        when (response.code) {
                            401, 403 -> "核心拒绝了管理员凭据，请重新验证"
                            404 -> "当前核心不支持远程映射表更新"
                            else -> result?.optString("errorMessage")?.takeIf(String::isNotBlank)
                                ?: "远程映射表更新失败（HTTP ${response.code}）"
                        }
                    }
                    result ?: error("核心返回了无效的更新结果")
                }
            }
            // The phone editor writes .env; a running core may still be using the previous address.
            val active = request("config").optJSONObject("originalEnvVars")?.optString(key)
            check(active?.trim() == savedUrl.trim()) { "运行中的核心尚未加载这个地址，请刷新配置或重启服务后再试" }
            val result = request("$endpoint/refresh", post = true)
            check(result.optBoolean("success")) { "核心未确认更新成功" }
            result.optInt("count", -1).takeIf { it >= 0 } ?: error("核心未返回更新条数")
        }
}
