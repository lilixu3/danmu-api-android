package com.example.danmuapiapp.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.danmuapiapp.domain.model.DanmuDownloadFormat
import com.example.danmuapiapp.domain.model.DanmuDownloadInput
import com.example.danmuapiapp.domain.model.DanmuDownloadRecord
import com.example.danmuapiapp.domain.model.DanmuDownloadResult
import com.example.danmuapiapp.domain.model.DanmuDownloadSettings
import com.example.danmuapiapp.domain.model.DanmuDownloadTask
import com.example.danmuapiapp.domain.model.DownloadConflictPolicy
import com.example.danmuapiapp.domain.model.DownloadQueueStatus
import com.example.danmuapiapp.domain.model.DownloadRecordStatus
import com.example.danmuapiapp.domain.model.DownloadThrottlePreset
import com.example.danmuapiapp.domain.model.toQueueTask
import com.example.danmuapiapp.domain.repository.DanmuDownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
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
        private const val KEY_RECORDS_JSON = "records_json"
        private const val KEY_QUEUE_JSON = "queue_json"
        private const val MAX_RECORDS = 500
        private const val MAX_QUEUE_TASKS = 1200
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

            val treeUri = Uri.parse(treeUriText)
            val root = DocumentFile.fromTreeUri(context, treeUri)
                ?: error("保存目录无效，请重新选择")
            if (!root.canWrite()) {
                error("保存目录不可写，请重新授权目录权限")
            }

            onProgress(0.08f, "正在请求弹幕数据")
            val requestUrl = buildCommentUrl(input)
            val (httpCode, payload) = requestDanmuPayload(requestUrl)

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
            val normalizedPayload = normalizePayloadIfNeeded(input, payload)
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

    override fun clearRecords() {
        _records.value = emptyList()
        prefs.edit().putString(KEY_RECORDS_JSON, "[]").apply()
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
            httpCode = result.httpCode,
            errorMessage = result.errorMessage
        )
    }

    private fun requestDanmuPayload(url: String): Pair<Int, ByteArray> {
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
            return code to body.bytes()
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

    private fun persistSettings(next: DanmuDownloadSettings) {
        _settings.value = next
        prefs.edit()
            .putString(KEY_SAVE_TREE_URI, next.saveTreeUri)
            .putString(KEY_SAVE_DIR_DISPLAY, next.saveDirDisplayName)
            .putString(KEY_DEFAULT_FORMAT, next.defaultFormat)
            .putString(KEY_FILE_TEMPLATE, next.fileNameTemplate)
            .putString(KEY_CONFLICT_POLICY, next.conflictPolicy)
            .putString(KEY_THROTTLE_PRESET, next.throttlePreset)
            .apply()
    }

    private fun appendRecord(record: DanmuDownloadRecord) {
        val merged = listOf(record) + _records.value
        val trimmed = merged.take(MAX_RECORDS)
        _records.value = trimmed
        val payload = runCatching {
            json.encodeToString(ListSerializer(DanmuDownloadRecord.serializer()), trimmed)
        }.getOrDefault("[]")
        prefs.edit().putString(KEY_RECORDS_JSON, payload).apply()
    }

    private fun persistQueueTasks(tasks: List<DanmuDownloadTask>) {
        _queueTasks.value = tasks
        val payload = runCatching {
            json.encodeToString(ListSerializer(DanmuDownloadTask.serializer()), tasks)
        }.getOrDefault("[]")
        prefs.edit().putString(KEY_QUEUE_JSON, payload).apply()
    }

    private fun loadSettings(): DanmuDownloadSettings {
        return DanmuDownloadSettings(
            saveTreeUri = prefs.getString(KEY_SAVE_TREE_URI, "").orEmpty(),
            saveDirDisplayName = prefs.getString(KEY_SAVE_DIR_DISPLAY, "").orEmpty(),
            defaultFormat = prefs.getString(KEY_DEFAULT_FORMAT, DanmuDownloadFormat.Json.value).orEmpty(),
            fileNameTemplate = prefs.getString(KEY_FILE_TEMPLATE, DanmuDownloadSettings().fileNameTemplate).orEmpty(),
            conflictPolicy = prefs.getString(KEY_CONFLICT_POLICY, DownloadConflictPolicy.Rename.key).orEmpty(),
            throttlePreset = prefs.getString(KEY_THROTTLE_PRESET, DownloadThrottlePreset.Conservative.key).orEmpty()
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
