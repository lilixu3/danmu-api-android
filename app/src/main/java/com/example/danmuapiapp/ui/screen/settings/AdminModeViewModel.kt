package com.example.danmuapiapp.ui.screen.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.danmuapiapp.domain.repository.AdminSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminModeViewModel @Inject constructor(
    private val adminSessionRepository: AdminSessionRepository
) : ViewModel() {

    val sessionState = adminSessionRepository.sessionState

    var tokenInput by mutableStateOf("")
        private set
    var showToken by mutableStateOf(false)
        private set
    var isOperating by mutableStateOf(false)
        private set
    var operationMessage by mutableStateOf<String?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun refresh() = adminSessionRepository.refresh()

    fun updateTokenInput(value: String) {
        tokenInput = value
    }

    fun toggleTokenVisible() {
        showToken = !showToken
    }

    fun submit() {
        if (isOperating) return
        viewModelScope.launch {
            isOperating = true
            val state = sessionState.value
            val result = if (state.hasAdminTokenConfigured) {
                adminSessionRepository.login(tokenInput)
            } else {
                adminSessionRepository.setAdminTokenAndLogin(tokenInput)
            }
            result.onSuccess {
                operationMessage = if (state.hasAdminTokenConfigured) {
                    "管理员模式已开启"
                } else {
                    "ADMIN_TOKEN 已保存并开启管理员模式"
                }
                tokenInput = ""
            }.onFailure {
                errorMessage = it.message ?: "操作失败"
            }
            isOperating = false
        }
    }

    fun logout() {
        if (isOperating) return
        viewModelScope.launch {
            isOperating = true
            adminSessionRepository.logout()
            operationMessage = "已退出管理员模式"
            tokenInput = ""
            isOperating = false
        }
    }

    fun dismissMessage() {
        operationMessage = null
    }

    fun dismissError() {
        errorMessage = null
    }
}

