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
import com.example.danmuapiapp.domain.model.RunMode
import com.example.danmuapiapp.domain.model.ServiceStatus
import com.example.danmuapiapp.domain.repository.CoreRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.ui.common.ProxyPickerController
import com.example.danmuapiapp.ui.common.buildRootSwitchDeniedMessage
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
    private val githubProxyService: GithubProxyService,
    private val githubProxySpeedTester: GithubProxySpeedTester
) : ViewModel() {

    val runtimeState = runtimeRepo.runtimeState
    val coreInfoList = coreRepo.coreInfoList
    val isCoreInfoLoading = coreRepo.isCoreInfoLoading
    val downloadProgress = coreRepo.downloadProgress
    val proxyOptions = githubProxyService.proxyOptions()

    var operationMessage by mutableStateOf<String?>(null)
        private set
    var isRunModeSwitching by mutableStateOf(false)
        private set

    private val proxyPickerController = ProxyPickerController(
        githubProxyService = githubProxyService,
        githubProxySpeedTester = githubProxySpeedTester,
        scope = viewModelScope,
        proxyOptionsProvider = { proxyOptions }
    )
    private var pendingInstallVariant: ApiVariant? = null

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
            "已选择${variant.label}"
        } else {
            "已选择${variant.label}，可以开始下载"
        }
    }

    fun installCore(variant: ApiVariant) {
        if (downloadProgress.value.inProgress) return
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
    }

    fun confirmProxySelection() {
        val variant = pendingInstallVariant
        proxyPickerController.confirm {
            pendingInstallVariant = null
            if (variant != null) {
                doInstallCore(variant)
            } else {
                operationMessage = "GitHub 线路已保存"
            }
        }
    }

    private fun doInstallCore(variant: ApiVariant) {
        viewModelScope.launch {
            runtimeRepo.updateVariant(variant)
            operationMessage = "正在下载${variant.label}..."
            coreRepo.installCore(variant).fold(
                onSuccess = {
                    coreRepo.refreshCoreInfo()
                    operationMessage = "${variant.label} 已准备好"
                },
                onFailure = {
                    operationMessage = "下载失败：${it.message ?: "请稍后重试"}"
                    if (githubProxyService.isUsingProxy()) {
                        pendingInstallVariant = variant
                        proxyPickerController.open()
                    }
                }
            )
        }
    }
}
