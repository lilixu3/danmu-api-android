package com.example.danmuapiapp.ui.screen.records

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.domain.repository.AdminSessionRepository
import com.example.danmuapiapp.domain.repository.RequestRecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class RequestRecordsViewModel @Inject constructor(
    private val repository: RequestRecordRepository,
    private val adminSessionRepository: AdminSessionRepository
) : ViewModel() {

    val records = repository.records
    val adminSessionState = adminSessionRepository.sessionState
    var isRefreshing by mutableStateOf(false)
        private set

    init {
        refresh()
    }

    fun refresh() {
        if (isRefreshing) return
        viewModelScope.launch {
            isRefreshing = true
            try {
                withContext(Dispatchers.IO) {
                    repository.refreshFromService()
                }
            } finally {
                isRefreshing = false
            }
        }
    }

    fun clearAll() = repository.clearRecords()
}
