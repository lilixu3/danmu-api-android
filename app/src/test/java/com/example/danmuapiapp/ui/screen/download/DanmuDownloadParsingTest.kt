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
    fun bothPickersUseUniqueTitleNumbersAndRetainSequenceFallbacks() {
        val raw = """{"episodes":[
            {"id":10,"number":1,"title":"【qq】第1集"},
            {"id":20,"number":2,"title":"【qq】EP03"},
            {"id":30,"number":3,"title":"特别节目"},
            {"id":40,"number":4,"title":"第8集 上"},
            {"id":50,"number":5,"title":"第8集 下"},
            {"id":60,"title":"第1期下"}
        ]}"""
        for (episodes in listOf(parseEpisodeCandidates(raw), parsePushEpisodeCandidates(raw))) {
            assertEquals(listOf(1, 3, 3, 4, 5, 6), episodes.map { it.episodeNumber })
            assertEquals(20L, episodes.first { it.episodeNumber == 3 }.episodeId)
        }
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
