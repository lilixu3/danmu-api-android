package com.example.danmuapiapp.ui.screen.cache

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.domain.model.CacheClearItem
import com.example.danmuapiapp.domain.model.CacheClearSupport
import com.example.danmuapiapp.domain.repository.AdminSessionRepository
import com.example.danmuapiapp.domain.repository.CacheRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CacheViewModel @Inject constructor(
    private val cacheRepository: CacheRepository,
    private val adminSessionRepository: AdminSessionRepository
) : ViewModel() {

    val stats = cacheRepository.cacheStats
    val entries = cacheRepository.cacheEntries
    val adminSessionState = adminSessionRepository.sessionState
    val isLoading = cacheRepository.isLoading
    val clearCapability = cacheRepository.clearCapability

    var selectedItems by mutableStateOf(CacheClearItem.entries.toSet())
        private set

    var isClearing by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var showClearConfirmDialog by mutableStateOf(false)
        private set
    var showAdminRequiredDialog by mutableStateOf(false)
        private set
    var adminRequiredMessage by mutableStateOf("")
        private set

    init {
        refresh()
        viewModelScope.launch {
            clearCapability.collect { capability ->
                if (!capability.supportsSelective) selectAll()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            cacheRepository.refresh()
        }
    }

    fun toggleClearItem(item: CacheClearItem) {
        if (clearCapability.value.support != CacheClearSupport.Selective) return
        selectedItems = if (item in selectedItems) selectedItems - item else selectedItems + item
    }

    fun selectAll() {
        selectedItems = CacheClearItem.entries.toSet()
    }

    fun selectNone() {
        if (clearCapability.value.support == CacheClearSupport.Selective) selectedItems = emptySet()
    }

    fun requestClear() {
        if (selectedItems.isEmpty()) {
            message = "请至少选择一项要清理的缓存"
            return
        }
        val adminState = adminSessionState.value
        if (!adminState.isAdminMode) {
            adminRequiredMessage = if (adminState.hasAdminTokenConfigured) {
                "清理缓存属于管理员写操作，请先到 设置 > 管理员权限 开启管理员模式。"
            } else {
                "当前核心可能要求 ADMIN_TOKEN 才能清理缓存，请先到 设置 > 管理员权限 配置并开启管理员模式。"
            }
            showAdminRequiredDialog = true
            return
        }
        showClearConfirmDialog = true
    }

    fun dismissClearConfirm() {
        showClearConfirmDialog = false
    }

    fun dismissAdminRequiredDialog() {
        showAdminRequiredDialog = false
        adminRequiredMessage = ""
    }

    fun clearSelected() {
        showClearConfirmDialog = false
        isClearing = true
        viewModelScope.launch(Dispatchers.IO) {
            val requested = selectedItems
            cacheRepository.clear(requested).fold(
                onSuccess = { result ->
                    val count = result.clearedItems.size
                    message = if (result.usedSelectiveProtocol) "已清理 $count 项缓存" else "缓存已全部清理"
                },
                onFailure = { message = "清理失败：${it.message}" }
            )
            isClearing = false
        }
    }

    fun dismissMessage() {
        message = null
    }
}
