package com.example.danmuapiapp.ui.screen.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.danmuapiapp.data.service.TvConfigSyncClient
import com.example.danmuapiapp.data.service.TvConfigSyncCodec
import com.example.danmuapiapp.domain.model.LogLevel
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 设置页「同步到电视」子控制器：解析同步码并推送当前配置。
 */
internal class SettingsTvSyncController(
    private val scope: CoroutineScope,
    private val tvConfigSyncClient: TvConfigSyncClient,
    private val runtimeRepo: RuntimeRepository,
    private val postMessage: (String) -> Unit
) {
    var isTvSyncOperating by mutableStateOf(false)
        private set
    var tvSyncOperatingText by mutableStateOf("")
        private set

    fun syncConfigToTv(inviteText: String) {
        if (isTvSyncOperating) return
        scope.launch {
            val target = TvConfigSyncCodec.parseTarget(inviteText).getOrElse {
                postMessage(it.message ?: "未识别到电视同步码")
                return@launch
            }
            isTvSyncOperating = true
            tvSyncOperatingText = if (target.deviceName.isBlank()) {
                "正在向电视发送当前配置..."
            } else {
                "正在同步到 ${target.deviceName}..."
            }
            tvConfigSyncClient.syncToTarget(target).fold(
                onSuccess = {
                    postMessage(it)
                    runtimeRepo.addLog(LogLevel.Info, "已通过扫码同步配置到电视端")
                },
                onFailure = {
                    postMessage("同步失败：${it.message ?: "请检查局域网与同步码"}")
                }
            )
            isTvSyncOperating = false
            tvSyncOperatingText = ""
        }
    }
}
