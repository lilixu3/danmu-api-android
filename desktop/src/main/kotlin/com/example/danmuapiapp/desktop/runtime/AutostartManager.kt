package com.example.danmuapiapp.desktop.runtime

import java.io.File

/**
 * 开机自启（Windows）：HKCU Run 键 + `--autostart` 参数。
 * 启用后开机拉起应用，应用检测到该参数后自动启动服务。
 * 仅打包版（jpackage）可用；开发运行无独立可执行文件。
 */
object AutostartManager {

    private const val RUN_KEY = "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val VALUE_NAME = "DanmuApiDesktop"
    private const val AUTOSTART_ARG = "--autostart"

    /** 当前进程的可执行文件路径；开发运行返回 null（无法自启）。 */
    fun resolveExecutablePath(): String? {
        val command = ProcessHandle.current().info().command().orElse(null) ?: return null
        val file = File(command)
        if (!file.isFile) return null
        // 开发运行时是 java.exe / gradle 启动，不具备独立可执行入口
        return if (file.nameWithoutExtension.equals("java", ignoreCase = true)) null else file.absolutePath
    }

    fun isSupported(): Boolean = resolveExecutablePath() != null

    fun isEnabled(): Boolean {
        val output = exec("reg query \"$RUN_KEY\" /v $VALUE_NAME")
        return output.contains(VALUE_NAME, ignoreCase = true)
    }

    /** 启用开机自启；返回 null 表示成功，否则为错误信息。 */
    fun enable(): String? {
        val exe = resolveExecutablePath() ?: return "仅打包版应用支持开机自启（开发运行不可用）"
        val output = exec(
            "reg add \"$RUN_KEY\" /v $VALUE_NAME /t REG_SZ /d \"\\\"$exe\\\" $AUTOSTART_ARG\" /f",
        )
        return if (output.contains("操作成功完成")) null else "写入注册表失败：$output"
    }

    fun disable(): String? {
        exec("reg delete \"$RUN_KEY\" /v $VALUE_NAME /f")
        return null
    }

    fun isAutostartLaunch(args: Array<String>): Boolean = AUTOSTART_ARG in args

    private fun exec(command: String): String {
        val process = ProcessBuilder("cmd", "/c", command).start()
        val stdout = process.inputStream.bufferedReader().readText()
        process.waitFor()
        return stdout
    }
}
