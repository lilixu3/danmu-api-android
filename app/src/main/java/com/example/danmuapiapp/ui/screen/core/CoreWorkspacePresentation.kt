package com.example.danmuapiapp.ui.screen.core

import com.example.danmuapiapp.domain.model.CoreInfo
import com.example.danmuapiapp.domain.model.GithubAccountStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun coreQuotaUsedRatio(status: GithubAccountStatus): Float {
    val limit = status.coreLimit ?: return 0f
    val remaining = status.coreRemaining ?: return 0f
    if (limit <= 0) return 0f
    return (1f - remaining.toFloat() / limit).coerceIn(0f, 1f)
}

internal fun coreQuotaResetHint(status: GithubAccountStatus, nowSeconds: Long = Instant.now().epochSecond): String {
    val authentication = when {
        status.tokenValid == true -> "已认证"
        status.tokenValid == false -> "认证失败"
        status.tokenConfigured -> "待验证"
        else -> "匿名"
    }
    val reset = status.coreResetEpochSeconds ?: return authentication
    val minutes = ((reset - nowSeconds).coerceAtLeast(0L) + 59) / 60
    return "$authentication · ${if (minutes > 0) "$minutes 分钟后重置" else "即将重置"}"
}

internal fun coreInstalledSubtitle(info: CoreInfo, zone: ZoneId = ZoneId.systemDefault()): String {
    if (!info.isInstalled) return "点下方按钮安装核心"
    val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone)
    if (info.sourceInstalledAtEpochMillis > 0) {
        return "${format.format(Instant.ofEpochMilli(info.sourceInstalledAtEpochMillis))} · GitHub"
    }
    val committedAt = info.sourceCommitPublishedAt.trim()
    val time = runCatching {
        val instant = committedAt.toLongOrNull()?.takeIf { it > 0 }?.let(Instant::ofEpochMilli)
            ?: Instant.parse(committedAt)
        format.format(instant)
    }.getOrNull()
    return time?.let { "提交于 $it" } ?: if (info.sourceCommitSha.isBlank()) "本地核心" else "GitHub · 本地核心"
}
