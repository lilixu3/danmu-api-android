package com.example.danmuapiapp.ui.screen.home

import com.example.danmuapiapp.ui.component.CorePullRequestUpdateChoiceHost
import com.example.danmuapiapp.ui.screen.core.CoreUpdateDetailsPanel
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.danmuapiapp.ui.component.GithubProxyPickerDialog
import com.example.danmuapiapp.ui.component.CoreDependencyRepairHost
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.model.CoreInfo
import com.example.danmuapiapp.domain.model.CoreVariantDisplayNames
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun HomeNoCoreDialogHost(
    viewModel: HomeViewModel,
    state: RuntimeState,
    currentVariantLabel: String,
    coreDisplayNames: CoreVariantDisplayNames,
    customRepo: String,
    customRepoBranch: String,
    onOpenCoreManagement: () -> Unit,
) {
    if (viewModel.showNoCoreDialog) {
        NoCoreDialog(
            currentVariant = state.variant,
            currentVariantLabel = currentVariantLabel,
            coreDisplayNames = coreDisplayNames,
            customRepo = customRepo,
            customRepoBranch = customRepoBranch,
            onDismiss = viewModel::dismissNoCoreDialog,
            onInstall = viewModel::installAndStart,
            onOpenCoreManagement = onOpenCoreManagement
        )
    }
}

@Composable
internal fun HomeUnavailableVariantDialogHost(
    viewModel: HomeViewModel,
    coreDisplayNames: CoreVariantDisplayNames,
    customRepo: String,
    onOpenCoreManagement: () -> Unit,
) {
    viewModel.unavailableVariant?.let { unavailable ->
        UnavailableVariantDialog(
            variant = unavailable,
            variantLabel = coreDisplayNames.resolve(unavailable),
            customRepoConfigured = customRepo.isNotBlank(),
            onDismiss = viewModel::dismissUnavailableVariantDialog,
            onInstall = viewModel::installUnavailableVariant,
            onOpenCoreManagement = {
                viewModel.consumeUnavailableVariantForSettings()
                onOpenCoreManagement()
            }
        )
    }
}

@Composable
internal fun HomeCoreDependencyHosts(viewModel: HomeViewModel) {
    val pendingDependencyRepair by viewModel.pendingDependencyRepair.collectAsStateWithLifecycle()
    CorePullRequestUpdateChoiceHost(viewModel.coreUpdateChoice)
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
}

@Composable
internal fun HomeVariantPickerDialogHost(
    viewModel: HomeViewModel,
    state: RuntimeState,
    coreList: List<CoreInfo>,
    isCoreInfoLoading: Boolean,
    coreDisplayNames: CoreVariantDisplayNames,
    customRepo: String,
    customRepoBranch: String,
    isBusy: Boolean,
) {
    if (viewModel.showVariantPicker) {
        VariantPickerDialog(
            currentVariant = state.variant,
            coreList = coreList,
            isCoreInfoLoading = isCoreInfoLoading,
            coreDisplayNames = coreDisplayNames,
            customRepo = customRepo,
            customRepoBranch = customRepoBranch,
            isBusy = isBusy,
            onSelect = viewModel::switchVariant,
            onDismiss = viewModel::dismissVariantPicker
        )
    }
}

@Composable
internal fun HomeGithubProxyPickerDialogHost(viewModel: HomeViewModel) {
    if (viewModel.showProxyPickerDialog) {
        GithubProxyPickerDialog(
            title = "选择 GitHub 线路",
            subtitle = "首次下载核心前请先测速并选择线路",
            options = viewModel.proxyOptions,
            selectedId = viewModel.proxySelectedId,
            testingIds = viewModel.proxyTestingIds,
            resultMap = viewModel.proxyLatencyMap,
            onSelect = viewModel::selectProxy,
            onRetest = viewModel::retestProxySpeed,
            onConfirm = viewModel::confirmProxySelection,
            onDismiss = viewModel::dismissProxyPickerDialog,
            confirmText = "保存并继续"
        )
    }
}

@Composable
internal fun HomeUpdatePromptDialogHost(
    viewModel: HomeViewModel,
    coreList: List<CoreInfo>,
    coreDisplayNames: CoreVariantDisplayNames,
) {
    if (viewModel.showUpdatePromptDialog) {
        UpdatePromptDialog(
            variantLabel = viewModel.updatePromptVariant?.let { coreDisplayNames.resolve(it) },
            currentVersion = viewModel.updatePromptCurrentVersion,
            latestVersion = viewModel.updatePromptLatestVersion,
            remoteCommit = viewModel.updatePromptVariant?.let { variant ->
                coreList.firstOrNull { it.variant == variant }?.remoteCommit
            },
            pullRequestNumbers = coreList.firstOrNull { it.variant == viewModel.updatePromptVariant }?.pullRequestNumbers.orEmpty(),
            updatedPullRequestNumbers = coreList.firstOrNull { it.variant == viewModel.updatePromptVariant }?.updatedPullRequestHeads?.keys?.toList().orEmpty(),
            sourceMismatch = viewModel.updatePromptSourceMismatch,
            sourceUnknownLegacy = viewModel.updatePromptSourceUnknownLegacy,
            desiredSource = viewModel.updatePromptDesiredSource,
            onShowDetails = viewModel::openUpdateDetailsFromPrompt,
            onUpdate = viewModel::updateFromPrompt,
            onDismiss = viewModel::dismissCurrentUpdatePrompt
        )
    }
}

@Composable
internal fun HomeCoreUpdateDetailsHost(
    viewModel: HomeViewModel,
    coreList: List<CoreInfo>,
    coreDisplayNames: CoreVariantDisplayNames,
) {
    if (viewModel.showCoreUpdateDetails) {
        viewModel.coreUpdateDetailsVariant?.let { variant ->
            CoreUpdateDetailsPanel(
                displayName = coreDisplayNames.resolve(variant),
                info = coreList.firstOrNull { it.variant == variant },
                comparison = viewModel.coreUpdateComparison,
                isLoading = viewModel.isLoadingCoreUpdateComparison,
                errorMessage = viewModel.coreUpdateComparisonError,
                onRetry = viewModel::retryCoreUpdateComparison,
                onDismiss = viewModel::dismissCoreUpdateDetails,
                onUpdateNow = viewModel::updateFromCoreUpdateDetails
            )
        }
    }
}

