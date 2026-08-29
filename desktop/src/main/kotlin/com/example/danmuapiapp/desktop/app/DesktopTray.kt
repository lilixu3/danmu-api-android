package com.example.danmuapiapp.desktop.app

import com.example.danmuapiapp.desktop.APP_NAME
import com.example.danmuapiapp.desktop.runtime.DesktopRuntimeController
import com.example.danmuapiapp.desktop.runtime.ServicePhase
import java.awt.EventQueue
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * 系统托盘（右下角常驻图标）：
 * - 左键单击：打开应用窗口（headless 模式下启动一个新的 UI 进程）；
 * - 右键单击：弹出托盘菜单（由调用方提供的 onMenu 回调触发，菜单窗用 Compose 渲染，
 *   避免 AWT PopupMenu 在裁剪运行时下的中文乱码问题）；
 * - 状态转换时右下角弹气泡通知（自启成功提示由此实现；被系统专注助手屏蔽时
 *   可从托盘菜单首行查看状态）。
 * UI 与 headless 两种模式共用；重复安装是幂等的。
 */
object DesktopTray {

    @Volatile
    private var installed = false

    /** 轮询线程读取的状态源，install 时注入。 */
    @Volatile
    private var controllerState: kotlinx.coroutines.flow.StateFlow<com.example.danmuapiapp.desktop.runtime.ServiceUiState>? = null

    private var trayIcon: TrayIcon? = null
    private var lastPhase: ServicePhase? = null
    private var lastAnnouncedPhase: ServicePhase? = null

    /** 托盘安装诊断结果（null=成功），供调用方写入日志，失败必须暴露而不是静默。 */
    fun install(
        controller: DesktopRuntimeController,
        onOpenApp: () -> Unit,
        onMenu: (screenX: Int, screenY: Int) -> Unit,
    ): String? {
        if (installed) return null
        if (!SystemTray.isSupported()) return "系统不支持托盘（SystemTray.isSupported=false）"
        val image = loadTrayImage()
            ?: return "托盘图标资源加载失败（branding/app-icon-32.png）"
        installed = true
        controllerState = controller.state

        // 托盘组件必须在 AWT EDT 创建。注意：Compose Desktop 的 UI 线程本身就是 EDT，
        // 若当前已在 EDT 直接执行；否则 invokeAndWait 切换。此前 UI 模式在 EDT 上
        // 调用 invokeAndWait 抛错且被 runCatching 吞掉，正是"手动打开无托盘图标"的根因。
        var error: String? = null
        val createOnEdt = Runnable {
            try {
                val icon = TrayIcon(image, "$APP_NAME - 未运行")
                icon.isImageAutoSize = true
                // 左键单击打开应用（AWT 的 action 事件是双击语义，这里用鼠标监听实现单击）
                icon.addMouseListener(object : MouseAdapter() {
                    override fun mousePressed(e: MouseEvent) {
                        if (e.button == MouseEvent.BUTTON1) {
                            runCatching { EventQueue.invokeLater(onOpenApp) }
                                .onFailure(::logError)
                        }
                    }

                    override fun mouseReleased(e: MouseEvent) {
                        if (e.button == MouseEvent.BUTTON3) {
                            // 必须用事件自带的屏幕坐标：TrayIcon 非常规显示组件，
                            // component.locationOnScreen 会抛 IllegalComponentStateException
                            // （曾导致右键菜单完全不弹且异常被吞）。
                            runCatching { onMenu(e.xOnScreen, e.yOnScreen) }
                                .onFailure(::logError)
                        }
                    }
                })
                SystemTray.getSystemTray().add(icon)
                trayIcon = icon
            } catch (t: Throwable) {
                error = t.message ?: t.toString()
                installed = false
            }
        }
        if (EventQueue.isDispatchThread()) {
            createOnEdt.run()
        } else {
            EventQueue.invokeAndWait(createOnEdt)
        }
        startPolling()
        return error
    }

    /** 状态轮询：tooltip 每次轮询都刷新（任何漏更新 1 秒内自愈）；气泡仅在进入 Running/Failed 时弹一次。 */
    private fun startPolling() {
        Thread({
            while (installed) {
                runCatching {
                    val state = controllerState?.value ?: return@runCatching
                    val phase = state.phase
                    val tooltip = "$APP_NAME - " + when (phase) {
                        ServicePhase.Running -> "运行中 (127.0.0.1:${state.port})"
                        ServicePhase.Preparing -> "正在准备运行时"
                        ServicePhase.Starting -> "正在启动服务"
                        ServicePhase.Stopping -> "正在停止服务"
                        ServicePhase.Failed -> "启动失败"
                        ServicePhase.Stopped -> "未运行"
                    }
                    EventQueue.invokeLater {
                        trayIcon?.setToolTip(tooltip)
                    }
                    if (phase != lastAnnouncedPhase && (phase == ServicePhase.Running || phase == ServicePhase.Failed)) {
                        lastAnnouncedPhase = phase
                        val lan = lanAddress()
                        val (title, message, type) = when (phase) {
                            ServicePhase.Running -> Triple(
                                APP_NAME,
                                buildString {
                                    append("服务已在后台启动：http://127.0.0.1:${state.port}")
                                    if (lan != null) append("（局域网 http://$lan:${state.port}）")
                                },
                                TrayIcon.MessageType.INFO,
                            )
                            else -> Triple(
                                APP_NAME,
                                "服务启动失败：${state.message.take(120)}",
                                TrayIcon.MessageType.ERROR,
                            )
                        }
                        EventQueue.invokeLater {
                            runCatching { trayIcon?.displayMessage(title, message, type) }
                        }
                    }
                }
                Thread.sleep(1000)
            }
        }, "danmu-desktop-tray").apply { isDaemon = true }.start()
    }

    fun remove() {
        runCatching {
            trayIcon?.let { SystemTray.getSystemTray().remove(it) }
        }
        trayIcon = null
        installed = false
    }

    /** 本机站点内 IPv4 地址（供通知/界面展示局域网访问地址）。 */
    fun lanAddress(): String? {
        return runCatching {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val addresses = java.util.Collections.list(interfaces.nextElement().inetAddresses)
                addresses.filterIsInstance<java.net.Inet4Address>()
                    .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
                    ?.hostAddress
                    ?.let { return it }
            }
            null
        }.getOrNull()
    }

    private fun loadTrayImage(): BufferedImage? {
        val loader = Thread.currentThread().contextClassLoader
        return loader.getResourceAsStream("branding/app-icon-32.png")?.use { input ->
            ImageIO.read(input)
        }?.takeIf { it.width > 0 }
    }

    /** 托盘链路错误落盘（%LOCALAPPDATA%\DanmuApi\logs\tray.log），禁止静默吞掉。 */
    private fun logError(t: Throwable) {
        runCatching {
            val appdata = System.getenv("LOCALAPPDATA")
                ?: (System.getProperty("user.home") + "\\AppData\\Local")
            val log = File(appdata, "DanmuApi/logs/tray.log")
            log.parentFile?.mkdirs()
            log.appendText("${java.time.LocalDateTime.now()}  ${t.message ?: t.toString()}${System.lineSeparator()}")
        }
    }
}
