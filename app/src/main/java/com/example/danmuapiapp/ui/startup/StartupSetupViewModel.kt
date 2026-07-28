package com.example.danmuapiapp.ui.startup

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.data.service.GithubProxyService
import com.example.danmuapiapp.data.service.GithubProxySpeedTester
import com.example.danmuapiapp.data.service.RootShell
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreDependencyRepairRequiredException
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.model.resolveCustomCoreConfig
import com.example.danmuapiapp.domain.repository.CoreRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.domain.repository.SettingsRepository
import com.example.danmuapiapp.ui.common.ProxyPickerController
import com.example.danmuapiapp.ui.common.CoreDependencyRepairController
import com.example.danmuapiapp.ui.common.buildRootSwitchDeniedMessage
import com.example.danmuapiapp.ui.common.continueAfterDependencyRepair
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@HiltViewModel
class StartupSetupViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val runtimeRepo: RuntimeRepository,
    private val coreRepo: CoreRepository,
    private val settingsRepo: SettingsRepository,
    private val githubProxyService: GithubProxyService,
    private val githubProxySpeedTester: GithubProxySpeedTester
) : ViewModel() {

    val runtimeState = runtimeRepo.runtimeState
    val coreInfoList = coreRepo.coreInfoList
    val isCoreInfoLoading = coreRepo.isCoreInfoLoading
    val downloadProgress = coreRepo.downloadProgress
    val pendingDependencyRepair = coreRepo.pendingDependencyRepair
    val coreDisplayNames = settingsRepo.coreDisplayNames
    val customCoreSource = settingsRepo.customCoreSource
    val customRepo = settingsRepo.customRepo
    val customRepoBranch = settingsRepo.customRepoBranch
    val proxyOptions = githubProxyService.proxyOptions()

    var operationMessage by mutableStateOf<String?>(null)
        private set
    var isRunModeSwitching by mutableStateOf(false)
        private set
    var isRepairingDependencies by mutableStateOf(false)
        private set

    private val proxyPickerController = ProxyPickerController(
        githubProxyService = githubProxyService,
        githubProxySpeedTester = githubProxySpeedTester,
        scope = viewModelScope,
        proxyOptionsProvider = { proxyOptions }
    )
    private var pendingInstallVariant: ApiVariant? = null
    private var pendingOnlineDependencyRepair = false
    private val dependencyRepairController = CoreDependencyRepairController(
        scope = viewModelScope,
        repository = coreRepo,
        autoShowRequiredPrompt = false,
        setOperating = { isRepairingDependencies = it },
        postMessage = { operationMessage = it },
        onApplied = { request ->
            coreRepo.refreshCoreInfo()
            val continuation = runtimeRepo.continueAfterDependencyRepair(request)
            if (continuation != null) {
                "${variantLabel(request.variant)}依赖已修复，$continuation"
            } else {
                "${variantLabel(request.variant)}依赖检测通过，核心已准备好"
            }
        },
        onDiscarded = { request ->
            "已取消${variantLabel(request.variant)}${request.actionLabel}，可以重新下载核心"
        }
    )

    val showDependencyRepairDialog: Boolean
        get() = dependencyRepairController.showRepairDialog

    val showProxyPickerDialog: Boolean
        get() = proxyPickerController.uiState.isVisible
    val proxySelectedId: String
        get() = proxyPickerController.uiState.selectedId
    val proxyTestingIds: Set<String>
        get() = proxyPickerController.uiState.testingIds
    val proxyLatencyMap: Map<String, Long>
        get() = proxyPickerController.uiState.latencyMap

    init {
        coreRepo.refreshCoreInfo()
        viewModelScope.launch {
            runtimeState
                .map { it.runMode }
                .distinctUntilChanged()
                .collect {
                    coreRepo.refreshCoreInfo()
                }
        }
    }

    fun switchRunMode(target: RunMode) {
        val state = runtimeState.value
        if (isRunModeSwitching || state.runMode == target) return
        if (state.status == ServiceStatus.Running ||
            state.status == ServiceStatus.Starting ||
            state.status == ServiceStatus.Stopping
        ) {
            operationMessage = "服务运行中时请到首页切换运行模式"
            return
        }

        viewModelScope.launch {
            isRunModeSwitching = true
            try {
                if (target.requiresRoot) {
                    val check = withContext(Dispatchers.IO) {
                        RootShell.exec("id", timeoutMs = 4_000L)
                    }
                    if (!check.ok) {
                        operationMessage = buildRootSwitchDeniedMessage(check)
                        return@launch
                    }
                }

                runtimeRepo.updateRunMode(target)
                val switched = withTimeoutOrNull(12_000L) {
                    runtimeState.first { it.runMode == target }
                } != null
                operationMessage = if (switched) {
                    coreRepo.refreshCoreInfo()
                    "已切换到${target.label}模式"
                } else {
                    "切换${target.label}失败，请稍后重试"
                }
            } finally {
                isRunModeSwitching = false
            }
        }
    }

    fun chooseVariant(variant: ApiVariant) {
        if (downloadProgress.value.inProgress) return
        runtimeRepo.updateVariant(variant)
        coreRepo.refreshCoreInfo()
        val installed = coreInfoList.value.find { it.variant == variant }?.isInstalled == true
        operationMessage = if (installed) {
            "已选择${variantLabel(variant)}"
        } else {
            "已选择${variantLabel(variant)}，可以开始下载"
        }
    }

    fun installCore(variant: ApiVariant) {
        if (downloadProgress.value.inProgress) return
        validateVariantBeforeInstall(variant)?.let {
            operationMessage = it
            return
        }
        runtimeRepo.updateVariant(variant)
        if (!githubProxyService.hasUserSelectedProxy()) {
            pendingInstallVariant = variant
            operationMessage = "首次下载前，请先选一条 GitHub 线路"
            proxyPickerController.open()
            return
        }
        doInstallCore(variant)
    }

    fun dismissMessage() {
        operationMessage = null
    }

    fun selectProxy(proxyId: String) {
        proxyPickerController.select(proxyId)
    }

    fun retestProxySpeed() {
        proxyPickerController.retest()
    }

    fun dismissProxyPickerDialog() {
        proxyPickerController.dismiss()
        pendingInstallVariant = null
        pendingOnlineDependencyRepair = false
    }

    fun confirmProxySelection() {
        val variant = pendingInstallVariant
        val repairDependencies = pendingOnlineDependencyRepair
        proxyPickerController.confirm {
            pendingInstallVariant = null
            pendingOnlineDependencyRepair = false
            if (repairDependencies) {
                dependencyRepairController.repairOnlineNow()
            } else if (variant != null) {
                doInstallCore(variant)
            } else {
                operationMessage = "GitHub 线路已保存"
            }
        }
    }

    fun saveCustomCoreSettings(
        displayName: String,
        repo: String,
        branch: String
    ): Boolean {
        val preview = resolveCustomCoreConfig(displayName, repo, branch)
        if (!preview.isValidRepo) {
            operationMessage = "请先填写正确的 GitHub 仓库地址"
            return false
        }
        val resolved = settingsRepo.saveCustomCoreConfig(
            displayName = displayName,
            repoInput = repo,
            branchInput = branch
        )
        coreRepo.refreshCoreInfo()
        operationMessage = buildString {
            append(coreDisplayNames.value.resolve(ApiVariant.Custom))
            append(" 已保存（")
            append(resolved.repo)
            append(" · ")
            append(resolved.branch)
            append("）")
        }
        return true
    }

    private fun validateVariantBeforeInstall(variant: ApiVariant): String? {
        if (variant != ApiVariant.Custom) return null
        if (customCoreSource.value.isValidRepo) return null
        return "${variantLabel(variant)} 未配置仓库，请先到核心管理里填写仓库地址"
    }

    private fun doInstallCore(variant: ApiVariant) {
        viewModelScope.launch {
            runtimeRepo.updateVariant(variant)
            operationMessage = "正在下载${variantLabel(variant)}..."
            coreRepo.installCore(variant).fold(
                onSuccess = {
                    coreRepo.refreshCoreInfo()
                    operationMessage = "${variantLabel(variant)} 已准备好"
                },
                onFailure = { error ->
                    operationMessage = if (error is CoreDependencyRepairRequiredException) {
                        "${variantLabel(variant)}核心已下载，请完成运行依赖检测"
                    } else {
                        "下载失败：${error.message ?: "请稍后重试"}"
                    }
                    if (error !is CoreDependencyRepairRequiredException && githubProxyService.isUsingProxy()) {
                        pendingInstallVariant = variant
                        proxyPickerController.open()
                    }
                }
            )
        }
    }

    fun openDependencyRepairDialog() = dependencyRepairController.openRepairDialog()

    fun dismissDependencyRepairDialog() = dependencyRepairController.dismissRepairDialog()

    fun repairPendingDependenciesOnline() {
        if (githubProxyService.hasUserSelectedProxy()) {
            dependencyRepairController.repairOnlineNow()
        } else {
            pendingOnlineDependencyRepair = true
            operationMessage = "在线修复前，请先选择 GitHub 线路"
            proxyPickerController.open()
        }
    }

    fun repairPendingDependenciesFromArchive(archiveUri: String) {
        dependencyRepairController.repairFromArchive(archiveUri)
    }

    fun discardPendingCoreMutation() = dependencyRepairController.discardPendingMutation()

    private fun variantLabel(variant: ApiVariant): String {
        return coreDisplayNames.value.resolve(variant)
    }
}
