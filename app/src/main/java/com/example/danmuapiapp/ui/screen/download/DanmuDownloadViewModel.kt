package com.example.danmuapiapp.ui.screen.download

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.data.util.RuntimeUrlParser
import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import com.example.danmuapiapp.domain.model.DanmuDownloadInput
import com.example.danmuapiapp.domain.model.DanmuDownloadResult
import com.example.danmuapiapp.domain.model.DanmuDownloadTask
import com.example.danmuapiapp.domain.model.DownloadConflictPolicy
import com.example.danmuapiapp.domain.model.DownloadQueueStatus
import com.example.danmuapiapp.domain.model.DownloadRecordStatus
import com.example.danmuapiapp.domain.model.DownloadThrottlePreset
import com.example.danmuapiapp.domain.repository.DanmuDownloadRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import kotlin.math.min
import kotlin.random.Random
import javax.inject.Inject

data class DownloadAnimeCandidate(
    val animeId: Long,
    val title: String,
    val episodeCount: Int
)

data class DownloadEpisodeCandidate(
    val episodeId: Long,
    val episodeNumber: Int,
    val title: String,
    val source: String
)

enum class EpisodeDownloadState {
    Idle,
    Queued,
    Running,
    Success,
    Failed,
    Skipped,
    Canceled
}

data class EpisodeDownloadUiState(
    val state: EpisodeDownloadState = EpisodeDownloadState.Idle,
    val progress: Float = 0f,
    val detail: String = ""
)

data class EpisodeStateSummary(
    val total: Int = 0,
    val idle: Int = 0,
    val queued: Int = 0,
    val running: Int = 0,
    val success: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
    val canceled: Int = 0
) {
    val unfinished: Int
        get() = total - success - skipped
}

data class DownloadQueueSummary(
    val total: Int = 0,
    val pending: Int = 0,
    val running: Int = 0,
    val success: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
    val canceled: Int = 0
) {
    val active: Int
        get() = pending + running
}

data class AnimeQueueEpisodeItem(
    val taskId: Long,
    val episodeId: Long,
    val episodeNo: Int,
    val episodeTitle: String,
    val source: String,
    val status: DownloadQueueStatus,
    val detail: String,
    val updatedAt: Long
)

data class AnimeQueueGroup(
    val animeTitle: String,
    val total: Int,
    val completed: Int,
    val pending: Int,
    val running: Int,
    val success: Int,
    val failed: Int,
    val skipped: Int,
    val canceled: Int,
    val runningEpisodeNo: Int? = null,
    val detail: String = "",
    val latestUpdatedAt: Long = 0L,
    val episodes: List<AnimeQueueEpisodeItem> = emptyList()
) {
    val progress: Float
        get() = if (total <= 0) 0f else completed.toFloat() / total.toFloat()
}

private data class RateLimitBypassSession(
    val originalValue: String,
    val bypassApplied: Boolean
)

@HiltViewModel
class DanmuDownloadViewModel @Inject constructor(
    runtimeRepository: RuntimeRepository,
    private val downloadRepository: DanmuDownloadRepository,
    private val httpClient: OkHttpClient
) : ViewModel() {
    companion object {
        private const val RATE_LIMIT_ENV_KEY = "RATE_LIMIT_MAX_REQUESTS"
        private const val RATE_LIMIT_BYPASS_VALUE = "0"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    val runtimeState = runtimeRepository.runtimeState
    val settings = downloadRepository.settings
    val records = downloadRepository.records
    val queueTasks = downloadRepository.queueTasks

    var sourceBase by mutableStateOf("")
        private set

    var keyword by mutableStateOf("")
        private set

    var selectedFormat by mutableStateOf(DanmuDownloadFormat.Xml)
        private set

    var fileNameTemplate by mutableStateOf("")
        private set

    var sourceFilter by mutableStateOf<String?>(null)
        private set

    var isSearching by mutableStateOf(false)
        private set

    var isLoadingEpisodes by mutableStateOf(false)
        private set

    var isDownloading by mutableStateOf(false)
        private set

    var hasSearchedAnime by mutableStateOf(false)
        private set

    var animeCandidates by mutableStateOf<List<DownloadAnimeCandidate>>(emptyList())
        private set

    var currentAnime by mutableStateOf<DownloadAnimeCandidate?>(null)
        private set

    var episodeCandidates by mutableStateOf<List<DownloadEpisodeCandidate>>(emptyList())
        private set

    var selectedEpisodeIds by mutableStateOf<Set<Long>>(emptySet())
        private set

    var episodeStates by mutableStateOf<Map<Long, EpisodeDownloadUiState>>(emptyMap())
        private set

    var overallProgress by mutableStateOf(0f)
        private set

    var progressSummary by mutableStateOf("等待开始")
        private set

    var operationMessage by mutableStateOf<String?>(null)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var activeTaskId by mutableStateOf<Long?>(null)
        private set

    var activeTaskAnimeTitle by mutableStateOf("")
        private set

    var activeTaskEpisodeNo by mutableStateOf<Int?>(null)
        private set

    var activeTaskSource by mutableStateOf("")
        private set

    var activeTaskDetail by mutableStateOf("")
        private set

    var activeTaskProgress by mutableStateOf(0f)
        private set

    var throttleHint by mutableStateOf<String?>(null)
        private set

    private var cancelRequested by mutableStateOf(false)
    private var requireQueuePreparationBeforeRun by mutableStateOf(false)
    private val random = Random(System.currentTimeMillis())

    init {
        sourceBase = RuntimeUrlParser.extractBase(runtimeState.value.lanUrl)
        val recovered = downloadRepository.markRunningTasksAsPending()
        if (recovered > 0) {
            operationMessage = "检测到上次未完成任务，已恢复到队列（$recovered）"
        }
        viewModelScope.launch {
            settings.collect { config ->
                selectedFormat = config.format()
                fileNameTemplate = config.fileNameTemplate
            }
        }
    }

    fun updateSourceBase(value: String) {
        sourceBase = value
    }

    fun updateKeyword(value: String) {
        keyword = value
    }

    fun useLocalBase() {
        sourceBase = RuntimeUrlParser.extractBase(runtimeState.value.localUrl)
    }

    fun useLanBase() {
        sourceBase = RuntimeUrlParser.extractBase(runtimeState.value.lanUrl)
    }

    fun updateFormat(format: DanmuDownloadFormat) {
        selectedFormat = format
        downloadRepository.setDefaultFormat(format)
    }

    fun updateFileNameTemplate(value: String) {
        fileNameTemplate = value
    }

    fun useDefaultTemplate() {
        fileNameTemplate = settings.value.fileNameTemplate
    }

    fun dismissMessage() {
        operationMessage = null
    }

    fun clearError() {
        errorMessage = null
    }

    fun clearRecords() {
        downloadRepository.clearRecords()
        operationMessage = "下载记录已清空"
    }

    fun queueSummary(): DownloadQueueSummary {
        val tasks = queueTasks.value
        var pending = 0
        var running = 0
        var success = 0
        var failed = 0
        var skipped = 0
        var canceled = 0
        tasks.forEach { task ->
            when (task.statusEnum()) {
                DownloadQueueStatus.Pending -> pending++
                DownloadQueueStatus.Running -> running++
                DownloadQueueStatus.Success -> success++
                DownloadQueueStatus.Failed -> failed++
                DownloadQueueStatus.Skipped -> skipped++
                DownloadQueueStatus.Canceled -> canceled++
            }
        }
        if (activeTaskId != null && running == 0 && pending > 0) {
            running = 1
            pending -= 1
        }
        return DownloadQueueSummary(
            total = tasks.size,
            pending = pending,
            running = running,
            success = success,
            failed = failed,
            skipped = skipped,
            canceled = canceled
        )
    }

    fun queueCompletedCount(): Int {
        return queueTasks.value.count { task ->
            when (task.statusEnum()) {
                DownloadQueueStatus.Success,
                DownloadQueueStatus.Failed,
                DownloadQueueStatus.Skipped,
                DownloadQueueStatus.Canceled -> true

                DownloadQueueStatus.Pending,
                DownloadQueueStatus.Running -> false
            }
        }
    }

    fun queueRunningStatusText(): String {
        if (activeTaskId != null) {
            val source = activeTaskSource.ifBlank { "unknown" }
            val episodeText = activeTaskEpisodeNo?.let { "第${it}集" } ?: "当前集"
            val pct = (activeTaskProgress * 100f).toInt().coerceIn(0, 100)
            val baseDetail = activeTaskDetail.ifBlank { "下载中" }
            val detail = when {
                pct > 0 && !baseDetail.contains("%") && !baseDetail.contains("等待") -> "$baseDetail ${pct}%"
                else -> baseDetail
            }
            return "${activeTaskAnimeTitle} · $episodeText · $source · $detail"
        }
        val running = queueTasks.value.firstOrNull { it.statusEnum() == DownloadQueueStatus.Running }
            ?: return "当前无运行中的任务"
        val source = running.source.ifBlank { "unknown" }
        val detail = running.lastDetail.ifBlank { "下载中" }
        return "${running.animeTitle} · 第${running.episodeNo}集 · $source · $detail"
    }

    fun animeQueueGroups(): List<AnimeQueueGroup> {
        val grouped = queueTasks.value.groupBy { task ->
            task.animeTitle.trim().ifBlank { "未命名剧集" }
        }
        return grouped.map { (animeTitle, tasks) ->
            var pending = 0
            var running = 0
            var success = 0
            var failed = 0
            var skipped = 0
            var canceled = 0
            tasks.forEach { task ->
                when (task.statusEnum()) {
                    DownloadQueueStatus.Pending -> pending++
                    DownloadQueueStatus.Running -> running++
                    DownloadQueueStatus.Success -> success++
                    DownloadQueueStatus.Failed -> failed++
                    DownloadQueueStatus.Skipped -> skipped++
                    DownloadQueueStatus.Canceled -> canceled++
                }
            }
            val runningTask = tasks
                .filter { it.statusEnum() == DownloadQueueStatus.Running }
                .maxByOrNull { it.updatedAt }
            val latest = tasks.maxByOrNull { it.updatedAt }
            val isActiveAnime = activeTaskId != null && activeTaskAnimeTitle == animeTitle
            val displayRunning = if (isActiveAnime && running == 0) 1 else running
            val displayPending = if (isActiveAnime && running == 0 && pending > 0) pending - 1 else pending
            val displayEpisodeNo = if (isActiveAnime) activeTaskEpisodeNo else runningTask?.episodeNo
            val displayDetail = if (isActiveAnime) {
                val pct = (activeTaskProgress * 100f).toInt().coerceIn(0, 100)
                val base = activeTaskDetail.ifBlank { runningTask?.lastDetail ?: latest?.lastDetail.orEmpty() }
                if (pct > 0 && !base.contains("%") && !base.contains("等待")) "$base ${pct}%" else base
            } else {
                runningTask?.lastDetail ?: latest?.lastDetail.orEmpty()
            }
            val episodeItems = tasks
                .sortedWith(
                    compareBy<DanmuDownloadTask> { it.episodeNo }
                        .thenBy { it.source.lowercase() }
                        .thenBy { it.episodeId }
                        .thenBy { it.createdAt }
                )
                .map { task ->
                    val rawStatus = task.statusEnum()
                    val isActiveTask = activeTaskId != null && activeTaskId == task.taskId
                    val displayStatus = if (isActiveTask && rawStatus == DownloadQueueStatus.Pending) {
                        DownloadQueueStatus.Running
                    } else {
                        rawStatus
                    }
                    val taskDetail = if (isActiveTask) {
                        val pct = (activeTaskProgress * 100f).toInt().coerceIn(0, 100)
                        val base = activeTaskDetail.ifBlank { task.lastDetail }
                        if (pct > 0 && !base.contains("%") && !base.contains("等待")) "$base ${pct}%" else base
                    } else {
                        task.lastDetail
                    }
                    AnimeQueueEpisodeItem(
                        taskId = task.taskId,
                        episodeId = task.episodeId,
                        episodeNo = task.episodeNo,
                        episodeTitle = task.episodeTitle,
                        source = task.source,
                        status = displayStatus,
                        detail = taskDetail,
                        updatedAt = task.updatedAt
                    )
                }
            AnimeQueueGroup(
                animeTitle = animeTitle,
                total = tasks.size,
                completed = success + failed + skipped + canceled,
                pending = displayPending.coerceAtLeast(0),
                running = displayRunning,
                success = success,
                failed = failed,
                skipped = skipped,
                canceled = canceled,
                runningEpisodeNo = displayEpisodeNo,
                detail = displayDetail,
                latestUpdatedAt = latest?.updatedAt ?: 0L,
                episodes = episodeItems
            )
        }.sortedBy { group ->
            // Preserve queue order: use the index of the first task of each group
            val firstTask = queueTasks.value.indexOfFirst {
                it.animeTitle.trim().ifBlank { "未命名剧集" } == group.animeTitle
            }
            if (firstTask < 0) Int.MAX_VALUE else firstTask
        }
    }

    fun resumePendingQueue() {
        if (isDownloading) return
        val pending = queueTasks.value.count { it.statusEnum() == DownloadQueueStatus.Pending }
        if (pending <= 0) {
            operationMessage = "当前没有待处理任务"
            return
        }
        requireQueuePreparationBeforeRun = true
        processPendingQueue()
    }

    fun clearQueueTasks() {
        if (isDownloading) {
            operationMessage = "下载进行中，无法清空队列"
            return
        }
        downloadRepository.clearQueueTasks()
        operationMessage = "队列已清空"
    }

    fun clearCompletedQueueTasks() {
        if (isDownloading) {
            operationMessage = "下载进行中，无法清理已完成任务"
            return
        }
        val removed = downloadRepository.clearCompletedQueueTasks()
        operationMessage = if (removed > 0) "已清理 $removed 个已完成任务" else "没有可清理的已完成任务"
    }

    fun retryFailedQueueTasks() {
        if (isDownloading) {
            operationMessage = "下载进行中，无法重试失败项"
            return
        }
        val failedTaskIds = queueTasks.value
            .filter { task -> task.statusEnum() == DownloadQueueStatus.Failed }
            .map { it.taskId }
            .toSet()
        if (failedTaskIds.isEmpty()) {
            operationMessage = "队列中没有失败项"
            return
        }
        val reset = downloadRepository.resetQueueTasks(failedTaskIds, detail = "等待重试")
        if (reset > 0) {
            operationMessage = "已重试入队 $reset 项"
            processPendingQueue()
        } else {
            operationMessage = "失败项重试入队失败"
        }
    }

    fun pauseDownload() {
        if (!isDownloading) {
            operationMessage = "当前没有正在执行的下载"
            return
        }
        cancelRequested = true
        progressSummary = "正在暂停..."
    }

    fun moveQueueGroupUp(animeTitle: String) {
        if (isDownloading) {
            operationMessage = "下载进行中，请先暂停再调整顺序"
            return
        }
        val tasks = queueTasks.value
        val grouped = tasks.groupBy { it.animeTitle.trim().ifBlank { "未命名剧集" } }
            .entries.toMutableList()
        val idx = grouped.indexOfFirst { it.key == animeTitle }
        if (idx <= 0) return
        val tmp = grouped[idx]
        grouped[idx] = grouped[idx - 1]
        grouped[idx - 1] = tmp
        downloadRepository.reorderQueueTasks(grouped.flatMap { it.value })
    }

    fun moveQueueGroupDown(animeTitle: String) {
        if (isDownloading) {
            operationMessage = "下载进行中，请先暂停再调整顺序"
            return
        }
        val tasks = queueTasks.value
        val grouped = tasks.groupBy { it.animeTitle.trim().ifBlank { "未命名剧集" } }
            .entries.toMutableList()
        val idx = grouped.indexOfFirst { it.key == animeTitle }
        if (idx < 0 || idx >= grouped.size - 1) return
        val tmp = grouped[idx]
        grouped[idx] = grouped[idx + 1]
        grouped[idx + 1] = tmp
        downloadRepository.reorderQueueTasks(grouped.flatMap { it.value })
    }

    fun backToAnimeList() {
        currentAnime = null
        episodeCandidates = emptyList()
        selectedEpisodeIds = emptySet()
        episodeStates = emptyMap()
        sourceFilter = null
    }

    fun selectSourceFilter(value: String?) {
        sourceFilter = value
        selectedEpisodeIds = selectedEpisodeIds.intersect(visibleEpisodes().map { it.episodeId }.toSet())
    }

    fun toggleEpisodeSelection(episodeId: Long) {
        selectedEpisodeIds = if (selectedEpisodeIds.contains(episodeId)) {
            selectedEpisodeIds - episodeId
        } else {
            selectedEpisodeIds + episodeId
        }
    }

    fun toggleSelectAllVisible() {
        val visibleIds = visibleEpisodes().map { it.episodeId }.toSet()
        if (visibleIds.isEmpty()) return
        val allSelected = visibleIds.all { selectedEpisodeIds.contains(it) }
        selectedEpisodeIds = if (allSelected) {
            selectedEpisodeIds - visibleIds
        } else {
            selectedEpisodeIds + visibleIds
        }
    }

    fun clearSelection() {
        selectedEpisodeIds = emptySet()
    }

    fun selectFailedVisibleEpisodes() {
        if (isDownloading) return
        val failedIds = visibleEpisodes().filter { episode ->
            stateOfEpisode(episode) == EpisodeDownloadState.Failed
        }.map { it.episodeId }.toSet()
        selectedEpisodeIds = failedIds
        operationMessage = if (failedIds.isEmpty()) "当前筛选下没有失败项" else "已选择 ${failedIds.size} 个失败项"
    }

    fun selectUnfinishedVisibleEpisodes() {
        if (isDownloading) return
        val ids = visibleEpisodes().filter { episode ->
            when (stateOfEpisode(episode)) {
                EpisodeDownloadState.Success, EpisodeDownloadState.Skipped -> false
                else -> true
            }
        }.map { it.episodeId }.toSet()
        selectedEpisodeIds = ids
        operationMessage = if (ids.isEmpty()) "当前筛选下没有未完成项" else "已选择 ${ids.size} 个未完成项"
    }

    fun retryFailedVisibleEpisodes() {
        if (isDownloading || isSearching || isLoadingEpisodes) return
        val visibleEpisodeIds = visibleEpisodes().map { it.episodeId }.toSet()
        val failedTaskIds = queueTasks.value
            .filter { task ->
                visibleEpisodeIds.contains(task.episodeId) && task.statusEnum() == DownloadQueueStatus.Failed
            }
            .map { it.taskId }
            .toSet()
        if (failedTaskIds.isNotEmpty()) {
            val reset = downloadRepository.resetQueueTasks(failedTaskIds, detail = "等待重试")
            if (reset > 0) {
                operationMessage = "已重试入队 $reset 项"
                processPendingQueue()
            } else {
                operationMessage = "失败项重试入队失败"
            }
            return
        }

        val anime = currentAnime
        val apiBase = resolveApiBaseUrl()
        val failedEpisodes = visibleEpisodes().filter { episode ->
            stateOfEpisode(episode) == EpisodeDownloadState.Failed
        }
        if (anime == null || apiBase == null || failedEpisodes.isEmpty()) {
            operationMessage = "当前筛选下没有失败项可重试"
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
                format = selectedFormat,
                fileNameTemplate = fileNameTemplate,
                conflictPolicy = settings.value.policy()
            )
        }
        val added = downloadRepository.enqueueTasks(inputs)
        if (added > 0) {
            operationMessage = "已重试入队 $added 项"
            processPendingQueue()
        } else {
            operationMessage = "当前筛选下没有失败项可重试"
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
            when (stateOfEpisode(episode)) {
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
        return episodeCandidates
            .map { it.source }
            .distinct()
            .sortedBy { it.lowercase() }
    }

    fun visibleEpisodes(): List<DownloadEpisodeCandidate> {
        val filter = sourceFilter
        if (filter.isNullOrBlank()) return episodeCandidates
        return episodeCandidates.filter { it.source == filter }
    }

    fun searchAnime() {
        if (isSearching || isLoadingEpisodes) return

        val apiBase = resolveApiBaseUrl()
        if (apiBase == null) {
            errorMessage = "弹幕源地址无效"
            return
        }

        val q = keyword.trim()
        if (q.isBlank()) {
            errorMessage = "请输入搜索关键词"
            return
        }

        val url = "$apiBase/api/v2/search/anime?keyword=${urlEncode(q)}"
        viewModelScope.launch {
            isSearching = true
            errorMessage = null
            val result = withContext(Dispatchers.IO) { requestGet(url) }
            hasSearchedAnime = true
            result.fold(
                onSuccess = { (code, body) ->
                    if (code in 200..299) {
                        animeCandidates = parseAnimeCandidates(body)
                        if (animeCandidates.isEmpty()) {
                            operationMessage = "未搜索到匹配动漫"
                        } else {
                            operationMessage = "已搜索到 ${animeCandidates.size} 个动漫"
                        }
                        currentAnime = null
                        episodeCandidates = emptyList()
                        selectedEpisodeIds = emptySet()
                        episodeStates = emptyMap()
                        sourceFilter = null
                    } else {
                        errorMessage = "搜索失败：HTTP $code"
                        animeCandidates = emptyList()
                    }
                },
                onFailure = {
                    errorMessage = it.message ?: "搜索失败"
                    animeCandidates = emptyList()
                }
            )
            isSearching = false
        }
    }

    fun openAnimeDetail(anime: DownloadAnimeCandidate) {
        if (isSearching || isLoadingEpisodes) return

        val apiBase = resolveApiBaseUrl()
        if (apiBase == null) {
            errorMessage = "弹幕源地址无效"
            return
        }

        viewModelScope.launch {
            isLoadingEpisodes = true
            errorMessage = null
            currentAnime = anime
            episodeCandidates = emptyList()
            selectedEpisodeIds = emptySet()
            episodeStates = emptyMap()
            sourceFilter = null
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
                            queueTasksSnapshot = queueTasks.value,
                            recordsSnapshot = records.value
                        )
                    }
                    episodeCandidates = episodes
                    episodeStates = initialStates
                    operationMessage = if (episodes.isEmpty()) {
                        "该动漫暂无可下载剧集"
                    } else {
                        "共加载 ${episodes.size} 集"
                    }
                },
                onFailure = { throwable ->
                    errorMessage = throwable.message ?: "加载剧集失败"
                    episodeCandidates = emptyList()
                }
            )
            isLoadingEpisodes = false
        }
    }

    fun cancelDownload() {
        if (!isDownloading) return
        cancelRequested = true
        progressSummary = "正在取消下载..."
    }

    fun startDownloadSelectedEpisodes() {
        if (isDownloading || isSearching || isLoadingEpisodes) return

        val anime = currentAnime ?: run {
            errorMessage = "请先选择动漫"
            return
        }
        if (settings.value.saveTreeUri.isBlank()) {
            errorMessage = "请先到设置-弹幕下载中配置保存目录"
            return
        }

        val selectedList = visibleEpisodes().filter { selectedEpisodeIds.contains(it.episodeId) }
        if (selectedList.isEmpty()) {
            errorMessage = "请至少选择一集"
            return
        }

        val apiBase = resolveApiBaseUrl()
        if (apiBase == null) {
            errorMessage = "弹幕源地址无效"
            return
        }

        val inputs = selectedList.map { episode ->
            DanmuDownloadInput(
                apiBaseUrl = apiBase,
                animeTitle = anime.title,
                episodeTitle = episode.title,
                episodeId = episode.episodeId,
                episodeNo = episode.episodeNumber,
                source = episode.source,
                format = selectedFormat,
                fileNameTemplate = fileNameTemplate,
                conflictPolicy = settings.value.policy()
            )
        }
        val added = downloadRepository.enqueueTasks(inputs)
        episodeStates = episodeStates.toMutableMap().apply {
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
        operationMessage = if (added > 0) {
            "已加入队列 $added 集，开始执行下载"
        } else {
            "所选剧集已在队列中，继续执行队列"
        }
        processPendingQueue()
    }

    private fun processPendingQueue() {
        if (isDownloading || isSearching || isLoadingEpisodes) return
        if (settings.value.saveTreeUri.isBlank()) {
            errorMessage = "请先到设置-弹幕下载中配置保存目录"
            return
        }
        val pendingCount = queueTasks.value.count { it.statusEnum() == DownloadQueueStatus.Pending }
        if (pendingCount <= 0) {
            operationMessage = "当前没有待处理任务"
            return
        }

        viewModelScope.launch {
            isDownloading = true
            cancelRequested = false
            val rateLimitBypassSession = prepareRateLimitBypassForDownload()
            var summaryMessage: String? = null
            val prepareChainForThisRun = requireQueuePreparationBeforeRun
            requireQueuePreparationBeforeRun = false
            val throttle = settings.value.throttle()
            val sourceCooldownUntil = mutableMapOf<String, Long>()
            val sourceBackoffLevel = mutableMapOf<String, Int>()
            var processed = 0
            var success = 0
            var failed = 0
            var skipped = 0
            progressSummary = "队列执行中：待处理 $pendingCount 集 · 流控${throttle.label}"

            try {
                while (true) {
                if (cancelRequested) break
                val task = queueTasks.value.firstOrNull { it.statusEnum() == DownloadQueueStatus.Pending } ?: break
                setActiveTask(
                    taskId = task.taskId,
                    animeTitle = task.animeTitle,
                    episodeNo = task.episodeNo,
                    source = task.source,
                    detail = "准备下载",
                    progress = 0f
                )
                downloadRepository.setQueueTaskStatus(
                    taskId = task.taskId,
                    status = DownloadQueueStatus.Running,
                    detail = "准备下载",
                    incrementAttempt = true
                )
                updateEpisodeState(
                    episodeId = task.episodeId,
                    state = EpisodeDownloadState.Running,
                    progress = 0f,
                    detail = "准备下载"
                )
                var downloadInput = task.toInput()
                if (prepareChainForThisRun) {
                    val prepareDetail = "准备下载中：优先尝试原链路，失效后自动重建"
                    updateActiveTask(detail = prepareDetail, progress = 0f)
                    downloadRepository.setQueueTaskStatus(
                        taskId = task.taskId,
                        status = DownloadQueueStatus.Running,
                        detail = prepareDetail
                    )
                    updateEpisodeState(
                        episodeId = task.episodeId,
                        state = EpisodeDownloadState.Running,
                        progress = 0f,
                        detail = prepareDetail
                    )
                }
                val sourceKey = sourceKey(task.source)
                val sourceWait = (sourceCooldownUntil[sourceKey] ?: 0L) - System.currentTimeMillis()
                if (sourceWait > 0L && !cancelRequested) {
                    val waitDetail = "来源限流等待 ${sourceWait / 1000}s"
                    updateActiveTask(detail = waitDetail, progress = 0f)
                    downloadRepository.setQueueTaskStatus(
                        taskId = task.taskId,
                        status = DownloadQueueStatus.Running,
                        detail = waitDetail
                    )
                    updateEpisodeState(
                        episodeId = task.episodeId,
                        state = EpisodeDownloadState.Running,
                        progress = 0f,
                        detail = waitDetail
                    )
                    progressSummary = "来源 ${task.source.ifBlank { "unknown" }} 限流等待 ${sourceWait / 1000}s"
                    throttleHint = "流控中：来源限流等待 ${sourceWait / 1000}s，请耐心等待"
                    interruptibleDelay(sourceWait)
                    throttleHint = null
                }
                if (cancelRequested) {
                    revertTaskToPending(task)
                    break
                }
                if (processed > 0 && !cancelRequested) {
                    val reqWait = throttle.baseDelayMs + nextJitterMs(throttle.jitterMaxMs)
                    if (reqWait > 0L) {
                        val waitDetail = "节流等待 ${reqWait}ms"
                        updateActiveTask(detail = waitDetail, progress = 0f)
                        downloadRepository.setQueueTaskStatus(
                            taskId = task.taskId,
                            status = DownloadQueueStatus.Running,
                            detail = waitDetail
                        )
                        updateEpisodeState(
                            episodeId = task.episodeId,
                            state = EpisodeDownloadState.Running,
                            progress = 0f,
                            detail = waitDetail
                        )
                        progressSummary = "流控等待 ${reqWait}ms 后继续"
                        interruptibleDelay(reqWait)
                    }
                }
                if (cancelRequested) {
                    revertTaskToPending(task)
                    break
                }
                // 单任务自动重试：首次失败后最多重试两次。
                val maxAutoRetry = 2
                var retryCount = 0
                var finalResult: Result<DanmuDownloadResult>? = null
                var staleRebuildDone = false

                while (!cancelRequested) {
                    val attemptNo = retryCount + 1
                    val totalAttempts = maxAutoRetry + 1
                    val startDetail = if (attemptNo == 1) {
                        "开始下载"
                    } else {
                        "开始重试（$attemptNo/$totalAttempts）"
                    }
                    downloadRepository.setQueueTaskStatus(
                        taskId = task.taskId,
                        status = DownloadQueueStatus.Running,
                        detail = startDetail,
                        incrementAttempt = attemptNo > 1
                    )
                    updateActiveTask(
                        detail = startDetail,
                        progress = 0f
                    )
                    updateEpisodeState(
                        episodeId = task.episodeId,
                        state = EpisodeDownloadState.Running,
                        progress = 0f,
                        detail = startDetail
                    )

                    val attemptResult = withContext(Dispatchers.IO) {
                        downloadRepository.downloadEpisode(downloadInput) { progress, detail ->
                            viewModelScope.launch {
                                updateActiveTask(
                                    detail = detail,
                                    progress = progress.coerceIn(0f, 1f)
                                )
                                updateEpisodeState(
                                    episodeId = task.episodeId,
                                    state = EpisodeDownloadState.Running,
                                    progress = progress.coerceIn(0f, 1f),
                                    detail = detail
                                )
                            }
                        }
                    }
                    finalResult = attemptResult

                    val canRetry = retryCount < maxAutoRetry
                    val shouldRetry = attemptResult.fold(
                        onSuccess = { output ->
                            output.status == DownloadRecordStatus.Failed && canRetry
                        },
                        onFailure = {
                            canRetry
                        }
                    )
                    if (!shouldRetry) {
                        break
                    }

                    val rawDetail = attemptResult.fold(
                        onSuccess = { output ->
                            output.errorMessage ?: "下载失败"
                        },
                        onFailure = { throwable ->
                            throwable.message ?: "下载失败"
                        }
                    )
                    val failureHttpCode = attemptResult.fold(
                        onSuccess = { output -> output.httpCode ?: extractHttpCodeFromDetail(rawDetail) },
                        onFailure = { extractHttpCodeFromDetail(rawDetail) }
                    )
                    if (
                        prepareChainForThisRun &&
                        !staleRebuildDone &&
                        shouldRebuildChainForStaleFailure(failureHttpCode, rawDetail)
                    ) {
                        val rebuildingDetail = "检测到旧链路可能失效，正在重建弹幕链路"
                        updateActiveTask(detail = rebuildingDetail, progress = 0f)
                        downloadRepository.setQueueTaskStatus(
                            taskId = task.taskId,
                            status = DownloadQueueStatus.Running,
                            detail = rebuildingDetail
                        )
                        updateEpisodeState(
                            episodeId = task.episodeId,
                            state = EpisodeDownloadState.Running,
                            progress = 0f,
                            detail = rebuildingDetail
                        )
                        val rebuiltResult = rebuildQueueTaskInput(task)
                        if (rebuiltResult.isSuccess) {
                            val rebuiltInput = rebuiltResult.getOrThrow()
                            val oldEpisodeId = downloadInput.episodeId
                            downloadInput = rebuiltInput
                            staleRebuildDone = true
                            val rebuiltDetail = if (downloadInput.episodeId != oldEpisodeId) {
                                "链路重建完成：已刷新弹幕ID（$oldEpisodeId→${downloadInput.episodeId}）"
                            } else {
                                "链路重建完成：已刷新映射"
                            }
                            updateActiveTask(detail = rebuiltDetail, progress = 0f)
                            downloadRepository.setQueueTaskStatus(
                                taskId = task.taskId,
                                status = DownloadQueueStatus.Running,
                                detail = rebuiltDetail
                            )
                            updateEpisodeState(
                                episodeId = task.episodeId,
                                state = EpisodeDownloadState.Running,
                                progress = 0f,
                                detail = rebuiltDetail
                            )
                            retryCount++
                            continue
                        } else {
                            val rebuildFailDetail = "链路重建失败：${rebuiltResult.exceptionOrNull()?.message ?: "未知错误"}"
                            updateActiveTask(detail = rebuildFailDetail, progress = 0f)
                            downloadRepository.setQueueTaskStatus(
                                taskId = task.taskId,
                                status = DownloadQueueStatus.Running,
                                detail = rebuildFailDetail
                            )
                            updateEpisodeState(
                                episodeId = task.episodeId,
                                state = EpisodeDownloadState.Running,
                                progress = 0f,
                                detail = rebuildFailDetail
                            )
                        }
                    }
                    val shouldBackoffRetry = attemptResult.fold(
                        onSuccess = { output ->
                            shouldTriggerBackoff(output.httpCode, rawDetail)
                        },
                        onFailure = {
                            true
                        }
                    )
                    val nextAttemptNo = attemptNo + 1
                    val nextDetailPrefix = "下载失败，准备重试（$nextAttemptNo/$totalAttempts）"
                    val retryDetail = if (rawDetail.isNotBlank()) {
                        "$nextDetailPrefix：$rawDetail"
                    } else {
                        nextDetailPrefix
                    }

                    var backoffMs = 0L
                    if (shouldBackoffRetry) {
                        val level = (sourceBackoffLevel[sourceKey] ?: 0) + 1
                        sourceBackoffLevel[sourceKey] = level
                        backoffMs = backoffDelayMs(throttle, level)
                        sourceCooldownUntil[sourceKey] = System.currentTimeMillis() + backoffMs
                    }
                    val retryDetailWithBackoff = if (backoffMs > 0L) {
                        "$retryDetail（退避${backoffMs / 1000}s）"
                    } else {
                        retryDetail
                    }
                    updateActiveTask(
                        detail = retryDetailWithBackoff,
                        progress = 0f
                    )
                    downloadRepository.setQueueTaskStatus(
                        taskId = task.taskId,
                        status = DownloadQueueStatus.Running,
                        detail = retryDetailWithBackoff
                    )
                    updateEpisodeState(
                        episodeId = task.episodeId,
                        state = EpisodeDownloadState.Running,
                        progress = 0f,
                        detail = retryDetailWithBackoff
                    )
                    if (backoffMs > 0L && !cancelRequested) {
                        progressSummary = "来源 ${task.source.ifBlank { "unknown" }} 重试退避 ${backoffMs / 1000}s"
                        throttleHint = "流控中：下载失败自动重试，等待 ${backoffMs / 1000}s"
                        interruptibleDelay(backoffMs)
                        throttleHint = null
                    }
                    retryCount++
                }

                if (cancelRequested) {
                    revertTaskToPending(task)
                    break
                }

                processed++
                val result = finalResult ?: Result.failure<DanmuDownloadResult>(
                    IllegalStateException("下载任务执行异常")
                )
                result.fold(
                    onSuccess = { output ->
                        when (output.status) {
                            DownloadRecordStatus.Success -> {
                                success++
                                val detail = "已保存：${output.relativePath}"
                                updateActiveTask(
                                    detail = detail,
                                    progress = 1f
                                )
                                sourceBackoffLevel[sourceKey] = 0
                                sourceCooldownUntil.remove(sourceKey)
                                downloadRepository.setQueueTaskStatus(
                                    taskId = task.taskId,
                                    status = DownloadQueueStatus.Success,
                                    detail = detail
                                )
                                updateEpisodeState(
                                    episodeId = task.episodeId,
                                    state = EpisodeDownloadState.Success,
                                    progress = 1f,
                                    detail = detail
                                )
                            }

                            DownloadRecordStatus.Skipped -> {
                                skipped++
                                val detail = output.errorMessage ?: "已跳过"
                                updateActiveTask(
                                    detail = detail,
                                    progress = 1f
                                )
                                sourceBackoffLevel[sourceKey] = 0
                                sourceCooldownUntil.remove(sourceKey)
                                downloadRepository.setQueueTaskStatus(
                                    taskId = task.taskId,
                                    status = DownloadQueueStatus.Skipped,
                                    detail = detail
                                )
                                updateEpisodeState(
                                    episodeId = task.episodeId,
                                    state = EpisodeDownloadState.Skipped,
                                    progress = 1f,
                                    detail = detail
                                )
                            }

                            DownloadRecordStatus.Failed -> {
                                failed++
                                val rawDetail = output.errorMessage ?: "下载失败"
                                val shouldBackoff = shouldTriggerBackoff(output.httpCode, rawDetail)
                                val failPrefix = "重试${maxAutoRetry}次后仍失败："
                                val detail = if (shouldBackoff) {
                                    val level = (sourceBackoffLevel[sourceKey] ?: 0) + 1
                                    sourceBackoffLevel[sourceKey] = level
                                    val backoffMs = backoffDelayMs(throttle, level)
                                    sourceCooldownUntil[sourceKey] = System.currentTimeMillis() + backoffMs
                                    "$failPrefix$rawDetail（退避${backoffMs / 1000}s）"
                                } else {
                                    "$failPrefix$rawDetail"
                                }
                                updateActiveTask(
                                    detail = detail,
                                    progress = 1f
                                )
                                downloadRepository.setQueueTaskStatus(
                                    taskId = task.taskId,
                                    status = DownloadQueueStatus.Failed,
                                    detail = detail
                                )
                                updateEpisodeState(
                                    episodeId = task.episodeId,
                                    state = EpisodeDownloadState.Failed,
                                    progress = 1f,
                                    detail = detail
                                )
                            }
                        }
                    },
                    onFailure = { throwable ->
                        failed++
                        val rawDetail = throwable.message ?: "下载失败"
                        val failPrefix = "重试${maxAutoRetry}次后仍失败："
                        val level = (sourceBackoffLevel[sourceKey] ?: 0) + 1
                        sourceBackoffLevel[sourceKey] = level
                        val backoffMs = backoffDelayMs(throttle, level)
                        sourceCooldownUntil[sourceKey] = System.currentTimeMillis() + backoffMs
                        val detail = "$failPrefix$rawDetail（退避${backoffMs / 1000}s）"
                        updateActiveTask(
                            detail = detail,
                            progress = 1f
                        )
                        downloadRepository.setQueueTaskStatus(
                            taskId = task.taskId,
                            status = DownloadQueueStatus.Failed,
                            detail = detail
                        )
                        updateEpisodeState(
                            episodeId = task.episodeId,
                            state = EpisodeDownloadState.Failed,
                            progress = 1f,
                            detail = detail
                        )
                    }
                )

                val remaining = queueTasks.value.count { it.statusEnum() == DownloadQueueStatus.Pending }
                val totalForRun = processed + remaining
                overallProgress = if (totalForRun <= 0) 1f else processed.toFloat() / totalForRun.toFloat()
                progressSummary = "队列执行：已完成 $processed，待处理 $remaining（成功 $success，失败 $failed，跳过 $skipped）"

                if (!cancelRequested && remaining > 0 && throttle.batchSize > 0 && processed % throttle.batchSize == 0) {
                    val batchWait = throttle.batchRestMs + nextJitterMs(min(2000L, throttle.jitterMaxMs))
                    if (batchWait > 0L) {
                        progressSummary = "已完成 $processed 集，批次休息 ${batchWait / 1000}s"
                        throttleHint = "流控中：每 ${throttle.batchSize} 集休息 ${batchWait / 1000}s，防止被封禁"
                        interruptibleDelay(batchWait)
                        throttleHint = null
                    }
                }
            }

                val remain = queueTasks.value.count { it.statusEnum() == DownloadQueueStatus.Pending }
                if (cancelRequested) {
                    progressSummary = "队列已暂停，待处理 $remain 集"
                    summaryMessage = progressSummary
                } else {
                    progressSummary = "队列执行完成：成功 $success，失败 $failed，跳过 $skipped"
                    summaryMessage = progressSummary
                }
            } catch (throwable: Throwable) {
                val fallback = throwable.message ?: "队列执行失败"
                progressSummary = fallback
                summaryMessage = fallback
            } finally {
                val restoreError = restoreRateLimitBypassForDownload(rateLimitBypassSession)
                when {
                    !summaryMessage.isNullOrBlank() && !restoreError.isNullOrBlank() -> {
                        operationMessage = "${summaryMessage}（$restoreError）"
                    }
                    !summaryMessage.isNullOrBlank() -> {
                        operationMessage = summaryMessage
                    }
                    !restoreError.isNullOrBlank() -> {
                        operationMessage = restoreError
                    }
                }
                clearActiveTask()
                throttleHint = null
                isDownloading = false
            }
        }
    }

    private fun sourceKey(raw: String): String {
        return raw.trim().ifBlank { "unknown" }.lowercase()
    }

    private suspend fun interruptibleDelay(totalMs: Long) {
        val step = 300L
        var remaining = totalMs
        while (remaining > 0L && !cancelRequested) {
            delay(min(step, remaining))
            remaining -= step
        }
    }

    private fun revertTaskToPending(task: com.example.danmuapiapp.domain.model.DanmuDownloadTask) {
        downloadRepository.setQueueTaskStatus(
            taskId = task.taskId,
            status = DownloadQueueStatus.Pending,
            detail = "等待恢复"
        )
        updateEpisodeState(
            episodeId = task.episodeId,
            state = EpisodeDownloadState.Queued,
            progress = 0f,
            detail = "等待恢复"
        )
        clearActiveTask()
    }

    private suspend fun prepareRateLimitBypassForDownload(): RateLimitBypassSession? {
        val originalValue = fetchRateLimitEnvValue().getOrElse { return null }
        val normalized = originalValue.trim()
        if (normalized == RATE_LIMIT_BYPASS_VALUE) {
            return RateLimitBypassSession(
                originalValue = normalized,
                bypassApplied = false
            )
        }
        val setResult = setRateLimitEnvValue(RATE_LIMIT_BYPASS_VALUE)
        if (setResult.isFailure) {
            return null
        }
        return RateLimitBypassSession(
            originalValue = originalValue,
            bypassApplied = true
        )
    }

    private suspend fun restoreRateLimitBypassForDownload(
        session: RateLimitBypassSession?
    ): String? {
        if (session == null || !session.bypassApplied) return null
        val restoreValue = session.originalValue
        val result = setRateLimitEnvValue(restoreValue)
        return result.exceptionOrNull()?.message
    }

    private suspend fun fetchRateLimitEnvValue(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(buildLocalControlApiUrl("/api/config"))
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                val raw = response.body.string()
                if (!response.isSuccessful) {
                    val fallback = "读取 $RATE_LIMIT_ENV_KEY 失败：HTTP ${response.code}"
                    val message = runCatching {
                        JSONObject(raw).optString("message").trim().ifBlank { fallback }
                    }.getOrDefault(fallback)
                    error(message)
                }
                val root = if (raw.isBlank()) JSONObject() else JSONObject(raw)
                val value = extractEnvValueFromConfig(root, RATE_LIMIT_ENV_KEY)
                    ?: error("读取 $RATE_LIMIT_ENV_KEY 失败：配置项不存在")
                value
            }
        }
    }

    private suspend fun setRateLimitEnvValue(value: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = JSONObject()
                .put("key", RATE_LIMIT_ENV_KEY)
                .put("value", value)
                .toString()
                .toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(buildLocalControlApiUrl("/api/env/set"))
                .post(payload)
                .build()
            httpClient.newCall(request).execute().use { response ->
                val raw = response.body.string()
                val fallback = "设置 $RATE_LIMIT_ENV_KEY 失败：HTTP ${response.code}"
                if (!response.isSuccessful) {
                    val message = runCatching {
                        JSONObject(raw).optString("message").trim().ifBlank { fallback }
                    }.getOrDefault(fallback)
                    error(message)
                }
                if (raw.isBlank()) return@use
                val root = runCatching { JSONObject(raw) }.getOrNull() ?: return@use
                if (root.has("success") && !root.optBoolean("success", false)) {
                    val message = root.optString("message").trim().ifBlank { "设置 $RATE_LIMIT_ENV_KEY 失败" }
                    error(message)
                }
            }
        }
    }

    private fun buildLocalControlApiUrl(path: String): String {
        val state = runtimeState.value
        val token = state.token.trim().trim('/')
        val tokenPath = if (token.isBlank()) "" else "/$token"
        return "http://127.0.0.1:${state.port}$tokenPath$path"
    }

    private fun extractEnvValueFromConfig(root: JSONObject, key: String): String? {
        val original = root.optJSONObject("originalEnvVars")?.opt(key)
        envValueToString(original)?.let { return it }

        val envs = root.optJSONObject("envs")?.opt(key)
        envValueToString(envs)?.let { return it }

        return null
    }

    private fun envValueToString(raw: Any?): String? {
        if (raw == null || raw == JSONObject.NULL) return null
        return when (raw) {
            is JSONObject -> {
                val inner = raw.opt("value")
                if (inner == null || inner == JSONObject.NULL) null else inner.toString()
            }
            is Number -> raw.toString()
            is Boolean -> raw.toString()
            else -> raw.toString()
        }?.trim()
    }

    private fun nextJitterMs(maxMs: Long): Long {
        if (maxMs <= 0L) return 0L
        val bound = (maxMs + 1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return random.nextInt(bound).toLong()
    }

    private fun backoffDelayMs(preset: DownloadThrottlePreset, level: Int): Long {
        var delayMs = preset.backoffBaseMs.coerceAtLeast(0L)
        val rounds = (level - 1).coerceAtLeast(0).coerceAtMost(10)
        repeat(rounds) {
            delayMs = (delayMs * 2L).coerceAtMost(preset.backoffMaxMs)
        }
        delayMs = (delayMs + nextJitterMs(min(1500L, preset.jitterMaxMs))).coerceAtMost(preset.backoffMaxMs)
        return delayMs.coerceAtLeast(1000L)
    }

    private fun shouldTriggerBackoff(httpCode: Int?, detail: String): Boolean {
        if (httpCode == 429 || httpCode == 403 || httpCode == 408) return true
        if (httpCode != null && httpCode in 500..599) return true

        val text = detail.lowercase()
        return text.contains("timeout") ||
            text.contains("timed out") ||
            text.contains("连接超时") ||
            text.contains("请求超时") ||
            text.contains("unable to resolve host") ||
            text.contains("connection reset") ||
            text.contains("429")
    }

    private fun extractHttpCodeFromDetail(detail: String): Int? {
        val matched = Regex("http\\s*([0-9]{3})", RegexOption.IGNORE_CASE).find(detail)
        return matched?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun shouldRebuildChainForStaleFailure(httpCode: Int?, detail: String): Boolean {
        if (httpCode == 400 || httpCode == 404 || httpCode == 410 || httpCode == 422) {
            return true
        }
        val text = detail.lowercase()
        if (httpCode == 500 && (
                text.contains("invalid") ||
                    text.contains("无效") ||
                    text.contains("不存在") ||
                    text.contains("not found") ||
                    text.contains("episode")
                )
        ) {
            return true
        }
        return text.contains("missing or invalid") ||
            text.contains("参数错误") ||
            text.contains("episodeid") ||
            text.contains("弹幕数据为空") ||
            text.contains("资源不存在")
    }

    private fun setActiveTask(
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

    private fun updateActiveTask(
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

    private fun clearActiveTask() {
        activeTaskId = null
        activeTaskAnimeTitle = ""
        activeTaskEpisodeNo = null
        activeTaskSource = ""
        activeTaskDetail = ""
        activeTaskProgress = 0f
    }

    private fun updateEpisodeState(
        episodeId: Long,
        state: EpisodeDownloadState,
        progress: Float,
        detail: String
    ) {
        val old = episodeStates[episodeId]
        val next = (old ?: EpisodeDownloadUiState()).copy(
            state = state,
            progress = progress,
            detail = detail
        )
        episodeStates = episodeStates + (episodeId to next)
    }

    fun episodeUiState(episode: DownloadEpisodeCandidate): EpisodeDownloadUiState {
        return episodeStates[episode.episodeId] ?: EpisodeDownloadUiState()
    }

    private fun stateOfEpisode(episode: DownloadEpisodeCandidate): EpisodeDownloadState {
        return episodeStates[episode.episodeId]?.state ?: EpisodeDownloadState.Idle
    }

    private fun latestQueueTaskForEpisode(episode: DownloadEpisodeCandidate): DanmuDownloadTask? {
        val animeKey = normalizeAnimeTitleForMatch(currentAnime?.title.orEmpty())
        val sourceKey = canonicalSourceKey(episode.source)
        val episodeTitleKey = normalizeEpisodeTitleForMatch(episode.title)
        return queueTasks.value
            .asSequence()
            .filter { task ->
                val taskAnimeKey = normalizeAnimeTitleForMatch(task.animeTitle)
                val animeMatches = animeKey.isBlank() || taskAnimeKey == animeKey
                val sourceMatches = sourceKey == "unknown" || canonicalSourceKey(task.source) == sourceKey
                val numberMatches = task.episodeNo == episode.episodeNumber
                val idMatches = task.episodeId == episode.episodeId
                val titleMatches = episodeTitleKey.isNotBlank() &&
                    normalizeEpisodeTitleForMatch(task.episodeTitle) == episodeTitleKey
                animeMatches && sourceMatches && (numberMatches || idMatches || titleMatches)
            }
            .maxByOrNull { it.updatedAt }
    }

    private fun latestRecordForEpisode(episode: DownloadEpisodeCandidate): com.example.danmuapiapp.domain.model.DanmuDownloadRecord? {
        val animeKey = normalizeAnimeTitleForMatch(currentAnime?.title.orEmpty())
        val sourceKey = canonicalSourceKey(episode.source)
        val episodeTitleKey = normalizeEpisodeTitleForMatch(episode.title)
        return records.value
            .asSequence()
            .filter { record ->
                val recordAnimeKey = normalizeAnimeTitleForMatch(record.animeTitle)
                val animeMatches = animeKey.isBlank() || recordAnimeKey == animeKey
                val sourceMatches = sourceKey == "unknown" || canonicalSourceKey(record.source) == sourceKey
                val numberMatches = record.episodeNo == episode.episodeNumber
                val idMatches = record.episodeId == episode.episodeId
                val titleMatches = episodeTitleKey.isNotBlank() &&
                    normalizeEpisodeTitleForMatch(record.episodeTitle) == episodeTitleKey
                animeMatches && sourceMatches && (numberMatches || idMatches || titleMatches)
            }
            .maxByOrNull { it.createdAt }
    }

    private fun requestGet(url: String): Result<Pair<Int, String>> {
        return runCatching {
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                response.code to response.body.string()
            }
        }
    }

    private fun parseAnimeCandidates(raw: String): List<DownloadAnimeCandidate> {
        val root = runCatching { JSONObject(raw) }.getOrElse { return emptyList() }
        val arr = root.optJSONArray("animes") ?: root.optJSONArray("data") ?: JSONArray()
        val out = ArrayList<DownloadAnimeCandidate>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val animeId = readLong(item, "animeId", "id")
            val title = readString(item, "animeTitle", "title", "name")
            if (animeId <= 0L || title.isBlank()) continue
            val count = readInt(item, "episodeCount", "totalEpisodes", "count").coerceAtLeast(0)
            out += DownloadAnimeCandidate(
                animeId = animeId,
                title = title,
                episodeCount = count
            )
        }
        return out.distinctBy { it.animeId }
    }

    private fun parseEpisodeCandidates(raw: String, fallbackSource: String = ""): List<DownloadEpisodeCandidate> {
        val root = runCatching { JSONObject(raw) }.getOrElse { return emptyList() }
        val bangumi = root.optJSONObject("bangumi")
            ?: root.optJSONObject("data")
            ?: JSONObject()
        val episodes = bangumi.optJSONArray("episodes")
            ?: root.optJSONArray("episodes")
            ?: JSONArray()

        val out = ArrayList<DownloadEpisodeCandidate>(episodes.length())
        for (i in 0 until episodes.length()) {
            val item = episodes.optJSONObject(i) ?: continue
            val episodeId = readLong(item, "episodeId", "id", "cid")
            if (episodeId <= 0L) continue
            val number = readInt(item, "episodeNumber", "number", "sort", "index")
                .takeIf { it > 0 } ?: (i + 1)
            val rawTitle = readString(item, "episodeTitle", "title", "name")
            val parsedSource = parseSource(rawTitle)
            val source = if (parsedSource == "unknown") {
                fallbackSource.ifBlank { "unknown" }
            } else {
                parsedSource
            }
            val title = stripSourceTag(rawTitle).ifBlank { "第${number}集" }
            out += DownloadEpisodeCandidate(
                episodeId = episodeId,
                episodeNumber = number,
                title = title,
                source = source
            )
        }
        val dedupByEpisode = LinkedHashMap<String, DownloadEpisodeCandidate>(out.size)
        out.forEach { episode ->
            val titleKey = normalizeEpisodeTitleForMatch(episode.title)
            val key = if (titleKey.isNotBlank()) {
                "${episode.episodeNumber}|$titleKey"
            } else {
                "id-${episode.episodeId}"
            }
            dedupByEpisode.putIfAbsent(key, episode)
        }
        return dedupByEpisode.values
            .sortedWith(compareBy<DownloadEpisodeCandidate> { it.episodeNumber }.thenBy { it.episodeId })
    }

    private fun buildInitialEpisodeStates(
        animeTitle: String,
        episodes: List<DownloadEpisodeCandidate>,
        queueTasksSnapshot: List<DanmuDownloadTask>,
        recordsSnapshot: List<com.example.danmuapiapp.domain.model.DanmuDownloadRecord>
    ): Map<Long, EpisodeDownloadUiState> {
        if (episodes.isEmpty()) return emptyMap()
        val animeKey = normalizeAnimeTitleForMatch(animeTitle)
        val filteredQueue = queueTasksSnapshot.filter { task ->
            animeKey.isBlank() || normalizeAnimeTitleForMatch(task.animeTitle) == animeKey
        }
        val filteredRecords = recordsSnapshot.filter { record ->
            animeKey.isBlank() || normalizeAnimeTitleForMatch(record.animeTitle) == animeKey
        }
        val stateMap = LinkedHashMap<Long, EpisodeDownloadUiState>(episodes.size)
        episodes.forEach { episode ->
            val sourceKey = canonicalSourceKey(episode.source)
            val titleKey = normalizeEpisodeTitleForMatch(episode.title)
            val queueTask = filteredQueue
                .asSequence()
                .filter { task ->
                    val sourceMatches = sourceKey == "unknown" || canonicalSourceKey(task.source) == sourceKey
                    val numberMatches = task.episodeNo == episode.episodeNumber
                    val idMatches = task.episodeId == episode.episodeId
                    val titleMatches = titleKey.isNotBlank() &&
                        normalizeEpisodeTitleForMatch(task.episodeTitle) == titleKey
                    sourceMatches && (numberMatches || idMatches || titleMatches)
                }
                .maxByOrNull { it.updatedAt }
            if (queueTask != null) {
                val mappedState = when (queueTask.statusEnum()) {
                    DownloadQueueStatus.Pending -> EpisodeDownloadState.Queued
                    DownloadQueueStatus.Running -> EpisodeDownloadState.Running
                    DownloadQueueStatus.Success -> EpisodeDownloadState.Success
                    DownloadQueueStatus.Failed -> EpisodeDownloadState.Failed
                    DownloadQueueStatus.Skipped -> EpisodeDownloadState.Skipped
                    DownloadQueueStatus.Canceled -> EpisodeDownloadState.Canceled
                }
                val progress = when (mappedState) {
                    EpisodeDownloadState.Success,
                    EpisodeDownloadState.Failed,
                    EpisodeDownloadState.Skipped,
                    EpisodeDownloadState.Canceled -> 1f
                    EpisodeDownloadState.Running -> 0.15f
                    EpisodeDownloadState.Queued,
                    EpisodeDownloadState.Idle -> 0f
                }
                stateMap[episode.episodeId] = EpisodeDownloadUiState(
                    state = mappedState,
                    progress = progress,
                    detail = queueTask.lastDetail
                )
                return@forEach
            }
            val record = filteredRecords
                .asSequence()
                .filter { record ->
                    val sourceMatches = sourceKey == "unknown" || canonicalSourceKey(record.source) == sourceKey
                    val numberMatches = record.episodeNo == episode.episodeNumber
                    val idMatches = record.episodeId == episode.episodeId
                    val titleMatches = titleKey.isNotBlank() &&
                        normalizeEpisodeTitleForMatch(record.episodeTitle) == titleKey
                    sourceMatches && (numberMatches || idMatches || titleMatches)
                }
                .maxByOrNull { it.createdAt }
            if (record != null) {
                val mappedState = when (record.statusEnum()) {
                    DownloadRecordStatus.Success -> EpisodeDownloadState.Success
                    DownloadRecordStatus.Failed -> EpisodeDownloadState.Failed
                    DownloadRecordStatus.Skipped -> EpisodeDownloadState.Skipped
                }
                stateMap[episode.episodeId] = EpisodeDownloadUiState(
                    state = mappedState,
                    progress = 1f,
                    detail = record.relativePath.ifBlank { record.errorMessage ?: "" }
                )
            }
        }
        return stateMap
    }

    private fun parseSource(rawTitle: String): String {
        val match = Regex("^\\s*【([^】]+)】\\s*").find(rawTitle)
        return match?.groupValues?.getOrNull(1)?.trim().orEmpty().ifBlank { "unknown" }
    }

    private fun stripSourceTag(rawTitle: String): String {
        return rawTitle.replace(Regex("^\\s*【[^】]+】\\s*"), "").trim()
    }

    private fun normalizeAnimeTitleForMatch(raw: String): String {
        if (raw.isBlank()) return ""
        val noFrom = raw.replace(Regex("\\s*from\\s+.*$", RegexOption.IGNORE_CASE), "")
        val noType = noFrom.replace(Regex("【[^】]*】"), "")
        val noYear = noType.replace(Regex("[（(]\\d{4}[)）]"), "")
        return noYear
            .replace(Regex("[\\s\\p{Punct}　【】（）()\\[\\]「」]"), "")
            .lowercase()
            .trim()
    }

    private fun normalizeEpisodeTitleForMatch(raw: String): String {
        if (raw.isBlank()) return ""
        return stripSourceTag(raw)
            .replace(Regex("[\\s\\p{Punct}　【】（）()\\[\\]「」]"), "")
            .lowercase()
            .trim()
    }

    private fun extractSourceFromAnimeTitle(raw: String): String {
        val matched = Regex("from\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE).find(raw)
        return matched?.groupValues?.getOrNull(1)
            ?.trim()
            ?.trim('【', '】', '[', ']')
            .orEmpty()
    }

    private fun canonicalSourceKey(raw: String): String {
        val key = raw.trim()
            .replace(Regex("^from\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
            .lowercase()
        if (key.isBlank()) return "unknown"
        return when (key) {
            "qq", "tencent" -> "tencent"
            "bilibili", "bilibili1", "bili", "b23" -> "bilibili"
            "iqiyi", "qiyi" -> "iqiyi"
            "imgo", "mango", "mgtv", "hunantv" -> "imgo"
            "douyin", "xigua" -> "xigua"
            else -> key
        }
    }

    private fun extractAnimeKeywordForSearch(rawAnimeTitle: String): String {
        val noFrom = rawAnimeTitle.replace(Regex("\\s*from\\s+.*$", RegexOption.IGNORE_CASE), "")
        val noType = noFrom.replace(Regex("【[^】]*】"), "")
        val noYear = noType.replace(Regex("[（(]\\d{4}[)）]"), "")
        val keyword = noYear.trim()
        return if (keyword.isNotBlank()) keyword else noFrom.trim()
    }

    private fun pickEpisodeForTask(task: DanmuDownloadTask, episodes: List<DownloadEpisodeCandidate>): DownloadEpisodeCandidate? {
        if (episodes.isEmpty()) return null
        val taskSource = canonicalSourceKey(task.source)
        val sameSourceEpisodes = if (taskSource == "unknown") {
            episodes
        } else {
            episodes.filter { canonicalSourceKey(it.source) == taskSource }
        }
        val taskTitleKey = normalizeEpisodeTitleForMatch(task.episodeTitle)
        val pool = if (sameSourceEpisodes.isNotEmpty()) sameSourceEpisodes else episodes
        pool.firstOrNull { it.episodeNumber == task.episodeNo }?.let { return it }
        if (taskTitleKey.isNotBlank()) {
            pool.firstOrNull { normalizeEpisodeTitleForMatch(it.title) == taskTitleKey }?.let { return it }
        }
        episodes.firstOrNull { it.episodeNumber == task.episodeNo }?.let { return it }
        if (taskTitleKey.isNotBlank()) {
            episodes.firstOrNull { normalizeEpisodeTitleForMatch(it.title) == taskTitleKey }?.let { return it }
        }
        return null
    }

    private suspend fun rebuildQueueTaskInput(task: DanmuDownloadTask): Result<DanmuDownloadInput> {
        return runCatching {
            val apiBase = resolveApiBaseUrl() ?: task.apiBaseUrl.trim().ifBlank {
                error("弹幕源地址无效")
            }
            val keyword = extractAnimeKeywordForSearch(task.animeTitle)
            if (keyword.isBlank()) {
                error("剧名为空，无法重建链路")
            }
            val searchUrl = "$apiBase/api/v2/search/anime?keyword=${urlEncode(keyword)}"
            val searchResp = withContext(Dispatchers.IO) { requestGet(searchUrl) }.getOrElse { throw it }
            val searchCode = searchResp.first
            if (searchCode !in 200..299) {
                error("搜索失败：HTTP $searchCode")
            }
            val animes = parseAnimeCandidates(searchResp.second)
            if (animes.isEmpty()) {
                error("未搜索到匹配动漫")
            }

            val taskAnimeKey = normalizeAnimeTitleForMatch(task.animeTitle)
            val taskSourceKey = canonicalSourceKey(
                task.source.ifBlank { extractSourceFromAnimeTitle(task.animeTitle) }
            )
            val exactMatches = animes.filter { candidate ->
                val titleMatches = normalizeAnimeTitleForMatch(candidate.title) == taskAnimeKey
                val sourceMatches = taskSourceKey == "unknown" ||
                    canonicalSourceKey(extractSourceFromAnimeTitle(candidate.title)) == taskSourceKey
                titleMatches && sourceMatches
            }
            val titleMatches = animes.filter { candidate ->
                normalizeAnimeTitleForMatch(candidate.title) == taskAnimeKey
            }
            val sourceMatches = animes.filter { candidate ->
                taskSourceKey != "unknown" &&
                    canonicalSourceKey(extractSourceFromAnimeTitle(candidate.title)) == taskSourceKey
            }
            val ordered = (exactMatches + titleMatches + sourceMatches + animes).distinctBy { it.animeId }

            for (candidate in ordered) {
                val bangumiUrl = "$apiBase/api/v2/bangumi/${candidate.animeId}"
                val bangumiResp = withContext(Dispatchers.IO) { requestGet(bangumiUrl) }.getOrElse { continue }
                val bangumiCode = bangumiResp.first
                if (bangumiCode !in 200..299) continue
                val episodes = parseEpisodeCandidates(
                    bangumiResp.second,
                    fallbackSource = extractSourceFromAnimeTitle(candidate.title)
                )
                val matchedEpisode = pickEpisodeForTask(task, episodes) ?: continue
                return@runCatching DanmuDownloadInput(
                    apiBaseUrl = apiBase,
                    animeTitle = candidate.title.ifBlank { task.animeTitle },
                    episodeTitle = matchedEpisode.title.ifBlank { task.episodeTitle },
                    episodeId = matchedEpisode.episodeId,
                    episodeNo = matchedEpisode.episodeNumber.takeIf { it > 0 } ?: task.episodeNo,
                    source = matchedEpisode.source.ifBlank { task.source },
                    format = DanmuDownloadFormat.fromValue(task.format),
                    fileNameTemplate = task.fileNameTemplate,
                    conflictPolicy = DownloadConflictPolicy.fromKey(task.conflictPolicy)
                )
            }
            error("未找到匹配剧集：第${task.episodeNo}集（${task.source.ifBlank { "unknown" }}）")
        }
    }

    private fun resolveApiBaseUrl(): String? {
        val raw = sourceBase.trim()
        if (raw.isBlank()) return null
        val normalized = ensureHttpPrefix(raw).trimEnd('/')
        val token = runtimeState.value.token.trim().trim('/')
        if (token.isBlank()) return normalized

        val uri = runCatching { URI(normalized) }.getOrNull() ?: return normalized
        val segments = uri.path
            ?.split('/')
            ?.filter { it.isNotBlank() }
            .orEmpty()
        if (segments.firstOrNull() == token) {
            return normalized
        }
        return "$normalized/$token"
    }

    private fun ensureHttpPrefix(url: String): String {
        if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
            return url
        }
        return "http://$url"
    }

    private fun readString(obj: JSONObject, vararg keys: String): String {
        keys.forEach { key ->
            val value = obj.optString(key, "").trim()
            if (value.isNotBlank() && !value.equals("null", true)) {
                return value
            }
        }
        return ""
    }

    private fun readInt(obj: JSONObject, vararg keys: String): Int {
        keys.forEach { key ->
            if (obj.has(key)) {
                val n = obj.optInt(key, Int.MIN_VALUE)
                if (n != Int.MIN_VALUE) return n
                obj.optString(key, "").trim().toIntOrNull()?.let { return it }
            }
        }
        return 0
    }

    private fun readLong(obj: JSONObject, vararg keys: String): Long {
        keys.forEach { key ->
            if (obj.has(key)) {
                val n = obj.optLong(key, Long.MIN_VALUE)
                if (n != Long.MIN_VALUE) return n
                obj.optString(key, "").trim().toLongOrNull()?.let { return it }
            }
        }
        return -1L
    }

    private fun urlEncode(raw: String): String {
        return URLEncoder.encode(raw, Charsets.UTF_8.name())
    }
}
