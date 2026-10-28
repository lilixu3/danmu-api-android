package com.example.danmuapiapp.ui.screen.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.danmuapiapp.domain.model.ApiVariant
import com.example.danmuapiapp.domain.model.CoreInfo
import com.example.danmuapiapp.domain.model.CoreSourceStatus
import com.example.danmuapiapp.domain.model.RuntimeState
import com.example.danmuapiapp.domain.repository.SettingsRepository
import com.example.danmuapiapp.ui.screen.home.support.resolveAutoCoreUpdatePrompt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 首页「核心更新提示」子控制器：自动更新弹窗状态机、忽略/延后版本记录与来源关注提示。
 *
 * 对外由 [HomeViewModel] 转发，行为与拆分前一致。
 */
internal class HomeCoreUpdatePromptController(
    private val scope: CoroutineScope,
    private val coreInfoList: StateFlow<List<CoreInfo>>,
    private val runtimeState: StateFlow<RuntimeState>,
    private val settingsRepo: SettingsRepository,
    private val onUpdateRequested: (ApiVariant) -> Unit
) {
    var showUpdatePromptDialog by mutableStateOf(false)
        private set
    var updatePromptVariant by mutableStateOf<ApiVariant?>(null)
        private set
    var updatePromptCurrentVersion by mutableStateOf<String?>(null)
        private set
    var updatePromptLatestVersion by mutableStateOf<String?>(null)
        private set
    var updatePromptSourceMismatch by mutableStateOf(false)
        private set
    var updatePromptSourceUnknownLegacy by mutableStateOf(false)
        private set
    var updatePromptDesiredSource by mutableStateOf<String?>(null)
        private set

    private val ignoredUpdateVersionMap = mutableMapOf<ApiVariant, String?>()
    private val suppressedAutoUpdatePromptVersionMap = mutableMapOf<ApiVariant, String?>()

    /** 载入各核心已忽略的更新版本。 */
    fun loadIgnoredUpdateVersions() {
        ApiVariant.entries.forEach { variant ->
            ignoredUpdateVersionMap[variant] = settingsRepo.getIgnoredUpdateVersion(variant)
        }
    }

    /** 注册核心信息与当前核心变化驱动的更新提示监听。 */
    fun observeUpdatePrompt() {
        scope.launch {
            coreInfoList.collect { list ->
                refreshUpdatePrompt(list)
            }
        }
        scope.launch {
            runtimeState.map { it.variant }.distinctUntilChanged().collect {
                clearCoreAttentionPrompt()
                refreshUpdatePrompt(coreInfoList.value)
            }
        }
    }

    fun openUpdatePromptFromCard() {
        val variant = runtimeState.value.variant
        val info = coreInfoList.value.find { it.variant == variant } ?: return
        if (!info.needsAttention) return
        showCoreAttentionPrompt(variant, info)
    }

    fun dismissCurrentUpdatePrompt() {
        val variant = updatePromptVariant
        val latest = updatePromptLatestVersion
        if (
            variant != null &&
            updatePromptSourceMismatch.not() &&
            updatePromptSourceUnknownLegacy.not() &&
            !latest.isNullOrBlank()
        ) {
            suppressedAutoUpdatePromptVersionMap[variant] = latest.trim()
        }
        clearCoreAttentionPrompt()
    }

    fun ignoreCurrentUpdatePrompt() {
        val variant = updatePromptVariant
        val latest = updatePromptLatestVersion?.trim().orEmpty()
        if (
            variant != null &&
            updatePromptSourceMismatch.not() &&
            updatePromptSourceUnknownLegacy.not() &&
            latest.isNotBlank()
        ) {
            settingsRepo.setIgnoredUpdateVersion(variant, latest)
            ignoredUpdateVersionMap[variant] = latest
        }
        clearCoreAttentionPrompt()
    }

    fun updateFromPrompt() {
        val variant = updatePromptVariant ?: runtimeState.value.variant
        val latest = updatePromptLatestVersion
        if (
            updatePromptSourceMismatch.not() &&
            updatePromptSourceUnknownLegacy.not() &&
            !latest.isNullOrBlank()
        ) {
            suppressedAutoUpdatePromptVersionMap[variant] = latest.trim()
        }
        clearCoreAttentionPrompt()
        onUpdateRequested(variant)
    }

    fun refreshUpdatePrompt(list: List<CoreInfo>) {
        val currentVariant = runtimeState.value.variant
        val info = list.find { it.variant == currentVariant }
        if (info == null || !info.isInstalled || !info.hasVersionUpdate || info.availableVersion.isNullOrBlank()) {
            if (
                updatePromptVariant == currentVariant &&
                updatePromptSourceMismatch.not() &&
                updatePromptSourceUnknownLegacy.not()
            ) {
                clearCoreAttentionPrompt()
            }
            suppressedAutoUpdatePromptVersionMap.remove(currentVariant)
            return
        }

        val latest = info.availableVersion.orEmpty().trim()
        val ignored = ignoredUpdateVersionMap[currentVariant]?.trim().orEmpty()
        if (ignored.isNotBlank() && ignored != latest) {
            settingsRepo.setIgnoredUpdateVersion(currentVariant, null)
            ignoredUpdateVersionMap[currentVariant] = null
        }

        val suppressed = suppressedAutoUpdatePromptVersionMap[currentVariant]?.trim().orEmpty()
        if (suppressed.isNotBlank() && suppressed != latest) {
            suppressedAutoUpdatePromptVersionMap.remove(currentVariant)
        }

        val samePromptShown = showUpdatePromptDialog &&
            updatePromptVariant == currentVariant &&
            updatePromptLatestVersion == latest

        val prompt = resolveAutoCoreUpdatePrompt(
            info = info,
            ignoredVersion = ignoredUpdateVersionMap[currentVariant],
            suppressedVersion = suppressedAutoUpdatePromptVersionMap[currentVariant],
            samePromptShown = samePromptShown
        ) ?: return

        updatePromptVariant = currentVariant
        updatePromptCurrentVersion = prompt.currentVersion
        updatePromptLatestVersion = prompt.latestVersion
        updatePromptSourceMismatch = false
        updatePromptSourceUnknownLegacy = false
        updatePromptDesiredSource = null
        showUpdatePromptDialog = true
    }

    fun showCoreAttentionPrompt(
        variant: ApiVariant,
        info: CoreInfo
    ) {
        updatePromptVariant = variant
        updatePromptCurrentVersion = info.version
        updatePromptLatestVersion = info.availableVersion
        updatePromptSourceMismatch = info.sourceMismatch
        updatePromptSourceUnknownLegacy = info.sourceStatus == CoreSourceStatus.UnknownLegacy
        updatePromptDesiredSource = info.desiredSource
        showUpdatePromptDialog = true
    }

    fun clearCoreAttentionPrompt() {
        showUpdatePromptDialog = false
        updatePromptVariant = null
        updatePromptCurrentVersion = null
        updatePromptLatestVersion = null
        updatePromptSourceMismatch = false
        updatePromptSourceUnknownLegacy = false
        updatePromptDesiredSource = null
    }

    /** 清除某个核心已忽略的更新版本（检查更新或更新成功后调用）。 */
    fun clearIgnoredVersion(variant: ApiVariant) {
        settingsRepo.setIgnoredUpdateVersion(variant, null)
        ignoredUpdateVersionMap[variant] = null
    }

    /** 记录某个核心本轮已展示过的更新版本，避免重复弹出。 */
    fun suppressVersion(variant: ApiVariant, version: String) {
        suppressedAutoUpdatePromptVersionMap[variant] = version
    }
}
