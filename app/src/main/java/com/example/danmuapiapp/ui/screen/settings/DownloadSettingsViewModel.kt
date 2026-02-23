package com.example.danmuapiapp.ui.screen.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import com.example.danmuapiapp.domain.model.DownloadConflictPolicy
import com.example.danmuapiapp.domain.model.DownloadThrottlePreset
import com.example.danmuapiapp.domain.repository.DanmuDownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DownloadSettingsViewModel @Inject constructor(
    private val repository: DanmuDownloadRepository
) : ViewModel() {

    val settings = repository.settings

    var operationMessage by mutableStateOf<String?>(null)
        private set

    fun dismissMessage() {
        operationMessage = null
    }

    fun setSaveTree(uri: String, displayName: String) {
        repository.setSaveTreeUri(uri, displayName)
        operationMessage = "下载目录已保存"
    }

    fun clearSaveTree() {
        repository.clearSaveTreeUri()
        operationMessage = "已清空下载目录"
    }

    fun setDefaultFormat(format: DanmuDownloadFormat) {
        repository.setDefaultFormat(format)
        operationMessage = "默认格式已切换为 ${format.label}"
    }

    fun setConflictPolicy(policy: DownloadConflictPolicy) {
        repository.setConflictPolicy(policy)
        operationMessage = "冲突策略已切换为 ${policy.label}"
    }

    fun setThrottlePreset(preset: DownloadThrottlePreset) {
        repository.setThrottlePreset(preset)
        operationMessage = "流控预设已切换为 ${preset.label}"
    }

    fun setFileNameTemplate(template: String) {
        repository.setFileNameTemplate(template)
        operationMessage = "命名模板已保存"
    }
}
