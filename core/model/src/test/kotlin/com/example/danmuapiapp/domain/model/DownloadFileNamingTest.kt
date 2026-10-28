package com.example.danmuapiapp.domain.model

import org.junit.Assert.*
import org.junit.Test

class DownloadFileNamingTest {
    @Test fun `clean show names follow Flutter decorations including merged sources`() {
        val cases = mapOf(
            "凡人修仙传(2020)【动漫】from bilibili" to "凡人修仙传",
            "一瓯春(2026)【电视剧】from youku" to "一瓯春",
            "[字幕组] 测试剧 [2023] from tencent&iqiyi" to "测试剧",
            "测试剧（2023）【电视剧】 @youku" to "测试剧",
            "测试剧 第2季(2023)来源：tencent" to "测试剧 第2季"
        )
        cases.forEach { (raw, expected) -> assertEquals(raw, expected, cleanDownloadAnimeTitle(raw)) }
    }
    @Test fun `standard template preserves original show title and source like Flutter`() {
        val actual = renderDownloadFileName("{animeTitle}_E{episodeNo2}_{episodeTitle}_{source}.{ext}",
            DanmuDownloadFormat.Xml, "一瓯春(2026)【电视剧】from youku", "第18集 一瓯春 18", 18, 10018, "youku")
        assertEquals("一瓯春(2026)【电视剧】from youku_E18_第18集 一瓯春 18_youku.xml", actual)
    }
    @Test fun `preview and actual saving share the same renderer and sanitization`() {
        val template = "{animeTitle}/{episodeNo3}:{episodeTitle}?_{source}.{ext}"
        val actual = renderDownloadFileName(template, DanmuDownloadFormat.BiliXml, "测试剧", "第3集", 3, 99, "bilibili1")
        val preview = renderFileNameTemplatePreview(template, DanmuDownloadFormat.BiliXml, "测试剧", "第3集", 3, 99, "bilibili1")
        assertEquals("测试剧_003_第3集__bilibili1.bili.xml", actual)
        assertEquals(actual, preview)
    }
    @Test fun `long names use the same 180 character limit as Flutter`() {
        assertEquals(180, sanitizeDownloadFileName("a".repeat(200)).length)
        assertTrue(sanitizeDownloadFileName("长".repeat(200)).toByteArray(Charsets.UTF_8).size <= 240)
        assertEquals("hello world", sanitizeDownloadFileName("  hello  world  "))
        assertEquals("hello_world", sanitizeDownloadFileName("hello\tworld"))
        assertEquals("danmu", sanitizeDownloadFileName("  "))
    }
    @Test fun `templates with no dot receive the chosen multipart extension`() {
        assertEquals("测试剧-第3集.danuni.binpb", renderDownloadFileName("{animeTitle}-第{episodeNo}集",
            DanmuDownloadFormat.DanuniBinPb, "测试剧", "第3集", 3, 99, "bilibili1"))
    }
    @Test fun `long multipart filenames and conflict numbering preserve a scannable extension`() {
        for (format in DanmuDownloadFormat.entries) {
            val filename = renderDownloadFileName("{animeTitle}_{episodeTitle}.{ext}", format,
                "长剧名".repeat(100), "第1集".repeat(100), 1, 101, "youku")
            assertTrue(filename.endsWith(".${format.extension}"))
            assertTrue(filename.length <= 180)
            assertTrue(filename.toByteArray(Charsets.UTF_8).size <= 240)
            assertEquals(format, DanmuDownloadFormat.fromFileName(filename))
            val numbered = numberedDownloadFileName(filename, 9998)
            assertTrue(numbered.endsWith("(9998).${format.extension}"))
            assertTrue(numbered.length <= 180)
            assertTrue(numbered.toByteArray(Charsets.UTF_8).size <= 240)
            assertEquals(format, DanmuDownloadFormat.fromFileName(numbered))
        }
    }
    @Test fun `title dots cannot suppress the chosen extension and emoji truncation stays valid`() {
        val name = renderDownloadFileName("{animeTitle}", DanmuDownloadFormat.Xml,
            "Show.v2", "", 1, 1, "")
        assertEquals("Show.v2.xml", name)
        val emoji = renderDownloadFileName("{animeTitle}.{ext}", DanmuDownloadFormat.BiliXml,
            "😀".repeat(150), "", 1, 1, "")
        assertEquals(emoji, emoji.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
        assertTrue(emoji.endsWith(".bili.xml"))
    }
}
