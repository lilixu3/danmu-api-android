package com.example.danmuapiapp.ui.screen.download

import com.example.danmuapiapp.domain.model.DanmuDownloadInput
import com.example.danmuapiapp.domain.model.DanmuDownloadResult
import com.example.danmuapiapp.domain.model.DanmuDownloadSettings
import com.example.danmuapiapp.domain.model.DanmuDownloadTask
import com.example.danmuapiapp.domain.model.DownloadQueueStatus
import com.example.danmuapiapp.domain.model.DownloadRecordStatus
import com.example.danmuapiapp.domain.model.DownloadThrottleConfig
import com.example.danmuapiapp.domain.repository.DanmuDownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * 队列执行器：串行消费待处理任务，负责自动重试、链路重建、退避流控与状态回写。
 *
 * 只读写状态容器与仓库；链路重建与限流旁路由调用方注入。
 */
internal class DownloadQueueRunner(
    private val scope: CoroutineScope,
    private val downloadRepository: DanmuDownloadRepository,
    private val settings: () -> DanmuDownloadSettings,
    private val queueTasks: () -> List<DanmuDownloadTask>,
    private val searchState: DownloadSearchUiState,
    private val taskState: DownloadTaskRuntimeState,
    private val episodeStore: DownloadEpisodeStateStore,
    private val messages: DownloadMessageState,
    private val rateLimitBypass: DownloadRateLimitBypass,
    private val rebuildTaskInput: suspend (DanmuDownloadTask) -> Result<DanmuDownloadInput>
) {

    /** 把待处理与运行中的任务统一标记为已取消，并同步剧集状态。 */
    fun cancelPendingTasks() {
        queueTasks().filter { it.statusEnum() in setOf(DownloadQueueStatus.Pending, DownloadQueueStatus.Running) }.forEach { task ->
            downloadRepository.setQueueTaskStatus(task.taskId, DownloadQueueStatus.Canceled, "已取消")
            episodeStore.update(task.episodeId, EpisodeDownloadState.Canceled, 0f, "已取消")
        }
    }

    fun processPendingQueue() {
        if (taskState.isDownloading || searchState.isSearching || searchState.isLoadingEpisodes) return
        if (settings().saveTreeUri.isBlank() && settings().saveDirPath.isBlank()) {
            messages.errorMessage = "请先到设置-弹幕下载中配置保存目录"
            return
        }
        val pendingCount = queueTasks().count { it.statusEnum() == DownloadQueueStatus.Pending }
        if (pendingCount <= 0) {
            messages.operationMessage = "当前没有待处理任务"
            return
        }

        scope.launch {
            taskState.isDownloading = true
            taskState.cancelRequested = false
            taskState.cancelRemainingTasks = false
            messages.errorMessage = null
            val rateLimitBypassSession = rateLimitBypass.prepare()
            var summaryMessage: String? = null
            val prepareChainForThisRun = taskState.requireQueuePreparationBeforeRun
            taskState.requireQueuePreparationBeforeRun = false
            val throttle = settings().throttleConfig()
            val sourceCooldownUntil = mutableMapOf<String, Long>()
            val sourceBackoffLevel = mutableMapOf<String, Int>()
            var processed = 0
            var success = 0
            var failed = 0
            var skipped = 0
            messages.progressSummary = "队列执行中：待处理 $pendingCount 集 · 流控${throttle.label}"

            try {
                while (true) {
                if (taskState.cancelRequested) break
                val task = queueTasks().firstOrNull { it.statusEnum() == DownloadQueueStatus.Pending } ?: break
                taskState.setActiveTask(
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
                episodeStore.update(
                    episodeId = task.episodeId,
                    state = EpisodeDownloadState.Running,
                    progress = 0f,
                    detail = "准备下载"
                )
                var downloadInput = task.toInput()
                var activeEpisodeId = task.episodeId
                var taskSourceKey = sourceKey(task.source)
                if (prepareChainForThisRun) {
                    val prepareDetail = "准备下载中：优先尝试原链路，失效后自动重建"
                    taskState.updateActiveTask(detail = prepareDetail, progress = 0f)
                    downloadRepository.setQueueTaskStatus(
                        taskId = task.taskId,
                        status = DownloadQueueStatus.Running,
                        detail = prepareDetail
                    )
                    episodeStore.update(
                        episodeId = activeEpisodeId,
                        state = EpisodeDownloadState.Running,
                        progress = 0f,
                        detail = prepareDetail
                    )
                }
                val persistedRetryWait = task.retryNotBeforeAt - System.currentTimeMillis()
                if (persistedRetryWait > 0L && !taskState.cancelRequested) {
                    val waitSeconds = (persistedRetryWait / 1000L).coerceAtLeast(1L)
                    val waitDetail = "上次失败退避等待 ${waitSeconds}s"
                    taskState.updateActiveTask(detail = waitDetail, progress = 0f)
                    downloadRepository.setQueueTaskStatus(
                        taskId = task.taskId,
                        status = DownloadQueueStatus.Running,
                        detail = waitDetail
                    )
                    episodeStore.update(
                        episodeId = activeEpisodeId,
                        state = EpisodeDownloadState.Running,
                        progress = 0f,
                        detail = waitDetail
                    )
                    messages.progressSummary = "等待上次失败退避结束（${waitSeconds}s）"
                    messages.throttleHint = "来源退避中：等待 ${waitSeconds}s 后重试"
                    interruptibleDelay(persistedRetryWait)
                    messages.throttleHint = null
                }
                if (taskState.cancelRequested) {
                    revertTaskToPending(task, activeEpisodeId)
                    break
                }
                if (task.retryNotBeforeAt > 0L) {
                    downloadRepository.setQueueTaskRetryNotBefore(task.taskId, 0L)
                }
                val sourceWait = (sourceCooldownUntil[taskSourceKey] ?: 0L) - System.currentTimeMillis()
                if (sourceWait > 0L && !taskState.cancelRequested) {
                    val waitDetail = "来源限流等待 ${sourceWait / 1000}s"
                    taskState.updateActiveTask(detail = waitDetail, progress = 0f)
                    downloadRepository.setQueueTaskStatus(
                        taskId = task.taskId,
                        status = DownloadQueueStatus.Running,
                        detail = waitDetail
                    )
                    episodeStore.update(
                        episodeId = activeEpisodeId,
                        state = EpisodeDownloadState.Running,
                        progress = 0f,
                        detail = waitDetail
                    )
                    messages.progressSummary = "来源 ${task.source.ifBlank { "unknown" }} 限流等待 ${sourceWait / 1000}s"
                    messages.throttleHint = "流控中：来源限流等待 ${sourceWait / 1000}s，请耐心等待"
                    interruptibleDelay(sourceWait)
                    messages.throttleHint = null
                }
                if (taskState.cancelRequested) {
                    revertTaskToPending(task, activeEpisodeId)
                    break
                }
                if (processed > 0 && !taskState.cancelRequested) {
                    val reqWait = throttle.baseDelayMs + nextJitterMs(throttle.jitterMaxMs)
                    if (reqWait > 0L) {
                        val waitDetail = "节流等待 ${reqWait}ms"
                        taskState.updateActiveTask(detail = waitDetail, progress = 0f)
                        downloadRepository.setQueueTaskStatus(
                            taskId = task.taskId,
                            status = DownloadQueueStatus.Running,
                            detail = waitDetail
                        )
                        episodeStore.update(
                            episodeId = activeEpisodeId,
                            state = EpisodeDownloadState.Running,
                            progress = 0f,
                            detail = waitDetail
                        )
                        messages.progressSummary = "流控等待 ${reqWait}ms 后继续"
                        interruptibleDelay(reqWait)
                    }
                }
                if (taskState.cancelRequested) {
                    revertTaskToPending(task, activeEpisodeId)
                    break
                }
                // 单任务自动重试：首次失败后最多重试两次。
                val maxAutoRetry = 2
                var retryCount = 0
                var finalResult: Result<DanmuDownloadResult>? = null
                var staleRebuildDone = false
                // The source key may change if stale-chain recovery resolves a new source.

                while (!taskState.cancelRequested) {
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
                    taskState.updateActiveTask(
                        detail = startDetail,
                        progress = 0f
                    )
                    episodeStore.update(
                        episodeId = activeEpisodeId,
                        state = EpisodeDownloadState.Running,
                        progress = 0f,
                        detail = startDetail
                    )

                    val attemptResult = withContext(Dispatchers.IO) {
                        downloadRepository.downloadEpisode(downloadInput) { progress, detail ->
                            scope.launch {
                                taskState.updateActiveTask(
                                    detail = detail,
                                    progress = progress.coerceIn(0f, 1f)
                                )
                                episodeStore.update(
                                    episodeId = activeEpisodeId,
                                    state = EpisodeDownloadState.Running,
                                    progress = progress.coerceIn(0f, 1f),
                                    detail = detail
                                )
                            }
                        }
                    }
                    finalResult = attemptResult

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
                    val failedResult = attemptResult.fold(
                        onSuccess = { output -> output.status == DownloadRecordStatus.Failed },
                        onFailure = { true }
                    )
                    val canRetry = retryCount < maxAutoRetry
                    if (
                        prepareChainForThisRun &&
                        failedResult &&
                        canRetry &&
                        !staleRebuildDone &&
                        DownloadRetryPolicy.shouldRebuildChainForStaleFailure(failureHttpCode, rawDetail)
                    ) {
                        val rebuildingDetail = "检测到旧链路可能失效，正在重建弹幕链路"
                        taskState.updateActiveTask(detail = rebuildingDetail, progress = 0f)
                        downloadRepository.setQueueTaskStatus(
                            taskId = task.taskId,
                            status = DownloadQueueStatus.Running,
                            detail = rebuildingDetail
                        )
                        episodeStore.update(
                            episodeId = activeEpisodeId,
                            state = EpisodeDownloadState.Running,
                            progress = 0f,
                            detail = rebuildingDetail
                        )
                        val rebuiltResult = rebuildTaskInput(task)
                        if (rebuiltResult.isSuccess) {
                            val rebuiltInput = rebuiltResult.getOrThrow()
                            val oldEpisodeId = downloadInput.episodeId
                            val rebuiltDetail = if (rebuiltInput.episodeId != oldEpisodeId) {
                                "链路重建完成：已刷新弹幕ID（$oldEpisodeId→${rebuiltInput.episodeId}）"
                            } else {
                                "链路重建完成：已刷新映射"
                            }
                            if (!downloadRepository.updateQueueTaskInput(task.taskId, rebuiltInput, rebuiltDetail)) {
                                val persistFailDetail = "链路重建失败：无法保存新的弹幕链路"
                                finalResult = Result.failure(IllegalStateException(persistFailDetail))
                                taskState.updateActiveTask(detail = persistFailDetail, progress = 0f)
                                downloadRepository.setQueueTaskStatus(
                                    taskId = task.taskId,
                                    status = DownloadQueueStatus.Running,
                                    detail = persistFailDetail
                                )
                                episodeStore.update(
                                    episodeId = activeEpisodeId,
                                    state = EpisodeDownloadState.Running,
                                    progress = 0f,
                                    detail = persistFailDetail
                                )
                                break
                            }
                            downloadInput = rebuiltInput
                            activeEpisodeId = rebuiltInput.episodeId
                            taskSourceKey = sourceKey(rebuiltInput.source)
                            staleRebuildDone = true
                            taskState.setActiveTask(
                                taskId = task.taskId,
                                animeTitle = rebuiltInput.animeTitle,
                                episodeNo = rebuiltInput.episodeNo,
                                source = rebuiltInput.source,
                                detail = rebuiltDetail,
                                progress = 0f
                            )
                            downloadRepository.setQueueTaskStatus(
                                taskId = task.taskId,
                                status = DownloadQueueStatus.Running,
                                detail = rebuiltDetail
                            )
                            episodeStore.update(
                                episodeId = activeEpisodeId,
                                state = EpisodeDownloadState.Running,
                                progress = 0f,
                                detail = rebuiltDetail
                            )
                            retryCount++
                            continue
                        } else {
                            val rebuildFailDetail = "链路重建失败：${rebuiltResult.exceptionOrNull()?.message ?: "未知错误"}"
                            taskState.updateActiveTask(detail = rebuildFailDetail, progress = 0f)
                            downloadRepository.setQueueTaskStatus(
                                taskId = task.taskId,
                                status = DownloadQueueStatus.Running,
                                detail = rebuildFailDetail
                            )
                            episodeStore.update(
                                episodeId = activeEpisodeId,
                                state = EpisodeDownloadState.Running,
                                progress = 0f,
                                detail = rebuildFailDetail
                            )
                            finalResult = Result.failure(
                                IllegalStateException(rebuildFailDetail)
                            )
                            break
                        }
                    }
                    val shouldRetry = failedResult &&
                        canRetry &&
                        DownloadRetryPolicy.shouldRetryFailure(failureHttpCode, rawDetail)
                    if (!shouldRetry) {
                        break
                    }

                    val shouldBackoffRetry = DownloadRetryPolicy.shouldTriggerBackoff(
                        failureHttpCode,
                        rawDetail
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
                        val level = (sourceBackoffLevel[taskSourceKey] ?: 0) + 1
                        sourceBackoffLevel[taskSourceKey] = level
                        backoffMs = backoffDelayMs(throttle, level)
                        sourceCooldownUntil[taskSourceKey] = System.currentTimeMillis() + backoffMs
                    }
                    val retryDetailWithBackoff = if (backoffMs > 0L) {
                        "$retryDetail（退避${backoffMs / 1000}s）"
                    } else {
                        retryDetail
                    }
                    taskState.updateActiveTask(
                        detail = retryDetailWithBackoff,
                        progress = 0f
                    )
                    downloadRepository.setQueueTaskStatus(
                        taskId = task.taskId,
                        status = DownloadQueueStatus.Running,
                        detail = retryDetailWithBackoff
                    )
                    episodeStore.update(
                        episodeId = activeEpisodeId,
                        state = EpisodeDownloadState.Running,
                        progress = 0f,
                        detail = retryDetailWithBackoff
                    )
                    if (backoffMs > 0L && !taskState.cancelRequested) {
                        messages.progressSummary = "来源 ${task.source.ifBlank { "unknown" }} 重试退避 ${backoffMs / 1000}s"
                        messages.throttleHint = "流控中：下载失败自动重试，等待 ${backoffMs / 1000}s"
                        interruptibleDelay(backoffMs)
                        messages.throttleHint = null
                    }
                    retryCount++
                }

                // A stop request arriving during HTTP must not erase the success
                // already saved to disk/history. Only unfinished work returns to pending.
                if (taskState.cancelRequested && finalResult?.isSuccess != true) {
                    revertTaskToPending(task, activeEpisodeId)
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
                                val detail = output.errorMessage?.takeIf { it.isNotBlank() }?.let {
                                    "已保存：${output.relativePath}（$it）"
                                } ?: "已保存：${output.relativePath}"
                                taskState.updateActiveTask(
                                    detail = detail,
                                    progress = 1f
                                )
                                sourceBackoffLevel[taskSourceKey] = 0
                                sourceCooldownUntil.remove(taskSourceKey)
                                downloadRepository.setQueueTaskStatus(
                                    taskId = task.taskId,
                                    status = DownloadQueueStatus.Success,
                                    detail = detail
                                )
                                downloadRepository.setQueueTaskRetryNotBefore(task.taskId, 0L)
                                episodeStore.update(
                                    episodeId = activeEpisodeId,
                                    state = EpisodeDownloadState.Success,
                                    progress = 1f,
                                    detail = detail
                                )
                            }

                            DownloadRecordStatus.Skipped -> {
                                skipped++
                                val detail = output.errorMessage ?: "已跳过"
                                taskState.updateActiveTask(
                                    detail = detail,
                                    progress = 1f
                                )
                                sourceBackoffLevel[taskSourceKey] = 0
                                sourceCooldownUntil.remove(taskSourceKey)
                                downloadRepository.setQueueTaskStatus(
                                    taskId = task.taskId,
                                    status = DownloadQueueStatus.Skipped,
                                    detail = detail
                                )
                                downloadRepository.setQueueTaskRetryNotBefore(task.taskId, 0L)
                                episodeStore.update(
                                    episodeId = activeEpisodeId,
                                    state = EpisodeDownloadState.Skipped,
                                    progress = 1f,
                                    detail = detail
                                )
                            }

                            DownloadRecordStatus.Failed -> {
                                failed++
                                val rawDetail = output.errorMessage ?: "下载失败"
                                val shouldBackoff = DownloadRetryPolicy.shouldTriggerBackoff(
                                    output.httpCode,
                                    rawDetail
                                )
                                val failPrefix = if (retryCount > 0) {
                                    "重试${retryCount}次后仍失败："
                                } else {
                                    "下载失败："
                                }
                                var retryNotBeforeAt = 0L
                                val detail = if (shouldBackoff) {
                                    val level = (sourceBackoffLevel[taskSourceKey] ?: 0) + 1
                                    sourceBackoffLevel[taskSourceKey] = level
                                    val backoffMs = backoffDelayMs(throttle, level)
                                    retryNotBeforeAt = System.currentTimeMillis() + backoffMs
                                    sourceCooldownUntil[taskSourceKey] = retryNotBeforeAt
                                    "$failPrefix$rawDetail（退避${backoffMs / 1000}s）"
                                } else {
                                    "$failPrefix$rawDetail"
                                }
                                taskState.updateActiveTask(
                                    detail = detail,
                                    progress = 1f
                                )
                                downloadRepository.setQueueTaskStatus(
                                    taskId = task.taskId,
                                    status = DownloadQueueStatus.Failed,
                                    detail = detail
                                )
                                downloadRepository.setQueueTaskRetryNotBefore(
                                    task.taskId,
                                    retryNotBeforeAt
                                )
                                episodeStore.update(
                                    episodeId = activeEpisodeId,
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
                        val failPrefix = if (retryCount > 0) {
                            "重试${retryCount}次后仍失败："
                        } else {
                            "下载失败："
                        }
                        val failureHttpCode = extractHttpCodeFromDetail(rawDetail)
                        val shouldBackoff = DownloadRetryPolicy.shouldTriggerBackoff(
                            failureHttpCode,
                            rawDetail
                        )
                        var retryNotBeforeAt = 0L
                        val detail = if (shouldBackoff) {
                            val level = (sourceBackoffLevel[taskSourceKey] ?: 0) + 1
                            sourceBackoffLevel[taskSourceKey] = level
                            val backoffMs = backoffDelayMs(throttle, level)
                            retryNotBeforeAt = System.currentTimeMillis() + backoffMs
                            sourceCooldownUntil[taskSourceKey] = retryNotBeforeAt
                            "$failPrefix$rawDetail（退避${backoffMs / 1000}s）"
                        } else {
                            "$failPrefix$rawDetail"
                        }
                        taskState.updateActiveTask(
                            detail = detail,
                            progress = 1f
                        )
                        downloadRepository.setQueueTaskStatus(
                            taskId = task.taskId,
                            status = DownloadQueueStatus.Failed,
                            detail = detail
                        )
                        downloadRepository.setQueueTaskRetryNotBefore(
                            task.taskId,
                            retryNotBeforeAt
                        )
                        episodeStore.update(
                            episodeId = activeEpisodeId,
                            state = EpisodeDownloadState.Failed,
                            progress = 1f,
                            detail = detail
                        )
                    }
                )

                val remaining = queueTasks().count { it.statusEnum() == DownloadQueueStatus.Pending }
                val totalForRun = processed + remaining
                taskState.overallProgress = if (totalForRun <= 0) 1f else processed.toFloat() / totalForRun.toFloat()
                messages.progressSummary = "队列执行：已完成 $processed，待处理 $remaining（成功 $success，失败 $failed，跳过 $skipped）"

                if (!taskState.cancelRequested && remaining > 0 && throttle.batchSize > 0 && processed % throttle.batchSize == 0) {
                    val batchWait = throttle.batchRestMs + nextJitterMs(min(2000L, throttle.jitterMaxMs))
                    if (batchWait > 0L) {
                        messages.progressSummary = "已完成 $processed 集，批次休息 ${batchWait / 1000}s"
                        messages.throttleHint = "流控中：每 ${throttle.batchSize} 集休息 ${batchWait / 1000}s，防止被封禁"
                        interruptibleDelay(batchWait)
                        messages.throttleHint = null
                    }
                }
            }

                if (taskState.cancelRequested && taskState.cancelRemainingTasks) cancelPendingTasks()
                val remain = queueTasks().count { it.statusEnum() == DownloadQueueStatus.Pending }
                if (taskState.cancelRequested) {
                    messages.progressSummary = if (taskState.cancelRemainingTasks) "剩余任务已取消，成功下载的文件已保留" else "队列已暂停，待处理 $remain 集"
                    summaryMessage = messages.progressSummary
                } else {
                    messages.progressSummary = "队列执行完成：成功 $success，失败 $failed，跳过 $skipped"
                    summaryMessage = messages.progressSummary
                }
            } catch (throwable: Throwable) {
                val fallback = throwable.message ?: "队列执行失败"
                messages.progressSummary = fallback
                summaryMessage = fallback
            } finally {
                val restoreError = rateLimitBypass.restore(rateLimitBypassSession)
                when {
                    !summaryMessage.isNullOrBlank() && !restoreError.isNullOrBlank() -> {
                        messages.operationMessage = "${summaryMessage}（$restoreError）"
                    }
                    !summaryMessage.isNullOrBlank() -> {
                        messages.operationMessage = summaryMessage
                    }
                    !restoreError.isNullOrBlank() -> {
                        messages.operationMessage = restoreError
                    }
                }
                taskState.clearActiveTask()
                messages.throttleHint = null
                taskState.isDownloading = false
            }
        }
    }

    private fun sourceKey(raw: String): String {
        return raw.trim().ifBlank { "unknown" }.lowercase()
    }

    private suspend fun interruptibleDelay(totalMs: Long) {
        val step = 300L
        var remaining = totalMs
        while (remaining > 0L && !taskState.cancelRequested) {
            delay(min(step, remaining))
            remaining -= step
        }
    }

    private fun revertTaskToPending(
        task: com.example.danmuapiapp.domain.model.DanmuDownloadTask,
        episodeId: Long = task.episodeId
    ) {
        downloadRepository.setQueueTaskStatus(
            taskId = task.taskId,
            status = DownloadQueueStatus.Pending,
            detail = "等待恢复"
        )
        episodeStore.update(
            episodeId = episodeId,
            state = EpisodeDownloadState.Queued,
            progress = 0f,
            detail = "等待恢复"
        )
        taskState.clearActiveTask()
    }

    private fun nextJitterMs(maxMs: Long): Long {
        if (maxMs <= 0L) return 0L
        val bound = (maxMs + 1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return taskState.random.nextInt(bound).toLong()
    }

    private fun backoffDelayMs(config: DownloadThrottleConfig, level: Int): Long {
        var delayMs = config.backoffBaseMs.coerceAtLeast(0L)
        val rounds = (level - 1).coerceAtLeast(0).coerceAtMost(10)
        repeat(rounds) {
            delayMs = (delayMs * 2L).coerceAtMost(config.backoffMaxMs)
        }
        delayMs = (delayMs + nextJitterMs(min(1500L, config.jitterMaxMs))).coerceAtMost(config.backoffMaxMs)
        return delayMs.coerceAtLeast(1000L)
    }

    private fun extractHttpCodeFromDetail(detail: String): Int? {
        val matched = Regex("http\\s*([0-9]{3})", RegexOption.IGNORE_CASE).find(detail)
        return matched?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
}
