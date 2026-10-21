package com.example.danmuapiapp.ui.screen.download

import com.example.danmuapiapp.domain.model.DanmuDownloadTask
import com.example.danmuapiapp.domain.model.DownloadQueueStatus
import com.example.danmuapiapp.domain.repository.DanmuDownloadRepository

/**
 * 下载队列视图与队列级操作：统计、分组、排序、清空、重试、暂停与取消。
 */
internal class DownloadQueueController(
    private val downloadRepository: DanmuDownloadRepository,
    private val queueTasks: () -> List<DanmuDownloadTask>,
    private val taskState: DownloadTaskRuntimeState,
    private val messages: DownloadMessageState,
    private val startQueueProcessing: () -> Unit,
    private val cancelPendingTasks: () -> Unit
) {

    fun queueSummary(): DownloadQueueSummary {
        val tasks = queueTasks()
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
        if (taskState.activeTaskId != null && running == 0 && pending > 0) {
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
        return queueTasks().count { task ->
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
        if (taskState.activeTaskId != null) {
            val source = taskState.activeTaskSource.ifBlank { "unknown" }
            val episodeText = taskState.activeTaskEpisodeNo?.let { "第${it}集" } ?: "当前集"
            val pct = (taskState.activeTaskProgress * 100f).toInt().coerceIn(0, 100)
            val baseDetail = taskState.activeTaskDetail.ifBlank { "下载中" }
            val detail = when {
                pct > 0 && !baseDetail.contains("%") && !baseDetail.contains("等待") -> "$baseDetail ${pct}%"
                else -> baseDetail
            }
            return "${taskState.activeTaskAnimeTitle} · $episodeText · $source · $detail"
        }
        val running = queueTasks().firstOrNull { it.statusEnum() == DownloadQueueStatus.Running }
            ?: return "当前无运行中的任务"
        val source = running.source.ifBlank { "unknown" }
        val detail = running.lastDetail.ifBlank { "下载中" }
        return "${running.animeTitle} · 第${running.episodeNo}集 · $source · $detail"
    }

    fun animeQueueGroups(): List<AnimeQueueGroup> {
        val grouped = queueTasks().groupBy { task ->
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
            val isActiveAnime = taskState.activeTaskId != null && taskState.activeTaskAnimeTitle == animeTitle
            val displayRunning = if (isActiveAnime && running == 0) 1 else running
            val displayPending = if (isActiveAnime && running == 0 && pending > 0) pending - 1 else pending
            val displayEpisodeNo = if (isActiveAnime) taskState.activeTaskEpisodeNo else runningTask?.episodeNo
            val displayDetail = if (isActiveAnime) {
                val pct = (taskState.activeTaskProgress * 100f).toInt().coerceIn(0, 100)
                val base = taskState.activeTaskDetail.ifBlank { runningTask?.lastDetail ?: latest?.lastDetail.orEmpty() }
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
                    val isActiveTask = taskState.activeTaskId != null && taskState.activeTaskId == task.taskId
                    val displayStatus = if (isActiveTask && rawStatus == DownloadQueueStatus.Pending) {
                        DownloadQueueStatus.Running
                    } else {
                        rawStatus
                    }
                    val taskDetail = if (isActiveTask) {
                        val pct = (taskState.activeTaskProgress * 100f).toInt().coerceIn(0, 100)
                        val base = taskState.activeTaskDetail.ifBlank { task.lastDetail }
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
            val firstTask = queueTasks().indexOfFirst {
                it.animeTitle.trim().ifBlank { "未命名剧集" } == group.animeTitle
            }
            if (firstTask < 0) Int.MAX_VALUE else firstTask
        }
    }

    fun resumePendingQueue() {
        if (taskState.isDownloading) return
        val pending = queueTasks().count { it.statusEnum() == DownloadQueueStatus.Pending }
        if (pending <= 0) {
            messages.operationMessage = "当前没有待处理任务"
            return
        }
        taskState.requireQueuePreparationBeforeRun = true
        startQueueProcessing()
    }

    fun clearQueueTasks() {
        if (taskState.isDownloading) {
            messages.operationMessage = "下载进行中，无法清空队列"
            return
        }
        downloadRepository.clearQueueTasks()
        messages.operationMessage = "队列已清空"
    }

    fun clearCompletedQueueTasks() {
        if (taskState.isDownloading) {
            messages.operationMessage = "下载进行中，无法清理已完成任务"
            return
        }
        val removed = downloadRepository.clearCompletedQueueTasks()
        messages.operationMessage = if (removed > 0) "已清理 $removed 个已完成任务" else "没有可清理的已完成任务"
    }

    fun retryFailedQueueTasks() {
        if (taskState.isDownloading) {
            messages.operationMessage = "下载进行中，无法重试失败项"
            return
        }
        val failedTaskIds = queueTasks()
            .filter { task -> task.statusEnum() == DownloadQueueStatus.Failed }
            .map { it.taskId }
            .toSet()
        if (failedTaskIds.isEmpty()) {
            messages.operationMessage = "队列中没有失败项"
            return
        }
        val reset = downloadRepository.resetQueueTasks(failedTaskIds, detail = "等待重试")
        if (reset > 0) {
            taskState.requireQueuePreparationBeforeRun = true
            messages.operationMessage = "已重试入队 $reset 项"
            startQueueProcessing()
        } else {
            messages.operationMessage = "失败项重试入队失败"
        }
    }

    fun pauseDownload() {
        taskState.cancelRemainingTasks = false
        if (!taskState.isDownloading) {
            messages.operationMessage = "当前没有正在执行的下载"
            return
        }
        taskState.cancelRequested = true
        messages.progressSummary = "正在暂停..."
    }

    fun moveQueueGroupUp(animeTitle: String) {
        if (taskState.isDownloading) {
            messages.operationMessage = "下载进行中，请先暂停再调整顺序"
            return
        }
        val tasks = queueTasks()
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
        if (taskState.isDownloading) {
            messages.operationMessage = "下载进行中，请先暂停再调整顺序"
            return
        }
        val tasks = queueTasks()
        val grouped = tasks.groupBy { it.animeTitle.trim().ifBlank { "未命名剧集" } }
            .entries.toMutableList()
        val idx = grouped.indexOfFirst { it.key == animeTitle }
        if (idx < 0 || idx >= grouped.size - 1) return
        val tmp = grouped[idx]
        grouped[idx] = grouped[idx + 1]
        grouped[idx + 1] = tmp
        downloadRepository.reorderQueueTasks(grouped.flatMap { it.value })
    }

    fun removeQueueGroup(title: String) {
        if (!taskState.isDownloading) downloadRepository.removeQueueTasks(queueTasks().filter { it.animeTitle == title }.map { it.taskId }.toSet())
    }

    fun cancelDownload() {
        taskState.cancelRemainingTasks = true
        if (!taskState.isDownloading) {
            cancelPendingTasks()
            messages.operationMessage = "已取消剩余任务，下载记录与文件保留"
            return
        }
        taskState.cancelRequested = true
        messages.progressSummary = "正在取消剩余任务..."
    }

}
