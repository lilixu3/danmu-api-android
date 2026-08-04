package com.example.danmuapiapp.ui.screen.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import com.example.danmuapiapp.domain.model.DownloadConflictPolicy
import com.example.danmuapiapp.domain.model.DownloadThrottlePreset
import com.example.danmuapiapp.domain.repository.DanmuDownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadSettingsViewModel @Inject constructor(
    private val repository: DanmuDownloadRepository
) : ViewModel() {

    val settings = repository.settings

    var operationMessage by mutableStateOf<String?>(null)
        private set

    var isSyncingDirectory by mutableStateOf(false)
        private set

    fun dismissMessage() {
        operationMessage = null
    }

    fun setSaveTree(uri: String, displayName: String) {
        repository.setSaveTreeUri(uri, displayName)
        syncExistingFiles(messagePrefix = "下载目录已保存")
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

    fun setCustomThrottleConfig(
        baseDelayMs: Long,
        jitterMaxMs: Long,
        batchSize: Int,
        batchRestMs: Long,
        backoffBaseMs: Long,
        backoffMaxMs: Long
    ) {
        repository.setCustomThrottleConfig(
            baseDelayMs = baseDelayMs,
            jitterMaxMs = jitterMaxMs,
            batchSize = batchSize,
            batchRestMs = batchRestMs,
            backoffBaseMs = backoffBaseMs,
            backoffMaxMs = backoffMaxMs
        )
        repository.setThrottlePreset(DownloadThrottlePreset.Custom)
        operationMessage = "自定义流控参数已保存"
    }

    fun setFileNameTemplate(template: String) {
        repository.setFileNameTemplate(template)
        operationMessage = "命名模板已保存"
    }

    fun syncExistingFiles() {
        syncExistingFiles(messagePrefix = "目录扫描完成")
    }

    private fun syncExistingFiles(messagePrefix: String) {
        if (isSyncingDirectory) return
        viewModelScope.launch {
            isSyncingDirectory = true
            operationMessage = null
            val result = repository.syncExistingFiles()
            operationMessage = result.fold(
                onSuccess = { summary ->
                    buildString {
                        append(messagePrefix)
                        append("：扫描 ${summary.scannedFiles} 个文件，新增 ${summary.importedRecords} 条记录")
                        if (summary.truncated) append("（已达到扫描上限）")
                    }
                },
                onFailure = { throwable ->
                    "目录扫描失败：${throwable.message ?: "未知错误"}"
                }
            )
            isSyncingDirectory = false
        }
    }
}
