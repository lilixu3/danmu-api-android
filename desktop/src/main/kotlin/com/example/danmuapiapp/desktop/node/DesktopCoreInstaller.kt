package com.example.danmuapiapp.desktop.node

import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Windows 桌面端核心安装器。
 *
 * 与 Android 端一致：danmu_api 核心（stable/dev 变体）**不随包内置**，
 * 首次启动时从 GitHub 在线下载 zipball 并解压到运行时目录（scriptDir/danmu_api_stable）。
 * 包内只内置 Node 宿主、polyfill 与 node_modules（与 APK 的 runtime_asset_layout 对齐）。
 *
 * P0 仅实现默认分支下载 + 本地缓存；版本比较、分支选择、更新与回退属于 W-0402。
 */
object DesktopCoreInstaller {

    const val STABLE_REPO = "huangxd-/danmu_api"
    const val DEV_REPO = "lilixu3/danmu_api"
    const val DEFAULT_BRANCH = "main"

    private const val MAX_ZIP_BYTES = 128L * 1024L * 1024L

    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    fun isCoreInstalled(scriptDir: File): Boolean {
        return File(scriptDir, "danmu_api_stable/worker.js").isFile
    }

    /** 核心缺失时在线下载安装；已安装则幂等跳过。失败抛 IOException，由监督器转为 Failed。 */
    fun ensureCoreInstalled(
        scriptDir: File,
        cacheDir: File? = null,
        repo: String = STABLE_REPO,
        branch: String = DEFAULT_BRANCH,
    ) {
        if (isCoreInstalled(scriptDir)) return
        val zip = downloadCoreZip(repo, branch, cacheDir ?: defaultCacheDir())
        extractCore(zip, File(scriptDir, "danmu_api_stable"))
        if (!isCoreInstalled(scriptDir)) {
            throw IOException("核心解压后缺少 danmu_api_stable/worker.js: $repo@$branch")
        }
    }

    private val coreDirNameRegex = Regex("^danmu[-_]api$", RegexOption.IGNORE_CASE)

    private fun defaultCacheDir(): File {
        return File(System.getProperty("java.io.tmpdir"), "danmu-desktop-core-cache")
    }

    private fun downloadCoreZip(repo: String, branch: String, cacheDir: File): File {
        val url = "https://codeload.github.com/$repo/zip/refs/heads/$branch"
        val cacheFile = File(cacheDir, repo.replace('/', '_') + "-" + branch + ".zip")
        if (cacheFile.isFile && cacheFile.length() > 1024) return cacheFile

        // 本机网络可能对 codeload 的 TLS 握手挂死，且 JDK HttpClient 的 request timeout
        // 覆盖不到连接阶段；用 sendAsync + orTimeout 强制限时，超时快速失败。
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMinutes(2))
            .GET()
            .build()
        val response = try {
            client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .orTimeout(150, TimeUnit.SECONDS)
                .join()
        } catch (t: Throwable) {
            throw IOException("核心下载超时或中断: $url", t)
        }
        if (response.statusCode() != 200) {
            throw IOException("核心下载失败 HTTP ${response.statusCode()}: $url")
        }
        val bytes = response.body()
        if (bytes.isEmpty()) throw IOException("核心下载内容为空: $url")
        if (bytes.size > MAX_ZIP_BYTES) throw IOException("核心下载超过大小上限: $url")

        cacheDir.mkdirs()
        val part = File(cacheDir, cacheFile.name + ".part")
        part.writeBytes(bytes)
        if (!part.renameTo(cacheFile)) {
            part.copyTo(cacheFile, overwrite = true)
            part.delete()
        }
        return cacheFile
    }

    /**
     * 解压并整理核心目录。语义与 Android 端 NodeProjectManager.normalizeCoreLayout
     * 对齐：仓库 zipball 的核心代码可能在根目录，也可能嵌套在 danmu_api/（或 danmu-api/）
     * 子目录中，需要把子目录内容上提到核心目录根部；并保证 package.json 存在且为
     * ESM（"type": "module"），否则宿主无法 import worker.js。
     */
    private fun extractCore(zip: File, coreDir: File) {
        coreDir.deleteRecursively()
        val staging = File(zip.parentFile, "staging-" + System.nanoTime())
        staging.mkdirs()
        try {
            val rootPrefix = staging.canonicalPath + File.separator
            ZipInputStream(zip.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    // 剥掉 zipball 的仓库根目录前缀（danmu_api-<sha>/…），解压后 staging 即仓库根
                    val stripped = entry.name.substringAfter('/', "")
                    if (stripped.isNotEmpty()) {
                        val target = File(staging, stripped)
                        if (!target.canonicalPath.startsWith(rootPrefix)) {
                            throw IOException("核心压缩包含非法路径: ${entry.name}")
                        }
                        if (entry.isDirectory) {
                            target.mkdirs()
                        } else {
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zis.copyTo(it) }
                        }
                    }
                    entry = zis.nextEntry
                }
            }
            val coreSource = if (File(staging, "worker.js").isFile) {
                staging
            } else {
                staging.listFiles()?.firstOrNull { child ->
                    child.isDirectory && coreDirNameRegex.matches(child.name) &&
                        File(child, "worker.js").isFile
                } ?: throw IOException("压缩包中找不到核心代码（worker.js）: ${zip.name}")
            }
            coreDir.mkdirs()
            coreSource.listFiles()?.forEach { child ->
                child.copyRecursively(File(coreDir, child.name), overwrite = true)
            }
            ensureEsmPackageJson(coreDir, staging)
            if (!isCoreInstalledAfterExtract(coreDir)) {
                throw IOException("核心解压后缺少 worker.js: $coreDir")
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun isCoreInstalledAfterExtract(coreDir: File): Boolean {
        return File(coreDir, "worker.js").isFile
    }

    private fun ensureEsmPackageJson(coreDir: File, repoRoot: File) {
        val pkg = File(coreDir, "package.json")
        if (!pkg.isFile) {
            val repoPkg = File(repoRoot, "package.json")
            if (repoPkg.isFile) {
                repoPkg.copyTo(pkg, overwrite = true)
            } else {
                pkg.writeText("{\n  \"type\": \"module\",\n  \"version\": \"0.0.0\"\n}\n")
                return
            }
        }
        val text = pkg.readText()
        if (!text.contains(Regex("\"type\"\\s*:\\s*\"module\""))) {
            pkg.writeText(text.replaceFirst("{", "{\n  \"type\": \"module\","))
        }
    }
}
