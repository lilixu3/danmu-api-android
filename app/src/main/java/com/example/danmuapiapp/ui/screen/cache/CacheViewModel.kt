package com.example.danmuapiapp.ui.screen.cache

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.domain.repository.CacheRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CacheViewModel @Inject constructor(
    private val cacheRepository: CacheRepository
) : ViewModel() {

    val stats = cacheRepository.cacheStats
    val entries = cacheRepository.cacheEntries
    val isLoading = cacheRepository.isLoading

    var isClearing by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var showClearConfirmDialog by mutableStateOf(false)
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            cacheRepository.refresh()
        }
    }

    fun requestClear() {
        showClearConfirmDialog = true
    }

    fun dismissClearConfirm() {
        showClearConfirmDialog = false
    }

    fun clearAll() {
        showClearConfirmDialog = false
        isClearing = true
        viewModelScope.launch(Dispatchers.IO) {
            cacheRepository.clearAll().fold(
                onSuccess = { message = "缓存已全部清理" },
                onFailure = { message = "清理失败：${it.message}" }
            )
            isClearing = false
        }
    }

    fun dismissMessage() {
        message = null
    }
}
