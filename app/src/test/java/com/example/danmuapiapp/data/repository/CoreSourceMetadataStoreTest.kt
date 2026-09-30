package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.RunMode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CoreSourceMetadataStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val installed = CoreSourceMetadata(
        repo = "owner/core", branch = "main", commitSha = "a".repeat(40), versionLabel = "1.21.3",
        pullRequestNumbers = listOf(492, 500), pullRequestHeadShas = listOf("b".repeat(40), "c".repeat(40)),
        localMergeSha = "d".repeat(40)
    )

    @Test fun `Root reads real core metadata instead of stale Normal copy`() {
        val normal = folder.newFile().apply { writeText(Json.encodeToString(installed.copy(commitSha = "stale"))) }
        var requestedPath = ""
        val actual = CoreSourceMetadataStore.read(RunMode.Root, normal, "/root/core/source.json") {
            requestedPath = it
            Json.encodeToString(installed)
        }
        assertEquals("/root/core/source.json", requestedPath)
        assertEquals(installed, actual)
        assertNotEquals(actual?.commitSha, actual?.localMergeSha)
    }

    @Test fun `missing Root metadata does not borrow Normal PR or commit state`() {
        val normal = folder.newFile().apply { writeText(Json.encodeToString(installed)) }
        assertNull(CoreSourceMetadataStore.read(RunMode.Root, normal, "/root/core/source.json") { null })
        assertNull(CoreSourceMetadataStore.read(RunMode.Root, normal, "/root/core/source.json") { "broken json" })
    }

    @Test fun `Normal reads selected working directory without requesting root`() {
        val normal = folder.newFile().apply { writeText(Json.encodeToString(installed)) }
        assertEquals(installed, CoreSourceMetadataStore.read(RunMode.Normal, normal, "unused") {
            error("Normal must not request root")
        })
    }

    @Test fun `legacy metadata without PR fields and future fields remain readable`() {
        val normal = folder.newFile().apply {
            writeText("""{"repo":"owner/core","branch":"main","commitSha":"abc1234","futureField":true}""")
        }
        val actual = CoreSourceMetadataStore.read(RunMode.Normal, normal, "unused") { null }!!
        assertEquals("abc1234", actual.commitSha)
        assertTrue(actual.pullRequestNumbers.isEmpty())
        assertTrue(actual.pullRequestHeadShas.isEmpty())
    }
}
