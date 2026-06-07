package com.example.danmuapiapp.ui.screen.apitest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualUrlDanmuSelectionFactoryTest {

    @Test
    fun `manual url search builds anime candidate first instead of danmu insight`() {
        val selection = buildManualUrlDanmuSelection(
            inputUrl = "https://v.qq.com/x/cover/example/episode.html",
            metadata = UrlDanmuMetadata(
                title = "凡人修仙传",
                episodeTitle = "凡人修仙传 第12集",
                posterUrl = "https://example.com/poster.jpg",
                year = "2024",
                episodeLabel = "第12集",
                platformLabel = "腾讯视频"
            ),
            metadataTrace = emptyList()
        )

        assertTrue(selection.anime.animeId < 0L)
        assertEquals("凡人修仙传", selection.anime.title)
        assertEquals("https://example.com/poster.jpg", selection.anime.imageUrl)
        assertEquals("腾讯视频", selection.anime.sourceLabel)
        assertEquals("2024", selection.anime.year)
        assertEquals("第12集", selection.anime.episodeLabel)
        assertEquals(1, selection.anime.episodeCount)

        assertTrue(selection.episode.episodeId < 0L)
        assertEquals(12, selection.episode.episodeNumber)
        assertEquals("凡人修仙传 第12集", selection.episode.title)
        assertEquals("腾讯视频", selection.episode.source)
    }

    @Test
    fun `manual url candidate falls back safely when metadata is unavailable`() {
        val selection = buildManualUrlDanmuSelection(
            inputUrl = "https://example.com/video?id=1",
            metadata = null,
            metadataTrace = emptyList()
        )

        assertEquals("https://example.com/video?id=1", selection.anime.title)
        assertEquals("URL", selection.anime.sourceLabel)
        assertEquals("URL 直达集", selection.episode.title)
        assertEquals(1, selection.episode.episodeNumber)
    }
}
