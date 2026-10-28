package com.example.danmuapiapp.data.service

import android.content.Context
import org.json.JSONArray

internal fun normalizeRuntimeAssetPath(raw: String): String {
    val normalized = raw.trim().replace('\\', '/')
    require(normalized.isNotEmpty()) { "运行时资产路径不能为空" }
    require(!normalized.startsWith('/')) { "运行时资产必须使用相对路径：$raw" }
    val segments = normalized.split('/')
    require(segments.none { it.isEmpty() || it == "." || it == ".." }) {
        "运行时资产路径不能越界：$raw"
    }
    return segments.joinToString("/")
}

internal object AppRuntimeAssets {
    private const val PROJECT_ASSET_ROOT = "nodejs-project"
    private const val MANIFEST_NAME = "app-runtime-assets.json"

    fun relativePaths(context: Context): List<String> {
        val array = JSONArray(
            context.assets.open("$PROJECT_ASSET_ROOT/$MANIFEST_NAME")
                .bufferedReader()
                .use { it.readText() }
        )
        val entries = (0 until array.length())
            .map { normalizeRuntimeAssetPath(array.getString(it)) }
            .distinct()
        require(entries.isNotEmpty()) { "运行时资产清单不能为空" }
        require(entries.none { it == MANIFEST_NAME }) { "运行时资产清单不能包含自身" }
        val missing = entries.filter {
            runCatching { context.assets.open("$PROJECT_ASSET_ROOT/$it").close() }.isFailure
        }
        require(missing.isEmpty()) { "运行时资产缺失：${missing.joinToString()}" }
        return entries
    }

    fun names(context: Context): List<String> = listOf(MANIFEST_NAME) + relativePaths(context)
}
