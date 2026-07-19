package com.example.danmuapiapp.data.repository

import android.content.Context
import com.example.danmuapiapp.data.remote.github.GithubRemoteService
import com.example.danmuapiapp.data.service.GithubProxyService
import com.example.danmuapiapp.domain.model.ApiVariant
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
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
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
internal data class RuntimePackIndex(
    val schema: Int = 0,
    val channel: String = "",
    val source: RuntimePackSource = RuntimePackSource(),
    val entries: Map<String, RuntimePackEntry> = emptyMap(),
    val dependencyEntries: Map<String, String> = emptyMap()
)

@Serializable
internal data class RuntimePackSource(
    val repo: String = "",
    val branch: String = ""
)

@Serializable
internal data class RuntimePackEntry(
    val channel: String = "",
    val coreRepo: String = "",
    val coreBranch: String = "",
    val coreSha: String = "",
    val coreVersion: String = "",
    val runtimeProtocol: Int = 0,
    val dependencyFingerprint: String = "",
    val artifactUrl: String = "",
    val artifactSha256: String = "",
    val artifactSize: Long = 0L,
    val manifestSha256: String = "",
    val packages: List<RuntimePackPackage> = emptyList()
)

@Serializable
internal data class RuntimePackPackage(
    val name: String = "",
    val version: String = "",
    val integrity: String? = null,
    val path: String = ""
)

@Serializable
internal data class RuntimePackManifest(
    val schema: Int = 0,
    val channel: String = "",
    val coreRepo: String = "",
    val coreBranch: String = "",
    val coreSha: String = "",
    val coreVersion: String = "",
    val runtimeProtocol: Int = 0,
    val nodeMajor: Int = 0,
    val dependencyFingerprint: String = "",
    val packages: List<RuntimePackPackage> = emptyList(),
    val files: List<RuntimePackFile> = emptyList()
)

@Serializable
internal data class RuntimePackFile(
    val path: String = "",
    val size: Long = -1L,
    val sha256: String = ""
)

internal data class RuntimePackInstallResult(
    val applicable: Boolean,
    val unavailableReason: String? = null,
    val installedPackageCount: Int = 0
)

/**
 * Downloads and installs only signed, pure-JS packs derived from the trusted
 * stable or development source selected by [ApiVariant]. Custom cores never
 * query the pack repository. Malformed or cross-channel artifacts fail closed.
 */
@Singleton
class RuntimeDependencyPackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val githubRemoteService: GithubRemoteService,
    private val githubProxyService: GithubProxyService
) {
    companion object {
        private const val USER_AGENT = "DanmuApiApp"
        private const val CACHE_DIR_NAME = "danmu-runtime-dependency-packs"
        private const val CACHE_FILE_PREFIX = "pack-"
        private const val SHA256_PATTERN = "[0-9a-f]{64}"
        private const val CORE_SHA_PATTERN = "[0-9a-f]{40}"
        private const val ARTIFACT_PREFIX =
            "https://github.com/${RuntimeDependencyPackProtocol.PACK_REPO}/releases/download/"

        // RSA public key corresponding to the private key stored only in the
        // pack repository's GitHub Actions secret.
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

        internal fun selectEntryForFingerprint(
            index: RuntimePackIndex,
            fingerprint: String,
            preferredCoreSha: String = ""
        ): RuntimePackEntry? {
            val normalizedFingerprint = fingerprint.trim().lowercase()
            if (!Regex(SHA256_PATTERN).matches(normalizedFingerprint)) return null

            val normalizedPreferredSha = preferredCoreSha.trim().lowercase()
            val preferred = index.entries[normalizedPreferredSha]
            if (preferred?.dependencyFingerprint == normalizedFingerprint) return preferred

            val mappedSha = index.dependencyEntries[normalizedFingerprint]
            val mapped = mappedSha?.let(index.entries::get)
            if (mapped?.dependencyFingerprint == normalizedFingerprint) return mapped

            return null
        }

        internal fun readLimited(input: InputStream, maxBytes: Int): ByteArray {
            require(maxBytes > 0) { "maxBytes 必须大于 0" }
            val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE * 4))
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) {
                    throw IOException("响应超过允许大小")
                }
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }

        internal fun verifyIndexSignature(
            indexBytes: ByteArray,
            signatureText: String,
            publicKeyPem: String = TRUSTED_PUBLIC_KEY_PEM
        ): Boolean {
            return runCatching {
                val encodedKey = publicKeyPem
                    .lineSequence()
                    .map { line -> line.trim() }
                    .filter { line -> line.isNotBlank() && !line.startsWith("-----") }
                    .joinToString("")
                val keyBytes = encodedKey.decodeBase64()?.toByteArray()
                    ?: return@runCatching false
                val publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(X509EncodedKeySpec(keyBytes))
                val signatureBytes = signatureText.trim().decodeBase64()?.toByteArray()
                    ?: return@runCatching false
                Signature.getInstance("SHA256withRSA").run {
                    initVerify(publicKey)
                    update(indexBytes)
                    verify(signatureBytes)
                }
            }.getOrDefault(false)
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    internal fun installIfAvailable(
        coreDir: File,
        variant: ApiVariant,
        coreSha: String = "",
        onProgress: (stage: String, progress: Float?, downloadedBytes: Long, totalBytes: Long) -> Unit =
            { _, _, _, _ -> }
    ): RuntimePackInstallResult {
        val normalizedSha = coreSha.trim().lowercase()

        val dependencies = RuntimeDependencyPackProtocol.readCoreDependencies(coreDir)
        if (dependencies.isEmpty()) {
            return RuntimePackInstallResult(applicable = true)
        }
        val channel = RuntimeDependencyPackProtocol.channelForVariant(variant)
            ?: return RuntimePackInstallResult(
                applicable = false,
                unavailableReason = "自定义核心不使用稳定版或开发版依赖仓库"
            )
        val fingerprint = RuntimeDependencyPackProtocol.dependencyFingerprint(dependencies)
        onProgress("正在检查${channel.label}签名运行时依赖", null, 0L, -1L)

        val index = try {
            fetchSignedIndex(channel)
        } catch (error: PackIntegrityException) {
            throw error
        } catch (error: Exception) {
            return RuntimePackInstallResult(
                applicable = true,
                unavailableReason = error.message ?: "${channel.label}依赖索引暂时不可用"
            )
        } ?: return RuntimePackInstallResult(
            applicable = true,
            unavailableReason = "${channel.label}签名运行时依赖索引暂时不可用"
        )

        validateIndex(index, channel)
        val preferredSha = normalizedSha.takeIf { Regex(CORE_SHA_PATTERN).matches(it) }.orEmpty()
        val entry = selectEntryForFingerprint(index, fingerprint, preferredSha)
            ?: return RuntimePackInstallResult(
                applicable = true,
                unavailableReason = "${channel.label}依赖仓库未收录当前核心的依赖指纹 ${fingerprint.take(12)}"
            )
        validateEntry(entry, fingerprint, channel)

        val archive = obtainArchive(entry, channel, onProgress)
        onProgress("正在校验${channel.label}运行时依赖", 1f, archive.length(), archive.length())
        RuntimePackArchiveInstaller.verifyAndInstall(archive, entry, coreDir)
        return RuntimePackInstallResult(
            applicable = true,
            installedPackageCount = entry.packages.size
        )
    }

    private fun fetchSignedIndex(channel: RuntimePackChannel): RuntimePackIndex? {
        val indexBytes = requestLimitedBytes(
            urls = githubRemoteService.rawUrlCandidates(
                RuntimeDependencyPackProtocol.PACK_REPO,
                channel.indexPath
            ),
            maxBytes = RuntimeDependencyPackProtocol.MAX_INDEX_BYTES
        ) ?: return null
        val signatureBytes = requestLimitedBytes(
            urls = githubRemoteService.rawUrlCandidates(
                RuntimeDependencyPackProtocol.PACK_REPO,
                channel.indexSignaturePath
            ),
            maxBytes = RuntimeDependencyPackProtocol.MAX_INDEX_SIGNATURE_BYTES
        ) ?: return null
        val indexText = indexBytes.toString(Charsets.UTF_8)
        val signatureText = signatureBytes.toString(Charsets.UTF_8)
        if (!verifyIndexSignature(indexBytes, signatureText)) {
            throw PackIntegrityException("${channel.label}运行时依赖索引签名校验失败")
        }
        return runCatching {
            json.decodeFromString<RuntimePackIndex>(indexText)
        }.getOrElse { error ->
            throw PackIntegrityException("${channel.label}运行时依赖索引格式无效", error)
        }
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
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use
                        val body = response.body
                        val contentLength = body.contentLength()
                        if (contentLength > maxBytes.toLong()) {
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

    private fun validateIndex(index: RuntimePackIndex, channel: RuntimePackChannel) {
        if (index.schema != RuntimeDependencyPackProtocol.INDEX_SCHEMA ||
            index.channel != channel.key ||
            !RuntimeDependencyPackProtocol.isTrustedCoreSource(
                channel,
                index.source.repo,
                index.source.branch
            )
        ) {
            throw PackIntegrityException("${channel.label}运行时依赖索引通道或来源无效")
        }
        index.entries.forEach { (coreSha, entry) ->
            if (!Regex(CORE_SHA_PATTERN).matches(coreSha) ||
                entry.coreSha.lowercase() != coreSha ||
                entry.channel != channel.key ||
                !RuntimeDependencyPackProtocol.isTrustedCoreSource(
                    channel,
                    entry.coreRepo,
                    entry.coreBranch
                )
            ) {
                throw PackIntegrityException("${channel.label}运行时依赖索引包含跨通道或无效 entry")
            }
        }
        index.dependencyEntries.forEach { (fingerprint, coreSha) ->
            if (!Regex(SHA256_PATTERN).matches(fingerprint) || !Regex(CORE_SHA_PATTERN).matches(coreSha)) {
                throw PackIntegrityException("${channel.label}运行时依赖指纹映射格式无效")
            }
            val entry = index.entries[coreSha]
                ?: throw PackIntegrityException("${channel.label}运行时依赖指纹映射指向不存在的依赖包")
            if (entry.dependencyFingerprint != fingerprint) {
                throw PackIntegrityException("${channel.label}运行时依赖指纹映射与依赖包不一致")
            }
        }
    }

    private fun validateEntry(
        entry: RuntimePackEntry,
        expectedFingerprint: String,
        channel: RuntimePackChannel
    ) {
        if (entry.channel != channel.key ||
            !RuntimeDependencyPackProtocol.isTrustedCoreSource(
                channel,
                entry.coreRepo,
                entry.coreBranch
            )
        ) {
            throw PackIntegrityException("依赖包 entry 与${channel.label}核心来源不一致")
        }
        val sourceSha = entry.coreSha.lowercase()
        if (!Regex(CORE_SHA_PATTERN).matches(sourceSha)) {
            throw PackIntegrityException("依赖包缺少有效的${channel.label}来源 SHA")
        }
        if (entry.runtimeProtocol != RuntimeDependencyPackProtocol.RUNTIME_PROTOCOL) {
            throw PackIntegrityException("依赖包协议版本不兼容：${entry.runtimeProtocol}")
        }
        if (entry.dependencyFingerprint != expectedFingerprint) {
            throw PackIntegrityException("依赖包依赖指纹与核心 package.json 不一致")
        }
        if (!Regex(SHA256_PATTERN).matches(entry.artifactSha256) ||
            !Regex(SHA256_PATTERN).matches(entry.manifestSha256)
        ) {
            throw PackIntegrityException("依赖包缺少有效 SHA-256")
        }
        val shortSha = sourceSha.take(12)
        val expectedUrl = "$ARTIFACT_PREFIX" +
            "${channel.key}-core-$shortSha/runtime-pack-${channel.key}-$shortSha.zip"
        if (entry.artifactUrl != expectedUrl) {
            throw PackIntegrityException("依赖包下载地址与${channel.label}通道不一致")
        }
        val uri = runCatching { URI(entry.artifactUrl) }.getOrNull()
        if (uri?.scheme != "https" || uri.host != "github.com") {
            throw PackIntegrityException("依赖包下载地址必须使用 HTTPS GitHub 地址")
        }
        if (entry.artifactSize <= 0L || entry.artifactSize > RuntimeDependencyPackProtocol.MAX_ARCHIVE_BYTES) {
            throw PackIntegrityException("依赖包大小不在允许范围内")
        }
    }

    private fun obtainArchive(
        entry: RuntimePackEntry,
        channel: RuntimePackChannel,
        onProgress: (stage: String, progress: Float?, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): File {
        val cacheDir = File(context.filesDir, CACHE_DIR_NAME).apply { mkdirs() }
        val cached = File(
            cacheDir,
            "$CACHE_FILE_PREFIX${channel.key}-${entry.artifactSha256}.zip"
        )
        if (cached.isFile && cached.length() == entry.artifactSize &&
            RuntimeDependencyPackProtocol.sha256(cached) == entry.artifactSha256
        ) {
            return cached
        }
        runCatching { cached.delete() }

        val temporary = File.createTempFile("runtime-pack-", ".download", cacheDir)
        try {
            val urls = githubRemoteService.withProxyCandidates(entry.artifactUrl)
            var lastFailure: String? = null
            var downloaded = false
            for (url in urls.distinct()) {
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
                        val contentLength = body.contentLength()
                        if (contentLength > RuntimeDependencyPackProtocol.MAX_ARCHIVE_BYTES) {
                            throw PackIntegrityException("依赖包超过大小上限")
                        }
                        var count = 0L
                        onProgress("正在下载${channel.label}运行时依赖", 0f, 0L, entry.artifactSize)
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
                                    val progress = (count.toFloat() / entry.artifactSize.toFloat())
                                        .coerceIn(0f, 1f)
                                    onProgress(
                                        "正在下载${channel.label}运行时依赖",
                                        progress,
                                        count,
                                        entry.artifactSize
                                    )
                                }
                            }
                        }
                        if (count != entry.artifactSize) {
                            throw PackIntegrityException("依赖包大小校验失败")
                        }
                        if (RuntimeDependencyPackProtocol.sha256(temporary) != entry.artifactSha256) {
                            throw PackIntegrityException("依赖包 SHA-256 校验失败")
                        }
                        downloaded = true
                    }
                } catch (error: PackIntegrityException) {
                    lastFailure = error.message ?: "完整性校验失败"
                } catch (error: Exception) {
                    lastFailure = error.message ?: error::class.java.simpleName
                }
                if (downloaded) break
            }
            if (!downloaded) {
                throw IOException("下载${channel.label}运行时依赖失败：${lastFailure ?: "网络异常"}")
            }
            if (!temporary.renameTo(cached)) {
                temporary.copyTo(cached, overwrite = true)
                if (!temporary.delete()) {
                    throw IOException("无法清理依赖包临时文件")
                }
            }
            return cached
        } catch (error: Exception) {
            runCatching { temporary.delete() }
            throw error
        }
    }

    private class PackIntegrityException(message: String, cause: Throwable? = null) :
        IOException(message, cause)
}
