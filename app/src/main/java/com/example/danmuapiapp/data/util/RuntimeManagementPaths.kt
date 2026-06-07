package com.example.danmuapiapp.data.util

/**
 * Builds candidate URL path prefixes for App-internal management endpoints.
 *
 * Order matters: admin-token path should be tried before runtime TOKEN paths so endpoints that
 * reveal privileged fields (for example real clientIp in /api/reqrecords) return the richest data.
 * A bare-path fallback is kept last for users who explicitly run the core without a TOKEN.
 */
internal object RuntimeManagementPaths {
    fun tokenPaths(
        runtimeTokenPaths: List<String>,
        adminMode: Boolean,
        adminToken: String,
        includeBareFallback: Boolean = true
    ): List<String> {
        val paths = linkedSetOf<String>()
        val normalizedAdminToken = RuntimeTokenNormalizer.normalizeInput(adminToken).trim('/')
        if (adminMode && normalizedAdminToken.isNotBlank()) {
            paths += "/$normalizedAdminToken"
        }
        runtimeTokenPaths
            .map(::normalizePath)
            .filter { it.isNotBlank() }
            .forEach(paths::add)
        if (includeBareFallback) {
            paths += ""
        }
        return paths.toList()
    }

    private fun normalizePath(raw: String): String {
        val normalized = RuntimeTokenNormalizer.normalizeInput(raw).trim('/')
        return if (normalized.isBlank()) "" else "/$normalized"
    }
}
