package com.example.danmuapiapp.ui.screen.apitest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class UrlDanmuMetadataResolverTest {

    @Test
    fun `iqiyi entity id and base info metadata are parsed`() {
        assertEquals("385274600", UrlDanmuMetadataPlatformParsers.iqiyiEntityIdFromVideoId("19rrok4nt0"))

        val json = """
            {
              "data": {
                "base_data": {
                  "title": "航海王",
                  "current_video_title": "第1集 我是路飞！ 将要成为海贼王的男人",
                  "current_phase_title": "第1集",
                  "current_video_order": 1,
                  "current_video_year": "1999",
                  "image_url": "https://pic8.iqiyipic.com/image/20260402/poster.webp",
                  "horizontal_image_url": "https://pic8.iqiyipic.com/image/20260402/horizontal.webp"
                }
              }
            }
        """.trimIndent()

        val metadata = UrlDanmuMetadataPlatformParsers.parseIqiyiBaseInfoJson(
            inputUrl = "https://www.iqiyi.com/v_19rrok4nt0.html",
            json = json
        )!!

        assertEquals("航海王", metadata.title)
        assertEquals("第1集 我是路飞！ 将要成为海贼王的男人", metadata.episodeTitle)
        assertEquals("第1集", metadata.episodeLabel)
        assertEquals("1999", metadata.year)
        assertEquals("https://pic8.iqiyipic.com/image/20260402/poster.webp", metadata.posterUrl)
        assertEquals("爱奇艺", metadata.platformLabel)
    }

    @Test
    fun `youku metadata prefers show poster and release year`() {
        val videoJson = """
            {
              "title": "乡村爱情 01",
              "thumbnail": "https://m.ykimg.com/video-thumb",
              "bigThumbnail": "https://m.ykimg.com/video-big-thumb",
              "published": "2025-01-01 00:00:01",
              "show": { "id": "cbff2984962411de83b1", "name": "乡村爱情" }
            }
        """.trimIndent()
        val showJson = """
            {
              "name": "乡村爱情",
              "poster": "http://m.ykimg.com/poster",
              "poster_large": "http://m.ykimg.com/poster-large",
              "released": "2006-09-30",
              "releasedate_mainland": "2006-09-30"
            }
        """.trimIndent()

        val metadata = UrlDanmuMetadataPlatformParsers.parseYoukuJson(
            inputUrl = "https://v.youku.com/v_show/id_XMTU4Nzg5OTg0.html",
            videoJson = videoJson,
            showJson = showJson
        )!!

        assertEquals("乡村爱情", metadata.title)
        assertEquals("乡村爱情 01", metadata.episodeTitle)
        assertEquals("第1集", metadata.episodeLabel)
        assertEquals("2006", metadata.year)
        assertEquals("http://m.ykimg.com/poster-large", metadata.posterUrl)
    }

    @Test
    fun `mango metadata parses video info`() {
        val json = """
            {
              "data": {
                "info": {
                  "title": "歌手2026",
                  "videoName": "歌手2026 第1期：首轮竞演开启",
                  "clipImage": "https://0img.hitv.com/clip.jpg",
                  "clipImage2": "https://1img.hitv.com/clip-v.jpg",
                  "videoImage": "https://1img.hitv.com/video.jpg",
                  "detail": { "releaseTime": "2026-05-15" }
                }
              }
            }
        """.trimIndent()

        val metadata = UrlDanmuMetadataPlatformParsers.parseMangoVideoInfoJson(
            inputUrl = "https://www.mgtv.com/b/867929/24396695.html",
            json = json
        )!!

        assertEquals("歌手2026", metadata.title)
        assertEquals("歌手2026 第1期：首轮竞演开启", metadata.episodeTitle)
        assertEquals("第1集", metadata.episodeLabel)
        assertEquals("2026", metadata.year)
        assertEquals("https://1img.hitv.com/clip-v.jpg", metadata.posterUrl)
    }

    @Test
    fun `bilibili season metadata parses cover publish year and selected episode`() {
        val json = """
            {
              "code": 0,
              "result": {
                "title": "凡人修仙传",
                "season_title": "凡人修仙传",
                "cover": "https://i0.hdslb.com/bfs/bangumi/cover.png",
                "publish": { "pub_time": "2020-07-25 20:00:00" },
                "episodes": [
                  { "id": 733316, "title": "1", "long_title": "风起天南", "cover": "https://i0.hdslb.com/bfs/archive/ep.jpg", "pub_time": 1675566000 }
                ]
              }
            }
        """.trimIndent()

        val metadata = UrlDanmuMetadataPlatformParsers.parseBilibiliSeasonJson(
            inputUrl = "https://www.bilibili.com/bangumi/play/ep733316",
            json = json,
            preferredEpId = "733316"
        )!!

        assertEquals("凡人修仙传", metadata.title)
        assertEquals("凡人修仙传 第1集 风起天南", metadata.episodeTitle)
        assertEquals("第1集", metadata.episodeLabel)
        assertEquals("2020", metadata.year)
        assertEquals("https://i0.hdslb.com/bfs/bangumi/cover.png", metadata.posterUrl)
    }

    @Test
    fun `migu metadata parses h5 vertical poster and year`() {
        val json = """
            {
              "code": 200,
              "body": {
                "data": {
                  "name": "24/25赛季西甲第10轮全场回放",
                  "year": "2024",
                  "publishTime": "2024-10-19 22:16:30",
                  "h5pics": {
                    "highResolutionV": "https://wapx.cmvideo.cn/poster-v.jpg",
                    "highResolutionH": "https://wapx.cmvideo.cn/poster-h.jpg"
                  },
                  "pics": {
                    "highResolutionV": "https://wapx.cmvideo.cn/poster-v.webp"
                  }
                }
              }
            }
        """.trimIndent()

        val metadata = UrlDanmuMetadataPlatformParsers.parseMiguContentInfoJson(
            inputUrl = "https://www.miguvideo.com/mgs/website/prd/detail.html?cid=938565946",
            json = json
        )!!

        assertEquals("24/25赛季西甲第10轮全场回放", metadata.title)
        assertEquals("2024", metadata.year)
        assertEquals("https://wapx.cmvideo.cn/poster-v.jpg", metadata.posterUrl)
        assertEquals("咪咕视频", metadata.platformLabel)
    }

    @Test
    fun `sohu metadata parses playlist id and videolist metadata`() {
        val html = """
            <script>
              var videoData = { sid:8400079, plid:8400079, albumPicUrl:"http://photocdn.tv.sohu.com/album.jpg" };
            </script>
        """.trimIndent()
        assertEquals("8400079", UrlDanmuMetadataPlatformParsers.extractSohuPlaylistId(html))

        val json = """
            {
              "albumName": "屌丝男士第四季",
              "publishYear": "2015",
              "largeVerPicUrl": "http://photocdn.tv.sohu.com/ver.jpg",
              "albumPicUrl": "http://photocdn.tv.sohu.com/album.jpg",
              "videos": [ { "pageUrl": "https://m.tv.sohu.com/v/example.html", "videoName": "屌丝男士第四季第1集", "order": 1, "publishTime": "2015-05-19" } ]
            }
        """.trimIndent()

        val metadata = UrlDanmuMetadataPlatformParsers.parseSohuVideoListJson(
            inputUrl = "https://m.tv.sohu.com/v/example.html",
            json = json
        )!!

        assertEquals("屌丝男士第四季", metadata.title)
        assertEquals("屌丝男士第四季第1集", metadata.episodeTitle)
        assertEquals("第1集", metadata.episodeLabel)
        assertEquals("2015", metadata.year)
        assertEquals("http://photocdn.tv.sohu.com/ver.jpg", metadata.posterUrl)
    }

    @Test
    fun `leshi metadata parses pc page info`() {
        val html = """
            <html>
              <head><title>甄嬛传01 - 在线观看 - 电视剧 - 乐视视频</title><meta name="irAlbumName" content="甄嬛传" /></head>
              <body>
                <script>
                  window.__INFO__ = { video: { vid:1578861, title:"甄嬛传01", pTitle:"甄嬛传", pPicShutu:"http://i1.letvimg.com/poster-v.jpg", pPic:"http://i1.letvimg.com/poster.jpg", releasedate:"" } };
                </script>
              </body>
            </html>
        """.trimIndent()

        val metadata = UrlDanmuMetadataPlatformParsers.parseLeshiHtml(
            inputUrl = "https://www.le.com/ptv/vplay/1578861.html",
            html = html
        )!!

        assertEquals("甄嬛传", metadata.title)
        assertEquals("甄嬛传01", metadata.episodeTitle)
        assertEquals("http://i1.letvimg.com/poster-v.jpg", metadata.posterUrl)
        assertEquals("乐视视频", metadata.platformLabel)
        assertTrue(metadata.year.isBlank())
    }

    private val viewJson = """{"code":0,"data":{"bvid":"BV11xX5BjECL","aid":123,"title":"康斯坦丁","pic":"https://i0.hdslb.com/current.jpg",
        "pages":[{"cid":11,"page":1,"part":"上部"},{"cid":22,"page":2,"part":"下部"}],
        "ugc_season":{"sections":[{"episodes":[{"id":33,"title":"别的电影"}]}]}}}"""

    @Test
    fun `bilibili selects exact page and excludes uploader collection`() {
        val metadata = UrlDanmuMetadataPlatformParsers.parseBilibiliViewJson(
            "https://www.bilibili.com/video/BV11xX5BjECL?p=2", viewJson)!!
        assertEquals(2, metadata.episodes.size)
        assertEquals("22", metadata.selectedEpisodeId)
        assertEquals("下部", metadata.episodeTitle)
        assertEquals("P2", metadata.episodeLabel)
        assertEquals("https://www.bilibili.com/video/BV11xX5BjECL?p=2", metadata.canonicalUrl)
        assertTrue(metadata.episodesComplete)
    }

    @Test
    fun `bilibili rejects wrong video and never defaults an invalid page to P1`() {
        assertNull(UrlDanmuMetadataPlatformParsers.parseBilibiliViewJson("https://www.bilibili.com/video/BVwrong", viewJson))
        for (page in listOf("9", "0", "bad")) {
            val result = UrlDanmuMetadataPlatformParsers.parseBilibiliViewJson(
                "https://www.bilibili.com/video/BV11xX5BjECL?p=$page", viewJson)!!
            assertEquals("", result.selectedEpisodeId)
            assertTrue(result.episodes.isEmpty())
            assertFalse(result.episodesComplete)
        }
    }

    @Test
    fun `bilibili missing requested episode preserves URL and never takes the first episode`() {
        val result = UrlDanmuMetadataPlatformParsers.parseBilibiliSeasonJson(
            "https://www.bilibili.com/bangumi/play/ep99",
            """{"code":0,"result":{"title":"剧名","episodes":[{"id":1,"title":"1"}]}}""", "99")!!
        assertEquals("", result.selectedEpisodeId)
        assertTrue(result.episodes.isEmpty())
        assertEquals("https://www.bilibili.com/bangumi/play/ep99", result.canonicalUrl)
    }

    @Test
    fun `bilibili special episodes keep their group and exact identity`() {
        val result = UrlDanmuMetadataPlatformParsers.parseBilibiliSeasonJson(
            "https://www.bilibili.com/bangumi/play/ep2",
            """{"code":0,"result":{"title":"剧名","episodes":[{"id":1,"title":"1"}],
                "section":[{"title":"特别篇","episodes":[{"id":2,"title":"SP","long_title":"花絮"}]}]}}""", "2")!!
        assertEquals("2", result.selectedEpisodeId)
        assertEquals("特别篇", result.episodes[1].group)
    }

    @Test
    fun `source detection validates host rather than text in query`() {
        assertEquals(UrlDanmuMetadataPlatform.Unknown, UrlDanmuMetadataPlatform.detect("https://evil.test/?url=https://www.bilibili.com/video/BVabc"))
        assertEquals(UrlDanmuMetadataPlatform.Unknown, UrlDanmuMetadataPlatform.detect("https://b23.tv.evil.test/a"))
    }

    @Test
    fun `short URL is expanded before the platform API and never needs the video HTML`() = runBlocking {
        val requests = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests.add(request.url.toString())
            val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).message("OK")
            when (request.url.host) {
                "b23.tv" -> response.code(302).header("Location", "https://www.bilibili.com/video/BV11xX5BjECL?p=2").body("".toResponseBody()).build()
                "api.bilibili.com" -> response.code(200).body(viewJson.toResponseBody()).build()
                else -> throw AssertionError("Unexpected HTML request: ${request.url.host}")
            }
        }.build()
        val result = UrlDanmuMetadataResolver(client).resolve("https://b23.tv/NZWHH6K")!!
        assertEquals("22", result.selectedEpisodeId)
        assertEquals("https://i0.hdslb.com/current.jpg", result.posterUrl)
        assertEquals(2, requests.size)
        val withCollection = UrlDanmuMetadataResolver(client).resolve("https://www.bilibili.com/video/BV11xX5BjECL?p=2&season_id=6674023")!!
        assertEquals("22", withCollection.selectedEpisodeId)
        assertTrue(requests.last().contains("/x/web-interface/view"))
    }
}
