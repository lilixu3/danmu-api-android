package com.example.danmuapiapp.desktop.app

import com.example.danmuapiapp.desktop.APP_NAME
import com.example.danmuapiapp.desktop.runtime.DesktopRuntimeController
import com.example.danmuapiapp.desktop.runtime.ServicePhase
import java.awt.EventQueue
import java.awt.Font
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.system.exitProcess

/**
 * 系统托盘（右下角常驻图标）：
 * - 左键单击：打开应用窗口（headless 模式下启动一个新的 UI 进程）；
 * - 右键菜单：打开应用 / 启动 / 停止 / 重启 / 退出；
 * - 状态转换时右下角弹气泡通知（自启成功提示由此实现）。
 * UI 与 headless 两种模式共用；重复安装是幂等的。
 */
object DesktopTray {

    @Volatile
    private var installed = false

    private var trayIcon: TrayIcon? = null
    private var lastPhase: ServicePhase? = null
    private var lastAnnouncedPhase: ServicePhase? = null

    fun install(controller: DesktopRuntimeController, onOpenApp: () -> Unit) {
        if (installed) return
        if (!SystemTray.isSupported()) return
        val image = loadTrayImage() ?: run {
            // 图标加载失败时托盘不会出现，必须留下可见的失败痕迹而不是静默吞掉
            println("[DesktopTray] 托盘图标资源加载失败（branding/app-icon-32.png）")
            return
        }
        installed = true

        EventQueue.invokeAndWait {
            val popup = PopupMenu()
            // AWT 默认菜单字体缺中文字形会显示方块，显式指定中文字体
            val cjkFont = Font("Microsoft YaHei", Font.PLAIN, 12)
            popup.font = cjkFont
            val open = MenuItem("打开应用")
            val start = MenuItem("启动服务")
            val stop = MenuItem("停止服务")
            val restart = MenuItem("重启服务")
            val exit = MenuItem("退出")
            listOf(open, start, stop, restart, exit).forEach { it.font = cjkFont }
            open.addActionListener { EventQueue.invokeLater(onOpenApp) }
            start.addActionListener { controller.start() }
            stop.addActionListener { controller.stop() }
            restart.addActionListener { controller.restart() }
            exit.addActionListener {
                controller.shutdown()
                remove()
                exitProcess(0)
            }
            popup.add(open)
            popup.addSeparator()
            popup.add(start)
            popup.add(stop)
            popup.add(restart)
            popup.addSeparator()
            popup.add(exit)

            val icon = TrayIcon(image, "$APP_NAME - 未运行", popup)
            icon.isImageAutoSize = true
            // 左键单击打开应用（AWT 的 action 事件是双击语义，这里用鼠标监听实现单击）
            icon.addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    if (e.button == MouseEvent.BUTTON1) {
                        EventQueue.invokeLater(onOpenApp)
                    }
                }
            })
            SystemTray.getSystemTray().add(icon)
            trayIcon = icon
        }

        // 状态轮询：更新 tooltip，并在关键转换时弹气泡通知
        Thread({
            while (installed) {
                runCatching {
                    val state = controller.state.value
                    val phase = state.phase
                    if (phase != lastPhase) {
                        lastPhase = phase
                        EventQueue.invokeLater {
                            trayIcon?.setToolTip("$APP_NAME - " + when (phase) {
                                ServicePhase.Running -> "运行中 (127.0.0.1:${state.port})"
                                ServicePhase.Preparing -> "正在准备运行时"
                                ServicePhase.Starting -> "正在启动服务"
                                ServicePhase.Stopping -> "正在停止服务"
                                ServicePhase.Failed -> "启动失败"
                                ServicePhase.Stopped -> "未运行"
                            })
                        }
                        // 仅在进入 Running/Failed 时弹一次气泡（自启成功提示走这里）
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

    private fun loadTrayImage(): BufferedImage? {
        val loader = Thread.currentThread().contextClassLoader
        return loader.getResourceAsStream("branding/app-icon-32.png")?.use { input ->
            ImageIO.read(input)
        }?.takeIf { it.width > 0 }
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
}
