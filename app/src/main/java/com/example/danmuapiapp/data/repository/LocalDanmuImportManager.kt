package com.example.danmuapiapp.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地弹幕来源准备：既支持 SAF 选中的单个文件（content://），
 * 也支持「目录直读」模式下的真实路径（file://，需要所有文件访问权限）。
 * 压缩包导入不做（与核心一致，核心只接受单文件上传）。
 */
@Singleton
class LocalDanmuImportManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val CORE_MAX_UPLOAD_BYTES = 10L * 1024L * 1024L
        private val supportedExtensions = setOf("xml", "json", "ass", "ssa", "csv", "txt")
    }

    data class PreparedFile(
        val sourceUri: String,
        val displayName: String,
        val sizeBytes: Long?,
        val mimeType: String,
        val formatHint: String
    )

    suspend fun prepare(uriText: String): Result<PreparedFile> = withContext(Dispatchers.IO) {
        runLocalDanmuRequest {
            val uri = Uri.parse(uriText)
            val displayName = resolveDisplayName(uri) ?: "未命名文件"
            val sizeBytes = resolveSize(uri)
            val mimeType = runCatching { context.contentResolver.getType(uri) }
                .getOrNull()
                .orEmpty()
            val extension = extensionOf(displayName)
            val formatHint = when {
                extension in supportedExtensions -> extension
                else -> sniffTextFormat(uri)
            } ?: error("不支持的文件格式，仅支持 XML/JSON/ASS/SSA/CSV/TXT")
            if (sizeBytes != null && sizeBytes > CORE_MAX_UPLOAD_BYTES) {
                error("单文件不能超过 10 MB")
            }
            PreparedFile(
                sourceUri = uriText,
                displayName = displayName,
                sizeBytes = sizeBytes,
                mimeType = mimeType,
                formatHint = formatHint
            )
        }
    }

    /**
     * 目录直读模式的入口：直接用文件路径准备上传（file:// URI），
     * 不依赖 SAF 授权，进程重启后依然可读。需要“所有文件访问”权限。
     */
    suspend fun preparePath(path: String): Result<PreparedFile> = withContext(Dispatchers.IO) {
        runLocalDanmuRequest {
            val file = File(path)
            if (!file.isFile) error("文件不存在或已被移动")
            val displayName = file.name.ifBlank { "未命名文件" }
            val extension = extensionOf(displayName)
            val formatHint = if (extension in supportedExtensions) {
                extension
            } else {
                sniffFileFormat(file)
            } ?: error("不支持的文件格式，仅支持 XML/JSON/ASS/SSA/CSV/TXT")
            val sizeBytes = file.length()
            if (sizeBytes > CORE_MAX_UPLOAD_BYTES) error("单文件不能超过 10 MB")
            PreparedFile(
                sourceUri = Uri.fromFile(file).toString(),
                displayName = displayName,
                sizeBytes = sizeBytes,
                mimeType = mimeTypeForExtension(extension),
                formatHint = formatHint
            )
        }
    }

    suspend fun <T> withSourceStream(
        uriText: String,
        displayName: String,
        block: suspend (InputStream) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        runLocalDanmuRequest {
            val input = context.contentResolver.openInputStream(Uri.parse(uriText))
                ?: error("无法读取源文件")
            input.use { source -> block(source) }
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        val fromDocument = runCatching { DocumentFile.fromSingleUri(context, uri)?.name }.getOrNull()
        if (!fromDocument.isNullOrBlank()) return fromDocument
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index < 0) null else cursor.getString(index)
                }
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun resolveSize(uri: Uri): Long? {
        val fromDocument = runCatching { DocumentFile.fromSingleUri(context, uri)?.length() }
            .getOrNull()
            ?.takeIf { it > 0L }
        if (fromDocument != null) return fromDocument
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index < 0 || cursor.isNull(index)) null else cursor.getLong(index)
                }
        }.getOrNull()?.takeIf { it > 0L }
    }

    private fun sniffTextFormat(uri: Uri): String? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                sniff(input)
            }
        }.getOrNull()
    }

    private fun sniffFileFormat(file: File): String? {
        return runCatching { file.inputStream().use { sniff(it) } }.getOrNull()
    }

    private fun sniff(input: InputStream): String? {
        val buffer = ByteArray(4096)
        val read = input.read(buffer)
        if (read <= 0) return null
        val bytes = if (read == buffer.size) buffer else buffer.copyOf(read)
        val text = when {
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16LE)
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16BE)
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
                bytes.copyOfRange(3, bytes.size).toString(Charsets.UTF_8)
            else -> bytes.toString(Charsets.UTF_8)
        }.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        return when {
            text.startsWith("<") -> "xml"
            text.startsWith("{") || text.startsWith("[") -> "json"
            text.contains("[Script Info]", ignoreCase = true) ||
                text.contains("Dialogue:", ignoreCase = true) -> "ass"
            else -> "txt"
        }
    }

    private fun mimeTypeForExtension(extension: String): String = when (extension) {
        "xml" -> "text/xml"
        "json" -> "application/json"
        "ass", "ssa", "csv", "txt" -> "text/plain"
        else -> "application/octet-stream"
    }

    private fun extensionOf(name: String): String {
        val clean = name.substringAfterLast('/').substringAfterLast('\\')
        return clean.substringAfterLast('.', "").lowercase(Locale.ROOT)
    }
}
