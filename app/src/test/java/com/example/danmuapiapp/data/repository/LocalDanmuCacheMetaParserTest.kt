package com.example.danmuapiapp.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalDanmuCacheMetaParserTest {

    // 真实文件的开头（来自设备上的核心缓存，只截到 comments 之前）。
    private val head = """
        {"resourceKey":"凡人修仙传|2020|tv|116","videoId":"c8cfa258-44f0-4b51-9483-0e100c024b69",
        "title":"凡人修仙传","year":2020,"type":"tv","season":1,"episode":116,
        "filename":"凡人修仙传(2020)【动漫】from bilibili_E116_第116话 星海飞驰40_bilibili1.xml",
        "size":2325506,"format":"XML","status":"ready","count":25259,
        "matchKeys":["凡人修仙传","凡人修仙传 2020 tv"],"comments":[{"p":"14.30,5,16777215","m":"念头通达"}
    """.trimIndent()

    private val tail = """…],"updatedAt":"2026-09-12T11:32:19.322Z"}"""

    @Test
    fun `从头部与尾部还原资源元数据`() {
        val resource = LocalDanmuCacheMetaParser.parse(head, tail)

        requireNotNull(resource)
        assertEquals("凡人修仙传|2020|tv|116", resource.resourceKey)
        assertEquals("凡人修仙传", resource.title)
        assertEquals(2020, resource.year)
        assertEquals("tv", resource.type)
        assertEquals(1, resource.season)
        assertEquals(116, resource.episode)
        assertEquals(2325506L, resource.sizeBytes)
        assertEquals(25259, resource.count)
        assertEquals("XML", resource.format)
        assertEquals("ready", resource.status)
        assertEquals("2026-09-12T11:32:19.322Z", resource.updatedAt)
    }

    @Test
    fun `电影没有集数时 episode 为 null`() {
        val movieHead = """
            {"resourceKey":"某电影|2024|movie|all","videoId":"x","title":"某电影","year":2024,
            "type":"movie","season":1,"episode":null,"filename":"某电影.xml","size":100,
            "format":"XML","status":"ready","count":3,"comments":[]
        """.trimIndent()

        val resource = LocalDanmuCacheMetaParser.parse(movieHead, tail)

        requireNotNull(resource)
        assertNull(resource.episode)
        assertEquals(3, resource.count)
    }

    @Test
    fun `字符串里的转义与中文正常还原`() {
        val escaped = """
            {"resourceKey":"剧|2024|tv|1","videoId":"v","title":"带\"引号\"的剧","year":2024,
            "type":"tv","season":1,"episode":2,"filename":"a\\b.xml","size":1,"format":"XML",
            "status":"ready","count":1,"comments":[]
        """.trimIndent()

        val resource = LocalDanmuCacheMetaParser.parse(escaped, tail)

        requireNotNull(resource)
        assertEquals("带\"引号\"的剧", resource.title)
        assertEquals("a\\b.xml", resource.filename)
    }

    @Test
    fun `缺少资源键或没有 comments 标记时返回 null`() {
        assertNull(LocalDanmuCacheMetaParser.parse("""{"title":"x","comments":[]}""", tail))
        assertNull(LocalDanmuCacheMetaParser.parse("""{"resourceKey":"a|2024|tv|1"}""", tail))
    }

    @Test
    fun `尾部没有 updatedAt 时返回 null`() {
        assertNull(LocalDanmuCacheMetaParser.parseUpdatedAt("""…]}"""))
    }
}
