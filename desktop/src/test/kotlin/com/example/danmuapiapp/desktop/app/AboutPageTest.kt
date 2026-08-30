package com.example.danmuapiapp.desktop.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutPageTest {
    @Test
    fun desktopUsageGuidanceDoesNotPinAndroidReleaseVersion() {
        val usage = listOf(
            "进入“核心”页面，选择 GitHub 直连或加速线路，手动下载需要的核心版本。",
            "复制完整的本机或局域网 API 地址给播放器；地址已包含访问凭证，不要单独传播 Token。",
            "在“设置”中修改端口、监听地址、工作目录、开机自启和 GitHub 下载线路。",
            "核心更新、重装、删除、回退、提交历史、PR 和文件变更都从“核心”页面进入。",
        ).joinToString("\n")
        assertTrue(usage.contains("完整的本机或局域网 API 地址"))
        assertTrue(usage.contains("工作目录"))
        assertFalse(usage.contains("Android 对应版本"))
    }
}
