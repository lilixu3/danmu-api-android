package com.example.danmuapiapp.data.repository

import android.content.Context
import android.net.Uri
import com.example.danmuapiapp.data.service.AppDiagnosticLogger
import com.example.danmuapiapp.data.service.RuntimePaths
import com.example.danmuapiapp.data.util.DotEnvCodec
import com.example.danmuapiapp.data.util.RuntimeApiAccess
import com.example.danmuapiapp.data.util.RuntimeApiAccessResolver
import com.example.danmuapiapp.data.util.RuntimeApiUrls
import com.example.danmuapiapp.data.util.RuntimeManagementPaths
import com.example.danmuapiapp.data.util.RuntimeTokenNormalizer
import com.example.danmuapiapp.data.util.applyRuntimeApiAuth
import com.example.danmuapiapp.domain.model.LocalDanmuGroup
import com.example.danmuapiapp.domain.model.LocalDanmuResource
import com.example.danmuapiapp.domain.model.LocalDanmuSnapshot
import com.example.danmuapiapp.domain.model.LocalDanmuUploadRequest
import com.example.danmuapiapp.domain.model.LocalDanmuWritePermission
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.repository.AdminSessionRepository
import com.example.danmuapiapp.domain.repository.EnvConfigRepository
import com.example.danmuapiapp.domain.repository.LocalDanmuRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal enum class LocalDanmuErrorKind {
    Unsupported,
    Unauthorized,
    Forbidden,
    NotFound,
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

@Singleton
class LocalDanmuRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val adminSessionRepository: AdminSessionRepository,
    private val envConfigRepository: EnvConfigRepository,
    private val sourceStore: LocalDanmuSourceStore,
    private val coreCacheReader: LocalDanmuCoreCacheReader,
    private val runtimeRepository: RuntimeRepository
) : LocalDanmuRepository {

    companion object {
        private const val RUNTIME_PREFS_NAME = "runtime"
        private const val DEFAULT_PORT = 9321
        private const val DEFAULT_SOURCE_ORDER = "douban,360,renren,hanjutv"
        private const val LOCAL_SOURCE_KEY = "local"
        // 核心（Node）空闲 keep-alive 只有 5 秒，而 OkHttp 默认把空闲连接留 5 分钟，
        // 复用这类“服务端已关闭”的连接会一直等不到响应（读超时且 OkHttp 不会重试）。
        private const val LOCAL_GET_ATTEMPTS = 3
        private const val LOCAL_GET_RETRY_DELAY_MS = 350L
        /** 自动刷新（进出页面）在这个窗口内直接用内存快照，不再发请求。 */
        private const val CACHE_FRESH_WINDOW_MS = 60_000L
    }

    private val runtimePrefs = context.getSharedPreferences(RUNTIME_PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var cachedSnapshot: LocalDanmuSnapshot? = null
    @Volatile private var cachedAtMillis: Long = 0L
    @Volatile private var cachedFingerprint: String = ""
    /** 本地核心 API 专用连接池：不保留空闲连接，每次请求都新建连接，避免复用死连接。 */
    private val localApiConnectionPool = okhttp3.ConnectionPool(0, 1, TimeUnit.NANOSECONDS)
    private val localHttpClient = httpClient.newBuilder()
        .connectionPool(localApiConnectionPool)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .callTimeout(10, TimeUnit.MINUTES)
        .build()
    private val requestHttpClient = httpClient.newBuilder()
        .connectionPool(localApiConnectionPool)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
    override val writePermission: StateFlow<LocalDanmuWritePermission> =
        combine(
            adminSessionRepository.sessionState,
            envConfigRepository.envVars
        ) { adminState, envVars ->
            val relaxed = envVars["LOCAL_DANMU_NOT_REQUIRE_ADMIN"].isTruthy()
            when {
                adminState.isAdminMode -> LocalDanmuWritePermission.Writable
                relaxed -> LocalDanmuWritePermission.Writable
                adminState.hasAdminTokenConfigured -> LocalDanmuWritePermission.AdminRequired
                else -> LocalDanmuWritePermission.ReadOnly
            }
        }.stateIn(scope, SharingStarted.Eagerly, LocalDanmuWritePermission.ReadOnly)

    override val localSourceEnabled: StateFlow<Boolean> =
        envConfigRepository.envVars
            .map { envVars ->
                val raw = envVars["SOURCE_ORDER"].orEmpty()
                val tokens = raw.split(',').map { it.trim().lowercase(Locale.ROOT) }
                tokens.contains(LOCAL_SOURCE_KEY)
            }
            .stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * 列表加载：新鲜缓存 → 直读核心缓存目录（快）→ 接口兜底（慢，旧核心还要全量解析）。
     * 任何一条路径成功都会刷新内存快照，页面进出不再每次等接口。
     */
    override suspend fun refresh(force: Boolean): Result<LocalDanmuSnapshot> {
        val fingerprint = cacheFingerprint()
        if (!force) {
            val cached = cachedSnapshot
            if (cached != null &&
                cachedFingerprint == fingerprint &&
                System.currentTimeMillis() - cachedAtMillis < CACHE_FRESH_WINDOW_MS
            ) {
                AppDiagnosticLogger.i(
                    context,
                    "LocalDanmu",
                    "使用本地快照缓存：资源 ${cached.resources.size} 个"
                )
                return Result.success(cached)
            }
        }
        readFromCoreCache(fingerprint)?.let { return Result.success(it) }
        return refreshFromApi(fingerprint)
    }

    /** 直读核心缓存目录里的资源元数据（不触发核心解析）。失败时返回 null，由调用方回退接口。 */
    private suspend fun readFromCoreCache(fingerprint: String): LocalDanmuSnapshot? {
        val result = coreCacheReader.readResources()
        val resources = result.getOrElse { error ->
            AppDiagnosticLogger.i(
                context,
                "LocalDanmu",
                "直读核心缓存不可用（${error.message}），回退到接口"
            )
            return null
        }
        AppDiagnosticLogger.i(
            context,
            "LocalDanmu",
            "直读核心缓存成功：资源 ${resources.size} 个"
        )
        return publishSnapshot(resources, fingerprint)
    }

    /** 接口兜底：任何核心版本都能用，只是旧核心可能很慢。 */
    private suspend fun refreshFromApi(fingerprint: String): Result<LocalDanmuSnapshot> {
        return runLocalDanmuRequest {
            val access = resolveRuntimeAccess()
            val paths = tokenPaths(access)
            AppDiagnosticLogger.i(
                context,
                "LocalDanmu",
                "refresh 开始: port=${access.port} tokenLen=${access.runtimeToken.length} 候选路径=${paths.size}"
            )
            try {
                val resources = withTokenPaths(paths) { tokenPath ->
                    val (code, body) = executeGet(
                        access,
                        tokenPath,
                        listOf("api", "v2", "local-danmu", "list")
                    )
                    if (code !in 200..299) {
                        throw mapError(code, body, notFoundKind = LocalDanmuErrorKind.Unsupported)
                    }
                    parseSnapshot(body).resources
                }
                AppDiagnosticLogger.i(
                    context,
                    "LocalDanmu",
                    "refresh 成功: 资源 ${resources.size} 个"
                )
                publishSnapshot(resources, fingerprint)
            } catch (error: LocalDanmuApiException) {
                AppDiagnosticLogger.w(
                    context,
                    "LocalDanmu",
                    "refresh 失败: kind=${error.kind} http=${error.httpCode} 消息=${error.message}"
                )
                throw error
            }
        }
    }

    /** 统一发布快照：分组一律由 app 自己构建，直读与接口两条路径结果一致。 */
    private fun publishSnapshot(
        resources: List<LocalDanmuResource>,
        fingerprint: String
    ): LocalDanmuSnapshot {
        val snapshot = LocalDanmuSnapshot(
            resources = resources,
            groups = buildGroups(resources)
        )
        cachedSnapshot = snapshot
        cachedAtMillis = System.currentTimeMillis()
        cachedFingerprint = fingerprint
        return snapshot
    }

    private fun invalidateCache() {
        cachedAtMillis = 0L
    }

    /** 工作目录或核心变体变化时，内存快照立即失效。 */
    private fun cacheFingerprint(): String {
        val projectDir = runCatching { RuntimePaths.normalProjectDir(context).absolutePath }.getOrDefault("")
        val variant = runCatching { runtimeRepository.runtimeState.value.variant.key }.getOrDefault("")
        return "$projectDir|$variant"
    }

    override suspend fun loadDetail(resourceKey: String): Result<LocalDanmuResource> {
        return runLocalDanmuRequest {
            val access = resolveRuntimeAccess()
            val paths = tokenPaths(access)
            withTokenPaths(paths) { tokenPath ->
                val (code, body) = executeGet(
                    access,
                    tokenPath,
                    listOf("api", "v2", "local-danmu", resourceKey)
                )
                if (code !in 200..299) {
                    throw mapError(code, body, notFoundKind = LocalDanmuErrorKind.NotFound)
                }
                val root = decodeObject(body, "detail")
                parseResource(root.optJSONObject("resource") ?: JSONObject())
            }
        }
    }

    override suspend fun upload(
        request: LocalDanmuUploadRequest,
        onProgress: (Float) -> Unit
    ): Result<LocalDanmuResource> {
        return runLocalDanmuRequest {
            val stagedFile = request.stagedFile
            if ((stagedFile == null || !stagedFile.isFile) && request.sourceUri.isBlank()) {
                throw LocalDanmuApiException(
                    LocalDanmuErrorKind.InvalidRequest,
                    "源文件不存在，请重新选择文件"
                )
            }
            if (stagedFile != null &&
                stagedFile.length() > LocalDanmuImportManager.CORE_MAX_UPLOAD_BYTES
            ) {
                throw LocalDanmuApiException(
                    LocalDanmuErrorKind.FileTooLarge,
                    "单文件不能超过 10 MB"
                )
            }
            val access = resolveRuntimeAccess()
            val paths = tokenPaths(access)
            withTokenPaths(paths) { tokenPath ->
                val (code, body) = executeUpload(access, tokenPath, request, onProgress)
                if (code !in 200..299) {
                    throw mapError(code, body, notFoundKind = LocalDanmuErrorKind.Unsupported)
                }
                val root = decodeObject(body, "upload")
                if (!root.optBoolean("success", true)) {
                    throw LocalDanmuApiException(
                        LocalDanmuErrorKind.InvalidRequest,
                        root.optString("errorMessage").ifBlank { "核心拒绝了上传请求" },
                        code
                    )
                }
                val resource = root.optJSONObject("resource")
                    ?: throw LocalDanmuApiException(
                        LocalDanmuErrorKind.Server,
                        "核心未返回已保存的资源信息"
                    )
                parseResource(resource)
            }
        }.onSuccess { invalidateCache() }
    }

    override suspend fun delete(resourceKey: String): Result<Unit> {
        return runLocalDanmuRequest {
            val access = resolveRuntimeAccess()
            val paths = tokenPaths(access)
            withTokenPaths(paths) { tokenPath ->
                val (code, body) = executeDelete(
                    access = access,
                    tokenPath = tokenPath,
                    pathSegments = listOf("api", "v2", "local-danmu", resourceKey)
                )
                if (code == 404) return@withTokenPaths
                if (code !in 200..299) {
                    throw mapError(code, body, notFoundKind = LocalDanmuErrorKind.NotFound)
                }
                val root = runCatching { JSONObject(body) }.getOrNull()
                if (root?.optBoolean("success", true) == false) {
                    throw LocalDanmuApiException(
                        LocalDanmuErrorKind.InvalidRequest,
                        root.optString("errorMessage").ifBlank { "删除失败" },
                        code
                    )
                }
            }
            sourceStore.remove(listOf(resourceKey))
        }.onSuccess { invalidateCache() }
    }

    override suspend fun deleteMany(
        resourceKeys: Collection<String>,
        onProgress: (completed: Int, total: Int) -> Unit
    ): Result<Int> {
        val keys = resourceKeys.filter { it.isNotBlank() }.distinct()
        if (keys.isEmpty()) return Result.success(0)
        var completed = 0
        var success = 0
        keys.forEach { key ->
            delete(key).onSuccess { success++ }
            completed++
            onProgress(completed, keys.size)
        }
        return if (success == 0) {
            Result.failure(LocalDanmuApiException(LocalDanmuErrorKind.Server, "删除失败"))
        } else {
            Result.success(success)
        }
    }

    override suspend fun enableLocalSource(preferFirst: Boolean): Result<String> {
        return runLocalDanmuRequest {
            val raw = envConfigRepository.readCurrentRawContent().getOrNull().orEmpty()
            val current = DotEnvCodec.parse(raw)["SOURCE_ORDER"]
                .orEmpty()
                .ifBlank { DEFAULT_SOURCE_ORDER }
            val tokens = current.split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.equals(LOCAL_SOURCE_KEY, ignoreCase = true) }
                .toMutableList()
            if (preferFirst) tokens.add(0, LOCAL_SOURCE_KEY) else tokens.add(LOCAL_SOURCE_KEY)
            val next = tokens.joinToString(",")
            envConfigRepository.setValue("SOURCE_ORDER", next)
            envConfigRepository.reload()
            next
        }
    }

    private fun resolveRuntimeAccess(): RuntimeApiAccess {
        val base = RuntimeApiAccessResolver.resolve(context, runtimePrefs, DEFAULT_PORT)
        val runtimeState = runtimeRepository.runtimeState.value
        val port = runtimeState.port.takeIf { it in 1..65535 } ?: base.port
        val runtimeToken = RuntimeTokenNormalizer.normalizeInput(runtimeState.token)
            .ifBlank { base.runtimeToken }
        val tokenPaths = if (runtimeToken.isBlank()) {
            emptyList()
        } else {
            listOf("/${runtimeToken.trim('/')}")
        }
        return RuntimeApiAccess(
            port = port,
            runtimeToken = runtimeToken,
            tokenPaths = tokenPaths
        )
    }

    private fun tokenPaths(access: RuntimeApiAccess): List<String> {
        val adminState = adminSessionRepository.sessionState.value
        return RuntimeManagementPaths.tokenPaths(
            runtimeTokenPaths = access.tokenPaths,
            adminMode = adminState.isAdminMode,
            adminToken = adminSessionRepository.currentAdminTokenOrNull()
        )
    }

    private suspend fun <T> withTokenPaths(
        paths: List<String>,
        block: suspend (String) -> T
    ): T {
        var lastError: LocalDanmuApiException? = null
        val candidates = paths.ifEmpty { listOf("") }
        for (path in candidates) {
            try {
                return block(path)
            } catch (error: LocalDanmuApiException) {
                if (error.kind == LocalDanmuErrorKind.Unsupported ||
                    error.kind == LocalDanmuErrorKind.Busy ||
                    error.kind == LocalDanmuErrorKind.Network ||
                    error.kind == LocalDanmuErrorKind.Server
                ) {
                    throw error
                }
                lastError = error
            }
        }
        throw lastError ?: LocalDanmuApiException(
            LocalDanmuErrorKind.Network,
            "无法连接本地核心服务"
        )
    }

    private fun buildUrl(
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

    private suspend fun executeGet(
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
    private suspend fun executeGetWithRetry(request: Request): Pair<Int, String> {
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
    private fun maskTokenInPath(path: String): String {
        val token = RuntimeTokenNormalizer.normalizeInput(runtimeStateToken()).trim('/')
        if (token.isBlank()) return path
        val encoded = java.net.URLEncoder.encode(token, "UTF-8")
        return when {
            path.contains(token) -> path.replace(token, "***${token.takeLast(2)}")
            path.contains(encoded) -> path.replace(encoded, "***${token.takeLast(2)}")
            else -> path
        }
    }

    private fun runtimeStateToken(): String {
        val state = runtimeRepository.runtimeState.value
        return RuntimeTokenNormalizer.normalizeInput(state.token)
    }

    private suspend fun executeDelete(
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

    private suspend fun executeUpload(
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

    private suspend fun execute(
        request: Request,
        longRunning: Boolean = false
    ): Pair<Int, String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // 必须在 IO 线程执行：OkHttp 的 response.body.string() 会真正读 socket，
        // 主线程读取会被 BlockGuard 抛 NetworkOnMainThreadException（响应头已到、
        // 正文还没读完时最容易触发），此前被 runCatching 吞成空字符串，
        // 于是表现为「核心返回了空响应 / 本地接口请求失败」。
        try {
            val client = if (longRunning) localHttpClient else requestHttpClient
            client.newCall(request).executeCancellable().use { response ->
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

    private fun mapError(
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

    /**
     * 只解析 `resources`：响应里的 `groups[].episodes` 与它是重复数据（每集约一半体积），
     * 分组由 app 自己用 [buildGroups] 构建，直读与接口两条路径结果完全一致。
     */
    private fun parseSnapshot(raw: String): LocalDanmuSnapshot {
        val root = decodeObject(raw, "list")
        val resources = root.optJSONArray("resources")
            ?.let { array -> (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let(::parseResource)
            } }
            .orEmpty()
        return LocalDanmuSnapshot(
            resources = resources,
            groups = buildGroups(resources)
        )
    }

    private fun parseResource(obj: JSONObject): LocalDanmuResource {
        return LocalDanmuResource(
            resourceKey = obj.optString("resourceKey"),
            videoId = obj.optString("videoId"),
            title = obj.optString("title"),
            year = obj.optInt("year", 0),
            type = obj.optString("type"),
            season = obj.optInt("season", 1),
            episode = if (obj.has("episode") && !obj.isNull("episode")) obj.optInt("episode") else null,
            filename = obj.optString("filename"),
            sizeBytes = obj.optLong("size", 0L),
            format = obj.optString("format"),
            status = obj.optString("status", "ready"),
            count = obj.optInt("count", 0),
            updatedAt = obj.optString("updatedAt"),
            matchKeys = obj.optJSONArray("matchKeys")?.let { array ->
                (0 until array.length()).mapNotNull { index -> array.optString(index).takeIf { it.isNotBlank() } }
            }.orEmpty()
        )
    }

    private fun buildGroups(resources: List<LocalDanmuResource>): List<LocalDanmuGroup> {
        return resources
            .groupBy { resource ->
                listOf(
                    LocalDanmuResourceKey.normalizeKey(resource.title),
                    resource.year.toString(),
                    LocalDanmuResourceKey.normalizeType(resource.type),
                    resource.season.toString()
                ).joinToString("|")
            }
            .map { (key, rows) ->
                val first = rows.first()
                LocalDanmuGroup(
                    groupKey = key,
                    title = first.title,
                    year = first.year.takeIf { it > 0 },
                    type = first.type,
                    season = first.season,
                    episodeCount = rows.size,
                    count = rows.sumOf { it.count },
                    sizeBytes = rows.sumOf { it.sizeBytes },
                    updatedAt = rows.maxOfOrNull { it.updatedAt }.orEmpty(),
                    episodes = rows.sortedBy { it.episode ?: Int.MAX_VALUE }
                )
            }
            .sortedWith(
                compareBy<LocalDanmuGroup> { it.title.lowercase(Locale.getDefault()) }
                    .thenByDescending { it.year ?: 0 }
                    .thenBy { it.type }
                    .thenBy { it.season }
            )
    }

    private fun sanitizeMultipartFilename(raw: String): String {
        val cleaned = raw
            .replace(Regex("[\\r\\n\\u0000]"), "")
            .trim()
            .take(200)
        return cleaned.ifBlank { "danmu.txt" }
    }

    private fun decodeObject(raw: String, context: String): JSONObject {
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

    private fun String?.isTruthy(): Boolean {
        val value = this?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return value == "true" || value == "1" || value == "yes" || value == "on"
    }

    private class ProgressRequestBody(
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

    private class ContentUriRequestBody(
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
