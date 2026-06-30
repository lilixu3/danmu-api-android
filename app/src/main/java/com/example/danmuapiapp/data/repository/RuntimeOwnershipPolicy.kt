package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.data.service.RuntimeIdentityStore

internal enum class RuntimeOwnership {
    OwnedExact,
    OwnedLegacy,
    Unknown,
    Foreign
}

internal fun determineRuntimeOwnershipFromHealth(
    body: String,
    expectedIdentity: String,
    expectedHome: String
): RuntimeOwnership {
    val normalizedExpectedIdentity = expectedIdentity.trim()
    val actualIdentity = RuntimeIdentityStore.extractHealthIdentity(body).orEmpty().trim()
    if (actualIdentity.isNotBlank()) {
        return if (normalizedExpectedIdentity.isNotBlank() && actualIdentity == normalizedExpectedIdentity) {
            RuntimeOwnership.OwnedExact
        } else {
            RuntimeOwnership.Foreign
        }
    }

    val expectedHomeAliases = runtimeHomeAliases(expectedHome)
    if (expectedHomeAliases.isEmpty()) return RuntimeOwnership.Foreign

    val homes = listOf(
        extractHealthString(body, "resolvedHome"),
        extractHealthString(body, "envHome"),
        extractHealthString(body, "cwd")
    ).flatMap(::runtimeHomeAliases)

    return if (homes.any { it in expectedHomeAliases }) {
        RuntimeOwnership.OwnedLegacy
    } else {
        RuntimeOwnership.Foreign
    }
}

internal fun isRuntimeOwnershipOwned(ownership: RuntimeOwnership): Boolean {
    return ownership == RuntimeOwnership.OwnedExact || ownership == RuntimeOwnership.OwnedLegacy
}

internal fun normalizeRuntimeHome(path: String?): String {
    val trimmed = path?.trim().orEmpty().replace('\\', '/')
    if (trimmed.isBlank()) return ""
    return trimmed.trimEnd('/').ifBlank { "/" }
}

private fun runtimeHomeAliases(path: String?): Set<String> {
    val normalized = normalizeRuntimeHome(path)
    if (normalized.isBlank()) return emptySet()
    val aliases = linkedSetOf(normalized)
    if (normalized.startsWith("/data/user/0/")) {
        aliases.add("/data/data/" + normalized.removePrefix("/data/user/0/"))
    } else if (normalized.startsWith("/data/data/")) {
        aliases.add("/data/user/0/" + normalized.removePrefix("/data/data/"))
    }
    return aliases
}

internal fun extractHealthString(body: String, fieldName: String): String? {
    return Regex("\"${Regex.escape(fieldName)}\"\\s*:\\s*\"([^\"]*)\"")
        .find(body)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
}
