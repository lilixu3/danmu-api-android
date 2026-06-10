package com.example.danmuapiapp.data.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoShellAutoPushLogicTest {

    @Test
    fun `parses fongmi media json into playback media`() {
        val media = VideoShellAutoPushLogic.parseMediaJson(
            """
            {
              "url": "https://example.com/video/凡人修仙传_S01E08.mp4",
              "state": 3,
              "title": "凡人修仙传",
              "artist": "第8集",
              "duration": 1456000,
              "position": 30000,
              "episode": 8
            }
            """.trimIndent()
        )

        requireNotNull(media)
        assertEquals("凡人修仙传", media.title)
        assertEquals("第8集", media.episodeText)
        assertEquals(8, media.episodeNumber)
        assertEquals(3, media.state)
        assertEquals("https://example.com/video/凡人修仙传_S01E08.mp4", media.url)
    }

    @Test
    fun `ignores empty media json`() {
        assertNull(VideoShellAutoPushLogic.parseMediaJson("{}"))
        assertNull(VideoShellAutoPushLogic.parseMediaJson("not-json"))
    }

    @Test
    fun `builds core fongmi candidate url from running api without random token`() {
        val media = VideoShellPlaybackMedia(
            title = "长安的荔枝",
            episodeText = "第 12 集",
            url = "http://video.example/12.m3u8",
            state = 3,
            episodeNumber = 12
        )

        val url = VideoShellAutoPushLogic.buildCoreFongmiDanmakuUrl(
            coreBaseUrl = "http://127.0.0.1:9321/87654321/",
            media = media
        )

        assertTrue(url.startsWith("http://127.0.0.1:9321/87654321/api/v2/fongmi/danmaku?"))
        assertTrue(url.contains("name=%E9%95%BF%E5%AE%89%E7%9A%84%E8%8D%94%E6%9E%9D"))
        assertTrue(url.contains("episode=%E7%AC%AC+12+%E9%9B%86"))
        assertFalse(url.contains("token="))
    }

    @Test
    fun `selects first valid candidate url from core response`() {
        val selected = VideoShellAutoPushLogic.selectCandidateUrl(
            """
            [
              {"name":"无效"},
              {"name":"凡人修仙传 - 第8集", "url":"http://127.0.0.1:9321/api/v2/comment/1008?format=xml"},
              {"name":"其它", "url":"http://127.0.0.1:9321/api/v2/comment/1009?format=xml"}
            ]
            """.trimIndent()
        )

        assertEquals("http://127.0.0.1:9321/api/v2/comment/1008?format=xml", selected)
    }

    @Test
    fun `builds shell action url with encoded danmaku path`() {
        val pushUrl = VideoShellAutoPushLogic.buildShellPushUrl(
            port = 9978,
            danmakuUrl = "http://127.0.0.1:9321/api/v2/comment/1008?format=xml&x=1 2"
        )

        assertEquals(
            "http://127.0.0.1:9978/action?do=refresh&type=danmaku&path=http%3A%2F%2F127.0.0.1%3A9321%2Fapi%2Fv2%2Fcomment%2F1008%3Fformat%3Dxml%26x%3D1+2",
            pushUrl
        )
    }

    @Test
    fun `deduplicates same playback signature but allows episode change`() {
        val first = VideoShellPlaybackMedia("凡人修仙传", "第8集", "u1", state = 3, episodeNumber = 8)
        val same = first.copy(positionMs = 20_000)
        val next = first.copy(episodeText = "第9集", episodeNumber = 9)
        val dedupe = VideoShellPushDedupe()

        assertTrue(dedupe.shouldPush(first))
        dedupe.markPushed(first)
        assertFalse(dedupe.shouldPush(same))
        assertTrue(dedupe.shouldPush(next))
    }
}
