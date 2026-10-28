package com.example.danmuapiapp.data.service

import android.content.Context
import java.io.File

/**
 * Root 运行时在设备上的固定位置与标识。
 *
 * 进程名、控制标记文件与 Root 目录只在这里定义一次，避免启动脚本、停止流程与同步
 * 流程各自维护一份字符串。
 */
internal object RootRuntimeLocation {

    /** `app_process --nice-name` 使用的进程名。 */
    const val PROCESS_NAME = "danmuapi_rootnode"

    const val PID_FILE_NAME = "root_node.pid"
    const val STARTED_AT_FILE_NAME = "root_node_started_at_ms"

    /** 核心/依赖整目录同步的超时时间。 */
    const val ROOT_SYNC_TIMEOUT_MS = 90_000L

    /** Root 独立进程的入口类名。 */
    val mainClassName: String = RootNodeEntry::class.java.name

    fun pidFile(context: Context): File = File(context.filesDir, PID_FILE_NAME)

    fun startedAtFile(context: Context): File = File(context.filesDir, STARTED_AT_FILE_NAME)

    fun rootBaseDir(context: Context): String {
        return RuntimePaths.rootBaseDir(context).absolutePath
    }

    fun rootProjectDir(context: Context): String {
        return "${rootBaseDir(context)}/nodejs-project"
    }
}
