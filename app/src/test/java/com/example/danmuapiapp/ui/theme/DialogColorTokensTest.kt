package com.example.danmuapiapp.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test

class DialogColorTokensTest {

    @Test
    fun `弹窗关键文字与状态组合应满足可读性对比度`() {
        assertContrast(
            DialogColorTokens.DARK_TEXT_PRIMARY,
            DialogColorTokens.DARK_DIALOG
        )
        assertContrast(
            DialogColorTokens.DARK_TEXT_SECONDARY,
            DialogColorTokens.DARK_DIALOG
        )
        assertContrast(
            DialogColorTokens.DARK_ON_PRIMARY,
            DialogColorTokens.DARK_PRIMARY
        )
        assertContrast(
            DialogColorTokens.DARK_ON_PRIMARY_CONTAINER,
            DialogColorTokens.DARK_PRIMARY_CONTAINER
        )
        assertContrast(
            DialogColorTokens.LIGHT_TEXT_PRIMARY,
            DialogColorTokens.LIGHT_DIALOG
        )
        assertContrast(
            DialogColorTokens.LIGHT_TEXT_SECONDARY,
            DialogColorTokens.LIGHT_DIALOG
        )
        assertContrast(
            DialogColorTokens.LIGHT_ON_PRIMARY,
            DialogColorTokens.LIGHT_PRIMARY
        )
        assertContrast(
            DialogColorTokens.LIGHT_ON_PRIMARY_CONTAINER,
            DialogColorTokens.LIGHT_PRIMARY_CONTAINER
        )
    }

    private fun assertContrast(foreground: Int, background: Int) {
        val lighter = maxOf(luminance(foreground), luminance(background))
        val darker = minOf(luminance(foreground), luminance(background))
        val ratio = (lighter + 0.05) / (darker + 0.05)
        assertTrue("contrast was $ratio", ratio >= 4.5)
    }

    private fun luminance(color: Int): Double {
        val channels = intArrayOf(
            color shr 16 and 0xFF,
            color shr 8 and 0xFF,
            color and 0xFF
        )
        val weights = doubleArrayOf(0.2126, 0.7152, 0.0722)
        return channels.indices.sumOf { index ->
            val value = channels[index] / 255.0
            val linear = if (value <= 0.03928) {
                value / 12.92
            } else {
                Math.pow((value + 0.055) / 1.055, 2.4)
            }
            linear * weights[index]
        }
    }
}
