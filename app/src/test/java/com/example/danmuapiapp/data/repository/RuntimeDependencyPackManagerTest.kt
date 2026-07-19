package com.example.danmuapiapp.data.repository

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
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
    fun `依赖包协议只绑定官方上游核心`() {
        assertEquals(
            "huangxd-/danmu_api",
            RuntimeDependencyPackProtocol.UPSTREAM_CORE_REPO
        )
        assertEquals(
            "lilixu3/danmu-api-runtime-packs",
            RuntimeDependencyPackProtocol.PACK_REPO
        )
        assertEquals(18, RuntimeDependencyPackProtocol.EMBEDDED_NODE_MAJOR)
        assertFalse(RuntimeDependencyPackProtocol.isOfficialCoreRepo("lilixu3/danmu_api"))
        assertTrue(RuntimeDependencyPackProtocol.isOfficialCoreRepo("huangxd-/danmu_api"))
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
    fun `开发版和自定义核心可按相同依赖指纹复用签名包`() {
        val fingerprint = "b".repeat(64)
        val officialSha = "a".repeat(40)
        val entry = RuntimePackEntry(
            coreRepo = RuntimeDependencyPackProtocol.UPSTREAM_CORE_REPO,
            coreSha = officialSha,
            dependencyFingerprint = fingerprint
        )
        val index = RuntimePackIndex(
            entries = mapOf(officialSha to entry),
            dependencyEntries = mapOf(fingerprint to officialSha)
        )

        val selected = RuntimeDependencyPackManager.selectEntryForFingerprint(
            index = index,
            fingerprint = fingerprint,
            preferredCoreSha = "c".repeat(40)
        )

        assertSame(entry, selected)
        assertEquals(
            null,
            RuntimeDependencyPackManager.selectEntryForFingerprint(
                index = index,
                fingerprint = "d".repeat(64),
                preferredCoreSha = officialSha
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
    fun `App 生产公钥可验证公网发布索引且来源是官方上游`() {
        val loader = checkNotNull(javaClass.classLoader)
        val indexBytes = checkNotNull(loader.getResourceAsStream("runtime-packs/index.json")).readBytes()
        val signature = checkNotNull(loader.getResourceAsStream("runtime-packs/index.sig"))
            .bufferedReader()
            .use { it.readText() }

        assertTrue(RuntimeDependencyPackManager.verifyIndexSignature(indexBytes, signature))
        val index = json.decodeFromString<RuntimePackIndex>(indexBytes.toString(Charsets.UTF_8))
        val expectedSha = "ce8b3cbddf01181c323627b485a1861390ca44b0"
        val entry = checkNotNull(index.entries[expectedSha])
        assertEquals(RuntimeDependencyPackProtocol.UPSTREAM_CORE_REPO, index.upstream.repo)
        assertEquals(expectedSha, index.dependencyEntries[entry.dependencyFingerprint])
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
