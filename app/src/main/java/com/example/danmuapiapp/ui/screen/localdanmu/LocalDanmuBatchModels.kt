package com.example.danmuapiapp.ui.screen.localdanmu

import com.example.danmuapiapp.data.repository.LocalDanmuFileBrowser
import com.example.danmuapiapp.data.repository.LocalDanmuResourceKey
import com.example.danmuapiapp.domain.model.LocalDanmuParseConfidence
import com.example.danmuapiapp.domain.model.LocalDanmuType
import java.util.Calendar

/** 目录浏览里的一行：目录/文件 + 「已导入」标记 + 多选状态。 */
data class LocalDanmuBrowserRow(
    val entry: LocalDanmuFileBrowser.Entry,
    val importedLabel: String? = null,
    val selected: Boolean = false
)

enum class LocalDanmuBatchStatus(val label: String) {
    Ready("待导入"),
    Uploading("上传中"),
    Success("已导入"),
    Failed("失败"),
    Skipped("已跳过")
}

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
    val progress: Float = 0f
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
            type == LocalDanmuType.Movie -> "正片"
            season != null && episode != null -> "第${season}季 第${episode}集"
            episode != null -> "第${episode}集"
            else -> "未识别集数"
        }
}

data class LocalDanmuBatchState(
    val visible: Boolean = false,
    val items: List<LocalDanmuBatchItem> = emptyList(),
    val isRunning: Boolean = false,
    val cancelRequested: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0
) {
    val pendingCount: Int
        get() = items.count { it.canImport }

    val successCount: Int
        get() = items.count { it.status == LocalDanmuBatchStatus.Success }

    val failedCount: Int
        get() = items.count { it.status == LocalDanmuBatchStatus.Failed }

    val progress: Float
        get() = if (total <= 0) 0f else (completed.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    val hasResult: Boolean
        get() = successCount > 0 || failedCount > 0
}

internal fun batchResourceKeyOf(item: LocalDanmuBatchItem): String? =
    LocalDanmuResourceKey.buildResourceKey(
        title = item.title,
        year = item.year,
        type = item.type.wire,
        season = item.season,
        episode = item.episode
    )

/**
 * 重复选择同一来源直接跳过；同一季同一集的第二个文件保留，但提示会覆盖。
 */
internal fun markBatchDuplicates(items: List<LocalDanmuBatchItem>): List<LocalDanmuBatchItem> {
    val seenSources = mutableSetOf<String>()
    val seenKeys = mutableMapOf<String, String>()
    return items.map { item ->
        val key = batchResourceKeyOf(item)
        when {
            !seenSources.add(item.sourceUri) -> item.copy(
                status = LocalDanmuBatchStatus.Skipped,
                selected = false,
                message = "重复选择，已跳过"
            )
            key != null && seenKeys.containsKey(key) -> item.copy(
                message = "与「${seenKeys[key]}」同季同集，导入时会覆盖"
            )
            else -> {
                if (key != null) seenKeys[key] = item.displayName
                item
            }
        }
    }
}
