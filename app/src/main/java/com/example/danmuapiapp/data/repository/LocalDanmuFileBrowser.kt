package com.example.danmuapiapp.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.danmuapiapp.data.service.RuntimePaths
import com.example.danmuapiapp.domain.repository.DanmuDownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 目录直读模式：在用户授予“所有文件访问”后，直接按路径浏览弹幕目录并选文件，
 * 不再依赖 SAF 的逐文件授权（进程重启后也不会失效）。
 */
@Singleton
class LocalDanmuFileBrowser @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DanmuDownloadRepository
) {

    companion object {
        const val MAX_VISIBLE_ENTRIES = LocalDanmuDirectoryListing.MAX_ENTRIES
    }

    data class Entry(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val sizeBytes: Long = 0L
    )

    data class Snapshot(
        val path: String,
        val parent: String?,
        val entries: List<Entry>,
        val truncated: Boolean = false
    )

    fun isAllFilesAccessGranted(): Boolean = RuntimePaths.isAllFilesAccessGranted(context)

    /**
     * 目录优先级：用户指定的默认目录 → 上次浏览的目录 → 弹幕下载页配置的保存目录
     * → 常见的 `/sdcard/Download/弹幕下载` → `/sdcard/Download` → 外置存储根。
     */
    fun preferredDirectory(remembered: String? = null, custom: String? = null): String {
        firstExistingDirectory(custom)?.let { return it }
        firstExistingDirectory(remembered)?.let { return it }
        configuredDownloadDirectory()?.let { return it }
        return defaultDirectory()
    }

    private fun firstExistingDirectory(path: String?): String? {
        val raw = path?.trim().orEmpty()
        if (raw.isBlank()) return null
        val dir = File(raw)
        return if (dir.isDirectory) dir.absolutePath else null
    }

    /** 弹幕下载页里用户选择的保存目录（SAF 目录授权），能解析成真实路径才使用。 */
    fun configuredDownloadDirectory(): String? {
        val treeUri = runCatching { downloadRepository.settings.value.saveTreeUri }
            .getOrNull()
            ?.trim()
            .orEmpty()
        if (treeUri.isBlank()) return null
        val path = runCatching { RuntimePaths.resolveTreeUriToPath(Uri.parse(treeUri)) }
            .getOrNull()
            ?.let { firstExistingDirectory(it) }
        return path
    }

    /** 兜底默认目录：下载目录下的“弹幕下载”。 */
    fun defaultDirectory(): String {
        val root = Environment.getExternalStorageDirectory()
        val danmu = File(root, "Download/弹幕下载")
        if (danmu.isDirectory) return danmu.absolutePath
        val download = File(root, "Download")
        if (download.isDirectory) return download.absolutePath
        return root.absolutePath
    }

    fun normalize(path: String?): String {
        val raw = path?.trim().orEmpty().ifBlank { defaultDirectory() }
        return runCatching { File(raw).canonicalPath }.getOrDefault(raw)
    }

    fun parentOf(path: String): String? {
        val dir = File(path)
        val parent = dir.parentFile ?: return null
        if (parent.absolutePath == dir.absolutePath) return null
        return parent.absolutePath
    }

    suspend fun list(path: String): Result<Snapshot> = withContext(Dispatchers.IO) {
        runLocalDanmuRequest {
            val dir = File(normalize(path))
            if (!dir.isDirectory) error("目录不存在或不可访问：${dir.absolutePath}")
            val listed = LocalDanmuDirectoryListing.list(dir)
            Snapshot(
                path = dir.absolutePath,
                parent = parentOf(dir.absolutePath),
                entries = listed.entries,
                truncated = listed.truncated
            )
        }
    }
}

/** 纯文件系统逻辑，便于单元测试。 */
internal object LocalDanmuDirectoryListing {

    val supportedExtensions: Set<String> = setOf("xml", "json", "ass", "ssa", "csv", "txt")

    const val MAX_ENTRIES = 2_000

    data class Result(
        val entries: List<LocalDanmuFileBrowser.Entry>,
        val truncated: Boolean = false
    )

    fun supports(name: String): Boolean = extensionOf(name) in supportedExtensions

    fun list(dir: File): Result {
        val children = dir.listFiles().orEmpty()
        val entries = ArrayList<LocalDanmuFileBrowser.Entry>(minOf(children.size, MAX_ENTRIES))
        var truncated = false
        for (child in children) {
            val name = child.name
            if (name.startsWith(".")) continue
            val isDirectory = child.isDirectory
            if (!isDirectory && !supports(name)) continue
            if (entries.size >= MAX_ENTRIES) {
                truncated = true
                break
            }
            entries += LocalDanmuFileBrowser.Entry(
                name = name,
                path = child.absolutePath,
                isDirectory = isDirectory,
                sizeBytes = if (isDirectory) 0L else child.length()
            )
        }
        entries.sortWith(comparator)
        return Result(entries = entries, truncated = truncated)
    }

    private val comparator = Comparator<LocalDanmuFileBrowser.Entry> { left, right ->
        if (left.isDirectory != right.isDirectory) {
            if (left.isDirectory) -1 else 1
        } else {
            naturalCompare(left.name, right.name)
        }
    }

    /** 数字按数值排序，避免 E10 排在 E2 前面。 */
    fun naturalCompare(left: String, right: String): Int {
        var i = 0
        var j = 0
        while (i < left.length && j < right.length) {
            val l = left[i]
            val r = right[j]
            if (l.isDigit() && r.isDigit()) {
                var li = i
                while (li < left.length && left[li].isDigit()) li++
                var rj = j
                while (rj < right.length && right[rj].isDigit()) rj++
                val ln = left.substring(i, li).trimStart('0')
                val rn = right.substring(j, rj).trimStart('0')
                val byLength = ln.length.compareTo(rn.length)
                if (byLength != 0) return byLength
                val byValue = ln.compareTo(rn)
                if (byValue != 0) return byValue
                i = li
                j = rj
            } else {
                val lower = l.lowercase(Locale.ROOT).compareTo(r.lowercase(Locale.ROOT))
                if (lower != 0) return lower
                i++
                j++
            }
        }
        return (left.length - i).compareTo(right.length - j)
    }

    private fun extensionOf(name: String): String =
        name.substringAfterLast('/', name).substringAfterLast('.', "").lowercase(Locale.ROOT)
}
