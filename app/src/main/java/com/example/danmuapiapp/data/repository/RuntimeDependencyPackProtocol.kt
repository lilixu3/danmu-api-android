package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.ApiVariant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.security.MessageDigest

internal enum class RuntimePackChannel(
    val key: String,
    val label: String,
    val coreRepo: String,
    val coreBranch: String
) {
    Stable(
        key = "stable",
        label = "稳定版",
        coreRepo = "huangxd-/danmu_api",
        coreBranch = "main"
    ),
    Dev(
        key = "dev",
        label = "开发版",
        coreRepo = "lilixu3/danmu_api",
        coreBranch = "main"
    );

    val indexPath: String
        get() = "${RuntimeDependencyPackProtocol.PACK_BRANCH}/$key/index.json"

    val indexSignaturePath: String
        get() = "${RuntimeDependencyPackProtocol.PACK_BRANCH}/$key/index.sig"
}

internal object RuntimeDependencyPackProtocol {
    const val INDEX_SCHEMA = 2
    const val STABLE_CORE_REPO = "huangxd-/danmu_api"
    const val DEV_CORE_REPO = "lilixu3/danmu_api"
    const val CORE_BRANCH = "main"
    const val PACK_REPO = "lilixu3/danmu-api-runtime-packs"
    const val PACK_BRANCH = "main"
    const val INSTALLED_MANIFEST_FILE = ".danmuapiapp-runtime-pack.json"
    const val INSTALLED_LOCK_FILE = ".danmuapiapp-runtime-lock.json"
    const val RUNTIME_PROTOCOL = 1
    const val EMBEDDED_NODE_MAJOR = 18
    const val MAX_INDEX_BYTES = 1024 * 1024
    const val MAX_INDEX_SIGNATURE_BYTES = 16 * 1024
    const val MAX_ARCHIVE_BYTES = 64L * 1024L * 1024L
    const val MAX_EXTRACTED_BYTES = 128L * 1024L * 1024L
    const val MAX_ARCHIVE_ENTRIES = 20_000

    private val json = Json { ignoreUnknownKeys = true }

    fun channelForVariant(variant: ApiVariant): RuntimePackChannel? = when (variant) {
        ApiVariant.Stable -> RuntimePackChannel.Stable
        ApiVariant.Dev -> RuntimePackChannel.Dev
        ApiVariant.Custom -> null
    }

    fun isTrustedCoreSource(
        channel: RuntimePackChannel,
        repo: String,
        branch: String
    ): Boolean = normalizeRepo(repo).equals(channel.coreRepo, ignoreCase = true) &&
        branch.trim() == channel.coreBranch

    private fun normalizeRepo(repo: String): String =
        repo.trim().removeSuffix(".git")

    fun readCoreDependencies(coreDir: File): Map<String, String> {
        val packageJson = File(coreDir, "package.json")
        if (!packageJson.isFile) return emptyMap()
        val root = runCatching {
            json.parseToJsonElement(packageJson.readText(Charsets.UTF_8)) as? JsonObject
        }.getOrNull() ?: return emptyMap()
        val dependencies = sortedMapOf<String, String>()
        listOf("dependencies", "optionalDependencies").forEach { field ->
            val values = root[field] as? JsonObject ?: return@forEach
            values.forEach { (name, element) ->
                val spec = (element as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                if (name.isNotBlank() && spec.isNotBlank()) {
                    dependencies[name] = spec
                }
            }
        }
        return LinkedHashMap(dependencies)
    }

    fun dependencyFingerprint(dependencies: Map<String, String>): String {
        val canonical = dependencies.toSortedMap().entries.joinToString(
            separator = ",",
            prefix = "{",
            postfix = "}"
        ) { (name, spec) ->
            "${JsonPrimitive(name)}:${JsonPrimitive(spec)}"
        }
        return sha256(canonical.toByteArray(Charsets.UTF_8))
    }

    fun isSafeArchivePath(rawPath: String): Boolean {
        if (rawPath.isBlank() || rawPath != rawPath.trim()) return false
        if (rawPath.startsWith('/') || rawPath.endsWith('/') || '\\' in rawPath) return false
        val parts = rawPath.split('/')
        if (parts.any { it.isBlank() || it == "." || it == ".." }) return false
        if (rawPath == "manifest.json" || rawPath == "runtime-lock.json") return true
        return parts.size >= 2 && parts.first() == "node_modules"
    }

    fun sha256(payload: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(payload)
            .toHexString()

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHexString()
    }

    private fun ByteArray.toHexString(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
