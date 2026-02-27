package com.example.danmuapiapp.ui.screen.console

import androidx.lifecycle.ViewModel
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import com.example.danmuapiapp.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ConsoleViewModel @Inject constructor(
    private val runtimeRepo: RuntimeRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val logs = runtimeRepo.logs

    val logEnabled = settingsRepository.logEnabled
    val logPreviewEnabled = settingsRepository.logPreviewEnabled
    val logMaxCount = settingsRepository.logMaxCount

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    fun refreshLogs() = runtimeRepo.refreshLogs()

    fun clearLogs() = runtimeRepo.clearLogs()

    fun setLogEnabled(enabled: Boolean) = settingsRepository.setLogEnabled(enabled)

    fun setLogPreviewEnabled(enabled: Boolean) = settingsRepository.setLogPreviewEnabled(enabled)

    fun setLogMaxCount(count: Int) = settingsRepository.setLogMaxCount(count)

    fun setSearchQuery(query: String) { _searchQuery.value = query }
}
