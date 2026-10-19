package com.example.danmuapiapp.ui.screen.push

import com.example.danmuapiapp.data.util.resolvePushTargetAddress
import org.junit.Assert.*
import org.junit.Test

class PushTargetAddressTest {
    @Test
    fun `IP 输入补全默认端口且保留自定义端口`() {
        assertEquals(defaultPushTarget("192.168.1.8"), resolvePushTargetAddress(" 192.168.1.8 "))
        assertEquals("http://192.168.1.8:1234/action?do=refresh&type=danmaku&path=", resolvePushTargetAddress("192.168.1.8:1234"))
        assertEquals("http://[::1]:9978/action?do=refresh&type=danmaku&path=", resolvePushTargetAddress("[::1]"))
    }

    @Test
    fun `完整地址保留 HTTPS 端口及转义参数`() {
        val full = "https://tv.example:8443/action?token=A%2FB&path=&type=danmaku"
        assertEquals(full, resolvePushTargetAddress(full))
        assertEquals("https://tv.example/action?do=refresh&type=danmaku&path=", resolvePushTargetAddress("HTTPS://tv.example"))
    }

    @Test
    fun `无效协议端口空白和片段不会应用或保存`() {
        listOf("", "abc def", "http://", "ftp://tv.example", "http://tv.example:0", "http://tv.example:65536", "http://tv.example:", "http://tv.example:abc", "http://tv.example/#path=", "http://tv.example/\npath=").forEach {
            assertNull(it, resolvePushTargetAddress(it))
        }
    }

    @Test
    fun `HTTP HTTPS 和不同路径不能被误判为相同目标`() {
        assertFalse(samePushTarget("http://tv.example:9978/action", "https://tv.example:9978/action"))
        assertFalse(samePushTarget("http://tv.example/A", "http://tv.example/a"))
        assertFalse(samePushTarget("http://tv.example/a%2Fb", "http://tv.example/a/b"))
        assertFalse(samePushTarget("http://tv.example/action/", "http://tv.example/action"))
        assertTrue(samePushTarget("http://TV.EXAMPLE:80/action", "http://tv.example/action"))
        assertTrue(samePushTarget("tv.example:9978", "http://tv.example:9978/action?do=refresh&type=danmaku&path="))
    }
}
