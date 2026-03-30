package com.example.danmuapiapp.data.service

import android.app.Activity
import android.app.DownloadManager
import android.content.ClipData
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.text.Html
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.example.danmuapiapp.data.remote.github.GithubRemoteService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val githubProxyService: GithubProxyService,
    private val githubRemoteService: GithubRemoteService
) {
    companion object {
        private const val APP_REPO = "lilixu3/danmu-api-android"
        private const val FALLBACK_LATEST_PAGE =
            "https://github.com/lilixu3/danmu-api-android/releases/latest"

        private const val PREFS_NAME = "app_update_checker"
        private const val KEY_PENDING_INSTALL_URI = "pending_install_uri"
        private const val KEY_PENDING_INSTALL_NAME = "pending_install_name"
        private const val KEY_PENDING_INSTALL_VERSION = "pending_install_version"
        private const val KEY_PENDING_INSTALL_PATH = "pending_install_path"
        private const val KEY_PENDING_INSTALL_TS = "pending_install_ts"
        private const val PENDING_INSTALL_MAX_AGE_MS = 12 * 60 * 60 * 1000L
    }

    data class ApkAsset(
        val name: String,
        val url: String,
        val size: Long
    )

    data class CheckResult(
        val currentVersion: String,
        val latestVersion: String,
        val hasUpdate: Boolean,
        val releasePage: String,
        val releaseNotes: String,
        val bestAsset: ApkAsset?,
        val downloadUrls: List<String>
    )

    data class DownloadedApk(
        val uri: Uri,
        val displayName: String,
        val displayPath: String,
        val version: String,
        val sizeBytes: Long
    )

    sealed class InstallResult {
        data object Launched : InstallResult()
        data object NeedUnknownSourcePermission : InstallResult()
        data class Failed(val message: String) : InstallResult()
    }

    private data class PendingInstallRecord(
        val uri: Uri?,
        val displayName: String,
        val version: String,
        val displayPath: String,
        val savedAt: Long
    )

    fun currentVersionName(): String {
        return runCatching {
            @Suppress("DEPRECATION")
            val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            (pi.versionName ?: "").trim().ifBlank { "未知" }
        }.getOrElse { "未知" }
    }

    suspend fun checkLatestRelease(): Result<CheckResult> = withContext(Dispatchers.IO) {
        runCatching {
            val currentVersion = currentVersionName()
            val info = fetchLatestReleaseInfo()
            val latestVersion = info.tagName.removePrefix("v").removePrefix("V").trim()
            if (latestVersion.isBlank()) error("版本信息为空")

            val hasUpdate = isNewerVersion(latestVersion, currentVersion)
            val bestAsset = selectBestApk(info.apkAssets)
            val releasePage = githubProxyService
                .buildUrlCandidates(info.htmlUrl.ifBlank { FALLBACK_LATEST_PAGE })
                .plus(info.htmlUrl.ifBlank { FALLBACK_LATEST_PAGE })
                .distinct()
                .firstOrNull()
                .orEmpty()
            val downloadCandidates = bestAsset?.url?.let { original ->
                githubProxyService.buildUrlCandidates(original).plus(original).distinct()
            } ?: emptyList()

            CheckResult(
                currentVersion = currentVersion,
                latestVersion = latestVersion,
                hasUpdate = hasUpdate,
                releasePage = releasePage.ifBlank { FALLBACK_LATEST_PAGE },
                releaseNotes = sanitizeReleaseNotes(info.body),
                bestAsset = bestAsset,
                downloadUrls = downloadCandidates
            )
        }
    }

    suspend fun downloadApk(
        urls: List<String>,
        version: String,
        onProgress: (Long, Long) -> Unit
    ): Result<DownloadedApk> = withContext(Dispatchers.IO) {
        runCatching {
            val target = prepareDownloadTarget(version) ?: error("无法创建下载目录")
            clearPendingInstall(context)

            var timeoutLike = false
            val candidates = urls.distinct()
            if (candidates.isEmpty()) error("下载地址为空")

            for (url in candidates) {
                val ok = downloadOnce(url, target, onProgress)
                timeoutLike = timeoutLike || !ok
                if (ok) {
                    target.finalizeWrite(context)
                    val uri = target.toInstallUri(context)
                        ?: error("下载完成，但无法生成安装地址")
                    val size = resolveTargetSize(target)
                    return@runCatching DownloadedApk(
                        uri = uri,
                        displayName = target.displayName,
                        displayPath = target.displayPath,
                        version = version,
                        sizeBytes = size
                    )
                }
            }

            target.cleanup(context)
            if (timeoutLike) error("下载超时，请切换 GitHub 线路后重试")
            error("下载失败，请稍后重试")
        }
    }

    fun installApk(activity: Activity, apk: DownloadedApk): InstallResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    "package:${activity.packageName}".toUri()
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                val canResolve = settingsIntent.resolveActivity(activity.packageManager) != null
                if (canResolve) {
                    savePendingInstall(activity, apk)
                    runCatching { activity.startActivity(settingsIntent) }
                    Toast.makeText(activity, "请先允许安装未知应用，授权后返回将自动继续安装", Toast.LENGTH_LONG).show()
                    return InstallResult.NeedUnknownSourcePermission
                }
            }
        }
        clearPendingInstall(activity)
        return launchInstaller(activity, apk.uri)
    }

    fun tryResumePendingInstall(activity: Activity): Boolean {
        val pending = loadPendingInstall(activity) ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            return false
        }

        val candidates = buildPendingInstallCandidates(activity, pending)
        if (candidates.isEmpty()) {
            clearPendingInstall(activity)
            Toast.makeText(activity, "安装包已不可用，请重新下载", Toast.LENGTH_SHORT).show()
            return false
        }

        candidates.forEach { uri ->
            when (launchInstaller(activity, uri)) {
                is InstallResult.Launched -> {
                    clearPendingInstall(activity)
                    return true
                }
                else -> Unit
            }
        }
        return false
    }

    fun openDownloadsApp(activity: Activity) {
        runCatching {
            val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        }
    }

    fun openUrl(activity: Activity, url: String) {
        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }

    private data class ReleaseInfo(
        val tagName: String,
        val htmlUrl: String,
        val body: String,
        val apkAssets: List<ApkAsset>
    )

    private sealed class DownloadTarget {
        abstract val displayName: String
        abstract val displayPath: String
        abstract fun openOutputStream(context: Context): OutputStream?
        open fun finalizeWrite(context: Context) {}
        abstract fun toInstallUri(context: Context): Uri?
        abstract fun resolveSize(context: Context): Long
        abstract fun cleanup(context: Context)
    }

    private data class UriDownloadTarget(
        val uri: Uri,
        override val displayName: String,
        override val displayPath: String
    ) : DownloadTarget() {
        override fun openOutputStream(context: Context): OutputStream? {
            return context.contentResolver.openOutputStream(uri, "w")
        }

        override fun finalizeWrite(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                runCatching { context.contentResolver.update(uri, values, null, null) }
            }
        }

        override fun toInstallUri(context: Context): Uri? = uri

        override fun resolveSize(context: Context): Long {
            return runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0) else -1L
                } ?: -1L
            }.getOrDefault(-1L)
        }

        override fun cleanup(context: Context) {
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
    }

    private data class FileDownloadTarget(
        val file: File,
        override val displayName: String,
        override val displayPath: String
    ) : DownloadTarget() {
        override fun openOutputStream(context: Context): OutputStream? {
            return runCatching { FileOutputStream(file) }.getOrNull()
        }

        override fun toInstallUri(context: Context): Uri? {
            return runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }.getOrNull()
        }

        override fun resolveSize(context: Context): Long = file.length()

        override fun cleanup(context: Context) {
            runCatching { if (file.exists()) file.delete() }
        }
    }

    private fun fetchLatestReleaseInfo(): ReleaseInfo {
        githubRemoteService.fetchLatestRelease(APP_REPO)?.let { release ->
            return releaseInfoFromPayload(release)
        }
        return fetchLatestReleaseInfoFromHtml()
            ?: error("无法获取最新版本信息")
    }

    private fun releaseInfoFromPayload(
        release: GithubRemoteService.ReleasePayload
    ): ReleaseInfo {
        val assets = release.assets.mapNotNull { asset ->
            if (!asset.name.lowercase(Locale.getDefault()).endsWith(".apk")) return@mapNotNull null
            ApkAsset(
                name = asset.name,
                url = asset.downloadUrl,
                size = asset.size
            )
        }
        return ReleaseInfo(
            tagName = release.tagName,
            htmlUrl = release.htmlUrl.ifBlank { FALLBACK_LATEST_PAGE },
            body = release.body,
            apkAssets = assets
        )
    }

    private fun fetchLatestReleaseInfoFromHtml(): ReleaseInfo? {
        val page = githubRemoteService.requestTextResponse(
            urls = githubProxyService.buildUrlCandidates(FALLBACK_LATEST_PAGE)
                .plus(FALLBACK_LATEST_PAGE)
                .distinct(),
            headers = mapOf(
                "User-Agent" to GithubRemoteService.UserAgent,
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
        ) ?: return null

        val tagName = extractReleaseTag(page.finalUrl, page.body) ?: return null
        val releasePage = "https://github.com/$APP_REPO/releases/tag/$tagName"
        val releaseNotes = extractReleaseNotesFromHtml(page.body)
        val assets = fetchAssetsFromHtml(tagName)

        return ReleaseInfo(
            tagName = tagName,
            htmlUrl = releasePage,
            body = releaseNotes,
            apkAssets = assets
        )
    }

    private fun fetchAssetsFromHtml(tagName: String): List<ApkAsset> {
        val expandedAssetsUrl = "https://github.com/$APP_REPO/releases/expanded_assets/$tagName"
        val payload = githubRemoteService.requestTextResponse(
            urls = githubProxyService.buildUrlCandidates(expandedAssetsUrl)
                .plus(expandedAssetsUrl)
                .distinct(),
            headers = mapOf(
                "User-Agent" to GithubRemoteService.UserAgent,
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
        ) ?: return emptyList()

        val itemRegex = Regex("""<li\b.*?</li>""", setOf(RegexOption.DOT_MATCHES_ALL))
        val hrefRegex = Regex("href=\"([^\"]+\\.apk)\"")
        val nameRegex = Regex("""Truncate-text text-bold">([^<]+\.apk)""")
        val sizeRegex = Regex(
            """class="color-fg-muted text-right flex-shrink-0 flex-grow-0 ml-2 ml-sm-3 ml-md-4">([^<]+)</span>"""
        )

        return itemRegex.findAll(payload.body).mapNotNull { match ->
            val itemHtml = match.value
            val href = hrefRegex.find(itemHtml)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val name = nameRegex.find(itemHtml)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            if (href.isBlank() || name.isBlank()) return@mapNotNull null

            val sizeText = sizeRegex.find(itemHtml)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            ApkAsset(
                name = name,
                url = normalizeGithubPath(href),
                size = parseAssetSizeBytes(sizeText)
            )
        }.toList()
    }

    private fun extractReleaseTag(finalUrl: String, html: String): String? {
        val tagRegex = Regex("""releases/tag/([^/?#"'&<>]+)""")
        return sequenceOf(finalUrl, html)
            .flatMap { source ->
                tagRegex.findAll(source).mapNotNull { match ->
                    match.groupValues.getOrNull(1)?.trim()
                }
            }
            .map { htmlToPlainText(it) }
            .map { it.trim() }
            .firstOrNull { candidate ->
                candidate.isNotBlank() &&
                    candidate != "latest" &&
                    !candidate.contains('*')
            }
    }

    private fun extractReleaseNotesFromHtml(html: String): String {
        val metaRegex = Regex(
            "(?:property|name)=\\\"(?:og:description|twitter:description)\\\"\\s+content=\\\"(.*?)\\\"",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        val bodyRegex = Regex(
            """data-test-selector="body-content"[^>]*>(.*?)</div>\s*</div>""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )

        val raw = bodyRegex.find(html)?.groupValues?.getOrNull(1)
            ?: metaRegex.find(html)?.groupValues?.getOrNull(1)
            ?: ""
        return htmlToPlainText(raw, preserveStructure = bodyRegex.containsMatchIn(html))
    }

    private fun htmlToPlainText(raw: String, preserveStructure: Boolean = false): String {
        if (raw.isBlank()) return ""
        val normalizedRaw = if (preserveStructure) {
            raw
                .replace(Regex("(?i)<br\\s*/?>"), "\n")
                .replace(Regex("(?i)</(p|div|h1|h2|h3|h4|h5|h6|li|ul|ol|pre|blockquote)>"), "$0\n")
                .replace(Regex("(?i)<li[^>]*>"), "\n- ")
                .replace(Regex("(?i)<h1[^>]*>"), "\n# ")
                .replace(Regex("(?i)<h2[^>]*>"), "\n## ")
                .replace(Regex("(?i)<h3[^>]*>"), "\n### ")
                .replace(Regex("(?i)<h4[^>]*>"), "\n#### ")
                .replace(Regex("(?i)<pre[^>]*><code[^>]*>"), "\n```text\n")
                .replace(Regex("(?i)</code></pre>"), "\n```\n")
                .replace(Regex("(?i)<code[^>]*>"), "`")
                .replace(Regex("(?i)</code>"), "`")
        } else {
            raw
        }
        val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(normalizedRaw, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(normalizedRaw)
        }
        return decoded.toString()
            .replace("\u00A0", " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .trim()
    }

    private fun normalizeGithubPath(rawPath: String): String {
        val trimmed = rawPath.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("/") -> "https://github.com$trimmed"
            else -> "https://github.com/$trimmed"
        }
    }

    private fun parseAssetSizeBytes(sizeText: String): Long {
        val match = Regex("""([0-9]+(?:\.[0-9]+)?)\s*([KMG]?B)""", RegexOption.IGNORE_CASE)
            .find(sizeText.trim())
            ?: return 0L
        val value = match.groupValues.getOrNull(1)?.toDoubleOrNull() ?: return 0L
        val unit = match.groupValues.getOrNull(2)?.uppercase(Locale.US).orEmpty()
        val multiplier = when (unit) {
            "KB" -> 1024.0
            "MB" -> 1024.0 * 1024.0
            "GB" -> 1024.0 * 1024.0 * 1024.0
            else -> 1.0
        }
        return (value * multiplier).toLong()
    }

    private fun sanitizeReleaseNotes(raw: String): String {
        val clean = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        if (clean.isBlank()) return "（无更新说明）"
        return clean
    }

    private fun selectBestApk(assets: List<ApkAsset>): ApkAsset? {
        if (assets.isEmpty()) return null
        data class ParsedAsset(
            val asset: ApkAsset,
            val abiTag: String?
        )

        val parsed = assets.map { asset ->
            ParsedAsset(
                asset = asset,
                abiTag = parseAbiTagFromFileName(asset.name)
            )
        }

        fun findByAbiTag(tag: String): ApkAsset? {
            return parsed.firstOrNull { it.abiTag == tag }?.asset
        }

        // 按设备 ABI 优先级选择，确保下载到可安装架构。
        val preferredAbiOrder = buildPreferredAbiOrder(Build.SUPPORTED_ABIS.toList())
        preferredAbiOrder.forEach { abiTag ->
            findByAbiTag(abiTag)?.let { return it }
        }

        // 如果存在通用包，优先使用通用包。
        findByAbiTag("universal")?.let { return it }

        // 文件名无法识别 ABI 时，若只有一个 APK，则保留兼容旧发布行为。
        val unknownAbiAssets = parsed.filter { it.abiTag == null }.map { it.asset }
        if (unknownAbiAssets.size == 1) return unknownAbiAssets.first()

        // 若全部是已识别但不兼容的 ABI，则返回 null，避免误下错架构安装包。
        if (parsed.any { it.abiTag != null }) return null

        // 最后兜底：旧格式且无法识别时，保持原行为。
        return assets.firstOrNull()
    }

    private fun buildPreferredAbiOrder(abis: List<String>): List<String> {
        val normalizedAbis = abis.map { normalizeAbiTag(it) }.distinct()
        val order = mutableListOf<String>()
        normalizedAbis.forEach { abi ->
            when (abi) {
                "arm64-v8a" -> {
                    order += "arm64-v8a"
                    // arm64 设备通常兼容 32 位包，作为降级兜底。
                    order += "armeabi-v7a"
                }
                "armeabi-v7a", "armeabi" -> {
                    order += "armeabi-v7a"
                }
                "x86_64" -> {
                    order += "x86_64"
                    order += "x86"
                }
                "x86" -> {
                    order += "x86"
                }
            }
        }
        return order.distinct()
    }

    private fun normalizeAbiTag(raw: String): String {
        val abi = raw.trim().lowercase(Locale.getDefault())
        return when {
            abi.contains("arm64") -> "arm64-v8a"
            abi.contains("armeabi-v7a") -> "armeabi-v7a"
            abi == "armeabi" -> "armeabi"
            abi.contains("x86_64") -> "x86_64"
            abi == "x86" -> "x86"
            else -> abi
        }
    }

    private fun parseAbiTagFromFileName(fileName: String): String? {
        val name = fileName.lowercase(Locale.getDefault())
        fun hasToken(pattern: String): Boolean = Regex(pattern).containsMatchIn(name)

        return when {
            hasToken("""(^|[-_.])arm64-v8a($|[-_.])""") -> "arm64-v8a"
            hasToken("""(^|[-_.])armeabi-v7a($|[-_.])""") -> "armeabi-v7a"
            hasToken("""(^|[-_.])x86_64($|[-_.])""") -> "x86_64"
            hasToken("""(^|[-_.])x86(?!_?64)($|[-_.])""") -> "x86"
            hasToken("""(^|[-_.])(universal|noarch|all)($|[-_.])""") -> "universal"
            else -> null
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val l = normalizeVersion(latest)
        val c = normalizeVersion(current)
        if (l.isEmpty() || c.isEmpty()) return false
        val max = maxOf(l.size, c.size)
        for (i in 0 until max) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv != cv) return lv > cv
        }
        return false
    }

    private fun normalizeVersion(version: String): List<Int> {
        val main = version.trim().split("-", limit = 2).firstOrNull().orEmpty()
        if (main.isBlank()) return emptyList()
        return main.split(".")
            .mapNotNull { part ->
                part.toIntOrNull() ?: part.filter { it.isDigit() }.toIntOrNull()
            }
    }

    private fun prepareDownloadTarget(version: String): DownloadTarget? {
        val fileName = safeApkFileName(version)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            deleteExistingDownloadEntry(fileName)
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + File.separator)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = runCatching {
                context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            }.getOrNull() ?: return null
            return UriDownloadTarget(
                uri = uri,
                displayName = fileName,
                displayPath = "下载/$fileName"
            )
        }

        val hasLegacyStorage = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        val file = if (hasLegacyStorage) {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            runCatching { if (!dir.exists()) dir.mkdirs() }
            File(dir, fileName)
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            runCatching { if (!dir.exists()) dir.mkdirs() }
            File(dir, fileName)
        }
        runCatching { if (file.exists()) file.delete() }
        return FileDownloadTarget(file, fileName, file.absolutePath)
    }

    private fun deleteExistingDownloadEntry(fileName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val selection = "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?"
        val args = arrayOf(fileName, Environment.DIRECTORY_DOWNLOADS + File.separator)
        runCatching {
            context.contentResolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI, selection, args)
        }
    }

    private fun safeApkFileName(version: String): String {
        val base = version.trim().ifBlank { System.currentTimeMillis().toString() }
        val safe = base.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return "danmu-api-$safe.apk"
    }

    private fun downloadOnce(
        url: String,
        target: DownloadTarget,
        onProgress: (Long, Long) -> Unit
    ): Boolean {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "DanmuApiApp")
            .build()

        val output = target.openOutputStream(context) ?: return false
        output.use { out ->
            return runCatching {
                httpClient.newBuilder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
                    .callTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                    .newCall(request)
                    .execute()
                    .use { response ->
                        if (!response.isSuccessful) return@use false
                        val body = response.body
                        val total = body.contentLength().coerceAtLeast(-1L)
                        body.byteStream().use { input ->
                            val buf = ByteArray(16 * 1024)
                            var soFar = 0L
                            while (true) {
                                val len = input.read(buf)
                                if (len <= 0) break
                                out.write(buf, 0, len)
                                soFar += len
                                onProgress(soFar, total)
                            }
                            out.flush()
                        }
                        true
                    }
            }.getOrDefault(false)
        }
    }

    private fun resolveTargetSize(target: DownloadTarget): Long {
        return target.resolveSize(context)
    }

    private fun savePendingInstall(context: Context, apk: DownloadedApk) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_PENDING_INSTALL_URI, apk.uri.toString())
            putString(KEY_PENDING_INSTALL_NAME, apk.displayName)
            putString(KEY_PENDING_INSTALL_VERSION, apk.version)
            putString(KEY_PENDING_INSTALL_PATH, apk.displayPath)
            putLong(KEY_PENDING_INSTALL_TS, System.currentTimeMillis())
        }
    }

    private fun loadPendingInstall(context: Context): PendingInstallRecord? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedAt = prefs.getLong(KEY_PENDING_INSTALL_TS, 0L)
        if (savedAt > 0L && System.currentTimeMillis() - savedAt > PENDING_INSTALL_MAX_AGE_MS) {
            clearPendingInstall(context)
            return null
        }

        val uri = prefs.getString(KEY_PENDING_INSTALL_URI, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { it.toUri() }.getOrNull() }
        val displayName = prefs.getString(KEY_PENDING_INSTALL_NAME, null).orEmpty().trim()
        val version = prefs.getString(KEY_PENDING_INSTALL_VERSION, null).orEmpty().trim()
        val displayPath = prefs.getString(KEY_PENDING_INSTALL_PATH, null).orEmpty().trim()

        if (uri == null && displayName.isBlank() && displayPath.isBlank()) {
            clearPendingInstall(context)
            return null
        }

        return PendingInstallRecord(
            uri = uri,
            displayName = displayName,
            version = version,
            displayPath = displayPath,
            savedAt = savedAt
        )
    }

    private fun buildPendingInstallCandidates(
        context: Context,
        pending: PendingInstallRecord
    ): List<Uri> {
        val candidates = linkedSetOf<Uri>()
        pending.uri?.let { candidates += it }
        resolveInstallUriFromSavedPath(context, pending.displayPath)?.let { candidates += it }
        resolveMediaStoreInstallUri(context, pending.displayName)?.let { candidates += it }
        return candidates.toList()
    }

    private fun resolveInstallUriFromSavedPath(context: Context, rawPath: String): Uri? {
        val path = rawPath.trim()
        if (path.isEmpty()) return null
        val file = runCatching { File(path) }.getOrNull() ?: return null
        if (!file.exists() || !file.isFile) return null
        return runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }

    private fun resolveMediaStoreInstallUri(context: Context, displayName: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val name = displayName.trim()
        if (name.isEmpty()) return null
        return runCatching {
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME}=?",
                arrayOf(name),
                "${MediaStore.Downloads.DATE_ADDED} DESC"
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val id = cursor.getLong(0)
                ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            }
        }.getOrNull()
    }

    private fun clearPendingInstall(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            remove(KEY_PENDING_INSTALL_URI)
            remove(KEY_PENDING_INSTALL_NAME)
            remove(KEY_PENDING_INSTALL_VERSION)
            remove(KEY_PENDING_INSTALL_PATH)
            remove(KEY_PENDING_INSTALL_TS)
        }
    }

    private fun launchInstaller(activity: Activity, apkUri: Uri): InstallResult {
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            clipData = ClipData.newUri(activity.contentResolver, "apk", apkUri)
        }
        val pm = activity.packageManager
        val target = installIntent.takeIf { it.resolveActivity(pm) != null }
            ?: return InstallResult.Failed("未找到可用的安装器")

        return runCatching {
            activity.startActivity(target)
            InstallResult.Launched
        }.getOrElse {
            InstallResult.Failed("无法打开安装器：${it.message}")
        }
    }
}
