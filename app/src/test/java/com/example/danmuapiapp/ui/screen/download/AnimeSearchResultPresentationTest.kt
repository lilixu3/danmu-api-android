package com.example.danmuapiapp.ui.screen.download

import org.junit.Assert.assertEquals
import org.junit.Test

class AnimeSearchResultPresentationTest {
    @Test
    fun `搜索结果应从标题中拆出来源并保留完整标题`() {
        val presentation = buildAnimeSearchResultPresentation(
            DownloadAnimeCandidate(
                animeId = 1015038832L,
                title = "夏目友人帐 第2季(2009)【动漫】 from 腾讯视频",
                episodeCount = 13
            )
        )

        assertEquals("夏目友人帐 第2季(2009)【动漫】", presentation.title)
        assertEquals("腾讯视频", presentation.source)
        assertEquals("13 集", presentation.episodeCountText)
        assertEquals("ID：1015038832", presentation.idText)
    }

    @Test
    fun `没有来源的搜索结果应显示来源未知`() {
        val presentation = buildAnimeSearchResultPresentation(
            DownloadAnimeCandidate(
                animeId = 1618609812L,
                title = "剧场版 夏目友人帐 ~ 缘结空蝉 ~ (2018)",
                episodeCount = 1
            )
        )

        assertEquals("剧场版 夏目友人帐 ~ 缘结空蝉 ~ (2018)", presentation.title)
        assertEquals("来源未知", presentation.source)
        assertEquals("1 集", presentation.episodeCountText)
    }
}
