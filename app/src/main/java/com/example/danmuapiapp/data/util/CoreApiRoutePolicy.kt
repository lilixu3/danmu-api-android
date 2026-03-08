package com.example.danmuapiapp.data.util

internal enum class CoreApiRouteMode {
    StandardRead,
    PreferAdminRead,
    AdminMutation
}

internal object CoreApiRoutePolicy {
    fun tokenPaths(
        runtimeToken: String,
        adminToken: String,
        isAdminMode: Boolean,
        mode: CoreApiRouteMode
    ): List<String> {
        val normalizedRuntimeToken = runtimeToken.trim().trim('/')
        val normalizedAdminToken = adminToken.trim().trim('/')
        val paths = linkedSetOf<String>()

        when (mode) {
            CoreApiRouteMode.StandardRead -> {
                if (normalizedRuntimeToken.isNotBlank()) {
                    paths += "/$normalizedRuntimeToken"
                }
                if (isAdminMode && normalizedAdminToken.isNotBlank()) {
                    paths += "/$normalizedAdminToken"
                }
            }

            CoreApiRouteMode.PreferAdminRead -> {
                if (isAdminMode && normalizedAdminToken.isNotBlank()) {
                    paths += "/$normalizedAdminToken"
                }
                if (normalizedRuntimeToken.isNotBlank()) {
                    paths += "/$normalizedRuntimeToken"
                }
            }

            CoreApiRouteMode.AdminMutation -> {
                if (isAdminMode && normalizedAdminToken.isNotBlank()) {
                    paths += "/$normalizedAdminToken"
                }
                if (normalizedRuntimeToken.isNotBlank()) {
                    paths += "/$normalizedRuntimeToken"
                }
            }
        }

        paths += ""
        return paths.toList()
    }
}
