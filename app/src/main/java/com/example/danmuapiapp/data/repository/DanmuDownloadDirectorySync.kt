package com.example.danmuapiapp.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import com.example.danmuapiapp.domain.model.DanmuDownloadRecord
import com.example.danmuapiapp.domain.model.DanmuDownloadSettings
import com.example.danmuapiapp.domain.model.DownloadDirectorySyncResult
import com.example.danmuapiapp.domain.model.DownloadRecordStatus

/**
 * 下载目录访问层：保存目录解析（真实路径或 SAF 树）、目录扫描与已有文件回收、本地文件删除。
 *
 * 不持有记录状态：读取当前记录与写回持久化都由 [DanmuDownloadRepositoryImpl] 注入，
 * 记录锁也沿用宿主的那一把，保证扫描与删除之间互斥。
 */
internal class DanmuDownloadDirectorySync(
    private val context: Context,
    private val recordsLock: Any,
    private val maxRecords: Int,
    private val currentRecords: () -> List<DanmuDownloadRecord>,
    private val persistRecords: (List<DanmuDownloadRecord>) -> Unit
) {

    companion object {
        private const val MAX_SCAN_VISITED_FILES = 2_000
        private const val MAX_SCAN_DEPTH = 8
    }

    fun outputDirectory(settings: DanmuDownloadSettings, create: Boolean = true): DocumentFile {
        val path = settings.saveDirPath.trim()
        if (path.isNotEmpty()) {
            val directory = java.io.File(path)
            if (!directory.isDirectory && (!create || !directory.mkdirs())) error("下载目录不存在或无法访问：$path")
            return DocumentFile.fromFile(directory)
        }
        val tree = settings.saveTreeUri.trim()
        if (tree.isEmpty()) error("请先在下载设置中选择保存目录")
        return DocumentFile.fromTreeUri(context, tree.toUri()) ?: error("保存目录无效，请重新选择")
    }

    fun deleteLocalFile(uriText: String): LocalFileDeleteState {
        val uri = runCatching { uriText.toUri() }.getOrNull()
            ?: return LocalFileDeleteState.Failed
        if (uri.scheme == "file") {
            val file = java.io.File(uri.path ?: return LocalFileDeleteState.Failed)
            return if (!file.exists()) LocalFileDeleteState.Missing
                else if (file.delete()) LocalFileDeleteState.Deleted else LocalFileDeleteState.Failed
        }
        if (uri.scheme != "content") return LocalFileDeleteState.Failed
        val document = runCatching { DocumentFile.fromSingleUri(context, uri) }.getOrNull()
            ?: return LocalFileDeleteState.Failed
        val exists = runCatching { document.exists() }.getOrElse {
            Log.w("DanmuDownloadRepo", "检查弹幕文件失败：$uriText", it)
            return LocalFileDeleteState.Failed
        }
        if (!exists) return LocalFileDeleteState.Missing
        return if (runCatching { document.delete() }.onFailure {
                Log.w("DanmuDownloadRepo", "删除弹幕文件失败：$uriText", it)
            }.getOrDefault(false)
        ) {
            LocalFileDeleteState.Deleted
        } else {
            LocalFileDeleteState.Failed
        }
    }

    fun sync(settings: DanmuDownloadSettings): DownloadDirectorySyncResult {
        val configured = settings
        val root = outputDirectory(configured, create = false)
        if (!root.canRead()) error("下载目录不可读，请重新授权目录权限")

        val existingUris = currentRecords()
            .map { it.fileUri.trim() }
            .filter(String::isNotBlank)
            .toMutableSet()
        val scan = DownloadDirectoryScanner.scan(
            rootUri = root.uri.toString(),
            existingUris = existingUris,
            maxFiles = MAX_SCAN_VISITED_FILES,
            maxDepth = MAX_SCAN_DEPTH
        ) { uri -> listDirectoryEntries(uri.toUri(), existingUris) }
        var skippedFiles = scan.skippedFiles
        var truncated = scan.truncated
        var unreadableFiles = 0

        val now = System.currentTimeMillis()
        val imported = mutableListOf<DanmuDownloadRecord>()
        val rejectedCandidates = mutableListOf<DownloadDirectoryScanner.Candidate>()
        val sortedCandidates = scan.candidates
            .sortedWith(
                compareByDescending<DownloadDirectoryScanner.Candidate> { it.lastModified }
                    .thenBy { it.relativePath }
            )
        if (sortedCandidates.size > maxRecords) {
            skippedFiles += sortedCandidates.size - maxRecords
            truncated = true
        }
        sortedCandidates
            .take(maxRecords)
            .forEach { candidate ->
                val inspection = inspectDirectoryFile(candidate)
                if (inspection == null) {
                    unreadableFiles++
                    skippedFiles++
                    return@forEach
                }
                if (!inspection.valid) {
                    rejectedCandidates += candidate
                    skippedFiles++
                    return@forEach
                }
                val record = candidate.toDownloadRecord(createdAtFallback = now)
                if (existingUris.add(record.fileUri)) imported += record
            }

        // Old exports can have fields unknown to the inspector. If every matching file
        // was rejected, recover by extension and leave a visible warning instead of an empty history.
        if (imported.isEmpty() && rejectedCandidates.isNotEmpty()) {
            rejectedCandidates.forEach { candidate ->
                val record = candidate.toDownloadRecord(now).copy(errorMessage = "目录恢复：内容格式待预览核对")
                if (existingUris.add(record.fileUri)) imported += record
            }
        }

        var persistedImportedCount = 0
        if (imported.isNotEmpty()) {
            synchronized(recordsLock) {
                val merged = (currentRecords() + imported)
                    .distinctBy { record ->
                        record.fileUri.trim().takeIf(String::isNotBlank) ?: "record:${record.id}"
                    }
                    .sortedByDescending { it.createdAt }
                    .take(maxRecords)
                val mergedUris = merged.mapTo(mutableSetOf()) { it.fileUri }
                persistedImportedCount = imported.count { it.fileUri in mergedUris }
                persistRecords(merged)
            }
        }

        var removedRecords = 0
        if (scan.reliable && !truncated && (configured.saveDirPath.isNotBlank() || scan.scannedUris.isNotEmpty())) {
            val prefix = if (configured.saveDirPath.isNotBlank()) {
                android.net.Uri.fromFile(java.io.File(configured.saveDirPath)).toString().trimEnd('/') + "/"
            } else configured.saveTreeUri.trim()
            synchronized(recordsLock) {
                val retained = currentRecords().filter { record ->
                    val belongs = prefix.isNotBlank() && record.fileUri.startsWith(prefix)
                    val keep = !belongs || record.importedToLocal || record.statusEnum() != DownloadRecordStatus.Success || record.fileUri in scan.scannedUris
                    if (!keep) removedRecords++
                    keep
                }
                if (removedRecords > 0) persistRecords(retained)
            }
        }

        return DownloadDirectorySyncResult(
            scannedFiles = scan.scannedFiles,
            importedRecords = persistedImportedCount,
            skippedFiles = skippedFiles,
            truncated = truncated,
            matchedFiles = scan.matchedFiles, removedRecords = removedRecords,
            note = listOfNotNull(
                "有子目录读取失败，已保留原记录".takeIf { !scan.reliable },
                "有 $unreadableFiles 个文件为空或无法读取，已跳过".takeIf { unreadableFiles > 0 }
            ).joinToString("；")
        )
    }

    /** Read metadata once per directory; SAF columns are fetched together in one query. */
    fun listDirectoryEntries(
        directory: Uri,
        existingUris: Set<String>
    ): List<DownloadDirectoryScanner.Entry>? = runCatching<List<DownloadDirectoryScanner.Entry>?> {
        if (directory.scheme == "file") {
            val children = java.io.File(directory.path.orEmpty()).listFiles() ?: return@runCatching null
            return@runCatching children.mapNotNull { child ->
                val isDirectory = child.isDirectory
                if (!isDirectory && !child.isFile) return@mapNotNull null
                val uri = Uri.fromFile(child).toString()
                val needsMetadata = !isDirectory && uri !in existingUris &&
                    DanmuDownloadFormat.fromFileName(child.name) != null
                DownloadDirectoryScanner.Entry(
                    uri = uri,
                    name = child.name,
                    isDirectory = isDirectory,
                    lastModified = if (needsMetadata) child.lastModified() else 0L,
                    bytes = if (needsMetadata) child.length() else 0L
                )
            }
        }
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            directory, DocumentsContract.getDocumentId(directory)
        )
        context.contentResolver.query(childrenUri, columns, null, null, null)?.use { cursor ->
            buildList<DownloadDirectoryScanner.Entry> {
                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(0) ?: error("目录返回了无效文件信息")
                    val name = cursor.getString(1)?.takeIf { it.isNotBlank() }
                        ?: error("目录返回了无效文件名")
                    val mime = cursor.getString(2).orEmpty()
                    // Preserve unknown entries instead of treating an incomplete listing as deletion.
                    if (mime.isBlank()) error("目录返回了无效文件类型")
                    add(DownloadDirectoryScanner.Entry(
                        uri = DocumentsContract.buildDocumentUriUsingTree(directory, documentId).toString(),
                        name = name,
                        isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                        bytes = if (cursor.isNull(3)) 0L else cursor.getLong(3),
                        lastModified = if (cursor.isNull(4)) 0L else cursor.getLong(4)
                    ))
                }
            }
        }
    }.onFailure {
        Log.w("DanmuDownloadRepo", "读取下载目录失败", it)
    }.getOrNull()

    fun inspectDirectoryFile(candidate: DownloadDirectoryScanner.Candidate): DanmuPayloadInspection? {
        val prefix = runCatching {
            context.contentResolver.openInputStream(candidate.uri.toUri())?.use { input ->
                DownloadDirectoryScanner.readPrefix(input)
            }
        }.onFailure {
            Log.w("DanmuDownloadRepo", "读取已有弹幕文件失败", it)
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        return DanmuPayloadInspection(
            valid = DownloadDirectoryScanner.isPlausiblePrefix(prefix, candidate.format)
        )
    }

    fun DownloadDirectoryScanner.Candidate.toDownloadRecord(
        createdAtFallback: Long
    ): DanmuDownloadRecord = DownloadRecordRecovery.record(this, createdAtFallback)
}

internal enum class LocalFileDeleteState {
    Deleted,
    Missing,
    Failed
}
