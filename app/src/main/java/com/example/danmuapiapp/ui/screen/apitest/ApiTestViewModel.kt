package com.example.danmuapiapp.ui.screen.apitest

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.data.util.DotEnvCodec
import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import com.example.danmuapiapp.domain.repository.EnvConfigRepository
import com.example.danmuapiapp.domain.repository.RequestRecordRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.ui.screen.download.DownloadAnimeCandidate
import com.example.danmuapiapp.ui.screen.download.DownloadEpisodeCandidate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * 弹幕测试页门面 ViewModel：只做依赖装配、状态转发与跨控制器编排。
 *
 * 具体职责按域拆到同包控制器中：
 * - [ApiTestRequestExecutor]：出站请求、请求记录落库与 Base URL 归一化
 * - [ApiTestDebugController]：接口调试页的参数拼装、发送请求与调试响应
 * - [ApiTestAutoMatchController]：文件名自动匹配与 URL 直达解析
 * - [ApiTestManualSearchController]：关键词/URL 搜索、剧集加载与手动取弹幕
 * - [ApiTestFavoriteController]：收藏列表加载与增删/刷新/定时设置
 * - [ApiTestExportController]：弹幕导出请求、载荷校验与待保存文件
 *
 * 对外 API 与拆分前保持一致，界面调用点零改动。
 */
@HiltViewModel
class ApiTestViewModel @Inject constructor(
    private val runtimeRepository: RuntimeRepository,
    private val recordRepository: RequestRecordRepository,
    private val httpClient: OkHttpClient,
    private val envConfigRepository: EnvConfigRepository
) : ViewModel() {
    private companion object {
        const val SEARCH_RESULTS_LAYOUT_KEY = "APP_API_SEARCH_RESULTS_LAYOUT"
    }

    val runtimeState = runtimeRepository.runtimeState
    val logs = runtimeRepository.logs
    val endpoints = ApiTestCatalog.endpoints

    var animeResultsGrid by mutableStateOf(false)
        private set

    var isSavingAnimeResultsLayout by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var uiNotice by mutableStateOf<ApiTestUiNotice?>(null)
        private set

    var lastApiActionStartedAtMs by mutableStateOf<Long?>(null)
        private set

    private var uiEventSequence = 0L

    private val requestExecutor: ApiTestRequestExecutor = ApiTestRequestExecutor(
        httpClient = httpClient,
        recordRepository = recordRepository,
        runtimeState = runtimeState
    )
    private val favoriteController: ApiTestFavoriteController = ApiTestFavoriteController(
        scope = viewModelScope,
        executor = requestExecutor,
        emitNotice = ::emitNotice,
        successfulSearchKeyword = { manualSearchController.successfulManualSearchKeyword },
        hasAnimeCandidates = { manualSearchController.manualAnimeCandidates.isNotEmpty() }
    )
    private val manualSearchController: ApiTestManualSearchController = ApiTestManualSearchController(
        scope = viewModelScope,
        executor = requestExecutor,
        httpClient = httpClient,
        setError = { errorMessage = it },
        markApiAction = { lastApiActionStartedAtMs = it },
        reloadFavorites = { base, showLoading -> favoriteController.loadFavoritesResolved(base, showLoading) }
    )
    private val autoMatchController: ApiTestAutoMatchController = ApiTestAutoMatchController(
        scope = viewModelScope,
        executor = requestExecutor,
        httpClient = httpClient,
        currentError = { errorMessage },
        setError = { errorMessage = it },
        markApiAction = { lastApiActionStartedAtMs = it }
    )
    private val debugController: ApiTestDebugController = ApiTestDebugController(
        scope = viewModelScope,
        executor = requestExecutor,
        setError = { errorMessage = it },
        markApiAction = { lastApiActionStartedAtMs = it }
    )
    private val exportController: ApiTestExportController = ApiTestExportController(
        scope = viewModelScope,
        executor = requestExecutor,
        emitNotice = ::emitNotice,
        nextEventId = ::nextUiEventId
    )

    // 接口调试
    val isLoading: Boolean
        get() = debugController.isLoading
    val debugResponse: ApiDebugResponse?
        get() = debugController.debugResponse
    val debugCalls: List<ApiDebugCall>
        get() = debugController.debugCalls
    val requestUrl: String
        get() = debugController.requestUrl
    val curlCommand: String
        get() = debugController.curlCommand

    // 自动匹配
    val isAutoMatching: Boolean
        get() = autoMatchController.isAutoMatching
    val autoMatchResult: DanmuInsight?
        get() = autoMatchController.autoMatchResult

    // 手动搜索
    val isSearchingAnime: Boolean
        get() = manualSearchController.isSearchingAnime
    val isLoadingEpisodes: Boolean
        get() = manualSearchController.isLoadingEpisodes
    val isLoadingManualDanmu: Boolean
        get() = manualSearchController.isLoadingManualDanmu
    val loadingEpisodeId: Long?
        get() = manualSearchController.loadingEpisodeId
    val manualHasSearched: Boolean
        get() = manualSearchController.manualHasSearched
    val manualAnimeCandidates: List<DownloadAnimeCandidate>
        get() = manualSearchController.manualAnimeCandidates
    val manualCurrentAnime: DownloadAnimeCandidate?
        get() = manualSearchController.manualCurrentAnime
    val manualEpisodeCandidates: List<DownloadEpisodeCandidate>
        get() = manualSearchController.manualEpisodeCandidates
    val manualResult: DanmuInsight?
        get() = manualSearchController.manualResult
    val loadingAnimeId: Long?
        get() = manualSearchController.loadingAnimeId
    val successfulManualSearchKeyword: String
        get() = manualSearchController.successfulManualSearchKeyword

    // 收藏
    val favoriteSupportState: FavoriteSupportState
        get() = favoriteController.favoriteSupportState
    val favoriteItems: List<ApiTestFavoriteItem>
        get() = favoriteController.favoriteItems
    val scheduledFavoriteRefreshSupported: Boolean
        get() = favoriteController.scheduledFavoriteRefreshSupported
    val favoriteLoadError: String?
        get() = favoriteController.favoriteLoadError
    val favoriteOperation: FavoriteOperation?
        get() = favoriteController.favoriteOperation
    val favoriteOperationKeyword: String?
        get() = favoriteController.favoriteOperationKeyword

    // 导出
    val isExportingDanmu: Boolean
        get() = exportController.isExportingDanmu
    val preparedExport: DanmuExportPayload?
        get() = exportController.preparedExport

    init {
        viewModelScope.launch {
            envConfigRepository.loadedEnvVars.collect { values ->
                if (values != null && !isSavingAnimeResultsLayout) {
                    animeResultsGrid = values[SEARCH_RESULTS_LAYOUT_KEY]?.trim().equals("grid", ignoreCase = true)
                }
            }
        }
        envConfigRepository.reload()
    }

    fun toggleAnimeResultsLayout() {
        if (isSavingAnimeResultsLayout) return
        val previous = animeResultsGrid
        val value = if (previous) "list" else "grid"
        animeResultsGrid = !previous
        isSavingAnimeResultsLayout = true
        viewModelScope.launch {
            try {
                envConfigRepository.setValue(SEARCH_RESULTS_LAYOUT_KEY, value)
                val saved = withContext(Dispatchers.IO) {
                    DotEnvCodec.parse(envConfigRepository.readCurrentRawContent().getOrThrow())
                }
                check(saved[SEARCH_RESULTS_LAYOUT_KEY] == value) { "未能保存布局配置" }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                animeResultsGrid = previous
                emitNotice("布局保存失败：${error.message ?: "无法写入配置"}", isError = true)
            } finally {
                isSavingAnimeResultsLayout = false
            }
        }
    }

    fun dismissError() {
        errorMessage = null
    }

    fun refreshLogs() = runtimeRepository.refreshLogs()

    fun consumeUiNotice(id: Long) {
        if (uiNotice?.id == id) uiNotice = null
    }

    fun danmuRequestUrl(baseUrl: String, insight: DanmuInsight): String {
        val base = requestExecutor.resolveApiBaseUrl(baseUrl).orEmpty()
        return when (val target = insight.exportTarget) {
            is DanmuExportTarget.Episode -> "$base/api/v2/comment/${target.episodeId}?format=json&duration=true"
            is DanmuExportTarget.VideoUrl -> "$base/api/v2/comment?url=${requestExecutor.urlEncode(target.url)}&format=json&duration=true"
            null -> ""
        }
    }

    // 接口调试
    fun sendRequest(
        endpoint: ApiEndpointConfig,
        baseUrl: String,
        paramValues: Map<String, String>,
        rawBody: String
    ) = debugController.sendRequest(endpoint, baseUrl, paramValues, rawBody)

    fun clearDebugResponse() = debugController.clearDebugResponse()

    fun clearDebugCalls() = debugController.clearDebugCalls()

    // 自动匹配
    fun runAutoMatch(baseUrl: String, fileName: String) = autoMatchController.runAutoMatch(baseUrl, fileName)

    fun clearAutoResult() = autoMatchController.clearAutoResult()

    // 手动搜索
    fun searchAnime(baseUrl: String, keyword: String) = manualSearchController.searchAnime(baseUrl, keyword)

    fun openManualAnimeDetail(baseUrl: String, anime: DownloadAnimeCandidate) =
        manualSearchController.openManualAnimeDetail(baseUrl, anime)

    fun loadManualDanmu(
        baseUrl: String,
        anime: DownloadAnimeCandidate,
        episode: DownloadEpisodeCandidate
    ) = manualSearchController.loadManualDanmu(baseUrl, anime, episode)

    fun clearManualDanmu() = manualSearchController.clearManualDanmu()

    fun reloadManualDanmu(baseUrl: String) = manualSearchController.reloadManualDanmu(baseUrl)

    fun backManualStep() = manualSearchController.backManualStep()

    // 收藏
    fun loadFavorites(baseUrl: String) = favoriteController.loadFavorites(baseUrl)

    fun toggleManualFavorite(baseUrl: String, currentQuery: String) =
        favoriteController.toggleManualFavorite(baseUrl, currentQuery)

    fun refreshFavorite(baseUrl: String, item: ApiTestFavoriteItem) =
        favoriteController.refreshFavorite(baseUrl, item)

    fun removeFavorite(baseUrl: String, item: ApiTestFavoriteItem) =
        favoriteController.removeFavorite(baseUrl, item)

    fun updateFavoriteSchedule(
        baseUrl: String,
        item: ApiTestFavoriteItem,
        schedule: FavoriteScheduleDraft?
    ) = favoriteController.updateFavoriteSchedule(baseUrl, item, schedule)

    fun favoriteForKeyword(keyword: String): ApiTestFavoriteItem? =
        favoriteController.favoriteForKeyword(keyword)

    // 导出
    fun prepareDanmuExport(
        baseUrl: String,
        insight: DanmuInsight,
        format: DanmuDownloadFormat
    ) = exportController.prepareDanmuExport(baseUrl, insight, format)

    fun onExportSaved(fileName: String) = exportController.onExportSaved(fileName)

    fun onExportSaveFailed(message: String) = exportController.onExportSaveFailed(message)

    fun consumePreparedExport(id: Long) = exportController.consumePreparedExport(id)

    private fun emitNotice(message: String, isError: Boolean = false) {
        uiNotice = ApiTestUiNotice(
            id = nextUiEventId(),
            message = message,
            isError = isError
        )
    }

    private fun nextUiEventId(): Long {
        uiEventSequence += 1L
        return (System.currentTimeMillis() shl 8) xor uiEventSequence
    }
}
