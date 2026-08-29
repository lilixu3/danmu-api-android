package com.example.danmuapiapp.desktop.runtime

import java.util.Base64

/**
 * Windows 防火墙入站放行管理：
 * - 查询（netsh show）无需管理员；
 * - 添加规则需要提权（UAC 确认；内置管理员账户静默通过）；
 * - 只处理本应用运行目录内 node.exe 的入站 TCP 放行，不做其他改动。
 */
object FirewallManager {

    /** 查询入站规则中是否已放行指定程序（按完整路径匹配，路径不受系统语言影响）。 */
    fun hasInboundRule(programPath: String): Boolean {
        val (code, output) = exec(
            "netsh", "advfirewall", "firewall", "show", "rule", "name=all", "dir=in", "verbose",
        )
        return code == 0 && output.contains(programPath, ignoreCase = true)
    }

    /**
     * 确保存在入站放行规则；已存在直接返回 null。
     * 返回 null 表示成功（或已存在），否则为需要展示给用户的错误信息。
     */
    fun ensureInboundRule(programPath: String, ruleName: String): String? {
        if (hasInboundRule(programPath)) return null
        val script = "netsh advfirewall firewall add rule " +
            "name='$ruleName' " +
            "description='DanmuApi node.exe inbound' " +
            "dir=in action=allow program='$programPath' " +
            "enable=yes profile=any"
        val encoded = Base64.getEncoder()
            .encodeToString(script.toByteArray(Charsets.UTF_16LE))
        val wrapper = "Start-Process powershell -Verb RunAs -Wait -WindowStyle Hidden " +
            "-ArgumentList '-NoProfile','-NonInteractive','-EncodedCommand','$encoded'"
        val wrapperEncoded = Base64.getEncoder()
            .encodeToString(wrapper.toByteArray(Charsets.UTF_16LE))
        val (code, output) = exec(
            powershell(),
            "-NoProfile",
            "-NonInteractive",
            "-EncodedCommand",
            wrapperEncoded,
        )
        if (code != 0) {
            return "防火墙规则添加失败（exit=$code）：${output.trim().take(160)}"
        }
        return if (hasInboundRule(programPath)) {
            null
        } else {
            "防火墙规则未见生效，请手动放行：$programPath"
        }
    }

    private fun powershell(): String {
        val root = System.getenv("SystemRoot") ?: System.getenv("windir") ?: "C:\\Windows"
        return "$root\\System32\\WindowsPowerShell\\v1.0\\powershell.exe"
    }

    private fun exec(vararg command: String): Pair<Int, String> {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val code = process.waitFor()
        return code to output
    }
}
