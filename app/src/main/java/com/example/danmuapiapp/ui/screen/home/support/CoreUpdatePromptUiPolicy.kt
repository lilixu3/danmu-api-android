package com.example.danmuapiapp.ui.screen.home.support

import com.example.danmuapiapp.domain.model.CoreInfo

internal data class CoreAutoUpdatePromptState(
    val currentVersion: String?,
    val latestVersion: String
)

internal fun resolveAutoCoreUpdatePrompt(
    info: CoreInfo?,
    ignoredVersion: String?,
    suppressedVersion: String?,
    samePromptShown: Boolean
): CoreAutoUpdatePromptState? {
    if (info == null || !info.isInstalled || !info.hasVersionUpdate) return null

    val latest = info.availableVersion?.trim().orEmpty()
    if (latest.isBlank()) return null

    val ignored = ignoredVersion?.trim().orEmpty()
    if (ignored == latest) return null

    val suppressed = suppressedVersion?.trim().orEmpty()
    if (suppressed == latest) return null

    if (samePromptShown) return null

    return CoreAutoUpdatePromptState(
        currentVersion = info.version,
        latestVersion = latest
    )
}

internal fun resolveCoreActionButtonText(
    isCoreInfoLoading: Boolean,
    isCoreInstalled: Boolean,
    hasVersionUpdate: Boolean,
    sourceMismatch: Boolean,
    sourceUnknownLegacy: Boolean = false,
    availableVersion: String?,
    isInstalling: Boolean,
    isUpdating: Boolean
): String {
    return when {
        isInstalling -> "下载中..."
        isUpdating -> "更新中..."
        isCoreInfoLoading -> "检测中"
        !isCoreInstalled -> "点击下载"
        sourceMismatch -> "重新下载"
        sourceUnknownLegacy -> "重新下载"
        hasVersionUpdate -> "更新 ${availableVersion?.let { "v$it" } ?: "核心"}"
        else -> "暂无更新"
    }
}
