package com.example.danmuapiapp.ui.screen.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PushTargetPresentationTest {

    @Test
    fun `保存默认不会凭空产生快速切换地址`() {
        assertTrue(buildPushTargetChoices(emptyList()).isEmpty())
    }

    @Test
    fun `快速切换只包含推送成功的地址`() {
        val recent = listOf(
            "http://192.168.1.9:9978/action?do=refresh&type=danmaku&path=",
            "http://192.168.1.10:9978/action?do=refresh&type=danmaku&path="
        )

        val choices = buildPushTargetChoices(recent)

        assertEquals(recent, choices.map { it.address })
        assertTrue(choices.all { it.origin == TargetChoiceOrigin.Recent })
    }

    @Test
    fun `当前地址在历史中保留一条以便选中和移除`() {
        val current = "http://192.168.1.9:9978/action?do=refresh&type=danmaku&path="

        assertEquals(listOf(current), buildPushTargetChoices(listOf(current, current)).map { it.address })
    }

    @Test
    fun `快速切换最多显示三个地址并按最近推送排序`() {
        val recent = (1..4).map { "http://192.168.1.$it:9978/action?do=refresh&type=danmaku&path=" }

        assertEquals(recent.take(3), buildPushTargetChoices(recent).map { it.address })
    }

    @Test
    fun `当前地址能区分配置默认和最近使用`() {
        val saved = "http://192.168.1.8:9978/action?do=refresh&type=danmaku&path="
        val other = "http://192.168.1.9:9978/action?do=refresh&type=danmaku&path="

        val configured = resolvePushTargetSelection(saved, saved, saved, listOf(other))
        val recent = resolvePushTargetSelection(other, saved, saved, listOf(other))

        assertEquals(TargetChoiceOrigin.ConfiguredDefault, configured.origin)
        assertEquals("配置默认", currentTargetLabel(configured))
        assertEquals(TargetChoiceOrigin.Recent, recent.origin)
        assertEquals("最近使用", currentTargetLabel(recent))
    }
}
