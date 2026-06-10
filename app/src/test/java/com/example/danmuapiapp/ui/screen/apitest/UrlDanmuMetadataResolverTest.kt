package com.example.danmuapiapp.ui.screen.apitest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
              "videos": [ { "videoName": "屌丝男士第四季第1集", "order": 1, "publishTime": "2015-05-19" } ]
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
                  window.__INFO__ = { video: { title:"甄嬛传01", pTitle:"甄嬛传", pPicShutu:"http://i1.letvimg.com/poster-v.jpg", pPic:"http://i1.letvimg.com/poster.jpg", releasedate:"" } };
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
}
