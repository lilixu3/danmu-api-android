package com.example.danmuapiapp.ui.screen.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.data.tunnel.TunnelRepository
import com.example.danmuapiapp.domain.repository.AdminSessionRepository
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val runtimeRepository: RuntimeRepository,
    private val adminSessionRepository: AdminSessionRepository,
    private val settingsRepository: SettingsRepository,
    private val tunnelRepository: TunnelRepository
) : ViewModel() {
    val runtimeState = runtimeRepository.runtimeState
    val logs = runtimeRepository.logs
    val adminSessionState = adminSessionRepository.sessionState
    val coreDisplayNames = settingsRepository.coreDisplayNames
    val logPreviewEnabled = settingsRepository.logPreviewEnabled
    val logEnabled = settingsRepository.logEnabled
    val tunnelState = tunnelRepository.state

    fun refreshLogs() = runtimeRepository.refreshLogs()

    fun refreshAdminState() = adminSessionRepository.refresh()

    fun refreshTunnel() = viewModelScope.launch { tunnelRepository.refresh() }
}
