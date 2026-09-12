package com.example.danmuapiapp.ui.screen.localdanmu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.data.repository.LocalDanmuApiException
import com.example.danmuapiapp.data.repository.LocalDanmuErrorKind
import com.example.danmuapiapp.data.repository.LocalDanmuImportManager
import com.example.danmuapiapp.data.repository.LocalDanmuFileBrowser
import com.example.danmuapiapp.data.repository.LocalDanmuImportedIndex
import com.example.danmuapiapp.data.repository.LocalDanmuMetadataResolver
import com.example.danmuapiapp.data.repository.LocalDanmuPreviewLoader
import com.example.danmuapiapp.data.repository.LocalDanmuResourceKey
import com.example.danmuapiapp.data.repository.LocalDanmuSourceStore
import com.example.danmuapiapp.data.repository.LocalDanmuUploadHistoryStore
import com.example.danmuapiapp.domain.model.LocalDanmuGroup
import com.example.danmuapiapp.domain.model.LocalDanmuParseConfidence
import com.example.danmuapiapp.domain.model.LocalDanmuResource
import com.example.danmuapiapp.domain.model.LocalDanmuSnapshot
import com.example.danmuapiapp.domain.model.LocalDanmuSourceKind
import com.example.danmuapiapp.domain.model.LocalDanmuSourceRef
import com.example.danmuapiapp.domain.model.LocalDanmuType
import com.example.danmuapiapp.domain.model.LocalDanmuUploadRequest
import com.example.danmuapiapp.domain.model.LocalDanmuWritePermission
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
import java.io.File
import java.util.Calendar
import javax.inject.Inject

data class LocalDanmuUploadUiState(
    val sourceUri: String = "",
    val displayName: String = "",
    val sizeBytes: Long? = null,
    val formatHint: String = "",
    val mimeType: String = "",
    val title: String = "",
    val year: Int? = Calendar.getInstance().get(Calendar.YEAR),
    val type: LocalDanmuType = LocalDanmuType.Tv,
    val season: Int? = 1,
    val episode: Int? = 1,
    val confidence: LocalDanmuParseConfidence = LocalDanmuParseConfidence.Medium,
    val notes: List<String> = emptyList(),
    val isPreparing: Boolean = false,
    val isUploading: Boolean = false,
    val progress: Float = 0f
) {
    val hasFile: Boolean get() = sourceUri.isNotBlank()
}

data class LocalDanmuUiState(
    val isLoading: Boolean = false,
    val snapshot: LocalDanmuSnapshot = LocalDanmuSnapshot(emptyList(), emptyList()),
    val writePermission: LocalDanmuWritePermission = LocalDanmuWritePermission.ReadOnly,
    val localSourceEnabled: Boolean = false,
    val searchQuery: String = "",
    val typeFilter: String? = null,
    val expandedGroupKeys: Set<String> = emptySet(),
    val message: String? = null,
    val errorMessage: String? = null,
    val upload: LocalDanmuUploadUiState = LocalDanmuUploadUiState(),
    val recentUpload: LocalDanmuUploadHistoryStore.RecentFile? = null,
    /** 上传成功的自增计数，界面据此收起上传表单并回到列表。 */
    val uploadSuccessTick: Int = 0,
    val allFilesAccessGranted: Boolean = false,
    val browser: LocalDanmuBrowserState = LocalDanmuBrowserState(),
    val batch: LocalDanmuBatchState = LocalDanmuBatchState(),
    val detailResource: LocalDanmuResource? = null,
    val previewState: DanmuPreviewDialogState = DanmuPreviewDialogState(),
    val pendingDeleteResources: List<LocalDanmuResource> = emptyList(),
    val deleteInProgress: Boolean = false,
    val deleteProgress: String = ""
) {
    val filteredGroups: List<LocalDanmuGroup>
        get() {
            val query = searchQuery.trim()
            return snapshot.groups.filter { group ->
                val typeMatches = typeFilter == null || group.type == typeFilter
                val queryMatches = query.isBlank() ||
                    group.title.contains(query, ignoreCase = true) ||
                    group.episodes.any { episode ->
                        episode.filename.contains(query, ignoreCase = true) ||
                            episode.episode?.toString() == query
                    }
                typeMatches && queryMatches
            }
        }

    val totalEpisodeFiles: Int get() = snapshot.resources.size
    val totalDanmuCount: Int get() = snapshot.resources.sumOf { it.count }
    val totalSizeBytes: Long get() = snapshot.resources.sumOf { it.sizeBytes }
}

data class LocalDanmuBrowserState(
    val visible: Boolean = false,
    val path: String = "",
    val parent: String? = null,
    val rows: List<LocalDanmuBrowserRow> = emptyList(),
    val isLoading: Boolean = false,
    val truncated: Boolean = false,
    val error: String? = null,
    val selectedPaths: Set<String> = emptySet(),
    val isDefaultDirectory: Boolean = false
) {
    val selectedCount: Int get() = selectedPaths.size
    /** 当前目录下可勾选的文件（不含子目录）。 */
    val selectablePaths: List<String>
        get() = rows.filterNot { it.entry.isDirectory }.map { it.entry.path }
    val allSelected: Boolean
        get() = selectablePaths.isNotEmpty() && selectablePaths.all { it in selectedPaths }
}

@HiltViewModel
class LocalDanmuViewModel @Inject constructor(
    private val repository: LocalDanmuRepository,
    private val importManager: LocalDanmuImportManager,
    private val fileBrowser: LocalDanmuFileBrowser,
    private val sourceStore: LocalDanmuSourceStore,
    private val uploadHistoryStore: LocalDanmuUploadHistoryStore,
    private val previewLoader: LocalDanmuPreviewLoader,
    private val runtimeRepository: RuntimeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocalDanmuUiState())
    val uiState: StateFlow<LocalDanmuUiState> = _uiState.asStateFlow()
    private var importedIndex: Map<String, String> = emptyMap()
    private var batchItemId = 0L
    private var refreshJob: Job? = null

    init {
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

    /** 自动刷新：命中内存快照或直读核心缓存，通常不发（慢的）列表接口。 */
    fun refresh() = refreshInternal(force = false)

    /** 手动刷新（点刷新、导入/删除完成）：强制重新读一次。 */
    fun refreshNow() = refreshInternal(force = true)

    private fun refreshInternal(force: Boolean) {
        // 进入页面时 init 与 ON_START 会各触发一次刷新，这里合并成一次；
        // 连点「重试」也不会堆叠出重复请求。
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    recentUpload = uploadHistoryStore.recent(),
                    allFilesAccessGranted = fileBrowser.isAllFilesAccessGranted()
                )
            }
            repository.refresh(force = force).fold(
                onSuccess = { snapshot ->
                    importedIndex = LocalDanmuImportedIndex.build(
                        resources = snapshot.resources,
                        sources = sourceStore.all()
                    )
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            snapshot = snapshot,
                            errorMessage = null,
                            browser = it.browser.copy(
                                rows = remapBrowserRows(it.browser.rows, it.browser.selectedPaths)
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

    // ───────────────────────── 手动单文件上传 ─────────────────────────

    fun selectUploadFile(uri: String) {
        if (uri.isBlank()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    upload = LocalDanmuUploadUiState(isPreparing = true),
                    errorMessage = null
                )
            }
            importManager.prepare(uri).fold(
                onSuccess = { prepared -> applyPreparedFile(prepared) },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            upload = LocalDanmuUploadUiState(),
                            errorMessage = userMessage(error)
                        )
                    }
                }
            )
        }
    }

    /** 复用上一次选中的文件（授权已持久化），省去重新翻目录。 */
    fun reuseRecentUpload() {
        val recent = _uiState.value.recentUpload ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    upload = LocalDanmuUploadUiState(isPreparing = true),
                    errorMessage = null
                )
            }
            importManager.prepare(recent.uri).fold(
                onSuccess = { prepared -> applyPreparedFile(prepared) },
                onFailure = {
                    uploadHistoryStore.clear()
                    _uiState.update { state ->
                        state.copy(
                            upload = LocalDanmuUploadUiState(),
                            recentUpload = null,
                            errorMessage = "上次选择的文件已不可用，请重新选择"
                        )
                    }
                }
            )
        }
    }

    // ───────────────────── 目录直读模式（所有文件访问） ─────────────────────

    fun refreshAllFilesAccess() {
        _uiState.update { it.copy(allFilesAccessGranted = fileBrowser.isAllFilesAccessGranted()) }
    }

    fun openDirectoryBrowser() {
        val start = fileBrowser.preferredDirectory(
            remembered = uploadHistoryStore.recentDirectory(),
            custom = uploadHistoryStore.customDirectory()
        )
        _uiState.update { it.copy(browser = it.browser.copy(visible = true)) }
        browseDirectory(start)
    }

    fun browseDirectory(path: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    browser = it.browser.copy(
                        visible = true,
                        isLoading = true,
                        path = fileBrowser.normalize(path),
                        error = null
                    )
                )
            }
            fileBrowser.list(path).fold(
                onSuccess = { snapshot ->
                    uploadHistoryStore.saveRecentDirectory(snapshot.path)
                    _uiState.update {
                        it.copy(
                            browser = it.browser.copy(
                                visible = true,
                                isLoading = false,
                                path = snapshot.path,
                                parent = snapshot.parent,
                                rows = snapshot.entries.map { entry -> toBrowserRow(entry, it.browser.selectedPaths) },
                                truncated = snapshot.truncated,
                                error = null,
                                isDefaultDirectory = snapshot.path == defaultDirectory()
                            )
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            browser = it.browser.copy(
                                isLoading = false,
                                error = error.message?.takeIf { message -> message.isNotBlank() }
                                    ?: "无法读取该目录"
                            )
                        )
                    }
                }
            )
        }
    }

    fun browseParent() {
        val parent = _uiState.value.browser.parent ?: return
        browseDirectory(parent)
    }

    fun browseDefaultDirectory() {
        browseDirectory(defaultDirectory())
    }

    fun closeDirectoryBrowser() {
        _uiState.update {
            it.copy(
                browser = it.browser.copy(
                    visible = false,
                    error = null,
                    selectedPaths = emptySet()
                )
            )
        }
    }

    fun toggleBrowserSelection(path: String) {
        _uiState.update { state ->
            val selected = state.browser.selectedPaths.toMutableSet()
            if (!selected.add(path)) selected.remove(path)
            state.copy(
                browser = state.browser.copy(
                    selectedPaths = selected,
                    rows = state.browser.rows.map { row ->
                        if (row.entry.isDirectory) {
                            row
                        } else {
                            row.copy(selected = row.entry.path in selected)
                        }
                    }
                )
            )
        }
    }

    /** 全选 / 取消全选当前目录下的文件（不含子目录）。 */
    fun toggleBrowserSelectAll() {
        _uiState.update { state ->
            val paths = state.browser.selectablePaths
            if (paths.isEmpty()) return@update state
            val selected = if (state.browser.allSelected) {
                state.browser.selectedPaths - paths.toSet()
            } else {
                state.browser.selectedPaths + paths
            }
            state.copy(
                browser = state.browser.copy(
                    selectedPaths = selected,
                    rows = state.browser.rows.map { row ->
                        if (row.entry.isDirectory) {
                            row
                        } else {
                            row.copy(selected = row.entry.path in selected)
                        }
                    }
                )
            )
        }
    }

    fun setBrowserDefaultDirectory() {
        val path = _uiState.value.browser.path
        if (path.isBlank()) return
        uploadHistoryStore.saveCustomDirectory(path)
        _uiState.update {
            it.copy(
                browser = it.browser.copy(isDefaultDirectory = true),
                message = "已把该目录设为默认浏览目录"
            )
        }
    }

    /** 目录多选 → 批量导入面板。 */
    fun importSelectedBrowserFiles() {
        val paths = _uiState.value.browser.selectedPaths.toList()
        if (paths.isEmpty()) return
        closeDirectoryBrowser()
        addBatchSources(sources = paths) { path -> importManager.preparePath(path) }
    }

    // ───────────────────────────── 批量导入 ─────────────────────────────

    fun addBatchUris(uris: List<String>) {
        val distinct = uris.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (distinct.isEmpty()) return
        addBatchSources(sources = distinct) { uri -> importManager.prepare(uri) }
    }

    private fun addBatchSources(
        sources: List<String>,
        prepare: suspend (String) -> Result<LocalDanmuImportManager.PreparedFile>
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    batch = it.batch.copy(visible = true, isRunning = true, cancelRequested = false),
                    errorMessage = null
                )
            }
            val items = mutableListOf<LocalDanmuBatchItem>()
            val failures = mutableListOf<String>()
            sources.forEach { source ->
                prepare(source).fold(
                    onSuccess = { prepared -> items += batchItemOf(prepared) },
                    onFailure = { error ->
                        failures += "${source.substringAfterLast('/')}：${
                            error.message?.takeIf { it.isNotBlank() } ?: "无法读取文件"
                        }"
                    }
                )
            }
            val prepared = markBatchDuplicates(items)
            _uiState.update { state ->
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

    private fun batchItemOf(prepared: LocalDanmuImportManager.PreparedFile): LocalDanmuBatchItem {
        val parsed = LocalDanmuMetadataResolver.resolve(
            fileName = prepared.displayName,
            relativePath = prepared.displayName
        )
        return LocalDanmuBatchItem(
            id = ++batchItemId,
            sourceUri = prepared.sourceUri,
            displayName = prepared.displayName,
            sizeBytes = prepared.sizeBytes,
            formatHint = prepared.formatHint,
            mimeType = prepared.mimeType,
            title = parsed.title,
            year = parsed.year ?: Calendar.getInstance().get(Calendar.YEAR),
            type = LocalDanmuType.fromWire(parsed.type) ?: LocalDanmuType.Tv,
            season = parsed.season ?: 1,
            episode = parsed.episode ?: 1,
            confidence = parsed.confidence,
            importedLabel = LocalDanmuImportedIndex.lookup(
                index = importedIndex,
                path = LocalDanmuImportedIndex.pathFromUri(prepared.sourceUri).orEmpty(),
                name = prepared.displayName,
                sizeBytes = prepared.sizeBytes ?: 0L
            )
        )
    }

    fun toggleBatchSelected(id: Long) {
        updateBatchItem(id) {
            if (it.status == LocalDanmuBatchStatus.Uploading) it else it.copy(selected = !it.selected)
        }
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
        _uiState.update { state ->
            state.copy(batch = state.batch.copy(items = state.batch.items.filterNot { it.id == id }))
        }
    }

    fun closeBatch() {
        if (_uiState.value.batch.isRunning) return
        _uiState.update { it.copy(batch = LocalDanmuBatchState()) }
    }

    fun cancelBatchImport() {
        _uiState.update { it.copy(batch = it.batch.copy(cancelRequested = true)) }
    }

    fun retryFailedBatch() {
        if (_uiState.value.batch.isRunning) return
        _uiState.update { state ->
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
        if (_uiState.value.batch.isRunning) return
        val targets = _uiState.value.batch.items.filter { it.canImport }
        if (targets.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "没有可导入的条目，请检查标题、年份、季集") }
            return
        }
        viewModelScope.launch {
            _uiState.update {
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
                if (_uiState.value.batch.cancelRequested) break
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
                _uiState.update { it.copy(batch = it.batch.copy(completed = it.batch.completed + 1)) }
            }
            val canceled = _uiState.value.batch.cancelRequested
            refreshNow()
            _uiState.update {
                it.copy(
                    batch = it.batch.copy(isRunning = false, cancelRequested = false),
                    message = when {
                        canceled -> "已取消批量导入：成功 $success 个，失败 $failed 个"
                        failed == 0 -> "批量导入完成：成功 $success 个"
                        else -> "批量导入完成：成功 $success 个，失败 $failed 个"
                    }
                )
            }
        }
    }

    private fun updateBatchItem(
        id: Long,
        transform: (LocalDanmuBatchItem) -> LocalDanmuBatchItem
    ) {
        _uiState.update { state ->
            state.copy(
                batch = state.batch.copy(
                    items = state.batch.items.map { if (it.id == id) transform(it) else it }
                )
            )
        }
    }

    private fun defaultDirectory(): String = fileBrowser.preferredDirectory(
        remembered = uploadHistoryStore.recentDirectory(),
        custom = uploadHistoryStore.customDirectory()
    )

    private fun toBrowserRow(
        entry: LocalDanmuFileBrowser.Entry,
        selectedPaths: Set<String>
    ): LocalDanmuBrowserRow {
        val imported = if (entry.isDirectory) {
            null
        } else {
            LocalDanmuImportedIndex.lookup(
                index = importedIndex,
                path = entry.path,
                name = entry.name,
                sizeBytes = entry.sizeBytes
            )
        }
        return LocalDanmuBrowserRow(
            entry = entry,
            importedLabel = imported,
            selected = entry.path in selectedPaths
        )
    }

    private fun remapBrowserRows(
        rows: List<LocalDanmuBrowserRow>,
        selectedPaths: Set<String>
    ): List<LocalDanmuBrowserRow> = rows.map { row -> toBrowserRow(row.entry, selectedPaths) }

    private fun applyPreparedFile(prepared: LocalDanmuImportManager.PreparedFile) {
        val parsed = LocalDanmuMetadataResolver.resolve(
            fileName = prepared.displayName,
            relativePath = prepared.displayName
        )
        val recent = LocalDanmuUploadHistoryStore.RecentFile(
            uri = prepared.sourceUri,
            displayName = prepared.displayName,
            sizeBytes = prepared.sizeBytes ?: 0L,
            formatHint = prepared.formatHint,
            mimeType = prepared.mimeType
        )
        uploadHistoryStore.save(recent)
        _uiState.update {
            it.copy(
                upload = LocalDanmuUploadUiState(
                    sourceUri = prepared.sourceUri,
                    displayName = prepared.displayName,
                    sizeBytes = prepared.sizeBytes,
                    formatHint = prepared.formatHint,
                    mimeType = prepared.mimeType,
                    title = parsed.title,
                    year = parsed.year ?: Calendar.getInstance().get(Calendar.YEAR),
                    type = LocalDanmuType.fromWire(parsed.type) ?: LocalDanmuType.Tv,
                    season = parsed.season ?: 1,
                    episode = parsed.episode ?: 1,
                    confidence = parsed.confidence,
                    notes = parsed.notes,
                    isPreparing = false
                ),
                recentUpload = uploadHistoryStore.recent(),
                errorMessage = null
            )
        }
    }

    fun updateUploadTitle(value: String) {
        updateUpload { it.copy(title = value, confidence = LocalDanmuParseConfidence.Medium) }
    }

    fun updateUploadYear(value: String) {
        updateUpload {
            it.copy(
                year = value.filter(Char::isDigit).take(4).toIntOrNull(),
                confidence = LocalDanmuParseConfidence.Medium
            )
        }
    }

    fun updateUploadType(value: LocalDanmuType) {
        updateUpload {
            it.copy(
                type = value,
                season = if (value == LocalDanmuType.Movie) null else it.season ?: 1,
                episode = if (value == LocalDanmuType.Movie) null else it.episode ?: 1,
                confidence = LocalDanmuParseConfidence.Medium
            )
        }
    }

    fun updateUploadSeason(value: String) {
        updateUpload {
            it.copy(
                season = value.filter(Char::isDigit).take(3).toIntOrNull(),
                confidence = LocalDanmuParseConfidence.Medium
            )
        }
    }

    fun updateUploadEpisode(value: String) {
        updateUpload {
            it.copy(
                episode = value.filter(Char::isDigit).take(4).toIntOrNull(),
                confidence = LocalDanmuParseConfidence.Medium
            )
        }
    }

    fun clearUploadDraft() {
        _uiState.update { it.copy(upload = LocalDanmuUploadUiState()) }
    }

    fun uploadCurrent() {
        val state = _uiState.value
        val upload = state.upload
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val validation = LocalDanmuResourceKey.validateUpload(
            title = upload.title,
            year = upload.year,
            type = upload.type,
            season = upload.season,
            episode = upload.episode,
            currentYear = currentYear
        )
        if (validation != null) {
            _uiState.update { it.copy(errorMessage = validation) }
            return
        }
        if (!upload.hasFile) {
            _uiState.update { it.copy(errorMessage = "请选择要上传的弹幕文件") }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(upload = upload.copy(isUploading = true, progress = 0f), errorMessage = null)
            }
            val year = upload.year ?: return@launch
            uploadPreparedFile(
                sourceUri = upload.sourceUri,
                displayName = upload.displayName,
                year = year,
                type = upload.type,
                season = upload.season,
                episode = upload.episode,
                title = upload.title,
                formatHint = upload.formatHint,
                sizeBytes = upload.sizeBytes,
                mimeType = upload.mimeType,
                onProgress = { progress ->
                    val previous = _uiState.value.upload.progress
                    if (progress >= 1f || progress - previous >= 0.05f) {
                        updateUpload { it.copy(progress = progress) }
                    }
                }
            )
                .fold(
                onSuccess = { resource ->
                    _uiState.update {
                        it.copy(
                            upload = LocalDanmuUploadUiState(),
                            recentUpload = uploadHistoryStore.recent(),
                            uploadSuccessTick = it.uploadSuccessTick + 1,
                            message = uploadSuccessMessage(upload, resource)
                        )
                    }
                    refreshNow()
                },
                onFailure = { error ->
                    updateUpload { it.copy(isUploading = false, progress = 0f) }
                    _uiState.update { it.copy(errorMessage = userMessage(error)) }
                }
            )
        }
    }

    // ───────────────────────── 详情/预览/删除 ─────────────────────────

    fun loadDetail(resourceKey: String) {
        val cached = _uiState.value.snapshot.resources.firstOrNull { it.resourceKey == resourceKey }
        if (cached != null) _uiState.update { it.copy(detailResource = cached) }
        viewModelScope.launch {
            repository.loadDetail(resourceKey).fold(
                onSuccess = { resource -> _uiState.update { it.copy(detailResource = resource) } },
                onFailure = { error ->
                    if (cached == null) {
                        _uiState.update { it.copy(errorMessage = userMessage(error)) }
                    }
                }
            )
        }
    }

    fun dismissDetail() {
        _uiState.update { it.copy(detailResource = null) }
    }

    fun loadPreview(resourceKey: String) {
        _uiState.update {
            it.copy(previewState = DanmuPreviewDialogState(loadingRecordId = -1L))
        }
        viewModelScope.launch {
            previewLoader.load(resourceKey).fold(
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
            repository.deleteMany(resources.map { it.resourceKey }) { done, total ->
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

    private fun updateUpload(transform: (LocalDanmuUploadUiState) -> LocalDanmuUploadUiState) {
        _uiState.update { it.copy(upload = transform(it.upload)) }
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
                LocalDanmuErrorKind.NotFound -> "资源不存在，列表可能已变化"
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

    private fun uploadSuccessMessage(
        upload: LocalDanmuUploadUiState,
        resource: LocalDanmuResource
    ): String {
        val title = upload.title.trim().ifBlank { resource.title }
        val episodeLabel = when {
            upload.type == LocalDanmuType.Movie -> "正片"
            upload.episode != null && upload.season != null ->
                "第${upload.season}季 第${upload.episode}集"
            upload.episode != null -> "第${upload.episode}集"
            else -> "全集"
        }
        return "上传成功：$title $episodeLabel · ${resource.count} 条弹幕"
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
        onProgress: (Float) -> Unit
    ): Result<LocalDanmuResource> {
        val request = LocalDanmuUploadRequest(
            title = title.trim(),
            year = year,
            type = type,
            season = season,
            episode = episode,
            filename = displayName,
            stagedFile = null,
            sourceUri = sourceUri,
            contentLength = sizeBytes,
            mimeType = mimeType
        )
        return repository.upload(request, onProgress).onSuccess { resource ->
            sourceStore.save(
                LocalDanmuSourceRef(
                    resourceKey = resource.resourceKey,
                    kind = LocalDanmuSourceKind.File,
                    uri = sourceUri,
                    displayName = displayName,
                    format = formatHint,
                    sizeBytes = sizeBytes ?: 0L
                )
            )
        }
    }
}
