package com.example.danmuapiapp.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DanmuXposedTextPolicyTest {

    @Test
    fun `集数解析覆盖常见文件名和播放标签`() {
        assertEquals(8, DanmuXposedTextPolicy.extractEpisodeNumber("S01E08"))
        assertEquals(12, DanmuXposedTextPolicy.extractEpisodeNumber("第十二集"))
        assertEquals(8, DanmuXposedTextPolicy.extractEpisodeNumber("[1.2GB] 08 1080p"))
        assertEquals(-1, DanmuXposedTextPolicy.extractEpisodeNumber("2024"))
    }

    @Test
    fun `标题清洗应去掉集数清晰度和来源后缀`() {
        assertEquals("凡人修仙传", DanmuXposedTextPolicy.normalizeSearchTitle("凡人修仙传 第08集 1080p"))
        assertEquals("凡人修仙传", DanmuXposedTextPolicy.normalizeDisplayTitle("凡人修仙传 from tencent"))
    }

    @Test
    fun `来源归一化和展示名保持兼容`() {
        assertEquals("tencent", DanmuXposedTextPolicy.normalizeSourceKey("qq"))
        assertEquals("iqiyi&bilibili", DanmuXposedTextPolicy.normalizeSourceKey("qiyi&bili"))
        assertEquals("腾讯/爱奇艺", DanmuXposedTextPolicy.displaySourceName("tencent&iqiyi"))
    }

    @Test
    fun `偏移和数字解析保持原有容错`() {
        assertEquals("0", DanmuXposedTextPolicy.formatOffsetSeconds(0.0))
        assertEquals("-0.5", DanmuXposedTextPolicy.formatOffsetSeconds(-0.5))
        assertEquals(0.0, DanmuXposedTextPolicy.parseNullableDouble(""), 0.0)
        assertNull(DanmuXposedTextPolicy.parseNullableDouble("bad"))
    }
}
