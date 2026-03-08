package com.example.danmuapiapp.data.util

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Request

internal data class RuntimeApiAccess(
    val port: Int,
    val runtimeToken: String,
    val tokenPaths: List<String>
)

internal object RuntimeApiAccessResolver {
    fun resolve(
        context: Context,
        prefs: SharedPreferences,
        defaultPort: Int
    ): RuntimeApiAccess {
        val port = prefs.getInt("port", defaultPort).coerceIn(1, 65535)
        val runtimeToken = TokenDefaults.resolveTokenFromPrefs(prefs, context).trim()
        val tokenPaths = linkedSetOf<String>()
        if (runtimeToken.isNotBlank()) {
            tokenPaths += "/$runtimeToken"
        }
        tokenPaths += ""
        return RuntimeApiAccess(
            port = port,
            runtimeToken = runtimeToken,
            tokenPaths = tokenPaths.toList()
        )
    }
}

internal fun Request.Builder.applyRuntimeApiAuth(access: RuntimeApiAccess): Request.Builder {
    if (access.runtimeToken.isNotBlank()) {
        header("Authorization", "Bearer ${access.runtimeToken}")
    }
    return this
}
