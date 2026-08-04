package com.example.danmuapiapp.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmuDownloadFormatTest {

    @Test
    fun `core output formats are all exposed`() {
        assertEquals(
            setOf(
                "json",
                "xml",
                "artplayer.json",
                "baha.json",
                "bili.xml",
                "danuni.json",
                "danuni.binpb",
                "ddplay.json",
                "dplayer.json",
                "vod.json"
            ),
            DanmuDownloadFormat.entries.map { it.value }.toSet()
        )
    }

    @Test
    fun `file name detection prefers full format suffix`() {
        assertEquals(
            DanmuDownloadFormat.ArtplayerJson,
            DanmuDownloadFormat.fromFileName("episode.artplayer.json")
        )
        assertEquals(
            DanmuDownloadFormat.BiliXml,
            DanmuDownloadFormat.fromFileName("episode.BILI.XML")
        )
        assertEquals(
            DanmuDownloadFormat.DanuniBinPb,
            DanmuDownloadFormat.fromFileName("episode.danuni.binpb")
        )
        assertEquals(DanmuDownloadFormat.Json, DanmuDownloadFormat.fromFileName("episode.json"))
        assertNull(DanmuDownloadFormat.fromFileName("episode.ass"))
    }

    @Test
    fun `unknown persisted values are detectable without changing settings fallback`() {
        assertNull(DanmuDownloadFormat.fromValueOrNull("future.format"))
        assertEquals(DanmuDownloadFormat.Xml, DanmuDownloadFormat.fromValue("future.format"))
    }

    @Test
    fun `multi part extension is preserved by filename template`() {
        val preview = renderFileNameTemplatePreview(
            template = "demo.{ext}",
            format = DanmuDownloadFormat.DplayerJson
        )
        assertTrue(preview.endsWith(".dplayer.json"))
    }

    @Test
    fun `old download records without anime id remain readable`() {
        val record = Json.decodeFromString<DanmuDownloadRecord>(
            """{
                "animeTitle":"测试动画",
                "episodeTitle":"第一集",
                "episodeId":1001,
                "episodeNo":1,
                "source":"bilibili",
                "format":"xml",
                "status":"success"
            }""".trimIndent()
        )

        assertEquals(0L, record.animeId)
    }

    @Test
    fun `anime id is preserved from queue task to download input`() {
        val input = DanmuDownloadTask(animeId = 9001L).toInput()

        assertEquals(9001L, input.animeId)
    }
}
