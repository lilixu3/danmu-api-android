package com.example.danmuapiapp.ui.component

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreUpdateAvailablePolicyTest {

    @Test
    fun `真实版本更新应提供详情与立即更新操作`() {
        assertTrue(
            shouldOfferCoreUpdateActions(
                hasVersionUpdate = true,
                hasCheckError = false,
                sourceMismatch = false,
                sourceUnknownLegacy = false
            )
        )
    }

    @Test
    fun `检查失败或来源异常不应进入版本更新操作弹窗`() {
        assertFalse(
            shouldOfferCoreUpdateActions(
                hasVersionUpdate = true,
                hasCheckError = true,
                sourceMismatch = false,
                sourceUnknownLegacy = false
            )
        )
        assertFalse(
            shouldOfferCoreUpdateActions(
                hasVersionUpdate = true,
                hasCheckError = false,
                sourceMismatch = true,
                sourceUnknownLegacy = false
            )
        )
        assertFalse(
            shouldOfferCoreUpdateActions(
                hasVersionUpdate = true,
                hasCheckError = false,
                sourceMismatch = false,
                sourceUnknownLegacy = true
            )
        )
    }
}
