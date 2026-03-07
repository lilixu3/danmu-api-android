package com.example.danmuapiapp.ui.screen.tools

import androidx.lifecycle.ViewModel
import com.example.danmuapiapp.domain.repository.AdminSessionRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val runtimeRepository: RuntimeRepository,
    private val adminSessionRepository: AdminSessionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val runtimeState = runtimeRepository.runtimeState
    val logs = runtimeRepository.logs
    val adminSessionState = adminSessionRepository.sessionState
    val logPreviewEnabled = settingsRepository.logPreviewEnabled
    val logEnabled = settingsRepository.logEnabled

    fun refreshLogs() = runtimeRepository.refreshLogs()

    fun refreshAdminState() = adminSessionRepository.refresh()
}
