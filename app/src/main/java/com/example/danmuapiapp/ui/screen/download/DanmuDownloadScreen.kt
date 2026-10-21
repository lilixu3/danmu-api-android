package com.example.danmuapiapp.ui.screen.download

import com.example.danmuapiapp.ui.component.AppToast

import com.example.danmuapiapp.ui.component.AppDialog
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.domain.model.*
import com.example.danmuapiapp.domain.model.extractSourceFromAnimeTitle
import com.example.danmuapiapp.ui.component.AnimePosterThumbnail
import com.example.danmuapiapp.ui.common.formatByteSize
import com.example.danmuapiapp.ui.screen.tools.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable fun DanmuDownloadScreen(onBack: () -> Unit, onOpenDownloadSettings: () -> Unit,
    providedViewModel: DanmuDownloadViewModel? = null) {
    val activity = LocalContext.current.downloadActivity()
    val vm = providedViewModel ?: hiltViewModel<DanmuDownloadViewModel>(viewModelStoreOwner = checkNotNull(activity))
    val importer: DownloadImportViewModel = hiltViewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val records by vm.records.collectAsStateWithLifecycle()
    val tasks by vm.queueTasks.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(tab == 0 && vm.currentAnime != null) { vm.backToAnimeList() }
    LaunchedEffect(vm.operationMessage) { vm.operationMessage?.let { AppToast.show(it); vm.dismissMessage() } }
    LaunchedEffect(tab, vm.isSyncingRecords) { if (tab == 2 && !vm.isSyncingRecords) importer.refreshMatches() }
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ToolHeader("弹幕下载", onBack) {
                ToolIcon(Icons.Outlined.Tune, "下载设置", onOpenDownloadSettings, size = 40)
                if (tab == 2) ToolIcon(Icons.Outlined.Sync, "同步已有文件", vm::syncExistingFiles,
                    busy = vm.isSyncingRecords, size = 40)
            }
            val active = tasks.count { it.statusEnum() in setOf(DownloadQueueStatus.Pending, DownloadQueueStatus.Running) }
            val tabs = listOf("搜索下载", if (active > 0) "任务队列 $active" else "任务队列",
                if (records.isEmpty()) "下载记录" else "下载记录 ${records.size}")
            Surface(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.height(36.dp)) {
                    tabs.forEachIndexed { index, label ->
                        Box(Modifier.weight(1f).fillMaxHeight().background(if (tab == index) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { tab = index }, contentAlignment = Alignment.Center) {
                            Text(label, fontSize = 12.sp, color = if (tab == index) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            when (tab) {
                0 -> DownloadSearchContent(vm, records, onOpenDownloadSettings) { tab = 1 }
                1 -> DownloadQueueContent(vm, tasks)
                else -> DownloadRecordsContent(vm, importer, records, settings, onOpenDownloadSettings)
            }
        }
    }
    DownloadImportDialog(importer)
    if (vm.previewDialogState.isVisible) DanmuPreviewDialog(vm.previewDialogState, vm::dismissRecordPreview)
    vm.missingEpisodes?.let { state ->
        AppDialog(onDismissRequest = vm::dismissMissing, scrollContent = false, title = { ToolTitle("刷新《${state.title}》", size = 16) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolHint("源里 ${state.sourceTotal} 集 · 已下载 ${state.downloadedCount} 集 · ${state.way}")
                ToolTitle("源里有、本地还没下载（${state.episodes.size} 集）", size = 12)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolChip("全选", onClick = { vm.selectAllMissing(true) }); ToolChip("全不选", onClick = { vm.selectAllMissing(false) })
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    items(state.episodes, key = { it.episodeId }) { episode ->
                        Row(Modifier.fillMaxWidth().clickable { vm.toggleMissingEpisode(episode.episodeId) }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(episode.episodeId in state.selectedIds, { vm.toggleMissingEpisode(episode.episodeId) }, Modifier.size(36.dp))
                            Text("第${episode.episodeNumber}集", fontSize = 12.sp, modifier = Modifier.width(50.dp))
                            ToolHint(episode.title, Modifier.weight(1f), maxLines = 1)
                        }
                    }
                }
                ToolHint("下载后可用记录旁的导入按钮把新集导入本地库")
            } }, dismissButton = { TextButton(vm::dismissMissing) { Text("取消", fontSize = 12.sp) } },
            confirmButton = { ToolButton("下载 ${state.selectedIds.size} 集", { vm.downloadMissing(); if (vm.missingEpisodes == null) tab = 1 }, enabled = state.selectedIds.isNotEmpty()) })
    }
}
@Composable private fun DownloadSearchContent(vm: DanmuDownloadViewModel, records: List<DanmuDownloadRecord>,
    openSettings: () -> Unit, openQueue: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(14.dp, 6.dp, 14.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ToolHint("下载在后台进行，可以退出本页。保存位置、格式、模板和限速在右上角下载设置里修改。") }
        item {
            ToolCard {
                ToolTitle("搜索动漫")
                ToolInput(vm.keyword, vm::updateKeyword, "例如：凡人修仙传，或粘贴视频链接", icon = Icons.Outlined.Search, onSubmit = vm::searchAnime)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolChip("本机 127.0.0.1", vm.sourceBase.contains("127.0.0.1"), vm::useLocalBase)
                    ToolChip("局域网", !vm.sourceBase.contains("127.0.0.1"), vm::useLanBase)
                }
                ToolButton("搜索", vm::searchAnime, Modifier.fillMaxWidth(), Icons.Outlined.Search, busy = vm.isSearching)
            }
        }
        vm.errorMessage?.let { error -> item { ToolError(error, vm::clearError) } }
        val anime = vm.currentAnime
        if (anime == null) {
            if (vm.animeCandidates.isEmpty()) item { ToolEmpty(if (vm.hasSearchedAnime) "没有搜索结果" else "搜索后选择番剧与剧集", Icons.Outlined.Download,
                "支持批量下载与断点查看记录") }
            items(vm.animeCandidates, key = { it.animeId }) { candidate ->
                ToolCard(onClick = { vm.openAnimeDetail(candidate) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AnimePosterThumbnail(candidate.imageUrl, candidate.title, Modifier.size(58.dp, 82.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ToolTitle(candidate.title, size = 14)
                            ToolHint(listOf(candidate.sourceLabel.ifBlank { extractSourceFromAnimeTitle(candidate.title) }, candidate.typeDescription).filter { it.isNotBlank() }.joinToString(" · "))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ToolHint(if (candidate.episodeCount > 0) "${candidate.episodeCount} 集" else "集数未知", Modifier.weight(1f))
                                Text("选择剧集", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                                Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        } else {
            val downloaded = vm.episodeCandidates.filter { vm.episodeUiState(it).downloadedBefore || vm.episodeUiState(it).state == EpisodeDownloadState.Success }.map { it.episodeId }.toSet()
            item {
                ToolCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ToolTitle(anime.title, Modifier.weight(1f), size = 14)
                        TextButton(vm::backToAnimeList) { Text("返回结果", fontSize = 12.sp) }
                    }
                    ToolHint("共 ${vm.episodeCandidates.size} 集 · 已选 ${vm.selectedEpisodeIds.size} · 已下载 ${vm.episodeCandidates.count { it.episodeId in downloaded }}")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolChip("全选", onClick = vm::selectAllVisible)
                        ToolChip("全不选", onClick = vm::clearSelection)
                        ToolChip("反选", onClick = vm::invertVisibleSelection)
                        ToolChip("只选未下载", onClick = vm::selectUndownloadedEpisodes)
                    }
                    if (vm.sourceOptions().size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ToolChip("全部来源", vm.sourceFilter == null, { vm.selectSourceFilter(null) })
                        vm.sourceOptions().forEach { source -> ToolChip(source, vm.sourceFilter == source, { vm.selectSourceFilter(source) }) }
                    }
                    ToolButton("下载 ${vm.selectedEpisodeIds.size} 集", { vm.startDownloadSelectedEpisodes(); if (vm.errorMessage == null) openQueue() },
                        Modifier.fillMaxWidth(), Icons.Outlined.Download, enabled = vm.selectedEpisodeIds.isNotEmpty())
                    if (vm.settings.value.saveDirPath.isBlank() && vm.settings.value.saveTreeUri.isBlank())
                        TextButton(openSettings) { Text("先选择保存目录", fontSize = 12.sp) }
                }
            }
            if (vm.isLoadingEpisodes) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            items(vm.visibleEpisodes(), key = { it.episodeId }) { episode ->
                val ui = vm.episodeStates[episode.episodeId]
                ToolCard(onClick = { vm.toggleEpisodeSelection(episode.episodeId) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(episode.episodeId in vm.selectedEpisodeIds, { vm.toggleEpisodeSelection(episode.episodeId) }, Modifier.size(34.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ToolTitle("第${episode.episodeNumber}集  ${episode.title}", size = 12)
                            ToolHint(listOf(episode.source, if (episode.episodeId in downloaded) "已下载" else "未下载",
                                ui?.detail.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "))
                        }
                        if (episode.episodeId in downloaded) Icon(Icons.Outlined.TaskAlt, "已下载", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
@Composable private fun DownloadQueueContent(vm: DanmuDownloadViewModel, tasks: List<DanmuDownloadTask>) {
    var expanded by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var deleting by remember { mutableStateOf<String?>(null) }
    LazyColumn(contentPadding = PaddingValues(14.dp, 6.dp, 14.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (tasks.isEmpty()) item { ToolEmpty("还没有下载任务", Icons.Outlined.Queue) }
        else {
            item { ToolCard {
                LinearProgressIndicator(progress = { vm.overallProgress }, modifier = Modifier.fillMaxWidth())
                ToolHint(vm.progressSummary)
                if (vm.activeTaskAnimeTitle.isNotBlank()) ToolHint("${vm.activeTaskAnimeTitle} · 第${vm.activeTaskEpisodeNo ?: "-"}集\n${vm.activeTaskDetail}")
                vm.throttleHint?.let { ToolHint(it) }
            } }
            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.isDownloading) {
                    ToolButton("暂停", vm::pauseDownload, icon = Icons.Outlined.Pause, outlined = true)
                    ToolButton("取消", vm::cancelDownload, icon = Icons.Outlined.StopCircle, outlined = true)
                } else {
                    if (tasks.any { it.statusEnum() == DownloadQueueStatus.Pending }) {
                        ToolButton("继续", vm::resumePendingQueue, icon = Icons.Outlined.PlayArrow)
                        ToolButton("取消", vm::cancelDownload, icon = Icons.Outlined.StopCircle, outlined = true)
                    }
                    if (tasks.any { it.statusEnum() == DownloadQueueStatus.Failed }) ToolButton("重试失败", vm::retryFailedQueueTasks, outlined = true)
                    ToolButton("清空", { deleting = "" }, outlined = true)
                }
            } }
            items(vm.animeQueueGroups(), key = { it.animeTitle }) { group ->
                ToolCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ToolIcon(if (group.animeTitle in expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight, "展开剧集", {
                            expanded = if (group.animeTitle in expanded) expanded - group.animeTitle else expanded + group.animeTitle
                        })
                        ToolTitle(extractAnimeKeywordForSearch(group.animeTitle), Modifier.weight(1f), size = 14)
                        ToolHint("${group.completed}/${group.total}")
                        ToolIcon(Icons.Outlined.DeleteOutline, "删除任务", { deleting = group.animeTitle }, enabled = !vm.isDownloading, danger = true)
                    }
                    LinearProgressIndicator(progress = { group.progress }, modifier = Modifier.fillMaxWidth())
                    if (group.animeTitle in expanded) {
                        ToolHint("成功 ${group.success} · 失败 ${group.failed} · 跳过 ${group.skipped} · 取消 ${group.canceled}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton({ vm.moveQueueGroupUp(group.animeTitle) }) { Text("上移", fontSize = 11.sp) }
                            TextButton({ vm.moveQueueGroupDown(group.animeTitle) }) { Text("下移", fontSize = 11.sp) }
                        }
                        group.episodes.forEach { episode ->
                            ToolListRow("第${episode.episodeNo}集", episode.status.label, episode.episodeTitle, episode.detail)
                        }
                    }
                }
            }
        }
    }
    deleting?.let { title -> AppDialog(onDismissRequest = { deleting = null }, title = { ToolTitle(if (title.isEmpty()) "清空队列" else "删除剧集任务", size = 16) },
        text = { ToolHint("下载记录与磁盘文件会保留。") }, dismissButton = { TextButton({ deleting = null }) { Text("取消") } },
        confirmButton = { ToolButton("删除", { if (title.isEmpty()) vm.clearQueueTasks() else vm.removeQueueGroup(title); deleting = null }) }) }
}
@Composable private fun DownloadRecordsContent(vm: DanmuDownloadViewModel, importer: DownloadImportViewModel,
    records: List<DanmuDownloadRecord>, settings: DanmuDownloadSettings, openSettings: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var deleting by remember { mutableStateOf<List<DanmuDownloadRecord>?>(null) }
    val groups = remember(records) { buildDownloadRecordAnimeGroups(records) }
    LazyColumn(contentPadding = PaddingValues(14.dp, 6.dp, 14.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            ToolHint("共 ${records.size} 条记录 · ${groups.size} 部番剧", Modifier.weight(1f))
            if (records.isNotEmpty()) TextButton({ deleting = records }) { Text("清空记录", fontSize = 12.sp) }
        } }
        vm.errorMessage?.let { error -> item { ToolError(error, vm::clearError) } }
        vm.lastSync?.let { sync -> item { ToolHint("上次同步：扫描 ${sync.scannedFiles} 个 · 匹配 ${sync.matchedFiles} 个 · 新增 ${sync.importedRecords} 条 · 清理 ${sync.removedRecords} 条${if (sync.note.isNotBlank()) "\n${sync.note}" else ""}${if (sync.truncated) "（扫描已达上限）" else ""}") } }
        if (records.isEmpty()) {
            item { ToolEmpty("还没有下载记录", Icons.Outlined.FolderOpen) }
            item { ToolCard {
                ToolTitle("下载目录")
                ToolHint(settings.saveDirPath.ifBlank { settings.saveDirDisplayName.ifBlank { "未设置，右上角下载设置里选择" } })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolButton("重新同步", vm::syncExistingFiles, Modifier.weight(1f), Icons.Outlined.Sync, outlined = true, busy = vm.isSyncingRecords)
                    ToolButton("选择目录", openSettings, Modifier.weight(1f), Icons.Outlined.FolderOpen)
                }
            } }
        }
        items(groups, key = { it.key }) { group ->
            val good = group.records.filter { it.statusEnum() == DownloadRecordStatus.Success }
            val imported = good.isNotEmpty() && good.all { it.id in importer.importedKeys }
            ToolCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ToolIcon(if (group.key in expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight, "展开记录", {
                        expanded = if (group.key in expanded) expanded - group.key else expanded + group.key
                    })
                    ToolTitle(group.title, Modifier.weight(1f), size = 14)
                    ToolHint("${group.records.size} 条")
                    ToolIcon(if (imported) Icons.Outlined.TaskAlt else Icons.Outlined.DownloadForOffline, "整部导入本地弹幕",
                        { importer.open(good, "导入《${extractAnimeKeywordForSearch(group.title)}》") }, enabled = good.isNotEmpty() && !importer.planning)
                    ToolIcon(Icons.Outlined.Refresh, "刷新未下载剧集", { vm.refreshSeries(group) }, busy = vm.refreshingSeriesKey == group.key)
                    ToolIcon(Icons.Outlined.DeleteOutline, "删除整部记录", { deleting = group.records }, enabled = !vm.isDownloading, danger = true)
                }
                ToolHint("成功 ${good.size} · 失败 ${group.failedCount} · 跳过 ${group.skippedCount} · ${formatByteSize(group.totalBytes)}${if (imported) " · 已导入本地库" else ""}")
                if (group.key in expanded) group.records.forEach { record ->
                    val isImported = record.id in importer.importedKeys
                    ToolListRow("第${record.episodeNo}集", record.danmuCount?.let { "$it 条" } ?: record.statusEnum().label,
                        record.fileName.ifBlank { record.episodeTitle },
                        "${downloadTime(record.createdAt)} · ${formatByteSize(record.bytes)}${if (isImported) " · 已导入本地弹幕" else ""}${record.errorMessage?.let { " · $it" }.orEmpty()}",
                        actions = {
                            ToolIcon(Icons.Outlined.Visibility, "预览", { vm.openRecordPreview(record) })
                            ToolIcon(if (isImported) Icons.Outlined.TaskAlt else Icons.Outlined.DownloadForOffline, "导入本地弹幕",
                                { importer.open(listOf(record)) }, enabled = record.statusEnum() == DownloadRecordStatus.Success && !importer.planning)
                            ToolIcon(Icons.Outlined.DeleteOutline, "删除记录", { deleting = listOf(record) }, enabled = !vm.isDownloading, danger = true)
                        })
                }
            }
        }
    }
    deleting?.let { targets ->
        AppDialog(onDismissRequest = { deleting = null }, title = { ToolTitle("删除 ${targets.size} 条下载记录", size = 16) },
            text = { ToolHint("可以只删除记录，或同时删除对应的磁盘文件。导入到本地库的弹幕会保留。") },
            dismissButton = { TextButton({ deleting = null }) { Text("取消", fontSize = 12.sp) } },
            confirmButton = { Row {
                TextButton({ vm.deleteRecords(targets.map { it.id }.toSet(), false); deleting = null }) { Text("只删记录", fontSize = 12.sp) }
                TextButton({ vm.deleteRecords(targets.map { it.id }.toSet(), true); deleting = null }) { Text("同时删文件", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            } })
    }
}
private fun Context.downloadActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.downloadActivity()
    else -> null
}
internal fun downloadTime(time: Long): String = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(time))
