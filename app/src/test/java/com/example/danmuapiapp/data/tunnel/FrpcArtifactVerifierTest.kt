package com.example.danmuapiapp.data.tunnel

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

class FrpcArtifactVerifierTest {
    private fun payload(digest: String = "sha256:" + "a".repeat(64), size: Long = 10): String = JSONObject()
        .put("tag_name", "v0.68.0").put("draft", false).put("prerelease", false)
        .put("assets", JSONArray().put(JSONObject().put("name", "frp_0.68.0_linux_arm64.tar.gz")
            .put("browser_download_url", "https://github.com/fatedier/frp/releases/download/v0.68.0/frp_0.68.0_linux_arm64.tar.gz")
            .put("digest", digest).put("size", size))).toString()
    private fun rejected(block: () -> Unit) { try { block(); fail("must reject") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {} }
    @Test fun `requires official asset digest and exact version URL size`() {
        assertEquals("a".repeat(64), verifiedFrpcAsset(payload(), "0.68.0", "arm64").sha256)
        rejected { verifiedFrpcAsset(payload(""), "0.68.0", "arm64") }
        rejected { verifiedFrpcAsset(payload(size = FRPC_MAX_ARCHIVE_BYTES + 1), "0.68.0", "arm64") }
        rejected { verifiedFrpcAsset(payload().replace("https://github.com/", "https://evil.test/"), "0.68.0", "arm64") }
        rejected { verifiedFrpcAsset(payload(), "0.68.1", "arm64") }
    }
    @Test fun `stream verifies digest length and cancellation before execution`() {
        val data = "archive".toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it.toInt() and 255) }
        val asset = VerifiedFrpcAsset("", data.size.toLong(), digest)
        val output = ByteArrayOutputStream()
        copyVerifiedFrpcArchive(ByteArrayInputStream(data), output, asset)
        assertArrayEquals(data, output.toByteArray())
        rejected { copyVerifiedFrpcArchive(ByteArrayInputStream("bad".toByteArray()), ByteArrayOutputStream(), asset) }
        rejected { copyVerifiedFrpcArchive(ByteArrayInputStream(data), ByteArrayOutputStream(), asset.copy(sha256 = "0".repeat(64))) }
        try { copyVerifiedFrpcArchive(ByteArrayInputStream(data), ByteArrayOutputStream(), asset) { throw java.util.concurrent.CancellationException() }; fail("must cancel") }
        catch (_: java.util.concurrent.CancellationException) {}
    }
}
