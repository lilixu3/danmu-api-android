package com.example.danmuapiapp.data.repository

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

internal object RuntimePackArchiveInstaller {
    private const val SHA256_PATTERN = "[0-9a-f]{64}"
    private val json = Json { ignoreUnknownKeys = true }

    fun verifyAndInstall(
        archive: File,
        entry: RuntimePackEntry,
        coreDir: File
    ) {
        val parent = coreDir.parentFile ?: throw IOException("核心目录父路径无效")
        val unpackDir = File.createTempFile("runtime-pack-unpack-", "", parent).apply {
            delete()
            if (!mkdirs()) throw IOException("无法创建依赖包临时目录")
        }
        try {
            extractArchive(archive, unpackDir)
            val manifestFile = File(unpackDir, "manifest.json")
            val runtimeLockFile = File(unpackDir, "runtime-lock.json")
            if (!manifestFile.isFile) throw IOException("依赖包缺少 manifest.json")
            if (!runtimeLockFile.isFile) throw IOException("依赖包缺少 runtime-lock.json")
            val manifestBytes = manifestFile.readBytes()
            if (RuntimeDependencyPackProtocol.sha256(manifestBytes) != entry.manifestSha256) {
                throw IOException("依赖包 manifest SHA-256 校验失败")
            }
            val manifest = runCatching {
                json.decodeFromString<RuntimePackManifest>(manifestBytes.toString(Charsets.UTF_8))
            }.getOrElse { error ->
                throw IOException("依赖包 manifest 格式无效", error)
            }
            verifyManifestIdentity(manifest, entry)
            verifyManifestFiles(unpackDir, manifest)
            installVerifiedFiles(
                coreDir = coreDir,
                sourceNodeModules = File(unpackDir, "node_modules"),
                manifestFile = manifestFile,
                runtimeLockFile = runtimeLockFile
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
                if (zipEntry.isDirectory || !RuntimeDependencyPackProtocol.isSafeArchivePath(name)) {
                    throw IOException("依赖包包含不安全路径：$name")
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

    private fun verifyManifestIdentity(
        manifest: RuntimePackManifest,
        entry: RuntimePackEntry
    ) {
        if (manifest.schema != 1 ||
            !RuntimeDependencyPackProtocol.isOfficialCoreRepo(manifest.coreRepo) ||
            manifest.coreSha.lowercase() != entry.coreSha.lowercase() ||
            manifest.runtimeProtocol != RuntimeDependencyPackProtocol.RUNTIME_PROTOCOL ||
            manifest.nodeMajor != RuntimeDependencyPackProtocol.EMBEDDED_NODE_MAJOR ||
            manifest.dependencyFingerprint != entry.dependencyFingerprint ||
            manifest.packages != entry.packages
        ) {
            throw IOException("依赖包 manifest 与索引不一致")
        }
    }

    private fun verifyManifestFiles(unpackDir: File, manifest: RuntimePackManifest) {
        if (manifest.files.isEmpty()) throw IOException("依赖包 manifest 没有文件清单")
        val expected = HashSet<String>()
        manifest.files.forEach { item ->
            if (!item.path.startsWith("node_modules/") ||
                !RuntimeDependencyPackProtocol.isSafeArchivePath(item.path) ||
                item.size < 0L ||
                !Regex(SHA256_PATTERN).matches(item.sha256) ||
                !expected.add(item.path)
            ) {
                throw IOException("依赖包 manifest 文件清单无效：${item.path}")
            }
            val file = safeResolve(unpackDir, item.path)
            if (!file.isFile) {
                throw IOException("依赖包文件缺失：${item.path}")
            }
            if (file.length() != item.size ||
                RuntimeDependencyPackProtocol.sha256(file) != item.sha256
            ) {
                throw IOException("依赖包文件校验失败：${item.path}")
            }
        }
        val actual = File(unpackDir, "node_modules")
            .walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(unpackDir).invariantSeparatorsPath }
            .toSet()
        if (actual != expected) {
            throw IOException("依赖包存在未声明文件")
        }
    }

    private fun installVerifiedFiles(
        coreDir: File,
        sourceNodeModules: File,
        manifestFile: File,
        runtimeLockFile: File
    ) {
        if (!sourceNodeModules.isDirectory) throw IOException("依赖包缺少 node_modules")
        val targetNodeModules = File(coreDir, "node_modules")
        if (targetNodeModules.exists() && !targetNodeModules.deleteRecursively()) {
            throw IOException("无法替换核心本地 node_modules")
        }
        if (!sourceNodeModules.copyRecursively(targetNodeModules, overwrite = true)) {
            throw IOException("无法安装核心本地 node_modules")
        }
        manifestFile.copyTo(
            File(coreDir, RuntimeDependencyPackProtocol.INSTALLED_MANIFEST_FILE),
            overwrite = true
        )
        runtimeLockFile.copyTo(
            File(coreDir, RuntimeDependencyPackProtocol.INSTALLED_LOCK_FILE),
            overwrite = true
        )
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
