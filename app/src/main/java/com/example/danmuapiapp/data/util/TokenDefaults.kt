package com.example.danmuapiapp.data.util

import android.content.Context
import android.content.SharedPreferences
import com.example.danmuapiapp.data.parser.EnvVarConfigLoader
import java.io.File

/**
 * Token 默认值解析器：优先用户配置，其次项目 .env，最后读取核心 envs.js 默认值。
 */
object TokenDefaults {

    private const val TOKEN_KEY = "token"
    private const val TOKEN_ENV_KEY = "TOKEN"
    private const val ASSET_ENV_PATH = "nodejs-project/config/.env"
    private const val FALLBACK_DEFAULT_TOKEN = "87654321"

    fun resolveTokenFromPrefs(
        prefs: SharedPreferences,
        context: Context,
        envFile: File? = null,
        key: String = TOKEN_KEY
    ): String {
        val prefToken = if (prefs.contains(key)) prefs.safeGetString(key).trim() else ""
        if (prefToken.isNotBlank()) return prefToken

        val envToken = resolveTokenFromEnvFile(envFile).orEmpty().trim()
        if (envToken.isNotBlank()) return envToken

        return resolveCoreDefaultToken(context)
    }

    fun resolveCoreDefaultToken(context: Context): String {
        val fromCoreConfig = runCatching {
            EnvVarConfigLoader.loadDefaultValue(context, TOKEN_ENV_KEY).orEmpty().trim()
        }.getOrNull().orEmpty()
        if (fromCoreConfig.isNotBlank()) return fromCoreConfig

        val fromAssetEnv = runCatching {
            context.assets.open(ASSET_ENV_PATH).bufferedReader(Charsets.UTF_8).useLines { lines ->
                parseTokenFromLines(lines)
            }
        }.getOrNull().orEmpty()
        if (fromAssetEnv.isNotBlank()) return fromAssetEnv

        return FALLBACK_DEFAULT_TOKEN
    }

    fun resolveTokenFromEnvFile(envFile: File?): String? {
        if (envFile == null || !envFile.exists() || !envFile.isFile) return null
        return runCatching {
            envFile.bufferedReader(Charsets.UTF_8).useLines { lines ->
                parseTokenFromLines(lines)
            }
        }.getOrNull()
    }

    private fun parseTokenFromLines(lines: Sequence<String>): String? {
        lines.forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val idx = line.indexOf('=')
            if (idx <= 0) return@forEach
            val key = line.substring(0, idx).trim()
            if (!key.equals(TOKEN_ENV_KEY, ignoreCase = true)) return@forEach
            var value = line.substring(idx + 1).trim()
            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                (value.startsWith("'") && value.endsWith("'"))
            ) {
                value = value.substring(1, value.length - 1)
            }
            return value.trim().ifBlank { null }
        }
        return null
    }
}
