package com.example.danmuapiapp.data.repository


import android.content.Context
import com.example.danmuapiapp.data.service.AppDiagnosticLogger
import com.example.danmuapiapp.data.service.RuntimeDataAccess
import com.example.danmuapiapp.data.service.RuntimeDataContext
import com.example.danmuapiapp.data.util.DotEnvCodec
import com.example.danmuapiapp.data.util.applyRuntimeApiAuth
import com.example.danmuapiapp.domain.model.LocalDanmuGroup
import com.example.danmuapiapp.domain.model.LocalDanmuMetadataPatch
import com.example.danmuapiapp.domain.model.LocalDanmuResource
import com.example.danmuapiapp.domain.model.LocalDanmuResourceKey
import com.example.danmuapiapp.domain.model.LocalDanmuSnapshot
import com.example.danmuapiapp.domain.model.LocalDanmuUploadRequest
import com.example.danmuapiapp.domain.model.LocalDanmuUpdateResult
import com.example.danmuapiapp.domain.model.LocalDanmuWritePermission
import com.example.danmuapiapp.domain.repository.AdminSessionRepository
import com.example.danmuapiapp.domain.repository.EnvConfigRepository
import com.example.danmuapiapp.domain.repository.LocalDanmuRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalDanmuRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val adminSessionRepository: AdminSessionRepository,
    private val envConfigRepository: EnvConfigRepository,
    private val sourceStore: LocalDanmuSourceStore,
    private val runtimeData: RuntimeDataAccess,
    private val runtimeRepository: RuntimeRepository
) : LocalDanmuRepository {

    companion object {
        private const val DEFAULT_SOURCE_ORDER = "douban,360,renren,hanjutv"
        private const val LOCAL_SOURCE_KEY = "local"
        /** 自动刷新（进出页面）在这个窗口内直接用内存快照，不再发请求。 */
        private const val CACHE_FRESH_WINDOW_MS = 60_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var cachedSnapshot: LocalDanmuSnapshot? = null
    @Volatile private var cachedAtMillis: Long = 0L
    @Volatile private var cachedFingerprint: String = ""
    private val transport = LocalDanmuHttpTransport(
        context = context,
        baseClient = httpClient,
        adminSessionRepository = adminSessionRepository,
        runtimeRepository = runtimeRepository
    )
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

    override suspend fun refresh(force: Boolean): Result<LocalDanmuSnapshot> = runScoped { target ->
        val fingerprint = target.sessionKey
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
                return@runScoped cached
            }
        }
        refreshFromApi(target).getOrThrow()
    }

    private suspend fun refreshFromApi(target: RuntimeDataContext): Result<LocalDanmuSnapshot> {
        return runLocalDanmuRequest {
            val access = target.access
            val paths = transport.tokenPaths(access)
            AppDiagnosticLogger.i(
                context,
                "LocalDanmu",
                "refresh 开始: port=${access.port} tokenLen=${access.runtimeToken.length} 候选路径=${paths.size}"
            )
            try {
                val resources = withTokenPaths(paths) { tokenPath ->
                    val (code, body) = transport.executeGet(
                        access,
                        tokenPath,
                        listOf("api", "v2", "local-danmu", "list")
                    )
                    if (code !in 200..299) {
                        throw transport.mapError(code, body, notFoundKind = LocalDanmuErrorKind.Unsupported)
                    }
                    parseSnapshot(body).resources
                }
                AppDiagnosticLogger.i(
                    context,
                    "LocalDanmu",
                    "refresh 成功: 资源 ${resources.size} 个"
                )
                publishSnapshot(resources, target)
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

    private fun publishSnapshot(
        resources: List<LocalDanmuResource>,
        target: RuntimeDataContext
    ): LocalDanmuSnapshot {
        runtimeData.requireCurrent(target)
        val scoped = resources.map { it.copy(libraryId = target.libraryId, sessionKey = target.sessionKey) }
        val snapshot = LocalDanmuSnapshot(
            resources = scoped,
            groups = buildGroups(scoped),
            libraryId = target.libraryId,
            sessionKey = target.sessionKey
        )
        cachedSnapshot = snapshot
        cachedAtMillis = System.currentTimeMillis()
        cachedFingerprint = target.sessionKey
        return snapshot
    }

    private fun invalidateCache() {
        cachedAtMillis = 0L
    }

    private suspend fun <T> runScoped(expected: String = "", mutation: Boolean = false,
        block: suspend (RuntimeDataContext) -> T): Result<T> = runLocalDanmuRequest {
        runtimeData.withRuntime(expected) { target ->
            try { block(target) } finally { if (mutation) invalidateCache() }
        }
    }

    override suspend fun loadDetail(resourceKey: String, expectedSessionKey: String): Result<LocalDanmuResource> {
        return runScoped(expectedSessionKey) { target ->
            val access = target.access
            val paths = transport.tokenPaths(access)
            withTokenPaths(paths) { tokenPath ->
                val (code, body) = transport.executeGet(
                    access,
                    tokenPath,
                    listOf("api", "v2", "local-danmu", resourceKey)
                )
                if (code !in 200..299) {
                    throw transport.mapError(code, body, notFoundKind = LocalDanmuErrorKind.NotFound)
                }
                val root = transport.decodeObject(body, "detail")
                parseResource(root.optJSONObject("resource") ?: JSONObject()).copy(libraryId = target.libraryId, sessionKey = target.sessionKey)
            }
        }
    }

    override suspend fun loadPreview(resourceKey: String, expectedSessionKey: String): Result<com.example.danmuapiapp.domain.model.DanmuFilePreview> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runScoped(expectedSessionKey) { target ->
                val access = target.access
                withTokenPaths(transport.tokenPaths(access)) { tokenPath ->
                    val url = transport.buildUrl(access, tokenPath, listOf("api", "v2", "comment")).newBuilder()
                        .addQueryParameter("url", "local:$resourceKey").addQueryParameter("format", "json").build()
                    val request = Request.Builder().url(url).applyRuntimeApiAuth(access).get().build()
                    val (code, body) = transport.executeGetWithRetry(request)
                    if (code !in 200..299) throw transport.mapError(code, body, LocalDanmuErrorKind.NotFound)
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    val resource = cachedSnapshot?.resources?.firstOrNull { it.resourceKey == resourceKey }
                    val preview = DanmuFilePreviewParser.parse(bytes.inputStream(), com.example.danmuapiapp.domain.model.DanmuDownloadFormat.Json,
                        resource?.filename?.ifBlank { resource.title } ?: resourceKey, resourceKey, bytes.size.toLong(), 100_000)
                    if (preview.count <= 0) error("核心返回 0 条弹幕，该资源可能已被清理，请重新导入")
                    preview
                }
            }
        }

    override suspend fun upload(
        request: LocalDanmuUploadRequest,
        onProgress: (Float) -> Unit
    ): Result<LocalDanmuResource> {
        return runScoped(request.expectedSessionKey, mutation = true) { target ->
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
            val access = target.access
            val paths = transport.tokenPaths(access)
            withTokenPaths(paths) { tokenPath ->
                val (code, body) = transport.executeUpload(access, tokenPath, request, onProgress)
                if (code !in 200..299) {
                    throw transport.mapError(code, body, notFoundKind = LocalDanmuErrorKind.Unsupported)
                }
                val root = transport.decodeObject(body, "upload")
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
                parseResource(resource).copy(libraryId = target.libraryId, sessionKey = target.sessionKey)
            }
        }.onSuccess { invalidateCache() }
    }

    override suspend fun delete(resourceKey: String, expectedSessionKey: String): Result<Unit> {
        return runScoped(expectedSessionKey, mutation = true) { target ->
            val access = target.access
            val paths = transport.tokenPaths(access)
            withTokenPaths(paths) { tokenPath ->
                val (code, body) = transport.executeDelete(
                    access = access,
                    tokenPath = tokenPath,
                    pathSegments = listOf("api", "v2", "local-danmu", resourceKey)
                )
                if (code == 404) return@withTokenPaths
                if (code !in 200..299) {
                    throw transport.mapError(code, body, notFoundKind = LocalDanmuErrorKind.NotFound)
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
            sourceStore.remove(target.libraryId, listOf(resourceKey))
        }.onSuccess { invalidateCache() }
    }

    override suspend fun updateMetadata(
        resourceKey: String,
        patch: LocalDanmuMetadataPatch
    ): Result<LocalDanmuUpdateResult> {
        if (resourceKey.isBlank()) {
            return Result.failure(
                LocalDanmuApiException(LocalDanmuErrorKind.InvalidRequest, "资源标识无效，请刷新列表")
            )
        }
        val body = buildLocalDanmuPatchBody(patch)
        return runScoped(patch.expectedSessionKey, mutation = true) { target ->
            val access = target.access
            val paths = transport.tokenPaths(access)
            withTokenPaths(paths) { tokenPath ->
                val (code, responseBody) = transport.executePatch(
                    access = access,
                    tokenPath = tokenPath,
                    resourceKey = resourceKey,
                    jsonBody = body
                )
                if (code == 404) {
                    // 旧核心没有 PATCH 路由，统一落到 "Not found"；提示里带上"先更新核心"。
                    throw LocalDanmuApiException(
                        LocalDanmuErrorKind.NotFound,
                        "资源不存在，或当前核心版本不支持元数据编辑（请先更新核心）",
                        code
                    )
                }
                if (code !in 200..299) {
                    throw transport.mapError(code, responseBody, notFoundKind = LocalDanmuErrorKind.NotFound)
                }
                val root = transport.decodeObject(responseBody, "update")
                if (!root.optBoolean("success", true)) {
                    throw LocalDanmuApiException(
                        LocalDanmuErrorKind.InvalidRequest,
                        root.optString("errorMessage").ifBlank { "核心拒绝了修改请求" },
                        code
                    )
                }
                val resources = root.optJSONArray("resources")
                    ?.let { array ->
                        (0 until array.length()).mapNotNull { index ->
                            array.optJSONObject(index)?.let(::parseResource)
                        }
                    }
                    .orEmpty()
                val single = root.optJSONObject("resource")?.let(::parseResource)
                val updated = resources.ifEmpty { listOfNotNull(single) }
                if (updated.isEmpty()) {
                    throw LocalDanmuApiException(
                        LocalDanmuErrorKind.Server,
                        "核心未返回更新后的资源信息"
                    )
                }
                LocalDanmuUpdateResult(
                    scope = root.optString("scope").ifBlank { patch.scope.wire },
                    resources = updated.map { it.copy(libraryId = target.libraryId, sessionKey = target.sessionKey) }
                )
            }
        }.onSuccess { invalidateCache() }
    }

    override suspend fun deleteMany(
        resourceKeys: Collection<String>,
        expectedSessionKey: String,
        onProgress: (completed: Int, total: Int) -> Unit
    ): Result<Int> {
        val keys = resourceKeys.filter { it.isNotBlank() }.distinct()
        if (keys.isEmpty()) return Result.success(0)
        var completed = 0
        var success = 0
        keys.forEach { key ->
            delete(key, expectedSessionKey).onSuccess { success++ }
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
                    error.kind == LocalDanmuErrorKind.Server ||
                    // 409 说明请求已经被核心处理过（只是目标已存在），换 token 路径重发没有意义。
                    error.kind == LocalDanmuErrorKind.Conflict
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

    private fun parseSnapshot(raw: String): LocalDanmuSnapshot {
        val root = transport.decodeObject(raw, "list")
        val array = root.optJSONArray("resources")
            ?: throw LocalDanmuApiException(LocalDanmuErrorKind.Server, "当前核心未返回有效的弹幕列表")
        val resources = (0 until array.length()).map { index ->
            val resource = array.optJSONObject(index)?.let(::parseResource)
            if (resource == null || resource.resourceKey.isBlank()) {
                throw LocalDanmuApiException(LocalDanmuErrorKind.Server, "弹幕列表包含无效资源，未覆盖已有导入状态")
            }
            resource
        }
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

    private fun String?.isTruthy(): Boolean {
        val value = this?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return value == "true" || value == "1" || value == "yes" || value == "on"
    }

}
