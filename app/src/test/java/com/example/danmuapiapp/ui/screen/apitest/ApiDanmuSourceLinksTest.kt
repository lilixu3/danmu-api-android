package com.example.danmuapiapp.ui.screen.apitest

import org.junit.Assert.*
import org.junit.Test

class ApiDanmuSourceLinksTest {
    @Test fun `merged links retain each actual URL for copy and open`() {
        val links = detailSourceLinks("tencent:https://v.qq.com/x/cover/a.html\$\$\$imgo:https://www.mgtv.com/b/a.html")
        assertEquals(listOf("https://v.qq.com/x/cover/a.html", "https://www.mgtv.com/b/a.html"), links.map { it.second })
        assertTrue(links.all { it.first.isNotBlank() })
    }

    @Test fun `single URL preserves scheme port and query`() {
        val url = "https://example.com:8443/video?a=1&b=2"
        assertEquals(listOf("自定义" to url), detailSourceLinks(url, "自定义"))
    }

    @Test fun `source with numeric ID remains copyable`() {
        assertEquals("123456", detailSourceLinks("bilibili:123456").single().second)
    }

    @Test fun `empty segments and empty source targets are omitted`() {
        assertTrue(detailSourceLinks("  \$\$\$tencent: \$\$\$ ").isEmpty())
    }

    @Test fun `schemes are not mistaken for source names`() {
        assertEquals("content://media/123", detailSourceLinks("content://media/123").single().second)
    }

    @Test fun `auto match keeps poster and source URL for detail hero`() {
        val match = parseMatchSelection("""{"matches":[{"episodeId":1001,"animeTitle":"测试番剧","episodeTitle":"第一集","source":"tencent","imageUrl":"https://example.com/poster.jpg","url":"https://v.qq.com/video","year":"2026"}]}""")!!
        assertEquals("https://example.com/poster.jpg", match.posterUrl)
        assertEquals("https://v.qq.com/video", match.sourceUrl)
        assertEquals("2026", match.year)
    }
}
