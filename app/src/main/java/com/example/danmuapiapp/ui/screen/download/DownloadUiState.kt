package com.example.danmuapiapp.ui.screen.download

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import com.example.danmuapiapp.domain.model.DanmuDownloadRecord
import com.example.danmuapiapp.domain.model.DanmuDownloadTask
import com.example.danmuapiapp.domain.model.DownloadDirectorySyncResult
import kotlin.random.Random

/**
 * 搜索页状态：来源地址、关键词、格式模板、候选动漫与剧集列表。
 *
 * 状态容器只保存 Compose 可变状态，读写规则留在各自的控制器里。
 */
internal class DownloadSearchUiState {
    var sourceBase by mutableStateOf("")
    var keyword by mutableStateOf("")
    var selectedFormat by mutableStateOf(DanmuDownloadFormat.Xml)
    var fileNameTemplate by mutableStateOf("")
    var sourceFilter by mutableStateOf<String?>(null)
    var isSearching by mutableStateOf(false)
    var isLoadingEpisodes by mutableStateOf(false)
    var hasSearchedAnime by mutableStateOf(false)
    var animeCandidates by mutableStateOf<List<DownloadAnimeCandidate>>(emptyList())
    var currentAnime by mutableStateOf<DownloadAnimeCandidate?>(null)
    var episodeCandidates by mutableStateOf<List<DownloadEpisodeCandidate>>(emptyList())
    var selectedEpisodeIds by mutableStateOf<Set<Long>>(emptySet())
}

/**
 * 剧集下载状态表：由队列与下载记录推导初始状态，并承载运行期进度。
 */
internal class DownloadEpisodeStateStore {
    var states by mutableStateOf<Map<Long, EpisodeDownloadUiState>>(emptyMap())
        private set

    fun replaceAll(next: Map<Long, EpisodeDownloadUiState>) {
        states = next
    }

    fun clear() {
        states = emptyMap()
    }

    fun uiState(episode: DownloadEpisodeCandidate): EpisodeDownloadUiState {
        return states[episode.episodeId] ?: EpisodeDownloadUiState()
    }

    fun stateOf(episode: DownloadEpisodeCandidate): EpisodeDownloadState {
        return states[episode.episodeId]?.state ?: EpisodeDownloadState.Idle
    }

    fun update(
        episodeId: Long,
        state: EpisodeDownloadState,
        progress: Float,
        detail: String
    ) {
        val old = states[episodeId]
        val next = (old ?: EpisodeDownloadUiState()).copy(
            state = state,
            progress = progress,
            detail = detail,
            downloadedBefore = old?.downloadedBefore == true || state == EpisodeDownloadState.Success,
            downloadedRecordCount = if (state == EpisodeDownloadState.Success) {
                maxOf(old?.downloadedRecordCount ?: 0, 1)
            } else {
                old?.downloadedRecordCount ?: 0
            },
            lastDownloadedAt = if (state == EpisodeDownloadState.Success) {
                System.currentTimeMillis()
            } else {
                old?.lastDownloadedAt
            }
        )
        states = states + (episodeId to next)
    }

    /** 按当前剧集与队列/记录快照重建状态表；缺剧集或缺动漫时保持原样。 */
    fun refreshFromSnapshots(
        anime: DownloadAnimeCandidate?,
        episodes: List<DownloadEpisodeCandidate>,
        queueTasks: List<DanmuDownloadTask>,
        records: List<DanmuDownloadRecord>
    ) {
        if (anime == null) return
        if (episodes.isEmpty()) return
        states = buildInitialEpisodeStates(
            animeTitle = anime.title,
            episodes = episodes,
            queueTasksSnapshot = queueTasks,
            recordsSnapshot = records,
            animeId = anime.animeId
        )
    }
}

/**
 * 队列执行状态：运行标记、取消请求、活动任务展示与流控随机源。
 */
internal class DownloadTaskRuntimeState {
    var isDownloading by mutableStateOf(false)
    var overallProgress by mutableFloatStateOf(0f)
    var activeTaskId by mutableStateOf<Long?>(null)
    var activeTaskAnimeTitle by mutableStateOf("")
    var activeTaskEpisodeNo by mutableStateOf<Int?>(null)
    var activeTaskSource by mutableStateOf("")
    var activeTaskDetail by mutableStateOf("")
    var activeTaskProgress by mutableFloatStateOf(0f)
    var cancelRequested by mutableStateOf(false)
    var cancelRemainingTasks = false
    var requireQueuePreparationBeforeRun by mutableStateOf(false)
    val random = Random(System.currentTimeMillis())

    fun setActiveTask(
        taskId: Long,
        animeTitle: String,
        episodeNo: Int,
        source: String,
        detail: String,
        progress: Float
    ) {
        activeTaskId = taskId
        activeTaskAnimeTitle = animeTitle
        activeTaskEpisodeNo = episodeNo
        activeTaskSource = source
        activeTaskDetail = detail
        activeTaskProgress = progress.coerceIn(0f, 1f)
    }

    fun updateActiveTask(
        detail: String? = null,
        progress: Float? = null
    ) {
        if (activeTaskId == null) return
        if (detail != null) {
            activeTaskDetail = detail
        }
        if (progress != null) {
            activeTaskProgress = progress.coerceIn(0f, 1f)
        }
    }

    fun clearActiveTask() {
        activeTaskId = null
        activeTaskAnimeTitle = ""
        activeTaskEpisodeNo = null
        activeTaskSource = ""
        activeTaskDetail = ""
        activeTaskProgress = 0f
    }
}

/**
 * 页面级瞬时提示：操作结果、错误、进度摘要与流控提示。
 */
internal class DownloadMessageState {
    var operationMessage by mutableStateOf<String?>(null)
    var errorMessage by mutableStateOf<String?>(null)
    var progressSummary by mutableStateOf("等待开始")
    var throttleHint by mutableStateOf<String?>(null)
}

/**
 * 下载记录页状态：整季刷新、缺失集、目录同步/删除进行中与弹幕预览弹窗。
 */
internal class DownloadRecordsUiState {
    var refreshingSeriesKey by mutableStateOf<String?>(null)
    var missingEpisodes by mutableStateOf<MissingEpisodesState?>(null)
    var lastSync by mutableStateOf<DownloadDirectorySyncResult?>(null)
    var isSyncingRecords by mutableStateOf(false)
    var isDeletingRecords by mutableStateOf(false)
    var previewDialogState by mutableStateOf(DanmuPreviewDialogState())
}
