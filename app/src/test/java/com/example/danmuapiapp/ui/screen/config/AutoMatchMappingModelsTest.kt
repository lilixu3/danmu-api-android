package com.example.danmuapiapp.ui.screen.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoMatchMappingModelsTest {

    @Test
    fun `映射规则结构化解析后可稳定序列化`() {
        val raw = "永生 S05E02~03 -> 永生 S01E58~59;航海王 S01E01 -> 航海王(1999)【动漫】 S01E62 @qiyi"

        val rows = parseAutoMatchMappingDrafts(raw)
        val serialized = serializeAutoMatchMappingDrafts(rows)

        assertEquals(2, rows.size)
        assertEquals("qiyi", rows[1].targetPlatform)
        assertTrue(validateAutoMatchMappingTable(serialized, listOf("qiyi", "qq")).valid)
    }

    @Test
    fun `范围长度和平台都会在保存前校验`() {
        val badRange = validateAutoMatchMappingTable(
            "永生 S05E02~03->永生 S01E58~60",
            listOf("qiyi")
        )
        val badPlatform = validateAutoMatchMappingTable(
            "永生 S05E02->永生 S01E58 @unknown",
            listOf("qiyi")
        )

        assertFalse(badRange.valid)
        assertTrue(badRange.message.contains("范围长度"))
        assertFalse(badPlatform.valid)
        assertTrue(badPlatform.message.contains("平台"))
    }

    @Test
    fun `文件名预览优先采用有限范围并计算目标集数`() {
        val preview = previewAutoMatchMapping(
            raw = "永生 S05E02->永生 S01E58;永生 S05E02~03->永生重制版 S02E08~09 @qiyi",
            fileName = "永生.S05E03.1080p.mkv",
            allowedPlatforms = listOf("qiyi")
        )

        assertNotNull(preview)
        assertEquals("永生 S05E03", preview?.sourceLabel)
        assertEquals("永生重制版 S02E09 @qiyi", preview?.targetLabel)
    }
}
