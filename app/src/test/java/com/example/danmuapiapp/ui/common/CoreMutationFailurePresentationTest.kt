package com.example.danmuapiapp.ui.common

import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreRuntimeDependencyUnavailableException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreMutationFailurePresentationTest {

    @Test
    fun `依赖缺失应生成阻断弹窗而不是普通提示`() {
        val error = CoreRuntimeDependencyUnavailableException(
            missingDependencies = listOf("ws", "brotli", "ws", " "),
            runtimePackUnavailableReason = "开发版索引中没有匹配依赖包"
        )

        val presentation = resolveCoreMutationFailure(
            error = error,
            variant = ApiVariant.Dev,
            variantLabel = "开发版",
            actionLabel = "更新",
            failurePrefix = "更新失败"
        )

        assertNull(presentation.fallbackMessage)
        val prompt = requireNotNull(presentation.dependencyBlockedPrompt)
        assertEquals("开发版更新已取消", prompt.title)
        assertEquals(listOf("brotli", "ws"), prompt.missingDependencies)
        assertEquals("开发版索引中没有匹配依赖包", prompt.unavailableReason)
        assertTrue(prompt.guidance.contains("对应通道"))
    }

    @Test
    fun `被包装的依赖阻断错误也应生成弹窗`() {
        val dependencyError = CoreRuntimeDependencyUnavailableException(
            missingDependencies = listOf("sharp"),
            runtimePackUnavailableReason = "签名校验失败"
        )
        val wrapped = IllegalStateException("外层错误", dependencyError)

        val presentation = resolveCoreMutationFailure(
            error = wrapped,
            variant = ApiVariant.Stable,
            variantLabel = "稳定版",
            actionLabel = "重装",
            failurePrefix = "重装失败"
        )

        assertNull(presentation.fallbackMessage)
        assertEquals(
            listOf("sharp"),
            requireNotNull(presentation.dependencyBlockedPrompt).missingDependencies
        )
    }

    @Test
    fun `自定义核心依赖阻断应说明不查询官方依赖仓库`() {
        val presentation = resolveCoreMutationFailure(
            error = CoreRuntimeDependencyUnavailableException(
                missingDependencies = listOf("canvas"),
                runtimePackUnavailableReason = "自定义核心不使用稳定版或开发版依赖仓库"
            ),
            variant = ApiVariant.Custom,
            variantLabel = "自定义版",
            actionLabel = "安装",
            failurePrefix = "安装失败"
        )

        val prompt = requireNotNull(presentation.dependencyBlockedPrompt)
        assertTrue(prompt.guidance.contains("自定义核心不会查询"))
        assertTrue(prompt.guidance.contains("签名依赖仓库"))
    }

    @Test
    fun `普通网络错误应保留原提示且不生成依赖弹窗`() {
        val presentation = resolveCoreMutationFailure(
            error = IOException("连接超时"),
            variant = ApiVariant.Stable,
            variantLabel = "稳定版",
            actionLabel = "更新",
            failurePrefix = "更新失败"
        )

        assertEquals("更新失败：连接超时", presentation.fallbackMessage)
        assertNull(presentation.dependencyBlockedPrompt)
    }

    @Test
    fun `专用异常消息应明确替换前取消并保留现有核心`() {
        val error = CoreRuntimeDependencyUnavailableException(
            missingDependencies = listOf("ws"),
            runtimePackUnavailableReason = null
        )

        assertTrue(error.message.orEmpty().contains("替换前取消"))
        assertTrue(error.message.orEmpty().contains("正式核心未被替换"))
        assertTrue(error.message.orEmpty().contains("原有版本（如有）保持不变"))
    }
}
