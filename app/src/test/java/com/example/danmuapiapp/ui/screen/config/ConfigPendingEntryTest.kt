package com.example.danmuapiapp.ui.screen.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigPendingEntryTest {
    @Test fun `保存暂存组保持拖动顺序且不会重复追加已确认的组`() {
        val staging = listOf("animeko", "dandan")
        val completed = appendStagedSourceGroup("imgo", staging)
        assertEquals("imgo,animeko&dandan", completed)
        assertEquals(completed, appendStagedSourceGroup(completed, staging))
        assertEquals(completed, appendStagedSourceGroup(completed, emptyList()))
        assertEquals("dandan", appendStagedSourceGroup("", listOf("dandan")))
    }

    @Test fun `合并阻断和百分比偏移沿用确认添加格式`() {
        val merge = buildCustomMergeRuleFromEntities("merge", "甲＠animeko", "乙@dandan", "E01>E02")
        assertEquals("旧规则; 甲@animeko -> 乙@dandan | E01>E02", appendSegment("旧规则", merge, "; "))
        assertEquals("甲@animeko × 乙@dandan", buildCustomMergeRuleFromEntities("block", "甲@animeko", "乙@dandan", ""))
        assertEquals("甲/S01/E02@qq%:-1.5", buildTimelineRule("甲", "1", "2", "qq", "-1.5", true))
        assertEquals("", buildCustomMergeRuleFromEntities("merge", "@qq", "乙@dandan", ""))
        assertEquals("", buildTimelineRule("甲", "", "", "all", "", false))
        assertEquals("", buildTimelineRule("甲", "", "", "all", "NaN", false))
    }

    @Test fun `保存批量颜色保留已写入的十进制数且整批有效才提交`() {
        val batch = requireNotNull(parseColorBatch("#fff, #0000ff"))
        assertEquals("255,123456,16777215,255", appendColorsToRaw("255,123456", batch))
        assertEquals("default", appendColorsToRaw("default", emptyList()))
        assertNull(parseColorBatch("#ff0000, invalid"))
    }
}
