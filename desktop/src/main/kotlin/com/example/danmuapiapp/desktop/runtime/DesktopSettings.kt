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

    init {
        if (settingsFile.isFile) {
            runCatching {
                val props = Properties()
                settingsFile.inputStream().use { props.load(it) }
                props.forEach { (k, v) -> values[k.toString()] = v.toString() }
            }
        }
        runtimeRootOverride = values[RUNTIME_ROOT]?.trim()?.takeIf { it.isNotBlank() }
        githubProxyId = values[GITHUB_PROXY]?.trim()?.takeIf { it.isNotBlank() } ?: PROXY_ORIGINAL
    }

    fun setRuntimeRoot(path: String?) {
        runtimeRootOverride = path?.trim()?.takeIf { it.isNotBlank() }
        persist(RUNTIME_ROOT, runtimeRootOverride)
    }

    fun setGithubProxy(id: String) {
        githubProxyId = id
        persist(GITHUB_PROXY, id)
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
