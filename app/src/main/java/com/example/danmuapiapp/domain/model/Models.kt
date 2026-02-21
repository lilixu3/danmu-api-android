package com.example.danmuapiapp.domain.model

import kotlinx.serialization.Serializable

enum class ServiceStatus {
    Stopped, Starting, Running, Stopping, Error
}

enum class ApiVariant(val key: String, val label: String, val repo: String) {
    Stable("stable", "稳定版", "huangxd-/danmu_api"),
    Dev("dev", "开发版", "lilixu3/danmu_api"),
    Custom("custom", "自定义版", "")
}

enum class RunMode(
    val key: String,
    val label: String,
    val requiresRoot: Boolean
) {
    Normal("normal", "普通", false),
    Root("root", "Root", true);

    companion object {
        fun fromKey(raw: String?): RunMode? {
            val value = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.key == value }
        }
    }
}

data class RuntimeState(
    val status: ServiceStatus = ServiceStatus.Stopped,
    val port: Int = 9321,
    val token: String = "",
    val variant: ApiVariant = ApiVariant.Stable,
    val runMode: RunMode = RunMode.Normal,
    val pid: Int? = null,
    val uptimeSeconds: Long = 0,
    val localUrl: String = "",
    val lanUrl: String = "",
    val errorMessage: String? = null
)

data class CoreInfo(
    val variant: ApiVariant,
    val version: String?,
    val isInstalled: Boolean,
    val isUpdating: Boolean = false,
    val latestVersion: String? = null,
    val hasUpdate: Boolean = false
)

data class CoreDownloadProgress(
    val inProgress: Boolean = false,
    val variant: ApiVariant? = null,
    val actionLabel: String = "",
    val stageText: String = "",
    val progress: Float? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L
)

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel = LogLevel.Info,
    val message: String = ""
)

enum class LogLevel { Info, Warn, Error }

data class GithubRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val publishedAt: String,
    val zipballUrl: String
)

data class GithubProxyOption(
    val id: String,
    val name: String,
    val baseUrl: String,
    val isOriginal: Boolean = false
)

data class WebDavConfig(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val folderPath: String = ""
)

@Serializable
data class RequestRecord(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val scene: String = "",
    val method: String = "GET",
    val url: String = "",
    val statusCode: Int? = null,
    val durationMs: Long = 0L,
    val success: Boolean = false,
    val errorMessage: String? = null,
    val responseSnippet: String? = null
)
