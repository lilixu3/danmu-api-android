package com.example.danmuapiapp.data.repository

import java.io.File
import java.io.IOException

internal fun moveCoreDirectory(sourceDir: File, targetDir: File) {
    if (!sourceDir.exists()) return
    targetDir.parentFile?.let { parent ->
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("无法创建目录: ${parent.absolutePath}")
        }
    }
    if (sourceDir.renameTo(targetDir)) return
    if (targetDir.exists()) {
        targetDir.deleteRecursively()
    }
    copyDirectoryOrThrow(sourceDir, targetDir)
}

internal fun replaceCoreDirectory(targetDir: File, stagingDir: File): File? {
    if (!stagingDir.exists()) throw IOException("临时核心目录不存在")
    val parentDir = targetDir.parentFile ?: throw IOException("核心目录路径无效")
    var backupDir: File? = null

    if (targetDir.exists()) {
        backupDir = File(parentDir, "${targetDir.name}.backup-${System.currentTimeMillis()}")
        runCatching { backupDir.deleteRecursively() }
        moveCoreDirectory(targetDir, backupDir)
    }

    try {
        moveCoreDirectory(stagingDir, targetDir)
    } catch (error: Exception) {
        restoreCoreDirectory(targetDir, backupDir)
        throw error
    }
    return backupDir
}

internal fun restoreCoreDirectory(targetDir: File, backupDir: File?) {
    val backup = backupDir ?: return
    if (!backup.exists()) return
    runCatching { if (targetDir.exists()) targetDir.deleteRecursively() }
    moveCoreDirectory(backup, targetDir)
}
