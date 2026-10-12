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
        assertTrue(seriesCandidateMatches(candidate(source = "iqiyi", title = "测试剧(2023)【电视剧】from iqiyi"), record(source = "目录同步")))
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
            assertEquals("记录里的 animeId", result.way)
            assertTrue(calls.any { it.contains("/api/v2/bangumi/88") })
        }
    }
    private suspend fun withServer(searchFails: Boolean = false, block: suspend (String, MutableList<String>) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val calls = java.util.Collections.synchronizedList(mutableListOf<String>())
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.bufferedReader().use { it.readText() }
            calls.add(exchange.requestURI.path + " " + body)
            val (code, response) = when {
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
