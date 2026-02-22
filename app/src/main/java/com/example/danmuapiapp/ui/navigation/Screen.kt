package com.example.danmuapiapp.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    Home("home", "首页", Icons.Rounded.Home),
    Core("core", "核心", Icons.Rounded.Code),
    Tools("tools", "工具", Icons.Rounded.Build),
    Settings("settings", "设置", Icons.Rounded.Settings)
}

object ToolRoute {
    const val Console = "tool_console"
    const val ApiTest = "tool_api_test"
    const val PushDanmu = "tool_push_danmu"
    const val RequestRecords = "tool_request_records"
    const val Config = "tool_config"
    const val DeviceAccess = "tool_device_access"
}

object SettingsRoute {
    const val RuntimeAndDir = "settings_runtime_dir"
    const val ThemeDisplay = "settings_theme_display"
    const val WorkDir = "settings_work_dir"
    const val ServiceConfig = "settings_service_config"
    const val Network = "settings_network"
    const val GithubToken = "settings_github_token"
    const val BackupRestore = "settings_backup_restore"
    const val AdminMode = "settings_admin_mode"
}
