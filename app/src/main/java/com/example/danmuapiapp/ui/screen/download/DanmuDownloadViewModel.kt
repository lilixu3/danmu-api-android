package com.example.danmuapiapp.ui.screen.download

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.data.util.RuntimeUrlParser
import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import com.example.danmuapiapp.domain.model.DanmuDownloadInput
import com.example.danmuapiapp.domain.model.DanmuDownloadTask
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
import okhttp3.OkHttpClient
import okhttp3.Request
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
    val latestUpdatedAt: Long = 0L
) {
    val progress: Float
        get() = if (total <= 0) 0f else completed.toFloat() / total.toFloat()
}

@HiltViewModel
class DanmuDownloadViewModel @Inject constructor(
    runtimeRepository: RuntimeRepository,
    private val downloadRepository: DanmuDownloadRepository,
    private val httpClient: OkHttpClient
) : ViewModel() {

    val runtimeState = runtimeRepository.runtimeState
    val settings = downloadRepository.settings
    val records = downloadRepository.records
    val queueTasks = downloadRepository.queueTasks

    var sourceBase by mutableStateOf("")
        private set

    var keyword by mutableStateOf("")
        private set

    var selectedFormat by mutableStateOf(DanmuDownloadFormat.Json)
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

    private var cancelRequested by mutableStateOf(false)
    private var downloadOptionInitialized by mutableStateOf(false)
    private val random = Random(System.currentTimeMillis())

    init {
        sourceBase = RuntimeUrlParser.extractBase(runtimeState.value.lanUrl)
        val recovered = downloadRepository.markRunningTasksAsPending()
        if (recovered > 0) {
            operationMessage = "检测到上次未完成任务，已恢复到队列（$recovered）"
        }
        viewModelScope.launch {
            settings.collect { config ->
                if (!downloadOptionInitialized) {
                    selectedFormat = config.format()
                    downloadOptionInitialized = true
                }
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
                latestUpdatedAt = latest?.updatedAt ?: 0L
            )
        }.sortedWith(
            compareByDescending<AnimeQueueGroup> { it.running > 0 }
                .thenByDescending { it.pending > 0 }
                .thenByDescending { it.latestUpdatedAt }
        )
    }

    fun resumePendingQueue() {
        if (isDownloading) return
        val pending = queueTasks.value.count { it.statusEnum() == DownloadQueueStatus.Pending }
        if (pending <= 0) {
            operationMessage = "当前没有待处理任务"
            return
        }
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

    fun backToAnimeList() {
        currentAnime = null
        episodeCandidates = emptyList()
        selectedEpisodeIds = emptySet()
        episodeStates = emptyMap()
        sourceFilter = null
        overallProgress = 0f
        progressSummary = "等待开始"
        cancelRequested = false
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
            stateOfEpisode(episode.episodeId) == EpisodeDownloadState.Failed
        }.map { it.episodeId }.toSet()
        selectedEpisodeIds = failedIds
        operationMessage = if (failedIds.isEmpty()) "当前筛选下没有失败项" else "已选择 ${failedIds.size} 个失败项"
    }

    fun selectUnfinishedVisibleEpisodes() {
        if (isDownloading) return
        val ids = visibleEpisodes().filter { episode ->
            when (stateOfEpisode(episode.episodeId)) {
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
            stateOfEpisode(episode.episodeId) == EpisodeDownloadState.Failed
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
            when (stateOfEpisode(episode.episodeId)) {
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
        if (isSearching || isLoadingEpisodes || isDownloading) return

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
        if (isSearching || isLoadingEpisodes || isDownloading) return

        val apiBase = resolveApiBaseUrl()
        if (apiBase == null) {
            errorMessage = "弹幕源地址无效"
            return
        }

        val url = "$apiBase/api/v2/bangumi/${anime.animeId}"
        viewModelScope.launch {
            isLoadingEpisodes = true
            errorMessage = null
            val result = withContext(Dispatchers.IO) { requestGet(url) }
            result.fold(
                onSuccess = { (code, body) ->
                    if (code in 200..299) {
                        val episodes = parseEpisodeCandidates(body)
                        currentAnime = anime
                        episodeCandidates = episodes
                        selectedEpisodeIds = emptySet()
                        episodeStates = emptyMap()
                        sourceFilter = null
                        overallProgress = 0f
                        progressSummary = "等待开始"
                        operationMessage = if (episodes.isEmpty()) {
                            "该动漫暂无可下载剧集"
                        } else {
                            "共加载 ${episodes.size} 集，可选择后下载"
                        }
                    } else {
                        errorMessage = "加载剧集失败：HTTP $code"
                        currentAnime = null
                        episodeCandidates = emptyList()
                    }
                },
                onFailure = {
                    errorMessage = it.message ?: "加载剧集失败"
                    currentAnime = null
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
            val throttle = settings.value.throttle()
            val sourceCooldownUntil = mutableMapOf<String, Long>()
            val sourceBackoffLevel = mutableMapOf<String, Int>()
            var processed = 0
            var success = 0
            var failed = 0
            var skipped = 0
            progressSummary = "队列执行中：待处理 $pendingCount 集 · 流控${throttle.label}"

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
                val sourceKey = sourceKey(task.source)
                val sourceWait = (sourceCooldownUntil[sourceKey] ?: 0L) - System.currentTimeMillis()
                if (sourceWait > 0L) {
                    val waitDetail = "来源限流等待 ${sourceWait / 1000}s"
                    updateActiveTask(
                        detail = waitDetail,
                        progress = 0f
                    )
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
                    delay(sourceWait)
                }
                if (processed > 0) {
                    val reqWait = throttle.baseDelayMs + nextJitterMs(throttle.jitterMaxMs)
                    if (reqWait > 0L) {
                        val waitDetail = "节流等待 ${reqWait}ms"
                        updateActiveTask(
                            detail = waitDetail,
                            progress = 0f
                        )
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
                        delay(reqWait)
                    }
                }
                if (cancelRequested) {
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
                    break
                }
                downloadRepository.setQueueTaskStatus(
                    taskId = task.taskId,
                    status = DownloadQueueStatus.Running,
                    detail = "开始下载",
                    incrementAttempt = false
                )
                updateActiveTask(
                    detail = "开始下载",
                    progress = 0f
                )
                updateEpisodeState(
                    episodeId = task.episodeId,
                    state = EpisodeDownloadState.Running,
                    progress = 0f,
                    detail = "开始下载"
                )
                val result = withContext(Dispatchers.IO) {
                    downloadRepository.downloadEpisode(task.toInput()) { progress, detail ->
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

                processed++
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
                                val detail = if (shouldBackoff) {
                                    val level = (sourceBackoffLevel[sourceKey] ?: 0) + 1
                                    sourceBackoffLevel[sourceKey] = level
                                    val backoffMs = backoffDelayMs(throttle, level)
                                    sourceCooldownUntil[sourceKey] = System.currentTimeMillis() + backoffMs
                                    "$rawDetail（退避${backoffMs / 1000}s）"
                                } else {
                                    rawDetail
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
                        val level = (sourceBackoffLevel[sourceKey] ?: 0) + 1
                        sourceBackoffLevel[sourceKey] = level
                        val backoffMs = backoffDelayMs(throttle, level)
                        sourceCooldownUntil[sourceKey] = System.currentTimeMillis() + backoffMs
                        val detail = "$rawDetail（退避${backoffMs / 1000}s）"
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
                        delay(batchWait)
                    }
                }
            }

            val remain = queueTasks.value.count { it.statusEnum() == DownloadQueueStatus.Pending }
            if (cancelRequested) {
                progressSummary = "队列已暂停，待处理 $remain 集"
                operationMessage = progressSummary
            } else {
                progressSummary = "队列执行完成：成功 $success，失败 $failed，跳过 $skipped"
                operationMessage = progressSummary
            }
            clearActiveTask()
            isDownloading = false
        }
    }

    private fun sourceKey(raw: String): String {
        return raw.trim().ifBlank { "unknown" }.lowercase()
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

    fun episodeUiState(episodeId: Long): EpisodeDownloadUiState {
        episodeStates[episodeId]?.let { return it }
        val queueTask = latestQueueTaskOfEpisode(episodeId) ?: return EpisodeDownloadUiState()
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
        return EpisodeDownloadUiState(
            state = mappedState,
            progress = progress,
            detail = queueTask.lastDetail
        )
    }

    private fun stateOfEpisode(episodeId: Long): EpisodeDownloadState {
        return episodeUiState(episodeId).state
    }

    private fun latestQueueTaskOfEpisode(episodeId: Long): DanmuDownloadTask? {
        return queueTasks.value
            .asSequence()
            .filter { it.episodeId == episodeId }
            .maxByOrNull { it.updatedAt }
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

    private fun parseEpisodeCandidates(raw: String): List<DownloadEpisodeCandidate> {
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
            val source = parseSource(rawTitle)
            val title = stripSourceTag(rawTitle).ifBlank { "第${number}集" }
            out += DownloadEpisodeCandidate(
                episodeId = episodeId,
                episodeNumber = number,
                title = title,
                source = source
            )
        }
        return out
            .distinctBy { it.episodeId }
            .sortedWith(compareBy<DownloadEpisodeCandidate> { it.episodeNumber }.thenBy { it.episodeId })
    }

    private fun parseSource(rawTitle: String): String {
        val match = Regex("^\\s*【([^】]+)】\\s*").find(rawTitle)
        return match?.groupValues?.getOrNull(1)?.trim().orEmpty().ifBlank { "unknown" }
    }

    private fun stripSourceTag(rawTitle: String): String {
        return rawTitle.replace(Regex("^\\s*【[^】]+】\\s*"), "").trim()
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
