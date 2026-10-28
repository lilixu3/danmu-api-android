package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.GithubRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoreInstallMetadataPolicyTest {
    private fun release(
        tagName: String = "v1.2.3",
        publishedAt: String = "2026-10-20T00:00:00Z",
        zipballUrl: String = "https://example/archive/v1.2.3.zip",
        commitSha: String = "",
        version: String = ""
    ) = GithubRelease(
        tagName = tagName,
        name = tagName,
        body = "",
        publishedAt = publishedAt,
        zipballUrl = zipballUrl,
        commitSha = commitSha,
        version = version
    )

    @Test
    fun `release metadata prefers explicit commit and release version`() {
        val metadata = CoreInstallMetadataPolicy.buildReleaseMetadata(
            repo = "owner/core",
            branch = "main",
            release = release(commitSha = "a".repeat(40), version = "1.2.3"),
            versionHint = "fallback"
        )!!
        assertEquals("owner/core", metadata.repo)
        assertEquals("main", metadata.branch)
        assertEquals("a".repeat(40), metadata.commitSha)
        assertEquals("2026-10-20T00:00:00Z", metadata.commitPublishedAt)
        assertEquals("1.2.3", metadata.versionLabel)
    }

    @Test
    fun `release metadata falls back to archive commit and version hint`() {
        val metadata = CoreInstallMetadataPolicy.buildReleaseMetadata(
            repo = "owner/core",
            branch = null,
            release = release(zipballUrl = "https://github.com/owner/core/archive/abcdef123.zip"),
            versionHint = "1.2.3"
        )!!
        assertEquals("", metadata.branch)
        assertEquals("abcdef123", metadata.commitSha)
        assertEquals("1.2.3", metadata.versionLabel)
    }

    @Test
    fun `commit stored in zipball path is not reused when release has no commit`() {
        val metadata = CoreInstallMetadataPolicy.buildReleaseMetadata(
            repo = "owner/core",
            branch = "main",
            release = release(zipballUrl = "https://api.github.com/repos/owner/core/zipball/abc1234"),
            versionHint = null
        )!!
        assertEquals("abc1234", metadata.commitSha)
        assertEquals("", metadata.versionLabel)
    }

    @Test
    fun `blank repo never produces metadata`() {
        assertNull(
            CoreInstallMetadataPolicy.buildReleaseMetadata(
                repo = "  ",
                branch = "main",
                release = release(),
                versionHint = "1.2.3"
            )
        )
        assertNull(
            CoreInstallMetadataPolicy.buildRollbackMetadata(
                repo = "",
                branch = "main",
                release = release(),
                versionHint = "1.2.3",
                resolveTagCommitSha = { "ignored" }
            )
        )
    }

    @Test
    fun `rollback resolves tag head only when release and archive have no commit`() {
        val requested = mutableListOf<String>()
        val metadata = CoreInstallMetadataPolicy.buildRollbackMetadata(
            repo = "owner/core",
            branch = "release",
            release = release(zipballUrl = "https://example/archive/release.zip"),
            versionHint = null,
            resolveTagCommitSha = { tag ->
                requested += tag
                "b".repeat(40)
            }
        )!!
        assertEquals(listOf("v1.2.3"), requested)
        assertEquals("b".repeat(40), metadata.commitSha)
    }

    @Test
    fun `rollback does not resolve a tag when an archive commit is available`() {
        var requested = 0
        val metadata = CoreInstallMetadataPolicy.buildRollbackMetadata(
            repo = "owner/core",
            branch = "main",
            release = release(zipballUrl = "https://github.com/owner/core/archive/abcdef123.zip"),
            versionHint = "fallback",
            resolveTagCommitSha = {
                requested += 1
                "ignored"
            }
        )!!
        assertEquals("abcdef123", metadata.commitSha)
        assertEquals(0, requested)
        assertEquals("fallback", metadata.versionLabel)
    }
}
