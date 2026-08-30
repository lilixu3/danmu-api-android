package com.example.danmuapiapp.desktop.runtime

import com.example.danmuapiapp.desktop.node.StartConfig
import java.io.File
import java.util.Properties

/**
 * 服务配置解析。优先级与 Android NodeProjectManager/RuntimeRepository 对齐：
 * 显式桌面设置 > 当前运行目录 config/.env 合法值 > Android/核心默认值。
 */
data class DesktopRuntimeConfig(
    val port: Int = StartConfig.DEFAULT_PORT,
    val listenHost: String = StartConfig.DEFAULT_LISTEN_HOST,
    val variant: String = "stable",
    val ipv6Enabled: Boolean = false,
)

object DesktopRuntimeConfigResolver {

    fun resolve(settings: DesktopSettings, scriptDir: File): DesktopRuntimeConfig {
        val values = readEnv(scriptDir)
        val port = settings.portOverride
            ?: values["DANMU_API_PORT"]?.toIntOrNull()?.takeIf { it in 1..65535 }
            ?: StartConfig.DEFAULT_PORT
        val configuredHost = settings.listenHostOverride
            ?: values["DANMU_API_HOST"]?.trim()?.takeIf { it.isNotBlank() }
            ?: StartConfig.DEFAULT_LISTEN_HOST
        val ipv6Enabled = settings.ipv6Enabled
        val listenHost = if (ipv6Enabled) "::" else configuredHost.takeUnless { it == "::" } ?: StartConfig.DEFAULT_LISTEN_HOST
        val variant = settings.variantOverride
            ?: values["DANMU_API_VARIANT"]?.trim()?.lowercase()
                ?.takeIf { it in VALID_VARIANTS }
            ?: "stable"
        return DesktopRuntimeConfig(port, listenHost, variant, ipv6Enabled)
    }

    fun readEnv(scriptDir: File): Map<String, String> {
        val envFile = File(scriptDir, "config/.env")
        if (!envFile.isFile) return emptyMap()
        val props = Properties()
        runCatching {
            envFile.readLines(Charsets.UTF_8).forEach { line ->
                val clean = line.trim()
                if (clean.isBlank() || clean.startsWith('#')) return@forEach
                val key = clean.substringBefore('=').trim().uppercase()
                val value = clean.substringAfter('=', "").trim().trim('"', '\'')
                if (key.matches(KEY_PATTERN)) props[key] = value
            }
        }
        return props.entries.associate { it.key.toString() to it.value.toString() }
    }

    private val VALID_VARIANTS = setOf("stable", "dev", "custom")
    private val KEY_PATTERN = Regex("[A-Z_][A-Z0-9_]*")
}
