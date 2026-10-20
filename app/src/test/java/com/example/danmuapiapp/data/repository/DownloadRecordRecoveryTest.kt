package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import org.junit.Assert.*
import org.junit.Test

class DownloadRecordRecoveryTest {
    private fun candidate(name: String, path: String = name) = DownloadDirectoryScanner.Candidate(
        "content://downloads/42", name, DanmuDownloadFormat.fromFileName(name)!!, path, 123L, 456L)

    @Test fun `scan reconstructs show year season number and source using import metadata`() {
        val input = candidate("E03_bilibili1.bili.xml", "测试剧(2023)/Season 2/E03_bilibili1.bili.xml")
        val record = DownloadRecordRecovery.record(input, 999)
        assertEquals("测试剧 第2季(2023)", record.animeTitle)
        assertEquals(3, record.episodeNo)
        assertEquals("E03_bilibili1", record.episodeTitle)
        assertEquals("bilibili", record.source)
        assertEquals(123L, record.createdAt)
        assertEquals(456L, record.bytes)
        assertNull(record.danmuCount)
        assertFalse(record.canAutoImportToLocal)
        assertEquals(record.id, DownloadRecordRecovery.record(input, 1000).id)
    }
    @Test fun `flat files recover titles and sources without generic existing danmaku group`() {
        val record = DownloadRecordRecovery.record(candidate("测试剧(2023)_E04_qq.ddplay.json"), 99)
        assertEquals("测试剧(2023)", record.animeTitle)
        assertEquals("tencent", record.source)
        assertEquals(4, record.episodeNo)
    }
    @Test fun `unknown episode remains unknown instead of silently becoming episode one`() {
        val record = DownloadRecordRecovery.record(candidate("片段.xml", "测试剧/片段.xml"), 99)
        assertEquals(0, record.episodeNo)
        assertEquals("目录同步", record.source)
    }
    @Test fun `binary format suffix never becomes the title or containing folder`() {
        val metadata = LocalDanmuMetadataResolver.resolve("测试剧.danuni.binpb", "测试剧.danuni.binpb")
        assertEquals("测试剧", metadata.title)
        assertEquals("", DownloadRecordRecovery.source("测试剧.bili.xml"))
        assertEquals("tencent", DownloadRecordRecovery.source("测试剧_E01_qq.ddplay.json"))
    }
    @Test fun `download reuses the only compatible legacy source directory`() {
        assertEquals("测试剧(2023)【电视剧】from bilibili", DownloadRecordRecovery.seriesDirectory(
            "测试剧", "测试剧(2023)【电视剧】from tencent", listOf("测试剧(2023)【电视剧】from bilibili")))
    }
    @Test fun `download avoids ambiguous legacy directories and otherwise follows Flutter clean title selection`() {
        val legacy = "测试剧(2023)【电视剧】from tencent"
        assertEquals("测试剧", DownloadRecordRecovery.seriesDirectory("测试剧", legacy,
            listOf("测试剧(2023)from bilibili", "测试剧(2023)from iqiyi")))
        assertEquals("测试剧", DownloadRecordRecovery.seriesDirectory("测试剧", legacy, listOf("测试剧(2013)from bilibili")))
    }
    @Test fun `new episodes prefer the plain directory when both plain and legacy exist`() {
        assertEquals("一瓯春", DownloadRecordRecovery.seriesDirectory("一瓯春",
            "一瓯春(2026)【电视剧】from youku", listOf("一瓯春", "一瓯春(2026)【电视剧】from youku")))
    }
    @Test fun `a show season in its directory name is retained for numbered episode filenames`() {
        val record = DownloadRecordRecovery.record(candidate("E03.xml", "测试剧 第2季(2023)/E03.xml"), 99)
        assertEquals("测试剧 第2季(2023)", record.animeTitle)
        assertEquals(3, record.episodeNo)
    }
    @Test fun `scan keeps a filename year even when an old directory declares another year`() {
        val name = "同名剧(2023)_E01_youku.xml"
        val record = DownloadRecordRecovery.record(candidate(name, "同名剧(2013)【电视剧】from youku/$name"), 99)
        assertEquals("同名剧(2023)", record.animeTitle)
    }
}
