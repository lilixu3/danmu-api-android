package com.example.danmuapiapp.ui.screen.apitest

enum class DanmuCommentFilter(val label: String) {
    All("全部"),
    Scroll("滚动"),
    Top("顶部"),
    Bottom("底部")
}

data class DanmuCommentItem(
    val uniqueId: Long,
    val timeSeconds: Double,
    val mode: Int,
    val filter: DanmuCommentFilter,
    val text: String,
    val colorValue: Long?,
    val colorHex: String,
    val fontSize: Int? = null,
    val sentAtSeconds: Long? = null,
    val sourceLabel: String = "",
    val sourceId: String = ""
)

data class DanmuHeatBucket(
    val index: Int,
    val startSeconds: Double,
    val endSeconds: Double,
    val count: Int
)

data class DanmuHighMoment(
    val startSeconds: Double,
    val endSeconds: Double,
    val count: Int
)

data class DanmuInsight(
    val commentId: Long?,
    val animeTitle: String,
    val episodeTitle: String,
    val source: String,
    val pathLabel: String,
    val matchedAtMillis: Long,
    val totalCount: Int,
    val durationSeconds: Double,
    val maxHeatCount: Int,
    val requestDurationMs: Long?,
    val rawPreview: String,
    val rawPreviewTruncated: Boolean,
    val heatBuckets: List<DanmuHeatBucket>,
    val highMoments: List<DanmuHighMoment>,
    val comments: List<DanmuCommentItem>
)

data class TextPreview(
    val text: String,
    val isTruncated: Boolean
)

data class MatchSelection(
    val commentId: Long,
    val animeTitle: String,
    val episodeTitle: String,
    val source: String
)

data class ApiDebugResponse(
    val responseCode: Int,
    val responseBody: String,
    val responseDurationMs: Long,
    val previewText: String,
    val fullText: String,
    val previewTruncated: Boolean,
    val bodySizeBytes: Int,
    val danmuInsight: DanmuInsight? = null
)
