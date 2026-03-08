package com.example.danmuapiapp.ui.common

import com.example.danmuapiapp.data.service.RootShell
import java.util.Locale

internal fun formatBytesText(bytes: Long): String {
    if (bytes <= 0) return "0B"
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> String.format(Locale.getDefault(), "%.2fGB", bytes / gb)
        bytes >= mb -> String.format(Locale.getDefault(), "%.2fMB", bytes / mb)
        bytes >= kb -> String.format(Locale.getDefault(), "%.1fKB", bytes / kb)
        else -> "${bytes}B"
    }
}

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
    val map = linkedMapOf<String, String>()
    content.lineSequence().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
        val index = trimmed.indexOf('=')
        if (index <= 0) return@forEach
        val key = trimmed.substring(0, index).trim()
        var value = trimmed.substring(index + 1).trim()
        if ((value.startsWith("\"") && value.endsWith("\"")) ||
            (value.startsWith("'") && value.endsWith("'"))
        ) {
            value = value.substring(1, value.length - 1)
        }
        map[key] = value
    }
    return map
}
