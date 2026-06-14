package com.example.danmuapiapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogTagClassifierTest {

    @Test
    fun `stable core system tags classify as system`() {
        val info = LogTagClassifier.classifyCoreMessage(
            "[system] [Server] Memory cache cleared successfully"
        )

        assertEquals("system", info.category)
        assertTrue(info.tags.contains("system"))
        assertTrue(info.tags.contains("server"))
    }

    @Test
    fun `merge diagnostic tags collapse to merge category`() {
        val info = LogTagClassifier.classifyCoreMessage(
            "[Merge-Check] [Map-Build] 季度地图构建详情"
        )

        assertEquals("merge", info.category)
        assertTrue(info.tags.contains("merge"))
    }

    @Test
    fun `known source aliases normalize to stable filter tag`() {
        val info = LogTagClassifier.classifyCoreMessage(
            "[Bilibili-Proxy] 请求弹幕成功"
        )

        assertEquals("bilibili", info.category)
        assertTrue(info.tags.contains("bilibili"))
    }

    @Test
    fun `timestamp-only tags fall back to system`() {
        val info = LogTagClassifier.classifyCoreMessage(
            "[2026-06-07T12:00:00+08:00] only timestamp continuation"
        )

        assertEquals("system", info.category)
    }

    @Test
    fun `sorts tags with core web ui order`() {
        val sorted = LogTagClassifier.sortTags(listOf("youku", "system", "merge", "bilibili"))

        assertEquals(listOf("system", "merge", "bilibili", "youku"), sorted)
    }

    @Test
    fun `app entries do not synthesize a source tag`() {
        val info = LogTagClassifier.classifyAppEntry(AppLogSource.App, "Runtime")

        assertEquals("", info.category)
        assertEquals(emptyList<String>(), info.tags)
        assertEquals("", LogTagClassifier.sourceFilterFor(LogEntry(source = AppLogSource.App)))
    }

    @Test
    fun `source filter uses primary category only`() {
        val entry = LogEntry(
            source = AppLogSource.Core,
            category = "system",
            tags = listOf("system", "server")
        )

        assertTrue(LogTagClassifier.matchesSource(entry, "system"))
        assertEquals("system", LogTagClassifier.sourceFilterFor(entry))
    }

    @Test
    fun `source filter falls back to leading core tag in message`() {
        val entry = LogEntry(
            source = AppLogSource.Core,
            message = "[system] [Server] request path: /api/logs"
        )

        assertEquals("system", LogTagClassifier.sourceFilterFor(entry))
        assertTrue(LogTagClassifier.matchesSource(entry, "system"))
    }

    @Test
    fun `source filter reads leading core tag from bootstrap-collected logs`() {
        val entry = LogEntry(
            source = AppLogSource.NormalBootstrap,
            message = "[Utils] [Danmu] 去重分钟数: 1"
        )

        assertEquals("utils", LogTagClassifier.sourceFilterFor(entry))
        assertTrue(LogTagClassifier.matchesSource(entry, "utils"))
    }
}
