package com.example.danmuapiapp.data.repository

import java.io.File
import java.io.IOException

internal object CoreStagingPolicy {
    const val OPERATION_MARKER_FILE = CoreArtifactPolicy.OPERATION_MARKER_FILE

    fun prepareStagingDirectory(targetDir: File, suffix: String, now: Long = System.currentTimeMillis()): File {
        val parentDir = targetDir.parentFile ?: throw IOException("核心目录路径无效")
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            throw IOException("无法创建核心目录: ${parentDir.absolutePath}")
        }
        val tempDir = File(parentDir, CoreArtifactPolicy.tempDirectoryName(targetDir, suffix, now))
        runCatching { tempDir.deleteRecursively() }
        if (!tempDir.mkdirs()) {
            throw IOException("无法创建临时目录: ${tempDir.absolutePath}")
        }
        return tempDir
    }

    fun writeOperationMarker(
        directory: File,
        operationId: Long,
        now: Long = System.currentTimeMillis()
    ) {
        CoreArtifactPolicy.markerFile(directory).writeText(
            "$operationId\n$now\n",
            Charsets.UTF_8
        )
    }

    fun clearOperationMarker(directory: File) {
        val marker = CoreArtifactPolicy.markerFile(directory)
        if (marker.exists() && !marker.delete()) {
            throw IOException("无法清理核心事务标记")
        }
    }
}
