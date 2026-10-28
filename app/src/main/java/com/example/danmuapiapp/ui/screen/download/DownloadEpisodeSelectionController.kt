package com.example.danmuapiapp.ui.screen.download

import com.example.danmuapiapp.domain.model.DanmuDownloadInput
import com.example.danmuapiapp.domain.model.DanmuDownloadSettings
import com.example.danmuapiapp.domain.model.DanmuDownloadTask
import com.example.danmuapiapp.domain.model.DownloadQueueStatus
import com.example.danmuapiapp.domain.repository.DanmuDownloadRepository

/**
 * 剧集筛选与选中：来源过滤、批量选择、可见状态统计与“下载选中集”。
 */
internal class DownloadEpisodeSelectionController(
    private val downloadRepository: DanmuDownloadRepository,
    private val settings: () -> DanmuDownloadSettings,
    private val queueTasks: () -> List<DanmuDownloadTask>,
    private val state: DownloadSearchUiState,
    private val episodeStore: DownloadEpisodeStateStore,
    private val taskState: DownloadTaskRuntimeState,
    private val messages: DownloadMessageState,
    private val resolveApiBaseUrl: () -> String?,
    private val startQueueProcessing: () -> Unit
) {

    fun selectAllVisible() {
        state.selectedEpisodeIds = state.selectedEpisodeIds + visibleEpisodes().map { it.episodeId }
    }

    fun invertVisibleSelection() {
        val visible = visibleEpisodes().map { it.episodeId }.toSet()
        state.selectedEpisodeIds = (state.selectedEpisodeIds - visible) + (visible - state.selectedEpisodeIds)
    }

    fun selectUndownloadedEpisodes() {
        state.selectedEpisodeIds = visibleEpisodes()
            .filter { !episodeUiState(it).downloadedBefore && episodeUiState(it).state != EpisodeDownloadState.Success }
            .map { it.episodeId }
            .toSet()
    }

    fun selectSourceFilter(value: String?) {
        state.sourceFilter = value
        state.selectedEpisodeIds = state.selectedEpisodeIds.intersect(visibleEpisodes().map { it.episodeId }.toSet())
    }

    fun toggleEpisodeSelection(episodeId: Long) {
        state.selectedEpisodeIds = if (state.selectedEpisodeIds.contains(episodeId)) {
            state.selectedEpisodeIds - episodeId
        } else {
            state.selectedEpisodeIds + episodeId
        }
    }

    fun toggleSelectAllVisible() {
        val visibleIds = visibleEpisodes().map { it.episodeId }.toSet()
        if (visibleIds.isEmpty()) return
        val allSelected = visibleIds.all { state.selectedEpisodeIds.contains(it) }
        state.selectedEpisodeIds = if (allSelected) {
            state.selectedEpisodeIds - visibleIds
        } else {
            state.selectedEpisodeIds + visibleIds
        }
    }

    fun clearSelection() {
        state.selectedEpisodeIds = emptySet()
    }

    fun setEpisodeSelection(ids: Set<Long>) {
        state.selectedEpisodeIds = ids.intersect(visibleEpisodes().map { it.episodeId }.toSet())
    }

    fun selectFailedVisibleEpisodes() {
        if (taskState.isDownloading) return
        val failedIds = visibleEpisodes().filter { episode ->
            episodeStore.stateOf(episode) == EpisodeDownloadState.Failed
        }.map { it.episodeId }.toSet()
        state.selectedEpisodeIds = failedIds
        messages.operationMessage = if (failedIds.isEmpty()) "当前筛选下没有失败项" else "已选择 ${failedIds.size} 个失败项"
    }

    fun selectUnfinishedVisibleEpisodes() {
        if (taskState.isDownloading) return
        val ids = visibleEpisodes().filter { episode ->
            when (episodeStore.stateOf(episode)) {
                EpisodeDownloadState.Success, EpisodeDownloadState.Skipped -> false
                else -> true
            }
        }.map { it.episodeId }.toSet()
        state.selectedEpisodeIds = ids
        messages.operationMessage = if (ids.isEmpty()) "当前筛选下没有未完成项" else "已选择 ${ids.size} 个未完成项"
    }

    fun retryFailedVisibleEpisodes() {
        if (taskState.isDownloading || state.isSearching || state.isLoadingEpisodes) return
        val visibleEpisodeIds = visibleEpisodes().map { it.episodeId }.toSet()
        val failedTaskIds = queueTasks()
            .filter { task ->
                visibleEpisodeIds.contains(task.episodeId) && task.statusEnum() == DownloadQueueStatus.Failed
            }
            .map { it.taskId }
            .toSet()
        if (failedTaskIds.isNotEmpty()) {
            val reset = downloadRepository.resetQueueTasks(failedTaskIds, detail = "等待重试")
            if (reset > 0) {
                taskState.requireQueuePreparationBeforeRun = true
                messages.operationMessage = "已重试入队 $reset 项"
                startQueueProcessing()
            } else {
                messages.operationMessage = "失败项重试入队失败"
            }
            return
        }

        val anime = state.currentAnime
        val apiBase = resolveApiBaseUrl()
        val failedEpisodes = visibleEpisodes().filter { episode ->
            episodeStore.stateOf(episode) == EpisodeDownloadState.Failed
        }
        if (anime == null || apiBase == null || failedEpisodes.isEmpty()) {
            messages.operationMessage = "当前筛选下没有失败项可重试"
            return
        }
        val inputs = failedEpisodes.map { episode ->
            DanmuDownloadInput(
                apiBaseUrl = apiBase,
                animeTitle = anime.title,
                episodeTitle = episode.title,
                episodeId = episode.episodeId,
                episodeNo = episode.episodeNumber,
                source = episode.source,
                format = state.selectedFormat,
                fileNameTemplate = state.fileNameTemplate,
                conflictPolicy = settings().policy(),
                animeId = anime.animeId,
                mediaType = com.example.danmuapiapp.domain.model.LocalDanmuType.fromDescription(anime.typeDescription)?.wire.orEmpty(),
                directUrl = episode.directUrl
            )
        }
        val added = downloadRepository.enqueueTasks(inputs)
        if (added > 0) {
            taskState.requireQueuePreparationBeforeRun = true
            messages.operationMessage = "已重试入队 $added 项"
            startQueueProcessing()
        } else {
            messages.operationMessage = "当前筛选下没有失败项可重试"
        }
    }

    fun visibleStateSummary(): EpisodeStateSummary {
        val visible = visibleEpisodes()
        var idle = 0
        var queued = 0
        var running = 0
        var success = 0
        var failed = 0
        var skipped = 0
        var canceled = 0
        visible.forEach { episode ->
            when (episodeStore.stateOf(episode)) {
                EpisodeDownloadState.Idle -> idle++
                EpisodeDownloadState.Queued -> queued++
                EpisodeDownloadState.Running -> running++
                EpisodeDownloadState.Success -> success++
                EpisodeDownloadState.Failed -> failed++
                EpisodeDownloadState.Skipped -> skipped++
                EpisodeDownloadState.Canceled -> canceled++
            }
        }
        return EpisodeStateSummary(
            total = visible.size,
            idle = idle,
            queued = queued,
            running = running,
            success = success,
            failed = failed,
            skipped = skipped,
            canceled = canceled
        )
    }

    fun sourceOptions(): List<String> {
        return state.episodeCandidates
            .map { it.source }
            .distinct()
            .sortedBy { it.lowercase() }
    }

    fun visibleEpisodes(): List<DownloadEpisodeCandidate> {
        val filter = state.sourceFilter
        if (filter.isNullOrBlank()) return state.episodeCandidates
        return state.episodeCandidates.filter { it.source == filter }
    }

    fun episodeUiState(episode: DownloadEpisodeCandidate): EpisodeDownloadUiState {
        return episodeStore.uiState(episode)
    }

    fun startDownloadSelectedEpisodes(): Boolean {
        if (state.isSearching || state.isLoadingEpisodes) return false

        val anime = state.currentAnime ?: run {
            messages.errorMessage = "请先选择动漫"
            return false
        }
        if (settings().saveTreeUri.isBlank() && settings().saveDirPath.isBlank()) {
            messages.errorMessage = "请先到设置-弹幕下载中配置保存目录"
            return false
        }

        val selectedList = visibleEpisodes().filter { state.selectedEpisodeIds.contains(it.episodeId) }
        if (selectedList.isEmpty()) {
            messages.errorMessage = "请至少选择一集"
            return false
        }

        val apiBase = resolveApiBaseUrl()
        if (apiBase == null) {
            messages.errorMessage = "弹幕源地址无效"
            return false
        }

        val inputs = selectedList.map { episode ->
            DanmuDownloadInput(
                apiBaseUrl = apiBase,
                animeTitle = anime.title,
                episodeTitle = episode.title,
                episodeId = episode.episodeId,
                episodeNo = episode.episodeNumber,
                source = episode.source,
                format = state.selectedFormat,
                fileNameTemplate = state.fileNameTemplate,
                conflictPolicy = settings().policy(),
                animeId = anime.animeId,
                mediaType = com.example.danmuapiapp.domain.model.LocalDanmuType.fromDescription(anime.typeDescription)?.wire.orEmpty(),
                directUrl = episode.directUrl
            )
        }
        messages.errorMessage = null
        val added = downloadRepository.enqueueTasks(inputs)
        episodeStore.replaceAll(
            episodeStore.states.toMutableMap().apply {
                selectedList.forEach { episode ->
                    if (!containsKey(episode.episodeId)) {
                        this[episode.episodeId] = EpisodeDownloadUiState(
                            state = EpisodeDownloadState.Queued,
                            progress = 0f,
                            detail = "排队中"
                        )
                    }
                }
            }
        )
        messages.operationMessage = if (added > 0) {
            "已加入队列 $added 集，开始执行下载"
        } else {
            "所选剧集已在队列中，继续执行队列"
        }
        if (!taskState.isDownloading) startQueueProcessing()
        return true
    }
}
