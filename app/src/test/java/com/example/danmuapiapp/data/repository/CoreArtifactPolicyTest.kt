package com.example.danmuapiapp.data.repository

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreArtifactPolicyTest {
    @Test
    fun `recognizes every staging and backup naming convention`() {
        listOf(
            "danmu_api_stable.staging-123",
            "danmu_api_stable.dependency-repair-123",
            "danmu_api_stable.backup-123",
            ".node_modules-backup-123"
        ).forEach { name -> assertTrue(name, CoreArtifactPolicy.isKnownArtifact(name)) }

        listOf("danmu_api_stable", "node_modules", "logs", "danmu_api_stable-copy").forEach { name ->
            assertFalse(name, CoreArtifactPolicy.isKnownArtifact(name))
        }
    }

    @Test
    fun `marker timestamp wins over directory modification time`() {
        val dir = Files.createTempDirectory("core-artifact-marker").toFile()
        try {
            dir.setLastModified(1_000L)
            CoreArtifactPolicy.markerFile(dir).writeText("42\n2000\n")
            assertEquals(2_000L, CoreArtifactPolicy.markerCreatedAt(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `legacy artifacts fall back to directory modification time`() {
        val dir = Files.createTempDirectory("core-artifact-legacy").toFile()
        try {
            dir.setLastModified(3_000L)
            assertEquals(3_000L, CoreArtifactPolicy.markerCreatedAt(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `expiry distinguishes marked from legacy artifacts`() {
        val marked = CoreArtifactPolicy.MARKED_STAGING_RETENTION_MS
        val legacy = CoreArtifactPolicy.LEGACY_STAGING_RETENTION_MS
        assertFalse(CoreArtifactPolicy.isExpired(1_000L, 1_000L + marked - 1L, hasMarker = true))
        assertTrue(CoreArtifactPolicy.isExpired(1_000L, 1_000L + marked, hasMarker = true))
        assertFalse(CoreArtifactPolicy.isExpired(1_000L, 1_000L + legacy - 1L, hasMarker = false))
        assertTrue(CoreArtifactPolicy.isExpired(1_000L, 1_000L + legacy, hasMarker = false))
        assertFalse(CoreArtifactPolicy.isExpired(0L, Long.MAX_VALUE, hasMarker = true))
    }

    @Test
    fun `temporary directory names stay adjacent to the target`() {
        val target = File("/tmp/work/danmu_api_stable")
        assertEquals(
            "danmu_api_stable.staging-123",
            CoreArtifactPolicy.tempDirectoryName(target, "staging", 123L)
        )
        assertEquals(
            "danmu_api_stable.dependency-repair-456",
            CoreArtifactPolicy.tempDirectoryName(target, "dependency-repair", 456L)
        )
    }
}
