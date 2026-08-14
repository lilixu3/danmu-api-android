package com.example.danmuapiapp.ui.screen.core

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreDependencyRepairRequest
import com.example.danmuapiapp.domain.model.CoreDownloadProgress
import com.example.danmuapiapp.domain.model.CoreInfo
import com.example.danmuapiapp.domain.model.CoreSourceStatus
import com.example.danmuapiapp.domain.model.CoreVariantDisplayNames
import com.example.danmuapiapp.domain.model.GithubAccountStatus
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.model.formatCoreVersionTransition
import com.example.danmuapiapp.domain.model.formatCoreVersionValue
import com.example.danmuapiapp.domain.model.resolveCoreVariantRepo
import com.example.danmuapiapp.domain.model.resolveCoreVariantSourceText
import com.example.danmuapiapp.ui.common.CustomCoreSettingsForm
import com.example.danmuapiapp.ui.common.rememberCustomCoreSettingsFormState
import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.AppDialogStyle
import com.example.danmuapiapp.ui.component.AppDialogTone
import com.example.danmuapiapp.ui.component.CoreDependencyRepairHost
import com.example.danmuapiapp.ui.component.GithubProxyPickerDialog
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
    val customRepo by viewModel.customRepo.collectAsStateWithLifecycle()
    val customBranch by viewModel.customRepoBranch.collectAsStateWithLifecycle()
    val githubStatus by viewModel.githubAccountStatus.collectAsStateWithLifecycle()
    val githubToken by viewModel.githubToken.collectAsStateWithLifecycle()
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
        snackbarHost = { SnackbarHost(snackbar) },
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
                    serviceStatus = runtimeState.status,
                    viewModel = viewModel,
                    progress = downloadProgress,
                    pendingRepair = pendingDependencyRepair,
                    customRepo = customRepo,
                    customBranch = customBranch,
                    displayNames = displayNames,
                    onOpenPullRequestLab = onOpenPullRequestLab
                )
            }

            GithubQuotaStrip(
                status = githubStatus,
                onRefresh = viewModel::refreshGithubAccount,
                onFillToken = viewModel::openGithubTokenDialog
            )
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
    if (viewModel.showRevisionHistory) CoreRevisionHistoryPanel(viewModel)
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
            initialToken = githubToken,
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
        Text("核心管理", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
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
    Surface(
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
                IconButton(onClick = onRefresh, enabled = !status.isLoading, modifier = Modifier.size(40.dp)) {
                    if (status.isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Refresh, "刷新额度", Modifier.size(19.dp))
                }
                TextButton(
                    onClick = onFillToken,
                    shape = RoundedCornerShape(8.dp),
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ApiVariant.entries.forEach { variant ->
                val isSelected = variant == selected
                Surface(
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
    Surface(
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
    serviceStatus: ServiceStatus,
    viewModel: CoreViewModel,
    progress: CoreDownloadProgress,
    pendingRepair: CoreDependencyRepairRequest?,
    customRepo: String,
    customBranch: String,
    displayNames: CoreVariantDisplayNames,
    onOpenPullRequestLab: (ApiVariant) -> Unit
) {
    val isDarkTheme = LocalAppDarkTheme.current
    val source = resolveCoreVariantSourceText(info.variant, customRepo, customBranch)
    val label = displayNames.resolve(info.variant)
    var deleteConfirm by remember(info.variant) { mutableStateOf(false) }
    var menuExpanded by remember(info.variant) { mutableStateOf(false) }
    val repairing = pendingRepair?.variant == info.variant
    val isCurrent = info.variant == runtimeVariant
    val needsSource = info.variant == ApiVariant.Custom && source.isBlank()
    val busy = viewModel.isOperating || viewModel.isCheckingUpdate

    Surface(
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
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (isCurrent) "当前使用" else "可切换核心",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                label,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            StatusPill(coreStatusText(info, repairing), info.needsAttention || repairing)
                        }
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Rounded.MoreVert, "更多操作") }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(if (info.variant == ApiVariant.Custom) "编辑名称与仓库" else "编辑显示名称") },
                                onClick = { menuExpanded = false; viewModel.openVariantSettingsDialog(info.variant) },
                                leadingIcon = { Icon(Icons.Rounded.Edit, null) }
                            )
                            if (info.isInstalled) {
                                DropdownMenuItem(
                                    text = { Text("重新安装") },
                                    onClick = { menuExpanded = false; viewModel.reinstallCore(info.variant) },
                                    leadingIcon = { Icon(Icons.Rounded.Sync, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("删除核心") },
                                    onClick = { menuExpanded = false; deleteConfirm = true },
                                    leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) }
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("已安装版本", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (info.isInstalled) formatCoreVersionValue(info.version) else "未安装",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (info.isInstalled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (info.hasVersionUpdate) {
                        Text(
                            "可更新：${formatCoreVersionTransition(info.version, info.availableVersion)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (info.pullRequestNumbers.isNotEmpty()) {
                        Text(
                            "本地 PR 组合：${info.pullRequestNumbers.joinToString(" + ") { "#$it" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        when {
                            source.isNotBlank() -> source
                            info.variant == ApiVariant.Custom -> "尚未配置仓库"
                            else -> info.variant.repo
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

                when {
                    repairing -> Button(
                        onClick = viewModel::openDependencyRepairDialog,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Rounded.Build, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("修复核心依赖")
                    }
                    needsSource -> Button(
                        onClick = { viewModel.openVariantSettingsDialog(info.variant) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Rounded.Settings, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("配置核心仓库")
                    }
                    !info.isInstalled -> Button(
                        onClick = { viewModel.installCore(info.variant) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("安装此核心")
                    }
                    info.sourceMismatch -> Button(
                        onClick = { viewModel.reinstallCore(info.variant) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Rounded.Sync, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("按当前仓库重新安装")
                    }
                    !isCurrent -> Button(
                        onClick = { viewModel.updateVariant(info.variant) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Rounded.SwapHoriz, null, Modifier.size(19.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("切换到此核心")
                    }
                    else -> FilledTonalButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(
                            if (serviceStatus == ServiceStatus.Running) "当前核心 · 服务运行中"
                            else "当前使用的核心"
                        )
                    }
                }

                if (info.isInstalled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        FilledTonalButton(
                            onClick = {
                                if (info.needsAttention) viewModel.doUpdate(info.variant)
                                else viewModel.checkUpdate(info.variant)
                            },
                            enabled = !busy && !repairing,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(
                                if (info.needsAttention) Icons.Rounded.SystemUpdate else Icons.Rounded.Refresh,
                                null,
                                Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (info.needsAttention) "应用更新" else "检查更新", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { viewModel.openRollbackDialog(info.variant) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Rounded.History, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("版本回退", maxLines = 1)
                        }
                    }
                }

                if (!needsSource) {
                    OutlinedButton(
                        onClick = { onOpenPullRequestLab(info.variant) },
                        enabled = !busy && !repairing,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.CallMerge, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("PR 实验室 · 本地组合测试")
                    }
                }

                DownloadProgressBlock(progress, info.variant)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = viewModel::openProxyPickerManually, shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Rounded.CloudOff, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("下载线路")
                    }
                    TextButton(
                        onClick = { viewModel.openVariantSettingsDialog(info.variant) },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Rounded.Settings, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("核心设置")
                    }
                }
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
                TextButton(
                    onClick = { deleteConfirm = false; viewModel.deleteCore(info.variant) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun DownloadProgressBlock(progress: CoreDownloadProgress, variant: ApiVariant) {
    AnimatedVisibility(progress.inProgress && progress.variant == variant) {
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

@Composable
private fun StatusPill(text: String, attention: Boolean) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (attention) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (attention) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
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
    AppDialog(
        onDismissRequest = vm::dismissUpdateDialog,
        style = AppDialogStyle.Status,
        tone = if (info?.needsAttention == true) AppDialogTone.Info else AppDialogTone.Success,
        icon = { Icon(if (info?.needsAttention == true) Icons.Rounded.SystemUpdate else Icons.Rounded.CheckCircle, null) },
        title = { Text(if (info?.needsAttention == true) "发现核心变更" else "当前已是最新") },
        text = {
            Text(
                when {
                    info == null -> "未能读取核心状态"
                    info.sourceMismatch -> "${names.resolve(info.variant)} 的当前来源与设置不一致，需要重新下载。"
                    info.sourceStatus == CoreSourceStatus.UnknownLegacy -> "旧版安装缺少来源标记，重新下载后将写入当前来源。"
                    info.hasVersionUpdate -> formatCoreVersionTransition(info.version, info.availableVersion)
                    else -> "${names.resolve(info.variant)} 暂无可用更新。"
                }
            )
        },
        confirmButton = {
            if (info?.needsAttention == true && variant != null) {
                Button(onClick = { vm.doUpdate(variant) }) { Text("应用变更") }
            } else TextButton(onClick = vm::dismissUpdateDialog) { Text("完成") }
        },
        dismissButton = if (info?.needsAttention == true) {
            { TextButton(onClick = vm::dismissUpdateDialog) { Text("稍后") } }
        } else null
    )
}

@Composable
private fun GithubTokenDialog(
    initialToken: String,
    status: GithubAccountStatus,
    onValidate: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var token by remember(initialToken) { mutableStateOf(initialToken) }
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
                leadingIcon = { Icon(Icons.Rounded.Key, null) },
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(if (visible) Icons.Rounded.Security else Icons.Rounded.Key, "切换 Token 可见状态")
                    }
                },
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
            Surface(
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
            Button(onClick = { onValidate(token) }, enabled = !status.isLoading) {
                if (status.isLoading) {
                    CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(7.dp))
                }
                Text(if (token.isBlank()) "使用匿名额度" else "验证并保存")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (initialToken.isNotBlank()) {
                    TextButton(onClick = onClear) { Text("清空 Token") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
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
            Button(
                onClick = {
                    if (custom) form.toInput().let { onSave(it.displayName, it.repo, it.branch) }
                    else onSave(name.trim(), "", "")
                },
                enabled = !custom || form.canSaveConfig
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
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
