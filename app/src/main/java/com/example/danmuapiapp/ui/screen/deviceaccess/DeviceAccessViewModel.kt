package com.example.danmuapiapp.ui.screen.deviceaccess

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.domain.model.DeviceAccessConfig
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

enum class DeviceRuleList {
    Whitelist,
    Blacklist
}

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
                    repository.fetchSnapshot()
                }
                result.onSuccess { _snapshot.value = it }
                    .onFailure { errorMessage = it.message ?: "刷新失败" }
            } finally {
                isRefreshing = false
            }
        }
    }

    fun applyMode(mode: DeviceAccessMode) {
        if (!ensureAdminMode()) return
        val current = _snapshot.value.config
        if (current.mode == mode) return
        saveConfig(
            next = current.copy(mode = mode),
            successMessage = "访问模式已切换为 ${mode.label}"
        )
    }

    fun toggleRule(ipRaw: String, list: DeviceRuleList) {
        if (!ensureAdminMode()) return
        val ip = normalizeIp(ipRaw)
        if (ip.isBlank()) return
        val current = _snapshot.value.config

        val whitelist = current.whitelist.toMutableSet()
        val blacklist = current.blacklist.toMutableSet()
        when (list) {
            DeviceRuleList.Whitelist -> {
                if (whitelist.contains(ip)) whitelist.remove(ip)
                else {
                    whitelist.add(ip)
                    blacklist.remove(ip)
                }
            }

            DeviceRuleList.Blacklist -> {
                if (blacklist.contains(ip)) blacklist.remove(ip)
                else {
                    blacklist.add(ip)
                    whitelist.remove(ip)
                }
            }
        }

        val next = current.copy(
            whitelist = whitelist.toList().sorted(),
            blacklist = blacklist.toList().sorted()
        )
        saveConfig(next = next, successMessage = "规则已更新")
    }

    fun addManualIp(raw: String, list: DeviceRuleList) {
        if (!ensureAdminMode()) return
        val ip = normalizeIp(raw)
        if (ip.isBlank()) {
            errorMessage = "请输入有效的 IPv4/IPv6 地址"
            return
        }
        val current = _snapshot.value.config
        val whitelist = current.whitelist.toMutableSet()
        val blacklist = current.blacklist.toMutableSet()
        when (list) {
            DeviceRuleList.Whitelist -> {
                whitelist.add(ip)
                blacklist.remove(ip)
            }

            DeviceRuleList.Blacklist -> {
                blacklist.add(ip)
                whitelist.remove(ip)
            }
        }
        val next = current.copy(
            whitelist = whitelist.toList().sorted(),
            blacklist = blacklist.toList().sorted()
        )
        saveConfig(next = next, successMessage = "已添加设备 $ip")
    }

    fun clearRules() {
        if (!ensureAdminMode()) return
        val current = _snapshot.value.config
        saveConfig(
            next = current.copy(whitelist = emptyList(), blacklist = emptyList()),
            clearRules = true,
            successMessage = "已清空黑白名单"
        )
    }

    fun clearDeviceStats() {
        if (!ensureAdminMode()) return
        saveConfig(
            next = _snapshot.value.config,
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
        next: DeviceAccessConfig,
        clearDevices: Boolean = false,
        clearRules: Boolean = false,
        successMessage: String
    ) {
        if (isSaving) return
        viewModelScope.launch {
            isSaving = true
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.saveConfig(
                        config = next,
                        clearDevices = clearDevices,
                        clearRules = clearRules
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
