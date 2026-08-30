package com.example.danmuapiapp.desktop.app

import com.example.danmuapiapp.desktop.APP_NAME
import com.example.danmuapiapp.desktop.runtime.DesktopRuntimeController
import com.example.danmuapiapp.desktop.runtime.ServicePhase
import com.example.danmuapiapp.desktop.runtime.ServiceUiState
import java.awt.EventQueue
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.File
import java.util.EnumMap
import kotlinx.coroutines.flow.StateFlow

/**
 * 系统托盘（右下角常驻图标）：
 * - 左键单击：打开同一应用进程中的主窗口（后台模式不会启动第二个 UI 进程）；
 * - 右键单击：使用 Windows/AWT 原生 PopupMenu，由系统负责菜单外观、边界和键盘导航；
 * - 状态转换时右下角弹气泡通知（自启成功提示由此实现；被系统专注助手屏蔽时
 *   可从原生托盘菜单首行查看状态）。
 * UI 与 headless 两种模式共用；重复安装是幂等的。
 */
object DesktopTray {

    @Volatile
    private var installed = false

    /** 轮询线程读取的状态源，install 时注入。 */
    @Volatile
    private var controllerState: StateFlow<ServiceUiState>? = null

    private var trayIcon: TrayIcon? = null
    private var popupMenu: PopupMenu? = null
    private var menuItems: EnumMap<TrayMenuAction, MenuItem> = EnumMap(TrayMenuAction::class.java)
    private var statusItem: MenuItem? = null
    private var lastAnnouncedPhase: ServicePhase? = null

    /** 托盘安装诊断结果（null=成功），供调用方写入日志，失败必须暴露而不是静默。 */
    fun install(
        controller: DesktopRuntimeController,
        onOpenApp: () -> Unit,
        onOpenCoreConfig: () -> Unit,
        onOpenSettings: () -> Unit,
        onExitApp: () -> Unit,
    ): String? {
        if (installed) return null
        if (!SystemTray.isSupported()) return "系统不支持托盘（SystemTray.isSupported=false）"
        val image = loadTrayImage()
            ?: return "托盘图标资源加载失败（branding/app-icon-32.png）"
        installed = true
        controllerState = controller.state

        var error: String? = null
        val createOnEdt = Runnable {
            try {
                val icon = TrayIcon(image, "$APP_NAME - 未运行")
                icon.isImageAutoSize = true
                val menu = PopupMenu()
                val status = MenuItem("$APP_NAME - 未运行").apply { isEnabled = false }
                menu.add(status)
                menu.addSeparator()
                statusItem = status

                TrayMenuModel.groups(controller.state.value).forEachIndexed { groupIndex, group ->
                    if (groupIndex > 0) menu.addSeparator()
                    group.items.forEach { item ->
                        val menuItem = MenuItem(item.label).apply {
                            isEnabled = item.enabled
                            addActionListener {
                                runCatching {
                                    when (item.action) {
                                        TrayMenuAction.OpenApp -> EventQueue.invokeLater(onOpenApp)
                                        TrayMenuAction.OpenCoreConfig -> EventQueue.invokeLater(onOpenCoreConfig)
                                        TrayMenuAction.OpenSettings -> EventQueue.invokeLater(onOpenSettings)
                                        TrayMenuAction.Start -> controller.start()
                                        TrayMenuAction.Stop -> controller.stop()
                                        TrayMenuAction.Restart -> controller.restart()
                                        TrayMenuAction.Exit -> EventQueue.invokeLater(onExitApp)
                                    }
                                }.onFailure(::logError)
                            }
                        }
                        menu.add(menuItem)
                        menuItems[item.action] = menuItem
                    }
                }
                icon.popupMenu = menu
                icon.addMouseListener(object : MouseAdapter() {
                    override fun mousePressed(e: MouseEvent) {
                        if (e.button == MouseEvent.BUTTON1) {
                            runCatching { EventQueue.invokeLater(onOpenApp) }
                                .onFailure(::logError)
                        }
                    }
                })
                SystemTray.getSystemTray().add(icon)
                popupMenu = menu
                trayIcon = icon
            } catch (t: Throwable) {
                error = t.message ?: t.toString()
                installed = false
                statusItem = null
                popupMenu = null
                menuItems.clear()
            }
        }
        if (EventQueue.isDispatchThread()) {
            createOnEdt.run()
        } else {
            EventQueue.invokeAndWait(createOnEdt)
        }
        if (error == null) startPolling()
        return error
    }

    /** 状态轮询：tooltip 和原生菜单每次轮询都刷新；气泡仅在进入目标状态时弹一次。 */
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
                        ServicePhase.CoreSetupRequired -> "待准备核心"
                        ServicePhase.Failed -> "启动失败"
                        ServicePhase.Stopped -> "未运行"
                    }
                    EventQueue.invokeLater {
                        trayIcon?.setToolTip(tooltip)
                        updateMenu(state)
                    }
                    if (phase != lastAnnouncedPhase && (
                            phase == ServicePhase.Running ||
                                phase == ServicePhase.CoreSetupRequired ||
                                phase == ServicePhase.Failed
                        )
                    ) {
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
                            ServicePhase.CoreSetupRequired -> Triple(
                                APP_NAME,
                                "请先打开核心页面手动下载核心：${state.message.take(100)}",
                                TrayIcon.MessageType.WARNING,
                            )
                            ServicePhase.Failed -> Triple(
                                APP_NAME,
                                "服务启动失败：${state.message.take(120)}",
                                TrayIcon.MessageType.ERROR,
                            )
                        }
                        EventQueue.invokeLater {
                            runCatching { trayIcon?.displayMessage(title, message, type) }
                                .onFailure(::logError)
                        }
                    }
                }.onFailure(::logError)
                Thread.sleep(1000)
            }
        }, "danmu-desktop-tray").apply { isDaemon = true }.start()
    }

    private fun updateMenu(state: ServiceUiState) {
        statusItem?.label = "$APP_NAME - ${TrayMenuModel.statusText(state)}"
        TrayMenuModel.groups(state)
            .flatMap { it.items }
            .forEach { item -> menuItems[item.action]?.isEnabled = item.enabled }
    }

    fun remove() {
        runCatching {
            trayIcon?.let { SystemTray.getSystemTray().remove(it) }
        }.onFailure(::logError)
        trayIcon = null
        popupMenu = null
        statusItem = null
        menuItems.clear()
        installed = false
        controllerState = null
        lastAnnouncedPhase = null
    }

    /**
     * 单图标仲裁：界面进程打开时隐藏 headless 托盘图标，界面关闭后恢复。
     * 任何时刻用户只看到一个托盘图标。
     */
    fun setIconVisible(visible: Boolean) {
        if (!installed) return
        EventQueue.invokeLater {
            runCatching {
                val tray = SystemTray.getSystemTray()
                if (visible && trayIcon != null && !tray.trayIcons.contains(trayIcon)) {
                    tray.add(trayIcon)
                } else if (!visible && trayIcon != null && tray.trayIcons.contains(trayIcon)) {
                    tray.remove(trayIcon)
                }
            }.onFailure(::logError)
        }
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
            javax.imageio.ImageIO.read(input)
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
