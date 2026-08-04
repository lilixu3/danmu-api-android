package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmuFilePreviewParserTest {

    @Test
    fun `xml preview counts all d nodes and limits preview items`() {
        val xml = """
            <?xml version="1.0" ?>
            <i>
                <d p="1.23,1,25,16777215,0,0,hash,1">第一条</d>
                <d p="2.50,5,25,255,0,0,hash,2">第二条</d>
                <d p="3.00,1,25,16777215,0,0,hash,3">第三条</d>
            </i>
        """.trimIndent()

        val preview = DanmuFilePreviewParser.parse(
            input = xml.byteInputStream(),
            format = DanmuDownloadFormat.Xml,
            fileName = "demo.xml",
            relativePath = "番剧/demo.xml",
            bytes = xml.toByteArray().size.toLong(),
            previewLimit = 2
        )

        assertEquals(3, preview.count)
        assertEquals(2, preview.items.size)
        assertTrue(preview.truncated)
        assertEquals("第一条", preview.items[0].text)
        assertEquals(1.23, preview.items[0].timeSeconds ?: -1.0, 0.001)
        assertEquals("16777215", preview.items[0].color)
    }

    @Test
    fun `json preview reads count comments and p m fields`() {
        val json = """
            {
              "count": 2,
              "comments": [
                {"p":"12.3,1,16777215,qq","m":"测试弹幕1"},
                {"p":"45.6,1,16777215,qiyi","m":"测试弹幕2"}
              ]
            }
        """.trimIndent()

        val preview = DanmuFilePreviewParser.parse(
            input = json.byteInputStream(),
            format = DanmuDownloadFormat.Json,
            fileName = "demo.json",
            relativePath = "番剧/demo.json",
            bytes = json.toByteArray().size.toLong(),
            previewLimit = 10
        )

        assertEquals(2, preview.count)
        assertFalse(preview.truncated)
        assertEquals(2, preview.items.size)
        assertEquals("测试弹幕1", preview.items[0].text)
        assertEquals(12.3, preview.items[0].timeSeconds ?: -1.0, 0.001)
        assertEquals("qq", preview.items[0].source)
    }

    @Test
    fun `json preview supports root array and content progress fields`() {
        val json = """
            [
              {"progress": 1500, "mode": 1, "color": 16777215, "content":"一秒半"}
            ]
        """.trimIndent()

        val preview = DanmuFilePreviewParser.parse(
            input = json.byteInputStream(),
            format = DanmuDownloadFormat.Json,
            fileName = "array.json",
            relativePath = "array.json",
            bytes = json.toByteArray().size.toLong(),
            previewLimit = 10
        )

        assertEquals(1, preview.count)
        assertEquals("一秒半", preview.items.single().text)
        assertEquals(1.5, preview.items.single().timeSeconds ?: -1.0, 0.001)
    }

    @Test
    fun `artplayer preview maps fixed modes`() {
        val json = """
            {"danmuku":[
              {"text":"顶部","time":2.5,"mode":1,"color":"#FFFFFF"},
              {"text":"底部","time":3.0,"mode":2,"color":"#FFFFFF"}
            ]}
        """.trimIndent()

        val preview = DanmuFilePreviewParser.parse(
            input = json.byteInputStream(),
            format = DanmuDownloadFormat.ArtplayerJson,
            fileName = "demo.artplayer.json",
            relativePath = "demo.artplayer.json",
            bytes = json.toByteArray().size.toLong(),
            previewLimit = 10
        )

        assertEquals(2, preview.count)
        assertEquals("5", preview.items[0].mode)
        assertEquals("4", preview.items[1].mode)
    }

    @Test
    fun `baha preview reads nested danmu and converts tenths to seconds`() {
        val json = """
            {"data":{"danmu":[
              {"text":"测试","time":25,"position":0,"color":"#FFFFFF"}
            ],"totalCount":1}}
        """.trimIndent()

        val preview = DanmuFilePreviewParser.parse(
            input = json.byteInputStream(),
            format = DanmuDownloadFormat.BahaJson,
            fileName = "demo.baha.json",
            relativePath = "demo.baha.json",
            bytes = json.toByteArray().size.toLong(),
            previewLimit = 10
        )

        assertEquals(1, preview.count)
        assertEquals(2.5, preview.items.single().timeSeconds ?: -1.0, 0.001)
        assertEquals("测试", preview.items.single().text)
    }

    @Test
    fun `dplayer and vod tuple previews expose text and modes`() {
        val dplayer = """{"code":0,"data":[[1.5,1,16777215,"sender","顶部"]]}"""
        val dplayerPreview = DanmuFilePreviewParser.parse(
            input = dplayer.byteInputStream(),
            format = DanmuDownloadFormat.DplayerJson,
            fileName = "demo.dplayer.json",
            relativePath = "demo.dplayer.json",
            bytes = dplayer.toByteArray().size.toLong(),
            previewLimit = 10
        )
        assertEquals("顶部", dplayerPreview.items.single().text)
        assertEquals("5", dplayerPreview.items.single().mode)

        val vod = """{"danum":1,"danmuku":[[3,"bottom","#FFFFFF","","底部"]]}"""
        val vodPreview = DanmuFilePreviewParser.parse(
            input = vod.byteInputStream(),
            format = DanmuDownloadFormat.VodJson,
            fileName = "demo.vod.json",
            relativePath = "demo.vod.json",
            bytes = vod.toByteArray().size.toLong(),
            previewLimit = 10
        )
        assertEquals("底部", vodPreview.items.single().text)
        assertEquals("4", vodPreview.items.single().mode)
    }
}
