package com.example.danmuapiapp.ui.screen.apitest

import com.example.danmuapiapp.domain.model.DanmuDownloadRecord
import kotlinx.serialization.json.Json
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
            )
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
        assertEquals("https://v.qq.com/x/cover/example/episode.html", selection.episode.sourceUrl)
    }

    @Test
    fun `manual url candidate falls back safely when metadata is unavailable`() {
        val selection = buildManualUrlDanmuSelection(
            inputUrl = "https://example.com/video?id=1",
            metadata = null
        )

        assertEquals("https://example.com/video?id=1", selection.anime.title)
        assertEquals("URL", selection.anime.sourceLabel)
        assertEquals("URL 直达集", selection.episode.title)
        assertEquals(1, selection.episode.episodeNumber)
    }

    @Test
    fun `downloaded URL selection remains eligible for automatic local import after persistence`() {
        val selection = buildManualUrlDanmuSelection("https://www.bilibili.com/video/BVtest?p=2", null)
        val record = DanmuDownloadRecord(animeTitle = selection.anime.title,
            episodeTitle = selection.episode.title, episodeId = selection.episode.episodeId,
            episodeNo = selection.episode.episodeNumber, source = selection.episode.source,
            format = "xml", status = "success")
        val restored = Json.decodeFromString<DanmuDownloadRecord>(Json.encodeToString(DanmuDownloadRecord.serializer(), record))
        assertTrue(restored.episodeId < 0)
        assertTrue(restored.canAutoImportToLocal)
    }

    @Test
    fun `resolved pages carry distinct IDs and playback links into manual selection`() {
        val metadata = UrlDanmuMetadata(title = "康斯坦丁", posterUrl = "https://site.test/current.jpg",
            platformLabel = "哔哩哔哩", canonicalUrl = "https://www.bilibili.com/video/BVtest?p=2",
            selectedEpisodeId = "22", episodesComplete = true,
            episodes = listOf(UrlDanmuEpisode("11", "上部", "https://www.bilibili.com/video/BVtest?p=1", 1, "P1"),
                UrlDanmuEpisode("22", "下部", "https://www.bilibili.com/video/BVtest?p=2", 2, "P2")))
        val short = buildManualUrlDanmuSelection("https://b23.tv/test", metadata)
        val long = buildManualUrlDanmuSelection(metadata.canonicalUrl, metadata)
        assertEquals(short.anime.animeId, long.anime.animeId)
        assertEquals(2, short.anime.episodeCount)
        assertEquals(2, short.anime.directEpisodes.size)
        assertEquals(2, short.anime.directEpisodes.map { it.episodeId }.distinct().size)
        assertEquals("下部", short.episode.title)
        assertEquals("https://www.bilibili.com/video/BVtest?p=2", short.episode.directUrl)
        assertEquals(short.episode.directUrl, short.episode.sourceUrl)
        assertEquals(short.episode, buildManualUrlEpisodeCandidate(short.anime))
        assertEquals("", short.anime.directMetadataNotice)
    }
}
