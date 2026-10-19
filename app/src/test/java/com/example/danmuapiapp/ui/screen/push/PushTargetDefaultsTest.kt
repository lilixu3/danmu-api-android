package com.example.danmuapiapp.ui.screen.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushTargetDefaultsTest {

    @Test
    fun `出厂回环默认值会替换为当前局域网地址`() {
        assertEquals(
            defaultPushTarget("192.168.1.23"),
            resolveDefaultPushTarget(
                "http://127.0.0.1:9978/action?do=refresh&type=danmaku&path=",
                "192.168.1.23"
            )
        )
    }

    @Test
    fun `自定义局域网默认值保持不变`() {
        val saved = "http://192.168.1.88:9978/action?do=refresh&type=danmaku&path="

        assertEquals(saved, resolveDefaultPushTarget(saved, "192.168.1.23"))
    }

    @Test
    fun `用户自定义的回环地址不会被工厂值判断误伤`() {
        assertFalse(isFactoryLoopbackPushTarget("http://127.0.0.1:9978/action?do=refresh&type=danmaku&path=http%3A%2F%2Fsource"))
        assertFalse(isFactoryLoopbackPushTarget("http://127.0.0.1:9979/action?do=refresh&type=danmaku&path="))
        assertTrue(isFactoryLoopbackPushTarget("http://127.0.0.1:9978/action?do=refresh&type=danmaku&path="))
    }

    @Test
    fun `未配置默认值时仍给出可用局域网目标`() {
        assertEquals(defaultPushTarget("192.168.1.23"), resolveDefaultPushTarget("", "192.168.1.23"))
        assertEquals(defaultPushTarget(""), defaultPushTarget(""))
    }

    @Test
    fun `带额外参数或 HTTPS 的回环配置必须保留`() {
        listOf(
            "https://127.0.0.1:9978/action?do=refresh&type=danmaku&path=",
            "http://127.0.0.1:9978/action?do=refresh&type=danmaku&path=&token=custom",
            "http://127.0.0.1:9978/action?do=old&do=refresh&type=danmaku&path="
        ).forEach { target ->
            assertFalse(isFactoryLoopbackPushTarget(target))
            assertEquals(target, resolveDefaultPushTarget(target, "192.168.1.2"))
        }
        assertTrue(isFactoryLoopbackPushTarget("http://[::1]:9978/action?do=refresh&type=danmaku&path="))
    }
}
