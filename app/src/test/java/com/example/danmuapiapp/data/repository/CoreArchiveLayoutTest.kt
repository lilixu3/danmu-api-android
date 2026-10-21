package com.example.danmuapiapp.data.repository

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreArchiveLayoutTest {
    @Test
    fun `resolves danmu folders case insensitively and rejects unrelated roots`() {
        assertEquals("worker.js", CoreArchiveLayout.resolveDanmuRelativePath("repo/danmu_api/worker.js"))
        assertEquals("configs/globals.js", CoreArchiveLayout.resolveDanmuRelativePath("repo/danmu-api/configs/globals.js"))
        assertEquals("", CoreArchiveLayout.resolveDanmuRelativePath("repo/DANMU_API"))
        assertNull(CoreArchiveLayout.resolveDanmuRelativePath("repo/worker.js"))
        assertNull(CoreArchiveLayout.resolveDanmuRelativePath(""))
    }

    @Test
    fun `only root-level package json is copied`() {
        assertEquals("package.json", CoreArchiveLayout.resolveRootLevelFileName("repo/package.json"))
        assertEquals("package.json", CoreArchiveLayout.resolveRootLevelFileName("repo\\package.json"))
        assertNull(CoreArchiveLayout.resolveRootLevelFileName("repo/danmu_api/package.json"))
        assertNull(CoreArchiveLayout.resolveRootLevelFileName("package.json"))
        assertNull(CoreArchiveLayout.resolveRootLevelFileName("repo/worker.js"))
    }

    @Test
    fun `extracts only the danmu directory plus root package metadata`() {
        val archive = ByteArrayOutputStream()
        ZipOutputStream(archive).use { zip ->
            zip.putNextEntry(ZipEntry("owner-repo-abcdef1/danmu_api/worker.js"))
            zip.write("export const worker = 1\n".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("owner-repo-abcdef1/danmu_api/configs/globals.js"))
            zip.write("export const version = '1.0.0'\n".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("owner-repo-abcdef1/package.json"))
            zip.write("{\"type\":\"module\"}\n".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("owner-repo-abcdef1/README.md"))
            zip.write("ignored\n".toByteArray())
            zip.closeEntry()
        }

        val outDir = Files.createTempDirectory("core-archive-layout").toFile()
        try {
            val result = CoreArchiveLayout.extractDanmuFolder(
                ByteArrayInputStream(archive.toByteArray()),
                outDir
            )
            assertTrue(result.extractedAny)
            assertEquals("abcdef1", result.commitSha)
            assertEquals(
                "export const worker = 1\n",
                outDir.resolve("worker.js").readText()
            )
            assertEquals(
                "export const version = '1.0.0'\n",
                outDir.resolve("configs/globals.js").readText()
            )
            assertEquals("{\"type\":\"module\"}\n", outDir.resolve("package.json").readText())
            assertFalse(outDir.resolve("README.md").exists())
        } finally {
            outDir.deleteRecursively()
        }
    }

    @Test
    fun `zip slip entries never escape the output directory`() {
        val archive = ByteArrayOutputStream()
        ZipOutputStream(archive).use { zip ->
            zip.putNextEntry(ZipEntry("owner-repo-abcdef1/danmu_api/../../escaped.txt"))
            zip.write("escape\n".toByteArray())
            zip.closeEntry()
        }
        val outDir = Files.createTempDirectory("core-archive-slip").toFile()
        val sibling = java.io.File(outDir.parentFile, outDir.name + "-escaped.txt")
        try {
            val result = CoreArchiveLayout.extractDanmuFolder(
                ByteArrayInputStream(archive.toByteArray()),
                outDir
            )
            assertFalse(sibling.exists())
            assertFalse(result.extractedAny)
        } finally {
            outDir.deleteRecursively()
            sibling.delete()
        }
    }
}
