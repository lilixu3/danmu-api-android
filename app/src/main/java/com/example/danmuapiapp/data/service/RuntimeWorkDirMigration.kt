package com.example.danmuapiapp.data.service

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * 工作目录迁移事务。
 *
 * 迁移只搬运“当前选中的核心源码 + 配置 + 收藏缓存”，依赖、日志与运行缓存留在原目录，
 * 由目标目录重新生成或修复。任何一步失败都必须把目标目录还原到迁移前的状态。
 */
internal object RuntimeWorkDirMigration {

    private val CORE_MIGRATION_EXCLUDED_NAMES = setOf(
        "node_modules",
        ".cache",
        "logs",
        "bangumi-data-cache",
        "bangumi-data-cache.json"
    )

    fun migrateSelectedCoreAndConfig(
        oldBase: File,
        newBase: File,
        selectedVariantKey: String
    ) {
        performTransaction(
            oldBase = oldBase,
            newBase = newBase,
            selectedVariantKey = selectedVariantKey,
            commitPreference = { true }
        )
    }

    fun performTransaction(
        oldBase: File,
        newBase: File,
        selectedVariantKey: String,
        commitPreference: () -> Boolean
    ) {
        val receipt = prepareSelectedCoreAndConfigMigration(
            oldBase = oldBase,
            newBase = newBase,
            selectedVariantKey = selectedVariantKey
        )
        try {
            if (!commitPreference()) {
                throw IOException("无法保存工作目录设置")
            }
            receipt.commit()
        } catch (error: Exception) {
            runCatching { receipt.rollback() }
                .exceptionOrNull()
                ?.let(error::addSuppressed)
            throw error
        }
    }

    private class WorkDirMigrationReceipt(
        private val targetConfig: File?,
        private val configBackup: File?,
        private val configApplied: Boolean,
        private val targetCore: File?,
        private val coreBackup: File?,
        private val coreApplied: Boolean,
        private val targetFavorites: File?,
        private val favoritesBackup: File?,
        private val favoritesApplied: Boolean
    ) {
        fun commit() {
            listOfNotNull(configBackup, coreBackup, favoritesBackup).forEach { backup ->
                runCatching { backup.deleteRecursively() }
            }
        }

        fun rollback() {
            val errors = mutableListOf<Throwable>()
            rollbackTarget(targetFavorites, favoritesBackup, favoritesApplied, "收藏", errors)
            rollbackTarget(targetCore, coreBackup, coreApplied, "核心", errors)
            rollbackTarget(targetConfig, configBackup, configApplied, "配置", errors)
            if (errors.isNotEmpty()) {
                throw IOException("工作目录迁移回滚不完整").also { error ->
                    errors.forEach(error::addSuppressed)
                }
            }
        }

        private fun rollbackTarget(
            target: File?,
            backup: File?,
            applied: Boolean,
            label: String,
            errors: MutableList<Throwable>
        ) {
            if (target == null) return
            runCatching {
                if (backup?.exists() == true) {
                    if (target.exists() && !target.deleteRecursively()) {
                        throw IOException("无法移除未完成的目标$label：${target.absolutePath}")
                    }
                    if (!backup.renameTo(target)) {
                        throw IOException("无法恢复目标目录原有$label：${target.absolutePath}")
                    }
                } else if (applied && target.exists() && !target.deleteRecursively()) {
                    throw IOException("无法撤销迁入$label：${target.absolutePath}")
                }
            }.onFailure(errors::add)
        }
    }

    private fun prepareSelectedCoreAndConfigMigration(
        oldBase: File,
        newBase: File,
        selectedVariantKey: String
    ): WorkDirMigrationReceipt {
        val oldRoot = File(oldBase, "nodejs-project")
        if (!oldRoot.exists()) {
            return WorkDirMigrationReceipt(
                null, null, false,
                null, null, false,
                null, null, false
            )
        }
        if (!oldRoot.isDirectory) {
            throw IllegalStateException("原工作目录不是有效目录：${oldRoot.absolutePath}")
        }

        val newRoot = File(newBase, "nodejs-project")
        if (!newRoot.exists() && !newRoot.mkdirs()) {
            throw IllegalStateException("无法创建目标运行目录：${newRoot.absolutePath}")
        }
        val token = "${System.currentTimeMillis()}-${System.nanoTime()}"
        val sourceConfig = File(oldRoot, "config")
        val targetConfig = File(newRoot, "config")
        val stagedConfig = File(newRoot, ".config-migration-$token")
        val coreName = "danmu_api_${RuntimeVariantKeyResolver.normalizeKey(selectedVariantKey)}"
        val sourceCore = File(oldRoot, coreName)
        val targetCore = File(newRoot, coreName)
        val stagedCore = File(newRoot, ".$coreName-migration-$token")
        val configBackup = File(newRoot, ".config-backup-$token")
        val coreBackup = File(newRoot, ".$coreName-backup-$token")
        val sourceFavorites = File(oldRoot, ".cache/${FavoriteCacheStore.FILE_NAME}")
        val targetFavorites = File(newRoot, ".cache/${FavoriteCacheStore.FILE_NAME}")
        val stagedFavorites = File(newRoot, ".favorites-migration-$token")
        val favoritesBackup = File(newRoot, ".favorites-backup-$token")

        fun cleanStaging() {
            listOf(stagedConfig, stagedCore, stagedFavorites).forEach { artifact ->
                runCatching { artifact.deleteRecursively() }
            }
        }

        cleanStaging()
        listOf(configBackup, coreBackup, favoritesBackup).forEach { artifact ->
            runCatching { artifact.deleteRecursively() }
        }
        var replaceConfig = false
        var replaceCore = false
        var replaceFavorites = false
        var configApplied = false
        var coreApplied = false
        var favoritesApplied = false
        try {
            if (targetConfig.isDirectory) {
                copyDirectoryStrict(targetConfig, stagedConfig)
                replaceConfig = true
            }
            if (sourceConfig.isDirectory) {
                if (!stagedConfig.exists() && !stagedConfig.mkdirs()) {
                    throw IllegalStateException("无法创建配置迁移目录")
                }
                copyDirectoryStrict(sourceConfig, stagedConfig, overwrite = false)
                replaceConfig = true
            }

            // 只迁移当前核心源码；依赖、日志和运行缓存由目标目录重新生成或修复。
            if (sourceCore.isDirectory && !File(targetCore, "worker.js").isFile) {
                copyDirectoryStrict(
                    source = sourceCore,
                    target = stagedCore,
                    skipTopLevelNames = CORE_MIGRATION_EXCLUDED_NAMES
                )
                if (!File(stagedCore, "worker.js").isFile) {
                    throw IllegalStateException("迁移后的核心缺少 worker.js")
                }
                replaceCore = true
            }

            if (sourceFavorites.isFile) {
                val targetRaw = if (targetFavorites.isFile) {
                    targetFavorites.readText(Charsets.UTF_8)
                } else {
                    "{}"
                }
                val merged = FavoriteCacheStore.mergeDocuments(
                    preferredRaw = sourceFavorites.readText(Charsets.UTF_8),
                    secondaryRaw = targetRaw
                )
                stagedFavorites.writeText(
                    FavoriteCacheStore.snapshotOf(merged).content,
                    Charsets.UTF_8
                )
                replaceFavorites = true
            }

            if (replaceConfig) {
                if (targetConfig.exists() && !targetConfig.renameTo(configBackup)) {
                    throw IllegalStateException("无法备份目标配置")
                }
                if (!stagedConfig.renameTo(targetConfig)) {
                    configBackup.renameTo(targetConfig)
                    throw IllegalStateException("无法应用迁移配置")
                }
                configApplied = true
            }
            if (replaceCore) {
                if (targetCore.exists() && !targetCore.renameTo(coreBackup)) {
                    throw IllegalStateException("无法备份目标核心")
                }
                if (!stagedCore.renameTo(targetCore)) {
                    coreBackup.renameTo(targetCore)
                    throw IllegalStateException("无法应用迁移核心")
                }
                coreApplied = true
            }
            if (replaceFavorites) {
                val favoritesDir = targetFavorites.parentFile
                    ?: throw IllegalStateException("目标收藏目录无效")
                if (!favoritesDir.exists() && !favoritesDir.mkdirs()) {
                    throw IllegalStateException("无法创建目标收藏目录")
                }
                if (targetFavorites.exists() && !targetFavorites.renameTo(favoritesBackup)) {
                    throw IllegalStateException("无法备份目标收藏")
                }
                if (!stagedFavorites.renameTo(targetFavorites)) {
                    favoritesBackup.renameTo(targetFavorites)
                    throw IllegalStateException("无法应用迁移收藏")
                }
                favoritesApplied = true
            }
            return WorkDirMigrationReceipt(
                targetConfig = targetConfig.takeIf { replaceConfig },
                configBackup = configBackup.takeIf { it.exists() },
                configApplied = configApplied,
                targetCore = targetCore.takeIf { replaceCore },
                coreBackup = coreBackup.takeIf { it.exists() },
                coreApplied = coreApplied,
                targetFavorites = targetFavorites.takeIf { replaceFavorites },
                favoritesBackup = favoritesBackup.takeIf { it.exists() },
                favoritesApplied = favoritesApplied
            )
        } catch (error: Exception) {
            val rollbackErrors = mutableListOf<String>()
            if (favoritesBackup.exists()) {
                if (targetFavorites.exists() && !targetFavorites.deleteRecursively()) {
                    rollbackErrors += "无法移除未完成的目标收藏"
                } else if (!favoritesBackup.renameTo(targetFavorites)) {
                    rollbackErrors += "无法恢复收藏备份：${favoritesBackup.absolutePath}"
                }
            } else if (favoritesApplied) {
                if (targetFavorites.exists() && !targetFavorites.deleteRecursively()) {
                    rollbackErrors += "无法撤销新收藏"
                }
            }
            if (configBackup.exists()) {
                if (targetConfig.exists() && !targetConfig.deleteRecursively()) {
                    rollbackErrors += "无法移除未完成的目标配置"
                } else if (!configBackup.renameTo(targetConfig)) {
                    rollbackErrors += "无法恢复配置备份：${configBackup.absolutePath}"
                }
            } else if (configApplied) {
                if (targetConfig.exists() && !targetConfig.deleteRecursively()) {
                    rollbackErrors += "无法撤销新配置：${targetConfig.absolutePath}"
                }
            }
            if (coreBackup.exists()) {
                if (targetCore.exists() && !targetCore.deleteRecursively()) {
                    rollbackErrors += "无法移除未完成的目标核心"
                } else if (!coreBackup.renameTo(targetCore)) {
                    rollbackErrors += "无法恢复核心备份：${coreBackup.absolutePath}"
                }
            } else if (coreApplied) {
                if (targetCore.exists() && !targetCore.deleteRecursively()) {
                    rollbackErrors += "无法撤销新核心：${targetCore.absolutePath}"
                }
            }
            if (rollbackErrors.isNotEmpty()) {
                throw IllegalStateException(
                    "${error.message ?: "迁移失败"}；${rollbackErrors.joinToString("；")}",
                    error
                )
            }
            throw error
        } finally {
            cleanStaging()
        }
    }

    private fun copyDirectoryStrict(
        source: File,
        target: File,
        overwrite: Boolean = true,
        skipTopLevelNames: Set<String> = emptySet()
    ) {
        if (!source.isDirectory) return
        if (!target.exists() && !target.mkdirs()) {
            throw IllegalStateException("无法创建目录：${target.absolutePath}")
        }
        val children = source.listFiles()
            ?: throw IllegalStateException("无法读取目录：${source.absolutePath}")
        children.forEach { child ->
            if (child.name in skipTopLevelNames) return@forEach
            val destination = File(target, child.name)
            if (child.isDirectory) {
                copyDirectoryStrict(child, destination, overwrite = overwrite)
            } else if (overwrite || !destination.exists()) {
                destination.parentFile?.let { parent ->
                    if (!parent.exists() && !parent.mkdirs()) {
                        throw IllegalStateException("无法创建目录：${parent.absolutePath}")
                    }
                }
                copyFileStrictly(child, destination, overwrite)
            }
        }
    }

    private fun copyFileStrictly(source: File, target: File, overwrite: Boolean) {
        if (!source.isFile) {
            throw IOException("无法读取文件：${source.absolutePath}")
        }
        if (target.exists() && !overwrite) return
        val parent = target.parentFile
            ?: throw IOException("目标文件路径无效：${target.absolutePath}")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("无法创建目录：${parent.absolutePath}")
        }

        val temporary = File(parent, ".${target.name}.copy-${UUID.randomUUID()}")
        try {
            source.inputStream().buffered().use { input ->
                FileOutputStream(temporary).buffered().use { output -> input.copyTo(output) }
            }
            if (!temporary.isFile || temporary.length() != source.length()) {
                throw IOException("文件复制校验失败：${source.absolutePath}")
            }
            if (target.exists() && !target.delete()) {
                throw IOException("无法替换目标文件：${target.absolutePath}")
            }
            if (!temporary.renameTo(target)) {
                throw IOException("无法应用复制文件：${target.absolutePath}")
            }
        } finally {
            if (temporary.exists()) {
                runCatching { temporary.delete() }
            }
        }
    }
}
