package com.example.danmuapiapp.desktop.app

import com.example.danmuapiapp.desktop.runtime.ServicePhase
import com.example.danmuapiapp.desktop.runtime.ServiceUiState

enum class TrayMenuAction {
    OpenApp,
    Start,
    Stop,
    Restart,
    OpenCoreConfig,
    OpenSettings,
    Exit,
}

data class TrayMenuItem(
    val action: TrayMenuAction,
    val label: String,
    val icon: DesktopIconGlyph,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
)

data class TrayMenuGroup(
    val title: String,
    val items: List<TrayMenuItem>,
)

/** Pure menu model: state decides availability, renderer decides presentation. */
object TrayMenuModel {
    fun groups(state: ServiceUiState): List<TrayMenuGroup> = listOf(
        TrayMenuGroup(
            title = "服务",
            items = listOf(
                TrayMenuItem(TrayMenuAction.Start, "启动服务", DesktopIcons.Start, state.canStart && !state.isBusy),
                TrayMenuItem(TrayMenuAction.Stop, "停止服务", DesktopIcons.Stop, state.canStop && !state.isBusy),
                TrayMenuItem(TrayMenuAction.Restart, "重启服务", DesktopIcons.Restart, state.canStop && !state.isBusy),
            ),
        ),
        TrayMenuGroup(
            title = "窗口",
            items = listOf(
                TrayMenuItem(TrayMenuAction.OpenApp, "打开控制台", DesktopIcons.Overview),
                TrayMenuItem(TrayMenuAction.OpenCoreConfig, "打开核心配置", DesktopIcons.Tools),
                TrayMenuItem(TrayMenuAction.OpenSettings, "打开设置", DesktopIcons.Settings),
            ),
        ),
        TrayMenuGroup(
            title = "应用",
            items = listOf(
                TrayMenuItem(TrayMenuAction.Exit, "退出应用", DesktopIcons.Stop, destructive = true),
            ),
        ),
    )

    fun statusText(state: ServiceUiState): String = when (state.phase) {
        ServicePhase.Running -> state.port?.let { "运行中 · 127.0.0.1:$it" } ?: "运行中"
        ServicePhase.Preparing -> "正在准备运行时"
        ServicePhase.Starting -> "正在启动服务"
        ServicePhase.Stopping -> "正在停止服务"
        ServicePhase.CoreSetupRequired -> "待准备核心"
        ServicePhase.Failed -> "启动失败"
        ServicePhase.Stopped -> "未运行"
    }
}
