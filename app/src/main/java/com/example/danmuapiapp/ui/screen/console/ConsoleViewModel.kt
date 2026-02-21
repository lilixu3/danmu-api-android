package com.example.danmuapiapp.ui.screen.console

import androidx.lifecycle.ViewModel
import com.example.danmuapiapp.domain.repository.RuntimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ConsoleViewModel @Inject constructor(
    private val runtimeRepo: RuntimeRepository
) : ViewModel() {

    val logs = runtimeRepo.logs

    fun refreshLogs() = runtimeRepo.refreshLogs()

    fun clearLogs() = runtimeRepo.clearLogs()
}
