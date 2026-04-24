package com.example.danmuapiapp.ui.screen.core

import com.example.danmuapiapp.ui.component.AppBottomSheetDialog
import com.example.danmuapiapp.ui.component.AppBottomSheetStyle
import com.example.danmuapiapp.ui.component.AppBottomSheetTone

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
import com.example.danmuapiapp.domain.model.CoreSourceStatus
import com.example.danmuapiapp.domain.model.CoreDownloadProgress
import com.example.danmuapiapp.domain.model.CoreInfo
import com.example.danmuapiapp.domain.model.CoreVariantDisplayNames
import com.example.danmuapiapp.domain.model.GithubRelease
import com.example.danmuapiapp.domain.model.formatCoreVersionTransition
import com.example.danmuapiapp.domain.model.resolveCoreVariantRepo
import com.example.danmuapiapp.domain.model.resolveCoreVariantSourceText
import com.example.danmuapiapp.ui.common.CustomCoreSettingsForm
import com.example.danmuapiapp.ui.common.rememberCustomCoreSettingsFormState
import com.example.danmuapiapp.ui.component.GlassCard
import com.example.danmuapiapp.ui.component.GithubProxyPickerDialog
import com.example.danmuapiapp.ui.theme.appTonalButtonColors
import com.example.danmuapiapp.ui.theme.appTonalIconButtonColors
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoreScreen(viewModel: CoreViewModel = hiltViewModel()) {
    val coreList by viewModel.coreInfoList.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val runtimeState by viewModel.runtimeState.collectAsStateWithLifecycle()
    val coreDisplayNames by viewModel.coreDisplayNames.collectAsStateWithLifecycle()
    val customRepo by viewModel.customRepo.collectAsStateWithLifecycle()
    val customRepoBranch by viewModel.customRepoBranch.collectAsStateWithLifecycle()
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
                        ) { Text(coreDisplayNames.resolve(variant)) }
                    }
                }
            }

            coreList.forEach { info ->
                CoreVariantCard(
                    info = info,
                    isActive = info.variant == activeVariant,
                    vm = viewModel,
                    downloadProgress = downloadProgress,
                    customRepo = customRepo,
                    customRepoBranch = customRepoBranch,
                    coreDisplayNames = coreDisplayNames
                )
            }
        }
    }

    if (viewModel.showUpdateDialog) {
        UpdateResultDialog(viewModel, coreDisplayNames)
    }
    if (viewModel.showRollbackDialog) {
        RollbackDialog(viewModel.rollbackVariant, viewModel.releaseHistory,
            viewModel.isLoadingHistory, viewModel::rollbackTo, viewModel::dismissRollbackDialog)
    }
    viewModel.showVariantSettingsDialog?.let { variant ->
        VariantSettingsDialog(
            variant = variant,
            currentDisplayName = when (variant) {
                ApiVariant.Stable -> coreDisplayNames.stable
                ApiVariant.Dev -> coreDisplayNames.dev
                ApiVariant.Custom -> coreDisplayNames.custom
            },
            currentRepo = customRepo,
            currentBranch = customRepoBranch,
            onSave = { displayName, repo, branch ->
                viewModel.saveVariantSettings(variant, displayName, repo, branch)
            },
            onDismiss = viewModel::dismissVariantSettingsDialog
        )
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
    downloadProgress: CoreDownloadProgress,
    customRepo: String,
    customRepoBranch: String,
    coreDisplayNames: CoreVariantDisplayNames
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val showGearMenu = vm.showGearMenu == info.variant
    val isDownloadingThisCard = downloadProgress.inProgress && downloadProgress.variant == info.variant
    val dark = isSystemInDarkTheme()
    val variantLabel = coreDisplayNames.resolve(info.variant)
    val variantSource = resolveCoreVariantSourceText(info.variant, customRepo, customRepoBranch)
    val sourceUnknownLegacy = info.sourceStatus == CoreSourceStatus.UnknownLegacy

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
                    Text(variantLabel, style = MaterialTheme.typography.titleMedium,
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
                    val versionText = if (info.hasVersionUpdate && info.availableVersion != null) {
                        formatCoreVersionTransition(info.version, info.availableVersion)
                    } else {
                        formatCoreVersionTransition(info.version, null)
                    }
                    Text(versionText, style = MaterialTheme.typography.bodySmall,
                        color = if (info.hasVersionUpdate) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (variantSource.isNotBlank()) {
                    Text(variantSource, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Surface(shape = RoundedCornerShape(8.dp),
                color = when {
                    info.needsAttention -> MaterialTheme.colorScheme.primaryContainer
                    info.isInstalled -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                }) {
                Text(
                    text = when {
                        info.sourceMismatch -> "需替换"
                        sourceUnknownLegacy -> "需刷新"
                        info.hasVersionUpdate -> "有更新"
                        info.isInstalled -> "已安装"
                        else -> "未安装"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        info.needsAttention -> MaterialTheme.colorScheme.primary
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
            Box {
                FilledTonalIconButton(
                    onClick = { vm.openGearMenu(info.variant) },
                    enabled = !vm.isOperating,
                    modifier = Modifier.size(44.dp),
                    colors = appTonalIconButtonColors()
                ) {
                    Icon(Icons.Rounded.Settings, "核心设置", Modifier.size(20.dp))
                }
                DropdownMenu(expanded = showGearMenu, onDismissRequest = { vm.dismissGearMenu() }) {
                    DropdownMenuItem(
                        text = {
                            Text(if (info.variant == ApiVariant.Custom) "编辑名称 / 仓库 / 分支" else "编辑显示名称")
                        },
                        onClick = { vm.openVariantSettingsDialog(info.variant) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null, Modifier.size(20.dp)) }
                    )
                    if (info.isInstalled) {
                        DropdownMenuItem(
                            text = { Text("重装") },
                            onClick = { vm.reinstallCore(info.variant) },
                            leadingIcon = { Icon(Icons.Rounded.Refresh, null, Modifier.size(20.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text("回退") },
                            onClick = { vm.openRollbackDialog(info.variant) },
                            leadingIcon = { Icon(Icons.Rounded.History, null, Modifier.size(20.dp)) }
                        )
                    }
                }
            }

            if (!info.isInstalled) {
                val needsCustomSource = info.variant == ApiVariant.Custom && variantSource.isBlank()
                Button(
                    onClick = {
                        if (needsCustomSource) vm.openVariantSettingsDialog(info.variant)
                        else vm.installCore(info.variant)
                    },
                    enabled = !vm.isOperating,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                ) {
                    Icon(
                        if (needsCustomSource) Icons.Rounded.Edit else Icons.Rounded.Download,
                        null,
                        Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (needsCustomSource) "配置来源" else "安装")
                }
            } else {
                FilledTonalButton(
                    onClick = {
                        if (info.needsAttention) vm.doUpdate(info.variant)
                        else vm.checkUpdate(info.variant)
                    },
                    enabled = !vm.isOperating && !vm.isCheckingUpdate,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp),
                    colors = appTonalButtonColors()
                ) {
                    if (!info.needsAttention && vm.isCheckingUpdate) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (info.needsAttention) Icons.Rounded.SystemUpdate else Icons.Rounded.Update,
                            null,
                            Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            info.sourceMismatch -> "重新下载"
                            sourceUnknownLegacy -> "重新下载"
                            info.hasVersionUpdate -> "点击更新"
                            else -> "检查更新"
                        }
                    )
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
        AppBottomSheetDialog(
            onDismissRequest = { showDeleteConfirm = false },
            style = AppBottomSheetStyle.Confirm,
            tone = AppBottomSheetTone.Danger,
            title = { Text("确认删除") },
            text = { Text("确定要删除 $variantLabel 吗？") },
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
        bytes >= gb -> String.format(Locale.getDefault(), "%.2f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.getDefault(), "%.2f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.getDefault(), "%.2f KB", bytes / kb)
        else -> "$bytes B"
    }
}

@Composable
private fun UpdateResultDialog(
    vm: CoreViewModel,
    coreDisplayNames: CoreVariantDisplayNames
) {
    val info = vm.updateDialogInfo
    val variant = vm.updateDialogVariant
    val sourceUnknownLegacy = info?.sourceStatus == CoreSourceStatus.UnknownLegacy
    AppBottomSheetDialog(
        onDismissRequest = vm::dismissUpdateDialog,
        style = AppBottomSheetStyle.Status,
        tone = AppBottomSheetTone.Brand,
        icon = {
            Icon(
                if (info?.needsAttention == true) Icons.Rounded.SystemUpdate else Icons.Rounded.CheckCircle,
                null
            )
        },
        title = {
            Text(
                when {
                    info?.sourceMismatch == true -> "需要替换核心"
                    sourceUnknownLegacy -> "核心来源待确认"
                    info?.hasVersionUpdate == true -> "发现新版本"
                    else -> "已是最新"
                }
            )
        },
        text = {
            if (info?.sourceMismatch == true) {
                Text(
                    "${coreDisplayNames.resolve(info.variant)} 当前来源与设置不一致，将替换为 ${info.desiredSource ?: "目标仓库"}"
                )
            } else if (sourceUnknownLegacy) {
                Text(
                    "${coreDisplayNames.resolve(info.variant)} 是旧版安装，缺少来源标记；本地核心可继续启动，重新下载后会写入当前来源标记。"
                )
            } else if (info?.hasVersionUpdate == true) {
                Text(
                    "${coreDisplayNames.resolve(info.variant)}: " +
                        formatCoreVersionTransition(info.version, info.availableVersion)
                )
            } else {
                Text("${info?.let { coreDisplayNames.resolve(it.variant) } ?: ""} 当前已是最新版本")
            }
        },
        confirmButton = {
            if (info?.needsAttention == true && variant != null) {
                TextButton(onClick = { vm.doUpdate(variant) }) {
                    Text(if (info.sourceMismatch || sourceUnknownLegacy) "重新下载" else "立即更新")
                }
            } else {
                TextButton(onClick = vm::dismissUpdateDialog) { Text("确定") }
            }
        },
        dismissButton = {
            if (info?.needsAttention == true) {
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
    AppBottomSheetDialog(
        onDismissRequest = onDismiss,
        style = AppBottomSheetStyle.Selection,
        tone = AppBottomSheetTone.Info,
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun VariantSettingsDialog(
    variant: ApiVariant,
    currentDisplayName: String,
    currentRepo: String,
    currentBranch: String,
    onSave: (displayName: String, repo: String, branch: String) -> Unit,
    onDismiss: () -> Unit
) {
    val isCustom = variant == ApiVariant.Custom
    val customFormState = rememberCustomCoreSettingsFormState(
        initialDisplayName = currentDisplayName,
        initialRepo = currentRepo,
        initialBranch = currentBranch
    )
    var displayNameText by remember(variant, currentDisplayName) { mutableStateOf(currentDisplayName) }

    AppBottomSheetDialog(
        onDismissRequest = onDismiss,
        style = AppBottomSheetStyle.Form,
        tone = AppBottomSheetTone.Brand,
        icon = { Icon(if (isCustom) Icons.Rounded.Tune else Icons.Rounded.Edit, null) },
        title = { Text(if (isCustom) "编辑自定义核心" else "编辑显示名称") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isCustom) {
                    CustomCoreSettingsForm(
                        state = customFormState,
                        displayNamePlaceholder = variant.label
                    )
                } else {
                    Text(
                        "这里只改显示名称，不影响实际仓库来源。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = displayNameText,
                        onValueChange = { displayNameText = it },
                        label = { Text("显示名称") },
                        placeholder = { Text(variant.label) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        )
                    ) {
                        Text(
                            text = "实际仓库：${resolveCoreVariantRepo(variant, currentRepo)}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isCustom) {
                        val input = customFormState.toInput()
                        onSave(input.displayName, input.repo, input.branch)
                    } else {
                        onSave(displayNameText.trim(), "", "")
                    }
                },
                enabled = if (isCustom) customFormState.canSaveConfig else true
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
