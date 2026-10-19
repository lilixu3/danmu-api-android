package com.example.danmuapiapp.ui.screen.push

import java.net.URI
import com.example.danmuapiapp.data.util.pushTargetAddressKey

internal enum class TargetChoiceOrigin {
    Current,
    ConfiguredDefault,
    Recent
}

internal data class PushTargetChoice(
    val address: String,
    val origin: TargetChoiceOrigin
)

/**
 * The selected address can be the saved default, a recently pushed address, or a temporary input.
 * Keeping these identities separate avoids showing the same address twice and prevents saving a
 * default from looking like a successful push.
 */
internal data class PushTargetSelection(
    val address: String,
    val effectiveDefault: String,
    val hasConfiguredDefault: Boolean,
    val matchesConfiguredDefault: Boolean,
    val isFactoryDefault: Boolean,
    val origin: TargetChoiceOrigin
)

internal fun resolvePushTargetSelection(
    target: String,
    savedDefaultTarget: String,
    effectiveDefaultTarget: String,
    recentTargets: List<String>
): PushTargetSelection {
    val current = target.trim()
    val saved = savedDefaultTarget.trim()
    val effective = effectiveDefaultTarget.trim()
    val matchesSaved = current.isNotBlank() && samePushTarget(current, saved)
    val matchesEffective = current.isNotBlank() && samePushTarget(current, effective)
    val factoryDefault = saved.isNotBlank() && isFactoryLoopbackPushTarget(saved)
    val origin = when {
        matchesSaved || (factoryDefault && matchesEffective) -> TargetChoiceOrigin.ConfiguredDefault
        recentTargets.any { samePushTarget(it, current) } -> TargetChoiceOrigin.Recent
        else -> TargetChoiceOrigin.Current
    }
    return PushTargetSelection(
        address = current,
        effectiveDefault = effective,
        hasConfiguredDefault = saved.isNotBlank(),
        matchesConfiguredDefault = matchesSaved || (factoryDefault && matchesEffective),
        isFactoryDefault = factoryDefault,
        origin = origin
    )
}

/**
 * Keep the current address visible too, so its history entry can still be selected or removed.
 * Saving a default never creates a successful-push record.
 */
internal fun buildPushTargetChoices(
    recentTargets: List<String>
): List<PushTargetChoice> {
    val result = mutableListOf<PushTargetChoice>()
    recentTargets.forEach { address ->
        val normalized = address.trim()
        if (normalized.isBlank()) return@forEach
        if (result.any { samePushTarget(it.address, normalized) }) return@forEach
        result += PushTargetChoice(address = normalized, origin = TargetChoiceOrigin.Recent)
    }
    return result.take(3)
}

internal fun currentTargetLabel(selection: PushTargetSelection): String = when {
    !selection.hasConfiguredDefault -> when (selection.origin) {
        TargetChoiceOrigin.Recent -> "最近使用"
        else -> "当前使用"
    }
    selection.matchesConfiguredDefault && selection.isFactoryDefault -> "配置默认 · 自动适配"
    selection.matchesConfiguredDefault -> "配置默认"
    selection.origin == TargetChoiceOrigin.Recent -> "最近使用"
    else -> "当前使用"
}

internal fun samePushTarget(left: String, right: String): Boolean =
    normalizePushTarget(left) == normalizePushTarget(right)

internal fun normalizePushTarget(raw: String): String {
    return pushTargetAddressKey(raw)
}

internal fun summarizePushHost(raw: String): String = runCatching {
    URI(if (raw.contains("://")) raw else "http://$raw").let { uri ->
        uri.host?.let { host -> host + if (uri.port > 0) ":${uri.port}" else "" }
    }
}.getOrNull()?.takeIf { it.isNotBlank() } ?: raw.take(32).ifBlank { "未设置" }
