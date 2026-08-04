package com.example.danmuapiapp.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmuXposedTextPolicyTest {

    private class FakeRemoteServer {
        companion object {
            @JvmField
            var n = 9986
        }
    }

    private class InvalidRemoteServer {
        companion object {
            @JvmField
            var n = 10001
        }
    }

    private class InstancePortRemoteServer {
        @JvmField
        var n = 9987
    }

    private class RenamedPortRemoteServer {
        companion object {
            @JvmField
            var q = 9991
        }
    }

    private class AmbiguousPortRemoteServer {
        companion object {
            @JvmField
            var q = 9988

            @JvmField
            var r = 9989
        }
    }

    private class FakeFongMiProxy {
        companion object {
            @JvmStatic
            fun getPort() = 9982
        }
    }

    private class InvalidFongMiProxy {
        companion object {
            @JvmStatic
            fun getPort() = -1
        }
    }

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
        assertEquals("片名", DanmuXposedTextPolicy.normalizeSearchTitle("“片名” 第03集"))
        assertEquals(2, DanmuXposedTextPolicy.extractSeasonNumber("片名 Ⅱ"))
        assertEquals(2, DanmuXposedTextPolicy.extractSeasonNumber("片名 第二季"))
        assertEquals(2, DanmuXposedTextPolicy.extractSeasonNumber("S02E03"))
        assertEquals(-1, DanmuXposedTextPolicy.extractSeasonNumber("Cars 2"))
        assertEquals("片名", DanmuXposedTextPolicy.normalizeTitleForMatch("片名 S02E03"))
        assertTrue(DanmuXposedTextPolicy.titlesMatch("片名 Ⅱ", "片名 第二季"))
        assertTrue(DanmuXposedTextPolicy.titlesMatch("片名 S02E03", "片名 第二季 第3集"))
        assertFalse(DanmuXposedTextPolicy.titlesMatch("电影人生", "人生"))
        assertEquals("仙逆", DanmuXposedTextPolicy.normalizeTitleForMatch("仙逆(2023)【动漫】from tencent"))
    }

    @Test
    fun `NewBox播放标题应拆分剧名和当前集`() {
        val media = requireNotNull(
            DanmuXposedShellMediaReader.parseNewBoxPlaybackLabel("凡人修仙传 · 年番 第125集", 9978)
        )

        assertEquals("凡人修仙传", media.title)
        assertEquals("年番 第125集", media.episodeText)
        assertEquals(125, media.episodeNumber)
    }

    @Test
    fun `NewBox推送应读取宿主递增后的真实端口`() {
        assertTrue(DanmuXposedHostShell.isTvBoxFamilyPackage("com.newbox.mobile"))
        assertTrue(DanmuXposedHostShell.isTvBoxFamilyPackage("com.truthvision.homecare.nb.bn"))
        assertTrue(DanmuXposedHostShell.isTvBoxFamilyPackage("com.github.tvbox.osc"))
        assertFalse(DanmuXposedHostShell.isTvBoxFamilyPackage("com.fongmi.android.tv"))
        assertTrue(DanmuXposedHostShell.isFongMiFamilyPackage("com.fongmi.android.tv"))
        assertTrue(DanmuXposedHostShell.isFongMiFamilyPackage("com.fongmi.android.tw"))
        assertEquals(
            9986,
            DanmuXposedHostShell.resolveRemoteServerPort(FakeRemoteServer::class.java, 9978)
        )
        assertEquals(
            9978,
            DanmuXposedHostShell.resolveRemoteServerPort(InvalidRemoteServer::class.java, 9978)
        )
        assertEquals(
            9978,
            DanmuXposedHostShell.resolveRemoteServerPort(InstancePortRemoteServer::class.java, 9978)
        )
        assertEquals(
            9991,
            DanmuXposedHostShell.resolveRemoteServerPort(RenamedPortRemoteServer::class.java, 9978)
        )
        assertEquals(
            9978,
            DanmuXposedHostShell.resolveRemoteServerPort(AmbiguousPortRemoteServer::class.java, 9978)
        )
        assertEquals(
            9982,
            DanmuXposedHostShell.resolveFongMiProxyPort(FakeFongMiProxy::class.java, 9978)
        )
        assertEquals(
            9978,
            DanmuXposedHostShell.resolveFongMiProxyPort(InvalidFongMiProxy::class.java, 9978)
        )
    }

    @Test
    fun `搜索年份应兼容日期字段和标题后缀`() {
        assertEquals("2024", DanmuXposedTextPolicy.extractYear("2024-01-01T00:00:00.000Z"))
        assertEquals("2023", DanmuXposedTextPolicy.extractYear("", "仙逆(2023)【动漫】from tencent"))

        val anime = AnimeRef("http://127.0.0.1:9321", "1", "1", "仙逆(2023)",
            "2023", 76, "tencent", "动漫")
        val label = DanmuXposedEpisodeRepository.buildAnimeCandidateLabel(anime, 1)
        assertTrue(label.contains("仙逆 · 2023"))
    }

    @Test
    fun `数字片名不得误当发行年份删除或参与年份筛选`() {
        val yearOnlyTitle = MediaIdentity.from("1917", "", -1)
        val leadingYearTitle = MediaIdentity.from("2001: A Space Odyssey", "", -1)
        val metadataYear = MediaIdentity.from("片名 2024 第二季", "第1集", 1)

        assertEquals("1917", yearOnlyTitle.baseTitle)
        assertEquals("", yearOnlyTitle.year)
        assertEquals("2001aspaceodyssey", leadingYearTitle.baseTitle)
        assertEquals("", leadingYearTitle.year)
        assertEquals("片名", metadataYear.baseTitle)
        assertEquals("2024", metadataYear.year)
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

    @Test
    fun `综艺日期和期数应形成带后缀的稳定身份`() {
        assertEquals(
            "date:20260727:嘉宾篇",
            DanmuXposedTextPolicy.normalizeVarietyEpisode("2026-07-27 嘉宾篇")
        )
        assertEquals(
            DanmuXposedTextPolicy.normalizeVarietyEpisode("20260727 嘉宾篇"),
            DanmuXposedTextPolicy.normalizeVarietyEpisode("2026年07月27日 嘉宾篇")
        )
        assertNotEquals(
            DanmuXposedTextPolicy.normalizeVarietyEpisode("第3期 上"),
            DanmuXposedTextPolicy.normalizeVarietyEpisode("第3期 下")
        )
    }

    @Test
    fun `自动作品匹配必须核对显式年份和季度`() {
        val target = MediaIdentity.from(ShellMedia(
            9978, "“片名” (2024) Ⅱ", "第3集", 3, "u1", "line-a", 3, 0, 1000
        ))
        val exact = AnimeRef("core", "1", "1", "片名 第二季", "2024", 12, "tencent", "动漫")
        val wrongYear = AnimeRef("core", "2", "2", "片名 第二季", "2023", 12, "tencent", "动漫")
        val wrongSeason = AnimeRef("core", "3", "3", "片名 第三季", "2024", 12, "tencent", "动漫")

        assertTrue(DanmuXposedEpisodeRepository.animeMatches(target, exact))
        assertFalse(DanmuXposedEpisodeRepository.animeMatches(target, wrongYear))
        assertFalse(DanmuXposedEpisodeRepository.animeMatches(target, wrongSeason))
    }

    @Test
    fun `自动详情请求额度只统计标题年份季度均匹配的候选`() {
        val target = MediaIdentity.from("片名", "第1集", 1)
        val mismatches = (1..DanmuXposedEpisodeRepository.MAX_AUTO_DETAIL_REQUESTS).map { index ->
            AnimeRef("core", "wrong-$index", "", "其他片名$index", "", 12, "", "动漫")
        }
        val exact = AnimeRef("core", "exact", "", "片名", "", 12, "", "动漫")

        assertEquals(
            listOf(exact),
            DanmuXposedEpisodeRepository.matchingAutoDetailCandidates(mismatches + exact, target)
        )
    }

    @Test
    fun `自动剧集匹配拒绝预告和非精确兜底`() {
        val episode2 = EpisodeCandidate("片名", "第2集", 2, "tencent", "u2")
        val episode3Trailer = EpisodeCandidate("片名", "第3集 预告", 3, "tencent", "u3-preview")
        val episode3 = EpisodeCandidate("片名", "第3集", 3, "tencent", "u3")
        val target3 = MediaIdentity.from("片名", "第3集", 3)
        val target4 = MediaIdentity.from("片名", "第4集", 4)

        assertEquals(
            episode3,
            DanmuXposedEpisodeRepository.selectStrictEpisode(
                listOf(episode2, episode3Trailer, episode3), target3, false
            )
        )
        assertNull(DanmuXposedEpisodeRepository.selectStrictEpisode(listOf(episode2, episode3), target4, false))
        assertNull(DanmuXposedEpisodeRepository.selectStrictEpisode(
            listOf(episode2, episode3Trailer), target3, false
        ))
        assertNull(DanmuXposedEpisodeRepository.selectStrictEpisode(
            listOf(episode2, episode3), MediaIdentity.from("片名", "", -1), false
        ))
    }

    @Test
    fun `综艺自动匹配必须同时核对日期与后缀`() {
        val wrong = EpisodeCandidate("综艺", "2026-07-27 访谈篇", -1, "", "wrong")
        val exact = EpisodeCandidate("综艺", "2026-07-27 嘉宾篇", -1, "", "exact")
        val target = MediaIdentity.from("综艺", "20260727 嘉宾篇", -1)

        assertEquals(
            exact,
            DanmuXposedEpisodeRepository.selectStrictEpisode(listOf(wrong, exact), target, false)
        )
    }

    @Test
    fun `播放触发身份应覆盖地址和线路但忽略状态与进度`() {
        val base = ShellMedia(9978, "片名 (2024) Ⅱ", "第3集", 3, "u1", "line-a", 3, 100, 1000)
        val progressed = ShellMedia(9978, base.title, base.episodeText, 3, "u1", "line-a", 3, 500, 1000)
        val newUrl = ShellMedia(9978, base.title, base.episodeText, 3, "u2", "line-a", 3, 100, 1000)
        val newLine = ShellMedia(9978, base.title, base.episodeText, 3, "u1", "line-b", 3, 100, 1000)
        val paused = ShellMedia(9978, base.title, base.episodeText, 3, "u1", "line-a", 1, 100, 1000)

        assertEquals(base.signature(), progressed.signature())
        assertEquals(base.matchSignature(), newUrl.matchSignature())
        assertNotEquals(base.signature(), newUrl.signature())
        assertNotEquals(base.signature(), newLine.signature())
        assertEquals(base.signature(), paused.signature())
    }

    @Test
    fun `播放就绪只接受播放态或未知状态下实际前进的进度`() {
        val gate = PlaybackReadinessGate()
        val unknown = ShellMedia(9978, "片名", "第1集", 1, "u1", "", -1, -1, -1)

        assertFalse(gate.isReady(unknown))
        assertFalse(gate.isReady(unknown))
        assertFalse(gate.isReady(unknown))
        assertFalse(gate.isReady(unknown))
        assertFalse(gate.isReady(ShellMedia(9978, "片名", "第1集", 1, "u2", "", 1, 0, 1000)))
        assertFalse(gate.isReady(ShellMedia(9978, "片名", "第1集", 1, "u2", "", 2, 0, 1000)))
        assertTrue(gate.isReady(ShellMedia(9978, "片名", "第1集", 1, "u2", "", 3, 0, 1000)))

        gate.reset()
        assertFalse(gate.isReady(ShellMedia(9978, "片名", "第1集", 1, "u3", "", -1, 100, 1000)))
        assertTrue(gate.isReady(ShellMedia(9978, "片名", "第1集", 1, "u3", "", -1, 200, 1000)))
    }

    @Test
    fun `TVBox和Media3播放状态映射不得把暂停或缓冲当成播放`() {
        assertEquals(3, DanmuXposedShellMediaReader.mapTvBoxPlaybackState(3, false, 0, 1000))
        assertEquals(1, DanmuXposedShellMediaReader.mapTvBoxPlaybackState(4, false, 100, 1000))
        assertEquals(6, DanmuXposedShellMediaReader.mapTvBoxPlaybackState(6, false, 100, 1000))
        assertEquals(3, DanmuXposedShellMediaReader.mapTvBoxPlaybackState(6, true, 100, 1000))
        assertEquals(6, DanmuXposedShellMediaReader.mapMedia3PlaybackState(2, false, 0, 1000))
        assertEquals(1, DanmuXposedShellMediaReader.mapMedia3PlaybackState(3, false, 0, 1000))
        assertEquals(3, DanmuXposedShellMediaReader.mapMedia3PlaybackState(3, true, 0, 1000))
        assertEquals(-1, DanmuXposedShellMediaReader.mapMedia3PlaybackState(null, false, 100, 1000))
        assertEquals(6, DanmuXposedShellMediaReader.mapShellEndpointPlaybackState(1))
        assertEquals(1, DanmuXposedShellMediaReader.mapShellEndpointPlaybackState(2))
        assertEquals(3, DanmuXposedShellMediaReader.mapShellEndpointPlaybackState(3))
    }

    @Test
    fun `自动预匹配缓存必须绑定选集代次并按时过期`() {
        val candidate = EpisodeCandidate("片名", "第1集", 1, "tencent", "u1")
        val plan = PendingAutoPush("sig-1", 7, candidate, 9978, 900, 1000)

        assertTrue(plan.isUsable("sig-1", 7, 1100, 200))
        assertFalse(plan.isUsable("sig-2", 7, 1100, 200))
        assertFalse(plan.isUsable("sig-1", 8, 1100, 200))
        assertFalse(plan.isUsable("sig-1", 7, 1201, 200))
    }

    @Test
    fun `媒体合并保留Activity选集身份并采用接口播放状态`() {
        val endpoint = ShellMedia(9979, "片名 (2024)", "来源线路", -1, "stream", "", 3, 20, 1000)
        val activity = ShellMedia(9978, "片名", "第3集", 3, "", "line-a", -1, -1, -1)
        val merged = requireNotNull(DanmuXposedShellMediaReader.mergeMedia(endpoint, activity))

        assertEquals(9979, merged.port)
        assertEquals("片名 (2024)", merged.title)
        assertEquals("第3集", merged.episodeText)
        assertEquals(3, merged.episodeNumber)
        assertEquals("stream", merged.url)
        assertEquals("line-a", merged.vodFlag)
        assertEquals(3, merged.state)

        val enriched = requireNotNull(DanmuXposedShellMediaReader.mergeMedia(
            ShellMedia(9978, "片名", "第3集", 3, "stream", "", 3, 20, 1000),
            ShellMedia(9978, "片名 (2024) Ⅱ", "第3集", 3, "", "line-a", -1, -1, -1)
        ))
        assertEquals("片名 (2024) Ⅱ", enriched.title)
    }

    @Test
    fun `宿主刷新只有返回ok才算成功`() {
        assertTrue(DanmuXposedHttp.isSuccessfulShellPushResponse("OK"))
        assertTrue(DanmuXposedHttp.isSuccessfulShellPushResponse("{\"message\":\"ok\"}"))
        assertTrue(DanmuXposedHttp.isSuccessfulShellPushResponse("refresh=ok_done"))
        assertFalse(DanmuXposedHttp.isSuccessfulShellPushResponse(""))
        assertFalse(DanmuXposedHttp.isSuccessfulShellPushResponse("{\"error\":\"unsupported\"}"))
        assertFalse(DanmuXposedHttp.isSuccessfulShellPushResponse("not ok"))
        assertFalse(DanmuXposedHttp.isSuccessfulShellPushResponse("{\"ok\":false,\"message\":\"ok\"}"))
        assertFalse(DanmuXposedHttp.isSuccessfulShellPushResponse("broken"))
    }

    @Test
    fun `弹幕预取响应必须返回可信本地地址和明确数量`() {
        val prepared = DanmuXposedHttp.parsePreparedDanmakuResponse(
            """{"success":true,"url":"http://127.0.0.1:9321/__danmaku/prepared/a-1.xml","count":0,"size":128,"expiresAt":99}""",
            9321
        )

        assertEquals(0, prepared.count)
        assertEquals(128L, prepared.size)
        assertEquals(99L, prepared.expiresAtMs)
        assertTrue(prepared.url.endsWith("/a-1.xml"))

        assertTrue(runCatching {
            DanmuXposedHttp.parsePreparedDanmakuResponse(
                """{"success":true,"url":"https://example.com/danmu.xml","count":12}""",
                9321
            )
        }.isFailure)
        assertTrue(runCatching {
            DanmuXposedHttp.parsePreparedDanmakuResponse(
                """{"success":false,"errorMessage":"prepare failed"}""",
                9321
            )
        }.isFailure)
    }

    @Test
    fun `弹幕预取请求应完整编码原始URL`() {
        val url = DanmuXposedHttp.buildDanmakuPrepareUrl(
            9321,
            "http://127.0.0.1:9321/token/api/v2/comment/1?format=xml&offset=1.5"
        )

        assertTrue(url.startsWith("http://127.0.0.1:9321/__danmaku/prepare?url="))
        assertTrue(url.contains("%2Fapi%2Fv2%2Fcomment%2F1%3Fformat%3Dxml%26offset%3D1.5"))
    }

    @Test
    fun `番剧详情应优先用episodeId生成核心弹幕地址`() {
        val client = DanmuXposedBridgeClient(null)
        val anime = AnimeRef(
            "http://127.0.0.1:9321/token", "4747068", "", "测试番剧",
            "2026", 1, "iqiyi", "动漫"
        )
        val candidates = client.parseBangumiCandidates(
            anime.coreBase,
            """{"bangumi":{"episodes":[{"episodeId":"987654321","episodeNumber":"1","url":"https://www.iqiyi.com/v_example.html"}]}}""",
            anime,
            1
        )

        assertEquals(1, candidates.size)
        assertEquals(
            "http://127.0.0.1:9321/token/api/v2/comment/987654321?format=xml",
            candidates.single().url
        )
    }

    @Test
    fun `没有episodeId的平台地址应经核心评论接口解析`() {
        assertEquals(
            "http://127.0.0.1:9321/token/api/v2/comment?url=" +
                "https%3A%2F%2Fwww.iqiyi.com%2Fv_example.html%3Fx%3D1%26y%3D2&format=xml",
            DanmuXposedBridgeClient.resolveEpisodeDanmakuUrl(
                "http://127.0.0.1:9321/token/",
                "",
                "https://www.iqiyi.com/v_example.html?x=1&y=2"
            )
        )
    }
}
