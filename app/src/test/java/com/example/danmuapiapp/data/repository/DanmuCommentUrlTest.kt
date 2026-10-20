package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class DanmuCommentUrlTest {
    @Test
    fun `普通剧集仍按 episodeId 请求弹幕`() {
        val url = buildDanmuCommentUrl(
            apiBaseUrl = "http://127.0.0.1:8080",
            format = DanmuDownloadFormat.Xml,
            episodeId = 2334455L,
            directUrl = ""
        )

        assertEquals("http://127.0.0.1:8080/api/v2/comment/2334455?format=xml", url)
    }

    @Test
    fun `链接解析剧集改走 url 参数并保留原始链接`() {
        val url = buildDanmuCommentUrl(
            apiBaseUrl = "http://127.0.0.1:8080/",
            format = DanmuDownloadFormat.Json,
            episodeId = -123L,
            directUrl = "https://www.bilibili.com/video/BV11xX5BjECL?p=2"
        )

        assertEquals(
            "http://127.0.0.1:8080/api/v2/comment?url=" +
                "https%3A%2F%2Fwww.bilibili.com%2Fvideo%2FBV11xX5BjECL%3Fp%3D2&format=json",
            url
        )
    }

    @Test
    fun `空链接不会误判为直达剧集`() {
        val url = buildDanmuCommentUrl(
            apiBaseUrl = "http://127.0.0.1:8080",
            format = DanmuDownloadFormat.Xml,
            episodeId = 7L,
            directUrl = "   "
        )

        assertEquals("http://127.0.0.1:8080/api/v2/comment/7?format=xml", url)
    }
}
