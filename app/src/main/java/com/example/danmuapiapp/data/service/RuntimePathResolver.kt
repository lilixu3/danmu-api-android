package com.example.danmuapiapp.data.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * 运行目录路径解析与可用性判定。
 *
 * 只处理路径本身：规范化、SAF tree URI 映射、外部存储访问判定与写入探针。
 * 不读写 SharedPreferences，也不搬移文件。
 */
internal object RuntimePathResolver {

    fun normalizeBaseDir(path: String): File = normalizeBaseDir(File(path))

    fun normalizeBaseDir(input: File): File {
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

    fun workDirIdentity(baseDir: File): String {
        val normalized = normalizeBaseDir(baseDir)
        val path = runCatching { normalized.canonicalPath }
            .getOrElse { normalized.absolutePath }
            .trimEnd(File.separatorChar)
        return path.ifBlank { File.separator }
    }

    fun isWithinAppExternal(context: Context, path: String): Boolean {
        val target = runCatching { File(path).canonicalFile }.getOrNull() ?: return false
        val roots = context.getExternalFilesDirs(null).filterNotNull().mapNotNull {
            runCatching { it.canonicalFile }.getOrNull()
        }
        return roots.any { root -> isUnder(root, target) }
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

    fun verifyDirectoryWritable(dir: File): Boolean {
        return runCatching {
            if (dir.exists()) {
                if (!dir.isDirectory) return@runCatching false
            } else if (!dir.mkdirs() || !dir.isDirectory) {
                return@runCatching false
            }

            val expected = "danmu-api-work-dir-probe".toByteArray(Charsets.UTF_8)
            val probe = File(dir, ".danmu-write-probe-${UUID.randomUUID()}")
            try {
                FileOutputStream(probe).use { output ->
                    output.write(expected)
                    output.flush()
                }
                probe.isFile && probe.readBytes().contentEquals(expected)
            } finally {
                if (probe.exists() && !probe.delete()) {
                    throw IOException("无法清理目录写入探针：${probe.absolutePath}")
                }
            }
        }.getOrDefault(false)
    }

    fun workDirectoriesOverlap(first: File, second: File): Boolean {
        val firstCanonical = runCatching { first.canonicalFile }.getOrElse { first.absoluteFile }
        val secondCanonical = runCatching { second.canonicalFile }.getOrElse { second.absoluteFile }
        if (firstCanonical == secondCanonical) return false
        return isUnder(firstCanonical, secondCanonical) || isUnder(secondCanonical, firstCanonical)
    }

    fun isUnder(root: File, target: File): Boolean {
        val rootCanonical = runCatching { root.canonicalFile }.getOrNull() ?: return false
        val targetCanonical = runCatching { target.canonicalFile }.getOrNull() ?: return false
        var cursor: File? = targetCanonical
        while (cursor != null) {
            if (cursor == rootCanonical) return true
            cursor = cursor.parentFile
        }
        return false
    }

    private fun isTreeUriCompat(uri: Uri): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            DocumentsContract.isTreeUri(uri)
        } else {
            val segments = uri.pathSegments
            segments.isNotEmpty() && segments[0] == "tree"
        }
    }
}
