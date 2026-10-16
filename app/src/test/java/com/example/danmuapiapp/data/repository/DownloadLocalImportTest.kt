package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DownloadLocalImportTest {
    private fun record(name: String = "凡人修仙传(2020)【动漫】from bilibili_E99_第99话 星海飞驰23_bilibili1.xml",
        episode: Int = 99, title: String = "凡人修仙传", uri: String = "content://documents/episodes/99", bytes: Long = 4096L,
        format: String = "bili.xml", status: String = "success") = DanmuDownloadRecord(id = 42,
        animeTitle = title, episodeTitle = "第${episode}话", episodeId = 499, episodeNo = episode, source = "bilibili",
        format = format, status = status, fileName = name, fileUri = uri, bytes = bytes)

    @Test fun `quick import derives the same title year season and episode as manual import`() {
        val item = requireNotNull(DownloadLocalImportPlanner.plan(record()).item)
        assertEquals("凡人修仙传", item.title)
        assertEquals(2020, item.year)
        assertEquals(LocalDanmuType.Tv, item.type)
        assertEquals(1, item.season)
        assertEquals(99, item.episode)
        assertTrue(item.canImport)
    }
    @Test fun `filename metadata wins over stale download record number`() {
        val item = requireNotNull(DownloadLocalImportPlanner.plan(record("测试剧(2023)_E04_标题.xml", episode = 99)).item)
        assertEquals("测试剧", item.title)
        assertEquals(4, item.episode)
    }
    @Test fun `quick import uses series and season folders for simple filenames`() {
        val input = record("E03.bili.xml", episode = 3, title = "测试剧").copy(relativePath = "测试剧(2023)/Season 2/E03.bili.xml")
        val item = requireNotNull(DownloadLocalImportPlanner.plan(input).item)
        assertEquals("测试剧", item.title)
        assertEquals(2023, item.year)
        assertEquals(2, item.season)
        assertEquals(3, item.episode)
        assertTrue(item.canImport)
    }
    @Test fun `missing filename episode falls back to record number`() {
        val item = requireNotNull(DownloadLocalImportPlanner.plan(record("测试剧(2023).xml", episode = 8)).item)
        assertEquals(8, item.episode)
        assertTrue(item.canImport)
    }
    @Test fun `missing year remains invalid instead of silently choosing current year`() {
        val item = requireNotNull(DownloadLocalImportPlanner.plan(record("某剧_E01_第1话.xml", episode = 1)).item)
        assertNull(item.year)
        assertFalse(item.canImport)
        assertTrue(item.validationError!!.contains("年份"))
    }
    @Test fun `movie has no episode and carries filename year`() {
        val item = requireNotNull(DownloadLocalImportPlanner.plan(record("长安三万里(2023)【电影】from tencent_E01_正片.xml", episode = 1)).item)
        assertEquals(LocalDanmuType.Movie, item.type)
        assertNull(item.episode)
        assertEquals(2023, item.year)
    }
    @Test fun `season does not collapse into the first season`() {
        val item = requireNotNull(DownloadLocalImportPlanner.plan(record("测试剧(2023).S02E03.xml", episode = 3)).item)
        assertEquals(2, item.season)
        assertEquals(3, item.episode)
    }
    @Test fun `already imported by manual path is skipped by normalized resource key`() {
        val resource = LocalDanmuResource("core|key", title = "凡人修仙传", year = 2020, type = "tv", season = 1, episode = 99)
        val existing = DownloadLocalImportPlanner.existingKeys(listOf(resource))
        val item = requireNotNull(DownloadLocalImportPlanner.plan(record(), existing).item)
        assertNotNull(item.importedLabel)
        assertFalse(item.selected)
        assertFalse(item.canImport)
        assertTrue(item.copy(selected = true).canImport)
    }
    @Test fun `other season never marks this season imported`() {
        val resource = LocalDanmuResource("other", title = "凡人修仙传", year = 2020, type = "tv", season = 2, episode = 99)
        val item = requireNotNull(DownloadLocalImportPlanner.plan(record(), DownloadLocalImportPlanner.existingKeys(listOf(resource))).item)
        assertTrue(item.selected)
    }
    @Test fun `missing location and oversized files are blocked before reading`() {
        assertNotNull(DownloadLocalImportPlanner.plan(record(uri = "")).blockedReason)
        assertNull(DownloadLocalImportPlanner.plan(record(uri = "")).item)
        val tooLarge = DownloadLocalImportPlanner.plan(record(bytes = 10L * 1024 * 1024 + 1))
        assertNull(tooLarge.item)
        assertTrue(tooLarge.blockedReason.contains("10 MB"))
        assertNotNull(DownloadLocalImportPlanner.plan(record(bytes = 10L * 1024 * 1024)).item)
    }
    @Test fun `SAF and binary records are allowed to reach full-byte uploader`() {
        val item = requireNotNull(DownloadLocalImportPlanner.plan(record(format = "danuni.binpb")).item)
        assertEquals("content://documents/episodes/99", item.sourceUri)
        assertTrue(item.canImport)
    }
    @Test fun `failed downloads cannot be imported`() {
        assertNull(DownloadLocalImportPlanner.plan(record(status = "failed")).item)
    }
    @Test fun `normalization preserves all xml rows text and colors`() {
        val xml = "<i>" + (1..1500).joinToString("") { "<d p=\"$it,5,25,255,0,0,0,0\">弹幕$it &amp; 原始内容</d>" } + "</i>"
        val converted = requireNotNull(normalizeForLocalLibrary(xml.toByteArray(), "demo.bili.xml"))
        val comments = JSONObject(converted.toString(Charsets.UTF_8)).getJSONArray("comments")
        assertEquals(1500, comments.length())
        assertEquals("弹幕1500 & 原始内容", comments.getJSONObject(1499).getString("m"))
        assertEquals("1500.00,5,255", comments.getJSONObject(1499).getString("p"))
    }
    @Test fun `normalization supports exported JSON arrays without changing mode`() {
        val converted = requireNotNull(normalizeForLocalLibrary("[{\"p\":\"12.3,4,65535\",\"m\":\"底部\"}]".toByteArray(), "demo.danuni.json"))
        val row = JSONObject(converted.toString(Charsets.UTF_8)).getJSONArray("comments").getJSONObject(0)
        assertEquals("12.30,4,65535", row.getString("p"))
        assertEquals("底部", row.getString("m"))
    }
    @Test fun `ASS BOM and comma-containing text survive fallback`() {
        val text = "[Script Info]\nDialogue: 0,0:01:02.50,0:01:08.00,Default,,0,0,0,,测试,含逗号\\N第二行"
        val bytes = byteArrayOf(0xff.toByte(), 0xfe.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        val converted = requireNotNull(normalizeForLocalLibrary(bytes, "demo.ass"))
        val row = JSONObject(converted.toString(Charsets.UTF_8)).getJSONArray("comments").getJSONObject(0)
        assertEquals("62.50,1,16777215", row.getString("p"))
        assertEquals("测试,含逗号\n第二行", row.getString("m"))
    }
    @Test fun `unsupported binary is not falsely reported as imported content`() {
        assertNull(normalizeForLocalLibrary(byteArrayOf(0, 1, 2, 3, 0, 5), "demo.danuni.binpb"))
    }
    @Test fun `filename and record years are not replaced by a conflicting reused directory`() {
        val input = record("同名剧(2023)_E01_youku.xml", 1, "同名剧(2023)【电视剧】from youku")
            .copy(relativePath = "同名剧(2013)【电视剧】from youku/同名剧(2023)_E01_youku.xml")
        assertEquals(2023, DownloadLocalImportPlanner.metadata(input).year)
        assertEquals(2023, DownloadLocalImportPlanner.metadata(input.copy(fileName = "E01.xml")).year)
        assertEquals(2024, DownloadLocalImportPlanner.metadata(input.copy(fileName = "同名剧(2024)_E01.xml")).year)
    }
}
