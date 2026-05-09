package com.example.danmuapiapp.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class CoreRemoteSourceFallbackTest {

    @Test
    fun `分支 head API 不可用但 globals 有版本时应生成 archive 下载源`() {
        val plan = buildBranchRemoteFallbackPlan(
            repo = "huangxd-/danmu_api",
            branch = "refs/heads/main",
            versionLabel = "1.19.3"
        )

        assertEquals("1.19.3", plan.tagName)
        assertEquals("1.19.3", plan.name)
        assertEquals("1.19.3", plan.versionLabel)
        assertEquals(
            "https://github.com/huangxd-/danmu_api/archive/refs/heads/main.zip",
            plan.zipballUrl
        )
    }

    @Test
    fun `archive 下载源应保留分支斜杠并编码路径片段`() {
        val plan = buildBranchRemoteFallbackPlan(
            repo = "owner/repo",
            branch = "refs/heads/feature/test build",
            versionLabel = "2.0.0"
        )

        assertEquals(
            "https://github.com/owner/repo/archive/refs/heads/feature/test%20build.zip",
            plan.zipballUrl
        )
    }
}
