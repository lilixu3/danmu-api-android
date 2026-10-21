package com.example.danmuapiapp.ui.screen.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.danmuapiapp.domain.model.AdminSessionState
import com.example.danmuapiapp.domain.model.CacheClearCapability
import com.example.danmuapiapp.domain.model.CacheClearItem
import com.example.danmuapiapp.domain.repository.CacheRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 首页「缓存清理」子控制器：可选项状态、管理员校验与清理执行。
 *
 * 对外由 [HomeViewModel] 转发，行为与拆分前一致。
 */
internal class HomeCacheClearController(
    private val scope: CoroutineScope,
    private val cacheRepo: CacheRepository,
    private val adminSessionState: StateFlow<AdminSessionState>,
    private val cacheClearCapability: StateFlow<CacheClearCapability>,
    private val postMessage: (String) -> Unit,
    private val refreshCache: () -> Unit
) {
    var isClearingCache by mutableStateOf(false)
        private set
    var selectedCacheClearItems by mutableStateOf(CacheClearItem.entries.toSet())
        private set
    var showCacheAdminRequiredDialog by mutableStateOf(false)
        private set
    var cacheAdminRequiredMessage by mutableStateOf("")
        private set

    /** 选择性清理能力变化时收敛已选项。 */
    fun observeClearCapability() {
        scope.launch {
            cacheClearCapability.collect { capability ->
                if (!capability.supportsSelective) {
                    selectedCacheClearItems = CacheClearItem.entries.toSet()
                }
            }
        }
    }

    fun prepareCacheClearSelection() {
        selectedCacheClearItems = CacheClearItem.entries.toSet()
        refreshCache()
    }

    fun toggleCacheClearItem(item: CacheClearItem) {
        if (!cacheClearCapability.value.supportsSelective) return
        selectedCacheClearItems = if (item in selectedCacheClearItems) {
            selectedCacheClearItems - item
        } else {
            selectedCacheClearItems + item
        }
    }

    fun selectAllCacheClearItems() {
        selectedCacheClearItems = CacheClearItem.entries.toSet()
    }

    fun clearCacheClearSelection() {
        if (cacheClearCapability.value.supportsSelective) selectedCacheClearItems = emptySet()
    }

    fun clearSelectedCache() {
        if (selectedCacheClearItems.isEmpty()) {
            postMessage("请至少选择一项要清理的缓存")
            return
        }
        val adminState = adminSessionState.value
        if (!adminState.isAdminMode) {
            cacheAdminRequiredMessage = if (adminState.hasAdminTokenConfigured) {
                "清理缓存属于管理员写操作，请先到 设置 > 管理员权限 开启管理员模式。"
            } else {
                "当前核心可能要求 ADMIN_TOKEN 才能清理缓存，请先到 设置 > 管理员权限 配置并开启管理员模式。"
            }
            showCacheAdminRequiredDialog = true
            return
        }
        if (isClearingCache) return
        isClearingCache = true
        scope.launch(Dispatchers.IO) {
            val requestedItems = selectedCacheClearItems
            cacheRepo.clear(requestedItems).fold(
                onSuccess = { result ->
                    postMessage(
                        when {
                            !result.isVerified -> "清理请求已完成，但当前核心未返回确认详情，请刷新后核对"
                            result.usedSelectiveProtocol -> "已清理 ${result.clearedItems.size} 项缓存"
                            else -> "缓存已全部清理"
                        }
                    )
                },
                onFailure = { postMessage("清理失败：${it.message}") }
            )
            isClearingCache = false
        }
    }

    fun dismissCacheAdminRequiredDialog() {
        showCacheAdminRequiredDialog = false
        cacheAdminRequiredMessage = ""
    }
}
