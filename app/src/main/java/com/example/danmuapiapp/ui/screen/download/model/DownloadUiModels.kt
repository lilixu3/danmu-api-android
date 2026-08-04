package com.example.danmuapiapp.ui.screen.download

import com.example.danmuapiapp.domain.model.DanmuFilePreview
import com.example.danmuapiapp.domain.model.DanmuDownloadRecord
import com.example.danmuapiapp.domain.model.DownloadQueueStatus
import com.example.danmuapiapp.domain.model.DownloadRecordStatus

data class DownloadAnimeCandidate(
    val animeId: Long,
    val title: String,
    val episodeCount: Int,
    val imageUrl: String = "",
    val sourceLabel: String = "",
    val year: String = "",
    val episodeLabel: String = "",
    val directUrl: String = "",
    val resolvedEpisodeTitle: String = ""
)

data class DownloadEpisodeCandidate(
    val episodeId: Long,
    val episodeNumber: Int,
    val title: String,
    val source: String,
    val directUrl: String = "",
    val posterUrl: String = "",
    val year: String = "",
    val resolvedEpisodeLabel: String = ""
)

enum class EpisodeDownloadState {
    Idle,
    Queued,
    Running,
    Success,
    Failed,
    Skipped,
    Canceled
}

data class EpisodeDownloadUiState(
    val state: EpisodeDownloadState = EpisodeDownloadState.Idle,
    val progress: Float = 0f,
    val detail: String = "",
    val downloadedBefore: Boolean = false,
    val downloadedRecordCount: Int = 0,
    val lastDownloadedAt: Long? = null
)

data class AnimeDownloadHistorySummary(
    val downloadedEpisodeCount: Int = 0,
    val successfulRecordCount: Int = 0,
    val latestDownloadedAt: Long? = null
)

data class DownloadRecordAnimeGroup(
    val key: String,
    val title: String,
    val records: List<DanmuDownloadRecord>,
    val episodes: List<DownloadRecordEpisodeGroup>,
    val latestAt: Long,
    val failedCount: Int,
    val skippedCount: Int,
    val totalBytes: Long
)

data class DownloadRecordEpisodeGroup(
    val key: String,
    val episodeNo: Int,
    val title: String,
    val records: List<DanmuDownloadRecord>,
    val representative: DanmuDownloadRecord,
    val previewRecord: DanmuDownloadRecord?,
    val status: DownloadRecordStatus,
    val successfulFileCount: Int,
    val latestAt: Long
)

data class EpisodeStateSummary(
    val total: Int = 0,
    val idle: Int = 0,
    val queued: Int = 0,
    val running: Int = 0,
    val success: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
    val canceled: Int = 0
) {
    val unfinished: Int
        get() = total - success - skipped
}

data class DownloadQueueSummary(
    val total: Int = 0,
    val pending: Int = 0,
    val running: Int = 0,
    val success: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
    val canceled: Int = 0
) {
    val active: Int
        get() = pending + running
}

data class AnimeQueueEpisodeItem(
    val taskId: Long,
    val episodeId: Long,
    val episodeNo: Int,
    val episodeTitle: String,
    val source: String,
    val status: DownloadQueueStatus,
    val detail: String,
    val updatedAt: Long
)

data class AnimeQueueGroup(
    val animeTitle: String,
    val total: Int,
    val completed: Int,
    val pending: Int,
    val running: Int,
    val success: Int,
    val failed: Int,
    val skipped: Int,
    val canceled: Int,
    val runningEpisodeNo: Int? = null,
    val detail: String = "",
    val latestUpdatedAt: Long = 0L,
    val episodes: List<AnimeQueueEpisodeItem> = emptyList()
) {
    val progress: Float
        get() = if (total <= 0) 0f else completed.toFloat() / total.toFloat()
}

data class DanmuPreviewDialogState(
    val loadingRecordId: Long? = null,
    val preview: DanmuFilePreview? = null,
    val error: String? = null
) {
    val isVisible: Boolean
        get() = loadingRecordId != null || preview != null || error != null
}
