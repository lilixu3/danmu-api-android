package com.example.danmuapiapp.ui.screen.localdanmu

import com.example.danmuapiapp.data.repository.DownloadLocalImportPlanner
import com.example.danmuapiapp.data.repository.LocalDanmuImportManager
import com.example.danmuapiapp.data.repository.LocalDanmuImportedIndex
import com.example.danmuapiapp.data.repository.LocalDanmuMetadataResolver
import com.example.danmuapiapp.data.repository.LocalDanmuUploadService
import com.example.danmuapiapp.data.service.RuntimeDataAccess
import com.example.danmuapiapp.domain.model.LocalDanmuBatchItem
import com.example.danmuapiapp.domain.model.LocalDanmuBatchStatus
import com.example.danmuapiapp.domain.model.LocalDanmuParseConfidence
import com.example.danmuapiapp.domain.model.LocalDanmuResource
import com.example.danmuapiapp.domain.model.LocalDanmuType
import com.example.danmuapiapp.domain.repository.DanmuDownloadRepository
import com.example.danmuapiapp.domain.repository.LocalDanmuRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 本地弹幕「批量导入」子控制器：选文件 → 预解析元数据 → 去重 → 串行上传 → 结果汇总。
 *
 * 只读写 [LocalDanmuUiState] 的 batch 切片；错误文案与列表刷新由宿主注入，
 * 保证与单个资源操作使用同一套提示与刷新路径。
 */
internal class LocalDanmuBatchImportController(
    private val scope: CoroutineScope,
    private val repository: LocalDanmuRepository,
    private val importManager: LocalDanmuImportManager,
    private val uploader: LocalDanmuUploadService,
    private val downloads: DanmuDownloadRepository,
    private val runtimeData: RuntimeDataAccess,
    private val uiState: MutableStateFlow<LocalDanmuUiState>,
    private val importedIndex: () -> Map<String, String>,
    private val userMessage: (Throwable) -> String,
    private val refreshAndAwait: suspend () -> Unit
) {
    private var batchItemId = 0L

    /** 目录浏览选中文件后的入口：按路径逐个准备并加入批量面板。 */
    fun addFilePaths(paths: List<String>) {
        addBatchSources(sources = paths) { path -> importManager.preparePath(path) }
    }

    private fun showError(message: String) {
        uiState.update { it.copy(errorMessage = message) }
    }


    private fun addBatchSources(
        sources: List<String>,
        prepare: suspend (String) -> Result<LocalDanmuImportManager.PreparedFile>
    ) {
        scope.launch {
            val snapshot = repository.refresh(force = true).getOrElse { error ->
                showError(userMessage(error))
                return@launch
            }
            if (!runtimeData.isCurrentSession(snapshot.sessionKey)) return@launch
            uiState.update { it.copy(snapshot = snapshot) }
            uiState.update {
                it.copy(
                    batch = it.batch.copy(visible = true, isRunning = true, cancelRequested = false),
                    errorMessage = null
                )
            }
            val items = mutableListOf<LocalDanmuBatchItem>()
            val failures = mutableListOf<String>()
            sources.forEach { source ->
                prepare(source).fold(
                    onSuccess = { prepared -> items += batchItemOf(prepared, snapshot.sessionKey) },
                    onFailure = { error ->
                        failures += "${source.substringAfterLast('/')}：${
                            error.message?.takeIf { it.isNotBlank() } ?: "无法读取文件"
                        }"
                    }
                )
            }
            val prepared = markBatchDuplicates(items)
            if (!runtimeData.isCurrentSession(snapshot.sessionKey)) return@launch
            uiState.update { state ->
                state.copy(
                    batch = state.batch.copy(
                        visible = true,
                        isRunning = false,
                        items = state.batch.items + prepared
                    ),
                    errorMessage = failures.takeIf { it.isNotEmpty() }?.joinToString("\n")
                )
            }
        }
    }

    private fun batchItemOf(prepared: LocalDanmuImportManager.PreparedFile, sessionKey: String): LocalDanmuBatchItem {
        val record = downloads.records.value.firstOrNull { it.fileUri == prepared.sourceUri && it.bytes == prepared.sizeBytes }
        val parsed = record?.let(com.example.danmuapiapp.data.repository.DownloadLocalImportPlanner::metadata)
            ?: LocalDanmuMetadataResolver.resolve(
            fileName = prepared.displayName,
            relativePath = prepared.relativePath
        )
        val imported = LocalDanmuImportedIndex.lookup(importedIndex(),
            LocalDanmuImportedIndex.pathFromUri(prepared.sourceUri).orEmpty(), prepared.displayName, prepared.sizeBytes ?: 0L)
        return LocalDanmuBatchItem(
            selected = imported == null,
            id = ++batchItemId,
            sourceUri = prepared.sourceUri,
            displayName = prepared.displayName,
            sizeBytes = prepared.sizeBytes,
            formatHint = prepared.formatHint,
            mimeType = prepared.mimeType,
            expectedSessionKey = sessionKey,
            title = parsed.title,
            year = parsed.year,
            type = LocalDanmuType.fromWire(parsed.type) ?: LocalDanmuType.Unknown,
            season = parsed.season ?: 1,
            episode = parsed.episode,
            confidence = parsed.confidence,
            importedLabel = LocalDanmuImportedIndex.lookup(
                index = importedIndex(),
                path = LocalDanmuImportedIndex.pathFromUri(prepared.sourceUri).orEmpty(),
                name = prepared.displayName,
                sizeBytes = prepared.sizeBytes ?: 0L
            )
        )
    }

    fun toggleBatchSelected(id: Long) {
        uiState.update { state ->
            val selected = state.batch.selectedIds
            state.copy(batch = state.batch.withSelection(if (id in selected) selected - id else selected + id))
        }
    }

    fun selectBatchItems(ids: Set<Long>) {
        uiState.update { it.copy(batch = it.batch.withSelection(ids)) }
    }

    fun setBatchType(type: LocalDanmuType?) {
        if (uiState.value.batch.isRunning) return
        uiState.update { it.copy(batch = it.batch.copy(items = applyImportType(it.batch.items, type))) }
    }

    fun updateBatchItemMetadata(
        id: Long,
        title: String,
        year: Int?,
        type: LocalDanmuType,
        season: Int?,
        episode: Int?
    ) {
        updateBatchItem(id) {
            it.copy(
                title = title,
                year = year,
                type = type,
                season = season,
                episode = episode,
                confidence = LocalDanmuParseConfidence.Medium
            )
        }
    }

    fun removeBatchItem(id: Long) {
        uiState.update { state ->
            state.copy(batch = state.batch.copy(items = state.batch.items.filterNot { it.id == id }))
        }
    }

    fun closeBatch() {
        if (uiState.value.batch.isRunning) return
        uiState.update { it.copy(batch = LocalDanmuBatchState()) }
    }

    fun cancelBatchImport() {
        uiState.update { it.copy(batch = it.batch.copy(cancelRequested = true)) }
    }

    fun retryFailedBatch() {
        if (uiState.value.batch.isRunning) return
        uiState.update { state ->
            state.copy(
                batch = state.batch.copy(
                    items = state.batch.items.map { item ->
                        if (item.status == LocalDanmuBatchStatus.Failed) {
                            item.copy(status = LocalDanmuBatchStatus.Ready, selected = true, progress = 0f)
                        } else {
                            item
                        }
                    }
                )
            )
        }
        runBatchImport()
    }

    fun runBatchImport() {
        if (uiState.value.batch.isRunning) return
        val targets = uiState.value.batch.items.filter { it.canImport }
        if (targets.isEmpty()) {
            uiState.update { it.copy(errorMessage = "没有可导入的条目，请检查标题、年份、季集") }
            return
        }
        scope.launch {
            uiState.update {
                it.copy(
                    batch = it.batch.copy(
                        isRunning = true,
                        cancelRequested = false,
                        total = targets.size,
                        completed = 0
                    ),
                    errorMessage = null
                )
            }
            var success = 0
            var failed = 0
            for (item in targets) {
                if (uiState.value.batch.cancelRequested) break
                updateBatchItem(item.id) {
                    it.copy(status = LocalDanmuBatchStatus.Uploading, progress = 0f, message = "")
                }
                val year = item.year ?: continue
                uploadPreparedFile(
                    sourceUri = item.sourceUri,
                    displayName = item.displayName,
                    year = year,
                    type = item.type,
                    season = item.season,
                    episode = item.episode,
                    title = item.title,
                    formatHint = item.formatHint,
                    sizeBytes = item.sizeBytes,
                    mimeType = item.mimeType,
                    expectedSessionKey = item.expectedSessionKey,
                    onProgress = { progress ->
                        updateBatchItem(item.id) { current ->
                            if (progress >= 1f || progress - current.progress >= 0.05f) {
                                current.copy(progress = progress)
                            } else {
                                current
                            }
                        }
                    }
                ).fold(
                    onSuccess = { resource ->
                        success++
                        if (!runtimeData.isCurrentSession(resource.sessionKey)) return@fold
                        updateBatchItem(item.id) {
                            it.copy(
                                status = LocalDanmuBatchStatus.Success,
                                progress = 1f,
                                message = "${resource.count} 条弹幕"
                            )
                        }
                    },
                    onFailure = { error ->
                        failed++
                        updateBatchItem(item.id) {
                            it.copy(
                                status = LocalDanmuBatchStatus.Failed,
                                progress = 0f,
                                message = userMessage(error)
                            )
                        }
                    }
                )
                uiState.update { it.copy(batch = it.batch.copy(completed = it.batch.completed + 1)) }
            }
            val canceled = uiState.value.batch.cancelRequested
            refreshAndAwait()
            uiState.update {
                it.copy(
                    batch = it.batch.copy(isRunning = false, cancelRequested = false),
                    message = (when {
                        canceled -> "已取消批量导入：成功 $success 个，失败 $failed 个"
                        failed == 0 -> "批量导入完成：成功 $success 个"
                        else -> "批量导入完成：成功 $success 个，失败 $failed 个"
                    }) + if (success > 0 && it.errorMessage != null) "；列表刷新失败，请重试刷新（无需重复上传）" else ""
                )
            }
        }
    }

    private fun updateBatchItem(
        id: Long,
        transform: (LocalDanmuBatchItem) -> LocalDanmuBatchItem
    ) {
        uiState.update { state ->
            state.copy(
                batch = state.batch.copy(
                    items = state.batch.items.map { if (it.id == id) transform(it) else it }
                )
            )
        }
    }

    /**
     * 单文件上传与批量导入共用：构造上传请求，成功后把来源登记到本地来源表
     * （详情页预览要用它按 uri 读原文件）。
     */
    private suspend fun uploadPreparedFile(
        sourceUri: String,
        displayName: String,
        title: String,
        year: Int,
        type: LocalDanmuType,
        season: Int?,
        episode: Int?,
        formatHint: String,
        sizeBytes: Long?,
        mimeType: String,
        expectedSessionKey: String,
        onProgress: (Float) -> Unit
    ): Result<LocalDanmuResource> {
        return uploader.upload(LocalDanmuBatchItem(id = 0, sourceUri = sourceUri,
            displayName = displayName, sizeBytes = sizeBytes, formatHint = formatHint, mimeType = mimeType,
            title = title, year = year, type = type, season = season, episode = episode,
            expectedSessionKey = expectedSessionKey), onProgress)
    }
}
