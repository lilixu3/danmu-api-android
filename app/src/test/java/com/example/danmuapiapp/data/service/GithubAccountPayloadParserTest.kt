package com.example.danmuapiapp.data.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GithubAccountPayloadParserTest {

    @Test
    fun `用户响应应解析真实登录名`() {
        val login = GithubAccountPayloadParser.parseLogin(
            """{"login":"octocat","id":1,"name":"The Octocat"}"""
        )

        assertEquals("octocat", login)
    }

    @Test
    fun `额度响应应只读取 core 资源`() {
        val rateLimit = GithubAccountPayloadParser.parseCoreRateLimit(
            """
                {
                  "resources": {
                    "core": {"limit": 5000, "remaining": 4899, "reset": 1735689600},
                    "search": {"limit": 30, "remaining": 29, "reset": 1735689500}
                  },
                  "rate": {"limit": 5000, "remaining": 4899, "reset": 1735689600}
                }
            """.trimIndent()
        )

        assertEquals(5000, rateLimit?.limit)
        assertEquals(4899, rateLimit?.remaining)
        assertEquals(1735689600L, rateLimit?.resetEpochSeconds)
    }

    @Test
    fun `畸形或不完整响应不应产生误导状态`() {
        assertNull(GithubAccountPayloadParser.parseLogin("not-json"))
        assertNull(
            GithubAccountPayloadParser.parseCoreRateLimit(
                """{"resources":{"core":{"limit":60,"remaining":59}}}"""
            )
        )
        assertNull(
            GithubAccountPayloadParser.parseCoreRateLimit(
                """{"resources":{"core":{"limit":60,"remaining":-1,"reset":1735689600}}}"""
            )
        )
    }

    @Test
    fun `错误响应应提取 GitHub 消息`() {
        assertEquals(
            "Bad credentials",
            GithubAccountPayloadParser.parseErrorMessage(
                """{"message":"Bad credentials","documentation_url":"https://docs.github.com/rest"}"""
            )
        )
    }
}
