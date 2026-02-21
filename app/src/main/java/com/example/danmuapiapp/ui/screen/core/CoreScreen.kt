package com.example.danmuapiapp.ui.screen.core

import androidx.compose.animation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreDownloadProgress
import com.example.danmuapiapp.domain.model.CoreInfo
import com.example.danmuapiapp.domain.model.GithubRelease
import com.example.danmuapiapp.ui.component.GlassCard
import com.example.danmuapiapp.ui.component.GithubProxyPickerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoreScreen(viewModel: CoreViewModel = hiltViewModel()) {
    val coreList by viewModel.coreInfoList.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val runtimeState by viewModel.runtimeState.collectAsStateWithLifecycle()
    val customRepo by viewModel.customRepo.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val activeVariant = runtimeState.variant

    LaunchedEffect(viewModel.operationMessage) {
        viewModel.operationMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("API 核心管理", style = MaterialTheme.typography.headlineLarge)
            Text(
                "安装、更新、回退与切换统一在此完成",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                )
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp)
                ) {
                    ApiVariant.entries.forEachIndexed { index, variant ->
                        SegmentedButton(
                            selected = activeVariant == variant,
                            onClick = { viewModel.updateVariant(variant) },
                            shape = SegmentedButtonDefaults.itemShape(index, ApiVariant.entries.size)
                        ) { Text(variant.label) }
                    }
                }
            }

            AnimatedVisibility(visible = activeVariant == ApiVariant.Custom) {
                OutlinedTextField(
                    value = customRepo,
                    onValueChange = { viewModel.saveCustomRepo(it.trim()) },
                    label = { Text("自定义仓库") },
                    placeholder = { Text("owner/repo") },
                    leadingIcon = { Icon(Icons.Rounded.Tune, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            coreList.forEach { info ->
                CoreVariantCard(info, info.variant == activeVariant, viewModel, downloadProgress)
            }
        }
    }

    if (viewModel.showUpdateDialog) { UpdateResultDialog(viewModel) }
    if (viewModel.showRollbackDialog) {
        RollbackDialog(viewModel.rollbackVariant, viewModel.releaseHistory,
            viewModel.isLoadingHistory, viewModel::rollbackTo, viewModel::dismissRollbackDialog)
    }
    if (viewModel.showCustomRepoDialog) {
        CustomRepoDialog(customRepo, viewModel::saveCustomRepo, viewModel::dismissCustomRepoDialog)
    }
    if (viewModel.showProxyPickerDialog) {
        GithubProxyPickerDialog(
            title = "选择 GitHub 线路",
            subtitle = "首次下载/更新前请先选择，测速结果会逐项更新",
            options = viewModel.proxyOptions,
            selectedId = viewModel.proxySelectedId,
            testingIds = viewModel.proxyTestingIds,
            resultMap = viewModel.proxyLatencyMap,
            onSelect = viewModel::selectProxy,
            onRetest = viewModel::retestProxySpeed,
            onConfirm = viewModel::confirmProxySelection,
            onDismiss = viewModel::dismissProxyPickerDialog
        )
    }
}

@Composable
private fun CoreVariantCard(
    info: CoreInfo,
    isActive: Boolean,
    vm: CoreViewModel,
    downloadProgress: CoreDownloadProgress
) {
    val customRepo by vm.customRepo.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val showGearMenu = vm.showGearMenu == info.variant
    val isDownloadingThisCard = downloadProgress.inProgress && downloadProgress.variant == info.variant
    val dark = isSystemInDarkTheme()

    GlassCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when (info.variant) {
                    ApiVariant.Stable -> Icons.Rounded.Verified
                    ApiVariant.Dev -> Icons.Rounded.Science
                    ApiVariant.Custom -> Icons.Rounded.Tune
                },
                contentDescription = null,
                tint = when (info.variant) {
                    ApiVariant.Stable -> if (dark) Color(0xFF4ADE80) else Color(0xFF4CAF50)
                    ApiVariant.Dev    -> if (dark) Color(0xFFFBBF24) else Color(0xFFFFC107)
                    ApiVariant.Custom -> MaterialTheme.colorScheme.tertiary
                },
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(info.variant.label, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    if (isActive) {
                        Surface(shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary) {
                            Text("使用中", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                }
                if (info.version != null) {
                    val versionText = if (info.hasUpdate && info.latestVersion != null)
                        "v${info.version} → v${info.latestVersion}"
                    else "v${info.version}"
                    Text(versionText, style = MaterialTheme.typography.bodySmall,
                        color = if (info.hasUpdate) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (info.variant.repo.isNotBlank()) {
                    Text(info.variant.repo, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Surface(shape = RoundedCornerShape(8.dp),
                color = when {
                    info.hasUpdate -> MaterialTheme.colorScheme.primaryContainer
                    info.isInstalled -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                }) {
                Text(
                    text = when {
                        info.hasUpdate -> "有更新"
                        info.isInstalled -> "已安装"
                        else -> "未安装"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        info.hasUpdate -> MaterialTheme.colorScheme.primary
                        info.isInstalled -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!info.isInstalled) {
                Button(
                    onClick = { vm.installCore(info.variant) },
                    enabled = !vm.isOperating && (info.variant.repo.isNotBlank() ||
                        (info.variant == ApiVariant.Custom && customRepo.isNotBlank())),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                ) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("安装")
                }
                if (info.variant == ApiVariant.Custom && customRepo.isBlank()) {
                    OutlinedButton(onClick = { vm.openCustomRepoDialog() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.heightIn(min = 44.dp)) {
                        Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("设置仓库")
                    }
                }
            } else {
                Box {
                FilledTonalIconButton(
                    onClick = { vm.openGearMenu(info.variant) },
                    enabled = !vm.isOperating,
                    modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Rounded.Settings, "核心维护", Modifier.size(20.dp))
                }
                    DropdownMenu(expanded = showGearMenu, onDismissRequest = { vm.dismissGearMenu() }) {
                        DropdownMenuItem(text = { Text("重装") }, onClick = { vm.reinstallCore(info.variant) },
                            leadingIcon = { Icon(Icons.Rounded.Refresh, null, Modifier.size(20.dp)) })
                        DropdownMenuItem(text = { Text("回退") }, onClick = { vm.openRollbackDialog(info.variant) },
                            leadingIcon = { Icon(Icons.Rounded.History, null, Modifier.size(20.dp)) })
                    }
                }
                FilledTonalButton(
                    onClick = {
                        if (info.hasUpdate) vm.doUpdate(info.variant)
                        else vm.checkUpdate(info.variant)
                    },
                    enabled = !vm.isOperating && !vm.isCheckingUpdate,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (!info.hasUpdate && vm.isCheckingUpdate) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (info.hasUpdate) Icons.Rounded.SystemUpdate else Icons.Rounded.Update,
                            null,
                            Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(if (info.hasUpdate) "点击更新" else "检查更新")
                }
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    enabled = !vm.isOperating,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(44.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(18.dp))
                }
            }
        }

        AnimatedVisibility(visible = isDownloadingThisCard) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val percentText = downloadProgress.progress?.let {
                    "${(it * 100f).toInt().coerceIn(0, 100)}%"
                } ?: "处理中"
                val bytesText = if (downloadProgress.totalBytes > 0) {
                    "${formatBytes(downloadProgress.downloadedBytes)} / ${formatBytes(downloadProgress.totalBytes)}"
                } else {
                    formatBytes(downloadProgress.downloadedBytes)
                }
                Text(
                    text = "${downloadProgress.actionLabel}中 · $percentText",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${downloadProgress.stageText} · $bytesText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (downloadProgress.progress == null) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { downloadProgress.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除 ${info.variant.label} 吗？") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; vm.deleteCore(info.variant) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error)) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } }
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> String.format("%.2f GB", bytes / gb)
        bytes >= mb -> String.format("%.2f MB", bytes / mb)
        bytes >= kb -> String.format("%.2f KB", bytes / kb)
        else -> "$bytes B"
    }
}

@Composable
private fun UpdateResultDialog(vm: CoreViewModel) {
    val info = vm.updateDialogInfo
    val variant = vm.updateDialogVariant
    AlertDialog(
        onDismissRequest = vm::dismissUpdateDialog,
        icon = {
            Icon(
                if (info?.hasUpdate == true) Icons.Rounded.SystemUpdate else Icons.Rounded.CheckCircle,
                null
            )
        },
        title = { Text(if (info?.hasUpdate == true) "发现新版本" else "已是最新") },
        text = {
            if (info?.hasUpdate == true) {
                Text("${info.variant.label}: v${info.version ?: "?"} → v${info.latestVersion}")
            } else {
                Text("${info?.variant?.label ?: ""} 当前已是最新版本")
            }
        },
        confirmButton = {
            if (info?.hasUpdate == true && variant != null) {
                TextButton(onClick = { vm.doUpdate(variant) }) { Text("立即更新") }
            } else {
                TextButton(onClick = vm::dismissUpdateDialog) { Text("确定") }
            }
        },
        dismissButton = {
            if (info?.hasUpdate == true) {
                TextButton(onClick = vm::dismissUpdateDialog) { Text("稍后") }
            }
        }
    )
}

@Composable
private fun RollbackDialog(
    variant: ApiVariant?,
    releases: List<GithubRelease>,
    isLoading: Boolean,
    onSelect: (ApiVariant, GithubRelease) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.History, null) },
        title = { Text("回退版本") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (isLoading) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (releases.isEmpty()) {
                    Text("没有找到可回退版本（提交历史或发行版）", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    releases.forEach { release ->
                        OutlinedCard(
                            onClick = { variant?.let { onSelect(it, release) } },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(release.name.ifBlank { release.tagName },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium)
                                val meta = buildList {
                                    if (release.tagName.isNotBlank() && release.tagName != release.name) {
                                        add(release.tagName)
                                    }
                                    if (release.publishedAt.isNotBlank()) {
                                        add(release.publishedAt.take(10))
                                    }
                                }.joinToString("  ·  ")
                                if (meta.isNotBlank()) {
                                    Text(meta,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun CustomRepoDialog(
    currentRepo: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var repoText by remember { mutableStateOf(currentRepo) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Tune, null) },
        title = { Text("自定义仓库") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("输入 GitHub 仓库地址，格式: owner/repo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = repoText, onValueChange = { repoText = it },
                    label = { Text("仓库地址") }, placeholder = { Text("owner/repo") },
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(repoText.trim()) },
                enabled = repoText.trim().contains('/')) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
