package com.example.danmuapiapp.desktop.runtime

import java.io.File

/**
 * 桌面端数据目录布局（总计划 7.4 节）。默认全部位于 %LOCALAPPDATA%\DanmuApi 下，
 * 可通过 rootOverride 整体重定向（测试用）。卸载时该目录默认保留。
 */
class DesktopPaths(private val rootOverride: File? = null) {

    val root: File
        get() = rootOverride ?: File(System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() } ?: fallbackRoot(), "DanmuApi")

    /** 内置运行时解压目录：nodejs-project + node.exe（随包资源，首启解压）。 */
    val runtimeDir: File get() = File(root, "runtime")

    /** Node 服务工作目录（DANMU_API_HOME）：config/logs/compile-cache 都在这里。 */
    val dataDir: File get() = File(root, "data")

    /** 桌面宿主日志目录。 */
    val logsDir: File get() = File(root, "logs")

    /** 核心 zipball 下载缓存。 */
    val coreCacheDir: File get() = File(root, "core-cache")

    private fun fallbackRoot(): String = System.getProperty("user.home")
}
