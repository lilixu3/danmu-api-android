package com.example.danmuapiapp.ui.screen.settings

import com.example.danmuapiapp.ui.component.AppSnackbarHost

import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.AppDialogStyle
import com.example.danmuapiapp.ui.component.AppDialogTone

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.graphics.Color
import com.example.danmuapiapp.ui.component.*
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton
import com.example.danmuapiapp.data.service.TvConfigSyncCodec
import com.example.danmuapiapp.data.service.AppBackupPreview
import com.example.danmuapiapp.data.service.AppBackupCodec
import com.example.danmuapiapp.data.service.AppBackupSection
import com.example.danmuapiapp.ui.theme.appTonalButtonColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackupRestoreScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    val scope = rememberCoroutineScope()
    var pendingExportContent by remember { mutableStateOf<String?>(null) }
    var pendingImportContent by remember { mutableStateOf<String?>(null) }
    var pendingFavoriteExportContent by remember { mutableStateOf<String?>(null) }
    var pendingFavoriteImportContent by remember { mutableStateOf<String?>(null) }
    var pendingFullExportContent by remember { mutableStateOf<String?>(null) }
    var pendingFullImportContent by remember { mutableStateOf<String?>(null) }
    var fullBackupPreview by remember { mutableStateOf<AppBackupPreview?>(null) }
    var selectedBackupSections by remember { mutableStateOf(AppBackupSection.entries.toSet()) }
    var selectedRestoreSections by remember { mutableStateOf(emptySet<AppBackupSection>()) }
    var showFullBackupExportDialog by remember { mutableStateOf(false) }
    var showFullBackupRestoreDialog by remember { mutableStateOf(false) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var showFavoriteImportConfirmDialog by remember { mutableStateOf(false) }
    var showWebDavRestoreConfirmDialog by remember { mutableStateOf(false) }
    var isDecodingTvSync by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val content = pendingExportContent
        pendingExportContent = null
        if (uri == null || content == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(content.toByteArray(Charsets.UTF_8))
                    } ?: error("无法写入目标文件")
                }
                viewModel.postMessage("导出成功")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                viewModel.postMessage("导出失败：${error.message}")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        AppBackupCodec.readUtf8(input, maxBytes = 2 * 1024 * 1024, label = ".env 文件")
                    } ?: error("无法读取 .env 文件")
                }
                pendingImportContent = content
                showImportConfirmDialog = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                viewModel.postMessage("导入失败：${error.message}")
            }
        }
    }

    val favoriteExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val content = pendingFavoriteExportContent
        pendingFavoriteExportContent = null
        if (uri == null || content == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(content.toByteArray(Charsets.UTF_8))
                    } ?: error("无法写入目标文件")
                }
                viewModel.postMessage("收藏数据导出成功")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                viewModel.postMessage("收藏数据导出失败：${error.message}")
            }
        }
    }

    val favoriteImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        AppBackupCodec.readUtf8(input, maxBytes = 8 * 1024 * 1024, label = "收藏文件")
                    } ?: error("无法读取收藏文件")
                }
                pendingFavoriteImportContent = content
                showFavoriteImportConfirmDialog = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                viewModel.postMessage("收藏数据导入失败：${error.message}")
            }
        }
    }

    val fullBackupExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val content = pendingFullExportContent
        pendingFullExportContent = null
        if (uri == null || content == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(content.toByteArray(Charsets.UTF_8))
                    } ?: error("无法写入目标文件")
                }
                viewModel.postMessage("完整备份已导出")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                viewModel.postMessage("导出失败：${error.message}")
            }
        }
    }

    val fullBackupImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        AppBackupCodec.readUtf8(input)
                    } ?: error("无法读取文件")
                }
                val previewResult = withContext(Dispatchers.Default) {
                    viewModel.inspectFullBackup(content)
                }
                previewResult.fold(
                    onSuccess = { preview ->
                        pendingFullImportContent = content
                        fullBackupPreview = preview
                        selectedRestoreSections = preview.sections
                        showFullBackupRestoreDialog = true
                    },
                    onFailure = { viewModel.postMessage("完整备份无效：${it.message}") }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                viewModel.postMessage("读取备份失败：${error.message}")
            }
        }
    }

    val tvSyncLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap == null) {
            isDecodingTvSync = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            isDecodingTvSync = true
            val invite = withContext(Dispatchers.Default) {
                TvConfigSyncCodec.decodeQrText(bitmap)
            }
            isDecodingTvSync = false
            invite.onSuccess { viewModel.syncConfigToTv(it) }
                .onFailure {
                    viewModel.postMessage(it.message ?: "未识别到电视同步码，请靠近电视后重试")
                }
        }
    }

    LaunchedEffect(viewModel.operationMessage) {
        viewModel.operationMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissMessage()
        }
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsPageHeader(
                title = "备份与恢复",
                subtitle = "服务配置、App 设置与核心来源的分类备份",
                onBack = onBack
            )

            // ── Current info ──
            SettingsGroup(title = "当前配置") {
                SettingsValueItem(
                    title = ".env 路径",
                    value = viewModel.envFilePath(),
                    icon = Icons.AutoMirrored.Rounded.InsertDriveFile
                )
                SettingsDivider()
                SettingsValueItem(
                    title = "WebDAV",
                    value = viewModel.webDavSummary(),
                    icon = Icons.Rounded.Cloud
                )
            }

            // ── Local backup ──
            SettingsGroup(title = "本地备份") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "完整备份",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppGlassButton(
                            onClick = {
                                selectedBackupSections = AppBackupSection.entries.toSet()
                                showFullBackupExportDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !viewModel.isFullBackupOperating,
                        ) {
                            Icon(Icons.Rounded.Inventory2, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("整包导出")
                        }
                        AppGlassButton(
                            onClick = {
                                fullBackupImportLauncher.launch(
                                    arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")
                                )
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !viewModel.isFullBackupOperating,
                        ) {
                            Icon(Icons.Rounded.Restore, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("整包恢复")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Token、密码和会话凭据不会写入整包。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "兼容旧备份",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppGlassButton(
                            onClick = {
                                scope.launch {
                                    viewModel.exportEnvContent().fold(
                                        onSuccess = { content ->
                                            pendingExportContent = content
                                            exportLauncher.launch(viewModel.buildExportFileName())
                                        },
                                        onFailure = { viewModel.postMessage("读取 .env 失败：${it.message}") }
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("导出 .env")
                        }
                        AppGlassButton(
                            onClick = {
                                importLauncher.launch(arrayOf("text/plain", "application/octet-stream", "*/*"))
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("导入 .env")
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "收藏与定时计划",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppGlassButton(
                            onClick = {
                                scope.launch {
                                    viewModel.exportFavoriteContent().fold(
                                        onSuccess = { snapshot ->
                                            pendingFavoriteExportContent = snapshot.content
                                            favoriteExportLauncher.launch(viewModel.buildFavoriteExportFileName())
                                        },
                                        onFailure = {
                                            viewModel.postMessage("收藏数据导出失败：${it.message}")
                                        }
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.Bookmark, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("导出收藏")
                        }
                        AppGlassButton(
                            onClick = {
                                favoriteImportLauncher.launch(
                                    arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.Restore, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("导入收藏")
                        }
                    }
                }
            }

            SettingsGroup(title = "同步到电视 / 盒子") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "先在 TV 兼容模式里打开“手机同步”卡片，再用系统相机拍下电视二维码，即可把当前 .env 与核心仓库配置同步过去。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    AppGlassButton(
                        onClick = {
                            isDecodingTvSync = false
                            tvSyncLauncher.launch(null)
                        },
                        enabled = !viewModel.isTvSyncOperating && !isDecodingTvSync,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.CloudSync, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("扫码同步到电视")
                    }
                    AnimatedVisibility(visible = isDecodingTvSync || viewModel.isTvSyncOperating) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                if (isDecodingTvSync) "正在识别电视同步码..." else viewModel.tvSyncOperatingText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            // ── WebDAV sync ──
            SettingsGroup(title = "WebDAV 同步") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppGlassButton(
                            onClick = viewModel::backupToWebDav,
                            modifier = Modifier.weight(1f),
                            enabled = !viewModel.isWebDavOperating,
                        ) {
                            Icon(Icons.Rounded.CloudUpload, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("云端备份")
                        }
                        AppGlassButton(
                            onClick = { showWebDavRestoreConfirmDialog = true },
                            modifier = Modifier.weight(1f),
                            enabled = !viewModel.isWebDavOperating,
                        ) {
                            Icon(Icons.Rounded.CloudDownload, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("云端恢复")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    AppGlassButton(
                        onClick = viewModel::openWebDavConfigDialog,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Settings, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("配置 WebDAV 账户")
                    }

                    AnimatedVisibility(visible = viewModel.isWebDavOperating) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                viewModel.webDavOperatingText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

        }
    }

    if (showFullBackupExportDialog) {
        BackupSectionSelectionDialog(
            title = "选择备份内容",
            available = AppBackupSection.entries.toSet(),
            selected = selectedBackupSections,
            confirmLabel = "导出",
            onSelectionChange = { selectedBackupSections = it },
            onDismiss = { showFullBackupExportDialog = false },
            onConfirm = {
                val sections = selectedBackupSections
                showFullBackupExportDialog = false
                scope.launch {
                    viewModel.createFullBackup(sections).fold(
                        onSuccess = { content ->
                            pendingFullExportContent = content
                            fullBackupExportLauncher.launch(viewModel.buildFullBackupFileName())
                        },
                        onFailure = { viewModel.postMessage("完整备份失败：${it.message}") }
                    )
                }
            }
        )
    }

    if (showFullBackupRestoreDialog) {
        BackupSectionSelectionDialog(
            title = "选择恢复内容",
            available = fullBackupPreview?.sections.orEmpty(),
            selected = selectedRestoreSections,
            confirmLabel = "恢复所选",
            onSelectionChange = { selectedRestoreSections = it },
            onDismiss = {
                showFullBackupRestoreDialog = false
                pendingFullImportContent = null
                fullBackupPreview = null
            },
            onConfirm = {
                val content = pendingFullImportContent
                val sections = selectedRestoreSections
                showFullBackupRestoreDialog = false
                pendingFullImportContent = null
                fullBackupPreview = null
                if (content != null) viewModel.restoreFullBackup(content, sections)
            }
        )
    }

    // ── Import confirm dialog ──
    if (showImportConfirmDialog) {
        AppDialog(
            onDismissRequest = { showImportConfirmDialog = false },
            style = AppDialogStyle.Confirm,
            tone = AppDialogTone.Warning,
            title = { Text("确认导入 .env") },
            text = { Text("导入将覆盖当前配置，是否继续？") },
            confirmButton = {
                AppGlassButton(onClick = {
                    val content = pendingImportContent
                    showImportConfirmDialog = false
                    pendingImportContent = null
                    if (content != null) {
                        viewModel.importEnvContent(content)
                    } else {
                        viewModel.postMessage("导入失败：文件内容为空")
                    }
                }) { Text("确认导入") }
            },
            dismissButton = {
                AppGlassButton(onClick = {
                    showImportConfirmDialog = false
                    pendingImportContent = null
                }) { Text("取消") }
            }
        )
    }

    if (showFavoriteImportConfirmDialog) {
        AppDialog(
            onDismissRequest = { showFavoriteImportConfirmDialog = false },
            style = AppDialogStyle.Confirm,
            tone = AppDialogTone.Warning,
            title = { Text("确认导入收藏") },
            text = { Text("导入将覆盖当前模式的收藏与定时计划，是否继续？") },
            confirmButton = {
                AppGlassButton(onClick = {
                    val content = pendingFavoriteImportContent
                    showFavoriteImportConfirmDialog = false
                    pendingFavoriteImportContent = null
                    if (content != null) {
                        viewModel.importFavoriteContent(content)
                    } else {
                        viewModel.postMessage("收藏导入失败：文件内容为空")
                    }
                }) { Text("确认导入") }
            },
            dismissButton = {
                AppGlassButton(onClick = {
                    showFavoriteImportConfirmDialog = false
                    pendingFavoriteImportContent = null
                }) { Text("取消") }
            }
        )
    }

    // ── WebDAV restore confirm dialog ──
    if (showWebDavRestoreConfirmDialog) {
        AppDialog(
            onDismissRequest = { showWebDavRestoreConfirmDialog = false },
            style = AppDialogStyle.Confirm,
            tone = AppDialogTone.Warning,
            title = { Text("确认云端恢复") },
            text = { Text("优先恢复 WebDAV 完整备份；若云端只有旧格式，则兼容恢复 .env 与收藏。") },
            confirmButton = {
                AppGlassButton(onClick = {
                    showWebDavRestoreConfirmDialog = false
                    viewModel.restoreFromWebDav()
                }) { Text("确认恢复") }
            },
            dismissButton = {
                AppGlassButton(onClick = { showWebDavRestoreConfirmDialog = false }) { Text("取消") }
            }
        )
    }

    // ── WebDAV config dialog ──
    if (viewModel.showWebDavConfigDialog) {
        WebDavConfigDialog(
            url = viewModel.webDavUrlInput,
            username = viewModel.webDavUserInput,
            password = viewModel.webDavPassInput,
            folder = viewModel.webDavPathInput,
            onUrlChange = viewModel::updateWebDavUrl,
            onUsernameChange = viewModel::updateWebDavUser,
            onPasswordChange = viewModel::updateWebDavPass,
            onFolderChange = viewModel::updateWebDavPath,
            onSave = viewModel::saveWebDavConfig,
            onDismiss = viewModel::dismissWebDavConfigDialog
        )
    }
}

@Composable
private fun BackupSectionSelectionDialog(
    title: String,
    available: Set<AppBackupSection>,
    selected: Set<AppBackupSection>,
    confirmLabel: String,
    onSelectionChange: (Set<AppBackupSection>) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        style = AppDialogStyle.Selection,
        tone = AppDialogTone.Brand,
        title = { Text(title) },
        text = {
            AppBackupSection.entries.filter { it in available }.forEach { section ->
                AppDialogOption(
                    selected = section in selected,
                    onClick = {
                        onSelectionChange(
                            if (section in selected) selected - section else selected + section
                        )
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Checkbox(
                        checked = section in selected,
                        onCheckedChange = { checked ->
                            onSelectionChange(if (checked) selected + section else selected - section)
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(section.backupLabel(), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            section.backupDescription(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (section in selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            AppGlassButton(onClick = onConfirm, enabled = selected.isNotEmpty()) { Text(confirmLabel) }
        },
        dismissButton = { AppGlassButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun AppBackupSection.backupLabel(): String = when (this) {
    AppBackupSection.Environment -> "服务配置"
    AppBackupSection.Favorites -> "收藏与定时计划"
    AppBackupSection.AppSettings -> "App 设置"
    AppBackupSection.CoreSources -> "核心仓库与版本清单"
    AppBackupSection.AccessRules -> "设备访问规则"
}

private fun AppBackupSection.backupDescription(): String = when (this) {
    AppBackupSection.Environment -> ".env 非敏感项，保留本机现有凭据"
    AppBackupSection.Favorites -> "当前模式的收藏、刷新计划与状态"
    AppBackupSection.AppSettings -> "界面、端口、保活和下载偏好；保留本机运行模式"
    AppBackupSection.CoreSources -> "各工作目录仓库设置，不包含核心文件"
    AppBackupSection.AccessRules -> "访问模式与黑名单"
}

@Composable
private fun WebDavConfigDialog(
    url: String,
    username: String,
    password: String,
    folder: String,
    onUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onFolderChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    var showPassword by remember { mutableStateOf(false) }
    AppDialog(
        onDismissRequest = onDismiss,
        style = AppDialogStyle.Form,
        tone = AppDialogTone.Brand,
        title = { Text("WebDAV 设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = url, onValueChange = onUrlChange,
                    label = { Text("服务器地址") },
                    placeholder = { Text("https://dav.example.com/dav") },
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username, onValueChange = onUsernameChange,
                    label = { Text("用户名") },
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password, onValueChange = onPasswordChange,
                    label = { Text("密码 / 应用专用密码") },
                    trailingIcon = {
                        AppGlassIconButton(
                            onClick = { showPassword = !showPassword },
                            size = 32.dp
                        ) {
                            Icon(
                                if (showPassword) Icons.Rounded.VisibilityOff
                                else Icons.Rounded.Visibility, null
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = folder, onValueChange = onFolderChange,
                    label = { Text("备份目录（可选）") },
                    placeholder = { Text("DanmuApi") },
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "留空目录时默认使用 DanmuApi/.env",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { AppGlassButton(onClick = onSave) { Text("保存") } },
        dismissButton = { AppGlassButton(onClick = onDismiss) { Text("取消") } }
    )
}
