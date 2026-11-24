package com.example.danmuapiapp.ui.screen.records

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.domain.model.RequestTraceDetail
import com.example.danmuapiapp.domain.repository.AdminSessionRepository
import com.example.danmuapiapp.domain.repository.RequestRecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class RequestRecordsViewModel @Inject constructor(
    private val repository: RequestRecordRepository,
    private val adminSessionRepository: AdminSessionRepository
) : ViewModel() {

    data class TraceDetailState(
        val requestId: String? = null,
        val detail: RequestTraceDetail? = null,
        val loading: Boolean = false,
        val error: String? = null
    )

    val records = repository.records
    val todayRequestCount = repository.todayRequestCount
    val adminSessionState = adminSessionRepository.sessionState
    var isRefreshing by mutableStateOf(false)
        private set
    var traceDetailState by mutableStateOf(TraceDetailState())
        private set
    private var traceDetailJob: Job? = null

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

    fun openTraceDetail(requestId: String) {
        traceDetailJob?.cancel()
        traceDetailState = TraceDetailState(requestId = requestId, loading = true)
        traceDetailJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.fetchTraceDetail(requestId)
            }
            if (traceDetailState.requestId != requestId) return@launch
            traceDetailState = result.fold(
                onSuccess = { TraceDetailState(requestId = requestId, detail = it) },
                onFailure = {
                    TraceDetailState(
                        requestId = requestId,
                        error = it.message ?: "读取请求处理详情失败"
                    )
                }
            )
        }
    }

    fun closeTraceDetail() {
        traceDetailJob?.cancel()
        traceDetailJob = null
        traceDetailState = TraceDetailState()
    }
}
