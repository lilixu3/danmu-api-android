package com.example.danmuapiapp.data.util

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Request

data class RuntimeApiAccess(
    val port: Int,
    val runtimeToken: String,
    val tokenPaths: List<String>,
    val expectedSessionId: String = ""
)

internal object RuntimeApiAccessResolver {
    fun resolve(
        context: Context,
        prefs: SharedPreferences,
        defaultPort: Int
    ): RuntimeApiAccess {
        val port = prefs.getInt("port", defaultPort).coerceIn(1, 65535)
        val runtimeToken = RuntimeTokenNormalizer.normalizeInput(
            TokenDefaults.resolveTokenFromPrefs(prefs, context)
        )
        val tokenPaths = linkedSetOf<String>()
        if (runtimeToken.isNotBlank()) {
            tokenPaths += "/$runtimeToken"
        }
        return RuntimeApiAccess(
            port = port,
            runtimeToken = runtimeToken,
            tokenPaths = tokenPaths.toList()
        )
    }
}

internal fun Request.Builder.applyRuntimeApiAuth(access: RuntimeApiAccess): Request.Builder {
    if (access.expectedSessionId.isNotBlank()) header("X-Danmu-App-Session", access.expectedSessionId)
    if (access.runtimeToken.isNotBlank()) {
        header("Authorization", "Bearer ${access.runtimeToken}")
    }
    return this
}

/**
 * Loopback control endpoints remain reachable without a path Token.
 *
 * Runtime scripts only strip the Token path when it exactly matches process.env.TOKEN.
 * A service started without TOKEN therefore returns 404 for the Token-prefixed internal
 * route while the bare route still works. Keep the Token path first for consistency, then
 * let the caller fall back to the bare route after a prefix-mismatch response.
 */
internal fun RuntimeApiAccess.localPathCandidates(): List<String> {
    val paths = linkedSetOf<String>()
    tokenPaths.forEach { rawPath ->
        val token = rawPath.trim().trim('/')
        if (token.isNotEmpty()) paths += "/$token"
    }
    paths += ""
    return paths.toList()
}
