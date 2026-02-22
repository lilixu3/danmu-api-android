package com.example.danmuapiapp.ui.screen.deviceaccess

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.domain.model.DeviceAccessMode
import com.example.danmuapiapp.domain.model.DeviceAccessSnapshot
import com.example.danmuapiapp.domain.repository.AccessControlRepository
import com.example.danmuapiapp.domain.repository.AdminSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DeviceAccessViewModel @Inject constructor(
    private val repository: AccessControlRepository,
    private val adminSessionRepository: AdminSessionRepository
) : ViewModel() {

    companion object {
        private val IPV4_REGEX = Regex(
            """^((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)$"""
        )
        private val IPV6_REGEX = Regex(
            """^(([0-9a-f]{1,4}:){7}[0-9a-f]{1,4}|([0-9a-f]{1,4}:){1,7}:|:([0-9a-f]{1,4}:){1,7}|([0-9a-f]{1,4}:){1,6}:[0-9a-f]{1,4}|([0-9a-f]{1,4}:){1,5}(:[0-9a-f]{1,4}){1,2}|([0-9a-f]{1,4}:){1,4}(:[0-9a-f]{1,4}){1,3}|([0-9a-f]{1,4}:){1,3}(:[0-9a-f]{1,4}){1,4}|([0-9a-f]{1,4}:){1,2}(:[0-9a-f]{1,4}){1,5}|[0-9a-f]{1,4}:((:[0-9a-f]{1,4}){1,6})|:((:[0-9a-f]{1,4}){1,7}|:))$"""
        )
    }

    private val _snapshot = MutableStateFlow(DeviceAccessSnapshot())
    val snapshot: StateFlow<DeviceAccessSnapshot> = _snapshot.asStateFlow()
    val adminSessionState = adminSessionRepository.sessionState

    var isRefreshing by mutableStateOf(false)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var showLanNeighbors by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var operationMessage by mutableStateOf<String?>(null)
        private set

    init {
        refresh()
    }

    fun refresh() {
        if (isRefreshing) return
        viewModelScope.launch {
            isRefreshing = true
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.fetchSnapshot(includeLanNeighbors = showLanNeighbors)
                }
                result.onSuccess {
                    _snapshot.value = it
                }.onFailure {
                    errorMessage = it.message ?: "刷新失败"
                }
            } finally {
                isRefreshing = false
            }
        }
    }

    fun scanLanDevices() {
        if (isRefreshing) return
        showLanNeighbors = true
        viewModelScope.launch {
            isRefreshing = true
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.scanLanDevices()
                }
                result.onSuccess {
                    _snapshot.value = it
                    operationMessage = if (it.lanScannedCount > 0) {
                        "检测到 ${it.lanScannedCount} 台局域网设备"
                    } else {
                        "未检测到可识别的局域网设备"
                    }
                }.onFailure {
                    errorMessage = it.message ?: "检测失败"
                }
            } finally {
                isRefreshing = false
            }
        }
    }

    fun hideLanDevices() {
        if (!showLanNeighbors) return
        showLanNeighbors = false
        refresh()
    }

    fun setBlacklistEnabled(enabled: Boolean) {
        if (!ensureAdminMode()) return
        val current = _snapshot.value.config
        val nextMode = if (enabled) DeviceAccessMode.Blacklist else DeviceAccessMode.Off
        if (current.mode == nextMode) return
        saveConfig(
            nextMode = nextMode,
            blacklist = current.blacklist,
            successMessage = if (enabled) "已开启黑名单防护" else "已关闭黑名单防护"
        )
    }

    fun toggleBlock(ipRaw: String) {
        if (!ensureAdminMode()) return
        val ip = normalizeIp(ipRaw)
        if (ip.isBlank()) return
        val current = _snapshot.value.config
        val blacklist = current.blacklist.toMutableSet()
        val added = if (blacklist.contains(ip)) {
            blacklist.remove(ip)
            false
        } else {
            blacklist.add(ip)
            true
        }
        saveConfig(
            nextMode = current.mode,
            blacklist = blacklist.toList().sorted(),
            successMessage = if (added) "已拉黑设备 $ip" else "已解除拉黑 $ip"
        )
    }

    fun clearBlacklist() {
        if (!ensureAdminMode()) return
        val current = _snapshot.value.config
        if (current.blacklist.isEmpty()) {
            operationMessage = "黑名单已为空"
            return
        }
        saveConfig(
            nextMode = current.mode,
            blacklist = emptyList(),
            clearBlacklist = true,
            successMessage = "已清空黑名单"
        )
    }

    fun clearDeviceStats() {
        if (!ensureAdminMode()) return
        val current = _snapshot.value.config
        saveConfig(
            nextMode = current.mode,
            blacklist = current.blacklist,
            clearDevices = true,
            successMessage = "已清空设备访问统计"
        )
    }

    fun dismissError() {
        errorMessage = null
    }

    fun dismissMessage() {
        operationMessage = null
    }

    private fun saveConfig(
        nextMode: DeviceAccessMode,
        blacklist: List<String>,
        clearDevices: Boolean = false,
        clearBlacklist: Boolean = false,
        successMessage: String
    ) {
        if (isSaving) return
        viewModelScope.launch {
            isSaving = true
            try {
                val currentConfig = _snapshot.value.config
                val result = withContext(Dispatchers.IO) {
                    repository.saveConfig(
                        config = currentConfig.copy(mode = nextMode, blacklist = blacklist),
                        clearDevices = clearDevices,
                        clearBlacklist = clearBlacklist
                    )
                }
                result.onSuccess {
                    _snapshot.value = it
                    operationMessage = successMessage
                }.onFailure {
                    errorMessage = it.message ?: "保存失败"
                }
            } finally {
                isSaving = false
            }
        }
    }

    private fun normalizeIp(raw: String): String {
        var value = raw.trim().lowercase()
        if (value.isBlank()) return ""
        if (value.contains(',')) {
            value = value.substringBefore(',').trim()
        }
        if (value.startsWith('[') && value.contains(']')) {
            value = value.substringAfter('[').substringBefore(']')
        }
        if (value.contains('%')) {
            value = value.substringBefore('%').trim()
        }
        if (value.startsWith("::ffff:")) {
            val mapped = value.removePrefix("::ffff:")
            if (isIp(mapped)) return mapped
        }
        if (Regex("""^\d{1,3}(\.\d{1,3}){3}:\d+$""").matches(value)) {
            value = value.substringBefore(':')
        }
        return if (isIp(value)) value else ""
    }

    private fun isIp(value: String): Boolean {
        if (value.isBlank()) return false
        return IPV4_REGEX.matches(value) || IPV6_REGEX.matches(value)
    }

    private fun ensureAdminMode(): Boolean {
        if (adminSessionRepository.sessionState.value.isAdminMode) return true
        errorMessage = "请先到 设置 > 管理员权限 输入 ADMIN_TOKEN，再执行此操作"
        return false
    }
}
