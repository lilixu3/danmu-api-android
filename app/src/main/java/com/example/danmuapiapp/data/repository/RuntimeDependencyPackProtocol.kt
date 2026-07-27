package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.ApiVariant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.security.MessageDigest

internal object RuntimeDependencyPackProtocol {
    const val MANIFEST_SCHEMA = 3
    const val PACK_REPO = "lilixu3/danmu-api-runtime-packs"
    const val PACK_BRANCH = "main"
    const val MANIFEST_PATH = "$PACK_BRANCH/manifest.json"
    const val MANIFEST_SIGNATURE_PATH = "$PACK_BRANCH/manifest.sig"
    const val INSTALLED_MANIFEST_FILE = ".danmuapiapp-runtime-pack.json"
    const val LEGACY_INSTALLED_LOCK_FILE = ".danmuapiapp-runtime-lock.json"
    const val RUNTIME_PROTOCOL = 2
    const val EMBEDDED_NODE_MAJOR = 18
    const val MAX_MANIFEST_BYTES = 1024 * 1024
    const val MAX_MANIFEST_SIGNATURE_BYTES = 16 * 1024
    const val MAX_ARCHIVE_BYTES = 64L * 1024L * 1024L
    const val MAX_EXTRACTED_BYTES = 128L * 1024L * 1024L
    const val MAX_ARCHIVE_ENTRIES = 20_000

    private val json = Json { ignoreUnknownKeys = true }

    fun supportsOnlineRepair(variant: ApiVariant): Boolean = variant != ApiVariant.Custom

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
                if (name.isNotBlank() && spec.isNotBlank()) dependencies[name] = spec
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

    fun isSafeArchivePath(path: String): Boolean {
        if (path.isBlank() || path != path.trim() || '\\' in path || '\u0000' in path) return false
        if (path.startsWith('/') || !path.startsWith("node_modules/")) return false
        val normalized = path.removeSuffix("/")
        val parts = normalized.split('/')
        return parts.size >= 2 && parts.none { it.isBlank() || it == "." || it == ".." }
    }

    fun isNativeArtifactPath(path: String): Boolean {
        val lower = path.lowercase()
        return lower.endsWith(".node") ||
            lower.endsWith(".so") ||
            lower.endsWith(".dll") ||
            lower.endsWith(".dylib") ||
            lower.endsWith("/binding.gyp") ||
            "/prebuilds/" in lower
    }

    fun sha256(file: File): String = file.inputStream().use(::sha256)

    fun sha256(bytes: ByteArray): String = sha256(bytes.inputStream())

    private fun sha256(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
