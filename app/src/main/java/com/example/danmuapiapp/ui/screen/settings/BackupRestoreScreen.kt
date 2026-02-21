package com.example.danmuapiapp.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.graphics.Color
import com.example.danmuapiapp.ui.component.*

@Composable
fun BackupRestoreScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    var pendingExportContent by remember { mutableStateOf<String?>(null) }
    var pendingImportContent by remember { mutableStateOf<String?>(null) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var showWebDavRestoreConfirmDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val content = pendingExportContent
        pendingExportContent = null
        if (uri == null || content == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
            } ?: error("无法写入目标文件")
        }.onSuccess {
            viewModel.postMessage("导出成功")
        }.onFailure {
            viewModel.postMessage("导出失败：${it.message}")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
            }.orEmpty()
        }.onSuccess { content ->
            pendingImportContent = content
            showImportConfirmDialog = true
        }.onFailure {
            viewModel.postMessage("导入失败：${it.message}")
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                subtitle = ".env 导入导出与 WebDAV 云端同步",
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                pendingExportContent = viewModel.exportEnvContent()
                                exportLauncher.launch(viewModel.buildExportFileName())
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("导出 .env")
                        }
                        OutlinedButton(
                            onClick = {
                                importLauncher.launch(arrayOf("text/plain", "application/octet-stream", "*/*"))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("导入 .env")
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
                        FilledTonalButton(
                            onClick = viewModel::backupToWebDav,
                            modifier = Modifier.weight(1f),
                            enabled = !viewModel.isWebDavOperating,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Rounded.CloudUpload, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("云端备份")
                        }
                        OutlinedButton(
                            onClick = { showWebDavRestoreConfirmDialog = true },
                            modifier = Modifier.weight(1f),
                            enabled = !viewModel.isWebDavOperating,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.CloudDownload, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("云端恢复")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = viewModel::openWebDavConfigDialog,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
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

    // ── Import confirm dialog ──
    if (showImportConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showImportConfirmDialog = false },
            title = { Text("确认导入 .env") },
            text = { Text("导入将覆盖当前配置，是否继续？") },
            confirmButton = {
                TextButton(onClick = {
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
                TextButton(onClick = {
                    showImportConfirmDialog = false
                    pendingImportContent = null
                }) { Text("取消") }
            }
        )
    }

    // ── WebDAV restore confirm dialog ──
    if (showWebDavRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showWebDavRestoreConfirmDialog = false },
            title = { Text("确认云端恢复") },
            text = { Text("将从 WebDAV 下载并覆盖当前 .env，是否继续？") },
            confirmButton = {
                TextButton(onClick = {
                    showWebDavRestoreConfirmDialog = false
                    viewModel.restoreFromWebDav()
                }) { Text("确认恢复") }
            },
            dismissButton = {
                TextButton(onClick = { showWebDavRestoreConfirmDialog = false }) { Text("取消") }
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
    AlertDialog(
        onDismissRequest = onDismiss,
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
                        IconButton(onClick = { showPassword = !showPassword }) {
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
        confirmButton = { TextButton(onClick = onSave) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
