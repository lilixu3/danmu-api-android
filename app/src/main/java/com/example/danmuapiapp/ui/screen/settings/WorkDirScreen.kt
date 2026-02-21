package com.example.danmuapiapp.ui.screen.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.data.service.RuntimePaths
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.ui.component.SettingsDivider
import com.example.danmuapiapp.ui.component.SettingsGroup
import com.example.danmuapiapp.ui.component.SettingsPageHeader
import androidx.compose.ui.graphics.Color
import com.example.danmuapiapp.ui.component.SettingsValueItem

@Composable
fun WorkDirScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.runtimeState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val workDirInfo = viewModel.workDirInfo

    var showWorkDirDialog by remember { mutableStateOf(false) }
    var showAllFilesAccessDialog by remember { mutableStateOf(false) }
    var pendingWorkDirPathForPermission by remember { mutableStateOf<String?>(null) }
    var workDirInput by remember { mutableStateOf("") }

    val workDirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            }
        }
        if (uri == null) {
            viewModel.postMessage("未选择目录")
            return@rememberLauncherForActivityResult
        }
        val resolvedPath = RuntimePaths.resolveTreeUriToPath(uri)
        if (resolvedPath.isNullOrBlank()) {
            viewModel.postMessage("无法解析所选目录，请改用手动输入")
            return@rememberLauncherForActivityResult
        }
        if (RuntimePaths.needsAllFilesAccess(context, resolvedPath) &&
            !RuntimePaths.isAllFilesAccessGranted(context)
        ) {
            pendingWorkDirPathForPermission = resolvedPath
            showAllFilesAccessDialog = true
            return@rememberLauncherForActivityResult
        }
        viewModel.applyWorkDirPath(resolvedPath)
    }

    val allFilesAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val pendingPath = pendingWorkDirPathForPermission
        pendingWorkDirPathForPermission = null
        if (pendingPath.isNullOrBlank()) return@rememberLauncherForActivityResult
        if (RuntimePaths.isAllFilesAccessGranted(context)) {
            viewModel.applyWorkDirPath(pendingPath)
        } else {
            viewModel.postMessage("未授予完整存储权限，无法使用该目录")
        }
    }

    LaunchedEffect(viewModel.operationMessage) {
        viewModel.operationMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissMessage()
        }
    }

    LaunchedEffect(state.runMode) {
        viewModel.refreshWorkDirInfo()
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
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
                title = "工作目录",
                subtitle = "核心解包、配置与运行目录管理",
                onBack = onBack
            )

            SettingsGroup(title = "目录信息") {
                SettingsValueItem(
                    title = "当前目录",
                    value = workDirInfo.currentBaseDir.absolutePath,
                    icon = Icons.Rounded.Folder
                )

                if (state.runMode != RunMode.Normal) {
                    SettingsDivider()
                    SettingsValueItem(
                        title = "固定路径",
                        value = workDirInfo.rootBaseDir.absolutePath,
                        icon = Icons.Rounded.Lock
                    )
                } else {
                    SettingsDivider()
                    SettingsValueItem(
                        title = "默认目录",
                        value = workDirInfo.defaultBaseDir.absolutePath,
                        icon = Icons.Rounded.Home
                    )
                    SettingsDivider()
                    SettingsValueItem(
                        title = "自定义目录",
                        value = workDirInfo.customBaseDir?.absolutePath ?: "未设置",
                        icon = Icons.AutoMirrored.Rounded.DriveFileMove
                    )
                }
            }

            if (state.runMode == RunMode.Normal) {
                SettingsGroup(title = "目录操作") {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    workDirInput = workDirInfo.customBaseDir?.absolutePath
                                        ?: workDirInfo.normalBaseDir.absolutePath
                                    showWorkDirDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !viewModel.isApplyingWorkDir,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("手动设置")
                            }
                            OutlinedButton(
                                onClick = { workDirPickerLauncher.launch(null) },
                                modifier = Modifier.weight(1f),
                                enabled = !viewModel.isApplyingWorkDir,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.FolderOpen, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("选择目录")
                            }
                        }
                        if (workDirInfo.isCustomEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = viewModel::restoreDefaultWorkDir,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !viewModel.isApplyingWorkDir,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("恢复默认目录")
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = viewModel.isApplyingWorkDir) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "正在切换工作目录...",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    if (showWorkDirDialog) {
        AlertDialog(
            onDismissRequest = { showWorkDirDialog = false },
            title = { Text("设置工作目录") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = workDirInput,
                        onValueChange = { workDirInput = it },
                        label = { Text("目录路径") },
                        placeholder = { Text("/storage/emulated/0/danmu_api_runtime") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWorkDirDialog = false
                        val targetPath = workDirInput.trim()
                        if (RuntimePaths.needsAllFilesAccess(context, targetPath) &&
                            !RuntimePaths.isAllFilesAccessGranted(context)
                        ) {
                            pendingWorkDirPathForPermission = targetPath
                            showAllFilesAccessDialog = true
                        } else {
                            viewModel.applyWorkDirPath(targetPath)
                        }
                    },
                    enabled = workDirInput.trim().isNotBlank()
                ) { Text("确认切换") }
            },
            dismissButton = {
                TextButton(onClick = { showWorkDirDialog = false }) { Text("取消") }
            }
        )
    }

    if (showAllFilesAccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showAllFilesAccessDialog = false
                pendingWorkDirPathForPermission = null
            },
            title = { Text("需要完整存储权限") },
            text = {
                Text("Android 11 及以上系统访问此目录需要“所有文件访问权限”，授权后会自动继续切换。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showAllFilesAccessDialog = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val appIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        runCatching { allFilesAccessLauncher.launch(appIntent) }.getOrElse {
                            runCatching { allFilesAccessLauncher.launch(fallbackIntent) }.onFailure {
                                pendingWorkDirPathForPermission = null
                                viewModel.postMessage("无法打开存储权限设置页")
                            }
                        }
                    } else {
                        pendingWorkDirPathForPermission = null
                        viewModel.postMessage("当前系统无需完整存储权限")
                    }
                }) { Text("去授权") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAllFilesAccessDialog = false
                    pendingWorkDirPathForPermission = null
                }) { Text("取消") }
            }
        )
    }
}
