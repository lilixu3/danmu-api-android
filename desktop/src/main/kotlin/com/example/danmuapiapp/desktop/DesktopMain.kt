package com.example.danmuapiapp.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.danmuapiapp.desktop.app.DesktopShell
import com.example.danmuapiapp.desktop.app.DesktopTray
import com.example.danmuapiapp.desktop.app.ThemePreference
import com.example.danmuapiapp.desktop.node.DesktopCoreInstaller
import com.example.danmuapiapp.desktop.runtime.AutostartManager
import com.example.danmuapiapp.desktop.runtime.DesktopRuntimeController
import com.example.danmuapiapp.desktop.runtime.ServicePhase
import java.awt.Dimension
import java.io.File
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

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
    // 无感自启：开机自启参数走无窗口后台模式，直接运行服务，不进入 UI
    if (AutostartManager.isAutostartLaunch(args)) {
        runHeadlessService()
        return
    }
    application {
        val controller = remember { DesktopRuntimeController() }
        var themePref by mutableStateOf(ThemePreference.fromKey(controller.settings.theme))
        var closing by mutableStateOf(false)

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
            val icons = remember { loadWindowIcons() }
            if (icons.isNotEmpty()) {
                window.iconImages = icons
            }
            // 界面模式同样保留托盘；关闭窗口即退出应用
            LaunchedEffect(Unit) {
                runCatching {
                    DesktopTray.install(controller) {
                        window.isVisible = true
                        window.toFront()
                    }
                }
            }
            DesktopShell(
                controller = controller,
                themePreference = themePref,
                onThemeChange = { themePref = it },
            )
        }
    }
}

/**
 * 无感自启：无窗口后台运行 Node 服务。
 * - 服务被外部接管（UI 同身份 /__shutdown）或停止后，本进程自动退出；
 * - 注册 ShutdownHook 兜底，系统关机时同样不残留 node.exe；
 * - 全程写 headless.log（运行目录 logs 下），用于诊断"开机后服务没起来"。
 */
private fun runHeadlessService() {
    val controller = DesktopRuntimeController()
    val logFile = File(controller.paths.logsDir, "headless.log")
    fun log(message: String) {
        runCatching {
            logFile.parentFile?.mkdirs()
            logFile.appendText(
                "${java.time.LocalDateTime.now()}  $message${System.lineSeparator()}",
            )
        }
    }
    log("headless 启动（--autostart），运行目录=${controller.paths.root.absolutePath}")
    Runtime.getRuntime().addShutdownHook(Thread {
        log("进程退出（ShutdownHook）")
        runCatching { DesktopTray.remove() }
        runCatching { controller.shutdown() }
    })
    // 后台模式同样提供托盘：左键打开应用，右键可启动/停止/重启/退出
    runCatching {
        DesktopTray.install(controller) { launchAppWindow() }
    }.onFailure { log("托盘安装失败：${it.message}") }
    controller.start()
    var lastPhase = controller.state.value.phase
    log("首次状态：$lastPhase，message=${controller.state.value.message}")
    while (true) {
        val state = controller.state.value
        if (state.phase != lastPhase) {
            log("状态变化：$lastPhase -> ${state.phase}，message=${state.message}")
            lastPhase = state.phase
        }
        when (state.phase) {
            ServicePhase.Running -> {
                if (!controller.isChildAlive()) {
                    log("node 子进程已退出（可能被 UI 接管），headless 进程结束")
                    DesktopTray.remove()
                    return
                }
            }
            ServicePhase.Stopped, ServicePhase.Failed -> {
                log("服务进入 ${state.phase}，headless 进程结束")
                DesktopTray.remove()
                return
            }
            else -> {}
        }
        Thread.sleep(1500)
    }
}

/** headless 托盘的「打开应用」：以正常模式启动一个新的应用进程。 */
private fun launchAppWindow() {
    val exe = AutostartManager.resolveExecutablePath() ?: return
    runCatching {
        ProcessBuilder(exe).start()
    }
}
