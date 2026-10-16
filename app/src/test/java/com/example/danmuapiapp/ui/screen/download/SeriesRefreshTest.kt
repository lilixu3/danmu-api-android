package com.example.danmuapiapp.ui.screen.download

import com.example.danmuapiapp.domain.model.*
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress

class SeriesRefreshTest {
    private fun record(no: Int = 1, status: String = "success", source: String = "tencent") = DanmuDownloadRecord(
        id = no.toLong(), animeTitle = "测试剧(2023)【电视剧】from tencent", episodeTitle = "第${no}集", episodeId = 100L + no,
        episodeNo = no, source = source, format = "xml", status = status, fileName = "测试剧(2023)_E${no}.xml", animeId = 88)
    private fun candidate(title: String = "测试剧(2023)【电视剧】from tencent", source: String = "tencent") = DownloadAnimeCandidate(88, title, 4, sourceLabel = source)
    @Test fun `refresh contains updated and previously failed episodes but excludes downloaded ones`() {
        val episodes = (1..5).map { DownloadEpisodeCandidate(200L + it, it, "第${it}集", "tencent") }
        val missing = missingDownloadedEpisodes(episodes, listOf(record(1), record(2), record(3, "failed"), record(4, "skipped")))
        assertEquals(listOf(3, 4, 5), missing.map { it.episodeNumber })
    }
    @Test fun `changed episode IDs still match successful downloads by episode number`() {
        assertTrue(missingDownloadedEpisodes(listOf(DownloadEpisodeCandidate(9999, 1, "第一集", "tencent")), listOf(record())).isEmpty())
    }
    @Test fun `source lock rejects another platform`() {
        assertFalse(seriesCandidateMatches(candidate(source = "iqiyi", title = "测试剧(2023)【电视剧】from iqiyi"), record()))
        assertTrue(seriesCandidateMatches(candidate(source = "qq", title = "测试剧(2023)【电视剧】from qq"), record()))
    }
    @Test fun `merged source containing the original platform is allowed`() {
        assertTrue(seriesCandidateMatches(candidate(source = "tencent&aiyifan", title = "测试剧(2023)【电视剧】from tencent&aiyifan"), record()))
    }
    @Test fun `different year and different title are rejected`() {
        assertFalse(seriesCandidateMatches(candidate("测试剧(2013)【电视剧】from tencent"), record()))
        assertFalse(seriesCandidateMatches(candidate("另一部剧(2023)【电视剧】from tencent"), record()))
    }
    @Test fun `directory sync placeholder does not prevent discovering original source`() {
        assertTrue(seriesCandidateMatches(candidate(source = "iqiyi", title = "测试剧(2023)【电视剧】from iqiyi"),
            record(source = "目录同步").copy(animeTitle = "测试剧(2023)")))
    }
    @Test fun `explicit season is locked while partial title alias is accepted`() {
        val secondSeason = record().copy(animeTitle = "测试剧第二季(2023)【电视剧】from tencent", fileName = "测试剧(2023).S02E01.xml")
        assertFalse(seriesCandidateMatches(candidate("测试剧第三季(2023)【电视剧】from tencent"), secondSeason))
        assertTrue(seriesCandidateMatches(candidate("测试剧第二季特别篇(2023)【电视剧】from tencent"), secondSeason))
    }
    @Test fun `failed match falls back to same-source search and keeps latest full episode list`() = runBlocking {
        withServer { base, calls ->
            val result = SeriesRefreshService(OkHttpClient()).load(base, listOf(record()), "match")
            assertEquals("纯剧名搜索", result.way)
            assertEquals(listOf(1, 2), result.episodes.map { it.episodeNumber })
            assertEquals(42L, result.anime.animeId)
            assertTrue(calls.first().contains("/api/v2/match"))
            assertTrue(calls.any { it.contains("qq") })
        }
    }
    @Test fun `configured search mode starts with search and never needs match on success`() = runBlocking {
        withServer { base, calls ->
            val result = SeriesRefreshService(OkHttpClient()).load(base, listOf(record()), "search")
            assertEquals("纯剧名搜索", result.way)
            assertTrue(calls.first().contains("/api/v2/search/anime"))
            assertFalse(calls.any { it.contains("/api/v2/match") })
        }
    }
    @Test fun `both discovery methods failing use saved anime ID as final fallback`() = runBlocking {
        withServer(searchFails = true) { base, calls ->
            val result = SeriesRefreshService(OkHttpClient()).load(base, listOf(record()), "match")
            assertEquals("记录里的 animeId（缓存）", result.way)
            assertFalse(result.confirmedFresh)
            assertTrue(calls.any { it.contains("/api/v2/bangumi/88") })
        }
    }
    @Test fun `a missing file makes an old successful episode available for download again`() {
        val episodes = (1..3).map { DownloadEpisodeCandidate(200L + it, it, "第${it}集", "tencent") }
        val missing = missingDownloadedEpisodes(episodes, listOf(record(1), record(2)), setOf(2L))
        assertEquals(listOf(1, 3), missing.map { it.episodeNumber })
    }
    @Test fun `unidentified scanned episode does not hide episode one`() {
        val unknown = record().copy(episodeNo = 0, fileName = "测试剧.xml", episodeTitle = "正片")
        assertEquals(1, missingDownloadedEpisodes(listOf(DownloadEpisodeCandidate(999, 1, "第1集", "tencent")), listOf(unknown)).size)
    }
    @Test fun `refresh prefers a real source record over a newer ambiguous restored record`() {
        val unknown = record().copy(id = 200, animeId = 0, source = "目录同步", animeTitle = "测试剧", fileName = "E1.xml", createdAt = 1000)
        val known = record().copy(id = 100, createdAt = 1)
        assertEquals(known, refreshReferenceRecord(listOf(unknown, known)))
    }
    @Test fun `unknown original source never accepts local library results`() {
        val unknown = record(source = "目录同步").copy(animeTitle = "测试剧(2023)")
        assertFalse(seriesCandidateMatches(candidate("测试剧(2023)【电视剧】from local", "local"), unknown))
        assertTrue(seriesCandidateMatches(candidate(), unknown))
    }
    @Test fun `filename source survives directory sync placeholders`() {
        val restored = record(source = "目录同步").copy(animeTitle = "测试剧(2023)", fileName = "测试剧_E01_bilibili1.xml")
        assertEquals("bilibili", recordRefreshSource(restored))
        assertFalse(seriesCandidateMatches(candidate(), restored))
    }
    @Test fun `same title movie cannot replace a tv series`() {
        assertFalse(seriesCandidateMatches(candidate("测试剧(2023)【电影】from tencent"), record()))
    }
    @Test fun `fresh remote snapshot bypasses a shorter local candidate and stale detail endpoint`() = runBlocking {
        val local = """{"animeId":1,"animeTitle":"测试剧(2023)【电视剧】from local","source":"local"}"""
        val remote = """{"animeId":42,"animeTitle":"测试剧(2023)【电视剧】from tencent","source":"tencent"}"""
        val episodes = (1..18).joinToString(",") { """{"episodeId":${100 + it},"episodeNumber":$it,"episodeTitle":"第${it}集"}""" }
        val response = """{"appRefreshFresh":true,"animes":[$local,$remote],"appBangumis":[{"animeId":42,"source":"tencent","detail":{"success":true,"bangumi":{"animeTitle":"测试剧(2023)【电视剧】from tencent","episodes":[$episodes]}}}]}"""
        withServer(responder = { _, _ -> 200 to response }) { base, calls ->
            val result = SeriesRefreshService(OkHttpClient()).load(base, (1..12).map { record(it) }, "search")
            assertTrue(result.confirmedFresh)
            assertEquals(listOf(13, 14, 15, 16, 17, 18), missingDownloadedEpisodes(result.episodes, (1..12).map { record(it) }).map { it.episodeNumber })
            assertEquals(1, calls.size)
            assertTrue(calls.single().contains("app_refresh=1"))
            assertTrue(calls.single().contains("episode=13"))
        }
    }
    @Test fun `a cached successful match still refreshes search before declaring no new episodes`() = runBlocking {
        withServer(responder = { path, _ -> when {
            path.endsWith("/match") -> 200 to """{"matches":[{"animeId":88,"animeTitle":"测试剧(2023)【电视剧】from tencent","source":"tencent"}]}"""
            path.endsWith("/search/anime") -> 200 to """{"animes":[{"animeId":42,"animeTitle":"测试剧(2023)【电视剧】from tencent","source":"tencent"}]}"""
            else -> 200 to """{"bangumi":{"episodes":[{"episodeId":101,"episodeNumber":1}]}}"""
        } }) { base, calls ->
            val result = SeriesRefreshService(OkHttpClient()).load(base, listOf(record()), "match")
            assertEquals(42L, result.anime.animeId)
            assertTrue(calls.any { it.contains("app_refresh=1") })
            assertFalse(result.confirmedFresh)
        }
    }
    private suspend fun withServer(searchFails: Boolean = false,
        responder: ((String, String) -> Pair<Int, String>)? = null,
        block: suspend (String, MutableList<String>) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val calls = java.util.Collections.synchronizedList(mutableListOf<String>())
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.bufferedReader().use { it.readText() }
            calls.add(exchange.requestURI.toString() + " " + body)
            val (code, response) = responder?.invoke(exchange.requestURI.path, body) ?: when {
                exchange.requestURI.path.endsWith("/match") -> 503 to "{}"
                exchange.requestURI.path.endsWith("/search/anime") && searchFails -> 503 to "{}"
                exchange.requestURI.path.endsWith("/search/anime") -> 200 to """{"animes":[{"animeId":41,"animeTitle":"测试剧(2023)【电视剧】from iqiyi","source":"iqiyi"},{"animeId":42,"animeTitle":"测试剧(2023)【电视剧】from tencent","source":"tencent"}]}"""
                else -> 200 to """{"bangumi":{"episodes":[{"episodeId":101,"episodeNumber":1,"episodeTitle":"第1集"},{"episodeId":102,"episodeNumber":2,"episodeTitle":"第2集"}]}}"""
            }
            val bytes = response.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        try { block("http://127.0.0.1:${server.address.port}", calls) } finally { server.stop(0) }
    }
}
