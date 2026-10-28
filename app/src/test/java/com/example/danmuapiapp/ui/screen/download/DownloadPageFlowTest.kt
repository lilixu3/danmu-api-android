package com.example.danmuapiapp.ui.screen.download

import com.example.danmuapiapp.domain.model.DanmuDownloadInput
import com.example.danmuapiapp.domain.model.DanmuDownloadSettings
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.repository.DanmuDownloadRepository
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class DownloadPageFlowTest {
    @Test fun directLinkEpisodesStartUnselectedAndReopeningClearsSelection() {
        Fixture().use { fixture ->
            val anime = DownloadAnimeCandidate(1, "测试剧", 3, directUrl = "https://video.test/1", directEpisodes = fixture.episodes)
            assertTrue(fixture.search.openAnimeDetail(anime))
            assertEquals(3, fixture.state.episodeCandidates.size)
            assertTrue(fixture.state.selectedEpisodeIds.isEmpty())
            fixture.selection.selectAllVisible()
            fixture.search.backToAnimeList()
            assertTrue(fixture.search.openAnimeDetail(anime))
            assertTrue(fixture.state.selectedEpisodeIds.isEmpty())
        }
    }

    @Test fun fetchedEpisodesStartUnselected() = runBlocking {
        Fixture().use { fixture ->
            assertTrue(fixture.search.openAnimeDetail(DownloadAnimeCandidate(1, "测试剧", 1)))
            fixture.awaitRequests()
            assertEquals(11L, fixture.state.episodeCandidates.single().episodeId)
            assertTrue(fixture.state.selectedEpisodeIds.isEmpty())
            assertFalse(fixture.state.isLoadingEpisodes)
        }
    }

    @Test fun backCancelsOldEpisodeLoadWithoutOverwritingNewPage() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        Fixture { request ->
            if (request.url.encodedPath.endsWith("/1")) {
                started.complete(Unit)
                check(release.await(5, TimeUnit.SECONDS))
                episodeJson(11)
            } else episodeJson(22)
        }.use { fixture ->
            try {
                fixture.search.openAnimeDetail(DownloadAnimeCandidate(1, "旧剧", 1))
                withTimeout(5000) { started.await() }
                fixture.search.backToAnimeList()
                assertNull(fixture.state.currentAnime)
                assertFalse(fixture.state.isLoadingEpisodes)
                assertTrue(fixture.search.openAnimeDetail(DownloadAnimeCandidate(2, "新剧", 1)))
            } finally {
                release.countDown()
            }
            fixture.awaitRequests()
            assertEquals(2L, fixture.state.currentAnime?.animeId)
            assertEquals(22L, fixture.state.episodeCandidates.single().episodeId)
            assertTrue(fixture.state.selectedEpisodeIds.isEmpty())
        }
    }

    @Test fun emptySelectionCannotEnqueueAndPartialSelectionOnlyEnqueuesChosenEpisodes() {
        Fixture().use { fixture ->
            fixture.state.currentAnime = DownloadAnimeCandidate(1, "测试剧", 3)
            fixture.state.episodeCandidates = fixture.episodes
            assertFalse(fixture.selection.startDownloadSelectedEpisodes())
            assertTrue(fixture.enqueued.isEmpty())
            fixture.selection.setEpisodeSelection(setOf(1L, 3L, 999L))
            assertTrue(fixture.selection.startDownloadSelectedEpisodes())
            assertEquals(listOf(1L, 3L), fixture.enqueued.map { it.episodeId })
            assertNull(fixture.messages.errorMessage)
            assertEquals(1, fixture.queueStarts)
        }
    }

    @Test fun compactSelectionActionsUseTheCurrentVisibleEpisodes() {
        Fixture().use { fixture ->
            fixture.state.episodeCandidates = fixture.episodes
            fixture.store.replaceAll(mapOf(2L to EpisodeDownloadUiState(downloadedBefore = true)))
            fixture.selection.selectUndownloadedEpisodes()
            assertEquals(setOf(1L, 3L), fixture.state.selectedEpisodeIds)
            fixture.selection.invertVisibleSelection()
            assertEquals(setOf(2L), fixture.state.selectedEpisodeIds)
            fixture.selection.selectAllVisible()
            assertEquals(setOf(1L, 2L, 3L), fixture.state.selectedEpisodeIds)
            fixture.selection.selectSourceFilter("second")
            assertEquals(setOf(3L), fixture.state.selectedEpisodeIds)
            fixture.selection.clearSelection()
            fixture.selection.selectAllVisible()
            assertEquals(setOf(3L), fixture.state.selectedEpisodeIds)
        }
    }

    @Test fun blankSearchDoesNotRequestResultNavigation() {
        Fixture().use { fixture ->
            assertFalse(fixture.search.searchAnime())
            assertFalse(fixture.state.isSearching)
            assertNotNull(fixture.messages.errorMessage)
        }
    }

    private class Fixture(responseBody: (Request) -> String = { episodeJson(11) }) : AutoCloseable {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val state = DownloadSearchUiState()
        val store = DownloadEpisodeStateStore()
        val messages = DownloadMessageState()
        val tasks = DownloadTaskRuntimeState()
        val episodes = (1L..3L).map { DownloadEpisodeCandidate(it, it.toInt(), "第 $it 集", if (it == 3L) "second" else "first") }
        var enqueued = emptyList<DanmuDownloadInput>()
        var queueStarts = 0
        private val repository = Proxy.newProxyInstance(DanmuDownloadRepository::class.java.classLoader,
            arrayOf(DanmuDownloadRepository::class.java)) { _, method, arguments ->
            when (method.name) {
                "enqueueTasks" -> {
                    enqueued = (arguments!![0] as List<*>).filterIsInstance<DanmuDownloadInput>()
                    enqueued.size
                }
                else -> error("Unexpected repository call: ${method.name}")
            }
        } as DanmuDownloadRepository
        private val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(responseBody(chain.request()).toResponseBody()).build()
        }.build()
        private val settings = DanmuDownloadSettings(saveDirPath = "/unused-download-test-directory")
        val search = DownloadSearchController(scope, client, repository, { settings }, { RuntimeState() },
            { emptyList() }, { emptyList() }, state, store, messages, { "https://download.test" })
        val selection = DownloadEpisodeSelectionController(repository, { settings }, { emptyList() }, state,
            store, tasks, messages, { "https://download.test" }, { queueStarts++ })

        suspend fun awaitRequests() {
            withTimeout(5000) { scope.coroutineContext[Job]!!.children.toList().joinAll() }
        }

        override fun close() { scope.cancel() }
    }

    companion object {
        private fun episodeJson(id: Int) = """{"episodes":[{"episodeId":$id,"episodeNumber":1,"episodeTitle":"第一集"}]}"""
    }
}
