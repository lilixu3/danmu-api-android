package com.example.danmuapiapp.desktop.core

import java.io.IOException

/** Core update decision produced by a read-only remote check. */
data class CoreUpdateCheck(
    val variant: DesktopCoreVariant,
    val local: DesktopCoreInfo,
    val remote: CoreRemoteSnapshot,
    val available: Boolean,
) {
    val localSha: String get() = local.source?.commitSha.orEmpty()
    val remoteSha: String get() = remote.commitSha
}

/** Compare a complete local source record with the remote branch head. */
fun compareCoreUpdate(
    local: DesktopCoreInfo,
    remote: CoreRemoteSnapshot,
): CoreUpdateCheck {
    val source = local.source ?: throw IOException("核心缺少来源元数据，无法安全检查更新")
    val localSha = source.commitSha?.trim().orEmpty()
    if (!local.valid || localSha.isBlank()) {
        throw IOException("核心缺少本地提交 SHA，无法安全检查更新")
    }
    if (source.repository != remote.repository || source.branch != remote.branch) {
        throw IOException(
            "核心来源不一致：本地 ${source.repository}/${source.branch}，远端 ${remote.repository}/${remote.branch}",
        )
    }
    return CoreUpdateCheck(
        variant = local.variant,
        local = local,
        remote = remote,
        available = !localSha.equals(remote.commitSha, ignoreCase = true),
    )
}

/** Stages displayed by the in-app install/update progress dialog. */
enum class CoreInstallStage(val label: String) {
    Checking("检查远端版本"),
    StoppingService("停止服务"),
    Downloading("下载核心"),
    Extracting("解压核心"),
    Validating("校验核心"),
    Replacing("替换核心"),
    RestartingService("恢复服务"),
    Completed("已完成"),
}

data class CoreInstallProgress(
    val variant: DesktopCoreVariant,
    val stage: CoreInstallStage,
    val routeLabel: String? = null,
    val downloadedBytes: Long? = null,
    val totalBytes: Long? = null,
    val detail: String? = null,
) {
    val fraction: Float?
        get() = if (totalBytes != null && totalBytes > 0L && downloadedBytes != null) {
            (downloadedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        } else {
            null
        }
}
