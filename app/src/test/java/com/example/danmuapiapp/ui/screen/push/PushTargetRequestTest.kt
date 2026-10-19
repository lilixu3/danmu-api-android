package com.example.danmuapiapp.ui.screen.push

import org.junit.Assert.*
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class PushTargetRequestTest {
    private fun request(target: String): String = PushEpisodeRequestComposer.buildPushRequest(
        sourceBase = "http://192.168.1.1:9321",
        episodeId = 12,
        input = PushEpisodeRequestComposer.PreparedInput(target, 0.0, null)
    ).pushUrl

    @Test
    fun `完整端点缺少 path 时不会重复追加 action 路径`() {
        val uri = URI(request("http://192.168.1.8:9978/action?do=refresh&type=danmaku"))
        assertEquals("/action", uri.path)
        assertTrue(uri.rawQuery.startsWith("do=refresh&type=danmaku&path="))
    }

    @Test
    fun `替换 path 保留后续鉴权参数与转义`() {
        val uri = URI(request("https://tv.example/action?do=refresh&path=old&token=A%2FB&type=danmaku"))
        assertEquals("https", uri.scheme)
        assertTrue(uri.rawQuery.endsWith("&token=A%2FB&type=danmaku"))
        val path = uri.rawQuery.split('&').single { it.startsWith("path=") }.substringAfter('=')
        assertEquals("http://192.168.1.1:9321/api/v2/comment/12?format=xml", URLDecoder.decode(path, "UTF-8"))
    }

    @Test
    fun `其他参数中出现 path 字样不会被当作 path 参数`() {
        val uri = URI(request("http://tv.example/action?do=refresh&otherpath=keep&type=danmaku"))
        assertTrue(uri.rawQuery.contains("otherpath=keep"))
        assertEquals(1, uri.rawQuery.split('&').count { it.startsWith("path=") })
    }

    @Test
    fun `主机输入补全标准推送接口且 path 只编码一次`() {
        val uri = URI(request("192.168.1.8"))
        assertEquals(9978, uri.port)
        assertEquals("/action", uri.path)
        assertTrue(uri.rawQuery.startsWith("do=refresh&type=danmaku&path=http%3A%2F%2F"))
    }

    @Test
    fun `重复 path 合并为一个新的弹幕地址`() {
        val uri = URI(request("http://tv.example/action?path=old&path=older&token=keep"))
        assertEquals(1, uri.rawQuery.split('&').count { it.startsWith("path=") })
        assertTrue(uri.rawQuery.endsWith("&token=keep"))
    }
}
