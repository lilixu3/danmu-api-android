package com.example.danmuapiapp.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.ArrayDeque
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DanmuDownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient
) : DanmuDownloadRepository {

    companion object {
        private const val PREFS_NAME = "danmu_download"
        private const val KEY_SAVE_TREE_URI = "save_tree_uri"
        private const val KEY_SAVE_DIR_DISPLAY = "save_dir_display"
        private const val KEY_DEFAULT_FORMAT = "default_format"
        private const val KEY_FILE_TEMPLATE = "file_template"
        private const val KEY_CONFLICT_POLICY = "conflict_policy"
        private const val KEY_THROTTLE_PRESET = "throttle_preset"
        private const val KEY_THROTTLE_CUSTOM_BASE_DELAY = "throttle_custom_base_delay_ms"
        private const val KEY_THROTTLE_CUSTOM_JITTER = "throttle_custom_jitter_ms"
        private const val KEY_THROTTLE_CUSTOM_BATCH_SIZE = "throttle_custom_batch_size"
        private const val KEY_THROTTLE_CUSTOM_BATCH_REST = "throttle_custom_batch_rest_ms"
        private const val KEY_THROTTLE_CUSTOM_BACKOFF_BASE = "throttle_custom_backoff_base_ms"
        private const val KEY_THROTTLE_CUSTOM_BACKOFF_MAX = "throttle_custom_backoff_max_ms"
        private const val KEY_RECORDS_JSON = "records_json"
        private const val KEY_QUEUE_JSON = "queue_json"
        private const val MAX_RECORDS = 500
        private const val MAX_QUEUE_TASKS = 1200
        private const val MAX_SCAN_VISITED_FILES = 2_000
        private const val MAX_SCAN_DEPTH = 8
        private const val MAX_SCAN_INSPECT_BYTES = 16L * 1024L * 1024L
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _settings = MutableStateFlow(loadSettings())
    override val settings: StateFlow<DanmuDownloadSettings> = _settings.asStateFlow()

    private val _records = MutableStateFlow(loadRecords())
    override val records: StateFlow<List<DanmuDownloadRecord>> = _records.asStateFlow()

    private val _queueTasks = MutableStateFlow(loadQueueTasks())
    override val queueTasks: StateFlow<List<DanmuDownloadTask>> = _queueTasks.asStateFlow()

    private val recordsMutationMutex = Mutex()
    private val recordsLock = Any()

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
                "${task.episodeId}|${task.source}|${task.format}"
            }
            .toMutableSet()

        var baseId = System.currentTimeMillis() * 1000
        var added = 0
        inputs.forEach { input ->
            val key = "${input.episodeId}|${input.source}|${input.format.value}"
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
        var changed = false
        val next = _queueTasks.value.map { task ->
            if (task.taskId != taskId) return@map task
            changed = true
            task.copy(
                updatedAt = now,
                status = status.key,
                attempts = if (incrementAttempt) task.attempts + 1 else task.attempts,
                lastDetail = detail.ifBlank { task.lastDetail }
            )
        }
        if (changed) {
            persistQueueTasks(next)
        }
    }

    override fun updateQueueTaskInput(
        taskId: Long,
        input: DanmuDownloadInput,
        detail: String
    ): Boolean {
        val now = System.currentTimeMillis()
        var changed = false
        val next = _queueTasks.value.map { task ->
            if (task.taskId != taskId) return@map task
            changed = true
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
                lastDetail = detail.ifBlank { task.lastDetail }
            )
        }
        if (changed) {
            persistQueueTasks(next)
        }
        return changed
    }

    override fun setQueueTaskRetryNotBefore(taskId: Long, timestampMs: Long) {
        val now = System.currentTimeMillis()
        var changed = false
        val next = _queueTasks.value.map { task ->
            if (task.taskId != taskId) return@map task
            changed = true
            task.copy(
                updatedAt = now,
                retryNotBeforeAt = timestampMs.coerceAtLeast(0L)
            )
        }
        if (changed) {
            persistQueueTasks(next)
        }
    }

    override fun resetQueueTasks(taskIds: Set<Long>, detail: String): Int {
        if (taskIds.isEmpty()) return 0
        val now = System.currentTimeMillis()
        var count = 0
        val next = _queueTasks.value.map { task ->
            if (!taskIds.contains(task.taskId)) return@map task
            count++
            task.copy(
                updatedAt = now,
                status = DownloadQueueStatus.Pending.key,
                lastDetail = detail
            )
        }
        if (count > 0) {
            persistQueueTasks(next)
        }
        return count
    }

    override fun markRunningTasksAsPending(detail: String): Int {
        val now = System.currentTimeMillis()
        var count = 0
        val next = _queueTasks.value.map { task ->
            if (task.statusEnum() != DownloadQueueStatus.Running) return@map task
            count++
            task.copy(
                updatedAt = now,
                status = DownloadQueueStatus.Pending.key,
                lastDetail = detail
            )
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
            val treeUriText = currentSettings.saveTreeUri.trim()
            if (treeUriText.isBlank()) {
                error("请先在下载设置中选择保存目录")
            }

            val treeUri = treeUriText.toUri()
            val root = DocumentFile.fromTreeUri(context, treeUri)
                ?: error("保存目录无效，请重新选择")
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

            onProgress(0.50f, "正在准备输出目录")
            val animeDirName = sanitizeFileComponent(input.animeTitle).ifBlank { "未命名剧集" }
            val animeDir = findOrCreateDirectory(root, animeDirName)
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
                    errorMessage = "文件已存在，按策略跳过"
                )
                appendRecord(buildRecord(input, skipped))
                return@runCatching skipped
            }

            onProgress(0.72f, "正在写入文件")
            val targetFile = animeDir.createFile(input.format.mimeType, resolvedName)
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
                httpCode = httpCode
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
            animeId = input.animeId
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
                    syncExistingFilesLocked()
                }
            }
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
                when (deleteLocalFile(uriText)) {
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

    private fun deleteLocalFile(uriText: String): LocalFileDeleteState {
        val uri = runCatching { uriText.toUri() }.getOrNull()
            ?: return LocalFileDeleteState.Failed
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

    private enum class LocalFileDeleteState {
        Deleted,
        Missing,
        Failed
    }

    private fun syncExistingFilesLocked(): DownloadDirectorySyncResult {
        val treeUriText = _settings.value.saveTreeUri.trim()
        if (treeUriText.isBlank()) error("请先选择下载目录")
        val root = DocumentFile.fromTreeUri(context, treeUriText.toUri())
            ?: error("下载目录无效，请重新选择")
        if (!root.canRead()) error("下载目录不可读，请重新授权目录权限")

        val existingUris = _records.value
            .map { it.fileUri.trim() }
            .filter(String::isNotBlank)
            .toMutableSet()
        val candidates = mutableListOf<DirectoryFileCandidate>()
        val pendingDirectories = ArrayDeque<DirectoryScanNode>()
        val visitedDirectories = mutableSetOf<String>()
        pendingDirectories.add(DirectoryScanNode(root, relativePath = "", depth = 0))
        var visitedFiles = 0
        var skippedFiles = 0
        var truncated = false

        while (pendingDirectories.isNotEmpty() && visitedFiles < MAX_SCAN_VISITED_FILES) {
            val node = pendingDirectories.removeFirst()
            if (!visitedDirectories.add(node.directory.uri.toString())) continue
            val children = runCatching { node.directory.listFiles().toList() }
                .getOrElse {
                    Log.w("DanmuDownloadRepo", "扫描目录失败：${node.relativePath}", it)
                    emptyList()
                }
            for (child in children) {
                val name = child.name?.trim().orEmpty()
                if (name.isBlank()) continue
                val relativePath = if (node.relativePath.isBlank()) {
                    name
                } else {
                    "${node.relativePath}/$name"
                }
                if (child.isDirectory) {
                    if (node.depth < MAX_SCAN_DEPTH) {
                        pendingDirectories.add(
                            DirectoryScanNode(child, relativePath, node.depth + 1)
                        )
                    }
                    continue
                }
                if (!child.isFile) continue
                visitedFiles++
                if (visitedFiles > MAX_SCAN_VISITED_FILES) {
                    truncated = true
                    break
                }
                val format = DanmuDownloadFormat.fromFileName(name)
                if (format == null) {
                    skippedFiles++
                    continue
                }
                val uriText = child.uri.toString()
                if (uriText in existingUris) {
                    skippedFiles++
                    continue
                }
                candidates += DirectoryFileCandidate(
                    file = child,
                    format = format,
                    relativePath = relativePath,
                    lastModified = child.lastModified().takeIf { it > 0L } ?: 0L,
                    bytes = child.length().coerceAtLeast(0L)
                )
            }
        }
        if (pendingDirectories.isNotEmpty() || visitedFiles >= MAX_SCAN_VISITED_FILES) {
            truncated = true
        }

        val now = System.currentTimeMillis()
        val imported = mutableListOf<DanmuDownloadRecord>()
        val sortedCandidates = candidates
            .sortedWith(
                compareByDescending<DirectoryFileCandidate> { it.lastModified }
                    .thenBy { it.relativePath }
            )
        if (sortedCandidates.size > MAX_RECORDS) {
            skippedFiles += sortedCandidates.size - MAX_RECORDS
            truncated = true
        }
        sortedCandidates
            .take(MAX_RECORDS)
            .forEach { candidate ->
                val inspection = inspectDirectoryFile(candidate)
                if (inspection == null || !inspection.valid) {
                    skippedFiles++
                    return@forEach
                }
                val record = candidate.toDownloadRecord(
                    createdAtFallback = now,
                    danmuCount = inspection.count
                )
                if (existingUris.add(record.fileUri)) imported += record
            }

        var persistedImportedCount = 0
        if (imported.isNotEmpty()) {
            synchronized(recordsLock) {
                val merged = (_records.value + imported)
                    .distinctBy { record ->
                        record.fileUri.trim().takeIf(String::isNotBlank) ?: "record:${record.id}"
                    }
                    .sortedByDescending { it.createdAt }
                    .take(MAX_RECORDS)
                val mergedUris = merged.mapTo(mutableSetOf()) { it.fileUri }
                persistedImportedCount = imported.count { it.fileUri in mergedUris }
                persistRecordsLocked(merged)
            }
        }

        return DownloadDirectorySyncResult(
            scannedFiles = visitedFiles.coerceAtMost(MAX_SCAN_VISITED_FILES),
            importedRecords = persistedImportedCount,
            skippedFiles = skippedFiles,
            truncated = truncated
        )
    }

    private fun inspectDirectoryFile(candidate: DirectoryFileCandidate): DanmuPayloadInspection? {
        val payload = if (candidate.bytes in 0..MAX_SCAN_INSPECT_BYTES) {
            readFileWithinLimit(candidate.file.uri, MAX_SCAN_INSPECT_BYTES)
        } else {
            null
        }
        if (payload != null) {
            return DanmuPayloadInspector.inspect(payload, candidate.format)
        }
        val prefix = readFilePrefix(candidate.file.uri, 64 * 1024) ?: return null
        return DanmuPayloadInspection(valid = isPlausiblePayloadPrefix(prefix, candidate.format))
    }

    private fun readFileWithinLimit(uri: Uri, limit: Long): ByteArray? {
        val stream = context.contentResolver.openInputStream(uri) ?: return null
        return stream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) return@use null
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

    private fun readFilePrefix(uri: Uri, limit: Int): ByteArray? {
        val stream = context.contentResolver.openInputStream(uri) ?: return null
        return stream.use { input ->
            val output = ByteArrayOutputStream(limit)
            val buffer = ByteArray(minOf(DEFAULT_BUFFER_SIZE, limit))
            var remaining = limit
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (read < 0) break
                output.write(buffer, 0, read)
                remaining -= read
            }
            output.toByteArray()
        }
    }

    private fun isPlausiblePayloadPrefix(
        prefix: ByteArray,
        format: DanmuDownloadFormat
    ): Boolean {
        if (format == DanmuDownloadFormat.DanuniBinPb) return true
        val text = prefix.toString(Charsets.UTF_8).removePrefix("\uFEFF").trimStart()
        return when (format) {
            DanmuDownloadFormat.Xml,
            DanmuDownloadFormat.BiliXml -> text.startsWith("<?xml") || text.startsWith("<i")
            DanmuDownloadFormat.Json,
            DanmuDownloadFormat.DdplayJson -> text.startsWith("{") && text.contains("\"comments\"")
            DanmuDownloadFormat.ArtplayerJson,
            DanmuDownloadFormat.VodJson -> text.startsWith("{") && text.contains("\"danmuku\"")
            DanmuDownloadFormat.BahaJson ->
                text.startsWith("{") && text.contains("\"data\"") && text.contains("\"danmu\"")
            DanmuDownloadFormat.DanuniJson -> text.startsWith("[")
            DanmuDownloadFormat.DplayerJson -> text.startsWith("{") && text.contains("\"data\"")
            DanmuDownloadFormat.DanuniBinPb -> true
        }
    }

    private fun DirectoryFileCandidate.toDownloadRecord(
        createdAtFallback: Long,
        danmuCount: Int?
    ): DanmuDownloadRecord {
        val fileName = file.name?.trim().orEmpty().ifBlank { "已有弹幕.${format.extension}" }
        val suffixLength = format.extension.length + 1
        val baseName = if (
            fileName.length > suffixLength &&
            fileName.lowercase().endsWith(".${format.extension}")
        ) {
            fileName.dropLast(suffixLength)
        } else {
            fileName.substringBeforeLast('.', fileName)
        }
        val parentPath = relativePath.substringBeforeLast('/', missingDelimiterValue = "")
        val animeTitle = parentPath.substringAfterLast('/').ifBlank { "已有弹幕" }
        val episodeNo = extractEpisodeNumber(baseName)
        val uriText = file.uri.toString()
        return DanmuDownloadRecord(
            id = stableDirectoryRecordId(uriText),
            createdAt = lastModified.takeIf { it > 0L } ?: createdAtFallback,
            animeTitle = animeTitle,
            episodeTitle = baseName.ifBlank { fileName },
            episodeId = 0L,
            episodeNo = episodeNo,
            source = "目录同步",
            format = format.value,
            status = DownloadRecordStatus.Success.key,
            fileName = fileName,
            relativePath = relativePath,
            fileUri = uriText,
            durationMs = 0L,
            bytes = bytes,
            danmuCount = danmuCount,
            httpCode = null,
            errorMessage = null
        )
    }

    private fun extractEpisodeNumber(fileName: String): Int {
        val patterns = listOf(
            Regex("(?i)E(?:P)?(\\d{1,4})"),
            Regex("第\\s*(\\d{1,4})\\s*[集话]")
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(fileName)?.groupValues?.getOrNull(1)?.toIntOrNull()
        } ?: 0
    }

    private fun stableDirectoryRecordId(uri: String): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray(Charsets.UTF_8))
        val value = ByteBuffer.wrap(digest).long and Long.MAX_VALUE
        return value.takeIf { it != 0L } ?: 1L
    }

    private data class DirectoryScanNode(
        val directory: DocumentFile,
        val relativePath: String,
        val depth: Int
    )

    private data class DirectoryFileCandidate(
        val file: DocumentFile,
        val format: DanmuDownloadFormat,
        val relativePath: String,
        val lastModified: Long,
        val bytes: Long
    )

    private fun requestDanmuPayload(url: String): HttpPayload {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
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
        val base = input.apiBaseUrl.trim().trimEnd('/')
        val encodedFormat = URLEncoder.encode(input.format.value, Charsets.UTF_8.name())
        return "$base/api/v2/comment/${input.episodeId}?format=$encodedFormat"
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

    private fun buildOutputFileName(template: String, input: DanmuDownloadInput): String {
        val fallbackTemplate = DanmuDownloadSettings().fileNameTemplate
        var output = template.ifBlank { fallbackTemplate }
        val date = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val dateTime = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val mapping = linkedMapOf(
            "animeTitle" to input.animeTitle,
            "episodeTitle" to input.episodeTitle,
            "episodeNo" to input.episodeNo.toString(),
            "episodeNo2" to input.episodeNo.toString().padStart(2, '0'),
            "episodeNo3" to input.episodeNo.toString().padStart(3, '0'),
            "episodeId" to input.episodeId.toString(),
            "source" to input.source.ifBlank { "unknown" },
            "format" to input.format.value,
            "ext" to input.format.extension,
            "date" to date,
            "datetime" to dateTime
        )
        mapping.forEach { (key, value) ->
            output = output.replace("{$key}", value)
        }
        var sanitized = sanitizeFileComponent(output)
        if (sanitized.isBlank()) {
            sanitized = "episode_${input.episodeId}.${input.format.extension}"
        }
        if (!sanitized.contains('.')) {
            sanitized += ".${input.format.extension}"
        }
        return sanitized
    }

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
                val (baseName, ext) = splitFileName(desiredName)
                var index = 1
                while (index < 9999) {
                    val candidate = if (ext.isBlank()) {
                        "$baseName($index)"
                    } else {
                        "$baseName($index).$ext"
                    }
                    if (parent.findFile(candidate) == null) return candidate
                    index++
                }
                error("文件重命名次数超限：$desiredName")
            }
        }
    }

    private fun splitFileName(name: String): Pair<String, String> {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.lastIndex) {
            return name to ""
        }
        return name.substring(0, dot) to name.substring(dot + 1)
    }

    private fun findOrCreateDirectory(parent: DocumentFile, name: String): DocumentFile? {
        parent.listFiles().firstOrNull { it.isDirectory && it.name == name }?.let { return it }
        return parent.createDirectory(name)
    }

    private fun sanitizeFileComponent(raw: String): String {
        val replaced = raw
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("[\\u0000-\\u001F\\u007F]"), "_")
            .replace('\n', '_')
            .replace('\r', '_')
            .trim()
            .trim('.')
        val collapsed = replaced.replace(Regex("\\s+"), " ")
        return collapsed.take(120)
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
        prefs.edit {
            putString(KEY_SAVE_TREE_URI, next.saveTreeUri)
            putString(KEY_SAVE_DIR_DISPLAY, next.saveDirDisplayName)
            putString(KEY_DEFAULT_FORMAT, next.defaultFormat)
            putString(KEY_FILE_TEMPLATE, next.fileNameTemplate)
            putString(KEY_CONFLICT_POLICY, next.conflictPolicy)
            putString(KEY_THROTTLE_PRESET, next.throttlePreset)
            putLong(KEY_THROTTLE_CUSTOM_BASE_DELAY, next.customBaseDelayMs)
            putLong(KEY_THROTTLE_CUSTOM_JITTER, next.customJitterMaxMs)
            putInt(KEY_THROTTLE_CUSTOM_BATCH_SIZE, next.customBatchSize)
            putLong(KEY_THROTTLE_CUSTOM_BATCH_REST, next.customBatchRestMs)
            putLong(KEY_THROTTLE_CUSTOM_BACKOFF_BASE, next.customBackoffBaseMs)
            putLong(KEY_THROTTLE_CUSTOM_BACKOFF_MAX, next.customBackoffMaxMs)
        }
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

    private fun persistRecordsLocked(records: List<DanmuDownloadRecord>) {
        _records.value = records
        val payload = runCatching {
            json.encodeToString(ListSerializer(DanmuDownloadRecord.serializer()), records)
        }.onFailure {
            Log.w("DanmuDownloadRepo", "序列化下载记录失败，不覆写持久化", it)
        }.getOrNull()
        if (payload != null) {
            prefs.edit { putString(KEY_RECORDS_JSON, payload) }
        }
    }

    private fun persistQueueTasks(tasks: List<DanmuDownloadTask>) {
        _queueTasks.value = tasks
        val payload = runCatching {
            json.encodeToString(ListSerializer(DanmuDownloadTask.serializer()), tasks)
        }.onFailure {
            Log.w("DanmuDownloadRepo", "序列化队列任务失败，不覆写持久化", it)
        }.getOrNull()
        if (payload != null) {
            prefs.edit { putString(KEY_QUEUE_JSON, payload) }
        }
    }

    private fun loadSettings(): DanmuDownloadSettings {
        return DanmuDownloadSettings(
            saveTreeUri = prefs.getString(KEY_SAVE_TREE_URI, "").orEmpty(),
            saveDirDisplayName = prefs.getString(KEY_SAVE_DIR_DISPLAY, "").orEmpty(),
            defaultFormat = prefs.getString(KEY_DEFAULT_FORMAT, DanmuDownloadFormat.Xml.value).orEmpty(),
            fileNameTemplate = prefs.getString(KEY_FILE_TEMPLATE, DanmuDownloadSettings().fileNameTemplate).orEmpty(),
            conflictPolicy = prefs.getString(KEY_CONFLICT_POLICY, DownloadConflictPolicy.Rename.key).orEmpty(),
            throttlePreset = prefs.getString(KEY_THROTTLE_PRESET, DownloadThrottlePreset.Conservative.key).orEmpty(),
            customBaseDelayMs = prefs.getLong(
                KEY_THROTTLE_CUSTOM_BASE_DELAY,
                DanmuDownloadSettings().customBaseDelayMs
            ),
            customJitterMaxMs = prefs.getLong(
                KEY_THROTTLE_CUSTOM_JITTER,
                DanmuDownloadSettings().customJitterMaxMs
            ),
            customBatchSize = prefs.getInt(
                KEY_THROTTLE_CUSTOM_BATCH_SIZE,
                DanmuDownloadSettings().customBatchSize
            ),
            customBatchRestMs = prefs.getLong(
                KEY_THROTTLE_CUSTOM_BATCH_REST,
                DanmuDownloadSettings().customBatchRestMs
            ),
            customBackoffBaseMs = prefs.getLong(
                KEY_THROTTLE_CUSTOM_BACKOFF_BASE,
                DanmuDownloadSettings().customBackoffBaseMs
            ),
            customBackoffMaxMs = prefs.getLong(
                KEY_THROTTLE_CUSTOM_BACKOFF_MAX,
                DanmuDownloadSettings().customBackoffMaxMs
            )
        )
    }

    private fun loadRecords(): List<DanmuDownloadRecord> {
        val raw = prefs.getString(KEY_RECORDS_JSON, "[]").orEmpty()
        return runCatching {
            json.decodeFromString(ListSerializer(DanmuDownloadRecord.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private fun loadQueueTasks(): List<DanmuDownloadTask> {
        val raw = prefs.getString(KEY_QUEUE_JSON, "[]").orEmpty()
        return runCatching {
            json.decodeFromString(ListSerializer(DanmuDownloadTask.serializer()), raw)
        }.getOrDefault(emptyList())
    }
}
