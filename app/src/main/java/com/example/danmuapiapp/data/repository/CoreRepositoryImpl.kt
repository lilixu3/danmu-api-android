package com.example.danmuapiapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.FileObserver
import android.os.Looper
import com.example.danmuapiapp.data.remote.github.GithubRemoteService
import com.example.danmuapiapp.data.service.CoreVersionParser
import com.example.danmuapiapp.data.service.GithubProxyService
import com.example.danmuapiapp.data.service.NodeProjectManager
import com.example.danmuapiapp.data.service.RootShell
import com.example.danmuapiapp.data.service.RuntimeModePrefs
import com.example.danmuapiapp.data.service.RuntimePaths
import com.example.danmuapiapp.data.util.ShellUtils.shellQuote
import com.example.danmuapiapp.domain.model.*
import com.example.danmuapiapp.domain.repository.CoreRepository
import com.example.danmuapiapp.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.*
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoreRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val githubRemoteService: GithubRemoteService,
    private val githubProxyService: GithubProxyService,
    private val settingsRepository: SettingsRepository
) : CoreRepository {

    companion object {
        private const val USER_AGENT = "DanmuApiApp"
        private const val CORE_REFRESH_DEBOUNCE_MS = 800L
        private const val WORK_DIR_PREFS = "danmu_work_dir"
        private const val WORK_DIR_KEY_CUSTOM_BASE_PATH = "custom_path"
        private const val RUNTIME_PREFS = "runtime"
        private const val CORE_SOURCE_METADATA_FILE = ".danmuapiapp-core-source.json"
    }

    @Serializable
    private data class CoreSourceMetadata(
        val repo: String = "",
        val branch: String = "",
        val commitSha: String = "",
        val commitPublishedAt: String = "",
        val versionLabel: String = ""
    )

    private data class CoreRemoteSource(
        val release: GithubRelease,
        val metadata: CoreSourceMetadata? = null
    )

    private data class BranchHeadInfo(
        val sha: String,
        val publishedAt: String,
        val title: String
    )

    private val workDirPrefs = context.getSharedPreferences(WORK_DIR_PREFS, Context.MODE_PRIVATE)
    private val runtimePrefs = context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
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
    private var hasLoadedCoreInfoOnce = false
    private val refreshTicket = AtomicLong(0L)

    init {
        workDirPrefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        runtimePrefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        repoScope.launch {
            settingsRepository.customCoreSource
                .map { it.repo to it.branch }
                .distinctUntilChanged()
                .collect {
                    refreshCoreInfo()
                }
        }
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

    override fun isCoreReady(variant: ApiVariant): Boolean {
        val cached = _coreInfoList.value.find { it.variant == variant }
        if (cached?.isReady == true) return true

        val isMainThread = Looper.myLooper() == Looper.getMainLooper()
        val mode = currentRunMode()
        if (mode != RunMode.Normal && isMainThread) {
            refreshCoreInfo()
            return cached?.isReady == true
        }
        return loadCoreState(variant, mode).info.isReady
    }

    override fun refreshCoreInfo() {
        refreshAllJob?.cancel()
        if (!hasLoadedCoreInfoOnce) {
            _isCoreInfoLoading.value = true
        }
        val ticket = refreshTicket.incrementAndGet()
        refreshAllJob = repoScope.launch {
            try {
                val mode = currentRunMode()
                ensureCoreDirWatcher(mode)
                val previous = _coreInfoList.value
                val refreshed = ApiVariant.entries.map { loadCoreState(it, mode) }
                val merged = refreshed.map { state ->
                    mergeVersionUpdateState(
                        previousInfo = previous.find { it.variant == state.info.variant },
                        refreshedInfo = state.info,
                        refreshedMetadata = state.localMetadata
                    )
                }
                _coreInfoList.value = merged
            } finally {
                if (refreshTicket.get() == ticket) {
                    hasLoadedCoreInfoOnce = true
                    _isCoreInfoLoading.value = false
                }
            }
        }
    }

    private data class LoadedCoreState(
        val info: CoreInfo,
        val localMetadata: CoreSourceMetadata?
    )

    private fun loadCoreState(variant: ApiVariant, mode: RunMode): LoadedCoreState {
        val location = getCoreLocation(variant, mode)
        if (mode == RunMode.Normal) {
            NodeProjectManager.normalizeCoreLayout(location.normalDir)
        }
        val installed = hasValidCore(variant, mode)
        val version = if (installed) readLocalCoreVersion(variant, mode) else null
        val localMetadata = if (installed) readLocalCoreSourceMetadata(variant, mode) else null
        val desiredSourceText = if (variant == ApiVariant.Custom && installed) buildDesiredCustomSourceText() else ""
        val sourceMismatch = if (variant == ApiVariant.Custom && installed) {
            isCustomSourceMismatch(
                localMetadata = localMetadata,
                desiredRepo = resolveRepo(variant),
                desiredBranch = resolveBranch(variant)
            )
        } else {
            false
        }

        return LoadedCoreState(
            info = CoreInfo(
                variant = variant,
                version = version,
                isInstalled = installed,
                sourceMismatch = sourceMismatch,
                desiredSource = desiredSourceText.ifBlank { null }.takeIf { sourceMismatch }
            ),
            localMetadata = localMetadata
        )
    }

    private fun mergeVersionUpdateState(
        previousInfo: CoreInfo?,
        refreshedInfo: CoreInfo,
        refreshedMetadata: CoreSourceMetadata?
    ): CoreInfo {
        if (previousInfo == null ||
            previousInfo.hasVersionUpdate.not() ||
            previousInfo.availableVersion.isNullOrBlank() ||
            refreshedInfo.isInstalled.not() ||
            refreshedInfo.sourceMismatch
        ) {
            return refreshedInfo
        }

        val previousAvailable = parseAvailableVersionLabel(previousInfo.availableVersion)
        val localVersion = refreshedInfo.version?.removePrefix("v")?.trim().orEmpty()
        val localSha = refreshedMetadata?.commitSha?.trim().orEmpty()
        val stillHasUpdate = when {
            previousAvailable.commitSha.isNotBlank() && localSha.isNotBlank() ->
                commitShasEquivalent(previousAvailable.commitSha, localSha).not()
            previousAvailable.commitSha.isNotBlank() -> true
            localVersion.isBlank() || previousAvailable.version.isBlank() ->
                true
            else -> compareVersions(previousAvailable.version, localVersion) > 0
        }
        return if (stillHasUpdate) {
            refreshedInfo.copy(
                hasVersionUpdate = true,
                availableVersion = previousInfo.availableVersion
            )
        } else {
            refreshedInfo
        }
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

    private fun apiUrlCandidates(path: String): List<String> = githubRemoteService.apiUrlCandidates(path)

    private fun rawUrlCandidates(repo: String, filePath: String): List<String> =
        githubRemoteService.rawUrlCandidates(repo, filePath)

    private fun withProxyCandidates(url: String): List<String> = githubRemoteService.withProxyCandidates(url)

    private fun requestText(urls: List<String>, headers: Map<String, String>): String? =
        githubRemoteService.requestText(urls, headers)

    private fun <T> requestMapped(
        urls: List<String>,
        headers: Map<String, String>,
        mapper: (String) -> T?
    ): T? = githubRemoteService.requestMapped(urls, headers, mapper)

    private fun fetchLatestRelease(repo: String): GithubRelease? {
        return githubRemoteService.fetchLatestRelease(repo)?.let { release ->
            GithubRelease(
                tagName = release.tagName,
                name = release.name,
                body = release.body,
                publishedAt = release.publishedAt,
                zipballUrl = release.zipballUrl
            )
        }
    }

    private fun fetchVersionFromGlobals(
        repo: String,
        branches: List<String> = defaultBranchCandidates()
    ): String? = githubRemoteService.fetchVersionFromGlobals(repo, branches)

    private fun defaultBranchCandidates(): List<String> = listOf("main", "master")

    override suspend fun checkUpdate(variant: ApiVariant): GithubRelease? =
        withContext(Dispatchers.IO) {
            try {
                resolveRemoteSource(variant)?.release
            } catch (_: Exception) {
                null
            }
        }

    private fun currentCustomCoreSource(): ResolvedCustomCoreSource = settingsRepository.customCoreSource.value

    private fun resolveRepo(variant: ApiVariant): String {
        return if (variant == ApiVariant.Custom) currentCustomCoreSource().repo else variant.repo
    }

    private fun resolveBranch(variant: ApiVariant): String? {
        return if (variant == ApiVariant.Custom) {
            currentCustomCoreSource().branch.ifBlank { DEFAULT_CUSTOM_CORE_BRANCH }
        } else {
            null
        }
    }

    private fun resolveBranchCandidates(variant: ApiVariant): List<String> {
        return if (variant == ApiVariant.Custom) {
            listOf(resolveBranch(variant) ?: DEFAULT_CUSTOM_CORE_BRANCH)
        } else {
            defaultBranchCandidates()
        }
    }

    private fun resolveRemoteSource(variant: ApiVariant): CoreRemoteSource? {
        val repo = resolveRepo(variant)
        if (repo.isBlank()) return null

        val branch = resolveBranch(variant)
        if (!branch.isNullOrBlank()) {
            return fetchBranchRemoteSource(repo, branch)
                ?: throw IOException("未找到分支 $branch，请检查仓库与分支名")
        }

        fetchLatestRelease(repo)?.let { return CoreRemoteSource(release = it) }

        resolveBranchCandidates(variant).forEach { candidate ->
            fetchBranchRemoteSource(repo, candidate)?.let { return it }
        }
        return null
    }

    private fun fetchBranchRemoteSource(repo: String, branch: String): CoreRemoteSource? {
        val resolvedBranch = resolveRemoteBranchName(repo, branch) ?: return null
        val versionLabel = fetchVersionFromGlobals(repo, listOf(resolvedBranch)).orEmpty()
        val head = fetchBranchHead(repo, resolvedBranch) ?: return null

        val shortSha = head.sha.take(7)
        val branchTag = versionLabel.ifBlank { shortSha.ifBlank { resolvedBranch } }
        val branchName = buildString {
            append(resolvedBranch)
            if (shortSha.isNotBlank()) {
                append(" @ ")
                append(shortSha)
            }
        }
        return CoreRemoteSource(
            release = GithubRelease(
                tagName = branchTag,
                name = branchName,
                body = head.title,
                publishedAt = head.publishedAt,
                zipballUrl = buildBranchZipUrl(repo, resolvedBranch)
            ),
            metadata = CoreSourceMetadata(
                repo = repo,
                branch = resolvedBranch,
                commitSha = head.sha,
                commitPublishedAt = head.publishedAt,
                versionLabel = versionLabel.ifBlank { branchTag }
            )
        )
    }

    private fun resolveRemoteBranchName(repo: String, requestedBranch: String): String? {
        val normalized = requestedBranch.trim()
            .removePrefix("refs/heads/")
            .trim()
            .trim('/')
        if (normalized.isBlank()) return null

        if (fetchBranchHead(repo, normalized) != null) return normalized
        if (!fetchVersionFromGlobals(repo, listOf(normalized)).isNullOrBlank()) return normalized
        if (normalized.contains('/')) return null

        val branches = fetchBranchList(repo)
        val direct = branches.firstOrNull { it.equals(normalized, ignoreCase = true) }
        if (direct != null) return direct

        val suffixMatches = branches.filter { branchName ->
            branchName.substringAfterLast('/').equals(normalized, ignoreCase = true) ||
                branchName.endsWith("/$normalized", ignoreCase = true)
        }
        return suffixMatches.singleOrNull()
    }

    private fun fetchBranchList(repo: String): List<String> {
        return requestMapped(
            urls = apiUrlCandidates("repos/$repo/branches?per_page=100"),
            headers = mapOf(
                "Accept" to "application/vnd.github+json",
                "User-Agent" to USER_AGENT
            )
        ) { body ->
            val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonArray
                ?: return@requestMapped null
            root.mapNotNull { element ->
                ((element as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull?.trim()
                    ?.takeIf { it.isNotBlank() }
            }.takeIf { it.isNotEmpty() }
        } ?: emptyList()
    }

    private fun fetchBranchHead(repo: String, branch: String): BranchHeadInfo? {
        val encodedBranch = encodeUrlPart(branch)
        return requestMapped(
            urls = apiUrlCandidates("repos/$repo/commits/$encodedBranch"),
            headers = mapOf(
                "Accept" to "application/vnd.github+json",
                "User-Agent" to USER_AGENT
            )
        ) { body ->
            val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
                ?: return@requestMapped null
            val sha = (root["sha"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (sha.isBlank()) return@requestMapped null
            val commitObj = root["commit"] as? JsonObject
            val message = (commitObj?.get("message") as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            val title = message.lineSequence().firstOrNull()?.trim().orEmpty().ifBlank { "提交 ${sha.take(7)}" }
            val authorObj = commitObj?.get("author") as? JsonObject
            val publishedAt = (authorObj?.get("date") as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            BranchHeadInfo(
                sha = sha,
                publishedAt = publishedAt,
                title = title
            )
        }
    }

    private fun encodeUrlPart(raw: String): String {
        return URLEncoder.encode(raw, Charsets.UTF_8.name()).replace("+", "%20")
    }

    private fun buildBranchZipUrl(repo: String, branch: String): String {
        return "https://api.github.com/repos/$repo/zipball/${encodeUrlPart(branch)}"
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
                val remoteSource = resolveRemoteSource(variant) ?: return@withContext
                val remoteVersion = remoteSource.metadata?.versionLabel
                    ?.removePrefix("v")
                    ?.trim()
                    .orEmpty()
                    .ifBlank {
                        remoteSource.release.tagName.removePrefix("v").trim()
                            .ifBlank { remoteSource.release.name.removePrefix("v").trim() }
                    }
                val localVersion = info.version?.removePrefix("v")?.trim().orEmpty()
                val localMetadata = readLocalCoreSourceMetadata(variant, currentRunMode())
                val remoteSha = remoteSource.metadata?.commitSha?.trim().orEmpty()
                val localSha = localMetadata?.commitSha?.trim().orEmpty()
                val sourceMismatch = isCustomSourceMismatch(
                    localMetadata = localMetadata,
                    desiredRepo = resolveRepo(variant),
                    desiredBranch = resolveBranch(variant)
                )
                val hasVersionUpdate = when {
                    sourceMismatch -> false
                    remoteSha.isNotBlank() && localSha.isNotBlank() -> !commitShasEquivalent(remoteSha, localSha)
                    remoteVersion.isNotBlank() && localVersion.isNotBlank() ->
                        compareVersions(remoteVersion, localVersion) > 0
                    else -> false
                }
                val availableVersionLabel = buildLatestVersionLabel(remoteVersion, remoteSha)
                val desiredSource = buildDesiredCustomSourceText().ifBlank { null }
                _coreInfoList.value = _coreInfoList.value.map {
                    if (it.variant == variant) {
                        it.copy(
                            availableVersion = availableVersionLabel.ifBlank { null }.takeIf { hasVersionUpdate },
                            hasVersionUpdate = hasVersionUpdate,
                            sourceMismatch = sourceMismatch,
                            desiredSource = desiredSource.takeIf { sourceMismatch }
                        )
                    }
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
                downloadAndExtract(
                    variant = variant,
                    zipUrl = release.zipballUrl,
                    versionHint = versionHint,
                    actionLabel = "回退",
                    sourceMetadata = buildRollbackMetadata(variant, release, versionHint)
                )
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
                val commitHistory = fetchCommitHistory(repo, resolveBranchCandidates(variant))
                if (commitHistory.isNotEmpty()) return@withContext commitHistory

                fetchReleaseHistoryFromReleases(repo)
            } catch (_: Exception) {
                emptyList()
            }
        }

    private fun fetchCommitHistory(
        repo: String,
        branchCandidates: List<String>
    ): List<GithubRelease> {
        val urls = buildList {
            branchCandidates.forEach { branch ->
                addAll(apiUrlCandidates("repos/$repo/commits?sha=${encodeUrlPart(branch)}&per_page=20"))
            }
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

        return fetchCommitHistoryFromAtom(repo, branchCandidates)
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
            zipballUrl = "https://api.github.com/repos/$repo/zipball/$sha"
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

    private fun fetchCommitHistoryFromAtom(
        repo: String,
        branchCandidates: List<String>
    ): List<GithubRelease> {
        for (branch in branchCandidates) {
            val parsed = requestMapped(
                urls = withProxyCandidates("https://github.com/$repo/commits/${encodeUrlPart(branch)}.atom"),
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
                zipballUrl = "https://api.github.com/repos/$repo/zipball/$sha"
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

    private fun buildLatestVersionLabel(
        versionLabel: String,
        commitSha: String
    ): String {
        val normalizedVersion = versionLabel.trim()
        val shortSha = commitSha.trim().takeIf { it.isNotBlank() }?.take(7).orEmpty()
        return when {
            normalizedVersion.isNotBlank() && shortSha.isNotBlank() -> "$normalizedVersion@$shortSha"
            normalizedVersion.isNotBlank() -> normalizedVersion
            shortSha.isNotBlank() -> shortSha
            else -> ""
        }
    }

    private data class AvailableVersionLabel(
        val version: String = "",
        val commitSha: String = ""
    )

    private fun parseAvailableVersionLabel(value: String?): AvailableVersionLabel {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank()) return AvailableVersionLabel()
        val version = trimmed.substringBefore('@').trim()
        val commitSha = trimmed.substringAfter('@', "").trim()
        return AvailableVersionLabel(
            version = version.removePrefix("v").trim(),
            commitSha = commitSha
        )
    }

    private fun commitShasEquivalent(left: String, right: String): Boolean {
        val normalizedLeft = left.trim().lowercase()
        val normalizedRight = right.trim().lowercase()
        if (normalizedLeft.isBlank() || normalizedRight.isBlank()) return false
        return normalizedLeft == normalizedRight ||
            normalizedLeft.startsWith(normalizedRight) ||
            normalizedRight.startsWith(normalizedLeft)
    }

    private fun buildRollbackMetadata(
        variant: ApiVariant,
        release: GithubRelease,
        versionHint: String?
    ): CoreSourceMetadata? {
        val branch = resolveBranch(variant) ?: return null
        val repo = resolveRepo(variant)
        if (repo.isBlank()) return null
        val commitSha = extractArchiveCommitSha(release.zipballUrl).orEmpty()
        return CoreSourceMetadata(
            repo = repo,
            branch = branch,
            commitSha = commitSha,
            commitPublishedAt = release.publishedAt,
            versionLabel = versionHint.orEmpty()
        )
    }

    private fun extractArchiveCommitSha(zipUrl: String): String? {
        val match = Regex("""/(?:archive|zipball)/([0-9a-fA-F]{7,40})(?:\.zip)?""").find(zipUrl)
        return match?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun buildDesiredCustomSourceText(): String {
        return currentCustomCoreSource()
            .takeIf { it.isValidRepo }
            ?.sourceText
            .orEmpty()
    }

    private fun isCustomSourceMismatch(
        localMetadata: CoreSourceMetadata?,
        desiredRepo: String,
        desiredBranch: String?
    ): Boolean {
        if (desiredRepo.isBlank()) return false
        val localRepo = normalizeGithubRepo(localMetadata?.repo)
        val localBranch = normalizeGithubBranch(localMetadata?.branch)
        val targetBranch = normalizeGithubBranch(desiredBranch).ifBlank { DEFAULT_CUSTOM_CORE_BRANCH }
        return localRepo.isBlank() ||
            !localRepo.equals(desiredRepo, ignoreCase = true) ||
            localBranch.isBlank() ||
            !branchesEquivalent(localBranch, targetBranch)
    }

    private fun branchesEquivalent(localBranch: String, desiredBranch: String): Boolean {
        if (localBranch.equals(desiredBranch, ignoreCase = true)) return true
        if (!desiredBranch.contains('/')) {
            return localBranch.substringAfterLast('/').equals(desiredBranch, ignoreCase = true) ||
                localBranch.endsWith("/$desiredBranch", ignoreCase = true)
        }
        return false
    }

    private fun metadataFile(coreDir: File): File = File(coreDir, CORE_SOURCE_METADATA_FILE)

    private fun writeCoreSourceMetadata(
        coreDir: File,
        metadata: CoreSourceMetadata?
    ) {
        val file = metadataFile(coreDir)
        if (metadata == null || (metadata.repo.isBlank() && metadata.branch.isBlank() && metadata.commitSha.isBlank())) {
            runCatching { file.delete() }
            return
        }
        runCatching {
            file.writeText(json.encodeToString(metadata), Charsets.UTF_8)
        }
    }

    private fun readLocalCoreSourceMetadata(
        variant: ApiVariant,
        mode: RunMode
    ): CoreSourceMetadata? {
        val location = getCoreLocation(variant, mode)
        val candidates = buildList {
            add(metadataFile(location.normalDir))
            if (mode != RunMode.Normal) {
                add(File(location.rootDirPath, CORE_SOURCE_METADATA_FILE))
            }
        }
        return candidates.firstNotNullOfOrNull { file ->
            if (!file.exists() || !file.isFile) return@firstNotNullOfOrNull null
            runCatching {
                json.decodeFromString<CoreSourceMetadata>(file.readText(Charsets.UTF_8))
            }.getOrNull()
        }
    }

    private suspend fun installOrUpdateCore(
        variant: ApiVariant,
        actionLabel: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val remoteSource = resolveRemoteSource(variant)
                ?: return@withContext Result.failure(Exception("无法获取版本信息"))
            val release = remoteSource.release
            val versionHint = remoteSource.metadata?.versionLabel?.ifBlank { null } ?: release.tagName.ifBlank {
                release.name.ifBlank { "" }
            }.ifBlank { null }
            downloadAndExtract(
                variant = variant,
                zipUrl = release.zipballUrl,
                versionHint = versionHint,
                actionLabel = actionLabel,
                sourceMetadata = remoteSource.metadata
            )
            persistResolvedCustomSourceIfNeeded(variant, remoteSource.metadata)
            refreshCoreInfo()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun persistResolvedCustomSourceIfNeeded(
        variant: ApiVariant,
        metadata: CoreSourceMetadata?
    ) {
        if (variant != ApiVariant.Custom || metadata == null) return
        val normalizedRepo = normalizeGithubRepo(metadata.repo)
        val normalizedBranch = normalizeGithubBranch(metadata.branch).ifBlank { DEFAULT_CUSTOM_CORE_BRANCH }
        val currentRepo = resolveRepo(variant)
        val currentBranch = resolveBranch(variant).orEmpty()
        if (normalizedRepo.equals(currentRepo, ignoreCase = true) &&
            normalizedBranch.equals(currentBranch, ignoreCase = true)
        ) {
            return
        }
        settingsRepository.saveCustomCoreSource(
            repoInput = normalizedRepo,
            branchInput = normalizedBranch
        )
    }

    private fun downloadAndExtract(
        variant: ApiVariant,
        zipUrl: String,
        versionHint: String?,
        actionLabel: String,
        sourceMetadata: CoreSourceMetadata? = null
    ) {
        val mode = currentRunMode()
        val location = getCoreLocation(variant, mode)
        val targetDir = location.normalDir
        val stagingDir = createCoreTempDir(targetDir, "staging")
        var backupDir: File? = null
        var targetReplaced = false

        updateDownloadProgress(
            variant = variant,
            actionLabel = actionLabel,
            stageText = "准备下载核心包",
            progress = null,
            downloadedBytes = 0L,
            totalBytes = -1L
        )

        try {
            val candidateUrls = buildDownloadUrlCandidates(zipUrl)
            var lastFailureMessage: String? = null
            val response = candidateUrls.asSequence().mapNotNull { url ->
                try {
                    val reqBuilder = Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                    githubProxyService.applyGithubAuth(reqBuilder, url)
                    val resp = httpClient.newCall(reqBuilder.build()).execute()
                    if (resp.isSuccessful) resp else {
                        lastFailureMessage = when (resp.code) {
                            401, 403 -> "下载失败：GitHub 拒绝访问（HTTP ${resp.code}），请检查 Token、仓库权限或代理线路"
                            404 -> "下载失败：仓库、分支或版本不存在（HTTP 404）"
                            else -> "下载失败：GitHub 返回 HTTP ${resp.code}"
                        }
                        resp.close()
                        null
                    }
                } catch (e: Exception) {
                    lastFailureMessage = e.message ?: "下载失败：网络异常"
                    null
                }
            }.firstOrNull() ?: throw IOException(lastFailureMessage ?: "下载失败，请检查仓库、分支和 GitHub 线路")

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

                    val extracted = extractDanmuFolder(streamWithProgress, stagingDir)
                    if (!extracted) throw IOException("核心压缩包中未找到 danmu_api 或 danmu-api 目录")
                }
            } catch (e: Exception) {
                runCatching { stagingDir.deleteRecursively() }
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

            NodeProjectManager.normalizeCoreLayout(stagingDir)
            NodeProjectManager.ensureCorePackageJson(stagingDir, versionHint)
            writeCoreSourceMetadata(stagingDir, sourceMetadata)

            if (!NodeProjectManager.hasValidCore(stagingDir)) {
                throw IOException("核心文件不完整，缺少关键入口文件")
            }

            backupDir = replaceCoreDirectory(targetDir, stagingDir)
            targetReplaced = true

            if (mode != RunMode.Normal) {
                syncCoreDirToRoot(targetDir, location.rootDirPath)
                if (!rootHasValidCore(location.rootDirPath)) {
                    throw IOException("Root 核心同步后仍缺少关键入口文件")
                }
            }

            backupDir?.deleteRecursively()
            backupDir = null
        } catch (e: Exception) {
            if (targetReplaced) {
                runCatching { restoreCoreDirectory(targetDir, backupDir) }
                if (mode != RunMode.Normal && targetDir.exists()) {
                    runCatching { syncCoreDirToRoot(targetDir, location.rootDirPath) }
                }
            } else {
                runCatching { stagingDir.deleteRecursively() }
            }
            throw e
        } finally {
            _downloadProgress.value = CoreDownloadProgress()
        }
    }

    private fun buildDownloadUrlCandidates(zipUrl: String): List<String> {
        val preferDirectFirst = zipUrl.contains("://api.github.com/")
        return if (preferDirectFirst) {
            listOf(zipUrl).plus(withProxyCandidates(zipUrl)).distinct()
        } else {
            withProxyCandidates(zipUrl)
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

    private fun createCoreTempDir(targetDir: File, suffix: String): File {
        val parentDir = targetDir.parentFile ?: throw IOException("核心目录路径无效")
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            throw IOException("无法创建核心目录: ${parentDir.absolutePath}")
        }
        val tempDir = File(parentDir, "${targetDir.name}.${suffix}-${System.currentTimeMillis()}")
        runCatching { tempDir.deleteRecursively() }
        if (!tempDir.mkdirs()) {
            throw IOException("无法创建临时目录: ${tempDir.absolutePath}")
        }
        return tempDir
    }

    private fun replaceCoreDirectory(targetDir: File, stagingDir: File): File? {
        if (!stagingDir.exists()) throw IOException("临时核心目录不存在")
        val parentDir = targetDir.parentFile ?: throw IOException("核心目录路径无效")
        var backupDir: File? = null

        if (targetDir.exists()) {
            backupDir = File(parentDir, "${targetDir.name}.backup-${System.currentTimeMillis()}")
            runCatching { backupDir.deleteRecursively() }
            moveDirectory(targetDir, backupDir)
        }

        try {
            moveDirectory(stagingDir, targetDir)
        } catch (e: Exception) {
            restoreCoreDirectory(targetDir, backupDir)
            throw e
        }
        return backupDir
    }

    private fun restoreCoreDirectory(targetDir: File, backupDir: File?) {
        val backup = backupDir ?: return
        if (!backup.exists()) return
        runCatching { if (targetDir.exists()) targetDir.deleteRecursively() }
        moveDirectory(backup, targetDir)
    }

    private fun moveDirectory(sourceDir: File, targetDir: File) {
        if (!sourceDir.exists()) return
        targetDir.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) {
                throw IOException("无法创建目录: ${parent.absolutePath}")
            }
        }
        if (sourceDir.renameTo(targetDir)) return
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        copyDirectoryOrThrow(sourceDir, targetDir)
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
        val packageCandidates = listOf(
            "$rootDirPath/package.json",
            "$rootDirPath/danmu_api/package.json",
            "$rootDirPath/danmu-api/package.json"
        )
        val allCandidates = globalsCandidates + packageCandidates
        val candidateArgs = allCandidates.joinToString(separator = " ") { shellQuote(it) }
        val script = """
            for FILE in \
            $candidateArgs
            do
              [ -f "${'$'}FILE" ] || continue
              sed -n '1,220p' "${'$'}FILE" 2>/dev/null || cat "${'$'}FILE" 2>/dev/null || true
              printf '\n'
            done
        """.trimIndent()
        val result = RootShell.exec(script, timeoutMs = 4500L)
        if (!result.ok) return null
        val text = result.stdout
        if (text.isBlank()) return null

        val version = CoreVersionParser.extractVersion(text)
        return version?.removePrefix("v")?.takeIf { it.isNotBlank() }
    }

    private fun parseVersionFromSource(text: String): String? {
        return CoreVersionParser.extractSourceVersion(text)
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
                // 核心目录允许用户手工替换；这里不能在覆盖过程中递归介入，
                // 否则 refreshCoreInfo() -> normalizeCoreLayout() 会对半写入目录做结构整理。
                rel.startsWith("danmu_api_stable/") ||
                rel == "danmu_api_stable" ||
                rel.startsWith("danmu_api_dev/") ||
                rel == "danmu_api_dev" ||
                rel.startsWith("danmu_api_custom/") ||
                rel == "danmu_api_custom" ||
                rel.startsWith("logs/") ||
                rel == "logs" ||
                rel.startsWith(".cache/") ||
                rel == ".cache" ||
                rel.startsWith("node_modules/") ||
                rel == "node_modules"
        }
    }

}

internal fun copyDirectoryOrThrow(
    sourceDir: File,
    targetDir: File,
    copyBlock: (File, File) -> Boolean = { src, dst -> src.copyRecursively(dst, overwrite = true) },
    cleanupBlock: (File) -> Boolean = { dir -> dir.deleteRecursively() }
) {
    val copied = copyBlock(sourceDir, targetDir)
    if (!copied) {
        runCatching { if (targetDir.exists()) targetDir.deleteRecursively() }
        throw IOException("复制目录失败: ${sourceDir.absolutePath} -> ${targetDir.absolutePath}")
    }
    if (!cleanupBlock(sourceDir)) {
        throw IOException("无法清理目录: ${sourceDir.absolutePath}")
    }
}
