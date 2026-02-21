package com.example.danmuapiapp.data.service

import android.content.Context
import com.example.danmuapiapp.data.util.SecureStringStore
import com.example.danmuapiapp.data.util.safeGetString
import com.example.danmuapiapp.domain.model.WebDavConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.util.Base64
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDavService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient
) {
    private data class HttpResult(
        val code: Int,
        val body: String,
        val error: Throwable? = null
    )

    private data class FolderEnsureResult(
        val created: Boolean,
        val message: String,
        val error: Throwable? = null
    )

    companion object {
        private const val PREFS_NAME = "webdav_config"
        private const val KEY_URL = "url"
        private const val KEY_USER = "user"
        private const val KEY_PASS = "pass"
        private const val KEY_PATH = "path"
        private const val DEFAULT_FOLDER = "DanmuApi"
        private const val USER_AGENT = "DanmuApiApp/Android"
        private val TEXT_PLAIN = "text/plain; charset=utf-8".toMediaType()
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secureStore = SecureStringStore(prefs, "danmuapi_webdav_sensitive_v1")

    fun loadConfig(): WebDavConfig {
        return WebDavConfig(
            url = prefs.safeGetString(KEY_URL),
            username = prefs.safeGetString(KEY_USER),
            password = secureStore.get(KEY_PASS),
            folderPath = prefs.safeGetString(KEY_PATH)
        )
    }

    fun saveConfig(config: WebDavConfig) {
        prefs.edit()
            .putString(KEY_URL, config.url.trim())
            .putString(KEY_USER, config.username.trim())
            .putString(KEY_PATH, config.folderPath.trim())
            .apply()
        secureStore.put(KEY_PASS, config.password)
    }

    fun isConfigured(config: WebDavConfig = loadConfig()): Boolean {
        return config.url.trim().isNotBlank() &&
            config.username.trim().isNotBlank() &&
            config.password.isNotBlank()
    }

    suspend fun backupEnv(content: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val config = loadConfig()
            if (!isConfigured(config)) {
                return@withContext Result.failure(IllegalStateException("请先完成 WebDAV 配置"))
            }

            val baseUrls = buildServerCandidates(config.url)
            if (baseUrls.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("WebDAV 地址无效，请检查后重试"))
            }
            val folder = resolveFolder(config.folderPath)
            val authHeader = authHeader(config.username, config.password)

            var lastErrorMessage = "未知错误"
            for (baseUrl in baseUrls) {
                val folderResult = ensureFolder(baseUrl, folder, authHeader)
                if (!folderResult.created) {
                    lastErrorMessage = "创建目录失败：${friendlyMessage(folderResult.message, folderResult.error)}"
                    continue
                }

                val fileUrl = "$baseUrl/${encodePath(folder)}/.env"
                val uploadResult = executeRequest(
                    method = "PUT",
                    url = fileUrl,
                    authHeader = authHeader,
                    content = content
                )
                if (uploadResult.code in 200..299 || uploadResult.code == 204) {
                    return@withContext Result.success(displayPath(folder))
                }

                val detail = uploadResult.body.take(120).ifBlank { "无响应详情" }
                lastErrorMessage = "上传失败（HTTP ${uploadResult.code}）：${friendlyMessage(detail, uploadResult.error)}"
            }

            Result.failure(IOException(lastErrorMessage))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreEnv(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val config = loadConfig()
            if (!isConfigured(config)) {
                return@withContext Result.failure(IllegalStateException("请先完成 WebDAV 配置"))
            }

            val baseUrls = buildServerCandidates(config.url)
            if (baseUrls.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("WebDAV 地址无效，请检查后重试"))
            }
            val folder = resolveFolder(config.folderPath)
            val authHeader = authHeader(config.username, config.password)

            var lastErrorMessage = "未知错误"
            for (baseUrl in baseUrls) {
                val fileUrl = "$baseUrl/${encodePath(folder)}/.env"
                val downloadResult = executeRequest(
                    method = "GET",
                    url = fileUrl,
                    authHeader = authHeader
                )
                if (downloadResult.code in 200..299) {
                    return@withContext Result.success(downloadResult.body)
                }

                val detail = downloadResult.body.take(120).ifBlank { "无响应详情" }
                lastErrorMessage = "下载失败（HTTP ${downloadResult.code}）：${friendlyMessage(detail, downloadResult.error)}"
            }

            Result.failure(IOException(lastErrorMessage))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun displayRemoteEnvPath(): String {
        val folder = resolveFolder(loadConfig().folderPath)
        return displayPath(folder)
    }

    private fun executeRequest(
        method: String,
        url: String,
        authHeader: String,
        content: String? = null
    ): HttpResult {
        return runCatching {
            val builder = Request.Builder()
                .url(url)
                .header("Authorization", authHeader)
                .header("User-Agent", USER_AGENT)
                .header("Connection", "close")

            when (method) {
                "GET" -> builder.get()
                "PUT" -> builder.method(method, (content ?: "").toRequestBody(TEXT_PLAIN))
                else -> builder.method(method, null)
            }

            httpClient.newCall(builder.build()).execute().use { response ->
                HttpResult(
                    code = response.code,
                    body = response.body.string()
                )
            }
        }.getOrElse { throwable ->
            HttpResult(
                code = 0,
                body = throwable.message ?: "网络请求失败",
                error = throwable
            )
        }
    }

    private fun ensureFolder(baseUrl: String, folder: String, authHeader: String): FolderEnsureResult {
        val parts = folder.trim().trim('/').split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) return FolderEnsureResult(true, "OK")

        val built = mutableListOf<String>()
        for (part in parts) {
            built += encodeSegment(part)
            val currentUrl = "$baseUrl/${built.joinToString("/")}/"
            val result = executeRequest(
                method = "MKCOL",
                url = currentUrl,
                authHeader = authHeader
            )
            if (result.code in 200..299 || result.code == 405 || result.code == 207 || result.code in 300..399) {
                continue
            }
            val detail = result.body.take(120).ifBlank { "无响应详情" }
            return FolderEnsureResult(false, "HTTP ${result.code}: $detail", result.error)
        }
        return FolderEnsureResult(true, "OK")
    }

    private fun buildServerCandidates(raw: String): List<String> {
        val normalized = normalizeServerUrl(raw)
        if (normalized.isBlank()) return emptyList()

        val candidates = linkedSetOf(normalized)
        runCatching {
            val uri = URI(normalized)
            val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
            if (host == "dav.jianguoyun.com") {
                val altUri = URI(
                    uri.scheme,
                    uri.userInfo,
                    "appcu.jianguoyun.com",
                    uri.port,
                    uri.path,
                    null,
                    null
                ).toString().trimEnd('/')
                candidates += altUri
            }
        }
        return candidates.toList()
    }

    private fun normalizeServerUrl(raw: String): String {
        var clean = raw.trim()
            .trim('"', '\'', '“', '”', '‘', '’')
            .trim()
        if (clean.isBlank()) return ""
        if (!clean.contains("://")) {
            clean = "https://$clean"
        }

        return runCatching {
            val uri = URI(clean)
            val host = uri.host?.trim().orEmpty()
            if (host.isBlank()) return@runCatching clean.trimEnd('/')

            var scheme = uri.scheme?.lowercase(Locale.ROOT).orEmpty().ifBlank { "https" }
            var path = (uri.path ?: "").trim().trimEnd('/')
            if (host.contains("jianguoyun.com", ignoreCase = true)) {
                scheme = "https"
                if (path.isBlank()) {
                    path = "/dav"
                } else if (!path.startsWith("/dav")) {
                    path = "/dav$path"
                }
            }
            URI(
                scheme,
                uri.userInfo,
                host,
                uri.port,
                if (path.isBlank()) null else path,
                null,
                null
            ).toString().trimEnd('/')
        }.getOrElse {
            clean.trimEnd('/')
        }
    }

    private fun friendlyMessage(rawMessage: String, error: Throwable?): String {
        val source = buildString {
            append(rawMessage)
            if (error?.message?.isNotBlank() == true && !rawMessage.contains(error.message ?: "")) {
                append(" ")
                append(error.message)
            }
        }.ifBlank { "网络请求失败" }
        val lower = source.lowercase(Locale.ROOT)
        if (lower.contains("unable to resolve host") || lower.contains("no address associated with hostname")) {
            return "DNS 解析失败，请检查网络、私有 DNS 或 VPN；坚果云可尝试地址 https://appcu.jianguoyun.com/dav"
        }
        return source
    }

    private fun resolveFolder(raw: String): String {
        return raw.trim().trim('/').ifBlank { DEFAULT_FOLDER }
    }

    private fun authHeader(username: String, password: String): String {
        val token = Base64.getEncoder().encodeToString("$username:$password".toByteArray(Charsets.UTF_8))
        return "Basic $token"
    }

    private fun encodePath(path: String): String {
        return path.trim().trim('/').split('/').filter { it.isNotBlank() }
            .joinToString("/") { encodeSegment(it) }
    }

    private fun encodeSegment(segment: String): String {
        return URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
    }

    private fun displayPath(folder: String): String {
        return "/${folder.trim().trim('/')}/.env"
    }
}
