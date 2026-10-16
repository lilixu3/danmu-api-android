package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import java.io.File
import java.io.InputStream
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadDirectoryScannerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun scan(
        root: File,
        existingUris: Set<String> = emptySet(),
        maxFiles: Int = 2_000,
        maxDepth: Int = 8,
        onList: (String) -> Unit = {}
    ): DownloadDirectoryScanner.ScanResult = DownloadDirectoryScanner.scan(
        rootUri = root.toURI().toString(),
        existingUris = existingUris,
        maxFiles = maxFiles,
        maxDepth = maxDepth
    ) { uri ->
        onList(uri)
        File(URI(uri)).listFiles()?.map { file ->
            DownloadDirectoryScanner.Entry(
                uri = file.toURI().toString(),
                name = file.name,
                isDirectory = file.isDirectory,
                bytes = file.length(),
                lastModified = file.lastModified()
            )
        }
    }

    @Test
    fun `real directory scan preserves nested names and metadata with one listing per directory`() {
        val root = temporaryFolder.newFolder("弹幕")
        val series = File(root, "剧名 2026").apply { mkdirs() }
        val episode = File(series, "第1集.bili.xml").apply { writeText("<i><d p=\"1\">测试</d></i>") }
        File(root, "说明.txt").writeText("不是弹幕")
        val listed = mutableListOf<String>()

        val result = scan(root, onList = { listed += it })

        assertTrue(result.reliable)
        assertFalse(result.truncated)
        assertEquals(2, result.scannedFiles)
        assertEquals(1, result.matchedFiles)
        assertEquals(1, result.skippedFiles)
        val candidate = result.candidates.single()
        assertEquals("剧名 2026/第1集.bili.xml", candidate.relativePath)
        assertEquals(episode.length(), candidate.bytes)
        assertEquals(episode.lastModified(), candidate.lastModified)
        assertEquals(DanmuDownloadFormat.BiliXml, candidate.format)
        assertEquals(setOf(root.toURI().toString(), series.toURI().toString()), listed.toSet())
        assertEquals(2, listed.size)
    }

    @Test
    fun `known files remain in existence index without becoming inspection candidates`() {
        val root = temporaryFolder.newFolder()
        val known = File(root, "E1.xml").apply { writeText("<i/>") }
        val fresh = File(root, "E2.json").apply { writeText("{\"comments\":[]}") }

        val result = scan(root, existingUris = setOf(known.toURI().toString()))

        assertEquals(setOf(known.toURI().toString(), fresh.toURI().toString()), result.scannedUris)
        assertEquals(listOf(fresh.toURI().toString()), result.candidates.map { it.uri })
        assertEquals(2, result.matchedFiles)
        assertEquals(1, result.skippedFiles)
    }

    @Test
    fun `empty readable directory is a reliable complete scan`() {
        val result = scan(temporaryFolder.newFolder())

        assertTrue(result.reliable)
        assertFalse(result.truncated)
        assertEquals(0, result.scannedFiles)
    }

    @Test
    fun `unreadable child directory disables pruning but preserves readable candidates`() {
        val entries = mapOf(
            "root" to listOf(
                DownloadDirectoryScanner.Entry("denied", "无权限目录", true),
                DownloadDirectoryScanner.Entry("episode", "E1.xml", false)
            )
        )
        val result = DownloadDirectoryScanner.scan("root", emptySet(), 2_000, 8) { entries[it] }

        assertFalse(result.reliable)
        assertEquals(listOf("episode"), result.candidates.map { it.uri })
        assertTrue("episode" in result.scannedUris)
    }

    @Test
    fun `null root listing is not treated as an empty readable directory`() {
        val result = DownloadDirectoryScanner.scan("unreadable", emptySet(), 2_000, 8) { null }

        assertFalse(result.reliable)
        assertEquals(0, result.scannedFiles)
    }

    @Test
    fun `file limit marks scan incomplete and bounds the existence index`() {
        val root = temporaryFolder.newFolder()
        repeat(7) { File(root, "E$it.xml").writeText("<i/>") }

        val result = scan(root, maxFiles = 3)

        assertTrue(result.truncated)
        assertEquals(3, result.scannedFiles)
        assertEquals(3, result.scannedUris.size)
        assertEquals(3, result.candidates.size)
    }

    @Test
    fun `depth limit marks scan incomplete even when fewer than file limit were found`() {
        val root = temporaryFolder.newFolder()
        File(root, "E1.xml").writeText("<i/>")
        val deep = File(root, "第一层/第二层").apply { mkdirs() }
        File(deep, "E2.xml").writeText("<i/>")

        val result = scan(root, maxDepth = 1)

        assertTrue(result.truncated)
        assertTrue(result.reliable)
        assertEquals(1, result.scannedFiles)
    }

    @Test
    fun `directory references are listed only once`() {
        val counts = mutableMapOf<String, Int>()
        val result = DownloadDirectoryScanner.scan("root", emptySet(), 2_000, 8) { uri ->
            counts[uri] = counts.getOrDefault(uri, 0) + 1
            when (uri) {
                "root" -> listOf(DownloadDirectoryScanner.Entry("child", "子目录", true))
                else -> listOf(
                    DownloadDirectoryScanner.Entry("root", "返回上级", true),
                    DownloadDirectoryScanner.Entry("episode", "E1.xml", false)
                )
            }
        }

        assertEquals(mapOf("root" to 1, "child" to 1), counts)
        assertTrue(result.reliable)
        assertFalse(result.truncated)
        assertEquals(1, result.scannedFiles)
    }

    @Test
    fun `large payload inspection stops at 200 KiB without parsing the whole json`() {
        val limit = DownloadDirectoryScanner.PREFIX_BYTES
        val payload = ("{\"comments\":[\"" + "弹幕".repeat(limit)).toByteArray()
        val input = object : InputStream() {
            var position = 0
            override fun read(): Int {
                check(position < limit) { "read beyond scan prefix" }
                return payload[position++].toInt() and 0xff
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                check(position + length <= limit) { "read beyond scan prefix" }
                payload.copyInto(buffer, offset, position, position + length)
                position += length
                return length
            }
        }

        val prefix = DownloadDirectoryScanner.readPrefix(input)

        assertEquals(200 * 1024, prefix.size)
        assertEquals(limit, input.position)
        // This is deliberately incomplete JSON. Discovery only needs its format markers.
        assertTrue(DownloadDirectoryScanner.isPlausiblePrefix(prefix, DanmuDownloadFormat.Json))
    }

    @Test
    fun `short files return only their actual bytes`() {
        val payload = "<i/>".toByteArray()

        assertTrue(payload.contentEquals(DownloadDirectoryScanner.readPrefix(payload.inputStream())))
    }

    @Test
    fun `prefix sniffing covers every supported download format including bom`() {
        val cases = mapOf(
            DanmuDownloadFormat.Xml to "\uFEFF \n<?xml version=\"1.0\"?><i>",
            DanmuDownloadFormat.BiliXml to "<i><d p=\"1\">",
            DanmuDownloadFormat.Json to "{\"count\":1,\"comments\":[",
            DanmuDownloadFormat.DdplayJson to "{\"comments\":[",
            DanmuDownloadFormat.ArtplayerJson to "{\"danmuku\":[",
            DanmuDownloadFormat.VodJson to "{\"danum\":1,\"danmuku\":[",
            DanmuDownloadFormat.BahaJson to "{\"data\":{\"danmu\":[",
            DanmuDownloadFormat.DanuniJson to "[",
            DanmuDownloadFormat.DplayerJson to "{\"data\":[",
            DanmuDownloadFormat.DanuniBinPb to "\u000a\u0001"
        )

        assertEquals(DanmuDownloadFormat.entries.toSet(), cases.keys)
        cases.forEach { (format, text) ->
            assertTrue(format.value, DownloadDirectoryScanner.isPlausiblePrefix(text.toByteArray(), format))
        }
    }

    @Test
    fun `empty files and html are not accepted as text danmaku`() {
        DanmuDownloadFormat.entries.forEach { format ->
            assertFalse(format.value, DownloadDirectoryScanner.isPlausiblePrefix(byteArrayOf(), format))
            if (format != DanmuDownloadFormat.DanuniBinPb) {
                assertFalse(format.value, DownloadDirectoryScanner.isPlausiblePrefix("<html>403 Forbidden</html>".toByteArray(), format))
            }
        }
    }
}
