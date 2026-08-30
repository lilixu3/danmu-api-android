package com.example.danmuapiapp.desktop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.danmuapiapp.desktop.core.CoreDiffLineType
import com.example.danmuapiapp.desktop.core.CoreInstallProgress
import com.example.danmuapiapp.desktop.core.CoreInstallStage
import com.example.danmuapiapp.desktop.core.CoreUpdateCheck
import com.example.danmuapiapp.desktop.core.compareCoreUpdate
import com.example.danmuapiapp.desktop.core.CorePullRequest
import com.example.danmuapiapp.desktop.core.CoreRevision
import com.example.danmuapiapp.desktop.core.CoreRevisionDetails
import com.example.danmuapiapp.desktop.core.CoreRevisionFileChange
import com.example.danmuapiapp.desktop.core.DesktopCoreInfo
import com.example.danmuapiapp.desktop.core.DesktopCoreVariant
import com.example.danmuapiapp.desktop.core.GithubCoreRemote
import com.example.danmuapiapp.desktop.node.DesktopCoreInstaller
import com.example.danmuapiapp.desktop.runtime.DesktopPaths
import com.example.danmuapiapp.desktop.runtime.DesktopRuntimeController
import com.example.danmuapiapp.desktop.runtime.ServicePhase
import com.example.danmuapiapp.desktop.runtime.ServiceUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class CoreRoute {
    Overview,
    History,
    RevisionDetails,
    PullRequests,
    PullRequestDetails,
}

@Composable
fun CorePage(
    controller: DesktopRuntimeController,
    paths: DesktopPaths,
    state: ServiceUiState,
    foregroundTick: Int = 0,
    initialUpdate: CoreUpdateCheck? = null,
    onInitialUpdateConsumed: () -> Unit = {},
) {
    val scriptDir = File(paths.runtimeDir, "nodejs-project")
    var selectedVariant by remember { mutableStateOf(DesktopCoreVariant.fromKey(controller.configuredRuntime().variant)) }
    var selectedRoute by remember { mutableStateOf(controller.settings.githubProxyId) }
    var routeConfirmed by remember { mutableStateOf(controller.settings.githubProxyConfirmed) }
    var routeDialog by remember { mutableStateOf<CoreRouteRequest?>(null) }
    var operationMessage by remember { mutableStateOf<String?>(null) }
    var operationBusy by remember { mutableStateOf(false) }
    var remoteBusy by remember { mutableStateOf(false) }
    var installProgress by remember { mutableStateOf<CoreInstallProgress?>(null) }
    var confirmation by remember { mutableStateOf<CoreOperationRequest?>(null) }
    var pendingUpdate by remember { mutableStateOf<CoreUpdateCheck?>(initialUpdate) }
    androidx.compose.runtime.LaunchedEffect(initialUpdate) {
        if (initialUpdate != null) onInitialUpdateConsumed()
    }
    var refreshNonce by remember { mutableStateOf(0) }
    var route by remember { mutableStateOf(CoreRoute.Overview) }
    var history by remember { mutableStateOf<List<CoreRevision>>(emptyList()) }
    var historyError by remember { mutableStateOf<String?>(null) }
    var selectedRevision by remember { mutableStateOf<CoreRevision?>(null) }
    var revisionDetails by remember { mutableStateOf<CoreRevisionDetails?>(null) }
    var revisionDetailsError by remember { mutableStateOf<String?>(null) }
    var pullRequests by remember { mutableStateOf<List<CorePullRequest>>(emptyList()) }
    var pullRequestsError by remember { mutableStateOf<String?>(null) }
    var selectedPullRequest by remember { mutableStateOf<CorePullRequest?>(null) }
    var pullRequestFiles by remember { mutableStateOf<List<CoreRevisionFileChange>>(emptyList()) }
    var pullRequestFilesError by remember { mutableStateOf<String?>(null) }
    val operationScope = rememberCoroutineScope()
    val infos = remember(scriptDir, refreshNonce) {
        DesktopCoreVariant.entries.associateWith { variant ->
            DesktopCoreInstaller.inspect(
                scriptDir = scriptDir,
                variant = variant,
                repository = variant.defaultRepository.orEmpty(),
                branch = variant.defaultBranch,
            )
        }
    }

    fun checkForUpdate(request: CoreOperationRequest, automatic: Boolean) {
        if (remoteBusy || operationBusy) return
        val repository = request.variant.defaultRepository
        if (repository == null) {
            if (!automatic) operationMessage = "自定义核心尚未配置仓库"
            return
        }
        val info = infos.getValue(request.variant)
        if (!info.valid || info.source?.commitSha.isNullOrBlank()) {
            if (!automatic) operationMessage = "核心缺少来源提交，无法安全检查更新"
            return
        }
        remoteBusy = true
        operationScope.launch {
            try {
                val remote = withContext(Dispatchers.IO) {
                    GithubCoreRemote(repository, selectedRoute, controller.settings.githubToken)
                        .branchHead(request.variant.defaultBranch)
                }
                val result = compareCoreUpdate(info, remote)
                controller.settings.setLastCoreUpdateCheckAt(request.variant.key, System.currentTimeMillis())
                if (result.available) pendingUpdate = result
                else if (!automatic) operationMessage = "${request.variant.label} 已是最新（${remote.shortSha}）"
            } catch (error: Throwable) {
                if (!automatic) operationMessage = "检查更新失败：${diagnostic(error)}"
            } finally {
                remoteBusy = false
            }
        }
    }

    fun request(request: CoreOperationRequest) {
        if (operationBusy || remoteBusy || confirmation != null || installProgress != null) return
        when (request.action) {
            DesktopCoreActionLabel.Reinstall,
            DesktopCoreActionLabel.Delete,
            DesktopCoreActionLabel.Rollback,
            -> confirmation = request
            DesktopCoreActionLabel.Install,
            DesktopCoreActionLabel.Update,
            -> {
                if (request.requiresRoute && !routeConfirmed) {
                    routeDialog = CoreRouteRequest.Operation(request)
                } else if (request.action == DesktopCoreActionLabel.Update) {
                    checkForUpdate(request, automatic = false)
                } else {
                    executeCoreOperation(
                        request,
                        scriptDir,
                        paths,
                        controller,
                        selectedRoute,
                        state,
                        onBusy = { operationBusy = it },
                        onProgress = { installProgress = it },
                        onMessage = { installProgress = null; operationMessage = it; refreshNonce++ },
                        onRouteInvalid = {
                            routeConfirmed = false
                            controller.settings.setGithubProxyConfirmed(false)
                            operationMessage = "已选 GitHub 线路超时或不可达，请重新选择线路。"
                        },
                        scope = operationScope,
                    )
                }
            }
        }
    }

    fun loadHistory() {
        if (remoteBusy) return
        route = CoreRoute.History
        historyError = null
        remoteBusy = true
        val repository = selectedVariant.defaultRepository
        if (repository == null) {
            historyError = "自定义核心尚未配置仓库"
            remoteBusy = false
            return
        }
        operationScope.launch {
            try {
                history = withContext(Dispatchers.IO) {
                    GithubCoreRemote(repository, selectedRoute, controller.settings.githubToken)
                        .commits(selectedVariant.defaultBranch, perPage = 20)
                        .revisions
                }
            } catch (error: Throwable) {
                history = emptyList()
                historyError = "无法读取提交历史：${diagnostic(error)}"
            } finally {
                remoteBusy = false
            }
        }
    }

    fun loadRevisionDetails(revision: CoreRevision) {
        if (remoteBusy) return
        val repository = selectedVariant.defaultRepository
        if (repository == null) {
            revisionDetailsError = "自定义核心尚未配置仓库"
            route = CoreRoute.RevisionDetails
            return
        }
        selectedRevision = revision
        revisionDetails = null
        revisionDetailsError = null
        route = CoreRoute.RevisionDetails
        remoteBusy = true
        operationScope.launch {
            try {
                revisionDetails = withContext(Dispatchers.IO) {
                    GithubCoreRemote(repository, selectedRoute, controller.settings.githubToken)
                        .commitDetails(revision.commitSha)
                }
            } catch (error: Throwable) {
                revisionDetailsError = "无法读取文件变更：${diagnostic(error)}"
            } finally {
                remoteBusy = false
            }
        }
    }

    fun loadPullRequests() {
        if (remoteBusy) return
        route = CoreRoute.PullRequests
        pullRequestsError = null
        remoteBusy = true
        val repository = selectedVariant.defaultRepository
        if (repository == null) {
            pullRequestsError = "自定义核心尚未配置仓库"
            remoteBusy = false
            return
        }
        operationScope.launch {
            try {
                pullRequests = withContext(Dispatchers.IO) {
                    GithubCoreRemote(repository, selectedRoute, controller.settings.githubToken)
                        .pullRequests(state = "open", perPage = 30)
                }
            } catch (error: Throwable) {
                pullRequests = emptyList()
                pullRequestsError = "无法读取 PR 列表：${diagnostic(error)}"
            } finally {
                remoteBusy = false
            }
        }
    }

    fun loadPullRequestFiles(pull: CorePullRequest) {
        if (remoteBusy) return
        val repository = selectedVariant.defaultRepository
        if (repository == null) {
            pullRequestFilesError = "自定义核心尚未配置仓库"
            route = CoreRoute.PullRequestDetails
            return
        }
        selectedPullRequest = pull
        pullRequestFiles = emptyList()
        pullRequestFilesError = null
        route = CoreRoute.PullRequestDetails
        remoteBusy = true
        operationScope.launch {
            try {
                pullRequestFiles = withContext(Dispatchers.IO) {
                    GithubCoreRemote(repository, selectedRoute, controller.settings.githubToken)
                        .pullRequestFiles(pull.number)
                }
            } catch (error: Throwable) {
                pullRequestFilesError = "无法读取 PR 文件变更：${diagnostic(error)}"
            } finally {
                remoteBusy = false
            }
        }
    }

    val info = infos.getValue(selectedVariant)
    Column(
        modifier = Modifier.fillMaxSize().padding(DesktopTokens.PagePadding),
        verticalArrangement = Arrangement.spacedBy(DesktopTokens.PageGap),
    ) {
        when (route) {
            CoreRoute.Overview -> Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(DesktopTokens.PageGap),
            ) {
                CoreOverviewPage(
                    selectedVariant = selectedVariant,
                    info = info,
                    infos = infos,
                    scriptDir = scriptDir,
                    operationBusy = operationBusy,
                    remoteBusy = remoteBusy,
                    state = state,
                    operationMessage = operationMessage,
                    onVariantSelected = { selectedVariant = it },
                    onOperation = ::request,
                    onHistory = ::loadHistory,
                    onPullRequests = ::loadPullRequests,
                )
            }
            CoreRoute.History -> CoreHistoryPage(
                variant = selectedVariant,
                history = history,
                error = historyError,
                busy = remoteBusy,
                onBack = { route = CoreRoute.Overview },
                onRefresh = ::loadHistory,
                onSelect = ::loadRevisionDetails,
            )
            CoreRoute.RevisionDetails -> CoreRevisionDetailsPage(
                details = revisionDetails,
                error = revisionDetailsError,
                busy = remoteBusy,
                onBack = { route = CoreRoute.History },
                onRollback = { revision ->
                    confirmation = CoreOperationRequest(
                        action = DesktopCoreActionLabel.Rollback,
                        variant = selectedVariant,
                        requiresRoute = true,
                        commitSha = revision.commitSha,
                        commitTitle = revision.title,
                    )
                },
            )
            CoreRoute.PullRequests -> CorePullRequestsPage(
                variant = selectedVariant,
                pulls = pullRequests,
                error = pullRequestsError,
                busy = remoteBusy,
                onBack = { route = CoreRoute.Overview },
                onRefresh = ::loadPullRequests,
                onSelect = ::loadPullRequestFiles,
            )
            CoreRoute.PullRequestDetails -> CorePullRequestDetailsPage(
                pull = selectedPullRequest,
                files = pullRequestFiles,
                error = pullRequestFilesError,
                busy = remoteBusy,
                onBack = { route = CoreRoute.PullRequests },
            )
        }
    }

    routeDialog?.let { request ->
        GithubRoutePickerDialog(
            title = "选择 GitHub 线路",
            description = "${request.action.label}前必须明确选择一次线路；不会静默切换到未选择的线路。",
            selectedId = selectedRoute,
            onSelected = { selectedRoute = it },
            onConfirm = {
                routeDialog = null
                controller.settings.setGithubProxy(selectedRoute)
                controller.settings.setGithubProxyConfirmed(true)
                routeConfirmed = true
                when (request) {
                    is CoreRouteRequest.Operation -> {
                        if (request.request.action == DesktopCoreActionLabel.Update) {
                            checkForUpdate(request.request, automatic = false)
                        } else {
                            executeCoreOperation(
                                request.request,
                                scriptDir,
                                paths,
                                controller,
                                selectedRoute,
                                state,
                                onBusy = { operationBusy = it },
                                onProgress = { installProgress = it },
                                onMessage = { installProgress = null; operationMessage = it; refreshNonce++ },
                                onRouteInvalid = {
                                    routeConfirmed = false
                                    controller.settings.setGithubProxyConfirmed(false)
                                    operationMessage = "已选 GitHub 线路超时或不可达，请重新选择线路。"
                                },
                                scope = operationScope,
                            )
                        }
                    }
                }
            },
            onDismiss = { routeDialog = null },
        )
    }

    confirmation?.let { request ->
        DesktopDialogFrame(
            spec = DesktopDialogSpec(
                title = "确认${request.action.label}",
                description = "此操作会修改本地核心文件${if (state.phase == ServicePhase.Running) "，并先停止当前服务" else ""}。",
                tone = if (request.action == DesktopCoreActionLabel.Delete) DesktopDialogTone.Danger else DesktopDialogTone.Warning,
                dismissOnClickOutside = false,
            ),
            onDismissRequest = { confirmation = null },
            leadingIcon = if (request.action == DesktopCoreActionLabel.Delete) DesktopIcons.Warning else DesktopIcons.Restart,
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("核心：${request.variant.label}")
                    request.commitSha?.let { Text("目标提交：${it.take(12)}", fontFamily = FontFamily.Monospace) }
                    Text(
                        when (request.action) {
                            DesktopCoreActionLabel.Reinstall -> "将重新下载并替换现有核心。"
                            DesktopCoreActionLabel.Delete -> "将删除本地核心目录，此操作不可撤销。"
                            DesktopCoreActionLabel.Rollback -> "将下载并切换到指定历史提交。"
                            else -> "将继续执行核心操作。"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            actions = {
                DesktopDialogButton(
                    action = DesktopDialogAction("取消"),
                    onClick = { confirmation = null },
                )
                DesktopDialogButton(
                    action = DesktopDialogAction(
                        label = "确认并继续",
                        tone = if (request.action == DesktopCoreActionLabel.Delete) DesktopDialogTone.Danger else DesktopDialogTone.Neutral,
                        isPrimary = true,
                    ),
                    onClick = {
                        confirmation = null
                        if (request.requiresRoute && !routeConfirmed) routeDialog = CoreRouteRequest.Operation(request)
                        else executeCoreOperation(
                            request,
                            scriptDir,
                            paths,
                            controller,
                            selectedRoute,
                            state,
                            onBusy = { operationBusy = it },
                            onProgress = { installProgress = it },
                            onMessage = { installProgress = null; operationMessage = it; refreshNonce++ },
                            onRouteInvalid = {
                                routeConfirmed = false
                                controller.settings.setGithubProxyConfirmed(false)
                                operationMessage = "已选 GitHub 线路超时或不可达，请重新选择线路。"
                            },
                            scope = operationScope,
                        )
                    },
                )
            },
        )
    }

    pendingUpdate?.let { update ->
        DesktopDialogFrame(
            spec = DesktopDialogSpec(
                title = "发现核心更新",
                description = update.variant.label,
                tone = DesktopDialogTone.Info,
                dismissOnClickOutside = false,
            ),
            onDismissRequest = { pendingUpdate = null },
            leadingIcon = DesktopIcons.Restart,
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DesktopInfoRow("当前提交", update.localSha.take(12), monospace = true)
                    DesktopDivider()
                    DesktopInfoRow("最新提交", update.remoteSha.take(12), monospace = true)
                    DesktopDivider()
                    Text(update.remote.title, fontWeight = FontWeight.SemiBold)
                    Text(update.remote.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("检查只读取远端信息，不会自动下载；只有点击“立即更新”才会替换本地核心。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            actions = {
                DesktopDialogButton(
                    action = DesktopDialogAction("稍后"),
                    onClick = { pendingUpdate = null },
                )
                DesktopDialogButton(
                    action = DesktopDialogAction("立即更新", isPrimary = true),
                    onClick = {
                        pendingUpdate = null
                        executeCoreOperation(
                            CoreOperationRequest(DesktopCoreActionLabel.Update, update.variant, true, update.remoteSha, update.remote.title),
                            scriptDir,
                            paths,
                            controller,
                            selectedRoute,
                            state,
                            onBusy = { operationBusy = it },
                            onProgress = { installProgress = it },
                            onMessage = { installProgress = null; operationMessage = it; refreshNonce++ },
                            onRouteInvalid = {
                                routeConfirmed = false
                                controller.settings.setGithubProxyConfirmed(false)
                                operationMessage = "已选 GitHub 线路超时或不可达，请重新选择线路。"
                            },
                            scope = operationScope,
                        )
                    },
                )
            },
        )
    }

    installProgress?.let { progress ->
        DesktopDialogFrame(
            spec = DesktopDialogSpec(
                title = "${progress.variant.label} · ${progress.stage.label}",
                description = progress.routeLabel?.let { "GitHub 线路：$it" },
                tone = DesktopDialogTone.Info,
                dismissOnClickOutside = false,
                dismissOnEscape = false,
            ),
            onDismissRequest = {},
            leadingIcon = DesktopIcons.Downloads,
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    progress.fraction?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                        ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    val downloaded = progress.downloadedBytes
                    val total = progress.totalBytes
                    if (downloaded != null) {
                        Text(
                            if (total != null) "${formatBytes(downloaded)} / ${formatBytes(total)}" else "已下载 ${formatBytes(downloaded)}",
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    progress.detail?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            },
            actions = {
                Spacer(Modifier.weight(1f))
                DesktopDialogButton(
                    action = DesktopDialogAction("后台等待", isPrimary = true),
                    onClick = {},
                )
            },
        )
    }
}

private fun formatBytes(value: Long): String = when {
    value >= 1024L * 1024L -> "%.1f MiB".format(value / (1024.0 * 1024.0))
    value >= 1024L -> "%.1f KiB".format(value / 1024.0)
    else -> "$value B"
}

@Composable
private fun CoreOverviewPage(
    selectedVariant: DesktopCoreVariant,
    info: DesktopCoreInfo,
    infos: Map<DesktopCoreVariant, DesktopCoreInfo>,
    scriptDir: File,
    operationBusy: Boolean,
    remoteBusy: Boolean,
    state: ServiceUiState,
    operationMessage: String?,
    onVariantSelected: (DesktopCoreVariant) -> Unit,
    onOperation: (CoreOperationRequest) -> Unit,
    onHistory: () -> Unit,
    onPullRequests: () -> Unit,
) {
    DesktopSurface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(modifier = Modifier.fillMaxWidth().padding(DesktopTokens.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("核心工作台", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "核心不随安装包内置。首次使用、更新、重装和回退都必须由你主动发起，并在开始前确认 GitHub 线路。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DesktopTokens.PageGap)) {
        DesktopCoreVariant.entries.forEach { variant ->
            CoreVariantCard(
                info = infos.getValue(variant),
                selected = selectedVariant == variant,
                onClick = { onVariantSelected(variant) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    DesktopSectionCard(
        title = "${selectedVariant.label} · 操作",
        supportingText = info.source?.let { "${it.repository} · ${it.branch}" } ?: "尚未安装",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!info.valid) {
                DesktopActionButton(
                    label = "选择线路并下载",
                    onClick = { onOperation(CoreOperationRequest(DesktopCoreActionLabel.Install, selectedVariant, true)) },
                    enabled = !operationBusy && !remoteBusy,
                    icon = DesktopIcons.Downloads,
                )
            } else {
                DesktopActionButton(
                    label = "检查更新",
                    onClick = { onOperation(CoreOperationRequest(DesktopCoreActionLabel.Update, selectedVariant, true)) },
                    enabled = !operationBusy && !remoteBusy,
                    style = DesktopActionButtonStyle.Tonal,
                    icon = DesktopIcons.Restart,
                )
                DesktopActionButton(
                    label = "重装核心",
                    onClick = { onOperation(CoreOperationRequest(DesktopCoreActionLabel.Reinstall, selectedVariant, true)) },
                    enabled = !operationBusy && !remoteBusy,
                    style = DesktopActionButtonStyle.Outlined,
                    icon = DesktopIcons.Restart,
                )
                DesktopActionButton(
                    label = "删除核心",
                    onClick = { onOperation(CoreOperationRequest(DesktopCoreActionLabel.Delete, selectedVariant, false)) },
                    enabled = !operationBusy && !remoteBusy,
                    style = DesktopActionButtonStyle.Destructive,
                    icon = DesktopIcons.Stop,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        if (info.valid) {
            DesktopInfoRow("当前版本", info.version ?: "未读取", monospace = true)
            DesktopDivider()
            DesktopInfoRow("来源仓库", info.source?.repository ?: "未知", monospace = true)
            DesktopDivider()
            DesktopInfoRow("提交 SHA", info.source?.commitSha ?: "未记录", monospace = true)
            DesktopDivider()
            DesktopInfoRow("核心目录", DesktopCoreInstaller.coreDir(scriptDir, selectedVariant).absolutePath, monospace = true)
        } else {
            DesktopEmptyState(
                title = "尚未准备 ${selectedVariant.label}",
                description = info.diagnostic ?: "点击上方按钮选择 GitHub 线路并手动下载。",
                icon = DesktopIcons.Downloads,
            )
        }
    }

    DesktopSectionCard(
        title = "版本与变更详情",
        supportingText = "进入独立页面查看提交历史、文件差异和 PR 实验室，不再把详情堆在概览页底部。",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DesktopActionButton(
                label = if (remoteBusy) "读取中…" else "提交历史",
                onClick = onHistory,
                enabled = !operationBusy && !remoteBusy,
                style = DesktopActionButtonStyle.Tonal,
                icon = DesktopIcons.Activity,
            )
            DesktopActionButton(
                label = "PR 实验室",
                onClick = onPullRequests,
                enabled = !operationBusy && !remoteBusy,
                style = DesktopActionButtonStyle.Outlined,
                icon = DesktopIcons.Tools,
            )
        }
    }

    operationMessage?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun CoreHistoryPage(
    variant: DesktopCoreVariant,
    history: List<CoreRevision>,
    error: String?,
    busy: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (CoreRevision) -> Unit,
) {
    CoreSubpageHeader(
        title = "${variant.label} · 提交历史",
        subtitle = "选择一个提交进入独立的文件变更详情页。",
        onBack = onBack,
        action = {
            DesktopActionButton(
                label = if (busy) "读取中…" else "刷新",
                onClick = onRefresh,
                enabled = !busy,
                style = DesktopActionButtonStyle.Outlined,
                icon = DesktopIcons.Restart,
            )
        },
    )
    DesktopSurface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(DesktopTokens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                history.isEmpty() && busy -> Text("正在读取提交历史…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                history.isEmpty() -> DesktopEmptyState(title = "没有可显示的提交", icon = DesktopIcons.Activity)
                else -> history.forEach { revision ->
                    DesktopSurface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = DesktopTokens.ItemShape,
                        onClick = { onSelect(revision) },
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(revision.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${revision.commitSha.take(7)} · ${revision.author ?: "未知作者"} · ${revision.committedAt ?: "时间未知"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoreRevisionDetailsPage(
    details: CoreRevisionDetails?,
    error: String?,
    busy: Boolean,
    onBack: () -> Unit,
    onRollback: (CoreRevision) -> Unit,
) {
    CoreSubpageHeader(
        title = "提交详情",
        subtitle = details?.revision?.commitSha?.take(12) ?: "读取文件变更",
        onBack = onBack,
        action = details?.let { revision ->
            {
                DesktopActionButton(
                    label = "回退到此提交",
                    onClick = { onRollback(revision.revision) },
                    style = DesktopActionButtonStyle.Outlined,
                    icon = DesktopIcons.Restart,
                )
            }
        },
    )
    DesktopSurface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(DesktopTokens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(DesktopTokens.PageGap),
        ) {
            when {
                error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                details == null && busy -> Text("正在读取文件变更…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                details == null -> DesktopEmptyState(title = "暂无提交详情", icon = DesktopIcons.Empty)
                else -> {
                    Text(details.revision.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${details.revision.commitSha} · ${details.revision.author ?: "未知作者"} · ${details.revision.committedAt ?: "时间未知"}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(details.revision.message, style = MaterialTheme.typography.bodyMedium)
                    DesktopRevisionStats(details)
                    details.files.forEach { change -> DesktopFileChangePanel(change) }
                }
            }
        }
    }
}

@Composable
private fun CorePullRequestsPage(
    variant: DesktopCoreVariant,
    pulls: List<CorePullRequest>,
    error: String?,
    busy: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (CorePullRequest) -> Unit,
) {
    CoreSubpageHeader(
        title = "${variant.label} · PR 实验室",
        subtitle = "选择一个 PR 进入独立详情页查看正文与文件变更。",
        onBack = onBack,
        action = {
            DesktopActionButton(
                label = if (busy) "读取中…" else "刷新",
                onClick = onRefresh,
                enabled = !busy,
                style = DesktopActionButtonStyle.Outlined,
                icon = DesktopIcons.Restart,
            )
        },
    )
    DesktopSurface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(DesktopTokens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                pulls.isEmpty() && busy -> Text("正在读取 PR…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                pulls.isEmpty() -> DesktopEmptyState(title = "当前没有开放的 PR", icon = DesktopIcons.Tools)
                else -> pulls.forEach { pull ->
                    DesktopSurface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = DesktopTokens.ItemShape,
                        onClick = { onSelect(pull) },
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("#${pull.number} ${pull.title}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${pull.baseBranch} ← ${pull.headBranch} · ${pull.author ?: "未知作者"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CorePullRequestDetailsPage(
    pull: CorePullRequest?,
    files: List<CoreRevisionFileChange>,
    error: String?,
    busy: Boolean,
    onBack: () -> Unit,
) {
    CoreSubpageHeader(
        title = pull?.let { "PR #${it.number}" } ?: "PR 详情",
        subtitle = pull?.title ?: "读取 PR 详情",
        onBack = onBack,
    )
    DesktopSurface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(DesktopTokens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(DesktopTokens.PageGap),
        ) {
            when {
                error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                pull == null && busy -> Text("正在读取 PR 文件变更…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                pull == null -> DesktopEmptyState(title = "暂无 PR 详情", icon = DesktopIcons.Empty)
                else -> {
                    Text(pull.body.ifBlank { "该 PR 没有正文。" }, style = MaterialTheme.typography.bodyMedium)
                    DesktopInfoRow("状态", pull.state)
                    DesktopDivider()
                    DesktopInfoRow("分支", "${pull.baseBranch} ← ${pull.headBranch}", monospace = true)
                    DesktopDivider()
                    DesktopInfoRow("作者", pull.author ?: "未知作者")
                    DesktopDivider()
                    Text("文件变更", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    files.forEach { change -> DesktopFileChangePanel(change) }
                }
            }
        }
    }
}

@Composable
private fun CoreSubpageHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    DesktopPageHeader(
        title = title,
        subtitle = subtitle,
        leadingContent = {
            DesktopIconButtonGlyph(
                icon = DesktopIcons.Back,
                onClick = onBack,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = "返回核心概览",
            )
        },
        actions = action?.let { { it() } },
    )
}

@Composable
private fun DesktopRevisionStats(details: CoreRevisionDetails) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DesktopMetricCard("文件", details.changedFiles.toString(), modifier = Modifier.weight(1f))
        DesktopMetricCard("新增", "+${details.additions}", modifier = Modifier.weight(1f), status = DesktopStatus.Success)
        DesktopMetricCard("删除", "-${details.deletions}", modifier = Modifier.weight(1f), status = DesktopStatus.Error)
    }
}

@Composable
private fun CoreVariantCard(
    info: DesktopCoreInfo,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DesktopSurface(
        modifier = modifier,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(DesktopTokens.CompactCardPadding), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(info.variant.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            DesktopStatusBadge(
                status = when {
                    info.valid -> DesktopStatus.Success
                    info.installed -> DesktopStatus.Warning
                    else -> DesktopStatus.Neutral
                },
                label = when {
                    info.valid -> "已安装"
                    info.installed -> "不完整"
                    else -> "未安装"
                },
                compact = true,
            )
            Text(info.version ?: "尚无版本", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

private enum class DesktopCoreActionLabel(val label: String) {
    Install("下载核心"),
    Update("更新核心"),
    Reinstall("重装核心"),
    Delete("删除核心"),
    Rollback("回退核心"),
}

private data class CoreOperationRequest(
    val action: DesktopCoreActionLabel,
    val variant: DesktopCoreVariant,
    val requiresRoute: Boolean,
    val commitSha: String? = null,
    val commitTitle: String? = null,
)

private sealed interface CoreRouteRequest {
    val action: DesktopCoreActionLabel

    data class Operation(val request: CoreOperationRequest) : CoreRouteRequest {
        override val action: DesktopCoreActionLabel get() = request.action
    }
}

private data class CoreOperationResult(
    val message: String,
    val restartService: Boolean,
)

private fun executeCoreOperation(
    request: CoreOperationRequest,
    scriptDir: File,
    paths: DesktopPaths,
    controller: DesktopRuntimeController,
    routeId: String,
    state: ServiceUiState,
    onBusy: (Boolean) -> Unit,
    onProgress: (CoreInstallProgress) -> Unit = {},
    onMessage: (String) -> Unit,
    onRouteInvalid: () -> Unit = {},
    scope: CoroutineScope,
) {
    onBusy(true)
    scope.launch {
        val wasRunning = state.phase == ServicePhase.Running
        try {
            val result = withContext(Dispatchers.IO) {
                val repository = request.variant.defaultRepository
                    ?: throw IllegalStateException("自定义核心尚未配置仓库，无法执行 ${request.action.label}")
                val branch = request.variant.defaultBranch
                val remoteHead = if (request.action == DesktopCoreActionLabel.Delete ||
                    request.action == DesktopCoreActionLabel.Rollback
                ) {
                    null
                } else {
                    GithubCoreRemote(
                        repository = repository,
                        proxyId = routeId,
                        token = controller.settings.githubToken,
                    ).branchHead(branch)
                }
                val targetCommitSha = request.commitSha ?: remoteHead?.commitSha
                val current = DesktopCoreInstaller.inspect(
                    scriptDir = scriptDir,
                    variant = request.variant,
                    repository = repository,
                    branch = branch,
                )
                if (request.action == DesktopCoreActionLabel.Update) {
                    val remote = remoteHead ?: throw IllegalStateException("更新检查未返回远端提交")
                    if (current.source?.commitSha == remote.commitSha) {
                        return@withContext CoreOperationResult(
                            message = "${request.variant.label} 已是最新（${remote.shortSha}）",
                            restartService = false,
                        )
                    }
                }
                if (wasRunning) {
                    controller.stop()
                    val deadline = System.currentTimeMillis() + 15_000
                    while (controller.state.value.phase != ServicePhase.Stopped && System.currentTimeMillis() < deadline) {
                        Thread.sleep(100)
                    }
                    if (controller.state.value.phase != ServicePhase.Stopped) {
                        throw IllegalStateException("服务未能在 15 秒内停止，已取消核心操作")
                    }
                }
                when (request.action) {
                    DesktopCoreActionLabel.Delete -> DesktopCoreInstaller.deleteCore(scriptDir, request.variant)
                    else -> DesktopCoreInstaller.installOrReplace(
                        scriptDir = scriptDir,
                        cacheDir = paths.coreCacheDir,
                        variant = request.variant,
                        repository = repository,
                        branch = branch,
                        githubProxyId = routeId,
                        commitSha = targetCommitSha,
                        onProgress = onProgress,
                    )
                }
                CoreOperationResult(
                    message = "${request.action.label}完成：${request.variant.label}" +
                        (targetCommitSha?.let { " · ${it.take(7)}" } ?: ""),
                    restartService = wasRunning && request.action != DesktopCoreActionLabel.Delete,
                )
            }
            onProgress(CoreInstallProgress(request.variant, CoreInstallStage.Completed, detail = result.message))
            onMessage(result.message)
            if (result.restartService) controller.start()
        } catch (error: Throwable) {
            if (error is com.example.danmuapiapp.desktop.node.GithubRouteFailureException) {
                onRouteInvalid()
            }
            onMessage("${request.action.label}失败：${diagnostic(error)}")
        } finally {
            onBusy(false)
        }
    }
}

@Composable
private fun DesktopFileChangePanel(change: CoreRevisionFileChange) {
    DesktopSurface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = DesktopTokens.ItemShape,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(change.path, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
            Text(
                "${change.status} · +${change.additions} -${change.deletions}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (change.lines.isEmpty()) {
                Text(
                    change.patchUnavailableReason ?: "没有可显示的文本 patch",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                change.lines.take(MAX_VISIBLE_DIFF_LINES).forEach { line ->
                    val lineColor = when (line.type) {
                        CoreDiffLineType.Added -> LocalDesktopThemePalette.current.success.content
                        CoreDiffLineType.Removed -> MaterialTheme.colorScheme.error
                        CoreDiffLineType.Header -> MaterialTheme.colorScheme.primary
                        CoreDiffLineType.Context -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        text = "%5s %5s  %s".format(
                            line.oldLineNumber?.toString().orEmpty(),
                            line.newLineNumber?.toString().orEmpty(),
                            line.content,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = lineColor,
                    )
                }
                if (change.lines.size > MAX_VISIBLE_DIFF_LINES) {
                    Text(
                        "此处显示前 $MAX_VISIBLE_DIFF_LINES 行，共 ${change.lines.size} 行",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private const val MAX_VISIBLE_DIFF_LINES = 120

private fun diagnostic(error: Throwable): String = error.message?.trim()?.takeIf { it.isNotEmpty() } ?: error::class.java.simpleName
