package com.example.danmuapiapp.ui.screen.apitest

internal data class IndexedDanmuComment(val comment: DanmuCommentItem, val sources: Set<String>)

internal data class DanmuListIndex(
    val entries: List<IndexedDanmuComment>,
    val sourceKeys: List<String>,
    val coloredCount: Int
)

internal data class DanmuListFilterResult(
    val comments: List<DanmuCommentItem>,
    val modeCounts: Map<DanmuCommentFilter, Int>,
    val sourceCounts: List<Pair<String, Int>>
)

internal fun buildDanmuListIndex(comments: List<DanmuCommentItem>, fallbackSource: String): DanmuListIndex {
    val sourceCounts = mutableMapOf<String, Int>()
    val entries = comments.sortedBy { it.timeSeconds }.map { comment ->
        val sources = comment.sourceLabel.ifBlank { fallbackSource }.split('&', '＆')
            .map(::detailSourceKey).filter { it.isNotBlank() }.toSet().ifEmpty { setOf("未知来源") }
        sources.forEach { source -> sourceCounts[source] = (sourceCounts[source] ?: 0) + 1 }
        IndexedDanmuComment(comment, sources)
    }
    return DanmuListIndex(entries, sourceCounts.toList().sortedByDescending { it.second }.map { it.first },
        comments.count { it.colorValue?.let { value -> (value and 0xffffffL) != 0xffffffL } == true })
}

// Each facet counts matches for the other filters, so choosing a source updates
// the mode totals and choosing a mode updates the source totals.
internal fun filterDanmuList(
    index: DanmuListIndex,
    query: String = "",
    mode: DanmuCommentFilter = DanmuCommentFilter.All,
    sources: Set<String> = emptySet(),
    bucket: DanmuHeatBucket? = null,
    includeRangeEnd: Boolean = false
): DanmuListFilterResult {
    val keyword = query.trim()
    val modeCounts = DanmuCommentFilter.entries.associateWith { 0 }.toMutableMap()
    val sourceCounts = index.sourceKeys.associateWith { 0 }.toMutableMap()
    val comments = mutableListOf<DanmuCommentItem>()
    index.entries.forEach { entry ->
        val comment = entry.comment
        if (keyword.isNotEmpty() && !comment.text.contains(keyword, ignoreCase = true)) return@forEach
        if (bucket != null && (comment.timeSeconds < bucket.startSeconds ||
                comment.timeSeconds > bucket.endSeconds || !includeRangeEnd && comment.timeSeconds == bucket.endSeconds)) return@forEach
        val matchesMode = mode == DanmuCommentFilter.All || comment.filter == mode
        val matchesSource = sources.isEmpty() || entry.sources.any { it in sources }
        if (matchesSource) {
            modeCounts[DanmuCommentFilter.All] = modeCounts.getValue(DanmuCommentFilter.All) + 1
            if (comment.filter != DanmuCommentFilter.All) modeCounts[comment.filter] = modeCounts.getValue(comment.filter) + 1
        }
        if (matchesMode) entry.sources.forEach { source -> sourceCounts[source] = sourceCounts.getValue(source) + 1 }
        if (matchesMode && matchesSource) comments.add(comment)
    }
    return DanmuListFilterResult(comments, modeCounts, index.sourceKeys.map { it to sourceCounts.getValue(it) })
}
