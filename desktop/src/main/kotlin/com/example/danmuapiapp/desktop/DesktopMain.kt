package com.example.danmuapiapp.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.danmuapiapp.desktop.app.DesktopShell
import com.example.danmuapiapp.desktop.app.DesktopTray
import com.example.danmuapiapp.desktop.app.ThemePreference
import com.example.danmuapiapp.desktop.app.TrayMenuWindow
import com.example.danmuapiapp.desktop.node.DesktopCoreInstaller
import com.example.danmuapiapp.desktop.runtime.AppInstanceLock
import com.example.danmuapiapp.desktop.runtime.AutostartManager
import com.example.danmuapiapp.desktop.runtime.DesktopSettings
import com.example.danmuapiapp.desktop.runtime.DesktopRuntimeController
import com.example.danmuapiapp.desktop.runtime.ServicePhase
import java.awt.Dimension
import java.io.File
import java.awt.Window
import java.awt.image.BufferedImage
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
    "核心策略: danmu_api 核心不随包内置，首次启动在线下载（${DesktopCoreInstaller.STABLE_REPO}）",
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
    // 并清理历史版本键名；UI 与 headless 两条路径都执行
    runCatching { AutostartManager.refreshIfEnabled() }
    if (AutostartManager.isAutostartLaunch(args)) {
        // 无感自启：后台模式。不持有应用锁——UI 必须能随时打开并接管显示/控制，
        // headless 进程在自己的服务被 UI 接管（node 被关闭）后自动退出。
        runHeadlessApp()
        return
    }
    // 界面模式：单实例互斥（headless 不持锁，二者可并存；UI 会接管显示后台服务）
    val instanceLockFile = File(DesktopSettings.defaultSettingsFile().parentFile, "app.lock")
    if (!AppInstanceLock.tryAcquire(instanceLockFile)) {
        javax.swing.JOptionPane.showMessageDialog(
            null,
            "$APP_NAME 界面已在运行，请从任务栏或托盘打开。",
            APP_NAME,
            javax.swing.JOptionPane.INFORMATION_MESSAGE,
        )
        return
    }
    application {
        val controller = remember { DesktopRuntimeController() }
        var themePref by mutableStateOf(ThemePreference.fromKey(controller.settings.theme))
        var closing by mutableStateOf(false)
        var trayMenuVisible by remember { mutableStateOf(false) }
        var trayMenuPos by remember { mutableStateOf(IntOffset.Zero) }
        var mainWindow: java.awt.Window? = null

        val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
        Window(
            onCloseRequest = {
                // 兜底：确认 node.exe 不残留（优雅关闭，必要时强杀），之后再退出
                if (!closing) {
                    closing = true
                    DesktopTray.remove()
                    controller.shutdown()
                }
                exitApplication()
            },
            title = APP_NAME,
            state = windowState,
        ) {
            window.minimumSize = Dimension(960, 640)
            mainWindow = window
            val icons = remember { loadWindowIcons() }
            if (icons.isNotEmpty()) {
                window.iconImages = icons
            }
            LaunchedEffect(Unit) {
                val error = runCatching {
                    DesktopTray.install(
                        controller,
                        onOpenApp = {
                            window.isVisible = true
                            window.toFront()
                        },
                        onMenu = { x, y ->
                            trayMenuPos = IntOffset(x, y)
                            trayMenuVisible = true
                        },
                    )
                }.exceptionOrNull()
                if (error != null) {
                    runCatching {
                        val log = File(controller.paths.logsDir, "tray.log")
                        log.parentFile?.mkdirs()
                        log.appendText("${java.time.LocalDateTime.now()}  托盘安装异常：$error${System.lineSeparator()}")
                    }
                }
            }
            DesktopShell(
                controller = controller,
                themePreference = themePref,
                onThemeChange = { themePref = it },
            )
        }
        if (trayMenuVisible) {
            TrayMenuWindow(
                screenX = trayMenuPos.x,
                screenY = trayMenuPos.y,
                controller = controller,
                onOpenApp = {
                    mainWindow?.isVisible = true
                    mainWindow?.toFront()
                },
                onExitApp = {
                    DesktopTray.remove()
                    controller.shutdown()
                    exitProcess(0)
                },
                onClose = { trayMenuVisible = false },
            )
        }
    }
}

/**
 * 无感自启：后台 Compose 应用（无主窗口），提供托盘与托盘菜单。
 * - headless 不持有应用锁：UI 可随时打开并接管显示/控制；
 * - 服务被 UI 接管（同身份 /__shutdown）或停止后，本进程自动退出；
 * - ShutdownHook 兜底，系统关机同样不残留 node.exe。
 */
private fun runHeadlessApp() = application(exitProcessOnExit = false) {
    val controller = remember { DesktopRuntimeController() }
    val logFile = remember { File(controller.paths.logsDir, "headless.log") }
    var trayMenuVisible by remember { mutableStateOf(false) }
    var trayMenuPos by remember { mutableStateOf(IntOffset.Zero) }

    fun log(message: String) {
        runCatching {
            logFile.parentFile?.mkdirs()
            logFile.appendText("${java.time.LocalDateTime.now()}  $message${System.lineSeparator()}")
        }
    }

    LaunchedEffect(Unit) {
        log("headless 启动（--autostart），运行目录=${controller.paths.root.absolutePath}")
        Runtime.getRuntime().addShutdownHook(Thread {
            log("进程退出（ShutdownHook）")
            runCatching { DesktopTray.remove() }
            runCatching { controller.shutdown() }
        })
        runCatching {
            val error = DesktopTray.install(
                controller,
                onOpenApp = {
                    val exe = AutostartManager.resolveExecutablePath()
                    if (exe != null) ProcessBuilder(exe).start()
                },
                onMenu = { x, y ->
                    trayMenuPos = IntOffset(x, y)
                    trayMenuVisible = true
                },
            )
            if (error != null) log("托盘安装失败：$error") else log("托盘已安装")
        }.onFailure { log("托盘安装异常：${it.message}") }

        controller.start()
        while (true) {
            val state = controller.state.value
            if (state.phase == ServicePhase.Running && !controller.isChildAlive()) {
                log("node 子进程已退出（可能被 UI 接管），headless 进程结束")
                DesktopTray.remove()
                exitProcess(0)
            }
            if (state.phase == ServicePhase.Stopped || state.phase == ServicePhase.Failed) {
                log("服务进入 ${state.phase}，headless 进程结束")
                DesktopTray.remove()
                exitProcess(0)
            }
            delay(1500)
        }
    }

    // 不可见窗口保活（Compose application 无可见窗口会直接退出）
    Window(visible = false, onCloseRequest = {}) {}

    if (trayMenuVisible) {
        TrayMenuWindow(
            screenX = trayMenuPos.x,
            screenY = trayMenuPos.y,
            controller = controller,
            onOpenApp = {
                val exe = AutostartManager.resolveExecutablePath()
                if (exe != null) ProcessBuilder(exe).start()
            },
            onExitApp = {
                DesktopTray.remove()
                controller.shutdown()
                exitProcess(0)
            },
            onClose = { trayMenuVisible = false },
        )
    }
}
