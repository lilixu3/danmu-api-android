package com.example.danmuapiapp.ui.screen.core

import com.example.danmuapiapp.ui.component.AppToast

import com.example.danmuapiapp.ui.component.CorePullRequestUpdateChoiceHost
import com.example.danmuapiapp.ui.component.AppGlassSurface

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreInfo
import com.example.danmuapiapp.domain.model.CoreSourceStatus
import com.example.danmuapiapp.domain.model.CoreVariantDisplayNames
import com.example.danmuapiapp.domain.model.GithubAccountStatus
import com.example.danmuapiapp.domain.model.resolveCoreVariantRepo
import com.example.danmuapiapp.domain.model.resolveCoreVariantBranch
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
import com.example.danmuapiapp.ui.component.liquid.AppGlassIconButton
import com.example.danmuapiapp.ui.component.liquid.AppGlassPrimaryButton
import com.example.danmuapiapp.ui.component.shouldOfferCoreUpdateActions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoreScreen(
    onOpenPullRequestLab: (ApiVariant) -> Unit = {},
    viewModel: CoreViewModel = hiltViewModel()
) {
    val coreList by viewModel.coreInfoList.collectAsStateWithLifecycle()
    val isCoreInfoLoading by viewModel.isCoreInfoLoading.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val pendingDependencyRepair by viewModel.pendingDependencyRepair.collectAsStateWithLifecycle()
    val runtimeState by viewModel.runtimeState.collectAsStateWithLifecycle()
    val displayNames by viewModel.coreDisplayNames.collectAsStateWithLifecycle()
    val branchSelections by viewModel.coreBranchSelections.collectAsStateWithLifecycle()
    val customRepo by viewModel.customRepo.collectAsStateWithLifecycle()
    val customBranch by viewModel.customRepoBranch.collectAsStateWithLifecycle()
    val githubStatus by viewModel.githubAccountStatus.collectAsStateWithLifecycle()
    val selectedProxyId by viewModel.selectedGithubProxyId.collectAsStateWithLifecycle()
    var viewedVariantKey by rememberSaveable { mutableStateOf<String?>(null) }
    val viewedVariant = ApiVariant.entries.firstOrNull { it.key == viewedVariantKey }
        ?: runtimeState.variant
    val viewedInfo = coreList.firstOrNull { it.variant == viewedVariant }
    val cachedInfo = if (isCoreInfoLoading) viewModel.cachedCoreDisplayInfo(viewedVariant) else null
    val displayInfo = cachedInfo ?: viewedInfo ?: CoreInfo(viewedVariant, null, isInstalled = false)
    val hasLocalInfo = cachedInfo != null || (!isCoreInfoLoading && viewedInfo != null)

    val viewedSource = resolveCoreVariantRepo(viewedVariant, customRepo) to
        resolveCoreVariantBranch(viewedVariant, customRepo, customBranch, branchSelections)
    LifecycleResumeEffect(viewedVariant, viewedSource) {
        viewModel.refreshWorkspace(viewedVariant)
        onPauseOrDispose { }
    }

    LaunchedEffect(viewModel.operationMessage) {
        viewModel.operationMessage?.let {
            AppToast.show(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = viewModel.isManualWorkspaceRefresh,
            onRefresh = { viewModel.refreshWorkspace(viewedVariant, force = true) },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CoreWorkspaceHeader(
                    refreshing = viewModel.isManualWorkspaceRefresh,
                    enabled = !viewModel.isOperating,
                    onRefresh = { viewModel.refreshWorkspace(viewedVariant, force = true) }
                )

                VariantRail(
                    selected = viewedVariant,
                    active = runtimeState.variant,
                    enabled = !viewModel.isOperating && !viewModel.isCheckingUpdate,
                    displayNames = displayNames,
                    onSelect = { viewedVariantKey = it.key }
                )

                CoreControlPanel(
                    info = displayInfo,
                    localInfoLoading = isCoreInfoLoading,
                    hasLocalInfo = hasLocalInfo,
                    runtimeVariant = runtimeState.variant,
                    viewModel = viewModel,
                    pendingRepair = pendingDependencyRepair,
                    customRepo = customRepo,
                    customBranch = customBranch,
                    branchSelections = branchSelections,
                    displayNames = displayNames,
                    routeLabel = viewModel.proxyOptions.firstOrNull { it.id == selectedProxyId }
                        ?.let { if (it.isOriginal) "直连" else "线路" } ?: "线路",
                    onOpenPullRequestLab = onOpenPullRequestLab
                )

                GithubQuotaStrip(
                    status = githubStatus,
                    onRefresh = viewModel::refreshGithubAccount,
                    onFillToken = viewModel::openGithubTokenDialog
                )
                DownloadProgressBlock(downloadProgress)
                FloatingBottomBarContentSpacer()
            }
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

    CorePullRequestUpdateChoiceHost(viewModel.coreUpdateChoice)
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
            pullRequestNumbers = info.pullRequestNumbers,
            updatedPullRequestNumbers = info.updatedPullRequestHeads.keys.toList(),
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
internal fun GithubTokenDialog(
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
