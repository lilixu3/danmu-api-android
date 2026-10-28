package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.data.network.newOutboundCall

import android.content.Context
import android.net.Uri
import com.example.danmuapiapp.data.service.AppDiagnosticLogger
import com.example.danmuapiapp.data.util.RuntimeApiAccess
import com.example.danmuapiapp.data.util.RuntimeApiUrls
import com.example.danmuapiapp.data.util.RuntimeManagementPaths
import com.example.danmuapiapp.data.util.RuntimeTokenNormalizer
import com.example.danmuapiapp.data.util.applyRuntimeApiAuth
import com.example.danmuapiapp.domain.model.LocalDanmuUploadRequest
import com.example.danmuapiapp.domain.repository.AdminSessionRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import org.json.JSONObject

internal enum class LocalDanmuErrorKind {
    Unsupported,
    Unauthorized,
    Forbidden,
    NotFound,
    /** 目标 resourceKey 已存在（核心返回 409），需要用户改集数或文件名。 */
    Conflict,
    FileTooLarge,
    InvalidRequest,
    Busy,
    Network,
    Server
}

internal class LocalDanmuApiException(
    val kind: LocalDanmuErrorKind,
    override val message: String,
    val httpCode: Int? = null
) : Exception(message)

/**
 * 本地弹幕核心接口的 HTTP 传输层：URL 拼装、token 路径候选、幂等 GET 重试、
 * 响应读取与 HTTP→[LocalDanmuErrorKind] 映射。
 *
 * 只做传输与错误归类，业务编排（刷新、上传、删除、元数据修改）留在 [LocalDanmuRepositoryImpl]。
 */
internal class LocalDanmuHttpTransport(
    private val context: Context,
    baseClient: OkHttpClient,
    private val adminSessionRepository: AdminSessionRepository,
    private val runtimeRepository: RuntimeRepository
) {

    companion object {
        // 核心（Node）空闲 keep-alive 只有 5 秒，而 OkHttp 默认把空闲连接留 5 分钟，
        // 复用这类“服务端已关闭”的连接会一直等不到响应（读超时且 OkHttp 不会重试）。
        private const val LOCAL_GET_ATTEMPTS = 3
        private const val LOCAL_GET_RETRY_DELAY_MS = 350L
    }

    /** 本地核心 API 专用连接池：不保留空闲连接，每次请求都新建连接，避免复用死连接。 */
    private val localApiConnectionPool = okhttp3.ConnectionPool(0, 1, TimeUnit.NANOSECONDS)
    private val localHttpClient = baseClient.newBuilder()
        .connectionPool(localApiConnectionPool)
        .retryOnConnectionFailure(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .callTimeout(10, TimeUnit.MINUTES)
        .build()
    private val requestHttpClient = baseClient.newBuilder()
        .connectionPool(localApiConnectionPool)
        .retryOnConnectionFailure(false)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    fun tokenPaths(access: RuntimeApiAccess): List<String> {
        val adminState = adminSessionRepository.sessionState.value
        return RuntimeManagementPaths.tokenPaths(
            runtimeTokenPaths = access.tokenPaths,
            adminMode = adminState.isAdminMode,
            adminToken = adminSessionRepository.currentAdminTokenOrNull()
        )
    }

    fun buildUrl(
        access: RuntimeApiAccess,
        tokenPath: String,
        pathSegments: List<String>
    ): HttpUrl {
        val builder = RuntimeApiUrls.local(access.port).toHttpUrl().newBuilder()
        val token = tokenPath.trim('/')
        if (token.isNotBlank()) builder.addPathSegment(token)
        pathSegments.forEach { segment -> builder.addPathSegment(segment) }
        return builder.build()
    }

    suspend fun executeGet(
        access: RuntimeApiAccess,
        tokenPath: String,
        pathSegments: List<String>
    ): Pair<Int, String> {
        val request = Request.Builder()
            .url(buildUrl(access, tokenPath, pathSegments))
            .applyRuntimeApiAuth(access)
            .get()
            .build()
        return executeGetWithRetry(request)
    }

    /**
     * 幂等 GET 的重试：本地请求偶发失败（连接被回收、进程被系统冻结等）时重试几次，
     * 避免一次失败就直接把界面判成「核心忙/未启动」。
     */
    suspend fun executeGetWithRetry(request: Request): Pair<Int, String> {
        var attempt = 0
        while (true) {
            attempt++
            val path = maskTokenInPath(request.url.encodedPath)
            try {
                val result = execute(request)
                // 只在重试时记录成功，避免每次刷新都写一条 Info 日志。
                if (attempt > 1) {
                    AppDiagnosticLogger.i(
                        context,
                        "LocalDanmu",
                        "GET $path 第${attempt}次重试成功: HTTP ${result.first}，长度 ${result.second.length}"
                    )
                }
                return result
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: LocalDanmuApiException) {
                AppDiagnosticLogger.w(
                    context,
                    "LocalDanmu",
                    "GET $path 第${attempt}次失败: kind=${error.kind} http=${error.httpCode} 消息=${error.message}"
                )
                if (!shouldRetryLocalDanmuRequest(error.kind, attempt, LOCAL_GET_ATTEMPTS)) {
                    if (attempt > 1 && error.kind == LocalDanmuErrorKind.Network) {
                        throw LocalDanmuApiException(
                            LocalDanmuErrorKind.Network,
                            "本地接口请求失败（已重试 ${attempt - 1} 次）：${error.message.orEmpty()}",
                            error.httpCode
                        )
                    }
                    throw error
                }
                kotlinx.coroutines.delay(LOCAL_GET_RETRY_DELAY_MS * attempt)
            }
        }
    }

    /** 日志里不打印完整 token，只保留末两位便于比对。 */
    fun maskTokenInPath(path: String): String {
        val token = RuntimeTokenNormalizer.normalizeInput(runtimeStateToken()).trim('/')
        if (token.isBlank()) return path
        val encoded = java.net.URLEncoder.encode(token, "UTF-8")
        return when {
            path.contains(token) -> path.replace(token, "***${token.takeLast(2)}")
            path.contains(encoded) -> path.replace(encoded, "***${token.takeLast(2)}")
            else -> path
        }
    }

    fun runtimeStateToken(): String {
        val state = runtimeRepository.runtimeState.value
        return RuntimeTokenNormalizer.normalizeInput(state.token)
    }

    suspend fun executeDelete(
        access: RuntimeApiAccess,
        tokenPath: String,
        pathSegments: List<String>
    ): Pair<Int, String> {
        val request = Request.Builder()
            .url(buildUrl(access, tokenPath, pathSegments))
            .applyRuntimeApiAuth(access)
            .delete()
            .build()
        return execute(request)
    }

    suspend fun executePatch(
        access: RuntimeApiAccess,
        tokenPath: String,
        resourceKey: String,
        jsonBody: String
    ): Pair<Int, String> {
        val request = Request.Builder()
            .url(buildUrl(access, tokenPath, listOf("api", "v2", "local-danmu", resourceKey)))
            .applyRuntimeApiAuth(access)
            .patch(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .build()
        return execute(request)
    }

    suspend fun executeUpload(
        access: RuntimeApiAccess,
        tokenPath: String,
        upload: LocalDanmuUploadRequest,
        onProgress: (Float) -> Unit
    ): Pair<Int, String> {
        val mediaType = upload.mimeType
            .substringBefore(';')
            .trim()
            .takeIf { it.isNotBlank() }
            ?.toMediaTypeOrNull()
            ?: "application/octet-stream".toMediaTypeOrNull()
        val fileBody = upload.stagedFile?.takeIf { it.isFile }?.asRequestBody(mediaType)
            ?: ContentUriRequestBody(
                context = context,
                uri = Uri.parse(upload.sourceUri),
                declaredLength = upload.contentLength,
                mediaType = mediaType
            )
        val body = ProgressRequestBody(
            delegate = fileBody,
            onProgress = onProgress
        )
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", sanitizeMultipartFilename(upload.filename), body)
            .addFormDataPart("title", upload.title.trim())
            .addFormDataPart("year", upload.year.toString())
            .addFormDataPart("type", upload.type.wire)
            .apply {
                upload.season?.let { addFormDataPart("season", it.toString()) }
                upload.episode?.let { addFormDataPart("episode", it.toString()) }
            }
            .build()
        val request = Request.Builder()
            .url(buildUrl(access, tokenPath, listOf("api", "v2", "local-danmu", "upload")))
            .applyRuntimeApiAuth(access)
            .post(multipart)
            .build()
        return execute(request, longRunning = true)
    }

    suspend fun execute(
        request: Request,
        longRunning: Boolean = false
    ): Pair<Int, String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // 必须在 IO 线程执行：OkHttp 的 response.body.string() 会真正读 socket，
        // 主线程读取会被 BlockGuard 抛 NetworkOnMainThreadException（响应头已到、
        // 正文还没读完时最容易触发），此前被 runCatching 吞成空字符串，
        // 于是表现为「核心返回了空响应 / 本地接口请求失败」。
        try {
            val client = if (longRunning) localHttpClient else requestHttpClient
            client.newOutboundCall(request).executeCancellable().use { response ->
                val read = runCatching { response.body.string() }
                if (read.isFailure) {
                    val failure = read.exceptionOrNull()
                    AppDiagnosticLogger.w(
                        context,
                        "LocalDanmu",
                        "读取响应体失败: code=${response.code}" +
                            " len=${response.header("Content-Length")}" +
                            " enc=${response.header("Content-Encoding")}" +
                            " conn=${response.header("Connection")}" +
                            " 异常=${failure?.javaClass?.name}: ${failure?.message}",
                        failure
                    )
                } else if (read.getOrNull().isNullOrEmpty()) {
                    AppDiagnosticLogger.w(
                        context,
                        "LocalDanmu",
                        "响应体为空: code=${response.code}" +
                            " len=${response.header("Content-Length")}" +
                            " enc=${response.header("Content-Encoding")}" +
                            " conn=${response.header("Connection")}"
                    )
                }
                response.code to read.getOrDefault("")
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            AppDiagnosticLogger.w(
                context,
                "LocalDanmu",
                "HTTP 请求异常: ${error.javaClass.name}: ${error.message}",
                error
            )
            throw LocalDanmuApiException(
                LocalDanmuErrorKind.Network,
                "本地接口请求失败（${error.javaClass.simpleName}: ${error.message ?: "无消息"}）"
            )
        }
    }

    fun mapError(
        code: Int,
        body: String,
        notFoundKind: LocalDanmuErrorKind
    ): LocalDanmuApiException {
        val message = runCatching {
            JSONObject(body).optString("errorMessage").trim()
        }.getOrNull().orEmpty()
        return when (code) {
            400 -> LocalDanmuApiException(
                LocalDanmuErrorKind.InvalidRequest,
                message.ifBlank { "请求参数或弹幕文件无效" },
                code
            )
            401 -> LocalDanmuApiException(
                LocalDanmuErrorKind.Unauthorized,
                message.ifBlank { "令牌校验失败，请检查 TOKEN 配置" },
                code
            )
            403 -> LocalDanmuApiException(
                LocalDanmuErrorKind.Forbidden,
                message.ifBlank { "需要 ADMIN_TOKEN 或开启 LOCAL_DANMU_NOT_REQUIRE_ADMIN" },
                code
            )
            404 -> LocalDanmuApiException(
                notFoundKind,
                if (notFoundKind == LocalDanmuErrorKind.Unsupported) {
                    "当前核心版本不支持本地弹幕功能"
                } else {
                    message.ifBlank { "资源不存在" }
                },
                code
            )
            409 -> LocalDanmuApiException(
                LocalDanmuErrorKind.Conflict,
                message.ifBlank { "目标资源已存在，无法覆盖" },
                code
            )
            413 -> LocalDanmuApiException(
                LocalDanmuErrorKind.FileTooLarge,
                message.ifBlank { "单文件不能超过 10 MB" },
                code
            )
            in 500..599 -> LocalDanmuApiException(
                LocalDanmuErrorKind.Server,
                message.ifBlank { "核心服务返回了错误" },
                code
            )
            else -> LocalDanmuApiException(
                LocalDanmuErrorKind.InvalidRequest,
                message.ifBlank { "请求失败（HTTP $code）" },
                code
            )
        }
    }

    fun sanitizeMultipartFilename(raw: String): String {
        val cleaned = raw
            .replace(Regex("[\\r\\n\\u0000]"), "")
            .trim()
            .take(200)
        return cleaned.ifBlank { "danmu.txt" }
    }

    fun decodeObject(raw: String, context: String): JSONObject {
        if (raw.isBlank()) {
            throw LocalDanmuApiException(
                LocalDanmuErrorKind.Busy,
                "核心返回了空响应，可能正在重启，请稍后重试",
                null
            )
        }
        return try {
            JSONObject(raw)
        } catch (error: org.json.JSONException) {
            throw LocalDanmuApiException(
                LocalDanmuErrorKind.Server,
                "核心返回了无效响应（$context）：${error.message ?: "解析失败"}",
                null
            )
        }
    }

    class ProgressRequestBody(
        private val delegate: RequestBody,
        private val onProgress: (Float) -> Unit
    ) : RequestBody() {
        override fun contentType() = delegate.contentType()

        override fun contentLength(): Long = delegate.contentLength()

        override fun writeTo(sink: BufferedSink) {
            val total = contentLength().takeIf { it > 0L } ?: 0L
            val countingSink = object : ForwardingSink(sink) {
                private var written = 0L

                override fun write(source: okio.Buffer, byteCount: Long) {
                    super.write(source, byteCount)
                    written += byteCount
                    if (total > 0L) {
                        onProgress((written.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }
            val buffered = countingSink.buffer()
            delegate.writeTo(buffered)
            buffered.flush()
        }
    }

    class ContentUriRequestBody(
        private val context: Context,
        private val uri: Uri,
        private val declaredLength: Long?,
        private val mediaType: okhttp3.MediaType?
    ) : RequestBody() {
        override fun contentType() = mediaType

        override fun contentLength(): Long =
            declaredLength?.takeIf { it >= 0L } ?: -1L

        override fun writeTo(sink: BufferedSink) {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("无法打开源文件")
            input.use { source ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = source.read(buffer)
                    if (read <= 0) break
                    sink.write(buffer, 0, read)
                }
            }
        }
    }
}

/**
 * 幂等本地请求是否还值得重试：只重试网络类失败，且未到上限。
 * 4xx/5xx 是核心明确给出的业务结果，重试没有意义。
 */
internal fun shouldRetryLocalDanmuRequest(
    kind: LocalDanmuErrorKind,
    attempt: Int,
    maxAttempts: Int
): Boolean = kind == LocalDanmuErrorKind.Network && attempt < maxAttempts
