package com.example.danmuapiapp.ui.screen.core

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.domain.model.CoreDownloadProgress
import com.example.danmuapiapp.domain.model.CorePullRequest
import com.example.danmuapiapp.domain.model.CorePullRequestFilter
import com.example.danmuapiapp.domain.model.CorePullRequestFilePage
import com.example.danmuapiapp.domain.model.CorePullRequestInclusion
import com.example.danmuapiapp.domain.model.CorePullRequestStatus
import com.example.danmuapiapp.domain.model.PullRequestFirstContribution
import com.example.danmuapiapp.domain.model.canApplyToCurrentCore
import com.example.danmuapiapp.domain.model.effectiveStatus
import com.example.danmuapiapp.ui.component.AppDialog
import com.example.danmuapiapp.ui.component.AppDialogStyle
import com.example.danmuapiapp.ui.component.AppDialogTone
import com.example.danmuapiapp.ui.component.CoreDependencyRepairHost
import com.example.danmuapiapp.ui.component.AppModalPanel
import com.example.danmuapiapp.ui.component.SimpleMarkdownText
import com.example.danmuapiapp.ui.component.rememberSimpleMarkdownState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PullRequestLabScreen(
    onBack: () -> Unit,
    viewModel: PullRequestLabViewModel = hiltViewModel()
) {
    val coreInfos by viewModel.coreInfoList.collectAsStateWithLifecycle()
    val displayNames by viewModel.coreDisplayNames.collectAsStateWithLifecycle()
    val progress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val pendingRepair by viewModel.pendingDependencyRepair.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val installedPrs = coreInfos.firstOrNull { it.variant == viewModel.variant && it.isReady }
        ?.pullRequestNumbers.orEmpty()

    LaunchedEffect(viewModel.statusMessage) {
        viewModel.statusMessage?.let { message ->
            snackbar.showSnackbar(message)
            viewModel.consumeStatusMessage()
        }
    }
    LaunchedEffect(installedPrs) {
        viewModel.syncLocallyMergedPullRequests(installedPrs)
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
                .padding(horizontal = 18.dp)
        ) {
            PullRequestLabHeader(
                title = "PR 实验室",
                subtitle = viewModel.pageData?.let { "${it.repository} · ${it.baseBranch}" }
                    ?: displayNames.resolve(viewModel.variant),
                isLoading = viewModel.isLoading,
                canRefresh = !viewModel.isBuilding && !viewModel.isActivating,
                searchActive = viewModel.isSearchVisible,
                onBack = onBack,
                onSearchToggle = viewModel::toggleSearch,
                onRefresh = viewModel::refresh
            )

            if (viewModel.isSearchVisible) {
                PullRequestSearchBar(
                    query = viewModel.searchQuery,
                    filter = viewModel.selectedFilter,
                    isLoading = viewModel.isLoading,
                    enabled = !viewModel.isBuilding && !viewModel.isActivating,
                    onQueryChange = viewModel::updateSearchQuery,
                    onSearch = viewModel::submitSearch,
                    onClear = viewModel::clearSearch
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 22.dp)
            ) {
                if (installedPrs.isNotEmpty()) {
                    item(key = "installed-stack") {
                        InstalledPullRequestStack(numbers = installedPrs)
                    }
                }

                if (viewModel.selectedPullRequests.isNotEmpty()) {
                    item(key = "selected-title") {
                        SectionTitle(
                            title = "待合并队列",
                            subtitle = "${viewModel.selectedPullRequests.size} 个 PR · 按当前顺序执行"
                        )
                    }
                    items(
                        items = viewModel.selectedPullRequests,
                        key = { "selected-${it.number}" }
                    ) { pullRequest ->
                        val index = viewModel.selectedPullRequests.indexOfFirst {
                            it.number == pullRequest.number
                        }
                        SelectedPullRequestRow(
                            pullRequest = pullRequest,
                            position = index + 1,
                            canMoveUp = index > 0,
                            canMoveDown = index in 0 until viewModel.selectedPullRequests.lastIndex,
                            enabled = !viewModel.isBuilding && !viewModel.isActivating,
                            onMoveUp = { viewModel.moveSelection(index, -1) },
                            onMoveDown = { viewModel.moveSelection(index, 1) },
                            onRemove = { viewModel.toggleSelection(pullRequest) },
                            onOpenDetails = { viewModel.openPullRequestDetails(pullRequest) }
                        )
                    }
                    item(key = "selected-divider") {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        )
                    }
                }

                if (viewModel.isBuilding || viewModel.isActivating) {
                    item(key = "progress") {
                        PullRequestBuildProgress(
                            progress = progress,
                            isActivating = viewModel.isActivating,
                            onCancel = viewModel::cancelBuild,
                            canCancel = viewModel.isBuilding && !viewModel.isActivating
                        )
                    }
                }

                viewModel.errorMessage?.let { error ->
                    item(key = "error") {
                        PullRequestErrorPanel(
                            message = error,
                            conflictNumber = viewModel.conflictPullRequestNumber,
                            conflictFiles = viewModel.conflictFiles,
                            onDismiss = viewModel::dismissError
                        )
                    }
                }

                if (viewModel.pageData?.isPrivateRepository == true) {
                    item(key = "private-repo") {
                        PrivateRepositoryNotice()
                    }
                }

                item(key = "pr-filter") {
                    PullRequestFilterSelector(
                        selected = viewModel.selectedFilter,
                        enabled = !viewModel.isBuilding && !viewModel.isActivating,
                        onSelected = viewModel::selectFilter
                    )
                }

                item(key = "available-title") {
                    SectionTitle(
                        title = viewModel.selectedFilter.sectionTitle,
                        subtitle = viewModel.pageData?.let {
                            val pageLabel = "第 ${it.page} 页 · 每页 15 条"
                            if (viewModel.appliedSearchQuery.isBlank()) pageLabel
                            else "${viewModel.appliedSearchQuery} · $pageLabel"
                        }
                            ?: "正在读取仓库"
                    )
                }

                when {
                    viewModel.isLoading && viewModel.pageData == null -> {
                        item(key = "initial-loading") {
                            LoadingPanel(
                                if (viewModel.appliedSearchQuery.isBlank()) {
                                    "正在读取 PR 列表"
                                } else {
                                    viewModel.selectedFilter.searchLoadingMessage
                                }
                            )
                        }
                    }
                    viewModel.pageData?.items.isNullOrEmpty() && viewModel.errorMessage == null -> {
                        item(key = "empty") {
                            EmptyPullRequestPanel(
                                filter = viewModel.selectedFilter,
                                isSearching = viewModel.appliedSearchQuery.isNotBlank()
                            )
                        }
                    }
                    else -> {
                        items(
                            items = viewModel.pageData?.items.orEmpty(),
                            key = { "pr-${it.number}" }
                        ) { pullRequest ->
                            val alreadyIncluded = pullRequest.number in installedPrs
                            val selectedIndex = if (alreadyIncluded) {
                                -1
                            } else {
                                viewModel.selectedPullRequests.indexOfFirst {
                                    it.number == pullRequest.number
                                }
                            }
                            PullRequestCard(
                                pullRequest = pullRequest,
                                selectedPosition = selectedIndex.takeIf { it >= 0 }?.plus(1),
                                alreadyIncluded = alreadyIncluded,
                                showOpenState = viewModel.selectedFilter == CorePullRequestFilter.All,
                                enabled = !viewModel.isBuilding && !viewModel.isActivating,
                                enrichmentKey = viewModel.listContentGeneration,
                                enrichmentEnabled = !viewModel.isLoading,
                                onVisible = { viewModel.onPullRequestVisible(pullRequest) },
                                onToggle = { viewModel.toggleSelection(pullRequest) },
                                onOpenDetails = { viewModel.openPullRequestDetails(pullRequest) }
                            )
                        }
                    }
                }

                viewModel.pageData?.let { page ->
                    item(key = "pagination") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.loadPage(page.page - 1) },
                                enabled = page.hasPreviousPage && !viewModel.isLoading,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("上一页")
                            }
                            FilledTonalButton(
                                onClick = { viewModel.loadPage(page.page + 1) },
                                enabled = page.hasNextPage && !viewModel.isLoading,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("下一页")
                            }
                        }
                    }
                }

                item(key = "build-action") {
                    Button(
                        onClick = viewModel::requestBuild,
                        enabled = viewModel.selectedPullRequests.isNotEmpty() &&
                            !viewModel.isBuilding &&
                            !viewModel.isActivating &&
                            viewModel.pageData?.isPrivateRepository != true,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.CallMerge, null, Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (viewModel.selectedPullRequests.isEmpty()) {
                                "选择要合并的 PR"
                            } else {
                                "合并 ${viewModel.selectedPullRequests.size} 个 PR"
                            }
                        )
                    }
                }
            }
        }
    }

    viewModel.openedPullRequest?.let { pullRequest ->
        PullRequestDetailsPanel(
            pullRequest = pullRequest,
            locallyMerged = pullRequest.number in installedPrs,
            filePage = viewModel.pullRequestFilePage,
            isLoading = viewModel.isLoadingPullRequestDetails,
            error = viewModel.pullRequestDetailsError,
            onPreviousPage = {
                viewModel.loadPullRequestFilePage((viewModel.pullRequestFilePage?.page ?: 1) - 1)
            },
            onNextPage = {
                viewModel.loadPullRequestFilePage((viewModel.pullRequestFilePage?.page ?: 1) + 1)
            },
            onClose = viewModel::closePullRequestDetails
        )
    }

    if (viewModel.showBuildConfirmation) {
        BuildPullRequestStackDialog(
            pullRequests = viewModel.selectedPullRequests,
            activateAfterInstall = viewModel.activateAfterInstall,
            onActivateChange = viewModel::updateActivateAfterInstall,
            onConfirm = viewModel::confirmBuild,
            onDismiss = viewModel::dismissBuildConfirmation
        )
    }

    CoreDependencyRepairHost(
        request = pendingRepair?.takeIf { it.variant == viewModel.variant },
        showRequiredPrompt = viewModel.showDependencyRequiredPrompt,
        showRepairDialog = viewModel.showDependencyRepairDialog,
        onOpenRepair = viewModel::openDependencyRepairDialog,
        onDismissRequiredPrompt = viewModel::dismissDependencyRequiredPrompt,
        onOnlineRepair = viewModel::repairPendingDependenciesOnline,
        onRepairFromArchive = viewModel::repairPendingDependenciesFromArchive,
        onCancelMutation = viewModel::discardPendingCoreMutation,
        onDismissRepairDialog = viewModel::dismissDependencyRepairDialog
    )
}

@Composable
private fun PullRequestLabHeader(
    title: String,
    subtitle: String,
    isLoading: Boolean,
    canRefresh: Boolean,
    searchActive: Boolean,
    onBack: () -> Unit,
    onSearchToggle: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalIconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalIconButton(
                onClick = onSearchToggle,
                enabled = canRefresh,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    if (searchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                    if (searchActive) "关闭搜索" else "搜索 PR",
                    Modifier.size(18.dp)
                )
            }
            FilledTonalIconButton(
                onClick = onRefresh,
                enabled = canRefresh && !isLoading,
                modifier = Modifier.size(36.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Refresh, "刷新", Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun PullRequestSearchBar(
    query: String,
    filter: CorePullRequestFilter,
    isLoading: Boolean,
    enabled: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        enabled = enabled,
        singleLine = true,
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotBlank()) {
                    IconButton(onClick = onClear, enabled = !isLoading) {
                        Icon(Icons.Rounded.Close, "清空搜索")
                    }
                }
                IconButton(onClick = onSearch, enabled = !isLoading && query.isNotBlank()) {
                    Icon(Icons.Rounded.Search, "执行 GitHub PR 搜索")
                }
            }
        },
        placeholder = { Text(filter.searchPlaceholder) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onSearch() }),
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PullRequestFilterSelector(
    selected: CorePullRequestFilter,
    enabled: Boolean,
    onSelected: (CorePullRequestFilter) -> Unit
) {
    val filters = CorePullRequestFilter.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        filters.forEachIndexed { index, filter ->
            SegmentedButton(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = filters.size),
                label = {
                    Text(
                        text = filter.shortLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
            )
        }
    }
}

private val CorePullRequestFilter.shortLabel: String
    get() = when (this) {
        CorePullRequestFilter.Open -> "开放"
        CorePullRequestFilter.Merged -> "已合并"
        CorePullRequestFilter.Closed -> "已关闭"
        CorePullRequestFilter.All -> "全部"
    }

private val CorePullRequestFilter.sectionTitle: String
    get() = when (this) {
        CorePullRequestFilter.Open -> "开放的 PR"
        CorePullRequestFilter.Merged -> "已合并的 PR"
        CorePullRequestFilter.Closed -> "已关闭的 PR"
        CorePullRequestFilter.All -> "全部 PR"
    }

private val CorePullRequestFilter.emptyMessage: String
    get() = when (this) {
        CorePullRequestFilter.Open -> "没有开放的 PR"
        CorePullRequestFilter.Merged -> "没有已合并的 PR"
        CorePullRequestFilter.Closed -> "没有未合并而关闭的 PR"
        CorePullRequestFilter.All -> "仓库中还没有 PR"
    }

private val CorePullRequestFilter.searchPlaceholder: String
    get() = when (this) {
        CorePullRequestFilter.Open -> "搜索开放的 PR、作者或 #编号"
        CorePullRequestFilter.Merged -> "搜索已合并的 PR、作者或 #编号"
        CorePullRequestFilter.Closed -> "搜索已关闭的 PR、作者或 #编号"
        CorePullRequestFilter.All -> "搜索全部 PR、作者或 #编号"
    }

private val CorePullRequestFilter.searchLoadingMessage: String
    get() = when (this) {
        CorePullRequestFilter.Open -> "正在搜索开放的 PR"
        CorePullRequestFilter.Merged -> "正在搜索已合并的 PR"
        CorePullRequestFilter.Closed -> "正在搜索已关闭的 PR"
        CorePullRequestFilter.All -> "正在搜索全部 PR"
    }

@Composable
private fun InstalledPullRequestStack(numbers: List<Int>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("当前已安装 PR 组合", style = MaterialTheme.typography.titleSmall)
                Text(
                    numbers.joinToString(" + ") { "#$it" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SelectedPullRequestRow(
    pullRequest: CorePullRequest,
    position: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    enabled: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onOpenDetails: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    position.toString(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f).clickable(onClick = onOpenDetails)
            ) {
                Text(
                    "#${pullRequest.number} ${pullRequest.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    pullRequest.headSha.take(7),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onMoveUp, enabled = enabled && canMoveUp) {
                Icon(Icons.Rounded.ArrowUpward, "上移 PR #${pullRequest.number}", Modifier.size(19.dp))
            }
            IconButton(onClick = onMoveDown, enabled = enabled && canMoveDown) {
                Icon(Icons.Rounded.ArrowDownward, "下移 PR #${pullRequest.number}", Modifier.size(19.dp))
            }
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(Icons.Rounded.Close, "移除 PR #${pullRequest.number}", Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun PullRequestCard(
    pullRequest: CorePullRequest,
    selectedPosition: Int?,
    alreadyIncluded: Boolean,
    showOpenState: Boolean,
    enabled: Boolean,
    enrichmentKey: Long,
    enrichmentEnabled: Boolean,
    onVisible: () -> Unit,
    onToggle: () -> Unit,
    onOpenDetails: () -> Unit
) {
    LaunchedEffect(enrichmentKey, enrichmentEnabled, pullRequest.number) {
        if (enrichmentEnabled) onVisible()
    }
    val selected = selectedPosition != null
    val locallyIncluded = alreadyIncluded ||
        pullRequest.currentCoreInclusion == CorePullRequestInclusion.LocalMerge
    val effectiveStatus = pullRequest.effectiveStatus(locallyIncluded)
    val selectable = pullRequest.canApplyToCurrentCore(alreadyIncluded)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetails),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                enabled = enabled && selectable
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "#${pullRequest.number}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        when {
                            locallyIncluded -> StatusBadge("本地已并入", StatusBadgeTone.Merged)
                            effectiveStatus == CorePullRequestStatus.Merged -> {
                                when (pullRequest.currentCoreInclusion) {
                                    CorePullRequestInclusion.Included -> {
                                        StatusBadge("当前版本已包含", StatusBadgeTone.Merged)
                                    }
                                    CorePullRequestInclusion.NotIncluded -> {
                                        StatusBadge("已合并 · 未包含", StatusBadgeTone.Warning)
                                    }
                                    else -> StatusBadge("已合并 · 待确认", StatusBadgeTone.Merged)
                                }
                            }
                            effectiveStatus == CorePullRequestStatus.Closed -> {
                                StatusBadge("已关闭", StatusBadgeTone.Closed)
                            }
                            pullRequest.draft -> StatusBadge("草稿", StatusBadgeTone.Warning)
                            showOpenState -> StatusBadge("开放", StatusBadgeTone.Open)
                        }
                        selectedPosition?.let { StatusBadge("队列 $it", StatusBadgeTone.Open) }
                        Icon(
                            Icons.Rounded.ChevronRight,
                            "查看 PR #${pullRequest.number} 变更详情",
                            Modifier.size(19.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    pullRequest.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                pullRequest.firstContribution?.let { firstContribution ->
                    FirstContributorBadge(firstContribution)
                }
                pullRequest.body.lineSequence().firstOrNull { it.isNotBlank() }?.let { summary ->
                    Text(
                        summary.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "${pullRequest.author} · ${formatPullRequestTime(pullRequest.updatedAt)} · ${pullRequest.headSha.take(7)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FirstContributorBadge(firstContribution: PullRequestFirstContribution) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                Icons.Rounded.PersonAdd,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                firstContribution.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

private val PullRequestFirstContribution.label: String
    get() = when (this) {
        PullRequestFirstContribution.Github -> "首次在 GitHub 贡献"
        PullRequestFirstContribution.Repository -> "首次向此仓库贡献"
    }

private enum class StatusBadgeTone {
    Open,
    Merged,
    Closed,
    Warning
}

@Composable
private fun StatusBadge(text: String, tone: StatusBadgeTone) {
    val backgroundColor = when (tone) {
        StatusBadgeTone.Open -> MaterialTheme.colorScheme.primaryContainer
        StatusBadgeTone.Merged -> MaterialTheme.colorScheme.tertiaryContainer
        StatusBadgeTone.Closed -> MaterialTheme.colorScheme.errorContainer
        StatusBadgeTone.Warning -> MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = when (tone) {
        StatusBadgeTone.Open -> MaterialTheme.colorScheme.onPrimaryContainer
        StatusBadgeTone.Merged -> MaterialTheme.colorScheme.onTertiaryContainer
        StatusBadgeTone.Closed -> MaterialTheme.colorScheme.onErrorContainer
        StatusBadgeTone.Warning -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = backgroundColor
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}

@Composable
private fun PullRequestBuildProgress(
    progress: CoreDownloadProgress,
    isActivating: Boolean,
    onCancel: () -> Unit,
    canCancel: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.2.dp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (isActivating) "正在切换并验证核心" else "正在构建 PR 组合",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        if (isActivating) "等待服务返回启动结果" else progress.stageText.ifBlank { "准备本地 Git 工作区" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canCancel) {
                    TextButton(onClick = onCancel) { Text("取消") }
                }
            }
            progress.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun PullRequestErrorPanel(
    message: String,
    conflictNumber: Int?,
    conflictFiles: List<String>,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
    ) {
        Row(modifier = Modifier.padding(start = 13.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    conflictNumber?.let { "PR #$it 合并冲突" } ?: "操作未完成",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                if (conflictFiles.isNotEmpty()) {
                    Text(
                        conflictFiles.take(8).joinToString("\n") { "• $it" } +
                            if (conflictFiles.size > 8) "\n• 其余 ${conflictFiles.size - 8} 个文件" else "",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, "关闭错误提示", Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PrivateRepositoryNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("私有仓库不可本地克隆", style = MaterialTheme.typography.titleSmall)
                Text(
                    "PR 元数据仍可查看；为避免把 Token 发送到 api.github.com 以外的地址，组合构建已禁用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
private fun LoadingPanel(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.2.dp)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EmptyPullRequestPanel(filter: CorePullRequestFilter, isSearching: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.AutoMirrored.Rounded.CallMerge, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (isSearching) "没有找到匹配的 ${filter.shortLabel} PR" else filter.emptyMessage,
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

@Composable
private fun PullRequestDetailsPanel(
    pullRequest: CorePullRequest,
    locallyMerged: Boolean,
    filePage: CorePullRequestFilePage?,
    isLoading: Boolean,
    error: String?,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onClose: () -> Unit
) {
    val expandedFiles = remember(
        pullRequest.baseRepository,
        pullRequest.number
    ) {
        mutableStateMapOf<String, Boolean>()
    }
    val retainedMarkdownState = rememberSimpleMarkdownState(pullRequest.body)
    AppModalPanel(
        onDismissRequest = onClose,
        maxWidth = 980.dp,
        expanded = true,
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回 PR 列表")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "PR #${pullRequest.number}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${pullRequest.baseRef} <- ${pullRequest.headLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Close, "关闭")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "pr-summary", contentType = "pr-summary") {
                    PullRequestDetailsSummary(
                        pullRequest = pullRequest,
                        locallyMerged = locallyMerged
                    )
                }
                if (pullRequest.body.isNotBlank()) {
                    item(key = "pr-description", contentType = "pr-description") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    "变更说明",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                SimpleMarkdownText(
                                    markdown = pullRequest.body,
                                    modifier = Modifier.fillMaxWidth(),
                                    retainedMarkdownState = retainedMarkdownState
                                )
                            }
                        }
                    }
                }
                item(key = "pr-metrics", contentType = "pr-metrics") {
                    PullRequestDiffMetrics(pullRequest = pullRequest, filePage = filePage)
                }
                item(key = "files-title", contentType = "diff-heading") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "变更文件",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        filePage?.let {
                            Text(
                                "第 ${it.page} 页 · 每页 15 个",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (isLoading) {
                    item(key = "details-progress") {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                if (error != null) {
                    item(key = "details-error") {
                        Text(
                            error,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                items(
                    items = filePage?.files.orEmpty(),
                    key = { "${it.status}:${it.path}" },
                    contentType = { "core-diff-file" }
                ) { file ->
                    CoreFileDiff(
                        file = file,
                        expanded = expandedFiles[file.path] == true,
                        onExpandedChange = { expanded ->
                            if (expanded) {
                                expandedFiles[file.path] = true
                            } else {
                                expandedFiles.remove(file.path)
                            }
                        }
                    )
                }
                if (!isLoading && error == null && filePage?.files.isNullOrEmpty()) {
                    item(key = "no-files") {
                        Text(
                            "没有可显示的文件变更",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                filePage?.let { page ->
                    item(key = "file-pagination") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onPreviousPage,
                                enabled = page.hasPreviousPage && !isLoading,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.NavigateBefore, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("上一页")
                            }
                            FilledTonalButton(
                                onClick = onNextPage,
                                enabled = page.hasNextPage && !isLoading,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("下一页")
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Rounded.NavigateNext, null, Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PullRequestDetailsSummary(
    pullRequest: CorePullRequest,
    locallyMerged: Boolean
) {
    val locallyIncluded = locallyMerged ||
        pullRequest.currentCoreInclusion == CorePullRequestInclusion.LocalMerge
    val effectiveStatus = pullRequest.effectiveStatus(locallyIncluded)
    val statusLabel = when {
        locallyIncluded -> "本地已并入"
        effectiveStatus == CorePullRequestStatus.Merged -> when (pullRequest.currentCoreInclusion) {
            CorePullRequestInclusion.Included -> "GitHub 已合并 · 当前核心已包含"
            CorePullRequestInclusion.NotIncluded -> "GitHub 已合并 · 当前核心未包含"
            CorePullRequestInclusion.LocalMerge -> "本地已并入"
            CorePullRequestInclusion.Unknown -> "GitHub 已合并 · 本地状态待确认"
        }
        effectiveStatus == CorePullRequestStatus.Closed -> "已关闭"
        pullRequest.draft -> "草稿"
        else -> "开放"
    }
    val statusColor = when (effectiveStatus) {
        CorePullRequestStatus.Open -> if (pullRequest.draft) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.primary
        }
        CorePullRequestStatus.Merged -> MaterialTheme.colorScheme.tertiary
        CorePullRequestStatus.Closed -> MaterialTheme.colorScheme.error
    }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                statusLabel,
                style = MaterialTheme.typography.labelLarge,
                color = statusColor
            )
            Text(
                pullRequest.headSha.take(7),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            pullRequest.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "${pullRequest.author} · ${formatPullRequestTime(pullRequest.updatedAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        pullRequest.firstContribution?.let { firstContribution ->
            FirstContributorBadge(firstContribution)
        }
    }
}

@Composable
private fun PullRequestDiffMetrics(
    pullRequest: CorePullRequest,
    filePage: CorePullRequestFilePage?
) {
    val visibleAdditions = pullRequest.additions ?: filePage?.files?.sumOf { it.additions } ?: 0
    val visibleDeletions = pullRequest.deletions ?: filePage?.files?.sumOf { it.deletions } ?: 0
    val visibleFiles = pullRequest.changedFiles ?: filePage?.files?.size ?: 0
    val hasRepositoryTotals = pullRequest.changedFiles != null &&
        pullRequest.additions != null &&
        pullRequest.deletions != null
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DiffMetric(
            Icons.Rounded.Code,
            if (hasRepositoryTotals) "$visibleFiles 个文件" else "本页 $visibleFiles 个文件",
            MaterialTheme.colorScheme.primary
        )
        DiffMetric(Icons.Rounded.Add, "+$visibleAdditions", diffAddedColor())
        DiffMetric(Icons.Rounded.DeleteOutline, "-$visibleDeletions", MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun BuildPullRequestStackDialog(
    pullRequests: List<CorePullRequest>,
    activateAfterInstall: Boolean,
    onActivateChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val firstContributorPullRequests = pullRequests.filter { it.firstContribution != null }
    AppDialog(
        onDismissRequest = onDismiss,
        style = AppDialogStyle.Confirm,
        tone = AppDialogTone.Warning,
        icon = { Icon(Icons.Rounded.Warning, null) },
        title = { Text("构建本地 PR 组合") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 390.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("PR 中的代码将在本机执行。构建只读取 GitHub，不会修改或合并远程仓库。")
                if (firstContributorPullRequests.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                "包含首次贡献者的 PR",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                firstContributorPullRequests.joinToString("\n") { pullRequest ->
                                    "#${pullRequest.number} @${pullRequest.author} · " +
                                        requireNotNull(pullRequest.firstContribution).label
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        pullRequests.forEachIndexed { index, pullRequest ->
                            Text(
                                "${index + 1}. #${pullRequest.number} ${pullRequest.title}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("安装后切换", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "服务运行时会安全重启并保留恢复点",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = activateAfterInstall, onCheckedChange = onActivateChange)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("开始构建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun formatPullRequestTime(raw: String): String {
    if (raw.isBlank()) return "时间未知"
    return runCatching {
        val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.getDefault())
        Instant.parse(raw).atZone(ZoneId.systemDefault()).format(formatter)
    }.getOrDefault(raw.take(10))
}
