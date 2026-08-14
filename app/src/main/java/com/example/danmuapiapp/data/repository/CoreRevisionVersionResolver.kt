package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.data.service.CoreVersionParser

internal object CoreRevisionVersionResolver {
    fun globalsFilePaths(): List<String> = listOf(
        "configs/globals.js",
        "config/globals.js",
        "globals.js",
        "danmu_api/configs/globals.js",
        "danmu_api/config/globals.js",
        "danmu_api/globals.js",
        "danmu-api/configs/globals.js",
        "danmu-api/config/globals.js",
        "danmu-api/globals.js"
    )

    fun globalsPaths(commitSha: String): List<String> =
        globalsFilePaths().map { "$commitSha/$it" }

    fun parseGlobalsVersion(content: String): String? {
        return CoreVersionParser.extractSourceVersion(content)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }
}
