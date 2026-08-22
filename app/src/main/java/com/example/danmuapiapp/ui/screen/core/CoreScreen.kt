package com.example.danmuapiapp.ui.screen.core

import com.example.danmuapiapp.ui.component.AppSnackbarHost
import com.example.danmuapiapp.ui.component.AppGlassSurface

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreDependencyRepairRequest
import com.example.danmuapiapp.domain.model.CoreBranchSelections
import com.example.danmuapiapp.domain.model.CoreDownloadProgress
import com.example.danmuapiapp.domain.model.CoreInfo
import com.example.danmuapiapp.domain.model.CoreSourceStatus
import com.example.danmuapiapp.domain.model.CoreVariantDisplayNames
import com.example.danmuapiapp.domain.model.GithubAccountStatus
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.model.formatCoreVersionTransition
import com.example.danmuapiapp.domain.model.formatCoreVersionValue
import com.example.danmuapiapp.domain.model.resolveCoreVariantRepo
import com.example.danmuapiapp.domain.model.resolveCoreVariantBranch
import com.example.danmuapiapp.domain.model.resolveCoreVariantSourceText
import com.example.danmuapiapp.ui.common.CustomCoreSettingsForm
import com.example.danmuapiapp.ui.common.rememberCustomCoreSettingsFormState
import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.AppDialogStyle
import com.example.danmuapiapp.ui.component.AppDialogTone
import com.example.danmuapiapp.ui.component.CoreDependencyRepairHost
import com.example.danmuapiapp.ui.component.CoreBranchPickerDialog
import com.example.danmuapiapp.ui.component.CoreUpdateAvailableDialog
import com.example.danmuapiapp.ui.component.FloatingBottomBarContentSpacer
import com.example.danmuapiapp.ui.component.GithubProxyPickerDialog
import com.example.danmuapiapp.ui.component.liquid.AppGlassButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassDangerButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassPrimaryButton
import com.example.danmuapiapp.ui.component.shouldOfferCoreUpdateActions
import com.example.danmuapiapp.ui.theme.LocalAppDarkTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CoreScreen(
    onOpenPullRequestLab: (ApiVariant) -> Unit = {},
    viewModel: CoreViewModel = hiltViewModel()
) {
    val coreList by viewModel.coreInfoList.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val pendingDependencyRepair by viewModel.pendingDependencyRepair.collectAsStateWithLifecycle()
    val runtimeState by viewModel.runtimeState.collectAsStateWithLifecycle()
    val displayNames by viewModel.coreDisplayNames.collectAsStateWithLifecycle()
    val branchSelections by viewModel.coreBranchSelections.collectAsStateWithLifecycle()
    val customRepo by viewModel.customRepo.collectAsStateWithLifecycle()
    val customBranch by viewModel.customRepoBranch.collectAsStateWithLifecycle()
    val githubStatus by viewModel.githubAccountStatus.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var viewedVariantKey by rememberSaveable { mutableStateOf<String?>(null) }
    val viewedVariant = ApiVariant.entries.firstOrNull { it.key == viewedVariantKey }
        ?: runtimeState.variant
    val viewedInfo = coreList.firstOrNull { it.variant == viewedVariant }

    LaunchedEffect(viewModel.operationMessage) {
        viewModel.operationMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbar) },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CoreWorkspaceHeader(
                activeVariantName = displayNames.resolve(runtimeState.variant),
                serviceStatus = runtimeState.status
            )

            VariantRail(
                selected = viewedVariant,
                displayNames = displayNames,
                onSelect = { viewedVariantKey = it.key }
            )

            if (viewedInfo == null) {
                CoreLoadingPanel()
            } else {
                CoreControlPanel(
                    info = viewedInfo,
                    runtimeVariant = runtimeState.variant,
                    viewModel = viewModel,
                    progress = downloadProgress,
                    pendingRepair = pendingDependencyRepair,
                    customRepo = customRepo,
                    customBranch = customBranch,
                    branchSelections = branchSelections,
                    displayNames = displayNames,
                    onOpenPullRequestLab = onOpenPullRequestLab
                )
            }

            GithubQuotaStrip(
                status = githubStatus,
                onRefresh = viewModel::refreshGithubAccount,
                onFillToken = viewModel::openGithubTokenDialog
            )
            FloatingBottomBarContentSpacer()
        }
    }

    CoreDependencyRepairHost(
        request = pendingDependencyRepair,
        showRequiredPrompt = viewModel.showDependencyRequiredPrompt,
        showRepairDialog = viewModel.showDependencyRepairDialog,
        onOpenRepair = viewModel::openDependencyRepairDialog,
        onDismissRequiredPrompt = viewModel::dismissDependencyRequiredPrompt,
        onOnlineRepair = viewModel::repairPendingDependenciesOnline,
        onRepairFromArchive = viewModel::repairPendingDependenciesFromArchive,
        onCancelMutation = viewModel::discardPendingCoreMutation,
        onDismissRepairDialog = viewModel::dismissDependencyRepairDialog
    )

    if (viewModel.showUpdateDialog) UpdateResultDialog(viewModel, displayNames)
    if (viewModel.showUpdateDetails) CoreUpdateDetailsPanel(viewModel, displayNames)
    if (viewModel.showRevisionHistory) CoreRevisionHistoryPanel(viewModel)
    viewModel.branchDialogVariant?.let { variant ->
        CoreBranchPickerDialog(
            variantLabel = displayNames.resolve(variant),
            catalog = viewModel.branchCatalog,
            currentBranch = branchSelections.resolve(variant),
            isLoading = viewModel.isLoadingBranches,
            errorMessage = viewModel.branchLoadError,
            onRetry = viewModel::retryLoadBranches,
            onConfirm = viewModel::switchBranch,
            onDismiss = viewModel::dismissBranchDialog
        )
    }
    viewModel.showVariantSettingsDialog?.let { variant ->
        VariantSettingsDialog(
            variant = variant,
            currentDisplayName = when (variant) {
                ApiVariant.Stable -> displayNames.stable
                ApiVariant.Dev -> displayNames.dev
                ApiVariant.Custom -> displayNames.custom
            },
            currentRepo = customRepo,
            currentBranch = customBranch,
            onSave = { name, repo, branch -> viewModel.saveVariantSettings(variant, name, repo, branch) },
            onDismiss = viewModel::dismissVariantSettingsDialog
        )
    }
    if (viewModel.showGithubTokenDialog) {
        GithubTokenDialog(
            tokenConfigured = githubStatus.tokenConfigured,
            status = githubStatus,
            onValidate = viewModel::validateAndSaveGithubToken,
            onClear = viewModel::clearGithubToken,
            onDismiss = viewModel::dismissGithubTokenDialog
        )
    }
    if (viewModel.showProxyPickerDialog) {
        GithubProxyPickerDialog(
            title = "选择 GitHub 线路",
            subtitle = "下载线路与 Token 验证分离；Token 始终只发往 GitHub 官方 API",
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
private fun CoreWorkspaceHeader(
    activeVariantName: String,
    serviceStatus: ServiceStatus
) {
    val statusColor = when (serviceStatus) {
        ServiceStatus.Running -> MaterialTheme.colorScheme.tertiary
        ServiceStatus.Starting, ServiceStatus.Stopping -> MaterialTheme.colorScheme.primary
        ServiceStatus.Error -> MaterialTheme.colorScheme.error
        ServiceStatus.Stopped -> MaterialTheme.colorScheme.outline
    }
    val statusText = when (serviceStatus) {
        ServiceStatus.Running -> "服务运行中"
        ServiceStatus.Starting -> "服务启动中"
        ServiceStatus.Stopping -> "服务停止中"
        ServiceStatus.Error -> "服务异常"
        ServiceStatus.Stopped -> "服务未运行"
    }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            "核心管理",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = statusColor) {}
            Text(
                "$statusText · 当前使用 $activeVariantName",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GithubQuotaStrip(
    status: GithubAccountStatus,
    onRefresh: () -> Unit,
    onFillToken: () -> Unit
) {
    val isDarkTheme = LocalAppDarkTheme.current
    val stateColor = when {
        status.tokenValid == false -> MaterialTheme.colorScheme.error
        status.tokenValid == true -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (isDarkTheme) {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDarkTheme) 0.42f else 0.55f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 13.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = when {
                        status.tokenValid == true -> Icons.Rounded.Verified
                        status.tokenValid == false -> Icons.Rounded.ErrorOutline
                        else -> Icons.Rounded.AccountCircle
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = stateColor
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        when {
                            status.isLoading -> "GitHub · 正在读取额度"
                            status.tokenValid == true -> "GitHub · ${status.login ?: "Token 已验证"}"
                            status.tokenValid == false -> "GitHub · Token 验证失败"
                            else -> "GitHub · 匿名访问"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        quotaText(status),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status.error != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AppGlassIconButton(onClick = onRefresh, enabled = !status.isLoading, size = 40.dp) {
                    if (status.isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Refresh, "刷新额度", Modifier.size(19.dp))
                }
                AppGlassButton(
                    onClick = onFillToken,
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Rounded.Key, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(if (status.tokenConfigured) "更换" else "填写", maxLines = 1)
                }
            }
            val remaining = status.coreRemaining
            val limit = status.coreLimit
            if (remaining != null && limit != null && limit > 0) {
                LinearProgressIndicator(
                    progress = { (remaining.toFloat() / limit.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = stateColor,
                    trackColor = if (isDarkTheme) {
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.58f)
                    } else {
                        Color.Transparent
                    }
                )
            }
        }
    }
}

@Composable
private fun VariantRail(
    selected: ApiVariant,
    displayNames: CoreVariantDisplayNames,
    onSelect: (ApiVariant) -> Unit
) {
    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ApiVariant.entries.forEach { variant ->
                val isSelected = variant == selected
                AppGlassSurface(
                    onClick = { onSelect(variant) },
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            displayNames.resolve(variant),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoreLoadingPanel() {
    AppGlassSurface(
        modifier = Modifier.fillMaxWidth().height(310.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun CoreControlPanel(
    info: CoreInfo,
    runtimeVariant: ApiVariant,
    viewModel: CoreViewModel,
    progress: CoreDownloadProgress,
    pendingRepair: CoreDependencyRepairRequest?,
    customRepo: String,
    customBranch: String,
    branchSelections: CoreBranchSelections,
    displayNames: CoreVariantDisplayNames,
    onOpenPullRequestLab: (ApiVariant) -> Unit
) {
    val isDarkTheme = LocalAppDarkTheme.current
    val source = resolveCoreVariantSourceText(
        info.variant,
        customRepo,
        customBranch,
        branchSelections
    )
    val sourceRepo = resolveCoreVariantRepo(info.variant, customRepo)
    val sourceBranch = resolveCoreVariantBranch(
        info.variant,
        customRepo,
        customBranch,
        branchSelections
    )
    val label = displayNames.resolve(info.variant)
    var deleteConfirm by remember(info.variant) { mutableStateOf(false) }
    var menuExpanded by remember(info.variant) { mutableStateOf(false) }
    val repairing = pendingRepair?.variant == info.variant
    val isCurrent = info.variant == runtimeVariant
    val needsSource = info.variant == ApiVariant.Custom && source.isBlank()
    val busy = viewModel.isOperating || viewModel.isCheckingUpdate

    AppGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (isDarkTheme) {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDarkTheme) 0.44f else 0.65f)
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        if (isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
            Column(
                modifier = Modifier.padding(start = 18.dp, top = 14.dp, end = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (isCurrent) "当前核心" else "可切换核心",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    CoreStatusPill(
                        text = coreStatusText(info, repairing),
                        attention = info.needsAttention || repairing
                    )
                    Spacer(Modifier.width(6.dp))
                    Box {
                        AppGlassIconButton(onClick = { menuExpanded = true }, size = 36.dp) {
                            Icon(Icons.Rounded.MoreVert, "更多操作")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.86f),
                            tonalElevation = 0.dp
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (info.variant == ApiVariant.Custom) "编辑名称与仓库" else "编辑显示名称") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.openVariantSettingsDialog(info.variant)
                                },
                                leadingIcon = { Icon(Icons.Rounded.Edit, null) }
                            )
                            if (info.isInstalled) {
                                DropdownMenuItem(
                                    text = { Text("重新安装") },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.reinstallCore(info.variant)
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Sync, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("删除核心") },
                                    onClick = {
                                        menuExpanded = false
                                        deleteConfirm = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.DeleteOutline,
                                            null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = "已安装版本",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (info.isInstalled) formatCoreVersionValue(info.version) else "未安装",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (info.isInstalled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (info.pullRequestNumbers.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.CallMerge,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "已合并 PR",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = info.pullRequestNumbers.joinToString("  ") { "#$it" },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (info.hasVersionUpdate) {
                        Text(
                            text = "可更新：${formatCoreVersionTransition(info.version, info.availableVersion)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                CoreCommitSummary(
                    info = info,
                    isCheckingUpdate = viewModel.isCheckingUpdate
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.CallMerge,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = sourceRepo.ifBlank { "尚未配置仓库" },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (sourceRepo.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val canPickBranch = info.isInstalled && source.isNotBlank() && !busy && !repairing
                Row(
                    modifier = Modifier
                        .widthIn(max = 126.dp)
                        .clickable(enabled = canPickBranch) {
                            viewModel.openBranchDialog(info.variant)
                        }
                        .padding(horizontal = 6.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sourceBranch
                            ?: info.remoteBranch?.takeIf { it.isNotBlank() }
                            ?: "默认分支",
                        modifier = Modifier.widthIn(max = 96.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (canPickBranch) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Rounded.ArrowDropDown,
                        contentDescription = "展开分支列表",
                        modifier = Modifier.size(18.dp),
                        tint = if (canPickBranch) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))

            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                CoreContextAction(
                    info = info,
                    isCurrent = isCurrent,
                    repairing = repairing,
                    needsSource = needsSource,
                    busy = busy,
                    viewModel = viewModel
                )

                if (info.isInstalled) {
                    val hasContextAction = repairing || needsSource || info.sourceMismatch || !isCurrent
                    if (hasContextAction) Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppGlassPrimaryButton(
                            onClick = {
                                when {
                                    info.sourceMismatch ||
                                        info.sourceStatus == CoreSourceStatus.UnknownLegacy ->
                                        viewModel.doUpdate(info.variant)
                                    info.hasVersionUpdate -> viewModel.openUpdatePrompt(info.variant)
                                    else -> viewModel.checkUpdate(info.variant)
                                }
                            },
                            enabled = !busy && !repairing,
                            modifier = Modifier.weight(1f).height(46.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            if (viewModel.isCheckingUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = androidx.compose.material3.LocalContentColor.current
                                )
                            } else {
                                Icon(
                                    if (info.needsAttention) Icons.Rounded.SystemUpdate else Icons.Rounded.Refresh,
                                    null,
                                    Modifier.size(19.dp)
                                )
                            }
                            Spacer(Modifier.width(7.dp))
                            Text(
                                when {
                                    info.sourceMismatch ||
                                        info.sourceStatus == CoreSourceStatus.UnknownLegacy -> "应用变更"
                                    info.hasVersionUpdate -> "查看更新"
                                    else -> "检查更新"
                                },
                                maxLines = 1
                            )
                        }
                        AppGlassButton(
                            onClick = { viewModel.openRollbackDialog(info.variant) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f).height(46.dp),
                            surfaceColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.42f),
                            contentColor = if (busy) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Rounded.History, null, Modifier.size(19.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("版本回退", maxLines = 1)
                        }
                    }
                }

                DownloadProgressBlock(
                    progress = progress,
                    variant = info.variant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoreShortcutButton(
                    icon = Icons.Rounded.CloudDownload,
                    label = "下载线路",
                    enabled = !busy,
                    onClick = viewModel::openProxyPickerManually,
                    modifier = Modifier.weight(1f)
                )
                CoreShortcutButton(
                    icon = Icons.AutoMirrored.Rounded.CallMerge,
                    label = "PR 实验室",
                    enabled = !needsSource && !busy && !repairing,
                    onClick = { onOpenPullRequestLab(info.variant) },
                    modifier = Modifier.weight(1f)
                )
                CoreShortcutButton(
                    icon = Icons.Rounded.Settings,
                    label = "核心设置",
                    onClick = { viewModel.openVariantSettingsDialog(info.variant) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (deleteConfirm) {
        AppDialog(
            onDismissRequest = { deleteConfirm = false },
            style = AppDialogStyle.Confirm,
            tone = AppDialogTone.Danger,
            icon = { Icon(Icons.Rounded.DeleteOutline, null) },
            title = { Text("删除 $label？") },
            text = { Text("将删除当前工作目录中的该核心文件。") },
            confirmButton = {
                AppGlassDangerButton(
                    onClick = { deleteConfirm = false; viewModel.deleteCore(info.variant) },
                ) { Text("删除") }
            },
            dismissButton = { AppGlassButton(onClick = { deleteConfirm = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun CoreStatusPill(text: String, attention: Boolean) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (attention) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (attention) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun CoreCommitSummary(
    info: CoreInfo,
    isCheckingUpdate: Boolean,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = LocalAppDarkTheme.current
    val hasCheckError = !info.updateCheckError.isNullOrBlank()
    val badgeText = when {
        isCheckingUpdate -> "检查中"
        info.remoteCommit != null -> info.remoteCommit.shortSha
        info.sourceCommitSha.isNotBlank() -> info.sourceCommitSha.take(7)
        info.isInstalled -> "本地"
        else -> "未安装"
    }
    val headline = when {
        isCheckingUpdate -> "正在读取远程版本与提交信息"
        hasCheckError -> "上次检查失败：${info.updateCheckError}"
        !info.remoteCommit?.title.isNullOrBlank() -> info.remoteCommit.title
        info.pullRequestNumbers.isNotEmpty() ->
            "本地 PR 组合：${info.pullRequestNumbers.joinToString(" + ") { "#$it" }}"
        info.isInstalled -> "本地核心已就绪，检查更新后显示远程提交信息"
        else -> "安装核心后可检查版本与提交信息"
    }
    val containerColor = when {
        hasCheckError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = if (isDarkTheme) 0.46f else 0.62f)
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDarkTheme) 0.42f else 0.58f)
    }
    val badgeColor = when {
        hasCheckError -> MaterialTheme.colorScheme.error.copy(alpha = if (isDarkTheme) 0.22f else 0.12f)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkTheme) 0.22f else 0.11f)
    }
    val accentColor = if (hasCheckError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    AppGlassSurface(
        modifier = modifier.fillMaxWidth().heightIn(min = 60.dp),
        shape = RoundedCornerShape(8.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = RoundedCornerShape(6.dp), color = badgeColor) {
                Text(
                    text = badgeText,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                    maxLines = 1
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (hasCheckError) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CoreContextAction(
    info: CoreInfo,
    isCurrent: Boolean,
    repairing: Boolean,
    needsSource: Boolean,
    busy: Boolean,
    viewModel: CoreViewModel
) {
    when {
        repairing -> AppGlassButton(
            onClick = viewModel::openDependencyRepairDialog,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            surfaceColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.52f),
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ) {
            Icon(Icons.Rounded.Build, null, Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text("修复核心依赖")
        }
        needsSource -> AppGlassButton(
            onClick = { viewModel.openVariantSettingsDialog(info.variant) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            surfaceColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(Icons.Rounded.Settings, null, Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text("配置核心仓库")
        }
        !info.isInstalled -> AppGlassPrimaryButton(
            onClick = { viewModel.installCore(info.variant) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text("安装此核心")
        }
        info.sourceMismatch -> AppGlassButton(
            onClick = { viewModel.reinstallCore(info.variant) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            surfaceColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.52f),
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ) {
            Icon(Icons.Rounded.Sync, null, Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text("按当前仓库重新安装")
        }
        !isCurrent -> AppGlassPrimaryButton(
            onClick = { viewModel.updateVariant(info.variant) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Icon(Icons.Rounded.SwapHoriz, null, Modifier.size(19.dp))
            Spacer(Modifier.width(7.dp))
            Text("切换到此核心")
        }
    }
}

@Composable
private fun CoreShortcutButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    AppGlassButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        height = 40.dp,
        shape = RoundedCornerShape(10.dp),
        surfaceColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.42f),
        contentColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        contentPadding = PaddingValues(horizontal = 6.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DownloadProgressBlock(
    progress: CoreDownloadProgress,
    variant: ApiVariant,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = progress.inProgress && progress.variant == variant,
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val percent = progress.progress?.let { "${(it * 100).toInt().coerceIn(0, 100)}%" } ?: "处理中"
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${progress.actionLabel} · ${progress.stageText}", style = MaterialTheme.typography.labelMedium)
                Text(percent, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (progress.progress == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(5.dp))
            else LinearProgressIndicator(progress = { progress.progress }, modifier = Modifier.fillMaxWidth().height(5.dp))
            if (progress.downloadedBytes > 0) {
                Text(
                    if (progress.totalBytes > 0) "${formatBytes(progress.downloadedBytes)} / ${formatBytes(progress.totalBytes)}"
                    else formatBytes(progress.downloadedBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun coreStatusText(info: CoreInfo, repairing: Boolean): String = when {
    repairing -> "待修复"
    info.sourceMismatch -> "来源变更"
    info.sourceStatus == CoreSourceStatus.UnknownLegacy -> "待确认"
    info.hasVersionUpdate -> "有更新"
    info.pullRequestNumbers.isNotEmpty() -> "PR 组合"
    info.isInstalled -> "已安装"
    else -> "未安装"
}

@Composable
private fun UpdateResultDialog(vm: CoreViewModel, names: CoreVariantDisplayNames) {
    val info = vm.updateDialogInfo
    val variant = vm.updateDialogVariant
    val failed = info?.updateCheckError != null
    val hasVersionUpdate = shouldOfferCoreUpdateActions(
        hasVersionUpdate = info?.hasVersionUpdate == true,
        hasCheckError = failed,
        sourceMismatch = info?.sourceMismatch == true,
        sourceUnknownLegacy = info?.sourceStatus == CoreSourceStatus.UnknownLegacy
    )
    if (hasVersionUpdate && variant != null && info != null) {
        CoreUpdateAvailableDialog(
            variantLabel = names.resolve(variant),
            currentVersion = info.version,
            latestVersion = info.availableVersion ?: info.remoteVersion,
            remoteCommit = info.remoteCommit,
            onDismiss = vm::dismissUpdateDialog,
            onShowDetails = { vm.openUpdateDetails(variant) },
            onUpdateNow = { vm.doUpdate(variant) }
        )
        return
    }
    val canApply = info != null && !failed && (
        info.sourceMismatch ||
            info.sourceStatus == CoreSourceStatus.UnknownLegacy
        )
    AppDialog(
        onDismissRequest = vm::dismissUpdateDialog,
        style = AppDialogStyle.Status,
        tone = when {
            failed -> AppDialogTone.Danger
            info?.needsAttention == true -> AppDialogTone.Info
            else -> AppDialogTone.Success
        },
        icon = {
            Icon(
                when {
                    failed -> Icons.Rounded.ErrorOutline
                    info?.needsAttention == true -> Icons.Rounded.SystemUpdate
                    else -> Icons.Rounded.CheckCircle
                },
                null
            )
        },
        title = {
            Text(
                when {
                    failed -> "检查更新失败"
                    info?.needsAttention == true -> "发现核心变更"
                    else -> "当前已是最新"
                }
            )
        },
        text = {
            Text(
                when {
                    info == null -> "未能读取核心状态"
                    failed -> info.updateCheckError.orEmpty()
                    info.sourceMismatch -> "${names.resolve(info.variant)} 的当前来源与设置不一致，需要重新下载。"
                    info.sourceStatus == CoreSourceStatus.UnknownLegacy -> "旧版安装缺少来源标记，重新下载后将写入当前来源。"
                    else -> "${names.resolve(info.variant)} 暂无可用更新。"
                }
            )
        },
        confirmButton = {
            if (canApply && variant != null) {
            AppGlassPrimaryButton(
                onClick = { vm.doUpdate(variant) },
                ) { Text("重新下载") }
            } else AppGlassButton(onClick = vm::dismissUpdateDialog) { Text("完成") }
        },
        dismissButton = if (canApply) {
            { AppGlassButton(onClick = vm::dismissUpdateDialog) { Text("稍后") } }
        } else null
    )
}

@Composable
private fun GithubTokenDialog(
    tokenConfigured: Boolean,
    status: GithubAccountStatus,
    onValidate: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var token by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    AppDialog(
        onDismissRequest = onDismiss,
        style = AppDialogStyle.Form,
        tone = AppDialogTone.Brand,
        icon = { Icon(Icons.Rounded.Security, null) },
        title = { Text("GitHub API 凭据") },
        supportingText = { Text("Token 仅发送到 api.github.com，验证成功后保存到本机凭据存储") },
        text = {
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Personal Access Token") },
                placeholder = {
                    if (tokenConfigured) Text("已配置；输入新 Token 以替换")
                },
                leadingIcon = { Icon(Icons.Rounded.Key, null) },
                trailingIcon = {
                    AppGlassIconButton(onClick = { visible = !visible }, size = 34.dp) {
                        Icon(if (visible) Icons.Rounded.Security else Icons.Rounded.Key, "切换 Token 可见状态")
                    }
                },
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
            AppGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (token.isBlank()) "匿名访问" else "验证后保存", style = MaterialTheme.typography.labelMedium)
                    Text(
                        if (token.isBlank()) "不填写也会显示 GitHub 匿名额度（通常为每小时 60 次）。"
                        else "有效 Token 通常可获得每小时 5,000 次核心 REST API 请求额度。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            status.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        },
        confirmButton = {
            AppGlassPrimaryButton(
                onClick = { onValidate(token) },
                enabled = !status.isLoading && (token.isNotBlank() || !tokenConfigured)
            ) {
                if (status.isLoading) {
                    CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(7.dp))
                }
                Text(if (token.isBlank()) "使用匿名额度" else "验证并保存")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (tokenConfigured) {
                    AppGlassButton(onClick = onClear) { Text("清空 Token") }
                }
                AppGlassButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

@Composable
private fun VariantSettingsDialog(
    variant: ApiVariant,
    currentDisplayName: String,
    currentRepo: String,
    currentBranch: String,
    onSave: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val custom = variant == ApiVariant.Custom
    val form = rememberCustomCoreSettingsFormState(currentDisplayName, currentRepo, currentBranch)
    var name by remember(variant, currentDisplayName) { mutableStateOf(currentDisplayName) }
    AppDialog(
        onDismissRequest = onDismiss,
        style = AppDialogStyle.Form,
        tone = AppDialogTone.Brand,
        icon = { Icon(if (custom) Icons.Rounded.Tune else Icons.Rounded.Edit, null) },
        title = { Text(if (custom) "自定义核心来源" else "核心显示名称") },
        text = {
            if (custom) CustomCoreSettingsForm(state = form, displayNamePlaceholder = variant.label)
            else {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("显示名称") },
                    placeholder = { Text(variant.label) },
                    singleLine = true
                )
                Text(
                    "实际仓库：${resolveCoreVariantRepo(variant, currentRepo)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            AppGlassPrimaryButton(
                onClick = {
                    if (custom) form.toInput().let { onSave(it.displayName, it.repo, it.branch) }
                    else onSave(name.trim(), "", "")
                },
                enabled = !custom || form.canSaveConfig
            ) { Text("保存") }
        },
        dismissButton = { AppGlassButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun quotaText(status: GithubAccountStatus): String {
    val remaining = status.coreRemaining
    val limit = status.coreLimit
    val reset = status.coreResetEpochSeconds
    if (remaining == null || limit == null) return status.error ?: "额度尚未读取"
    val resetText = reset?.let {
        runCatching {
            DateTimeFormatter.ofPattern("HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochSecond(it))
        }.getOrNull()
    }
    return "本小时剩余 $remaining / $limit${resetText?.let { " · $it 重置" }.orEmpty()}"
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
