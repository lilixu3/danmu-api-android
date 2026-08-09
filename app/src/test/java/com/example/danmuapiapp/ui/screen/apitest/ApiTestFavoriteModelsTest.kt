package com.example.danmuapiapp.ui.screen.apitest

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiTestFavoriteModelsTest {

    @Test
    fun `favorite list parses metadata and weekly schedule`() {
        val result = parseFavoriteListResponse(
            """
            {
              "success": true,
              "scheduledRefreshSupported": true,
              "favorites": [
                {
                  "keyword": "凡人修仙传",
                  "animeTitle": "凡人修仙传",
                  "source": "bilibili、dandan",
                  "sources": ["bilibili", "dandan"],
                  "imageUrl": "https://example.com/poster.jpg",
                  "episodeCount": 42,
                  "resultsCount": 2,
                  "timestamp": 1000,
                  "lastRefreshAt": 2000,
                  "refreshSchedule": {
                    "frequency": "weekly",
                    "time": "03:05",
                    "weekday": 7,
                    "timezone": "Asia/Shanghai",
                    "nextRunAt": 3000,
                    "lastStatus": "success"
                  }
                }
              ]
            }
            """.trimIndent()
        )

        assertTrue(result.scheduledRefreshSupported)
        val item = result.favorites.single()
        assertEquals("凡人修仙传", item.keyword)
        assertEquals(listOf("bilibili", "dandan"), item.sources)
        assertEquals(42, item.episodeCount)
        assertEquals(2000L, item.lastRefreshAt)
        assertEquals(FavoriteScheduleFrequency.Weekly, item.refreshSchedule?.frequency)
        assertEquals(7, item.refreshSchedule?.weekday)
        assertEquals("03:05", item.refreshSchedule?.time)
    }

    @Test
    fun `favorite matching requires complete title and ignores season cache suffix`() {
        val item = ApiTestFavoriteItem(
            keyword = "火影忍者",
            animeTitle = "火影忍者",
            source = "dandan",
            sources = listOf("dandan"),
            imageUrl = "",
            episodeCount = 720,
            resultsCount = 1,
            timestamp = 1,
            lastRefreshAt = 1
        )

        assertEquals(item, findFavoriteForKeyword(listOf(item), "火影忍者"))
        assertEquals(item, findFavoriteForKeyword(listOf(item), "火影忍者_S02"))
        assertNull(findFavoriteForKeyword(listOf(item), "火影忍者 疾风传"))
        assertNull(findFavoriteForKeyword(listOf(item), "火"))
        assertNull(findFavoriteForKeyword(listOf(item), "海贼王"))
    }

    @Test
    fun `short and long favorite titles never collide`() {
        val short = favorite("火影忍者")
        val long = favorite("火影忍者 疾风传")

        assertEquals(short, findFavoriteForKeyword(listOf(long, short), "火影忍者"))
        assertEquals(long, findFavoriteForKeyword(listOf(short, long), "火影忍者 疾风传_S01"))
    }

    @Test
    fun `schedule body supports weekly and disabling`() {
        val weekly = JSONObject(
            buildFavoriteScheduleBody(
                keyword = "测试动画",
                schedule = FavoriteScheduleDraft(
                    frequency = FavoriteScheduleFrequency.Weekly,
                    time = "3:05",
                    weekday = 2
                )
            )
        )
        val schedule = weekly.getJSONObject("schedule")
        assertEquals("weekly", schedule.getString("frequency"))
        assertEquals("03:05", schedule.getString("time"))
        assertEquals(2, schedule.getInt("weekday"))

        val disabled = JSONObject(buildFavoriteScheduleBody("测试动画", null))
        assertTrue(disabled.isNull("schedule"))
        assertFalse(disabled.getString("keyword").isBlank())
    }

    private fun favorite(title: String) = ApiTestFavoriteItem(
        keyword = title,
        animeTitle = title,
        source = "dandan",
        sources = listOf("dandan"),
        imageUrl = "",
        episodeCount = 1,
        resultsCount = 1,
        timestamp = 1,
        lastRefreshAt = 1
    )
}
