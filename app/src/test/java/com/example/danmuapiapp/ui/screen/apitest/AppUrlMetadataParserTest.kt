package com.example.danmuapiapp.ui.screen.apitest

import org.junit.Assert.*
import org.junit.Test

class AppUrlMetadataParserTest {
    private val url = "https://www.bilibili.com/video/BVtest?p=2"
    private val body = """{"success":true,"appUrlMetadata":{"version":1,"inputUrl":"$url","canonicalUrl":"$url",
        "title":"康斯坦丁","platformLabel":"哔哩哔哩","posterUrl":"https://site.test/current.jpg",
        "selectedEpisodeId":"22","episodesComplete":true,"episodes":[
          {"id":"11","title":"上部","url":"https://www.bilibili.com/video/BVtest?p=1","number":1,"label":"P1"},
          {"id":"22","title":"下部","url":"$url","number":2,"label":"P2"}]}}"""

    @Test
    fun `app extension preserves episode selection through candidate creation`() {
        val metadata = parseAppUrlMetadata(body, url)!!
        val result = buildManualUrlDanmuSelection(url, metadata)
        assertEquals("下部", result.episode.title)
        assertEquals(url, result.episode.sourceUrl)
        assertEquals(2, result.anime.directEpisodes.size)
    }

    @Test
    fun `older core and stale response cannot supply another videos metadata`() {
        assertNull(parseAppUrlMetadata("""{"success":true,"animes":[{"animeTitle":"占位条目"}]}""", url))
        assertNull(parseAppUrlMetadata(body, "https://www.bilibili.com/video/BVother"))
        assertNull(parseAppUrlMetadata(body.replace("\"success\":true", "\"success\":false"), url))
    }
}
