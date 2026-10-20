package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.*
import com.example.danmuapiapp.ui.screen.localdanmu.*
import org.junit.Assert.*
import org.junit.Test

class LocalDanmuTypeInferenceTest {
    @Test fun `unlabelled single files are unknown instead of defaulting to TV`() {
        val result = LocalDanmuMetadataResolver.resolve("星际穿越.2014.xml")
        assertEquals("", result.type)
        assertNull(result.episode)
        assertEquals(LocalDanmuParseConfidence.Low, result.confidence)
    }

    @Test fun `movie directory supplies type without becoming the movie title`() {
        val result = LocalDanmuMetadataResolver.resolve("星际穿越(2014).xml", "Movies/星际穿越(2014).xml")
        assertEquals("movie", result.type)
        assertEquals("星际穿越", result.title)
        assertNull(result.episode)
        assertFalse(result.notes.any { "默认第 1 集" in it })
    }

    @Test fun `context and persisted type survive generic E01 download names`() {
        assertEquals("movie", LocalDanmuMetadataResolver.resolve("星际穿越_E01.xml", contextName = "星际穿越【电影】").type)
        val record = DanmuDownloadRecord(animeTitle = "星际穿越(2014)", episodeTitle = "正片", episodeId = 1,
            episodeNo = 1, source = "test", format = "xml", status = "success", fileName = "星际穿越(2014)_E01.xml", mediaType = "movie")
        val result = DownloadLocalImportPlanner.metadata(record)
        assertEquals("movie", result.type)
        assertNull(result.episode)
        val unknown = DownloadLocalImportPlanner.metadata(record.copy(fileName = "星际穿越(2014).xml", mediaType = ""))
        assertEquals("", unknown.type)
        assertNull(unknown.episode)
        assertEquals("tv", LocalDanmuMetadataResolver.resolve("剧名.S02E03.xml").type)
        assertEquals("", LocalDanmuMetadataResolver.resolve("MovieMaker(2020).xml").type)
    }

    @Test fun `bulk overrides preserve unselected items and automatic detection`() {
        val item = LocalDanmuBatchItem(1, "file:///one.xml", "one.xml", 1, "xml", "text/xml", "测试", 2020,
            LocalDanmuType.Unknown, 1, null)
        val skipped = item.copy(id = 2, selected = false)
        assertFalse(item.canImport)
        assertNotNull(validateLocalDanmuEdit(LocalDanmuEditScope.Group, "测试", 2020,
            LocalDanmuType.Unknown, 1, null, "测试.xml", 2026))
        val movies = applyImportType(listOf(item, skipped), LocalDanmuType.Movie)
        assertTrue(movies.first().canImport)
        assertEquals(skipped, movies.last())
        assertNull(movies.first().episode)
        assertEquals(LocalDanmuType.Unknown, applyImportType(movies, null).first().type)
    }
}
