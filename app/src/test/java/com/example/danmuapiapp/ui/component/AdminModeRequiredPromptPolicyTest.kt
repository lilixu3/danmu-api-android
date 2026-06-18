package com.example.danmuapiapp.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminModeRequiredPromptPolicyTest {

    @Test
    fun `config item prompt should offer admin password entry`() {
        val prompt = adminModeRequiredPrompt(
            target = AdminModeRequiredTarget.ConfigItem("AI_API_KEY"),
            hasAdminTokenConfigured = true
        )

        assertEquals("需要管理员模式", prompt.title)
        assertTrue(prompt.message.contains("AI_API_KEY"))
        assertTrue(prompt.message.contains("配置项"))
        assertEquals("输入管理员密码", prompt.confirmText)
    }

    @Test
    fun `clear cache prompt should mention admin setup when token is missing`() {
        val prompt = adminModeRequiredPrompt(
            target = AdminModeRequiredTarget.ClearCache,
            hasAdminTokenConfigured = false
        )

        assertTrue(prompt.message.contains("清理缓存"))
        assertTrue(prompt.message.contains("配置"))
        assertEquals("配置管理员密码", prompt.confirmText)
    }
}
