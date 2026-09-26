package com.example.danmuapiapp.ui.screen.config

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigDandanplayVerifyTest {

    @Test
    fun `successful verify keeps core message`() {
        val result = parseDandanplayVerifyResponse(
            JSONObject("""{"success":true,"ok":true,"message":"弹弹play账号连通性测试成功"}""")
        )

        assertTrue(result.isReachable)
        assertEquals("弹弹play账号连通性测试成功", result.message)
    }

    @Test
    fun `failed verify is surfaced as unreachable with core message`() {
        val result = parseDandanplayVerifyResponse(
            JSONObject("""{"success":false,"ok":false,"message":"账号或密码错误"}""")
        )

        assertFalse(result.isReachable)
        assertEquals("账号或密码错误", result.message)
    }

    @Test
    fun `missing message falls back by reachability`() {
        val ok = parseDandanplayVerifyResponse(JSONObject("""{"ok":true}"""))
        val failed = parseDandanplayVerifyResponse(JSONObject("""{"ok":false}"""))

        assertTrue(ok.isReachable)
        assertTrue(ok.message.isNotBlank())
        assertFalse(failed.isReachable)
        assertTrue(failed.message.isNotBlank())
    }

    @Test
    fun `legacy success field and data wrapper are accepted`() {
        val wrapped = parseDandanplayVerifyResponse(
            JSONObject("""{"data":{"ok":true,"message":"中转服务可用"}}""")
        )
        val legacy = parseDandanplayVerifyResponse(JSONObject("""{"success":true}"""))

        assertTrue(wrapped.isReachable)
        assertEquals("中转服务可用", wrapped.message)
        assertTrue(legacy.isReachable)
    }
}
