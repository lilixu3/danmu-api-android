package com.example.danmuapiapp.data.service

import android.app.Activity
import android.app.DownloadManager
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
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
    private val githubProxyService: GithubProxyService
) {
    companion object {
        private const val LATEST_RELEASE_API =
            "https://api.github.com/repos/lilixu3/danmu-api-android/releases/latest"
        private const val FALLBACK_LATEST_PAGE =
            "https://github.com/lilixu3/danmu-api-android/releases/latest"

        private const val PREFS_NAME = "app_update_checker"
        private const val KEY_PENDING_INSTALL_URI = "pending_install_uri"
        private const val KEY_PENDING_INSTALL_NAME = "pending_install_name"
        private const val KEY_PENDING_INSTALL_VERSION = "pending_install_version"
        private const val KEY_PENDING_INSTALL_PATH = "pending_install_path"
        private const val KEY_PENDING_INSTALL_TS = "pending_install_ts"
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
                savePendingInstall(activity, apk)
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${activity.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { activity.startActivity(intent) }
                Toast.makeText(
                    activity,
                    "请先允许“安装未知应用”，授权后返回 App 将自动继续安装",
                    Toast.LENGTH_LONG
                ).show()
                return InstallResult.NeedUnknownSourcePermission
            }
        }

        clearPendingInstall(activity)
        return launchInstaller(activity, apk.uri)
    }

    fun tryResumePendingInstall(activity: Activity): Boolean {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriStr = prefs.getString(KEY_PENDING_INSTALL_URI, null) ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            return false
        }

        val uri = runCatching { Uri.parse(uriStr) }.getOrNull()
        if (uri == null || !isUriReadable(activity, uri)) {
            clearPendingInstall(activity)
            Toast.makeText(activity, "安装包已不可用，请重新下载", Toast.LENGTH_SHORT).show()
            return false
        }

        clearPendingInstall(activity)
        return when (launchInstaller(activity, uri)) {
            is InstallResult.Launched -> true
            else -> false
        }
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
        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
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
        val apiCandidates = githubProxyService.buildUrlCandidates(LATEST_RELEASE_API)
            .plus(LATEST_RELEASE_API)
            .distinct()

        apiCandidates.forEach { apiUrl ->
            runCatching {
                val request = Request.Builder()
                    .url(apiUrl)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "DanmuApiApp")
                githubProxyService.applyGithubAuth(request, apiUrl)

                httpClient.newBuilder()
                    .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                    .callTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                    .newCall(request.build())
                    .execute()
                    .use { response ->
                        if (!response.isSuccessful) return@use
                        val body = response.body.string()
                        if (body.isBlank()) return@use
                        val json = JSONObject(body)
                        val tagName = json.optString("tag_name").orEmpty()
                        if (tagName.isBlank()) return@use

                        val htmlUrl = json.optString("html_url", FALLBACK_LATEST_PAGE)
                            .ifBlank { FALLBACK_LATEST_PAGE }
                        val releaseBody = json.optString("body").orEmpty()
                        val assets = mutableListOf<ApkAsset>()
                        val arr = json.optJSONArray("assets")
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val item = arr.optJSONObject(i) ?: continue
                                val name = item.optString("name").orEmpty()
                                val downloadUrl = item.optString("browser_download_url").orEmpty()
                                if (!name.lowercase(Locale.getDefault()).endsWith(".apk")) continue
                                if (downloadUrl.isBlank()) continue
                                assets.add(
                                    ApkAsset(
                                        name = name,
                                        url = downloadUrl,
                                        size = item.optLong("size", 0L)
                                    )
                                )
                            }
                        }

                        return ReleaseInfo(
                            tagName = tagName,
                            htmlUrl = htmlUrl,
                            body = releaseBody,
                            apkAssets = assets
                        )
                    }
            }
        }
        error("无法获取最新版本信息")
    }

    private fun sanitizeReleaseNotes(raw: String): String {
        if (raw.isBlank()) return "（无更新说明）"
        val marker = Regex("(?i)(sha256|checksum|校验|sha256sums)")
        val lines = raw.lines()
        val out = StringBuilder()
        var skipping = false
        var inCodeBlock = false

        for (line in lines) {
            val trimmed = line.trim()
            if (skipping) {
                if (trimmed.startsWith("```")) {
                    inCodeBlock = !inCodeBlock
                    if (!inCodeBlock) skipping = false
                    continue
                }
                if (!inCodeBlock && trimmed.startsWith("#")) {
                    skipping = false
                } else {
                    continue
                }
            }
            if (marker.containsMatchIn(line)) {
                skipping = true
                continue
            }
            out.append(line).append('\n')
        }

        val clean = out.toString().replace(Regex("\n{3,}"), "\n\n").trim()
        if (clean.isBlank()) return "（无更新说明）"
        return if (clean.length <= 900) clean else clean.take(900) + "\n…"
    }

    private fun selectBestApk(assets: List<ApkAsset>): ApkAsset? {
        if (assets.isEmpty()) return null
        val normalized = assets.map { it to it.name.lowercase(Locale.getDefault()) }
        val abis = Build.SUPPORTED_ABIS.map { it.lowercase(Locale.getDefault()) }

        fun findBySuffix(suffix: String): ApkAsset? {
            return normalized.firstOrNull { it.second.contains(suffix) }?.first
        }

        for (abi in abis) {
            val match = findBySuffix("-$abi.apk") ?: findBySuffix("_$abi.apk")
            if (match != null) return match
        }

        val universal = findBySuffix("-universal.apk")
            ?: normalized.firstOrNull { it.second.contains("universal") }?.first
        if (universal != null) return universal

        if (abis.any { it.startsWith("arm64") }) {
            return findBySuffix("-arm64-v8a.apk") ?: findBySuffix("-armeabi-v7a.apk") ?: assets.first()
        }
        if (abis.any { it.startsWith("armeabi") }) {
            return findBySuffix("-armeabi-v7a.apk") ?: assets.first()
        }
        if (abis.any { it.startsWith("x86_64") }) {
            return findBySuffix("-x86_64.apk") ?: findBySuffix("-x86.apk") ?: assets.first()
        }
        if (abis.any { it.startsWith("x86") }) {
            return findBySuffix("-x86.apk") ?: assets.first()
        }
        return assets.first()
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
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_INSTALL_URI, apk.uri.toString())
            .putString(KEY_PENDING_INSTALL_NAME, apk.displayName)
            .putString(KEY_PENDING_INSTALL_VERSION, apk.version)
            .putString(KEY_PENDING_INSTALL_PATH, apk.displayPath)
            .putLong(KEY_PENDING_INSTALL_TS, System.currentTimeMillis())
            .apply()
    }

    private fun clearPendingInstall(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING_INSTALL_URI)
            .remove(KEY_PENDING_INSTALL_NAME)
            .remove(KEY_PENDING_INSTALL_VERSION)
            .remove(KEY_PENDING_INSTALL_PATH)
            .remove(KEY_PENDING_INSTALL_TS)
            .apply()
    }

    private fun isUriReadable(context: Context, uri: Uri): Boolean {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.read()
            }
            true
        }.getOrDefault(false)
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
