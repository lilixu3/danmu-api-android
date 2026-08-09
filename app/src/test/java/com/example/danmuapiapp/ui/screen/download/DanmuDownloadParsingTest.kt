package com.example.danmuapiapp.ui.screen.download

import org.junit.Assert.assertEquals
import org.junit.Test

class DanmuDownloadParsingTest {

    @Test
    fun `episode parser keeps core source url`() {
        val episodes = parseEpisodeCandidates(
            """
            {
              "success": true,
              "bangumi": {
                "episodes": [
                  {
                    "episodeId": 12783,
                    "episodeNumber": "1",
                    "episodeTitle": "【iqiyi】第1集",
                    "url": "https://www.iqiyi.com/v_example.html"
                  }
                ]
              }
            }
            """.trimIndent()
        )

        assertEquals(1, episodes.size)
        assertEquals("https://www.iqiyi.com/v_example.html", episodes.single().sourceUrl)
        assertEquals("iqiyi", episodes.single().source)
    }

    @Test
    fun `episode parser accepts sourceUrl alias`() {
        val episodes = parseEpisodeCandidates(
            """
            {
              "episodes": [
                {
                  "episodeId": 9,
                  "episodeNumber": 2,
                  "episodeTitle": "第二集",
                  "sourceUrl": "https://example.com/episode/2"
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals("https://example.com/episode/2", episodes.single().sourceUrl)
    }
}
