package com.example.danmuapiapp.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.danmuapiapp.desktop.app.CloseActionDialog
import com.example.danmuapiapp.desktop.app.DesktopPage
import com.example.danmuapiapp.desktop.app.DesktopShell
import com.example.danmuapiapp.desktop.app.DesktopTheme
import com.example.danmuapiapp.desktop.app.DesktopTray
import com.example.danmuapiapp.desktop.app.ThemePreference
import com.example.danmuapiapp.desktop.node.DesktopCoreInstaller
import com.example.danmuapiapp.desktop.runtime.AppInstanceLock
import com.example.danmuapiapp.desktop.runtime.InstanceCommand
import com.example.danmuapiapp.desktop.runtime.AutostartManager
import com.example.danmuapiapp.desktop.runtime.DesktopSettings
import com.example.danmuapiapp.desktop.runtime.DesktopRuntimeController
import com.example.danmuapiapp.desktop.runtime.ServicePhase
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Color as AwtColor
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.delay
import kotlin.system.exitProcess

/** 与 Android 端对齐的应用显示名（app/src/main/res/values/strings.xml 的 app_name）。 */
const val APP_NAME = "弹幕API"

fun hasClasspathResource(path: String): Boolean {
    val loader = Thread.currentThread().contextClassLoader
    // ClassLoader.getResourceAsStream 不接受前导斜杠
    return loader.getResourceAsStream(path.removePrefix("/"))?.use { true } ?: false
}

fun buildInfoLines(): List<String> = listOf(
    "$APP_NAME — P0 技术验证",
    "OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}",
    "Java: ${System.getProperty("java.version")}（${System.getProperty("java.vendor")}）",
    "核心策略: danmu_api 核心不随包内置，首次使用需在核心页手动下载（${DesktopCoreInstaller.STABLE_REPO}）",
)

/** 读取随包品牌图标（与 Android 启动图标同源），用于窗口/任务栏图标。 */
fun loadWindowIcons(): List<BufferedImage> {
    val loader = Thread.currentThread().contextClassLoader
    return listOf("branding/app-icon-32.png", "branding/app-icon-256.png").mapNotNull { path ->
        loader.getResourceAsStream(path)?.use { input -> ImageIO.read(input) }
    }.filter { it.width > 0 }
}

fun main(args: Array<String>) {
    // 自愈：已开启自启时用当前路径刷新注册表（应用移动/重装后旧路径失效的场景），
    // 并清理历史版本键名；UI 与后台启动共用同一单实例入口。
    runCatching { AutostartManager.refreshIfEnabled() }

    val lockFile = File(DesktopSettings.defaultSettingsFile().parentFile, "app.lock")
    if (!AppInstanceLock.tryAcquire(lockFile)) {
        val command = if (args.contains("--settings")) {
            InstanceCommand.SHOW_SETTINGS
        } else {
            InstanceCommand.SHOW_OVERVIEW
        }
        val result = AppInstanceLock.sendCommand(command)
        if (result.isFailure && !AutostartManager.isAutostartLaunch(args)) {
            javax.swing.JOptionPane.showMessageDialog(
                null,
                "$APP_NAME 已有实例正在运行，但无法唤醒它：${result.exceptionOrNull()?.message}",
                APP_NAME,
                javax.swing.JOptionPane.ERROR_MESSAGE,
            )
        }
        return
    }

    runDesktopApplication(
        args = args,
        initiallyVisible = !AutostartManager.isAutostartLaunch(args),
    )
}

/**
 * UI 与开机后台模式共用一个 Compose 应用进程、一个窗口和一个托盘图标。
 * 后台模式只把主窗口设为不可见；不可见保活窗口保证 Compose 事件循环继续运行，
 * 因而不会因为隐藏窗口而触发 ShutdownHook 并关闭 Node 服务。
 */
@Composable
private fun WindowScope.BorderlessWindowFrame(
    windowState: WindowState,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = androidx.compose.material3.MaterialTheme.colorScheme
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.background,
        contentColor = colors.onBackground,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 8.dp,
    ) {
        ColumnWithTitleBar(
            windowState = windowState,
            onMinimize = onMinimize,
            onToggleMaximize = onToggleMaximize,
            onClose = onClose,
            content = content,
        )
    }
}

@Composable
private fun WindowScope.ColumnWithTitleBar(
    windowState: WindowState,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(42.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 仅标题文字区域负责拖拽窗口；控制按钮必须位于拖拽区之外，否则 Windows 下
            // 会优先把点击解释为窗口拖动，导致最大化按钮无法收到点击。
            WindowDraggableArea(
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(start = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        APP_NAME,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TitleBarButton("—", onMinimize)
            TitleBarButton(
                if (windowState.placement == WindowPlacement.Maximized) "❐" else "□",
                onToggleMaximize,
            )
            TitleBarButton("×", onClose, destructive = true)
        }
        Box(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun TitleBarButton(label: String, onClick: () -> Unit, destructive: Boolean = false) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        color = Color.Transparent,
        contentColor = if (destructive) androidx.compose.material3.MaterialTheme.colorScheme.error else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(width = 46.dp, height = 42.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontWeight = FontWeight.Medium)
        }
    }
}

private fun runDesktopApplication(
    args: Array<String>,
    initiallyVisible: Boolean,
) = application(exitProcessOnExit = false) {
    val controller = remember { DesktopRuntimeController() }
    val lockFile = remember { File(DesktopSettings.defaultSettingsFile().parentFile, "app.lock") }
    val logFile = remember { File(controller.paths.logsDir, "headless.log") }
    var themePref by remember { mutableStateOf(ThemePreference.fromKey(controller.settings.theme)) }
    val darkTheme = when (themePref) {
        ThemePreference.Light -> false
        ThemePreference.Dark -> true
        ThemePreference.System -> isSystemInDarkTheme()
    }
    var closeDialogVisible by remember { mutableStateOf(false) }
    var requestedPage by remember {
        mutableStateOf(if (args.contains("--settings")) DesktopPage.Settings else DesktopPage.Overview)
    }
    var windowVisible by remember { mutableStateOf(initiallyVisible) }
    var foregroundTick by remember { mutableStateOf(0) }
    var mainWindow by remember { mutableStateOf<java.awt.Window?>(null) }

    fun log(message: String) {
        runCatching {
            logFile.parentFile?.mkdirs()
            logFile.appendText("${java.time.LocalDateTime.now()}  $message${System.lineSeparator()}")
        }
    }

    fun restoreWindow() {
        windowVisible = true
        mainWindow?.let { window ->
            window.focusableWindowState = true
            if (window is java.awt.Frame && window.state == java.awt.Frame.ICONIFIED) {
                window.state = java.awt.Frame.NORMAL
            }
        }
    }

    fun showPage(page: DesktopPage) {
        requestedPage = page
        restoreWindow()
    }

    fun hideToTray() {
        // 后台运行：仅隐藏业务窗口；keep-alive 窗口保证 Compose/AWT 事件循环继续存活。
        log("收到后台运行请求：隐藏主窗口，保留控制通道与 Node 子进程")
        windowVisible = false
        mainWindow?.isVisible = false
    }

    fun exitCompletely() {
        log("收到退出应用请求：停止 Node、移除托盘并释放单实例锁")
        DesktopTray.remove()
        controller.shutdown()
        AppInstanceLock.release()
        exitProcess(0)
    }

    fun handleCloseRequest() {
        when (controller.settings.closeAction) {
            "exit" -> exitCompletely()
            "tray" -> hideToTray()
            else -> closeDialogVisible = true
        }
    }

    LaunchedEffect(Unit) {
        val isAutostart = !initiallyVisible
        if (isAutostart) {
            log("后台启动（--autostart），运行目录=${controller.paths.root.absolutePath}")
        }
        val endpointError = AppInstanceLock.startControlServer(lockFile) { command ->
            // IPC 在独立的 accept 线程上，Compose 状态必须回到 AWT/Compose 事件线程修改。
            EventQueue.invokeLater {
                when (command) {
                    InstanceCommand.SHOW_OVERVIEW -> showPage(DesktopPage.Overview)
                    InstanceCommand.SHOW_SETTINGS -> showPage(DesktopPage.Settings)
                }
            }
        }
        if (endpointError != null) {
            log("本地唤醒通道启动失败：$endpointError")
        }
        val trayError = runCatching {
            DesktopTray.install(
                controller = controller,
                onOpenApp = { showPage(DesktopPage.Overview) },
                onOpenCoreConfig = { showPage(DesktopPage.Configuration) },
                onOpenSettings = { showPage(DesktopPage.Settings) },
                onExitApp = ::exitCompletely,
            )
        }.getOrElse { error -> "托盘安装异常：${error.message ?: error::class.java.simpleName}" }
        if (trayError != null) {
            log("托盘安装失败：$trayError")
        } else if (isAutostart) {
            log("后台托盘已安装")
        }
        if (isAutostart) {
            controller.start()
            // 后台实例持续运行直到用户明确退出或服务进入终止状态。
            val startedAt = System.currentTimeMillis()
            var sawActivity = false
            var livenessReconcileSubmitted = false
            while (true) {
                val state = controller.state.value
                if (state.phase != ServicePhase.Stopped) sawActivity = true
                when {
                    state.phase == ServicePhase.Running && !controller.isChildAlive() && !livenessReconcileSubmitted -> {
                        livenessReconcileSubmitted = true
                        log("检测到 node 子进程已退出，后台应用保留并同步服务失败")
                        controller.reconcileLiveness()
                    }
                    state.phase == ServicePhase.CoreSetupRequired -> {
                        log("核心尚未准备：${state.message.take(200)}，后台应用结束")
                        exitCompletely()
                        return@LaunchedEffect
                    }
                    state.phase == ServicePhase.Failed -> {
                        log("服务进入 Failed：${state.message.take(200)}，后台应用保留，等待用户从托盘打开处理")
                        return@LaunchedEffect
                    }
                    state.phase == ServicePhase.Stopped && sawActivity -> {
                        log("服务已停止，后台应用保留，等待用户从托盘打开处理")
                        return@LaunchedEffect
                    }
                    state.phase == ServicePhase.Stopped &&
                        System.currentTimeMillis() - startedAt > 15_000 -> {
                        log("启动无进展（Stopped 超过 15s），后台应用保留，等待用户从托盘打开处理")
                        return@LaunchedEffect
                    }
                }
                delay(1_500)
            }
        }
    }

    val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
    Window(
        visible = windowVisible,
        onCloseRequest = ::handleCloseRequest,
        title = APP_NAME,
        state = windowState,
        undecorated = true,
        transparent = true,
        resizable = true,
    ) {
        val icons = remember { loadWindowIcons() }
        LaunchedEffect(window) {
            window.minimumSize = Dimension(960, 640)
            window.background = AwtColor(0, 0, 0, 0)
            mainWindow = window
            window.focusableWindowState = true
            if (icons.isNotEmpty()) {
                window.iconImages = icons
            }
            EventQueue.invokeLater {
                if (windowVisible) {
                    window.toFront()
                    window.requestFocus()
                }
            }
        }
        val focusListener = remember {
            object : WindowAdapter() {
                override fun windowActivated(e: WindowEvent) {
                    foregroundTick++
                }
            }
        }
        androidx.compose.runtime.DisposableEffect(focusListener) {
            window.addWindowFocusListener(focusListener)
            onDispose { window.removeWindowFocusListener(focusListener) }
        }
        DesktopTheme(darkTheme = darkTheme) {
            BorderlessWindowFrame(
                windowState = windowState,
                onMinimize = { windowState.isMinimized = true },
            onToggleMaximize = {
                windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                    WindowPlacement.Floating
                } else {
                    WindowPlacement.Maximized
                }
            },
            onClose = ::handleCloseRequest,
        ) {
                Box(Modifier.fillMaxSize()) {
                    DesktopShell(
                        controller = controller,
                        themePreference = themePref,
                        onThemeChange = { themePref = it },
                        initialPage = requestedPage,
                        foregroundTick = foregroundTick,
                    )
                    if (closeDialogVisible) {
                        CloseActionDialog(
                            onChoose = { action, rememberChoice ->
                                closeDialogVisible = false
                                if (rememberChoice) controller.settings.setCloseAction(action)
                                when (action) {
                                    "exit" -> exitCompletely()
                                    "tray" -> hideToTray()
                                }
                            },
                            onCancel = { closeDialogVisible = false },
                        )
                    }
                }
            }
        }
    }

    // Compose application 在所有窗口都不可见时可能结束事件循环；仅 visible=false
    // 并不能作为后台保活。这个 1x1 透明、移到屏幕外的窗口保持 Compose/AWT 事件循环，
    // 不承载业务内容，也不会创建可见的第二个应用窗口或标题栏。
    val keepAliveState = rememberWindowState(width = 1.dp, height = 1.dp)
    Window(
        visible = true,
        onCloseRequest = {},
        title = "",
        state = keepAliveState,
        undecorated = true,
        transparent = true,
        resizable = false,
        focusable = false,
    ) {
        LaunchedEffect(Unit) {
            window.setLocation(-32_000, -32_000)
            window.focusableWindowState = false
            runCatching { window.opacity = 0f }
        }
        Box(Modifier.size(1.dp))
    }

    // ShutdownHook 只在 JVM 真正退出时兜底清理；隐藏到托盘不会触发它。
    LaunchedEffect(Unit) {
        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching { DesktopTray.remove() }
            runCatching { controller.shutdown() }
            runCatching { AppInstanceLock.release() }
        })
    }
}
