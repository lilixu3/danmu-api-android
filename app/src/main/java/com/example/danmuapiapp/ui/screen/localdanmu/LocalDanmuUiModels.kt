package com.example.danmuapiapp.ui.screen.localdanmu

import com.example.danmuapiapp.domain.model.LocalDanmuEditScope
import com.example.danmuapiapp.domain.model.LocalDanmuGroup
import com.example.danmuapiapp.domain.model.LocalDanmuResource
import com.example.danmuapiapp.domain.model.LocalDanmuResourceKey
import com.example.danmuapiapp.domain.model.LocalDanmuSnapshot
import com.example.danmuapiapp.domain.model.LocalDanmuType
import com.example.danmuapiapp.domain.model.LocalDanmuWritePermission
import com.example.danmuapiapp.ui.screen.download.DanmuPreviewDialogState


/**
 * 元数据编辑弹窗状态。scope=Resource 时只有集数/文件名字段生效，
 * scope=Group 时只有标题/年份/类型/季数生效（对应核心 PATCH 的两档范围）。
 */
data class LocalDanmuEditState(
    val target: LocalDanmuResource? = null,
    val scope: LocalDanmuEditScope = LocalDanmuEditScope.Resource,
    val title: String = "",
    val year: Int? = null,
    val type: LocalDanmuType = LocalDanmuType.Tv,
    val season: Int? = null,
    val episode: Int? = null,
    val filename: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null
) {
    val isVisible: Boolean get() = target != null
    val isMovie: Boolean get() = type == LocalDanmuType.Movie
}

data class LocalDanmuUiState(
    val isLoading: Boolean = false,
    val snapshot: LocalDanmuSnapshot = LocalDanmuSnapshot(emptyList(), emptyList()),
    val writePermission: LocalDanmuWritePermission = LocalDanmuWritePermission.ReadOnly,
    val localSourceEnabled: Boolean = false,
    val searchQuery: String = "",
    val typeFilter: String? = null,
    val expandedGroupKeys: Set<String> = emptySet(),
    val message: String? = null,
    val errorMessage: String? = null,
    val allFilesAccessGranted: Boolean = false,
    val browser: LocalDanmuBrowserState = LocalDanmuBrowserState(),
    val batch: LocalDanmuBatchState = LocalDanmuBatchState(),
    val detailResource: LocalDanmuResource? = null,
    val editState: LocalDanmuEditState = LocalDanmuEditState(),
    val previewState: DanmuPreviewDialogState = DanmuPreviewDialogState(),
    val pendingDeleteResources: List<LocalDanmuResource> = emptyList(),
    val deleteInProgress: Boolean = false,
    val deleteProgress: String = ""
) {
    val filteredGroups: List<LocalDanmuGroup>
        get() {
            val query = searchQuery.trim()
            return snapshot.groups.filter { group ->
                val typeMatches = typeFilter == null || group.type == typeFilter
                val queryMatches = query.isBlank() ||
                    group.title.contains(query, ignoreCase = true) ||
                    group.episodes.any { episode ->
                        episode.filename.contains(query, ignoreCase = true) ||
                            episode.episode?.toString() == query
                    }
                typeMatches && queryMatches
            }
        }

    val totalEpisodeFiles: Int get() = snapshot.resources.size
    val totalDanmuCount: Int get() = snapshot.resources.sumOf { it.count }
    val totalSizeBytes: Long get() = snapshot.resources.sumOf { it.sizeBytes }
}

/**
 * 核心 PATCH scope=group 的作用范围：同标题、同年份、同类型、同季的全部资源。
 * app 侧只用于展示「会改到几个文件」，核心仍是最终判定方。
 */
internal fun localDanmuGroupResources(
    resources: List<LocalDanmuResource>,
    target: LocalDanmuResource
): List<LocalDanmuResource> {
    val normalizedTitle = LocalDanmuResourceKey.normalizeKey(target.title)
    val normalizedType = LocalDanmuResourceKey.normalizeType(target.type)
    return resources.filter { resource ->
        LocalDanmuResourceKey.normalizeKey(resource.title) == normalizedTitle &&
            resource.year == target.year &&
            LocalDanmuResourceKey.normalizeType(resource.type) == normalizedType &&
            resource.season == target.season
    }
}

data class LocalDanmuBrowserState(
    val visible: Boolean = false,
    val path: String = "",
    val parent: String? = null,
    val rows: List<LocalDanmuBrowserRow> = emptyList(),
    val isLoading: Boolean = false,
    val truncated: Boolean = false,
    val error: String? = null,
    val selectedPaths: Set<String> = emptySet(),
    val isDefaultDirectory: Boolean = false
) {
    val selectedCount: Int get() = selectedPaths.size
    /** 当前目录下可勾选的文件（不含子目录）。 */
    val selectablePaths: List<String>
        get() = rows.filterNot { it.entry.isDirectory }.map { it.entry.path }
    val allSelected: Boolean
        get() = selectablePaths.isNotEmpty() && selectablePaths.all { it in selectedPaths }
}
