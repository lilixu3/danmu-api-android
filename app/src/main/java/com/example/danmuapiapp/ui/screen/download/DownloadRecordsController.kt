package com.example.danmuapiapp.ui.screen.download

import com.example.danmuapiapp.domain.model.DanmuDownloadInput
import com.example.danmuapiapp.domain.model.DanmuDownloadRecord
import com.example.danmuapiapp.domain.model.DanmuDownloadSettings
import com.example.danmuapiapp.domain.model.DanmuDownloadTask
import com.example.danmuapiapp.domain.repository.DanmuDownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 下载记录与整季补集：记录预览、删除、目录同步，以及按季刷新缺失剧集。
 */
internal class DownloadRecordsController(
    private val scope: CoroutineScope,
    private val downloadRepository: DanmuDownloadRepository,
    private val seriesRefresh: SeriesRefreshService,
    private val settings: () -> DanmuDownloadSettings,
    private val queueTasks: () -> List<DanmuDownloadTask>,
    private val records: () -> List<DanmuDownloadRecord>,
    private val state: DownloadSearchUiState,
    private val episodeStore: DownloadEpisodeStateStore,
    private val taskState: DownloadTaskRuntimeState,
    private val messages: DownloadMessageState,
    private val recordsState: DownloadRecordsUiState,
    private val resolveApiBaseUrl: () -> String?,
    private val startQueueProcessing: () -> Unit
) {

    fun refreshSeries(group: DownloadRecordAnimeGroup) {
        if (recordsState.refreshingSeriesKey != null) return
        val base = resolveApiBaseUrl() ?: run { messages.errorMessage = "弹幕源地址无效"; return }
        recordsState.refreshingSeriesKey = group.key
        scope.launch {
            try {
                recordsState.missingEpisodes = null
                val result = seriesRefresh.load(base, group.records, settings().matchMode)
                val available = downloadRepository.availableDownloadRecordIds(group.records).getOrThrow()
                val missing = missingDownloadedEpisodes(result.episodes, group.records, available)
                if (missing.isEmpty()) {
                    messages.operationMessage = if (result.confirmedFresh) {
                        "《${group.title}》源里共 ${result.episodes.size} 集，已经全部下载或导入本地"
                    } else {
                        "《${group.title}》当前集列表共 ${result.episodes.size} 集，未发现待下载集；尚未确认源端最新列表，请稍后重试"
                    }
                } else {
                    recordsState.missingEpisodes = MissingEpisodesState(group.title, result.anime,
                        missing, result.episodes.size - missing.size, result.episodes.size, result.way,
                        missing.map { it.episodeId }.toSet())
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { messages.errorMessage = e.message ?: "刷新失败" }
            finally { recordsState.refreshingSeriesKey = null }
        }
    }

    fun toggleMissingEpisode(id: Long) {
        recordsState.missingEpisodes = recordsState.missingEpisodes?.let { it.copy(selectedIds = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id) }
    }

    fun selectAllMissing(select: Boolean) {
        recordsState.missingEpisodes = recordsState.missingEpisodes?.let { it.copy(selectedIds = if (select) it.episodes.map { e -> e.episodeId }.toSet() else emptySet()) }
    }

    fun dismissMissing() {
        recordsState.missingEpisodes = null
    }

    fun downloadMissing() {
        val missingState = recordsState.missingEpisodes ?: return
        if (settings().saveTreeUri.isBlank() && settings().saveDirPath.isBlank()) {
            messages.errorMessage = "请先在下载设置中选择保存目录"; return
        }
        val base = resolveApiBaseUrl() ?: return
        val inputs = missingState.episodes.filter { it.episodeId in missingState.selectedIds }.map { episode ->
            DanmuDownloadInput(apiBaseUrl = base, animeTitle = missingState.anime.title, episodeTitle = episode.title,
                episodeId = episode.episodeId, episodeNo = episode.episodeNumber, source = episode.source,
                format = settings().format(), fileNameTemplate = settings().fileNameTemplate,
                conflictPolicy = settings().policy(), animeId = missingState.anime.animeId,
                mediaType = com.example.danmuapiapp.domain.model.LocalDanmuType.fromDescription(missingState.anime.typeDescription)?.wire.orEmpty(),
                directUrl = episode.directUrl)
        }
        if (inputs.isEmpty()) return
        val added = downloadRepository.enqueueTasks(inputs)
        recordsState.missingEpisodes = null
        messages.operationMessage = "已加入队列 $added 集"
        if (!taskState.isDownloading) startQueueProcessing()
    }

    fun openRecordPreview(record: DanmuDownloadRecord) {
        if (recordsState.previewDialogState.loadingRecordId != null) return
        recordsState.previewDialogState = DanmuPreviewDialogState(loadingRecordId = record.id)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                downloadRepository.loadDanmuPreview(record)
            }
            recordsState.previewDialogState = result.fold(
                onSuccess = { preview -> DanmuPreviewDialogState(preview = preview) },
                onFailure = { throwable ->
                    DanmuPreviewDialogState(error = throwable.message ?: "读取弹幕文件失败")
                }
            )
        }
    }

    fun dismissRecordPreview() {
        recordsState.previewDialogState = DanmuPreviewDialogState()
    }

    fun deleteRecords(recordIds: Set<Long>, deleteLocalFiles: Boolean) {
        if (recordIds.isEmpty() || recordsState.isDeletingRecords) return
        if (taskState.isDownloading) {
            messages.operationMessage = "下载进行中，请暂停后再删除记录"
            return
        }
        if (recordsState.isSyncingRecords) {
            messages.operationMessage = "正在扫描目录，请稍后再删除"
            return
        }
        scope.launch {
            recordsState.isDeletingRecords = true
            messages.errorMessage = null
            downloadRepository.deleteRecords(recordIds, deleteLocalFiles).fold(
                onSuccess = { summary ->
                    refreshCurrentEpisodeStates()
                    messages.operationMessage = when {
                        summary.removedRecords <= 0 -> "所选记录已不存在"
                        !deleteLocalFiles -> "已清理 ${summary.removedRecords} 条 App 下载记录，本地文件已保留"
                        else -> buildString {
                            append("已清理 ${summary.removedRecords} 条记录")
                            append("，删除 ${summary.deletedFiles} 个本地文件")
                            if (summary.missingFiles > 0) {
                                append("，${summary.missingFiles} 个文件已不存在")
                            }
                            if (summary.retainedSharedFiles > 0) {
                                append("，保留 ${summary.retainedSharedFiles} 个仍被其他记录引用的文件")
                            }
                            if (summary.failedFiles > 0) {
                                append("，${summary.failedFiles} 个文件删除失败")
                            }
                        }
                    }
                },
                onFailure = { throwable ->
                    messages.errorMessage = "删除下载记录失败：${throwable.message ?: "未知错误"}"
                }
            )
            recordsState.isDeletingRecords = false
        }
    }

    fun syncExistingFiles() {
        syncExistingFiles(showMessageWhenUnchanged = true)
    }

    fun syncExistingFiles(showMessageWhenUnchanged: Boolean) {
        if (recordsState.isSyncingRecords || recordsState.isDeletingRecords) return
        if (settings().saveTreeUri.isBlank() && settings().saveDirPath.isBlank()) { messages.errorMessage = "请先设置下载目录"; return }
        scope.launch {
            recordsState.isSyncingRecords = true
            val result = downloadRepository.syncExistingFiles()
            result.fold(
                onSuccess = { summary ->
                    recordsState.lastSync = summary
                    if (summary.importedRecords > 0) {
                        refreshCurrentEpisodeStates()
                    }
                    if (summary.importedRecords > 0 || showMessageWhenUnchanged) {
                        messages.operationMessage = buildString {
                            append("已扫描 ${summary.scannedFiles} 个文件，新增 ${summary.importedRecords} 条，清理 ${summary.removedRecords} 条")
                            if (summary.truncated) append("（已达到扫描上限）")
                        }
                    }
                },
                onFailure = { throwable ->
                    if (showMessageWhenUnchanged) {
                        messages.errorMessage = "目录扫描失败：${throwable.message ?: "未知错误"}"
                    }
                }
            )
            recordsState.isSyncingRecords = false
        }
    }

    private fun refreshCurrentEpisodeStates() {
        episodeStore.refreshFromSnapshots(
            anime = state.currentAnime,
            episodes = state.episodeCandidates,
            queueTasks = queueTasks(),
            records = records()
        )
    }
}
