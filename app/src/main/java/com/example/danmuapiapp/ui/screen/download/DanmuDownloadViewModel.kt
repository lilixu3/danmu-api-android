package com.example.danmuapiapp.ui.screen.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.data.util.RuntimeUrlParser
import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import com.example.danmuapiapp.domain.model.DanmuDownloadRecord
import com.example.danmuapiapp.domain.model.DownloadDirectorySyncResult
import com.example.danmuapiapp.domain.repository.AdminSessionRepository
import com.example.danmuapiapp.domain.repository.DanmuDownloadRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.ui.screen.download.support.resolveDownloadApiBaseUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * 弹幕下载页门面。
 *
 * 只做装配、初始装载与公开 API 转发，页面状态与业务规则都在专职控制器里：
 * - 搜索、链接解析与剧集加载：[DownloadSearchController]
 * - 剧集筛选与选中：[DownloadEpisodeSelectionController]
 * - 队列统计与队列级操作：[DownloadQueueController]
 * - 队列串行执行与流控重试：[DownloadQueueRunner]
 * - 下载记录、目录同步与整季补集：[DownloadRecordsController]
 * - 链路重建：[DownloadTaskInputRebuilder]；限流旁路：[DownloadRateLimitBypass]
 *
 * 对外属性仍是 Compose 可变状态，只是改由状态容器持有，读写路径不变。
 */
@HiltViewModel
class DanmuDownloadViewModel @Inject constructor(
    runtimeRepository: RuntimeRepository,
    private val downloadRepository: DanmuDownloadRepository,
    private val adminSessionRepository: AdminSessionRepository,
    private val httpClient: OkHttpClient,
    private val seriesRefresh: SeriesRefreshService,
    private val localUploader: com.example.danmuapiapp.data.repository.LocalDanmuUploadService
) : ViewModel() {

    val runtimeState = runtimeRepository.runtimeState
    val settings = downloadRepository.settings
    val records = downloadRepository.records
    val queueTasks = downloadRepository.queueTasks

    private val searchState = DownloadSearchUiState()
    private val episodeStore = DownloadEpisodeStateStore()
    private val taskState = DownloadTaskRuntimeState()
    private val messages = DownloadMessageState()
    private val recordsState = DownloadRecordsUiState()

    private fun resolveApiBaseUrl(): String? =
        resolveDownloadApiBaseUrl(searchState.sourceBase, runtimeState.value.token)

    private val rateLimitBypass = DownloadRateLimitBypass(
        httpClient = httpClient,
        adminSessionRepository = adminSessionRepository,
        runtimeState = { runtimeState.value }
    )

    private val taskInputRebuilder = DownloadTaskInputRebuilder(
        httpClient = httpClient,
        apiBase = { resolveApiBaseUrl() }
    )

    private val runner = DownloadQueueRunner(
        scope = viewModelScope,
        downloadRepository = downloadRepository,
        settings = { settings.value },
        queueTasks = { queueTasks.value },
        searchState = searchState,
        taskState = taskState,
        episodeStore = episodeStore,
        messages = messages,
        rateLimitBypass = rateLimitBypass,
        rebuildTaskInput = { task -> taskInputRebuilder.rebuild(task) }
    )

    private val queueController = DownloadQueueController(
        downloadRepository = downloadRepository,
        queueTasks = { queueTasks.value },
        taskState = taskState,
        messages = messages,
        startQueueProcessing = { runner.processPendingQueue() },
        cancelPendingTasks = { runner.cancelPendingTasks() }
    )

    private val selectionController = DownloadEpisodeSelectionController(
        downloadRepository = downloadRepository,
        settings = { settings.value },
        queueTasks = { queueTasks.value },
        state = searchState,
        episodeStore = episodeStore,
        taskState = taskState,
        messages = messages,
        resolveApiBaseUrl = { resolveApiBaseUrl() },
        startQueueProcessing = { runner.processPendingQueue() }
    )

    private val searchController = DownloadSearchController(
        scope = viewModelScope,
        httpClient = httpClient,
        downloadRepository = downloadRepository,
        settings = { settings.value },
        runtimeState = { runtimeState.value },
        queueTasks = { queueTasks.value },
        records = { records.value },
        state = searchState,
        episodeStore = episodeStore,
        messages = messages,
        resolveApiBaseUrl = { resolveApiBaseUrl() }
    )

    private val recordsController = DownloadRecordsController(
        scope = viewModelScope,
        downloadRepository = downloadRepository,
        seriesRefresh = seriesRefresh,
        settings = { settings.value },
        queueTasks = { queueTasks.value },
        records = { records.value },
        state = searchState,
        episodeStore = episodeStore,
        taskState = taskState,
        messages = messages,
        recordsState = recordsState,
        resolveApiBaseUrl = { resolveApiBaseUrl() },
        startQueueProcessing = { runner.processPendingQueue() }
    )

    val refreshingSeriesKey: String? get() = recordsState.refreshingSeriesKey
    internal val missingEpisodes: MissingEpisodesState? get() = recordsState.missingEpisodes
    val lastSync: DownloadDirectorySyncResult? get() = recordsState.lastSync
    val isSyncingRecords: Boolean get() = recordsState.isSyncingRecords
    val isDeletingRecords: Boolean get() = recordsState.isDeletingRecords
    val previewDialogState: DanmuPreviewDialogState get() = recordsState.previewDialogState

    val sourceBase: String get() = searchState.sourceBase
    val keyword: String get() = searchState.keyword
    val selectedFormat: DanmuDownloadFormat get() = searchState.selectedFormat
    val fileNameTemplate: String get() = searchState.fileNameTemplate
    val sourceFilter: String? get() = searchState.sourceFilter
    val isSearching: Boolean get() = searchState.isSearching
    val isLoadingEpisodes: Boolean get() = searchState.isLoadingEpisodes
    val hasSearchedAnime: Boolean get() = searchState.hasSearchedAnime
    val animeCandidates: List<DownloadAnimeCandidate> get() = searchState.animeCandidates
    val currentAnime: DownloadAnimeCandidate? get() = searchState.currentAnime
    val episodeCandidates: List<DownloadEpisodeCandidate> get() = searchState.episodeCandidates
    val selectedEpisodeIds: Set<Long> get() = searchState.selectedEpisodeIds
    val episodeStates: Map<Long, EpisodeDownloadUiState> get() = episodeStore.states

    val isDownloading: Boolean get() = taskState.isDownloading
    val overallProgress: Float get() = taskState.overallProgress
    val activeTaskId: Long? get() = taskState.activeTaskId
    val activeTaskAnimeTitle: String get() = taskState.activeTaskAnimeTitle
    val activeTaskEpisodeNo: Int? get() = taskState.activeTaskEpisodeNo
    val activeTaskSource: String get() = taskState.activeTaskSource
    val activeTaskDetail: String get() = taskState.activeTaskDetail
    val activeTaskProgress: Float get() = taskState.activeTaskProgress
    val progressSummary: String get() = messages.progressSummary
    val operationMessage: String? get() = messages.operationMessage
    val errorMessage: String? get() = messages.errorMessage
    val throttleHint: String? get() = messages.throttleHint

    init {
        searchState.sourceBase = RuntimeUrlParser.extractBase(runtimeState.value.lanUrl)
        val recovered = downloadRepository.markRunningTasksAsPending()
        if (recovered > 0) {
            taskState.requireQueuePreparationBeforeRun = true
            messages.operationMessage = "检测到上次未完成任务，已恢复到队列（$recovered）"
        }
        viewModelScope.launch {
            settings.collect { config ->
                searchState.selectedFormat = config.format()
                searchState.fileNameTemplate = config.fileNameTemplate
            }
        }
        viewModelScope.launch {
            val seen = records.value.map { it.id }.toMutableSet()
            records.collect { snapshot ->
                val fresh = snapshot.filter { it.id !in seen }
                seen.addAll(snapshot.map { it.id })
                if (settings.value.autoImportToLocal) fresh.filter { it.canAutoImportToLocal }.forEach { record ->
                    viewModelScope.launch {
                        localUploader.importRecord(record).exceptionOrNull()?.let { failure ->
                            if (failure.message != "已导入，跳过") {
                                val note = "自动导入未完成：${failure.message}；可在记录里订正后重试"
                                downloadRepository.noteRecord(record.id, note)
                                messages.operationMessage = "第${record.episodeNo}集已下载，$note"
                            }
                        }
                    }
                }
            }
        }
        if (settings.value.saveTreeUri.isNotBlank() || settings.value.saveDirPath.isNotBlank()) {
            recordsController.syncExistingFiles(showMessageWhenUnchanged = false)
        }
    }

    fun refreshSeries(group: DownloadRecordAnimeGroup) = recordsController.refreshSeries(group)

    fun toggleMissingEpisode(id: Long) = recordsController.toggleMissingEpisode(id)

    fun selectAllMissing(select: Boolean) = recordsController.selectAllMissing(select)

    fun setMissingSelection(ids: Set<Long>) = recordsController.setMissingSelection(ids)

    fun dismissMissing() = recordsController.dismissMissing()

    fun downloadMissing() = recordsController.downloadMissing()

    fun selectAllVisible() = selectionController.selectAllVisible()

    fun setEpisodeSelection(ids: Set<Long>) = selectionController.setEpisodeSelection(ids)

    fun invertVisibleSelection() = selectionController.invertVisibleSelection()

    fun selectUndownloadedEpisodes() = selectionController.selectUndownloadedEpisodes()

    fun removeQueueGroup(title: String) = queueController.removeQueueGroup(title)

    fun updateSourceBase(value: String) = searchController.updateSourceBase(value)

    fun updateKeyword(value: String) = searchController.updateKeyword(value)

    fun useLocalBase() = searchController.useLocalBase()

    fun useLanBase() = searchController.useLanBase()

    fun updateFormat(format: DanmuDownloadFormat) = searchController.updateFormat(format)

    fun updateFileNameTemplate(value: String) = searchController.updateFileNameTemplate(value)

    fun useDefaultTemplate() = searchController.useDefaultTemplate()

    fun dismissMessage() {
        messages.operationMessage = null
    }

    fun clearError() {
        messages.errorMessage = null
    }

    fun openRecordPreview(record: DanmuDownloadRecord) = recordsController.openRecordPreview(record)

    fun dismissRecordPreview() = recordsController.dismissRecordPreview()

    fun deleteRecords(recordIds: Set<Long>, deleteLocalFiles: Boolean) =
        recordsController.deleteRecords(recordIds, deleteLocalFiles)

    fun syncExistingFiles() = recordsController.syncExistingFiles()

    fun queueSummary(): DownloadQueueSummary = queueController.queueSummary()

    fun queueCompletedCount(): Int = queueController.queueCompletedCount()

    fun queueRunningStatusText(): String = queueController.queueRunningStatusText()

    fun animeQueueGroups(): List<AnimeQueueGroup> = queueController.animeQueueGroups()

    fun resumePendingQueue() = queueController.resumePendingQueue()

    fun clearQueueTasks() = queueController.clearQueueTasks()

    fun clearCompletedQueueTasks() = queueController.clearCompletedQueueTasks()

    fun retryFailedQueueTasks() = queueController.retryFailedQueueTasks()

    fun pauseDownload() = queueController.pauseDownload()

    fun moveQueueGroupUp(animeTitle: String) = queueController.moveQueueGroupUp(animeTitle)

    fun moveQueueGroupDown(animeTitle: String) = queueController.moveQueueGroupDown(animeTitle)

    fun backToAnimeList() = searchController.backToAnimeList()

    fun selectSourceFilter(value: String?) = selectionController.selectSourceFilter(value)

    fun toggleEpisodeSelection(episodeId: Long) = selectionController.toggleEpisodeSelection(episodeId)

    fun toggleSelectAllVisible() = selectionController.toggleSelectAllVisible()

    fun clearSelection() = selectionController.clearSelection()

    fun selectFailedVisibleEpisodes() = selectionController.selectFailedVisibleEpisodes()

    fun selectUnfinishedVisibleEpisodes() = selectionController.selectUnfinishedVisibleEpisodes()

    fun retryFailedVisibleEpisodes() = selectionController.retryFailedVisibleEpisodes()

    fun visibleStateSummary(): EpisodeStateSummary = selectionController.visibleStateSummary()

    fun sourceOptions(): List<String> = selectionController.sourceOptions()

    fun visibleEpisodes(): List<DownloadEpisodeCandidate> = selectionController.visibleEpisodes()

    fun searchAnime(): Boolean = searchController.searchAnime()

    fun openAnimeDetail(anime: DownloadAnimeCandidate): Boolean = searchController.openAnimeDetail(anime)

    fun cancelDownload() = queueController.cancelDownload()

    fun startDownloadSelectedEpisodes(): Boolean = selectionController.startDownloadSelectedEpisodes()

    fun episodeUiState(episode: DownloadEpisodeCandidate): EpisodeDownloadUiState =
        selectionController.episodeUiState(episode)
}
