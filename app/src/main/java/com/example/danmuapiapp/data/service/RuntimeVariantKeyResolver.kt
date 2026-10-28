package com.example.danmuapiapp.data.service

import android.content.Context
import com.example.danmuapiapp.data.util.DotEnvCodec
import com.example.danmuapiapp.data.util.safeGetString
import java.io.File

/**
 * 已选核心变体的解析与规范化。
 *
 * 优先级保持为：`runtime` 偏好 → 旧版 `danmu_api_variant` 偏好 → 工作目录 `.env`，
 * 全部无效时回落到 [DEFAULT_KEY]。
 */
internal object RuntimeVariantKeyResolver {

    const val DEFAULT_KEY = "stable"

    private const val PREFS_RUNTIME = "runtime"
    private const val PREFS_LEGACY_RUNTIME_VARIANT = "danmu_api_variant"
    private const val KEY_RUNTIME_VARIANT = "variant"
    private const val ENV_VARIANT_KEY = "DANMU_API_VARIANT"

    fun selectedKey(context: Context, oldBase: File): String {
        val runtimePrefs = context.getSharedPreferences(PREFS_RUNTIME, Context.MODE_PRIVATE)
        val legacyPrefs =
            context.getSharedPreferences(PREFS_LEGACY_RUNTIME_VARIANT, Context.MODE_PRIVATE)
        val envFile = File(oldBase, "nodejs-project/config/.env")
        val envVariant = runCatching {
            if (!envFile.exists() || !envFile.isFile) {
                null
            } else {
                DotEnvCodec.parse(envFile.readText(Charsets.UTF_8))[ENV_VARIANT_KEY]
            }
        }.getOrNull()

        return resolve(
            runtimeVariant = if (runtimePrefs.contains(KEY_RUNTIME_VARIANT)) {
                runtimePrefs.safeGetString(KEY_RUNTIME_VARIANT)
            } else {
                null
            },
            legacyVariant = legacyPrefs.safeGetString(KEY_RUNTIME_VARIANT),
            envVariant = envVariant
        )
    }

    fun resolve(
        runtimeVariant: String?,
        legacyVariant: String?,
        envVariant: String?
    ): String {
        return sequenceOf(runtimeVariant, legacyVariant, envVariant)
            .mapNotNull(::normalizeKeyOrNull)
            .firstOrNull()
            ?: DEFAULT_KEY
    }

    fun normalizeKey(raw: String?): String {
        return normalizeKeyOrNull(raw) ?: DEFAULT_KEY
    }

    fun normalizeKeyOrNull(raw: String?): String? {
        return when (raw?.trim()?.lowercase()) {
            "stable" -> "stable"
            "dev", "develop", "development" -> "dev"
            "custom" -> "custom"
            else -> null
        }
    }
}
