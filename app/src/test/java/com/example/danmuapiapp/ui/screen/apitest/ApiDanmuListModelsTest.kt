package com.example.danmuapiapp.ui.screen.apitest

import org.junit.Assert.*
import org.junit.Test

class ApiDanmuListModelsTest {
    private fun comment(id: Long, mode: DanmuCommentFilter, source: String, text: String = "弹幕", time: Double = id.toDouble()) =
        DanmuCommentItem(id, time, when (mode) { DanmuCommentFilter.Top -> 5; DanmuCommentFilter.Bottom -> 4; else -> 1 },
            mode, text, 0xffffffL, "#FFFFFF", sourceLabel = source)

    private val index = buildDanmuListIndex(listOf(
        comment(1, DanmuCommentFilter.Scroll, "tencent", "你好"),
        comment(2, DanmuCommentFilter.Scroll, "aiyifan", "Hello"),
        comment(3, DanmuCommentFilter.Top, "tencent", "Hello world"),
        comment(4, DanmuCommentFilter.Bottom, "tencent&aiyifan", "你好世界")
    ), "")

    @Test fun `four mode totals count merged comments only once`() {
        val result = filterDanmuList(index)
        assertEquals(mapOf(DanmuCommentFilter.All to 4, DanmuCommentFilter.Scroll to 2,
            DanmuCommentFilter.Top to 1, DanmuCommentFilter.Bottom to 1), result.modeCounts)
        assertEquals(mapOf("tencent" to 3, "aiyifan" to 2), result.sourceCounts.toMap())
    }

    @Test fun `choosing source updates mode totals and selecting mode narrows list`() {
        val result = filterDanmuList(index, mode = DanmuCommentFilter.Scroll, sources = setOf("tencent"))
        assertEquals(listOf(1L), result.comments.map { it.uniqueId })
        assertEquals(3, result.modeCounts[DanmuCommentFilter.All])
        assertEquals(1, result.modeCounts[DanmuCommentFilter.Scroll])
        assertEquals(mapOf("tencent" to 1, "aiyifan" to 1), result.sourceCounts.toMap())
    }

    @Test fun `multiple sources use union without duplicate merged comments`() {
        assertEquals(4, filterDanmuList(index, sources = setOf("tencent", "aiyifan")).comments.size)
    }

    @Test fun `search combines with modes and sources case insensitively`() {
        val result = filterDanmuList(index, query = "  HELLO  ", sources = setOf("tencent"))
        assertEquals(listOf(3L), result.comments.map { it.uniqueId })
        assertEquals(1, result.modeCounts[DanmuCommentFilter.All])
        assertEquals(0, result.modeCounts[DanmuCommentFilter.Bottom])
    }

    @Test fun `zero types remain available with zero counts`() {
        val result = filterDanmuList(buildDanmuListIndex(listOf(comment(1, DanmuCommentFilter.Scroll, "tencent")), ""))
        assertEquals(4, result.modeCounts.size)
        assertEquals(0, result.modeCounts[DanmuCommentFilter.Top])
        assertEquals(0, result.modeCounts[DanmuCommentFilter.Bottom])
    }

    @Test fun `time range avoids overlapping endpoint except final bucket`() {
        val bucket = DanmuHeatBucket(0, 1.0, 3.0, 0)
        assertEquals(listOf(1L, 2L), filterDanmuList(index, bucket = bucket).comments.map { it.uniqueId })
        assertEquals(listOf(1L, 2L, 3L), filterDanmuList(index, bucket = bucket, includeRangeEnd = true).comments.map { it.uniqueId })
    }

    @Test fun `source aliases and duplicate labels share one total`() {
        val aliases = buildDanmuListIndex(listOf(comment(1, DanmuCommentFilter.Scroll, "qq＆tencent")), "")
        assertEquals(listOf("tencent" to 1), filterDanmuList(aliases).sourceCounts)
    }
}
