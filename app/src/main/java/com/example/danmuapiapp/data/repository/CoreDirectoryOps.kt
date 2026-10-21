package com.example.danmuapiapp.data.repository

import java.io.File
import java.io.IOException

internal fun copyDirectoryOrThrow(
    sourceDir: File,
    targetDir: File,
    copyBlock: (File, File) -> Boolean = { src, dst -> src.copyRecursively(dst, overwrite = true) },
    cleanupBlock: (File) -> Boolean = { dir -> dir.deleteRecursively() }
) {
    val copied = copyBlock(sourceDir, targetDir)
    if (!copied) {
        runCatching { if (targetDir.exists()) targetDir.deleteRecursively() }
        throw IOException("复制目录失败: ${sourceDir.absolutePath} -> ${targetDir.absolutePath}")
    }
    if (!cleanupBlock(sourceDir)) {
        throw IOException("无法清理目录: ${sourceDir.absolutePath}")
    }
}

internal fun replaceInstalledCoreDependencies(
    stagingCoreDir: File,
    installedCoreDir: File,
    afterInstall: () -> Unit = {},
    verifyInstalled: () -> Unit
) {
    val stagedNodeModules = File(stagingCoreDir, "node_modules")
    if (!stagedNodeModules.isDirectory) {
        throw IOException("待安装依赖缺少 node_modules 目录")
    }
    if (!installedCoreDir.isDirectory) {
        throw IOException("当前核心目录不存在")
    }

    val targetNodeModules = File(installedCoreDir, "node_modules")
    val backupNodeModules = File(
        installedCoreDir,
        ".node_modules-backup-${System.currentTimeMillis()}"
    )
    val metadataNames = listOf(
        RuntimeDependencyPackProtocol.INSTALLED_MANIFEST_FILE,
        LocalRuntimeDependencyArchiveImporter.LOCAL_IMPORT_AUDIT_FILE,
        RuntimeDependencyPackProtocol.LEGACY_INSTALLED_LOCK_FILE
    )
    val previousMetadata = metadataNames.associateWith { name ->
        File(installedCoreDir, name).takeIf { it.isFile }?.readBytes()
    }

    fun moveDirectory(source: File, target: File) {
        if (source.renameTo(target)) return
        copyDirectoryOrThrow(source, target)
    }

    var originalMoved = false
    var keepBackup = false
    try {
        if (targetNodeModules.exists()) {
            runCatching { backupNodeModules.deleteRecursively() }
            moveDirectory(targetNodeModules, backupNodeModules)
            originalMoved = true
        }
        moveDirectory(stagedNodeModules, targetNodeModules)

        metadataNames.forEach { name ->
            runCatching { File(installedCoreDir, name).delete() }
            val stagedMetadata = File(stagingCoreDir, name)
            if (stagedMetadata.isFile) {
                stagedMetadata.copyTo(File(installedCoreDir, name), overwrite = true)
            }
        }
        verifyInstalled()
        afterInstall()
        runCatching { backupNodeModules.deleteRecursively() }
    } catch (error: Exception) {
        runCatching { targetNodeModules.deleteRecursively() }
        if (originalMoved && backupNodeModules.exists()) {
            runCatching { moveDirectory(backupNodeModules, targetNodeModules) }
                .onFailure { restoreError ->
                    keepBackup = true
                    error.addSuppressed(restoreError)
                }
        }
        metadataNames.forEach { name ->
            val target = File(installedCoreDir, name)
            runCatching { target.delete() }
            previousMetadata[name]?.let { bytes ->
                runCatching { target.writeBytes(bytes) }
            }
        }
        throw error
    } finally {
        if (!keepBackup) {
            runCatching { backupNodeModules.deleteRecursively() }
        }
    }
}

internal fun cleanupStaleCoreDirectories(
    projectDir: File,
    activeBackupPath: String?,
    now: Long = System.currentTimeMillis()
) {
    if (!projectDir.isDirectory) return
    val candidates = buildList {
        addAll(projectDir.listFiles().orEmpty().filter { it.isDirectory })
        projectDir.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("danmu_api_") }
            .forEach { coreDir -> addAll(coreDir.listFiles().orEmpty().filter { it.isDirectory }) }
    }
    candidates.distinctBy { it.absolutePath }.forEach { directory ->
        if (activeBackupPath != null &&
            runCatching { directory.canonicalPath }.getOrNull() == activeBackupPath
        ) return@forEach
        if (!CoreArtifactPolicy.isKnownArtifact(directory.name)) return@forEach
        val marker = CoreArtifactPolicy.markerFile(directory)
        if (CoreArtifactPolicy.isExpired(
                CoreArtifactPolicy.markerCreatedAt(directory),
                now,
                hasMarker = marker.isFile
            )
        ) {
            runCatching { directory.deleteRecursively() }
        }
    }
}
