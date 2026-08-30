package com.example.danmuapiapp.desktop.node

import com.example.danmuapiapp.desktop.core.CoreInstallProgress
import com.example.danmuapiapp.desktop.core.CoreInstallStage
import com.example.danmuapiapp.desktop.core.CoreSourceMetadata
import com.example.danmuapiapp.desktop.core.DesktopCoreInfo
import com.example.danmuapiapp.desktop.core.DesktopCoreVariant
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.Properties
import java.util.zip.ZipInputStream

/**
 * Windows core file manager. Network actions are invoked only by the Core page after the user
 * confirms a GitHub route; service startup never calls this object by default.
 */
object DesktopCoreInstaller {

    const val STABLE_REPO = "huangxd-/danmu_api"
    const val DEV_REPO = "lilixu3/danmu_api"
    const val DEFAULT_BRANCH = "main"

    private const val SOURCE_FILE = ".danmuapi-core-source.properties"
    private val coreDirNameRegex = Regex("^danmu[-_]api$", RegexOption.IGNORE_CASE)

    fun coreDir(scriptDir: File, variant: DesktopCoreVariant): File =
        File(scriptDir, "danmu_api_${variant.key}")

    fun inspect(
        scriptDir: File,
        variant: DesktopCoreVariant,
        repository: String = variant.defaultRepository.orEmpty(),
        branch: String = variant.defaultBranch,
    ): DesktopCoreInfo {
        val dir = coreDir(scriptDir, variant)
        val worker = File(dir, "worker.js")
        val installed = dir.isDirectory
        val valid = worker.isFile
        val metadata = readSourceMetadata(dir)
        val version = readCoreVersion(dir) ?: metadata?.version
        val diagnostic = when {
            !installed -> "核心尚未下载"
            !valid -> "核心目录缺少 worker.js：${worker.absolutePath}"
            metadata == null -> "核心可用，但缺少来源元数据；更新前需要重新确认仓库与分支"
            repository.isNotBlank() && metadata.repository != repository ->
                "当前来源 ${metadata.repository} 与配置来源 $repository 不一致"
            metadata.branch != branch -> "当前分支 ${metadata.branch} 与配置分支 $branch 不一致"
            else -> null
        }
        return DesktopCoreInfo(variant, installed, valid, version, metadata, diagnostic)
    }

    /** Compatibility helper retained for existing tests. */
    fun isCoreInstalled(scriptDir: File): Boolean = inspect(scriptDir, DesktopCoreVariant.Stable).valid

    /** Compatibility helper. Production UI uses [installOrReplace] after route confirmation. */
    fun ensureCoreInstalled(
        scriptDir: File,
        cacheDir: File? = null,
        githubProxyId: String = GithubProxyCatalog.ID_ORIGINAL,
        repo: String = STABLE_REPO,
        branch: String = DEFAULT_BRANCH,
    ) {
        if (isCoreInstalled(scriptDir)) return
        installOrReplace(
            scriptDir = scriptDir,
            cacheDir = cacheDir ?: defaultCacheDir(),
            variant = DesktopCoreVariant.Stable,
            repository = repo,
            branch = branch,
            githubProxyId = githubProxyId,
        )
    }

    fun installOrReplace(
        scriptDir: File,
        cacheDir: File,
        variant: DesktopCoreVariant,
        repository: String,
        branch: String,
        githubProxyId: String,
        commitSha: String? = null,
        onProgress: (CoreInstallProgress) -> Unit = {},
    ) {
        require(repository.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) {
            "核心仓库格式必须为 owner/repo：$repository"
        }
        require(branch.isNotBlank()) { "核心分支不能为空" }
        val target = coreDir(scriptDir, variant)
        val archiveRef = commitSha?.trim()?.takeIf { it.isNotEmpty() } ?: branch
        onProgress(CoreInstallProgress(variant, CoreInstallStage.Downloading, GithubProxyCatalog.optionById(githubProxyId).label))
        val zip = downloadCoreZip(repository, archiveRef, cacheDir, githubProxyId) { downloaded, total, route ->
            onProgress(
                CoreInstallProgress(
                    variant = variant,
                    stage = CoreInstallStage.Downloading,
                    routeLabel = route,
                    downloadedBytes = downloaded,
                    totalBytes = total,
                ),
            )
        }
        val staging = File(scriptDir, ".${target.name}.staging-${System.nanoTime()}")
        val backup = File(scriptDir, ".${target.name}.backup-${System.currentTimeMillis()}")
        try {
            onProgress(CoreInstallProgress(variant, CoreInstallStage.Extracting, detail = "正在解压核心文件"))
            extractCore(zip, staging)
            onProgress(CoreInstallProgress(variant, CoreInstallStage.Validating, detail = "正在校验 worker.js 与来源元数据"))
            writeSourceMetadata(
                staging,
                CoreSourceMetadata(
                    repository = repository,
                    branch = branch,
                    commitSha = commitSha,
                    version = readCoreVersion(staging),
                    installedAtEpochMillis = System.currentTimeMillis(),
                ),
            )
            onProgress(CoreInstallProgress(variant, CoreInstallStage.Replacing, detail = "正在原子替换核心目录"))
            replaceCoreDirectory(target, staging, backup)
            onProgress(CoreInstallProgress(variant, CoreInstallStage.Completed, detail = "核心文件已安装"))
        } catch (error: Throwable) {
            staging.deleteRecursively()
            if (!target.exists() && backup.exists()) restoreCoreDirectory(target, backup)
            throw IOException("核心 ${variant.label} 应用失败：${error.message}", error)
        }
    }

    fun deleteCore(scriptDir: File, variant: DesktopCoreVariant) {
        val target = coreDir(scriptDir, variant)
        if (!target.exists()) return
        if (!target.deleteRecursively() || target.exists()) {
            throw IOException("无法删除核心目录：${target.absolutePath}")
        }
    }

    private fun defaultCacheDir(): File =
        File(System.getProperty("java.io.tmpdir"), "danmu-desktop-core-cache")

    private fun downloadCoreZip(
        repo: String,
        ref: String,
        cacheDir: File,
        proxyId: String,
        onProgress: (Long, Long?, String) -> Unit = { _, _, _ -> },
    ): File {
        val originalUrl = "https://api.github.com/repos/$repo/zipball/$ref"
        val safeRef = ref.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val cacheFile = File(cacheDir, repo.replace('/', '_') + "-$safeRef-${System.nanoTime()}.zip")
        cacheDir.mkdirs()
        GithubFileDownloader.download(originalUrl, proxyId, cacheFile, onProgress = onProgress)
        if (cacheFile.length() <= 1024) {
            cacheFile.delete()
            throw IOException("核心压缩包过小，拒绝应用：${cacheFile.absolutePath}")
        }
        return cacheFile
    }

    private fun extractCore(zip: File, coreDir: File) {
        if (coreDir.exists() && !coreDir.deleteRecursively()) {
            throw IOException("无法清理核心暂存目录：${coreDir.absolutePath}")
        }
        val stagingRoot = File(zip.parentFile, "extract-${System.nanoTime()}")
        stagingRoot.mkdirs()
        try {
            val rootPrefix = stagingRoot.canonicalPath + File.separator
            ZipInputStream(zip.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val stripped = entry.name.substringAfter('/', "")
                    if (stripped.isNotEmpty()) {
                        val target = File(stagingRoot, stripped)
                        if (!target.canonicalPath.startsWith(rootPrefix)) {
                            throw IOException("核心压缩包含非法路径：${entry.name}")
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
            val source = locateCoreSource(stagingRoot)
            coreDir.mkdirs()
            source.listFiles()?.forEach { child ->
                child.copyRecursively(File(coreDir, child.name), overwrite = true)
            }
            ensureEsmPackageJson(coreDir, stagingRoot)
            if (!File(coreDir, "worker.js").isFile) {
                throw IOException("核心解压后缺少 worker.js：${coreDir.absolutePath}")
            }
        } finally {
            stagingRoot.deleteRecursively()
        }
    }

    private fun locateCoreSource(root: File): File {
        if (File(root, "worker.js").isFile) return root
        root.listFiles()?.firstOrNull { child ->
            child.isDirectory && coreDirNameRegex.matches(child.name) && File(child, "worker.js").isFile
        }?.let { return it }
        return root.walkTopDown()
            .maxDepth(3)
            .firstOrNull { it.isDirectory && coreDirNameRegex.matches(it.name) && File(it, "worker.js").isFile }
            ?: throw IOException("压缩包中找不到核心代码（worker.js）")
    }

    private fun replaceCoreDirectory(target: File, staging: File, backup: File) {
        if (target.exists()) moveDirectory(target, backup)
        try {
            moveDirectory(staging, target)
            if (!File(target, "worker.js").isFile) throw IOException("替换后的核心缺少 worker.js")
            if (backup.exists()) backup.deleteRecursively()
        } catch (error: Throwable) {
            if (target.exists()) target.deleteRecursively()
            if (backup.exists()) restoreCoreDirectory(target, backup)
            throw error
        }
    }

    private fun restoreCoreDirectory(target: File, backup: File) {
        moveDirectory(backup, target)
    }

    private fun moveDirectory(source: File, target: File) {
        target.parentFile?.mkdirs()
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            source.copyRecursively(target, overwrite = true)
            if (!source.deleteRecursively()) throw IOException("无法清理旧目录：${source.absolutePath}")
        }
    }

    private fun ensureEsmPackageJson(coreDir: File, repoRoot: File) {
        val pkg = File(coreDir, "package.json")
        if (!pkg.isFile) {
            val repoPkg = File(repoRoot, "package.json")
            if (repoPkg.isFile) repoPkg.copyTo(pkg, overwrite = true)
            else pkg.writeText("{\n  \"type\": \"module\",\n  \"version\": \"0.0.0\"\n}\n")
        }
        val text = pkg.readText()
        if (!text.contains(Regex("\"type\"\\s*:\\s*\"module\""))) {
            pkg.writeText(text.replaceFirst("{", "{\n  \"type\": \"module\","))
        }
    }

    private fun readCoreVersion(coreDir: File): String? {
        val globalsCandidates = listOf(
            File(coreDir, "configs/globals.js"),
            File(coreDir, "config/globals.js"),
        )
        globalsCandidates.firstOrNull { it.isFile }?.readText(Charsets.UTF_8)?.let { text ->
            Regex("(?m)\\b(?:VERSION|version)\\s*[:=]\\s*['\"]([^'\"]+)['\"]")
                .find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        val packageJson = File(coreDir, "package.json")
        if (packageJson.isFile) {
            return Regex("\"version\"\\s*:\\s*\"([^\"]+)\"")
                .find(packageJson.readText(Charsets.UTF_8))
                ?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
        }
        return null
    }

    private fun writeSourceMetadata(coreDir: File, metadata: CoreSourceMetadata) {
        val props = Properties().apply {
            setProperty("repository", metadata.repository)
            setProperty("branch", metadata.branch)
            metadata.commitSha?.let { setProperty("commit", it) }
            metadata.version?.let { setProperty("version", it) }
            setProperty("installed_at", metadata.installedAtEpochMillis.toString())
            setProperty("installed_at_iso", Instant.ofEpochMilli(metadata.installedAtEpochMillis).toString())
        }
        File(coreDir, SOURCE_FILE).writer(Charsets.UTF_8).use { props.store(it, "Danmu API core source") }
    }

    private fun readSourceMetadata(coreDir: File): CoreSourceMetadata? {
        val file = File(coreDir, SOURCE_FILE)
        if (!file.isFile) return null
        return try {
            val props = Properties()
            file.reader(Charsets.UTF_8).use(props::load)
            val repository = props.getProperty("repository")?.trim().orEmpty()
            val branch = props.getProperty("branch")?.trim().orEmpty()
            val installedAt = props.getProperty("installed_at")?.toLongOrNull()
            if (repository.isBlank() || branch.isBlank() || installedAt == null) {
                throw IOException("核心来源元数据缺少必填字段")
            }
            CoreSourceMetadata(
                repository = repository,
                branch = branch,
                commitSha = props.getProperty("commit")?.trim()?.takeIf { it.isNotEmpty() },
                version = props.getProperty("version")?.trim()?.takeIf { it.isNotEmpty() },
                installedAtEpochMillis = installedAt,
            )
        } catch (error: Exception) {
            throw IOException("无法读取核心来源元数据：${file.absolutePath}", error)
        }
    }
}
