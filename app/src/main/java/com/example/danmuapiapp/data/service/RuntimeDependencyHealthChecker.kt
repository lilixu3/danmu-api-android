package com.example.danmuapiapp.data.service

import android.content.Context
import androidx.core.content.edit
import com.example.danmuapiapp.data.util.DotEnvCodec
import com.example.danmuapiapp.domain.model.ApiVariant
import java.io.File
import java.io.IOException

/** Lightweight startup check. It reads dependency package manifests only. */
object RuntimeDependencyHealthChecker {
    private const val PREFS_NAME = "runtime_dependency_health"
    private const val KEY_VARIANT = "variant"
    private const val KEY_MISSING = "missing"
    private const val KEY_DETECTED_AT = "detected_at"

    sealed interface Status {
        data object Ready : Status
        data object CoreUnavailable : Status
        data class Missing(
            val variant: ApiVariant,
            val dependencies: List<String>
        ) : Status
    }

    data class PendingIssue(
        val variant: ApiVariant,
        val missingDependencies: List<String>,
        val detectedAt: Long
    )

    fun inspectSelectedCore(
        context: Context,
        projectDir: File = RuntimePaths.normalProjectDir(context),
        preferredVariant: ApiVariant? = null
    ): Status {
        val variant = preferredVariant ?: resolveSelectedVariant(context, projectDir)
        val coreDir = File(projectDir, "danmu_api_${variant.key}")
        if (!NodeProjectManager.hasValidCore(coreDir)) return Status.CoreUnavailable

        val missing = NodeProjectManager.collectMissingRuntimeDepsForCore(
            coreDir = coreDir,
            runtimeNodeModulesDir = File(projectDir, "node_modules")
        )
        return if (missing.isEmpty()) {
            clearPendingIssue(context, variant)
            Status.Ready
        } else {
            recordPendingIssue(context, variant, missing)
            Status.Missing(variant, missing)
        }
    }

    fun readPendingIssue(context: Context): PendingIssue? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val variant = ApiVariant.entries.firstOrNull {
            it.key == prefs.getString(KEY_VARIANT, null)
        } ?: return null
        val missing = prefs.getString(KEY_MISSING, null)
            ?.lineSequence()
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.distinct()
            ?.sorted()
            ?.toList()
            .orEmpty()
        if (missing.isEmpty()) return null
        return PendingIssue(
            variant = variant,
            missingDependencies = missing,
            detectedAt = prefs.getLong(KEY_DETECTED_AT, 0L)
        )
    }

    fun clearPendingIssue(context: Context, variant: ApiVariant? = null) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (variant != null && prefs.getString(KEY_VARIANT, null) != variant.key) return
        prefs.edit(commit = true) { clear() }
    }

    fun missingMessage(missing: List<String>): String {
        return "运行时依赖缺失：${missing.joinToString(", ")}。请打开 App 修复依赖后重试"
    }

    private fun recordPendingIssue(
        context: Context,
        variant: ApiVariant,
        missing: List<String>
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit(commit = true) {
            putString(KEY_VARIANT, variant.key)
            putString(KEY_MISSING, missing.distinct().sorted().joinToString("\n"))
            putLong(KEY_DETECTED_AT, System.currentTimeMillis())
        }
    }

    private fun resolveSelectedVariant(context: Context, projectDir: File): ApiVariant {
        val envVariant = runCatching {
            val envFile = File(projectDir, "config/.env")
            if (!envFile.isFile) null else DotEnvCodec.parse(envFile.readText())["DANMU_API_VARIANT"]
        }.getOrNull()?.trim()
        val prefVariant = context.getSharedPreferences("runtime", Context.MODE_PRIVATE)
            .getString("variant", "stable")
            ?.trim()
        val key = envVariant?.takeIf(String::isNotBlank) ?: prefVariant
        return ApiVariant.entries.firstOrNull { it.key == key } ?: ApiVariant.Stable
    }
}

class RuntimeDependenciesMissingException(
    val variant: ApiVariant,
    val missingDependencies: List<String>
) : IOException(RuntimeDependencyHealthChecker.missingMessage(missingDependencies))
