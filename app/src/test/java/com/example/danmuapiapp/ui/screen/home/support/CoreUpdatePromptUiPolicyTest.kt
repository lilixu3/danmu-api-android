package com.example.danmuapiapp.ui.screen.home.support

import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CoreUpdatePromptUiPolicyTest {

    @Test
    fun `同版本更新进行中时不应再次自动弹出更新提示`() {
        val result = resolveAutoCoreUpdatePrompt(
            info = coreInfo(hasVersionUpdate = true, availableVersion = "2.0.0"),
            ignoredVersion = null,
            suppressedVersion = "2.0.0",
            samePromptShown = false
        )

        assertNull(result)
    }

    @Test
    fun `存在新版本且未忽略未抑制时应返回自动提示内容`() {
        val result = resolveAutoCoreUpdatePrompt(
            info = coreInfo(hasVersionUpdate = true, availableVersion = "2.0.0"),
            ignoredVersion = null,
            suppressedVersion = null,
            samePromptShown = false
        )

        assertNotNull(result)
        assertEquals("1.0.0", result?.currentVersion)
        assertEquals("2.0.0", result?.latestVersion)
    }

    @Test
    fun `来源不一致不应进入自动版本更新提示`() {
        val result = resolveAutoCoreUpdatePrompt(
            info = coreInfo(sourceMismatch = true),
            ignoredVersion = null,
            suppressedVersion = null,
            samePromptShown = false
        )

        assertNull(result)
    }

    @Test
    fun `更新进行中时按钮文案应优先显示更新中`() {
        val text = resolveCoreActionButtonText(
            isCoreInfoLoading = false,
            isCoreInstalled = true,
            hasVersionUpdate = true,
            sourceMismatch = false,
            isInstalling = false,
            isUpdating = true
        )

        assertEquals("更新中...", text)
    }

    @Test
    fun `来源不一致时按钮文案应提示重新下载`() {
        val text = resolveCoreActionButtonText(
            isCoreInfoLoading = false,
            isCoreInstalled = true,
            hasVersionUpdate = false,
            sourceMismatch = true,
            isInstalling = false,
            isUpdating = false
        )

        assertEquals("重新下载", text)
    }

    @Test
    fun `存在可用更新时按钮文案应提示先查看而非直接更新`() {
        val text = resolveCoreActionButtonText(
            isCoreInfoLoading = false,
            isCoreInstalled = true,
            hasVersionUpdate = true,
            sourceMismatch = false,
            isInstalling = false,
            isUpdating = false
        )

        assertEquals("查看更新", text)
    }

    private fun coreInfo(
        hasVersionUpdate: Boolean = false,
        sourceMismatch: Boolean = false,
        availableVersion: String? = null
    ): CoreInfo {
        return CoreInfo(
            variant = ApiVariant.Stable,
            version = "1.0.0",
            isInstalled = true,
            hasVersionUpdate = hasVersionUpdate,
            sourceMismatch = sourceMismatch,
            availableVersion = availableVersion
        )
    }
}
