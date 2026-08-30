package com.example.danmuapiapp.desktop.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.example.danmuapiapp.desktop.APP_NAME
import com.example.danmuapiapp.desktop.runtime.DesktopRuntimeController
import kotlinx.coroutines.delay
import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

/**
 * Compose-rendered tray menu. A pure [TrayMenuModel] supplies actions; this window only renders
 * them and forwards callbacks, avoiding nested popup windows and localized AWT PopupMenu.
 */
@Composable
fun TrayMenuWindow(
    cursorX: Int,
    cursorY: Int,
    controller: DesktopRuntimeController,
    onOpenApp: () -> Unit,
    onOpenCoreConfig: () -> Unit = onOpenApp,
    onOpenSettings: () -> Unit = onOpenApp,
    onExitApp: () -> Unit,
    onClose: () -> Unit,
) {
    val state by controller.state.collectAsState()
    val windowState = rememberWindowState(width = 280.dp, height = 360.dp)
    Window(
        onCloseRequest = onClose,
        visible = true,
        title = "",
        undecorated = true,
        resizable = false,
        alwaysOnTop = true,
        focusable = true,
        state = windowState,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(30.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(9.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("◈", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(APP_NAME, style = MaterialTheme.typography.labelLarge)
                        Text(
                            TrayMenuModel.statusText(state),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
                TrayMenuModel.groups(state).forEachIndexed { groupIndex, group ->
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                    )
                    group.items.forEach { item ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp),
                            shape = DesktopTokens.ItemShape,
                            color = androidx.compose.ui.graphics.Color.Transparent,
                            contentColor = if (item.destructive) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            enabled = item.enabled,
                            onClick = {
                                onClose()
                                when (item.action) {
                                    TrayMenuAction.OpenApp -> onOpenApp()
                                    TrayMenuAction.OpenCoreConfig -> onOpenCoreConfig()
                                    TrayMenuAction.Start -> controller.start()
                                    TrayMenuAction.Stop -> controller.stop()
                                    TrayMenuAction.Restart -> controller.restart()
                                    TrayMenuAction.OpenSettings -> onOpenSettings()
                                    TrayMenuAction.Exit -> onExitApp()
                                }
                            },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                DesktopIcon(
                                    item.icon,
                                    tint = if (item.destructive) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    size = 17.sp,
                                )
                                Text(item.label)
                            }
                        }
                    }
                    if (groupIndex < TrayMenuModel.groups(state).lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        LaunchedEffect(cursorX, cursorY) {
            delay(60)
            val awt = window
            val graphics = graphicsForPoint(cursorX, cursorY) ?: awt.graphicsConfiguration
            val bounds = graphics.bounds
            val w = awt.width.coerceAtLeast(1)
            val h = awt.height.coerceAtLeast(1)
            val px = cursorX.coerceIn(
                bounds.x + 4,
                (bounds.x + bounds.width - w - 4).coerceAtLeast(bounds.x + 4),
            )
            val py = cursorY.coerceIn(
                bounds.y + 4,
                (bounds.y + bounds.height - h - 40).coerceAtLeast(bounds.y + 4),
            )
            awt.setLocation(px, py)
            awt.toFront()
            awt.requestFocus()
        }

        val focusListener = remember(onClose) {
            object : WindowAdapter() {
                override fun windowLostFocus(e: WindowEvent) {
                    onClose()
                }
            }
        }
        DisposableEffect(focusListener) {
            window.addWindowFocusListener(focusListener)
            onDispose { window.removeWindowFocusListener(focusListener) }
        }
    }
}

private fun graphicsForPoint(x: Int, y: Int): GraphicsConfiguration? =
    GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
        .mapNotNull { it.defaultConfiguration }
        .firstOrNull { it.bounds.contains(x, y) }
