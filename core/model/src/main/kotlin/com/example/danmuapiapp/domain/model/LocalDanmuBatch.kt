package com.example.danmuapiapp.domain.model

import java.util.Calendar

enum class LocalDanmuBatchStatus(val label: String) {
    Ready("待导入"),
    Uploading("上传中"),
    Success("已导入"),
    Failed("失败"),
    Skipped("已跳过")
}

/**
 * One file prepared for local-danmu upload.
 *
 * The model belongs to the domain because both download-import data flows and local-danmu UI
 * operate on it. UI-only batch state remains in the local-danmu screen package.
 */
data class LocalDanmuBatchItem(
    val id: Long,
    val sourceUri: String,
    val displayName: String,
    val sizeBytes: Long?,
    val formatHint: String,
    val mimeType: String,
    val title: String,
    val year: Int?,
    val type: LocalDanmuType,
    val season: Int?,
    val episode: Int?,
    val confidence: LocalDanmuParseConfidence = LocalDanmuParseConfidence.Medium,
    /** 该文件此前已经导入过时的提示（按路径或文件名+大小匹配）。 */
    val importedLabel: String? = null,
    val selected: Boolean = true,
    val status: LocalDanmuBatchStatus = LocalDanmuBatchStatus.Ready,
    val message: String = "",
    val progress: Float = 0f,
    val expectedSessionKey: String = "",
    val detectedType: LocalDanmuType = type,
    val detectedEpisode: Int? = episode
) {
    val validationError: String?
        get() = LocalDanmuResourceKey.validateUpload(
            title = title,
            year = year,
            type = type,
            season = season,
            episode = episode,
            currentYear = Calendar.getInstance().get(Calendar.YEAR)
        )

    val canImport: Boolean
        get() = selected &&
            sourceUri.isNotBlank() &&
            validationError == null &&
            status != LocalDanmuBatchStatus.Success &&
            status != LocalDanmuBatchStatus.Skipped &&
            status != LocalDanmuBatchStatus.Uploading

    val episodeLabel: String
        get() = when {
            type == LocalDanmuType.Unknown -> "类型待确认"
            type == LocalDanmuType.Movie -> "正片"
            season != null && episode != null -> "第${season}季 第${episode}集"
            episode != null -> "第${episode}集"
            else -> "未识别集数"
        }
}
