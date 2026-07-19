package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.ApiVariant
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RuntimeDependencyPackManagerTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `依赖包协议只绑定稳定版和开发版两个可信来源`() {
        assertEquals(2, RuntimeDependencyPackProtocol.INDEX_SCHEMA)
        assertEquals(
            "huangxd-/danmu_api",
            RuntimeDependencyPackProtocol.STABLE_CORE_REPO
        )
        assertEquals(
            "lilixu3/danmu_api",
            RuntimeDependencyPackProtocol.DEV_CORE_REPO
        )
        assertEquals(
            "lilixu3/danmu-api-runtime-packs",
            RuntimeDependencyPackProtocol.PACK_REPO
        )
        assertEquals(18, RuntimeDependencyPackProtocol.EMBEDDED_NODE_MAJOR)

        val stable = checkNotNull(RuntimeDependencyPackProtocol.channelForVariant(ApiVariant.Stable))
        val dev = checkNotNull(RuntimeDependencyPackProtocol.channelForVariant(ApiVariant.Dev))
        assertEquals(RuntimePackChannel.Stable, stable)
        assertEquals(RuntimePackChannel.Dev, dev)
        assertEquals(null, RuntimeDependencyPackProtocol.channelForVariant(ApiVariant.Custom))
        assertEquals("main/stable/index.json", stable.indexPath)
        assertEquals("main/dev/index.json", dev.indexPath)

        assertTrue(
            RuntimeDependencyPackProtocol.isTrustedCoreSource(
                stable,
                "huangxd-/danmu_api",
                "main"
            )
        )
        assertFalse(
            RuntimeDependencyPackProtocol.isTrustedCoreSource(
                stable,
                "lilixu3/danmu_api",
                "main"
            )
        )
        assertTrue(
            RuntimeDependencyPackProtocol.isTrustedCoreSource(
                dev,
                "lilixu3/danmu_api",
                "main"
            )
        )
        assertFalse(
            RuntimeDependencyPackProtocol.isTrustedCoreSource(
                dev,
                "huangxd-/danmu_api",
                "main"
            )
        )
        assertFalse(
            RuntimeDependencyPackProtocol.isTrustedCoreSource(
                dev,
                "lilixu3/danmu_api",
                "test"
            )
        )
    }

    @Test
    fun `依赖指纹对 JSON 键顺序不敏感`() {
        val first = linkedMapOf("brotli" to "^1.3.3", "pako" to "^2.1.0")
        val second = linkedMapOf("pako" to "^2.1.0", "brotli" to "^1.3.3")

        assertEquals(
            RuntimeDependencyPackProtocol.dependencyFingerprint(first),
            RuntimeDependencyPackProtocol.dependencyFingerprint(second)
        )
        assertEquals(
            "546a071745a850d49ec26f4b27dc7591d018e75e3d6cc45ede7d3cb9c604b0ff",
            RuntimeDependencyPackProtocol.dependencyFingerprint(first)
        )
        assertEquals(
            64,
            RuntimeDependencyPackProtocol.dependencyFingerprint(first).length
        )
    }

    @Test
    fun `相同依赖指纹仍只在调用方选定的通道索引中命中`() {
        val fingerprint = "b".repeat(64)
        val sameSha = "a".repeat(40)
        val stableEntry = RuntimePackEntry(
            channel = "stable",
            coreRepo = RuntimeDependencyPackProtocol.STABLE_CORE_REPO,
            coreBranch = "main",
            coreSha = sameSha,
            dependencyFingerprint = fingerprint
        )
        val devEntry = RuntimePackEntry(
            channel = "dev",
            coreRepo = RuntimeDependencyPackProtocol.DEV_CORE_REPO,
            coreBranch = "main",
            coreSha = sameSha,
            dependencyFingerprint = fingerprint
        )
        val stableIndex = RuntimePackIndex(
            channel = "stable",
            source = RuntimePackSource(RuntimeDependencyPackProtocol.STABLE_CORE_REPO, "main"),
            entries = mapOf(sameSha to stableEntry),
            dependencyEntries = mapOf(fingerprint to sameSha)
        )
        val devIndex = RuntimePackIndex(
            channel = "dev",
            source = RuntimePackSource(RuntimeDependencyPackProtocol.DEV_CORE_REPO, "main"),
            entries = mapOf(sameSha to devEntry),
            dependencyEntries = mapOf(fingerprint to sameSha)
        )

        assertSame(
            stableEntry,
            RuntimeDependencyPackManager.selectEntryForFingerprint(
                index = stableIndex,
                fingerprint = fingerprint,
                preferredCoreSha = sameSha
            )
        )
        assertSame(
            devEntry,
            RuntimeDependencyPackManager.selectEntryForFingerprint(
                index = devIndex,
                fingerprint = fingerprint,
                preferredCoreSha = sameSha
            )
        )
        assertEquals(
            null,
            RuntimeDependencyPackManager.selectEntryForFingerprint(
                index = stableIndex.copy(dependencyEntries = emptyMap()),
                fingerprint = fingerprint,
                preferredCoreSha = "c".repeat(40)
            )
        )
    }

    @Test
    fun `ZIP 路径必须位于允许的 node_modules 根下`() {
        assertTrue(RuntimeDependencyPackProtocol.isSafeArchivePath("manifest.json"))
        assertTrue(RuntimeDependencyPackProtocol.isSafeArchivePath("runtime-lock.json"))
        assertTrue(RuntimeDependencyPackProtocol.isSafeArchivePath("node_modules/brotli/decompress.js"))
        assertTrue(RuntimeDependencyPackProtocol.isSafeArchivePath("node_modules/@scope/pkg/index.js"))

        listOf(
            "../escape",
            "/absolute/path",
            "node_modules/../escape",
            "node_modules/bad\\path.js",
            "config/secrets",
            "node_modules/"
        ).forEach { path ->
            assertFalse("不应接受 $path", RuntimeDependencyPackProtocol.isSafeArchivePath(path))
        }
    }

    @Test
    fun `App 生产公钥可分别验证稳定版和开发版公网发布索引`() {
        val loader = checkNotNull(javaClass.classLoader)
        val expectedSha = "ce8b3cbddf01181c323627b485a1861390ca44b0"
        val channels = listOf(
            RuntimePackChannel.Stable to RuntimeDependencyPackProtocol.STABLE_CORE_REPO,
            RuntimePackChannel.Dev to RuntimeDependencyPackProtocol.DEV_CORE_REPO
        )
        val fingerprints = mutableSetOf<String>()

        channels.forEach { (channel, expectedRepo) ->
            val resourceRoot = "runtime-packs/${channel.key}"
            val indexBytes = checkNotNull(
                loader.getResourceAsStream("$resourceRoot/index.json")
            ).readBytes()
            val signature = checkNotNull(
                loader.getResourceAsStream("$resourceRoot/index.sig")
            ).bufferedReader().use { it.readText() }

            assertTrue(RuntimeDependencyPackManager.verifyIndexSignature(indexBytes, signature))
            val index = json.decodeFromString<RuntimePackIndex>(indexBytes.toString(Charsets.UTF_8))
            val entry = checkNotNull(index.entries[expectedSha])
            assertEquals(RuntimeDependencyPackProtocol.INDEX_SCHEMA, index.schema)
            assertEquals(channel.key, index.channel)
            assertEquals(expectedRepo, index.source.repo)
            assertEquals("main", index.source.branch)
            assertEquals(channel.key, entry.channel)
            assertEquals(expectedRepo, entry.coreRepo)
            assertEquals("main", entry.coreBranch)
            assertEquals(expectedSha, index.dependencyEntries[entry.dependencyFingerprint])
            assertTrue(entry.artifactUrl.contains("/${channel.key}-core-"))
            assertTrue(entry.artifactUrl.endsWith("runtime-pack-${channel.key}-${expectedSha.take(12)}.zip"))
            fingerprints += entry.dependencyFingerprint
        }
        assertEquals(1, fingerprints.size)
    }

    @Test
    fun `旧版公网兼容索引仍是 schema 1 且只包含稳定版独立资产`() {
        val loader = checkNotNull(javaClass.classLoader)
        val indexBytes = checkNotNull(
            loader.getResourceAsStream("runtime-packs/index.json")
        ).readBytes()
        val signature = checkNotNull(
            loader.getResourceAsStream("runtime-packs/index.sig")
        ).bufferedReader().use { it.readText() }

        assertTrue(RuntimeDependencyPackManager.verifyIndexSignature(indexBytes, signature))
        val root = json.parseToJsonElement(indexBytes.toString(Charsets.UTF_8)).jsonObject
        assertEquals(1, root.getValue("schema").jsonPrimitive.int)
        assertEquals(
            RuntimeDependencyPackProtocol.STABLE_CORE_REPO,
            root.getValue("upstream").jsonObject.getValue("repo").jsonPrimitive.content
        )
        val entries = root.getValue("entries").jsonObject
        assertEquals(1, entries.size)
        entries.values.forEach { value ->
            val entry = value.jsonObject
            assertEquals(
                RuntimeDependencyPackProtocol.STABLE_CORE_REPO,
                entry.getValue("coreRepo").jsonPrimitive.content
            )
            val artifactUrl = entry.getValue("artifactUrl").jsonPrimitive.content
            assertTrue(artifactUrl.contains("/releases/download/core-"))
            assertFalse(artifactUrl.contains("/stable-core-"))
            assertFalse(artifactUrl.contains("/dev-core-"))
            assertFalse(entry.containsKey("channel"))
            assertFalse(entry.containsKey("coreBranch"))
        }
    }

    @Test
    fun `索引响应即使没有 Content Length 也不能超过内存上限`() {
        val allowed = byteArrayOf(1, 2, 3, 4)
        assertArrayEquals(
            allowed,
            RuntimeDependencyPackManager.readLimited(ByteArrayInputStream(allowed), allowed.size)
        )

        try {
            RuntimeDependencyPackManager.readLimited(ByteArrayInputStream(allowed), allowed.size - 1)
            fail("超过上限时应拒绝响应")
        } catch (_: IOException) {
            // expected
        }
    }

    @Test
    fun `RSA 签名索引通过验证且篡改后拒绝`() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val payload = "{\"schema\":1,\"entries\":{}}".toByteArray()
        val signer = Signature.getInstance("SHA256withRSA").apply {
            initSign(pair.private)
            update(payload)
        }
        val signature = Base64.getEncoder().encodeToString(signer.sign())
        val publicKeyPem = buildString {
            append("    -----BEGIN PUBLIC KEY-----\n")
            append("    ")
            append(Base64.getEncoder().encodeToString(pair.public.encoded))
            append("\n    -----END PUBLIC KEY-----")
        }

        assertTrue(
            RuntimeDependencyPackManager.verifyIndexSignature(payload, signature, publicKeyPem)
        )
        assertFalse(
            RuntimeDependencyPackManager.verifyIndexSignature(
                "{\"schema\":1,\"entries\":{\"tampered\":true}}".toByteArray(),
                signature,
                publicKeyPem
            )
        )
    }

    @Test
    fun `核心依赖读取包含 dependencies 和 optionalDependencies`() {
        val root = Files.createTempDirectory("runtime-pack-core-deps").toFile()
        try {
            File(root, "package.json").writeText(
                """
                {
                  "dependencies": {"brotli": "^1.3.3"},
                  "optionalDependencies": {"redis": "^5.11.0"}
                }
                """.trimIndent()
            )
            assertEquals(
                linkedMapOf("brotli" to "^1.3.3", "redis" to "^5.11.0"),
                RuntimeDependencyPackProtocol.readCoreDependencies(root)
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
