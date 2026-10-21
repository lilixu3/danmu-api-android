package com.example.danmuapiapp.data.repository

import android.content.Context
import com.example.danmuapiapp.domain.model.*
import com.example.danmuapiapp.domain.repository.DanmuDownloadRepository
import com.example.danmuapiapp.domain.repository.LocalDanmuRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

internal data class DownloadImportPlan(val record: DanmuDownloadRecord,
    val item: LocalDanmuBatchItem? = null, val blockedReason: String = "")

/** Filename metadata has the same precedence as manual file import. Never invent a year. */
internal object DownloadLocalImportPlanner {
    fun metadata(record: DanmuDownloadRecord): ResolvedLocalDanmuMetadata {
        val name = record.fileName.ifBlank { record.episodeTitle.ifBlank { record.animeTitle } }
        val parsed = LocalDanmuMetadataResolver.resolve(
            fileName = name,
            relativePath = record.relativePath.ifBlank { name },
            contextName = record.animeTitle
        )
        val recordMetadata = LocalDanmuMetadataResolver.resolve(record.episodeTitle, contextName = record.animeTitle)
        val type = LocalDanmuType.fromWire(record.mediaType)?.takeIf { it != LocalDanmuType.Unknown }?.wire
            ?: parsed.type.ifBlank { recordMetadata.type }
        return parsed.copy(
            type = type,
            year = LocalDanmuMetadataResolver.resolve(name).year
                ?: LocalDanmuMetadataResolver.resolve(record.animeTitle + ".xml").year
                ?: parsed.year,
            title = parsed.title.takeIf { it.isNotBlank() && it != "未命名" }
                ?: LocalDanmuMetadataResolver.resolve(record.animeTitle + ".xml").title,
            season = parsed.season ?: 1,
            episode = if (type == "tv") parsed.episode ?: record.episodeNo.takeIf { it > 0 } else null
        )
    }
    fun key(record: DanmuDownloadRecord): String? = metadata(record).let {
        if (it.year == null) null else LocalDanmuResourceKey.buildResourceKey(it.title, it.year, it.type, it.season, it.episode)
    }
    fun existingKeys(resources: List<LocalDanmuResource>): Map<String, String> = resources.mapNotNull {
        LocalDanmuResourceKey.buildResourceKey(it.title, it.year, it.type, it.season, it.episode)?.let { key -> key to it.resourceKey }
    }.toMap()
    fun matches(records: List<DanmuDownloadRecord>, snapshot: LocalDanmuSnapshot): Map<Long, String> {
        val resourceKeys = snapshot.resources.mapTo(hashSetOf()) { it.resourceKey }
        val metadataKeys = existingKeys(snapshot.resources)
        return records.mapNotNull { record ->
            val stored = record.localImports[snapshot.libraryId]?.takeIf { it in resourceKeys }
            (stored ?: metadataKeys[key(record)])?.let { record.id to it }
        }.toMap()
    }
    fun plan(record: DanmuDownloadRecord, existing: Map<String, String> = emptyMap(), importedResourceKey: String? = null): DownloadImportPlan {
        if (record.statusEnum() != DownloadRecordStatus.Success) return DownloadImportPlan(record, blockedReason = "尚未下载成功")
        if (record.fileUri.isBlank()) return DownloadImportPlan(record, blockedReason = "没有可读的文件位置")
        if (record.bytes > LocalDanmuImportManager.CORE_MAX_UPLOAD_BYTES)
            return DownloadImportPlan(record, blockedReason = "文件超过核心 10 MB 上限")
        val metadata = metadata(record)
        val hit = importedResourceKey ?: existing[key(record)]
        return DownloadImportPlan(record, LocalDanmuBatchItem(id = record.id, sourceUri = record.fileUri,
            displayName = record.fileName.ifBlank { record.episodeTitle }, sizeBytes = record.bytes,
            formatHint = record.format, mimeType = record.formatEnum().mimeType,
            title = metadata.title, year = metadata.year, type = LocalDanmuType.fromWire(metadata.type) ?: LocalDanmuType.Unknown,
            season = metadata.season ?: 1, episode = metadata.episode, confidence = metadata.confidence,
            importedLabel = hit?.let { "已导入本地弹幕" }, selected = hit == null,
            message = if (metadata.year == null) "未识别年份，点此订正" else ""))
    }
}

/** Full bytes, bounded reads and one upload pipeline for manual, quick and automatic imports. */
@Singleton
class LocalDanmuUploadService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val files: LocalDanmuImportManager,
    private val repository: LocalDanmuRepository,
    private val sourceStore: LocalDanmuSourceStore,
    private val downloads: DanmuDownloadRepository,
    private val runtimeData: com.example.danmuapiapp.data.service.RuntimeDataAccess
) {
    suspend fun upload(item: LocalDanmuBatchItem, onProgress: (Float) -> Unit = {}): Result<LocalDanmuResource> =
        withContext(Dispatchers.IO) {
            runLocalDanmuRequest {
                check(item.expectedSessionKey.isNotBlank()) { "请先刷新当前本地库，再准备导入" }
                runtimeData.withRuntime(item.expectedSessionKey) { target ->
                    item.validationError?.let { error(it) }
                    val bytes = files.withSourceStream(item.sourceUri, item.displayName) { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (output.size().toLong() + count > LocalDanmuImportManager.CORE_MAX_UPLOAD_BYTES)
                                error("单文件不能超过 10 MB")
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }.getOrThrow()
                    if (bytes.isEmpty()) error("文件是空的：${item.displayName}")
                    val temp = File.createTempFile("danmu-import-", ".tmp", context.cacheDir)
                    try {
                        temp.writeBytes(bytes)
                        fun request(filename: String) = LocalDanmuUploadRequest(title = item.title.trim(),
                            year = requireNotNull(item.year), type = item.type, season = item.season ?: 1,
                            episode = if (item.type == LocalDanmuType.Movie) null else item.episode,
                            filename = filename, stagedFile = temp, contentLength = temp.length(), mimeType = item.mimeType,
                            expectedSessionKey = target.sessionKey)
                        var resource = repository.upload(request(item.displayName), onProgress).getOrThrow()
                        if (resource.count <= 0) {
                            val converted = normalizeForLocalLibrary(bytes, item.displayName)
                                ?: error("核心解析出 0 条弹幕，请改用 XML / JSON 格式下载后导入")
                            if (converted.size > LocalDanmuImportManager.CORE_MAX_UPLOAD_BYTES) error("转换后超过核心 10 MB 上限")
                            temp.writeBytes(converted)
                            resource = repository.upload(request(item.displayName.substringBeforeLast('.') + ".json"), onProgress).getOrThrow()
                            if (resource.count <= 0) error("转换为 JSON 后仍解析出 0 条弹幕")
                        }
                        sourceStore.save(target.libraryId, LocalDanmuSourceRef(resource.resourceKey, LocalDanmuSourceKind.File,
                            item.sourceUri, displayName = item.displayName, format = item.formatHint, sizeBytes = bytes.size.toLong()))
                        val matches = downloads.records.value.filter { it.fileUri == item.sourceUri }
                            .associate { it.id to resource.resourceKey }
                        downloads.markRecordsImported(target.libraryId, matches, mediaType = item.type.wire)
                        resource
                    } finally { temp.delete() }
                }
            }
        }

    suspend fun importRecord(record: DanmuDownloadRecord): Result<LocalDanmuResource> {
        val snapshot = repository.refresh(force = true).getOrElse { return Result.failure(it) }
        val existing = DownloadLocalImportPlanner.existingKeys(snapshot.resources)
        val actualKey = DownloadLocalImportPlanner.matches(listOf(record), snapshot)[record.id]
        val plan = DownloadLocalImportPlanner.plan(record, existing, actualKey)
        val item = plan.item?.copy(expectedSessionKey = snapshot.sessionKey)
            ?: return Result.failure(IllegalStateException(plan.blockedReason))
        if (item.importedLabel != null) {
            val key = actualKey
            val detail = repository.loadDetail(key ?: return Result.failure(IllegalStateException("已导入，跳过")), snapshot.sessionKey)
            detail.onSuccess { downloads.markRecordsImported(it.libraryId, mapOf(record.id to it.resourceKey)) }
            return detail
        }
        return upload(item).mapCatching { resource ->
            downloads.markRecordsImported(resource.libraryId, mapOf(record.id to resource.resourceKey))
            if (downloads.settings.value.deleteSourceAfterImport) downloads.deleteDownloadedFiles(setOf(record.id)).getOrThrow()
            resource
        }
    }
}

internal fun normalizeForLocalLibrary(bytes: ByteArray, fileName: String): ByteArray? {
    val format = DanmuDownloadFormat.fromFileName(fileName)
    val rows = if (format?.supportsPreview == true) {
        val preview = DanmuFilePreviewParser.parse(bytes.inputStream(), format, fileName, "", bytes.size.toLong(), 100_000)
        if (preview.truncated || preview.parseError != null) return null
        preview.items
    } else {
        val text = when {
            bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() -> bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16LE)
            bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte() -> bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16BE)
            else -> bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        }
        text.lineSequence().mapNotNull { line ->
            if (!line.startsWith("Dialogue:", ignoreCase = true)) return@mapNotNull null
            val parts = line.substringAfter(':').split(',', limit = 10)
            if (parts.size < 10) return@mapNotNull null
            val clock = parts[1].trim().split(':').mapNotNull { it.toDoubleOrNull() }
            if (clock.size != 3) return@mapNotNull null
            DanmuPreviewItem(0, clock[0] * 3600 + clock[1] * 60 + clock[2], "1", "16777215", text = parts[9].replace("\\N", "\n"))
        }.toList()
    }
    if (rows.isEmpty()) return null
    val comments = JSONArray()
    rows.forEach { row ->
        val color = row.color.removePrefix("#").let { if (row.color.startsWith('#')) it.toLongOrNull(16) else it.toLongOrNull() } ?: 16777215L
        comments.put(JSONObject().put("p", String.format(Locale.ROOT, "%.2f,%s,%d", row.timeSeconds ?: 0.0,
            row.mode.toIntOrNull() ?: 1, color)).put("m", row.text))
    }
    return JSONObject().put("comments", comments).toString().toByteArray(Charsets.UTF_8)
}
