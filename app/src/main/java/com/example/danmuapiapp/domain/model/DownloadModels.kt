package com.example.danmuapiapp.domain.model

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DanmuDownloadFormat(
    val value: String,
    val label: String,
    val extension: String,
    val mimeType: String
) {
    Json(
        value = "json",
        label = "JSON",
        extension = "json",
        mimeType = "application/json"
    ),
    Xml(
        value = "xml",
        label = "XML",
        extension = "xml",
        mimeType = "application/xml"
    );

    companion object {
        fun fromValue(raw: String?): DanmuDownloadFormat {
            val value = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.value == value } ?: Xml
        }
    }
}

enum class DownloadConflictPolicy(
    val key: String,
    val label: String
) {
    Rename("rename", "重命名"),
    Overwrite("overwrite", "覆盖"),
    Skip("skip", "跳过");

    companion object {
        fun fromKey(raw: String?): DownloadConflictPolicy {
            val key = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.key == key } ?: Rename
        }
    }
}

enum class DownloadRecordStatus(
    val key: String,
    val label: String
) {
    Success("success", "成功"),
    Failed("failed", "失败"),
    Skipped("skipped", "跳过")
}

enum class DownloadQueueStatus(
    val key: String,
    val label: String
) {
    Pending("pending", "待处理"),
    Running("running", "下载中"),
    Success("success", "成功"),
    Failed("failed", "失败"),
    Skipped("skipped", "跳过"),
    Canceled("canceled", "已取消");

    companion object {
        fun fromKey(raw: String?): DownloadQueueStatus {
            val key = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.key == key } ?: Pending
        }
    }
}

enum class DownloadThrottlePreset(
    val key: String,
    val label: String,
    val baseDelayMs: Long,
    val jitterMaxMs: Long,
    val batchSize: Int,
    val batchRestMs: Long,
    val backoffBaseMs: Long,
    val backoffMaxMs: Long
) {
    Conservative(
        key = "conservative",
        label = "保守",
        baseDelayMs = 2000L,
        jitterMaxMs = 900L,
        batchSize = 8,
        batchRestMs = 30000L,
        backoffBaseMs = 15000L,
        backoffMaxMs = 300000L
    ),
    Balanced(
        key = "balanced",
        label = "均衡",
        baseDelayMs = 1400L,
        jitterMaxMs = 600L,
        batchSize = 10,
        batchRestMs = 20000L,
        backoffBaseMs = 10000L,
        backoffMaxMs = 240000L
    ),
    Fast(
        key = "fast",
        label = "快速",
        baseDelayMs = 900L,
        jitterMaxMs = 350L,
        batchSize = 12,
        batchRestMs = 12000L,
        backoffBaseMs = 8000L,
        backoffMaxMs = 180000L
    ),
    Custom(
        key = "custom",
        label = "自定义",
        baseDelayMs = 1400L,
        jitterMaxMs = 600L,
        batchSize = 10,
        batchRestMs = 20000L,
        backoffBaseMs = 10000L,
        backoffMaxMs = 240000L
    );

    fun toConfig(): DownloadThrottleConfig {
        return DownloadThrottleConfig(
            preset = this,
            baseDelayMs = baseDelayMs,
            jitterMaxMs = jitterMaxMs,
            batchSize = batchSize,
            batchRestMs = batchRestMs,
            backoffBaseMs = backoffBaseMs,
            backoffMaxMs = backoffMaxMs
        )
    }

    companion object {
        fun fromKey(raw: String?): DownloadThrottlePreset {
            val key = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.key == key } ?: Conservative
        }
    }
}

data class DownloadThrottleConfig(
    val preset: DownloadThrottlePreset,
    val baseDelayMs: Long,
    val jitterMaxMs: Long,
    val batchSize: Int,
    val batchRestMs: Long,
    val backoffBaseMs: Long,
    val backoffMaxMs: Long
) {
    val label: String
        get() = preset.label
}

data class DanmuFileNameTemplatePreset(
    val name: String,
    val template: String
)

val DANMU_FILE_NAME_TEMPLATE_PRESETS: List<DanmuFileNameTemplatePreset> = listOf(
    DanmuFileNameTemplatePreset(
        name = "标准",
        template = "{animeTitle}_E{episodeNo2}_{episodeTitle}_{source}.{ext}"
    ),
    DanmuFileNameTemplatePreset(
        name = "简洁",
        template = "{animeTitle}-第{episodeNo}集.{ext}"
    ),
    DanmuFileNameTemplatePreset(
        name = "按来源",
        template = "{animeTitle}_[{source}]_E{episodeNo2}_{episodeTitle}.{ext}"
    ),
    DanmuFileNameTemplatePreset(
        name = "带日期",
        template = "{animeTitle}_E{episodeNo2}_{date}_{source}.{ext}"
    )
)

fun renderFileNameTemplatePreview(
    template: String,
    format: DanmuDownloadFormat = DanmuDownloadFormat.Xml,
    animeTitle: String = "凡人修仙传",
    episodeTitle: String = "再入星海",
    episodeNo: Int = 3,
    episodeId: Long = 2334455L,
    source: String = "bilibili1"
): String {
    val now = Date()
    val mapping = linkedMapOf(
        "animeTitle" to animeTitle,
        "episodeTitle" to episodeTitle,
        "episodeNo" to episodeNo.toString(),
        "episodeNo2" to episodeNo.toString().padStart(2, '0'),
        "episodeNo3" to episodeNo.toString().padStart(3, '0'),
        "episodeId" to episodeId.toString(),
        "source" to source,
        "format" to format.value,
        "ext" to format.extension,
        "date" to SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(now),
        "datetime" to SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(now)
    )

    var preview = template.trim().ifBlank { DanmuDownloadSettings().fileNameTemplate }
    mapping.forEach { (key, value) ->
        preview = preview.replace("{$key}", value)
    }
    if (!preview.contains('.')) {
        preview += ".${format.extension}"
    }
    return preview
}

@Serializable
data class DanmuDownloadSettings(
    val saveTreeUri: String = "",
    val saveDirDisplayName: String = "",
    val defaultFormat: String = DanmuDownloadFormat.Xml.value,
    val fileNameTemplate: String = "{animeTitle}_E{episodeNo2}_{episodeTitle}_{source}.{ext}",
    val conflictPolicy: String = DownloadConflictPolicy.Rename.key,
    val throttlePreset: String = DownloadThrottlePreset.Conservative.key,
    val customBaseDelayMs: Long = DownloadThrottlePreset.Custom.baseDelayMs,
    val customJitterMaxMs: Long = DownloadThrottlePreset.Custom.jitterMaxMs,
    val customBatchSize: Int = DownloadThrottlePreset.Custom.batchSize,
    val customBatchRestMs: Long = DownloadThrottlePreset.Custom.batchRestMs,
    val customBackoffBaseMs: Long = DownloadThrottlePreset.Custom.backoffBaseMs,
    val customBackoffMaxMs: Long = DownloadThrottlePreset.Custom.backoffMaxMs
) {
    fun format(): DanmuDownloadFormat = DanmuDownloadFormat.fromValue(defaultFormat)
    fun policy(): DownloadConflictPolicy = DownloadConflictPolicy.fromKey(conflictPolicy)
    fun throttle(): DownloadThrottlePreset = DownloadThrottlePreset.fromKey(throttlePreset)

    fun throttleConfig(): DownloadThrottleConfig {
        val preset = throttle()
        if (preset != DownloadThrottlePreset.Custom) {
            return preset.toConfig()
        }
        val baseDelay = customBaseDelayMs.coerceIn(100L, 120_000L)
        val jitter = customJitterMaxMs.coerceIn(0L, 20_000L)
        val batch = customBatchSize.coerceIn(1, 500)
        val batchRest = customBatchRestMs.coerceIn(0L, 900_000L)
        val backoffBase = customBackoffBaseMs.coerceIn(1_000L, 900_000L)
        val backoffMax = customBackoffMaxMs.coerceIn(backoffBase, 1_800_000L)
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
}

@Serializable
data class DanmuDownloadRecord(
    val id: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val animeTitle: String,
    val episodeTitle: String,
    val episodeId: Long,
    val episodeNo: Int,
    val source: String,
    val format: String,
    val status: String,
    val fileName: String = "",
    val relativePath: String = "",
    val fileUri: String = "",
    val durationMs: Long = 0L,
    val bytes: Long = 0L,
    val httpCode: Int? = null,
    val errorMessage: String? = null
) {
    fun statusEnum(): DownloadRecordStatus {
        return DownloadRecordStatus.entries.firstOrNull { it.key == status } ?: DownloadRecordStatus.Failed
    }

    fun formatEnum(): DanmuDownloadFormat = DanmuDownloadFormat.fromValue(format)
}

@Serializable
data class DanmuDownloadTask(
    val taskId: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val apiBaseUrl: String = "",
    val animeTitle: String = "",
    val episodeTitle: String = "",
    val episodeId: Long = 0L,
    val episodeNo: Int = 0,
    val source: String = "",
    val format: String = DanmuDownloadFormat.Xml.value,
    val fileNameTemplate: String = DanmuDownloadSettings().fileNameTemplate,
    val conflictPolicy: String = DownloadConflictPolicy.Rename.key,
    val status: String = DownloadQueueStatus.Pending.key,
    val attempts: Int = 0,
    val lastDetail: String = ""
) {
    fun statusEnum(): DownloadQueueStatus = DownloadQueueStatus.fromKey(status)

    fun toInput(): DanmuDownloadInput {
        return DanmuDownloadInput(
            apiBaseUrl = apiBaseUrl,
            animeTitle = animeTitle,
            episodeTitle = episodeTitle,
            episodeId = episodeId,
            episodeNo = episodeNo,
            source = source,
            format = DanmuDownloadFormat.fromValue(format),
            fileNameTemplate = fileNameTemplate,
            conflictPolicy = DownloadConflictPolicy.fromKey(conflictPolicy)
        )
    }
}

data class DanmuDownloadInput(
    val apiBaseUrl: String,
    val animeTitle: String,
    val episodeTitle: String,
    val episodeId: Long,
    val episodeNo: Int,
    val source: String,
    val format: DanmuDownloadFormat,
    val fileNameTemplate: String,
    val conflictPolicy: DownloadConflictPolicy
)

data class DanmuDownloadResult(
    val status: DownloadRecordStatus,
    val fileName: String,
    val relativePath: String,
    val fileUri: String,
    val bytes: Long,
    val durationMs: Long,
    val httpCode: Int? = null,
    val errorMessage: String? = null
)

fun DanmuDownloadInput.toQueueTask(taskId: Long): DanmuDownloadTask {
    val now = System.currentTimeMillis()
    return DanmuDownloadTask(
        taskId = taskId,
        createdAt = now,
        updatedAt = now,
        apiBaseUrl = apiBaseUrl,
        animeTitle = animeTitle,
        episodeTitle = episodeTitle,
        episodeId = episodeId,
        episodeNo = episodeNo,
        source = source,
        format = format.value,
        fileNameTemplate = fileNameTemplate,
        conflictPolicy = conflictPolicy.key,
        status = DownloadQueueStatus.Pending.key,
        attempts = 0,
        lastDetail = "等待下载"
    )
}
