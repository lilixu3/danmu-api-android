package com.example.danmuapiapp.ui.screen.localdanmu

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.domain.model.LocalDanmuGroup
import com.example.danmuapiapp.domain.model.LocalDanmuResource
import com.example.danmuapiapp.domain.model.LocalDanmuType
import com.example.danmuapiapp.domain.model.LocalDanmuWritePermission
import com.example.danmuapiapp.ui.component.AdminModeRequiredDialog
import com.example.danmuapiapp.ui.component.AdminModeRequiredTarget
import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.AppDialogStyle
import com.example.danmuapiapp.ui.component.AppDialogTone
import com.example.danmuapiapp.ui.component.AppGlassSurface
import com.example.danmuapiapp.ui.component.AppSnackbarHost
import com.example.danmuapiapp.ui.component.adminModeRequiredPrompt
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassDangerButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton
import com.example.danmuapiapp.ui.screen.download.DanmuPreviewDialog

/**
 * 与 ActivityResultContracts.OpenDocument 等价的系统文档选择器，但支持传入
 * EXTRA_INITIAL_URI（Android 8.0+），让选择器尽量停在上次选过文件的目录，
 * 减少每次导入都要重新翻目录的情况。
 */
private class OpenDocumentAtFolderContract(
    private val initialUri: Uri?
) : ActivityResultContract<Array<String>, Uri?>() {

    private companion object {
        const val EXTRA_INITIAL_URI = "android.provider.extra.INITIAL_URI"
    }

    override fun createIntent(context: Context, input: Array<String>): Intent {
        val types = input.filter { it.isNotBlank() }
        val mimeTypes = if (types.isEmpty()) arrayOf("*/*") else types.toTypedArray()
        return Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(types.firstOrNull() ?: "*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
            .apply { initialUri?.let { putExtra(EXTRA_INITIAL_URI, it) } }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

@Composable
fun LocalDanmuScreen(
    onBack: () -> Unit,
    onOpenAdminMode: () -> Unit,
    onOpenConfig: () -> Unit,
    viewModel: LocalDanmuViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showUploadPanel by rememberSaveable { mutableStateOf(false) }
    var showAdminPrompt by remember { mutableStateOf(false) }
    var showAllFilesAccessPrompt by remember { mutableStateOf(false) }

    val allFilesAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshAllFilesAccess()
        if (viewModel.uiState.value.allFilesAccessGranted) {
            viewModel.openDirectoryBrowser()
        }
    }

    val pickerContract = remember(state.recentUpload?.uri) {
        OpenDocumentAtFolderContract(
            initialUri = state.recentUpload?.uri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        )
    }
    val filePicker = rememberLauncherForActivityResult(
        contract = pickerContract
    ) { uri ->
        if (uri != null) {
            // 持久化读取授权，便于下次复用同一个文件，并作为选择器的初始目录提示。
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.selectUploadFile(uri.toString())
            showUploadPanel = true
        }
    }

    val multiFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            viewModel.addBatchUris(uris.map { it.toString() })
        }
    }

    val startMultiImport: () -> Unit = {
        if (state.allFilesAccessGranted) {
            // 目录浏览本身就是自由勾选，进目录即可。
            viewModel.openDirectoryBrowser()
        } else {
            multiFilePicker.launch(arrayOf("*/*"))
        }
    }

    // 上传成功后收起表单回到列表，成功提示走列表页的 Snackbar，避免停留在空表单上。
    LaunchedEffect(state.uploadSuccessTick) {
        if (state.uploadSuccessTick > 0) showUploadPanel = false
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
            viewModel.refreshAllFilesAccess()
            viewModel.openDirectoryBrowser()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
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

    // 有上次选择的文件时先进表单，让用户可以直接“重新使用”，不必每次重翻目录。
    val openUploadFlow: () -> Unit = {
        viewModel.clearUploadDraft()
        when {
            // 目录直读已开启：直接进目录挑文件，跳开系统文件选择器。
            state.allFilesAccessGranted -> {
                showUploadPanel = true
                viewModel.openDirectoryBrowser()
            }
            // 未授权时先进表单：单文件、批量导入、上次文件、去开启都在这一页。
            else -> showUploadPanel = true
        }
    }

    BackHandler(enabled = showUploadPanel && !state.browser.visible) {
        if (!state.upload.isUploading) showUploadPanel = false
    }

    // 对话框统一在最前面组合：目录面板/上传面板都会 early return，
    // 放在后面会导致「在上传面板里点去开启没反应，退回列表才弹窗」。
    LocalDanmuDialogHost(
        state = state,
        showAdminPrompt = showAdminPrompt,
        showAllFilesAccessPrompt = showAllFilesAccessPrompt,
        onOpenAdminMode = onOpenAdminMode,
        onDismissAdminPrompt = { showAdminPrompt = false },
        onDismissAllFilesAccessPrompt = { showAllFilesAccessPrompt = false },
        onRequestAllFilesAccess = requestAllFilesAccess,
        onPreview = viewModel::loadPreview,
        onDismissDetail = viewModel::dismissDetail,
        onDelete = { resource ->
            viewModel.dismissDetail()
            viewModel.requestDelete(resource)
        },
        onConfirmDelete = viewModel::confirmDelete,
        onDismissDelete = viewModel::dismissDelete,
        onDismissPreview = viewModel::dismissPreview
    )

    if (state.browser.visible) {
        LocalDanmuDirectoryBrowserPanel(
            state = state.browser,
            onBack = viewModel::closeDirectoryBrowser,
            onOpenParent = viewModel::browseParent,
            onOpenDefaultDirectory = viewModel::browseDefaultDirectory,
            onUseSystemPicker = {
                viewModel.closeDirectoryBrowser()
                showUploadPanel = true
                filePicker.launch(arrayOf("*/*"))
            },
            onToggleSelectAll = viewModel::toggleBrowserSelectAll,
            onSetDefaultDirectory = viewModel::setBrowserDefaultDirectory,
            onToggleSelection = viewModel::toggleBrowserSelection,
            onImportSelected = viewModel::importSelectedBrowserFiles,
            onRefresh = { viewModel.browseDirectory(state.browser.path) },
            onOpenDirectory = viewModel::browseDirectory
        )
        return
    }

    if (state.batch.visible) {
        LocalDanmuBatchPanel(
            state = state.batch,
            errorMessage = state.errorMessage,
            onBack = {
                showUploadPanel = false
                viewModel.closeBatch()
            },
            onToggleSelected = viewModel::toggleBatchSelected,
            onRemove = viewModel::removeBatchItem,
            onUpdateMetadata = viewModel::updateBatchItemMetadata,
            onRun = viewModel::runBatchImport,
            onCancel = viewModel::cancelBatchImport,
            onRetryFailed = viewModel::retryFailedBatch,
            onDismissError = viewModel::dismissError
        )
        return
    }

    if (showUploadPanel) {
        LocalDanmuUploadPanel(
            state = state.upload,
            recentFile = state.recentUpload,
            errorMessage = state.errorMessage,
            allFilesAccessGranted = state.allFilesAccessGranted,
            onPickFile = { filePicker.launch(arrayOf("*/*")) },
            onReuseRecent = viewModel::reuseRecentUpload,
            onOpenDirectoryBrowser = viewModel::openDirectoryBrowser,
            onImportMultiple = startMultiImport,
            onRequestAllFilesAccess = { showAllFilesAccessPrompt = true },
            onDismissError = viewModel::dismissError,
            onBack = { showUploadPanel = false },
            onTitleChange = viewModel::updateUploadTitle,
            onYearChange = viewModel::updateUploadYear,
            onTypeChange = viewModel::updateUploadType,
            onSeasonChange = viewModel::updateUploadSeason,
            onEpisodeChange = viewModel::updateUploadEpisode,
            onSubmit = viewModel::uploadCurrent
        )
        return
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppGlassIconButton(onClick = onBack, size = 36.dp) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("本地弹幕", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        if (state.totalEpisodeFiles > 0) {
                            "${state.snapshot.groups.size} 个资源 · ${state.totalEpisodeFiles} 个文件 · ${formatLocalDanmuSize(state.totalSizeBytes)}"
                        } else {
                            "手动上传并维护本地弹幕文件"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AppGlassIconButton(
                    onClick = viewModel::refreshNow,
                    enabled = !state.isLoading,
                    size = 36.dp
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新", modifier = Modifier.size(18.dp))
                    }
                }
                AppGlassIconButton(
                    onClick = { requireWrite(openUploadFlow) },
                    size = 36.dp
                ) {
                    Icon(
                        Icons.Rounded.UploadFile,
                        contentDescription = "上传弹幕文件",
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 22.dp)
            ) {
                // 只反映这次请求的结果：失败就显示原始原因 + 重试/重启服务，
                // 不做任何能力判定，也不因为“未确认”而隐藏列表。
                state.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    item(key = "error") {
                        LocalDanmuErrorBanner(
                            message = message,
                            onRetry = viewModel::refreshNow,
                            onRestartService = viewModel::restartService
                        )
                    }
                }
                if (state.deleteInProgress) {
                    item(key = "delete-progress") {
                        DeleteProgressCard(state.deleteProgress)
                    }
                }
                item(key = "stats") { LocalDanmuStatsRow(state) }
                if (state.writePermission != LocalDanmuWritePermission.Writable) {
                    item(key = "permission") {
                        WritePermissionBanner(
                            permission = state.writePermission,
                            onOpenAdminMode = onOpenAdminMode,
                            onOpenConfig = onOpenConfig
                        )
                    }
                }
                if (!state.localSourceEnabled && state.snapshot.resources.isNotEmpty()) {
                    item(key = "source") {
                        LocalSourceBanner(onEnable = viewModel::enableLocalSource)
                    }
                }
                item(key = "search") {
                    LocalDanmuSearchBar(
                        query = state.searchQuery,
                        typeFilter = state.typeFilter,
                        onQueryChange = viewModel::setSearchQuery,
                        onTypeChange = viewModel::setTypeFilter
                    )
                }
                if (state.filteredGroups.isEmpty()) {
                    item(key = "empty") {
                        LocalDanmuEmptyState(
                            hasAnyResource = state.snapshot.resources.isNotEmpty(),
                            onUpload = { requireWrite(openUploadFlow) }
                        )
                    }
                } else {
                    items(
                        items = state.filteredGroups,
                        key = { it.groupKey }
                    ) { group ->
                        LocalDanmuGroupCard(
                            group = group,
                            expanded = group.groupKey in state.expandedGroupKeys,
                            canWrite = state.writePermission == LocalDanmuWritePermission.Writable,
                            onToggle = { viewModel.toggleGroup(group.groupKey) },
                            onOpenDetail = viewModel::loadDetail,
                            onDeleteEpisode = viewModel::requestDelete,
                            onDeleteSeason = { viewModel.requestDeleteGroup(group) },
                            onDeleteSeries = { viewModel.requestDeleteSeries(group) }
                        )
                    }
                }
            }
        }
    }

}

@Composable
private fun LocalDanmuDialogHost(
    state: LocalDanmuUiState,
    showAdminPrompt: Boolean,
    showAllFilesAccessPrompt: Boolean,
    onOpenAdminMode: () -> Unit,
    onDismissAdminPrompt: () -> Unit,
    onDismissAllFilesAccessPrompt: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onPreview: (String) -> Unit,
    onDismissDetail: () -> Unit,
    onDelete: (LocalDanmuResource) -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onDismissPreview: () -> Unit
) {
    state.detailResource?.let { resource ->
        LocalDanmuDetailDialog(
            resource = resource,
            canWrite = state.writePermission == LocalDanmuWritePermission.Writable,
            onPreview = { onPreview(resource.resourceKey) },
            onDismiss = onDismissDetail,
            onDelete = { onDelete(resource) }
        )
    }

    if (state.pendingDeleteResources.isNotEmpty()) {
        val resources = state.pendingDeleteResources
        val count = resources.size
        val danmuCount = resources.sumOf { it.count }
        AppDialog(
            onDismissRequest = onDismissDelete,
            style = AppDialogStyle.Confirm,
            tone = AppDialogTone.Danger,
            icon = { Icon(Icons.Rounded.DeleteOutline, null) },
            title = { Text(if (count == 1) "确认删除本地弹幕" else "确认批量删除") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (count == 1) {
                            "将删除「${resources.first().title} ${resources.first().episodeLabel}」。"
                        } else {
                            "将删除 ${resources.first().title} 的 $count 个文件，共 $danmuCount 条弹幕。"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "删除后如果仍需要，只能重新上传。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                AppGlassDangerButton(onClick = onConfirmDelete) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                AppGlassButton(onClick = onDismissDelete) {
                    Text("取消")
                }
            }
        )
    }

    if (state.previewState.isVisible) {
        DanmuPreviewDialog(
            state = state.previewState,
            onDismiss = onDismissPreview
        )
    }

    if (showAdminPrompt) {
        AdminModeRequiredDialog(
            prompt = adminModeRequiredPrompt(
                target = AdminModeRequiredTarget.LocalDanmu,
                hasAdminTokenConfigured = state.writePermission != LocalDanmuWritePermission.ReadOnly
            ),
            onOpenAdminMode = {
                onDismissAdminPrompt()
                onOpenAdminMode()
            },
            onDismiss = onDismissAdminPrompt
        )
    }

    if (showAllFilesAccessPrompt) {
        AppDialog(
            onDismissRequest = onDismissAllFilesAccessPrompt,
            style = AppDialogStyle.Confirm,
            tone = AppDialogTone.Warning,
            icon = { Icon(Icons.Rounded.LockOpen, null) },
            title = { Text("开启目录直读模式") },
            text = {
                Text(
                    "开启「所有文件访问」后，可以直接浏览弹幕目录并选择文件，" +
                        "不用每次经过系统文件选择器，也不会因为重启应用而丢失读取授权。\n\n" +
                        "该权限需要在系统设置中手动开启，仅用于读取本地弹幕文件。"
                )
            },
            confirmButton = {
                AppGlassButton(onClick = {
                    onDismissAllFilesAccessPrompt()
                    onRequestAllFilesAccess()
                }) { Text("去开启") }
            },
            dismissButton = {
                AppGlassButton(onClick = onDismissAllFilesAccessPrompt) { Text("取消") }
            }
        )
    }
}

@Composable
private fun LocalDanmuErrorBanner(
    message: String,
    onRetry: () -> Unit,
    onRestartService: () -> Unit
) {
    BannerCard(
        icon = Icons.Rounded.ErrorOutline,
        tint = MaterialTheme.colorScheme.error,
        title = "本地弹幕请求失败",
        message = message,
        actionText = "重试",
        onAction = onRetry,
        secondaryActionText = "重启服务",
        onSecondaryAction = onRestartService
    )
}

@Composable
private fun WritePermissionBanner(
    permission: LocalDanmuWritePermission,
    onOpenAdminMode: () -> Unit,
    onOpenConfig: () -> Unit
) {
    val message = when (permission) {
        LocalDanmuWritePermission.ReadOnly ->
            "当前为只读模式：可以查看列表。上传和删除需要配置 ADMIN_TOKEN，或在核心配置中开启 LOCAL_DANMU_NOT_REQUIRE_ADMIN。"
        LocalDanmuWritePermission.AdminRequired ->
            "当前为只读模式：上传和删除需要进入管理员模式。"
        LocalDanmuWritePermission.Writable -> return
    }
    BannerCard(
        icon = Icons.Rounded.AdminPanelSettings,
        tint = MaterialTheme.colorScheme.tertiary,
        title = "只读模式",
        message = message,
        actionText = "管理员设置",
        onAction = onOpenAdminMode,
        secondaryActionText = "核心配置",
        onSecondaryAction = onOpenConfig
    )
}

@Composable
private fun LocalSourceBanner(onEnable: (Boolean) -> Unit) {
    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "搜索还看不到本地弹幕？",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "需要把 local 加入 SOURCE_ORDER，核心才会在搜索和自动匹配时检索已导入的弹幕。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppGlassButton(onClick = { onEnable(true) }) { Text("优先本地") }
                AppGlassButton(onClick = { onEnable(false) }) { Text("作为补充") }
            }
        }
    }
}

@Composable
private fun DeleteProgressCard(progress: String) {
    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "正在删除 ${progress.ifBlank { "…" }}",
                    style = MaterialTheme.typography.bodyMedium
                )
                val parts = progress.split('/')
                val done = parts.getOrNull(0)?.toIntOrNull()
                val total = parts.getOrNull(1)?.toIntOrNull()
                if (done != null && total != null && total > 0) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalDanmuStatsRow(state: LocalDanmuUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LocalDanmuStatChip(
            modifier = Modifier.weight(1f),
            label = "资源",
            value = "${state.snapshot.groups.size} 组",
            accent = MaterialTheme.colorScheme.primary
        )
        LocalDanmuStatChip(
            modifier = Modifier.weight(1f),
            label = "文件",
            value = "${state.totalEpisodeFiles} 个",
            accent = MaterialTheme.colorScheme.secondary
        )
        LocalDanmuStatChip(
            modifier = Modifier.weight(1f),
            label = "弹幕",
            value = "${state.totalDanmuCount} 条",
            accent = MaterialTheme.colorScheme.tertiary
        )
        LocalDanmuStatChip(
            modifier = Modifier.weight(1f),
            label = "占用",
            value = formatLocalDanmuSize(state.totalSizeBytes),
            accent = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LocalDanmuStatChip(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accent: Color
) {
    AppGlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LocalDanmuSearchBar(
    query: String,
    typeFilter: String?,
    onQueryChange: (String) -> Unit,
    onTypeChange: (String?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("搜索标题或文件名") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    AppGlassIconButton(onClick = { onQueryChange("") }, size = 32.dp) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "清空",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = typeFilter == null,
                onClick = { onTypeChange(null) },
                label = { Text("全部") }
            )
            LocalDanmuType.entries.forEach { type ->
                FilterChip(
                    selected = typeFilter == type.wire,
                    onClick = { onTypeChange(type.wire) },
                    label = { Text(type.label) }
                )
            }
        }
    }
}

@Composable
private fun LocalDanmuEmptyState(
    hasAnyResource: Boolean,
    onUpload: () -> Unit
) {
    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Rounded.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp)
            )
            Text(
                if (hasAnyResource) "没有匹配的本地弹幕" else "还没有导入本地弹幕",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (hasAnyResource) {
                    "换一个关键词或类型筛选试试。"
                } else {
                    "和核心一致：每次手动选择一个弹幕文件，文件名会自动解析。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AppGlassButton(onClick = onUpload) {
                Icon(Icons.Rounded.UploadFile, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.size(6.dp))
                Text("上传弹幕文件")
            }
        }
    }
}

@Composable
private fun LocalDanmuGroupCard(
    group: LocalDanmuGroup,
    expanded: Boolean,
    canWrite: Boolean,
    onToggle: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onDeleteEpisode: (LocalDanmuResource) -> Unit,
    onDeleteSeason: () -> Unit,
    onDeleteSeries: () -> Unit
) {
    val typeLabel = LocalDanmuType.fromWire(group.type)?.label ?: group.type.ifBlank { "未知类型" }
    val seasonText = if (group.type == LocalDanmuType.Movie.wire && group.season == 1) {
        ""
    } else {
        " · 第${group.season}季"
    }
    var menuExpanded by remember { mutableStateOf(false) }
    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        group.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${group.year ?: "年份未知"} · $typeLabel$seasonText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "已上传 ${group.episodeCount} ${if (group.type == LocalDanmuType.Movie.wire) "个文件" else "集"} · " +
                            "${group.count} 条弹幕 · ${formatLocalDanmuSize(group.sizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canWrite) {
                    Box {
                        AppGlassIconButton(
                            onClick = { menuExpanded = true },
                            size = 34.dp
                        ) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = "更多操作",
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("删除本季（${group.episodes.size} 集）") },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteSeason()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("删除整部（含所有季）") },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteSeries()
                                }
                            )
                        }
                    }
                }
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
                )
                group.episodes.forEach { resource ->
                    LocalDanmuEpisodeRow(
                        resource = resource,
                        canWrite = canWrite,
                        onOpenDetail = { onOpenDetail(resource.resourceKey) },
                        onDelete = { onDeleteEpisode(resource) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalDanmuEpisodeRow(
    resource: LocalDanmuResource,
    canWrite: Boolean,
    onOpenDetail: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail)
            .padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Rounded.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                resource.episodeLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                resource.filename.ifBlank { "弹幕文件" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${resource.count} 条 · ${formatLocalDanmuSize(resource.sizeBytes)} · " +
                    "${resource.format.ifBlank { "未知格式" }} · ${formatLocalDanmuTime(resource.updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (canWrite) {
            AppGlassIconButton(onClick = onDelete, size = 34.dp) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "删除",
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 46.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
    )
}

@Composable
private fun LocalDanmuDetailDialog(
    resource: LocalDanmuResource,
    canWrite: Boolean,
    onPreview: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        style = AppDialogStyle.Status,
        tone = AppDialogTone.Info,
        icon = { Icon(Icons.Rounded.Description, null) },
        title = { Text("本地弹幕详情") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailRow("标题", resource.title)
                DetailRow("类型", "${resource.typeLabel} · 第${resource.season}季")
                DetailRow("集数", resource.episodeLabel)
                DetailRow("文件名", resource.filename.ifBlank { "未记录" })
                DetailRow("格式", resource.format.ifBlank { "未记录" })
                DetailRow("弹幕数量", "${resource.count} 条")
                DetailRow("文件大小", formatLocalDanmuSize(resource.sizeBytes))
                DetailRow("更新时间", formatLocalDanmuTime(resource.updatedAt))
                DetailRow("resourceKey", resource.resourceKey)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppGlassButton(
                        onClick = onPreview,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Rounded.PlayCircleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("预览")
                    }
                    if (canWrite) {
                        AppGlassDangerButton(
                            onClick = onDelete,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("删除")
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppGlassButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(86.dp)
        )
        Text(
            value.ifBlank { "—" },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BannerCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    message: String,
    actionText: String,
    onAction: () -> Unit,
    secondaryActionText: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Icon(icon, contentDescription = null, tint = tint)
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppGlassButton(onClick = onAction) { Text(actionText) }
                if (secondaryActionText != null && onSecondaryAction != null) {
                    AppGlassButton(onClick = onSecondaryAction) {
                        Text(secondaryActionText)
                    }
                }
            }
        }
    }
}
