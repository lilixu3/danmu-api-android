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
        assertEquals(0L, actual.installedAtEpochMillis)
    }

    @Test fun `installation time round trips independently of remote commit date`() {
        val metadata = installed.copy(
            commitPublishedAt = "2026-10-01T08:00:00Z",
            installedAtEpochMillis = 1791763200000L
        )
        val normal = folder.newFile().apply { writeText(Json.encodeToString(metadata)) }
        val actual = CoreSourceMetadataStore.read(RunMode.Normal, normal, "unused") { null }!!
        assertEquals(metadata, actual)
        assertEquals("2026-10-01T08:00:00Z", actual.commitPublishedAt)
        assertEquals(1791763200000L, actual.installedAtEpochMillis)
    }

    @Test fun `writing metadata stamps installation time and removes empty records`() {
        val coreDir = folder.newFolder("core")
        CoreSourceMetadataStore.write(coreDir, installed, now = 1791763200123L)
        val written = CoreSourceMetadataStore.read(RunMode.Normal, CoreSourceMetadataStore.fileIn(coreDir), "unused") { null }!!
        assertEquals(installed.repo, written.repo)
        assertEquals(1791763200123L, written.installedAtEpochMillis)

        CoreSourceMetadataStore.write(coreDir, CoreSourceMetadata(), now = 1L)
        assertFalse(CoreSourceMetadataStore.fileIn(coreDir).exists())
    }

    @Test fun `root metadata path uses the shared file name contract`() {
        assertEquals(".danmuapiapp-core-source.json", CoreSourceMetadataStore.FILE_NAME)
        assertEquals(
            "/root/core/.danmuapiapp-core-source.json",
            "/root/core/${CoreSourceMetadataStore.FILE_NAME}"
        )
    }
}
