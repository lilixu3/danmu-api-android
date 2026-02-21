package com.example.danmuapiapp.ui.screen.tools

import androidx.lifecycle.ViewModel
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val runtimeRepository: RuntimeRepository
) : ViewModel() {
    val logs = runtimeRepository.logs

    fun refreshLogs() = runtimeRepository.refreshLogs()
}
