package com.example.danmuapiapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.FileObserver
import android.os.Looper
import com.example.danmuapiapp.data.service.GithubProxyService
import com.example.danmuapiapp.data.service.NodeProjectManager
import com.example.danmuapiapp.data.service.RootShell
import com.example.danmuapiapp.data.service.RuntimeModePrefs
import com.example.danmuapiapp.data.service.RuntimePaths
import com.example.danmuapiapp.domain.model.*
import com.example.danmuapiapp.domain.repository.CoreRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.*
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoreRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val githubProxyService: GithubProxyService
) : CoreRepository {

    companion object {
        private const val USER_AGENT = "DanmuApiApp"
        private const val CORE_REFRESH_DEBOUNCE_MS = 800L
        private const val WORK_DIR_PREFS = "danmu_work_dir"
        private const val WORK_DIR_KEY_CUSTOM_BASE_PATH = "custom_path"
        private const val RUNTIME_PREFS = "runtime"
        private val coreVersionRegexList = listOf(
            Regex("""(?m)\bVERSION\b\s*[:=]\s*['"]([^'"]+)['"]"""),
            Regex("""(?m)\bversion\b\s*[:=]\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
        )
        private val packageVersionRegex = Regex("(?m)\"version\"\\s*:\\s*\"([^\"]+)\"")
    }

    private val settingsPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val workDirPrefs = context.getSharedPreferences(WORK_DIR_PREFS, Context.MODE_PRIVATE)
    private val runtimePrefs = context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val metadataHttpClient = httpClient.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when {
            prefs === workDirPrefs && key == WORK_DIR_KEY_CUSTOM_BASE_PATH -> refreshCoreInfo()
            prefs === runtimePrefs &&
                (key == RuntimeModePrefs.KEY_RUN_MODE || key == RuntimeModePrefs.KEY_ROOT_MODE_LEGACY) -> {
                refreshCoreInfo()
            }
        }
    }

    private val _coreInfoList = MutableStateFlow(
        ApiVariant.entries.map { variant ->
            CoreInfo(
                variant = variant,
                version = null,
                isInstalled = false
            )
        }
    )
    override val coreInfoList: StateFlow<List<CoreInfo>> = _coreInfoList.asStateFlow()
    private val _isCoreInfoLoading = MutableStateFlow(true)
    override val isCoreInfoLoading: StateFlow<Boolean> = _isCoreInfoLoading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(CoreDownloadProgress())
    override val downloadProgress: StateFlow<CoreDownloadProgress> = _downloadProgress.asStateFlow()
    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var coreWatcher: CoreDirWatcher? = null
    private var coreRefreshJob: Job? = null
    private var refreshAllJob: Job? = null
    private var pendingCoreRefreshReason: String? = null
    private val refreshTicket = AtomicLong(0L)

    init {
        workDirPrefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        runtimePrefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        ensureCoreDirWatcher(currentRunMode())
        refreshCoreInfo()
    }

    override fun isCoreInstalled(variant: ApiVariant): Boolean {
        val cached = _coreInfoList.value.find { it.variant == variant }?.isInstalled ?: false
        if (cached) return true

        val isMainThread = Looper.myLooper() == Looper.getMainLooper()
        val mode = currentRunMode()
        if (mode != RunMode.Normal && isMainThread) {
            // 避免主线程触发 su 检测导致界面卡顿，交给后台刷新后再更新状态。
            refreshCoreInfo()
            return false
        }
        return hasValidCore(variant, mode)
    }

    override fun refreshCoreInfo() {
        refreshAllJob?.cancel()
        _isCoreInfoLoading.value = true
        val ticket = refreshTicket.incrementAndGet()
        refreshAllJob = repoScope.launch {
            try {
                val mode = currentRunMode()
                if (mode == RunMode.Normal) {
                    runCatching {
                        NodeProjectManager.ensureProjectExtracted(context, RuntimePaths.normalProjectDir(context))
                    }
                }
                ensureCoreDirWatcher(mode)
                val refreshed = ApiVariant.entries.map { loadCoreInfo(it, mode) }
                _coreInfoList.value = refreshed
            } finally {
                if (refreshTicket.get() == ticket) {
                    _isCoreInfoLoading.value = false
                }
            }
        }
    }

    private fun loadCoreInfo(variant: ApiVariant, mode: RunMode): CoreInfo {
        val location = getCoreLocation(variant, mode)
        if (mode == RunMode.Normal) {
            NodeProjectManager.normalizeCoreLayout(location.normalDir)
        }
        val version = readLocalCoreVersion(variant, mode)

        return CoreInfo(
            variant = variant,
            version = version,
            isInstalled = hasValidCore(variant, mode)
        )
    }

    private data class CoreLocation(
        val mode: RunMode,
        val normalDir: File,
        val rootDirPath: String
    )

    private fun currentRunMode(): RunMode = RuntimePaths.currentRunMode(context)

    private fun getCoreLocation(variant: ApiVariant, mode: RunMode = currentRunMode()): CoreLocation {
        val normalDir = File(RuntimePaths.normalProjectDir(context), "danmu_api_${variant.key}")
        val rootDirPath = "${RuntimePaths.rootProjectDir(context).absolutePath}/danmu_api_${variant.key}"
        return CoreLocation(
            mode = mode,
            normalDir = normalDir,
            rootDirPath = rootDirPath
        )
    }

    private fun hasValidCore(variant: ApiVariant, mode: RunMode): Boolean {
        val location = getCoreLocation(variant, mode)
        return if (mode != RunMode.Normal) {
            rootHasValidCore(location.rootDirPath)
        } else {
            NodeProjectManager.hasValidCore(location.normalDir)
        }
    }

    private fun readLocalCoreVersion(variant: ApiVariant, mode: RunMode): String? {
        val location = getCoreLocation(variant, mode)
        return if (mode != RunMode.Normal) {
            rootReadCoreVersion(location.rootDirPath)
        } else {
            NodeProjectManager.readCoreVersion(location.normalDir)
        }
    }

    private fun apiUrlCandidates(path: String): List<String> {
        val direct = "https://api.github.com/$path"
        return withProxyCandidates(direct)
    }

    private fun rawUrlCandidates(repo: String, filePath: String): List<String> {
        val direct = "https://raw.githubusercontent.com/$repo/$filePath"
        return withProxyCandidates(direct)
    }

    private fun withProxyCandidates(url: String): List<String> {
        return githubProxyService.buildUrlCandidates(url)
    }

    private fun requestText(urls: List<String>, headers: Map<String, String>): String? {
        return requestMapped(urls, headers) { body ->
            body.takeIf { it.isNotBlank() }
        }
    }

    private fun <T> requestMapped(
        urls: List<String>,
        headers: Map<String, String>,
        mapper: (String) -> T?
    ): T? {
        var lastException: Exception? = null
        for (url in urls) {
            repeat(2) { attempt ->
                try {
                    val reqBuilder = Request.Builder().url(url)
                    headers.forEach { (k, v) -> reqBuilder.header(k, v) }
                    githubProxyService.applyGithubAuth(reqBuilder, url)
                    metadataHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body.string()
                            val mapped = mapper(body)
                            if (mapped != null) return mapped
                        }
                    }
                } catch (e: Exception) {
                    lastException = e
                    if (attempt == 0) {
                        Thread.sleep(500)
                    }
                }
            }
        }
        return null
    }

    private fun fetchLatestRelease(repo: String): GithubRelease? {
        return requestMapped(
            urls = apiUrlCandidates("repos/$repo/releases/latest"),
            headers = mapOf(
                "Accept" to "application/vnd.github+json",
                "User-Agent" to USER_AGENT
            )
        ) { body ->
            parseRelease(body)
        }
    }

    private fun fetchVersionFromGlobals(repo: String): String? {
        val paths = listOf(
            "refs/heads/main/danmu_api/configs/globals.js",
            "refs/heads/main/danmu-api/configs/globals.js"
        )
        val regexes = listOf(
            """(?m)\bVERSION\b\s*[:=]\s*['"]([^'"]+)['"]""".toRegex(),
            """(?m)\bversion\b\s*[:=]\s*['"]([^'"]+)['"]""".toRegex(RegexOption.IGNORE_CASE)
        )
        return paths.firstNotNullOfOrNull { path ->
            requestMapped(
                urls = rawUrlCandidates(repo, path),
                headers = mapOf("User-Agent" to USER_AGENT)
            ) { body ->
                regexes.firstNotNullOfOrNull { regex ->
                    regex.find(body)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
                }
            }
        }
    }

    override suspend fun checkUpdate(variant: ApiVariant): GithubRelease? =
        withContext(Dispatchers.IO) {
            val repo = resolveRepo(variant)
            if (repo.isBlank()) return@withContext null
            try {
                // 优先走 release；失败时回退到 main 分支直链，兼容无 release/限流场景。
                fetchLatestRelease(repo) ?: run {
                    val version = fetchVersionFromGlobals(repo) ?: "main"
                    GithubRelease(
                        tagName = version,
                        name = version,
                        body = "",
                        publishedAt = "",
                        zipballUrl = "https://github.com/$repo/archive/refs/heads/main.zip"
                    )
                }
            } catch (_: Exception) {
                null
            }
        }

    private fun resolveRepo(variant: ApiVariant): String {
        if (variant == ApiVariant.Custom) {
            val direct = settingsPrefs.getString("custom_repo", "")?.trim().orEmpty()
            if (direct.isNotBlank()) return direct
            val legacy = context.getSharedPreferences("danmu_api_variant", Context.MODE_PRIVATE)
            val owner = legacy.getString("custom_owner", "")?.trim().orEmpty()
            val repo = legacy.getString("custom_repo", "")?.trim().orEmpty()
            if (owner.isNotBlank() && repo.isNotBlank()) return "$owner/$repo"
            return repo
        }
        return variant.repo
    }

    override suspend fun installCore(variant: ApiVariant): Result<Unit> =
        installOrUpdateCore(variant, actionLabel = "安装")

    override suspend fun updateCore(variant: ApiVariant): Result<Unit> =
        installOrUpdateCore(variant, actionLabel = "更新")

    override suspend fun deleteCore(variant: ApiVariant): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val mode = currentRunMode()
                val location = getCoreLocation(variant, mode)
                runCatching { location.normalDir.deleteRecursively() }
                if (mode != RunMode.Normal) {
                    deleteRootCoreDir(location.rootDirPath)
                }
                refreshCoreInfo()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun checkAndMarkUpdate(variant: ApiVariant) {
        withContext(Dispatchers.IO) {
            try {
                val info = _coreInfoList.value.find { it.variant == variant } ?: return@withContext
                if (!info.isInstalled) return@withContext
                val release = checkUpdate(variant) ?: return@withContext
                val remoteVersion = release.tagName.removePrefix("v").trim()
                    .ifBlank { release.name.removePrefix("v").trim() }
                if (remoteVersion.isBlank()) return@withContext
                val localVersion = info.version?.removePrefix("v")?.trim().orEmpty()
                val hasUpdate = remoteVersion.isNotBlank() && localVersion.isNotBlank() &&
                    compareVersions(remoteVersion, localVersion) > 0
                _coreInfoList.value = _coreInfoList.value.map {
                    if (it.variant == variant) it.copy(latestVersion = remoteVersion, hasUpdate = hasUpdate)
                    else it
                }
            } catch (_: Exception) {}
        }
    }

    override suspend fun checkAllUpdates() {
        withContext(Dispatchers.IO) {
            ApiVariant.entries.forEach { variant ->
                try { checkAndMarkUpdate(variant) } catch (_: Exception) {}
            }
        }
    }

    override suspend fun rollbackCore(variant: ApiVariant, release: GithubRelease): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val versionHint = release.tagName.ifBlank {
                    release.name.ifBlank { "" }
                }.ifBlank { null }
                downloadAndExtract(variant, release.zipballUrl, versionHint, actionLabel = "回退")
                refreshCoreInfo()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun fetchReleaseHistory(variant: ApiVariant): List<GithubRelease> =
        withContext(Dispatchers.IO) {
            val repo = resolveRepo(variant)
            if (repo.isBlank()) return@withContext emptyList()
            try {
                val commitHistory = fetchCommitHistory(repo)
                if (commitHistory.isNotEmpty()) return@withContext commitHistory

                fetchReleaseHistoryFromReleases(repo)
            } catch (_: Exception) {
                emptyList()
            }
        }

    private fun fetchCommitHistory(repo: String): List<GithubRelease> {
        val urls = buildList {
            addAll(apiUrlCandidates("repos/$repo/commits?sha=main&per_page=20"))
            addAll(apiUrlCandidates("repos/$repo/commits?sha=master&per_page=20"))
            addAll(apiUrlCandidates("repos/$repo/commits?per_page=20"))
        }.distinct()

        val result = requestMapped(
            urls = urls,
            headers = mapOf(
                "Accept" to "application/vnd.github+json",
                "User-Agent" to USER_AGENT
            )
        ) { body ->
            val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonArray
                ?: return@requestMapped null
            root.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                parseCommitAsRelease(repo, obj)
            }.takeIf { it.isNotEmpty() }
        }
        if (!result.isNullOrEmpty()) return result

        return fetchCommitHistoryFromAtom(repo)
    }

    private fun parseCommitAsRelease(repo: String, obj: JsonObject): GithubRelease? {
        val sha = (obj["sha"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (sha.isBlank()) return null

        val shortSha = sha.take(7)
        val commitObj = obj["commit"] as? JsonObject
        val message = (commitObj?.get("message") as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val title = message.lineSequence().firstOrNull()?.trim().orEmpty().ifBlank { "提交 $shortSha" }
        val authorObj = commitObj?.get("author") as? JsonObject
        val publishedAt = (authorObj?.get("date") as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

        return GithubRelease(
            tagName = shortSha,
            name = title,
            body = message,
            publishedAt = publishedAt,
            zipballUrl = "https://github.com/$repo/archive/$sha.zip"
        )
    }

    private fun fetchReleaseHistoryFromReleases(repo: String): List<GithubRelease> {
        return requestMapped(
            urls = apiUrlCandidates("repos/$repo/releases?per_page=10"),
            headers = mapOf(
                "Accept" to "application/vnd.github+json",
                "User-Agent" to USER_AGENT
            )
        ) { body ->
            val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonArray
                ?: return@requestMapped null
            root.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                parseRelease(obj.toString())
            }.takeIf { it.isNotEmpty() }
        } ?: emptyList()
    }

    private fun fetchCommitHistoryFromAtom(repo: String): List<GithubRelease> {
        val branchCandidates = listOf("main", "master")
        for (branch in branchCandidates) {
            val parsed = requestMapped(
                urls = withProxyCandidates("https://github.com/$repo/commits/$branch.atom"),
                headers = mapOf(
                    "Accept" to "application/atom+xml,application/xml,text/xml,*/*",
                    "User-Agent" to USER_AGENT
                )
            ) { body ->
                parseCommitAtom(repo, body).takeIf { it.isNotEmpty() }
            }
            if (!parsed.isNullOrEmpty()) return parsed
        }
        return emptyList()
    }

    private fun parseCommitAtom(repo: String, xmlText: String): List<GithubRelease> {
        val entryRegex = Regex("(?s)<entry>(.*?)</entry>")
        return entryRegex.findAll(xmlText).mapNotNull { match ->
            val entry = match.groupValues.getOrNull(1).orEmpty()
            val sha = extractCommitSha(entry) ?: return@mapNotNull null
            val shortSha = sha.take(7)

            val rawTitle = extractXmlTag(entry, "title")
            val title = decodeXmlEntities(rawTitle).lineSequence()
                .firstOrNull()
                ?.trim()
                .orEmpty()
                .ifBlank { "提交 $shortSha" }
            val publishedAt = decodeXmlEntities(extractXmlTag(entry, "updated")).trim()

            GithubRelease(
                tagName = shortSha,
                name = title,
                body = title,
                publishedAt = publishedAt,
                zipballUrl = "https://github.com/$repo/archive/$sha.zip"
            )
        }.toList()
    }

    private fun extractCommitSha(entryXml: String): String? {
        val linkRegex = Regex("""href=["']https://github\.com/[^"']+/commit/([0-9a-fA-F]{7,40})["']""")
        val fromLink = linkRegex.find(entryXml)?.groupValues?.getOrNull(1)?.trim()
        if (!fromLink.isNullOrBlank()) return fromLink

        val idRegex = Regex("""<id>[^<]*/commit/([0-9a-fA-F]{7,40})</id>""")
        val fromId = idRegex.find(entryXml)?.groupValues?.getOrNull(1)?.trim()
        if (!fromId.isNullOrBlank()) return fromId

        return null
    }

    private fun extractXmlTag(xmlText: String, tag: String): String {
        val regex = Regex("(?s)<$tag>(.*?)</$tag>")
        return regex.find(xmlText)?.groupValues?.getOrNull(1).orEmpty()
    }

    private fun decodeXmlEntities(text: String): String {
        return text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
    }

    private fun parseRelease(jsonStr: String): GithubRelease? {
        return try {
            val obj = json.parseToJsonElement(jsonStr).jsonObject
            GithubRelease(
                tagName = obj["tag_name"]?.jsonPrimitive?.content ?: "",
                name = obj["name"]?.jsonPrimitive?.content ?: "",
                body = obj["body"]?.jsonPrimitive?.content ?: "",
                publishedAt = obj["published_at"]?.jsonPrimitive?.content ?: "",
                zipballUrl = obj["zipball_url"]?.jsonPrimitive?.content ?: ""
            )
        } catch (_: Exception) { null }
    }

    private suspend fun installOrUpdateCore(
        variant: ApiVariant,
        actionLabel: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val release = checkUpdate(variant)
                ?: return@withContext Result.failure(Exception("无法获取版本信息"))
            val versionHint = release.tagName.ifBlank {
                release.name.ifBlank { "" }
            }.ifBlank { null }
            downloadAndExtract(variant, release.zipballUrl, versionHint, actionLabel)
            refreshCoreInfo()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun downloadAndExtract(
        variant: ApiVariant,
        zipUrl: String,
        versionHint: String?,
        actionLabel: String
    ) {
        val mode = currentRunMode()
        val location = getCoreLocation(variant, mode)
        val targetDir = location.normalDir

        updateDownloadProgress(
            variant = variant,
            actionLabel = actionLabel,
            stageText = "准备下载核心包",
            progress = null,
            downloadedBytes = 0L,
            totalBytes = -1L
        )

        try {
            val response = withProxyCandidates(zipUrl).asSequence().mapNotNull { url ->
                try {
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .build()
                    val resp = httpClient.newCall(req).execute()
                    if (resp.isSuccessful) resp else {
                        resp.close()
                        null
                    }
                } catch (_: Exception) {
                    null
                }
            }.firstOrNull() ?: throw IOException("Download failed")

            targetDir.deleteRecursively()
            targetDir.mkdirs()

            var lastBytes = 0L
            var totalBytes = -1L
            var lastEmitAt = 0L

            try {
                response.use { resp ->
                    val body = resp.body
                    totalBytes = body.contentLength().takeIf { it > 0 } ?: -1L
                    val rawStream = body.byteStream()
                    updateDownloadProgress(
                        variant = variant,
                        actionLabel = actionLabel,
                        stageText = "正在下载核心包",
                        progress = if (totalBytes > 0) 0f else null,
                        downloadedBytes = 0L,
                        totalBytes = totalBytes
                    )

                    val streamWithProgress = ProgressInputStream(rawStream) { bytes ->
                        lastBytes = bytes
                        val now = System.currentTimeMillis()
                        val shouldEmit = now - lastEmitAt >= 300 || (totalBytes > 0 && bytes >= totalBytes)
                        if (shouldEmit) {
                            lastEmitAt = now
                            val progress = if (totalBytes > 0) {
                                (bytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            } else {
                                null
                            }
                            updateDownloadProgress(
                                variant = variant,
                                actionLabel = actionLabel,
                                stageText = "正在下载核心包",
                                progress = progress,
                                downloadedBytes = bytes,
                                totalBytes = totalBytes
                            )
                        }
                    }

                    val extracted = extractDanmuFolder(streamWithProgress, targetDir)
                    if (!extracted) throw IOException("核心压缩包中未找到 danmu_api 或 danmu-api 目录")
                }
            } catch (e: Exception) {
                targetDir.deleteRecursively()
                throw e
            }

            updateDownloadProgress(
                variant = variant,
                actionLabel = actionLabel,
                stageText = "正在整理核心文件",
                progress = 1f,
                downloadedBytes = if (totalBytes > 0) totalBytes else lastBytes,
                totalBytes = totalBytes
            )

            NodeProjectManager.normalizeCoreLayout(targetDir)
            NodeProjectManager.ensureCorePackageJson(targetDir, versionHint)

            if (!NodeProjectManager.hasValidCore(targetDir)) {
                throw IOException("核心文件不完整，缺少 worker.js")
            }

            if (mode != RunMode.Normal) {
                syncCoreDirToRoot(targetDir, location.rootDirPath)
                if (!rootHasValidCore(location.rootDirPath)) {
                    throw IOException("Root 核心同步后仍缺少 worker.js")
                }
            }
        } finally {
            _downloadProgress.value = CoreDownloadProgress()
        }
    }

    private fun updateDownloadProgress(
        variant: ApiVariant,
        actionLabel: String,
        stageText: String,
        progress: Float?,
        downloadedBytes: Long,
        totalBytes: Long
    ) {
        _downloadProgress.value = CoreDownloadProgress(
            inProgress = true,
            variant = variant,
            actionLabel = actionLabel,
            stageText = stageText,
            progress = progress,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes
        )
    }

    private fun deleteRootCoreDir(rootDirPath: String) {
        val script = """
            DIR=${shellQuote(rootDirPath)}
            rm -rf "${'$'}DIR" 2>/dev/null || true
        """.trimIndent()
        val result = RootShell.exec(script, timeoutMs = 8000L)
        if (!result.ok) {
            val detail = (result.stderr.ifBlank { result.stdout }).trim().ifBlank { "未知错误" }
            throw IOException("删除 Root 核心目录失败: $detail")
        }
    }

    private fun syncCoreDirToRoot(srcDir: File, rootDirPath: String) {
        val script = """
            SRC=${shellQuote(srcDir.absolutePath)}
            DST=${shellQuote(rootDirPath)}
            if [ ! -d "${'$'}SRC" ]; then
              exit 2
            fi
            rm -rf "${'$'}DST" 2>/dev/null || true
            mkdir -p "${'$'}DST" 2>/dev/null || true
            cp -a "${'$'}SRC/." "${'$'}DST/" 2>/dev/null || cp -r "${'$'}SRC/." "${'$'}DST/" 2>/dev/null || true
            [ -f "${'$'}DST/worker.js" ] || [ -f "${'$'}DST/danmu_api/worker.js" ] || [ -f "${'$'}DST/danmu-api/worker.js" ]
        """.trimIndent()
        val result = RootShell.exec(script, timeoutMs = 30000L)
        if (!result.ok) {
            val detail = (result.stderr.ifBlank { result.stdout }).trim().ifBlank { "未知错误" }
            throw IOException("同步 Root 核心目录失败: $detail")
        }
    }

    private fun rootHasValidCore(rootDirPath: String): Boolean {
        val script = """
            DIR=${shellQuote(rootDirPath)}
            [ -f "${'$'}DIR/worker.js" ] && exit 0
            [ -f "${'$'}DIR/danmu_api/worker.js" ] && exit 0
            [ -f "${'$'}DIR/danmu-api/worker.js" ] && exit 0
            exit 1
        """.trimIndent()
        return RootShell.exec(script, timeoutMs = 3000L).ok
    }

    private fun rootReadCoreVersion(rootDirPath: String): String? {
        val globalsCandidates = listOf(
            "$rootDirPath/configs/globals.js",
            "$rootDirPath/config/globals.js",
            "$rootDirPath/globals.js",
            "$rootDirPath/danmu_api/configs/globals.js",
            "$rootDirPath/danmu_api/config/globals.js",
            "$rootDirPath/danmu_api/globals.js",
            "$rootDirPath/danmu-api/configs/globals.js",
            "$rootDirPath/danmu-api/config/globals.js",
            "$rootDirPath/danmu-api/globals.js"
        )
        for (path in globalsCandidates) {
            val text = rootReadText(path) ?: continue
            val version = parseVersionFromSource(text)
            if (!version.isNullOrBlank()) return version.removePrefix("v")
        }

        val packageCandidates = listOf(
            "$rootDirPath/package.json",
            "$rootDirPath/danmu_api/package.json",
            "$rootDirPath/danmu-api/package.json"
        )
        for (path in packageCandidates) {
            val text = rootReadText(path) ?: continue
            val version = packageVersionRegex.find(text)?.groupValues?.getOrNull(1)?.trim()
            if (!version.isNullOrBlank()) return version.removePrefix("v")
        }
        return null
    }

    private fun parseVersionFromSource(text: String): String? {
        for (regex in coreVersionRegexList) {
            val version = regex.find(text)?.groupValues?.getOrNull(1)?.trim()
            if (!version.isNullOrBlank()) return version
        }
        return null
    }

    private data class VersionParts(
        val core: List<Int>,
        val preRelease: List<String>
    )

    /**
     * 比较两个语义化版本号
     * @return 正数表示 v1 > v2，0 表示相等，负数表示 v1 < v2
     */
    private fun compareVersions(v1: String, v2: String): Int {
        if (v1 == v2) return 0

        val p1 = parseVersionParts(v1)
        val p2 = parseVersionParts(v2)

        val coreLen = maxOf(p1.core.size, p2.core.size)
        for (i in 0 until coreLen) {
            val n1 = p1.core.getOrNull(i) ?: 0
            val n2 = p2.core.getOrNull(i) ?: 0
            if (n1 != n2) return n1 - n2
        }

        val pre1 = p1.preRelease
        val pre2 = p2.preRelease
        if (pre1.isEmpty() && pre2.isEmpty()) return 0
        if (pre1.isEmpty()) return 1
        if (pre2.isEmpty()) return -1

        val preLen = maxOf(pre1.size, pre2.size)
        for (i in 0 until preLen) {
            val a = pre1.getOrNull(i)
            val b = pre2.getOrNull(i)
            if (a == b) continue
            if (a == null) return -1
            if (b == null) return 1

            val ai = a.toIntOrNull()
            val bi = b.toIntOrNull()
            if (ai != null && bi != null) {
                if (ai != bi) return ai - bi
                continue
            }
            if (ai != null && bi == null) return -1
            if (ai == null && bi != null) return 1

            val lexical = a.compareTo(b)
            if (lexical != 0) return lexical
        }

        return 0
    }

    private fun parseVersionParts(version: String): VersionParts {
        val cleaned = version.trim()
            .removePrefix("v")
            .substringBefore('+')
        if (cleaned.isBlank()) {
            return VersionParts(core = listOf(0), preRelease = emptyList())
        }

        val main = cleaned.substringBefore('-')
        val pre = cleaned.substringAfter('-', "").takeIf { it.isNotBlank() }

        val core = main.split('.')
            .filter { it.isNotBlank() }
            .map { token ->
                token.toIntOrNull()
                    ?: token.takeWhile { ch -> ch.isDigit() }.toIntOrNull()
                    ?: 0
            }
            .ifEmpty { listOf(0) }

        val preRelease = pre?.split('.')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        return VersionParts(core = core, preRelease = preRelease)
    }

    private fun rootReadText(path: String): String? {
        val script = """
            FILE=${shellQuote(path)}
            if [ ! -f "${'$'}FILE" ]; then
              exit 1
            fi
            cat "${'$'}FILE"
        """.trimIndent()
        val result = RootShell.exec(script, timeoutMs = 4500L)
        if (!result.ok) return null
        return result.stdout
    }

    private fun shellQuote(input: String): String {
        return "'" + input.replace("'", "'\"'\"'") + "'"
    }

    private fun extractDanmuFolder(zipStream: InputStream, outDir: File): Boolean {
        var extractedAny = false

        ZipInputStream(BufferedInputStream(zipStream)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val name = entry.name ?: ""
                val rel = resolveDanmuRelativePath(name)
                if (rel == null) {
                    zis.closeEntry()
                    continue
                }

                if (rel.isBlank()) {
                    zis.closeEntry()
                    continue
                }

                val outFile = File(outDir, rel)
                val canonRoot = outDir.canonicalPath
                val canonOut = outFile.canonicalPath
                if (canonOut != canonRoot && !canonOut.startsWith(canonRoot + File.separator)) {
                    zis.closeEntry()
                    continue
                }

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                    extractedAny = true
                }
                zis.closeEntry()
            }
        }
        return extractedAny
    }

    private fun resolveDanmuRelativePath(entryName: String): String? {
        val clean = entryName.replace('\\', '/').trim('/')
        if (clean.isBlank()) return null

        val parts = clean.split('/')
        val idx = parts.indexOfFirst {
            it.equals("danmu_api", ignoreCase = true) ||
                it.equals("danmu-api", ignoreCase = true)
        }
        if (idx < 0) return null

        val relParts = parts.drop(idx + 1)
        if (relParts.isEmpty()) return ""
        return relParts.joinToString("/")
    }

    private class ProgressInputStream(
        private val source: InputStream,
        private val onRead: (Long) -> Unit
    ) : FilterInputStream(source) {
        private var totalRead = 0L

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) {
                totalRead += 1
                onRead(totalRead)
            }
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val readSize = super.read(buffer, offset, length)
            if (readSize > 0) {
                totalRead += readSize
                onRead(totalRead)
            }
            return readSize
        }
    }

    private fun ensureCoreDirWatcher(mode: RunMode) {
        if (mode != RunMode.Normal) {
            coreWatcher?.stop()
            coreWatcher = null
            return
        }

        val projectDir = RuntimePaths.normalProjectDir(context)
        val projectPath = projectDir.absolutePath
        val current = coreWatcher
        if (current != null && current.rootPath == projectPath) return

        current?.stop()
        coreWatcher = null

        if (!projectDir.exists() || !projectDir.isDirectory) return
        val watcher = CoreDirWatcher(projectDir) { changedPath ->
            scheduleCoreInfoRefresh("检测到核心目录变更：$changedPath")
        }
        watcher.start()
        coreWatcher = watcher
    }

    private fun scheduleCoreInfoRefresh(reason: String) {
        pendingCoreRefreshReason = reason
        coreRefreshJob?.cancel()
        coreRefreshJob = repoScope.launch {
            delay(CORE_REFRESH_DEBOUNCE_MS)
            if (pendingCoreRefreshReason == null) return@launch
            pendingCoreRefreshReason = null
            try {
                refreshCoreInfo()
            } catch (_: Exception) {
                // 忽略刷新异常，避免影响核心下载与运行流程
            }
        }
    }

    private inner class CoreDirWatcher(
        rootDir: File,
        private val onChanged: (String) -> Unit
    ) {
        val rootPath: String = rootDir.absolutePath
        private val rootCanonical = runCatching { rootDir.canonicalFile }.getOrElse { rootDir }
        private val observers = LinkedHashMap<String, FileObserver>()
        private val mask = FileObserver.CLOSE_WRITE or
            FileObserver.MODIFY or
            FileObserver.CREATE or
            FileObserver.MOVED_TO or
            FileObserver.MOVED_FROM or
            FileObserver.DELETE or
            FileObserver.DELETE_SELF or
            FileObserver.MOVE_SELF

        fun start() {
            watchRecursively(rootCanonical)
        }

        fun stop() {
            observers.values.forEach { observer ->
                runCatching { observer.stopWatching() }
            }
            observers.clear()
        }

        private fun createFileObserver(path: String, onEvent: (Int, String?) -> Unit): FileObserver {
            val watchFile = File(path)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                object : FileObserver(watchFile, mask) {
                    override fun onEvent(event: Int, relativePath: String?) {
                        onEvent.invoke(event, relativePath)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                object : FileObserver(path, mask) {
                    override fun onEvent(event: Int, relativePath: String?) {
                        onEvent.invoke(event, relativePath)
                    }
                }
            }
        }

        private fun watchRecursively(dir: File) {
            if (!dir.exists() || !dir.isDirectory || shouldIgnore(dir)) return
            val key = runCatching { dir.canonicalPath }.getOrElse { dir.absolutePath }
            if (observers.containsKey(key)) return

            val observer = createFileObserver(key) { event, path ->
                val target = if (path.isNullOrBlank()) {
                    File(key)
                } else {
                    File(key, path)
                }
                if (shouldIgnore(target)) return@createFileObserver

                if ((event and (FileObserver.CREATE or FileObserver.MOVED_TO)) != 0 && target.isDirectory) {
                    watchRecursively(target)
                }

                val rel = toRelative(target).ifBlank { target.name }
                onChanged(rel)

                if (event and (FileObserver.DELETE_SELF or FileObserver.MOVE_SELF) != 0) {
                    observers.remove(key)?.let { removed ->
                        runCatching { removed.stopWatching() }
                    }
                }
            }

            observer.startWatching()
            observers[key] = observer
            dir.listFiles()?.filter { it.isDirectory }?.forEach { child ->
                watchRecursively(child)
            }
        }

        private fun toRelative(target: File): String {
            return runCatching {
                target.canonicalFile.relativeTo(rootCanonical).path.replace('\\', '/')
            }.getOrElse {
                target.name
            }
        }

        private fun shouldIgnore(target: File): Boolean {
            val rel = toRelative(target)
            if (rel == "." || rel.isBlank()) return false
            return rel == ".app_version" ||
                rel.startsWith("logs/") ||
                rel == "logs" ||
                rel.startsWith(".cache/") ||
                rel == ".cache" ||
                rel.startsWith("node_modules/") ||
                rel == "node_modules"
        }
    }

}
