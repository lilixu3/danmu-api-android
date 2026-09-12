package com.example.danmuapiapp.data.repository

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDanmuDirectoryListingTest {

    private fun tempDir(): File =
        File(System.getProperty("java.io.tmpdir"), "local-danmu-listing-${System.nanoTime()}").apply {
            mkdirs()
        }

    @Test
    fun `lists folders before files and keeps natural order`() {
        val root = tempDir()
        try {
            File(root, "bilibili").mkdirs()
            File(root, "凡人修仙传").mkdirs()
            File(root, "剧集_E2.xml").writeText("<i/>")
            File(root, "剧集_E10.xml").writeText("<i/>")
            File(root, "剧集_E1.xml").writeText("<i/>")

            val listed = LocalDanmuDirectoryListing.list(root)

            assertEquals(
                listOf("bilibili", "凡人修仙传", "剧集_E1.xml", "剧集_E2.xml", "剧集_E10.xml"),
                listed.entries.map { it.name }
            )
            assertTrue(listed.entries.take(2).all { it.isDirectory })
            assertFalse(listed.truncated)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `skips hidden entries and unsupported extensions`() {
        val root = tempDir()
        try {
            File(root, ".nomedia").writeText("")
            File(root, ".hidden.xml").writeText("<i/>")
            File(root, "readme.md").writeText("x")
            File(root, "video.mp4").writeText("x")
            File(root, "弹幕.json").writeText("[]")
            File(root, "弹幕.ass").writeText("[Script Info]")

            val listed = LocalDanmuDirectoryListing.list(root)

            assertEquals(listOf("弹幕.ass", "弹幕.json"), listed.entries.map { it.name })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `reports truncation when a directory has too many entries`() {
        val root = tempDir()
        try {
            repeat(LocalDanmuDirectoryListing.MAX_ENTRIES + 20) { index ->
                File(root, "file_%05d.xml".format(index)).writeText("<i/>")
            }

            val listed = LocalDanmuDirectoryListing.list(root)

            assertEquals(LocalDanmuDirectoryListing.MAX_ENTRIES, listed.entries.size)
            assertTrue(listed.truncated)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `supports only known danmaku extensions`() {
        assertTrue(LocalDanmuDirectoryListing.supports("a.xml"))
        assertTrue(LocalDanmuDirectoryListing.supports("a.JSON"))
        assertTrue(LocalDanmuDirectoryListing.supports("凡人修仙传_E1.ass"))
        assertFalse(LocalDanmuDirectoryListing.supports("a.zip"))
        assertFalse(LocalDanmuDirectoryListing.supports("a.mp4"))
        assertFalse(LocalDanmuDirectoryListing.supports("noextension"))
    }
}
