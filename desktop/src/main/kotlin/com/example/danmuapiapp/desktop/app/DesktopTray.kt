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

    /** 托盘安装诊断结果（null=成功），供调用方写入日志，失败必须暴露而不是静默。 */
    fun install(controller: DesktopRuntimeController, onOpenApp: () -> Unit): String? {
        if (installed) return null
        if (!SystemTray.isSupported()) return "系统不支持托盘（SystemTray.isSupported=false）"
        val image = loadTrayImage()
            ?: return "托盘图标资源加载失败（branding/app-icon-32.png）"
        installed = true

        // 托盘组件必须在 AWT EDT 创建。注意：Compose Desktop 的 UI 线程本身就是 EDT，
        // 若当前已在 EDT 直接执行；否则 invokeAndWait 切换。此前 UI 模式在 EDT 上
        // 调用 invokeAndWait 抛错且被 runCatching 吞掉，正是"手动打开无托盘图标"的根因。
        var error: String? = null
        val createOnEdt = Runnable {
            try {
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
        return error
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
}
