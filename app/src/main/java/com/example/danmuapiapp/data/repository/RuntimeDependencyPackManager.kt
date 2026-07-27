package com.example.danmuapiapp.data.repository

import android.content.Context
import androidx.core.content.edit
import com.example.danmuapiapp.data.remote.github.GithubRemoteService
import com.example.danmuapiapp.data.service.GithubProxyService
import com.example.danmuapiapp.data.service.NodeProjectManager
import com.example.danmuapiapp.data.service.NpmVersionRange
import com.example.danmuapiapp.domain.model.ApiVariant
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.ByteString.Companion.decodeBase64
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
internal data class RuntimePackManifest(
    val schema: Int = 0,
    val serial: Long = 0L,
    val runtimeProtocol: Int = 0,
    val nodeMajor: Int = 0,
    val runtimeLockSha256: String = "",
    val dependencyFingerprint: String = "",
    val dependencies: Map<String, String> = emptyMap(),
    val artifactUrl: String = "",
    val artifactSha256: String = "",
    val artifactSize: Long = 0L,
    val packages: List<RuntimePackPackage> = emptyList()
)

@Serializable
internal data class RuntimePackPackage(
    val name: String = "",
    val version: String = "",
    val integrity: String? = null,
    val path: String = ""
)

internal data class RuntimePackInstallResult(
    val unavailableReason: String? = null
)

private data class SignedRuntimePackManifest(
    val bytes: ByteArray,
    val manifest: RuntimePackManifest
)

/** Downloads one signed dependency closure shared by the stable and dev cores. */
@Singleton
class RuntimeDependencyPackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val githubRemoteService: GithubRemoteService,
    private val githubProxyService: GithubProxyService
) {
    companion object {
        private const val USER_AGENT = "DanmuApiApp"
        private const val LEGACY_CACHE_DIR_NAME = "danmu-runtime-dependency-packs"
        private const val SERIAL_PREFS_NAME = "runtime-dependency-pack"
        private const val SERIAL_KEY = "manifest-serial"
        private const val DOWNLOAD_PREFIX = "danmu-runtime-dependencies-"
        private const val DOWNLOAD_SUFFIX = ".zip"
        private const val STALE_DOWNLOAD_RETENTION_MS = 60L * 60L * 1000L
        private const val SHA256_PATTERN = "[0-9a-f]{64}"
        private const val ARTIFACT_PREFIX =
            "https://github.com/${RuntimeDependencyPackProtocol.PACK_REPO}/releases/download/"

        private const val TRUSTED_PUBLIC_KEY_PEM = """
            -----BEGIN PUBLIC KEY-----
            MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAw23l6/+FdYKWvwIVuczi
            ZPmPRLDXCqKjWzarqQhwjORb6/NneAYfqkzN1TnqBRZcuxESpQhdbLWfZaoUhqjX
            xCEC2J77zzchdDi+5P5RZ0HD+vLNMmDmH8ut+zBD/77dzzMYHe99AoPkUJs8Zd9W
            MbEdt4J/jmIPky7abnQi0snnMpJWZ1tZcdUqBisHj/5k30vWVTMlk/RQlvDZergf
            DzD3/dkAT847chGNIO3QFBa5DXOogJOIfeBtCwahkpEnCoNoB1NotuJPd4Ye05G6
            qN4+0HJxeUU7siHd4OsXGuDxtm6Ay/HqSSqSZx+ow/x8qhEdtQDSEhNUamblR8qL
            x5FeWN8B08rml+8AFQSBWvO7y7VFChu6t37fGuxjXqdgdqUjJwA1zy5toj5MRjSq
            VR4s8t3BGZrBEUc5WgerO9t26NlTIq6qpptdCPqh9TlanBVh0HGiV0/oNM0TU/N/
            VUsmyyO7hViS/U7pwIdYiXT0+rvwwcyLhWyzUJjI+2clAgMBAAE=
            -----END PUBLIC KEY-----
        """

        internal fun isManifestSerialAcceptable(highestSeen: Long, incoming: Long): Boolean =
            incoming >= highestSeen

        internal fun readLimited(input: InputStream, maxBytes: Int): ByteArray {
            require(maxBytes > 0) { "maxBytes 必须大于 0" }
            val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE * 4))
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) throw IOException("响应超过允许大小")
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }

        internal fun verifyManifestSignature(
            manifestBytes: ByteArray,
            signatureText: String,
            publicKeyPem: String = TRUSTED_PUBLIC_KEY_PEM
        ): Boolean {
            return runCatching {
                val encodedKey = publicKeyPem
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() && !it.startsWith("-----") }
                    .joinToString("")
                val keyBytes = encodedKey.decodeBase64()?.toByteArray()
                    ?: return@runCatching false
                val signatureBytes = signatureText.trim().decodeBase64()?.toByteArray()
                    ?: return@runCatching false
                val publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(X509EncodedKeySpec(keyBytes))
                Signature.getInstance("SHA256withRSA").run {
                    initVerify(publicKey)
                    update(manifestBytes)
                    verify(signatureBytes)
                }
            }.getOrDefault(false)
        }

        internal fun uncoveredDependencies(
            manifest: RuntimePackManifest,
            requiredDependencies: Map<String, String>
        ): List<String> {
            val topLevelVersions = manifest.packages
                .filter { item -> item.path == "node_modules/${item.name}" }
                .associate { item -> item.name to item.version }
            return requiredDependencies.mapNotNull { (name, range) ->
                val installed = topLevelVersions[name]
                if (installed != null && NpmVersionRange.isSatisfied(range, installed)) {
                    null
                } else {
                    "$name@$range"
                }
            }.sorted()
        }
    }

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    private val metadataHttpClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    internal fun installIfAvailable(
        coreDir: File,
        variant: ApiVariant,
        onProgress: (stage: String, progress: Float?, downloadedBytes: Long, totalBytes: Long) -> Unit =
            { _, _, _, _ -> }
    ): RuntimePackInstallResult {
        val requiredDependencies = NodeProjectManager.runtimeDependenciesForCore(coreDir)
        if (requiredDependencies.isEmpty()) return RuntimePackInstallResult()
        if (!RuntimeDependencyPackProtocol.supportsOnlineRepair(variant)) {
            return RuntimePackInstallResult(
                unavailableReason = "自定义核心不使用在线公共依赖包，请导入 node_modules.zip"
            )
        }

        cleanupLegacyArchiveCache()
        discardStaleDownloads()
        onProgress("正在检查签名运行时依赖", null, 0L, -1L)
        val signedManifest = try {
            fetchSignedManifest()
        } catch (error: PackIntegrityException) {
            throw error
        } catch (error: Exception) {
            return RuntimePackInstallResult(
                unavailableReason = error.message ?: "签名运行时依赖清单暂时不可用"
            )
        } ?: return RuntimePackInstallResult(
            unavailableReason = "签名运行时依赖清单暂时不可用"
        )

        val manifest = signedManifest.manifest
        validateManifest(manifest)
        val uncovered = uncoveredDependencies(manifest, requiredDependencies)
        if (uncovered.isNotEmpty()) {
            return RuntimePackInstallResult(
                unavailableReason = "公共依赖包未覆盖：${uncovered.joinToString(", ")}"
            )
        }

        val archive = downloadArchive(manifest, onProgress)
        return try {
            onProgress("正在校验运行时依赖", 1f, archive.length(), archive.length())
            RuntimePackArchiveInstaller.verifyAndInstall(
                archive = archive,
                manifest = manifest,
                manifestBytes = signedManifest.bytes,
                coreDir = coreDir
            )
            RuntimePackInstallResult()
        } finally {
            runCatching { archive.delete() }
        }
    }

    private fun fetchSignedManifest(): SignedRuntimePackManifest? {
        val manifestBytes = requestLimitedBytes(
            urls = githubRemoteService.rawUrlCandidates(
                RuntimeDependencyPackProtocol.PACK_REPO,
                RuntimeDependencyPackProtocol.MANIFEST_PATH
            ),
            maxBytes = RuntimeDependencyPackProtocol.MAX_MANIFEST_BYTES
        ) ?: return null
        val signatureBytes = requestLimitedBytes(
            urls = githubRemoteService.rawUrlCandidates(
                RuntimeDependencyPackProtocol.PACK_REPO,
                RuntimeDependencyPackProtocol.MANIFEST_SIGNATURE_PATH
            ),
            maxBytes = RuntimeDependencyPackProtocol.MAX_MANIFEST_SIGNATURE_BYTES
        ) ?: return null
        if (!verifyManifestSignature(manifestBytes, signatureBytes.toString(Charsets.UTF_8))) {
            throw PackIntegrityException("运行时依赖清单签名校验失败")
        }
        val manifest = runCatching {
            json.decodeFromString<RuntimePackManifest>(manifestBytes.toString(Charsets.UTF_8))
        }.getOrElse { error ->
            throw PackIntegrityException("运行时依赖清单格式无效", error)
        }
        return SignedRuntimePackManifest(manifestBytes, manifest)
    }

    private fun requestLimitedBytes(urls: List<String>, maxBytes: Int): ByteArray? {
        for (url in urls.distinct()) {
            repeat(2) { attempt ->
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .apply { githubProxyService.applyGithubAuth(this, url) }
                        .build()
                    metadataHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use
                        val body = response.body
                        if (body.contentLength() > maxBytes.toLong()) {
                            throw IOException("响应超过允许大小")
                        }
                        BufferedInputStream(body.byteStream()).use { input ->
                            return readLimited(input, maxBytes)
                        }
                    }
                } catch (_: Exception) {
                    if (attempt == 0) Thread.sleep(300L)
                }
            }
        }
        return null
    }

    private fun validateManifest(manifest: RuntimePackManifest) {
        if (manifest.schema != RuntimeDependencyPackProtocol.MANIFEST_SCHEMA ||
            manifest.runtimeProtocol != RuntimeDependencyPackProtocol.RUNTIME_PROTOCOL ||
            manifest.nodeMajor != RuntimeDependencyPackProtocol.EMBEDDED_NODE_MAJOR ||
            manifest.serial <= 0L ||
            manifest.dependencies.isEmpty() ||
            manifest.packages.isEmpty()
        ) {
            throw PackIntegrityException("运行时依赖清单协议不兼容")
        }
        if (!Regex(SHA256_PATTERN).matches(manifest.runtimeLockSha256) ||
            !Regex(SHA256_PATTERN).matches(manifest.dependencyFingerprint) ||
            manifest.dependencyFingerprint != RuntimeDependencyPackProtocol.dependencyFingerprint(
                manifest.dependencies
            ) ||
            !Regex(SHA256_PATTERN).matches(manifest.artifactSha256)
        ) {
            throw PackIntegrityException("运行时依赖清单哈希无效")
        }
        if (manifest.artifactSize <= 0L ||
            manifest.artifactSize > RuntimeDependencyPackProtocol.MAX_ARCHIVE_BYTES
        ) {
            throw PackIntegrityException("运行时依赖包大小不在允许范围内")
        }
        val expectedTag = "runtime-dependencies-${manifest.artifactSha256.take(12)}"
        val expectedUrl = "$ARTIFACT_PREFIX$expectedTag/node_modules.zip"
        val uri = runCatching { URI(manifest.artifactUrl) }.getOrNull()
        if (manifest.artifactUrl != expectedUrl || uri?.scheme != "https" || uri.host != "github.com") {
            throw PackIntegrityException("运行时依赖包下载地址无效")
        }

        val paths = HashSet<String>()
        manifest.packages.forEach { item ->
            if (item.name.isBlank() || item.version.isBlank() ||
                !RuntimeDependencyPackProtocol.isSafeArchivePath("${item.path}/package.json") ||
                !paths.add(item.path)
            ) {
                throw PackIntegrityException("运行时依赖包清单包含无效包：${item.path}")
            }
        }
        val topLevelVersions = manifest.packages
            .filter { item -> item.path == "node_modules/${item.name}" }
            .associate { item -> item.name to item.version }
        val invalidRoots = manifest.dependencies.filter { (name, range) ->
            val installed = topLevelVersions[name]
            installed == null || !NpmVersionRange.isSatisfied(range, installed)
        }
        if (invalidRoots.isNotEmpty()) {
            throw PackIntegrityException("运行时依赖包顶层依赖与清单不一致")
        }
        rejectRolledBackManifest(manifest.serial)
    }

    private fun rejectRolledBackManifest(incomingSerial: Long) {
        val preferences = context.getSharedPreferences(SERIAL_PREFS_NAME, Context.MODE_PRIVATE)
        val seen = runCatching { preferences.getLong(SERIAL_KEY, 0L) }.getOrDefault(0L)
        if (!isManifestSerialAcceptable(seen, incomingSerial)) {
            throw PackIntegrityException("运行时依赖清单版本回退（$seen -> $incomingSerial）")
        }
        if (incomingSerial > seen) {
            runCatching { preferences.edit { putLong(SERIAL_KEY, incomingSerial) } }
        }
    }

    private fun downloadArchive(
        manifest: RuntimePackManifest,
        onProgress: (stage: String, progress: Float?, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): File {
        val temporary = File.createTempFile(DOWNLOAD_PREFIX, DOWNLOAD_SUFFIX, context.cacheDir)
        try {
            var lastFailure: String? = null
            var downloaded = false
            for (url in githubRemoteService.withProxyCandidates(manifest.artifactUrl).distinct()) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .apply { githubProxyService.applyGithubAuth(this, url) }
                        .build()
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            lastFailure = "HTTP ${response.code}"
                            return@use
                        }
                        val body = response.body
                        if (body.contentLength() > RuntimeDependencyPackProtocol.MAX_ARCHIVE_BYTES) {
                            throw PackIntegrityException("依赖包超过大小上限")
                        }
                        var count = 0L
                        onProgress("正在下载运行时依赖", 0f, 0L, manifest.artifactSize)
                        FileOutputStream(temporary).use { output ->
                            BufferedInputStream(body.byteStream()).use { input ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    count += read
                                    if (count > RuntimeDependencyPackProtocol.MAX_ARCHIVE_BYTES) {
                                        throw PackIntegrityException("依赖包超过大小上限")
                                    }
                                    output.write(buffer, 0, read)
                                    onProgress(
                                        "正在下载运行时依赖",
                                        (count.toFloat() / manifest.artifactSize.toFloat())
                                            .coerceIn(0f, 1f),
                                        count,
                                        manifest.artifactSize
                                    )
                                }
                            }
                        }
                        if (count != manifest.artifactSize ||
                            RuntimeDependencyPackProtocol.sha256(temporary) != manifest.artifactSha256
                        ) {
                            throw PackIntegrityException("依赖包大小或 SHA-256 校验失败")
                        }
                        downloaded = true
                    }
                } catch (error: Exception) {
                    lastFailure = error.message ?: error::class.java.simpleName
                }
                if (downloaded) break
            }
            if (!downloaded) {
                throw IOException("下载运行时依赖失败：${lastFailure ?: "网络异常"}")
            }
            return temporary
        } catch (error: Exception) {
            runCatching { temporary.delete() }
            throw error
        }
    }

    private fun cleanupLegacyArchiveCache() {
        runCatching { File(context.filesDir, LEGACY_CACHE_DIR_NAME).deleteRecursively() }
    }

    private fun discardStaleDownloads() {
        val staleBefore = System.currentTimeMillis() - STALE_DOWNLOAD_RETENTION_MS
        context.cacheDir.listFiles()?.forEach { file ->
            val staleDownload = file.isFile &&
                file.name.startsWith(DOWNLOAD_PREFIX) &&
                file.name.endsWith(DOWNLOAD_SUFFIX)
            val staleUnpack = file.isDirectory && file.name.startsWith("runtime-pack-unpack-")
            if ((staleDownload || staleUnpack) && file.lastModified() < staleBefore) {
                runCatching { file.deleteRecursively() }
            }
        }
    }

    private class PackIntegrityException(message: String, cause: Throwable? = null) :
        IOException(message, cause)
}
