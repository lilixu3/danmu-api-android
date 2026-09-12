package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.DanmuDownloadRecord
import com.example.danmuapiapp.domain.model.LocalDanmuType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDanmuMetadataResolverTest {

    private val fanrenFile =
        "凡人修仙传(2020)【动漫】from bilibili_E01_第1话 凡人风起天南1重制版_bilibili1.xml"

    @Test
    fun `parses this app download template from a show folder`() {
        val result = LocalDanmuMetadataResolver.resolve(
            fileName = fanrenFile,
            relativePath = "凡人修仙传(2020)【动漫】from bilibili/$fanrenFile",
            archiveName = null
        )
        assertEquals("凡人修仙传", result.title)
        assertEquals(2020, result.year)
        assertEquals(1, result.season)
        assertEquals(1, result.episode)
        assertEquals(LocalDanmuType.Tv.wire, result.type)
        assertEquals("第1话 凡人风起天南1重制版", result.episodeTitle)
    }

    @Test
    fun `parses the same template in a flat mixed folder`() {
        val result = LocalDanmuMetadataResolver.resolve(
            fileName = fanrenFile,
            relativePath = fanrenFile
        )
        assertEquals("凡人修仙传", result.title)
        assertEquals(2020, result.year)
        assertEquals(1, result.episode)
    }

    @Test
    fun `download record wins over a messy file name`() {
        val record = DanmuDownloadRecord(
            animeTitle = "凡人修仙传(2020)【动漫】from bilibili",
            episodeTitle = "第33话 魔道争锋12",
            episodeId = 33L,
            episodeNo = 33,
            source = "bilibili1",
            format = "xml",
            status = "success",
            fileName = fanrenFile,
            relativePath = "凡人修仙传(2020)【动漫】from bilibili/$fanrenFile"
        )
        val result = LocalDanmuMetadataResolver.resolve(
            fileName = fanrenFile,
            relativePath = record.relativePath,
            downloadRecord = record
        )
        assertEquals("凡人修仙传", result.title)
        assertEquals(2020, result.year)
        assertEquals(33, result.episode)
        assertEquals("第33话 魔道争锋12", result.episodeTitle)
        assertTrue(result.matchSource == "download-record")
    }

    @Test
    fun `uses a season folder and keeps the show title`() {
        val result = LocalDanmuMetadataResolver.resolve(
            fileName = "斗破苍穹.S02E05.2160p.xml",
            relativePath = "斗破苍穹/S02/斗破苍穹.S02E05.2160p.xml"
        )
        assertEquals("斗破苍穹", result.title)
        assertEquals(2, result.season)
        assertEquals(5, result.episode)
        assertEquals(LocalDanmuType.Tv.wire, result.type)
    }

    @Test
    fun `parses the common release group dash pattern`() {
        val result = LocalDanmuMetadataResolver.resolve(
            fileName = "[ANi] 葬送的芙莉莲 - 01 [1080P][Baha].xml"
        )
        assertEquals("葬送的芙莉莲", result.title)
        assertEquals(1, result.episode)
        assertEquals(LocalDanmuType.Tv.wire, result.type)
    }

    @Test
    fun `keeps dots in a title with spaces`() {
        val result = LocalDanmuMetadataResolver.resolve(
            fileName = "Mr. Robot - 03 [1080p].xml"
        )
        assertEquals("Mr. Robot", result.title)
        assertEquals(3, result.episode)
    }

    @Test
    fun `parses sxxexx with dots`() {
        val result = LocalDanmuMetadataResolver.resolve(
            fileName = "Show.Name.S01E02.1080p.WEB-DL.xml"
        )
        assertEquals("Show Name", result.title)
        assertEquals(1, result.season)
        assertEquals(2, result.episode)
    }

    @Test
    fun `parses zip entries using the archive context`() {
        val result = LocalDanmuMetadataResolver.resolve(
            fileName = fanrenFile,
            relativePath = "凡人修仙传(2020)【动漫】from bilibili.zip/$fanrenFile",
            archiveName = "凡人修仙传(2020)【动漫】from bilibili.zip"
        )
        assertEquals("凡人修仙传", result.title)
        assertEquals(2020, result.year)
        assertEquals(1, result.episode)
    }

    @Test
    fun `uses archive title for numbered zip entries`() {
        val result = LocalDanmuMetadataResolver.resolve(
            fileName = "1.xml",
            relativePath = "凡人修仙传.S01.zip/1.xml",
            archiveName = "凡人修仙传.S01.zip"
        )
        assertEquals("凡人修仙传", result.title)
        assertEquals(1, result.season)
        assertEquals(1, result.episode)
    }
}
