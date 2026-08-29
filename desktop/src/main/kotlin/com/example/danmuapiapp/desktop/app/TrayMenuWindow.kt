package com.example.danmuapiapp.desktop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.example.danmuapiapp.desktop.APP_NAME
import com.example.danmuapiapp.desktop.runtime.DesktopRuntimeController
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

/**
 * 托盘右键菜单窗：无边框、置顶的小窗，用 Compose 渲染（中文可靠），
 * 替代 AWT PopupMenu 在裁剪运行时下的乱码问题。
 * 点击菜单项执行动作并关闭；点击窗体外不自动关闭（可再次点击托盘切换）。
 */
@Composable
fun TrayMenuWindow(
    screenX: Int,
    screenY: Int,
    controller: DesktopRuntimeController,
    onOpenApp: () -> Unit,
    onExitApp: () -> Unit,
    onClose: () -> Unit,
) {
    val state by controller.state.collectAsState()
    val menuWidth = 216.dp
    val x = screenX.coerceAtLeast(0)
    val y = screenY.coerceAtLeast(0)

    Window(
        onCloseRequest = onClose,
        visible = true,
        title = "",
        undecorated = true,
        resizable = false,
        alwaysOnTop = true,
        state = rememberWindowState(
            width = menuWidth,
            position = WindowPosition.Absolute(x.dp, y.dp),
        ),
    ) {
        window.addWindowFocusListener(object : WindowAdapter() {
            override fun windowLostFocus(e: WindowEvent) {
                onClose()
            }
        })
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.width(menuWidth),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                // 首行状态（气泡被系统专注助手屏蔽时也能看到服务状态）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$APP_NAME · " + when (state.phase) {
                            com.example.danmuapiapp.desktop.runtime.ServicePhase.Running ->
                                "运行中 (127.0.0.1:${state.port})"
                            com.example.danmuapiapp.desktop.runtime.ServicePhase.Preparing -> "正在准备运行时"
                            com.example.danmuapiapp.desktop.runtime.ServicePhase.Starting -> "正在启动服务"
                            com.example.danmuapiapp.desktop.runtime.ServicePhase.Stopping -> "正在停止服务"
                            com.example.danmuapiapp.desktop.runtime.ServicePhase.Failed -> "启动失败"
                            com.example.danmuapiapp.desktop.runtime.ServicePhase.Stopped -> "未运行"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TrayMenuItem("打开应用") { onOpenApp(); onClose() }
                TrayMenuItem("启动服务", enabled = state.canStart && !state.isBusy) { controller.start(); onClose() }
                TrayMenuItem("停止服务", enabled = state.canStop && !state.isBusy) { controller.stop(); onClose() }
                TrayMenuItem("重启服务", enabled = state.canStop && !state.isBusy) { controller.restart(); onClose() }
                TrayMenuItem("退出", enabled = !state.isBusy, danger = true) {
                    onClose()
                    onExitApp()
                }
            }
        }
    }
}

@Composable
private fun TrayMenuItem(
    label: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = Color.Transparent,
        contentColor = if (danger) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
