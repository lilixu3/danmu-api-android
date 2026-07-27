package com.example.danmuapiapp.data.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

internal object RuntimePackArchiveInstaller {
    private val json = Json { ignoreUnknownKeys = true }

    private data class InstalledPackage(
        val name: String,
        val version: String,
        val path: String
    )

    fun verifyAndInstall(
        archive: File,
        manifest: RuntimePackManifest,
        manifestBytes: ByteArray,
        coreDir: File
    ) {
        if (!archive.isFile || archive.length() != manifest.artifactSize ||
            RuntimeDependencyPackProtocol.sha256(archive) != manifest.artifactSha256
        ) {
            throw IOException("依赖包大小或 SHA-256 校验失败")
        }
        val tempParent = archive.parentFile
            ?: throw IOException("依赖包缓存目录无效")
        val unpackDir = File.createTempFile("runtime-pack-unpack-", "", tempParent).apply {
            delete()
            if (!mkdirs()) throw IOException("无法创建依赖包临时目录")
        }
        try {
            extractArchive(archive, unpackDir)
            val sourceNodeModules = File(unpackDir, "node_modules")
            verifyPackageInventory(sourceNodeModules, manifest.packages)
            installVerifiedFiles(
                coreDir = coreDir,
                sourceNodeModules = sourceNodeModules,
                manifestBytes = manifestBytes
            )
        } finally {
            runCatching { unpackDir.deleteRecursively() }
        }
    }

    private fun extractArchive(archive: File, unpackDir: File) {
        val seen = HashSet<String>()
        var entryCount = 0
        var extractedBytes = 0L
        ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { input ->
            while (true) {
                val zipEntry = input.nextEntry ?: break
                entryCount += 1
                if (entryCount > RuntimeDependencyPackProtocol.MAX_ARCHIVE_ENTRIES) {
                    throw IOException("依赖包文件数量超过上限")
                }
                val name = zipEntry.name
                val isRootDirectory = zipEntry.isDirectory && name == "node_modules/"
                if (!isRootDirectory && !RuntimeDependencyPackProtocol.isSafeArchivePath(name)) {
                    throw IOException("依赖包包含不安全路径：$name")
                }
                if (zipEntry.isDirectory) {
                    input.closeEntry()
                    continue
                }
                if (RuntimeDependencyPackProtocol.isNativeArtifactPath(name)) {
                    throw IOException("依赖包包含不支持的原生文件：$name")
                }
                if (!seen.add(name)) {
                    throw IOException("依赖包包含重复路径：$name")
                }
                val destination = safeResolve(unpackDir, name)
                destination.parentFile?.mkdirs()
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        extractedBytes += read
                        if (extractedBytes > RuntimeDependencyPackProtocol.MAX_EXTRACTED_BYTES) {
                            throw IOException("依赖包解压后超过大小上限")
                        }
                        output.write(buffer, 0, read)
                    }
                }
                input.closeEntry()
            }
        }
    }

    private fun verifyPackageInventory(
        nodeModulesDir: File,
        expectedPackages: List<RuntimePackPackage>
    ) {
        if (!nodeModulesDir.isDirectory || expectedPackages.isEmpty()) {
            throw IOException("依赖包缺少 node_modules 或包清单")
        }
        val expected = LinkedHashMap<String, RuntimePackPackage>()
        expectedPackages.forEach { item ->
            if (item.name.isBlank() || item.version.isBlank() ||
                !RuntimeDependencyPackProtocol.isSafeArchivePath("${item.path}/package.json") ||
                expected.put(item.path, item) != null
            ) {
                throw IOException("依赖包包清单无效：${item.path}")
            }
        }
        val actual = collectInstalledPackages(nodeModulesDir)
        if (actual.keys != expected.keys) {
            throw IOException("依赖包实际包集合与签名清单不一致")
        }
        expected.forEach { (path, item) ->
            val installed = actual.getValue(path)
            if (installed.name != item.name || installed.version != item.version) {
                throw IOException("依赖包版本与签名清单不一致：$path")
            }
        }
    }

    private fun collectInstalledPackages(nodeModulesDir: File): Map<String, InstalledPackage> {
        val unpackRoot = nodeModulesDir.parentFile?.canonicalFile
            ?: throw IOException("依赖包解压目录无效")
        return nodeModulesDir.walkTopDown()
            .filter { it.isFile && it.name == "package.json" }
            .mapNotNull { packageJson ->
                val packageDir = packageJson.parentFile ?: return@mapNotNull null
                val relative = packageDir.relativeTo(unpackRoot).invariantSeparatorsPath
                val parts = relative.split('/')
                val marker = parts.indexOfLast { it == "node_modules" }
                if (marker < 0) return@mapNotNull null
                val packageParts = parts.drop(marker + 1)
                val isPackageRoot = if (packageParts.firstOrNull()?.startsWith('@') == true) {
                    packageParts.size == 2
                } else {
                    packageParts.size == 1
                }
                if (!isPackageRoot) return@mapNotNull null
                val root = runCatching {
                    json.parseToJsonElement(packageJson.readText(Charsets.UTF_8)) as? JsonObject
                }.getOrNull() ?: throw IOException("依赖包 package.json 无效：$relative")
                val name = (root["name"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                val version = (root["version"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                if (name.isBlank() || version.isBlank()) {
                    throw IOException("依赖包 package.json 缺少名称或版本：$relative")
                }
                InstalledPackage(name = name, version = version, path = relative)
            }
            .associateBy { it.path }
    }

    private fun installVerifiedFiles(
        coreDir: File,
        sourceNodeModules: File,
        manifestBytes: ByteArray
    ) {
        val targetNodeModules = File(coreDir, "node_modules")
        if (targetNodeModules.exists() && !targetNodeModules.deleteRecursively()) {
            throw IOException("无法替换候选核心的本地 node_modules")
        }
        targetNodeModules.parentFile?.mkdirs()
        val installed = sourceNodeModules.renameTo(targetNodeModules) ||
            sourceNodeModules.copyRecursively(targetNodeModules, overwrite = true)
        if (!installed) {
            runCatching { targetNodeModules.deleteRecursively() }
            throw IOException("无法安装候选核心依赖")
        }
        try {
            File(coreDir, RuntimeDependencyPackProtocol.INSTALLED_MANIFEST_FILE)
                .writeBytes(manifestBytes)
            runCatching {
                File(coreDir, RuntimeDependencyPackProtocol.LEGACY_INSTALLED_LOCK_FILE).delete()
            }
        } catch (error: Exception) {
            runCatching { targetNodeModules.deleteRecursively() }
            throw IOException("无法记录候选核心依赖版本", error)
        }
    }

    private fun safeResolve(root: File, relativePath: String): File {
        val rootCanonical = root.canonicalFile
        val candidate = File(rootCanonical, relativePath).canonicalFile
        val prefix = rootCanonical.path + File.separator
        if (candidate.path != rootCanonical.path && !candidate.path.startsWith(prefix)) {
            throw IOException("依赖包路径越界：$relativePath")
        }
        return candidate
    }
}
