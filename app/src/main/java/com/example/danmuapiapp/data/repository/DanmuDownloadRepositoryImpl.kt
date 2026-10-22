package com.example.danmuapiapp.data.repository

import com.example.danmuapiapp.data.network.newOutboundCall

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import com.example.danmuapiapp.domain.model.DanmuFilePreview
import com.example.danmuapiapp.domain.model.DanmuDownloadInput
import com.example.danmuapiapp.domain.model.DanmuDownloadRecord
import com.example.danmuapiapp.domain.model.DanmuDownloadResult
import com.example.danmuapiapp.domain.model.DanmuDownloadSettings
import com.example.danmuapiapp.domain.model.DanmuDownloadTask
import com.example.danmuapiapp.domain.model.DownloadDirectorySyncResult
import com.example.danmuapiapp.domain.model.DownloadConflictPolicy
import com.example.danmuapiapp.domain.model.DownloadRecordDeleteResult
import com.example.danmuapiapp.domain.model.DownloadThrottleConfig
import com.example.danmuapiapp.domain.model.DownloadQueueStatus
import com.example.danmuapiapp.domain.model.DownloadRecordStatus
import com.example.danmuapiapp.domain.model.DownloadThrottlePreset
import com.example.danmuapiapp.domain.model.toQueueTask
import com.example.danmuapiapp.domain.repository.DanmuDownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DanmuDownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient
) : DanmuDownloadRepository {

    companion object {
        private const val MAX_RECORDS = 500
        private const val MAX_QUEUE_TASKS = 1200
    }

    private val store = DanmuDownloadStore(context)

    private val _settings = MutableStateFlow(store.loadSettings())
    override val settings: StateFlow<DanmuDownloadSettings> = _settings.asStateFlow()

    private val _records = MutableStateFlow(store.loadRecords())
    override val records: StateFlow<List<DanmuDownloadRecord>> = _records.asStateFlow()

    private val _queueTasks = MutableStateFlow(store.loadQueueTasks())
    override val queueTasks: StateFlow<List<DanmuDownloadTask>> = _queueTasks.asStateFlow()

    private val recordsMutationMutex = Mutex()
    private val recordsLock = Any()
    private val directorySync = DanmuDownloadDirectorySync(
        context = context,
        recordsLock = recordsLock,
        maxRecords = MAX_RECORDS,
        currentRecords = { _records.value },
        persistRecords = { persistRecordsLocked(it) }
    )

    private data class HttpPayload(
        val code: Int,
        val bytes: ByteArray,
        val contentType: String?,
        val resolvedFormat: String?,
        val danmuCount: Int?
    )

    override fun setSaveTreeUri(uri: String, displayName: String) {
        val trimmedUri = uri.trim()
        val trimmedDisplay = displayName.trim()
        val next = _settings.value.copy(
            saveTreeUri = trimmedUri,
            saveDirDisplayName = trimmedDisplay
        )
        persistSettings(next)
    }

    override fun setSaveDirPath(path: String) {
        persistSettings(_settings.value.copy(saveDirPath = path.trim()))
    }

    override fun setImportOptions(autoImport: Boolean, deleteSource: Boolean, matchMode: String) {
        persistSettings(_settings.value.copy(autoImportToLocal = autoImport,
            deleteSourceAfterImport = deleteSource, matchMode = matchMode))
    }

    override fun markRecordsImported(libraryId: String, resourceKeys: Map<Long, String>, reconcile: Boolean, mediaType: String) {
        require(libraryId.isNotBlank())
        if (resourceKeys.isEmpty() && !reconcile) return
        synchronized(recordsLock) {
            persistRecordsLocked(_records.value.map { record ->
                reconcileLocalLibraryRecord(record, libraryId, resourceKeys[record.id], reconcile, mediaType, System.currentTimeMillis())
            })
        }
    }

    override fun noteRecord(recordId: Long, message: String) {
        synchronized(recordsLock) {
            persistRecordsLocked(_records.value.map { if (it.id == recordId) it.copy(errorMessage = message) else it })
        }
    }

    override suspend fun deleteDownloadedFiles(recordIds: Set<Long>): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            recordsMutationMutex.withLock {
                // Keep the history and imported marker; only successful imports may remove a source.
                val targets = _records.value.filter { it.id in recordIds && it.importedToLocal }
                var removed = 0
                targets.map { it.fileUri }.filter { it.isNotBlank() }.distinct().forEach { uri ->
                    when (directorySync.deleteLocalFile(uri)) {
                        LocalFileDeleteState.Deleted -> removed++
                        LocalFileDeleteState.Missing -> Unit
                        LocalFileDeleteState.Failed -> error("本地库已导入，但删除源文件失败")
                    }
                }
                removed
            }
        }
    }

    override fun removeQueueTasks(taskIds: Set<Long>) {
        persistQueueTasks(_queueTasks.value.filterNot { it.taskId in taskIds })
    }

    override fun clearSaveTreeUri() {
        val next = _settings.value.copy(saveTreeUri = "", saveDirDisplayName = "")
        persistSettings(next)
    }

    override fun setDefaultFormat(format: DanmuDownloadFormat) {
        val next = _settings.value.copy(defaultFormat = format.value)
        persistSettings(next)
    }

    override fun setFileNameTemplate(template: String) {
        val normalized = template.trim().ifBlank { DanmuDownloadSettings().fileNameTemplate }
        val next = _settings.value.copy(fileNameTemplate = normalized)
        persistSettings(next)
    }

    override fun setConflictPolicy(policy: DownloadConflictPolicy) {
        val next = _settings.value.copy(conflictPolicy = policy.key)
        persistSettings(next)
    }

    override fun setThrottlePreset(preset: DownloadThrottlePreset) {
        val next = _settings.value.copy(throttlePreset = preset.key)
        persistSettings(next)
    }

    override fun setCustomThrottleConfig(
        baseDelayMs: Long,
        jitterMaxMs: Long,
        batchSize: Int,
        batchRestMs: Long,
        backoffBaseMs: Long,
        backoffMaxMs: Long
    ) {
        val sanitized = sanitizeCustomThrottle(
            baseDelayMs = baseDelayMs,
            jitterMaxMs = jitterMaxMs,
            batchSize = batchSize,
            batchRestMs = batchRestMs,
            backoffBaseMs = backoffBaseMs,
            backoffMaxMs = backoffMaxMs
        )
        val next = _settings.value.copy(
            customBaseDelayMs = sanitized.baseDelayMs,
            customJitterMaxMs = sanitized.jitterMaxMs,
            customBatchSize = sanitized.batchSize,
            customBatchRestMs = sanitized.batchRestMs,
            customBackoffBaseMs = sanitized.backoffBaseMs,
            customBackoffMaxMs = sanitized.backoffMaxMs
        )
        persistSettings(next)
    }

    override fun enqueueTasks(inputs: List<DanmuDownloadInput>): Int {
        if (inputs.isEmpty()) return 0
        val existing = _queueTasks.value.toMutableList()
        val activeKeys = existing
            .filter { task ->
                val status = task.statusEnum()
                status == DownloadQueueStatus.Pending || status == DownloadQueueStatus.Running
            }
            .map { task ->
                "${task.episodeId}|${task.source}|${task.format}|${task.directUrl}"
            }
            .toMutableSet()

        var baseId = System.currentTimeMillis() * 1000
        var added = 0
        inputs.forEach { input ->
            val key = "${input.episodeId}|${input.source}|${input.format.value}|${input.directUrl}"
            if (!activeKeys.add(key)) return@forEach
            baseId += 1
            existing += input.toQueueTask(baseId)
            added++
        }
        if (added > 0) {
            persistQueueTasks(existing.takeLast(MAX_QUEUE_TASKS))
        }
        return added
    }

    override fun setQueueTaskStatus(
        taskId: Long,
        status: DownloadQueueStatus,
        detail: String,
        incrementAttempt: Boolean
    ) {
        val now = System.currentTimeMillis()
        updateQueueTasks({ task -> task.taskId == taskId }) { task ->
            task.copy(
                updatedAt = now,
                status = status.key,
                attempts = if (incrementAttempt) task.attempts + 1 else task.attempts,
                lastDetail = detail.ifBlank { task.lastDetail }
            )
        }
    }

    override fun updateQueueTaskInput(
        taskId: Long,
        input: DanmuDownloadInput,
        detail: String
    ): Boolean {
        val now = System.currentTimeMillis()
        val updated = updateQueueTasks({ task -> task.taskId == taskId }) { task ->
            task.copy(
                updatedAt = now,
                apiBaseUrl = input.apiBaseUrl.trim(),
                animeTitle = input.animeTitle,
                episodeTitle = input.episodeTitle,
                episodeId = input.episodeId,
                episodeNo = input.episodeNo,
                source = input.source,
                format = input.format.value,
                fileNameTemplate = input.fileNameTemplate,
                conflictPolicy = input.conflictPolicy.key,
                animeId = input.animeId,
                directUrl = input.directUrl,
                mediaType = input.mediaType,
                lastDetail = detail.ifBlank { task.lastDetail }
            )
        }
        return updated > 0
    }

    override fun setQueueTaskRetryNotBefore(taskId: Long, timestampMs: Long) {
        val now = System.currentTimeMillis()
        updateQueueTasks({ task -> task.taskId == taskId }) { task ->
            task.copy(
                updatedAt = now,
                retryNotBeforeAt = timestampMs.coerceAtLeast(0L)
            )
        }
    }

    override fun resetQueueTasks(taskIds: Set<Long>, detail: String): Int {
        if (taskIds.isEmpty()) return 0
        val now = System.currentTimeMillis()
        return updateQueueTasks({ task -> taskIds.contains(task.taskId) }) { task ->
            task.copy(
                updatedAt = now,
                status = DownloadQueueStatus.Pending.key,
                lastDetail = detail
            )
        }
    }

    override fun markRunningTasksAsPending(detail: String): Int {
        val now = System.currentTimeMillis()
        return updateQueueTasks({ task -> task.statusEnum() == DownloadQueueStatus.Running }) { task ->
            task.copy(
                updatedAt = now,
                status = DownloadQueueStatus.Pending.key,
                lastDetail = detail
            )
        }
    }

    /**
     * 按条件就地改写队列任务：返回命中条数，命中即落盘，一条都没命中时不写。
     * 原先六个队列更新方法各写一遍同样的「map + changed 标记 + 落盘」，这里统一成一份。
     */
    private fun updateQueueTasks(
        matches: (DanmuDownloadTask) -> Boolean,
        transform: (DanmuDownloadTask) -> DanmuDownloadTask
    ): Int {
        var count = 0
        val next = _queueTasks.value.map { task ->
            if (!matches(task)) {
                task
            } else {
                count++
                transform(task)
            }
        }
        if (count > 0) {
            persistQueueTasks(next)
        }
        return count
    }

    override fun clearQueueTasks() {
        persistQueueTasks(emptyList())
    }

    override fun clearCompletedQueueTasks(): Int {
        val filtered = _queueTasks.value.filter { task ->
            when (task.statusEnum()) {
                DownloadQueueStatus.Success,
                DownloadQueueStatus.Skipped,
                DownloadQueueStatus.Canceled -> false
                DownloadQueueStatus.Pending,
                DownloadQueueStatus.Running,
                DownloadQueueStatus.Failed -> true
            }
        }
        val removed = _queueTasks.value.size - filtered.size
        if (removed > 0) {
            persistQueueTasks(filtered)
        }
        return removed
    }

    override fun reorderQueueTasks(reorderedTasks: List<DanmuDownloadTask>) {
        persistQueueTasks(reorderedTasks)
    }

    override suspend fun downloadEpisode(
        input: DanmuDownloadInput,
        onProgress: (Float, String) -> Unit
    ): Result<DanmuDownloadResult> {
        val startedAt = System.currentTimeMillis()
        return runCatching {
            val currentSettings = _settings.value
            val root = directorySync.outputDirectory(currentSettings)
            if (!root.canWrite()) {
                error("保存目录不可写，请重新授权目录权限")
            }

            onProgress(0.08f, "正在请求弹幕数据")
            val requestUrl = buildCommentUrl(input)
            val responsePayload = requestDanmuPayload(requestUrl)
            val normalizedPayload = normalizePayloadIfNeeded(input, responsePayload.bytes)
            val resolvedFormat = responsePayload.resolvedFormat
                ?.trim()
                ?.lowercase()
                .orEmpty()
            if (resolvedFormat.isNotBlank() && resolvedFormat != input.format.value) {
                error(
                    "核心实际返回格式为 $resolvedFormat，与请求的 ${input.format.value} 不一致"
                )
            }
            val inspection = DanmuPayloadInspector.inspect(
                payload = normalizedPayload,
                format = input.format,
                contentType = responsePayload.contentType
            )
            if (!inspection.valid) error(inspection.error)
            val danmuCount = responsePayload.danmuCount ?: inspection.count
            val httpCode = responsePayload.code
            val validationWarning = inspection.warning

            onProgress(0.50f, "正在准备输出目录")
            val preferredDirName = com.example.danmuapiapp.domain.model.sanitizeDownloadFileName(com.example.danmuapiapp.domain.model.cleanDownloadAnimeTitle(input.animeTitle)).ifBlank { "未命名剧集" }
            val directories = root.listFiles().filter { it.isDirectory }
            val animeDirName = DownloadRecordRecovery.seriesDirectory(
                preferredDirName, com.example.danmuapiapp.domain.model.sanitizeDownloadFileName(input.animeTitle), directories.mapNotNull { it.name }
            )
            val animeDir = directories.firstOrNull { it.name == animeDirName } ?: root.createDirectory(animeDirName)
                ?: error("无法创建剧集目录：$animeDirName")

            val template = input.fileNameTemplate.trim().ifBlank { DanmuDownloadSettings().fileNameTemplate }
            val desiredName = buildOutputFileName(template, input)
            val resolvedName = resolveOutputFileName(animeDir, desiredName, input.conflictPolicy)
            if (resolvedName == null) {
                val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
                val skipped = DanmuDownloadResult(
                    status = DownloadRecordStatus.Skipped,
                    fileName = desiredName,
                    relativePath = "$animeDirName/$desiredName",
                    fileUri = "",
                    bytes = 0L,
                    durationMs = elapsed,
                    httpCode = httpCode,
                    errorMessage = listOfNotNull(
                        "文件已存在，按策略跳过",
                        validationWarning
                    ).joinToString("；")
                )
                appendRecord(buildRecord(input, skipped))
                return@runCatching skipped
            }

            onProgress(0.72f, "正在写入文件")
            val targetFile = if (animeDir.uri.scheme == "file") {
                val file = java.io.File(requireNotNull(animeDir.uri.path), resolvedName)
                if (!file.createNewFile()) error("创建文件失败：$resolvedName")
                DocumentFile.fromFile(file)
            } else animeDir.createFile(input.format.mimeType, resolvedName)
                ?: error("创建文件失败：$resolvedName")
            writePayload(targetFile.uri, normalizedPayload)

            onProgress(1.0f, "下载完成")
            val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
            val success = DanmuDownloadResult(
                status = DownloadRecordStatus.Success,
                fileName = resolvedName,
                relativePath = "$animeDirName/$resolvedName",
                fileUri = targetFile.uri.toString(),
                bytes = normalizedPayload.size.toLong(),
                durationMs = elapsed,
                danmuCount = danmuCount,
                httpCode = httpCode,
                errorMessage = validationWarning
            )
            appendRecord(buildRecord(input, success))
            success
        }.onFailure { throwable ->
            val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
            val failed = DanmuDownloadResult(
                status = DownloadRecordStatus.Failed,
                fileName = "",
                relativePath = "",
                fileUri = "",
                bytes = 0L,
                durationMs = elapsed,
                errorMessage = throwable.message ?: "下载失败"
            )
            appendRecord(buildRecord(input, failed))
        }
    }

    private fun buildRecord(
        input: DanmuDownloadInput,
        result: DanmuDownloadResult
    ): DanmuDownloadRecord {
        return DanmuDownloadRecord(
            id = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            animeTitle = input.animeTitle,
            episodeTitle = input.episodeTitle,
            episodeId = input.episodeId,
            episodeNo = input.episodeNo,
            source = input.source,
            format = input.format.value,
            status = result.status.key,
            fileName = result.fileName,
            relativePath = result.relativePath,
            fileUri = result.fileUri,
            durationMs = result.durationMs,
            bytes = result.bytes,
            danmuCount = result.danmuCount,
            httpCode = result.httpCode,
            errorMessage = result.errorMessage,
            animeId = input.animeId,
            mediaType = input.mediaType
        )
    }

    override suspend fun loadDanmuPreview(
        record: DanmuDownloadRecord,
        previewLimit: Int
    ): Result<DanmuFilePreview> {
        return runCatching {
            if (record.statusEnum() != DownloadRecordStatus.Success) {
                error("只有下载成功的记录可以查看弹幕内容")
            }
            val fileUriText = record.fileUri.trim()
            if (fileUriText.isBlank()) {
                error("该记录没有可读取的文件地址")
            }
            val format = record.formatOrNull()
                ?: error("该记录的格式 ${record.format} 不受当前版本支持")
            if (!format.supportsPreview) {
                error("${format.label} 是二进制格式，不支持内容预览")
            }
            val stream = context.contentResolver.openInputStream(fileUriText.toUri())
                ?: error("无法打开文件，可能已被移动或目录权限失效")
            stream.use { input ->
                val preview = DanmuFilePreviewParser.parse(
                    input = input,
                    format = format,
                    fileName = record.fileName,
                    relativePath = record.relativePath,
                    bytes = record.bytes,
                    previewLimit = previewLimit
                )
                updateRecordDanmuCount(record.id, preview.count)
                preview
            }
        }
    }

    override suspend fun syncExistingFiles(): Result<DownloadDirectorySyncResult> {
        return withContext(Dispatchers.IO) {
            runCatching {
                recordsMutationMutex.withLock {
                    directorySync.sync(settings.value)
                }
            }
        }
    }

    override suspend fun availableDownloadRecordIds(records: List<DanmuDownloadRecord>): Result<Set<Long>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val presence = mutableMapOf<String, Boolean>()
                records.filter { record ->
                    if (record.statusEnum() != DownloadRecordStatus.Success) return@filter false
                    if (record.importedToLocal) return@filter true
                    val text = record.fileUri.trim()
                    if (text.isEmpty()) return@filter false
                    presence.getOrPut(text) {
                        val uri = text.toUri()
                        if (uri.scheme == "file") {
                            val file = java.io.File(requireNotNull(uri.path))
                            if (!file.exists() && file.parentFile?.canRead() == false) {
                                error("下载目录无法访问，请检查权限后重试")
                            }
                            file.isFile && file.length() > 0L
                        } else {
                            context.contentResolver.query(uri, arrayOf(
                                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                                DocumentsContract.Document.COLUMN_SIZE
                            ), null, null, null)?.use { cursor ->
                                cursor.moveToFirst() && (cursor.isNull(1) || cursor.getLong(1) > 0L)
                            } ?: error("无法读取下载文件信息，请检查目录权限")
                        }
                    }
                }.map { it.id }.toSet()
            }
        }

    override suspend fun deleteRecords(
        recordIds: Set<Long>,
        deleteLocalFiles: Boolean
    ): Result<DownloadRecordDeleteResult> {
        return withContext(Dispatchers.IO) {
            runCatching {
                recordsMutationMutex.withLock {
                    deleteRecordsLocked(recordIds, deleteLocalFiles)
                }
            }
        }
    }

    private fun deleteRecordsLocked(
        recordIds: Set<Long>,
        deleteLocalFiles: Boolean
    ): DownloadRecordDeleteResult {
        if (recordIds.isEmpty()) {
            return DownloadRecordDeleteResult(0, 0, 0, 0, 0, 0)
        }

        val snapshot = _records.value
        val selected = snapshot.filter { recordIds.contains(it.id) }
        if (selected.isEmpty()) {
            return DownloadRecordDeleteResult(0, 0, 0, 0, 0, 0)
        }

        var requestedFiles = 0
        var deletedFiles = 0
        var missingFiles = 0
        var failedFiles = 0
        var retainedSharedFiles = 0
        if (deleteLocalFiles) {
            val retainedUris = snapshot
                .asSequence()
                .filterNot { recordIds.contains(it.id) }
                .map { it.fileUri.trim() }
                .filter(String::isNotBlank)
                .toSet()
            val selectedUris = selected
                .asSequence()
                .map { it.fileUri.trim() }
                .filter(String::isNotBlank)
                .distinct()
                .toList()

            selectedUris.forEach { uriText ->
                if (retainedUris.contains(uriText)) {
                    retainedSharedFiles++
                    return@forEach
                }
                requestedFiles++
                when (directorySync.deleteLocalFile(uriText)) {
                    LocalFileDeleteState.Deleted -> deletedFiles++
                    LocalFileDeleteState.Missing -> missingFiles++
                    LocalFileDeleteState.Failed -> failedFiles++
                }
            }
        }

        val selectedIds = selected.mapTo(mutableSetOf()) { it.id }
        synchronized(recordsLock) {
            persistRecordsLocked(_records.value.filterNot { selectedIds.contains(it.id) })
        }
        return DownloadRecordDeleteResult(
            removedRecords = selected.size,
            requestedFiles = requestedFiles,
            deletedFiles = deletedFiles,
            missingFiles = missingFiles,
            failedFiles = failedFiles,
            retainedSharedFiles = retainedSharedFiles
        )
    }

    private fun requestDanmuPayload(url: String): HttpPayload {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        httpClient.newOutboundCall(request).execute().use { response ->
            val code = response.code
            val body = response.body
            if (code !in 200..299) {
                val bodyText = runCatching { body.string() }.getOrDefault("")
                val tail = bodyText.trim().take(180)
                if (tail.isBlank()) {
                    error("请求失败：HTTP $code")
                } else {
                    error("请求失败：HTTP $code，$tail")
                }
            }
            val contentType = body.contentType()?.toString() ?: response.header("Content-Type")
            val bytes = body.bytes()
            return HttpPayload(
                code = code,
                bytes = bytes,
                contentType = contentType,
                resolvedFormat = response.header("X-Danmu-Format"),
                danmuCount = response.header("X-Danmu-Count")
                    ?.trim()
                    ?.toIntOrNull()
                    ?.takeIf { it >= 0 }
            )
        }
    }

    private fun buildCommentUrl(input: DanmuDownloadInput): String {
        return buildDanmuCommentUrl(
            apiBaseUrl = input.apiBaseUrl,
            format = input.format,
            episodeId = input.episodeId,
            directUrl = input.directUrl
        )
    }

    private fun writePayload(fileUri: Uri, payload: ByteArray) {
        val stream = context.contentResolver.openOutputStream(fileUri, "w")
            ?: error("无法写入文件")
        stream.use { output ->
            output.write(payload)
            output.flush()
        }
    }

    private fun normalizePayloadIfNeeded(input: DanmuDownloadInput, payload: ByteArray): ByteArray {
        if (input.format != DanmuDownloadFormat.Json) return payload

        val rawText = payload.toString(Charsets.UTF_8)
        if (rawText.isBlank()) return payload
        val cleanedText = rawText.removePrefix("\uFEFF").trim()
        if (cleanedText.isBlank()) return payload

        val prettyText = runCatching {
            when (val node = JSONTokener(cleanedText).nextValue()) {
                is JSONObject -> node.toString(2)
                is JSONArray -> node.toString(2)
                else -> cleanedText
            }
        }.getOrElse { return payload }

        return (prettyText.trimEnd() + "\n").toByteArray(Charsets.UTF_8)
    }

    private fun buildOutputFileName(template: String, input: DanmuDownloadInput): String =
        com.example.danmuapiapp.domain.model.renderDownloadFileName(
            template, input.format, input.animeTitle, input.episodeTitle, input.episodeNo, input.episodeId, input.source
        )

    private fun resolveOutputFileName(
        parent: DocumentFile,
        desiredName: String,
        policy: DownloadConflictPolicy
    ): String? {
        val existing = parent.findFile(desiredName)
        if (existing == null) return desiredName

        return when (policy) {
            DownloadConflictPolicy.Skip -> null
            DownloadConflictPolicy.Overwrite -> {
                if (!existing.delete()) {
                    error("无法覆盖已存在文件：$desiredName")
                }
                desiredName
            }
            DownloadConflictPolicy.Rename -> {
                var index = 1
                while (index < 9999) {
                    val candidate = com.example.danmuapiapp.domain.model.numberedDownloadFileName(desiredName, index)
                    if (parent.findFile(candidate) == null) return candidate
                    index++
                }
                error("文件重命名次数超限：$desiredName")
            }
        }
    }

    private fun sanitizeCustomThrottle(
        baseDelayMs: Long,
        jitterMaxMs: Long,
        batchSize: Int,
        batchRestMs: Long,
        backoffBaseMs: Long,
        backoffMaxMs: Long
    ): DownloadThrottleConfig {
        val baseDelay = baseDelayMs.coerceIn(100L, 120_000L)
        val jitter = jitterMaxMs.coerceIn(0L, 20_000L)
        val batch = batchSize.coerceIn(1, 500)
        val batchRest = batchRestMs.coerceIn(0L, 900_000L)
        val backoffBase = backoffBaseMs.coerceIn(1_000L, 900_000L)
        val backoffMax = backoffMaxMs.coerceIn(backoffBase, 1_800_000L)
        return DownloadThrottleConfig(
            preset = DownloadThrottlePreset.Custom,
            baseDelayMs = baseDelay,
            jitterMaxMs = jitter,
            batchSize = batch,
            batchRestMs = batchRest,
            backoffBaseMs = backoffBase,
            backoffMaxMs = backoffMax
        )
    }

    private fun persistSettings(next: DanmuDownloadSettings) {
        _settings.value = next
        store.persistSettings(next)
    }

    private fun persistRecordsLocked(records: List<DanmuDownloadRecord>) {
        _records.value = records
        store.persistRecords(records)
    }

    private fun persistQueueTasks(tasks: List<DanmuDownloadTask>) {
        _queueTasks.value = tasks
        store.persistQueueTasks(tasks)
    }

    private fun appendRecord(record: DanmuDownloadRecord) {
        synchronized(recordsLock) {
            val merged = listOf(record) + _records.value
            persistRecordsLocked(merged.take(MAX_RECORDS))
        }
    }

    private fun updateRecordDanmuCount(recordId: Long, count: Int) {
        synchronized(recordsLock) {
            val current = _records.value
            val next = current.map { record ->
                if (record.id == recordId && record.danmuCount != count) {
                    record.copy(danmuCount = count)
                } else {
                    record
                }
            }
            if (next != current) {
                persistRecordsLocked(next)
            }
        }
    }

}

/**
 * 链接解析得到的剧集没有真实 bangumi 集号，直接带 original URL 调用弹幕接口。
 * 普通剧集仍走 /api/v2/comment/{episodeId}。
 */
internal fun buildDanmuCommentUrl(
    apiBaseUrl: String,
    format: DanmuDownloadFormat,
    episodeId: Long,
    directUrl: String
): String {
    val base = apiBaseUrl.trim().trimEnd('/')
    val encodedFormat = URLEncoder.encode(format.value, Charsets.UTF_8.name())
    val link = directUrl.trim()
    if (link.isNotBlank()) {
        val encodedUrl = URLEncoder.encode(link, Charsets.UTF_8.name())
        return "$base/api/v2/comment?url=$encodedUrl&format=$encodedFormat"
    }
    return "$base/api/v2/comment/$episodeId?format=$encodedFormat"
}
