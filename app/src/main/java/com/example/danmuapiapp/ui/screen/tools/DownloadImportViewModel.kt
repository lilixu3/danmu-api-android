package com.example.danmuapiapp.ui.screen.tools

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.data.repository.*
import com.example.danmuapiapp.domain.model.*
import com.example.danmuapiapp.domain.repository.*
import com.example.danmuapiapp.ui.screen.localdanmu.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class DownloadImportViewModel @Inject constructor(
    private val downloads: DanmuDownloadRepository,
    private val local: LocalDanmuRepository,
    private val uploader: LocalDanmuUploadService
) : ViewModel() {
    val records = downloads.records
    var batch by mutableStateOf(LocalDanmuBatchState())
        private set
    var title by mutableStateOf("导入本地弹幕")
        private set
    var blocked by mutableStateOf<List<String>>(emptyList())
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var planning by mutableStateOf(false)
        private set
    var importedKeys by mutableStateOf<Map<Long, String>>(emptyMap())
        private set
    var completedVersion by mutableIntStateOf(0)
        private set

    fun refreshMatches() {
        viewModelScope.launch {
            val resources = local.refresh(force = true).getOrNull()?.resources ?: return@launch
            val existing = DownloadLocalImportPlanner.existingKeys(resources)
            importedKeys = downloads.records.value.mapNotNull { record ->
                existing[DownloadLocalImportPlanner.key(record)]?.let { record.id to it }
            }.toMap()
            downloads.markRecordsImported(importedKeys.filterKeys { id -> downloads.records.value.any { it.id == id && !it.importedToLocal } })
        }
    }
    fun open(records: List<DanmuDownloadRecord>, label: String = "导入本地弹幕") {
        if (planning || batch.isRunning) return
        planning = true
        error = null
        title = label
        viewModelScope.launch {
            try {
                val existing = local.refresh(force = true).getOrNull()?.resources?.let(DownloadLocalImportPlanner::existingKeys).orEmpty()
                val plans = records.map { DownloadLocalImportPlanner.plan(it, existing) }
                blocked = plans.filter { it.blockedReason.isNotBlank() }.map { "第${it.record.episodeNo}集：${it.blockedReason}" }
                batch = LocalDanmuBatchState(visible = true, items = markBatchDuplicates(plans.mapNotNull { it.item }))
            } finally { planning = false }
        }
    }
    fun dismiss() { if (!batch.isRunning) { batch = LocalDanmuBatchState(); blocked = emptyList(); error = null } }
    fun toggle(id: Long) { update(id) { it.copy(selected = !it.selected) } }
    fun edit(id: Long, title: String, year: Int?, type: LocalDanmuType, season: Int?, episode: Int?) {
        update(id) { it.copy(title = title, year = year, type = type, season = season,
            episode = if (type == LocalDanmuType.Movie) null else episode, status = LocalDanmuBatchStatus.Ready, message = "", selected = true) }
    }
    fun cancel() { batch = batch.copy(cancelRequested = true) }
    fun retry() {
        batch = batch.copy(items = batch.items.map { if (it.status == LocalDanmuBatchStatus.Failed)
            it.copy(status = LocalDanmuBatchStatus.Ready, selected = true) else it })
        run()
    }
    private fun update(id: Long, change: (LocalDanmuBatchItem) -> LocalDanmuBatchItem) {
        batch = batch.copy(items = batch.items.map { if (it.id == id) change(it) else it })
    }
    fun run() {
        if (batch.isRunning) return
        val targets = batch.items.filter { it.canImport }
        if (targets.isEmpty()) { error = "没有可导入的条目，请订正年份 / 季 / 集数，或勾选已导入项重新导入"; return }
        batch = batch.copy(isRunning = true, cancelRequested = false, completed = 0, total = targets.size)
        viewModelScope.launch {
            try {
                for (item in targets) {
                    if (batch.cancelRequested) break
                    update(item.id) { it.copy(status = LocalDanmuBatchStatus.Uploading, message = "上传中") }
                    uploader.upload(item) { p -> update(item.id) { it.copy(progress = p) } }.fold(
                        onSuccess = { resource ->
                            downloads.markRecordsImported(mapOf(item.id to resource.resourceKey))
                            importedKeys = importedKeys + (item.id to resource.resourceKey)
                            update(item.id) { it.copy(status = LocalDanmuBatchStatus.Success, progress = 1f, message = "已导入 ${resource.count} 条") }
                            if (downloads.settings.value.deleteSourceAfterImport) {
                                downloads.deleteDownloadedFiles(setOf(item.id)).exceptionOrNull()?.let { failure ->
                                    update(item.id) { it.copy(message = "已导入；${failure.message}") }
                                }
                            }
                        },
                        onFailure = { failure -> update(item.id) { it.copy(status = LocalDanmuBatchStatus.Failed, message = failure.message ?: "导入失败") } })
                    batch = batch.copy(completed = batch.completed + 1)
                }
            } finally {
                batch = batch.copy(isRunning = false, cancelRequested = false)
                completedVersion++
            }
        }
    }
}
