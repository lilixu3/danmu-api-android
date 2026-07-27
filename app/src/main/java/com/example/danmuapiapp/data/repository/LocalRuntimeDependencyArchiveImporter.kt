package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.data.service.NodeProjectManager
import com.example.danmuapiapp.data.service.NpmVersionRange
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

internal object LocalRuntimeDependencyArchiveImporter {
    private const val MAX_WRAPPER_DEPTH = 2
    internal const val LOCAL_IMPORT_AUDIT_FILE = ".danmuapiapp-runtime-import.json"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class LocalImportAudit(
        val schema: Int = 1,
        val archiveSha256: String,
        val dependencyFingerprint: String,
        val importedAt: Long
    )

    data class InstallResult(
        val packageCount: Int
    )

    fun verifyAndInstall(
        archive: File,
        coreDir: File,
        runtimeNodeModulesDir: File
    ): InstallResult {
        if (!archive.isFile || archive.length() <= 0L) {
            throw IOException("选择的依赖压缩包为空")
        }
        if (archive.length() > RuntimeDependencyPackProtocol.MAX_ARCHIVE_BYTES) {
            throw IOException("依赖压缩包超过 ${RuntimeDependencyPackProtocol.MAX_ARCHIVE_BYTES / 1024 / 1024} MB 上限")
        }

        val parent = coreDir.parentFile ?: throw IOException("核心目录父路径无效")
        val unpackDir = File.createTempFile("local-runtime-import-", "", parent).apply {
            delete()
            if (!mkdirs()) throw IOException("无法创建依赖导入临时目录")
        }
        try {
            extractNodeModules(archive, unpackDir)
            val sourceNodeModules = File(unpackDir, "node_modules")
            val packageDirs = findPackageDirs(sourceNodeModules)
            if (packageDirs.isEmpty()) {
                throw IOException("压缩包中的 node_modules 没有可识别的 npm 包")
            }
            verifyPackageSafety(packageDirs)
            verifyDependencyClosure(packageDirs, sourceNodeModules, runtimeNodeModulesDir)

            val targetNodeModules = File(coreDir, "node_modules")
            if (targetNodeModules.exists() && !targetNodeModules.deleteRecursively()) {
                throw IOException("无法替换候选核心的本地 node_modules")
            }
            if (!sourceNodeModules.copyRecursively(targetNodeModules, overwrite = true)) {
                runCatching { targetNodeModules.deleteRecursively() }
                throw IOException("无法导入候选核心依赖")
            }

            val missing = NodeProjectManager.collectMissingRuntimeDepsForCore(
                coreDir = coreDir,
                runtimeNodeModulesDir = runtimeNodeModulesDir
            )
            if (missing.isNotEmpty()) {
                runCatching { targetNodeModules.deleteRecursively() }
                throw IOException("导入后仍缺少依赖：${missing.joinToString(", ")}")
            }

            val audit = LocalImportAudit(
                archiveSha256 = RuntimeDependencyPackProtocol.sha256(archive),
                dependencyFingerprint = RuntimeDependencyPackProtocol.dependencyFingerprint(
                    RuntimeDependencyPackProtocol.readCoreDependencies(coreDir)
                ),
                importedAt = System.currentTimeMillis()
            )
            File(coreDir, LOCAL_IMPORT_AUDIT_FILE).writeText(json.encodeToString(audit))
            return InstallResult(packageDirs.size)
        } finally {
            runCatching { unpackDir.deleteRecursively() }
        }
    }

    private fun extractNodeModules(archive: File, unpackDir: File) {
        var wrapperPrefix: String? = null
        var entryCount = 0
        var extractedBytes = 0L
        val seen = HashSet<String>()

        ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                entryCount += 1
                if (entryCount > RuntimeDependencyPackProtocol.MAX_ARCHIVE_ENTRIES) {
                    throw IOException("依赖压缩包文件数量超过上限")
                }
                val sourcePath = normalizeSourcePath(entry.name)
                if (entry.isDirectory) {
                    input.closeEntry()
                    continue
                }
                val parts = sourcePath.split('/')
                val nodeModulesIndex = parts.indexOf("node_modules")
                if (nodeModulesIndex < 0) {
                    if (parts.last() !in setOf("manifest.json", "runtime-lock.json", "package-lock.json")) {
                        throw IOException("依赖压缩包包含 node_modules 之外的文件：$sourcePath")
                    }
                    input.closeEntry()
                    continue
                }
                if (nodeModulesIndex > MAX_WRAPPER_DEPTH) {
                    throw IOException("依赖压缩包外层目录过深：$sourcePath")
                }
                val prefix = parts.take(nodeModulesIndex).joinToString("/")
                val expectedPrefix = wrapperPrefix
                if (expectedPrefix == null) {
                    wrapperPrefix = prefix
                } else if (expectedPrefix != prefix) {
                    throw IOException("依赖压缩包包含多个 node_modules 根目录")
                }
                val relativePath = parts.drop(nodeModulesIndex).joinToString("/")
                if (!isSafeNodeModulesPath(relativePath)) {
                    throw IOException("依赖压缩包路径无效：$sourcePath")
                }
                rejectNativeArtifact(relativePath)
                if (!seen.add(relativePath)) {
                    throw IOException("依赖压缩包包含重复路径：$relativePath")
                }

                val destination = safeResolve(unpackDir, relativePath)
                destination.parentFile?.mkdirs()
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        extractedBytes += read
                        if (extractedBytes > RuntimeDependencyPackProtocol.MAX_EXTRACTED_BYTES) {
                            throw IOException("依赖压缩包解压后超过大小上限")
                        }
                        output.write(buffer, 0, read)
                    }
                }
                input.closeEntry()
            }
        }
        if (wrapperPrefix == null || !File(unpackDir, "node_modules").isDirectory) {
            throw IOException("压缩包中未找到 node_modules 目录")
        }
    }

    private fun normalizeSourcePath(rawPath: String): String {
        if (rawPath.isBlank() || rawPath != rawPath.trim() || '\\' in rawPath || '\u0000' in rawPath) {
            throw IOException("依赖压缩包包含无效路径")
        }
        val normalized = rawPath.removeSuffix("/")
        if (normalized.startsWith('/')) throw IOException("依赖压缩包包含绝对路径：$rawPath")
        val parts = normalized.split('/')
        if (parts.any { it.isBlank() || it == "." || it == ".." }) {
            throw IOException("依赖压缩包包含越界路径：$rawPath")
        }
        return normalized
    }

    private fun isSafeNodeModulesPath(path: String): Boolean {
        val parts = path.split('/')
        return parts.size >= 3 && parts.first() == "node_modules" &&
            parts.none { it.isBlank() || it == "." || it == ".." }
    }

    private fun rejectNativeArtifact(path: String) {
        val lower = path.lowercase()
        if (lower.endsWith(".node") || lower.endsWith(".so") || lower.endsWith(".dll") ||
            lower.endsWith(".dylib") || lower.endsWith("/binding.gyp")
        ) {
            throw IOException("本地依赖包含不支持的原生文件：$path")
        }
    }

    private fun findPackageDirs(nodeModulesDir: File): List<File> {
        if (!nodeModulesDir.isDirectory) return emptyList()
        return nodeModulesDir.walkTopDown()
            .filter { it.isFile && it.name == "package.json" }
            .mapNotNull { it.parentFile }
            .filter(::isNodeModulePackageDir)
            .distinctBy { it.canonicalPath }
            .toList()
    }

    private fun isNodeModulePackageDir(packageDir: File): Boolean {
        val parent = packageDir.parentFile ?: return false
        if (parent.name == "node_modules") return true
        return parent.name.startsWith('@') && parent.parentFile?.name == "node_modules"
    }

    private fun verifyPackageSafety(packageDirs: List<File>) {
        packageDirs.forEach { packageDir ->
            val root = readPackageJson(packageDir)
            val scripts = root["scripts"] as? JsonObject
            listOf("preinstall", "install", "postinstall").forEach { name ->
                val command = (scripts?.get(name) as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                if (command.isNotBlank()) {
                    throw IOException("本地依赖包含安装脚本：${packageName(root, packageDir)} ($name)")
                }
            }
        }
    }

    private fun verifyDependencyClosure(
        packageDirs: List<File>,
        importedNodeModulesDir: File,
        runtimeNodeModulesDir: File
    ) {
        packageDirs.forEach { packageDir ->
            val root = readPackageJson(packageDir)
            val packageLabel = packageName(root, packageDir)
            val required = readDependencyMap(root, "dependencies") + requiredPeerDependencies(root)
            required.forEach { (name, range) ->
                val resolved = resolveDependencyPackage(
                    fromPackageDir = packageDir,
                    dependencyName = name,
                    importedNodeModulesDir = importedNodeModulesDir,
                    runtimeNodeModulesDir = runtimeNodeModulesDir
                ) ?: throw IOException("本地依赖闭包不完整：$packageLabel 缺少 $name@$range")
                val installedVersion = packageVersion(resolved)
                if (!NpmVersionRange.isSatisfied(range, installedVersion)) {
                    throw IOException(
                        "本地依赖版本不匹配：$packageLabel 需要 $name@$range，实际为 ${installedVersion.ifBlank { "未知" }}"
                    )
                }
            }
        }
    }

    private fun requiredPeerDependencies(root: JsonObject): Map<String, String> {
        val peers = readDependencyMap(root, "peerDependencies")
        val peerMeta = root["peerDependenciesMeta"] as? JsonObject ?: return peers
        return peers.filterNot { (name, _) ->
            val metadata = peerMeta[name] as? JsonObject
            (metadata?.get("optional") as? JsonPrimitive)?.booleanOrNull == true
        }
    }

    private fun resolveDependencyPackage(
        fromPackageDir: File,
        dependencyName: String,
        importedNodeModulesDir: File,
        runtimeNodeModulesDir: File
    ): File? {
        val candidates = LinkedHashSet<File>()
        candidates += File(fromPackageDir, "node_modules/$dependencyName")
        var cursor: File? = fromPackageDir.parentFile
        val importedRoot = importedNodeModulesDir.canonicalFile
        while (cursor != null && isWithin(cursor, importedRoot)) {
            if (cursor.name == "node_modules") {
                candidates += File(cursor, dependencyName)
            }
            cursor = cursor.parentFile
        }
        candidates += File(importedNodeModulesDir, dependencyName)
        candidates += File(runtimeNodeModulesDir, dependencyName)
        return candidates.firstOrNull { File(it, "package.json").isFile }
    }

    private fun readDependencyMap(root: JsonObject, field: String): Map<String, String> {
        val dependencies = root[field] as? JsonObject ?: return emptyMap()
        return buildMap {
            dependencies.forEach { (name, value) ->
                val range = (value as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                if (name.isNotBlank() && range.isNotBlank()) put(name, range)
            }
        }
    }

    private fun readPackageJson(packageDir: File): JsonObject {
        val file = File(packageDir, "package.json")
        return runCatching { json.parseToJsonElement(file.readText()).jsonObject }
            .getOrElse { throw IOException("npm 包 package.json 无效：${packageDir.name}", it) }
    }

    private fun packageName(root: JsonObject, packageDir: File): String =
        (root["name"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty().ifBlank { packageDir.name }

    private fun packageVersion(packageDir: File): String =
        (readPackageJson(packageDir)["version"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

    private fun isWithin(candidate: File, root: File): Boolean {
        val path = candidate.canonicalFile.path
        return path == root.path || path.startsWith(root.path + File.separator)
    }

    private fun safeResolve(root: File, relativePath: String): File {
        val rootCanonical = root.canonicalFile
        val candidate = File(rootCanonical, relativePath).canonicalFile
        if (!isWithin(candidate, rootCanonical)) {
            throw IOException("依赖压缩包路径越界：$relativePath")
        }
        return candidate
    }
}
