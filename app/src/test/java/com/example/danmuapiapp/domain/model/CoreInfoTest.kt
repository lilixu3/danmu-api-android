package com.example.danmuapiapp.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreInfoTest {

    @Test
    fun `已安装核心即使来源不一致也应允许启动`() {
        val info = CoreInfo(
            variant = ApiVariant.Custom,
            version = "1.0.0",
            isInstalled = true,
            sourceMismatch = true,
            sourceStatus = CoreSourceStatus.Mismatched
        )

        assertTrue(info.isReady)
        assertTrue(info.needsAttention)
    }

    @Test
    fun `旧版自定义核心缺少来源标记时不阻断启动但需要提示刷新`() {
        val info = CoreInfo(
            variant = ApiVariant.Custom,
            version = "1.0.0",
            isInstalled = true,
            sourceStatus = CoreSourceStatus.UnknownLegacy
        )

        assertTrue(info.isReady)
        assertTrue(info.needsAttention)
    }

    @Test
    fun `未安装核心不可启动`() {
        val info = CoreInfo(
            variant = ApiVariant.Stable,
            version = null,
            isInstalled = false
        )

        assertFalse(info.isReady)
    }
}
