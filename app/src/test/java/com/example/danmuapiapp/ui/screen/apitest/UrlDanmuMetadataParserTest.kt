package com.example.danmuapiapp.ui.screen.apitest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class UrlDanmuMetadataParserTest {

    @Test
    fun `parses tencent desktop metadata and avoids avif poster`() {
        val html = """
            <html>
              <head>
                <title>第1集_高清1080P在线观看平台_腾讯视频</title>
                <meta property="og:title" content="第1集_高清1080P在线观看平台_腾讯视频" />
                <meta property="og:image" content="https://vpic-cover.puui.qpic.cn/r31988651g6/r31988651g6_1775669338_hz.jpg/496?imageMogr2/format/avif/thumbnail/252x" />
              </head>
              <body>
                window.COVER_INFO = {id:"mzc003ajnrzy399",second_title:"《罪妻边王》",episode_all:"144",new_pic_hz:"https://vcover-hz-pic.puui.qpic.cn/vcover_hz_pic/0/mzc003ajnrzy3991775668803/0",new_pic_vt:"https://vcover-vt-pic.puui.qpic.cn/vcover_vt_pic/0/mzc003ajnrzy3991775668804/0"};
                videoInfoMap:{r31988651g6:{title:"第1集",unionTitle:"第1集",imgUrl:"https://vpic-cover.puui.qpic.cn/r31988651g6/r31988651g6_1775669338_hz.jpg/496?imageMogr2/format/avif/thumbnail/252x"}}
              </body>
            </html>
        """.trimIndent()

        val metadata = parseUrlDanmuMetadata(
            inputUrl = "https://v.qq.com/x/cover/mzc003ajnrzy399/r31988651g6.html",
            html = html
        )!!

        assertEquals("罪妻边王", metadata.title)
        assertEquals("第1集", metadata.episodeLabel)
        assertEquals("腾讯视频", metadata.platformLabel)
        assertEquals("https://vcover-vt-pic.puui.qpic.cn/vcover_vt_pic/0/mzc003ajnrzy3991775668804/0", metadata.posterUrl)
        assertFalse(metadata.posterUrl.contains("format/avif", ignoreCase = true))
    }

    @Test
    fun `parses tencent mobile unicode escaped poster and year`() {
        val html = """
            <html>
              <head>
                <title>【腾讯视频】 妖神记 01</title>
                <meta itemprop="image" content="//vcover-hz-pic.puui.qpic.cn/vcover_hz_pic/0/yl6lapwmmx5ivew1754982955561/0?max_age=7776000" />
              </head>
              <body>
                window.COVER_INFO = {"id":"yl6lapwmmx5ivew","year":"2017","second_title":"妖神记","pic496x280":"http:\\u002F\\u002Fpuui.qpic.cn\\u002Fvpic_cover\\u002Fm0501m4tc0q\\u002Fm0501m4tc0q_hz.jpg\\u002F496"}
                window.VIDEO_INFO = {vid:"m0501m4tc0q",title:"妖神记 第1集"};
              </body>
            </html>
        """.trimIndent()

        val metadata = parseUrlDanmuMetadata(
            inputUrl = "https://m.v.qq.com/x/m/play?cid=yl6lapwmmx5ivew&vid=m0501m4tc0q",
            html = html
        )!!

        assertEquals("妖神记", metadata.title)
        assertEquals("2017", metadata.year)
        assertEquals("第1集", metadata.episodeLabel)
        assertTrue(metadata.posterUrl.startsWith("https://"))
        assertTrue(metadata.posterUrl.contains("qpic.cn"))
    }

    @Test
    fun `normalizes avif qpic poster into jpeg-compatible url`() {
        val normalized = normalizeUrlDanmuPosterUrl(
            pageUrl = "https://v.qq.com/x/cover/a/b.html",
            assetUrl = "https://vpic-cover.puui.qpic.cn/r31988651g6/r31988651g6_1775669338_hz.jpg/496?imageMogr2/format/avif/thumbnail/252x"
        )

        assertEquals(
            "https://vpic-cover.puui.qpic.cn/r31988651g6/r31988651g6_1775669338_hz.jpg/496?imageMogr2/thumbnail/252x",
            normalized
        )
    }

    @Test
    fun `unrelated recommendations cannot override current page title poster year or episode`() {
        val metadata = parseUrlDanmuMetadata("https://www.bilibili.com/video/BV11xX5BjECL", """
            <title>康斯坦丁_哔哩哔哩_bilibili</title>
            <meta property="og:image" content="https://i0.hdslb.com/current-video-cover.jpg" />
            <script>{"title":"广告", "pic":"https://i0.hdslb.com/a.jpg", "year":"1999", "episode":"9"}</script>
        """)!!
        assertEquals("康斯坦丁", metadata.title)
        assertEquals("https://i0.hdslb.com/current-video-cover.jpg", metadata.posterUrl)
        assertEquals("", metadata.episodeLabel)
        assertEquals("", metadata.year)
    }

    @Test
    fun `page declaring another video is rejected`() {
        assertNull(parseUrlDanmuMetadata("https://www.bilibili.com/video/BVcurrent", """
            <title>另一个视频</title><meta property="og:url" content="https://www.bilibili.com/video/BVother" />
        """))
    }

    @Test
    fun `unbound cover state is ignored even when it offers a vertical poster`() {
        val metadata = parseUrlDanmuMetadata("https://v.qq.com/x/cover/current/ep.html", """
            <title>当前视频</title><meta property="og:image" content="https://site.test/current.jpg" />
            <script>window.COVER_INFO = {id:"other", title:"别的剧",new_pic_vt:"https://site.test/wrong.jpg"};</script>
        """)!!
        assertEquals("当前视频", metadata.title)
        assertEquals("https://site.test/current.jpg", metadata.posterUrl)
    }
}
