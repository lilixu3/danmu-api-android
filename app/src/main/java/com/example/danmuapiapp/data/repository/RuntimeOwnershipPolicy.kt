package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.data.service.RuntimeIdentityStore

internal enum class RuntimeOwnership {
    OwnedExact,
    OwnedLegacy,
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

    val normalizedExpectedHome = normalizeRuntimeHome(expectedHome)
    if (normalizedExpectedHome.isBlank()) return RuntimeOwnership.Foreign

    val homes = listOf(
        extractHealthString(body, "resolvedHome"),
        extractHealthString(body, "envHome"),
        extractHealthString(body, "cwd")
    ).map(::normalizeRuntimeHome)

    return if (homes.any { it.isNotBlank() && it == normalizedExpectedHome }) {
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

internal fun extractHealthString(body: String, fieldName: String): String? {
    return Regex("\"${Regex.escape(fieldName)}\"\\s*:\\s*\"([^\"]*)\"")
        .find(body)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
}
