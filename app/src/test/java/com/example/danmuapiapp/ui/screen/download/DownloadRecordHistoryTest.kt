package com.example.danmuapiapp.ui.screen.download

import com.example.danmuapiapp.domain.model.DanmuDownloadRecord
import com.example.danmuapiapp.domain.model.DanmuDownloadTask
import com.example.danmuapiapp.domain.model.DownloadQueueStatus
import com.example.danmuapiapp.domain.model.DownloadRecordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRecordHistoryTest {
    @Test
    fun `目录恢复记录应匹配搜索结果并标记已下载`() {
        val anime = DownloadAnimeCandidate(
            animeId = 101L,
            title = "测试动画 from bilibili",
            episodeCount = 12
        )
        val record = record(
            animeTitle = "测试动画",
            episodeNo = 2,
            episodeTitle = "测试动画_E02",
            source = "目录同步"
        )

        val history = buildAnimeDownloadHistorySummary(anime, listOf(record))
        val states = buildInitialEpisodeStates(
            animeTitle = anime.title,
            animeId = anime.animeId,
            episodes = listOf(
                DownloadEpisodeCandidate(
                    episodeId = 2002L,
                    episodeNumber = 2,
                    title = "第二集",
                    source = "bilibili"
                )
            ),
            queueTasksSnapshot = emptyList(),
            recordsSnapshot = listOf(record)
        )

        assertEquals(1, history.downloadedEpisodeCount)
        assertTrue(states.getValue(2002L).downloadedBefore)
        assertEquals(EpisodeDownloadState.Success, states.getValue(2002L).state)
    }

    @Test
    fun `新的失败任务不应覆盖历史已下载标记`() {
        val episode = DownloadEpisodeCandidate(
            episodeId = 3001L,
            episodeNumber = 1,
            title = "第一集",
            source = "tencent"
        )
        val successfulRecord = record(
            animeTitle = "测试动画",
            episodeNo = 1,
            episodeTitle = "第一集",
            source = "tencent"
        )
        val failedTask = DanmuDownloadTask(
            taskId = 88L,
            animeTitle = "测试动画",
            episodeTitle = "第一集",
            episodeId = episode.episodeId,
            episodeNo = 1,
            source = "tencent",
            status = DownloadQueueStatus.Failed.key,
            updatedAt = 20L
        )

        val state = buildInitialEpisodeStates(
            animeTitle = "测试动画",
            episodes = listOf(episode),
            queueTasksSnapshot = listOf(failedTask),
            recordsSnapshot = listOf(successfulRecord)
        ).getValue(episode.episodeId)

        assertEquals(EpisodeDownloadState.Failed, state.state)
        assertTrue(state.downloadedBefore)
        assertEquals(1, state.downloadedRecordCount)
    }

    @Test
    fun `新记录有AnimeID时应优先精确匹配`() {
        val record = record(
            animeTitle = "同名动画",
            episodeNo = 1,
            episodeTitle = "第一集",
            source = "unknown",
            animeId = 11L
        )

        assertTrue(
            recordMatchesAnime(
                record,
                DownloadAnimeCandidate(11L, "同名动画", 1)
            )
        )
        assertFalse(
            recordMatchesAnime(
                record,
                DownloadAnimeCandidate(12L, "同名动画", 1)
            )
        )
        assertFalse(
            recordMatchesAnime(
                record.copy(animeId = 0L, animeTitle = "同名动画 (2024)"),
                DownloadAnimeCandidate(0L, "同名动画 (2025)", 1)
            )
        )
    }

    @Test
    fun `记录列表应按剧和集聚合且保留多次下载`() {
        val records = listOf(
            record(
                id = 1L,
                animeTitle = "测试动画 (2024) from bilibili",
                episodeNo = 1,
                episodeTitle = "第一集",
                source = "bilibili",
                fileUri = "content://danmu/one",
                bytes = 100L
            ),
            record(
                id = 2L,
                animeTitle = "测试动画 (2024)",
                episodeNo = 1,
                episodeTitle = "第一集",
                source = "bilibili",
                status = DownloadRecordStatus.Failed,
                createdAt = 20L
            ),
            record(
                id = 3L,
                animeTitle = "测试动画 (2024)",
                episodeNo = 2,
                episodeTitle = "第二集",
                source = "目录同步",
                fileUri = "content://danmu/two",
                bytes = 200L,
                createdAt = 30L
            )
        )

        val groups = buildDownloadRecordAnimeGroups(records)

        assertEquals(1, groups.size)
        assertEquals(2, groups.single().episodes.size)
        assertEquals(2, groups.single().episodes.first { it.episodeNo == 1 }.records.size)
        assertEquals(DownloadRecordStatus.Success, groups.single().episodes.first { it.episodeNo == 1 }.status)
        assertEquals(300L, groups.single().totalBytes)
    }

    private fun record(
        id: Long = 1L,
        animeTitle: String,
        episodeNo: Int,
        episodeTitle: String,
        source: String,
        status: DownloadRecordStatus = DownloadRecordStatus.Success,
        animeId: Long = 0L,
        fileUri: String = "content://danmu/default",
        bytes: Long = 10L,
        createdAt: Long = 10L
    ): DanmuDownloadRecord {
        return DanmuDownloadRecord(
            id = id,
            createdAt = createdAt,
            animeTitle = animeTitle,
            episodeTitle = episodeTitle,
            episodeId = 0L,
            episodeNo = episodeNo,
            source = source,
            format = "xml",
            status = status.key,
            fileName = "episode.xml",
            fileUri = fileUri,
            bytes = bytes,
            animeId = animeId
        )
    }
}
