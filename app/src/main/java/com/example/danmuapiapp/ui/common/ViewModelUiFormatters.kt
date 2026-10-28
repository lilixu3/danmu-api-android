package com.example.danmuapiapp.ui.common

import com.example.danmuapiapp.data.service.RootShell
import com.example.danmuapiapp.data.util.DotEnvCodec

internal fun buildRootSwitchDeniedMessage(result: RootShell.Result): String {
    if (result.timedOut) {
        return "Root 授权超时，请在授权弹窗中允许后重试"
    }
    val detail = (result.stderr.ifBlank { result.stdout })
        .lineSequence()
        .firstOrNull()
        ?.trim()
        .orEmpty()
    if (detail.contains("not found", ignoreCase = true)) {
        return "未检测到 su，无法切换到高权限模式"
    }
    if (detail.contains("denied", ignoreCase = true)) {
        return "未授予 Root 权限，无法切换到高权限模式"
    }
    return if (detail.isBlank()) {
        "未获得 Root 权限，无法切换到高权限模式"
    } else {
        "未获得 Root 权限，无法切换：$detail"
    }
}

internal fun parseEnvContentMap(content: String): Map<String, String> {
    return DotEnvCodec.parse(content)
}
