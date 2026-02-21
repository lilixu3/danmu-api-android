package com.example.danmuapiapp.data.service

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import com.example.danmuapiapp.data.util.safeGetString
import com.example.danmuapiapp.domain.model.RunMode
import android.Manifest
import android.content.pm.PackageManager
import java.io.File

@SuppressLint("ApplySharedPref")
object RuntimePaths {

    private const val PREFS_WORK_DIR = "danmu_work_dir"
    private const val KEY_CUSTOM_BASE_PATH = "custom_path"
    private const val KEY_CUSTOM_BASE_URI = "custom_uri"
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
        val uri = runCatching { Uri.parse(uriText) }.getOrNull() ?: return null
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

    fun clearCustomBasePath(context: Context) {
        context.getSharedPreferences(PREFS_WORK_DIR, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CUSTOM_BASE_PATH)
            .remove(KEY_CUSTOM_BASE_URI)
            .commit()
    }

    fun setCustomBasePath(context: Context, path: String?) {
        val prefs = context.getSharedPreferences(PREFS_WORK_DIR, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val value = path?.trim().orEmpty()
        if (value.isBlank()) {
            editor.remove(KEY_CUSTOM_BASE_PATH)
        } else {
            editor.putString(KEY_CUSTOM_BASE_PATH, value)
        }
        editor.commit()
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

    fun applyCustomBaseDir(context: Context, targetPath: String?): ApplyResult {
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
        if (sameDir) {
            if (shouldUseDefault && oldCustom != null) {
                clearCustomBasePath(context)
                return ApplyResult(true, "已恢复为默认目录")
            }
            if (!shouldUseDefault && oldCustom == targetCanonical.absolutePath) {
                return ApplyResult(true, "已是当前目录")
            }
        }

        return try {
            if (shouldUseDefault) {
                clearCustomBasePath(context)
            } else {
                setCustomBasePath(context, targetCanonical.absolutePath)
            }

            if (!sameDir) {
                migrateNodeProjectData(oldBase = oldBase, newBase = targetCanonical)
            }

            ApplyResult(true, if (shouldUseDefault) "已恢复为默认目录" else "已切换工作目录")
        } catch (e: Exception) {
            setCustomBasePath(context, oldCustom)
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

    private fun migrateNodeProjectData(oldBase: File, newBase: File) {
        val oldRoot = File(oldBase, "nodejs-project")
        if (!oldRoot.exists()) return

        val newRoot = File(newBase, "nodejs-project")
        if (!newRoot.exists()) {
            runCatching { newRoot.mkdirs() }
        }
        runCatching { File(newRoot, ".cache").mkdirs() }

        copyDirMerge(File(oldRoot, "config"), File(newRoot, "config"))
        copyDirMerge(File(oldRoot, "logs"), File(newRoot, "logs"))
        copyDirMerge(File(oldRoot, ".cache"), File(newRoot, ".cache"))

        listOf("danmu_api_stable", "danmu_api_dev", "danmu_api_custom").forEach { name ->
            val src = File(oldRoot, name)
            val dst = File(newRoot, name)
            if (!src.exists()) return@forEach
            val dstHasWorker = File(dst, "worker.js").exists()
            if (!dst.exists() || !dstHasWorker) {
                runCatching { if (dst.exists()) dst.deleteRecursively() }
                runCatching { src.copyRecursively(dst, overwrite = true) }
            }
        }
    }

    private fun copyDirMerge(src: File, dst: File) {
        if (!src.exists() || !src.isDirectory) return
        if (!dst.exists()) runCatching { dst.mkdirs() }
        val children = src.listFiles() ?: return
        for (child in children) {
            val target = File(dst, child.name)
            if (child.isDirectory) {
                copyDirMerge(child, target)
            } else if (!target.exists()) {
                runCatching { child.copyTo(target, overwrite = false) }
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
