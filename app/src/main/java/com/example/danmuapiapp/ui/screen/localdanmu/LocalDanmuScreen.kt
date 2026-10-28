package com.example.danmuapiapp.ui.screen.localdanmu

import com.example.danmuapiapp.ui.component.AppToast

import com.example.danmuapiapp.ui.component.AppDialog
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.domain.model.*
import com.example.danmuapiapp.ui.component.AdminModeRequiredDialog
import com.example.danmuapiapp.ui.component.AdminModeRequiredTarget
import com.example.danmuapiapp.ui.component.adminModeRequiredPrompt
import com.example.danmuapiapp.ui.common.formatByteSize
import com.example.danmuapiapp.ui.screen.download.DanmuPreviewDialog
import com.example.danmuapiapp.ui.screen.download.buildDownloadRecordAnimeGroups
import com.example.danmuapiapp.ui.screen.tools.*

@Composable
fun LocalDanmuScreen(
    onBack: () -> Unit,
    onOpenAdminMode: () -> Unit,
    onOpenConfig: () -> Unit,
    viewModel: LocalDanmuViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showAdminPrompt by remember { mutableStateOf(false) }
    var showAllFilesAccessPrompt by remember { mutableStateOf(false) }
    val importer: DownloadImportViewModel = hiltViewModel()
    val downloads by importer.records.collectAsStateWithLifecycle()
    var chooseDownloads by remember { mutableStateOf(false) }

    val allFilesAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshAllFilesAccess()
        if (viewModel.uiState.value.allFilesAccessGranted) {
            viewModel.openDirectoryBrowser()
        }
    }

    // Android 10 及以下没有「所有文件访问」，用运行时权限兜底。
    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshAllFilesAccess()
        if (viewModel.uiState.value.allFilesAccessGranted) {
            viewModel.openDirectoryBrowser()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.refresh()
                viewModel.refreshAllFilesAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val requestAllFilesAccess: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val appIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = "package:${context.packageName}".toUri()
            }
            val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            runCatching { allFilesAccessLauncher.launch(appIntent) }.getOrElse {
                runCatching { allFilesAccessLauncher.launch(fallbackIntent) }.onFailure {
                    viewModel.showError("无法打开「所有文件访问」设置页")
                }
            }
        } else {
            legacyStoragePermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            AppToast.show(message)
            viewModel.dismissMessage()
        }
    }

    val requireWrite: (() -> Unit) -> Unit = { action ->
        if (state.writePermission == LocalDanmuWritePermission.Writable) {
            action()
        } else {
            showAdminPrompt = true
        }
    }

    // 唯一的导入入口：授权后进入文件列表勾选，未授权则弹授权提示。
    val openImportFlow: () -> Unit = {
        if (state.allFilesAccessGranted) {
            viewModel.openDirectoryBrowser()
        } else {
            showAllFilesAccessPrompt = true
        }
    }

    LaunchedEffect(importer.completedVersion) {
        if (importer.completedVersion > 0) viewModel.refreshNow()
    }
    LaunchedEffect(chooseDownloads, state.snapshot) {
        if (chooseDownloads) importer.refreshMatches()
    }
    DownloadImportDialog(importer)
    if (chooseDownloads) {
        val groups = buildDownloadRecordAnimeGroups(downloads.filter { it.statusEnum() == DownloadRecordStatus.Success })
        AppDialog(onDismissRequest = { chooseDownloads = false }, scrollContent = false, title = { ToolTitle("从下载记录导入", size = 16) },
            text = {
                if (groups.isEmpty()) ToolHint("还没有成功的下载记录，先在弹幕下载里下载或同步下载目录。")
                else LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(groups, key = { it.key }) { group ->
                        ToolListRow("${group.records.size} 集", "当前库已导入 ${group.records.count { it.id in importer.importedKeys }}",
                            group.title, "点此选择并订正导入信息", { chooseDownloads = false; importer.open(group.records, "导入《${com.example.danmuapiapp.ui.screen.download.extractAnimeKeywordForSearch(group.title)}》") },
                            actions = { Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp)) })
                    }
                }
            }, confirmButton = { TextButton({ chooseDownloads = false }) { Text("取消") } })
    }
    LocalDanmuDialogHost(state, showAdminPrompt, showAllFilesAccessPrompt, onOpenAdminMode,
        { showAdminPrompt = false }, { showAllFilesAccessPrompt = false }, requestAllFilesAccess,
        viewModel::loadPreview, viewModel::dismissDetail,
        { resource -> viewModel.dismissDetail(); viewModel.requestDelete(resource) }, viewModel::confirmDelete,
        viewModel::dismissDelete, viewModel::dismissPreview)
    if (state.editState.isVisible) LocalDanmuEditDialog(state.editState,
        state.editState.target?.let { localDanmuGroupResources(state.snapshot.resources, it).size.coerceAtLeast(1) } ?: 1,
        viewModel::dismissEdit, viewModel::setEditScope, viewModel::updateEditTitle, viewModel::updateEditYear,
        viewModel::updateEditType, viewModel::updateEditSeason, viewModel::updateEditEpisode,
        viewModel::updateEditFilename, viewModel::submitEdit)
    if (state.browser.visible) {
        LocalDanmuDirectoryBrowserPanel(state.browser, viewModel::closeDirectoryBrowser, viewModel::browseParent,
            viewModel::browseDefaultDirectory, viewModel::selectBrowserPaths, viewModel::setBrowserDefaultDirectory,
            viewModel::toggleBrowserSelection, viewModel::importSelectedBrowserFiles,
            { viewModel.browseDirectory(state.browser.path) }, viewModel::browseDirectory)
        return
    }
    if (state.batch.visible) {
        LocalDanmuBatchPanel(state.batch, state.errorMessage, viewModel::closeBatch, viewModel::toggleBatchSelected,
            viewModel::removeBatchItem, viewModel::updateBatchItemMetadata, viewModel::runBatchImport,
            viewModel::cancelBatchImport, viewModel::retryFailedBatch, viewModel::dismissError, viewModel::setBatchType, viewModel::selectBatchItems)
        return
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ToolHeader("本地弹幕", onBack,
                "${state.totalEpisodeFiles} 个文件 · ${state.totalDanmuCount} 条弹幕 · ${formatByteSize(state.totalSizeBytes)}") {
                ToolIcon(Icons.Outlined.DownloadForOffline, "从下载记录导入", { requireWrite { chooseDownloads = true } }, size = 36)
                ToolIcon(Icons.Outlined.UploadFile, "上传弹幕文件", { requireWrite(openImportFlow) }, size = 36)
                ToolIcon(Icons.Outlined.Refresh, "刷新", viewModel::refreshNow, busy = state.isLoading, size = 36)
            }
            if (!state.localSourceEnabled && !state.isLoading) {
                ToolCard(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    ToolTitle("本地弹幕源未启用")
                    ToolHint("播放器尚未使用本地弹幕。选择优先使用本地库，或在其它来源找不到弹幕时使用。")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolButton("优先本地", { requireWrite { viewModel.enableLocalSource(true) } }, icon = Icons.Outlined.VerticalAlignTop, outlined = true)
                        ToolButton("本地兜底", { requireWrite { viewModel.enableLocalSource(false) } }, icon = Icons.Outlined.VerticalAlignBottom, outlined = true)
                    }
                }
            }
            ToolInput(state.searchQuery, viewModel::setSearchQuery, "搜索剧名或文件名",
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp), Icons.Outlined.Search)
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolChip("全部", state.typeFilter == null, { viewModel.setTypeFilter(null) })
                ToolChip("电视剧", state.typeFilter == "tv", { viewModel.setTypeFilter("tv") })
                ToolChip("电影", state.typeFilter == "movie", { viewModel.setTypeFilter("movie") })
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.errorMessage?.let { error -> item { ToolCard {
                    ToolError(error, viewModel::dismissError)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolButton("重试", viewModel::refreshNow, outlined = true)
                        ToolButton("配置", onOpenConfig, outlined = true)
                        ToolButton("重启服务", viewModel::restartService, outlined = true)
                    }
                } } }
                if (state.writePermission != LocalDanmuWritePermission.Writable) item {
                    ToolCard { ToolHint("当前只读，上传、编辑与删除需要管理员权限。")
                        ToolButton("管理员模式", onOpenAdminMode, outlined = true) }
                }
                if (state.deleteInProgress) item { ToolCard { LinearProgressIndicator(Modifier.fillMaxWidth()); ToolHint(state.deleteProgress) } }
                if (state.filteredGroups.isEmpty() && !state.isLoading) item { ToolEmpty(
                    if (state.searchQuery.isBlank()) "还没有本地弹幕" else "没有匹配的资源", Icons.Outlined.FolderOpen,
                    "右上角可上传文件，或从下载记录快速导入") }
                items(state.filteredGroups, key = { it.groupKey }) { group ->
                    val expanded = group.groupKey in state.expandedGroupKeys
                    ToolCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ToolIcon(if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                                "展开剧集", { viewModel.toggleGroup(group.groupKey) })
                            Column(Modifier.weight(1f).clickable { viewModel.toggleGroup(group.groupKey) }) {
                                ToolTitle(group.title, size = 14, maxLines = 1)
                                ToolHint("${group.year ?: "未知年份"} · ${LocalDanmuType.fromWire(group.type)?.label ?: group.type} · 第${group.season}季 · ${group.episodes.size} 集")
                            }
                            ToolHint("${group.count} 条")
                            ToolIcon(Icons.Outlined.Edit, "编辑整个分组", { requireWrite { viewModel.startEditGroup(group) } })
                            ToolIcon(Icons.Outlined.DeleteOutline, "删除整个分组", { requireWrite { viewModel.requestDeleteGroup(group) } }, danger = true)
                        }
                        if (expanded) group.episodes.forEach { episode ->
                            ToolListRow(if (episode.type == "movie") "剧场版" else "第${episode.episode ?: "-"}集", "${episode.count} 条",
                                episode.filename.ifBlank { episode.resourceKey },
                                "${episode.format.uppercase()} · ${formatByteSize(episode.sizeBytes)} · ${if (episode.status == "ready") "可用" else episode.status}",
                                { viewModel.loadDetail(episode.resourceKey) }, actions = {
                                    ToolIcon(Icons.Outlined.Visibility, "预览", { viewModel.loadDetail(episode.resourceKey) })
                                    ToolIcon(Icons.Outlined.Edit, "编辑这一集", { requireWrite { viewModel.startEdit(episode) } })
                                    ToolIcon(Icons.Outlined.DeleteOutline, "删除这一集", { requireWrite { viewModel.requestDelete(episode) } }, danger = true)
                                })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalDanmuDialogHost(state: LocalDanmuUiState, showAdminPrompt: Boolean, showAllFilesAccessPrompt: Boolean,
    onOpenAdminMode: () -> Unit, onDismissAdminPrompt: () -> Unit, onDismissAllFilesAccessPrompt: () -> Unit,
    onRequestAllFilesAccess: () -> Unit, onPreview: (String) -> Unit, onDismissDetail: () -> Unit,
    onDelete: (LocalDanmuResource) -> Unit, onConfirmDelete: () -> Unit, onDismissDelete: () -> Unit, onDismissPreview: () -> Unit) {
    state.detailResource?.let { resource ->
        AppDialog(onDismissRequest = { onDismissPreview(); onDismissDetail() }, scrollContent = false,
            title = { ToolTitle(resource.filename.ifBlank { resource.episodeLabel }, size = 15) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(resource.title, resource.typeLabel, resource.year.toString(), "第${resource.season}季", resource.episodeLabel)
                        .forEach { label -> ToolChip(label, onClick = {}) }
                }
                ToolHint("弹幕 ${resource.count} 条 · 格式 ${resource.format.ifBlank { "未知" }} · ${formatByteSize(resource.sizeBytes)}")
                if (state.previewState.loadingRecordId != null) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                state.previewState.error?.let { ToolError(it) }
                state.previewState.preview?.let { preview ->
                    ToolTitle("弹幕预览（前 50 条）", size = 12)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                        items(preview.items.take(50), key = { it.index }) { item ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val raw = if (item.color.startsWith('#')) item.color.drop(1).toLongOrNull(16) else item.color.toLongOrNull()
                                val color = if (raw == null || raw == 0xffffffL) MaterialTheme.colorScheme.outlineVariant else Color((raw or 0xff000000L).toInt())
                                Box(Modifier.size(9.dp).background(color, androidx.compose.foundation.shape.CircleShape))
                                val seconds = (item.timeSeconds ?: 0.0).toLong().coerceAtLeast(0)
                                Text("%02d:%02d".format(seconds / 60, seconds % 60), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(item.text, Modifier.weight(1f), fontSize = 11.sp)
                            }
                        }
                    }
                }
            } }, dismissButton = {
                if (state.previewState.preview == null && state.previewState.loadingRecordId == null)
                    TextButton({ onPreview(resource.resourceKey) }) { Text("预览") }
            }, confirmButton = { ToolButton("关闭", { onDismissPreview(); onDismissDetail() }) })
    }
    if (state.detailResource == null && state.previewState.isVisible) DanmuPreviewDialog(state.previewState, onDismissPreview)
    if (state.pendingDeleteResources.isNotEmpty()) {
        val resources = state.pendingDeleteResources
        AppDialog(onDismissRequest = onDismissDelete, title = { ToolTitle("确认删除本地弹幕", size = 16) },
            text = { ToolHint("将删除《${resources.first().title}》的 ${resources.size} 个文件，共 ${resources.sumOf { it.count }} 条弹幕。删除后需要重新导入才能恢复。") },
            confirmButton = { TextButton(onConfirmDelete) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onDismissDelete) { Text("取消") } })
    }
    if (showAdminPrompt) AdminModeRequiredDialog(
        adminModeRequiredPrompt(AdminModeRequiredTarget.LocalDanmu, state.writePermission != LocalDanmuWritePermission.ReadOnly),
        { onDismissAdminPrompt(); onOpenAdminMode() }, onDismissAdminPrompt)
    if (showAllFilesAccessPrompt) AppDialog(onDismissRequest = onDismissAllFilesAccessPrompt,
        title = { ToolTitle("开启目录直读模式", size = 16) },
        text = { ToolHint("开启所有文件访问权限后，可以直接浏览目录并选择弹幕文件。返回应用后会继续打开文件选择页。") },
        confirmButton = { ToolButton("去开启", { onDismissAllFilesAccessPrompt(); onRequestAllFilesAccess() }) },
        dismissButton = { TextButton(onDismissAllFilesAccessPrompt) { Text("取消") } })
}
