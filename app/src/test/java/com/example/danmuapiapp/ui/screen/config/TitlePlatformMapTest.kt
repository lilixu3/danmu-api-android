package com.example.danmuapiapp.ui.screen.config

import org.junit.Assert.assertEquals
import org.junit.Test

class TitlePlatformMapTest {
    @Test
    fun `批量编辑允许换行且同名剧以最后一项为准`() {
        val raw = "宝珠 -> imgo; 航海王->旧平台&dandan\r\n宝珠->qq\n未完成->; ->custom"
        val rows = parseTitlePlatformRows(raw)
        assertEquals(listOf(StableMapRow("宝珠", "qq"), StableMapRow("航海王", "旧平台&dandan")), rows)
        assertEquals("宝珠->qq;航海王->旧平台&dandan", serializeTitlePlatformRows(rows))
    }

    @Test
    fun `行编辑只保存完整项并保留剧名原文`() {
        val rows = listOf(StableMapRow(" 剧#1 ", "imgo"), StableMapRow("未选来源"), StableMapRow("", "qq"))
        assertEquals("剧#1->imgo", serializeTitlePlatformRows(rows))
        assertEquals("", serializeTitlePlatformRows(emptyList()))
    }
}
