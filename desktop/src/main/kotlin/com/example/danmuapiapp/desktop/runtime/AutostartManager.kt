package com.example.danmuapiapp.desktop.runtime

import java.io.File

/**
 * 开机自启（Windows）：HKCU Run 键 + `--autostart` 参数。
 * 启用后开机拉起应用，应用检测到该参数后自动启动服务（headless，无窗口）。
 * 仅打包版（jpackage）可用；开发运行无独立可执行文件。
 *
 * 实现注意：直接调用 reg.exe 并逐参数传递（不经 cmd /c 二次解析），
 * 避免可执行文件路径含空格/引号时被搅乱；结果以退出码为准（文案随系统语言变化）。
 */
object AutostartManager {

    private const val RUN_KEY = "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val VALUE_NAME = "DanmuApi"
    /** 历史版本曾用过的键名，刷新时清理，避免残留失效路径。 */
    private val LEGACY_VALUE_NAMES = listOf("DanmuApiDesktop")
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
        val (code, output) = exec("reg", "query", RUN_KEY, "/v", VALUE_NAME)
        return code == 0 && output.contains(VALUE_NAME, ignoreCase = true)
    }

    /**
     * 自愈：已开启自启时，用当前可执行路径重写注册表（应对应用移动/重命名/
     * 重新安装后旧路径失效导致开机不自启），并清理历史键名。
     * 在应用启动（UI 与 headless）时调用。
     */
    fun refreshIfEnabled() {
        runCatching {
            if (!isEnabled()) {
                LEGACY_VALUE_NAMES.forEach { legacy ->
                    exec("reg", "delete", RUN_KEY, "/v", legacy, "/f")
                }
                return
            }
            if (isSupported()) enable()
        }
    }

    /** 启用开机自启；返回 null 表示成功，否则为错误信息。 */
    fun enable(): String? {
        val exe = resolveExecutablePath()
            ?: return "仅打包版应用支持开机自启（开发运行不可用）"
        LEGACY_VALUE_NAMES.forEach { legacy ->
            exec("reg", "delete", RUN_KEY, "/v", legacy, "/f")
        }
        val (code, output) = writeRunValue("\"$exe\" $AUTOSTART_ARG")
        return if (code == 0) {
            null
        } else {
            "写入注册表失败（exit=$code）：${output.trim().ifBlank { "未知错误" }}"
        }
    }

    fun disable(): String? {
        exec("reg", "delete", RUN_KEY, "/v", VALUE_NAME, "/f")
        return null
    }

    fun isAutostartLaunch(args: Array<String>): Boolean = AUTOSTART_ARG in args

    /**
     * 写 Run 键值。reg.exe 的参数解析无法携带含嵌套引号的 /d 值（路径带空格时
     * 必须内嵌引号），改走 PowerShell -EncodedCommand（Base64 UTF-16LE，无引号歧义）。
     */
    internal fun writeRunValue(value: String, name: String = VALUE_NAME): Pair<Int, String> {
        val script = "Set-ItemProperty " +
            "-Path 'HKCU:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run' " +
            "-Name '$name' " +
            "-Value '" + value.replace("'", "''") + "'"
        val encoded = java.util.Base64.getEncoder()
            .encodeToString(script.toByteArray(Charsets.UTF_16LE))
        // 用 System32 绝对路径：PATH 里的 powershell 可能是商店别名存根（CreateProcess 拒绝访问），
        // 开机自启场景下 PATH 也不可靠
        val powershell = (System.getenv("SystemRoot") ?: System.getenv("windir") ?: "C:\\Windows") +
            "\\System32\\WindowsPowerShell\\v1.0\\powershell.exe"
        return exec(
            powershell,
            "-NoProfile",
            "-NonInteractive",
            "-EncodedCommand",
            encoded,
        )
    }

    private fun exec(vararg command: String): Pair<Int, String> {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val code = process.waitFor()
        return code to output
    }
}
