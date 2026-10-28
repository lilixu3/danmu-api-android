package com.example.danmuapiapp.data.repository

import java.io.File

internal object CoreDirWatchPolicy {
    private val ignoredTopLevelPaths = setOf(
        ".app_version",
        "danmu_api_stable",
        "danmu_api_dev",
        "danmu_api_custom",
        "logs",
        ".cache",
        "node_modules"
    )

    fun relativePath(rootDir: File, target: File): String {
        return runCatching {
            target.canonicalFile.relativeTo(rootDir.canonicalFile).path.replace('\\', '/')
        }.getOrElse {
            target.name
        }
    }

    fun shouldIgnore(rootDir: File, target: File): Boolean {
        val relative = relativePath(rootDir, target)
        if (relative == "." || relative.isBlank()) return false
        val topLevel = relative.substringBefore('/')
        return topLevel in ignoredTopLevelPaths
    }
}
