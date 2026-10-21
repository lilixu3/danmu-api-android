package com.example.danmuapiapp.ui.screen.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.danmuapiapp.data.service.GithubProxyService
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreInfo
import com.example.danmuapiapp.domain.model.CoreUpdateComparison
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.repository.CoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 首页「核心更新检查与变更详情」子控制器：手动检查更新、变更对比加载与详情弹窗状态。
 *
 * 对外由 [HomeViewModel] 转发，行为与拆分前一致。
 */
internal class HomeCoreUpdateCheckController(
    private val scope: CoroutineScope,
    private val coreRepo: CoreRepository,
    private val githubProxyService: GithubProxyService,
    private val runtimeState: StateFlow<RuntimeState>,
    private val coreInfoList: StateFlow<List<CoreInfo>>,
    private val updatePromptController: HomeCoreUpdatePromptController,
    private val variantLabel: (ApiVariant) -> String,
    private val isCoreBusy: () -> Boolean,
    private val setPendingProxyAction: (HomePendingProxyAction) -> Unit,
    private val openProxyPickerDialog: () -> Unit,
    private val onUpdateRequested: (ApiVariant) -> Unit
) {
    var isCheckingCoreUpdate by mutableStateOf(false)
        private set
    var coreUpdateCheckDialogMessage by mutableStateOf<String?>(null)
        private set
    var coreUpdateCheckDialogIsError by mutableStateOf(false)
        private set
    var showCoreUpdateDetails by mutableStateOf(false)
        private set
    var coreUpdateDetailsVariant by mutableStateOf<ApiVariant?>(null)
        private set
    var coreUpdateComparison by mutableStateOf<CoreUpdateComparison?>(null)
        private set
    var isLoadingCoreUpdateComparison by mutableStateOf(false)
        private set
    var coreUpdateComparisonError by mutableStateOf<String?>(null)
        private set

    private var coreUpdateComparisonJob: Job? = null
    private var coreUpdateComparisonGeneration = 0L

    fun quickCheckCurrentCoreUpdate() {
        if (!githubProxyService.apiQuota.allowManualOperation()) return
        if (isCoreBusy() || isCheckingCoreUpdate) return

        resetCoreUpdateCheckDialogState()
        val variant = runtimeState.value.variant
        val info = coreInfoList.value.find { it.variant == variant }
        if (info?.isInstalled != true) {
            coreUpdateCheckDialogMessage = "${variantLabel(variant)} 未安装，无法检查更新"
            coreUpdateCheckDialogIsError = true
            return
        }

        if (!githubProxyService.hasUserSelectedProxy()) {
            setPendingProxyAction(HomePendingProxyAction.CheckUpdate(variant))
            openProxyPickerDialog()
            return
        }

        doQuickCheckCurrentCoreUpdate(variant)
    }

    fun doQuickCheckCurrentCoreUpdate(variant: ApiVariant) {
        if (!githubProxyService.apiQuota.allowManualOperation()) return
        isCheckingCoreUpdate = true
        coreUpdateCheckDialogMessage = null
        coreUpdateCheckDialogIsError = false
        scope.launch {
            val checked = runCatching {
                updatePromptController.clearIgnoredVersion(variant)
                coreRepo.checkAndMarkUpdate(variant)
            }.onFailure {
                coreUpdateCheckDialogMessage = "检查更新失败：${it.message ?: "请稍后重试"}"
                coreUpdateCheckDialogIsError = true
            }.isSuccess
            if (!checked) {
                isCheckingCoreUpdate = false
                return@launch
            }

            val latestInfo = coreInfoList.value.find { it.variant == variant }
            if (latestInfo?.updateCheckError != null && !githubProxyService.apiQuota.allowManualOperation()) {
                isCheckingCoreUpdate = false
                return@launch
            }
            if (latestInfo?.updateCheckError != null) {
                coreUpdateCheckDialogMessage =
                    "检查更新失败：${latestInfo.updateCheckError}"
                coreUpdateCheckDialogIsError = true
            } else if (latestInfo?.needsAttention == true) {
                updatePromptController.showCoreAttentionPrompt(variant, latestInfo)
            } else if (latestInfo?.isInstalled == true) {
                coreUpdateCheckDialogMessage = "已确认 ${variantLabel(variant)} 当前是最新版本"
                coreUpdateCheckDialogIsError = false
            }
            isCheckingCoreUpdate = false
        }
    }

    fun resetCoreUpdateCheckDialogState() {
        if (isCheckingCoreUpdate) return
        coreUpdateCheckDialogMessage = null
        coreUpdateCheckDialogIsError = false
    }

    fun openUpdateDetailsFromPrompt() {
        val variant = updatePromptController.updatePromptVariant ?: return
        if (updatePromptController.updatePromptSourceMismatch ||
            updatePromptController.updatePromptSourceUnknownLegacy
        ) return
        if (!githubProxyService.hasUserSelectedProxy()) {
            setPendingProxyAction(HomePendingProxyAction.LoadUpdateDetails(variant))
            updatePromptController.dismissCurrentUpdatePrompt()
            openProxyPickerDialog()
            return
        }
        openCoreUpdateDetailsNow(variant)
    }

    fun openCoreUpdateDetailsNow(variant: ApiVariant) {
        val latest = coreInfoList.value.firstOrNull { it.variant == variant }
            ?.availableVersion
            ?.trim()
            .orEmpty()
        if (latest.isNotBlank()) {
            updatePromptController.suppressVersion(variant, latest)
        }
        updatePromptController.clearCoreAttentionPrompt()
        coreUpdateDetailsVariant = variant
        coreUpdateComparison = null
        coreUpdateComparisonError = null
        showCoreUpdateDetails = true
        loadCoreUpdateComparison(variant)
    }

    fun retryCoreUpdateComparison() {
        coreUpdateDetailsVariant?.let(::loadCoreUpdateComparison)
    }

    private fun loadCoreUpdateComparison(variant: ApiVariant) {
        coreUpdateComparisonGeneration += 1
        val generation = coreUpdateComparisonGeneration
        coreUpdateComparisonJob?.cancel()
        coreUpdateComparison = null
        coreUpdateComparisonError = null
        isLoadingCoreUpdateComparison = true
        coreUpdateComparisonJob = scope.launch {
            coreRepo.fetchUpdateComparison(variant).fold(
                onSuccess = { comparison ->
                    if (generation == coreUpdateComparisonGeneration && showCoreUpdateDetails) {
                        coreUpdateComparison = comparison
                    }
                },
                onFailure = { error ->
                    if (generation == coreUpdateComparisonGeneration && showCoreUpdateDetails) {
                        coreUpdateComparisonError = error.message ?: "无法获取核心变更详情"
                    }
                }
            )
            if (generation == coreUpdateComparisonGeneration && showCoreUpdateDetails) {
                isLoadingCoreUpdateComparison = false
                coreUpdateComparisonJob = null
            }
        }
    }

    fun dismissCoreUpdateDetails() {
        coreUpdateComparisonGeneration += 1
        coreUpdateComparisonJob?.cancel()
        coreUpdateComparisonJob = null
        showCoreUpdateDetails = false
        coreUpdateDetailsVariant = null
        coreUpdateComparison = null
        coreUpdateComparisonError = null
        isLoadingCoreUpdateComparison = false
    }

    fun updateFromCoreUpdateDetails() {
        val variant = coreUpdateDetailsVariant ?: return
        val latest = coreInfoList.value.firstOrNull { it.variant == variant }
            ?.availableVersion
            ?.trim()
            .orEmpty()
        if (latest.isNotBlank()) {
            updatePromptController.suppressVersion(variant, latest)
        }
        dismissCoreUpdateDetails()
        onUpdateRequested(variant)
    }

    /** ViewModel 清理时取消进行中的变更对比请求。 */
    fun cancelComparison() {
        coreUpdateComparisonJob?.cancel()
    }
}
