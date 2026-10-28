package com.example.danmuapiapp.ui.screen.download

import com.example.danmuapiapp.data.util.RuntimeUrlParser
import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import com.example.danmuapiapp.domain.model.DanmuDownloadRecord
import com.example.danmuapiapp.domain.model.DanmuDownloadSettings
import com.example.danmuapiapp.domain.model.DanmuDownloadTask
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.model.extractSourceFromAnimeTitle
import com.example.danmuapiapp.domain.repository.DanmuDownloadRepository
import com.example.danmuapiapp.ui.screen.download.support.downloadHttpGet
import com.example.danmuapiapp.ui.screen.apitest.ApiTestInputResolver
import com.example.danmuapiapp.ui.screen.apitest.UrlDanmuMetadataResolver
import com.example.danmuapiapp.ui.screen.apitest.buildManualUrlDanmuSelection
import com.example.danmuapiapp.ui.screen.apitest.buildManualUrlEpisodeCandidate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * 弹幕下载搜索：关键词搜索、视频链接解析、剧集加载与来源/格式设置。
 *
 * 只读写 [DownloadSearchUiState] 与 [DownloadEpisodeStateStore]，基础地址由调用方解析。
 */
internal class DownloadSearchController(
    private val scope: CoroutineScope,
    private val httpClient: OkHttpClient,
    private val downloadRepository: DanmuDownloadRepository,
    private val settings: () -> DanmuDownloadSettings,
    private val runtimeState: () -> RuntimeState,
    private val queueTasks: () -> List<DanmuDownloadTask>,
    private val records: () -> List<DanmuDownloadRecord>,
    private val state: DownloadSearchUiState,
    private val episodeStore: DownloadEpisodeStateStore,
    private val messages: DownloadMessageState,
    private val resolveApiBaseUrl: () -> String?
) {
    private var episodeLoadJob: Job? = null

    fun updateSourceBase(value: String) {
        state.sourceBase = value
    }

    fun updateKeyword(value: String) {
        state.keyword = value
    }

    fun useLocalBase() {
        state.sourceBase = RuntimeUrlParser.extractBase(runtimeState().localUrl)
    }

    fun useLanBase() {
        state.sourceBase = RuntimeUrlParser.extractBase(runtimeState().lanUrl)
    }

    fun updateFormat(format: DanmuDownloadFormat) {
        state.selectedFormat = format
        downloadRepository.setDefaultFormat(format)
    }

    fun updateFileNameTemplate(value: String) {
        state.fileNameTemplate = value
    }

    fun useDefaultTemplate() {
        state.fileNameTemplate = settings().fileNameTemplate
    }

    fun searchAnime(): Boolean {
        if (state.isSearching || state.isLoadingEpisodes) return false

        val apiBase = resolveApiBaseUrl()
        if (apiBase == null) {
            messages.errorMessage = "弹幕源地址无效"
            return false
        }

        val q = state.keyword.trim()
        if (q.isBlank()) {
            messages.errorMessage = "请输入搜索关键词或视频链接"
            return false
        }

        ApiTestInputResolver.extractHttpUrl(q)?.let { inputUrl ->
            searchUrlCandidate(apiBase, inputUrl)
            return true
        }

        val url = "$apiBase/api/v2/search/anime?keyword=${urlEncode(q)}"
        backToAnimeList()
        state.isSearching = true
        messages.errorMessage = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { requestGet(url) }
            state.hasSearchedAnime = true
            result.fold(
                onSuccess = { (code, body) ->
                    if (code in 200..299) {
                        state.animeCandidates = parseAnimeCandidates(body)
                        state.currentAnime = null
                        state.episodeCandidates = emptyList()
                        state.selectedEpisodeIds = emptySet()
                        episodeStore.clear()
                        state.sourceFilter = null
                    } else {
                        messages.errorMessage = "搜索失败：HTTP $code"
                        state.animeCandidates = emptyList()
                    }
                },
                onFailure = {
                    messages.errorMessage = it.message ?: "搜索失败"
                    state.animeCandidates = emptyList()
                }
            )
            state.isSearching = false
        }
        return true
    }

    private fun searchUrlCandidate(apiBase: String, inputUrl: String) {
        if (state.isSearching || state.isLoadingEpisodes) return
        scope.launch {
            state.isSearching = true
            messages.errorMessage = null
            state.animeCandidates = emptyList()
            state.currentAnime = null
            state.episodeCandidates = emptyList()
            state.selectedEpisodeIds = emptySet()
            episodeStore.clear()
            state.sourceFilter = null
            val metadata = withContext(Dispatchers.IO) {
                runCatching { UrlDanmuMetadataResolver(httpClient).resolve(inputUrl, apiBase) }.getOrNull()
            }
            val selection = withContext(Dispatchers.Default) {
                buildManualUrlDanmuSelection(inputUrl = inputUrl, metadata = metadata)
            }
            state.animeCandidates = listOf(selection.anime)
            state.hasSearchedAnime = true
            messages.operationMessage = selection.anime.directMetadataNotice.ifBlank {
                if (selection.anime.directEpisodes.size > 1) {
                    "已解析链接，共 ${selection.anime.directEpisodes.size} 个播放条目"
                } else {
                    "已解析链接，可直接下载弹幕"
                }
            }
            state.isSearching = false
        }
    }

    private fun openDirectUrlAnimeDetail(anime: DownloadAnimeCandidate): Boolean {
        val episodes = anime.directEpisodes.ifEmpty {
            listOfNotNull(buildManualUrlEpisodeCandidate(anime))
        }
        if (episodes.isEmpty()) {
            messages.errorMessage = "链接解析结果缺少可用地址"
            return false
        }
        state.currentAnime = anime
        state.episodeCandidates = episodes
        state.selectedEpisodeIds = emptySet()
        state.sourceFilter = null
        episodeStore.replaceAll(
            buildInitialEpisodeStates(
                animeTitle = anime.title,
                episodes = episodes,
                queueTasksSnapshot = queueTasks(),
                recordsSnapshot = records(),
                animeId = anime.animeId
            )
        )
        messages.errorMessage = null
        messages.operationMessage = anime.directMetadataNotice.takeIf { it.isNotBlank() }
        return true
    }

    fun openAnimeDetail(anime: DownloadAnimeCandidate): Boolean {
        if (state.isSearching || state.isLoadingEpisodes) return false

        if (anime.directUrl.isNotBlank()) {
            return openDirectUrlAnimeDetail(anime)
        }

        val apiBase = resolveApiBaseUrl()
        if (apiBase == null) {
            messages.errorMessage = "弹幕源地址无效"
            return false
        }

        state.isLoadingEpisodes = true
        messages.errorMessage = null
        state.currentAnime = anime
        state.episodeCandidates = emptyList()
        state.selectedEpisodeIds = emptySet()
        episodeStore.clear()
        state.sourceFilter = null
        episodeLoadJob = scope.launch {
            val fallbackSource = extractSourceFromAnimeTitle(anime.title)
            val url = "$apiBase/api/v2/bangumi/${anime.animeId}"
            val result = withContext(Dispatchers.IO) {
                requestGet(url).mapCatching { (code, body) ->
                    if (code !in 200..299) {
                        error("加载剧集失败：HTTP $code")
                    }
                    parseEpisodeCandidates(body, fallbackSource = fallbackSource)
                }
            }

            result.fold(
                onSuccess = { episodes ->
                    val initialStates = withContext(Dispatchers.Default) {
                        buildInitialEpisodeStates(
                            animeTitle = anime.title,
                            episodes = episodes,
                            queueTasksSnapshot = queueTasks(),
                            recordsSnapshot = records(),
                            animeId = anime.animeId
                        )
                    }
                    state.episodeCandidates = episodes
                    state.selectedEpisodeIds = emptySet()
                    episodeStore.replaceAll(initialStates)
                },
                onFailure = { throwable ->
                    messages.errorMessage = throwable.message ?: "加载剧集失败"
                    state.episodeCandidates = emptyList()
                }
            )
            state.isLoadingEpisodes = false
        }
        return true
    }

    fun backToAnimeList() {
        episodeLoadJob?.cancel()
        episodeLoadJob = null
        state.isLoadingEpisodes = false
        state.currentAnime = null
        state.episodeCandidates = emptyList()
        state.selectedEpisodeIds = emptySet()
        episodeStore.clear()
        state.sourceFilter = null
    }

    private fun requestGet(url: String): Result<Pair<Int, String>> {
        return downloadHttpGet(httpClient, url)
    }
}
