package com.example.danmuapiapp.ui.screen.localdanmu

import com.example.danmuapiapp.domain.model.LocalDanmuBatchItem
import com.example.danmuapiapp.domain.model.LocalDanmuBatchStatus
import com.example.danmuapiapp.domain.model.LocalDanmuResourceKey
import com.example.danmuapiapp.domain.model.LocalDanmuType

internal fun applyImportType(items: List<LocalDanmuBatchItem>, type: LocalDanmuType?): List<LocalDanmuBatchItem> = items.map { item ->
    if (!item.selected || item.status == LocalDanmuBatchStatus.Success || item.status == LocalDanmuBatchStatus.Uploading) item
    else {
        val resolved = type ?: item.detectedType
        item.copy(type = resolved, episode = if (resolved == LocalDanmuType.Movie) null else item.detectedEpisode,
            status = LocalDanmuBatchStatus.Ready, message = "")
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
