package com.example.danmuapiapp.ui.screen.push

import java.net.URI
import java.util.Locale
import com.example.danmuapiapp.data.util.DotEnvCodec
import com.example.danmuapiapp.domain.repository.EnvConfigRepository

internal const val PUSH_TARGET_CONFIG_KEY = "DANMU_PUSH_URL"

internal suspend fun persistDefaultPushTarget(repository: EnvConfigRepository, target: String) {
    // setValue falls back to an empty file on read failure and logs write failures without throwing.
    // Use the checked read/write APIs here, preserving unrelated entries and comments.
    val existing = repository.readCurrentRawContent().getOrThrow()
    val entry = "$PUSH_TARGET_CONFIG_KEY=${DotEnvCodec.formatValue(target)}"
    var replaced = false
    val lines = existing.replace("\r\n", "\n").split('\n').mapNotNull { line ->
        val key = line.trim().removePrefix("\uFEFF").substringBefore('=', "").trim()
        if (key == PUSH_TARGET_CONFIG_KEY) {
            if (replaced) null else entry.also { replaced = true }
        } else line
    }.toMutableList()
    if (!replaced) lines.add(entry)
    repository.saveRawContent(lines.joinToString("\n")).getOrThrow()
    val content = repository.readCurrentRawContent().getOrThrow()
    check(DotEnvCodec.parse(content)[PUSH_TARGET_CONFIG_KEY] == target) {
        "配置未写入，请检查写入权限后重试"
    }
}

internal fun defaultPushTarget(ip: String): String =
    "http://${ip.ifBlank { "127.0.0.1" }}:9978/action?do=refresh&type=danmaku&path="

/**
 * 配置管理中的默认目标可能仍是出厂回环地址。该地址只在本机运行时有效，
 * 手机上使用时会自动替换成当前局域网地址；用户自定义的回环地址保持不变。
 */
internal fun resolveDefaultPushTarget(savedTarget: String, lanIp: String): String {
    val saved = savedTarget.trim()
    return when {
        saved.isBlank() -> defaultPushTarget(lanIp)
        isFactoryLoopbackPushTarget(saved) && lanIp.isNotBlank() -> defaultPushTarget(lanIp)
        else -> saved
    }
}

internal fun isFactoryLoopbackPushTarget(raw: String): Boolean {
    val value = raw.trim()
    if (value.isBlank()) return false
    val uri = runCatching {
        URI(if (value.contains("://")) value else "http://$value")
    }.getOrNull() ?: return false

    val host = uri.host?.lowercase(Locale.ROOT)?.removeSurrounding("[", "]") ?: return false
    if (!uri.scheme.equals("http", ignoreCase = true) || uri.rawUserInfo != null || uri.rawFragment != null) return false
    if (host !in LOOPBACK_HOSTS) return false
    if (uri.port != 9978) return false
    if (uri.path != "/action") return false

    val query = uri.rawQuery.orEmpty()
    val parts = query.split('&').filter { it.isNotBlank() }
    val params = parts.associate { part ->
        val key = part.substringBefore('=')
        val value = part.substringAfter('=', "")
        key to value
    }
    return parts.size == 3 && params.keys == setOf("do", "type", "path") &&
        params["do"].equals("refresh", ignoreCase = true) &&
        params["type"].equals("danmaku", ignoreCase = true) &&
        params["path"].isNullOrEmpty()
}

private val LOOPBACK_HOSTS = setOf("127.0.0.1", "localhost", "::1", "0:0:0:0:0:0:0:1")
