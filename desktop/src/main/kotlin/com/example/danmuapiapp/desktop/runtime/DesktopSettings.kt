package com.example.danmuapiapp.desktop.runtime

import java.io.File
import java.util.Properties

/**
 * 桌面端设置持久化（settings.json 的 Properties 简版，原子写入）。
 * 与 Android prefs 对应的最小集合：运行目录自定义 + GitHub 线路选择。
 */
class DesktopSettings(private val settingsFile: File) {

    private val values = java.util.concurrent.ConcurrentHashMap<String, String>()

    @Volatile
    var runtimeRootOverride: String?
        private set

    @Volatile
    var githubProxyId: String
        private set

    /** Whether the user explicitly confirmed the selected GitHub route. */
    @Volatile
    var githubProxyConfirmed: Boolean
        private set

    /** 主题：system / light / dark（默认跟随系统）。 */
    @Volatile
    var theme: String
        private set

    /** GitHub Token（提高 API 限额；仅存本机设置文件）。 */
    @Volatile
    var githubToken: String
        private set

    /**
     * 管理员令牌的桌面覆盖值。
     * null 表示桌面端从未覆盖核心 .env；空字符串表示用户明确关闭 ADMIN_TOKEN。
     * 正文只在本机运行时配置链路中使用，UI 不应直接展示。
     */
    @Volatile
    var adminTokenOverride: String?
        private set

    /** 用户显式设置的服务端口；null 表示按运行目录 .env，再回退 9321。 */
    @Volatile
    var portOverride: Int?
        private set

    /** 用户显式设置的监听地址；null 表示按 .env，再回退 0.0.0.0。 */
    @Volatile
    var listenHostOverride: String?
        private set

    /** 是否使用核心的双栈监听（DANMU_API_HOST=::）。 */
    @Volatile
    var ipv6Enabled: Boolean
        private set

    /** 用户显式设置的核心变体；null 表示按 .env，再回退 stable。 */
    @Volatile
    var variantOverride: String?
        private set

    /** 关闭窗口行为：ask（每次询问）/ exit（退出并关闭服务）/ tray（后台运行）。 */
    @Volatile
    var closeAction: String
        private set

    /** 是否已设置过端口/监听/变体，区分“未设置”与默认值。 */
    val hasExplicitRuntimeConfig: Boolean
        get() = portOverride != null || listenHostOverride != null || variantOverride != null

    /** 核心前台自动检查间隔，与 Android CoreUpdateCheckPolicy 的默认值一致。 */
    val coreUpdateCheckIntervalMinutes: Int = 10

    fun lastCoreUpdateCheckAt(variantKey: String): Long =
        values[lastCoreUpdateCheckKey(variantKey)]?.toLongOrNull() ?: 0L

    fun setLastCoreUpdateCheckAt(variantKey: String, timestampMs: Long) {
        require(variantKey.isNotBlank()) { "核心变体不能为空" }
        require(timestampMs >= 0L) { "检查时间不能为负数" }
        persist(lastCoreUpdateCheckKey(variantKey), timestampMs.toString())
    }

    private fun lastCoreUpdateCheckKey(variantKey: String): String =
        "core_update_last_check_${variantKey.trim().lowercase()}"

    init {
        if (settingsFile.isFile) {
            try {
                val props = Properties()
                // The writer uses UTF-8. Properties.load(InputStream) interprets bytes as
                // ISO-8859-1, which silently corrupts Chinese paths after an app restart.
                settingsFile.reader(Charsets.UTF_8).use { props.load(it) }
                props.forEach { (k, v) -> values[k.toString()] = v.toString() }
            } catch (error: Exception) {
                throw IllegalStateException("无法读取桌面端设置文件：${settingsFile.absolutePath}", error)
            }
        }
        runtimeRootOverride = values[RUNTIME_ROOT]?.trim()?.takeIf { it.isNotBlank() }
        githubProxyId = values[GITHUB_PROXY]?.trim()?.takeIf { it.isNotBlank() } ?: PROXY_ORIGINAL
        githubProxyConfirmed = values[GITHUB_PROXY_CONFIRMED]?.trim()?.toBooleanStrictOrNull() ?: false
        theme = values[THEME]?.trim()?.takeIf { it in listOf("system", "light", "dark") } ?: "system"
        githubToken = values[GITHUB_TOKEN]?.trim().orEmpty()
        adminTokenOverride = values[ADMIN_TOKEN]?.let { it.trim() }
        portOverride = values[PORT]?.trim()?.toIntOrNull()?.takeIf { it in 1..65535 }
        listenHostOverride = values[LISTEN_HOST]?.trim()?.takeIf { it.isNotBlank() }
        ipv6Enabled = values[IPV6_ENABLED]?.trim()?.toBooleanStrictOrNull() ?: false
        variantOverride = values[VARIANT]?.trim()?.lowercase()?.takeIf { it in listOf("stable", "dev", "custom") }
        closeAction = values[CLOSE_ACTION]?.trim()?.takeIf { it in listOf("ask", "exit", "tray") } ?: "ask"
    }

    fun setCloseAction(value: String) {
        closeAction = if (value in listOf("ask", "exit", "tray")) value else "ask"
        persist(CLOSE_ACTION, closeAction)
    }

    fun setPortOverride(value: Int?) {
        portOverride = value?.takeIf { it in 1..65535 }
        persist(PORT, portOverride?.toString())
    }

    fun setListenHostOverride(value: String?) {
        listenHostOverride = value?.trim()?.takeIf { it.isNotBlank() }
        persist(LISTEN_HOST, listenHostOverride)
    }

    fun setIpv6Enabled(value: Boolean) {
        ipv6Enabled = value
        persist(IPV6_ENABLED, value.toString())
    }

    fun setVariantOverride(value: String?) {
        variantOverride = value?.trim()?.lowercase()?.takeIf { it in listOf("stable", "dev", "custom") }
        persist(VARIANT, variantOverride)
    }

    fun setRuntimeRoot(path: String?) {
        runtimeRootOverride = path?.trim()?.takeIf { it.isNotBlank() }
        persist(RUNTIME_ROOT, runtimeRootOverride)
    }

    fun setGithubProxy(id: String) {
        require(id in setOf(PROXY_ORIGINAL, "gh_proxy_org", "hk_gh_proxy", "cdn_gh_proxy", "edgeone_gh_proxy")) {
            "未知 GitHub 线路：$id"
        }
        githubProxyId = id
        persist(GITHUB_PROXY, id)
    }

    fun setGithubProxyConfirmed(confirmed: Boolean) {
        githubProxyConfirmed = confirmed
        persist(GITHUB_PROXY_CONFIRMED, confirmed.toString())
    }

    fun setTheme(value: String) {
        theme = if (value in listOf("system", "light", "dark")) value else "system"
        persist(THEME, theme)
    }

    fun setGithubToken(token: String) {
        githubToken = token.trim()
        persist(GITHUB_TOKEN, githubToken.ifBlank { null })
    }

    /** Save an explicit ADMIN_TOKEN override without ever logging or returning its value. */
    fun setAdminTokenOverride(token: String?) {
        val normalized = token?.trim()
        adminTokenOverride = normalized
        persist(ADMIN_TOKEN, normalized)
    }

    private fun persist(key: String, value: String?) {
        synchronized(values) {
            if (value == null) values.remove(key) else values[key] = value
            settingsFile.parentFile?.mkdirs()
            val tmp = File(settingsFile.parentFile, settingsFile.name + ".tmp")
            tmp.writeText(
                buildString {
                    append("# 弹幕API Desktop 设置\n")
                    values.entries.sortedBy { it.key }.forEach { (k, v) ->
                        append(k).append('=').append(v.replace("\\", "\\\\")).append('\n')
                    }
                },
                Charsets.UTF_8,
            )
            val moved = tmp.renameTo(settingsFile)
            if (!moved) {
                tmp.copyTo(settingsFile, overwrite = true)
                tmp.delete()
            }
        }
    }

    companion object {
        private const val RUNTIME_ROOT = "runtime_root"
        private const val GITHUB_PROXY = "github_proxy"
        private const val GITHUB_PROXY_CONFIRMED = "github_proxy_confirmed"
        private const val THEME = "theme"
        private const val GITHUB_TOKEN = "github_token"
        private const val ADMIN_TOKEN = "admin_token"
        private const val PORT = "port"
        private const val LISTEN_HOST = "listen_host"
        private const val IPV6_ENABLED = "ipv6_enabled"
        private const val VARIANT = "variant"
        private const val CLOSE_ACTION = "close_action"

        /** 与 Android GithubProxyService 的 PROXY_ID_ORIGINAL 一致。 */
        const val PROXY_ORIGINAL = "original"

        /** 默认设置文件位置：Roaming 应用私有目录（不随运行目录自定义而变）。 */
        fun defaultSettingsFile(): File {
            val appdata = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
                ?: (System.getProperty("user.home") + File.separator + "AppData" + File.separator + "Roaming")
            return File(appdata, "DanmuApi/settings.properties")
        }
    }
}
