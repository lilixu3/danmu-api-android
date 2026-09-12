package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.domain.model.LocalDanmuType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalDanmuResourceKeyTest {

    @Test
    fun `builds first season key without season suffix`() {
        assertEquals(
            "逐玉|2026|tv|5",
            LocalDanmuResourceKey.buildResourceKey(
                title = "逐玉",
                year = 2026,
                type = "TV",
                season = 1,
                episode = 5
            )
        )
        assertEquals(
            "逐玉|2026|tv|5",
            LocalDanmuResourceKey.buildResourceKey(
                title = "逐玉",
                year = 2026,
                type = "tv",
                season = null,
                episode = 5
            )
        )
    }

    @Test
    fun `isolates later seasons`() {
        assertEquals(
            "逐玉|2026|tv|s2|5",
            LocalDanmuResourceKey.buildResourceKey(
                title = "逐玉",
                year = 2026,
                type = "tv",
                season = 2,
                episode = 5
            )
        )
    }

    @Test
    fun `movie key uses all when episode is absent`() {
        assertEquals(
            "movie title|2024|movie|all",
            LocalDanmuResourceKey.buildResourceKey(
                title = "Movie Title",
                year = 2024,
                type = "movie",
                season = 1,
                episode = null
            )
        )
    }

    @Test
    fun `normalizes invalid key characters and case`() {
        assertEquals(
            "a b c d ",
            LocalDanmuResourceKey.normalizeKey("A/B:C*D?")
        )
        assertEquals(
            "mr",
            LocalDanmuResourceKey.normalizeKey("Mr. Robot")
        )
        assertEquals(
            "show name",
            LocalDanmuResourceKey.normalizeKey("Show Name.xml")
        )
    }

    @Test
    fun `normalizes season and episode formats`() {
        assertEquals(2, LocalDanmuResourceKey.normalizeSeason("S02"))
        assertEquals(2, LocalDanmuResourceKey.normalizeSeason("第2季"))
        assertEquals(1, LocalDanmuResourceKey.normalizeSeason(""))
        assertNull(LocalDanmuResourceKey.normalizeSeason("abc"))
        assertEquals(5, LocalDanmuResourceKey.normalizeEpisode("EP05"))
        assertEquals(5, LocalDanmuResourceKey.normalizeEpisode("第5集"))
        assertNull(LocalDanmuResourceKey.normalizeEpisode("abc"))
    }

    @Test
    fun `validates upload fields like core`() {
        assertEquals(
            "标题为必填项",
            LocalDanmuResourceKey.validateUpload(
                title = " ",
                year = 2026,
                type = LocalDanmuType.Tv,
                season = 1,
                episode = 1,
                currentYear = 2026
            )
        )
        assertEquals(
            "年份必须在 1900–2026 年之间",
            LocalDanmuResourceKey.validateUpload(
                title = "逐玉",
                year = 1800,
                type = LocalDanmuType.Tv,
                season = 1,
                episode = 1,
                currentYear = 2026
            )
        )
        assertNull(
            LocalDanmuResourceKey.validateUpload(
                title = "逐玉",
                year = 2026,
                type = LocalDanmuType.Tv,
                season = 1,
                episode = 1,
                currentYear = 2026
            )
        )
    }
}
