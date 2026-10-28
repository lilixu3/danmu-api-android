package com.example.danmuapiapp.ui.screen.core

import com.example.danmuapiapp.ui.component.AppToast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.domain.model.CorePullRequestFilter

@Composable
fun PullRequestLabScreen(
    onBack: () -> Unit,
    viewModel: PullRequestLabViewModel = hiltViewModel()
) {
    val coreInfos by viewModel.coreInfoList.collectAsStateWithLifecycle()
    val displayNames by viewModel.coreDisplayNames.collectAsStateWithLifecycle()
    val progress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val installedPrs = coreInfos.firstOrNull { it.variant == viewModel.variant && it.isReady }
        ?.pullRequestNumbers.orEmpty()

    LaunchedEffect(viewModel.statusMessage) {
        viewModel.statusMessage?.let { message ->
            AppToast.show(message)
            viewModel.consumeStatusMessage()
        }
    }
    LaunchedEffect(installedPrs) {
        viewModel.syncLocallyMergedPullRequests(installedPrs)
    }

    Scaffold(
        bottomBar = {
            PullRequestBuildBar(
                selectedPullRequests = viewModel.selectedPullRequests,
                isBuilding = viewModel.isBuilding,
                isActivating = viewModel.isActivating,
                isPrivateRepository = viewModel.pageData?.isPrivateRepository == true,
                onBuild = viewModel::requestBuild
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 14.dp)
            ) {
                item(key = "workspace-summary") {
                    PullRequestWorkspaceSummary(
                        installedNumbers = installedPrs,
                        selectedNumbers = viewModel.selectedPullRequests.map { it.number }
                    )
                }

                if (viewModel.selectedPullRequests.isNotEmpty()) {
                    item(key = "selected-queue") {
                        SelectedPullRequestQueue(
                            pullRequests = viewModel.selectedPullRequests,
                            enabled = !viewModel.isBuilding && !viewModel.isActivating,
                            onMove = viewModel::moveSelection,
                            onRemove = viewModel::toggleSelection,
                            onOpenDetails = viewModel::openPullRequestDetails
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
                            val pageLabel = "${it.items.size} 条结果 · 第 ${it.page} 页"
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
                        PullRequestPagination(
                            page = page.page,
                            canGoPrevious = page.hasPreviousPage && !viewModel.isLoading,
                            canGoNext = page.hasNextPage && !viewModel.isLoading,
                            onPrevious = { viewModel.loadPage(page.page - 1) },
                            onNext = { viewModel.loadPage(page.page + 1) }
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
