package com.example.danmuapiapp.domain.model

data class AdminSessionState(
    val isAdminMode: Boolean = false,
    val hasAdminTokenConfigured: Boolean = false,
    val tokenHint: String = "未配置"
)

