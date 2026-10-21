package com.example.danmuapiapp.data.service

import android.content.Context
import android.net.Uri
import com.example.danmuapiapp.domain.model.RunMode
import java.io.File

/**
 * 运行目录门面。
 *
 * 只保留对外稳定 API 与目录编排：路径解析在 [RuntimePathResolver]，
 * 偏好存储在 [RuntimeWorkDirPreferences]，迁移事务在 [RuntimeWorkDirMigration]，
 * 核心变体解析在 [RuntimeVariantKeyResolver]。
 */
object RuntimePaths {

    enum class WorkDirSwitchMode {
        SwitchOnly,
        MigrateSelectedCore
    }

    internal const val PREFS_WORK_DIR = RuntimeWorkDirPreferences.PREFS_NAME
    internal const val KEY_CUSTOM_BASE_PATH = RuntimeWorkDirPreferences.KEY_CUSTOM_BASE_PATH
    internal const val KEY_CUSTOM_BASE_URI = RuntimeWorkDirPreferences.KEY_CUSTOM_BASE_URI
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

    fun readCustomBasePath(context: Context): String? =
        RuntimeWorkDirPreferences.readCustomBasePath(context)

    fun readCustomBaseDir(context: Context): File? =
        RuntimeWorkDirPreferences.readCustomBaseDir(context)

    fun normalBaseDir(context: Context): File {
        return readCustomBaseDir(context) ?: defaultBaseDir(context)
    }

    internal fun normalWorkDirIdentity(context: Context): String {
        return workDirIdentity(normalBaseDir(context))
    }

    internal fun workDirIdentity(baseDir: File): String =
        RuntimePathResolver.workDirIdentity(baseDir)

    internal fun isWorkDirSelectionPreference(key: String?): Boolean =
        RuntimeWorkDirPreferences.isSelectionKey(key)

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

    @Deprecated("必须显式选择运行模式或 normalProjectDir", level = DeprecationLevel.ERROR)
    fun projectDir(context: Context): File = normalProjectDir(context)

    fun projectDir(context: Context, mode: RunMode): File {
        val base = if (mode != RunMode.Normal) rootBaseDir(context) else normalBaseDir(context)
        return File(base, "nodejs-project")
    }

    fun normalProjectDir(context: Context): File = File(normalBaseDir(context), "nodejs-project")

    fun rootProjectDir(context: Context): File = File(rootBaseDir(context), "nodejs-project")

    fun isCustomEnabled(context: Context): Boolean = readCustomBasePath(context) != null

    fun clearCustomBasePath(context: Context): Boolean =
        RuntimeWorkDirPreferences.clearCustomBasePath(context)

    fun setCustomBasePath(context: Context, path: String?): Boolean =
        RuntimeWorkDirPreferences.setCustomBasePath(context, path)

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
            RuntimePathResolver.normalizeBaseDir(File(targetPath))
        }

        val targetCanonical = runCatching { normalizedTarget.canonicalFile }.getOrElse { normalizedTarget }
        val defaultCanonical = runCatching { defaultBase.canonicalFile }.getOrElse { defaultBase }
        val oldCanonical = runCatching { oldBase.canonicalFile }.getOrElse { oldBase }
        val sameDir = oldCanonical == targetCanonical
        if (!sameDir && switchMode == WorkDirSwitchMode.MigrateSelectedCore &&
            RuntimePathResolver.workDirectoriesOverlap(oldCanonical, targetCanonical)
        ) {
            return ApplyResult(false, "迁移时新旧工作目录不能互相包含，请选择独立目录")
        }

        if (!RuntimePathResolver.verifyDirectoryWritable(normalizedTarget)) {
            return ApplyResult(false, "目录不可用或无写入权限：${normalizedTarget.absolutePath}")
        }

        val shouldUseDefault = targetCanonical == defaultCanonical
        val targetProjectDir = File(targetCanonical, "nodejs-project")
        val targetCacheDir = File(targetProjectDir, ".cache")
        if (!RuntimePathResolver.verifyDirectoryWritable(targetProjectDir) ||
            !RuntimePathResolver.verifyDirectoryWritable(targetCacheDir)
        ) {
            return ApplyResult(false, "目录不可用或无写入权限：${targetCacheDir.absolutePath}")
        }

        val selectedVariantKey = RuntimeVariantKeyResolver.selectedKey(context, oldBase)
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
                RuntimeWorkDirMigration.performTransaction(
                    oldBase = oldBase,
                    newBase = targetCanonical,
                    selectedVariantKey = selectedVariantKey,
                    commitPreference = {
                        if (shouldUseDefault) {
                            clearCustomBasePath(context)
                        } else {
                            setCustomBasePath(context, targetCanonical.absolutePath)
                        }
                    }
                )
            } else {
                val preferenceSaved = if (shouldUseDefault) {
                    clearCustomBasePath(context)
                } else {
                    setCustomBasePath(context, targetCanonical.absolutePath)
                }
                if (!preferenceSaved) {
                    throw IllegalStateException("无法保存工作目录设置")
                }
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
            val preferenceRestored = if (oldCustom == null) {
                clearCustomBasePath(context)
            } else {
                setCustomBasePath(context, oldCustom)
            }
            val suffix = if (preferenceRestored) "" else "；原工作目录设置恢复失败，请重新选择"
            ApplyResult(false, "切换失败：${e.message ?: "未知错误"}$suffix")
        }
    }

    fun resolveTreeUriToPath(uri: Uri): String? = RuntimePathResolver.resolveTreeUriToPath(uri)

    fun isAllFilesAccessGranted(context: Context): Boolean =
        RuntimePathResolver.isAllFilesAccessGranted(context)

    fun needsAllFilesAccess(context: Context, targetPath: String): Boolean =
        RuntimePathResolver.needsAllFilesAccess(context, targetPath)

    fun normalizeBaseDir(path: String): File = RuntimePathResolver.normalizeBaseDir(path)

    internal fun verifyDirectoryWritable(dir: File): Boolean =
        RuntimePathResolver.verifyDirectoryWritable(dir)

    internal fun migrateSelectedCoreAndConfig(
        oldBase: File,
        newBase: File,
        selectedVariantKey: String
    ) {
        RuntimeWorkDirMigration.migrateSelectedCoreAndConfig(
            oldBase = oldBase,
            newBase = newBase,
            selectedVariantKey = selectedVariantKey
        )
    }

    internal fun performWorkDirMigrationTransaction(
        oldBase: File,
        newBase: File,
        selectedVariantKey: String,
        commitPreference: () -> Boolean
    ) {
        RuntimeWorkDirMigration.performTransaction(
            oldBase = oldBase,
            newBase = newBase,
            selectedVariantKey = selectedVariantKey,
            commitPreference = commitPreference
        )
    }

    internal fun workDirectoriesOverlap(first: File, second: File): Boolean =
        RuntimePathResolver.workDirectoriesOverlap(first, second)

    internal fun resolveSelectedRuntimeVariantKey(
        runtimeVariant: String?,
        legacyVariant: String?,
        envVariant: String?
    ): String = RuntimeVariantKeyResolver.resolve(
        runtimeVariant = runtimeVariant,
        legacyVariant = legacyVariant,
        envVariant = envVariant
    )
}
