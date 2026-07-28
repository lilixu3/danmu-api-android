package com.example.danmuapiapp.data.service

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.danmuapiapp.data.util.DotEnvCodec
import com.example.danmuapiapp.data.util.safeGetString
import com.example.danmuapiapp.domain.model.RunMode
import android.Manifest
import android.content.pm.PackageManager
import java.io.File

@SuppressLint("ApplySharedPref")
object RuntimePaths {

    enum class WorkDirSwitchMode {
        SwitchOnly,
        MigrateSelectedCore
    }

    private const val PREFS_WORK_DIR = "danmu_work_dir"
    private const val KEY_CUSTOM_BASE_PATH = "custom_path"
    private const val KEY_CUSTOM_BASE_URI = "custom_uri"
    private const val PREFS_RUNTIME = "runtime"
    private const val PREFS_LEGACY_RUNTIME_VARIANT = "danmu_api_variant"
    private const val KEY_RUNTIME_VARIANT = "variant"
    private const val ROOT_RUNTIME_BASE = "/data/adb/danmuapi_runtime"

    data class WorkDirInfo(
        val runMode: RunMode,
        val currentBaseDir: File,
        val normalBaseDir: File,
        val defaultBaseDir: File,
        val customBaseDir: File?,
        val rootBaseDir: File,
        val isCustomEnabled: Boolean
    )

    data class ApplyResult(
        val ok: Boolean,
        val message: String
    )

    fun currentRunMode(context: Context): RunMode {
        return RuntimeModePrefs.get(context)
    }

    fun defaultBaseDir(context: Context): File = context.filesDir

    fun readCustomBasePath(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_WORK_DIR, Context.MODE_PRIVATE)
        val raw = prefs.safeGetString(KEY_CUSTOM_BASE_PATH).trim()
        if (raw.isNotBlank()) return raw
        val uriText = prefs.safeGetString(KEY_CUSTOM_BASE_URI).trim()
        if (uriText.isBlank()) return null
        val uri = runCatching { uriText.toUri() }.getOrNull() ?: return null
        return resolveTreeUriToPath(uri)
    }

    fun readCustomBaseDir(context: Context): File? {
        val raw = readCustomBasePath(context) ?: return null
        return normalizeBaseDir(File(raw))
    }

    fun normalBaseDir(context: Context): File {
        return readCustomBaseDir(context) ?: defaultBaseDir(context)
    }

    fun rootBaseDir(context: Context): File {
        // Root 运行目录跟随当前安装包名，避免改 applicationId 后读写到旧目录。
        return File(ROOT_RUNTIME_BASE, context.packageName)
    }

    fun currentBaseDir(context: Context): File {
        return if (currentRunMode(context) != RunMode.Normal) {
            rootBaseDir(context)
        } else {
            normalBaseDir(context)
        }
    }

    fun projectDir(context: Context): File = File(normalBaseDir(context), "nodejs-project")

    fun projectDir(context: Context, mode: RunMode): File {
        val base = if (mode != RunMode.Normal) rootBaseDir(context) else normalBaseDir(context)
        return File(base, "nodejs-project")
    }

    fun normalProjectDir(context: Context): File = File(normalBaseDir(context), "nodejs-project")

    fun rootProjectDir(context: Context): File = File(rootBaseDir(context), "nodejs-project")

    fun isCustomEnabled(context: Context): Boolean = readCustomBasePath(context) != null

    fun clearCustomBasePath(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_WORK_DIR, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CUSTOM_BASE_PATH)
            .remove(KEY_CUSTOM_BASE_URI)
            .commit()
    }

    fun setCustomBasePath(context: Context, path: String?): Boolean {
        val prefs = context.getSharedPreferences(PREFS_WORK_DIR, Context.MODE_PRIVATE)
        val value = path?.trim().orEmpty()
        val editor = prefs.edit()
        if (value.isBlank()) {
            editor.remove(KEY_CUSTOM_BASE_PATH)
        } else {
            editor.putString(KEY_CUSTOM_BASE_PATH, value)
        }
        return editor.commit()
    }

    fun buildWorkDirInfo(context: Context): WorkDirInfo {
        val mode = currentRunMode(context)
        val custom = readCustomBaseDir(context)
        val normal = normalBaseDir(context)
        val root = rootBaseDir(context)
        val current = if (mode != RunMode.Normal) root else normal
        return WorkDirInfo(
            runMode = mode,
            currentBaseDir = current,
            normalBaseDir = normal,
            defaultBaseDir = defaultBaseDir(context),
            customBaseDir = custom,
            rootBaseDir = root,
            isCustomEnabled = custom != null
        )
    }

    fun applyCustomBaseDir(
        context: Context,
        targetPath: String?,
        switchMode: WorkDirSwitchMode = WorkDirSwitchMode.SwitchOnly
    ): ApplyResult {
        if (currentRunMode(context) != RunMode.Normal) {
            return ApplyResult(false, "高权限模式工作目录固定在 ${rootBaseDir(context).absolutePath}")
        }

        val oldCustom = readCustomBasePath(context)
        val oldBase = normalBaseDir(context)
        val defaultBase = defaultBaseDir(context)

        val normalizedTarget = if (targetPath.isNullOrBlank()) {
            defaultBase
        } else {
            normalizeBaseDir(File(targetPath))
        }

        if (!ensureDirWritable(normalizedTarget)) {
            return ApplyResult(false, "目录不可用或无写入权限：${normalizedTarget.absolutePath}")
        }

        val targetCanonical = runCatching { normalizedTarget.canonicalFile }.getOrElse { normalizedTarget }
        val defaultCanonical = runCatching { defaultBase.canonicalFile }.getOrElse { defaultBase }
        val shouldUseDefault = targetCanonical == defaultCanonical
        val targetProjectDir = File(targetCanonical, "nodejs-project")
        val targetCacheDir = File(targetProjectDir, ".cache")
        if (!ensureDirWritable(targetProjectDir) || !ensureDirWritable(targetCacheDir)) {
            return ApplyResult(false, "目录不可用或无写入权限：${targetCacheDir.absolutePath}")
        }

        val oldCanonical = runCatching { oldBase.canonicalFile }.getOrElse { oldBase }
        val sameDir = oldCanonical == targetCanonical
        val selectedVariantKey = selectedRuntimeVariantKey(context, oldBase)
        if (sameDir) {
            if (shouldUseDefault && oldCustom != null) {
                return if (clearCustomBasePath(context)) {
                    ApplyResult(true, "已恢复为默认目录")
                } else {
                    ApplyResult(false, "无法保存工作目录设置")
                }
            }
            if (!shouldUseDefault && oldCustom == targetCanonical.absolutePath) {
                return ApplyResult(true, "已是当前目录")
            }
        }

        return try {
            if (!sameDir && switchMode == WorkDirSwitchMode.MigrateSelectedCore) {
                migrateSelectedCoreAndConfig(
                    oldBase = oldBase,
                    newBase = targetCanonical,
                    selectedVariantKey = selectedVariantKey
                )
            }

            val preferenceSaved = if (shouldUseDefault) {
                clearCustomBasePath(context)
            } else {
                setCustomBasePath(context, targetCanonical.absolutePath)
            }
            if (!preferenceSaved) {
                throw IllegalStateException("无法保存工作目录设置")
            }

            val suffix = if (!sameDir && switchMode == WorkDirSwitchMode.MigrateSelectedCore) {
                "，已迁移当前核心（依赖将在使用前检测）"
            } else {
                ""
            }
            ApplyResult(
                true,
                (if (shouldUseDefault) "已恢复为默认目录" else "已切换工作目录") + suffix
            )
        } catch (e: Exception) {
            ApplyResult(false, "切换失败：${e.message}")
        }
    }

    fun resolveTreeUriToPath(uri: Uri): String? {
        return runCatching {
            if (!isTreeUriCompat(uri)) return@runCatching null
            val docId = DocumentsContract.getTreeDocumentId(uri)
            if (docId.isBlank()) return@runCatching null

            if (docId.startsWith("raw:")) {
                val rawPath = docId.removePrefix("raw:").trim()
                if (rawPath.isNotBlank()) return@runCatching File(rawPath).canonicalPath
            }

            val parts = docId.split(":", limit = 2)
            val volume = parts.getOrNull(0).orEmpty()
            val rel = parts.getOrNull(1).orEmpty()

            val base = if (volume.equals("primary", ignoreCase = true)) {
                Environment.getExternalStorageDirectory().absolutePath
            } else {
                "/storage/$volume"
            }

            val fullPath = if (rel.isBlank()) base else File(base, rel).path
            File(fullPath).canonicalPath
        }.getOrNull()
    }

    fun isAllFilesAccessGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val readGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            val writeGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            readGranted && writeGranted
        }
    }

    fun needsAllFilesAccess(context: Context, targetPath: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        if (isWithinAppExternal(context, targetPath)) return false
        return true
    }

    fun normalizeBaseDir(path: String): File = normalizeBaseDir(File(path))

    private fun normalizeBaseDir(input: File): File {
        val canonical = runCatching { input.canonicalFile }.getOrElse { input }
        var mapped = canonical
        val path = canonical.path
        if (path == "/data/media/0") {
            mapped = File("/storage/emulated/0")
        } else if (path.startsWith("/data/media/0/")) {
            mapped = File("/storage/emulated/0" + path.removePrefix("/data/media/0"))
        }
        if (mapped.name == "nodejs-project") {
            return mapped.parentFile ?: mapped
        }
        return mapped
    }

    private fun isWithinAppExternal(context: Context, path: String): Boolean {
        val target = runCatching { File(path).canonicalFile }.getOrNull() ?: return false
        val roots = context.getExternalFilesDirs(null).filterNotNull().mapNotNull {
            runCatching { it.canonicalFile }.getOrNull()
        }
        return roots.any { root -> isUnder(root, target) }
    }

    private fun isTreeUriCompat(uri: Uri): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            DocumentsContract.isTreeUri(uri)
        } else {
            val segments = uri.pathSegments
            segments.isNotEmpty() && segments[0] == "tree"
        }
    }

    private fun ensureDirWritable(dir: File): Boolean {
        if (dir.exists()) {
            return dir.isDirectory && dir.canWrite()
        }
        return runCatching { dir.mkdirs() }.getOrDefault(false) && dir.isDirectory && dir.canWrite()
    }

    internal fun migrateSelectedCoreAndConfig(
        oldBase: File,
        newBase: File,
        selectedVariantKey: String
    ) {
        val oldRoot = File(oldBase, "nodejs-project")
        if (!oldRoot.isDirectory) return

        val newRoot = File(newBase, "nodejs-project")
        if (!newRoot.exists() && !newRoot.mkdirs()) {
            throw IllegalStateException("无法创建目标运行目录：${newRoot.absolutePath}")
        }
        val token = "${System.currentTimeMillis()}-${System.nanoTime()}"
        val sourceConfig = File(oldRoot, "config")
        val targetConfig = File(newRoot, "config")
        val stagedConfig = File(newRoot, ".config-migration-$token")
        val coreName = "danmu_api_${normalizeRuntimeVariantKey(selectedVariantKey)}"
        val sourceCore = File(oldRoot, coreName)
        val targetCore = File(newRoot, coreName)
        val stagedCore = File(newRoot, ".$coreName-migration-$token")
        val configBackup = File(newRoot, ".config-backup-$token")
        val coreBackup = File(newRoot, ".$coreName-backup-$token")

        fun cleanStaging() {
            listOf(stagedConfig, stagedCore).forEach { artifact ->
                runCatching { artifact.deleteRecursively() }
            }
        }

        cleanStaging()
        listOf(configBackup, coreBackup).forEach { artifact ->
            runCatching { artifact.deleteRecursively() }
        }
        var replaceConfig = false
        var replaceCore = false
        var configApplied = false
        var coreApplied = false
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

            // 只迁移当前核心源码；node_modules 由目标目录的统一依赖检查按需修复。
            if (sourceCore.isDirectory && !File(targetCore, "worker.js").isFile) {
                copyDirectoryStrict(
                    source = sourceCore,
                    target = stagedCore,
                    skipTopLevelNames = setOf("node_modules")
                )
                if (!File(stagedCore, "worker.js").isFile) {
                    throw IllegalStateException("迁移后的核心缺少 worker.js")
                }
                replaceCore = true
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
            configBackup.deleteRecursively()
            coreBackup.deleteRecursively()
        } catch (error: Exception) {
            val rollbackErrors = mutableListOf<String>()
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

    private fun selectedRuntimeVariantKey(context: Context, oldBase: File): String {
        val runtimePrefs = context.getSharedPreferences(PREFS_RUNTIME, Context.MODE_PRIVATE)
        val legacyPrefs = context.getSharedPreferences(PREFS_LEGACY_RUNTIME_VARIANT, Context.MODE_PRIVATE)
        val envFile = File(oldBase, "nodejs-project/config/.env")
        val envVariant = runCatching {
            if (!envFile.exists() || !envFile.isFile) {
                null
            } else {
                DotEnvCodec.parse(envFile.readText(Charsets.UTF_8))["DANMU_API_VARIANT"]
            }
        }.getOrNull()

        return resolveSelectedRuntimeVariantKey(
            runtimeVariant = if (runtimePrefs.contains(KEY_RUNTIME_VARIANT)) {
                runtimePrefs.safeGetString(KEY_RUNTIME_VARIANT)
            } else {
                null
            },
            legacyVariant = legacyPrefs.safeGetString(KEY_RUNTIME_VARIANT),
            envVariant = envVariant
        )
    }

    internal fun resolveSelectedRuntimeVariantKey(
        runtimeVariant: String?,
        legacyVariant: String?,
        envVariant: String?
    ): String {
        return sequenceOf(runtimeVariant, legacyVariant, envVariant)
            .mapNotNull(::normalizeRuntimeVariantKeyOrNull)
            .firstOrNull()
            ?: "stable"
    }

    private fun normalizeRuntimeVariantKey(raw: String?): String {
        return normalizeRuntimeVariantKeyOrNull(raw) ?: "stable"
    }

    private fun normalizeRuntimeVariantKeyOrNull(raw: String?): String? {
        return when (raw?.trim()?.lowercase()) {
            "stable" -> "stable"
            "dev", "develop", "development" -> "dev"
            "custom" -> "custom"
            else -> null
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
                child.copyTo(destination, overwrite = overwrite)
            }
        }
    }

    private fun isUnder(root: File, target: File): Boolean {
        val rootCanonical = runCatching { root.canonicalFile }.getOrNull() ?: return false
        val targetCanonical = runCatching { target.canonicalFile }.getOrNull() ?: return false
        var cursor: File? = targetCanonical
        while (cursor != null) {
            if (cursor == rootCanonical) return true
            cursor = cursor.parentFile
        }
        return false
    }
}
