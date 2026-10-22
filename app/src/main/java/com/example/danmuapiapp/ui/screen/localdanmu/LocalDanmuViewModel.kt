package com.example.danmuapiapp.ui.screen.localdanmu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.data.repository.LocalDanmuApiException
import com.example.danmuapiapp.data.repository.LocalDanmuErrorKind
import com.example.danmuapiapp.data.repository.LocalDanmuImportManager
import com.example.danmuapiapp.data.repository.LocalDanmuFileBrowser
import com.example.danmuapiapp.data.repository.LocalDanmuImportedIndex
import com.example.danmuapiapp.data.repository.LocalDanmuSourceStore
import com.example.danmuapiapp.data.repository.LocalDanmuUploadHistoryStore
import com.example.danmuapiapp.data.repository.matchMigratedResourceKeys
import com.example.danmuapiapp.data.repository.validateLocalDanmuEdit
import com.example.danmuapiapp.domain.model.LocalDanmuEditScope
import com.example.danmuapiapp.domain.model.LocalDanmuGroup
import com.example.danmuapiapp.domain.model.LocalDanmuMetadataPatch
import com.example.danmuapiapp.domain.model.LocalDanmuResource
import com.example.danmuapiapp.domain.model.LocalDanmuResourceKey
import com.example.danmuapiapp.domain.model.LocalDanmuSnapshot
import com.example.danmuapiapp.domain.model.LocalDanmuType
import com.example.danmuapiapp.domain.repository.LocalDanmuRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.ui.screen.download.DanmuPreviewDialogState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class LocalDanmuViewModel @Inject constructor(
    private val repository: LocalDanmuRepository,
    private val importManager: LocalDanmuImportManager,
    private val fileBrowser: LocalDanmuFileBrowser,
    private val sourceStore: LocalDanmuSourceStore,
    private val uploadHistoryStore: LocalDanmuUploadHistoryStore,
    private val runtimeRepository: RuntimeRepository,
    private val uploader: com.example.danmuapiapp.data.repository.LocalDanmuUploadService,
    private val runtimeData: com.example.danmuapiapp.data.service.RuntimeDataAccess,
    private val downloads: com.example.danmuapiapp.domain.repository.DanmuDownloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocalDanmuUiState())
    val uiState: StateFlow<LocalDanmuUiState> = _uiState.asStateFlow()
    private var importedIndex: Map<String, String> = emptyMap()
    private var refreshJob: Job? = null
    private var detailJob: Job? = null
    private var previewJob: Job? = null

    /**
     * 目录浏览与批量导入各自的状态与规则都在子控制器里；
     * 这里持有两者共用的 uiState、导入索引与错误文案映射。
     */
    private val batchImport = LocalDanmuBatchImportController(
        scope = viewModelScope,
        repository = repository,
        importManager = importManager,
        uploader = uploader,
        downloads = downloads,
        runtimeData = runtimeData,
        uiState = _uiState,
        importedIndex = { importedIndex },
        userMessage = { userMessage(it) },
        refreshAndAwait = { refreshAndAwait() }
    )

    private val directoryBrowser = LocalDanmuDirectoryBrowserController(
        scope = viewModelScope,
        fileBrowser = fileBrowser,
        uploadHistoryStore = uploadHistoryStore,
        uiState = _uiState,
        importedIndex = { importedIndex },
        onImportSelected = { paths -> batchImport.addFilePaths(paths) }
    )

    init {
        viewModelScope.launch {
            var observed = runtimeData.changes.value
            runtimeData.changes.collect { generation ->
                if (generation != observed) {
                    observed = generation
                    detailJob?.cancel()
                    previewJob?.cancel()
                    importedIndex = emptyMap()
                    _uiState.update { it.copy(snapshot = LocalDanmuSnapshot(emptyList(), emptyList()),
                        detailResource = null, editState = LocalDanmuEditState(), pendingDeleteResources = emptyList(),
                        previewState = DanmuPreviewDialogState(),
                        batch = if (it.batch.isRunning) it.batch.copy(cancelRequested = true) else LocalDanmuBatchState()) }
                    refreshNow()
                }
            }
        }
        viewModelScope.launch {
            repository.writePermission.collect { permission ->
                _uiState.update { it.copy(writePermission = permission) }
            }
        }
        viewModelScope.launch {
            repository.localSourceEnabled.collect { enabled ->
                _uiState.update { it.copy(localSourceEnabled = enabled) }
            }
        }
        refresh()
    }

    fun refresh() = refreshInternal(force = false)

    /** 手动刷新（点刷新、导入/删除完成）：强制重新读一次。 */
    fun refreshNow() = refreshInternal(force = true)

    /**
     * 批量导入结束后：强制刷新列表，并等这次刷新真正结束再报结果
     * （刷新失败时提示「无需重复上传」依赖这个顺序）。
     */
    private suspend fun refreshAndAwait() {
        refreshNow()
        refreshJob?.join()
    }

    private fun refreshInternal(force: Boolean) {
        // 进入页面时 init 与 ON_START 会各触发一次刷新，这里合并成一次；
        // 连点「重试」也不会堆叠出重复请求。
        if (refreshJob?.isActive == true) {
            if (!force) return
            refreshJob?.cancel()
        }
        refreshJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    allFilesAccessGranted = fileBrowser.isAllFilesAccessGranted()
                )
            }
            repository.refresh(force = force).fold(
                onSuccess = { snapshot ->
                    if (!runtimeData.isCurrentSession(snapshot.sessionKey)) return@fold
                    importedIndex = LocalDanmuImportedIndex.build(
                        resources = snapshot.resources,
                        sources = sourceStore.all(snapshot.libraryId)
                    )
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            snapshot = snapshot,
                            errorMessage = null,
                            browser = it.browser.copy(
                                rows = directoryBrowser.remapBrowserRows(it.browser.rows, it.browser.selectedPaths)
                            )
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = userMessage(error))
                    }
                }
            )
        }
    }

    fun restartService() {
        viewModelScope.launch {
            _uiState.update { it.copy(message = "正在重启核心服务…", errorMessage = null) }
            runCatching { runtimeRepository.restartServiceAndAwait() }
            delay(1_200L)
            val firstAttempt = repository.refresh(force = true)
            val result = if (firstAttempt.isFailure) {
                runtimeRepository.startService()
                delay(1_500L)
                repository.refresh(force = true)
            } else {
                firstAttempt
            }
            result.fold(
                onSuccess = { snapshot ->
                    _uiState.update {
                        it.copy(
                            snapshot = snapshot,
                            message = "核心服务已恢复",
                            errorMessage = null,
                            isLoading = false
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(errorMessage = userMessage(error), isLoading = false)
                    }
                }
            )
        }
    }

    fun setSearchQuery(value: String) {
        _uiState.update { it.copy(searchQuery = value) }
    }

    fun setTypeFilter(value: String?) {
        _uiState.update { it.copy(typeFilter = value) }
    }

    fun toggleGroup(groupKey: String) {
        _uiState.update { state ->
            val next = state.expandedGroupKeys.toMutableSet()
            if (!next.add(groupKey)) next.remove(groupKey)
            state.copy(expandedGroupKeys = next)
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun showError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    // ───────────────────── 目录直读模式（所有文件访问） ─────────────────────

    fun refreshAllFilesAccess() {
        _uiState.update { it.copy(allFilesAccessGranted = fileBrowser.isAllFilesAccessGranted()) }
    }

    fun openDirectoryBrowser() = directoryBrowser.openDirectoryBrowser()

    fun browseDirectory(path: String) = directoryBrowser.browseDirectory(path)

    fun browseParent() = directoryBrowser.browseParent()

    fun browseDefaultDirectory() = directoryBrowser.browseDefaultDirectory()

    fun closeDirectoryBrowser() = directoryBrowser.closeDirectoryBrowser()

    fun toggleBrowserSelection(path: String) = directoryBrowser.toggleBrowserSelection(path)

    fun toggleBrowserSelectAll() = directoryBrowser.toggleBrowserSelectAll()

    fun setBrowserDefaultDirectory() = directoryBrowser.setBrowserDefaultDirectory()

    fun importSelectedBrowserFiles() = directoryBrowser.importSelectedBrowserFiles()

    // ───────────────────────────── 批量导入 ─────────────────────────────

    fun toggleBatchSelected(id: Long) = batchImport.toggleBatchSelected(id)

    fun setBatchType(type: LocalDanmuType?) = batchImport.setBatchType(type)

    fun updateBatchItemMetadata(
        id: Long,
        title: String,
        year: Int?,
        type: LocalDanmuType,
        season: Int?,
        episode: Int?
    ) = batchImport.updateBatchItemMetadata(id, title, year, type, season, episode)

    fun removeBatchItem(id: Long) = batchImport.removeBatchItem(id)

    fun closeBatch() = batchImport.closeBatch()

    fun cancelBatchImport() = batchImport.cancelBatchImport()

    fun retryFailedBatch() = batchImport.retryFailedBatch()

    fun runBatchImport() = batchImport.runBatchImport()

    // ───────────────────────── 详情/预览/删除 ─────────────────────────

    fun loadDetail(resourceKey: String) {
        detailJob?.cancel()
        previewJob?.cancel()
        val cached = _uiState.value.snapshot.resources.firstOrNull { it.resourceKey == resourceKey }
        if (cached != null) _uiState.update { it.copy(detailResource = cached, previewState = DanmuPreviewDialogState()) }
        detailJob = viewModelScope.launch {
            repository.loadDetail(resourceKey, _uiState.value.snapshot.sessionKey).fold(
                onSuccess = { resource -> _uiState.update { if (it.detailResource?.resourceKey == resourceKey) it.copy(detailResource = resource) else it } },
                onFailure = { error ->
                    if (cached == null) {
                        _uiState.update { it.copy(errorMessage = userMessage(error)) }
                    }
                }
            )
        }
    }

    fun dismissDetail() {
        detailJob?.cancel()
        previewJob?.cancel()
        _uiState.update { it.copy(detailResource = null, previewState = DanmuPreviewDialogState()) }
    }

    // ───────────────────────── 元数据编辑 ─────────────────────────

    fun startEdit(resource: LocalDanmuResource) {
        _uiState.update {
            it.copy(
                editState = LocalDanmuEditState(
                    target = resource,
                    scope = LocalDanmuEditScope.Resource,
                    title = resource.title,
                    year = resource.year.takeIf { year -> year > 0 },
                    type = LocalDanmuType.fromWire(resource.type) ?: LocalDanmuType.Tv,
                    season = resource.season.takeIf { season -> season > 0 } ?: 1,
                    episode = resource.episode,
                    filename = resource.filename
                ),
                errorMessage = null
            )
        }
    }

    /** 从分组卡片直接改整季信息（标题/年份/类型/季数）。 */
    fun startEditGroup(group: LocalDanmuGroup) {
        val target = group.episodes.firstOrNull() ?: return
        startEdit(target)
        setEditScope(LocalDanmuEditScope.Group)
    }

    fun dismissEdit() {
        if (_uiState.value.editState.isSaving) return
        _uiState.update { it.copy(editState = LocalDanmuEditState()) }
    }

    fun setEditScope(scope: LocalDanmuEditScope) {
        updateEdit { it.copy(scope = scope, errorMessage = null) }
    }

    fun updateEditTitle(value: String) {
        updateEdit { it.copy(title = value, errorMessage = null) }
    }

    fun updateEditYear(value: String) {
        updateEdit {
            it.copy(
                year = value.filter(Char::isDigit).take(4).toIntOrNull(),
                errorMessage = null
            )
        }
    }

    fun updateEditType(value: LocalDanmuType) {
        updateEdit { it.copy(type = value, errorMessage = null) }
    }

    fun updateEditSeason(value: String) {
        updateEdit {
            it.copy(
                season = value.filter(Char::isDigit).take(3).toIntOrNull(),
                errorMessage = null
            )
        }
    }

    fun updateEditEpisode(value: String) {
        updateEdit {
            it.copy(
                episode = value.filter(Char::isDigit).take(4).toIntOrNull(),
                errorMessage = null
            )
        }
    }

    fun updateEditFilename(value: String) {
        updateEdit { it.copy(filename = value.take(240), errorMessage = null) }
    }

    fun submitEdit() {
        val state = _uiState.value
        val edit = state.editState
        val target = edit.target ?: return
        if (edit.isSaving) return
        val validation = validateLocalDanmuEdit(
            scope = edit.scope,
            title = edit.title,
            year = edit.year,
            type = edit.type,
            season = edit.season,
            episode = edit.episode,
            filename = edit.filename,
            currentYear = Calendar.getInstance().get(Calendar.YEAR)
        )
        if (validation != null) {
            updateEdit { it.copy(errorMessage = validation) }
            return
        }
        val scoped = edit.scope == LocalDanmuEditScope.Group
        val patch = LocalDanmuMetadataPatch(
            scope = edit.scope,
            title = edit.title.trim().takeIf { scoped },
            year = edit.year.takeIf { scoped },
            type = edit.type.takeIf { scoped },
            season = edit.season.takeIf { scoped },
            episode = edit.episode.takeIf { !scoped },
            filename = edit.filename.trim().takeIf { !scoped },
            expectedSessionKey = target.sessionKey
        )
        val affected = when (edit.scope) {
            LocalDanmuEditScope.Resource -> listOf(target)
            LocalDanmuEditScope.Group ->
                localDanmuGroupResources(state.snapshot.resources, target).ifEmpty { listOf(target) }
        }
        viewModelScope.launch {
            updateEdit { it.copy(isSaving = true, errorMessage = null) }
            com.example.danmuapiapp.data.repository.runLocalDanmuRequest {
                runtimeData.withRuntime(target.sessionKey) {
                    repository.updateMetadata(target.resourceKey, patch).getOrThrow().also { result ->
                        val mappings = matchMigratedResourceKeys(affected, result.resources)
                        sourceStore.migrate(target.libraryId, mappings)
                        val associations = downloads.records.value.mapNotNull { record ->
                            mappings[record.localImports[target.libraryId]]?.let { record.id to it }
                        }.toMap()
                        downloads.markRecordsImported(target.libraryId, associations, mediaType = edit.type.wire)
                    }
                }
            }.fold(
                onSuccess = { result ->
                    if (!runtimeData.isCurrentSession(target.sessionKey)) return@fold
                    _uiState.update {
                        it.copy(
                            editState = LocalDanmuEditState(),
                            message = editSuccessMessage(edit, result.resources.size)
                        )
                    }
                    refreshNow()
                },
                onFailure = { error ->
                    updateEdit { it.copy(isSaving = false, errorMessage = userMessage(error)) }
                }
            )
        }
    }

    private fun updateEdit(transform: (LocalDanmuEditState) -> LocalDanmuEditState) {
        _uiState.update { state ->
            if (state.editState.target == null) {
                state
            } else {
                state.copy(editState = transform(state.editState))
            }
        }
    }

    private fun editSuccessMessage(edit: LocalDanmuEditState, updatedCount: Int): String {
        val episodeLabel = when {
            edit.isMovie -> "正片"
            edit.episode != null -> "第${edit.episode}集"
            else -> "全集"
        }
        return when (edit.scope) {
            LocalDanmuEditScope.Resource -> "已保存：${edit.title} $episodeLabel"
            LocalDanmuEditScope.Group ->
                "已保存：${edit.title} 第${edit.season ?: 1}季（$updatedCount 个文件）"
        }
    }

    fun loadPreview(resourceKey: String) {
        previewJob?.cancel()
        _uiState.update {
            it.copy(previewState = DanmuPreviewDialogState(loadingRecordId = -1L))
        }
        previewJob = viewModelScope.launch {
            repository.loadPreview(resourceKey, _uiState.value.snapshot.sessionKey).fold(
                onSuccess = { preview ->
                    _uiState.update {
                        it.copy(previewState = DanmuPreviewDialogState(preview = preview))
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(previewState = DanmuPreviewDialogState(error = userMessage(error)))
                    }
                }
            )
        }
    }

    fun dismissPreview() {
        previewJob?.cancel()
        _uiState.update { it.copy(previewState = DanmuPreviewDialogState()) }
    }

    fun requestDelete(resource: LocalDanmuResource) {
        _uiState.update { it.copy(pendingDeleteResources = listOf(resource)) }
    }

    fun requestDeleteGroup(group: LocalDanmuGroup) {
        _uiState.update { it.copy(pendingDeleteResources = group.episodes) }
    }

    fun requestDeleteSeries(group: LocalDanmuGroup) {
        val normalizedTitle = LocalDanmuResourceKey.normalizeKey(group.title)
        val resources = _uiState.value.snapshot.resources.filter { resource ->
            LocalDanmuResourceKey.normalizeKey(resource.title) == normalizedTitle &&
                resource.year == group.year &&
                LocalDanmuResourceKey.normalizeType(resource.type) ==
                LocalDanmuResourceKey.normalizeType(group.type)
        }
        _uiState.update { it.copy(pendingDeleteResources = resources) }
    }

    fun dismissDelete() {
        _uiState.update { it.copy(pendingDeleteResources = emptyList()) }
    }

    fun confirmDelete() {
        val resources = _uiState.value.pendingDeleteResources
        if (resources.isEmpty()) return
        _uiState.update { it.copy(pendingDeleteResources = emptyList(), deleteInProgress = true) }
        viewModelScope.launch {
            repository.deleteMany(resources.map { it.resourceKey }, resources.firstOrNull()?.sessionKey.orEmpty()) { done, total ->
                _uiState.update { it.copy(deleteProgress = "$done/$total") }
            }.fold(
                onSuccess = { count ->
                    _uiState.update {
                        it.copy(
                            deleteInProgress = false,
                            deleteProgress = "",
                            message = "已删除 $count 个文件"
                        )
                    }
                    refreshNow()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            deleteInProgress = false,
                            deleteProgress = "",
                            errorMessage = userMessage(error)
                        )
                    }
                }
            )
        }
    }

    fun enableLocalSource(preferFirst: Boolean) {
        viewModelScope.launch {
            repository.enableLocalSource(preferFirst).fold(
                onSuccess = { value ->
                    _uiState.update { it.copy(message = "已启用本地弹幕源：$value") }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(errorMessage = userMessage(error)) }
                }
            )
        }
    }

    private fun userMessage(error: Throwable): String {
        if (error is LocalDanmuApiException) {
            return when (error.kind) {
                LocalDanmuErrorKind.Unsupported ->
                    "当前核心版本不支持本地弹幕，请先到「核心」页更新核心"
                LocalDanmuErrorKind.Unauthorized ->
                    "令牌校验失败，请检查 TOKEN/ADMIN_TOKEN 配置"
                LocalDanmuErrorKind.Forbidden ->
                    "需要管理员权限：请开启管理员模式，或将 LOCAL_DANMU_NOT_REQUIRE_ADMIN 设为 true"
                LocalDanmuErrorKind.NotFound ->
                    error.message.ifBlank { "资源不存在，列表可能已变化" }
                LocalDanmuErrorKind.Conflict ->
                    error.message.ifBlank { "目标资源已存在，无法覆盖，请改集数或文件名" }
                LocalDanmuErrorKind.FileTooLarge -> "单文件不能超过核心 10 MB 上限"
                LocalDanmuErrorKind.InvalidRequest ->
                    error.message.ifBlank { "请求参数或弹幕文件无效" }
                LocalDanmuErrorKind.Busy -> "核心服务正在处理任务，请稍后重试"
                LocalDanmuErrorKind.Network -> "无法连接本地核心服务，请确认服务已启动"
                LocalDanmuErrorKind.Server -> error.message.ifBlank { "核心服务返回错误" }
            }
        }
        val raw = error.message?.takeIf { it.isNotBlank() }.orEmpty()
        return when {
            error is SecurityException || raw.contains("Permission Denial", ignoreCase = true) ->
                "原文件读取授权已失效，请重新选择该文件（可用「目录浏览」直接挑文件）"
            error is java.io.FileNotFoundException -> "找不到原文件，可能已被移动或删除，请重新选择"
            raw.contains("End of input", ignoreCase = true) ->
                "核心返回了空响应，可能正在重启，请稍后重试"
            raw.isBlank() -> "操作失败，请稍后重试"
            else -> raw
        }
    }

}
